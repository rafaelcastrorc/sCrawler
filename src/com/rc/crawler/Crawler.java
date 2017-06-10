package com.rc.crawler;

import javafx.beans.property.*;
import org.apache.commons.io.FileUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Created by rafaelcastro on 5/31/17.
 * Crawler to gather data from Google Scholar.
 * It is the subject of the Observer pattern
 */


public class Crawler {
    String numOfCitations = "";
    private String citingPapersURL = "";
    //Counts number of requests
    private int requestCounter = 0;
    private int pdfCounter;
    private Integer[] listOfTimes;

    private Map<String, Proxy> mapThreadIdToProxy = Collections.synchronizedMap(new HashMap<String, Proxy>());
    //Keeps track of all the proxys that have been downloaded, since we are picking a random element, it is faster to use a list to get it
    private List<Proxy> listOfProxysGathered;
    //Keeps track of all proxys downloaed, used for contains() and remove()
    private Set<Proxy> setOfProxyGathered;
    //Current working proxy
    private Proxy ipAndPort;
    //Random chosen time to wait before searching
    private Integer timeToWait;
    private ExecutorService executorService;
    private ConcurrentLinkedQueue<Proxy> queueOfConnections = new ConcurrentLinkedQueue<>();



    //////////////////
    private StringProperty numberOfWorkingIPs = new SimpleStringProperty();
    private StringProperty alertPopUp = new SimpleStringProperty();
    private StringProperty searchResultLabel = new SimpleStringProperty();
    //Queue with IPs that work
    private DoubleProperty loadBar = new SimpleDoubleProperty();
    private StringProperty output = new SimpleStringProperty();
    private StringProperty connectionOutput = new SimpleStringProperty();
    private IntegerProperty numberOfPDF = new SimpleIntegerProperty();
/////////////////////////

    //Modifies connection output label
    public StringProperty getConnectionOutput() {
        return connectionOutput;
    }

    //Modifies number of PDFs label
    public IntegerProperty getNumberOfPDF() {
        return numberOfPDF;
    }

    //Modifies number of PDFs label
    public StringProperty getNumberOfWorkingIPs() {
        return numberOfWorkingIPs;
    }

    Queue getQueueOfProxies() {
        return queueOfConnections;
    }


    //Creates alerts
    public StringProperty getAlertPopUpProperty() {
        return alertPopUp;
    }

    //Modifies search result label
    public StringProperty getSearchResultLabelProperty() {
        return searchResultLabel;
    }

    //Modifies load bar
    public DoubleProperty getLoadBarProperty() {
        return loadBar;
    }


    //Modifies output
    public StringProperty getOutputProperty() {
        return output;
    }


    Crawler() {
        //Start getting the list of all proxys. Load progress bar up to 75%
        output.set("Initializing...");
    }

    void loadCrawler() {
        //Retrieve all the possible proxies
        getProxies();
        output.set("Establishing connections...");
        //Try to connect to n proxys
        startConnectionThreads();
    }

    private void startConnectionThreads() {

        //Start threads to try to get an initial connection
        //We use 10 threads, so we are going to ideally have 10 active connections at all times.
        executorService = Executors.newFixedThreadPool(10);
        //Start a list of Future that will contain the available connections
        List<Future<Proxy>> listOfFuture = new ArrayList<>();
        List<Callable<Proxy>> listOfRequests = new ArrayList<>();

        //Add 10 requests
        for (int i = 0; i < 10; i++) {
            listOfRequests.add(new Request());
        }

        boolean isFirst = true;


        for (Callable<Proxy> request : listOfRequests) {
            //Add the result to the list of connections. All the elements here have active connections
            listOfFuture.add(executorService.submit(request));
        }

        //Go through all the treads and get the unique working proxy each received

        for (Future<Proxy> proxy : listOfFuture) {
            try {
                if (isFirst) {
                    isFirst = false;
                    Proxy curr = proxy.get();
                    queueOfConnections.add(curr);
                    connectionOutput.setValue("This is the first Ip that will be used");
                    connectionOutput.setValue("First proxy " + queueOfConnections.peek().getProxy());
                    //"Done" loading, but just the first IP
                    loadBar.setValue(1);
                    output.set("Connected!");
                    connectionOutput.setValue("Connected");
                    numberOfWorkingIPs.setValue("add," + queueOfConnections.peek().getProxy());

                } else {
                    Proxy curr = proxy.get();
                    queueOfConnections.add(curr);
                    //Notify the number of working IPs
                    numberOfWorkingIPs.setValue("add," + curr.getProxy());
                    connectionOutput.setValue("Other proxies : " + curr.getProxy());
                }
            } catch (Exception ex) {
                alertPopUp.setValue(ex.toString());
            }
        }

    }


