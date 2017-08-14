package com.rc.crawler;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXRadioButton;
import com.jfoenix.controls.JFXTextField;
import javafx.application.Platform;

import javafx.concurrent.Task;
import javafx.event.ActionEvent;
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
import org.openqa.selenium.WebDriver;

import java.io.File;
import java.io.FileInputStream;
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
    volatile Crawler crawler;
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
    private boolean speedUp = false;
    private File submittedFile;
    private AtomicCounter numOfSuccessful = new AtomicCounter();
    //Counts only the ones that were downloaded
    private AtomicCounter numOfSuccessfulGral = new AtomicCounter();
    @FXML
    private ScrollPane scrollPanel;
    @FXML
    private Label output;
    @FXML
    private Label outputMultiple;
    @FXML
    private Label pdfsDownloadedLabel;
    @FXML
    private Label pdfsDownloadedLabelSFA;
    @FXML
    private Label pdfsDownloadedMultiple;
    @FXML
    private Label pdfsDownloadedMultipleSFA;
    @FXML
    private JFXTextField numberOfPDFs;
    @FXML
    private JFXTextField numberOfPDFsSFA;
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
    private Label searchLabelSFA;
    @FXML
    private Text workingProxyOutput;
    @FXML
    private Text connectionOutput;
    @FXML
    private JFXButton downloadButtonMultiple;
    @FXML
    private JFXButton downloadButtonMultipleSFA;
    @FXML
    private JFXButton alertButton;
    @FXML
    private JFXButton alertButton2;
    @FXML
    private JFXTextField numberOfPDFsMultiple;
    @FXML
    private JFXTextField numberOfPDFsMultipleSFA;
    @FXML
    private JFXRadioButton increaseSpeedButton1;
    @FXML
    private JFXRadioButton increaseSpeedButton;

    @SuppressWarnings("WeakerAccess")
    public Controller() {
    }

    /**
     * Performs all the operations to search for a paper in Google Scholar.
     * Method is called inside a task.
     *
     * @param title            article that we are looking for.
     * @param isMultipleSearch true if we are searching for multiple articles, using multiple article mode.
     * @param typeOfSearch     "searchForTheArticle" or "searchForCitedBy". Retrieves a different URL depending on if we
     *                         are trying to
     *                         download the paper itself or the papers that cite the paper
     */
    private String[] search(String title, boolean isMultipleSearch, String typeOfSearch) {
        updateSearchLabel(typeOfSearch + ",Loading...");
        SearchResultWindow searchResultWindow = new SearchResultWindow();
        guiLabels.associateThreadToSearchResultW(Thread.currentThread().getId(), searchResultWindow);
        //Array containing the number of citations and the url of the citing articles
        boolean hasSearchedBefore = false;
        if (mapThreadToBool.get(Thread.currentThread().getId()) != null) {
            hasSearchedBefore = true;
        } else {
            mapThreadToBool.put(Thread.currentThread().getId(), true);
        }
        String[] result = null;
        try {
            result = crawler.searchForArticle(title, hasSearchedBefore, isMultipleSearch, typeOfSearch);
        } catch (Exception e) {
            //If there was a problem searching, try searching again
            try {
                Thread.sleep(10000);
            } catch (InterruptedException ignored) {
            }
            try {
                result = crawler.searchForArticle(title, hasSearchedBefore, isMultipleSearch, typeOfSearch);
            }catch (Exception ignored) {
            }
        }
        String numberOfCitations = null;
        try {
            numberOfCitations = result[0];
        } catch (Exception e) {
            e.printStackTrace();
            result = null;
        }

        if (numberOfCitations == null || numberOfCitations.isEmpty() || numberOfCitations.equals
                ("Provide feedback")) {
                result = new String[2];
                result[0] = "";
            updateOutput("Could not find paper...");
        } else if (numberOfCitations.equals("There was more than 1 result found for your given query")) {

            //DELETE////////////////////------------------

            String selection = "";
            try {
                selection = (String) searchResultWindow.searchResultListView.getItems().get(0);
                System.out.println("Selection: " + selection);
            } catch (Exception ignored) {
            }
            if (isMultipleSearch) {
                if (!selection.isEmpty()) {

                    Long currentThreadId = Thread.currentThread().getId();
                    if (crawler.getSearchResultToLink().get(selection) == null) {
                        //If it could not find the article in the crawler, then we did not find
                        // the search result
                        selection = "";

                    } else {
                        String[] array = crawler.getSearchResultToLink().get(selection);
                        //Update search label to display result
                        searchResultWindow.store(array);
                        guiLabels.associateThreadToSearchResultW(currentThreadId, searchResultWindow);
                        mapThreadToTitle.put(currentThreadId, selection);
                    }

                }
                if (selection.isEmpty()) {
                    result[0] = "Provide feedback";
                    updateOutput("Could not find paper...");
                }
            }
            //////////////////////////------------------------------
            else {
                displaySearchResults(title, isMultipleSearch, typeOfSearch);
            }
        } else {
            updateOutput("Paper found!");
            if (!isMultipleSearch) {
                citingPapersURL = result[1];
            }

            downloadButton.setDisable(false);
        }
        updateOutput("Done searching");
        return result;

    }

    /**
     * Called when the download button is pressed. Creates a new task to start downloading PDFs. Used when downloading
     * articles that cite a given article
     */
    @FXML
    void downloadOnClick() {
        String articleName = inputText.getText();
        if (articleName.isEmpty()) {
            displayAlert("Please write a title");
            return;
        }
        String text = numberOfPDFs.getText();
        //Make sure that only numbers are accepted
        Pattern numbersOnly = Pattern.compile("^[0-9]+$");
        Matcher matcher = numbersOnly.matcher(text);

        if (matcher.find()) {
            numOfPDFToDownload = matcher.group();
            try {
                DoWork task = new DoWork("download", articleName, "searchForCitedBy");
                singleThreadExecutor.submit((Runnable) task);
            } catch (Exception e1) {
                displayAlert(e1.getMessage());
            }

        } else {
            displayAlert("Please only write numbers here.");
        }
    }

    @FXML
    void downloadSFAOnClick() {
        String articleName = inputText.getText();
        if (articleName.isEmpty()) {
            displayAlert("Please write a title");
            return;
        }
        String text = numberOfPDFsSFA.getText();
        //Make sure that only numbers are accepted
        Pattern numbersOnly = Pattern.compile("^[0-9]+$");
        Matcher matcher = numbersOnly.matcher(text);

        if (matcher.find()) {
            numOfPDFToDownload = matcher.group();
            try {
                DoWork task = new DoWork("download", articleName, "searchForTheArticle");
                singleThreadExecutor.submit((Runnable) task);
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
     * @param URL              "cited by" URL or "Versions" URL
     * @param isMultipleSearch true if the method is called inside of the multiple articles mode
     * @param originalArticle  Title of the article inputted by the user
     * @param typeOfSearch     The type of search that was performed
     */
    private void download(String currTitle, String URL, boolean isMultipleSearch, String originalArticle,
                          String typeOfSearch) {
        String paperDownloaded;
        if (currTitle.equals(originalArticle)) {
            paperDownloaded = currTitle;
        } else {
            paperDownloaded = originalArticle + " (Selected in SW: " + currTitle + ")";
        }
        try {
            PDFDownloader pdfDownloader = new PDFDownloader();
            //Generate a unique folder name
            String path = pdfDownloader.createUniqueFolder(currTitle);
            if (isMultipleSearch) {
                simultaneousDownloadsGUI.updateArticleName(path);
            }
            int numberOfPDFsDownloaded;

            Object[] result = crawler.getPDFs(Integer.parseInt(numOfPDFToDownload), URL,
                    isMultipleSearch, pdfDownloader, typeOfSearch);

            numberOfPDFsDownloaded = (int) result[0];
            boolean thereWasAPDF = (boolean) result[1];


            Logger logger = Logger.getInstance();

            if (numberOfPDFsDownloaded == 0) {
                //Add to list of files not downloaded
                File file = new File("./DownloadedPDFs/FilesNotDownloaded.txt");
                if (file.exists() && file.canRead()) {
                    logger.setListOfNotDownloadedPapers(true);
                } else {
                    logger.setListOfNotDownloadedPapers(false);
                }
                if (typeOfSearch.equals("searchForCitedBy")) {
                    if (!thereWasAPDF) {
                        logger.writeToFilesNotDownloaded("\n" + paperDownloaded + " - Error: No articles that cite " +
                                "this paper were found in PDF (" + typeOfSearch + ")");
                    } else {
                        logger.writeToFilesNotDownloaded("\n" + paperDownloaded + " - Error: Could not find any valid" +
                                " PDF for this paper (" + typeOfSearch + ")");

                    }
                }
                else {
                    if (!thereWasAPDF) {
                        logger.writeToFilesNotDownloaded("\n" + paperDownloaded + " - Error: Could not find any PDF " +
                                "version of the article(" + typeOfSearch + ")");


                    }
                    else {
                        logger.writeToFilesNotDownloaded("\n" + paperDownloaded + " - Error: Could not find any " +
                                "valid PDF version of the article(" + typeOfSearch + ")");
                        numOfSuccessful.decrease();

                    }
                }
                numOfSuccessfulGral.decrease();

            }

            //Add the information to the Report, and list of completed downloads
            logger.setReportWriter(true, "Report");
            logger.writeReport("\n-Paper downloaded(" + typeOfSearch + "): " + paperDownloaded + "\n   Number of PDFs" +
                    " " +
                    "downloaded: " +

                    numberOfPDFsDownloaded + "/" + numOfPDFToDownload +
                    "\n     Folder path: " + path + "\n");

            logger.setReportWriter(false, path + "/ArticleName");
            logger.writeReport("\nSearched paper: " + paperDownloaded);
            logger.writeReport("\nType of Search: " + typeOfSearch);


            //Add download to list of completed downloads
            File file = new File("./AppData/CompletedDownloads.txt");
            if (file.exists() && file.canRead()) {
                logger.setListOfFinishedPapers(true);
            } else {
                logger.setListOfFinishedPapers(false);
            }
            //We use the original article name for the list of finished papers.
            //We only add it to the list if it is in multiple search mode
            if (isMultipleSearch) {
                logger.writeToListOfFinishedPapers("\n" + "[" + typeOfSearch + "]" + originalArticle);
            }

            numOfSuccessfulGral.increment();
            numOfSuccessful.increment();


        } catch (Exception e) {

            //Add the information to the List of files not downloaded, and the report
            displayAlert("There was an error downloading a file");
            e.printStackTrace();
            Logger logger = Logger.getInstance();
            File file = new File("./DownloadedPDFs/FilesNotDownloaded.txt");
            try {
                if (file.exists() && file.canRead()) {
                    logger.setListOfNotDownloadedPapers(true);
                } else {
                    logger.setListOfNotDownloadedPapers(false);
                }
                if (typeOfSearch.equals("searchForCitedBy"))
                    logger.writeToFilesNotDownloaded("\n" + paperDownloaded + " - Error: There was an error " +
                            "downloading the articles that cite this paper(" + typeOfSearch + ")");
                else {
                    logger.writeToFilesNotDownloaded("\n" + paperDownloaded + " - Error: There was an error " +
                            "downloading this paper(" + typeOfSearch + ")");
                }

                //Add to report
                logger.setReportWriter(true, "Report");
                logger.writeReport("\n-Could not download paper (" + typeOfSearch + "): " + paperDownloaded + "\n");

            } catch (IOException e2) {
                displayAlert(e2.getMessage());
            }
        }
    }


    /**
     * Called when the user clicks the Upload button
     */
    @FXML
    void uploadOnClick(Event e) {
        Node node = (Node) e.getSource();
        window = node.getScene().getWindow();
        DoWork task = new DoWork("upload", null, null);
        singleThreadExecutor.submit((Runnable) task);

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
                    //Check if there are at least 8 files to have enough connections for each file
                    Scanner scanner = new Scanner(new FileInputStream(file));
                    while (scanner.hasNextLine()) {
                        String line = scanner.nextLine();
                        articleNames.add(line);
                        if (articleNames.size() > 8) {
                            break;
                        }
                    }
                    this.submittedFile = file;
                    updateOutputMultiple("File has been submitted.");
                    downloadButtonMultiple.setDisable(false);
                    downloadButtonMultipleSFA.setDisable(false);

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
                    Thread t = new MyThreadFactory().newThread(new DoWork("waitFor4Connections", String.valueOf
                            (numOfConnectionsNeeded), null));
                    singleThreadExecutor.submit((t));

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
    void multipleDownloadOnClick(ActionEvent event) {
        Object source = event.getSource();
        String buttonUsed = "";
        String typeOfSearch = "";
        if (source instanceof Button) { //should always be true in your example
            Button clickedBtn = (Button) source; // that's the button that was clicked
            System.out.println(clickedBtn.getId()); // prints the id of the button
            buttonUsed = clickedBtn.getId();
        }
        String text;

        if (buttonUsed.equals("downloadButtonMultiple")) {
            typeOfSearch = "searchForCitedBy";
           text = numberOfPDFsMultiple.getText();
        } else {
            typeOfSearch = "searchForTheArticle";
            text = numberOfPDFsMultipleSFA.getText();

        }
        //Show number of files that have been already downloaded, then ask if he wants to continue
        progressBar.progressProperty().setValue(0);
        //Make sure that only numbers are accepted
        Pattern numbersOnly = Pattern.compile("^[0-9]+$");
        Matcher matcher = numbersOnly.matcher(text);
        if (matcher.find()) {
            SetupFile setup = new SetupFile(typeOfSearch, submittedFile, this, matcher.group());
            //Set up the list of files to download appropriately
            FutureTask<Void> futureTask = new FutureTask<>(setup);
            Platform.runLater(futureTask);
        } else {
            displayAlert("Please only write numbers here.");
        }

    }

    void startMultipleDownloads(HashSet<String> articleNames, String numOfPDFToDownload, String typeOfSearch){
        this.articleNames = articleNames;
        simultaneousDownloadsGUI.resetCounter();
        this.numOfPDFToDownload = numOfPDFToDownload;
        //To download files simultaneously using threads. Based on calculations and experimentation, 4 seems to
        // be the ideal number.
        if (!hasSearchBeforeMultiple) {
            this.executorServiceMultiple = Executors.newFixedThreadPool(8, new MyThreadFactory());
            hasSearchBeforeMultiple = true;
        }
        //Restart pdf count
        crawler.resetCounter();
        updateOutputMultiple("Processing...");
        atomicCounter = new AtomicCounter();
        atomicCounter.reset();
        atomicCounter.setMaxNumber(articleNames.size());
        updateOutputMultiple("Number of articles to download: "+ articleNames.size());

        CompletionService<Void> taskCompletionService = new ExecutorCompletionService<>(executorServiceMultiple);
        updateProgressBarMultiple(0.0);
        List<Future> futures = new ArrayList<>();
        for (String article : articleNames) {
            Controller.DoWork task = new Controller.DoWork("multipleSearch", article, typeOfSearch);
            futures.add(taskCompletionService.submit(task));
        }
        Task task = new Task() {
            @Override
            protected Object call() throws Exception {
                int n = futures.size();
                for (int i = 0; i < n; ++i) {
                    try {
                        taskCompletionService.take();
                    } catch (InterruptedException ignored) {
                    }
                }
                return null;
            }
        };
        ExecutorService executorService2 = Executors.newSingleThreadExecutor(new MyThreadFactory());
        executorService2.submit(task);
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
     * @param text String with the type of search and the number of PDFs, separated by a comma
     */
    private void updateNumberOfPDFs(String text) {
        if (text != null) {
            String[] array = text.split(",");
            if (array[0].equals("searchForCitedBy")) {
                Platform.runLater(() -> pdfsDownloadedLabel.setText(array[1]));
            } else {
                Platform.runLater(() -> pdfsDownloadedLabelSFA.setText(array[1]));

            }
        }
    }

    /**
     * Updates the label with the number of PDFs downloaded in the multiple article mode
     *
     * @param newVal int with the number of PDFs
     */
    private void updateNumberOfPDFsMultiple(String newVal) {
        String[] array = newVal.split(",");
        if (array[0].equals("searchForCitedBy")) {
            Platform.runLater(() -> pdfsDownloadedMultiple.setText(array[1]));
        } else {
            Platform.runLater(() -> pdfsDownloadedMultipleSFA.setText(array[1]));
        }
    }

    /**
     * Updates the search label with the search result found
     *
     * @param s String with the search result
     */
    private void updateSearchLabel(String s) {
        if (s != null) {
            String[] array = s.split(",");

            if (array[0].equals("searchForCitedBy")) {
                Platform.runLater(() -> searchLabel.setText(array[1]));
            } else {
                Platform.runLater(() -> searchLabelSFA.setText(array[1]));
            }
        }

    }

    /**
     * To display blocked proxies
     *
     */
    @FXML
    void alertOnClick(){
            while (crawler.getQueueOfBlockedProxies().size() != 0) {
                Alert alert = new Alert(Alert.AlertType.WARNING);
                Proxy proxy = crawler.getQueueOfBlockedProxies().poll();

                alert.setTitle("Google has detected a robot");
                alert.setHeaderText("Google has blocked the following proxy: "+proxy.getProxy() +":"+proxy.getPort()+
                                "\nHelp the crawler by unlocking it. Close the window once you are done.");
                alert.setContentText(null);

                ButtonType unlock = new ButtonType("Unlock Proxy");
                ButtonType buttonTypeTwo = new ButtonType("Cancel");
                alert.getButtonTypes().setAll(unlock, buttonTypeTwo);

                Optional<ButtonType> result = alert.showAndWait();
                if (result.get() == unlock) {
                    ProxyChanger proxyChanger = new ProxyChanger(guiLabels, crawler);
                    proxyChanger.useChromeDriver(proxy, true, null);
                    //give user 4 minutes to solve the puzzle before adding it back
                    try {
                        Thread.sleep(4*1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    crawler.addUnlockedProxy(proxy);
                }
                else {
                    crawler.getQueueOfBlockedProxies().add(proxy);
                    break;
                }
            }
            if (crawler.getQueueOfBlockedProxies().size() == 0) {
                guiLabels.setIsThereAnAlert(false);
            }

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
            }
        });
    }


    /**
     * Updates the status label of the Multiple Article Mode
     *
     * @param message String with the message to output
     */
    void updateOutputMultiple(String message) {
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
    private void displaySearchResults(String title, boolean isMultipleSearch, String typeOfSearch) {
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
                    updateSearchLabel(typeOfSearch + "," + array[1]);
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
                logger.writeReport("\n-Paper not downloaded(" + typeOfSearch + "): " + currTitle + "\n");
            } catch (IOException e2) {
                displayAlert(e2.getMessage());
            }
        });
        FutureTask<Void> futureTask = new FutureTask<>(searchResultWindow);
        Platform.runLater(futureTask);

        try {
            futureTask.get();
        } catch (InterruptedException | ExecutionException e) {
            displayAlert("Thread was interrupted " + e.getMessage());
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
        //To perform tasks on Single Article mode (search, download), or actions that should block the rest of the
        // program.
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
            logger.writeReport("\n\n------------------------------------------------\n" + new DateTime().toString
                    (formatter) + "\nDirectory: " + getClass().getProtectionDomain().getCodeSource().getLocation());
        } catch (IOException e) {
            displayAlert(e.getMessage());
        }
        increaseSpeedButton.setSelectedColor(Color.web("#7d9a4f"));
        increaseSpeedButton1.setSelectedColor(Color.web("#7d9a4f"));
        //Todo: add the third and 4th  button

        //Initialize GUI management object
        guiLabels = new GUILabelManagement();
        //Start loading crawler. Show loading screen until first connection found. Block the rest of the GUI
        DoWork task = new DoWork("initialize", null, null);
        Thread thread = new MyThreadFactory().newThread(task);
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
                        Alert alert = new Alert(Alert.AlertType.WARNING, "If you want to speed up the downloaded " +
                                "process, the program will download most of the files without using a proxy, so you " +
                                "will lose your anonymity." +
                                "\nDo you want to continue?", ButtonType.YES, ButtonType.NO);
                        alert.setHeaderText(null);
                        alert.showAndWait();
                        if (alert.getResult() == ButtonType.YES) {
                            speedUp = true;
                            crawler.increaseSpeed(true);
                            increaseSpeedButton.setSelected(true);
                            increaseSpeedButton1.setSelected(true);

                        } else {
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
    class DoWork extends Task<Void> implements Callable {
        //Type of task that will be performed
        private final String type;
        private final String typeOfSearch;
        private String article;
        private String originalArticle;


        /**
         * Initializes new task. Takes as a parameter the type of task that will be performed. Task include:
         * download, search, initialize, close, multipleSearch, and waitFor4Connections
         * And the title of the article associated with the thread.
         *
         * @param type         String with the type of task
         * @param article      Article associated with the thread
         * @param typeOfSearch Search for the article itself, or for the articles that cite the article
         */
        DoWork(String type, String article, String typeOfSearch) {
            this.type = type;
            this.article = article;
            this.originalArticle = article;
            this.typeOfSearch = typeOfSearch;
        }

        /**
         * Called upon start of task. Depending on the task, it processes a different method
         *
         * @return null
         */
        @Override
        public Void call() {

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
                    simultaneousDownloadsGUI.addThreadToGroup(Thread.currentThread().getId());
                    simultaneousDownloadsGUI.updateStatus("Searching...");
                    simultaneousDownloadsGUI.updateArticleName("Not set");
                    String url = "";
                    simultaneousDownloadsGUI.updateProgressBar(0.0);
                    String[] result = search(article, true, typeOfSearch);
                    String numOfCitations = result[0];


                    if (numOfCitations.isEmpty() || numOfCitations.equals("Provide feedback")) {
                        //We don't download if this happens and omit the file
                        simultaneousDownloadsGUI.updateStatus("File was not found");
                        simultaneousDownloadsGUI.updateProgressBar(1.0);
                        //Set the progress bar, increment counter, countdown the latch
                        atomicCounter.increment();
                        Double rate = (numOfSuccessful.value()/(double) atomicCounter.value()) * 100;
                        Double rate2 = (numOfSuccessfulGral.value()/(double) atomicCounter.value()) * 100;

                        updateOutputMultiple("Downloads completed - " + atomicCounter.value()+ "  Download " +
                                "rate: ~"+String.format("%.2f", rate) +"% | "+String.format("%.2f", rate2)+"%");

                        Double currPercentage = atomicCounter.value() / ((double) atomicCounter.getMaxNumber());
                        //Add to the list of files that could not be downloaded
                        File file = new File("./DownloadedPDFs/FilesNotDownloaded.txt");
                        Logger logger = Logger.getInstance();
                        logger.writeToLogFileNotFound(file, originalArticle, typeOfSearch, true);
                        //Also add download to list of completed downloads since we have already processed it
                        if (currPercentage >= 0.99) {
                            updateOutputMultiple("All files have been downloaded");
                        }
                        updateProgressBarMultiple(currPercentage);
                        break;

                    } else if (numOfCitations.equals("There was more than 1 result found for your given query")) {
                        //Get search result window

                        SearchResultWindow searchResultWindow = guiLabels.getMapThreadToSearchResultW().get(Thread
                                .currentThread().getId());
                        if (searchResultWindow.getNumberOfCitations().equals("File not downloaded")) {
                            simultaneousDownloadsGUI.updateStatus("File was not downloaded");
                            simultaneousDownloadsGUI.updateProgressBar(1.0);
                            atomicCounter.increment();
                            Double rate = (numOfSuccessful.value()/(double) atomicCounter.value()) * 100;
                            Double rate2 = (numOfSuccessfulGral.value()/(double) atomicCounter.value()) * 100;

                            updateOutputMultiple("Downloads completed - " + atomicCounter.value()+ "  Download " +
                                    "rate: ~"+String.format("%.2f", rate) +"% | "+String.format("%.2f", rate2)+"%");

                            Double currPercentage = atomicCounter.value() / ((double) atomicCounter.getMaxNumber());
                            if (currPercentage >= 0.999) {
                                updateOutputMultiple("All files have been downloaded");
                            }
                            File file = new File("./DownloadedPDFs/FilesNotDownloaded.txt");
                            Logger logger = Logger.getInstance();
                            logger.fileNotDownloadedSearchResultWindow(file, originalArticle, typeOfSearch, true);
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
                    download(article, url, true, originalArticle, typeOfSearch);
                    simultaneousDownloadsGUI.updateProgressBar(1.0);
                    simultaneousDownloadsGUI.updateStatus("Done");
                    atomicCounter.increment();
                    Double rate = (numOfSuccessful.value()/(double) atomicCounter.value()) * 100;
                    Double rate2 = (numOfSuccessfulGral.value()/(double) atomicCounter.value()) * 100;

                    updateOutputMultiple("Downloads completed - " + atomicCounter.value()+ "  Download " +
                            "rate: ~"+String.format("%.2f", rate) +"% | "+String.format("%.2f", rate2)+"%");

                    Double currPercentage = atomicCounter.value() / ((double) atomicCounter.getMaxNumber());
                    if (currPercentage >= 0.999) {
                        updateOutputMultiple("All files have been downloaded");
                    }
                    updateProgressBarMultiple(currPercentage);
                    break;

                case "download":
                    progressBar.progressProperty().setValue(0);
                    if (article != null) {
                        mapThreadToTitle.put(Thread.currentThread().getId(), article);
                    }
                    Logger logger = Logger.getInstance();
                    updateOutput("Searching...");
                    result = search(article, false, typeOfSearch);
                    numOfCitations = result[0];
                    //In case the title changed, then retrieve the new article name
                    article = mapThreadToTitle.get(Thread.currentThread().getId());
                    progressBar.progressProperty().setValue(1);

                    if (numOfCitations.isEmpty() || numOfCitations.equals("Provide feedback")) {
                        //We don't download if this happens and omit the file
                        updateSearchLabel(typeOfSearch + ",File was not found");
                        guiLabels.setOutput("File was not found");

                        //Add it to the list of files not downloaded and to the files completed
                        File file = new File("./DownloadedPDFs/FilesNotDownloaded.txt");
                        logger.writeToLogFileNotFound(file, originalArticle, typeOfSearch, false);
                        break;

                    } else if (numOfCitations.equals("There was more than 1 result found for your given query")) {
                        //Get search result window
                        SearchResultWindow searchResultWindow = guiLabels.getMapThreadToSearchResultW().get(Thread
                                .currentThread().getId());
                        if (searchResultWindow.getNumberOfCitations().equals("File not downloaded")) {
                            guiLabels.setOutput("File was not downloaded");
                            updateSearchLabel(typeOfSearch + ",File was not downloaded");
                            File file = new File("./DownloadedPDFs/FilesNotDownloaded.txt");
                            logger.fileNotDownloadedSearchResultWindow(file, originalArticle, typeOfSearch, false);
                            break;
                        } else {
                            updateSearchLabel(typeOfSearch + ",Search result selected");
                        }
                    }
                    updateSearchLabel(typeOfSearch + ",Done searching");
                    //Downloads articles that cite a given article
                    progressBar.progressProperty().setValue(0);
                    download(mapThreadToTitle.get(Thread.currentThread().getId()), citingPapersURL, false,
                            originalArticle, typeOfSearch);
                    progressBar.progressProperty().setValue(1);
                    updateOutput("All files have been downloaded!");
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
                    guiLabels.getLoadBar().addListener((observable, oldValue, newValue) -> updateProgressBar(newValue
                            .doubleValue()));
                    guiLabels.getSearchResultLabel().addListener(((observable, oldValue, newValue) ->
                            updateSearchLabel(newValue)));
                    guiLabels.getConnectionOutput().addListener(((observable, oldValue, newValue) ->
                            updateConnectionOutput(newValue)));
                    guiLabels.getNumberOfWorkingIPs().addListener(((observable, oldValue, newValue) ->
                            updateWorkingProxiesLabel(newValue)));
                    guiLabels.getNumberOfPDFs().addListener(((observable, oldValue, newValue) -> updateNumberOfPDFs
                            (String.valueOf(newValue))));
                    //For multiple article mode
                    guiLabels.getOutputMultiple().addListener((observable, oldValue, newValue) ->
                            updateOutputMultiple(newValue));
                    guiLabels.getLoadBarMultiple().addListener((observable, oldValue, newValue) ->
                            updateProgressBarMultiple(newValue.doubleValue()));
                    guiLabels.getNumberOfPDFsMultiple().addListener((observable, oldValue, newValue) ->
                            updateNumberOfPDFsMultiple(newValue));
                    guiLabels.getIsThereAnAlert().addListener((observable, oldValue, newValue) -> {
                            alertButton.setVisible(newValue);
                            alertButton2.setVisible(newValue);
            });
                    //Load the crawler
                    crawler.loadCrawler();
                    break;
            }
            return null;
        }
    }


}
