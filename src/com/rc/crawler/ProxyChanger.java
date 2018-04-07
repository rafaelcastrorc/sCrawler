package com.rc.crawler;

import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.phantomjs.PhantomJSDriver;
import org.openqa.selenium.phantomjs.PhantomJSDriverService;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.Wait;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by rafaelcastro on 7/7/17.
 * Handles the logic to connect to a website using proxies
 */
class ProxyChanger {
    private GUILabelManagement guiLabels;
    private Crawler crawler;
    private boolean isError404;
    private boolean thereWasAnErrorWithProxy = false;
    private static SearchEngine.SupportedSearchEngine engine;
    private Long threadID = null;
    private boolean isPageEmpty = false;
    private boolean comesFromDownload = false;
    private StatsGUI stats;
    private OutsideServer server;

    ProxyChanger(GUILabelManagement guiLabels, Crawler crawler, SearchEngine.SupportedSearchEngine engine, StatsGUI
            stats) {
        this.guiLabels = guiLabels;
        this.crawler = crawler;
        ProxyChanger.engine = engine;
        this.stats = stats;
        this.server = OutsideServer.getInstance(guiLabels);
    }

    /**
     * Verifies if there is internet connection
     */
    private void verifyIfThereIsConnection() {
        while (!crawler.isThereConnection()) {
            try {
                //Sleep for 10 seconds, try until connection is found
                Thread.sleep(10 * 1000);
            } catch (InterruptedException ignored) {
            }
        }
    }

    /**
     * Retrieves a Document after parsing a website.
     *
     * @param url             URL of the site that will be parsed
     * @param hasSearchBefore Has the current thread perform a previous search
     * @param comesFromThread Was this method called from a thread
     * @return Document
     */
    Document getProxy(String url, boolean hasSearchBefore, boolean comesFromThread) {
        Long currThreadID = threadID;
        if (currThreadID == null) {
            currThreadID = Thread.currentThread().getId();
        }

        //Step 1. Check if there is internet connection
        if (!crawler.isThereConnection()) {
            guiLabels.setAlertPopUp("Could not connect, please check your internet connection");
            guiLabels.setOutput("No internet connection");
            guiLabels.setOutputMultiple("No internet connection");
            verifyIfThereIsConnection();
        }

        //Step 2. Check if there are less than 100 proxies remaining, we add more
        //But first we check if we have connection. Also we make sure that there is only one thread getting more proxies
        if (crawler.getSetOfProxyGathered().size() < 100 && !InUseProxies.getInstance().isThreadGettingMoreProxies()) {
            //Verify again if there is internet connection
            if (!crawler.isThereConnection()) {
                guiLabels.setOutput("No internet connection");
                guiLabels.setOutputMultiple("No internet connection");
                verifyIfThereIsConnection();
            }
            //Verify again
            synchronized (new Object()) {
                if (crawler.getSetOfProxyGathered().size() < 100) {
                    if (!InUseProxies.getInstance().isThreadGettingMoreProxies()) {
                        InUseProxies.getInstance().setIsThreadGettingMoreProxies(true);
                        crawler.getMoreProxies(engine);
                        InUseProxies.getInstance().setIsThreadGettingMoreProxies(false);
                    }
                }
            }
        }


        //If the program searched before with this proxy and it worked, then use previous proxy. But first, verify
        // the amount of request send to the given page is less than 40 for the current proxy
        if (hasSearchBefore && crawler.getNumberOfRequestFromMap(url, crawler.getMapThreadIdToProxy().get
                (currThreadID)) <= 40) {
            Document doc = useProxyAgain(url, currThreadID);
            if (doc != null && !(doc.text().contains("Sorry, we can't verify that you're not a robot") && !doc.text()
                    .contains("your computer or network may be sending automated queries"))) {
                return doc;
            }
        }

        //Connect to the new working proxy from the queue of working proxies if the number of request is >50 or the
        // proxy that the thread is using no longer works
        if (!comesFromThread) {
            return connectToProxyFromQueue(currThreadID, url);
        } else {

            return establishNewConnection(url);

        }


    }

