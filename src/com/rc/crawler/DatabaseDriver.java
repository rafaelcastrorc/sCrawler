package com.rc.crawler;


import com.mysql.jdbc.exceptions.jdbc4.MySQLIntegrityConstraintViolationException;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.joda.time.DateTime;
import org.openqa.selenium.Cookie;

import javax.xml.crypto.Data;
import java.io.FileNotFoundException;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;


/**
 * Created by rafaelcastro on 9/7/17.
 * Handles all database related activities
 */
class DatabaseDriver {
    private static DatabaseDriver databaseDriver;
    private static Connection myConnection;
    private static GUILabelManagement guiLabelManagement;
    private static boolean thereIsADialog = false;
    private static String instance;
    private static String dbName;
    private static String port;
    private static String password;
    private static String username;
    private static String serverAddress;
    private static String errorMessage;

    private DatabaseDriver() {

    }

    static DatabaseDriver getInstance(GUILabelManagement guiLabelManagement) {
        if (databaseDriver == null) {
            databaseDriver = new DatabaseDriver();
            DatabaseDriver.guiLabelManagement = guiLabelManagement;
            try {
                instance = Logger.getInstance().getInstanceID();
            } catch (FileNotFoundException e) {
                guiLabelManagement.setAlertPopUp(e.getMessage());
            }
            displayMainMenuDB();
            //Set up the tables if they do not exist
            createTables();
            return databaseDriver;
        } else {
            //readDBConfigData(false);
            return databaseDriver;
        }
    }


    /**
     * Displays the main menu for the user to choose to which database they want to connect
     */
    private static void displayMainMenuDB() {
        boolean thereWasAnError = false;
        String message = "";
        Logger logger = Logger.getInstance();
        //Check for the user preferences
        boolean doNotShowThisAgain = false;
        try {
            doNotShowThisAgain = (boolean) logger.readUserDBData().get(0);
        } catch (IllegalArgumentException ignored) {
        }
        //If it does not need to display the menu bc of user preferences, then just skip it.
        if (doNotShowThisAgain) {
            //Verify if you can connect to the db using the stored settings
            if (!readDBConfigData(true)) {
                thereWasAnError = true;
                message = "Unable to connect to the database using your stored setting.";
            } else {
                //If connection works, then do not show menu and just return
                return;
            }
        }
        // Create the custom dialog.
        Dialog<Object> dialog = new Dialog<>();
        dialog.setTitle("Configure your database connection");
        dialog.setHeaderText("In order for all the crawlers to be synchronized, all of them need to access\nthe " +
                "same database. Do you want to access the MySQL database stored in\nyour local settings, or do you " +
                "want to set up a new one.");

        // Set the butt
        // \on types.
        ButtonType useLocal = new ButtonType("Use stored settings");
        ButtonType connectToNew = new ButtonType("Connect to a new database");

        dialog.getDialogPane().getButtonTypes().addAll(useLocal, connectToNew);

        // Create the username and password labels and fields.
        VBox vBox = new VBox(20);
        vBox.setAlignment(Pos.CENTER_LEFT);

        //Set the GUI information
        Label instructions = new Label("You can get a free database from https://www.freemysqlhosting.net, \n" +
                "or any other MySQL database hosting provider such as Amazon");

        //Set the GUI
        if (!thereWasAnError) {
            vBox.getChildren().addAll(instructions);
        } else {
            Label error = new Label(message);
            error.setTextFill(Color.RED);
            vBox.getChildren().addAll(error, instructions);

        }
        dialog.getDialogPane().setContent(vBox);
        final Button useLocalButton = (Button) dialog.getDialogPane().lookupButton(useLocal);
        final Button connectToNewButton = (Button) dialog.getDialogPane().lookupButton(connectToNew);

        useLocalButton.addEventFilter(
                ActionEvent.ACTION,
                event -> {
                    //If it fails to read the configuration data, then let the user manually input the info
                    dialog.close();
                    if (!readDBConfigData(true)) {
                        showDialogForDBConfig(true);
                    }

                }
        );
        connectToNewButton.addEventFilter(
                ActionEvent.ACTION,
                event -> {
                    dialog.close();
                    showDialogForDBConfig(false);
                }
        );
        dialog.showAndWait();
    }


