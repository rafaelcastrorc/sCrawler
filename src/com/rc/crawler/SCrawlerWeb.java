package com.rc.crawler;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.joda.time.DateTime;
import org.json.JSONArray;
import org.json.JSONObject;
import org.openqa.selenium.Cookie;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;


/**
 * Created by rafaelcastro on 9/7/17.
 * Handles how an instance interacts with the sCrawler W website
 */
class SCrawlerWeb {
    private static SCrawlerWeb SCrawlerWeb;
    private static GUILabelManagement guiLabelManagement;
    private static String instance;
    private static boolean userWantsToUseWebsite;
    private static String email;
    private static String password;
    private static String errorMessage;
    //Current URL where sCrawler W is hosted
    // static final String SCRAWLERWURL = "https://scrawler-web.herokuapp.com";
    static final String SCRAWLERWURL = "http://localhost:3000";


    private SCrawlerWeb() {
    }

    static SCrawlerWeb getInstance(GUILabelManagement guiLabelManagement) {
        if (SCrawlerWeb == null) {
            SCrawlerWeb = new SCrawlerWeb();
            com.rc.crawler.SCrawlerWeb.guiLabelManagement = guiLabelManagement;
            try {
                instance = Logger.getInstance().getInstanceID();
            } catch (FileNotFoundException e) {
                guiLabelManagement.setAlertPopUp("Unable to find instance id, the application will be closed!");
                WebServer.getInstance(guiLabelManagement).closeButtonAction(false);
            }
            displayMainMenuSCrawlerW();
            if (!userWantsToUseWebsite) {
                return null;
            }
            //IMPORTANT: Note that SCrawler W assumes that the tables already exist!!! If they don't, you need to
            // manually configure a db first
            return SCrawlerWeb;
        } else {
            return SCrawlerWeb;
        }
    }


    /**
     * Displays the main menu for the user to choose how to connect to the website, and gives them the option to
     * manually configure
     */
    private static void displayMainMenuSCrawlerW() {
        boolean thereWasAnError = false;
        String message = "";
        Logger logger = Logger.getInstance();
        //Check for the user preferences
        boolean doNotShowThisAgain = false;
        boolean isApplicationRestarting = Restart.isApplicationRestarting();
        try {
            doNotShowThisAgain = (boolean) logger.readUserSCrawlerWData().get(0);
        } catch (IllegalArgumentException ignored) {
        }
        //If it does not need to display the menu bc of user preferences, or bc the application is restarting, then
        // just skip it.
        if (doNotShowThisAgain || isApplicationRestarting) {
            //Verify if you can connect to the db using the stored settings
            if (!readLoginData()) {
                thereWasAnError = true;
                message = "Unable to connect to sCrawler W using your stored setting.";
                //If there is an error connecting, then restart process won't work so stop it
                Restart.failedToRestart();
            } else {
                //If connection works, then do not show menu and just return
                return;
            }
        }
        // Create the custom dialog.
        Dialog<Object> dialog = new Dialog<>();
        dialog.setTitle("Configure your connection");
        dialog.setHeaderText("" +
                "In order for all the crawlers to be synchronized, all of them need to access the " +
                "same database.\n" +
                "Do you want to use sCrawler W to help you control all your instances, or use " +
                "your own database?");

        // Set the button types.
        ButtonType useSCrawlerW = new ButtonType("Use sCrawler W");
        ButtonType connectToNew = new ButtonType("Use my own database");

        dialog.getDialogPane().getButtonTypes().addAll(useSCrawlerW, connectToNew);

        // Create the username and password labels and fields.
        VBox vBox = new VBox(20);
        vBox.setAlignment(Pos.CENTER_LEFT);

        //Set the GUI information
        Label promo = new Label("" +
                "sCrawler W simplifies how you manage all your instances and it lets you control them online.\n" +
                "You can learn more at: " + SCRAWLERWURL);

        //Set the GUI
        if (!thereWasAnError) {
            vBox.getChildren().addAll(promo);
        } else {
            Label error = new Label(message);
            error.setTextFill(Color.RED);
            vBox.getChildren().addAll(error, promo);

        }

        dialog.getDialogPane().setContent(vBox);
        final Button useSCrawlerWButton = (Button) dialog.getDialogPane().lookupButton(useSCrawlerW);
        final Button connectToNewButton = (Button) dialog.getDialogPane().lookupButton(connectToNew);

        useSCrawlerWButton.addEventFilter(
                ActionEvent.ACTION,
                event -> {
                    //If it fails to read the configuration data, then let the user manually input the info
                    dialog.close();
                    if (!readLoginData()) {
                        showDialogToLoginToWebsite();
                    }

                }
        );
        connectToNewButton.addEventFilter(
                ActionEvent.ACTION,
                event -> {
                    dialog.close();
                    //If this happens, then start the DatabaseDriver instead
                    userWantsToUseWebsite = false;
                }
        );
        dialog.showAndWait();
    }


