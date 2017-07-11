package com.rc.crawler;

import org.apache.pdfbox.cos.COSDocument;
import org.apache.pdfbox.io.RandomAccessBufferedFileInputStream;
import org.apache.pdfbox.pdfparser.PDFParser;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by rafaelcastro on 5/31/17.
 * Crawler to gather data from Google Scholar.
 */
class Crawler {
    private final GUILabelManagement guiLabels;
    private SimultaneousDownloadsGUI simultaneousDownloadsGUI;
    //Counts number of requests
    private Integer[] listOfTimes;
    private Map<Long, Proxy> mapThreadIdToProxy = Collections.synchronizedMap(new HashMap<Long, Proxy>());
    //Keeps track of all the proxies that have been downloaded, since we are picking a random element, it is faster to
    //use a list to get it
    private List<Proxy> listOfProxiesGathered;
    //Keeps track of all proxies downloaded, used for contains() and remove(). Once a proxy has been used, it is removed
    // from the set
    private Set<Proxy> setOfProxyGathered;
    //Sets of all proxies ever downloaded, so that when we download new proxies while running the program, we only add
    // new proxies to the setOfProxyGathered
    private Set<Proxy> setOfAllProxiesEver;
    //Random chosen time to wait before searching
    private ExecutorService executorService;
    private ConcurrentLinkedQueue<Proxy> queueOfConnections = new ConcurrentLinkedQueue<>();
    private ProxiesDownloader proxiesDownloader;
    //Stores proxies tht have over 50 requests, but work, to use them later
    private List<Proxy> listOfWorkingProxies;
    //Maps thread id to the number of request for the given proxy being used in that thread
    private Map<String, Map<Proxy, Integer>> mapWebsiteToReqCountFromProxy = Collections.synchronizedMap(new
            HashMap<String, Map<Proxy, Integer>>());

    //Maps the different search results to their respective "cited by" URL
    private HashMap<String, String[]> searchResultToLink = new HashMap<>();
    private boolean speedUp;
    private AtomicCounter atomicCounter = new AtomicCounter();
    private boolean thereIsConnection = true;

    Map<Long, Proxy> getMapThreadIdToProxy() {
        return mapThreadIdToProxy;
    }

    public boolean isThreadGettingMoreProxies() {
        return threadIsGettingMoreProxies;
    }

    public void setThreadIsGettingMoreProxies(boolean threadIsGettingMoreProxies) {
        this.threadIsGettingMoreProxies = threadIsGettingMoreProxies;
    }

    private boolean threadIsGettingMoreProxies = false;

