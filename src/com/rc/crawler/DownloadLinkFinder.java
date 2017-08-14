package com.rc.crawler;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by rafaelcastro on 8/10/17.
 * Crawls a website to find download links.
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

    DownloadLinkFinder(GUILabelManagement guiLabels, Crawler crawler, SimultaneousDownloadsGUI
            simultaneousDownloadsGUI) {
        this.guiLabels = guiLabels;
        this.crawler = crawler;
        this.simultaneousDownloadsGUI = simultaneousDownloadsGUI;
    }


    Object[] getPDFS(String citingPapersURL, boolean isMultipleSearch, PDFDownloader pdfDownloader, String typeOfSearch,
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
            guiLabels.setOutput("Downloading...");
        }
        pdfCounter = 0;
        //Go though all the search result links
        Map.Entry<ArrayList<String>, Integer> entry = crawler.getAllLinks(citingPapersURL, typeOfSearch,
                isMultipleSearch);
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

            //Pause the current thread before searching
            pause(currUrl, isMultipleSearch);
            //Get the Document from the current url
            Document citingPaper = getCitingPaper(currUrl, currThreadID);
            if (citingPaper == null) {
                continue;
            }
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
     * Parse the website to find at least 1 download link
     */
    private String parseWebsite(Long currThreadID, String currUrl, boolean isMultipleSearch, Document citingPapers,
                                int limit) {

        //Add request to current website
        try {
            String baseURL = crawler.addRequestToMapOfRequests(currUrl, crawler.getMapThreadIdToProxy().get
                    (currThreadID));
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

        Pattern gScholarSearchResult = Pattern.compile("(<div class=\"gs_r\">)([^∞])+?(?=(<div " +
                "class=\"gs_r\">)|(<div id=\"gs_ccl_bottom\">))");
        Matcher gScholarSRMatcher = gScholarSearchResult.matcher(citingPapers.html());
        //If the url is part of google scholar, and the search result is empty, or  6 google searches are made and
        // no valid result is found, stop.
        if ((counterOfLinks > numOfNonGoogleURL && !gScholarSRMatcher.find()) || numberOfSearches == 6) {
            guiLabels.setConnectionOutput("No more papers found.");
            if (!isMultipleSearch) {
                guiLabels.setOutput("No more papers found.");
                guiLabels.setLoadBar(limit / (double) limit);
            } else {
                simultaneousDownloadsGUI.updateStatus("No more papers found");
            }
            return null;
        }

        if (!isMultipleSearch) {
            guiLabels.setOutput("Downloading...");
        }
        getPDFsHelper(citingPapers, currThreadID);
        return "";
    }

    /**
     * Pauses the current thread before going to the search results
     */
    private void pause(String currUrl, boolean isMultipleSearch) {
        //Wait before going to search results or website
        int timeToWait = crawler.getTimeToWait();
        if (!currUrl.contains("scholar.google")) {
            timeToWait = timeToWait - 12;
        }

        //Update GUI
        guiLabels.setConnectionOutput("Waiting " + timeToWait + " seconds before going to the search results");
        if (!isMultipleSearch) {
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
     * Gets the Document of the current URL
     *
     * @return Jsoup Document
     */
    private Document getCitingPaper(String currUrl, Long currThreadID) {
        //Increase counter for every new google link
        Document citingPapers;
        try {
            if (crawler.getNumberOfRequestFromMap(currUrl, crawler.getMapThreadIdToProxy().get(currThreadID)) >= 50) {
                guiLabels.setConnectionOutput("Wait... Changing proxy from thread " + currThreadID + " because of" +
                        " amount of requests...");
                citingPapers = crawler.changeIP(currUrl, false, false);
            } else {
                citingPapers = crawler.changeIP(currUrl, true, false);
            }
        } catch (IllegalArgumentException e) {
            return null;
        }

        if (citingPapers == null) {
            return null;
        }

        //Verify that Google has not flagged the proxy
        if (citingPapers.text().contains("Sorry, we can't verify that you're not a robot")) {
            //In case you been flagged as a bot even before searching
            guiLabels.setConnectionOutput("Google flagged thread " + currThreadID + " proxy as a bot." +
                    "\nChanging to a different one");
            citingPapers = crawler.changeIP(currUrl, false, false);
            if (citingPapers == null) {
                return null;
            }
        }
        return citingPapers;

    }

    private void getPDFsHelper(Document citingPapers, Long currThreadID) {
        //Go through all the links of the search result, and find links that contain PDFs
        Elements linksInsidePaper = citingPapers.select("a[href]");
        String text;
        String absLink;
        for (Element link : linksInsidePaper) {
            text = link.text();
            absLink = link.attr("abs:href");
            if (absLink.contains("pdf+html")) {
                absLink = absLink.replaceAll("pdf\\+html", "pdf");
            }
            if (absLink.contains("authguide") || absLink.contains("masthead") || absLink.contains
                    ("/pb-assets/documents")) {
                continue;
            }
            if (text.contains("PDF")) {
                int attempt = 0;
                //Try to download the doc using a proxy. If it returns error 403, 429, or the proxy is unable to
                //connect, use the proxy that is currently at the top of the queue, without removing it.
                while (attempt < 3) {
                    pdfCounter++;
                    crawler.getAtomicCounter().increment();
                    File file = null;
                    try {
                        Proxy proxyToUSe = crawler.getMapThreadIdToProxy().get(currThreadID);
                        if (attempt > 0) {
                            proxyToUSe = crawler.getQueueOfConnections().peek();
                        }
                        pdfDownloader.setCrawler(crawler);
                        pdfDownloader.downloadPDF(absLink, pdfCounter, guiLabels, proxyToUSe, crawler.getSpeedUp());
                        file = new File("./DownloadedPDFs/" + pdfDownloader.getPath() + "/" + pdfCounter
                                + ".pdf");
                        if (file.length() == 0 || !file.canRead()) {
                            thereWasAPDF = true;
                            throw new IOException("File is invalid");
                        }

                        PDFVerifier pdfVerifier = new PDFVerifier(file);
                        ExecutorService executorService3 = Executors.newSingleThreadExecutor(new MyThreadFactory());
                        Future<String> future = executorService3.submit(pdfVerifier);
                        String result = "";
                        try {
                            result = future.get(15 * 1000, TimeUnit.MILLISECONDS);
                        } catch (Exception e) {
                            thereWasAPDF = true;
                            future.cancel(true);
                        }
                        if (result.equals("Invalid File")) {
                            throw new IOException("File is invalid");
                        }
                        numberOfSearches = 0;
                        break;

                    } catch (Exception e2) {
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
                            System.out.println("Error: " + e2.getMessage());
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