    /**
     * Reads the locally stored sCrawler W login credentials. Returns false if it is unable to connect to the website
     */
    private static boolean readLoginData() {
        //Just read the login information already stored in the computer
        Logger logger = Logger.getInstance();
        ArrayList list;
        try {
            list = logger.readUserSCrawlerWData();
        } catch (IllegalArgumentException e) {
            guiLabelManagement.setAlertPopUp("There is no information about your sCrawler W login credentials" +
                    ".\nPlease manually sign in from here. " +
                    "\nIf you do not have an account, go to " + SCRAWLERWURL + " and register");
            return false;
        }
        String email = (String) list.get(1);
        String password = String.valueOf(list.get(2));
        //If there is no connection, prompt user with dialog box again
        if (!verifyIfConnectionWorks(email, password)) {
            guiLabelManagement.setAlertPopUp("Unable to connect to the database using your stored settings." +
                    "\nPlease manually set it up");
            return false;
        }
        userWantsToUseWebsite = true;
        return true;
    }


    /**
     * Displays a GUI with the different database configuration options
     */
    private static void showDialogToLoginToWebsite() {
        final boolean[] connected = {false};
        // Create the custom dialog.
        Alert dialog = new Alert(Alert.AlertType.NONE);
        dialog.setTitle("Database Configuration Details");
        dialog.setHeaderText("ERROR: Unable to connect to sCrawler W using your stored settings!" +
                "\nPlease manually log in. " +
                "\nIf you do not have an account go to: \n" +
                "" + SCRAWLERWURL + " to register.");

        // Set the button types.
        ButtonType loginButtonType = new ButtonType("Log in", ButtonBar.ButtonData.OK_DONE);
        ButtonType storeConnection = new ButtonType("Store Credentials & Continue", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(loginButtonType, storeConnection);

        // Create the username and password labels and fields.
        VBox vBox = new VBox(21);
        vBox.setAlignment(Pos.CENTER_LEFT);

        HBox hBox4 = new HBox(52);
        hBox4.setAlignment(Pos.CENTER_LEFT);
        Label label4 = new Label("Email:");
        TextField email = new TextField();
        email.setPromptText("Email");
        hBox4.getChildren().addAll(label4, email);

        HBox hBox5 = new HBox(55);
        hBox5.setAlignment(Pos.CENTER_LEFT);
        Label label5 = new Label("Password:");
        PasswordField password = new PasswordField();
        password.setPromptText("Password");
        hBox5.getChildren().addAll(label5, password);

        Label errorLabel = new Label();
        errorLabel.setWrapText(true);
        HBox hBox6 = new HBox();
        hBox6.setAlignment(Pos.CENTER_LEFT);
        hBox6.getChildren().addAll(errorLabel);


        vBox.getChildren().addAll(hBox4, hBox5, hBox6);

        dialog.getDialogPane().setContent(vBox);

        final Button loginButtonDB = (Button) dialog.getDialogPane().lookupButton(loginButtonType);
        final Button storeConnectionDB = (Button) dialog.getDialogPane().lookupButton(storeConnection);

        loginButtonDB.setDefaultButton(true);
        storeConnectionDB.setDisable(true);

        loginButtonDB.addEventFilter(
                ActionEvent.ACTION,
                event -> {
                    if (password.getText().isEmpty() || email.getText().isEmpty()) {
                        guiLabelManagement.setAlertPopUp("Please fill out all the fields");
                    } else {
                        connected[0] = verifyIfConnectionWorks(email.getText(), password.getText()
                        );
                        storeConnectionDB.setDisable(!connected[0]);
                        if (!connected[0]) {
                            errorLabel.setText(errorMessage);
                            errorLabel.setTextFill(Color.RED);
                        } else {
                            errorLabel.setText("Connected!");
                            errorLabel.setTextFill(Color.GREEN);
                        }
                    }

                    event.consume();
                }
        );
        storeConnectionDB.addEventFilter(
                ActionEvent.ACTION,
                event -> {
                    //Store the data
                    Logger logger = Logger.getInstance();
                    //Todo: Change the doNotShowThisAgain
                    logger.saveUserLoginData(false, email.getText(), password.getText());
                    userWantsToUseWebsite = true;
                    dialog.close();

                }
        );
        // Request focus on the username field by default.
        Platform.runLater(email::requestFocus);

        //Check the type of button that was pressed
        dialog.showAndWait();


    }


    /**
     * Verifies if the user can connect to the website, and if his login credentials work
     *
     * @param email    Email of the user
     * @param password Password of the user
     */

    private static boolean verifyIfConnectionWorks(String email, String password) {
        try {
            String urlStr = SCRAWLERWURL + "/api_scrawlers/login";
            HashMap<String, Object> parameters = new HashMap<>();
            parameters.put("username", email);
            parameters.put("password", password);
            JSONObject result = new JSONObject(SCrawlerWRequests.postRequest(urlStr, parameters));
            if (result.getBoolean("success")) {
                com.rc.crawler.SCrawlerWeb.email = email;
                com.rc.crawler.SCrawlerWeb.password = password;
                return true;
            } else {
                errorMessage = result.getString("message");
                return false;

            }

        } catch (Exception e) {
            errorMessage = e.getMessage();
            return false;
        }
    }

    /**
     * Returns all proxies stored in the db
     *
     * @return HashMap with all the proxies
     */
    HashMap<Proxy, Map<SearchEngine.SupportedSearchEngine, Set<Cookie>>> getAllUnlockedProxies() {
        HashMap<Proxy, Map<SearchEngine.SupportedSearchEngine, Set<Cookie>>> result = new HashMap<>();
        try {
            String urlStr = SCRAWLERWURL + "/api_scrawlers/unlocked_proxies";
            HashMap<String, Object> parameters = new HashMap<>();
            parameters.put("username", email);
            parameters.put("password", password);
            JSONArray jsonMainArr = new JSONArray(SCrawlerWRequests.postRequest(urlStr, parameters));
            //Iterate through the array of cookies
            for (int i = 0; i < jsonMainArr.length(); i++) {
                JSONObject currentCookie = jsonMainArr.getJSONObject(i);
                SearchEngine.SupportedSearchEngine engine = null;
                Set<Cookie> cookies = new HashSet<>();
                //Get data from database
                String ip = currentCookie.getString("ip");
                int port = currentCookie.getInt("port");
                String cookieString = currentCookie.getString("cookies");
                String searchEngine = currentCookie.getString("search_engine");

                Proxy currProxy = new Proxy(ip, port);
                if (searchEngine.equalsIgnoreCase(SearchEngine.SupportedSearchEngine.GoogleScholar.name())) {
                    engine = SearchEngine.SupportedSearchEngine.GoogleScholar;
                }
                if (searchEngine.equalsIgnoreCase(SearchEngine.SupportedSearchEngine.MicrosoftAcademic.name())) {
                    engine = SearchEngine.SupportedSearchEngine.MicrosoftAcademic;
                }
                Scanner scanner = new Scanner(cookieString);
                while (scanner.hasNextLine()) {
                    String line = scanner.nextLine();
                    String[] cookieInfo = line.split(";");
                    String name = cookieInfo[0];
                    String value = cookieInfo[1];
                    String domain = cookieInfo[2];
                    String path = cookieInfo[3];
                    java.util.Date expiry = null;
                    if (cookieInfo[4] != null && !cookieInfo[4].equals("null")) {
                        expiry = new java.util.Date(cookieInfo[4]);
                    }
                    Boolean isSecure = Boolean.valueOf(cookieInfo[4]);
                    //If the expiry is in this format, then its wrong.
                    if (expiry != null && expiry.toString().contains("1970")) {
                        expiry = null;
                        isSecure = true;
                    }
                    if (expiry != null) System.out.println(expiry);
                    Cookie ck = new Cookie(name, value, domain, path, expiry, isSecure);
                    cookies.add(ck);
                }
                getCookiesFromString(result, engine, cookies, currProxy);
            }

        } catch (Exception e) {
            guiLabelManagement.setAlertPopUp(e.getMessage());
        }

        return result;
    }


    static void getCookiesFromString(HashMap<Proxy, Map<SearchEngine.SupportedSearchEngine, Set<Cookie>>> result,
                                     SearchEngine.SupportedSearchEngine engine, Set<Cookie> cookies, Proxy currProxy) {
        if (!result.containsKey(currProxy)) {
            Map<SearchEngine.SupportedSearchEngine, Set<Cookie>> map = new HashMap<>();
            map.put(engine, cookies);
            result.put(currProxy, map);
        } else {
            Map<SearchEngine.SupportedSearchEngine, Set<Cookie>> map = result.get(currProxy);
            map.put(engine, cookies);
            result.put(currProxy, map);
        }
    }

    /**
     * Verifies if proxy can be used (there are less than 3 instances using it at the time)
     *
     * @param proxy Proxy to use
     * @return boolean
     */
    boolean canUseProxy(Proxy proxy) {
        try {
            String urlStr = SCRAWLERWURL + "/api_scrawlers/is_unlocked/";
            ArrayList<String> params = new ArrayList<>();
            params.add(proxy.getProxy());
            params.add(String.valueOf(proxy.getPort()));
            JSONObject result = new JSONObject(SCrawlerWRequests.getRequest(urlStr, params));
            //If there are no records about this proxy in our db, then no crawler can be using it at the moment
            if (result.isNull("unlocked")) {
                //If there are no records
                return true;
            }
            //Check if the current proxy is already using it.
            if (isCurrentInstanceUsingProxy(proxy)) return true;

            //Check if the proxy is unlocked
            if (result.getBoolean("unlocked") && result.getInt("num_of_instances") < 3) {
                return true;
            }
        } catch (Exception e) {
            guiLabelManagement.setAlertPopUp(e.getMessage());
            e.printStackTrace();
        }
        return false;

    }

    /**
     * Inserts an unlocked proxy into the database, if it already exists it updates the value
     *
     * @param proxy Proxy that is unlocked
     */
    void addUnlockedProxy(Proxy proxy, String cookies, SearchEngine.SupportedSearchEngine searchEngine, StatsGUI
            stats) {
        //Add it back to the list of proxies
        try {
            addProxyToListOfProxies(proxy);
        } catch (IllegalArgumentException ignored) {
        }
        try {
            String urlStr = SCRAWLERWURL + "/api_scrawlers/add_unlocked_proxy";
            HashMap<String, Object> parameters = new HashMap<>();
            parameters.put("username", email);
            parameters.put("password", password);
            parameters.put("ip", proxy.getProxy());
            parameters.put("port", String.valueOf(proxy.getPort()));
            parameters.put("cookies", cookies);
            //Try finding the search engine, if not default is GS
            try {
                parameters.put("search_engine", searchEngine.name());
            } catch (NullPointerException e) {
                parameters.put("search_engine", "GoogleScholar");
            }
            JSONObject result = new JSONObject(SCrawlerWRequests.postRequest(urlStr, parameters));
            stats.updateNumberOfUnlocked(stats.getNumberOfUnlockedProxies().get() + 1);
            if (!result.getBoolean("success")) {
                errorMessage = result.getString("message");
                guiLabelManagement.setAlertPopUp(errorMessage);
            }
        } catch (Exception e) {
            guiLabelManagement.setAlertPopUp(e.getMessage());
        }

    }


    /**
     * Inserts a locked proxy into the database
     *
     * @param proxy Proxy that is unlocked
     */

    void addLockedProxy(Proxy proxy) {
        try {
            String urlStr = SCRAWLERWURL + "/api_scrawlers/add_unlocked_proxy";
            HashMap<String, Object> parameters = new HashMap<>();
            parameters.put("username", email);
            parameters.put("password", password);
            parameters.put("ip", proxy.getProxy());
            parameters.put("port", String.valueOf(proxy.getPort()));
            JSONObject result = new JSONObject(SCrawlerWRequests.postRequest(urlStr, parameters));
            if (!result.getBoolean("success")) {
                errorMessage = result.getString("message");
                guiLabelManagement.setAlertPopUp(errorMessage);
            }
        } catch (Exception e) {
            guiLabelManagement.setAlertPopUp(e.getMessage());
        }
    }


    /**
     * Notifies the db that the current instance is using a proxy
     */
    void addProxyToCurrentInstance(Proxy proxy) {
        try {
            String urlStr = SCRAWLERWURL + "/api_scrawlers/add_proxy_to_instance";

            //If the current instance is not using this proxy, then update the database
            if (!isCurrentInstanceUsingProxy(proxy)) {
                HashMap<String, Object> parameters = new HashMap<>();
                parameters.put("username", email);
                parameters.put("password", password);
                parameters.put("id", instance);
                parameters.put("ip", proxy.getProxy());
                parameters.put("port", String.valueOf(proxy.getPort()));
                JSONObject result = new JSONObject(SCrawlerWRequests.postRequest(urlStr, parameters));
                checkForDuplicateEntry(result);
            }
        } catch (IllegalArgumentException e2) {
            throw new IllegalArgumentException();
        } catch (Exception e) {
            guiLabelManagement.setAlertPopUp(e.getMessage());
        }
    }


    /**
     * Check if the current proxy is already being used by the current instance
     *
     * @return boolean
     */
    boolean isCurrentInstanceUsingProxy(Proxy proxy) {
        try {
            String urlStr = SCRAWLERWURL + "/api_scrawlers/is_using_proxy/";
            ArrayList<String> params = new ArrayList<>();
            params.add(proxy.getProxy());
            params.add(String.valueOf(proxy.getPort()));
            JSONObject result = new JSONObject(SCrawlerWRequests.getRequest(urlStr, params));


            //If there are no records about this proxy in our db, then no crawler can be using it at the moment
            if (result.isNull("scrawler_id")) {
                return false;
            }
            //Process result, if the id appears, then the current crawler is already using it
            return (result.getString("scrawler_id").equals(instance));

        } catch (IOException e) {
            guiLabelManagement.setAlertPopUp(e.getMessage());
        }
        return false;
    }


    /**
     * Adds an instance of the current crawler to the database
     */
    void addCrawlerInstance() {
        //Map current instance to the proxy
        try {
            String urlStr = SCRAWLERWURL + "/api_scrawlers/add_instance";
            HashMap<String, Object> parameters = new HashMap<>();
            parameters.put("username", email);
            parameters.put("password", password);
            parameters.put("id", instance);
            parameters.put("location", "" + getClass().getProtectionDomain().getCodeSource().getLocation());
            Calendar calendar = Calendar.getInstance();
            java.sql.Timestamp timestamp = new java.sql.Timestamp(calendar.getTime().getTime());
            //Adds the time whenn the website was visited
            parameters.put("started", timestamp);
            JSONObject result = new JSONObject(SCrawlerWRequests.postRequest(urlStr, parameters));
            if (!result.getBoolean("success")) {
                errorMessage = result.getString("message");
                guiLabelManagement.setAlertPopUp(errorMessage);
            }
        } catch (Exception e) {
            e.printStackTrace();
            guiLabelManagement.setAlertPopUp(e.getMessage());
        }
    }


    /**
     * Removes an instance of the current crawler from the database
     */
    void removeCrawlerInstance(String instance) {
        try {
            //Get all the proxies that are currently using the crawler, and decrease the count
            String urlStr = SCRAWLERWURL + "/api_scrawlers/remove_instance";
            HashMap<String, Object> parameters = new HashMap<>();
            parameters.put("username", email);
            parameters.put("password", password);
            parameters.put("id", instance);
            JSONObject result = new JSONObject(SCrawlerWRequests.postRequest(urlStr, parameters));
            if (!result.getBoolean("success")) {
                errorMessage = result.getString("message");
                guiLabelManagement.setAlertPopUp(errorMessage);
            }
        } catch (Exception e) {
            e.printStackTrace();
            guiLabelManagement.setAlertPopUp(e.getMessage());
        }
    }

    /**
     * Adds the current download rate info to the db
     */
    void addDownloadRateToDB(Double currPercentage, Double effectiveness, int missingPapers) {
        //Get the current time
        Calendar calendar = Calendar.getInstance();
        java.sql.Timestamp timestamp = new java.sql.Timestamp(calendar.getTime().getTime());
        try {
            String urlStr = SCRAWLERWURL + "/api_scrawlers/add_download_rate";
            HashMap<String, Object> parameters = new HashMap<>();
            parameters.put("username", email);
            parameters.put("password", password);
            parameters.put("download_rate", currPercentage);
            parameters.put("last_updated", timestamp);
            parameters.put("effectiveness_rate", effectiveness);
            parameters.put("missing_papers", missingPapers);
            parameters.put("id", instance);
            JSONObject result = new JSONObject(SCrawlerWRequests.postRequest(urlStr, parameters));
            if (!result.getBoolean("success")) {
                errorMessage = result.getString("message");
                guiLabelManagement.setAlertPopUp(errorMessage);
            }
        } catch (Exception e) {
            e.printStackTrace();
            guiLabelManagement.setAlertPopUp(e.getMessage());
        }
    }


    /**
     * Adds an error to the error table
     */
    void addError(String error) {
        String urlStr = SCRAWLERWURL + "/api_scrawlers/add_error";
        try {
            //Get the current time
            Calendar calendar = Calendar.getInstance();
            java.sql.Timestamp timestamp = new java.sql.Timestamp(calendar.getTime().getTime());
            HashMap<String, Object> parameters = new HashMap<>();
            parameters.put("username", email);
            parameters.put("password", password);
            parameters.put("scrawler_id", instance);
            parameters.put("location", "" + getClass().getProtectionDomain().getCodeSource().getLocation());
            parameters.put("time", timestamp);
            parameters.put("error", error);

            JSONObject result = new JSONObject(SCrawlerWRequests.postRequest(urlStr, parameters));
            if (!result.getBoolean("success")) {
                errorMessage = result.getString("message");
                guiLabelManagement.setAlertPopUp(errorMessage);
            }
        } catch (Exception e) {
            guiLabelManagement.setAlertPopUp(e.getMessage());
        }

    }




    /**
     * Returns the operation to perform. Empty if none
     */
    String getOperationToPerform() {
        try {
            ArrayList<String> params = new ArrayList<>();
            params.add(instance);
            JSONObject result = new JSONObject(SCrawlerWRequests.getRequest(SCRAWLERWURL + "/operation/",
                    params));
            if (result.isNull("operation")) {
                //If there are no records
                return WebServer.SupportedOperations.none.name();
            }
            String operation = result.getString("operation");
            if (operation.equals(WebServer.SupportedOperations.close.name())) {
                return WebServer.SupportedOperations.close.name();
            } else if (operation.equals(WebServer.SupportedOperations.clean.name())) {
                return WebServer.SupportedOperations.clean.name();
            } else if (operation.contains("Update")) {
                return operation;
            } else if (operation.equals(WebServer.SupportedOperations.restart.name())) {
                return WebServer.SupportedOperations.restart.name();
            }

        } catch (Exception e) {
            guiLabelManagement.setAlertPopUp(e.getMessage());
        }
        return "";
    }


    /**
     * Returns the cookies for a given proxy
     *
     * @return Set of cookies
     */
    Set<Cookie> getCookies(Proxy proxy, SearchEngine.SupportedSearchEngine engine) {
        Set<Cookie> result = new HashSet<>();
        String urlStr = SCRAWLERWURL + "/api_scrawlers/unlocked_cookie";
        HashMap<String, Object> parameters = new HashMap<>();
        parameters.put("username", email);
        parameters.put("password", password);
        parameters.put("ip", proxy.getProxy());
        parameters.put("port", String.valueOf(proxy.getPort()));
        parameters.put("search_engine", engine.name());

        try {
            //Make post request to server
            String cookieString = new JSONObject(SCrawlerWRequests.postRequest(urlStr, parameters)).getString
                    ("cookies");
            Scanner scanner = new Scanner(cookieString);
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                String[] cookieInfo = line.split(";");
                String name = cookieInfo[0];
                String value = cookieInfo[1];
                String domain = cookieInfo[2];
                String path = cookieInfo[3];
                java.util.Date expiry = null;
                if (cookieInfo[4] != null && !cookieInfo[4].equals("null")) {
                    expiry = new java.util.Date(cookieInfo[4]);
                }
                Boolean isSecure = Boolean.valueOf(cookieInfo[4]);
                //If the expiry is in this format, then its wrong.
                if (expiry != null && expiry.toString().contains("1970")) {
                    expiry = null;
                    isSecure = true;
                }
                if (expiry != null) System.out.println(expiry);
                Cookie ck = new Cookie(name, value, domain, path, expiry, isSecure);
                result.add(ck);
            }

            return result;
        } catch (Exception e) {
            guiLabelManagement.setAlertPopUp(e.getMessage());
        }
        return new HashSet<>();
    }

