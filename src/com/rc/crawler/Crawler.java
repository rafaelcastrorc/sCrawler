package com.rc.crawler;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.*;
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
    private Map<Long, Integer> mapThreadIdToReqCount = Collections.synchronizedMap(new HashMap<Long, Integer>());
    //Maps the different search results to their respective "cited by" URL
    private HashMap<String, String[]> searchResultToLink = new HashMap<>();
    private boolean speedUp;

    /**
     * Constructor.
     */
    Crawler(GUILabelManagement guiLabels) {
        this.guiLabels = guiLabels;
        //Start getting the list of all proxies. Load progress bar up to 75%
        guiLabels.setLoadBar(0);
        guiLabels.setOutput("Initializing...");
        guiLabels.setOutputMultiple("Initializing...");

    }

    /**
     * Loads the crawler. Calls the different methods to download n proxies, and at least 1 working connection, before
     * the user can use the program.
     */
    void loadCrawler() {
        //Retrieve n proxies (n = 600) before starting the program.
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
            Request getMoreProxiesRequest = new Request(false, "getProxies");
            executorService.submit(getMoreProxiesRequest);
        }

        //Start a list of Future that will contain the available connections
        List<Future<Proxy>> listOfFuture = new ArrayList<>();
        List<Callable<Proxy>> listOfRequests = new ArrayList<>();
        //Add 10 requests
        for (int i = 0; i < 8; i++) {
            listOfRequests.add(new Request(false, "getConnection"));
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
            } catch (Exception ex) {
                guiLabels.setAlertPopUp(ex.toString());
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
                Proxy proxy = new Proxy(ipAndProxyString[0], Integer.valueOf(ipAndProxyString[1]));

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
    private void getMoreProxies() {
        if (listOfWorkingProxies.size() == 0) {
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
     * @param keyword         String with the title of the document
     * @param hasSearchBefore has the user press the button search before
     * @return array with 2 elements. First element represents the numOfCitations, the second is the citingPapersURL
     */
    String[] searchForArticle(String keyword, boolean hasSearchBefore, boolean isMultipleSearch) {
        int invalidAttempts = 0;
        //Replace space by + in the keyword as in the google search url
        keyword = keyword.replace(" ", "+");
        //Search google scholar
        String url = "https://scholar.google.com/scholar?hl=en&q=" + keyword;
        String numOfCitations = "";
        String citingPapersURL = "";

        boolean found = false;
        while (!found) {
            if (invalidAttempts >= 2) {
                if (!isMultipleSearch) {
                    guiLabels.setSearchResultLabel("Could not find paper, please try writing more specific information");
                }
                numOfCitations = "";
                citingPapersURL = "";
                found = true;
            } else {
                Document doc = changeIP(url, hasSearchBefore, false);
                if (doc.text().contains("Sorry, we can't verify that you're not a robot")) {
                    //In case you been flags as a bot even before searching
                    guiLabels.setConnectionOutput("Google flagged this proxy as a bot. Changing to a different one");
                    doc = changeIP(url, false, false);

                }
                Long currThread = Thread.currentThread().getId();
                mapThreadIdToReqCount.put(currThread, mapThreadIdToReqCount.get(currThread) + 1);
                guiLabels.setConnectionOutput("Number of requests from Thread " + currThread + ": " +
                        mapThreadIdToReqCount.get(currThread));


                String text = "";
                String absLink = "";
                Elements links = doc.select("a[href]");
                for (Element link : links) {
                    text = link.text();
                    absLink = link.attr("abs:href");

                    if (text.contains("Cited by")) {
                        found = true;
                        break;
                    }
                }

                numOfCitations = text;
                citingPapersURL = absLink;

                if (!doc.toString().contains("1 result") && !doc.toString().contains("Showing the best result for this" +
                        " search")) {
                    if (!isMultipleSearch) {
                        guiLabels.setSearchResultLabel("ERROR: There was more than 1 result found for your" +
                                " given query");
                    }
                    numOfCitations = "There was more than 1 result found for your given query";

                    boolean searchResultFound = false;
                    String searchResult = "";
                    for (Element link : links) {
                        text = link.text();
                        absLink = link.attr("abs:href");
                        Pattern pattern =
                                Pattern.compile("((www\\.)?scholar\\.google\\.com)|(www\\.(support\\.)?google\\.com)");
                        Matcher matcher = pattern.matcher(absLink);
                        if (!matcher.find()) {
                            text = link.text();
                            Pattern pattern2 = Pattern.compile("\\[HTML]|\\[PDF]");
                            Matcher matcher2 = pattern2.matcher(text);
                            if (!matcher2.find() && !text.equals("Provide feedback")) {
                                searchResult = text;
                                //Adds a search result to the search result window
                                guiLabels.setMultipleSearchResult(text);
                                searchResultFound = true;

                            }
                        } else if (searchResultFound) {
                            if (text.contains("Cited by")) {
                                searchResultToLink.put(searchResult, new String[]{absLink, text});
                                searchResultFound = false;

                            }

                        }
                    }
                }
                invalidAttempts++;
                hasSearchBefore = true;
            }
        }
        return new String[]{numOfCitations, citingPapersURL};

    }

    /**
     * Gets all the possible search results where the article is cited, based on a URL
     *
     * @return ArrayList with all the links
     */
    private ArrayList<String> getAllLinks(String citingPapersURL) {
        ArrayList<String> list = new ArrayList<>();
        Pattern pattern = Pattern.compile("=\\d*");
        Matcher matcher = pattern.matcher(citingPapersURL);
        String paperID = "";
        if (matcher.find()) {
            paperID = matcher.group();
            paperID = paperID.replace("=", "");
        }
        //Add 1-10 results
        list.add(citingPapersURL);
        for (int i = 10; i < 1000 + 1; i = i + 10) {
            String sb = "https://scholar.google.com/scholar?start=" + i +
                    "&hl=en&oe=ASCII&as_sdt=5,39&sciodt=0,39&cites=" + paperID + "&scipsc=";
            list.add(sb);

        }
        return list;
    }

    /**
     * Change current IP, or continue using the last working one
     *
     * @param url             url that you are trying to connect
     * @param hasSearchBefore has the user click the search button before
     * @return Document
     */
    private Document changeIP(String url, boolean hasSearchBefore, boolean comesFromThread) {
        long currThreadID = Thread.currentThread().getId();
        if (listOfProxiesGathered.isEmpty()) {
            //This happens if there is no internet connection
            Document d = null;
            try {
                d = Jsoup.connect(url).userAgent("Mozilla").get();


            } catch (IOException e) {
                guiLabels.setAlertPopUp("Could not connect, please check your internet connection");
                guiLabels.setOutput("No internet connection");
                guiLabels.setOutputMultiple("No internet connection");

            }
            return d;
        }
        if (setOfProxyGathered.size() < 50) {
            //If there are less than 50 proxies remaining, we add more.
            getMoreProxies();
        }

        if (hasSearchBefore && mapThreadIdToReqCount.get(currThreadID) <= 50) {
            //If has searched before and it worked, then use previous ip
            Proxy ipAndPort = mapThreadIdToProxy.get(currThreadID);
            try {
                return Jsoup.connect(url).proxy(ipAndPort.getProxy(), ipAndPort.getPort()).userAgent("Mozilla").get();
            } catch (IOException e2) {
                guiLabels.setConnectionOutput("There was a problem connecting to your previously used proxy.\nChanging" +
                        " to a different one");
                guiLabels.setConnectionOutput(e2.getMessage());

            }
        }

        //Connect to the next working IP if the number of request is >50 or the proxy no longer works
        if (queueOfConnections.size() > 0 && !comesFromThread) {

            if (mapThreadIdToReqCount.get(currThreadID) != null && mapThreadIdToReqCount.get(currThreadID) > 50) {
                //If we have used this proxy too many times, we save it for later
                guiLabels.setConnectionOutput("Proxy has more than 50 requests. Replacing it, and saving it to the " +
                        "list of working proxies.");
                listOfWorkingProxies.add(mapThreadIdToProxy.get(currThreadID));
            }
            boolean connected = false;
            Document doc = null;
            while (!connected) {
                //Get an ip from the working list
                Proxy proxyToUse = queueOfConnections.poll();
                System.out.println("Getting new IP " + currThreadID + " " + proxyToUse.getProxy() + " " +
                        proxyToUse.getPort());
                mapThreadIdToProxy.put(currThreadID, proxyToUse);
                //Since we are using a new proxy, we need to find a replacement
                Request request = new Request(true, "getConnection");
                executorService.submit(request);
                guiLabels.setNumberOfWorkingIPs("remove,none");

                try {
                    doc = Jsoup.connect(url).proxy(proxyToUse.getProxy(), proxyToUse.getPort()).userAgent("Mozilla").get();
                    guiLabels.setConnectionOutput("Successfully connected to proxy from queue.\nAdding a new thread to" +
                            " find a new connection");
                    if (doc.text().contains("Sorry, we can't verify that you're not a robot")) {
                        throw new IllegalArgumentException("Google flagged your IP as a bot. Changing to a different one");
                    }
                    connected = true;
                } catch (Exception e) {
                    guiLabels.setConnectionOutput("There was a problem connecting to one of the Proxies from the queue.");
                    guiLabels.setConnectionOutput(e.getMessage());
                }
            }
            mapThreadIdToReqCount.put(Thread.currentThread().getId(), 1);
            return doc;
        }
        //The only way to get to here is if it is one of the threads trying to find a new connection
        //Establish a new connection
        guiLabels.setConnectionOutput(String.valueOf("Number of requests Thread " + currThreadID + ": " +
                mapThreadIdToReqCount.get(currThreadID)));

        boolean connected = false;
        Document doc = null;
        boolean thereWasAnError = false;
        Proxy proxyToBeUsed;
        while (!connected) {
            proxyToBeUsed = addConnection();

            try {
                if (!thereWasAnError) {
                    guiLabels.setConnectionOutput("Connecting to Proxy...");
                }
                doc = Jsoup.connect(url).proxy(proxyToBeUsed.getProxy(), proxyToBeUsed.getPort()).userAgent("Mozilla").get();
                if (doc.text().contains("Sorry, we can't verify that you're not a robot")) {
                    throw new IllegalArgumentException();
                }
                connected = true;
                mapThreadIdToProxy.put(Thread.currentThread().getId(), proxyToBeUsed);

            } catch (Exception e) {
                thereWasAnError = true;
            }
        }
        return doc;
    }

    /**
     * Downloads the number of pdf requested
     *
     * @param limit max number of PDFs to download
     * @throws Exception Problem downloading or reading a file
     */
    int getPDFs(int limit, String citingPapersURL, boolean isMultipleSearch, PDFDownloader pdfDownloader) throws Exception {
        Long currThreadID = Thread.currentThread().getId();
        //Keeps track of the number of searches done
        int numberOfSearches = 0;
        if (!isMultipleSearch) {
            guiLabels.setOutput("Downloading...");
        }
        int pdfCounter = 0;
        //Go though all links
        ArrayList<String> list = getAllLinks(citingPapersURL);
        if (list.isEmpty()) {
            throw new IllegalArgumentException("Please search of an author before downloading");
        }
        for (String currUrl : list) {
            if (pdfCounter >= limit) {
                if (!isMultipleSearch) {
                    guiLabels.setOutput("All PDFs available have been downloaded");
                }
                break;
            }
            int timeToWait = getTimeToWait();
            guiLabels.setConnectionOutput("Waiting " + timeToWait + " seconds before going to the search results");
            if (!isMultipleSearch) {
                guiLabels.setOutput("Waiting " + timeToWait + " seconds before going to the search results");
            } else {
                simultaneousDownloadsGUI.updateStatus("Waiting " + timeToWait + " s");
            }
            Thread.sleep(timeToWait * 1000);

            //Increase counter for every new google link
            Document citingPapers;
            if (mapThreadIdToReqCount.get(currThreadID) >= 50) {
                guiLabels.setConnectionOutput("Wait... Changing proxy from thread " + currThreadID + " because of" +
                        " amount of requests...");
                citingPapers = changeIP(currUrl, false, false);
            } else {
                citingPapers = changeIP(currUrl, true, false);
            }

            if (citingPapers.text().contains("Sorry, we can't verify that you're not a robot")) {
                //In case you been flagged as a bot even before searching
                guiLabels.setConnectionOutput("Google flagged thread " + currThreadID + " proxy as a bot." +
                        "\nChanging to a different one");
                citingPapers = changeIP(currUrl, false, false);
            }
            mapThreadIdToReqCount.put(currThreadID, mapThreadIdToReqCount.get(currThreadID) + 1);
            numberOfSearches++;

            //If 15 searches are made and no valid result is found, stop
            if (numberOfSearches == 10) {
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
            guiLabels.setConnectionOutput(String.valueOf("Number of requests from Thread " + currThreadID + ": " +
                    mapThreadIdToReqCount.get(currThreadID)));
            Elements linksInsidePaper = citingPapers.select("a[href]");
            String text;
            String absLink;
            for (Element link : linksInsidePaper) {
                text = link.text();
                absLink = link.attr("abs:href");
                if (text.contains("PDF")) {
                    int attempt = 0;
                    //Try to download the doc using a proxy. If it returns error 403, 429, or the proxy is unable to
                    //connect, use the proxy that is currently at the top of the queue, without removing it.
                    while (attempt < 2) {
                        pdfCounter++;
                        try {
                            Proxy proxyToUSe = mapThreadIdToProxy.get(currThreadID);
                            if (attempt > 0) {
                                proxyToUSe = queueOfConnections.peek();
                            }
                            pdfDownloader.downloadPDF(absLink, pdfCounter, guiLabels, proxyToUSe, speedUp);
                            File file = new File("./DownloadedPDFs/" + pdfDownloader.getPath() + "/" + pdfCounter
                                    + ".pdf");
                            if (file.length() == 0 || !file.canRead()) {
                                throw new IOException("File is invalid");
                            }
                            numberOfSearches = 0;
                            break;

                        } catch (IOException e2) {
                            guiLabels.setConnectionOutput("This file could not be downloaded, skipping...");
                            pdfCounter--;
                            attempt++;
                            if (!e2.getMessage().contains("Error 403") && !e2.getMessage().contains("response code: 429") && !e2.getMessage().contains("Unable to tunnel through proxy.")) {
                                System.out.println("Error: " + e2.getMessage());
                                break;
                            }
                        }
                    }

                    if (!isMultipleSearch) {
                        guiLabels.setNumberOfPDF(pdfCounter + "/" + limit);
                        guiLabels.setLoadBar(pdfCounter / (double) limit);
                    } else {
                        simultaneousDownloadsGUI.updateStatus(pdfCounter + "/" + limit);
                        simultaneousDownloadsGUI.updateProgressBar(0.3 + (pdfCounter / (double) limit) * 0.7);
                    }
                    if (pdfCounter >= limit) {
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
    private synchronized Proxy addConnection() {
        //Makes sure that the a connection is only modified by one thread at a time to avoid race conditions
        int randomIndex = new Random().nextInt(listOfProxiesGathered.size());
        Proxy curr = listOfProxiesGathered.get(randomIndex);
        //If it is still in the set,  it has not yet been used.
        while (!setOfProxyGathered.contains(curr)) {
            randomIndex = new Random().nextInt(listOfProxiesGathered.size());
            curr = listOfProxiesGathered.get(randomIndex);
        }
        //Remove from set
        setOfProxyGathered.remove(curr);
        //Remove from list
        listOfProxiesGathered.remove(randomIndex);
        guiLabels.setConnectionOutput("Number of Proxies available: " + setOfProxyGathered.size());

        return curr;
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
                listOfTimes[i] = i + 10;
            }
        }
        int rnd = new Random().nextInt(listOfTimes.length);
        return listOfTimes[rnd];
    }


    void setListOfProxiesGathered(Proxy proxy) {
        listOfProxiesGathered.add(proxy);
    }

    void setSetOfProxyGathered(Proxy proxy) {
        setOfProxyGathered.add(proxy);
    }

    public void setGUI(SimultaneousDownloadsGUI simultaneousDownloadsGUI) {
        this.simultaneousDownloadsGUI = simultaneousDownloadsGUI;
    }

    /**
     * Increases the download speed by not using proxies to download files
     *
     * @param speedUp true if the user wants to increase the speed. False otherwise.
     */
    public void increaseSpeed(boolean speedUp) {
        this.speedUp = speedUp;
    }

    /**
     * Implements Callable. Assigns a task to a thread.
     * Find new working proxy and adds it to the queue.
     */
    class Request implements Callable<Proxy> {
        private final String type;
        private boolean atRuntime = false;

        Request(boolean atRuntime, String type) {
            this.atRuntime = atRuntime;
            this.type = type;

        }

        @Override
        public Proxy call() throws Exception {
            if (type.equals("getProxies")) {
                getMoreProxies();
                return null;
            } else if (type.equals("getConnection")) {
                guiLabels.setConnectionOutput("Thread " + Thread.currentThread().getId() + " is trying to connect");
                // Try to Establish connection
                boolean valid = false;
                while (!valid) {
                    try {
                        changeIP("https://scholar.google.com/scholar?hl=en&q=interesting+articles&btnG=&as_sdt=1%2C39&as_sdtp=", false, true);
                        valid = true;
                    } catch (Exception ignored) {
                    }
                }
                if (atRuntime) {
                    //If it is at runtime, add it to the queue from here
                    Proxy temp = mapThreadIdToProxy.get(Thread.currentThread().getId());
                    queueOfConnections.add(temp);
                    guiLabels.setNumberOfWorkingIPs("add," + temp.getProxy() + " Port: " + temp.getPort());
                }
                return mapThreadIdToProxy.get(Thread.currentThread().getId());
            }
            return null;
        }
    }

    void addToSetOfAllProxiesEver(Proxy proxy) {
        setOfAllProxiesEver.add(proxy);
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


}

