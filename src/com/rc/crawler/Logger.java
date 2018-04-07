package com.rc.crawler;


import org.joda.time.DateTime;
import org.openqa.selenium.Cookie;

import java.io.*;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.sql.SQLException;
import java.util.*;
import java.util.prefs.Preferences;


/**
 * Logger class to write to the different output files. Uses singleton pattern.
 */
class Logger {
    private static Logger instance;
    private static BufferedWriter reportWriter;
    private static BufferedWriter listOfFinishedPapers;
    private static BufferedWriter filesNotDownloaded;
    private static BufferedWriter listOfFilesToDownload;
    private static BufferedWriter cookieFile;
    private static String prevName = "";
    private static Preferences preferencesDB = Preferences.userNodeForPackage(DatabaseDriver.class);
    private static Preferences preferencesSCrawlerW = Preferences.userNodeForPackage(SCrawlerWeb.class);

    private Logger() {
    }

    /**
     * Gets an instance of the Logger. Logs the current instance id when first called.
     *
     * @return Logger
     */
    static Logger getInstance() {
        if (instance == null) {
            // Log an instance id for the current crawler, which will be used by the db
            try {
                File file = new File("./AppData/instanceID.dta");
                //Check if there was a previous instance name, and eliminate any records that might still be in the
                try {
                    Scanner scanner = new Scanner(file);
                    prevName = scanner.nextLine();
                } catch (FileNotFoundException ignored) {
                }

                BufferedWriter instanceWriter = new BufferedWriter(new FileWriter(file));
                UUID idOne = UUID.randomUUID();
                instanceWriter.write(String.valueOf(idOne));
                instanceWriter.flush();

            } catch (IOException e) {
                e.printStackTrace();
            }

            instance = new Logger();
        }
        return instance;
    }

    /**
     * Gets the previous name associated to this instance, if there was any
     *
     * @return String
     */
    String getPrevName() {
        return prevName;
    }


    /**
     * Returns the current instance ID
     */
    String getInstanceID() throws FileNotFoundException {
        File file = new File("./AppData/instanceID.dta");
        if (!file.exists()) {
            throw new FileNotFoundException();
        }
        Scanner scanner = new Scanner(file);
        return scanner.nextLine();
    }

    /**
     * Writes the latest sCrawler version that this directory has
     */
    void writeLatestVersion(String version) {
        try {
            File file = new File("./AppData/Version.dta");
            BufferedWriter versionWriter = new BufferedWriter(new FileWriter(file));
            versionWriter.write(version);
            versionWriter.flush();
        } catch (IOException ignored) {
        }
    }

    /**
     * Returns the latest sCrawler version that this directory has
     */
    String getVersion() {
        try {
            File file = new File("./AppData/Version.dta");
            Scanner scanner = new Scanner(file);
            if (scanner.hasNextLine()) {
                return scanner.nextLine();
            }
        } catch (IOException ignored) {
        }
        return "";
    }


    /**
     * Writes the restartFile
     */
    void writeRestartFile(ArrayList<String> restartArgs) throws IllegalArgumentException {
        try {
            File file = new File("./AppData/Restart.dta");
            BufferedWriter restartWriter = new BufferedWriter(new FileWriter(file));
            for (String s : restartArgs) {
                restartWriter.write(s);
                restartWriter.newLine();

            }
            restartWriter.flush();
        } catch (IOException e) {
            e.printStackTrace();
            throw new IllegalArgumentException();
        }
    }

    /**
     * Returns the latest sCrawler version that this directory has
     */
    ArrayList<String> getRestartFile() {
        ArrayList<String> res = new ArrayList<>();
        try {
            File file = new File("./AppData/Restart.dta");
            Scanner scanner = new Scanner(file);
            while (scanner.hasNextLine()) {
                 res.add(scanner.nextLine());
            }
        } catch (IOException ignored) {
        }
        return res;
    }

     void deleteRestartFile() {
         File file = new File("./AppData/Restart.dta");
         file.delete();
     }




    /**
     * Sets the file to be used to write the report.
     *
     * @param append   true if we are writing over an existing report, false otherwise.
     * @param location folder location where the report should be stored.
     * @throws IOException unable to write to file.
     */
    void setReportWriter(boolean append, String location) throws IOException {
        if (append) {
            reportWriter = new BufferedWriter(new FileWriter("./DownloadedPDFs/" + location + ".txt", true));
        } else {
            reportWriter = new BufferedWriter(new FileWriter("./DownloadedPDFs/" + location + ".txt", false));
        }
    }