    /**
     * In case the connection is closed
     */
    void reconnect() {
        verifyIfConnectionWorks(email, password);
    }


//
//    /**
//     * Returns all instances with their respective last update time
//     */
//    HashSet<WebServer.ScrawlerInstance> getAllInstances() {
//        HashSet<WebServer.ScrawlerInstance> result = new HashSet<>();
//        try {
//            String sql = "SELECT id, last_updated FROM scrawlers ";
//            PreparedStatement statement = myConnection.prepareStatement(sql);
//            ResultSet res = statement.executeQuery();
//            //If there are no records about this proxy in our db, then no crawler can be using it at the moment
//            if (!res.isBeforeFirst()) {
//                return result;
//            }
//            //Process result, if the id appears, then the current crawler is already using it
//            while (res.next()) {
//                String instanceID = res.getString("id");
//                DateTime time;
//                if (res.getTimestamp("last_updated") != null) {
//                    time = new DateTime(res.getTimestamp("last_updated"));
//                } else {
//                    time = null;
//                }
//                WebServer.ScrawlerInstance curr = new WebServer.ScrawlerInstance(instanceID, time);
//                result.add(curr);
//            }
//        } catch (SQLException e) {
//            guiLabelManagement.setAlertPopUp(e.getMessage());
//        }
//        return result;
//    }
//

