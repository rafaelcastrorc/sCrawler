package com.rc.crawler;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXTextField;
import javafx.application.Platform;

import javafx.concurrent.Task;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.Label;
import javafx.stage.*;
import javafx.stage.Window;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Controller implements Initializable {
   // private boolean hasSearchedBefore = false;
    private String numOfPDFToDownload;
    private LoadingWindow loading;
    private volatile Crawler crawler;
    private Window window;
    private HashSet<String> articleNames;
    private Map<Long, String> mapThreadToTitle = Collections.synchronizedMap(new HashMap<Long, String>());
    private Map<Long, Boolean> mapThreadToBool = Collections.synchronizedMap(new HashMap<Long, Boolean>());
    private GUILabelManagement guiLabels;
    private Map<Long, SearchResultWindow> threadToSearchResultWindow = Collections.synchronizedMap(new HashMap<Long, SearchResultWindow>());;
    private ExecutorService singleThreadExecutor;
    private String citingPapersURL = "";

    @FXML
    private Label output;
    @FXML
    private Label outputMultiple;

    @FXML
    private Label pdfsDownloadedLabel;

    @FXML
    private Label pdfsDownloadedMultipleLabel;

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
    private JFXButton downloadButtonMultiple;

    @FXML
    private JFXTextField numberOfPDFsMultiple;



    @SuppressWarnings("WeakerAccess")
    public Controller() {
    }

    /**
     * Called when the click button is pressed. Creates a new task to start searching
     */
    @FXML
    void searchOnClick() {

        String text = inputText.getText();
        if (text.isEmpty()) {
            displayAlert("Please write a title");
        } else {
            //Display loading message dialog
            informationPanel("Loading...");
            updateOutput("Searching...");
            //Create a new task to perform search in background
            DoWork task = new DoWork("search", text);
            singleThreadExecutor.submit(task);
        }
    }


    /**
     * Performs all the operations to search for a paper in Google Scholar.
     * Method is called inside a task.
     * @param title title that we are looking for. Null if the method is not called from a thread
     */
    private String[] search(String title, boolean isMultipleSearch) {
        updateSearchLabel("Loading...");
        SearchResultWindow searchResultWindow = new SearchResultWindow();
        threadToSearchResultWindow.put(Thread.currentThread().getId(), searchResultWindow);
        guiLabels.getMultipleSearchResult().addListener(((observable, oldValue, newValue) -> searchResultWindow.addItemToListView(newValue)));
        //Array containing the number of citations and the url of the citing articles
        boolean hasSearchedBefore = false;
        if (mapThreadToBool.get(Thread.currentThread().getId())!= null) {
            hasSearchedBefore = true;
        }
        else {
            mapThreadToBool.put(Thread.currentThread().getId(), true);
        }
        String[] result = crawler.searchForArticle(title, hasSearchedBefore);

        String numberOfCitations = result[0];
        if (numberOfCitations.isEmpty() || numberOfCitations.equals("Provide feedback")) {
            numberOfCitations = "Could not find paper";
            updateOutput("Could not find paper...");
            Logger logger = Logger.getInstance();
            try {
                logger.setReportWriter(true, "Report");
                logger.writeReport("\n-Could not find paper: "+ title+"\n");
            } catch (IOException e) {
               displayAlert(e.getMessage());
            }

        } else if (numberOfCitations.equals("There was more than 1 result found for your given query")) {
            numberOfCitations = "ERROR: There was more than 1 result found for your given query";
            displaySearchResults(title, isMultipleSearch);
        } else {
            numberOfCitations = numberOfCitations + " different papers";
            updateOutput("Paper found!");
            if (!isMultipleSearch) {
                citingPapersURL = result[1];
            }

            downloadButton.setDisable(false);
        }
        //this.hasSearchedBefore = true;
        updateOutput("Done searching");
        updateSearchLabel(numberOfCitations);
        return result;

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

            if (matcher.find()) {
                numOfPDFToDownload = matcher.group();
                try {
                    DoWork task = new DoWork("download", null);
                    singleThreadExecutor.submit(task);
                } catch (Exception e1) {
                    displayAlert(e1.getMessage());
                }

            } else {
                displayAlert("Please only write numbers here.");
            }
        }



    /**
     * Call the getPDFs method inside of crawler to start downloading PDFs.
     * Method is called inside a task.
     */
    private void download(String currTitle, String citingPaperURL) {
        try {
            PDFDownloader pdfDownloader = new PDFDownloader();
            crawler.setPdfDownloader(pdfDownloader);
            //Generate a unique folder name
            String path = pdfDownloader.createUniqueFolder(currTitle);
            int numberOfPDFsDownloaded = crawler.getPDFs(Integer.parseInt(numOfPDFToDownload), citingPaperURL);

            Logger logger = Logger.getInstance();
            logger.setReportWriter(true, "Report");
            logger.writeReport("\n-Paper downloaded: "+ currTitle + "\n   Number of PDFs downloaded: "+ numberOfPDFsDownloaded + "/"+numOfPDFToDownload+
                    "\n     Folder path: " + path+ "\n");

            logger.setReportWriter(false, path + "/ArticleName");
            logger.writeReport("Searched paper: "+currTitle);

        } catch (Exception e) {
            displayAlert(e.getMessage());
        }
    }



    @FXML
    void uploadOnClick(Event e) {
        Node node =  (Node) e.getSource();
        window = node.getScene().getWindow();
        DoWork task = new DoWork("upload", null);
        singleThreadExecutor.submit(task);

    }

    private void openFile() {
        Platform.runLater(() -> {
            FileChooser fileChooser = new FileChooser();
            configureFileChooser(fileChooser);
            File file = fileChooser.showOpenDialog(window);


            if (file == null) {
               informationPanel("Please upload a file.");
            }
            else if (!file.exists() || !file.canRead()) {
                displayAlert("There was an error opening the file");
            }
            else if (file.length() < 1) {
                displayAlert("File is empty");
            }
            else {
                articleNames = new HashSet<>();
                try {
                    Scanner scanner = new Scanner(file);
                    while (scanner.hasNextLine()) {
                        String line = scanner.nextLine();
                        articleNames.add(line);
                    }
                    updateOutput("File has been submitted.");
                    downloadButtonMultiple.setDisable(false);

                } catch (FileNotFoundException e) {
                    displayAlert(e.getMessage());
                }

            }
        });

    }

    private void configureFileChooser(FileChooser fileChooser) {
        fileChooser.setTitle("Please select the file");
        FileChooser.ExtensionFilter extFilter = new FileChooser.ExtensionFilter("TXT files (*.txt)", "*.txt");
        FileChooser.ExtensionFilter extFilter2 = new FileChooser.ExtensionFilter("CSV file (*.csv)", "*.csv");

        fileChooser.getExtensionFilters().add(extFilter2);
        fileChooser.getExtensionFilters().add(extFilter);
    }

    @FXML
    void multipleDownloadOnClick() {
        progressBar.progressProperty().setValue(0);
        String text = numberOfPDFsMultiple.getText();
        //Make sure that only numbers are accepted
        Pattern numbersOnly = Pattern.compile("^[0-9]+$");
        Matcher matcher = numbersOnly.matcher(text);

            if (matcher.find()) {
                numOfPDFToDownload = matcher.group();
                int numberOfThreadsToUse = optimizer();
                ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreadsToUse, new MyThreadFactory());
                for (String article : articleNames) {
                    //Re use task, and do has search before
                    DoWork task = new DoWork("multipleSearch", article);
                    executorService.submit(task);
                }

            } else {
                displayAlert("Please only write numbers here.");
            }
        }



    int optimizer() {
    return 2;
    }


    /**
     * Adds or removes elements to the list of proxies that work
     *
     * @param s Write: remove, if you want to remove a proxy.
     *          Write: add,proxyNumber to add a new proxy
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
        Platform.runLater(() -> connectionOutput.setText(text + "\n" + connectionOutput.getText()));
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
     * Updates the status label of the Single Article mode
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
     * Updates the status label of the Multiple Article Mode
     *
     * @param message String with the message to output
     */
    private void updateOutputMultiple(String message) {
        Platform.runLater(() -> {
        });
    }




    /**
     * Creates a pop up message that says Loading...
     */
    private void informationPanel(String s) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Information");
        alert.setHeaderText(null);
        alert.setContentText(s);
        alert.showAndWait();
    }


    private void displaySearchResults(String title, boolean isMultipleSearch) {
        Long currentThreadId = Thread.currentThread().getId();
        SearchResultWindow searchResultWindow = threadToSearchResultWindow.get(currentThreadId);
        searchResultWindow.setQueryStr(title);

        //Selection button for the search result window
        searchResultWindow.select.setOnAction(e-> {
            String selection;
            selection = (String) searchResultWindow.searchResultListView.getSelectionModel().getSelectedItem();
            if (selection == null || selection.isEmpty()) {
                displayAlert("Please select one search result.");
            }
            else {
                //Retrieves the number of citation and url for the selected search result
                String[] array = crawler.getSearchResultToLink().get(selection);
                if (!isMultipleSearch) {
                    citingPapersURL = array[0];
                }
                //Update search label to display result
                updateSearchLabel(array[1]);
                searchResultWindow.store(array);
                searchResultWindow.close();
                downloadButton.setDisable(false);
                threadToSearchResultWindow.put(currentThreadId,searchResultWindow);
                mapThreadToTitle.put(currentThreadId,selection);

            }
        });


        //Do not download button for the search result window
        searchResultWindow.doNotDownload.setOnAction(e -> {
            //Retrieve the search result window associated with thread
            searchResultWindow.close();
            //Store the result and then add it back to map
            searchResultWindow.store(new String[]{"File not downloaded", null});
            threadToSearchResultWindow.put(Thread.currentThread().getId(),searchResultWindow);

            updateOutput("File not downloaded");
            Logger logger = Logger.getInstance();
            String currTitle = mapThreadToTitle.get(Thread.currentThread().getId());
            if (currTitle == null) {
                currTitle = title;
            }
            try {
                logger.setReportWriter(true, "Report");
                logger.writeReport("\n-Paper not downloaded: "+ currTitle+"\n");
            }
            catch(IOException e2) {
                displayAlert(e2.getMessage());
            }
        });


        FutureTask<Void> futureTask = new FutureTask<>(searchResultWindow);
        Platform.runLater(futureTask);
        if (isMultipleSearch) {
            try {
                futureTask.get();
            } catch (InterruptedException | ExecutionException e) {
                displayAlert(e.getMessage());
            }
        }


    }

    /**
     * Call upon star
     *
     * @param location  Location
     * @param resources ResourceBundle
     */
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        //To perform tasks on single article mode.
        this.singleThreadExecutor= Executors.newSingleThreadExecutor(new MyThreadFactory());
        File dir = new File("DownloadedPDFs");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdir();
        Logger logger =Logger.getInstance();

        //Create a report, or write to an existing one.
        try {
            logger.setReportWriter(true, "Report");
            DateTimeFormatter formatter = DateTimeFormat.forPattern("dd/MM/yyyy HH:mm:ss");
            //Write current date
            logger.writeReport("\n\n------------------------------------------------\n"+ new DateTime().toString(formatter));
        } catch (IOException e) {
            displayAlert(e.getMessage());
        }

        guiLabels = new GUILabelManagement();
        //Start loading crawler. Show loading screen until first connection found
        DoWork task = new DoWork("initialize", null);
        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();


    }



    /**
     * Inner Task class. Allows to perform computations in the background while keeping GUI responsive
     */
    class DoWork extends Task<Void> {
        //Type of task that will be performed
        private final String type;
        private String article;


        /**
         * Initializes new task. Takes as a parameter the type of task that will be performed. Task include: download, search, initialize, close
         * And the title of the article associated with the thread.
         * @param type String with the type of task
         * @param article Article associated with the thread
         */
        DoWork(String type, String article) {
            this.type = type;
            this.article = article;
        }

        /**
         * Called upon start of task. Depending on the task, it processes a different method
         *
         * @return null
         */
        @Override
        protected Void call() {

            //The different types of background tasks that need to be completed
            switch (type) {
                case"multipleSearch":
                    String url = "";
                    progressBar.progressProperty().setValue(0);
                    String[] result = search(article, true);
                    String numOfCitations = result[0];

                    if (numOfCitations.isEmpty() || numOfCitations.equals("Provide feedback")) {
                        //We don't download if this happens and omit the file
                        //customOutput.setText("File could not be downloaded);
                        progressBar.progressProperty().setValue(1);
                        break;

                    }
                    else if (numOfCitations.equals("There was more than 1 result found for your given query")) {
                        //Get search result window

                        SearchResultWindow searchResultWindow = threadToSearchResultWindow.get(Thread.currentThread().getId());
                        if (searchResultWindow.getNumberOfCitations().equals("File not downloaded")) {
                            //customOutput.setText("File could not be downloaded);
                            progressBar.progressProperty().setValue(1);
                            break;
                        }
                        else {
                            url = searchResultWindow.getCitingPapersURL();
                            article = mapThreadToTitle.get(Thread.currentThread().getId());
                        }
                    }
                    //Custom output.setText("Done Searching");
                    progressBar.progressProperty().setValue(1);
                    if (url.isEmpty()) {
                        //If url was not updated in search result window, then it was obtained through a normal search
                        url = result[1];
                    }
                    progressBar.progressProperty().setValue(0);
                    download(article, url);
                    progressBar.progressProperty().setValue(1);
                    break;

                case "search":
                    progressBar.progressProperty().setValue(0);
                    if (article != null) {
                        //Only happens when we are using threads to do multiple searches
                        mapThreadToTitle.put(Thread.currentThread().getId(), article);
                    }
                    search(article, false);
                    //In case the title changed, then retrieve the new article name
                    article = mapThreadToTitle.get(Thread.currentThread().getId());
                    progressBar.progressProperty().setValue(1);
                    break;
                case "download":
                    //Traditional download
                    download(mapThreadToTitle.get(Thread.currentThread().getId()), citingPapersURL);
                    progressBar.progressProperty().setValue(1);
                    break;
                case "upload":
                    openFile();
                    break;
                default:
                    //Loading crawler case
                    loading = new LoadingWindow();
                    //Create a new loading box to display while application loads
                    Platform.runLater(() -> loading.display());
                    crawler = new Crawler(guiLabels);
                    //For single article mode
                    guiLabels.getAlertPopUp().addListener((observable, oldValue, newValue) -> displayAlert(newValue));
                    guiLabels.getOutput().addListener((observable, oldValue, newValue) -> updateOutput(newValue));
                    guiLabels.getLoadBar().addListener((observable, oldValue, newValue) -> updateProgressBar(newValue.doubleValue()));
                    guiLabels.getSearchResultLabel().addListener(((observable, oldValue, newValue) -> updateSearchLabel(newValue)));
                    guiLabels.getConnectionOutput().addListener(((observable, oldValue, newValue) -> updateConnectionOutput(newValue)));
                    guiLabels.getNumberOfWorkingIPs().addListener(((observable, oldValue, newValue) -> updateWorkingProxiesLabel(newValue)));
                    guiLabels.getNumberOfPDFs().addListener(((observable, oldValue, newValue) -> updateNumberOfPDFs(String.valueOf(newValue))));
                    //For multiple article mode
                    //Load the crawler
                    crawler.loadCrawler();
                    break;
            }
            return null;

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
