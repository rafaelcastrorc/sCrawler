package com.rc.crawler;

import javafx.application.Platform;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

/**
 * Created by rafaelcastro on 8/20/17.
 * Displays a SearchResultWindow object, when there are multiple search results for a given query.
 */
class SearchResultDisplay {
    private final Controller controller;
    private SearchResultWindow searchResultWindow;
    private GUILabelManagement guiLabels;
    private Crawler crawler;


    SearchResultDisplay(SearchResultWindow searchResultWindow, GUILabelManagement guiLabels, Crawler crawler,
                        Controller controller) {

        this.searchResultWindow = searchResultWindow;
        this.guiLabels = guiLabels;
        this.crawler = crawler;
        this.controller = controller;
    }

    /**
     * Show the Search Result Window to the user
     *
     * @param isMultipleSearch true if it is multiple search mode
     * @param typeOfSearch     searchForCitedBy or searchForTheArticle
     * @param currentThreadId  ID of the current thread
     * @return citing paper URL
     */
    String show(boolean isMultipleSearch, String typeOfSearch, Long currentThreadId, String title) {
        final String[] citingPapersURL = new String[1];

        //Selection button for the search result window
        searchResultWindow.select.setOnAction(e -> {
            String selection;
            selection = (String) searchResultWindow.searchResultListView.getSelectionModel().getSelectedItem();
            if (selection == null || selection.isEmpty()) {
                guiLabels.setAlertPopUp("Please select one search result.");
            } else {
                //Retrieves the number of citation and url for the selected search result
                String[] array = crawler.getSearchResultToLink().get(selection);
                if (!isMultipleSearch) {
                    //If is not multiple search, store url globally
                    citingPapersURL[0] = array[0];
                    guiLabels.setSearchResultLabel(typeOfSearch + "," + array[1]);
                }
                //Update search label to show result
                searchResultWindow.store(array);
                searchResultWindow.close();
                guiLabels.associateThreadToSearchResultW(currentThreadId, searchResultWindow);
                controller.getMapThreadToTitle().put(currentThreadId, selection);
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
                guiLabels.setOutput("File not downloaded");
            }
            Logger logger = Logger.getInstance();
            String currTitle = controller.getMapThreadToTitle().get(Thread.currentThread().getId());
            if (currTitle == null) {
                currTitle = title;
            }
            try {
                logger.setReportWriter(true, "Report");
                logger.writeReport("\n-Paper not downloaded(" + typeOfSearch + "): " + currTitle + "\n");
            } catch (IOException e2) {
                guiLabels.setAlertPopUp(e2.getMessage());
            }
        });
        FutureTask<Void> futureTask = new FutureTask<>(searchResultWindow);
        Platform.runLater(futureTask);

        try {
            futureTask.get();
        } catch (InterruptedException | ExecutionException e) {
            guiLabels.setAlertPopUp("Thread was interrupted " + e.getMessage());
        }

        return citingPapersURL[0];
    }

}