    /**
     * Retrieves all proxy compiling websites
     *
     * @return HashMap of Website to Time Accessed
     */
    HashMap<String, DateTime> getAllWebsites() {
        HashMap<String, DateTime> result = new HashMap<>();
        try {
            String urlStr = SCRAWLERWURL + "/api_scrawlers/list_of_websites";
            JSONArray jsonMainArr = new JSONArray(SCrawlerWRequests.getRequest(urlStr, new ArrayList<>()));
            //Iterate through the array of cookies
            for (int i = 0; i < jsonMainArr.length(); i++) {
                JSONObject currentWebsite = jsonMainArr.getJSONObject(i);
                String website = currentWebsite.getString("website");
                DateTime time;
                if (currentWebsite.getString("visited") != null) {
                    time = new DateTime(currentWebsite.getString("visited"));
                } else {
                    time = null;
                }
                result.put(website, time);

            }
        } catch (Exception e) {
            guiLabelManagement.setAlertPopUp(e.getMessage());
        }
        return result;

    }


    void updateWebsiteTime(String website) {
        try {
            String urlStr = SCRAWLERWURL + "/api_scrawlers/set_website_time";
            HashMap<String, Object> parameters = new HashMap<>();
            parameters.put("username", email);
            parameters.put("password", password);
            parameters.put("website", website);
            Calendar calendar = Calendar.getInstance();
            java.sql.Timestamp timestamp = new java.sql.Timestamp(calendar.getTime().getTime());
            //Adds the time whenn the website was visited
            parameters.put("visited", timestamp);
            JSONObject result = new JSONObject(SCrawlerWRequests.postRequest(urlStr, parameters));
            //Check if post request worked
            if (!result.getBoolean("success")) {
                errorMessage = result.getString("message");
                guiLabelManagement.setAlertPopUp(errorMessage);
            }
        } catch (Exception e) {
            guiLabelManagement.setAlertPopUp(e.getMessage());
        }
    }


