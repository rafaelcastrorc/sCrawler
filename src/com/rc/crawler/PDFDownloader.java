package com.rc.crawler;

import org.jsoup.nodes.Document;

import java.io.*;
import java.net.*;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by rafaelcastro on 6/14/17.
 * Manages the way PDFs are stored and downloaded.
 */
class PDFDownloader {
    private GUILabelManagement guiLabels;
    private String path = "";
    private Crawler crawler;

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
     * @param speedUp    If true, program does not use proxies to download most files.
     * @throws IOException Unable to open link
     */
    void downloadPDF(String url, int pdfCounter, GUILabelManagement guiLabels, Proxy ipAndPort, boolean speedUp)
            throws Exception {
        this.guiLabels = guiLabels;
        DownloadPDFTask downloadPDFTask = new DownloadPDFTask(url, pdfCounter, guiLabels, ipAndPort, speedUp);
        ExecutorService executorService = Executors.newSingleThreadExecutor(new MyThreadFactory());
        Future<String> future =executorService.submit(downloadPDFTask);
        String result;
        try {
            //Limit of 2 minute to download a file
            result = future.get(3, TimeUnit.MINUTES);
        } catch (Exception e){
            future.cancel(true);
            System.out.println("Timeout download: " + url);
            result = "Timeout";
        }
        //If result is not empty, it means that an exception was thrown inside the thread, or there was a timeout
        if (!result.isEmpty()) {
            throw new IOException(result);
        }
    }

    public void setCrawler(Crawler crawler) {
        this.crawler = crawler;
    }


    /**
     * Class to download PDF files using threads
     */
    class DownloadPDFTask implements Callable<String> {

        private String url;
        private final int pdfCounter;
        private final GUILabelManagement guiLabels;
        private final Proxy ipAndPort;
        private final boolean speedUp;

        DownloadPDFTask(String url, int pdfCounter, GUILabelManagement guiLabels, Proxy ipAndPort, boolean speedUp) {
            this.url = url;
            this.pdfCounter = pdfCounter;
            this.guiLabels = guiLabels;
            this.ipAndPort = ipAndPort;
            this.speedUp = speedUp;
        }

        @Override
        public String call() throws Exception {
            String result = "";
            try {
                URL urlObj = new URL(url);
                HttpURLConnection connection;
                java.net.Proxy proxy = new java.net.Proxy(java.net.Proxy.Type.HTTP, new InetSocketAddress(ipAndPort.getProxy(), ipAndPort.getPort()));

                if (!speedUp) {
                    connection = (HttpURLConnection) urlObj.openConnection(proxy);
                } else {
                    connection = (HttpURLConnection) urlObj.openConnection();

                }
                //Set request property to avoid error 403
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows; U; WindowsNT 5.1; en-US; rv1.8.1.6) Gecko/20070725 Firefox/2.0.0.6");
                connection.setRequestProperty("Referer", "https://scholar.google.com/");
                int status = connection.getResponseCode();

                if (status == 429) {
                    //If we have sent too many request to server, change proxy
                    String cookie = connection.getHeaderField("Set-Cookie").split(";")[0];
                    connection.disconnect();
                    guiLabels.setConnectionOutput("We have been blocked by this server. Using proxy to connect...");
                    //If server blocks us, then we connect via the current proxy we are using
                    connection = (HttpURLConnection) urlObj.openConnection(proxy);
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0");
                    connection.setRequestProperty("Referer", "https://scholar.google.com/");
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
                if (status == 403) {
                    throw new IOException("Error 403");

                }
                if (redirect) {
                    while (redirect) {
                        // get redirect url from "location" header field
                        String newUrl = connection.getHeaderField("Location");
                        url = newUrl;
                        // open the new connection again
                        if (!speedUp) {
                            connection = (HttpURLConnection) new URL(newUrl).openConnection(proxy);
                        } else {
                            connection = (HttpURLConnection) new URL(newUrl).openConnection();
                        }
                        connection.setRequestProperty("User-Agent", "Chrome");
                        status = connection.getResponseCode();
                        if (status == 429) {
                            String cookie = connection.getHeaderField("Set-Cookie").split(";")[0];
                            connection.disconnect();
                            guiLabels.setConnectionOutput("We have been blocked by this server. Using proxy to connect...");
                            //If server blocks us, then we connect via the current proxy we are using
                            connection = (HttpURLConnection) new URL(newUrl).openConnection(proxy);
                            connection.setRequestProperty("User-Agent", "Mozilla/5.0");
                            connection.setRequestProperty("Cookie", cookie);
                            status = connection.getResponseCode();

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
            }catch (Exception e) {
                e.printStackTrace();
                result = e.getMessage();
            }

            return result;
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
        StringBuilder firstWord = new StringBuilder(titleWords[0]);
        if (firstWord.length() < 3) {
            firstWord.append(titleWords[1]);
        }
        if (firstWord.length() > 3) {
            firstWord = new StringBuilder(firstWord.substring(0, 3));
        }
        while (firstWord.length() < 3) {
            firstWord.append("a");
        }
        File uniqueFileFolder = null;
        try {
            uniqueFileFolder = File.createTempFile(firstWord.toString(), null, new File("./DownloadedPDFs"));
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
