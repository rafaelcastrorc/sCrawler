package com.rc.crawler;

import javafx.application.Platform;
import javafx.beans.property.StringProperty;
import javafx.concurrent.Task;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.*;

/**
 * Created by rafaelcastro on 7/6/17.
 * Prepares the file that has to be downloaded in multiple search mode
 */
class SetupFile extends Task<Void> implements Callable<Void> {
    private final String typeOfSearch;
    private final File submittedFile;
    private Controller controller;
    private HashSet<String> articleNames;
    private String numberOfPDFsToDownload;


    SetupFile(String typeOfSearch, File submittedFile, Controller controller, String numberOfPDFsToDownload) {
        this.typeOfSearch = typeOfSearch;
        this.submittedFile = submittedFile;
        this.controller = controller;
        this.numberOfPDFsToDownload = numberOfPDFsToDownload;
    }


    @Override
    public Void call() throws Exception {
        HashSet<String> alreadyDownloadedPapers = new HashSet<>();
        this.articleNames = new HashSet<>();
        HashSet<String> articleNamesTemp = new HashSet<>();

        //Add to list of finished downloads all the articles that have been downloaded in the past by the
        //program, in the current search mode
        File listOfFinishedDownloads = new File("./AppData/CompletedDownloads.txt");
        Scanner finishedDownloads;
        try {
            finishedDownloads = new Scanner(listOfFinishedDownloads);
            while (finishedDownloads.hasNextLine()) {
                String line = finishedDownloads.nextLine();
                if (line.contains(typeOfSearch)) {
                    alreadyDownloadedPapers.add(line);
                }
            }
            finishedDownloads.close();

        } catch (FileNotFoundException ignored) {
        }

        Scanner scanner;
        try {
            scanner = new Scanner(submittedFile);

            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                String holder = "[" + typeOfSearch + "]" + line ;
                if (!alreadyDownloadedPapers.contains(holder)) {
                    //Add only the files that have not been downloaded
                    articleNamesTemp.add(line);
                }
                articleNames.add(line);
            }
            scanner.close();
        } catch (FileNotFoundException ignored) {
        }
        if (articleNamesTemp.size() != articleNames.size()) {
            //If there are files that have already been downloaded, ask the user if they want to re-download them

            Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "The program found that you have already " +
                    "some of these files before. Do you want to download them again?", ButtonType.YES, ButtonType.NO);
            alert.setHeaderText(null);
            alert.showAndWait();
            if (alert.getResult() == ButtonType.NO) {
                articleNames = articleNamesTemp;
            }

        }
        controller.startMultipleDownloads(articleNames, numberOfPDFsToDownload, typeOfSearch);
        return null;

    }

}