    /**
     * Adds a proxy into the list of proxies
     */
    void addProxyToListOfProxies(Proxy proxy) throws IllegalArgumentException {
        try {
            String urlStr = SCRAWLERWURL + "/api_scrawlers/add_to_list_of_proxies";
            HashMap<String, Object> parameters = new HashMap<>();
            parameters.put("username", email);
            parameters.put("password", password);
            parameters.put("ip", proxy.getProxy());
            parameters.put("port", String.valueOf(proxy.getPort()));
            Calendar calendar = Calendar.getInstance();
            java.sql.Timestamp timestamp = new java.sql.Timestamp(calendar.getTime().getTime());
            //Adds the time whenn the website was visited
            parameters.put("time", timestamp);
            JSONObject result = new JSONObject(SCrawlerWRequests.postRequest(urlStr, parameters));
            //Check if post request worked
            checkForDuplicateEntry(result);
        } catch (Exception e) {
            guiLabelManagement.setAlertPopUp(e.getMessage());
        }
    }

    private void checkForDuplicateEntry(JSONObject result) {
        if (!result.getBoolean("success")) {
            errorMessage = result.getString("message");
            //If this proxy is already in the db, throw an error to avoid counting it as a new proxy
            if (errorMessage.contains("ER_DUP_ENTRY")) {
                throw new IllegalArgumentException();
            }
            guiLabelManagement.setAlertPopUp(errorMessage);
        }
    }