    /**
     * Constructor.
     */
    Crawler(GUILabelManagement guiLabels) {
        this.guiLabels = guiLabels;
        //Start getting the list of all proxies. Load progress bar up to 75%
        guiLabels.setLoadBar(0);
        guiLabels.setOutput("Initializing...");
        guiLabels.setOutputMultiple("Initializing...");
        //Check if there is an internet connection
        ExecutorService connectionVerifier = Executors.newSingleThreadExecutor(new MyThreadFactory());
        connectionVerifier.submit((Runnable) () -> {
            boolean lostConnection = false;
            //noinspection InfiniteLoopStatement
            while (true) {
                try {
                    URL url = new URL("http://www.google.com");
                    HttpURLConnection con = (HttpURLConnection) url
                            .openConnection();
                    con.connect();
                    if (con.getResponseCode() == 200) {
                        thereIsConnection = true;
                        if (lostConnection) {
                            guiLabels.setOutput("Online!");
                            guiLabels.setOutputMultiple("Online!");
                        }
                    }
                } catch (Exception exception) {
                    thereIsConnection = false;
                    lostConnection = true;
                }
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });

    }

    /**
     * Loads the crawler. Calls the different methods to download n proxies, and at least 1 working connection, before
     * the user can use the program.
     */
    void loadCrawler() {
        //Retrieve n proxies (n = 600) before starting the program.
        guiLabels.setConnectionOutput("Directory " + getClass().getProtectionDomain().getCodeSource().getLocation());
        getProxies();
        guiLabels.setOutput("Establishing connections...");
        guiLabels.setOutputMultiple("Establishing connections...");


        //Try to connect to n proxies
        startConnectionThreads();
    }

    /**
     * Start threads to try to get an initial connection.
     * Ideally, the program will have 10 active working proxies at all times.
     */
    private void startConnectionThreads() {

        executorService = Executors.newFixedThreadPool(21, new MyThreadFactory());
        if (setOfProxyGathered.size() < 900) {
            //Add 1 request to get more proxies
            Request getMoreProxiesRequest = new Request(false, "getProxies", this, guiLabels);
            executorService.submit(getMoreProxiesRequest);
        }

        //Start a list of Future that will contain the available connections
        List<Future<Proxy>> listOfFuture = new ArrayList<>();
        List<Callable<Proxy>> listOfRequests = new ArrayList<>();
        //Add 10 requests
        for (int i = 0; i < 10; i++) {
            listOfRequests.add(new Request(false, "getConnection", this, guiLabels));
        }
        boolean isFirst = true;
        for (Callable<Proxy> request : listOfRequests) {
            //Add the result to the list of connections. All the elements here have active connections
            listOfFuture.add(executorService.submit(request));
        }
        //Go through all the treads and get the unique working proxy each received
        for (Future<Proxy> proxy : listOfFuture) {
            try {
                if (isFirst) {
                    isFirst = false;
                    Proxy curr = proxy.get();
                    queueOfConnections.add(curr);
                    guiLabels.setConnectionOutput("This is the first proxy that will be used: " +
                            queueOfConnections.peek().getProxy());
                    //"Done" loading, but just the first proxy
                    guiLabels.setLoadBar(1);
                    guiLabels.setLoadBarMultiple();
                    guiLabels.setOutput("Connected!");
                    guiLabels.setOutputMultiple("Connected!");
                    guiLabels.setConnectionOutput("Connected");
                    Proxy temp = queueOfConnections.peek();
                    guiLabels.setNumberOfWorkingIPs("add," + temp.getProxy() + " Port: " + temp.getPort());

                } else {
                    Proxy curr = proxy.get();
                    queueOfConnections.add(curr);
                    //Notify the number of working IPs
                    guiLabels.setNumberOfWorkingIPs("add," + curr.getProxy() + " Port: " + curr.getPort());
                    guiLabels.setConnectionOutput("Other proxies : " + curr.getProxy());
                }
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Downloads proxies from different websites, without duplicates. If proxies have been downloaded before, within a
     * 24h window, it loads those proxies instead.
     */
    private void getProxies() {
        int proxyCounter = 0;
        listOfProxiesGathered = Collections.synchronizedList(new ArrayList<Proxy>());
        //Sets don't allow repetition so we avoid duplicates
        setOfProxyGathered = Collections.synchronizedSet(new HashSet<Proxy>());

        setOfAllProxiesEver = Collections.synchronizedSet(new HashSet<Proxy>());

        listOfWorkingProxies = Collections.synchronizedList(new ArrayList<Proxy>());

        this.proxiesDownloader = new ProxiesDownloader();
        guiLabels.setOutput("Checking if a valid proxy file exists...");

        //Check first if there have been proxies downloaded
        Logger logger = Logger.getInstance();

        boolean fileExist = true;
        //First see if file exists

        Scanner scanner = null;
        File listOfProxiesFile = logger.getListOfProxies();
        try {
            scanner = new Scanner(listOfProxiesFile);
        } catch (FileNotFoundException e) {
            guiLabels.setConnectionOutput("Could not find ListOfProxies file.");
            fileExist = false;
        }

        if (fileExist && listOfProxiesFile.length() > 100) {
            //If file exist and is not empty, we continue
            boolean isFirstLine = true;
            boolean isValidDate = true;
            while (scanner.hasNext()) {
                String curr = scanner.nextLine();
                if (isFirstLine) {
                    //First line contains the date it was created. Verify that it has not been 24h since file was
                    //created.
                    DateTimeFormatter formatter = DateTimeFormat.forPattern("dd/MM/yyyy HH:mm:ss");
                    DateTime dateOfCreation = formatter.parseDateTime(curr);

                    DateTime now = new DateTime();
                    double hours = (now.getMillis() - dateOfCreation.getMillis()) / 1000 / 60 / 60;
                    if (hours >= 24) {
                        isValidDate = false;
                    }
                    isFirstLine = false;
                    continue;
                }
                if (!isValidDate) {
                    guiLabels.setOutput("No valid file found.");

                    //If it is not a valid date we stop.
                    scanner.close();
                    proxyCounter = proxiesDownloader.getProxiesFromWebsite(550, 0,
                            guiLabels, this, false);
                    break;
                }
                //It is a valid file so we retrieve all the stored proxies from the file.
                String[] ipAndProxyString = curr.split(",");
                Proxy proxy;
                try {
                    proxy = new Proxy(ipAndProxyString[0], Integer.valueOf(ipAndProxyString[1]));
                } catch (NumberFormatException e) {
                    continue;
                }

                //Add it to the sets
                if (!setOfAllProxiesEver.contains(proxy)) {
                    setOfProxyGathered.add(proxy);
                    listOfProxiesGathered.add(proxy);
                    setOfAllProxiesEver.add(proxy);
                    proxyCounter++;

                }
                Double d = (proxyCounter / (550) * 1.0) * 0.7;
                guiLabels.setLoadBar(d);
                guiLabels.setOutput("Proxies downloaded: " + proxyCounter + "/" + 550);
            }
            if (proxyCounter < 550) {
                //Get more proxies if there are not enough
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {
                }
                guiLabels.setOutput("Not enough proxies in file.");
                scanner.close();
                proxiesDownloader.getProxiesFromWebsite(550, proxyCounter, guiLabels,
                        this, false);
            }
            if (proxyCounter >= 1700) {
                //Delete and download proxies again because we have too many, and many will not work
                listOfProxiesGathered = Collections.synchronizedList(new ArrayList<Proxy>());
                setOfProxyGathered = Collections.synchronizedSet(new HashSet<Proxy>());
                setOfAllProxiesEver = Collections.synchronizedSet(new HashSet<Proxy>());
                listOfWorkingProxies = Collections.synchronizedList(new ArrayList<Proxy>());
                proxiesDownloader.getProxiesFromWebsite(550, 0, guiLabels,
                        this, false);
            }
        } else {
            guiLabels.setOutput("No valid file found.");
            proxiesDownloader.getProxiesFromWebsite(550, 0, guiLabels,
                    this, false);
        }
        if (scanner != null) {
            scanner.close();
        }
    }

    /**
     * Gets more unique proxies.
     */
    void getMoreProxies() {
        if (setOfAllProxiesEver.size() > 1700) {
            //delete all the proxies and start all over
            System.out.println("Restarting all lists of proxies");
            listOfProxiesGathered = Collections.synchronizedList(new ArrayList<Proxy>());
            setOfProxyGathered = Collections.synchronizedSet(new HashSet<Proxy>());
            setOfAllProxiesEver = Collections.synchronizedSet(new HashSet<Proxy>());
            listOfWorkingProxies = Collections.synchronizedList(new ArrayList<Proxy>());
            proxiesDownloader.getProxiesFromWebsite(1000, 0, guiLabels, this, false);
            mapWebsiteToReqCountFromProxy = new HashMap<>();

        } else if (listOfWorkingProxies.size() == 0) {
            proxiesDownloader.getProxiesFromWebsite(1000, setOfProxyGathered.size(), guiLabels, this, true);
        } else {
            //If there are proxies in this list, then we add them back to the sets so that they can re-use it
            for (Proxy p : listOfWorkingProxies) {
                listOfProxiesGathered.add(p);
                setOfProxyGathered.add(p);
            }
        }

    }

    /**
     * Search for an article in Google Scholar
     *
     * @param keyword          String with the title of the document
     * @param hasSearchBefore  has the user press the button search before
     * @param isMultipleSearch is the search being done in multiple article mode
     * @param type             "searchForTheArticle" or "searchForCitedBy". Retrieves a different URL depending on if we
     *                         are trying to
     *                         download the paper itself or the papers that cite the paper
     * @return array with 2 elements. First element represents the numOfCitations, the second is the citingPapersURL
     */
    String[] searchForArticle(String keyword, boolean hasSearchBefore, boolean isMultipleSearch, String type) throws IllegalArgumentException{
        int invalidAttempts = 0;
        //Replace space by + in the keyword as in the google search url
        keyword = keyword.replace(" ", "+");
        //Search google scholar
        String url = "https://scholar.google.com/scholar?hl=en&q=" + keyword;
        String numOfCitations = "";
        String citingPapersURL = "";
        String paperVersionsURL = "";

        boolean found = false;
        while (!found) {
            if (invalidAttempts >= 2) {
                //Program was unable to search for the query
                if (!isMultipleSearch) {
                    guiLabels.setSearchResultLabel(type + ",Could not find paper, please try writing more specific " +
                            "information");
                }
                numOfCitations = "";
                citingPapersURL = "";
                paperVersionsURL = "";
                found = true;
            } else {
                Document doc = changeIP(url, hasSearchBefore, false);
                if (doc == null) {
                    throw new IllegalArgumentException("Error searching for an article");
                }
                if (doc.text().contains("Sorry, we can't verify that you're not a robot")) {
                    //In case you been flags as a bot even before searching
                    guiLabels.setConnectionOutput("Google flagged this proxy as a bot. Changing to a different one");
                    doc = changeIP(url, false, false);

                }
                if (doc == null) {
                    throw new IllegalArgumentException("Error searching for an article");
                }
                //Check if doc contains "Did you mean to search for..." If so, redirect the corrected search
                if (doc.html().contains("Did you mean to search for:")) {
                    Pattern redirectPattern = Pattern.compile("Did you mean( to search for)?:.*<a href=\"[^\"]*");
                    Matcher redirectMatcher = redirectPattern.matcher(doc.html());
                    if (redirectMatcher.find()) {
                        String correctedURL = redirectMatcher.group();
                        correctedURL = correctedURL.replaceAll("Did you mean( to search for)?:.*<a href=\"", "");
                        correctedURL = correctedURL.replaceAll("/scholar.*(q=)", "https://scholar.google" +
                                ".com/scholar?hl=en&as_sdt=0,39&q=");
                        url = correctedURL;
                        doc = changeIP(url, hasSearchBefore, false);
                    }

                }

                Long currThread = Thread.currentThread().getId();
                //Increase request of current proxy
                String baseURL = addRequestToMapOfRequests(url, mapThreadIdToProxy.get(currThread));
                //Display
                guiLabels.setConnectionOutput("Number of reqs to " + baseURL + " from proxy " + mapThreadIdToProxy.get
                        (currThread).getProxy() +" = " + getNumberOfRequestFromMap(url, mapThreadIdToProxy.get
                        (currThread)));
                Elements links = doc.select("a[href]");
                SingleSearchResultFinder finder = new SingleSearchResultFinder(this, guiLabels,
                        simultaneousDownloadsGUI,
                        doc);
                //Analyze the current search result
                found = finder.findSingleSearchResult(links, type, url, isMultipleSearch);
                paperVersionsURL = finder.getPaperVersionsURL();
                numOfCitations = finder.getText();
                citingPapersURL = finder.getAbsLink();

                //Check if there is more than 1 result for a given search
                MultipleSearchResultsFinder finderMultiple = new MultipleSearchResultsFinder(doc, isMultipleSearch,
                        guiLabels, type, this);
                if (finderMultiple.findMultipleSearchResult(links, searchResultToLink)) {
                    numOfCitations = "There was more than 1 result found for your given query";
                }

                invalidAttempts++;
                hasSearchBefore = true;
            }
        }
        if (type.equals("searchForCitedBy")) {
            return new String[]{numOfCitations, citingPapersURL};
        } else {
            return new String[]{numOfCitations, paperVersionsURL};
        }

    }

    /**
     * Gets all the possible search results where the article is cited, based on a URL
     *
     * @return HashSet with all the links
     */
    private Map.Entry<ArrayList<String>, Integer> getAllLinks(String citingPapersURL, String typeOfSearch, boolean
            isMultipleSearch) {
        ArrayList<String> list = new ArrayList<>();
        Pattern pattern = Pattern.compile("=\\d*");
        Matcher matcher = pattern.matcher(citingPapersURL);
        String paperID = "";
        if (matcher.find()) {
            paperID = matcher.group();
            paperID = paperID.replace("=", "");
        }
        if (typeOfSearch.equals("searchForCitedBy")) {
            //Add the first result
            list.add(citingPapersURL);
            //Add all possible search results
            for (int i = 10; i < 1000 + 1; i = i + 10) {
                String sb = "https://scholar.google.com/scholar?start=" + i +
                        "&hl=en&oe=ASCII&as_sdt=5,39&sciodt=0,39&cites=" + paperID + "&scipsc=";
                list.add(sb);

            }
            return new AbstractMap.SimpleEntry<>(list, 0);
        } else {
            //If this is true, then we iterate though all the different versions available plus additional URLs
            if (citingPapersURL.contains("cluster")) {
                System.out.println("Case 2.1: Searching for All x Version URL and the first search result website url");
                String[] array = citingPapersURL.split("∆");
                String versionURL = array[0];
                int counter = 0;
                try {
                    for(int i = 1; i <4; i++) {
                        if (!list.contains(array[i])) {
                            if (array[i].contains("scholar")) {
                                MultipleSearchResultsFinder finder = new MultipleSearchResultsFinder(this);
                                if (!finder.verifyIfMultipleSearchResult(array[i], isMultipleSearch).isEmpty()) {
                                    list.add(array[i]);
                                    counter++;
                                }
                            }
                            else {
                                list.add(array[i]);
                                counter++;
                            }
                        }
                    }

                }
                catch (Exception ignored) {
                }

                list.add(versionURL);
                //Add all possible search results
                for (int i = 10; i < 1000 + 1; i = i + 10) {
                    String sb = "https://scholar.google.com/scholar?start=" + i +
                            "&hl=en&as_sdt=0,39&cluster=" + paperID;
                    list.add(sb);

                }
                return new AbstractMap.SimpleEntry<>(list, counter);
            } else {
                //When there is no Version URL from Google Scholar, then we just search for the search result, and
                //the main website URL
                System.out.println("Case 2.2 Searching for search result and main website url");
                //Add the SR as well as the link of the search result
                String[] array = citingPapersURL.split("∆");
                if (array.length == 2) {
                    list.add(array[1]);
                }
                //Verify if there no multiple search results
                MultipleSearchResultsFinder finder = new MultipleSearchResultsFinder(this);
                if (!finder.verifyIfMultipleSearchResult(array[0], isMultipleSearch).isEmpty()) {
                    list.add(array[0]);
                }
                return new AbstractMap.SimpleEntry<>(list, 1);
            }
        }
    }

    /**
     * Change current IP, or continue using the last working one
     *
     * @param url             url that you are trying to connect
     * @param hasSearchBefore has the user click the search button before
     * @return Document
     */
    Document changeIP(String url, boolean hasSearchBefore, boolean comesFromThread) {
        ChangeProxy changeProxy = new ChangeProxy(guiLabels, this);
        return changeProxy.getProxy(url, hasSearchBefore, comesFromThread);
    }

    /**
     * Downloads the number of pdf requested
     *
     * @param limit        max number of PDFs to download
     * @param typeOfSearch
     * @throws Exception Problem downloading or reading a file
     */
    int getPDFs(int limit, String citingPapersURL, boolean isMultipleSearch, PDFDownloader pdfDownloader, String
            typeOfSearch) throws
            Exception {
        Long currThreadID = Thread.currentThread().getId();
        //Keeps track of the number of searches done
        int numberOfSearches = 0;
        if (!isMultipleSearch) {
            guiLabels.setOutput("Downloading...");
        }
        int pdfCounter = 0;
        //Go though all the search result links
        Map.Entry<ArrayList<String> , Integer> entry = getAllLinks(citingPapersURL, typeOfSearch, isMultipleSearch);
        ArrayList<String> list = entry.getKey();
        int numOfNonGoogleURL = entry.getValue();
        int counterOfLinks = 0;

        if (list.isEmpty()) {
            throw new IllegalArgumentException("Please search of an author before downloading");
        }
        //isFirst is true if it is the first URL of the list
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
            int timeToWait = getTimeToWait();
            guiLabels.setConnectionOutput("Waiting " + timeToWait + " seconds before going to the search results");
            if (!isMultipleSearch) {
                guiLabels.setOutput("Waiting " + timeToWait + " seconds before going to the search results");
            } else {
                simultaneousDownloadsGUI.updateStatus("Waiting " + timeToWait + " s");
            }
            Thread.sleep(timeToWait * 1000);
            if (isMultipleSearch) {
                simultaneousDownloadsGUI.updateStatus("Downloading...");
            }

            //Increase counter for every new google link
            Document citingPapers;
            try {
                if (getNumberOfRequestFromMap(currUrl, mapThreadIdToProxy.get(currThreadID)) >= 50) {
                    guiLabels.setConnectionOutput("Wait... Changing proxy from thread " + currThreadID + " because of" +
                            " amount of requests...");
                    citingPapers = changeIP(currUrl, false, false);
                } else {
                    citingPapers = changeIP(currUrl, true, false);
                }
            } catch(IllegalArgumentException e) {
                continue;
            }

            if (citingPapers == null) {
                continue;
            }

            //Verify that Google has not flagged the proxy
            if (citingPapers.text().contains("Sorry, we can't verify that you're not a robot")) {
                //In case you been flagged as a bot even before searching
                guiLabels.setConnectionOutput("Google flagged thread " + currThreadID + " proxy as a bot." +
                        "\nChanging to a different one");
                citingPapers = changeIP(currUrl, false, false);
                if (citingPapers == null) {
                    continue;
                }
            }

            //Add request to current website
            try {
                String baseURL = addRequestToMapOfRequests(currUrl, mapThreadIdToProxy.get(currThreadID));
                //Display
                guiLabels.setConnectionOutput("Number of reqs to " + baseURL + " from proxy " + mapThreadIdToProxy.get
                        (currThreadID).getProxy() + " = " + getNumberOfRequestFromMap(currUrl, mapThreadIdToProxy.get
                        (currThreadID)));
            } catch (IllegalArgumentException e) {
                continue;
            }
            numberOfSearches++;

            if (numberOfSearches > 2 && isMultipleSearch) {
                simultaneousDownloadsGUI.updateStatus("No PDF found (" + numberOfSearches + " attempt(s))");
            }

            Pattern gScholarSearchResult = Pattern.compile("(<div class=\"gs_r\">)([^∞])+?(?=(<div " +
                    "class=\"gs_r\">)|(<div id=\"gs_ccl_bottom\">))");
            Matcher gScholarSRMatcher = gScholarSearchResult.matcher(citingPapers.html());
            //If the url is aprt of google scholar, and the search result is empty, or  10 google searches are made and
            // no valid result is found, stop.
            if ((counterOfLinks > numOfNonGoogleURL && !gScholarSRMatcher.find()) || numberOfSearches == 10) {
                guiLabels.setConnectionOutput("No more papers found.");
                if (!isMultipleSearch) {
                    guiLabels.setOutput("No more papers found.");
                    guiLabels.setLoadBar(limit / (double) limit);
                } else {
                    simultaneousDownloadsGUI.updateStatus("No more papers found");
                }
                break;
            }

            if (!isMultipleSearch) {
                guiLabels.setOutput("Downloading...");
            }


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
                if (absLink.contains("authguide") || absLink.contains("masthead") || absLink.contains("/pb-assets/documents") ) {
                    continue;
                }
                if (text.contains("PDF")) {
                    int attempt = 0;
                    //Try to download the doc using a proxy. If it returns error 403, 429, or the proxy is unable to
                    //connect, use the proxy that is currently at the top of the queue, without removing it.
                    while (attempt < 2) {
                        pdfCounter++;
                        atomicCounter.increment();
                        File file = null;
                        try {

                            Proxy proxyToUSe = mapThreadIdToProxy.get(currThreadID);
                            if (attempt > 0) {
                                proxyToUSe = queueOfConnections.peek();
                            }

                            pdfDownloader.setCrawler(this);
                            pdfDownloader.downloadPDF(absLink, pdfCounter, guiLabels, proxyToUSe, speedUp);
                            file = new File("./DownloadedPDFs/" + pdfDownloader.getPath() + "/" + pdfCounter
                                    + ".pdf");
                            if (file.length() == 0 || !file.canRead()) {
                                throw new IOException("File is invalid");
                            }

                            PDFVerifier pdfVerifier = new PDFVerifier(file);
                            ExecutorService executorService3 = Executors.newSingleThreadExecutor(new MyThreadFactory());
                            Future<String> future = executorService3.submit(pdfVerifier);
                            String result = "";
                            try {
                                result = future.get(15 * 1000, TimeUnit.MILLISECONDS);
                            } catch (Exception e) {
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
                            atomicCounter.decrease();
                            attempt++;

                            //If it is NOT any of these three errors, then do not try to re-download it
                            if (e2.getMessage() == null  || !e2.getMessage().contains("Error 403") &&
                                    !e2.getMessage().contains("response code: 429") &&
                                    !e2.getMessage().contains("Unable to tunnel through proxy.")) {
                                System.out.println("Error: " + e2.getMessage());
                                if (file != null) {
                                    //If the file was created, delete it
                                    //noinspection ResultOfMethodCallIgnored
                                    file.delete();
                                }
                                break;
                            }

                        }
                    }


                    if (!isMultipleSearch) {
                        guiLabels.setNumberOfPDF(typeOfSearch + "," + pdfCounter + "/" + limit);
                        guiLabels.setLoadBar(pdfCounter / (double) limit);
                    } else {
                        simultaneousDownloadsGUI.updateStatus(pdfCounter + "/" + limit);
                        simultaneousDownloadsGUI.updateProgressBar(0.3 + (pdfCounter / (double) limit) * 0.7);

                        guiLabels.setNumberOfPDFsMultiple(typeOfSearch+","+atomicCounter.value());

                    }
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
        return pdfCounter;
    }


    /**
     * Gets a random proxy.
     *
     * @return Proxy
     */
    synchronized Proxy addConnection() {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ignored) {
        }
        //Makes sure that the a connection is only modified by one thread at a time to avoid race conditions
        int randomIndex = new Random().nextInt(listOfProxiesGathered.size());
        Proxy curr = listOfProxiesGathered.get(randomIndex);
        //If it is still in the set,  it has not yet been used.
        while (!setOfProxyGathered.contains(curr)) {
            boolean valid = false;
            while (!valid) {
                try {
                    randomIndex = new Random().nextInt(listOfProxiesGathered.size());
                    valid = true;
                    curr = listOfProxiesGathered.get(randomIndex);
                } catch (IllegalArgumentException e) {
                    valid = false;
                    try {
                        Thread.sleep(10*1000);
                    } catch (InterruptedException ignored) {
                    }
                }
            }
        }
        //Remove from set
        setOfProxyGathered.remove(curr);
        //Remove from list
        listOfProxiesGathered.remove(randomIndex);
        guiLabels.setConnectionOutput("Number of Proxies available: " + setOfProxyGathered.size());
        while (setOfProxyGathered.size() < 50) {
            try {
                Thread.sleep(10*1000);
            } catch (InterruptedException e) {
            }
        }


        return curr;
    }

    String addRequestToMapOfRequests(String url, Proxy proxy) throws IllegalArgumentException {
        //Get base url
        Pattern pattern = Pattern.compile("^.+?[^/:](?=[?/]|$)");
        Matcher matcher = pattern.matcher(url);
        if (!matcher.find()) {
            throw new IllegalArgumentException("There was a problem finding base URL");
        }
        String baseURL = matcher.group();
        //See if it exist in the map
        if (mapWebsiteToReqCountFromProxy.get(baseURL) == null) {
           //If it does not, create a new map and add a new entry
            Map<Proxy,Integer> map = new HashMap<>();
            map.put(proxy, 1);
            mapWebsiteToReqCountFromProxy.put(baseURL, map);
        }
        else {
            Map<Proxy, Integer> map = mapWebsiteToReqCountFromProxy.get(baseURL);
            map.merge(proxy, 1, (a, b) -> a + b);
            mapWebsiteToReqCountFromProxy.put(baseURL, map);
        }
        return baseURL;
    }

    int getNumberOfRequestFromMap(String url, Proxy proxy) {
        //Get base url
        Pattern pattern = Pattern.compile("^.+?[^/:](?=[?/]|$)");
        Matcher matcher = pattern.matcher(url);
        if (!matcher.find()) {
            throw new IllegalArgumentException("There was a problem finding base URL");
        }
        String baseURL = matcher.group();
        if (mapWebsiteToReqCountFromProxy.get(baseURL) == null) {
            return 0;
        }
        else {
            if (mapWebsiteToReqCountFromProxy.get(baseURL).get(proxy) == null) {
                return 0;
            }
            else return mapWebsiteToReqCountFromProxy.get(baseURL).get(proxy);

        }
    }

    /**
     * Increases the download speed by not using proxies to download files
     *
     * @param speedUp true if the user wants to increase the speed. False otherwise.
     */
    void increaseSpeed(boolean speedUp) {
        this.speedUp = speedUp;
    }

    /**
     * Resets the counter of PDFs
     */
    void resetCounter() {
        atomicCounter.reset();
    }

    void addToSetOfAllProxiesEver(Proxy proxy) {
        setOfAllProxiesEver.add(proxy);
    }

    /**
     * Generates a random time to wait before performing a task (14-21 seconds)
     *
     * @return int that represents seconds
     */
    private int getTimeToWait() {
        if (listOfTimes == null) {
            listOfTimes = new Integer[10];
            for (int i = 0; i < listOfTimes.length; i++) {
                listOfTimes[i] = i + 14;
            }
        }
        int rnd = new Random().nextInt(listOfTimes.length);
        return listOfTimes[rnd];
    }


    ConcurrentLinkedQueue<Proxy> getQueueOfConnections() {
        return queueOfConnections;
    }

    Set<Proxy> getSetOfAllProxiesEver() {
        return setOfAllProxiesEver;
    }

    HashMap<String, String[]> getSearchResultToLink() {
        return searchResultToLink;
    }

    Set<Proxy> getSetOfProxyGathered() {
        return setOfProxyGathered;
    }

    List<Proxy> getListOfWorkingProxies() {
        return listOfWorkingProxies;
    }

    ExecutorService getExecutorService() {
        return executorService;
    }

    void setListOfProxiesGathered(Proxy proxy) {
        listOfProxiesGathered.add(proxy);
    }

    void setSetOfProxyGathered(Proxy proxy) {
        setOfProxyGathered.add(proxy);
    }

    void setGUI(SimultaneousDownloadsGUI simultaneousDownloadsGUI) {
        this.simultaneousDownloadsGUI = simultaneousDownloadsGUI;
    }

    /**
     * Class to verify if a given PDF is not corrupted
     */
    class PDFVerifier implements Callable<String> {

        private final File file;

        PDFVerifier(File file) {
            this.file = file;
        }

        @Override
        public String call() throws Exception {
            String result = "";
            try {
                PDFParser parser = new PDFParser(new RandomAccessBufferedFileInputStream(file));
                parser.parse();
                COSDocument cosDoc = parser.getDocument();
                cosDoc.close();
            } catch (Exception e) {
                result = "Invalid File";
            }
            return result;
        }
    }

    boolean isThereConnection() {
        return thereIsConnection;
    }


}

