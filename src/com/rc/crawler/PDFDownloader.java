package com.rc.crawler;

import org.openqa.selenium.Cookie;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
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
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.*;
import java.util.logging.Level;


/**
 * Created by rafaelcastro on 6/14/17.
 * Manages the way PDFs are stored and downloaded.
 */
class PDFDownloader {
    private GUILabelManagement guiLabels;
    private String path = "";
    private Crawler crawler;
    private String url;
    private int pdfCounter;
    private Proxy ipAndPort;
    private boolean speedUp;
    private SearchEngine.SupportedSearchEngine engine;
    Long currThreadID;

    PDFDownloader(SearchEngine.SupportedSearchEngine engine) {
        this.engine = engine;
        currThreadID = Thread.currentThread().getId();
    }

    /**
     * Gets the folder where the files are going to be stored for a given query
     *
     * @return string with the folder name
     */
    String getPath() {
        return path;
    }

    /**
     * Download a pdf file to a directory
     *
     * @param url        URL to download file from
     * @param pdfCounter Number of PDFs downloaded
     * @param guiLabels  GUILabelManagement object
     * @param ipAndPort  Proxy with the current proxy being used
     * @param speedUp    If true, program does not use proxies to download most files.
     * @throws IOException Unable to open link
     */
    void downloadPDF(String url, int pdfCounter, GUILabelManagement guiLabels, Proxy ipAndPort, boolean speedUp)
            throws Exception {
        this.url = url;
        this.ipAndPort = ipAndPort;
        this.pdfCounter = pdfCounter;
        this.guiLabels = guiLabels;
        this.speedUp = speedUp;
        DownloadPDFTask downloadPDFTask = new DownloadPDFTask();
        ExecutorService executorService = Executors.newSingleThreadExecutor(new MyThreadFactory());
        Future<String> future = executorService.submit(downloadPDFTask);
        String result;
        try {
            //Limit of 3 minute to download a file
            result = future.get(3, TimeUnit.MINUTES);
        } catch (Exception e) {
            future.cancel(true);
            result = "Timeout";
        }
        //If result is not empty, it means that an exception was thrown inside the thread, or there was a timeout
        if (!result.isEmpty()) {
            throw new IOException(result);
        }
    }

    public void setCrawler(Crawler crawler) {
        this.crawler = crawler;
    }


    /**
     * Class to download PDF files using threads
     */
    class DownloadPDFTask implements Callable<String> {
        private HttpURLConnection connection;
        private java.net.Proxy proxy;
        private Set<Cookie> cookies;
        private String pageSource;

        DownloadPDFTask() {
        }

        @Override
        public String call() throws Exception {
            String result = "";
            //Connect to the site holding the pdf and download it
            try {
                if (crawler.isSeleniumActive()) {
                    java.util.logging.Logger.getLogger(PhantomJSDriverService.class.getName()).setLevel(Level.OFF);
                    cookies = connectUsingSelenium();
                }
                    int status = connectUsingNetClass();
                    boolean redirect = false;
                    if (status != HttpURLConnection.HTTP_OK) {
                        if (status == HttpURLConnection.HTTP_MOVED_TEMP
                                || status == HttpURLConnection.HTTP_MOVED_PERM
                                || status == HttpURLConnection.HTTP_SEE_OTHER)
                            redirect = true;
                    }
                    if (status == 403) {
                        throw new IOException("Error 403");
                    }
                    if (redirect) {
                        redirect(connection, proxy);
                    } else {

                        ReadableByteChannel rbc = Channels.newChannel(connection.getInputStream());
                        FileOutputStream fos = new FileOutputStream("./DownloadedPDFs/" + path + "/" + pdfCounter + "" +
                                ".pdf");
                        fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);

                    }

            } catch (Exception e) {
                e.printStackTrace(System.out);
                result = e.getMessage();
            }

            return result;
        }

        /**
         * Connects to a URL using selenium
         */
        Set<Cookie> connectUsingSelenium() {
            Set<Cookie> cookies;
            WebDriver driver = null;
            try {
                org.openqa.selenium.Proxy nProxy = null;
                if (speedUp) {
                    nProxy = new org.openqa.selenium.Proxy();
                    final String proxyString = ipAndPort.getProxy() + ":" + String.valueOf(ipAndPort.getPort());
                    nProxy.setHttpProxy(proxyString).setSslProxy(proxyString);
                }
                ArrayList<String> cliArgsCap = new ArrayList<>();
                DesiredCapabilities capabilities = DesiredCapabilities.phantomjs();
                cliArgsCap.add("--web-security=false");
                cliArgsCap.add("--ssl-protocol=any");
                cliArgsCap.add("--ignore-ssl-errors=true");
                cliArgsCap.add("--webdriver-loglevel=NONE");

                if (speedUp) {
                    cliArgsCap.add("--proxy=" + ipAndPort.getProxy() + ":" + ipAndPort.getPort());
                }
                capabilities.setCapability(
                        PhantomJSDriverService.PHANTOMJS_CLI_ARGS, cliArgsCap);
                if (speedUp) {
                    capabilities.setCapability(CapabilityType.PROXY, nProxy);
                }


                //Initiate the driver
                driver = new PhantomJSDriver(capabilities);

                driver.manage().timeouts().pageLoadTimeout(3, TimeUnit.MINUTES);
                driver.manage().timeouts().implicitlyWait(1, TimeUnit.MINUTES);
                driver.get(url);
                waitForLoad(driver);
                cookies = driver.manage().getCookies();
                pageSource = driver.getPageSource();


            } catch (Exception e) {
                e.printStackTrace(System.out);
                throw new IllegalArgumentException();
            } finally {
                try {
                    //Close the driver
                    if (driver != null) {
                        driver.close();
                        driver.quit();
                        driver = null;
                    }
                } catch (Exception ignored) {
                }
            }
            return cookies;
        }

