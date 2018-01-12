package com.rc.crawler;

import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.WebClient;
import org.apache.commons.lang3.SystemUtils;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.htmlunit.HtmlUnitDriver;
import org.openqa.selenium.phantomjs.PhantomJSDriver;
import org.openqa.selenium.phantomjs.PhantomJSDriverService;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.Wait;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.*;
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
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Created by rafaelcastro on 7/7/17.
 * Handles the logic to connect to a website using proxies
 */
class ProxyChanger {
    private GUILabelManagement guiLabels;
    private Crawler crawler;
    private boolean isError404;
    private boolean thereWasAnErrorWithProxy = false;
    private SearchEngine.SupportedSearchEngine engine;
    private Long threadID = null;
    private boolean isPageEmpty = false;
    private boolean comesFromDownload = false;
    private StatsGUI stats;
    private DatabaseDriver db;

    ProxyChanger(GUILabelManagement guiLabels, Crawler crawler, SearchEngine.SupportedSearchEngine engine, StatsGUI
            stats) {
        this.guiLabels = guiLabels;
        this.crawler = crawler;
        this.engine = engine;
        this.stats = stats;
        this.db = DatabaseDriver.getInstance(guiLabels);
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
     * @param url             URL we are trying to connect to
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
                    Thread.sleep(10*1000);
                } catch (InterruptedException ignored) {
                }
            }
            //Check if it the proxy is not null, it is being used by less than 4 crawlers, it has less than 40
            // requests and there is not another thread that has already unlocked
            while (proxyToBeUsed == null || !db.canUseProxy(proxyToBeUsed) || crawler.getNumberOfRequestFromMap(url,
                    proxyToBeUsed) > 40 || db.isCurrentInstanceUsingProxy(proxyToBeUsed)) {

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
                if (db.isCurrentInstanceUsingProxy(proxyToBeUsed)) {
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
                    db.addProxyToCurrentInstance(proxyToBeUsed);
                    connected = true;
                    crawler.getMapThreadIdToProxy().put(Thread.currentThread().getId(), proxyToBeUsed);
                } else {
                    doc = getDocUsingJavaNetClass(proxyToBeUsed, url);
                    //If no error happens add it
                    InUseProxies.getInstance().hasCrawlerConnectedToProxy(proxyToBeUsed, true);
                    //If no error happens add it
                    db.addProxyToCurrentInstance(proxyToBeUsed);
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
                    !db.canUseProxy(proxyToUse) ||
                    (InUseProxies.getInstance().isProxyInUseForSearching(proxyToUse) && crawler.getMapThreadIdToProxy().get
                            (currThreadID) != proxyToUse)) {

                //Since we are using a new proxy, we need to find a replacement as long as there are less than 20
                //proxies in the queue or we have gone through over 70% of the proxies in the queue and none works
                if (first && InUseProxies.getInstance().getCounterOfRequestsToGetNewProxies().value() < 8  && crawler
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
                if ((isPageEmpty && comesFromDownload && attempt > limit )|| attempt > limit) {
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
     * @param proxyToUse          Proxy
     * @param url                 url that needs to be parsed
     * @param cookies             Set of cookies. Null if there are no cookies
     * @param proxyComesFromQueue
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
                driver.manage().timeouts().pageLoadTimeout(3, TimeUnit.MINUTES);
                driver.manage().timeouts().implicitlyWait(5, TimeUnit.MINUTES);
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
                    if (cookies != null ) {
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
                    //Remove it because this cannot be fixed
                    InUseProxies.getInstance().removeGSProxy(proxyToUse);
                    //We also remove the proxy since it won't work for any site
                    InUseProxies.getInstance().removeProxy(proxyToUse);
                    db.addLockedProxy(proxyToUse);
                    crawler.addRequestToMapOfRequests(url, proxyToUse, 50);
                }

                //If proxy is blocked by provider, then we cant do anything so lock the proxy and remove it completly
                  InUseProxies.getInstance().releaseProxyUsedToSearch(proxyToUse);
                throw new IllegalArgumentException();
            }
            if (pageSource.contains("we can't verify that you're not a " +
                    "robot when JavaScript is turned off")) {
                //This is a failure in loading so just throw an exception
                InUseProxies.getInstance().releaseProxyUsedToSearch(proxyToUse);
                System.out.println("Failed to load");
                throw new IllegalArgumentException();
            }
            //These errors can be fixed
            if (doc.text().contains("Sorry, we can't verify that you're not a robot") ||
                    pageSource.contains("Our systems have detected unusual traffic from your computer network")) {

                System.out.println("There is a blocked proxy");
                //Add to the queue of blocked proxies
                if (crawler.isSeleniumActive()) {

                    //Add locked proxy to server
                    InUseProxies.getInstance().releaseProxyUsedToSearch(proxyToUse);
                    InUseProxies.getInstance().removeGSProxy(proxyToUse);
                    db.addLockedProxy(proxyToUse);

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
            crawler.addUnlockedProxy(proxyToUse, new HashSet<>(), engine, db, false);
        }

        return doc;
    }


    /**
     * Attempts to close phantomjs correctly
     *
     * @param driver WebDriver
     */
    private void stopPhantomDrive(PhantomJSDriver driver) throws IOException, InterruptedException {
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
     * Configures Selenium the first time
     */
    boolean setUpSelenium() {
        guiLabels.setOutput("Configuring Selenium");
        try {
            String type;
            if (SystemUtils.IS_OS_MAC_OSX) {
                type = "mac";

            } else if (SystemUtils.IS_OS_WINDOWS) {
                type = "win";
            } else {
                type = null;
            }


            //Check if chromedriver and phantomjs exist
            File[] files = new File(".").listFiles();
            File chromeDriver = new File("chromedriver");
            File phantomjs = new File("phantomjs/bin/phantomjs");

            if (files != null) {
                for (File curr : files) {
                    if (curr.getName().contains("chrome")) {
                        if (curr.getName().contains("zip") || curr.getName().contains("log")) {
                            continue;
                        }
                        chromeDriver = new File(curr.getName());

                    } else if (curr.getName().contains("phantomjs")) {
                        if (curr.getName().contains("zip") || curr.getName().contains("log")) {
                            continue;
                        }
                        phantomjs = new File(curr.getName() + "/bin/phantomjs");
                    }
                }
            }

            if (type == null) {
                guiLabels.setAlertPopUp("Cannot use Javascript enable websites with your computer. sCrawler does " +
                        "not " +
                        "fully support your operating system");
                return false;
            }
            if (!chromeDriver.exists()) {
                chromeDriver = downloadChromeDriver(type);
            }
            if (!phantomjs.exists()) {
                phantomjs = downloadPhantomJS(type);
            }
            if (chromeDriver == null || !chromeDriver.exists() || phantomjs == null || !phantomjs.exists()) {

                guiLabels.setAlertPopUp("There was a problem downloading chromedriver and/or phantomjs in your " +
                        "computer. You won't be able to use Javascript-enabled websites with this crawler. If you" +
                        " " +
                        "believe that this is an error," +
                        " try restarting this crawler.\nYou can also manually download them from: http://docs" +
                        ".seleniumhq.org/download/ and put it in the same directory where this crawler is " +
                        "located, just" +
                        "decompress the folder once it is downloaded, and restart the crawler.");
                return false;
            } else {

                //Set up chromedriver
                String path = chromeDriver.getAbsolutePath();
                path = path.replaceAll("\\./", "");
                System.setProperty("webdriver.chrome.driver", path);
                //Set up phantomjs
                System.setProperty("phantomjs.binary.path", phantomjs.getAbsolutePath());
                String userAgent = "Mozilla/5.0 (Windows NT 6.0) AppleWebKit/535.1 (KHTML, like Gecko) " +
                        "Chrome/13.0.782.41" +
                        " Safari/535.1";
                System.setProperty("phantomjs.page.settings.userAgent", userAgent);
                guiLabels.setOutput("Finding working proxies...");

                testWebDrivers();
            }
        } catch (Exception e) {
            e.printStackTrace(System.out);
        }
        return true;
    }

    /**
     * Verify that the Web Drivers are working
     */
    private void testWebDrivers() throws IOException, InterruptedException {
        guiLabels.setOutput("Verifying web drivers...");

        //Test phantomjs
        PhantomJSDriver driver = new PhantomJSDriver();
        driver.manage().timeouts().pageLoadTimeout(5, TimeUnit.MINUTES);
        driver.manage().timeouts().implicitlyWait(1, TimeUnit.MINUTES);
        driver.get("https://www.google.com");
        waitForLoad(driver, false, "https://www.google.com");
        stopPhantomDrive(driver);

        //Test chromedriver

        ChromeDriver driver2 = new ChromeDriver();
        driver2.manage().timeouts().implicitlyWait(60, TimeUnit.SECONDS);
        driver2.get("https://www.google.com");
        waitForLoad(driver2, false, "https://www.google.com");
        driver2.close();
        driver2.quit();
        guiLabels.setOutput("Web drivers are working");

    }

    /**
     * Downloads the chrome driver into the local computer
     */

    private File downloadPhantomJS(String type) {
        guiLabels.setOutput("Downloading PhantomJS for " + type);
        try {
            //Get download link
            Document doc = Jsoup.connect("http://phantomjs.org/download.html").timeout(10 * 1000).userAgent
                    ("Mozilla/5.0 (Windows; U; WindowsNT 5.1; en-US; rv1.8.1.6) " +
                            "Gecko/20070725 Firefox/2.0.0.6").get();
            Elements links = doc.select("a[href]");
            String href = "";
            for (Element link : links) {
                if (link.toString().contains(type)) {
                    href = link.attr("href");
                    break;
                }
            }

            if (!href.isEmpty()) {
                //Download file
                URL url = new URL(href);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                InputStream in = connection.getInputStream();
                FileOutputStream out = new FileOutputStream("./phantomjs.zip");
                copy(in, out);
                out.close();

                //Unzip file
                String fileName = unzip("./phantomjs.zip", "./");
                File phantomJS = new File("./" + fileName);
                if (type.equals("mac")) {
                    Runtime.getRuntime().exec("chmod u+x " + phantomJS);
                }
                return phantomJS;
            }
        } catch (Exception e) {
            guiLabels.setAlertPopUp("There was a problem downloading PhantomJS. You won't be able to use " +
                    "Javascript-enabled websites nor unlock proxies with this crawler. If you believe that this is an" +
                    " error, try restarting this crawler.\nYou can also manually download it and put it in the same " +
                    "directory" +
                    " where this crawler is located, just decompress the folder, change the folder name to " +
                    "phantomjs and restart the crawler.");
            return null;
        }
        return null;
    }

    /**
     * Downloads the chrome driver into the local computer
     */
    private File downloadChromeDriver(String type) {
        guiLabels.setOutput("Downloading Chrome driver for " + type);
        try {
            //Get download link
            Document doc = Jsoup.connect("http://docs.seleniumhq.org/download/").timeout(10 * 1000).userAgent
                    ("Mozilla/5.0 (Windows; U; WindowsNT 5.1; en-US; rv1.8.1.6) " +
                            "Gecko/20070725 Firefox/2.0.0.6").get();
            Elements links = doc.select("a[href]");
            String href = "";
            for (Element link : links) {
                if (link.toString().contains("http://chromedriver.storage.googleapis.com/index")) {
                    href = link.attr("href");
                    break;
                }
            }
            //Find correct version
            DesiredCapabilities caps = new DesiredCapabilities();
            caps.setBrowserName("htmlunit");
            caps.setJavascriptEnabled(true);
            HtmlUnitDriver driver = new HtmlUnitDriver(caps) {
                @Override
                protected WebClient newWebClient(BrowserVersion version) {

                    WebClient webClient = super.newWebClient(BrowserVersion.FIREFOX_52);
                    webClient.getOptions().setThrowExceptionOnScriptError(false);
                    return webClient;
                }
            };
            driver.get(href);
            try {
                Thread.sleep(5000);
            } catch (Exception ignored) {
            }
            driver.manage().timeouts().pageLoadTimeout(60, TimeUnit.SECONDS);
            doc = Jsoup.parse(driver.getPageSource());
            driver.quit();
            links = doc.select("a[href]");
            href = "";
            for (Element link : links) {
                if (link.toString().contains(type)) {
                    href = link.attr("href");
                    break;
                }
            }
            href = "http://chromedriver.storage.googleapis.com" + href;

            if (!href.isEmpty()) {
                //Download file
                URL url = new URL(href);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                InputStream in = connection.getInputStream();
                FileOutputStream out = new FileOutputStream("./chromedriver.zip");
                copy(in, out);
                out.close();

                //Unzip file
                String fileName = unzip("./chromedriver.zip", "./");
                File chromeDriver = new File("./" + fileName);
                if (type.equals("mac")) {
                    Runtime.getRuntime().exec("chmod u+x " + chromeDriver);
                }
                return chromeDriver;

            }
        } catch (Exception e) {
            guiLabels.setAlertPopUp("There was a problem downloading chrome driver. You won't be able to use " +
                    "Javascript-enabled websites nor unlock proxies with this crawler. If you believe that this is an" +
                    " error, try " +
                    "restarting this crawler.\nYou can also manually download it and put it in the same directory" +
                    " where this crawler is located, just decompress the folder, change the folder name to " +
                    "chromedriver and restart the crawler.");
            return null;
        }
        return null;
    }


    /**
     * Waits for the driver to correctly load the page
     *
     * @param driver DatabaseDriver
     */

    private String waitForLoad(WebDriver driver, boolean isSearch, String url) {
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
     * Unzip a file
     *
     * @param zipFilePath   zip location
     * @param destDirectory destination
     * @return name of the unzipped file
     * @throws IOException Error writing to file
     */
    static String unzip(String zipFilePath, String destDirectory) throws IOException {
        String mainFile = "";
        boolean first = true;
        File destDir = new File(destDirectory);
        if (!destDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            destDir.mkdir();
        }
        ZipInputStream zipIn = new ZipInputStream(new FileInputStream(zipFilePath));
        ZipEntry entry = zipIn.getNextEntry();
        // iterates over entries in the zip file
        while (entry != null) {
            String filePath = destDirectory + File.separator + entry.getName();
            if (!entry.isDirectory()) {
                if (first) {
                    first = false;
                    mainFile = entry.getName();
                }
                // if the entry is a file, extracts it
                extractFile(zipIn, filePath);
            } else {
                // if the entry is a directory, make the directory
                File dir = new File(filePath);
                //noinspection ResultOfMethodCallIgnored
                dir.mkdir();
            }
            zipIn.closeEntry();
            entry = zipIn.getNextEntry();
        }
        zipIn.close();
        //Delete the zip file
        new File(zipFilePath).delete();
        return mainFile;
    }

    /**
     * Extracts a zip entry (file entry)
     *
     * @throws IOException Unable to write file
     */
    static void extractFile(ZipInputStream zipIn, String filePath) throws IOException {
        BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(filePath));
        byte[] bytesIn = new byte[4096];
        int read = 0;
        while ((read = zipIn.read(bytesIn)) != -1) {
            bos.write(bytesIn, 0, read);
        }
        bos.close();
    }

    /**
     * Copy file from one location to another
     *
     * @param input  InputStream
     * @param output OutputStream
     * @throws IOException Error copying file
     */
    static void copy(InputStream input, OutputStream output) throws IOException {
        byte[] buf = new byte[1024];
        int n = input.read(buf);
        while (n >= 0) {
            output.write(buf, 0, n);
            n = input.read(buf);
        }
        output.flush();
    }

    void setThreadID(Long threadID) {
        this.threadID = threadID;
    }

    void setComesFromDownload(boolean comesFromDownload) {
        this.comesFromDownload = comesFromDownload;
    }
}


