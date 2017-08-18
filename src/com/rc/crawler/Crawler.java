package com.rc.crawler;

import javafx.application.Platform;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.openqa.selenium.Cookie;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by rafaelcastro on 5/31/17.
 * Crawler to navigate through different websites, search for academic papers and download them.
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
    private boolean threadIsGettingMoreProxies = false;
    private EmailSender emailSender;
    //Holds the different objects that are active when the crawler can parse javacript
    private JavascriptEnabledCrawler javaScriptEnabledCrawler = null;


    /**
     * Constructor. Initializes the Crawler GUI and verifies if there is internet connection.
     */
    Crawler(GUILabelManagement guiLabels) {
        this.guiLabels = guiLabels;
        //Start getting the list of all proxies. Load progress bar up to 75%
        guiLabels.setLoadBar(0);
        guiLabels.setOutput("Initializing...");
        guiLabels.setOutputMultiple("Initializing...");
        //Check if there is an internet connection every 2  seconds while the crawler is active
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

        Platform.runLater(() -> {
            try {

                emailSender = new EmailSender(guiLabels);
                emailSender.displayPasswordDialog();
            } catch (Exception e) {
                guiLabels.setAlertPopUp(e.getMessage());
            }
        });

        //Download selenium
        ProxyChanger pr = new ProxyChanger(guiLabels, this);
        //Check if its active
        boolean isSeleniumActive = pr.setUpSelenium();
        this.javaScriptEnabledCrawler = new JavascriptEnabledCrawler();
        javaScriptEnabledCrawler.setSeleniumActive(isSeleniumActive);
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

        try {
            this.proxiesDownloader = new ProxiesDownloader();
        } catch (Error | Exception e) {
            e.printStackTrace(System.out);
        }

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
        if (setOfAllProxiesEver.size() > 1500) {
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
    String[] searchForArticle(String keyword, boolean hasSearchBefore, boolean isMultipleSearch, String type) throws
            IllegalArgumentException {
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
                        (currThread).getProxy() + " = " + getNumberOfRequestFromMap(url, mapThreadIdToProxy.get
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
    Map.Entry<ArrayList<String>, Integer> getAllLinks(String citingPapersURL, String typeOfSearch, boolean
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
                    for (int i = 1; i < 4; i++) {
                        if (!list.contains(array[i])) {
                            if (array[i].contains("scholar")) {
                                MultipleSearchResultsFinder finder = new MultipleSearchResultsFinder(this);
                                if (!finder.verifyIfMultipleSearchResult(array[i], isMultipleSearch).isEmpty()) {
                                    list.add(array[i]);
                                    counter++;
                                }
                            } else {
                                list.add(array[i]);
                                counter++;
                            }
                        }
                    }

                } catch (Exception ignored) {
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
        ProxyChanger proxyChanger = new ProxyChanger(guiLabels, this);
        return proxyChanger.getProxy(url, hasSearchBefore, comesFromThread);
    }

    /**
     * Downloads the number of pdf requested
     *
     * @param limit        max number of PDFs to download
     * @param typeOfSearch type of search performed
     * @throws Exception Problem downloading or reading a file
     */
    Object[] getPDFs(int limit, String citingPapersURL, boolean isMultipleSearch, PDFDownloader pdfDownloader, String
            typeOfSearch) throws Exception {
        DownloadLinkFinder finder = new DownloadLinkFinder(guiLabels, this, simultaneousDownloadsGUI);
        return finder.getPDFS(citingPapersURL, isMultipleSearch, pdfDownloader, typeOfSearch, limit);
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
                        Thread.sleep(10 * 1000);
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
                Thread.sleep(10 * 1000);
            } catch (InterruptedException ignored) {
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
            Map<Proxy, Integer> map = new HashMap<>();
            map.put(proxy, 1);
            mapWebsiteToReqCountFromProxy.put(baseURL, map);
        } else {
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
        } else {
            if (mapWebsiteToReqCountFromProxy.get(baseURL).get(proxy) == null) {
                return 0;
            } else return mapWebsiteToReqCountFromProxy.get(baseURL).get(proxy);

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
    int getTimeToWait() {
        if (listOfTimes == null) {
            listOfTimes = new Integer[10];
            for (int i = 0; i < listOfTimes.length; i++) {
                listOfTimes[i] = i + 14;
            }
        }
        int rnd = new Random().nextInt(listOfTimes.length);
        return listOfTimes[rnd];
    }

    /**
     * Add a proxy once it has been unlocked by a user
     *
     * @param p Proxy
     * @param cookies Set of cookies of the webdrive that unlocked the proxy
     */
    void addUnlockedProxy(Proxy p, Set<Cookie> cookies) {
        if (javaScriptEnabledCrawler.isSeleniumActive()) {
            //Add it back to the queue
            javaScriptEnabledCrawler.getQueueOfUnlockedProxies().add(p);
            javaScriptEnabledCrawler.getMapProxyToCookie().put(p, cookies);

            //Store it locally
            Logger logger = Logger.getInstance();
            File file = new File("./AppData/Cookies.dta");
            try {
                logger.setCookieFile(file.exists());
                logger.writeToCookiesFile(cookies, p);
            } catch (IOException e) {
                guiLabels.setAlertPopUp("There was a problem writing to Cookies.dta.\n"+e.getMessage());
            }

        }

    }

    /**
     * Sends a list of blocked proxies via email
     */
    synchronized void sendBlockedProxies() {
        //Todo: chnage to 10
        if (javaScriptEnabledCrawler.getBlockedProxies().size() >= 50) {
            Platform.runLater(() -> {
                //Check if email sender is active first
                if (emailSender.getIsActive()) {
                    String message = "The following proxies have been blocked by Google, please unlock them.\n" +
                            "To do so, connect to the proxy using your browser, navigate to scholar.goole.com,\n" +
                            "search for something and solve the captcha. Once the captcha is solved, try doing\n" +
                            "a normal search and make sure everything works.\n" +
                            "Ex: https://customers.trustedproxies.com/knowledgebase.php?action=displayarticle&id=10\n" +
                            "Proxy,Port\n";
                    StringBuilder sb = new StringBuilder();
                    sb.append(message);
                    while (!javaScriptEnabledCrawler.getBlockedProxies().isEmpty()) {
                        Proxy curr = javaScriptEnabledCrawler.getBlockedProxies().poll();
                        sb.append(curr.getProxy()).append(",").append(curr.getPort()).append("\n");
                    }
                    emailSender.send("Proxies blocked by Google", sb.toString());
                    //Todo: Add them back
                }
            });
        }

    }

    Map<Long, Proxy> getMapThreadIdToProxy() {
        return mapThreadIdToProxy;
    }

    ConcurrentLinkedQueue<Proxy> getQueueOfConnections() {
        return queueOfConnections;
    }

    Set<Proxy> getSetOfAllProxiesEver() {
        return setOfAllProxiesEver ;
    }

    HashMap<String, String[]> getSearchResultToLink() {
        return searchResultToLink;
    }

    Set<Proxy> getSetOfProxyGathered() {
        return setOfProxyGathered;
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

    void setThreadIsGettingMoreProxies(boolean threadIsGettingMoreProxies) {
        this.threadIsGettingMoreProxies = threadIsGettingMoreProxies;
    }

    boolean isThreadGettingMoreProxies() {
        return threadIsGettingMoreProxies;
    }

    boolean isThereConnection() {
        return thereIsConnection;
    }

    boolean getSpeedUp() {
        return speedUp;
    }

    AtomicCounter getAtomicCounter() {
        return atomicCounter;
    }

    Queue<Proxy> getQueueOfBlockedProxies() {
        return javaScriptEnabledCrawler.getBlockedProxies();
    }

    //Map<Proxy, Boolean> getMapProxyToSelenium() {return javaScriptEnabledCrawler.getMapProxyToSelenium();
   // }

    ConcurrentLinkedQueue<Proxy> getQueueOfUnlockedProxies() {return javaScriptEnabledCrawler.getQueueOfUnlockedProxies();
    }

    Map<Proxy, Set<Cookie>> getMapProxyToCookie() {return javaScriptEnabledCrawler.getMapProxyToCookie();
    }
    boolean isSeleniumActive() {return javaScriptEnabledCrawler.isSeleniumActive();
    }
}



