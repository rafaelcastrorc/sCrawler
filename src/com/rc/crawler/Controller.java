package com.rc.crawler;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXTabPane;
import com.jfoenix.controls.JFXTextField;
import javafx.application.Platform;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Controller implements Initializable {
    private String title;
    private boolean hasSearchedBefore = false;
    private String numOfPDFToDownload;
    private LoadingBox loading;
    private DoWork task;
    private volatile Crawler crawler;

    @SuppressWarnings("WeakerAccess")
    public Controller() {

    }

    @FXML
    private Label output;

    @FXML
    private Label pdfsDownloadedLabel;

    @FXML
    private JFXTextField numberOfPDFs;

    @FXML
    private JFXButton downloadButton;

    @FXML
    private ProgressBar progressBar;

    @FXML
    private JFXTextField inputText;

    @FXML
    private Label searchLabel;

    @FXML
    private Label workingProxyOutput;

    @FXML
    private Label connectionOutput;

    @FXML
    private ScrollPane scrollPane;

    @FXML
    private JFXTabPane tabPane;

    @FXML
    private Tab tab2;

    @FXML
    private Tab tab1;
    /**
     * Called when the click button is pressed. Creates a new task to start searching
     */
    @FXML
    void searchOnClick() {

        String text = inputText.getText();
        if (text.isEmpty()) {
            displayAlert("Please write a title");
        } else {
            title = text;
            //Display loading message dialog
            informationPanel();
            task = new DoWork();
            task.setType("search");
            new Thread(task).start();
        }

    }


    /**
     * Called when the download button is pressed. Creates a new task to start downloading PDFs
     */
    @FXML
    void downloadOnClick() {
        progressBar.progressProperty().setValue(0);
        String text = numberOfPDFs.getText();
        //Make sure that only numbers are accepted
        Pattern numbersOnly = Pattern.compile("^[0-9]+$");
        Matcher matcher = numbersOnly.matcher(text);
        if (!hasSearchedBefore) {
            displayAlert("Please search for a paper before pressing download.");
        } else {
            if (matcher.find()) {
                numOfPDFToDownload = matcher.group();
                try {
                    task = new DoWork();
                    task.setType("download");
                    new Thread(task).start();


                } catch (Exception e1) {
                    displayAlert(e1.getMessage());
                }

            } else {
                displayAlert("Please only write numbers here.");
            }
        }

    }

    /**
     * Call the getPDFs method inside of crawler to start downloading PDFs
     */
    private void download() {
        try {
            crawler.getPDFs(Integer.parseInt(numOfPDFToDownload));
        } catch (Exception e) {
            displayAlert(e.getMessage());
        }
    }

    /**
     * Performs all the operations to search for a paper in Google Scholar
     */
    private void search() {


        updateSearchLabel("Loading...");

        crawler.searchForArticle(title, hasSearchedBefore);

        String numberOfCitations = crawler.getNumberOfCitations();
        if (numberOfCitations.isEmpty() || numberOfCitations.equals("Provide feedback")) {
            numberOfCitations = "Could not find paper";
            hasSearchedBefore = false;
        } else if (numberOfCitations.equals("There was more than 1 result found for your given query")) {
            numberOfCitations = "ERROR: There was more than 1 result found for your given query";
        } else {
            numberOfCitations = numberOfCitations + " different papers";
            hasSearchedBefore = true;
            downloadButton.setDisable(false);


        }
        updateSearchLabel(numberOfCitations);

    }

    /**
     * Adds or removes elements to the list of proxies that work
     *
     * @param s remove,proxyNumber if you want to remove a proxy, add,proxyNumber to add a new proxy
     */
    private synchronized void updateWorkingProxiesLabel(String s) {
        Platform.runLater(() -> {
            String[] input = s.split(",");
            StringBuilder sb = new StringBuilder();
            String[] curr = workingProxyOutput.getText().split("\n");
            sb.append("Working Proxies:");
            if (input[0].equals("remove")) {
                //Then we remove the last one
                for (int i = 2; i < curr.length; i++) {
                    sb.append("\n").append(curr[i]);
                }
                workingProxyOutput.setText(sb.toString());
            } else {
                for (int i = 1; i < curr.length; i++) {
                    sb.append("\n").append(curr[i]);
                }
                sb.append("\n").append(input[1]);
                workingProxyOutput.setText(sb.toString());
            }

        });
    }

    /**
     * Updates the connection output. Appends to start.
     *
     * @param text text to add to output
     */
    private void updateConnectionOutput(String text) {
        Platform.runLater(() -> {
            connectionOutput.setText(text + "\n" + connectionOutput.getText());
        });
    }

    /**
     * Updates the label with the number of PDFs downloaded
     *
     * @param text String with the number of PDFs
     */
    private void updateNumberOfPDFs(String text) {
        Platform.runLater(() -> pdfsDownloadedLabel.setText(text));
    }


    /**
     * Updates the search label with the search result found
     *
     * @param s String with the search result
     */
    private void updateSearchLabel(String s) {
        Platform.runLater(() -> searchLabel.setText(s));
    }


    /**
     * Displays a pop up alert message
     *
     * @param message String with the message to display
     */
    private void displayAlert(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText(null);
            alert.setContentText(message);

            alert.showAndWait();
        });

    }


    /**
     * Updates the progress bar
     *
     * @param num Double with the current progress out of 1
     */
    private void updateProgressBar(Double num) {
        Platform.runLater(() -> progressBar.progressProperty().setValue(num));
    }


    /**
     * Updates the status label
     *
     * @param message String with the message to output
     */
    private void updateOutput(String message) {
        Platform.runLater(() -> {
            output.setText(message);
            if (message.equals("Connected!")) {
                //Close loading popup once it has connected
                loading.close();
                System.out.println("connected");
            }

        });
    }

    /**
     * Creates a pop up message that says Loading...
     */
    private void informationPanel() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Information");
        alert.setHeaderText(null);
        alert.setContentText("Loading...");

        alert.showAndWait();
    }


    /**
     * Call upon star
     *
     * @param location  Location
     * @param resources ResourceBundle
     */
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        task = new DoWork();
        task.setType("initialize");
        new Thread(task).start();
    }


    /**
     * Inner Task class. Allows to perform computations in the background while keeping GUI responsive
     */
    class DoWork extends Task<Void> {
        //Type of task that will be performed
        String type;

        /**
         * Called upon start of task. Depending on the task, it processes a different method
         *
         * @return null
         */
        @Override
        protected Void call() {

            //The different types of background tasks that need to be completed
            switch (type) {

                case "search":
                    progressBar.progressProperty().setValue(0);
                    search();
                    progressBar.progressProperty().setValue(1);
                    break;
                case "download":
                    download();
                    progressBar.progressProperty().setValue(1);

                    break;
                default:
                    //Loading crawler case
                    loading = new LoadingBox();
                    //Create a new loading box to display while application loads
                    Platform.runLater(() -> loading.display());
                    crawler = new Crawler();
                    //Add listeners to the different labels and loaders
                    crawler.getAlertPopUpProperty().addListener((observable, oldValue, newValue) -> displayAlert(newValue));
                    crawler.getOutputProperty().addListener((observable, oldValue, newValue) -> updateOutput(newValue));
                    crawler.getLoadBarProperty().addListener((observable, oldValue, newValue) -> updateProgressBar(newValue.doubleValue()));
                    crawler.getSearchResultLabelProperty().addListener(((observable, oldValue, newValue) -> updateSearchLabel(newValue)));
                    crawler.getConnectionOutput().addListener(((observable, oldValue, newValue) -> updateConnectionOutput(newValue)));
                    crawler.getNumberOfWorkingIPs().addListener(((observable, oldValue, newValue) -> updateWorkingProxiesLabel(newValue)));
                    crawler.getNumberOfPDF().addListener(((observable, oldValue, newValue) -> updateNumberOfPDFs(String.valueOf(newValue))));

                    //Load the crawler
                    crawler.loadCrawler();
                    break;
            }
            return null;

        }

        /**
         * Sets the type of task that will be performed. Task include: download, search, initialize, close
         *
         * @param type String with the type of task
         */
        void setType(String type) {
            this.type = type;
        }
    }


    /**
     * Safely closes the entire application. Shuts down logger and threads.
     */
    void close() {
        Logger logger = Logger.getInstance();
        try {
            logger.closeLoggers();
        } catch (IOException e) {
            displayAlert("There was a problem closing the loggers");
        }
    }


}
