package com.rc.crawler;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

/**
 * Created by rafaelcastro on 6/14/17.
 * Downloads PDFs files based on a URL
 */
class PDFDownloader {
    /**
     * Download a pdf file to a directory
     *
     * @param url URL to download file from
     * @param pdfCounter Number of PDFs downloaded
     * @param guiLabels GUILabelManagement object
     * @param ipAndPort Proxy with the current proxy being used
     * @throws IOException Unable to open link
     */
     void downloadPDF(String url, int pdfCounter, GUILabelManagement guiLabels, Proxy ipAndPort) throws IOException {
        URL urlObj = new URL(url);
        HttpURLConnection connection =  (HttpURLConnection) urlObj.openConnection();

        //Set request property to avoid error 403
        connection.setRequestProperty("User-Agent", "Chrome");
        int status = connection.getResponseCode();
        if (status == 200) {
            System.out.println(url);
            //Check if the url contains .pdf, if not, is not a pdf file
            if (!url.endsWith("pdf")) {
                throw new IOException("Invalid file");
            }
        }

        if (status == 429) {
            //If we have sent too many reuest to server, change proxy
            String cookie = connection.getHeaderField( "Set-Cookie").split(";")[0];
            connection.disconnect();
            guiLabels.setConnectionOutput("We have been blocked by this server. Using proxy to connect...");
            java.net.Proxy proxy = new java.net.Proxy(java.net.Proxy.Type.HTTP, new InetSocketAddress(ipAndPort.getProxy(), ipAndPort.getPort()));
            //If server blocks us, then we connect via the current proxy we are using
            connection = (HttpURLConnection) urlObj.openConnection(proxy);
            connection.setRequestProperty("User-Agent", "Chrome");
            connection.setRequestProperty("Cookie", cookie );
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
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.11 (KHTML, like Gecko) Chrome/23.0.1271.95 Safari/537.11");
                status = connection.getResponseCode();
                if (status == 429) {
                    String cookie = connection.getHeaderField( "Set-Cookie").split(";")[0];
                    connection.disconnect();
                    guiLabels.setConnectionOutput("We have been blocked by this server. Using proxy to connect...");
                    java.net.Proxy proxy = new java.net.Proxy(java.net.Proxy.Type.HTTP, new InetSocketAddress(ipAndPort.getProxy(), ipAndPort.getPort()));
                    //If server blocks us, then we connect via the current proxy we are using
                    connection = (HttpURLConnection) new URL(newUrl).openConnection(proxy);
                    connection.setRequestProperty("User-Agent", "Chrome");
                    connection.setRequestProperty("Cookie", cookie );
                    status = connection.getResponseCode();

                }
                if (status == 200) {
                    //Check if the url contains .pdf, if not, is not a pdf file
                    System.out.println(newUrl);
                    if (!newUrl.endsWith("pdf")) {
                        throw new IOException("Invalid file");
                    }
                }

                connection.connect();
                ReadableByteChannel rbc = Channels.newChannel(connection.getInputStream());
                FileOutputStream fos = new FileOutputStream("./DownloadedPDFs/" + pdfCounter + ".pdf");
                fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);

                if (status != HttpURLConnection.HTTP_OK) {
                    if (status == HttpURLConnection.HTTP_MOVED_TEMP
                            || status == HttpURLConnection.HTTP_MOVED_PERM
                            || status == HttpURLConnection.HTTP_SEE_OTHER)
                        redirect = true;
                }
                else {
                    redirect = false;
                }

            }
        }
        else {
            ReadableByteChannel rbc = Channels.newChannel(connection.getInputStream());
            FileOutputStream fos = new FileOutputStream("./DownloadedPDFs/" + pdfCounter + ".pdf");
            fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);

        }
    }
}