package com.rc.crawler;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Created by rafaelcastro on 8/10/17.
 * Handles the logic for finding and downloading the PDFs that appear in a website
 */
class DownloadLinkFinder {


    private final GUILabelManagement guiLabels;
    private final Crawler crawler;
    private SimultaneousDownloadsGUI simultaneousDownloadsGUI;
    private boolean thereWasAPDF;
    private int numberOfSearches;
    private int numOfNonGoogleURL;
    private int counterOfLinks;
    private int pdfCounter;
    private boolean isMultipleSearch;
    private PDFDownloader pdfDownloader;
    private String typeOfSearch;
    private int limit;
    private SearchEngine.SupportedSearchEngine engine;
    private StatsGUI stats;

    DownloadLinkFinder(GUILabelManagement guiLabels, Crawler crawler, SimultaneousDownloadsGUI
            simultaneousDownloadsGUI, SearchEngine.SupportedSearchEngine engine, StatsGUI stats) {
        this.guiLabels = guiLabels;
        this.crawler = crawler;
        this.simultaneousDownloadsGUI = simultaneousDownloadsGUI;
        this.engine = engine;
        this.stats = stats;
    }


    /**
     * Starts the process for searching inside a website, or multiple websites, looking for PDF download links
     *
     * @param citingPapersURL  Main URL where the papers that cite a paper appear.
     * @param isMultipleSearch Boolean
     * @param pdfDownloader    Class that handles the download process of a PDF
     * @param typeOfSearch     searchForCitedBy or searchForTheArticle
     * @param limit            Number of PDFs to download
     * @return Array where item at index 0 is the number of PDFs downloaded, and the item at index 1 is a boolean
     * that is only true if there was at least 1 PDF available to download.
     */
    Object[] getPDFs(String citingPapersURL, boolean isMultipleSearch, PDFDownloader pdfDownloader, String typeOfSearch,
                     int limit) {
        this.isMultipleSearch = isMultipleSearch;
        this.pdfDownloader = pdfDownloader;
        this.typeOfSearch = typeOfSearch;
        this.limit = limit;
        Long currThreadID = Thread.currentThread().getId();
        //Keeps track of the number of searches done
        numberOfSearches = 0;
        thereWasAPDF = false;
        if (!isMultipleSearch) {
            guiLabels.setOutput("Starting download process...");
        }
        pdfCounter = 0;
        //Go though all the search result links
        Map.Entry<ArrayList<String>, Integer> entry = SearchEngine.getAllLinks(citingPapersURL, typeOfSearch,
                isMultipleSearch, engine, crawler);
        ArrayList<String> list = entry.getKey();
        numOfNonGoogleURL = entry.getValue();
        counterOfLinks = 0;

        if (list.isEmpty()) {
            throw new IllegalArgumentException("Please search of an author before downloading");
        }
        //isFirst is true if it is the first URL of the list
        //Go through all the links of the current doc
        for (String currUrl : list) {
            counterOfLinks++;
            if (pdfCounter >= limit) {
                if (!isMultipleSearch) {
                    guiLabels.setOutput("All PDFs available have been downloaded");
                }
                break;
            }
            if (currUrl.contains("pdf+html")) {
                currUrl = currUrl.replaceAll("pdf\\+html", "pdf");
            }

            //Pause the current thread before searching for a random period of time
            randomPause(currUrl, isMultipleSearch);
            //Get the Document from the current url
            guiLabels.setOutput("Trying to connect to search result...");
            Document citingPaper = null;
            if (!currUrl.contains(".pdf")) {
                try {
                    citingPaper = getCitingPaper(currUrl, currThreadID);
                } catch (IllegalArgumentException e) {
                    guiLabels.setOutput("Could not download, trying again");
                    continue;
                }
            }
            //If the crawler was able to connect, then parse the website
            String result = parseWebsite(currThreadID, currUrl, isMultipleSearch, citingPaper,
                    limit);
            if (result == null) {
                break;
            }
        }
        Object[] array = new Object[2];
        array[0] = pdfCounter;
        array[1] = thereWasAPDF;
        return array;
    }

