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
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.openqa.selenium.Cookie;
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
    private SearchEngine.SupportedSearchEngine engine;
    private StatsGUI stats;
    private OutsideServer server;
    private String typeOfSearch;
    private String numberOfPDFsToDownload;

    //All buttons available in the GUI
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
    @FXML
    private Text startTimeGUI;
    @FXML
    private Text numberOfLockedProxies;
    @FXML
    private Text relocked;
    @FXML
    private Text numOfUnlocked;
    @FXML
    private Text lockedByProvider;
    @FXML
    private ImageView image;


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
    String[] search(String title, boolean isMultipleSearch, String typeOfSearch) {
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
            result = crawler.searchForArticle(title, hasSearchedBefore, isMultipleSearch, typeOfSearch,
                    engine);
        } catch (Exception e) {
            //If there was a problem searching, try searching again
            try {
                Thread.sleep(10000);
            } catch (InterruptedException ignored) {
            }
            try {
                result = crawler.searchForArticle(title, hasSearchedBefore, isMultipleSearch, typeOfSearch,
                        engine);
            } catch (Exception ignored) {
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
                        //Update search label to show result
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
                DoWork task = new DoWork("download", articleName, "searchForCitedBy"
                );
                task.setObjects(this, simultaneousDownloadsGUI, guiLabels, stats, server);
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
                task.setObjects(this, simultaneousDownloadsGUI, guiLabels, stats, server);
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
    void download(String currTitle, String URL, boolean isMultipleSearch, String originalArticle,
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
                    isMultipleSearch, pdfDownloader, typeOfSearch, engine);

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
                } else {
                    if (!thereWasAPDF) {
                        logger.writeToFilesNotDownloaded("\n" + paperDownloaded + " - Error: Could not find any PDF " +
                                "version of the article(" + typeOfSearch + ")");


                    } else {
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
            if (!isMultipleSearch) {
                displayAlert("There was an error downloading a file");
            }
            e.printStackTrace(System.out);
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
        task.setObjects(this, simultaneousDownloadsGUI, guiLabels, stats, server);
        singleThreadExecutor.submit((Runnable) task);

    }

    /**
     * Handles the logic to upload a file into the program.
     */
    void openFile() {
        Platform.runLater(() -> {
            FileChooser fileChooser = new FileChooser();
            configureFileChooser(fileChooser);
            File file = fileChooser.showOpenDialog(window);
            if (file == null) {
                displayInfo("Please upload a file.");
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

                    setUpMultipleGUI();
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

        fileChooser.getExtensionFilters().add(extFilter);
        fileChooser.getExtensionFilters().add(extFilter2);

    }

    /**
     * Called when the user clicks the Download button on the multiple articles section.
     */
    @FXML
    void multipleDownloadOnClick(ActionEvent event) {
        Object source = event.getSource();
        String buttonUsed = "";
        if (source instanceof Button) {
            Button clickedBtn = (Button) source;
            buttonUsed = clickedBtn.getId();
        }
        String text;

        if (buttonUsed.equals("downloadButtonMultiple")) {
            this.typeOfSearch = "searchForCitedBy";
            text = numberOfPDFsMultiple.getText();
        } else {
            this.typeOfSearch = "searchForTheArticle";
            text = numberOfPDFsMultipleSFA.getText();
        }
        //Show number of files that have been already downloaded, then ask if he wants to continue
        progressBar.progressProperty().setValue(0);
        //Make sure that only numbers are accepted
        Pattern numbersOnly = Pattern.compile("^[0-9]+$");
        Matcher matcher = numbersOnly.matcher(text);
        if (matcher.find()) {
            this.numberOfPDFsToDownload = matcher.group();
            DoWork task = new DoWork("waitForNConnections", String.valueOf(numOfConnectionsNeeded), null);
            task.setObjects(this, simultaneousDownloadsGUI, guiLabels, stats, server);
            SetupFile setup = new SetupFile(typeOfSearch, submittedFile, numberOfPDFsToDownload, task);
            //Set up the list of files to download appropriately
            FutureTask<Void> futureTask = new FutureTask<>(setup);
            Platform.runLater(futureTask);
        } else {
            displayAlert("Please only write numbers here.");
        }

    }

    void startMultipleDownloads(HashSet<String> articleNames, String numOfPDFToDownload, String typeOfSearch) {
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
        //Reset all the other counters
        atomicCounter.reset();
        numOfSuccessfulGral.reset();
        numOfSuccessful.reset();
        atomicCounter.setMaxNumber(articleNames.size());
        updateOutputMultiple("Number of articles to download: " + articleNames.size());

        CompletionService<Void> taskCompletionService = new ExecutorCompletionService<>(executorServiceMultiple);
        updateProgressBarMultiple(0.0);
        List<Future> futures = new ArrayList<>();
        for (String article : articleNames) {
            DoWork task = new DoWork("multipleSearch", article, typeOfSearch);
            task.setObjects(this, simultaneousDownloadsGUI, guiLabels, stats, server);
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
    synchronized void updateWorkingProxiesLabel(String s) {
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
    void updateConnectionOutput(String text) {
        Platform.runLater(() -> connectionOutput.setText(text + "\n" + connectionOutput.getText()));
    }

    /**
     * Updates the label with the number of PDFs downloaded in the single article mode
     *
     * @param text String with the type of search and the number of PDFs, separated by a comma
     */
    void updateNumberOfPDFs(String text) {
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
    void updateNumberOfPDFsMultiple(String newVal) {
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
    void updateSearchLabel(String s) {
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
     * Displays an alert for the user to unlock a proxy. If unlocked, it is added to te queue of working proxies.
     */
    @FXML
    void proxyAlertOnClick() {
        if (!crawler.isSeleniumActive()) {
            displayAlert("Chromedriver is not active in this crawler, so you won't be able to unlock proxies.");
        } else {
            while (crawler.getQueueOfBlockedProxies().size() != 0) {
                Alert alert = new Alert(Alert.AlertType.WARNING);
                Proxy proxy = crawler.getQueueOfBlockedProxies().poll();

                alert.setTitle("Google has detected a robot");
                alert.setHeaderText("Google has blocked the following proxy: " + proxy.getProxy() + ":" + proxy
                        .getPort() +

                        "\nHelp the crawler by unlocking it. Once you are done press the\n" +
                        "'Proxy is Unlocked' button.\n" +
                        "Note: If the page does not fully load, try reloading it multiple\n" +
                        "times, or go to scholar.google.com and search for something\ndifferent.");
                alert.setContentText(null);
                ButtonType unlock = new ButtonType("Unlock Proxy");
                ButtonType proxyIsUnlocked = new ButtonType("Proxy is Unlocked");

                ButtonType buttonTypeTwo = new ButtonType("Cancel");
                alert.getButtonTypes().setAll(unlock, proxyIsUnlocked, buttonTypeTwo);

                alert.getDialogPane().lookupButton(proxyIsUnlocked).setDisable(true);

                final WebDriver[] driver = {null};
                final Button unlockButton = (Button) alert.getDialogPane().lookupButton(unlock);
                final Button proxyIsUnlockedButton = (Button) alert.getDialogPane().lookupButton(proxyIsUnlocked);
                ProxyChanger proxyChanger = new ProxyChanger(guiLabels, crawler, engine, stats);
                unlockButton.addEventFilter(
                        ActionEvent.ACTION,
                        event -> {
                            //If the text of the button is Unlock Proxy
                            if (!unlockButton.getText().equals("Can't be unlocked")) {
                                //Use chromedriver to connect
                                driver[0] = proxyChanger.useChromeDriver(proxy, null, new HashSet<>());
                                alert.getDialogPane().lookupButton(proxyIsUnlocked).setDisable(false);
                                proxyIsUnlockedButton.setDefaultButton(true);
                                ((Button) alert.getDialogPane().lookupButton(unlock)).setText("Can't be unlocked");
                                //Don't close window
                                event.consume();
                            } else {
                                //If the text of the button is Can't be unlocked'
                                try {
                                    driver[0].close();
                                    driver[0].quit();
                                } catch (Exception ignored) {
                                }
                            }

                        }
                );
                Optional<ButtonType> result = alert.showAndWait();
                if (result.get() != unlock) {
                    //If the user press Proxy is unlocked
                    if (result.get() == proxyIsUnlocked) {
                        //Store the cookie of the browser that solved the captcha
                        Set<Cookie> cookies = driver[0].manage().getCookies();
                        try {
                            driver[0].close();
                            driver[0].quit();
                            //Add it to queue of working proxies
                            driver[0] = proxyChanger.useChromeDriver(proxy, null, cookies);
                            cookies = driver[0].manage().getCookies();
                            driver[0].close();
                            driver[0].quit();
                            crawler.addUnlockedProxy(proxy, cookies, engine, server, true);
                        } catch (Exception ignored) {
                        }

                    } else {
                        //If user press cancel, we add it back to the list of blocked proxies
                        crawler.getQueueOfBlockedProxies().add(proxy);
                        try {
                            driver[0].close();
                            driver[0].quit();
                        } catch (Exception ignored) {
                        }
                        break;
                    }
                }
            }
            if (crawler.getQueueOfBlockedProxies().size() == 0) {
                guiLabels.setIsThereAnAlert(false);
            }
        }

    }

    /**
     * Displays a pop up alert message
     *
     * @param message String with the message to show
     */
    void displayAlert(String message) {
        Platform.runLater(() -> {
            if (server != null) {
                server.addError(message);
            }
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
    void updateProgressBar(Double num) {
        Platform.runLater(() -> progressBar.progressProperty().setValue(num));
    }

    /**
     * Updates the progress bar for the Multiple Article mode
     *
     * @param num Double with the current progress out of 1
     */
    void updateProgressBarMultiple(Double num) {
        Platform.runLater(() -> progressBarMultiple.progressProperty().setValue(num));
    }

    /**
     * Updates the status label of the Single Article mode
     *
     * @param message String with the message to output
     */
    void updateOutput(String message) {
        Platform.runLater(() -> output.setText(message));
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
     * Updates the number of blocked proxies in the Statistics section
     *
     * @param newValue number of blocked proxies
     */
    void updateNumberOfBlocked(int newValue) {
        Platform.runLater(() -> numberOfLockedProxies.setText(String.valueOf
                (newValue)));
    }

    /**
     * Updates the number of relocked proxies in the Statistics section
     *
     * @param newValue number of blocked proxies
     */
    void updateNumberOfRelocked(int newValue) {
        Platform.runLater(() -> relocked.setText(String.valueOf
                (newValue)));
    }

    /**
     * Updates the number of unlocked proxies in the Statistics section
     *
     * @param newValue number of unlocked proxies
     */
    void updateNumberOfUnlocked(int newValue) {
        Platform.runLater(() -> numOfUnlocked.setText(String.valueOf
                (newValue)));
    }

    /**
     * Updates the number of proxies locked by the proxy provider
     */
    void updateNumberOfLockedByProvider(int number) {
        Platform.runLater(() -> lockedByProvider.setText(String.valueOf
                (number)));
    }

    /**
     * Updates the number of blocked proxies in the Statistics section
     */
    void updateStartTime(String newValue) {
        Platform.runLater(() -> startTimeGUI.setText(newValue));
    }

    /**
     * Creates a pop up message that says Loading...
     */
    void displayInfo(String s) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Information");
            alert.setHeaderText(null);
            alert.setContentText(s);
            alert.showAndWait();
        });
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
        SearchResultDisplay searchResultDisplay = new SearchResultDisplay(searchResultWindow, guiLabels, crawler
                , this);
        citingPapersURL = searchResultDisplay.show(isMultipleSearch, typeOfSearch, currentThreadId, title);
    }

    /**
     * Call upon start. Initializes the controller.
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
        dir.mkdir();
        dir = new File("AppData");
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
        //Add image
        Image logo = new Image("file:./imgs/scrawler_logo.png");
        image.setImage(logo);
        //Initialize GUI management object
        guiLabels = new GUILabelManagement();
        //Check if there is a restart file, if so start restart process
        Restart.setGUILabels(guiLabels);
        Restart.configureRestart();
        //Initialize connection to outside server
        OutsideServer server = OutsideServer.getInstance(guiLabels);
        // Remove any records associated with the previous instance id, if there are any
        server.removeCrawlerInstance(logger.getPrevName());
        server.addCrawlerInstance();
        //Select the correct search engine
        selectEngine();
        //Initialize stats
        stats = new StatsGUI();
        WebServer.getInstance(guiLabels).setController(this);
        //Start loading crawler. Show loading screen until first connection found. Block the rest of the GUI
        crawler = new Crawler(guiLabels, stats);
        DoWork task = new DoWork("initialize", null, null);
        task.setObjects(this, simultaneousDownloadsGUI, guiLabels, stats, server);
        Thread thread = new MyThreadFactory().newThread(task);
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Asks the user to select a search engine
     */
    private void selectEngine() {
        if (Restart.isApplicationRestarting()) {
            engine = Restart.getSearchEngine();
            return;
        }
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Select academic search engine");
        alert.setHeaderText("Which academic search engine do you want to use?");
        alert.setContentText(null);
        ButtonType gScholar = new ButtonType("Google Scholar");
        ButtonType msft = new ButtonType("Microsoft Academic");
        alert.getButtonTypes().setAll(gScholar, msft);
        Optional<ButtonType> result = alert.showAndWait();
        if (result.get() == gScholar) {
            engine = SearchEngine.SupportedSearchEngine.GoogleScholar;
        } else {
            engine = SearchEngine.SupportedSearchEngine.MicrosoftAcademic;
        }
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

    void setUpMultipleGUI() {
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
        Platform.runLater(() -> simultaneousDownloadsGUI.addGUI(scrollPanel));
    }

    Map<Long, String> getMapThreadToTitle() {
        return mapThreadToTitle;
    }

    JFXButton getAlertButton() {
        return alertButton;
    }

    JFXButton getAlertButton2() {
        return alertButton2;
    }

    Crawler getCrawler() {
        return crawler;
    }

    ProgressBar getProgressBar() {
        return progressBar;
    }

    String getCitingPapersURL() {
        return citingPapersURL;
    }

    AtomicCounter getAtomicCounter() {
        return atomicCounter;
    }

    AtomicCounter getNumOfSuccessful() {
        return numOfSuccessful;
    }

    AtomicCounter getNumOfSuccessfulGral() {
        return numOfSuccessfulGral;
    }

    SearchEngine.SupportedSearchEngine getSearchEngine() {
        return engine;
    }

    String getTypeOfSearch() {
        return typeOfSearch;
    }

    String getNumberOfPDFsToDownload() {
        return numberOfPDFsToDownload;
    }

    File getSubmittedFile() {
        return submittedFile;
    }

    HashSet<String> getArticleNames() {
        return articleNames;
    }

    void setArticleNames(HashSet<String> articleNames) {
        this.articleNames = articleNames;
    }

    void setTypeOfSearch(String typeOfSearch) {
        this.typeOfSearch = typeOfSearch;
    }

    void setNumberOfPDFsToDownload(String numberOfPDFsToDownload) {
        this.numberOfPDFsToDownload = numberOfPDFsToDownload;
    }

    public void setFile(File file) {
        this.submittedFile = file;
    }
}