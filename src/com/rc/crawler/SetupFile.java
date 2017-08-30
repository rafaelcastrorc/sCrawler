package com.rc.crawler;

import javafx.concurrent.Task;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Scanner;
import java.util.concurrent.Callable;

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

        if (submittedFile.getName().contains("FilesNotDownloaded")) {
            setupFilesNotDownloadedFile();
        } else {
            Scanner scanner;
            try {
                scanner = new Scanner(submittedFile);

                while (scanner.hasNextLine()) {
                    String line = scanner.nextLine();
                    String holder = "[" + typeOfSearch + "]" + line;
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
                        "some of these files before. Do you want to download them again?", ButtonType.YES, ButtonType
                        .NO);

                alert.setHeaderText(null);
                alert.showAndWait();
                if (alert.getResult() == ButtonType.NO) {
                    articleNames = articleNamesTemp;
                }

            }
            controller.startMultipleDownloads(articleNames, numberOfPDFsToDownload, typeOfSearch);

        }
        return null;

    }


    /**
     * Re-download the files that could not be downloaded because of some error at runtime
     */
    private void setupFilesNotDownloadedFile() {
        Scanner scanner = null;
        ArrayList<String> holder = new ArrayList<>();
        try {
            scanner = new Scanner(new FileInputStream(submittedFile));
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                if (line.contains(typeOfSearch)) {
                    //Try to re download only the articles where the program could not find the article, or when there
                    // was an error downloading an article
                    if (line.contains("Error: File was not found") || line.contains("Error: There was an error " +
                            "downloading") || line.contains("Error: Could not find any valid")) {
                        line = line.replaceAll("- Error: .*", "");
                        articleNames.add(line);
                    } else {
                        holder.add(line);
                    }
                } else {
                    holder.add(line);
                }
            }
            scanner.close();

        } catch (FileNotFoundException ignored) {
        }
        if (articleNames.size() > 1) {
            //If there are files that can be re-downloaded, ask the user what he wants to do

            Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "You can try to download " +
                    "some of the articles again. Do you want to do so?", ButtonType.YES, ButtonType.NO);

            alert.setHeaderText(null);
            alert.showAndWait();
            if (alert.getResult() == ButtonType.YES) {
                //Write Files not found only with the files that cannot be re-downloaded
                Logger logger = Logger.getInstance();
                try {
                    logger.setListOfNotDownloadedPapers(false);
                } catch (IOException ignored) {
                }
                for (String s : holder) {
                    try {
                        logger.writeToFilesNotDownloaded("\n" + s);
                    } catch (IOException ignored) {
                    }
                }
                try {
                    //Create a new txt file with all the files to download
                    logger.setListOfFilesToDownload();
                    for (String article : articleNames) {
                        logger.writeToFilesToDownload("\n" + article);
                    }
                    //clear completed downloads
                    logger.setListOfFinishedPapers(false);

                } catch (IOException ignored) {
                }
                controller.startMultipleDownloads(articleNames, numberOfPDFsToDownload, typeOfSearch);
            }
        } else {
            Alert alert = new Alert(Alert.AlertType.INFORMATION, "There are no files that can be downloaded",
                    ButtonType.OK);
            alert.setHeaderText(null);
            alert.showAndWait();
        }
    }

}