    /**
     * Parse the website Document to find at least 1 download link
     */
    private String parseWebsite(Long currThreadID, String currUrl, boolean isMultipleSearch, Document citingPapers,
                                int limit) {

        //Add request to current website
        String baseURL = "";
        try {
            baseURL = crawler.addRequestToMapOfRequests(currUrl, crawler.getMapThreadIdToProxy().get
                    (currThreadID), -1);
            //Update GUI
            guiLabels.setConnectionOutput("Number of reqs to " + baseURL + " from proxy " + crawler
                    .getMapThreadIdToProxy().get(currThreadID).getProxy() + " = " + crawler.getNumberOfRequestFromMap
                    (currUrl,
                            crawler.getMapThreadIdToProxy().get(currThreadID)));
        } catch (IllegalArgumentException e) {
            //the current url is not correct
            return "";
        }
        numberOfSearches++;

        if (numberOfSearches > 2 && isMultipleSearch) {
            simultaneousDownloadsGUI.updateStatus("No PDF found (" + numberOfSearches + " attempt(s))");
        }

        //If the url is part of google scholar, and the search result is empty, or  6 google searches are made and
        // no valid result is found, stop.
        if ((counterOfLinks > numOfNonGoogleURL && !SearchEngine.isThereASearchResult(engine, citingPapers, currUrl)) ||
                numberOfSearches == 6) {
            guiLabels.setConnectionOutput("No more papers found.");
            if (!isMultipleSearch) {
                guiLabels.setOutput("No more papers found.");
                guiLabels.setLoadBar(limit / (double) limit);
            } else {
                simultaneousDownloadsGUI.updateStatus("No more papers found");
            }
            return null;
        }
        try {
            getPDFsHelper(baseURL, currUrl, citingPapers, currThreadID);
        } catch (Exception e) {
            e.printStackTrace(System.out);
        }
        return "";
    }

