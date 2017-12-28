package com.rc.crawler;

import javafx.application.Platform;
import javafx.concurrent.Task;
import org.openqa.selenium.phantomjs.PhantomJSDriverService;

import java.io.File;
import java.util.HashSet;
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
    private DatabaseDriver db;


    /**
     * Initializes new task. Takes as a parameter the type of task that will be performed. Task include:
     * download, search, initialize, close, multipleSearch, and waitFor4Connections
     * And the title of the article associated with the thread.
     *  @param type         String with the type of task
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
        java.util.logging.Logger.getLogger(PhantomJSDriverService.class.getName()).setLevel(Level.SEVERE);
        //Prepare the GUI
        //For single article mode
        guiLabels.getAlertPopUp().addListener((observable, oldValue, newValue) -> controller.displayAlert
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
        initializeStats();
        //Load the crawler
        controller.getCrawler().loadCrawler(controller.getSearchEngine());
        article = String.valueOf(1);
        waitForConnections(1);
        connectionEstablished();
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
     * @param i The number of connections
     */
    private void waitForConnections(int i) {
        //Create a new loading box to show while application loads
        loading = new LoadingWindow();
        Platform.runLater(() -> loading.display());

        int currQueueSize = controller.getCrawler().getQueueOfConnections().size();
        controller.updateOutputMultiple("Connecting to " + Integer.valueOf(article) + " different proxies...");

        while (currQueueSize < Integer.valueOf(article)) {
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
     * Handles the logic behind the multiple search mode
     */
    private void multipleSearch() {
        //Add thread to a group
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
        Double rate = (controller.getNumOfSuccessful().value() / (double)  controller.getAtomicCounter().value()) * 100;
        Double rate2 = (controller.getNumOfSuccessfulGral().value() / (double)  controller.getAtomicCounter().value()) *
                100;

        controller.updateOutputMultiple("Downloads completed - " +  controller.getAtomicCounter().value() + " " +
                " " +
                "Download " +
                "rate: ~" + String.format("%.2f", rate) + "% | " + String.format("%.2f", rate2) + "%");

        Double currPercentage =  controller.getAtomicCounter().value() / ((double)  controller.getAtomicCounter().getMaxNumber());
        //Add to db
        db.addDownloadRateToDB(rate2);
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
        Double rate = (controller.getNumOfSuccessful().value() / (double)  controller.getAtomicCounter().value()) * 100;
        Double rate2 = (controller.getNumOfSuccessfulGral().value() / (double)  controller.getAtomicCounter().value()) *
                100;

        controller.updateOutputMultiple("Downloads completed - " +  controller.getAtomicCounter().value() + " " +
                " " +
                "Download " +
                "rate: ~" + String.format("%.2f", rate) + "% | " + String.format("%.2f", rate2) + "%");

        Double currPercentage =  controller.getAtomicCounter().value() / ((double)  controller.getAtomicCounter().getMaxNumber());
        db.addDownloadRateToDB(rate2);

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
        Double rate = (controller.getNumOfSuccessful().value() / (double)  controller.getAtomicCounter().value()) * 100;
        Double rate2 = (controller.getNumOfSuccessfulGral().value() / (double)  controller.getAtomicCounter().value()) *
                100;

        controller.updateOutputMultiple("Downloads completed - " +  controller.getAtomicCounter().value() + " " +
                " " +
                "Download " +
                "rate: ~" + String.format("%.2f", rate) + "% | " + String.format("%.2f", rate2) + "%");

        Double currPercentage =  controller.getAtomicCounter().value() / ((double)  controller.getAtomicCounter().getMaxNumber());
        db.addDownloadRateToDB(rate2);

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
                    GUILabelManagement guiLabels, StatsGUI stats, DatabaseDriver db) {
        this.controller = controller;
        this.guiLabels = guiLabels;
        this.simultaneousDownloadsGUI = simultaneousDownloadsGUI;
        this.stats = stats;
        this.db = db;
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

}
