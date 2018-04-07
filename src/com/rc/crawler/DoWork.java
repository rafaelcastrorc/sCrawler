package com.rc.crawler;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import org.apache.commons.io.FilenameUtils;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.openqa.selenium.phantomjs.PhantomJSDriverService;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.logging.Level;

/**
 * Performs computations in the background while keeping GUI responsive.
 */
class DoWork extends Task<Void> implements Callable {
    //Type of task that will be performed
    private final String type;
    private String typeOfSearch;
    private String article;
    private String originalArticle;
    private LoadingWindow loading;
    private Controller controller;
    private SimultaneousDownloadsGUI simultaneousDownloadsGUI;
    private StatsGUI stats;
    private GUILabelManagement guiLabels;
    private String numberOfPDFsToDownload;
    private HashSet<String> articleNames;
    private OutsideServer server;
    private Alert dialog;
    private static AtomicCounter papersLeftToProcess;


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
            case "waitForNConnections":
                this.loading = new LoadingWindow();
                waitForConnections(8);
                break;

            case "multipleSearch":
                multipleSearch();
                break;

            case "download":
                singleSearch();
                break;

            case "upload":
                controller.openFile();
                break;

            case "uploadProxiesLoading":
                uploadProxiesLoading();
                break;

            default:
                initialize();
                break;
        }
        return null;
    }


    /**
     * Initializes the crawler and links the different GUI elements
     */
    private void initialize() {
        java.util.logging.Logger.getLogger(PhantomJSDriverService.class.getName()).setLevel(Level.OFF);
        //Prepare the GUI
        prepareGUI();
        initializeStats();
        //Check for updates, but first check if its in debug mode to ignore
        boolean isDebug = java.lang.management.ManagementFactory.getRuntimeMXBean().
                getInputArguments().toString().contains("jdwp");
        if (!isDebug) {
            verifyItsTheLatestLocalVersion();
            checkForUpdates();
        }
        //Load the crawler
        controller.getCrawler().loadCrawler(controller.getSearchEngine());
        article = String.valueOf(1);
        waitForConnections(1);
        connectionEstablished();
        if (Restart.isApplicationRestarting()) {
            restartTheDownloadProcess();
        }
    }


    private void prepareGUI() {
        //For single article mode
        guiLabels.getAlertPopUp().addListener((observable, oldValue, newValue) -> controller.displayAlert
                (newValue));
        guiLabels.getInfoPopUp().addListener((observable, oldValue, newValue) -> controller.displayInfo
                (newValue));
        guiLabels.getOutput().addListener((observable, oldValue, newValue) -> controller.updateOutput
                (newValue));
        guiLabels.getLoadBar().addListener((observable, oldValue, newValue) ->
                controller.updateProgressBar(newValue.doubleValue()));
        guiLabels.getSearchResultLabel().addListener(((observable, oldValue, newValue) ->
                controller.updateSearchLabel(newValue)));
        guiLabels.getConnectionOutput().addListener(((observable, oldValue, newValue) ->
                controller.updateConnectionOutput(newValue)));
        guiLabels.getNumberOfWorkingIPs().addListener(((observable, oldValue, newValue) ->
                controller.updateWorkingProxiesLabel(newValue)));
        guiLabels.getNumberOfPDFs().addListener(((observable, oldValue, newValue) ->
                controller.updateNumberOfPDFs(String.valueOf(newValue))));
        //For multiple article mode
        guiLabels.getOutputMultiple().addListener((observable, oldValue, newValue) ->
                controller.updateOutputMultiple(newValue));
        guiLabels.getLoadBarMultiple().addListener((observable, oldValue, newValue) ->
                controller.updateProgressBarMultiple(newValue.doubleValue()));
        guiLabels.getNumberOfPDFsMultiple().addListener((observable, oldValue, newValue) ->
                controller.updateNumberOfPDFsMultiple(newValue));
        guiLabels.getIsThereAnAlert().addListener((observable, oldValue, newValue) -> {
            controller.getAlertButton().setVisible(newValue);
            controller.getAlertButton2().setVisible(newValue);
        });
    }

    /**
     * Makes sure that the user is using the latest version available in his machine
     */
    private void verifyItsTheLatestLocalVersion() {
        String currInstanceName = new java.io.File(WebServer.class.getProtectionDomain().getCodeSource()
                .getLocation()
                .getPath()).getName();
        if (!currInstanceName.contains(".jar")) {
            currInstanceName = currInstanceName + ".jar";
        }
        if (!Logger.getInstance().getVersion().equals(currInstanceName) && !Logger.getInstance().getVersion().isEmpty
                ()) {
            guiLabels.setAlertPopUp("This is not the latest version of the sCrawler that you have downloaded. Please " +
                    "open the file " + Logger.getInstance().getVersion() + ". This instance will close in 15 seconds.");
            try {
                Thread.sleep(15 * 1000);
                if (Restart.isApplicationRestarting()) {
                    Restart.failedToRestart();
                }
                WebServer.getInstance(guiLabels).closeButtonAction(false);
            } catch (InterruptedException ignored) {
            }
        } else {
            //Delete any other sCrawler version that the user might have downloaded
            File[] dir = new File("./").listFiles();
            if (dir != null) {
                for (File file : dir) {
                    String ext = FilenameUtils.getExtension(file.getAbsolutePath());
                    String fileName = file.getName();
                    if (!fileName.contains(ext)) {
                        fileName = fileName + "." + ext;
                    }
                    if (!fileName.equals(currInstanceName) && ext.equals("jar") && !fileName.contains("phantom")) {
                        File nFile = new File(fileName);
                        nFile.delete();
                    }
                }
            }
        }
    }

    /**
     * Checks if there is an updated version
     */
    private void checkForUpdates() {
        //Get the latest version
        Map.Entry<String, String> latestVersion = OutsideServer.getInstance(guiLabels).getLatestVersion();
        String version = latestVersion.getKey();
        String description = latestVersion.getValue();
        //Get the current version. We know that the current name of the jar file is the latest version since we
        // verified that beforehand
        String currInstanceName = new java.io.File(WebServer.class.getProtectionDomain().getCodeSource()
                .getLocation().getPath()).getName();
        if (!currInstanceName.contains(".jar")) {
            currInstanceName = currInstanceName + ".jar";
        }
        //If the instance name is not the same as the version, then update it
        if (!currInstanceName.equals(version)) {

            guiLabels.setInfoPopUp(("There is a new sCrawler version available. The program will automatically " +
                    "download it and start it. Please wait!\n" +
                    "The changes in " + version + " include:\n"
                    + description));

            //If there is a reload process active, then write again the reload.dta file
            Logger.getInstance().writeRestartFile(Restart.getArgs());
            try {
                Thread.sleep(30 * 1000);
            } catch (InterruptedException ignored) {
            }
            this.loading = new LoadingWindow();
            Platform.runLater(() -> loading.display());
            //Perform a critical update to update automatically
            WebServer.getInstance(guiLabels).update(WebServer.TypeOfUpdate.criticalUpdate.name());
        }
    }


    /**
     * Updates the GUI once the first connection has been established
     */
    private void connectionEstablished() {
        Proxy curr = controller.getCrawler().getQueueOfConnections().peek();
        guiLabels.setConnectionOutput("This is the first proxy that will be used: " +
                curr.getProxy());
        //"Done" loading, but just the first proxy
        guiLabels.setLoadBar(1);
        guiLabels.setLoadBarMultiple();
        guiLabels.setOutput("Connected!");
        guiLabels.setOutputMultiple("Connected!");
        guiLabels.setConnectionOutput("Connected");
        guiLabels.setNumberOfWorkingIPs("add," + curr.getProxy() + " Port: " + curr.getPort());

    }


    /**
     * Wait for n proxy connections to be established before searching
     *
     * @param i The number of connections
     */
    private void waitForConnections(int i) {
        //Create a new loading box to show while application loads
        loading = new LoadingWindow();
        Platform.runLater(() -> loading.display());

        int currQueueSize = controller.getCrawler().getQueueOfConnections().size();
        controller.updateOutputMultiple("Connecting to " + i + " different proxies...");

        while (currQueueSize < i) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            currQueueSize = controller.getCrawler().getQueueOfConnections().size();
        }
        Platform.runLater(() -> loading.close());
        controller.updateOutputMultiple("Connected!");
        if (i != 1) {
            controller.startMultipleDownloads(articleNames, numberOfPDFsToDownload, typeOfSearch);
        }
    }

    /**
     * Displays a loading screen window when uploading the proxies to the database
     */
    private void uploadProxiesLoading() {
        String res = "";
        loading = new LoadingWindow();
        Platform.runLater(() -> loading.display());
        try {
            Thread.sleep(2000);
        } catch (InterruptedException ignored) {
        }
        try {
            if (article.equals("local")) {
                Logger.getInstance().readCookieFileFromLocal(new GUILabelManagement(), new StatsGUI());
            } else {
                Logger.getInstance().downloadCookiesFromGithub(new GUILabelManagement(), new StatsGUI());
            }
        } catch (SQLException | IOException e) {
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("There was a problem");
                alert.setContentText(null);
                if (article.equals("local")) {
                    alert.setHeaderText("There was a problem reading your cookies.dta file");
                } else {
                    alert.setHeaderText("There was a problem downloading the cookie.dta file from the server");

                }
                alert.showAndWait();
                loading.close();
            });
            throw new IllegalArgumentException();
        }

        //If it succeeded, it will get to here, so just display message to user
        Platform.runLater(() -> {
            loading.close();
            try {
                dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL);
                dialog.close();
            } catch (Exception ignored) {
            }
        });


    }


    /**
     * Handles the logic behind the multiple search mode
     */
    private void multipleSearch() {
        //Add thread to a groupk
        simultaneousDownloadsGUI.addThreadToGroup(Thread.currentThread().getId());
        simultaneousDownloadsGUI.updateStatus("Searching...");
        simultaneousDownloadsGUI.updateArticleName("Not set");
        String url = "";
        simultaneousDownloadsGUI.updateProgressBar(0.0);
        stats.setStartTime();
        String[] result = controller.search(article, true, typeOfSearch);
        String numOfCitations = result[0];
        if (numOfCitations.isEmpty() || numOfCitations.equals("Provide feedback")) {
            //We don't download if this happens and omit the file
            multipleSearchModeNotFound();
            return;

        } else if (numOfCitations.equals("There was more than 1 result found for your given query")) {
            //Get search result window
            SearchResultWindow searchResultWindow = guiLabels.getMapThreadToSearchResultW().get(Thread
                    .currentThread().getId());
            //If the user decided to not download the file
            if (searchResultWindow.getNumberOfCitations().equals("File not downloaded")) {
                multipleSearchModeMultipleSR();
                return;
            } else {
                url = searchResultWindow.getCitingPapersURL();
                article = controller.getMapThreadToTitle().get(Thread.currentThread().getId());
            }
        }
        simultaneousDownloadsGUI.updateStatus("Done searching");
        if (url.isEmpty()) {
            //If url was not updated in search result window, then it was obtained through a normal search
            url = result[1];
        }
        simultaneousDownloadsGUI.updateStatus("Starting to download articles...");
        simultaneousDownloadsGUI.updateProgressBar(0.3);
        controller.download(article, url, true, originalArticle, typeOfSearch);
        multipleSearchModeUpdateGUI();
    }


    /**
     * Called when the article was not found, or an error occurred at runtime, during multiple search mode
     */
    private void multipleSearchModeNotFound() {
        simultaneousDownloadsGUI.updateStatus("File was not found");
        simultaneousDownloadsGUI.updateProgressBar(1.0);
        //Set the progress bar, increment counter, countdown the latch
        controller.getAtomicCounter().increment();
        Double rate = (controller.getNumOfSuccessful().value() / (double) controller.getAtomicCounter().value()) * 100;
        Double rate2 = (controller.getNumOfSuccessfulGral().value() / (double) controller.getAtomicCounter().value()) *
                100;

        controller.updateOutputMultiple("Downloads completed - " + controller.getAtomicCounter().value() + " " +
                " " +
                "Download " +
                "rate: ~" + String.format("%.2f", rate) + "% | " + String.format("%.2f", rate2) + "%");

        Double currPercentage = controller.getAtomicCounter().value() / ((double) controller.getAtomicCounter()
                .getMaxNumber());
        //Add to db
        server.addDownloadRateToDB(rate2, rate, numberOfPapersMissingToProcess());
        //Add to the list of files that could not be downloaded
        File file = new File("./DownloadedPDFs/FilesNotDownloaded.txt");
        Logger logger = Logger.getInstance();
        logger.writeToLogFileNotFound(file, originalArticle, typeOfSearch, true);
        //Also add download to list of completed downloads since we have already processed it
        if (currPercentage >= 0.99) {
            controller.updateOutputMultiple("All files have been downloaded");
        }
        controller.updateProgressBarMultiple(currPercentage);
    }

    /**
     * Called when there is more than one search result for a given search, but the user decides to not download the
     * file
     */
    private void multipleSearchModeMultipleSR() {
        simultaneousDownloadsGUI.updateStatus("File was not downloaded");
        simultaneousDownloadsGUI.updateProgressBar(1.0);
        controller.getAtomicCounter().increment();
        Double rate = (controller.getNumOfSuccessful().value() / (double) controller.getAtomicCounter().value()) * 100;
        Double rate2 = (controller.getNumOfSuccessfulGral().value() / (double) controller.getAtomicCounter().value()) *
                100;

        controller.updateOutputMultiple("Downloads completed - " + controller.getAtomicCounter().value() + " " +
                " " +
                "Download " +
                "rate: ~" + String.format("%.2f", rate) + "% | " + String.format("%.2f", rate2) + "%");

        Double currPercentage = controller.getAtomicCounter().value() / ((double) controller.getAtomicCounter()
                .getMaxNumber());
        server.addDownloadRateToDB(rate2, rate, numberOfPapersMissingToProcess());

        if (currPercentage >= 0.999) {
            controller.updateOutputMultiple("All files have been downloaded");
        }
        File file = new File("./DownloadedPDFs/FilesNotDownloaded.txt");
        Logger logger = Logger.getInstance();
        logger.fileNotDownloadedSearchResultWindow(file, originalArticle, typeOfSearch, true);
        controller.updateProgressBarMultiple(currPercentage);
    }

    /**
     * Updates the GUI once everything has being processed for a given article
     */
    private void multipleSearchModeUpdateGUI() {
        simultaneousDownloadsGUI.updateProgressBar(1.0);
        simultaneousDownloadsGUI.updateStatus("Done");
        controller.getAtomicCounter().increment();
        Double rate = (controller.getNumOfSuccessful().value() / (double) controller.getAtomicCounter().value()) * 100;
        Double rate2 = (controller.getNumOfSuccessfulGral().value() / (double) controller.getAtomicCounter().value()) *
                100;

        controller.updateOutputMultiple("Downloads completed - " + controller.getAtomicCounter().value() + " " +
                " " +
                "Download " +
                "rate: ~" + String.format("%.2f", rate) + "% | " + String.format("%.2f", rate2) + "%");

        Double currPercentage = controller.getAtomicCounter().value() / ((double) controller.getAtomicCounter()
                .getMaxNumber());
        server.addDownloadRateToDB(rate2, rate, numberOfPapersMissingToProcess());

        if (currPercentage >= 0.999) {
            controller.updateOutputMultiple("All files have been downloaded");
        }
        controller.updateProgressBarMultiple(currPercentage);
    }


    /**
     * Handles the logic behind single search mode. Searches for an article and downloads it.
     */
    private void singleSearch() {
        controller.getProgressBar().progressProperty().setValue(0);
        stats.setStartTime();
        if (article != null) {
            controller.getMapThreadToTitle().put(Thread.currentThread().getId(), article);
        }
        Logger logger = Logger.getInstance();
        controller.updateOutput("Searching...");

        String[] result = controller.search(article, false, typeOfSearch);
        String numOfCitations = result[0];
        //In case the title changed, then retrieve the new article name
        article = controller.getMapThreadToTitle().get(Thread.currentThread().getId());
        controller.getProgressBar().progressProperty().setValue(1);

        if (numOfCitations.isEmpty() || numOfCitations.equals("Provide feedback")) {
            //We don't download if this happens and omit the file
            controller.updateSearchLabel(typeOfSearch + ",File was not found");
            guiLabels.setOutput("File was not found");

            //Add it to the list of files not downloaded and to the files completed
            File file = new File("./DownloadedPDFs/FilesNotDownloaded.txt");
            logger.writeToLogFileNotFound(file, originalArticle, typeOfSearch, false);
            return;

        } else if (numOfCitations.equals("There was more than 1 result found for your given query")) {
            //Get search result window
            SearchResultWindow searchResultWindow = guiLabels.getMapThreadToSearchResultW().get(Thread
                    .currentThread().getId());
            if (searchResultWindow.getNumberOfCitations().equals("File not downloaded")) {
                guiLabels.setOutput("File was not downloaded");
                controller.updateSearchLabel(typeOfSearch + ",File was not downloaded");
                File file = new File("./DownloadedPDFs/FilesNotDownloaded.txt");
                logger.fileNotDownloadedSearchResultWindow(file, originalArticle, typeOfSearch, false);
                return;
            } else {
                controller.updateSearchLabel(typeOfSearch + ",Search result selected");
            }
        }
        singleSearchModeFinish();
    }

    /**
     * Updates the GUI for single search mode once an article has being processed, and downloads the paper.
     */
    private void singleSearchModeFinish() {
        controller.updateSearchLabel(typeOfSearch + ",Done searching");
        //Downloads articles that cite a given article
        controller.getProgressBar().progressProperty().setValue(0);
        controller.download(controller.getMapThreadToTitle().get(Thread.currentThread().getId()),
                controller.getCitingPapersURL(), false, originalArticle, typeOfSearch);
        controller.getProgressBar().progressProperty().setValue(1);
        controller.updateOutput("All files have been downloaded!");
    }


    void setObjects(Controller controller, SimultaneousDownloadsGUI simultaneousDownloadsGUI,
                    GUILabelManagement guiLabels, StatsGUI stats, OutsideServer server) {
        this.controller = controller;
        this.guiLabels = guiLabels;
        this.simultaneousDownloadsGUI = simultaneousDownloadsGUI;
        this.stats = stats;
        this.server = server;
    }

    /**
     * Initializes the StatisticsGUI
     */
    private void initializeStats() {
        stats.getStartTime().addListener((observable, oldValue, newValue) -> controller
                .updateStartTime(newValue));
        stats.getNumberOfBlockedProxies().addListener((observable, oldValue, newValue) -> controller
                .updateNumberOfBlocked((Integer) newValue));
        stats.getNumberOfRelockedProxies().addListener((observable, oldValue, newValue) -> controller
                .updateNumberOfRelocked((Integer) newValue));
        stats.getNumberOfUnlockedProxies().addListener((observable, oldValue, newValue) -> controller
                .updateNumberOfUnlocked((Integer) newValue));
        stats.getNumberOfLockedByProvider().addListener((observable, oldValue, newValue) -> controller
                .updateNumberOfLockedByProvider((Integer) newValue));
    }

    /**
     * Sets up the different files needed for multiple search mode
     */
    void setMultipleDownloadFiles(HashSet<String> articleNames, String numberOfPDFsToDownload, String
            typeOfSearch) {
        this.articleNames = articleNames;
        this.numberOfPDFsToDownload = numberOfPDFsToDownload;
        this.typeOfSearch = typeOfSearch;
    }

    /**
     * Restarts the download process using the previous settings
     */
    private void restartTheDownloadProcess() {
        //Display GUI showing it was restarted
        DateTimeFormatter formatter = DateTimeFormat.forPattern("dd/MM/yyyy HH:mm:ss");
        guiLabels.setInfoPopUp("The current instance was restarted at "+new DateTime().toString(formatter));
        guiLabels.setLoadBar(0);
        SetupFile setupFiles = new SetupFile(Restart.getTypeOfSearch(), Restart.getDownloadFile(), Restart
                .getNumberOfPDFsToDownload());
        //Set up the list of files to download appropriately
        setupFiles.setUp();

        setMultipleDownloadFiles(Restart.getArticleNames(), Restart.getNumberOfPDFsToDownload(), Restart
                .getTypeOfSearch());

        //Link everything back to the controller
        controller.setArticleNames(Restart.getArticleNames());
        controller.setNumberOfPDFsToDownload(Restart.getNumberOfPDFsToDownload());
        controller.setTypeOfSearch(Restart.getTypeOfSearch());
        controller.setUpMultipleGUI();
        controller.setFile(Restart.getDownloadFile());

        //Start loading process
        this.loading = new LoadingWindow();
        waitForConnections(8);


    }

    int numberOfPapersMissingToProcess() {
        if (papersLeftToProcess == null) {
            papersLeftToProcess = new AtomicCounter();
            papersLeftToProcess.setMaxNumber(controller.getArticleNames().size());
            return papersLeftToProcess.value();
        }
        papersLeftToProcess.decrease();
        return papersLeftToProcess.value();
    }

    void setDialog(Alert dialog) {
        this.dialog = dialog;
    }
}
