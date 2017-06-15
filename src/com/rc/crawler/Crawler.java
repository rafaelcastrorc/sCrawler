package com.rc.crawler;

import javafx.beans.property.*;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by rafaelcastro on 5/31/17.
 * Crawler to gather data from Google Scholar.
 */


class Crawler {
    private final GUILabelManagement guiLabels;


    private PDFDownloader pdfDownloader;
    private String numOfCitations = "";
    private String citingPapersURL = "";
    //Counts number of requests
    private int requestCounter = 0;
    private Integer[] listOfTimes;
    private Map<Long, Proxy> mapThreadIdToProxy = Collections.synchronizedMap(new HashMap<Long, Proxy>());
    //Keeps track of all the proxies that have been downloaded, since we are picking a random element, it is faster to use a list to get it
    private List<Proxy> listOfProxiesGathered;
    //Keeps track of all proxies downloaded, used for contains() and remove()
    private Set<Proxy> setOfProxyGathered;
    //Current working proxy
    private Proxy ipAndPort;
    //Random chosen time to wait before searching
    private Integer timeToWait;
    private ExecutorService executorService;
    private ConcurrentLinkedQueue<Proxy> queueOfConnections = new ConcurrentLinkedQueue<>();


    public void setPdfDownloader(PDFDownloader pdfDownloader) {
        this.pdfDownloader = pdfDownloader;
    }


    public HashMap<String, String[]> getSearchResultToLink() {
        return searchResultToLink;
    }

    public void setNumOfCitations(String numOfCitations) {
        this.numOfCitations = numOfCitations;
    }

    public void setCitingPapersURL(String citingPapersURL) {
        this.citingPapersURL = citingPapersURL;
    }

    private HashMap<String, String[]> searchResultToLink = new HashMap<>();
    //Modifies connection output label
     StringProperty getConnectionOutput() {
        return guiLabels.getConnectionOutput();
    }

    //Modifies number of PDFs label
     StringProperty getNumberOfPDF() {
        return guiLabels.getNumberOfPDFs();
    }

    //Modifies number of PDFs label
     StringProperty getNumberOfWorkingIPs() {
        return guiLabels.getNumberOfWorkingIPs();
    }

    //Creates alerts
     StringProperty getAlertPopUpProperty() {
        return guiLabels.getAlertPopUp();
    }

    //Modifies search result label
    public StringProperty getSearchResultLabelProperty() {
        return guiLabels.getSearchResultLabel();
    }

    //Modifies load bar
    DoubleProperty getLoadBarProperty() {
        return guiLabels.getLoadBar();
    }


    //Modifies output
    StringProperty getOutputProperty() {
        return guiLabels.getOutput();
    }

    //Modifies list view
    StringProperty getMultipleSearchResult() {
        return guiLabels.getMultipleSearchResult();
    }



    /**
     * Constructor.
     */
    Crawler() {
        this.guiLabels = new GUILabelManagement();
        //Start getting the list of all proxys. Load progress bar up to 75%
        guiLabels.setLoadBar(0);
        guiLabels.setOutput("Initializing...");
    }

