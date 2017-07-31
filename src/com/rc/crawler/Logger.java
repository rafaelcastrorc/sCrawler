package com.rc.crawler;


import org.joda.time.DateTime;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Logger class to write to the different output files. Uses singleton pattern.
 */
class Logger {
    private static BufferedWriter listOfProxiesWriter;
    private static Logger instance;
    private static BufferedWriter reportWriter;
    private static BufferedWriter listOfFinishedPapers;
    private static BufferedWriter filesNotDownloaded;
    private BufferedWriter listOfFilesToDownload;


    /**
     * Gets an instance of the Logger
     *
     * @return Logger
     */
    static Logger getInstance() {
        if (instance == null) {
            instance = new Logger();
        }
        return instance;
    }


    private Logger() {
    }

    /**
     * Gets the file that contains a list of proxies that have been gathered.
     *
     * @return File
     */
    File getListOfProxies() {
        return new File("./AppData/ListOfProxies.txt");
    }

    /**
     * Sets the file to write to the list of proxies.
     *
     * @param append if the file already exists and is valid, then append is true.
     * @throws IOException unable to write to file
     */
    void setListOfProxies(boolean append) throws IOException {
        if (append) {
            listOfProxiesWriter = new BufferedWriter(new FileWriter("./AppData/ListOfProxies.txt", true));
        } else {
            File dir = new File("AppData");
            //noinspection ResultOfMethodCallIgnored
            dir.mkdir();
            try {
                File file = new File("./AppData/ListOfProxies.txt");
                listOfProxiesWriter = new BufferedWriter(new FileWriter(file));
            } catch (IOException e) {
                throw new IOException("Unable to create list of proxies");
            }
        }
    }

    /**
     * Writes to the list of working proxies.
     *
     * @param s String to write
     * @throws IOException Unable to write to file
     */
    void writeToListOfProxies(String s) throws IOException {
        try {
            listOfProxiesWriter.write(s);
            listOfProxiesWriter.flush();
        } catch (IOException e) {
            throw new IOException("Cannot write to file");
        }
    }


    /**
     * Sets the file to be used to write the report.
     *
     * @param append   true if we are writing over an existing report, false otherwise.
     * @param location folder location where the report should be stored.
     * @throws IOException unable to write to file.
     */
    void setReportWriter(boolean append, String location) throws IOException {
        if (append) {
            reportWriter = new BufferedWriter(new FileWriter("./DownloadedPDFs/" + location + ".txt", true));
        } else {
            reportWriter = new BufferedWriter(new FileWriter("./DownloadedPDFs/" + location + ".txt", false));
        }
    }

    /**
     * Writes the report
     *
     * @param s String to write
     * @throws IOException Error writing to file
     */
    void writeReport(String s) throws IOException {
        try {
            reportWriter.write(s);
            reportWriter.flush();
        } catch (IOException e) {
            throw new IOException("Cannot write to file");

        }
    }



    /**
     * Sets the file to write the papers that have been downloaded
     *
     * @param append if the file already exists and is valid, then append is true.
     * @throws IOException unable to write to file
     */
    void setListOfFinishedPapers(boolean append) throws IOException {
        if (append) {
            listOfFinishedPapers = new BufferedWriter(new FileWriter("./AppData/CompletedDownloads.txt", true));
        } else {
            try {
                File file = new File("./AppData/CompletedDownloads.txt");
                listOfFinishedPapers = new BufferedWriter(new FileWriter(file));
            } catch (IOException e) {
                throw new IOException("Unable to create list of completed downloads");
            }
        }
    }

    /**
     * Writes to the list of working proxies.
     *
     * @param s String to write
     * @throws IOException Unable to write to file
     */
    void writeToListOfFinishedPapers(String s) throws IOException {
        try {
            listOfFinishedPapers.write(s);
            listOfFinishedPapers.flush();
        } catch (IOException e) {
            throw new IOException("Cannot write to file");
        }
    }



    /**
     * Writes to the list of papers that could not be downloaded
     *
     * @param s String to write
     * @throws IOException Unable to write to file
     */
    void writeToFilesNotDownloaded(String s) throws IOException {
        try {
            filesNotDownloaded.write(s);
            filesNotDownloaded.flush();
        } catch (IOException e) {
            throw new IOException("Cannot write to file");
        }
    }


