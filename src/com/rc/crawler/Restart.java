package com.rc.crawler;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;

/**
 * Handles the restart process of the application
 */
class Restart {
    private static boolean isApplicationRestarting = false;
    private static GUILabelManagement guiLabelManagement;
    private static SearchEngine.SupportedSearchEngine searchEngine;
    private static String typeOfSearch;
    private static String numberOfPDFsToDownload;
    private static String downloadFile;
    private static HashSet<String> articleNames;
    private static ArrayList<String> args;

    private Restart() {
    }

    static void configureRestart() {
        //Check if there is a restart file, if so, proceed to restart
        args = Logger.getInstance().getRestartFile();
        if (args.size() > 0) {
            isApplicationRestarting = true;
        } else {
            isApplicationRestarting =false;
            return;
        }
        //Get the search engine
        String searchEngineUsed = args.get(0);
        if (searchEngineUsed.equals(SearchEngine.SupportedSearchEngine.GoogleScholar.name())) {
            searchEngine = SearchEngine.SupportedSearchEngine.GoogleScholar;
        } else if (searchEngineUsed.equals(SearchEngine.SupportedSearchEngine.MicrosoftAcademic.name())) {
            searchEngine = SearchEngine.SupportedSearchEngine.MicrosoftAcademic;
        } else {
            failedToRestart();
        }
        //Get the type of search
        typeOfSearch = args.get(1);
        //Get number of articles to download
        numberOfPDFsToDownload = args.get(2);
        //Get the location of the download file
        downloadFile = args.get(3);
        //Delete restart file
        Logger.getInstance().deleteRestartFile();


    }

    static void setArticleNames(HashSet<String> articleNames) {
        Restart.articleNames = articleNames;

    }
    static void setGUILabels(GUILabelManagement guiLabelManagement) {
        Restart.guiLabelManagement = guiLabelManagement;
    }

    static SearchEngine.SupportedSearchEngine getSearchEngine() {
        return searchEngine;
    }

    /**
     * Checks if the current application is restarting
     */
    static boolean isApplicationRestarting() {
        return isApplicationRestarting;
    }

    /**
     * If application fails at some point to restart, then stop the whole process
     */
    static void failedToRestart() {
        isApplicationRestarting = false;
        Logger.getInstance().deleteRestartFile();
        guiLabelManagement.setAlertPopUp("Application failed to restart!");
    }

    static String getNumberOfPDFsToDownload() {
        return numberOfPDFsToDownload;
    }

    static File getDownloadFile() {
        return new File(downloadFile);
    }

    static String getTypeOfSearch() {
        return typeOfSearch;
    }

    /**
     * Retrieves all the articles that will be downloaded
     */
    static HashSet<String> getArticleNames() {
        return articleNames;
    }


    static ArrayList<String> getArgs() {
        return args;
    }
}
