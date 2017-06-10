package com.rc.crawler;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXTextField;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;

import java.net.URL;
import java.util.Queue;
import java.util.ResourceBundle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Controller implements Initializable {
    //private Crawler crawler;
    private String title;
    private boolean hasSearchedBefore = false;
    private String numOfPDFToDownload;
    private LoadingBox loading;
    private DoWork task;
    private volatile Crawler crawler;

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
    private JFXButton searchButton;

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
    void searchOnClick(ActionEvent event) {

        String text = inputText.getText();
        if (text.isEmpty()) {
            displayAlert("Please write a title");
        } else {
            title = text;
            //Display loading message dialog
            informationPanel("Loading...");
            task = new DoWork();
            task.setType("search");
            new Thread(task).start();
        }

    }




    @FXML
    void downloadOnClick(ActionEvent event) {
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

    public void download() {
        try {
            crawler.getPDFs(Integer.parseInt(numOfPDFToDownload));
        } catch (Exception e) {
            displayAlert(e.getMessage());
        }
    }
    /**
     * Performs all the operations to search for a paper in Google Scholar
     */
    public void search() {


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

    synchronized void updateWorkingProxiesLabel(String s) {
        System.out.println("Should have updated");
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                String[] input = s.split(",");
                StringBuilder sb = new StringBuilder();
                String[] curr = workingProxyOutput.getText().split("\n");
                sb.append("Working Proxies:");
                if (input[0].equals("remove")){
                    //Then we remove the last one
                    for (int i =2; i< curr.length; i++) {
                        sb.append("\n").append(curr[i]);
                    }
                    workingProxyOutput.setText(sb.toString());
                }
                else {
                    for (int i =1; i< curr.length; i++) {
                        sb.append("\n").append(curr[i]);
                    }
                    sb.append("\n").append(input[1]);
                    workingProxyOutput.setText(sb.toString());
                }

            }
        });
    }

    void updateConnectionOutput(String text) {
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                StringBuilder sb = new StringBuilder();
                sb.append(text).append("\n").append(connectionOutput.getText());
                connectionOutput.setText(sb.toString());
            }
        });
    }

    void updateNumberOfPDFs(String text) {
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                pdfsDownloadedLabel.setText(text);
            }
        });
    }


    private void updateSearchLabel(String s) {
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                searchLabel.setText(s);
            }
        });
    }



    public void displayAlert(String message) {
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Error");
                alert.setHeaderText(null);
                alert.setContentText(message);

                alert.showAndWait();
            }
        });

    }

    public void updateOutput(String message) {
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                output.setText(message);
                if (message.equals("Connected!")) {
                    //Close loading popup once it has connected
                    loading.close();
                    System.out.println("connected");
                }

            }
        });
    }

    public void informationPanel(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Information");
        alert.setHeaderText(null);
        alert.setContentText(message);

        alert.showAndWait();
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        task = new DoWork();
        scrollPane.setStyle("-fx-background-color:transparent;");

        task.setType("initialize");
        new Thread(task).start();
    }


    class DoWork extends Task<Void> {
        String type;

        @Override
        protected Void call() throws Exception {

            if (type.equals("search")) {
                progressBar.progressProperty().setValue(0);
                search();
                progressBar.progressProperty().setValue(1);
            } else if (type.equals("download")) {
                download();
                progressBar.progressProperty().setValue(1);

            } else {
                loading = new LoadingBox();
                Platform.runLater(new Runnable() {
                    @Override
                    public void run() {
                        loading.display();
                    }
                });
                crawler = new Crawler();
                //Add listeners to the different labels and loaders
                crawler.getAlertPopUpProperty().addListener((observable, oldValue, newValue) -> displayAlert(newValue));
                crawler.getOutputProperty().addListener((observable, oldValue, newValue) -> updateOutput(newValue));
                crawler.getLoadBarProperty().addListener((observable, oldValue, newValue) -> progressBar.progressProperty().setValue(newValue));
                crawler.getSearchResultLabelProperty().addListener(((observable, oldValue, newValue) -> updateSearchLabel(newValue)));
                crawler.getConnectionOutput().addListener(((observable, oldValue, newValue) -> updateConnectionOutput(newValue)));
                crawler.getNumberOfWorkingIPs().addListener(((observable, oldValue, newValue) -> {
                    updateWorkingProxiesLabel(newValue);
                }));
                crawler.getNumberOfPDF().addListener(((observable, oldValue, newValue) -> updateNumberOfPDFs(String.valueOf(newValue))));

                //Load the crawler
                crawler.loadCrawler();

            }
            return null;

        }

        public void setType(String type) {
            this.type = type;
        }
    }


}