    /**
     * Sets the file to write the papers that have NOT been downloaded
     *
     * @param append if the file already exists and is valid, then append is true.
     * @throws IOException unable to write to file
     */
    void setListOfNotDownloadedPapers(boolean append) throws IOException {
        if (append) {
            filesNotDownloaded = new BufferedWriter(new FileWriter("./DownloadedPDFs/FilesNotDownloaded.txt", true));
        } else {
            try {
                File file = new File("./DownloadedPDFs/FilesNotDownloaded.txt");
                filesNotDownloaded = new BufferedWriter(new FileWriter(file));
            } catch (IOException e) {
                throw new IOException("Unable to create list of files not downloaded");
            }
        }
    }


    /**
     * Writes to the new list of files to download
     *
     * @param s String to write
     * @throws IOException Unable to write to file
     */
    void writeToFilesToDownload(String s) throws IOException {
        try {
            listOfFilesToDownload.write(s);
            listOfFilesToDownload.flush();
        } catch (IOException e) {
            throw new IOException("Cannot write to file");
        }
    }


    /**
     * Creates a new list of files to download
     * @throws IOException If unable to create file throws exception
     */
    void setListOfFilesToDownload() throws IOException {
        DateTime time = new DateTime();
        String title = "FilesToDownload_"+ time.toDate()+".txt";
        try {
            File file = new File("./DownloadedPDFs/"+title);
            listOfFilesToDownload = new BufferedWriter(new FileWriter(file));
        } catch (IOException e) {
            throw new IOException("Unable to create list of files to download");
        }
    }

    /**
     * Adds a file that was not found to the report, the list of files not found, and to the list of completed downloads
     * @param file File that was not found
     * @param originalArticle Query inputted by the user
     * @param typeOfSearch Type of search used
     */
    void writeToLogFileNotFound(File file, String originalArticle, String typeOfSearch, boolean isMultipleSearch) {

        try {
            //Add to the report
            setReportWriter(true, "Report");
            writeReport("\n-Could not find paper ("+typeOfSearch +"): " + originalArticle + "\n");
            //Add to list of files not downloaded
            if (file.exists() && file.canRead()) {
                setListOfNotDownloadedPapers(true);
            } else {
                setListOfNotDownloadedPapers(false);
            }
            writeToFilesNotDownloaded("\n" + originalArticle + " - Error: File was not found ("+typeOfSearch+")");

            //Add to list of finished downloads
            file = new File("./AppData/CompletedDownloads.txt");
            if (file.exists() && file.canRead()) {
                setListOfFinishedPapers(true);
            } else {
                setListOfFinishedPapers(false);
            }
            if (isMultipleSearch) {
                writeToListOfFinishedPapers("\n" + "[" + typeOfSearch + "]" + originalArticle);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Adds a file that was not downloaded by the user (in the Search Result Window) to the list of files not found and
     * to the list of completed downloads
     * @param file File that was not found
     * @param originalArticle Query inputted by the user
     * @param typeOfSearch Type of search used
     */
    void fileNotDownloadedSearchResultWindow(File file, String originalArticle, String typeOfSearch, boolean
            isMultipleSearch) {
        try {
            if (file.exists() && file.canRead()) {

                setListOfNotDownloadedPapers(true);
            } else {
                setListOfNotDownloadedPapers(false);
            }
            writeToFilesNotDownloaded("\n" + originalArticle + " - Error: File was not " +
                    "found (Search Window) ("+typeOfSearch+")");

            file = new File("./AppData/CompletedDownloads.txt");
            if (file.exists() && file.canRead()) {
                setListOfFinishedPapers(true);
            } else {
                setListOfFinishedPapers(false);
            }
            if (isMultipleSearch) {
                writeToListOfFinishedPapers("\n" + "[" + typeOfSearch + "]" + originalArticle);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    /**
     * Closes the logger
     *
     * @throws IOException - unable to close
     */
    void closeLoggers() throws IOException {
        listOfProxiesWriter.flush();
        listOfProxiesWriter.close();

        if (reportWriter != null) {
            reportWriter.flush();
            reportWriter.close();
        }

    }


}
