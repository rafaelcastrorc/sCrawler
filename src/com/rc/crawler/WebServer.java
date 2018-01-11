package com.rc.crawler;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.stage.Stage;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.joda.time.DateTime;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.WebDriver;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by rafaelcastro on 9/7/17.
 * Changes the current instance based on the webserver requests
 */
class WebServer {

    private static WebServer webServer;
    private static Controller controller;
    private static GUILabelManagement guiLabelManagement;


    private WebServer() {
    }

    void setController(Controller controller) {
        WebServer.controller = controller;
    }


    static WebServer getInstance(GUILabelManagement guiLabels) {
        if (webServer == null) {
            guiLabelManagement = guiLabels;
            webServer = new WebServer();

        }
        return webServer;

    }


    /**
     * Closes the current instance of the crawler if possible
     */
    void checkForOperations() {
        String operationToPerform;
        operationToPerform = DatabaseDriver.getInstance(guiLabelManagement).getOperationToPerform();
        if (operationToPerform.equals(SupportedOperations.close.name())) {
            closeButtonAction();
        } else if (operationToPerform.equals(SupportedOperations.clean.name())) {
            clean();
        } else if (operationToPerform.equals(SupportedOperations.update.name())) {
            update();
        }

    }

    @FXML
    void closeButtonAction() {
        Platform.runLater(() -> {
            // get a handle to the stage
            Stage stage = (Stage) controller.getAlertButton().getScene().getWindow();
            close();
            stage.close();
        });
    }