    /**
     * Writes the report
     *
     * @param s String to write
     * @throws IOException Error writing to file
     */
    void writeReport(String s) throws IOException {
        try {
            reportWriter.write(s);
            reportWriter.flush();
        } catch (IOException e) {
            throw new IOException("Cannot write to file");

        }
    }


    /**
     * Sets the file to write the papers that have been downloaded
     *
     * @param append if the file already exists and is valid, then append is true.
     * @throws IOException unable to write to file
     */
    void setListOfFinishedPapers(boolean append) throws IOException {
        if (append) {
            listOfFinishedPapers = new BufferedWriter(new FileWriter("./AppData/CompletedDownloads.txt", true));
        } else {
            try {
                File file = new File("./AppData/CompletedDownloads.txt");
                listOfFinishedPapers = new BufferedWriter(new FileWriter(file));
            } catch (IOException e) {
                throw new IOException("Unable to create list of completed downloads");
            }
        }
    }

    /**
     * Writes to the list of working proxies.
     *
     * @param s String to write
     * @throws IOException Unable to write to file
     */
    void writeToListOfFinishedPapers(String s) throws IOException {
        try {
            listOfFinishedPapers.write(s);
            listOfFinishedPapers.flush();
        } catch (IOException e) {
            throw new IOException("Cannot write to file");
        }
    }


    /**
     * Writes to the list of papers that could not be downloaded
     *
     * @param s String to write
     * @throws IOException Unable to write to file
     */
    void writeToFilesNotDownloaded(String s) throws IOException {
        try {
            filesNotDownloaded.write(s);
            filesNotDownloaded.flush();
        } catch (IOException e) {
            throw new IOException("Cannot write to file");
        }
    }


    /**
     * Sets the file to write the papers that have NOT been downloaded
     *
     * @param append if the file already exists and is valid, then append is true.
     * @throws IOException unable to write to file
     */
    void setListOfNotDownloadedPapers(boolean append) throws IOException {
        if (append) {
            filesNotDownloaded = new BufferedWriter(new FileWriter("./DownloadedPDFs/FilesNotDownloaded.txt", true));
        } else {
            try {
                File file = new File("./DownloadedPDFs/FilesNotDownloaded.txt");
                filesNotDownloaded = new BufferedWriter(new FileWriter(file));
            } catch (IOException e) {
                throw new IOException("Unable to create list of files not downloaded");
            }
        }
    }


    /**
     * Writes to the new list of files to download
     *
     * @param s String to write
     * @throws IOException Unable to write to file
     */
    void writeToFilesToDownload(String s) throws IOException {
        try {
            listOfFilesToDownload.write(s);
            listOfFilesToDownload.flush();
        } catch (IOException e) {
            throw new IOException("Cannot write to file");
        }
    }


    /**
     * Creates a new list of files to download
     *
     * @throws IOException If unable to create file throws exception
     */
    void setListOfFilesToDownload() throws IOException {
        DateTime time = new DateTime();
        String title = "FilesToDownload_" + time.toDate() + ".txt";
        try {
            File file = new File("./DownloadedPDFs/" + title);
            listOfFilesToDownload = new BufferedWriter(new FileWriter(file));
        } catch (IOException e) {
            throw new IOException("Unable to create list of files to download");
        }
    }