    /**
     * Deletes a locked proxy from the list of proxies
     */
    void deleteProxyFromListOfProxies(Proxy proxy) {
        try {
            String urlStr = SCRAWLERWURL + "/api_scrawlers/delete_from_list_of_proxies";
            HashMap<String, Object> parameters = new HashMap<>();
            parameters.put("username", email);
            parameters.put("password", password);
            parameters.put("ip", proxy.getProxy());
            parameters.put("port", String.valueOf(proxy.getPort()));
            JSONObject result = new JSONObject(SCrawlerWRequests.postRequest(urlStr, parameters));
            //Check if post request worked
            if (!result.getBoolean("success")) {
                errorMessage = result.getString("message");
                guiLabelManagement.setAlertPopUp(errorMessage);
            }
        } catch (Exception e) {
            guiLabelManagement.setAlertPopUp(e.getMessage());
        }
    }


    /**
     * Gets all the proxies scrapped from the proxy compiling websites
     *
     * @return Set of proxies
     */
    HashSet<Proxy> getAllProxiesFromListOfProxies() {
        HashSet<Proxy> result = new HashSet<>();
        try {
            String urlStr = SCRAWLERWURL + "/api_scrawlers/list_of_proxies";
            JSONArray jsonMainArr = new JSONArray(SCrawlerWRequests.getRequest(urlStr, new ArrayList<>()));
            for (int i = 0; i < jsonMainArr.length(); i++) {
                //Get the proxy object
                JSONObject currProxy = jsonMainArr.getJSONObject(i);
                String ip = currProxy.getString("ip");
                int port = currProxy.getInt("port");
                //Get the time
                DateTime time;
                if (currProxy.getString("time") != null) {
                    time = new DateTime(currProxy.getString("time"));
                } else {
                    time = null;
                }
                //Create a new proxy obj and add it to the result
                Proxy p = new Proxy(ip, port);
                p.setTime(time);
                result.add(p);
            }
        } catch (Exception e) {
            e.printStackTrace(System.out);
            guiLabelManagement.setAlertPopUp(e.getMessage());
        }
        return result;
    }


