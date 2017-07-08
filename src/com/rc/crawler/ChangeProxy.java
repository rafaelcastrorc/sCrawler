package com.rc.crawler;

import org.jsoup.Connection;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.util.regex.Pattern;

/**
 * Created by rafaelcastro on 7/7/17.
 */
public class ChangeProxy {
    private GUILabelManagement guiLabels;
    private Crawler crawler;
    private boolean isError404;
    private boolean thereWasAnErrorWithProxy = false;

    ChangeProxy(GUILabelManagement guiLabels, Crawler crawler) {

        this.guiLabels = guiLabels;
        this.crawler = crawler;
    }

    /**
     * Verifies if there is internet connection
     */
    private void verifyIfThereIsConnection() {
        while (!crawler.isThereConnection()) {
            try {
                //Sleep for 10 seconds, try until connection is found
                Thread.sleep(10 * 1000);
            } catch (InterruptedException ignored) {
            }
        }
    }

    Document getProxy(String url, boolean hasSearchBefore, boolean comesFromThread) {
        long currThreadID = Thread.currentThread().getId();

        //Step 1. Check if there is internet connection
        if (!crawler.isThereConnection()) {
            guiLabels.setAlertPopUp("Could not connect, please check your internet connection");
            guiLabels.setOutput("No internet connection");
            guiLabels.setOutputMultiple("No internet connection");
            verifyIfThereIsConnection();
        }

        //Step 2. Check if there are less than 50 proxies remaining, we add more
        //But first we check if we have connection. Also we make sure that there is only one thread getting more proxies
        if (crawler.getSetOfProxyGathered().size() < 50 && !crawler.isThreadGettingMoreProxies()) {
            //Verify again if there is internet connection
            if (!crawler.isThereConnection()) {
                guiLabels.setOutput("No internet connection");
                guiLabels.setOutputMultiple("No internet connection");
                verifyIfThereIsConnection();
            }
            crawler.setThreadIsGettingMoreProxies(true);
            crawler.getMoreProxies();
            crawler.setThreadIsGettingMoreProxies(false);
        }


        //If the program searched before with this proxy and it worked, then use previous proxy. But first, verify
        // the amount of request send to the given page is less than 40 for the current proxy
        if (hasSearchBefore && crawler.getNumberOfRequestFromMap(url, crawler.getMapThreadIdToProxy().get
                (currThreadID)) <= 40) {
            Document doc = useProxyAgain(url, currThreadID);
            if (doc != null) {
                return doc;
            }
        }

        //Connect to the new working proxy if the number of request is >50 or the proxy that the thread is using no
        //longer works
        if (crawler.getQueueOfConnections().size() > 0 && !comesFromThread) {
            return connectToProxyFromQueue(currThreadID, url);
        }

        return establishNewConnection(comesFromThread, url);

    }

    /**
     * Finds a new working proxy
     *
     * @param comesFromThread To be true, it has to come from an obj from the Request class
     * @param url             URL we are trying to connect to
     */
    private Document establishNewConnection(boolean comesFromThread, String url) {
        Document doc = null;
        if (comesFromThread) {
            boolean connected = false;
            boolean thereWasAnError = false;
            Proxy proxyToBeUsed;
            while (!connected) {

                if (!crawler.isThereConnection()) {

                    guiLabels.setOutput("No internet connection");
                    guiLabels.setOutputMultiple("No internet connection");
                    verifyIfThereIsConnection();
                }
                proxyToBeUsed = crawler.addConnection();

                try {
                    if (!thereWasAnError) {
                        guiLabels.setConnectionOutput("Connecting to Proxy...");
                    }
                    doc = Jsoup.connect(url).proxy(proxyToBeUsed.getProxy(), proxyToBeUsed.getPort()).userAgent
                            ("Mozilla").get();
                    if (doc.text().contains("Sorry, we can't verify that you're not a robot")) {
                        throw new IllegalArgumentException();
                    }
                    connected = true;
                    crawler.getMapThreadIdToProxy().put(Thread.currentThread().getId(), proxyToBeUsed);

                } catch (Exception e) {
                    thereWasAnError = true;
                }
            }
        }
        return doc;
    }