    /**
     * Method called on initiation. Gets all the proxys available.
     */
    private void getProxies() {
        output.set("Starting to download Proxies...");

        int proxyCounter = 0;
        listOfProxysGathered = Collections.synchronizedList(new ArrayList());

        //Sets don't allow repetition so we avoid duplicates
        setOfProxyGathered = Collections.synchronizedSet(new HashSet());
        Document doc;
        //For website www.us-proxy.org
        try {
            doc = Jsoup.connect("http://www.us-proxy.org/").userAgent("Chrome").timeout(5000).get();

            Elements table = doc.select("table");
            Elements rows = table.select("tr");

            Pattern ips = Pattern.compile("\\b(?:(?:25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9]?[0-9])\\.){3}(?:25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9]?[0-9])\\b");
            for (int i = 1; i < rows.size(); i++) { //first row is the col names so skip it.
                Element row = rows.get(i);
                Elements cols = row.select("td");
                boolean found = false;
                String[] array = new String[2];
                for (Element elt : cols) {
                    Matcher matcher = ips.matcher(elt.toString());
                    if (found) {
                        //Get Port number
                        String portNum = elt.toString();
                        portNum = portNum.replaceAll("</?td>", "");
                        array[1] = portNum;
                        Proxy curr = new Proxy(array[0], Integer.valueOf(array[1]));
                        //add as long as it is not already in the set
                        if (!setOfProxyGathered.contains(curr)) {
                            setOfProxyGathered.add(curr);
                            listOfProxysGathered.add(curr);
                            proxyCounter++;

                        }
                        array = new String[2];
                        found = false;
                    }
                    if (matcher.find()) {
                        //If an Ip is found, then the next element is the port number
                        found = true;
                        array[0] = matcher.group();
                    }
                }
            }
            Double d = proxyCounter / 1000.0 * 0.7;
            loadBar.setValue(d);
            output.set("Proxies downloaded: " + proxyCounter + "/1000");


        } catch (IOException e) {
            alertPopUp.setValue("There was a problem accessing the Proxy Database. \nPlease make sure you have an internet connection.");
        }
    }


    /**
     * Search for an article in Google Schoolar
     *
     * @param keyword         String with the title of the document
     * @param hasSearchBefore has the user press the button search before
     * @throws IOException error while openning/connecting to the website
     */
    void searchForArticle(String keyword, boolean hasSearchBefore) {
        int invalidAttempts = 0;
        //Replace space by + in the keyword as in the google search url
        keyword = keyword.replace(" ", "+");
        //Search google scholar
        String url = "https://scholar.google.com/scholar?hl=en&q=" + keyword;
        boolean found = false;
        while (!found) {
            if (invalidAttempts >= 2) {
                searchResultLabel.setValue("Could not find paper, please try writing more specific information");
                numOfCitations = "";
                citingPapersURL = "";
                found = true;
            } else {
                Document doc = changeIP(url, hasSearchBefore, false);


                if (doc.text().contains("Sorry, we can't verify that you're not a robot")) {
                    //In case you been flags as a bot even before searching
                    connectionOutput.setValue("Google flagged your IP as a bot.Changing to a different one");
                    doc = changeIP(url, false, false);

                }
                requestCounter++;
                connectionOutput.setValue(String.valueOf("Number of requests: " + requestCounter));


                String text = "";
                String absLink = "";
                Elements links = doc.select("a[href]");
                for (Element link : links) {
                    text = link.text();
                    absLink = link.attr("abs:href");

                    if (text.contains("Cited by")) {
                        found = true;
                        break;
                    }
                }

                numOfCitations = text;
                citingPapersURL = absLink;
                System.out.println(!doc.toString().contains("1 result"));
                System.out.println(!doc.toString().contains("Showing the best result for this search"));

                if (!doc.toString().contains("1 result") && !doc.toString().contains("Showing the best result for this search")) {

                    searchResultLabel.setValue("ERROR: There was more than 1 result found for your given query.\nPlease write the entire title and/or the authors");
                    numOfCitations = "There was more than 1 result found for your given query";
                }
                invalidAttempts++;
            }
        }


    }

    /**
     * Gets all the possible search results where the article is cited
     *
     * @return ArrayList with all the links
     */
    private ArrayList<String> getAllLinks() {
        ArrayList<String> list = new ArrayList<>();
        Pattern pattern = Pattern.compile("=\\d*");
        Matcher matcher = pattern.matcher(citingPapersURL);
        String paperID = "";
        if (matcher.find()) {
            paperID = matcher.group();
            paperID = paperID.replace("=", "");
        }
        //Add 1-10 results
        list.add(citingPapersURL);
        for (int i = 10; i < 1000 + 1; i = i + 10) {
            String sb = "https://scholar.google.com/scholar?start=" + i + "&hl=en&oe=ASCII&as_sdt=5,39&sciodt=0,39&cites=" +
                    paperID + "&scipsc=";
            list.add(sb);

        }
        return list;

    }