        /**
         * Connects to a URL using java net class
         */
        private int connectUsingNetClass() throws IOException {
            URL urlObj = new URL(url);
            this.connection = null;
            this.proxy = new java.net.Proxy(java.net.Proxy.Type.HTTP, new InetSocketAddress(ipAndPort.getProxy(),
                    ipAndPort.getPort()));

            if (!speedUp) {
                connection = (HttpURLConnection) urlObj.openConnection(proxy);
            } else {
                connection = (HttpURLConnection) urlObj.openConnection();

            }
            //Set request property to avoid error 403
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows; U; WindowsNT 5.1; en-US; rv1.8.1.6) " +
                    "Gecko/20070725 Firefox/2.0.0.6");
            connection.setRequestProperty("Referer", "https://scholar.google.com/");
            if (crawler.isSeleniumActive()) {
                for (Cookie cookie : cookies) {
                    connection.addRequestProperty("Cookie", cookie.toString());
                }
            }
            int status = connection.getResponseCode();

            if (status == 429) {
                //If we have sent too many request to server, change proxy
                String cookie = connection.getHeaderField("Set-Cookie").split(";")[0];
                connection.disconnect();
                guiLabels.setConnectionOutput("We have been blocked by this server. Using proxy to connect...");
                //If server blocks us, then we connect via the current proxy we are using
                connection = (HttpURLConnection) urlObj.openConnection(proxy);
                connection.setRequestProperty("User-Agent", "Mozilla/5.0");
                connection.setRequestProperty("Referer", "https://scholar.google.com/");
                connection.setRequestProperty("Cookie", cookie);
                status = connection.getResponseCode();
            }
            connection.connect();
            return status;

        }

        /**
         * Redirects a connection
         */
        private void redirect(HttpURLConnection connection, java.net.Proxy proxy) throws IOException {
            boolean redirect = true;
            while (redirect) {
                // get redirect url from "location" header field
                String newUrl = connection.getHeaderField("Location");
                url = newUrl;
                // open the new connection again
                if (!speedUp) {
                    connection = (HttpURLConnection) new URL(newUrl).openConnection(proxy);
                } else {
                    connection = (HttpURLConnection) new URL(newUrl).openConnection();
                }
                if (crawler.isSeleniumActive()) {
                    for (Cookie cookie : cookies) {
                        connection.addRequestProperty("Cookie", cookie.toString());
                    }
                }
                connection.setRequestProperty("User-Agent", "Chrome");
                int status = connection.getResponseCode();
                if (status == 429) {
                    String cookie = connection.getHeaderField("Set-Cookie").split(";")[0];
                    connection.disconnect();
                    guiLabels.setConnectionOutput("We have been blocked by this server. Using proxy to connect...");
                    //If server blocks us, then we connect via the current proxy we are using
                    connection = (HttpURLConnection) new URL(newUrl).openConnection(proxy);
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0");
                    connection.setRequestProperty("Cookie", cookie);
                    status = connection.getResponseCode();

                }

                connection.connect();
                ReadableByteChannel rbc = Channels.newChannel(connection.getInputStream());
                FileOutputStream fos = new FileOutputStream("./DownloadedPDFs/" + path + "/" + pdfCounter + ".pdf");
                fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);

                if (status != HttpURLConnection.HTTP_OK) {
                    if (status == HttpURLConnection.HTTP_MOVED_TEMP
                            || status == HttpURLConnection.HTTP_MOVED_PERM
                            || status == HttpURLConnection.HTTP_SEE_OTHER)
                        redirect = true;
                } else {
                    redirect = false;
                }

            }
        }
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
        } catch (Exception ignored) {
        }

    }


    /**
     * Creates a unique folder to store the downloaded PDFs
     *
     * @param title title of the paper
     * @return string with the name of the folder
     */
    String createUniqueFolder(String title) {
        String[] titleWords = title.split(" ");
        StringBuilder firstWord = new StringBuilder(titleWords[0]);
        if (firstWord.length() < 3) {
            firstWord.append(titleWords[1]);
        }
        if (firstWord.length() > 3) {
            firstWord = new StringBuilder(firstWord.substring(0, 3));
        }
        while (firstWord.length() < 3) {
            firstWord.append("a");
        }
        File uniqueFileFolder = null;
        try {
            uniqueFileFolder = File.createTempFile(firstWord.toString(), null, new File("./DownloadedPDFs"));
        } catch (IOException e) {
            guiLabels.setAlertPopUp("Unable to create output file");
        }

        assert uniqueFileFolder != null;
        String nameOfFolder = uniqueFileFolder.getName().substring(0, uniqueFileFolder.getName().length() - 4);

        File dir = new File("./DownloadedPDFs/" + nameOfFolder);
        //noinspection ResultOfMethodCallIgnored
        dir.mkdir();

        //Delete temporary file
        //noinspection ResultOfMethodCallIgnored
        uniqueFileFolder.delete();

        path = nameOfFolder;
        return nameOfFolder;
    }


}