    /**
     * Pauses the current thread before going to the search results
     */
    private void randomPause(String currUrl, boolean isMultipleSearch) {
        //Wait before going to search results or website
        int timeToWait = crawler.getTimeToWait();
        if (!currUrl.contains("scholar.google")) {
            timeToWait = timeToWait - 12;
        }

        //Update GUI
        guiLabels.setConnectionOutput("Waiting " + timeToWait + " seconds before going to the search results");
        if (!isMultipleSearch) {
            try {
                Thread.sleep(300);
            } catch (InterruptedException ignored) {
            }
            guiLabels.setOutput("Waiting " + timeToWait + " seconds before going to the search results");
        } else {
            simultaneousDownloadsGUI.updateStatus("Waiting " + timeToWait + " s");
        }
        try {
            Thread.sleep(timeToWait * 1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if (isMultipleSearch) {
            simultaneousDownloadsGUI.updateStatus("Downloading...");
        }
    }


    /**
     * Gets the Document (JSoup Document) of the current URL
     *
     * @return Jsoup Document
     */
    private Document getCitingPaper(String currUrl, Long currThreadID) {
        //Increase counter for every new google link
        Document citingPapers;

        //Check if the proxy has at least 40 request to a given website to replace it
        if (crawler.getNumberOfRequestFromMap(currUrl, crawler.getMapThreadIdToProxy().get(currThreadID)) >= 40) {
            guiLabels.setConnectionOutput("Wait... Changing proxy from thread " + currThreadID + " because of" +
                    " amount of requests...");
            if (!isMultipleSearch) {
                guiLabels.setOutput("Changing proxy because of amounts of requests");
            }
            citingPapers = crawler.changeIP(currUrl, false, false, engine, Optional.empty());
        } else {
            //If not, just connect to the previous proxy
            ProxyChanger proxyChanger = new ProxyChanger(guiLabels, crawler, engine, stats);
            proxyChanger.setComesFromDownload(true);
            citingPapers = proxyChanger.getProxy(currUrl, true, false);
        }

        if (citingPapers == null) {
            return null;
        }

        //Verify that Google has not flagged the proxy
        if (citingPapers.text().contains("Sorry, we can't verify that you're not a robot")) {
            //In case you been flagged as a bot even before searching
            guiLabels.setConnectionOutput("Google flagged thread " + currThreadID + " proxy as a bot." +
                    "\nChanging to a different one");
            citingPapers = crawler.changeIP(currUrl, false, false, engine, Optional.empty());
            if (citingPapers == null) {
                throw new IllegalArgumentException();
            }
        }
        return citingPapers;

    }

    /**
     * Final step for downloading a PDF.
     * Retrieves all the PDF links that a website has and tries to download as many as requested.
     *
     * @param currUrl      Current url
     * @param citingPapers The Document of the website that contains the download links.
     * @param currThreadID The current thread id.
     */
    private void getPDFsHelper(String baseURL, String currUrl, Document citingPapers, Long currThreadID) {
        //Go through all the links of the search result, and find links that contain PDFs
        Elements linksInsidePaper;
        if (citingPapers == null) {
            String html = "<p>Remove links<a href=\"" + currUrl + "\">Download</a> Download.&nbsp;</p>";
            citingPapers = Jsoup.parse(html);
        }
        linksInsidePaper = citingPapers.select("a[href]");
        String text;
        String absLink;
        for (Element link : linksInsidePaper) {
            text = link.text();
            absLink = link.attr("abs:href");
            if (absLink.isEmpty() && !baseURL.isEmpty()) {
                String relativeURL = link.attr("href");
                absLink = baseURL + relativeURL;
            }
            if (absLink.contains("pdf+html")) {
                absLink = absLink.replaceAll("pdf\\+html", "pdf");
            }
            if (absLink.contains("authguide") || absLink.contains("masthead") || absLink.contains
                    ("/pb-assets/documents") || absLink.isEmpty()) {
                continue;
            }
            if (text.contains("PDF") || text.contains("Download")) {
                Proxy proxyToUSe = null;
                int attempt = 0;
                //Try to download the doc using a proxy. If it returns error 403, 429, or the proxy is unable to
                //connect, use the proxy that is currently at the top of the queue, without removing it.
                while (attempt < 3) {
                    pdfCounter++;
                    crawler.getAtomicCounter().increment();
                    File file = null;
                    try {
//                        Proxy proxyToUSe = crawler.getMapThreadIdToProxy().get(currThreadID);
//                        if (attempt > 0) {
//                            proxyToUSe = crawler.getQueueOfConnections().peek();
                       if (attempt == 0) {
                           proxyToUSe = getProxyToUse( absLink, currThreadID, null);
                       } else {
                           proxyToUSe = getProxyToUse( absLink, currThreadID, proxyToUSe);
                       }
                        if (!isMultipleSearch) {
                            guiLabels.setOutput("Downloading...");
                        }
                        //Download the file
                        pdfDownloader.setCrawler(crawler);
                        pdfDownloader.downloadPDF(absLink, pdfCounter, guiLabels, proxyToUSe, crawler.getSpeedUp());
                        //Release the proxy used to download the file
                        InUseProxies.getInstance().releaseProxyUsedToDownload(proxyToUSe);

                        file = new File("./DownloadedPDFs/" + pdfDownloader.getPath() + "/" + pdfCounter
                                + ".pdf");
                        if (file.length() == 0 || !file.canRead()) {
                            thereWasAPDF = true;
                            throw new IOException("File is invalid");
                        }
                        PDFVerifier pdfVerifier = null;
                        try {
                            pdfVerifier = new PDFVerifier(file);
                        } catch (Exception | Error e) {
                            e.printStackTrace(System.out);
                        }
                        ExecutorService executorService3 = Executors.newSingleThreadExecutor(new MyThreadFactory());
                        Future<String> future = executorService3.submit(pdfVerifier);
                        String result = "";
                        try {
                            result = future.get(15 * 1000, TimeUnit.MILLISECONDS);
                        } catch (Exception e) {
                            e.printStackTrace(System.out);
                            thereWasAPDF = true;
                            future.cancel(true);
                        }
                        if (result.equals("Invalid File")) {
                            throw new IOException("File is invalid");
                        }
                        numberOfSearches = 0;
                        break;

                    } catch (Exception e2) {
                        InUseProxies.getInstance().releaseProxyUsedToDownload(proxyToUSe);
                        guiLabels.setConnectionOutput("This file could not be downloaded, skipping...");
                        if (isMultipleSearch) {
                            simultaneousDownloadsGUI.updateStatus("Invalid file, skipping...");
                        }
                        pdfCounter--;
                        crawler.getAtomicCounter().decrease();
                        attempt++;

                        if (e2.getMessage() != null && (e2.getMessage().equals("Timeout") || e2.getMessage().equals
                                ("Connection reset"))) {
                            thereWasAPDF = true;
                        }
                        //If it is NOT any of these three errors, then do not try to re-download it
                        if (e2.getMessage() == null || !e2.getMessage().contains("Error 403") &&
                                !e2.getMessage().contains("response code: 429") &&
                                !e2.getMessage().contains("Unable to tunnel through proxy.")) {
                            if (file != null) {
                                //If the file was created, delete it
                                //noinspection ResultOfMethodCallIgnored
                                file.delete();
                            }
                            break;
                        } else {
                            thereWasAPDF = true;
                        }
                    }


                }

                updateGUI(isMultipleSearch);
                if (pdfCounter >= limit) {
                    break;
                }

                //If is the first URL, and the URL is not a google search result, then after finding the
                //first PDF stop since you can end up downloading PDFs that are not part of the query
                if (counterOfLinks <= numOfNonGoogleURL && typeOfSearch.equals("searchForTheArticle")) {
                    break;

                }
            }
        }
    }

    /**
     * Returns the proxy to use for download.
     * @return Proxy
     */
    private Proxy getProxyToUse(String absLink, Long currThreadID, Proxy proxyUsedBefore) {
        boolean thereWasAnError = true;
        Proxy proxyToUse = null;
        //If there was a proxy used before but it failed to download, block the proxy from the current url
        if (proxyUsedBefore != null) {
            crawler.addRequestToMapOfRequests(absLink, proxyUsedBefore, 50);
        }
        while (thereWasAnError) {

            Set<Proxy> workingProxies = InUseProxies.getInstance().getCurrentlyUsedProxies();
            proxyToUse = crawler.getMapThreadIdToProxy().get(currThreadID);
            boolean found = false;
            //Get a proxy that has less than 40 requests to the current website, based on the working proxies, and that
            // is not been used by another thread
            for (Proxy p : workingProxies) {
                if (crawler.getNumberOfRequestFromMap(absLink, p) < 40 && !InUseProxies.getInstance()
                        .isProxyInUseForDownloading(p)) {
                    proxyToUse = p;
                    found = true;
                    break;
                }
            }
            if (!found) {
                resetProxies(absLink);
            }
            //Add request to current website using the current proxy
            crawler.addRequestToMapOfRequests(absLink, proxyToUse, -1);
            try {
                InUseProxies.getInstance().addProxyUsedToDownload(proxyToUse);
                thereWasAnError = false;
            } catch (IllegalArgumentException ignored) {
            }
        }
        return proxyToUse;


    }

    /**
     * Resets all the proxies with more than 40 requests
     */
    private void resetProxies(String absLink) {
        for (Proxy p : InUseProxies.getInstance().getCurrentlyUsedProxies()) {
            crawler.addRequestToMapOfRequests(absLink, p, 0);
        }
    }

    private void updateGUI(boolean isMultipleSearch) {
        if (!isMultipleSearch) {
            guiLabels.setNumberOfPDF(typeOfSearch + "," + pdfCounter + "/" + limit);
            guiLabels.setLoadBar(pdfCounter / (double) limit);
        } else {

            simultaneousDownloadsGUI.updateStatus(pdfCounter + "/" + limit);
            simultaneousDownloadsGUI.updateProgressBar(0.3 + (pdfCounter / (double) limit) * 0.7);

            guiLabels.setNumberOfPDFsMultiple(typeOfSearch + "," + crawler.getAtomicCounter().value());

        }

    }
}