    /**
     * Download a pdf file to a directory
     *
     * @param url URL to download file from
     * @throws IOException Unable to open link
     */
    private void downloadPDF(String url) throws IOException {
        File docDestFile = new File("./DownloadedPDFs/" + pdfCounter + ".pdf");
        URL urlObj = new URL(url);
        FileUtils.copyURLToFile(urlObj, docDestFile);
    }


    /**
     * Get number of papers that cite this article
     *
     * @return String
     */
    String getNumberOfCitations() {
        return numOfCitations;
    }


    /**
     * Change current IP, or continue using the last working one
     *
     * @param url             url that you are trying to connect
     * @param hasSearchBefore has the user click the search button before
     * @return Document
     * @throws IOException Unable to open file
     */
    private Document changeIP(String url, boolean hasSearchBefore, boolean comesFromThread) {
        if (listOfProxysGathered.isEmpty()) {
            //This happens if there is no internet connection
            Document d = null;
            try {
                d = Jsoup.connect(url).userAgent("Mozilla").get();


            } catch (IOException e) {
                alertPopUp.set("Could not connect, please check your internet connection");
                output.setValue("No internet connection");

            }
            return d;
        }
        if (hasSearchBefore && requestCounter <= 100 && !numOfCitations.isEmpty()) {

            //If has searched before and it worked, then use previous ip
            try {
                System.out.println(ipAndPort.getProxy());
                return Jsoup.connect(url).proxy(ipAndPort.getProxy(), ipAndPort.getPort()).userAgent("Mozilla").get();
            } catch (IOException e) {
                connectionOutput.setValue("There was a problem connecting to your previously used proxy.\nChanging to a different one");
                connectionOutput.setValue(e.getMessage());
                changeIP(url, false, false);
            }

        }

        //Connect to the next working IP if the number of request is >100 or the proxy no longer works
        if (queueOfConnections.size() > 0 && !comesFromThread) {
            boolean connected = false;
            System.out.println(Thread.currentThread().getName() +  " is the only thread here");
            Document doc = null;
            while (!connected) {
                //Get an ip from the working list
                Proxy proxyToUse = queueOfConnections.poll();
                ipAndPort = proxyToUse;
                //Since we are using one, we need to find a replacement.
                Request request = new Request(true);
                executorService.submit(request);
                numberOfWorkingIPs.setValue("remove,none");

                try {
                    doc = Jsoup.connect(url).proxy(proxyToUse.getProxy(), proxyToUse.getPort()).userAgent("Mozilla").get();
                    connectionOutput.setValue("Successfully connected to proxy from queue.\nAdding a new thread to find a new connection");
                    System.out.println("what should be " + ipAndPort.getProxy());

                    connected = true;
                } catch (IOException e) {
                    connectionOutput.setValue("There was a problem connecting to one of the Proxies from the queue. ");
                    connectionOutput.setValue(e.getMessage());
                }
            }
            return doc;
        }


        //The only way to get to here is if it is one of the threads trying to find a new connection
        //Stablish a new connection
        //Reset request counter
        requestCounter = 0;
        connectionOutput.setValue(String.valueOf("Number of requests: " + requestCounter));
        boolean connected = false;
        Document doc = null;
        boolean thereWasAnError = false;
        int attempt = 1;
        Proxy proxyToBeUsed = null;
        while (!connected) {
            proxyToBeUsed = addConnection();

            try {
                if (thereWasAnError) {
                    connectionOutput.setValue("Attempt " + attempt + ": Failed to connect to Proxy, trying with a different one...");
                    attempt++;
                } else {
                    connectionOutput.setValue("Connecting to Proxy...");
                }
                doc = Jsoup.connect(url).proxy(proxyToBeUsed.getProxy(), proxyToBeUsed.getPort()).userAgent("Mozilla").get();
                if (doc.text().contains("Sorry, we can't verify that you're not a robot")) {
                    connectionOutput.setValue("Google flagged your IP as a bot.Changing to a different one");
                    throw new IllegalArgumentException();
                }





                connected = true;
                mapThreadIdToProxy.put(Thread.currentThread().getName(), proxyToBeUsed);

            } catch (Exception e) {
                thereWasAnError = true;
            }
        }

        return doc;

    }