    /**
     * Retrieves the latest version with its description
     *
     * @return String[] with two values, the
     */
    Map.Entry<String, String> getLatestVersion() {
        Map.Entry<String, String> result = new AbstractMap.SimpleEntry<>("", "");
        JSONObject getResult;
        try {
            getResult = new JSONObject(SCrawlerWRequests.getRequest(SCRAWLERWURL + "/api_scrawlers/version", new
                    ArrayList<>()));

            String version = getResult.getString("version");
            String description = getResult.getString("description");
            result = new AbstractMap.SimpleEntry<>(version, description);
        } catch (IOException e) {
            guiLabelManagement.setAlertPopUp(e.getMessage());
        }
        return result;
    }

    DateTime getLastMaintenanceTime() {
        DateTime result = new DateTime();
        try {
            JSONObject getResult = new JSONObject(SCrawlerWRequests.getRequest(SCRAWLERWURL +
                    "/api_maintenance/last_maintenance", new ArrayList<>()));
            if (getResult.isNull("last_maintenance")) {
                //If there are no records
                return null;
            } else {
                result = new DateTime(getResult.getString("last_maintenance"));
            }
        } catch (Exception e) {
            guiLabelManagement.setAlertPopUp(e.getMessage());
        }
        return result;

    }


    /**
     * If a proxy fails to load a website, increase the counter
     */
    void addFailureToLoad(Proxy proxy) {
        try {
            String urlStr = SCRAWLERWURL + "/api_scrawlers/add_failure";
            HashMap<String, Object> parameters = new HashMap<>();
            parameters.put("username", email);
            parameters.put("password", password);
            parameters.put("ip", proxy.getProxy());
            parameters.put("port", String.valueOf(proxy.getPort()));
            JSONObject result = new JSONObject(SCrawlerWRequests.postRequest(urlStr, parameters));
            //Check if post request worked
            if (!result.getBoolean("success")) {
                errorMessage = result.getString("message");
                guiLabelManagement.setAlertPopUp(errorMessage);
            }
        } catch (Exception e) {
            guiLabelManagement.setAlertPopUp(e.getMessage());
        }
    }