    /**
     * Finds a new working proxy
     *
     * @param url URL we are trying to connect to
     */
    private Document establishNewConnection(String url) {
        Document doc = null;
        Proxy proxyToBeUsed;

        boolean connected = false;
        boolean thereWasAnError = false;
        Set<Cookie> cookies;
        while (!connected) {
            proxyToBeUsed = null;
            if (!crawler.isThereConnection()) {
                guiLabels.setOutput("No internet connection");
                guiLabels.setOutputMultiple("No internet connection");
                verifyIfThereIsConnection();
            }
            //Sleep while there is a thread getting more proxies
            while (InUseProxies.getInstance().isThreadGettingMoreProxies()) {
                try {
                    Thread.sleep(10 * 1000);
                } catch (InterruptedException ignored) {
                }
            }
            //Check if it the proxy is not null, it is being used by less than 4 crawlers, it has less than 40
            // requests and there is not another thread that has already unlocked
            while (proxyToBeUsed == null || !server.canUseProxy(proxyToBeUsed) || crawler.getNumberOfRequestFromMap(url,
                    proxyToBeUsed) > 40 || server.isCurrentInstanceUsingProxy(proxyToBeUsed)) {

                //Try to use one of the unlocked proxies first
                if (crawler.isSeleniumActive() && crawler.getQueueOfUnlockedProxies().size() != 0) {
                    proxyToBeUsed = crawler.getQueueOfUnlockedProxies().poll();
                    cookies = crawler.getCookie(proxyToBeUsed, engine);
                    crawler.addRequestToMapOfRequests(SearchEngine.getBaseURL(engine), proxyToBeUsed, 0);
                } else {
                    //If there are no unlocked proxies, or the queue returned null, or it failed one of the
                    // above conditions, then try finding a new connection
                    proxyToBeUsed = crawler.addConnection();
                }
                //Check if there is not another thread already using the proxy
                try {
                    InUseProxies.getInstance().hasCrawlerConnectedToProxy(proxyToBeUsed, false);
                } catch (IllegalArgumentException e) {
                    proxyToBeUsed = null;
                }
            }

            try {
                InUseProxies.getInstance().hasCrawlerConnectedToProxy(proxyToBeUsed, false);
                if (server.isCurrentInstanceUsingProxy(proxyToBeUsed)) {
                    throw new IllegalArgumentException();
                }
                if (!thereWasAnError) {
                    guiLabels.setConnectionOutput("Connecting to Proxy...");
                }
                //If Selenium is enabled, get the Document using a webdriver
                if (crawler.isSeleniumActive()) {
                    //Check if it requires cookies
                    cookies = crawler.getCookie(proxyToBeUsed, engine);
                    doc = useSelenium(proxyToBeUsed, url, true, cookies, false);
                    //Verify again that no other thread is using it
                    InUseProxies.getInstance().hasCrawlerConnectedToProxy(proxyToBeUsed, true);
                    //If no error happens add it
                    server.addProxyToCurrentInstance(proxyToBeUsed);
                    connected = true;
                    crawler.getMapThreadIdToProxy().put(Thread.currentThread().getId(), proxyToBeUsed);
                } else {
                    doc = getDocUsingJavaNetClass(proxyToBeUsed, url);
                    //If no error happens add it
                    InUseProxies.getInstance().hasCrawlerConnectedToProxy(proxyToBeUsed, true);
                    //If no error happens add it
                    server.addProxyToCurrentInstance(proxyToBeUsed);
                    connected = true;
                    crawler.getMapThreadIdToProxy().put(Thread.currentThread().getId(), proxyToBeUsed);
                }
            } catch (HttpStatusException e) {
                //If this error happens, this proxy cannot be used
                if (e.getUrl().contains("ipv4.google.com/sorry")) {
                    throw new IllegalArgumentException();
                }
                e.printStackTrace();
            } catch (Exception e) {
                thereWasAnError = true;
            }
        }
        return doc;
    }