    /**
     * Reads the locally stored database login credentials. Returns false if it is unable to connect
     */
    private static boolean readDBConfigData(boolean calledFromMainMenu) {
        //Just read the login information already stored in the computer
        Logger logger = Logger.getInstance();
        ArrayList list;
        try {
            list = logger.readUserDBData();
        } catch (IllegalArgumentException e) {
            guiLabelManagement.setAlertPopUp("There is no information about your database credentials.\nPlease " +
                    "manually set it up");
            if (!calledFromMainMenu) {
                showDialogForDBConfig(true);
            }
            return false;
        }
        String serverAddress = (String) list.get(1);
        String port = String.valueOf(list.get(2));
        String databaseName = (String) list.get(3);
        String userName = (String) list.get(4);
        String password = (String) list.get(5);
        //If there is no connection, prompt user with dialog box again
        if (!verifyIfConnectionWorks(databaseName, serverAddress, port, userName, password)) {
            guiLabelManagement.setAlertPopUp("Unable to connect to the database using your stored setting." +
                    "\nPlease manually set it up");
            if (!calledFromMainMenu) {
                if (!thereIsADialog) {
                    Platform.runLater(() -> showDialogForDBConfig(true));
                }
            }
            return false;
        }
        return true;
    }


    /**
     * Displays a GUI with the different database configuration options
     *
     * @param thereWasAnError If there was an error accessing the db using the current settings
     */
    private static void showDialogForDBConfig(boolean thereWasAnError) {
        thereIsADialog = true;
        final boolean[] connected = {false};
        // Create the custom dialog.
        Alert dialog = new Alert(Alert.AlertType.NONE);
        dialog.setTitle("Database Configuration Details");
        if (thereWasAnError) {
            dialog.setHeaderText("ERROR: Unable to connect to the database using your stored setting!" +
                    "\nPlease manually set it up.");
        } else {
            dialog.setHeaderText(null);
        }

        // Set the button types.
        ButtonType loginButtonType = new ButtonType("Test Connection", ButtonBar.ButtonData.OK_DONE);
        ButtonType storeConnection = new ButtonType("Store Credentials & Continue", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(loginButtonType, storeConnection);

        // Create the username and password labels and fields.
        VBox vBox = new VBox(21);
        vBox.setAlignment(Pos.CENTER_LEFT);

        HBox hBox1 = new HBox(18);
        hBox1.setAlignment(Pos.CENTER_LEFT);
        Label label1 = new Label("Server Address:");
        TextField serverAddress = new TextField();
        serverAddress.setPromptText("sql.freesqlhosting.net");
        hBox1.getChildren().addAll(label1, serverAddress);

        HBox hBox2 = new HBox(17);
        hBox2.setAlignment(Pos.CENTER_LEFT);
        Label label2 = new Label("Database Name:");
        TextField databaseName = new TextField();
        databaseName.setPromptText("sql123");
        hBox2.getChildren().addAll(label2, databaseName);


        HBox hBox3 = new HBox(89);
        hBox3.setAlignment(Pos.CENTER_LEFT);
        Label label3 = new Label("Port:");
        TextField portNumber = new TextField();
        portNumber.setPromptText("3012");
        hBox3.getChildren().addAll(label3, portNumber);

        HBox hBox4 = new HBox(52);
        hBox4.setAlignment(Pos.CENTER_LEFT);
        Label label4 = new Label("Username:");
        TextField username = new TextField();
        username.setPromptText("Username");
        hBox4.getChildren().addAll(label4, username);

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


        vBox.getChildren().addAll(hBox1, hBox2, hBox3, hBox4, hBox5, hBox6);

        dialog.getDialogPane().setContent(vBox);

        final Button loginButtonDB = (Button) dialog.getDialogPane().lookupButton(loginButtonType);
        final Button storeConnectionDB = (Button) dialog.getDialogPane().lookupButton(storeConnection);

        loginButtonDB.setDefaultButton(true);
        storeConnectionDB.setDisable(true);

        loginButtonDB.addEventFilter(
                ActionEvent.ACTION,
                event -> {
                    if (serverAddress.getText().isEmpty() || databaseName.getText().isEmpty() ||
                            portNumber.getText().isEmpty() || serverAddress.getText().isEmpty()) {
                        guiLabelManagement.setAlertPopUp("Please fill out all the fields");
                    } else {
                        connected[0] = verifyIfConnectionWorks(databaseName.getText(), serverAddress.getText(),
                                portNumber.getText
                                        (), username.getText(), password.getText());
                        storeConnectionDB.setDisable(!connected[0]);
                        if (!connected[0]) {
                            errorLabel.setText("Failed to Connect! - SQLSTATE code: " + errorMessage);
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
                    logger.saveUserDBData(false, serverAddress.getText(), portNumber.getText
                            (), databaseName.getText(), username.getText(), password.getText());
                    thereIsADialog = false;
                    dialog.close();

                }
        );
        // Request focus on the username field by default.
        Platform.runLater(username::requestFocus);

        //Check the type of button that was pressed
        dialog.showAndWait();


    }


    /**
     * Creates the different tables, if they do not exist, in the current database
     */
    private static void createTables() {
        //Create proxies table
        String createProxiesTable = "CREATE TABLE proxies(" +
                "  ip               VARCHAR(20)            NOT NULL," +
                "  port             INT                    NOT NULL," +
                "  unlocked         TINYINT DEFAULT '0' NULL," +
                "  cookies          TEXT                   NULL," +
                "  search_engine    VARCHAR(20)            NULL," +
                "  num_of_instances INT DEFAULT '0'        NULL," +
                "  PRIMARY KEY (ip, port)" +
                ")";
        String createIndexOnProxiesTable = "CREATE INDEX proxies_ip_port_index" +
                "  ON proxies (ip, port)";
        try {
            Statement stmt = myConnection.createStatement();
            stmt.execute(createProxiesTable);
            stmt.execute(createIndexOnProxiesTable);
            //If there are no errors, then ask user which cookies he wants to use
            showProxiesDialog();
        } catch (SQLException ignored) {
        }

        //Create scrawlers table
        String createScrawlerTable = "CREATE TABLE scrawlers(" +
                "  id            VARCHAR(40)        NOT NULL PRIMARY KEY," +
                "  location      TEXT               NOT NULL," +
                "  download_rate DOUBLE PRECISION DEFAULT '0.00' NULL," +
                "  last_updated  TIMESTAMP          NULL," +
                "  operation     VARCHAR(30)        NULL," +
                "  CONSTRAINT scrawlers_id_uindex" +
                "  UNIQUE (id)" +
                ")";

        try {
            Statement stmt = myConnection.createStatement();
            stmt.execute(createScrawlerTable);
        } catch (SQLException ignored) {
        }
        //Create scrawler_to_proxy table
        String createScrawlerToProxyTable = "CREATE TABLE scrawler_to_proxy" +
                "(" +
                "  scrawler_id VARCHAR(40) NOT NULL," +
                "  ip          VARCHAR(20) NOT NULL," +
                "  port        INT         NOT NULL," +
                "  PRIMARY KEY (scrawler_id, ip, port)," +
                "  CONSTRAINT id" +
                "  FOREIGN KEY (scrawler_id) REFERENCES scrawlers (id)" +
                "    ON DELETE CASCADE" +
                ")";
        String createIndexOnScrawlerToProxyTable = "CREATE INDEX scrawler_to_proxy_scrawler_id_index" +
                "  ON scrawler_to_proxy (scrawler_id)";
        String createIndexOnScrawlerToProxyTable2 = "CREATE INDEX scrawler_to_proxy_ip_port_index" +
                "  ON scrawler_to_proxy (ip, port)";
        try {
            Statement stmt = myConnection.createStatement();
            stmt.execute(createScrawlerToProxyTable);
            stmt.execute(createIndexOnScrawlerToProxyTable);
            stmt.execute(createIndexOnScrawlerToProxyTable2);

        } catch (SQLException ignored) {
        }

        //Create errors table
        String errorsTable = "CREATE TABLE errors" +
                "(" +
                "  scrawler_id VARCHAR(40) NULL," +
                "  location   TEXT     NOT NULL," +
                "  time  TIMESTAMP NOT NULL," +
                "  error TEXT NULL," +
                "  FOREIGN KEY (scrawler_id) REFERENCES scrawlers (id) " +
                "       ON DELETE SET NULL" +
                ")";
        try {
            Statement stmt = myConnection.createStatement();
            stmt.execute(errorsTable);
        } catch (SQLException ignored) {
        }

        String createListOfProxiesTable = "CREATE TABLE list_of_proxies(" +
                "  ip               VARCHAR(20)            NOT NULL," +
                "  port             INT                    NOT NULL," +
                "  time  TIMESTAMP NOT NULL," +
                "  PRIMARY KEY (ip, port)" +
                ")";
        String createIndexOnListOfProxiesTable = "CREATE INDEX list_of_proxies_ip_port_index" +
                "  ON list_of_proxies (ip, port)";
        try {
            Statement stmt = myConnection.createStatement();
            stmt.execute(createListOfProxiesTable);
            stmt.execute(createIndexOnListOfProxiesTable);
        } catch (SQLException ignored) {
        }

        String createListOfWebsites = "CREATE TABLE list_of_websites(" +
                "  website               VARCHAR(200)            NOT NULL," +
                "  visited             TIMESTAMP                     NULL," +
                "  PRIMARY KEY (website)" +
                ")";
        try {
            Statement stmt = myConnection.createStatement();
            stmt.execute(createListOfWebsites);
            insertAllWebsites();
        } catch (SQLException ignored) {
        }
    }

    /**
     * Creates a dialog when there are no proxies table in the databae, to ask the user which proxies to upload
     */
    private static void showProxiesDialog() {
        // Create the custom dialog.
        Alert dialog = new Alert(Alert.AlertType.ERROR);
        dialog.setTitle("Upload your unlocked proxies");
        dialog.setHeaderText("Select the source of your unlocked proxies");
        Label info = new Label("There are no unlocked proxies in your current database. Without unlocked proxies, " +
                "the\ncrawler will take longer to find proxies to connect. \n\n" +
                "-If you want to upload the proxies that you have unlocked locally, press 'Use local proxies'\n" +
                "-If you want to use the proxies that we have unlocked, press 'Download from server'\n" +
                "-If you do not want to use any unlocked proxies, just press 'Ok'" +
                "\n\nNote that loading the proxies into the database might take a few minutes.");
        // Set the button types.
        ButtonType useLocalProxies = new ButtonType("Use local proxies");
        ButtonType downloadProxiesFromServer = new ButtonType("Download from server");

        dialog.getDialogPane().getButtonTypes().addAll(useLocalProxies, downloadProxiesFromServer);
        // Create the username and password labels and fields.
        VBox vBox = new VBox(20);
        vBox.setAlignment(Pos.CENTER_LEFT);

        //Set the GUI information
        Label result = new Label("");
        //Set the GUI
        result.setTextFill(Color.RED);
        vBox.getChildren().addAll(info, result);

        dialog.getDialogPane().setContent(vBox);

        final Button useLocalButton = (Button) dialog.getDialogPane().lookupButton(useLocalProxies);
        final Button downloadProxiesButton = (Button) dialog.getDialogPane().lookupButton(downloadProxiesFromServer);

        useLocalButton.addEventFilter(
                ActionEvent.ACTION,
                event ->

                {

                    //Ask the user which unlocked proxies they want to use
                    try {
                        result.setTextFill(Color.GREEN);
                        DoWork task = new DoWork("uploadProxiesLoading", "local", null);
                        task.setDialog(dialog);
                        ExecutorService executorService = Executors.newSingleThreadExecutor(new MyThreadFactory());
                        Future<String> e = executorService.submit((Callable<String>) task);
                        event.consume();
                    } catch (IllegalArgumentException e) {
                        guiLabelManagement.setAlertPopUp("There was a problem reading your cookies.dta file");
                        result.setTextFill(Color.RED);
                        result.setText("There was a problem reading your cookies.dta file");
                        event.consume();
                    }

                }
        );
        downloadProxiesButton.addEventFilter(
                ActionEvent.ACTION,
                event ->
                {
                    DoWork task = new DoWork("uploadProxiesLoading", "download", null);
                    task.setDialog(dialog);
                    ExecutorService executorService = Executors.newSingleThreadExecutor(new MyThreadFactory());
                    Future<String> e = executorService.submit((Callable<String>) task);
                    event.consume();

                }
        );
        dialog.showAndWait();
    }


    /**
     * Verifies if the database setting connection works
     */

    private static boolean verifyIfConnectionWorks(String dbName, String serverAddress, String port, String username,
                                                   String
                                                           password) {
        try {
            myConnection = DriverManager.getConnection("jdbc:mysql://" + serverAddress + ":" + port + "/" + dbName,
                    username, password);
            //Store everything
            DatabaseDriver.dbName = dbName;
            DatabaseDriver.serverAddress = serverAddress;
            DatabaseDriver.port = port;
            DatabaseDriver.username = username;
            DatabaseDriver.password = password;


            return true;

        } catch (SQLException e) {
            e.printStackTrace();
            errorMessage = e.getSQLState();
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
        Statement statement;
        try {
            statement = myConnection.createStatement();

            ResultSet res = statement.executeQuery("SELECT * FROM proxies WHERE unlocked");
            //Process result
            while (res.next()) {
                SearchEngine.SupportedSearchEngine engine = null;
                Set<Cookie> cookies = new HashSet<>();
                //Get data from database
                String ip = res.getString("ip");
                int port = res.getInt("port");
                String cookieString = res.getString("cookies");
                String searchEngine = res.getString("search_engine");

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
        } catch (Exception e) {
            guiLabelManagement.setAlertPopUp(e.getMessage());
        }
        return result;
    }

    /**
     * Verifies if proxy can be used (there are less than 3 instances using it at the time)
     *
     * @param proxy Proxy to use
     * @return boolean
     */
    boolean canUseProxy(Proxy proxy) {
        //Create a statement
        try {
            String sql = "SELECT unlocked, num_of_instances FROM proxies WHERE ip=? AND port=?";
            //Execute SQL query
            PreparedStatement statement = myConnection.prepareStatement(sql);
            statement.setString(1, proxy.getProxy());
            statement.setInt(2, proxy.getPort());

            ResultSet res = statement.executeQuery();
            //If there are no records about this proxy in our db, then no crawler can be using it at the moment
            if (!res.isBeforeFirst()) {
                return true;
            }

            //Check if the current proxy is already using it.
            if (isCurrentInstanceUsingProxy(proxy)) return true;

            //Check if the proxy is unlocked
            while (res.next()) {
                if (res.getBoolean("unlocked") && res.getInt("num_of_instances") < 4) {
                    return true;
                }

            }


        } catch (SQLException e) {
            guiLabelManagement.setAlertPopUp(e.getMessage());
        } catch (NullPointerException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Inserts an unlocked proxy into the database, if it already exists it updates the value
     *
     * @param proxy Proxy that is unlocked
     * @param stats
     */
    void addUnlockedProxy(Proxy proxy, String cookies, SearchEngine.SupportedSearchEngine searchEngine, StatsGUI
            stats) {
        //Add it back to the list of proxies
        try {
            addProxyToListOfProxies(proxy);
        } catch (IllegalArgumentException ignored) {
        }
        try {
            String sql = "INSERT INTO proxies " +
                    "(ip, port, unlocked, cookies, search_engine) " +
                    "VALUES (?, ?, TRUE, ?, ?)";

            PreparedStatement statement = myConnection.prepareStatement(sql);
            //Set params
            statement.setString(1, proxy.getProxy());
            statement.setInt(2, proxy.getPort());
            statement.setString(3, cookies);
            try {
                statement.setString(4, searchEngine.name());
            } catch (NullPointerException e) {
                statement.setString(4, "GoogleScholar");
            }
            statement.executeUpdate();
            stats.updateNumberOfUnlocked(stats.getNumberOfUnlockedProxies().get() + 1);
        } catch (MySQLIntegrityConstraintViolationException e) {
            //If this happens, record already exist so we update it instead

            try {
                String sql = "UPDATE proxies " +
                        "SET cookies = ?, search_engine = ?, unlocked = TRUE " +
                        "WHERE ip = ?  AND port = ?";
                PreparedStatement statement = myConnection.prepareStatement(sql);
                //Set params
                statement.setString(1, cookies);
                try {
                    statement.setString(2, searchEngine.name());
                } catch (NullPointerException e2) {
                    statement.setString(2, "GoogleScholar");
                }
                statement.setString(3, proxy.getProxy());
                statement.setInt(4, proxy.getPort());
                statement.executeUpdate();
                stats.updateNumberOfUnlocked(stats.getNumberOfUnlockedProxies().get() + 1);
            } catch (SQLException e1) {
                guiLabelManagement.setAlertPopUp(e1.getMessage());
            }
        } catch (SQLException e2) {
            guiLabelManagement.setAlertPopUp(e2.getMessage());
        }
    }

    /**
     * Inserts a locked proxy into the database
     *
     * @param proxy Proxy that is unlocked
     */

    void addLockedProxy(Proxy proxy) {
        try {
            String sql = "UPDATE proxies " +
                    "SET cookies = ?, unlocked = FALSE, num_of_instances = 0 " +
                    "WHERE ip = ?  AND port = ?";
            PreparedStatement statement = myConnection.prepareStatement(sql);
            statement.setString(1, "");
            statement.setString(2, proxy.getProxy());
            statement.setInt(3, proxy.getPort());
            statement.executeUpdate();

            //Remove it if there is crawler using it.
            sql = "DELETE FROM scrawler_to_proxy " +
                    "WHERE ip = (?) AND port = (?) ";

            statement = myConnection.prepareStatement(sql);
            //Set params
            statement.setString(1, proxy.getProxy());
            statement.setInt(2, proxy.getPort());

            statement.executeUpdate();
            //Delete it from the proxy list as well
            deleteProxyFromListOfProxies(proxy);
        } catch (SQLException e) {
            guiLabelManagement.setAlertPopUp(e.getMessage());
        }
    }

    /**
     * Notifies the db that the current instance is using a proxy
     */
    void addProxyToCurrentInstance(Proxy proxy) {
        try {
            //If the current instance is not using this proxy, then update the database
            if (!isCurrentInstanceUsingProxy(proxy)) {
                String sql = "INSERT INTO scrawler_to_proxy " +
                        "(scrawler_id, ip, port) " +
                        "VALUES (?, ?, ?)";

                PreparedStatement statement = myConnection.prepareStatement(sql);
                //Set params
                statement.setString(1, instance);
                statement.setString(2, proxy.getProxy());
                statement.setInt(3, proxy.getPort());

                statement.executeUpdate();

                //Increase the count of crawlers using this proxy
                sql = "UPDATE proxies " +
                        "SET num_of_instances = num_of_instances + 1, unlocked = TRUE " +
                        "WHERE ip = ?  AND port = ?";
                statement = myConnection.prepareStatement(sql);
                //Set params
                statement.setString(1, proxy.getProxy());
                statement.setInt(2, proxy.getPort());
                statement.executeUpdate();
            }
        } catch (MySQLIntegrityConstraintViolationException e) {
            throw new IllegalArgumentException();
        } catch (SQLException e) {
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
            String sql = "SELECT scrawler_id FROM scrawler_to_proxy WHERE ip=? AND port=?";
            PreparedStatement statement = myConnection.prepareStatement(sql);
            statement.setString(1, proxy.getProxy());
            statement.setInt(2, proxy.getPort());

            ResultSet res = statement.executeQuery();
            //If there are no records about this proxy in our db, then no crawler can be using it at the moment
            if (!res.isBeforeFirst()) {
                return false;
            }
            //Process result, if the id appears, then the current crawler is already using it
            while (res.next()) {
                if (res.getString("scrawler_id").equals(instance)) return true;
            }
        } catch (SQLException e) {
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
            String sql = "INSERT INTO scrawlers " +
                    "(id, location) " +
                    "VALUES (?, ?)";
            PreparedStatement statement = myConnection.prepareStatement(sql);
            //Set params
            statement.setString(1, instance);
            statement.setString(2, "" + getClass().getProtectionDomain().getCodeSource().getLocation());
            statement.executeUpdate();
        } catch (SQLException e) {
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
            //Get all the proxies
            String sql = "SELECT ip, port " +
                    "FROM scrawler_to_proxy " +
                    "WHERE scrawler_id = ? ";
            PreparedStatement statement = myConnection.prepareStatement(sql);
            statement.setString(1, instance);
            ResultSet res = statement.executeQuery();
            //Iterate through each proxy and port
            while (res.next()) {
                String ip = res.getString("ip");
                int port = res.getInt("port");
                //Decrease counter
                sql = "UPDATE proxies " +
                        "SET num_of_instances = num_of_instances - 1 " +
                        "WHERE ip = ?  AND port = ? AND num_of_instances > 0";
                statement = myConnection.prepareStatement(sql);
                //Set params
                statement.setString(1, ip);
                statement.setInt(2, port);
                statement.executeUpdate();
            }

            //Delete instance from the crawler from the db
            sql = "DELETE " +
                    "FROM scrawlers " +
                    "WHERE id = (?)";
            statement = myConnection.prepareStatement(sql);
            //Set params
            statement.setString(1, instance);
            statement.executeUpdate();
        } catch (SQLException e) {
            guiLabelManagement.setAlertPopUp(e.getMessage());
        }
    }

    /**
     * Adds the current download rate and a timestamp of when was this download rate updated
     */
    void addDownloadRateToDB(Double currPercentage) {
        //Get the current time
        Calendar calendar = Calendar.getInstance();
        java.sql.Timestamp timestamp = new java.sql.Timestamp(calendar.getTime().getTime());
        try {

            String sql = "UPDATE scrawlers " +
                    "SET download_rate = ?, last_updated = ? " +
                    "WHERE id = ?";
            PreparedStatement statement = myConnection.prepareStatement(sql);
            statement.setDouble(1, currPercentage);
            statement.setTimestamp(2, timestamp);
            statement.setString(3, instance);
            statement.executeUpdate();
        } catch (SQLException e) {
            guiLabelManagement.setAlertPopUp(e.getMessage());
        }
    }


    /**
     * Adds an error to the error table
     */
    void addError(String error) {
        //Get the current time
        Calendar calendar = Calendar.getInstance();
        java.sql.Timestamp timestamp = new java.sql.Timestamp(calendar.getTime().getTime());
        try {
            String sql = "INSERT INTO errors " +
                    "(scrawler_id, location, error, time) " +
                    "VALUES(?,?,?,?) ";
            PreparedStatement statement = myConnection.prepareStatement(sql);
            statement.setString(1, instance);
            statement.setString(2, "" + getClass().getProtectionDomain().getCodeSource().getLocation());
            statement.setString(3, error);
            statement.setTimestamp(4, timestamp);
            statement.executeUpdate();
        } catch (SQLException e) {
            guiLabelManagement.setAlertPopUp(e.getMessage());
        }
    }

    /**
     * Performs an operation in an instance
     */
    void performOperation(String instance, String operation) {
        try {
            String sql = "UPDATE scrawlers " +
                    "SET operation = ? " +
                    "WHERE id = ? ";
            PreparedStatement statement = myConnection.prepareStatement(sql);
            statement.setString(1, operation);
            statement.setString(2, instance);
            statement.executeUpdate();
        } catch (SQLException e) {
            guiLabelManagement.setAlertPopUp(e.getMessage());
        }
    }

    /**
     * Returns the operation to perform. Empty if none
     */
    String getOperationToPerform() {
        try {
            String sql = "SELECT operation " +
                    "     FROM scrawlers" +
                    "     WHERE id = ?";
            PreparedStatement statement = myConnection.prepareStatement(sql);
            statement.setString(1, instance);

            ResultSet res = statement.executeQuery();
            //If there are no records about this proxy in our db, then it is already closed
            if (!res.isBeforeFirst()) {
                return "";
            }
            //Process result, if the id appears, then the current crawler is already using it
            while (res.next()) {
                String operation = res.getString("operation");
                if (operation.equals(WebServer.SupportedOperations.close.toString())) {
                    return WebServer.SupportedOperations.close.name();
                } else if (operation.equals(WebServer.SupportedOperations.clean.toString())) {
                    return WebServer.SupportedOperations.clean.name();
                } else if (operation.equals(WebServer.SupportedOperations.update.toString())) {
                    return WebServer.SupportedOperations.update.name();
                }
            }
        } catch (NullPointerException e) {
            return "";
        } catch (SQLException e) {
            guiLabelManagement.setAlertPopUp(e.getMessage());
        }
        return "";
    }

    /**
     * Returns the cookies for a given proxy
     *
     * @return Set
     */
    Set<Cookie> getCookies(Proxy proxy, SearchEngine.SupportedSearchEngine engine) {
        Set<Cookie> result = new HashSet<>();
        try {
            String sql = "SELECT cookies FROM proxies WHERE ip = ? AND port = ? AND search_engine = ?";
            PreparedStatement statement = myConnection.prepareStatement(sql);
            statement.setString(1, proxy.getProxy());
            statement.setInt(2, proxy.getPort());
            statement.setString(3, engine.name());
            ResultSet res = statement.executeQuery();

            if (!res.isBeforeFirst()) {
                return result;
            }

            while (res.next()) {
                String cookieString = res.getString("cookies");
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
        verifyIfConnectionWorks(dbName, serverAddress, port, username, password);
    }


    void closeConnection() throws SQLException {
        myConnection.close();
    }

    /**
     * Returns all instances with their respective last update time
     */
    HashSet<WebServer.ScrawlerInstance> getAllInstances() {
        HashSet<WebServer.ScrawlerInstance> result = new HashSet<>();
        try {
            String sql = "SELECT id, last_updated FROM scrawlers ";
            PreparedStatement statement = myConnection.prepareStatement(sql);
            ResultSet res = statement.executeQuery();
            //If there are no records about this proxy in our db, then no crawler can be using it at the moment
            if (!res.isBeforeFirst()) {
                return result;
            }
            //Process result, if the id appears, then the current crawler is already using it
            while (res.next()) {
                String instanceID = res.getString("id");
                DateTime time;
                if (res.getTimestamp("last_updated") != null) {
                    time = new DateTime(res.getTimestamp("last_updated"));
                } else {
                    time = null;
                }
                WebServer.ScrawlerInstance curr = new WebServer.ScrawlerInstance(instanceID, time);
                result.add(curr);
            }
        } catch (SQLException e) {
            guiLabelManagement.setAlertPopUp(e.getMessage());
        }
        return result;
    }

    HashMap<String, DateTime> getAllWebsites() {
        HashMap<String, DateTime> result = new HashMap<>();
        try {
            String sql = "SELECT website, visited FROM list_of_websites ";
            PreparedStatement statement = myConnection.prepareStatement(sql);
            ResultSet res = statement.executeQuery();
            //If there are no records about this proxy in our db, then no crawler can be using it at the moment
            if (!res.isBeforeFirst()) {
                return result;
            }
            //Process result, if the id appears, then the current crawler is already using it
            while (res.next()) {
                String website = res.getString("website");
                DateTime time;
                if (res.getTimestamp("visited") != null) {
                    time = new DateTime(res.getTimestamp("visited"));
                } else {
                    time = null;
                }
                result.put(website, time);
            }
        } catch (SQLException e) {
            guiLabelManagement.setAlertPopUp(e.getMessage());
        }
        return result;
    }

    static void insertAllWebsites() {
        ProxiesDownloader proxiesDownloader = new ProxiesDownloader();
        ArrayList<String> websites = proxiesDownloader.getWebsites();
        for (String website : websites) {
            try {
                String sql = "INSERT INTO list_of_websites " +
                        "(website) " +
                        "VALUES (?)";
                PreparedStatement statement = myConnection.prepareStatement(sql);
                statement.setString(1, website);
                statement.executeUpdate();
            } catch (SQLException e) {
                guiLabelManagement.setAlertPopUp(e.getMessage());
            }
        }
    }

    void updateWebsiteTime(String website) {
        try {
            Calendar calendar = Calendar.getInstance();
            java.sql.Timestamp timestamp = new java.sql.Timestamp(calendar.getTime().getTime());

            String sql = "UPDATE list_of_websites SET visited = ? WHERE website = ? ";
            PreparedStatement statement = myConnection.prepareStatement(sql);
            statement.setTimestamp(1, timestamp);
            statement.setString(2, website);
            statement.executeUpdate();
        } catch (SQLException e) {
            guiLabelManagement.setAlertPopUp(e.getMessage());
        }
    }

    void addProxyToListOfProxies(Proxy proxy) throws IllegalArgumentException {
        try {
            String sql = "INSERT INTO list_of_proxies " +
                    "(ip, port, time) " +
                    "VALUES (?, ?, ?)";
            PreparedStatement statement = myConnection.prepareStatement(sql);
            statement.setString(1, proxy.getProxy());
            statement.setInt(2, proxy.getPort());
            Calendar calendar = Calendar.getInstance();
            java.sql.Timestamp timestamp = new java.sql.Timestamp(calendar.getTime().getTime());
            statement.setTimestamp(3, timestamp);
            statement.executeUpdate();
        } catch (MySQLIntegrityConstraintViolationException e) {
            throw new IllegalArgumentException();
        } catch (SQLException e) {
            guiLabelManagement.setAlertPopUp(e.getMessage());
        }
    }


    /**
     * Deletes a locked proxy from the list of proxies
     *
     * @param proxy
     */
    void deleteProxyFromListOfProxies(Proxy proxy) {
        try {
            String sql = "DELETE FROM list_of_proxies WHERE ip = ? AND port = ?";
            PreparedStatement statement = myConnection.prepareStatement(sql);
            statement.setString(1, proxy.getProxy());
            statement.setInt(2, proxy.getPort());
            statement.executeUpdate();
        } catch (SQLException e) {
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
            String sql = "SELECT * FROM list_of_proxies ";
            PreparedStatement statement = myConnection.prepareStatement(sql);
            ResultSet res = statement.executeQuery();
            //If there are no records about this proxy in our db, then no crawler can be using it at the moment
            if (!res.isBeforeFirst()) {
                return result;
            }
            //Process result, if the id appears, then the current crawler is already using it
            while (res.next()) {
                String ip = res.getString("ip");
                int port = res.getInt("port");
                DateTime time = new DateTime(res.getTimestamp("time"));
                Proxy p = new Proxy(ip, port);
                p.setTime(time);
                result.add(p);
            }
        } catch (SQLException e) {
            e.printStackTrace(System.out);
            guiLabelManagement.setAlertPopUp(e.getMessage());
        }
        return result;
    }

}
