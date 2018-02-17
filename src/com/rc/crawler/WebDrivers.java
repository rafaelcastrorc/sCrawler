package com.rc.crawler;

import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.WebClient;
import org.apache.commons.lang3.SystemUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.htmlunit.HtmlUnitDriver;
import org.openqa.selenium.phantomjs.PhantomJSDriver;
import org.openqa.selenium.remote.DesiredCapabilities;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.TimeUnit;

/**
 * Downloads the web drivers and makes sure that they are working
 */
class WebDrivers {
    private GUILabelManagement guiLabels;

    WebDrivers(GUILabelManagement guiLabels) {
        this.guiLabels = guiLabels;
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
                //Try checking if it was downloaded just as a single file
                if (!phantomjs.exists()) {
                    for (File curr : files) {
                        if (curr.getName().contains("phantomjs")) {
                            phantomjs = new File(curr.getName());
                        }
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
            guiLabels.setAlertPopUp("There was a problem downloading chromedriver and/or phantomjs in your " +
                    "computer. You won't be able to use Javascript-enabled websites with this crawler. If you" +
                    " " +
                    "believe that this is an error," +
                    " try restarting this crawler.\nYou can also manually download them from: http://docs" +
                    ".seleniumhq.org/download/ and put it in the same directory where this crawler is " +
                    "located, just" +
                    "decompress the folder once it is downloaded, and restart the crawler.");
            return false;
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
        driver.manage().timeouts().implicitlyWait(2, TimeUnit.MINUTES);
        driver.get("https://www.google.com");
        ProxyChanger.waitForLoad(driver, false, "https://www.google.com");
        ProxyChanger.stopPhantomDrive(driver);

        //Test chromedriver

        ChromeDriver driver2 = new ChromeDriver();
        driver2.manage().timeouts().implicitlyWait(60, TimeUnit.SECONDS);
        driver2.get("https://www.google.com");
        ProxyChanger.waitForLoad(driver2, false, "https://www.google.com");
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
                FileUtilities.copy(in, out);

                //Make a backup copy

                url = new URL(href);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                in = connection.getInputStream();
                FileOutputStream out2 = new FileOutputStream("./phantomjs2");
                FileUtilities.copy(in, out2);


                //Unzip file
                String fileName = FileUtilities.unzip("./phantomjs.zip", "./");
                //If this happens, phantomjs was not downloaded as a zip file
                if (fileName.isEmpty()) {
                    File[] files = new File("./").listFiles();
                    for (File f : files) {
                        if (f.getName().contains("phantom")) {
                            fileName = f.getName();
                        }
                    }
                }
                out.close();
                if (fileName.isEmpty()) throw new IllegalArgumentException();
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
                FileUtilities.copy(in, out);
                out.close();

                //Unzip file
                String fileName = FileUtilities.unzip("./chromedriver.zip", "./");
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
}
