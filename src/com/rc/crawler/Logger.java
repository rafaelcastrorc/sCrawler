package com.rc.crawler;


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
    private static BufferedWriter listOfWorkingProxiesWriter;
    private static BufferedWriter reportWriter;



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
     * Gets the file that contains the list of working proxies.
     *
     * @return File
     */
    File getListOfWorkingProxies() {
        return new File("./AppData/ListOfWorkingProxies.txt");
    }


    /**
     * Sets a File to be used for the list of working proxies (proxies that work, but have been used for more than 100 requests).
     */
    public void setListOfWorkingProxies(boolean append) throws IOException {
        if (append) {
            listOfProxiesWriter = new BufferedWriter(new FileWriter("./AppData/ListOfWorkingProxies.txt", true));
        }
        try {
            listOfWorkingProxiesWriter = new BufferedWriter(new FileWriter("./AppData/ListOfWorkingProxies.txt"));
        } catch (IOException e) {
            throw new IOException("Unable to create output file");
        }
    }

    /**
     * Writes to the list of working proxies
     *
     * @param s String to write
     * @throws IOException Error writing to file
     */
    public void writeToListOfWorkingProxies(String s) throws IOException {
        try {
            listOfWorkingProxiesWriter.write(s);
            listOfWorkingProxiesWriter.flush();
        } catch (IOException e) {
            throw new IOException("Cannot write to file");

        }
    }




    /**
     * Gets the file that contains the main report file
     * @return File
     */
    File getMainReportWriterFile() {
        return new File("./DownloadedPDFs/Report.txt");
    }



    public void setReportWriter(boolean append, String location) throws IOException {
        if (append) {
            reportWriter = new BufferedWriter(new FileWriter("./DownloadedPDFs/"+location+".txt", true));
        }
        try {
            reportWriter = new BufferedWriter(new FileWriter("./DownloadedPDFs/"+location+".txt"));
        } catch (IOException e) {
            throw new IOException("Unable to create report file");
        }
    }

    /**
     * Writes the report
     *
     * @param s String to write
     * @throws IOException Error writing to file
     */
    public void writeReport(String s) throws IOException {
        try {
            reportWriter.write(s);
            reportWriter.flush();
        } catch (IOException e) {
            throw new IOException("Cannot write to file");

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
        if (listOfWorkingProxiesWriter != null) {
            listOfWorkingProxiesWriter.flush();
            listOfWorkingProxiesWriter.close();
        }
        if (reportWriter != null) {
            reportWriter.flush();
            reportWriter.close();
        }

    }

}
