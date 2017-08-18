package com.rc.crawler;

import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.WebClient;
import org.apache.commons.lang3.SystemUtils;
import org.jsoup.Connection;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.htmlunit.HtmlUnitDriver;
import org.openqa.selenium.phantomjs.PhantomJSDriver;
import org.openqa.selenium.phantomjs.PhantomJSDriverService;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
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

    ProxyChanger(GUILabelManagement guiLabels, Crawler crawler) {
        this.guiLabels = guiLabels;
        this.crawler = crawler;
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
        long currThreadID = Thread.currentThread().getId();

        //Step 1. Check if there is internet connection
        if (!crawler.isThereConnection()) {
            guiLabels.setAlertPopUp("Could not connect, please check your internet connection");
            guiLabels.setOutput("No internet connection");
            guiLabels.setOutputMultiple("No internet connection");
            verifyIfThereIsConnection();
        }

        //Step 2. Check if there are less than 100 proxies remaining, we add more
        //But first we check if we have connection. Also we make sure that there is only one thread getting more proxies
        if (crawler.getSetOfProxyGathered().size() < 100 && !crawler.isThreadGettingMoreProxies()) {
            //Verify again if there is internet connection
            if (!crawler.isThereConnection()) {
                guiLabels.setOutput("No internet connection");
                guiLabels.setOutputMultiple("No internet connection");
                verifyIfThereIsConnection();
            }
            crawler.setThreadIsGettingMoreProxies(true);
            crawler.getMoreProxies();
            crawler.setThreadIsGettingMoreProxies(false);
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

        //Connect to the new working proxy if the number of request is >50 or the proxy that the thread is using no
        //longer works
        if (crawler.getQueueOfConnections().size() > 0 && !comesFromThread) {
            return connectToProxyFromQueue(currThreadID, url);
        }

        return establishNewConnection(comesFromThread, url);

    }

    /**
     * Finds a new working proxy
     *
     * @param comesFromThread To be true, it has to come from an obj from the Request class
     * @param url             URL we are trying to connect to
     */
    private Document establishNewConnection(boolean comesFromThread, String url) {
        Document doc = null;
        if (comesFromThread) {
            boolean connected = false;
            boolean thereWasAnError = false;
            Set<Cookie> cookies = null;
            while (!connected) {
                Proxy proxyToBeUsed = null;
                if (!crawler.isThereConnection()) {
                    guiLabels.setOutput("No internet connection");
                    guiLabels.setOutputMultiple("No internet connection");
                    verifyIfThereIsConnection();
                }
                //Try to use one of the unlocked proxies first
                if (crawler.isSeleniumActive() && crawler.getQueueOfUnlockedProxies().size() != 0) {
                    proxyToBeUsed = crawler.getQueueOfUnlockedProxies().poll();
                    cookies = crawler.getMapProxyToCookie().get(proxyToBeUsed);
                    System.out.println("Using proxy from unlocked proxies");
                }
                //If there are no unlocked proxies, or the queue returned null, try finding a new connection
                if (proxyToBeUsed == null) {
                    proxyToBeUsed = crawler.addConnection();
                }

                try {
                    if (!thereWasAnError) {
                        guiLabels.setConnectionOutput("Connecting to Proxy...");
                    }
                    //If Selenium is enabled, get the Document using a webdriver
                    if (crawler.isSeleniumActive()) {
                        doc = useSelenium(proxyToBeUsed, url, true, cookies);
                        //If no error happens add it
                        connected = true;
                        crawler.getMapThreadIdToProxy().put(Thread.currentThread().getId(), proxyToBeUsed);
                    } else {
                        doc = getDocUsingJavaNetClass(proxyToBeUsed, url);
                        //If no error happens add it
                        connected = true;
                        crawler.getMapThreadIdToProxy().put(Thread.currentThread().getId(), proxyToBeUsed);
                    }
                } catch (HttpStatusException e) {
                    //If this error happens, this proxy cannot be used
                    if (e.getUrl().contains("ipv4.google.com/sorry")) {
                        throw new IllegalArgumentException();
                    }
                    System.out.println("HTTTP STATUS EXCEPTION");
                    e.printStackTrace();
                } catch (Exception e) {
                    thereWasAnError = true;
                }
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
        int limit = 3;
        if (url.contains("scholar.google")) {
            limit = 30;
        }
        boolean connected = false;
        Document doc = null;
        int attempt = 0;
        while (!connected) {

            if (!thereWasAnErrorWithProxy && attempt > 0) {
                //Add it again to queue if it did not produce an actual error (non website related)
                crawler.getQueueOfConnections().add(crawler.getMapThreadIdToProxy().get(currThreadID));
            }
            if (thereWasAnErrorWithProxy) {
                //If there was an actual error, reset the counter.
                attempt = 0;
            }
            thereWasAnErrorWithProxy = false;
            //Get an ip from the working list
            Proxy proxyToUse = crawler.getQueueOfConnections().poll();
            //If the proxy is null, or it has more than 40 request for the current website, then trying finding a new
            //one
            boolean first = true;
            while (proxyToUse == null || crawler.getNumberOfRequestFromMap(url, proxyToUse) > 40) {

                //Since we are using a new proxy, we need to find a replacement
                if (first) {
                    Request request = new Request(true, "getConnection", crawler, guiLabels);
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

            crawler.getMapThreadIdToProxy().put(currThreadID, proxyToUse);
            //Since we are using a new proxy, we need to find a replacement
            //If there are already 12 proxies in the queue, then don't add more
            if (crawler.getQueueOfConnections().size() <= 12 && !isError404) {
                Request request = new Request(true, "getConnection", crawler, guiLabels);
                crawler.getExecutorService().submit(request);
                guiLabels.setNumberOfWorkingIPs("remove,none");
            }

            try {
                //Check if selenium is active
                if (crawler.isSeleniumActive()) {
                    //Check if the proxy requires cookies
                    Set<Cookie> cookies = null;
                    if (crawler.getMapProxyToCookie().containsKey(proxyToUse)) {
                        cookies = crawler.getMapProxyToCookie().get(proxyToUse);
                    }
                    doc = useSelenium(proxyToUse, url, true, cookies);
                } else {
                    doc = getDocUsingJavaNetClass(proxyToUse, url);
                }
                if (doc.text().contains("Sorry, we can't verify that you're not a robot") ||
                        doc.text().contains("your computer or network may be sending automated queries")) {
                    throw new IllegalArgumentException("Google flagged your IP as a bot. Changing to a different " +
                            "one");

                }
                connected = true;
            } catch (HttpStatusException e) {
                //e.printStackTrace(System.out);
                guiLabels.setConnectionOutput("There was a problem connecting to one of the Proxies from the " +
                        "queue.");
                if (attempt > limit) {
                    break;
                }
                attempt++;
                guiLabels.setConnectionOutput(e.getMessage());
            } catch (Exception e) {
                e.printStackTrace(System.out);
                guiLabels.setConnectionOutput("There was a problem connecting to one of the Proxies from the " +
                        "queue. Removing it");
                thereWasAnErrorWithProxy = true;
                attempt++;
            }
            crawler.addRequestToMapOfRequests(url, crawler.getMapThreadIdToProxy().get(currThreadID));
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
        Proxy ipAndPort = crawler.getMapThreadIdToProxy().get(currThreadID);
        Document doc = null;

        try {
            Set<Cookie> cookies = null;
            if (crawler.isSeleniumActive()) {
                //Check if the proxy requires cookies
                if (crawler.getMapProxyToCookie().containsKey(ipAndPort)) {
                    cookies = crawler.getMapProxyToCookie().get(ipAndPort);
                }
                doc = useSelenium(ipAndPort, url, true, cookies);
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
            e.printStackTrace(System.out);
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
        connection.setRequestProperty("Referer", "https://scholar.google.com/");
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

        //Google flagged this proxy if this happens
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
    Document useSelenium(Proxy proxyToUse, String url, boolean usesProxy, Set<Cookie> cookies) throws
            IllegalArgumentException {
        Document doc;
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
            DesiredCapabilities capabilities = DesiredCapabilities.phantomjs();
            cliArgsCap.add("--web-security=false");
            cliArgsCap.add("--ssl-protocol=any");
            cliArgsCap.add("--ignore-ssl-errors=true");
            if (usesProxy) {
                cliArgsCap.add("--proxy=" + proxyToUse.getProxy() + ":" + proxyToUse.getPort());
            }
            capabilities.setCapability(
                    PhantomJSDriverService.PHANTOMJS_CLI_ARGS, cliArgsCap);
            if (usesProxy) {
                capabilities.setCapability(CapabilityType.PROXY, nProxy);
            }

            //Initiate the driver
            PhantomJSDriver driver = new PhantomJSDriver(capabilities);


//            //Set up headless chrome driver to crawl the websites
//            //Set up chrome specific capabilities
//            ChromeOptions options = new ChromeOptions();
//            options.addArguments("--headless");
//            options.addArguments("window-size=1200x600");
//
//            //Set up the browser capabilities
//            DesiredCapabilities capabilities = DesiredCapabilities.chrome();
//            capabilities.setJavascriptEnabled(true);
//            if (usesProxy) {
//                capabilities.setCapability(CapabilityType.PROXY, nProxy);
//            }
//            capabilities.setCapability(ChromeOptions.CAPABILITY, options);
//            ChromeDriver driver = new ChromeDriver(capabilities);
            String pageSource = "";
            try {
                driver.manage().timeouts().pageLoadTimeout(5, TimeUnit.MINUTES);
                driver.manage().timeouts().implicitlyWait(1, TimeUnit.MINUTES);
                if (cookies != null) {
                    driver.manage().deleteAllCookies();
                    for (Cookie c : cookies) {
                        driver.manage().addCookie(c);
                    }
                }
                driver.get(url);
                waitForLoad(driver);
                pageSource = driver.getPageSource();

            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try {
                    //Close the driver
                    driver.close();
                    driver.quit();
                    driver = null;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            doc = Jsoup.parse(pageSource);
            //These errors cannot be fixed
            if (pageSource.contains("This site can’t be reached") ||
                    pageSource.contains("ERR_PROXY_CONNECTION_FAILED") ||
                    pageSource.contains("your computer or network may be sending " +
                            "automated queries. To protect our users, we can't process your request right now") ||
                    pageSource.equals("<html><head></head><body></body></html>") ||
                    pageSource.equals("") ||
                    pageSource.split(" ").length < 200) {
                throw new IllegalArgumentException();
            }
            //These errors can be fixed
            if (doc.text().contains("Sorry, we can't verify that you're not a robot") ||
                    pageSource.contains("Our systems have detected unusual traffic from your computer network")) {
                System.out.println("There is a blocked proxy");
                //Add to the queue of blocked proxies
                if (crawler.isSeleniumActive()) {
                    crawler.getQueueOfBlockedProxies().add(proxyToUse);
                    //Notify GUI
                    guiLabels.setIsThereAnAlert(true);
                    //Send email if possible
                    crawler.sendBlockedProxies();
                }
                throw new IllegalArgumentException();
            }

        } catch (Exception e) {
            throw new IllegalArgumentException();
        }
        return doc;
    }


    /**
     * Actives the chrome driver in order to solve google captcha
     *
     * @return WebDriver
     */
    WebDriver useChromeDriver(Proxy proxy, String url) {
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

            if (url == null) {
                driver.get("https://scholar.google.com/scholar?hl=en&q=this+is+the+one&btnG=&as_sdt=1%2C39&as_sdtp=");
            } else {
                driver.get(url);
            }
            waitForLoad(driver);


        } catch (Exception | Error e) {
            guiLabels.setAlertPopUp(e.getMessage());
        }
        return driver;
    }

    /**
     * Configures Selenium the first time
     */
    boolean setUpSelenium() {
        try {
            System.setProperty("phantomjs.binary.path", "./phantomjs/bin/phantomjs");
            String userAgent = "Mozilla/5.0 (Windows NT 6.0) AppleWebKit/535.1 (KHTML, like Gecko) Chrome/13.0.782.41" +
                    " Safari/535.1";
            System.setProperty("phantomjs.page.settings.userAgent", userAgent);
            guiLabels.setOutput("Configuring Selenium");
            String type = null;

            if (SystemUtils.IS_OS_MAC_OSX) {
                type = "mac";

            } else if (SystemUtils.IS_OS_WINDOWS) {
                type = "win";

            }
            //Check if chromedriver exist
            File[] files = new File(".").listFiles();
            File chromeDriver = new File("chromedriver");
            if (files != null) {
                for (File curr : files) {
                    if (curr.getName().contains("chrome")) {
                        if (curr.getName().contains("zip") || curr.getName().contains("log")) {
                            continue;
                        }
                        chromeDriver = new File(curr.getName());

                    }
                }
            }

            if (type == null) {
                guiLabels.setAlertPopUp("Cannot use Javascript enable websites with your computer");
                return false;
            }
            if (!chromeDriver.exists()) {
                chromeDriver = downloadChromeDriver(type);
            }
            if (chromeDriver == null || !chromeDriver.exists()) {

                guiLabels.setAlertPopUp("There was a problem downloading chromedriver. You won't be" +
                        "able to use Javascript-enabled websites with this crawler. If you believe that this is an " +
                        "error," +

                        " try restarting this crawler.\nYou can also manually download them from: http://docs" +
                        ".seleniumhq" +
                        ".org/download/ and put it in the same directory where this crawler is located, just " +
                        "decompress " +
                        "the folder once it is downloaded, and restart the crawler.");
                return false;
            } else {
                String path = chromeDriver.getAbsolutePath();
                path = path.replaceAll("\\./", "");
                System.setProperty("webdriver.chrome.driver", path);
                guiLabels.setOutput("Finding working proxies...");
            }
        } catch (Exception e) {
            e.printStackTrace(System.out);
        }
        return true;
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
                System.out.println(link.toString());
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
     * @param driver Driver
     */

    private void waitForLoad(WebDriver driver) {
        try {
            new WebDriverWait(driver, 120).until((ExpectedCondition<Boolean>) wd ->
                    ((JavascriptExecutor) wd).executeScript("return document.readyState").equals("complete"));
        } catch (Exception e) {
            System.out.println("THERE WAS AN EXCEPTION");
        }

    }


    /**
     * Unzip a file
     *
     * @param zipFilePath   zip location
     * @param destDirectory destination
     * @return name of the unzipped file
     * @throws IOException Error writing to file
     */
    private String unzip(String zipFilePath, String destDirectory) throws IOException {
        String mainFile = "";
        boolean first = true;
        File destDir = new File(destDirectory);
        if (!destDir.exists()) {
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
    private void extractFile(ZipInputStream zipIn, String filePath) throws IOException {
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
    private static void copy(InputStream input, OutputStream output) throws IOException {
        byte[] buf = new byte[1024];
        int n = input.read(buf);
        while (n >= 0) {
            output.write(buf, 0, n);
            n = input.read(buf);
        }
        output.flush();
    }


}