    /**
     * Connects to a working proxy from the queue of connections
     *
     * @param url          URL that the program is trying to connect to
     * @param currThreadID ID of the current thread
     * @return doc
     */
    private Document connectToProxyFromQueue(long currThreadID, String url) {

        //Check if the proxy has more than 40 connections, if so, replace it.
        if (crawler.getNumberOfRequestFromMap(url, crawler.getMapThreadIdToProxy().get(currThreadID)) > 40) {
            guiLabels.setConnectionOutput("Proxy has more than 40 requests");
        }

        boolean connected = false;
        Document doc = null;
        int attempt = 0;
        while (!connected) {

            if (!thereWasAnErrorWithProxy && attempt > 0) {
                //Add it again to queue if it did not produce an actual error (non website related)
                crawler.getQueueOfConnections().add(crawler.getMapThreadIdToProxy().get(currThreadID));
            }
            if (thereWasAnErrorWithProxy) {
                //If there was an actual error, reset the counter.
                attempt = 0;
            }
            thereWasAnErrorWithProxy = false;
            //Get an ip from the working list
            Proxy proxyToUse = crawler.getQueueOfConnections().poll();
            //If the proxy is null, or it has more than 40 request for the current website, then trying finding a new
            //one
            boolean first = true;
            while (proxyToUse == null || crawler.getNumberOfRequestFromMap(url, proxyToUse) > 40) {
                if (proxyToUse != null) {
                    //If the previous proxy was not null add it again
                    crawler.getQueueOfConnections().add(proxyToUse);
                }
                //Since we are using a new proxy, we need to find a replacement
                if (first) {
                    Request request = new Request(true, "getConnection", crawler, guiLabels);
                    crawler.getExecutorService().submit(request);
                    guiLabels.setNumberOfWorkingIPs("remove,none");
                    first = false;
                }
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                proxyToUse = crawler.getQueueOfConnections().poll();

            }

            System.out.println("Getting new IP " + currThreadID + " " + proxyToUse.getProxy() + " " +
                    proxyToUse.getPort());
            crawler.getMapThreadIdToProxy().put(currThreadID, proxyToUse);
            //Since we are using a new proxy, we need to find a replacement
            //If there are already 12 proxies in the queue, then don't add more
            if (crawler.getQueueOfConnections().size() <= 10 && !isError404) {
                Request request = new Request(true, "getConnection", crawler, guiLabels);
                crawler.getExecutorService().submit(request);
                guiLabels.setNumberOfWorkingIPs("remove,none");
            }

            try {
                Connection.Response connection = Jsoup.connect(url).proxy(proxyToUse.getProxy(), proxyToUse
                        .getPort()).userAgent("Mozilla/5.0 (Windows; U; WindowsNT 5.1; en-US; rv1.8.1.6) " +
                        "Gecko/20070725 Firefox/2.0.0.6").ignoreHttpErrors(true).execute();
                doc = connection.parse();
                if (connection.statusCode() != 200) {
                    throw new HttpStatusException(connection.statusMessage(), connection.statusCode(), connection.url
                            ().toString());
                }
                if (doc.text().contains("Sorry, we can't verify that you're not a robot")) {
                    throw new IllegalArgumentException("Google flagged your IP as a bot. Changing to a different " +
                            "one");
                }
                connected = true;
            } catch (HttpStatusException e) {
                guiLabels.setConnectionOutput("There was a problem connecting to one of the Proxies from the " +
                        "queue.");
                if (attempt > 2) {
                    break;
                }
                attempt++;
                guiLabels.setConnectionOutput(e.getMessage());
            } catch (Exception e) {
                guiLabels.setConnectionOutput("There was a problem connecting to one of the Proxies from the " +
                        "queue. Removing it");
                thereWasAnErrorWithProxy = true;
                attempt++;
            }
            crawler.addRequestToMapOfRequests(url, crawler.getMapThreadIdToProxy().get(currThreadID));

        }
        return doc;
    }

    /**
     * Reuse a previously working proxy
     *
     * @param url          URL that the program is trying to connect to
     * @param currThreadID ID of the current thread
     */
    private Document useProxyAgain(String url, long currThreadID) {
        Proxy ipAndPort = crawler.getMapThreadIdToProxy().get(currThreadID);
        try {
            Connection.Response connection = Jsoup.connect(url).timeout(10 * 1000).proxy(ipAndPort.getProxy(),
                    ipAndPort.getPort()).userAgent("Mozilla/5.0 (Windows; U; WindowsNT 5.1; en-US; rv1.8.1.6) " +
                    "Gecko/20070725 Firefox/2.0.0.6").execute();
            return connection.parse();
        } catch (HttpStatusException e2) {
            guiLabels.setConnectionOutput("There was a problem connecting to your previously used proxy" +
                    ".\nChanging to a different one");
            this.isError404 = e2.getStatusCode() == 404 || e2.getStatusCode() == 401 || e2.getStatusCode() == 403 ||
                    e2.getStatusCode() == 403;
            //Add it back to the queue if it is error 404, since it is not a problem of the proxy
            if (isError404) {
                crawler.getQueueOfConnections().add(ipAndPort);
            } else {
                thereWasAnErrorWithProxy = true;
            }
            guiLabels.setConnectionOutput(e2.getMessage());

        } catch (Exception e) {
            thereWasAnErrorWithProxy = true;
            guiLabels.setConnectionOutput("There was a problem connecting to your previously used proxy" +
                    ".\nChanging to a different one");
        }
        return null;
    }
}

