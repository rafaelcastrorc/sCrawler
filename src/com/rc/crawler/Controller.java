package com.rc.crawler;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXRadioButton;
import com.jfoenix.controls.JFXTextField;
import javafx.application.Platform;

import javafx.concurrent.Task;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.Label;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
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
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Controller implements Initializable {
    private String numOfPDFToDownload;
    private LoadingWindow loading;
    private volatile Crawler crawler;
    private Window window;
    private HashSet<String> articleNames;
    private Map<Long, String> mapThreadToTitle = Collections.synchronizedMap(new HashMap<Long, String>());
    private Map<Long, Boolean> mapThreadToBool = Collections.synchronizedMap(new HashMap<Long, Boolean>());
    private GUILabelManagement guiLabels;
    private ExecutorService singleThreadExecutor;
    private String citingPapersURL = "";
    private AtomicCounter atomicCounter;
    private boolean hasSearchBeforeMultiple = false;
    private SimultaneousDownloadsGUI simultaneousDownloadsGUI;
    private ExecutorService executorServiceMultiple;
    private int numOfConnectionsNeeded;
    boolean speedUp = false;
    @FXML
    private ScrollPane scrollPanel;
    @FXML
    private Label output;
    @FXML
    private Label outputMultiple;
    @FXML
    private Label pdfsDownloadedLabel;
    @FXML
    private Label pdfsDownloadedMultiple;
    @FXML
    private JFXTextField numberOfPDFs;
    @FXML
    private JFXButton downloadButton;
    @FXML
    private ProgressBar progressBar;
    @FXML
    private ProgressBar progressBarMultiple;
    @FXML
    private JFXTextField inputText;
    @FXML
    private Label searchLabel;
    @FXML
    private Text workingProxyOutput;
    @FXML
    private Text connectionOutput;
    @FXML
    private JFXButton downloadButtonMultiple;
    @FXML
    private JFXTextField numberOfPDFsMultiple;
    @FXML
    private JFXRadioButton increaseSpeedButton1;
    @FXML
    private JFXRadioButton increaseSpeedButton;
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
     *
     * @param title article that we are looking for.
     */
    private String[] search(String title, boolean isMultipleSearch) {
        updateSearchLabel("Loading...");
        SearchResultWindow searchResultWindow = new SearchResultWindow();
        guiLabels.associateThreadToSearchResultW(Thread.currentThread().getId(), searchResultWindow);
        //Array containing the number of citations and the url of the citing articles
        boolean hasSearchedBefore = false;
        if (mapThreadToBool.get(Thread.currentThread().getId()) != null) {
            hasSearchedBefore = true;
        } else {
            mapThreadToBool.put(Thread.currentThread().getId(), true);
        }
        String[] result = crawler.searchForArticle(title, hasSearchedBefore, isMultipleSearch);

        String numberOfCitations = result[0];
        if (numberOfCitations.isEmpty() || numberOfCitations.equals("Provide feedback")) {
            numberOfCitations = "Could not find paper";
            updateOutput("Could not find paper...");
            Logger logger = Logger.getInstance();
            try {
                logger.setReportWriter(true, "Report");
                logger.writeReport("\n-Could not find paper: " + title + "\n");
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
     *
     * @param currTitle        Title that we want to download
     * @param citingPaperURL   "cited by" URL
     * @param isMultipleSearch true if the method is called inside of the multiple articles mode
     */
    private void download(String currTitle, String citingPaperURL, boolean isMultipleSearch) {
        try {
            PDFDownloader pdfDownloader = new PDFDownloader();
            //Generate a unique folder name
            String path = pdfDownloader.createUniqueFolder(currTitle);
            if (isMultipleSearch) {
                simultaneousDownloadsGUI.updateArticleName(path);
            }
            int numberOfPDFsDownloaded = crawler.getPDFs(Integer.parseInt(numOfPDFToDownload), citingPaperURL, isMultipleSearch, pdfDownloader);
            if (isMultipleSearch) {
                updateNumberOfPDFsMultiple(numberOfPDFsDownloaded, false);
            }

            Logger logger = Logger.getInstance();
            logger.setReportWriter(true, "Report");
            logger.writeReport("\n-Paper downloaded: " + currTitle + "\n   Number of PDFs downloaded: " + numberOfPDFsDownloaded + "/" + numOfPDFToDownload +
                    "\n     Folder path: " + path + "\n");

            logger.setReportWriter(false, path + "/ArticleName");
            logger.writeReport("Searched paper: " + currTitle);

        } catch (Exception e) {
            displayAlert(e.getMessage());
        }
    }

    /**
     * Called when the user clicks the Upload button
     */
    @FXML
    void uploadOnClick(Event e) {
        Node node = (Node) e.getSource();
        window = node.getScene().getWindow();
        DoWork task = new DoWork("upload", null);
        singleThreadExecutor.submit(task);

    }

    /**
     * Handles the logic to upload a file into the program.
     */
    private void openFile() {
        Platform.runLater(() -> {
            FileChooser fileChooser = new FileChooser();
            configureFileChooser(fileChooser);
            File file = fileChooser.showOpenDialog(window);
            if (file == null) {
                informationPanel("Please upload a file.");
            } else if (!file.exists() || !file.canRead()) {
                displayAlert("There was an error opening the file");
            } else if (file.length() < 1) {
                displayAlert("File is empty");
            } else {
                articleNames = new HashSet<>();
                try {
                    Scanner scanner = new Scanner(file);
                    while (scanner.hasNextLine()) {
                        String line = scanner.nextLine();
                        articleNames.add(line);
                    }
                    updateOutputMultiple("File has been submitted.");
                    downloadButtonMultiple.setDisable(false);
                    if (articleNames.size() < 8) {
                        simultaneousDownloadsGUI = new SimultaneousDownloadsGUI();
                        simultaneousDownloadsGUI.setNumOfSimultaneousDownloads(articleNames.size());
                        crawler.setGUI(simultaneousDownloadsGUI);
                        this.numOfConnectionsNeeded = articleNames.size();
                    } else {
                        simultaneousDownloadsGUI = new SimultaneousDownloadsGUI();
                        simultaneousDownloadsGUI.setNumOfSimultaneousDownloads(8);
                        crawler.setGUI(simultaneousDownloadsGUI);
                        this.numOfConnectionsNeeded = 8;
                    }
                    simultaneousDownloadsGUI.addGUI(scrollPanel);
                    singleThreadExecutor.submit(new DoWork("waitFor4Connections", String.valueOf(numOfConnectionsNeeded)));

                } catch (FileNotFoundException e) {
                    displayAlert(e.getMessage());
                }
            }
        });
    }

    /**
     * Configures the types of files that are allowed to be upload (.txt or .csv)
     *
     * @param fileChooser the current fileChooser
     */
    private void configureFileChooser(FileChooser fileChooser) {
        fileChooser.setTitle("Please select the file");
        FileChooser.ExtensionFilter extFilter = new FileChooser.ExtensionFilter("TXT files (*.txt)", "*.txt");
        FileChooser.ExtensionFilter extFilter2 = new FileChooser.ExtensionFilter("CSV file (*.csv)", "*.csv");

        fileChooser.getExtensionFilters().add(extFilter2);
        fileChooser.getExtensionFilters().add(extFilter);
    }

    /**
     * Called when the user clicks the Download button on the multiple articles section.
     */
    @FXML
    void multipleDownloadOnClick() {
        progressBar.progressProperty().setValue(0);
        String text = numberOfPDFsMultiple.getText();
        //Make sure that only numbers are accepted
        Pattern numbersOnly = Pattern.compile("^[0-9]+$");
        Matcher matcher = numbersOnly.matcher(text);

        if (matcher.find()) {
            simultaneousDownloadsGUI.resetCounter();
            numOfPDFToDownload = matcher.group();
            //To download files simultaneously using threads. Based on calculations and experimentation, 4 seems to be the ideal number.
            if (!hasSearchBeforeMultiple) {
                this.executorServiceMultiple = Executors.newFixedThreadPool(8, new MyThreadFactory());
                hasSearchBeforeMultiple = true;
            }
            //Restart pdf count
            updateNumberOfPDFsMultiple(0, true);
            updateOutputMultiple("Processing...");
            atomicCounter = new AtomicCounter();
            atomicCounter.reset();
            atomicCounter.setMaxNumber(articleNames.size());
            updateProgressBarMultiple(0.0);
            List<Future> futures = new ArrayList<>();
            for (String article : articleNames) {
                DoWork task = new DoWork("multipleSearch", article);
                futures.add(executorServiceMultiple.submit(task));
            }
        } else {
            displayAlert("Please only write numbers here.");
        }
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
     * Updates the label with the number of PDFs downloaded in the single article mode
     *
     * @param text String with the number of PDFs
     */
    private void updateNumberOfPDFs(String text) {
        Platform.runLater(() -> pdfsDownloadedLabel.setText(text));
    }

    /**
     * Updates the label with the number of PDFs downloaded in the multiple article mode
     *
     * @param newVal int with the number of PDFs
     */
    private void updateNumberOfPDFsMultiple(int newVal, boolean isNewSearch) {
        Platform.runLater(() -> {
            if (isNewSearch) {
                pdfsDownloadedMultiple.setText("");
            } else {
                if (pdfsDownloadedMultiple.getText() == null || pdfsDownloadedMultiple.getText().isEmpty()) {
                    pdfsDownloadedMultiple.setText(String.valueOf(newVal));
                } else {
                    String prev = pdfsDownloadedMultiple.getText();
                    //We update the value by increase the previous count
                    int total = Integer.parseInt(prev) + newVal;
                    pdfsDownloadedMultiple.setText(String.valueOf(total));
                }
            }

        });
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
     * Updates the progress bar for the Single Article mode
     *
     * @param num Double with the current progress out of 1
     */
    private void updateProgressBar(Double num) {
        Platform.runLater(() -> progressBar.progressProperty().setValue(num));
    }

    /**
     * Updates the progress bar for the Multiple Article mode
     *
     * @param num Double with the current progress out of 1
     */
    private void updateProgressBarMultiple(Double num) {
        Platform.runLater(() -> progressBarMultiple.progressProperty().setValue(num));
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
        Platform.runLater(() -> outputMultiple.setText(message));
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

    /**
     * Handles the logic behind the Search Result Window
     *
     * @param title            title of the query with multiple matches
     * @param isMultipleSearch boolean if it is called during a multiple search result window.
     */
    private void displaySearchResults(String title, boolean isMultipleSearch) {
        Long currentThreadId = Thread.currentThread().getId();
        SearchResultWindow searchResultWindow = guiLabels.getMapThreadToSearchResultW().get(currentThreadId);
        searchResultWindow.setQueryStr(title);

        //Selection button for the search result window
        searchResultWindow.select.setOnAction(e -> {
            String selection;
            selection = (String) searchResultWindow.searchResultListView.getSelectionModel().getSelectedItem();
            if (selection == null || selection.isEmpty()) {
                displayAlert("Please select one search result.");
            } else {
                //Retrieves the number of citation and url for the selected search result
                String[] array = crawler.getSearchResultToLink().get(selection);
                if (!isMultipleSearch) {
                    //If is not multiple search, store url globally
                    citingPapersURL = array[0];
                    updateSearchLabel(array[1]);
                    downloadButton.setDisable(false);
                }
                //Update search label to display result
                searchResultWindow.store(array);
                searchResultWindow.close();
                guiLabels.associateThreadToSearchResultW(currentThreadId, searchResultWindow);
                mapThreadToTitle.put(currentThreadId, selection);
            }
        });

        //Do not download button for the search result window
        searchResultWindow.doNotDownload.setOnAction(e -> {
            //Retrieve the search result window associated with thread
            searchResultWindow.close();
            //Store the result and then add it back to map
            searchResultWindow.store(new String[]{null, "File not downloaded"});
            guiLabels.associateThreadToSearchResultW(currentThreadId, searchResultWindow);

            if (!isMultipleSearch) {
                updateOutput("File not downloaded");
            }
            Logger logger = Logger.getInstance();
            String currTitle = mapThreadToTitle.get(Thread.currentThread().getId());
            if (currTitle == null) {
                currTitle = title;
            }
            try {
                logger.setReportWriter(true, "Report");
                logger.writeReport("\n-Paper not downloaded: " + currTitle + "\n");
            } catch (IOException e2) {
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
     * Call upon start.
     *
     * @param location  Location
     * @param resources ResourceBundle
     */
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        //To perform tasks on Single Article mode (search, download), or actions that should block the rest of the program.
        this.singleThreadExecutor = Executors.newSingleThreadExecutor(new MyThreadFactory());
        File dir = new File("DownloadedPDFs");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdir();
        Logger logger = Logger.getInstance();

        //Create a report, or write to an existing one.
        try {
            logger.setReportWriter(true, "Report");
            DateTimeFormatter formatter = DateTimeFormat.forPattern("dd/MM/yyyy HH:mm:ss");
            //Write current date
            logger.writeReport("\n\n------------------------------------------------\n" + new DateTime().toString(formatter));
        } catch (IOException e) {
            displayAlert(e.getMessage());
        }
        increaseSpeedButton.setSelectedColor(Color.web("#7d9a4f"));
        increaseSpeedButton1.setSelectedColor(Color.web("#7d9a4f"));

        //Initialize GUI management object
        guiLabels = new GUILabelManagement();
        //Start loading crawler. Show loading screen until first connection found. Block the rest of the GUI
        DoWork task = new DoWork("initialize", null);
        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }


    /**
     * Increases the download speed by not using a proxy to download the files.
     */
    @FXML
    void increaseSpeedOnClick() {

        if (speedUp) {
            speedUp = false;
            crawler.increaseSpeed(false);
            Platform.runLater(() -> {
                increaseSpeedButton.setSelected(false);
                increaseSpeedButton1.setSelected(false);
            });
        } else {
            Platform.runLater(() -> {
                        Alert alert = new Alert(Alert.AlertType.WARNING, "If you want to speed up the downloaded process," +
                                " the program will download most of the files without using a proxy, so you will lose your anonymity." +
                                "\nDo you want to continue?", ButtonType.YES, ButtonType.NO);
                        alert.setHeaderText(null);
                        alert.showAndWait();
                        if (alert.getResult() == ButtonType.YES) {
                            speedUp = true;
                            crawler.increaseSpeed(true);
                            increaseSpeedButton.setSelected(true);
                            increaseSpeedButton1.setSelected(true);

                        }
                        else {
                            increaseSpeedButton.setSelected(false);
                            increaseSpeedButton1.setSelected(false);

                        }
                    }
            );
        }


    }

    /**
     * Inner Task class. Allows to perform computations in the background while keeping GUI responsive
     */
    class DoWork extends Task<Void> {
        //Type of task that will be performed
        private final String type;
        private String article;

        /**
         * Initializes new task. Takes as a parameter the type of task that will be performed. Task include: download, search, initialize, close, multipleSearch, and waitFor4Connections
         * And the title of the article associated with the thread.
         *
         * @param type    String with the type of task
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
                case "waitFor4Connections":
                    loading = new LoadingWindow();
                    //Create a new loading box to display while application loads
                    Platform.runLater(() -> loading.display());

                    int currQueueSize = crawler.getQueueOfConnections().size();
                    updateOutputMultiple("Connecting to " + Integer.valueOf(article) + " different proxies...");

                    while (currQueueSize < Integer.valueOf(article)) {
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        currQueueSize = crawler.getQueueOfConnections().size();
                    }
                    Platform.runLater(() -> loading.close());
                    updateOutputMultiple("Connected!");

                    break;
                case "multipleSearch":
                    //Add thread to a group
                    int s = simultaneousDownloadsGUI.addThreadToGroup(Thread.currentThread().getId());
                    simultaneousDownloadsGUI.updateStatus("Searching...");
                    simultaneousDownloadsGUI.updateArticleName("Not set");
                    String url = "";
                    simultaneousDownloadsGUI.updateProgressBar(0.0);
                    String[] result = search(article, true);
                    String numOfCitations = result[0];

                    if (numOfCitations.isEmpty() || numOfCitations.equals("Provide feedback")) {
                        //We don't download if this happens and omit the file
                        simultaneousDownloadsGUI.updateStatus("File was not found");
                        simultaneousDownloadsGUI.updateProgressBar(1.0);
                        //Set the progress bar, increment counter, countdown the latch
                        atomicCounter.increment();
                        Double currPercentage = atomicCounter.value() / (double) atomicCounter.getMaxNumber();
                        if (currPercentage == 1) {
                            updateOutputMultiple("All files have been downloaded");
                        }
                        updateProgressBarMultiple(currPercentage);
                        break;

                    } else if (numOfCitations.equals("There was more than 1 result found for your given query")) {
                        //Get search result window

                        SearchResultWindow searchResultWindow = guiLabels.getMapThreadToSearchResultW().get(Thread.currentThread().getId());
                        if (searchResultWindow.getNumberOfCitations().equals("File not downloaded")) {
                            simultaneousDownloadsGUI.updateStatus("File was not downloaded");
                            simultaneousDownloadsGUI.updateProgressBar(1.0);
                            atomicCounter.increment();
                            Double currPercentage = atomicCounter.value() / (double) atomicCounter.getMaxNumber();
                            if (currPercentage == 1) {
                                updateOutputMultiple("All files have been downloaded");
                            }
                            updateProgressBarMultiple(currPercentage);
                            break;
                        } else {
                            url = searchResultWindow.getCitingPapersURL();
                            article = mapThreadToTitle.get(Thread.currentThread().getId());
                        }
                    }
                    simultaneousDownloadsGUI.updateStatus("Done searching");
                    if (url.isEmpty()) {
                        //If url was not updated in search result window, then it was obtained through a normal search
                        url = result[1];
                    }
                    simultaneousDownloadsGUI.updateStatus("Starting to download articles...");
                    simultaneousDownloadsGUI.updateProgressBar(0.3);
                    download(article, url, true);
                    simultaneousDownloadsGUI.updateProgressBar(1.0);
                    simultaneousDownloadsGUI.updateStatus("Done");
                    atomicCounter.increment();
                    Double currPercentage = atomicCounter.value() / (double) atomicCounter.getMaxNumber();
                    if (currPercentage == 1) {
                        updateOutputMultiple("All files have been downloaded");
                    }
                    updateProgressBarMultiple(currPercentage);
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
                    progressBar.progressProperty().setValue(0);
                    download(mapThreadToTitle.get(Thread.currentThread().getId()), citingPapersURL, false);
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
                    guiLabels.getOutputMultiple().addListener((observable, oldValue, newValue) -> updateOutputMultiple(newValue));
                    guiLabels.getLoadBarMultiple().addListener((observable, oldValue, newValue) -> updateProgressBarMultiple(newValue.doubleValue()));
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
