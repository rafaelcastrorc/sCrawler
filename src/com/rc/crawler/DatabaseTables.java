package com.rc.crawler;

import javafx.event.ActionEvent;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Created by rafaelcastro on 9/7/17.
 * Creates the different tables used by the database
 */
class DatabaseTables {
    private Connection myConnection;
    private GUILabelManagement guiLabelManagement;

    DatabaseTables(Connection myConnection, GUILabelManagement guiLabelManagement) {
        this.myConnection = myConnection;
        this.guiLabelManagement = guiLabelManagement;
        createProxiesTable();
        createScrawlerTable();
        createScrawlerToProxyTable();
        createErrorsTable();
        createListOfProxiesTable();
        createListOfWebsitesTable();
        createVersionTable();
    }


    /**
     * Creates the table that holds the different proxy compiling websites
     */
    private void createListOfWebsitesTable() {
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
     * Inserts the different websites into the websites table
     */
    void insertAllWebsites() {
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


    /**
     * Creates a table for the different proxies scrapped from the proxy compiling websites
     */
    private void createListOfProxiesTable() {
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
    }

    /**
     * Creates a table to hold the different errors thrown by each instance
     */
    private void createErrorsTable() {
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
    }

    /**
     * Creates a table that maps each scrawler to the proxy they are using
     */
    private void createScrawlerToProxyTable() {
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


    }

    /**
     * Creates a table to hold all the locked and unlocked proxies
     */
    void createProxiesTable() {
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
    }

    /**
     * Creates a dialog when there are no proxies table in the database, to ask the user which proxies to upload
     */
    private void showProxiesDialog() {
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
     * Creates a table to hold all the current active sCrawlers
     */
    private void createScrawlerTable() {
        //Create scrawlers table
        String createScrawlerTable = "CREATE TABLE scrawlers(" +
                "  id            VARCHAR(40)        NOT NULL PRIMARY KEY," +
                "  location      TEXT               NOT NULL," +
                "  download_rate DOUBLE PRECISION DEFAULT '0.00' NULL," +
                "  last_updated  TIMESTAMP          NULL," +
                "  operation     ENUM('close', 'clean', 'none', 'criticalUpdate', 'standardUpdate', 'minorUpdate') 'none'," +
                "  CONSTRAINT scrawlers_id_uindex" +
                "  UNIQUE (id)" +
                ")";

        try {
            Statement stmt = myConnection.createStatement();
            stmt.execute(createScrawlerTable);
        } catch (SQLException ignored) {
        }
    }

    /**
     * Creates a table with the
     */
    private void createVersionTable() {
        String createScrawlerTable = "CREATE TABLE versions(" +
                "  version            VARCHAR(40)        NOT NULL PRIMARY KEY," +
                "  description      TEXT               NOT NULL," +
                "  CONSTRAINT scrawlers_version_uindex" +
                "  UNIQUE (version)" +
                ")";
        try {
            Statement stmt = myConnection.createStatement();
            stmt.execute(createScrawlerTable);
        } catch (SQLException ignored) {
        }
    }

}