    void close() {
        //Kil the phantomjs process
        Runtime rt = Runtime.getRuntime();
        if (System.getProperty("os.name").toLowerCase().contains("windows")) {
            try {
                rt.exec("taskkill /F /IM phantomjs.exe");
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        } else {
            try {
                rt.exec("pkill -f phantomjs");
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }
        //Remove all the information from the db related to this instance
        DatabaseDriver db = null;
        try {
            db = DatabaseDriver.getInstance(new GUILabelManagement());
            db.removeCrawlerInstance(new Scanner(new File("./AppData/instanceID.dta")).nextLine());
            db.closeConnection();
        } catch (FileNotFoundException | SQLException e1) {
            e1.printStackTrace();
        }
        Platform.exit();
        System.exit(0);
    }

    /**
     * Cleans the database from unused/stuck crawlers
     */
    private void clean() {
        DateTime now = new DateTime();
        //Get all the times
        HashSet<ScrawlerInstance> set = DatabaseDriver.getInstance(guiLabelManagement).getAllInstances();
        HashSet<String> instancesThatShouldBeClosed = new HashSet<>();
        for (ScrawlerInstance instance : set) {

            //If they have no time, it means that they have not been initialized year
            if (instance.getTime() == null) {
                continue;
            }
            //Get the crawlers with more than 1 hour
            if (now.getMillis() - instance.getTime().getMillis() > 60 * 60 * 1000) {
                instancesThatShouldBeClosed.add(instance.getInstanceID());
            }

        }
        //Close all the crawlers that have more than one hour
        for (String instance : instancesThatShouldBeClosed) {
            DatabaseDriver.getInstance(guiLabelManagement).performOperation(instance, SupportedOperations.close.name());
        }
        //Wait for 2 minutes
        try {
            Thread.sleep(120 * 1000);
        } catch (InterruptedException ignored) {
        }
        //Remove all the crawlers manually if they did not close
        set = DatabaseDriver.getInstance(guiLabelManagement).getAllInstances();
        HashSet<String> instancesLeftToBeClosed = new HashSet<>();

        for (ScrawlerInstance instance : set) {

            //If they have no time, it means that they have not been initialized year
            if (instance.getTime() == null) {
                continue;
            }
            //Get the crawlers with more than 1 hour
            if (now.getMillis() - instance.getTime().getMillis() > 60 * 60 * 1000) {
                instancesLeftToBeClosed.add(instance.getInstanceID());
            }
        }

        //Remove them
        for (String instance : instancesLeftToBeClosed) {
            DatabaseDriver.getInstance(guiLabelManagement).removeCrawlerInstance(instance);
        }
        //Remove operation
        try {
            DatabaseDriver.getInstance(guiLabelManagement).performOperation(new Scanner(new File("" +
                    "./AppData/instanceID" +
                    ".txt")).nextLine(), "");
        } catch (FileNotFoundException e) {
            guiLabelManagement.setAlertPopUp(e.getMessage());
        }


    }

    /**
     * Updates the application. Automatically downloads the new version into the local machine.
     */
    void update() {

        guiLabelManagement.setAlertPopUp("A new version of the crawler was just downloaded into the directory " +
                "where this crawler is located. Please close and DELETE this version and open the new version. Note " +
                "that this instance will be closed in 30 minutes");
        String urlStr = "https://github.com/rafaelcastrorc/sCrawler/releases";
        String baseURL = "https://github.com";
        try {
            //Get the current jar name
            String currInstanceName = new java.io.File(WebServer.class.getProtectionDomain().getCodeSource().getLocation()
                    .getPath()).getName();
            if (!currInstanceName.contains(".jar")) {
                currInstanceName = currInstanceName + ".jar";
            }

            //Check if there are any other crawler versions in the current directory, if so, delete all of them
            File[] dir = new File("./").listFiles();
            for (File file :dir) {
                String ext = FilenameUtils.getExtension(file.getAbsolutePath());
                String fileName = file.getName();
                if (!fileName.contains(ext)) {
                    fileName = fileName + "." +ext;
                }
                if (!fileName.equals(currInstanceName) && ext.equals("jar")) {
                    File nFile = new File(fileName);
                    nFile.delete();
                }
            }

            //Find the new version
            Document doc = Jsoup.connect(urlStr).timeout(10 * 1000).userAgent
                    ("Mozilla/5.0 (Windows; U; WindowsNT 5.1; en-US; rv1.8.1.6) " +
                            "Gecko/20070725 Firefox/2.0.0.6").get();
            Pattern pattern = Pattern.compile(".*\\.zip");
            Matcher matcher = pattern.matcher(doc.toString());
            String urlToVisit = "";
            if (matcher.find()) {
                urlToVisit = matcher.group();
                urlToVisit = urlToVisit.replaceAll("<a href=\"", "");
            }
            pattern = Pattern.compile("/.*");
            matcher = pattern.matcher(urlToVisit);
            if (matcher.find()) {
                urlToVisit = matcher.group();
            }
            URL url = new URL(baseURL + urlToVisit);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            InputStream in = connection.getInputStream();

            //Download the new version
            FileOutputStream out = new FileOutputStream("./sCrawler.zip");
            ProxyChanger.copy(in, out);
            out.close();

            //Unzip file
            ProxyChanger.unzip("./sCrawler.zip", "./");
            //Move the files
            File[] files = new File("./sCrawler").listFiles();
            String newVersionName = "";
            for (File file : files) {
                if (!file.isDirectory()) {
                    newVersionName = file.getName();
                    file.renameTo(new File("./" + file.getName()));
                } else {
                    File[] lib = file.listFiles();
                    for (File libFile : lib) {
                        libFile.renameTo(new File("./lib/" + libFile.getName()));
                    }
                }
            }

            if (!newVersionName.contains(".jar")) {
                newVersionName = newVersionName + ".jar";
            }
            FileUtils.deleteDirectory(new File("./sCrawler"));

            //Wait for 30 minutes, if no response open the new crawler and close the old one
            ExecutorService connectionVerifier = Executors.newSingleThreadExecutor(new MyThreadFactory());
            String finalNewVersionName = newVersionName;
            connectionVerifier.submit(() -> {
                try {
                    Thread.sleep(30 * 60 * 1000);
                    // Run a java app in a separate system process
                    Runtime.getRuntime().exec("java -jar "+ finalNewVersionName);
                    //Add the new version to the app dtaa
                    Logger.getInstance().writeLatestVersion(finalNewVersionName);
                    //Close this instance
                    closeButtonAction();
                } catch (InterruptedException ignored) {
                } catch (IOException e) {
                    guiLabelManagement.setAlertPopUp(e.getMessage());
                }
            });
            DatabaseDriver.getInstance(guiLabelManagement).performOperation(new Scanner(new File("" +
                    "./AppData/instanceID.dta")).nextLine(), "");


        } catch (IOException e) {
            guiLabelManagement.setAlertPopUp(e.getMessage());
        }


    }

    /**
     * Verifies that the current scrawler is the latest version, if not, it updates it
     */
    void checkForUpdate() {

    }

    void restart() {

    }

    void downloadMissing() {

    }


    /**
     * Supported operations by the web server
     */
    enum SupportedOperations {
        close, clean, update, restart, download, downloadMissing,
    }

    /**
     * Represents an instance of a crawler
     */
    static class ScrawlerInstance {
        private final String instance;
        private final DateTime time;

        ScrawlerInstance(String instance, DateTime time) {
            this.instance = instance;
            this.time = time;
        }

        String getInstanceID() {
            return instance;
        }

        DateTime getTime() {
            return time;
        }
    }


}