    /**
     * Loads the crawler. Calls the different methods to download n proxies, and at least 1 working connection, before
     * the user can use the program.
     */
    void loadCrawler() {
        //Retrieve n proxies (n = 1000)
        getProxies(1000);
        guiLabels.setOutput("Establishing connections...");
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
                    guiLabels.setConnectionOutput("This is the first proxy that will be used: "+ queueOfConnections.peek().getProxy());
                    //"Done" loading, but just the first proxy
                    guiLabels.setLoadBar(1);
                    guiLabels.setOutput("Connected!");
                    guiLabels.setConnectionOutput("Connected");
                    Proxy temp = queueOfConnections.peek();
                    guiLabels.setNumberOfWorkingIPs("add," + temp.getProxy()+" Port: "+temp.getPort());

                } else {
                    Proxy curr = proxy.get();
                    queueOfConnections.add(curr);
                    //Notify the number of working IPs
                    guiLabels.setNumberOfWorkingIPs("add," + curr.getProxy()+" Port: "+curr.getPort());
                    guiLabels.setConnectionOutput("Other proxies : " + curr.getProxy());
                }
            } catch (Exception ex) {
                guiLabels.setAlertPopUp(ex.toString());
            }
        }

    }


    /**
     * Downloads proxies from different websites, without duplicates. If proxies have been downloaded before, within a
     * 24h window, it loads those proxies instead.
     * @param numberOfProxiesToDownload limit of the proxies to download.
     */
    private void getProxies(int numberOfProxiesToDownload) {
        int proxyCounter = 0;
        listOfProxiesGathered = Collections.synchronizedList(new ArrayList<Proxy>());
        //Sets don't allow repetition so we avoid duplicates
        setOfProxyGathered = Collections.synchronizedSet(new HashSet<Proxy>());

        guiLabels.setOutput("Checking if a valid proxy file exists...");
        //Check first if there have been proxies downloaded
        Logger logger = Logger.getInstance();

        boolean fileExist = true;
        //First see if file exists

        Scanner scanner = null;
        File listOfProxiesFile = logger.getListOfProxies();
        try {

            scanner = new Scanner(listOfProxiesFile);
        } catch (FileNotFoundException e) {
            guiLabels.setConnectionOutput("Could not find ListOfProxies file.");
            fileExist = false;
        }

        if (fileExist && listOfProxiesFile.length() > 100) {
            //If file exist and is not empty, we continue
            boolean isFirstLine = true;
            boolean isValidDate = true;
            while (scanner.hasNext()) {
                String curr = scanner.nextLine();
                if (isFirstLine) {
                    //First line contains the date it was created. Verify that it has not been 24h since file was created.
                    DateTimeFormatter formatter = DateTimeFormat.forPattern("dd/MM/yyyy HH:mm:ss");
                    DateTime dateOfCreation = formatter.parseDateTime(curr);

                    DateTime now = new DateTime();
                    double hours = (now.getMillis() - dateOfCreation.getMillis()) / 1000 / 60 / 60;
                    if (hours >= 24) {
                        isValidDate = false;
                    }
                    isFirstLine = false;
                    continue;
                }
                if (!isValidDate) {
                    guiLabels.setOutput("No valid file found.");
                    //If it is not a valid date we stop.
                    scanner.close();
                    getProxiesFromWebsite(numberOfProxiesToDownload, 0);
                    break;
                }
                //It is a valid file so we retrieve all the stored proxies from the file.
                String[] ipAndProxyString = curr.split(",");
                Proxy proxy = new Proxy(ipAndProxyString[0], Integer.valueOf(ipAndProxyString[1]));

                //Add it to the sets
                if (!setOfProxyGathered.contains(proxy)) {
                    setOfProxyGathered.add(proxy);
                    listOfProxiesGathered.add(proxy);
                    proxyCounter++;

                }
                Double d = (proxyCounter / (numberOfProxiesToDownload) * 1.0) * 0.7;
                guiLabels.setLoadBar(d);
                guiLabels.setOutput("Proxies downloaded: " + proxyCounter + "/" + numberOfProxiesToDownload);
            }
            if (proxyCounter < numberOfProxiesToDownload) {
                //Get more proxies if there are not enough
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {
                }
                guiLabels.setOutput("Not enough proxies in file.");
                scanner.close();
                getProxiesFromWebsite(numberOfProxiesToDownload, proxyCounter);
            }
        } else {
            guiLabels.setOutput("No valid file found.");
            scanner.close();
            getProxiesFromWebsite(numberOfProxiesToDownload, 0);
        }
        scanner.close();
    }

    /**
     * Downloads proxies from different websites, without duplicates.
     * @param numberOfProxiesToDownload Limit of proxies to download
     * @param proxyCounter The number of proxies that have been downloaded so far.
     * @return
     */
    private boolean getProxiesFromWebsite(int numberOfProxiesToDownload, int proxyCounter) {


        //Set a new file, if there was one before, overwrite it
        Logger logger = Logger.getInstance();
        try {
            if (proxyCounter == 0) {
                //If there were no proxies before, we start a new file
                logger.setListOfProxies(false);
                DateTimeFormatter formatter = DateTimeFormat.forPattern("dd/MM/yyyy HH:mm:ss");
                //Write current date
                DateTime now = new DateTime();
                logger.writeToListOfProxies(now.toString(formatter));
            }
            else {
                //If there were proxies in the file, then just append
                logger.setListOfProxies(true);
            }


            guiLabels.setOutput("Starting to download Proxies...");

            Document doc;

            //Websites that contain lists with proxies
            ArrayList<String> proxiesLists = new ArrayList<>();
            //working US only proxy list. All results displayed at once. (< 200)
            proxiesLists.add("https://www.us-proxy.org");

            //Working international proxy list. All results displayed at once. (<300)
            proxiesLists.add("http://www.httptunnel.ge/ProxyListForFree.aspx");

             //Working international proxy list. All results displayed at once. (<150)
              proxiesLists.add("https://hidemy.name/en/proxy-list");


             //Use for backup
            //International list, but it is divided in entries.
             proxiesLists.add("https://www.hide-my-ip.com/proxylist.shtml");

            //This site has over 1000 proxies, but it is divided by entries.
            for (int i = 0; i< 300; i = i +15) {
                proxiesLists.add("http://proxydb.net/?offset="+i);
            }


            //Iterate over all websites
            for (String url : proxiesLists) {

                //Get Base URI
                URL urlObj = new URL(url);
                String baseURI = urlObj.getProtocol() + "://" + urlObj.getHost();


                boolean mainPage = true;
                //Link to possible url inside the table to get more entries
                String absLink = "";
                boolean areThereMoreEntries = true;

                //Go through the url, find all proxies and check if the website has more entries.
                while (areThereMoreEntries) {
                    try {

                        if (mainPage && !url.contains("http://proxydb.net/?offset=")) {
                            doc = Jsoup.connect(url).userAgent("Mozilla/5.0 (Windows; U; WindowsNT 5.1; en-US; rv1.8.1.6) Gecko/20070725 Firefox/2.0.0.6").get();
                            mainPage = false;
                        } else {
                            //Sleep random periods before requesting info from website
                            timeToWait = getTimeToWait();
                            Thread.sleep(timeToWait * 1000);
                            doc = Jsoup.connect(baseURI + absLink).userAgent("Mozilla/5.0 (Windows; U; WindowsNT 5.1; en-US; rv1.8.1.6) Gecko/20070725 Firefox/2.0.0.6").get();
                        }
                        areThereMoreEntries = false;
                        //Check if there are links to find more table entries
                        Pattern linkPattern = Pattern.compile("<a( )?(href).*(</a>)");
                        Matcher linkMatcher = linkPattern.matcher(doc.html());

                        while (linkMatcher.find()) {
                            String strLink = linkMatcher.group();
                            if (strLink.contains("start=")) {
                                Pattern newURLPattern = Pattern.compile("/[^\">]*");
                                Matcher newURLMatcher = newURLPattern.matcher(strLink);
                                if (newURLMatcher.find()) {
                                    //Get the new url, remove beginning /
                                    absLink = newURLMatcher.group();
                                    areThereMoreEntries = true;
                                    break;
                                }
                            }
                        }
                        //Get the data from the table
                        Elements table = doc.select("table");
                        Elements rows = table.select("tr");
                        Pattern ips = Pattern.compile("\\b(?:(?:25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9]?[0-9])\\.){3}(?:25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9]?[0-9])\\b");
                        Pattern ipAndPort = Pattern.compile("\\b(?:(?:25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9]?[0-9])\\.){3}(?:25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9]?[0-9])\\b:\\d{2,4}");
                        for (int i = 1; i < rows.size(); i++) { //first row is the col names so skip it.
                            Element row = rows.get(i);
                            Elements cols = row.select("td");
                            boolean found = false;
                            String[] array = new String[2];
                            for (Element elt : cols) {
                                //matcher matches only ip, matcher2 matches ip with port number
                                Matcher matcher = ips.matcher(elt.toString());
                                Matcher matcher2 = ipAndPort.matcher(elt.toString());
                                if (matcher2.find()) {
                                    //If port and number appear in the same string
                                    found = false;
                                    String[] currIPAndPort = matcher2.group().split(":");
                                    String ip = currIPAndPort[0];
                                    int port = Integer.valueOf(currIPAndPort[1]);
                                    Proxy nProxy = new Proxy(ip, port);

                                    if (!setOfProxyGathered.contains(nProxy)) {
                                        logger.writeToListOfProxies("\n" + nProxy.getProxy() + "," + nProxy.getPort());
                                        setOfProxyGathered.add(nProxy);
                                        listOfProxiesGathered.add(nProxy);
                                        proxyCounter++;
                                        if (proxyCounter == numberOfProxiesToDownload) {
                                            //Once we have enough proxies, stop
                                            return true;
                                        }
                                    }

                                }
                                else {
                                    if (found) {
                                        //Get Port number
                                        String portNum = elt.toString();
                                        portNum = portNum.replaceAll("</?td>", "");
                                        array[1] = portNum;
                                        Proxy curr = new Proxy(array[0], Integer.valueOf(array[1]));
                                        //add as long as it is not already in the set
                                        if (!setOfProxyGathered.contains(curr)) {
                                            logger.writeToListOfProxies("\n" + curr.getProxy() + "," + curr.getPort());
                                            setOfProxyGathered.add(curr);
                                            listOfProxiesGathered.add(curr);
                                            proxyCounter++;
                                            if (proxyCounter == numberOfProxiesToDownload) {
                                                //Once we have enough proxies, stop
                                                return true;
                                            }

                                        }
                                        array = new String[2];
                                        found = false;
                                    } else if (matcher.find()) {
                                        //If an Ip is found, then the next element is the port number
                                        found = true;
                                        array[0] = matcher.group();
                                    }
                                }

                            }
                        }
                        Double d = (proxyCounter / (double) numberOfProxiesToDownload) * 0.7;
                        guiLabels.setLoadBar(d);
                        guiLabels.setOutput("Proxies downloaded: " + proxyCounter + "/" + numberOfProxiesToDownload);
                        Thread.sleep(1000);


                    } catch (IOException e) {
                        guiLabels.setAlertPopUp("There was a problem one of the Proxy Databases. \nPlease make sure you have an internet connection.");
                    } catch (InterruptedException e) {
                        guiLabels.setConnectionOutput(e.getMessage());
                    }
                }
            }
        }
        catch (IOException e) {
            guiLabels.setAlertPopUp(e.getMessage());
        }
        return false;

    }


    void getMoreProxies() {
        //Todo
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
                guiLabels.setSearchResultLabel("Could not find paper, please try writing more specific information");
                numOfCitations = "";
                citingPapersURL = "";
                found = true;
            } else {
                Document doc = changeIP(url, hasSearchBefore, false);


                if (doc.text().contains("Sorry, we can't verify that you're not a robot")) {
                    //In case you been flags as a bot even before searching
                    guiLabels.setConnectionOutput("Google flagged this proxy as a bot. Changing to a different one");
                    doc = changeIP(url, false, false);

                }
                requestCounter++;
                guiLabels.setConnectionOutput("Number of requests: " + requestCounter);


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

                if (!doc.toString().contains("1 result") && !doc.toString().contains("Showing the best result for this search")) {
                    guiLabels.setSearchResultLabel("ERROR: There was more than 1 result found for your given query");
                    numOfCitations = "There was more than 1 result found for your given query";

                    boolean searchResultFound = false;
                    String searchResult = "";
                    for (Element link : links) {
                        text = link.text();
                        absLink = link.attr("abs:href");
                        Pattern pattern = Pattern.compile("((www\\.)?scholar\\.google\\.com)|(www\\.(support\\.)?google\\.com)");
                        Matcher matcher = pattern.matcher(absLink);
                        if (!matcher.find()) {
                            text = link.text();
                            Pattern pattern2 = Pattern.compile("\\[HTML]|\\[PDF]");
                            Matcher matcher2 = pattern2.matcher(text);
                            if (!matcher2.find() && !text.equals("Provide feedback")) {
                                searchResult = text;
                                guiLabels.setMultipleSearchResult(text);
                                searchResultFound = true;

                            }
                        }
                        else if (searchResultFound) {
                            if (text.contains("Cited by")) {
                                searchResultToLink.put(searchResult, new String[]{absLink, text});
                                searchResultFound = false;

                            }

                        }
                    }
                }
                invalidAttempts++;
                hasSearchBefore = true;
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
        if (listOfProxiesGathered.isEmpty()) {
            //This happens if there is no internet connection
            Document d = null;
            try {
                d = Jsoup.connect(url).userAgent("Mozilla").get();


            } catch (IOException e) {
                guiLabels.setAlertPopUp("Could not connect, please check your internet connection");
                guiLabels.setOutput("No internet connection");

            }
            return d;
        }

        if (hasSearchBefore && requestCounter <= 100) {

            //If has searched before and it worked, then use previous ip
            try {
                return Jsoup.connect(url).proxy(ipAndPort.getProxy(), ipAndPort.getPort()).userAgent("Mozilla").get();
            } catch (IOException e) {
                guiLabels.setConnectionOutput("There was a problem connecting to your previously used proxy.\nChanging to a different one");
                guiLabels.setConnectionOutput(e.getMessage());
            }

        }

        //Connect to the next working IP if the number of request is >100 or the proxy no longer works
        if (queueOfConnections.size() > 0 && !comesFromThread) {
            System.out.println(queueOfConnections.size());
            boolean connected = false;
            Document doc = null;
            while (!connected) {
                //Get an ip from the working list
                Proxy proxyToUse = queueOfConnections.poll();
                ipAndPort = proxyToUse;
                //Since we are using one, we need to find a replacement.
                Request request = new Request(true);
                executorService.submit(request);
                guiLabels.setNumberOfWorkingIPs("remove,none");

                try {
                    doc = Jsoup.connect(url).proxy(proxyToUse.getProxy(), proxyToUse.getPort()).userAgent("Mozilla").get();
                    guiLabels.setConnectionOutput("Successfully connected to proxy from queue.\nAdding a new thread to find a new connection");
                    if (doc.text().contains("Sorry, we can't verify that you're not a robot")) {
                        throw new IllegalArgumentException("Google flagged your IP as a bot. Changing to a different one");
                    }
                    connected = true;
                } catch (Exception e) {
                    guiLabels.setConnectionOutput("There was a problem connecting to one of the Proxies from the queue.");
                    guiLabels.setConnectionOutput(e.getMessage());
                }
            }
            requestCounter = 1;
            return doc;
        }

        //The only way to get to here is if it is one of the threads trying to find a new connection
        //Establish a new connection
        //Reset request counter
        guiLabels.setConnectionOutput(String.valueOf("Number of requests: " + requestCounter));
        boolean connected = false;
        Document doc = null;
        boolean thereWasAnError = false;
        int attempt = 1;
        Proxy proxyToBeUsed = null;
        while (!connected) {
            proxyToBeUsed = addConnection();

            try {
                if (thereWasAnError) {
                    guiLabels.setConnectionOutput("Attempt " + attempt + ": Failed to connect to Proxy, trying with a different one...");
                    attempt++;
                } else {
                    guiLabels.setConnectionOutput("Connecting to Proxy...");
                }
                doc = Jsoup.connect(url).proxy(proxyToBeUsed.getProxy(), proxyToBeUsed.getPort()).userAgent("Mozilla").get();
                if (doc.text().contains("Sorry, we can't verify that you're not a robot")) {
                   // guiLabels.setConnectionOutput("Google flagged this proxy as a bot. Changing to a different one");
                    throw new IllegalArgumentException();
                }
                connected = true;
                mapThreadIdToProxy.put(Thread.currentThread().getId(), proxyToBeUsed);

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
    int getPDFs(int limit) throws Exception {
        //Keeps track of the number of searches done
        int numberOfSearches = 0;
        guiLabels.setOutput("Downloading...");
        int pdfCounter = 0;
        //Go though all links
        ArrayList<String> list = getAllLinks();
        if (list.isEmpty()) {
            throw new IllegalArgumentException("Please search of an author before downloading");
        }
        for (String currUrl : list) {
            if (pdfCounter >= limit) {
                guiLabels.setOutput("All PDFs available have been downloaded");
                break;
            }
            timeToWait = getTimeToWait();
            guiLabels.setConnectionOutput("Waiting " + timeToWait + " seconds before going to the search results");
            Thread.sleep(timeToWait * 1000);

            //Increase counter for every new google link
            Document citingPapers;
            if (requestCounter >= 100) {
                guiLabels.setConnectionOutput("Wait... Changing proxy because of amount of requests...");
                citingPapers = changeIP(currUrl, false, false);
            } else {
                citingPapers = changeIP(currUrl, true, false);
            }

            if (citingPapers.text().contains("Sorry, we can't verify that you're not a robot")) {
                //In case you been flagged as a bot even before searching
                guiLabels.setConnectionOutput("Google flagged this proxy as a bot.\nChanging to a different one");
                citingPapers = changeIP(currUrl, false, false);
            }

            requestCounter++;
            numberOfSearches++;

            //If 20 searches are made and no valid result is found, stop
            if (numberOfSearches == 20) {
                guiLabels.setConnectionOutput("No more papers found.");
                guiLabels.setOutput("No more papers found.");
                guiLabels.setLoadBar(limit / (double) limit);
                break;
            }

            guiLabels.setConnectionOutput(String.valueOf("Number of requests: " + requestCounter));
            Elements linksInsidePaper = citingPapers.select("a[href]");
            String text;
            String absLink;
            for (Element link : linksInsidePaper) {
                text = link.text();
                absLink = link.attr("abs:href");
                if (text.contains("PDF")) {
                    System.out.println(text);

                    pdfCounter++;
                    try {
                        pdfDownloader.downloadPDF(absLink, pdfCounter, guiLabels, ipAndPort);

                        File file = new File("./DownloadedPDFs/"+pdfDownloader.getPath() +"/"+ pdfCounter + ".pdf");
                        if (file.length() == 0 || !file.canRead()) {
                            throw new IOException("File is invalid");
                        }
                        numberOfSearches = 0;

                    } catch (IOException e2) {
                        System.out.println(e2.getMessage());
                        guiLabels.setConnectionOutput("This file could not be downloaded, skipping...");
                        pdfCounter--;
                    }

                    guiLabels.setNumberOfPDF(pdfCounter +"/"+limit);
                    guiLabels.setLoadBar(pdfCounter / (double) limit);

                    if (pdfCounter >= limit) {
                        break;
                    }
                }

            }
        }
        return pdfCounter;
    }


    private synchronized Proxy addConnection() {
        //Makes sure that the a connection is only modified by one thread at a time to avoid race conditions

        int randomIndex = new Random().nextInt(listOfProxiesGathered.size());
        Proxy curr = listOfProxiesGathered.get(randomIndex);
        //If it is still in the set,  it has not yet been used.
        while (!setOfProxyGathered.contains(curr)) {
            randomIndex = new Random().nextInt(listOfProxiesGathered.size());
            curr = listOfProxiesGathered.get(randomIndex);
        }
        //Remove from set
        setOfProxyGathered.remove(curr);
        //Remove from list
        listOfProxiesGathered.remove(randomIndex);
        guiLabels.setConnectionOutput("Number of Proxies available: " + setOfProxyGathered.size());

        return curr;
    }


    /**
     * Generates a random time to wait before performing a task (10-20 seconds)
     *
     * @return int that represents seconds
     */
    private int getTimeToWait() {
        if (listOfTimes == null) {
            listOfTimes = new Integer[10];
            for (int i = 0; i < listOfTimes.length; i++) {
                listOfTimes[i] = i + 10;
            }
        }
        int rnd = new Random().nextInt(listOfTimes.length);
        this.timeToWait = listOfTimes[rnd];
        return timeToWait;
    }



    /**
     * Implements Callable. Assigns a task to a thread.
     * Find new working proxy and adds it to the queue.
     */
     class Request implements Callable<Proxy> {
        private boolean atRuntime = false;

        Request() {
        }

        Request(boolean atRuntime) {
            this.atRuntime = atRuntime;
        }

        @Override
        public Proxy call() throws Exception {
            guiLabels.setConnectionOutput(Thread.currentThread().getId() + " is trying to connect");
            // Try to Establish connection
            boolean valid = false;
            while (!valid) {
                try {
                    changeIP("https://scholar.google.com/scholar?hl=en&q=interesting+articles&btnG=&as_sdt=1%2C39&as_sdtp=", false, true);
                    valid = true;
                } catch (Exception ignored) {
                }
            }
            if (atRuntime) {
                //If it is at runtime, add it to the queue from here
                Proxy temp = mapThreadIdToProxy.get(Thread.currentThread().getId());
                queueOfConnections.add(temp);
                guiLabels.setNumberOfWorkingIPs("add," + temp.getProxy()+" Port: "+temp.getPort());
            }
            return mapThreadIdToProxy.get(Thread.currentThread().getId());
        }
    }



}