    /**
     * Connects to a working proxy from the queue of connections
     *
     * @param url          URL that the program is trying to connect to
     * @param currThreadID ID of the current thread
     * @return doc
     */
    private Document connectToProxyFromQueue(long currThreadID, String url) {

        //Check if the proxy has more than 40 connections, if so, replace it.
        if (crawler.getNumberOfRequestFromMap(url, crawler.getMapThreadIdToProxy().get(currThreadID)) > 40) {
            guiLabels.setConnectionOutput("Proxy has more than 40 requests");
        }
        int limit = 2;
        if (url.contains("scholar.google") || url.contains("academic.microsoft")) {
            limit = 3;
        }
        boolean connected = false;
        Document doc = null;
        int attempt = 0;
        while (!connected) {

            if (!thereWasAnErrorWithProxy && attempt > 0) {
                //Add it again to queue if it did not produce an actual error (non website related)
                crawler.getQueueOfConnections().add(crawler.getMapThreadIdToProxy().get(currThreadID));
            }
            thereWasAnErrorWithProxy = false;
            //Get an ip from the working list
            Proxy proxyToUse = crawler.getQueueOfConnections().poll();
            //If the proxy is null, or it has more than 40 request for the current website, then trying finding a new
            //one
            boolean first = true;


            while (proxyToUse == null || crawler.getNumberOfRequestFromMap(url, proxyToUse) > 40 ||
                    !server.canUseProxy(proxyToUse) ||
                    (InUseProxies.getInstance().isProxyInUseForSearching(proxyToUse) && crawler.getMapThreadIdToProxy
                            ().get
                            (currThreadID) != proxyToUse)) {

                //Since we are using a new proxy, we need to find a replacement as long as there are less than 20
                //proxies in the queue or we have gone through over 70% of the proxies in the queue and none works
                if (first && InUseProxies.getInstance().getCounterOfRequestsToGetNewProxies().value() < 8 && crawler
                        .getQueueOfConnections().size() < 12) {
                    Request request = new Request("getConnection", crawler, guiLabels, engine);
                    crawler.getExecutorService().submit(request);
                    guiLabels.setNumberOfWorkingIPs("remove,none");
                    first = false;
                }
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ignored) {
                }
                if (proxyToUse != null) {
                    //If the previous proxy was not null add it again
                    crawler.getQueueOfConnections().add(proxyToUse);
                }
                proxyToUse = crawler.getQueueOfConnections().poll();

            }

            //Map the current thread to the proxy
            crawler.getMapThreadIdToProxy().put(currThreadID, proxyToUse);
            //Mark the current proxy as used so that no other thread uses it
            InUseProxies.getInstance().addProxyUsedToSearch(proxyToUse);

            //Since we are using a new proxy, we need to find a replacement
            //If there are already 12 proxies in the queue, then don't add more
            System.out.println(InUseProxies.getInstance().getCounterOfRequestsToGetNewProxies().value());
            if (InUseProxies.getInstance().getCounterOfRequestsToGetNewProxies().value() < 8 && crawler
                    .getQueueOfConnections().size() < 12 && !isError404) {
                Request request = new Request("getConnection", crawler, guiLabels, engine);
                crawler.getExecutorService().submit(request);
                guiLabels.setNumberOfWorkingIPs("remove,none");
            }

            try {
                //Check if selenium is active
                if (crawler.isSeleniumActive()) {
                    //Check if the proxy requires cookies
                    Set<Cookie> cookies = crawler.getCookie(proxyToUse, engine);
                    doc = useSelenium(proxyToUse, url, true, cookies, true);
                } else {
                    doc = getDocUsingJavaNetClass(proxyToUse, url);
                }
                if (doc.text().contains("Sorry, we can't verify that you're not a robot") ||
                        doc.text().contains("your computer or network may be sending automated queries")) {
                    throw new IllegalArgumentException(engine.name() + " flagged your IP as a bot. Changing to a " +
                            "different " +
                            "one");

                }
                connected = true;
            } catch (HttpStatusException e) {
                e.printStackTrace();
                guiLabels.setConnectionOutput("There was a problem connecting to one of the Proxies from the " +
                        "queue.");
                if (attempt > limit) {
                    break;
                }
                attempt++;
                guiLabels.setConnectionOutput(e.getMessage());
            } catch (Exception e) {
                e.printStackTrace();
                //Remove the proxy
                if ((isPageEmpty && comesFromDownload && attempt > limit) || attempt > limit) {
                    return null;
                }
                guiLabels.setConnectionOutput("There was a problem connecting to one of the Proxies from the " +
                        "queue. Removing it");
                thereWasAnErrorWithProxy = true;
                attempt++;
            }
            try {
                crawler.addRequestToMapOfRequests(url, crawler.getMapThreadIdToProxy().get(currThreadID), -1);
            } catch (IllegalArgumentException e) {
                e.printStackTrace(System.out);
            }
        }
        return doc;
    }


    /**
     * Reuse a previously working proxy
     *
     * @param url          URL that the program is trying to connect to
     * @param currThreadID ID of the current thread
     */
    private Document useProxyAgain(String url, long currThreadID) {
        Document doc = null;
        Proxy ipAndPort = null;
        try {
            ipAndPort = crawler.getMapThreadIdToProxy().get(currThreadID);
            Set<Cookie> cookies = null;
            if (crawler.isSeleniumActive()) {
                //Check if the proxy requires cookies
                cookies = crawler.getCookie(ipAndPort, engine);
                doc = useSelenium(ipAndPort, url, true, cookies, false);
            } else {
                doc = getDocUsingJavaNetClass(ipAndPort, url);
            }


        } catch (HttpStatusException e2) {
            //Handle the errors
            e2.printStackTrace(System.out);
            guiLabels.setConnectionOutput("There was a problem connecting to your previously used proxy" +
                    ".\nChanging to a different one");
            this.isError404 = e2.getStatusCode() == 404 || e2.getStatusCode() == 401 || e2.getStatusCode() == 403 ||
                    e2.getStatusCode() == 403;
            //Add it back to the queue if it is error 404, since it is not a problem of the proxy
            if (isError404) {
                crawler.getQueueOfConnections().add(ipAndPort);
            } else {
                thereWasAnErrorWithProxy = true;
            }
            guiLabels.setConnectionOutput(e2.getMessage());

        } catch (Exception e) {
            System.out.println("There was a problem using a proxy again");
            thereWasAnErrorWithProxy = true;
            guiLabels.setConnectionOutput("There was a problem connecting to your previously used proxy" +
                    ".\nChanging to a different one");
        }
        return doc;
    }

    /**
     * Retrieves a document using java net class
     */
    private Document getDocUsingJavaNetClass(Proxy proxyToBeUsed, String url) throws IOException {
        //Connect to proxy using java net class
        java.net.Proxy proxy = new java.net.Proxy(java.net.Proxy.Type.HTTP, new InetSocketAddress
                (proxyToBeUsed.getProxy(), proxyToBeUsed.getPort()));

        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection(proxy);
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows; U; WindowsNT 5.1; en-US; " +
                "rv1.8.1.6) Gecko/20070725 Firefox/2.0.0.6");
        connection.setRequestProperty("Referer", SearchEngine.getBaseURL(engine));
        connection.connect();

        //Check for response code
        if (connection.getResponseCode() != 200) {
            throw new HttpStatusException(connection.getResponseMessage(), connection.getResponseCode(),
                    connection.getURL().toString());
        }
        String line;
        StringBuffer tmp = new StringBuffer();
        BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        while ((line = in.readLine()) != null) {
            tmp.append(line);
        }

        Document doc = Jsoup.parse(String.valueOf(tmp));

        //The current Search Engine flagged this proxy if this happens
        if (doc.text().contains("Sorry, we can't verify that you're not a robot") ||
                doc.text().contains("your computer or network may be sending automated queries")) {
            throw new IllegalArgumentException();
        }
        return doc;
    }


    /**
     * Use selenium to parse a website
     *
     * @param proxyToUse Proxy
     * @param url        url that needs to be parsed
     * @param cookies    Set of cookies. Null if there are no cookies
     * @return Document
     */
    Document useSelenium(Proxy proxyToUse, String url, boolean usesProxy, Set<Cookie> cookies, boolean
            proxyComesFromQueue) throws
            IllegalArgumentException {
        Document doc;
        java.util.logging.Logger.getLogger(PhantomJSDriverService.class.getName()).setLevel(Level.OFF);

        try {
            org.openqa.selenium.Proxy nProxy = null;
            if (usesProxy) {
                final String proxyHost = proxyToUse.getProxy();
                final String proxyPort = String.valueOf(proxyToUse.getPort());
                //Configure proxy
                nProxy = new org.openqa.selenium.Proxy();
                final String proxyString = proxyHost + ":" + proxyPort;
                nProxy.setHttpProxy(proxyString).setSslProxy(proxyString);
            }

            ArrayList<String> cliArgsCap = new ArrayList<>();
            //Add the capabilities
            DesiredCapabilities capabilities = DesiredCapabilities.phantomjs();
            cliArgsCap.add("--web-security=false");
            cliArgsCap.add("--ssl-protocol=any");
            cliArgsCap.add("--ignore-ssl-errors=true");
            cliArgsCap.add("--webdriver-loglevel=NONE");
            if (usesProxy) {
                cliArgsCap.add("--proxy=" + proxyToUse.getProxy() + ":" + proxyToUse.getPort());
            }
            capabilities.setCapability(
                    PhantomJSDriverService.PHANTOMJS_CLI_ARGS, cliArgsCap);
            if (usesProxy) {
                capabilities.setCapability(CapabilityType.PROXY, nProxy);
            }
            capabilities.setCapability("phantomjs.page.settings.userAgent", "Mozilla/5.0 (Windows NT 6.1) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/41.0.2228.0 Safari/537.36");
            //Initiate the driver
            PhantomJSDriver driver = new PhantomJSDriver(capabilities);
            String pageSource = "";
            try {
                driver.manage().timeouts().pageLoadTimeout(2, TimeUnit.MINUTES);
                driver.manage().timeouts().implicitlyWait(3, TimeUnit.MINUTES);
                if (cookies != null && cookies.size() > 0) {
                    //load the url once before requesting cookies
                    driver.get(url);
                    driver.manage().deleteAllCookies();
                    for (Cookie c : cookies) {
                        try {
                            driver.manage().addCookie(c);
                        } catch (InvalidCookieDomainException ignored) {
                        }
                    }
                    driver.navigate().refresh();
                }

                try {
                    driver.get(url);
                } catch (TimeoutException e) {
                    if (cookies != null && cookies.size() > 0) {
                        //Try again to download
                        driver.get(url);
                    }
                }
                pageSource = waitForLoad(driver, true, url);
                //In case it does not load, then reload the page
                if (pageSource.contains("we can't verify that you're not a " +
                        "robot when JavaScript is turned off")) {
                    driver.navigate().refresh();
                    String temp = driver.getPageSource();
                    if (cookies != null) {
                        for (Cookie c : cookies) {
                            try {
                                driver.manage().addCookie(c);
                            } catch (InvalidCookieDomainException ignored) {
                            }
                        }
                    }
                    driver.get(url);
                    pageSource = driver.getPageSource();
                    if (temp.length() > pageSource.length()) {
                        pageSource = temp;
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try {
                    //Close the driver
                    stopPhantomDrive(driver);
                } catch (Exception ignored) {
                }
            }
            doc = Jsoup.parse(pageSource);

            isPageEmpty = proxyComesFromQueue && pageSource.equals("<html><head></head><body></body></html>");

            //These errors cannot be fixed
            if (pageSource.contains("This site can’t be reached") ||
                    pageSource.contains("ERR_PROXY_CONNECTION_FAILED") ||
                    pageSource.contains("your computer or network may be sending " +
                            "automated queries. To protect our users, we can't process your request right now") ||
                    pageSource.equals("<html><head></head><body></body></html>") ||
                    pageSource.equals("") || pageSource.contains("ContentKeeper") ||
                    pageSource.split(" ").length < 200) {

                if (pageSource.contains("ContentKeeper")) {
                    //This happens if the proxy provider  blocks us
                    int curr = stats.getNumberOfLockedByProvider().get() + 1;
                    stats.updateNumberOfLockedByProvider(curr);
                    if (cookies != null && cookies.size() > 0) {
                        stats.updateNumberOfUnlocked(stats.getNumberOfUnlockedProxies().get() - 1);
                    }
                    blockProxy(proxyToUse, url, true);
                }

                InUseProxies.getInstance().releaseProxyUsedToSearch(proxyToUse);
                //Check if this proxy has failed to load more than 3 times
                int numOfFailures = OutsideServer.getInstance(guiLabels).getFailureToLoad(proxyToUse);
                if (numOfFailures >= 3) {
                    blockProxy(proxyToUse, url, true);
                }
                OutsideServer.getInstance(guiLabels).addFailureToLoad(proxyToUse);
                throw new IllegalArgumentException();
            }
            if (pageSource.contains("we can't verify that you're not a " +
                    "robot when JavaScript is turned off")) {
                //This is a failure in loading so just throw an exception
                InUseProxies.getInstance().releaseProxyUsedToSearch(proxyToUse);
                //Check if this proxy has failed to load more than 3 times
                int numOfFailures = OutsideServer.getInstance(guiLabels).getFailureToLoad(proxyToUse);
                if (numOfFailures >= 3) {
                    blockProxy(proxyToUse, url, true);
                }
                OutsideServer.getInstance(guiLabels).addFailureToLoad(proxyToUse);

                throw new IllegalArgumentException();
            }
            //These errors can be fixed
            if (doc.text().contains("Sorry, we can't verify that you're not a robot") ||
                    pageSource.contains("Our systems have detected unusual traffic from your computer network")) {

                System.out.println("There is a blocked proxy");
                //Add to the queue of blocked proxies
                if (crawler.isSeleniumActive()) {
                    //Add locked proxy to server, but remove it only from GS
                    blockProxy(proxyToUse, url, false);

                    crawler.getQueueOfBlockedProxies().add(proxyToUse);
                    //Notify GUI
                    guiLabels.setIsThereAnAlert(true);
                    //Send email if possible
                    crawler.sendBlockedProxies();
                    //Update stats
                    stats.updateNumberOfBlockedProxies(crawler.getQueueOfBlockedProxies().size());
                    if (cookies != null && cookies.size() > 0) {
                        //If this happens, it means that Google blocked a previously unlocked proxy
                        stats.updateNumberOfRelockedProxies(stats.getNumberOfRelockedProxies().get() + 1);
                        stats.updateNumberOfUnlocked(stats.getNumberOfUnlockedProxies().get() - 1);
                    }

                }
                crawler.addRequestToMapOfRequests(url, proxyToUse, 50);
                throw new IllegalArgumentException();
            }

        } catch (Exception e) {
            if (!e.getClass().getCanonicalName().contains("IllegalArgument")) {
                e.printStackTrace();
            }
            throw new IllegalArgumentException();
        }
        //If there are no cookies, then add it to the list of unlocked proxies since is not already there
        if ((cookies == null || cookies.size() == 0) && proxyToUse != null) {
            crawler.addUnlockedProxy(proxyToUse, new HashSet<>(), engine, server, false);
        }

        return doc;
    }


    /**
     * Attempts to close phantomjs correctly
     *
     * @param driver WebDriver
     */
    static void stopPhantomDrive(PhantomJSDriver driver) throws IOException, InterruptedException {
        try {
            driver.close();
            driver.quit();
            driver = null;
        } catch (Exception ignored) {
        }

    }


    /**
     * Actives the chrome driver in order to solve google captcha
     *
     * @return WebDriver
     */
    WebDriver useChromeDriver(Proxy proxy, String url, Set<Cookie> cookies) {
        ChromeDriver driver = null;
        try {
            org.openqa.selenium.Proxy nProxy;

            //Configure proxy
            int proxyPort = proxy.getPort();
            String proxyHost = proxy.getProxy();
            nProxy = new org.openqa.selenium.Proxy();
            final String proxyString = proxyHost + ":" + proxyPort;
            nProxy.setHttpProxy(proxyString).setSslProxy(proxyString);

            DesiredCapabilities caps = DesiredCapabilities.chrome();
            caps.setJavascriptEnabled(true);
            caps.setCapability(CapabilityType.PROXY, nProxy);

            driver = new ChromeDriver(caps);
            driver.manage().timeouts().implicitlyWait(60, TimeUnit.SECONDS);
            if (cookies.size() > 0) {
                driver.get(SearchEngine.getTestURL(engine));
                for (Cookie cookie : cookies) {
                    driver.manage().addCookie(cookie);
                }
            }

            if (url == null) {
                driver.get(SearchEngine.getTestURL(engine));
            } else {
                driver.get(url);
            }
            waitForLoad(driver, false, url);

        } catch (Exception | Error e) {
            guiLabels.setAlertPopUp(e.getMessage());
        }
        return driver;
    }


    /**
     * Waits for the driver to correctly load the page
     *
     * @param driver WebDriver
     */

    static String waitForLoad(WebDriver driver, boolean isSearch, String url) {
        if (engine == null) {
            engine = SearchEngine.SupportedSearchEngine.GoogleScholar;
        }
        String pageSource = "";
        try {
            ExpectedCondition<Boolean> expectation = driver1 -> ((JavascriptExecutor) driver).
                    executeScript("return document.readyState").equals("complete");
            Wait<WebDriver> wait = new WebDriverWait(driver, 240);
            wait.until(expectation);
            //Loading for Microsoft academic is different, so wait for website to fully load javascript
            if (engine == SearchEngine.SupportedSearchEngine.MicrosoftAcademic && isSearch && url.contains("academic" +
                    ".microsoft")) {
                int attempts = 0;
                Thread.sleep(15 * 1000);
                pageSource = driver.getPageSource();
                //Verify if MSFT has not blocked us for t minutes
                boolean needsToWait = true;
                int attempt2 = 0;
                while (needsToWait) {
                    Pattern pattern = Pattern.compile("You can wait .* to issue");
                    Matcher matcher = pattern.matcher(pageSource);
                    if (matcher.find()) {
                        //Reload page after t minuts
                        String s = matcher.group();
                        s = s.replaceAll("\\D+", "");
                        int timeToWait = Integer.valueOf(s);
                        Thread.sleep(timeToWait * 60 * 1000 + (5 * 1000));
                        driver.get(url);
                        pageSource = driver.getPageSource();
                        attempt2++;
                        if (attempt2 > 3) throw new IllegalArgumentException();
                    } else {
                        needsToWait = false;
                    }
                }

                while (pageSource.length() < 50000 && !pageSource.equals("<html><head></head><body></body></html>")
                        && attempts < 3) {
                    Thread.sleep(15 * 1000);
                    attempts++;
                    pageSource = driver.getPageSource();
                }
            }
        } catch (Exception e) {
            System.out.println("THERE WAS A TIMEOUT WHILE LOADING");
        }

        pageSource = driver.getPageSource();
        if (engine == SearchEngine.SupportedSearchEngine.MicrosoftAcademic && pageSource.length() < 50000 && !pageSource
                .equals("<html><head></head><body></body></html>")) {
            pageSource = "This site can’t be reached";
        }
        return pageSource;

    }

    /**
     * Blocks a proxy
     */
    private void blockProxy(Proxy proxyToUse, String url, boolean removeFromAllWebsites) {
        InUseProxies.getInstance().releaseProxyUsedToSearch(proxyToUse);
        InUseProxies.getInstance().removeGSProxy(proxyToUse);
        server.addLockedProxy(proxyToUse);
        crawler.addRequestToMapOfRequests(url, proxyToUse, 50);
        if (removeFromAllWebsites) {
            InUseProxies.getInstance().removeProxy(proxyToUse);
        }
    }

    void setThreadID(Long threadID) {
        this.threadID = threadID;
    }

    void setComesFromDownload() {
        this.comesFromDownload = true;
    }
}