    /**
     * Downloads the number of pdf requested
     *
     * @param limit max number of pdfs to download
     * @throws Exception Problem downloading or reading a file
     */
    void getPDFs(int limit) throws Exception {
        output.setValue("Downloading...");
        pdfCounter = 0;
        //Go though all links
        ArrayList<String> list = getAllLinks();
        if (list.isEmpty()) {
            throw new IllegalArgumentException("Please search of an author before downloading");
        }
        for (String currUrl : list) {
            if (pdfCounter >= limit) {
                output.setValue("All PDFs available have been downloaded");
                break;
            }
            timeToWait = getTimeToWait();
            connectionOutput.setValue("Waiting " + timeToWait + " seconds before going to the search results");
            Thread.sleep(timeToWait * 1000);

            //Increase counter for every new google link
            Document citingPapers;
            if (requestCounter >= 100) {
                connectionOutput.setValue("Wait... Changing proxy because of amount of requests...");
                citingPapers = changeIP(currUrl, false, false);
            } else {
                citingPapers = changeIP(currUrl, true, false);
            }

            if (citingPapers.text().contains("Sorry, we can't verify that you're not a robot")) {
                //In case you been flagges as a bot even before searching
                connectionOutput.setValue("Google flagged your IP as a bot.\nChanging to a different one");
                citingPapers = changeIP(currUrl, false, false);

            }

            requestCounter++;

            connectionOutput.setValue(String.valueOf("Number of requests: " + requestCounter));
            Elements linksInsidePaper = citingPapers.select("a[href]");
            String text;
            String absLink;
            for (Element link : linksInsidePaper) {
                text = link.text();
                absLink = link.attr("abs:href");
                if (text.contains("PDF")) {
                    pdfCounter++;
                    try {
                        downloadPDF(absLink);
                    } catch (IOException e2) {
                        connectionOutput.setValue("This file could not be downloaded, skipping...");
                        pdfCounter--;
                    }
                    numberOfPDF.setValue(pdfCounter);
                    loadBar.setValue(pdfCounter/limit);

                    System.out.println(text);
                    System.out.println(absLink);
                    if (pdfCounter >= limit) {
                        break;
                    }
                }

            }
        }
    }


    public synchronized Proxy addConnection() {
        //Make sure that the a connection is only modified by one thread at a time to avoid race conditions

        int randomIndex = new Random().nextInt(listOfProxysGathered.size());
        Proxy curr = listOfProxysGathered.get(randomIndex);
        //If it is still in the set,  it has not yet been used.
        while (!setOfProxyGathered.contains(curr)) {
            randomIndex = new Random().nextInt(listOfProxysGathered.size());
            curr = listOfProxysGathered.get(randomIndex);
        }
        //Remove from set
        setOfProxyGathered.remove(curr);
        //Remove from list
        listOfProxysGathered.remove(randomIndex);
        connectionOutput.setValue("Number of Proxies available: " + setOfProxyGathered.size());

        return curr;
    }


    /**
     * Generates a random time to wait before performing a task
     *
     * @return int that represents seconds
     */
    private int getTimeToWait() {
        if (listOfTimes == null) {
            listOfTimes = new Integer[5];
            for (int i = 0; i < listOfTimes.length; i++) {
                listOfTimes[i] = i + 2;
            }
        }
        int rnd = new Random().nextInt(listOfTimes.length);
        this.timeToWait = listOfTimes[rnd];
        return timeToWait;
    }


    static class Proxy {

        private final String proxy;
        private final int port;

        Proxy(String proxy, int port) {
            this.proxy = proxy;
            this.port = port;
        }

        String getProxy() {
            return proxy;
        }

        int getPort() {
            return port;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Proxy proxy1 = (Proxy) o;

            return port == proxy1.port && (proxy != null ? proxy.equals(proxy1.proxy) : proxy1.proxy == null);
        }

        @Override
        public int hashCode() {
            int result = proxy != null ? proxy.hashCode() : 0;
            result = 31 * result + port;
            return result;
        }
    }


    //Thread class to process requests
    public class Request implements Callable<Proxy> {
        private boolean atRuntime = false;

        Request() {
        }

        Request(boolean atRuntime) {
            this.atRuntime = atRuntime;
        }

        @Override
        public Proxy call() throws Exception {
            connectionOutput.setValue(Thread.currentThread().getId() + " is trying to connect");
            // Try to stablish connection
            boolean valid = false;
            while (!valid) {
                try {
                    changeIP("https://scholar.google.com/scholar?hl=en&q=interesting+articles&btnG=&as_sdt=1%2C39&as_sdtp=", false, true);
                    valid = true;
                } catch (Exception e) {

                }
            }
            if (atRuntime) {
                //If it is at runtime, add it to the queue from here
                queueOfConnections.add(mapThreadIdToProxy.get(Thread.currentThread().getName()));
                System.out.println("Thread found and added + ");
            }
            return mapThreadIdToProxy.get(Thread.currentThread().getName());
        }
    }
}

