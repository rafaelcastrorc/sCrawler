package com.rc.crawler;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

/**
 * Created by rafaelcastro on 6/14/17.
 * Manages the way PDFs are stored and downloaded.
 */
class PDFDownloader {
    private GUILabelManagement guiLabels;
    private String path = "";


    /**
     * Gets the folder where the files are going to be stored for a given query
     *
     * @return string with the folder name
     */
    String getPath() {
        return path;
    }

    /**
     * Download a pdf file to a directory
     *
     * @param url        URL to download file from
     * @param pdfCounter Number of PDFs downloaded
     * @param guiLabels  GUILabelManagement object
     * @param ipAndPort  Proxy with the current proxy being used
     * @throws IOException Unable to open link
     */
    void downloadPDF(String url, int pdfCounter, GUILabelManagement guiLabels, Proxy ipAndPort) throws IOException {
        URL urlObj = new URL(url);
        this.guiLabels = guiLabels;
        HttpURLConnection connection = (HttpURLConnection) urlObj.openConnection();

        //Set request property to avoid error 403
        connection.setRequestProperty("User-Agent", "Chrome");
        int status = connection.getResponseCode();
        if (status == 429) {
            //If we have sent too many request to server, change proxy
            String cookie = connection.getHeaderField("Set-Cookie").split(";")[0];
            connection.disconnect();
            guiLabels.setConnectionOutput("We have been blocked by this server. Using proxy to connect...");
            System.out.println(url);
            java.net.Proxy proxy = new java.net.Proxy(java.net.Proxy.Type.HTTP, new InetSocketAddress(ipAndPort.getProxy(), ipAndPort.getPort()));
            //If server blocks us, then we connect via the current proxy we are using
            connection = (HttpURLConnection) urlObj.openConnection(proxy);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");
            connection.setRequestProperty("Cookie", cookie);
            status = connection.getResponseCode();
        }
        connection.connect();


        boolean redirect = false;
        if (status != HttpURLConnection.HTTP_OK) {
            if (status == HttpURLConnection.HTTP_MOVED_TEMP
                    || status == HttpURLConnection.HTTP_MOVED_PERM
                    || status == HttpURLConnection.HTTP_SEE_OTHER)
                redirect = true;
        }

        if (redirect) {
            while (redirect) {
                // get redirect url from "location" header field
                String newUrl = connection.getHeaderField("Location");
                // open the new connection again
                connection = (HttpURLConnection) new URL(newUrl).openConnection();
                connection.setRequestProperty("User-Agent", "Chrome");
                status = connection.getResponseCode();
                if (status == 429) {
                    String cookie = connection.getHeaderField("Set-Cookie").split(";")[0];
                    connection.disconnect();
                    guiLabels.setConnectionOutput("We have been blocked by this server. Using proxy to connect...");
                    java.net.Proxy proxy = new java.net.Proxy(java.net.Proxy.Type.HTTP, new InetSocketAddress(ipAndPort.getProxy(), ipAndPort.getPort()));
                    //If server blocks us, then we connect via the current proxy we are using
                    connection = (HttpURLConnection) new URL(newUrl).openConnection(proxy);
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0");
                    connection.setRequestProperty("Cookie", cookie);
                    status = connection.getResponseCode();

                }
                if (status == 200) {
                    //Check if the url contains .pdf, if not, is not a pdf file
                    if (!newUrl.endsWith("pdf")) {
                        throw new IOException("Invalid file");
                    }
                }

                connection.connect();
                ReadableByteChannel rbc = Channels.newChannel(connection.getInputStream());
                FileOutputStream fos = new FileOutputStream("./DownloadedPDFs/" + path + "/" + pdfCounter + ".pdf");
                fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);

                if (status != HttpURLConnection.HTTP_OK) {
                    if (status == HttpURLConnection.HTTP_MOVED_TEMP
                            || status == HttpURLConnection.HTTP_MOVED_PERM
                            || status == HttpURLConnection.HTTP_SEE_OTHER)
                        redirect = true;
                } else {
                    redirect = false;
                }

            }
        } else {

            ReadableByteChannel rbc = Channels.newChannel(connection.getInputStream());
            FileOutputStream fos = new FileOutputStream("./DownloadedPDFs/" + path + "/" + pdfCounter + ".pdf");
            fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);

        }
    }


    /**
     * Creates a unique folder to store the downloaded PDFs
     *
     * @param title title of the paper
     * @return string with the name of the folder
     */
    String createUniqueFolder(String title) {
        String[] titleWords = title.split(" ");
        String firstWord = titleWords[0];
        if (firstWord.length() < 3) {
            firstWord = firstWord + titleWords[1];
        }
        if (firstWord.length() > 3) {
            firstWord = firstWord.substring(0, 3);
        }
        File uniqueFileFolder = null;
        try {
            uniqueFileFolder = File.createTempFile(firstWord, null, new File("./DownloadedPDFs"));
        } catch (IOException e) {
            guiLabels.setAlertPopUp("Unable to create output file");
        }

        assert uniqueFileFolder != null;
        String nameOfFolder = uniqueFileFolder.getName().substring(0, uniqueFileFolder.getName().length() - 4);

        File dir = new File("./DownloadedPDFs/" + nameOfFolder);
        //noinspection ResultOfMethodCallIgnored
        dir.mkdir();

        //Delete temporary file
        //noinspection ResultOfMethodCallIgnored
        uniqueFileFolder.delete();

        path = nameOfFolder;
        return nameOfFolder;
    }
}
