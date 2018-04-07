package com.rc.crawler;import javafx.application.Platform;import org.joda.time.DateTime;import org.jsoup.nodes.Document;import org.jsoup.select.Elements;import org.openqa.selenium.Cookie;import java.io.*;import java.net.HttpURLConnection;import java.net.URL;import java.sql.SQLException;import java.util.*;import java.util.List;import java.util.concurrent.*;import java.util.regex.Matcher;import java.util.regex.Pattern;/** * Created by rafaelcastro on 5/31/17. * Crawler to navigate through different websites, search for academic papers and download them. */class Crawler {    private final GUILabelManagement guiLabels;    private SimultaneousDownloadsGUI simultaneousDownloadsGUI;    //Counts number of requests    private Integer[] listOfTimes;    private Map<Long, Proxy> mapThreadIdToProxy = Collections.synchronizedMap(new HashMap<Long, Proxy>());    //Keeps track of all the proxies that have been downloaded, since we are picking a random element, it is faster to    //use a list to get it    private List<Proxy> listOfProxiesGathered;    //Keeps track of all proxies downloaded, used for contains() and remove(). Once a proxy has been used, it is removed    // from the set    private Set<Proxy> setOfProxyGathered;    //Sets of all proxies ever downloaded, so that when we download new proxies while running the program, we only add    // new proxies to the setOfProxyGathered    private Set<Proxy> setOfAllProxiesEver;    //Random chosen time to wait before searching    private ExecutorService executorService;    private ConcurrentLinkedQueue<Proxy> queueOfConnections = new ConcurrentLinkedQueue<>();    private ProxiesDownloader proxiesDownloader;    //Stores proxies tht have over 50 requests, but work, to use them later    private List<Proxy> listOfWorkingProxies;    //Maps thread id to the number of request for the given proxy being used in that thread    private Map<String, Map<Proxy, Integer>> mapWebsiteToReqCountFromProxy = Collections.synchronizedMap(new            HashMap<String, Map<Proxy, Integer>>());    //Maps the different search results to their respective "cited by" URL    private HashMap<String, String[]> searchResultToLink = new HashMap<>();    private boolean speedUp;    private AtomicCounter atomicCounter = new AtomicCounter();    private boolean thereIsConnection = true;    private boolean threadIsGettingMoreProxies = false;    private EmailSender emailSender;    //Holds the different objects that are active when the crawler can parse javacript    private JavascriptEnabledCrawler javaScriptEnabledCrawler = null;    private StatsGUI stats;    /**     * Constructor. Initializes the Crawler GUI and verifies if there is internet connection.     */    Crawler(GUILabelManagement guiLabels, StatsGUI stats) {        this.guiLabels = guiLabels;        this.stats = stats;        //Start getting the list of all proxies. Load progress bar up to 75%        guiLabels.setLoadBar(0);        guiLabels.setOutput("Initializing...");        guiLabels.setOutputMultiple("Initializing...");        //Check if there is an internet connection every 2  seconds while the crawler is active, and check if the        // crawler needs to close        ExecutorService connectionVerifier = Executors.newSingleThreadExecutor(new MyThreadFactory());        connectionVerifier.submit((Runnable) () -> {            boolean lostConnection = false;            //noinspection InfiniteLoopStatement            DateTime now = new DateTime();            while (true) {                try {                    URL url = new URL("http://www.google.com");                    HttpURLConnection con = (HttpURLConnection) url                            .openConnection();                    con.connect();                    if (con.getResponseCode() == 200) {                        thereIsConnection = true;                        if (lostConnection) {                            guiLabels.setOutput("Online!");                            guiLabels.setOutputMultiple("Online!");                            //Reconnect to database                            OutsideServer.getInstance(guiLabels).reconnect();                            lostConnection = false;                        }                        //Check if the current instance should perform any operation every  minute                        DateTime d2 = new DateTime();                        if (d2.getMillis() - now.getMillis() > 1 * 60 * 1000) {                            now = d2;                            WebServer.getInstance(guiLabels).checkForOperations();                        }                    }                } catch (Exception exception) {                    thereIsConnection = false;                    lostConnection = true;                }                try {                    Thread.sleep(2000);                } catch (InterruptedException e) {                    e.printStackTrace();                }            }        });    }    /**     * Loads the crawler. Calls the different methods to download n proxies, and at least 1 working connection, before     * the user can use the program.     */    void loadCrawler(SearchEngine.SupportedSearchEngine engine) {        try {            //Retrieve n proxies (n = 600) before starting the program.            guiLabels.setConnectionOutput("Directory " + getClass().getProtectionDomain().getCodeSource().getLocation                    ());            Platform.runLater(() -> {                try {                    emailSender = new EmailSender(guiLabels);                    //emailSender.displayPasswordDialog();                } catch (Exception e) {                    guiLabels.setAlertPopUp(e.getMessage());                }            });            //Configure web drivers            WebDrivers driversConfig = new WebDrivers(guiLabels);            boolean isSeleniumActive = driversConfig.setUpSelenium();            Thread.sleep(10*1000);            if (!isSeleniumActive) WebServer.getInstance(guiLabels).close(false);            this.javaScriptEnabledCrawler = new JavascriptEnabledCrawler(stats, guiLabels);            javaScriptEnabledCrawler.setSeleniumActive(isSeleniumActive);            getProxies();            guiLabels.setOutput("Establishing connections...");            guiLabels.setOutputMultiple("Establishing connections...");            //Try to connect to n proxies            startConnectionThreads(engine);        } catch (Exception e) {            e.printStackTrace();            guiLabels.setAlertPopUp(e.getMessage());        }    }    /**     * Start threads to try to get an initial connection.     * Ideally, the program will have 10 active working proxies at all times.     */    private void startConnectionThreads(SearchEngine.SupportedSearchEngine engine) {        executorService = Executors.newFixedThreadPool(21, new MyThreadFactory());        if (setOfProxyGathered.size() < 900) {            //Add 1 request to get more proxies            Request getMoreProxiesRequest = new Request("getProxies", this, guiLabels, engine);            executorService.submit(getMoreProxiesRequest);        }        List<Callable<Proxy>> listOfRequests = new ArrayList<>();        //Add 10 requests        for (int i = 0; i < 10; i++) {            listOfRequests.add(new Request("getConnection", this, guiLabels, engine));        }        for (Callable<Proxy> request : listOfRequests) {            //Add the result to the list of connections. All the elements here have active connections            executorService.submit(request);        }    }    /**     * Downloads proxies from different websites, without duplicates. If proxies have been downloaded before, within a     * 24h window, it loads those proxies instead.     */    private void getProxies() {        File dir = new File("AppData");        //noinspection ResultOfMethodCallIgnored        dir.mkdir();        int proxyCounter = 0;        listOfProxiesGathered = Collections.synchronizedList(new ArrayList<Proxy>());        //Sets don't allow repetition so we avoid duplicates        setOfProxyGathered = Collections.synchronizedSet(new HashSet<Proxy>());        setOfAllProxiesEver = Collections.synchronizedSet(new HashSet<Proxy>());        listOfWorkingProxies = Collections.synchronizedList(new ArrayList<Proxy>());        try {            this.proxiesDownloader = new ProxiesDownloader(guiLabels);        } catch (Error | Exception ignored) {        }        guiLabels.setOutput("Looking for proxies..");        //Check first if there have been proxies downloaded        proxiesDownloader.getProxiesFromWebsite(550, 0,                    this, false, stats);    }    /**     * Gets more unique proxies.     * @param engine     */    void getMoreProxies(SearchEngine.SupportedSearchEngine engine) {        if (setOfAllProxiesEver.size() > 1500) {            //delete all the proxies and start all over            listOfProxiesGathered = Collections.synchronizedList(new ArrayList<Proxy>());            setOfProxyGathered = Collections.synchronizedSet(new HashSet<Proxy>());            setOfAllProxiesEver = Collections.synchronizedSet(new HashSet<Proxy>());            listOfWorkingProxies = Collections.synchronizedList(new ArrayList<Proxy>());            proxiesDownloader.getProxiesFromWebsite(1000, 0, this, false, stats);            mapWebsiteToReqCountFromProxy = new HashMap<>();        } else if (listOfWorkingProxies.size() < 20) {            proxiesDownloader.getProxiesFromWebsite(500, setOfProxyGathered.size(), this, true, stats);        } else {            //If there are proxies in this list, then we add them back to the sets so that they can re-use it            for (Proxy p : listOfWorkingProxies) {                addRequestToMapOfRequests(SearchEngine.getBaseURL(engine), p, 0);                listOfProxiesGathered.add(p);                setOfProxyGathered.add(p);            }        }    }    /**     * Search for an article in Google Scholar     *     * @param keyword          String with the title of the document     * @param hasSearchBefore  has the user press the button search before     * @param isMultipleSearch is the search being done in multiple article mode     * @param type             "searchForTheArticle" or "searchForCitedBy". Retrieves a different URL depending on if we     *                         are trying to     *                         download the paper itself or the papers that cite the paper     * @param engine           The search engine that is being used     * @return array with 2 elements. First element represents the numOfCitations, the second is the citingPapersURL     */    String[] searchForArticle(String keyword, boolean hasSearchBefore, boolean isMultipleSearch, String type,                              SearchEngine.SupportedSearchEngine engine) throws            IllegalArgumentException {        int invalidAttempts = 0;        //Replace space by + in the keyword as in the google search url        keyword = keyword.replace(" ", "+");        //Search google scholar or Microsoft Academic        String url = SearchEngine.getQuery(engine, keyword);        String numOfCitations = "";        String citingPapersURL = "";        String paperVersionsURL = "";        boolean found = false;        while (!found) {            if (invalidAttempts >= 2) {                //Program was unable to search for the query                if (!isMultipleSearch) {                    guiLabels.setSearchResultLabel(type + ",Could not find paper, please try writing more specific " +                            "information");                }                numOfCitations = "";                citingPapersURL = "";                paperVersionsURL = "";                found = true;            } else {                Document doc = changeIP(url, hasSearchBefore, false, engine, Optional.empty());                if (doc == null) {                    throw new IllegalArgumentException("Error searching for an article");                }                if (doc.text().contains("Sorry, we can't verify that you're not a robot")) {                    //In case you been flags as a bot even before searching                    guiLabels.setConnectionOutput(engine.name() + " flagged this proxy as a bot. Changing to a " +                            "different " +                            "one");                    doc = changeIP(url, false, false, engine, Optional.empty());                }                if (doc == null) {                    throw new IllegalArgumentException("Error searching for an article");                }                System.out.println("----------------------------------------------------------");                //System.out.println(doc.html());                //Check if doc contains "Did you mean to search for..." If so, redirect the corrected search                if (doc.html().contains("Did you mean to search for:")) {                    Pattern redirectPattern = Pattern.compile("Did you mean( to search for)?:.*<a href=\"[^\"]*");                    Matcher redirectMatcher = redirectPattern.matcher(doc.html());                    if (redirectMatcher.find()) {                        String correctedURL = redirectMatcher.group();                        correctedURL = correctedURL.replaceAll("Did you mean( to search for)?:.*<a href=\"", "");                        correctedURL = correctedURL.replaceAll("/scholar.*(q=)", "https://scholar.google" +                                ".com/scholar?hl=en&as_sdt=0,39&q=");                        url = correctedURL;                        doc = changeIP(url, hasSearchBefore, false, engine, Optional.empty());                    }                }                Long currThread = Thread.currentThread().getId();                //Increase request of current proxy                String baseURL = addRequestToMapOfRequests(url, mapThreadIdToProxy.get(currThread), -1);                //Display                guiLabels.setConnectionOutput("Number of reqs to " + baseURL + " from proxy " + mapThreadIdToProxy.get                        (currThread).getProxy() + " = " + getNumberOfRequestFromMap(url, mapThreadIdToProxy.get                        (currThread)));                Elements links = doc.select("a[href]");                SingleSearchResultFinder finder = new SingleSearchResultFinder(this, guiLabels,                        simultaneousDownloadsGUI,                        doc);                //Analyze the current search result                found = finder.findSingleSearchResult(links, type, url, isMultipleSearch, engine);                paperVersionsURL = finder.getPaperVersionsURL();                numOfCitations = finder.getText();                citingPapersURL = finder.getAbsLink();                //Check if there is more than 1 result for a given search                MultipleSearchResultsFinder finderMultiple = new MultipleSearchResultsFinder(doc, isMultipleSearch,                        guiLabels, type, this, engine);                if (finderMultiple.findMultipleSearchResult(links, searchResultToLink)) {                    numOfCitations = "There was more than 1 result found for your given query";                }                invalidAttempts++;                hasSearchBefore = true;            }        }        if (type.equals("searchForCitedBy")) {            return new String[]{numOfCitations, citingPapersURL};        } else {            return new String[]{numOfCitations, paperVersionsURL};        }    }    /**     * Change current IP, or continue using the last working one     *     * @param url             url that you are trying to connect     * @param hasSearchBefore has the user click the search button before     * @return Document     */    Document changeIP(String url, boolean hasSearchBefore, boolean comesFromThread, SearchEngine            .SupportedSearchEngine engine, Optional<Long> threadID) {        ProxyChanger proxyChanger = new ProxyChanger(guiLabels, this, engine, stats);        threadID.ifPresent(aLong -> proxyChanger.setThreadID(threadID.get()));        return proxyChanger.getProxy(url, hasSearchBefore, comesFromThread);    }    /**     * Downloads the number of pdf requested     *     * @param limit        max number of PDFs to download     * @param typeOfSearch type of search performed     * @throws Exception Problem downloading or reading a file     */    Object[] getPDFs(int limit, String citingPapersURL, boolean isMultipleSearch, PDFDownloader pdfDownloader, String            typeOfSearch, SearchEngine.SupportedSearchEngine engine) throws Exception {        DownloadLinkFinder finder = new DownloadLinkFinder(guiLabels, this, simultaneousDownloadsGUI, engine, stats);        return finder.getPDFs(citingPapersURL, isMultipleSearch, pdfDownloader, typeOfSearch, limit);    }    /**     * Gets a random proxy.     *     * @return Proxy     */    synchronized Proxy addConnection() {        //Sleep while there is a thread getting more proxies        while (InUseProxies.getInstance().isThreadGettingMoreProxies()) {            try {                Thread.sleep(10*1000);            } catch (InterruptedException ignored) {            }        }        //Makes sure that the a connection is only modified by one thread at a time to avoid race conditions        int randomIndex = new Random().nextInt(listOfProxiesGathered.size());        Proxy curr = listOfProxiesGathered.get(randomIndex);        //If it is still in the set,  it has not yet been used.        while (!setOfProxyGathered.contains(curr)) {            boolean valid = false;            while (!valid) {                try {                    randomIndex = new Random().nextInt(listOfProxiesGathered.size());                    valid = true;                    curr = listOfProxiesGathered.get(randomIndex);                } catch (IllegalArgumentException e) {                    valid = false;                    try {                        Thread.sleep(10 * 1000);                    } catch (InterruptedException ignored) {                    }                }            }        }        //Remove from set        setOfProxyGathered.remove(curr);        //Remove from list        listOfProxiesGathered.remove(randomIndex);        guiLabels.setConnectionOutput("Number of Proxies available: " + setOfProxyGathered.size());        while (setOfProxyGathered.size() < 50) {            try {                Thread.sleep(10 * 1000);            } catch (InterruptedException ignored) {            }        }        return curr;    }    /**     * Adds a request for a given proxy to a given url. -1 to add 1, 0 to reset, 50 to lock     */    String addRequestToMapOfRequests(String url, Proxy proxy, int numberOfRequests) throws IllegalArgumentException {        if (numberOfRequests < 0) {            numberOfRequests = 1;        }        //Get base url        Pattern pattern = Pattern.compile("^.+?[^/:](?=[?/]|$)");        Matcher matcher = pattern.matcher(url);        if (!matcher.find()) {            throw new IllegalArgumentException("There was a problem finding base URL");        }        String baseURL = matcher.group();        //See if it exist in the map        if (mapWebsiteToReqCountFromProxy.get(baseURL) == null) {            //If it does not, create a new map and add a new entry            Map<Proxy, Integer> map = new HashMap<>();            map.put(proxy, numberOfRequests);            mapWebsiteToReqCountFromProxy.put(baseURL, map);        } else {            //Get the current map of that url            Map<Proxy, Integer> map = mapWebsiteToReqCountFromProxy.get(baseURL);            //Check if  the proxy is already part of this map            if (!map.containsKey(proxy)) {                map.put(proxy, numberOfRequests);            } else {                //In case is 0, we directly put it                if (numberOfRequests == 0) {                    map.put(proxy, numberOfRequests);                } else {                    //If not we add                    map.put(proxy, map.get(proxy) + numberOfRequests);                }            }            mapWebsiteToReqCountFromProxy.put(baseURL, map);        }        return baseURL;    }    int getNumberOfRequestFromMap(String url, Proxy proxy) {        //Get base url        Pattern pattern = Pattern.compile("^.+?[^/:](?=[?/]|$)");        Matcher matcher = pattern.matcher(url);        if (!matcher.find()) {            throw new IllegalArgumentException("There was a problem finding base URL");        }        String baseURL = matcher.group();        if (mapWebsiteToReqCountFromProxy.get(baseURL) == null) {            return 0;        } else {            if (mapWebsiteToReqCountFromProxy.get(baseURL).get(proxy) == null) {                return 0;            } else return mapWebsiteToReqCountFromProxy.get(baseURL).get(proxy);        }    }    /**     * Increases the download speed by not using proxies to download files     *     * @param speedUp true if the user wants to increase the speed. False otherwise.     */    void increaseSpeed(boolean speedUp) {        this.speedUp = speedUp;    }    /**     * Resets the counter of PDFs     */    void resetCounter() {        atomicCounter.reset();    }    void addToSetOfAllProxiesEver(Proxy proxy) {        setOfAllProxiesEver.add(proxy);    }    /**     * Generates a random time to wait before performing a task (14-21 seconds)     *     * @return int that represents seconds     */    int getTimeToWait() {        if (listOfTimes == null) {            listOfTimes = new Integer[10];            for (int i = 0; i < listOfTimes.length; i++) {                listOfTimes[i] = i + 14;            }        }        int rnd = new Random().nextInt(listOfTimes.length);        return listOfTimes[rnd];    }    /**     * Add a proxy once it has been unlocked by a user     *  @param p       Proxy     * @param cookies Set of cookies of the webdrive that unlocked the proxy     */    void addUnlockedProxy(Proxy p, Set<Cookie> cookies, SearchEngine.SupportedSearchEngine engine, OutsideServer server,                          boolean wasManuallyUnlocked) {        if (javaScriptEnabledCrawler.isSeleniumActive()) {            //Add it back to the queue only if it was manually unlocked (using chrome)            if (wasManuallyUnlocked) {                javaScriptEnabledCrawler.getQueueOfUnlockedProxies().add(p);            }            javaScriptEnabledCrawler.addCookieToMap(p, engine, cookies);            //Reset the requests            addRequestToMapOfRequests(SearchEngine.getBaseURL(engine), p, 0);            //Store it locally            Logger logger = Logger.getInstance();            File file = new File("./AppData/Cookies.dta");            try {                logger.setCookieFile(file.exists());                logger.writeToCookiesFile(cookies, p, engine, server, stats);            } catch (IOException | SQLException e) {                guiLabels.setAlertPopUp("There was a problem writing the cookies.\n" + e.getMessage());            }            if (stats.getNumberOfBlockedProxies().get() > 0) {                stats.updateNumberOfBlockedProxies(stats.getNumberOfBlockedProxies().get() - 1);            }        }    }    /**     * Sends a list of blocked proxies via email     */    synchronized void sendBlockedProxies() {        //Todo: chnage to 10        if (javaScriptEnabledCrawler.getBlockedProxies().size() >= 50) {            Platform.runLater(() -> {                //Check if email sender is active first                if (emailSender.getIsActive()) {                    String message = "The following proxies have been blocked by Google, please unlock them.\n" +                            "To do so, connect to the proxy using your browser, navigate to scholar.goole.com,\n" +                            "search for something and solve the captcha. Once the captcha is solved, try doing\n" +                            "a normal search and make sure everything works.\n" +                            "Ex: https://customers.trustedproxies.com/knowledgebase.php?action=displayarticle&id=10\n" +                            "Proxy,Port\n";                    StringBuilder sb = new StringBuilder();                    sb.append(message);                    while (!javaScriptEnabledCrawler.getBlockedProxies().isEmpty()) {                        Proxy curr = javaScriptEnabledCrawler.getBlockedProxies().poll();                        sb.append(curr.getProxy()).append(",").append(curr.getPort()).append("\n");                    }                    emailSender.send("Proxies blocked by Google", sb.toString());                    //Todo: Add them back                }            });        }    }    Map<Long, Proxy> getMapThreadIdToProxy() {        return mapThreadIdToProxy;    }    ConcurrentLinkedQueue<Proxy> getQueueOfConnections() {        return queueOfConnections;    }    Set<Proxy> getSetOfAllProxiesEver() {        return setOfAllProxiesEver;    }    HashMap<String, String[]> getSearchResultToLink() {        return searchResultToLink;    }    Set<Proxy> getSetOfProxyGathered() {        return setOfProxyGathered;    }    ExecutorService getExecutorService() {        return executorService;    }    void setListOfProxiesGathered(Proxy proxy) {        listOfProxiesGathered.add(proxy);    }    void setSetOfProxyGathered(Proxy proxy) {        setOfProxyGathered.add(proxy);    }    void setGUI(SimultaneousDownloadsGUI simultaneousDownloadsGUI) {        this.simultaneousDownloadsGUI = simultaneousDownloadsGUI;    }    boolean isThereConnection() {        return thereIsConnection;    }    boolean getSpeedUp() {        return speedUp;    }    AtomicCounter getAtomicCounter() {        return atomicCounter;    }    Queue<Proxy> getQueueOfBlockedProxies() {        return javaScriptEnabledCrawler.getBlockedProxies();    }    ConcurrentLinkedQueue<Proxy> getQueueOfUnlockedProxies() {        return javaScriptEnabledCrawler.getQueueOfUnlockedProxies();    }    Set<Cookie> getCookie(Proxy proxy, SearchEngine.SupportedSearchEngine engine) {        return OutsideServer.getInstance(guiLabels).getCookies(proxy, engine);       // return javaScriptEnabledCrawler.getCookie(proxy, engine);    }    boolean isSeleniumActive() {        return javaScriptEnabledCrawler.isSeleniumActive();    }    /**     * Resets all the different lists and sets that track the use of the proxies     */    void resetProxyTracking() {        listOfProxiesGathered = Collections.synchronizedList(new ArrayList<Proxy>());        setOfProxyGathered = Collections.synchronizedSet(new HashSet<Proxy>());        setOfAllProxiesEver = Collections.synchronizedSet(new HashSet<Proxy>());        listOfWorkingProxies = Collections.synchronizedList(new ArrayList<Proxy>());        mapWebsiteToReqCountFromProxy = new HashMap<>();    }}