    /**
     * Retrieves the current number of failures
     */
    int getFailureToLoad(Proxy proxy) {
        JSONObject result;
        try {
            ArrayList<String> params = new ArrayList<>();
            params.add(proxy.getProxy());
            params.add(String.valueOf(proxy.getPort()));
            result = new JSONObject(SCrawlerWRequests.getRequest(SCRAWLERWURL + "/failure_to_load/", params));
            if (result.isNull("failed_to_load")) {
                //If there are no records
                return 0;
            }
            return result.getInt("failed_to_load");
        } catch (Exception e) {
            guiLabelManagement.setAlertPopUp(e.getMessage());
        }
        return 0;
    }

    String getInstanceName() {
        return instance;
    }


    /**
     * Sets the number of proxies found in a website
     */
    void setNumberOfProxiesFound(int proxiesFoundInThisSite, String website) {
        try {
            String urlStr = SCRAWLERWURL + "/api_scrawlers/set_number_of_proxies";
            HashMap<String, Object> parameters = new HashMap<>();
            parameters.put("username", email);
            parameters.put("password", password);
            parameters.put("numberOfProxies", proxiesFoundInThisSite);
            parameters.put("website", website);
            JSONObject result = new JSONObject(SCrawlerWRequests.postRequest(urlStr, parameters));
            //Check if post request worked
            if (!result.getBoolean("success")) {
                errorMessage = result.getString("message");
                guiLabelManagement.setAlertPopUp(errorMessage);
            }
        } catch (Exception e) {
            guiLabelManagement.setAlertPopUp(e.getMessage());
        }
    }

}