    /**
     * Adds a file that was not found to the report, the list of files not found, and to the list of completed downloads
     *
     * @param file            File that was not found
     * @param originalArticle Query inputted by the user
     * @param typeOfSearch    Type of search used
     */
    void writeToLogFileNotFound(File file, String originalArticle, String typeOfSearch, boolean isMultipleSearch) {
        try {
            //Add to the report
            setReportWriter(true, "Report");
            writeReport("\n-Could not find paper (" + typeOfSearch + "): " + originalArticle + "\n");
            //Add to list of files not downloaded
            if (file.exists() && file.canRead()) {
                setListOfNotDownloadedPapers(true);
            } else {
                setListOfNotDownloadedPapers(false);
            }
            writeToFilesNotDownloaded("\n" + originalArticle + " - Error: File was not found (" + typeOfSearch + ")");

            //Add to list of finished downloads
            file = new File("./AppData/CompletedDownloads.txt");
            if (file.exists() && file.canRead()) {
                setListOfFinishedPapers(true);
            } else {
                setListOfFinishedPapers(false);
            }
            if (isMultipleSearch) {
                writeToListOfFinishedPapers("\n" + "[" + typeOfSearch + "]" + originalArticle);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Adds a file that was not downloaded by the user (in the Search Result Window) to the list of files not found and
     * to the list of completed downloads
     *
     * @param file            File that was not found
     * @param originalArticle Query inputted by the user
     * @param typeOfSearch    Type of search used
     */
    void fileNotDownloadedSearchResultWindow(File file, String originalArticle, String typeOfSearch, boolean
            isMultipleSearch) {
        try {
            if (file.exists() && file.canRead()) {

                setListOfNotDownloadedPapers(true);
            } else {
                setListOfNotDownloadedPapers(false);
            }
            writeToFilesNotDownloaded("\n" + originalArticle + " - Error: File was not " +
                    "found (Search Window) (" + typeOfSearch + ")");

            file = new File("./AppData/CompletedDownloads.txt");
            if (file.exists() && file.canRead()) {
                setListOfFinishedPapers(true);
            } else {
                setListOfFinishedPapers(false);
            }
            if (isMultipleSearch) {
                writeToListOfFinishedPapers("\n" + "[" + typeOfSearch + "]" + originalArticle);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Sets the file to write the cookies that have been used to unlock a proxy
     *
     * @param append if the file already exists and is valid, then append is true.
     * @throws IOException unable to write to file
     */
    void setCookieFile(boolean append) throws IOException {
        if (append) {
            cookieFile = new BufferedWriter(new FileWriter("./AppData/Cookies.dta", true));
        } else {
            try {
                File file = new File("./AppData/Cookies.dta");
                cookieFile = new BufferedWriter(new FileWriter(file));
            } catch (IOException e) {
                throw new IOException("Unable to create cookie file");
            }
        }
    }

    /**
     * Adds a cookie to the Cookies.dta file and the database file
     *  @param cookies Set of cookies
     * @param proxy   proxy that was unlocked
     */
    synchronized void writeToCookiesFile(Set<Cookie> cookies, Proxy proxy, SearchEngine.SupportedSearchEngine engine,
                                         OutsideServer server, StatsGUI stats)
            throws IOException, SQLException {
        StringBuilder sb = new StringBuilder();
        StringBuilder sb2 = new StringBuilder();

        sb.append("Proxy: ").append(proxy.getProxy()).append(":").append(proxy.getPort()).append("\n");
        sb.append("Search Engine: ").append(engine).append("\n");
        for (Cookie cookie : cookies) {
            sb.append(cookie.getName()).append(";").append(cookie.getValue()).append(";").append(cookie.getDomain())
                    .append(";").append(cookie.getPath()).append(";").append(cookie
                    .getExpiry()).append(";").append(cookie.isSecure()).append("\n");
            sb2.append(cookie.getName()).append(";").append(cookie.getValue()).append(";").append(cookie.getDomain())
                    .append(";").append(cookie.getPath()).append(";").append(cookie
                    .getExpiry()).append(";").append(cookie.isSecure()).append("\n");
        }
        if (cookies.size() > 0) {
            //Only write if there are cookies
            cookieFile.write(sb.toString());
            cookieFile.flush();
        }
        //Write to db
        server.addUnlockedProxy(proxy, sb2.toString(), engine, stats);
    }

    /**
     * Retrieves all the cookies from the database
     */
    HashMap<Proxy, Map<SearchEngine.SupportedSearchEngine, Set<Cookie>>> readCookieFileFromDB(GUILabelManagement
                                                                                                      guiLabels)
            throws FileNotFoundException, SQLException {

        return OutsideServer.getInstance(guiLabels).getAllUnlockedProxies();
    }

    /**
     * Retrieves all the cookies stored locally and uploads them to the database
     */
    HashMap<Proxy, Map<SearchEngine.SupportedSearchEngine, Set<Cookie>>> readCookieFileFromLocal(
            GUILabelManagement guiLabels, StatsGUI stats) throws FileNotFoundException, SQLException {
        HashMap<Proxy, Map<SearchEngine.SupportedSearchEngine, Set<Cookie>>> result = new HashMap<>();
        Scanner scanner = new Scanner(new File("./AppData/Cookies.dta"));
        Proxy currProxy = null;
        SearchEngine.SupportedSearchEngine engine = null;
        OutsideServer server =  OutsideServer.getInstance(guiLabels);
        Set<Cookie> cookies = new HashSet<>();
        try {
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                if (line.contains("Proxy: ")) {
                    //If there are cookies, we have already gone through at least one proxy, so we add it to the map.
                    if (!cookies.isEmpty()) {
                        //Check if map already contains the proxy. If it does, replace it
                        SCrawlerWeb.getCookiesFromString(result, engine, cookies, currProxy);
                        StringBuilder sb = new StringBuilder();
                        for (Cookie cookie : cookies) {
                            sb.append(cookie.getName()).append(";").append(cookie.getValue()).append(";").append
                                    (cookie.getDomain())
                                    .append(";").append(cookie.getPath()).append(";").append(cookie
                                    .getExpiry()).append(";").append(cookie.isSecure()).append("\n");
                        }
                        server.addUnlockedProxy(currProxy, sb.toString(), engine, stats);
                        cookies = new HashSet<>();
                    }
                    line = line.replace("Proxy: ", "");
                    //Get the proxy
                    String[] proxy = line.split(":");
                    String proxyNum = proxy[0];
                    String proxyPort = proxy[1];
                    currProxy = new Proxy(proxyNum, Integer.valueOf(proxyPort));
                } else if (line.contains("Search Engine")) {
                    //Find the search engine used
                    line = line.replaceAll("Search Engine: ", "");
                    if (line.equalsIgnoreCase(SearchEngine.SupportedSearchEngine.GoogleScholar.name())) {
                        engine = SearchEngine.SupportedSearchEngine.GoogleScholar;
                    }
                    if (line.equalsIgnoreCase(SearchEngine.SupportedSearchEngine.MicrosoftAcademic.name())) {
                        engine = SearchEngine.SupportedSearchEngine.MicrosoftAcademic;
                    }
                } else {
                    //Get the cookie
                    String[] cookieInfo = line.split(";");
                    String name = cookieInfo[0];
                    String value = cookieInfo[1];
                    String domain = cookieInfo[2];
                    String path = cookieInfo[3];
                    Date expiry = null;
                    if (cookieInfo[4] != null && !cookieInfo[4].equals("null")) {
                        expiry = new Date(cookieInfo[4]);
                    }
                    Boolean isSecure = Boolean.valueOf(cookieInfo[4]);
                    Cookie ck = new Cookie(name, value, domain, path, expiry, isSecure);
                    cookies.add(ck);
                }
            }
            if (!cookies.isEmpty()) {
                SCrawlerWeb.getCookiesFromString(result, engine, cookies, currProxy);

            }
        } catch (NullPointerException e) {
            throw new IllegalArgumentException("The cookies file is not formatted correctly, please revise it");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }


    /**
     * Retrieves all the cookies stored in github and uploads them to the database
     */
    HashMap<Proxy, Map<SearchEngine.SupportedSearchEngine, Set<Cookie>>> downloadCookiesFromGithub(GUILabelManagement
                                                                                                           guiLabels,
                                                                                                   StatsGUI stats)
            throws IOException, SQLException {
        String downloadLink = "https://raw.githubusercontent.com/rafaelcastrorc/sCrawler/master/AppData/Cookies.dta";
        URL website = new URL(downloadLink);
        ReadableByteChannel rbc = Channels.newChannel(website.openStream());
        FileOutputStream fos = new FileOutputStream("./AppData/Cookies.dta");
        fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);

        return readCookieFileFromLocal(guiLabels, stats);
    }


    /**
     * Stores the user db information
     */
    void saveUserDBData(boolean doNotShowThisAgain, String serverAddress, String port,
                        String databaseName, String username, String password) {
        preferencesDB.putBoolean("doNotShowThisAgain", doNotShowThisAgain);
        preferencesDB.put("serverAddress", serverAddress);
        preferencesDB.put("port", port);
        preferencesDB.put("databaseName", databaseName);
        preferencesDB.put("username", username);
        preferencesDB.put("password", password);
    }

    /**
     * Retrieves the user database information stored in the preference file
     */
    ArrayList readUserDBData() throws IllegalArgumentException {
        ArrayList list = new ArrayList<>();
        try {
            list.add(preferencesDB.getBoolean("doNotShowThisAgain", false));
            list.add(preferencesDB.get("serverAddress", null));
            list.add(preferencesDB.get("port", null));
            list.add(preferencesDB.get("databaseName", null));
            list.add(preferencesDB.get("username", null));
            list.add(preferencesDB.get("password", null));
        } catch (IllegalStateException | NullPointerException e) {
            throw new IllegalArgumentException();
        }
        return list;
    }

    /**
     * Stores the user sCrawler w login
     */
    void saveUserLoginData(boolean doNotShowThisAgain, String email, String password) {
        preferencesSCrawlerW.putBoolean("doNotShowThisAgain", doNotShowThisAgain);
        preferencesSCrawlerW.put("email", email);
        preferencesSCrawlerW.put("password", password);
    }

    /**
     * Retrieves the user database information stored in the preference file
     */
    ArrayList readUserSCrawlerWData() throws IllegalArgumentException {
        ArrayList list = new ArrayList<>();
        try {
            list.add(preferencesSCrawlerW.getBoolean("doNotShowThisAgain", false));
            list.add(preferencesSCrawlerW.get("email", null));
            list.add(preferencesSCrawlerW.get("password", null));
        } catch (IllegalStateException | NullPointerException e) {
            throw new IllegalArgumentException();
        }
        return list;
    }

}
