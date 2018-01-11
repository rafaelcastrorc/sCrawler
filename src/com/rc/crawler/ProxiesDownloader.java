package com.rc.crawler;

import org.joda.time.DateTime;
import org.jsoup.HttpStatusException;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.net.URL;
import java.sql.SQLException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by rafaelcastro on 6/15/17.
 * Finds and downloads proxies from different websites
 */
class ProxiesDownloader {
    private Integer[] listOfTimes;
    private Integer timeToWait;
    private ArrayList<String> websiteList = new ArrayList<>();
    private ArrayList<String> proxiesLists = new ArrayList<>();
    private int proxyCounter1;
    private boolean found;
    private String[] array;
    private boolean mainPage;
    private GUILabelManagement guiLabels;

    ProxiesDownloader(GUILabelManagement guiLabels) {
        this.guiLabels = guiLabels;
    }

    ProxiesDownloader() {
    }


    /**
     * Returns a list of websites that compiles proxies
     */
    private void getProxiesList() {
        //Get all the websites from the database
        HashMap<String, DateTime> map = DatabaseDriver.getInstance(guiLabels).getAllWebsites();
        //If the timeStamp is null, or its been more than 6 hours since a crawler visited the site, we can use it
        for (String website : map.keySet()) {
            if (map.get(website) == null) {
                websiteList.add(website);
            } else {
                DateTime now = new DateTime();
                //Only get the websites that we visited at least 6 hours ago, not less!
                if (now.getMillis() - map.get(website).getMillis() > 360 * 60 * 1000) {
                    websiteList.add(website);
                }
            }
        }
    }


    /**
     * Downloads proxies from different websites, without duplicates.
     *
     * @param numberOfProxiesToDownload Limit of proxies to download
     * @param proxyCounter              The number of proxies that have been downloaded so far.
     * @param crawler                   crawler reference
     * @param addMore                   true if the program is trying to add more proxies.
     * @param stats
     */
    synchronized int getProxiesFromWebsite(int numberOfProxiesToDownload, int proxyCounter, Crawler crawler, boolean
            addMore, StatsGUI stats) {

        //Set a new file, if there was one before, overwrite it
        Logger logger = Logger.getInstance();
        this.proxyCounter1 = proxyCounter;
        try {
            Document doc;
            websiteList = new ArrayList<>();
            //Get the list of websites that compile proxies that we can use
            getProxiesList();

            //If there are no websites that we can explore because we have already visited all in them last 6 hours,
            // then just reset all lists.
            if (websiteList.size() ==0) {
                crawler.resetProxyTracking();
            }

            //Iterate over all websites
            for (String url : websiteList) {
                //If we have enough proxies, we stop
                if (proxyCounter1 >= numberOfProxiesToDownload) {
                    break;
                }
                //Mark website as visited in the db
                DatabaseDriver.getInstance(guiLabels).updateWebsiteTime(url);

                //Get Base URI
                URL urlObj = new URL(url);
                String baseURI = urlObj.getProtocol() + "://" + urlObj.getHost();


                mainPage = true;
                //Link to possible url inside the table to get more entries
                String absLink = "";
                boolean areThereMoreEntries = true;

                //Go through the url, find all proxies and check if the website has more entries.
                while (areThereMoreEntries) {

                    try {
                        doc = getWebsiteDoc(url, baseURI, crawler, absLink, guiLabels, stats);
                        if (doc == null) {
                            areThereMoreEntries = false;
                            continue;
                        }
                        //Check if its a forum
                        if (url.contains("forum")) {
                            forumParser(doc.toString(), url, crawler, absLink, stats);
                            areThereMoreEntries = false;
                            continue;
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
                        Pattern ips = Pattern.compile("\\b(?:(?:25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9]?[0-9])\\.){3}" +
                                "(?:25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9]?[0-9])\\b");
                        Pattern ipAndPort = Pattern.compile("\\b(?:(?:25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9]?[0-9])\\" +
                                ".){3}(?:25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9]?[0-9])\\b:\\d{2,4}");
                        for (int i = 1; i < rows.size(); i++) { //first row is the col names so skip it.
                            Element row = rows.get(i);
                            Elements cols = row.select("td");
                            found = false;
                            array = new String[2];
                            for (Element elt : cols) {
                                getProxiesFromWebsiteHelper(elt, crawler, logger, ips, ipAndPort,
                                        numberOfProxiesToDownload);
                            }
                        }
                        Double d = (proxyCounter1 / (double) numberOfProxiesToDownload) * 0.7;
                        if (!addMore) {
                            guiLabels.setLoadBar(d);
                            guiLabels.setOutput("Proxies downloaded: " + proxyCounter1 + "/" +
                                    numberOfProxiesToDownload);
                        } else {
                            guiLabels.setConnectionOutput("Proxies downloaded: " + proxyCounter1 + "/" +
                                    numberOfProxiesToDownload);
                        }

                    } catch (HttpStatusException ignored) {
                    } catch (Exception e) {
                        if (e.getMessage().contains("Network is unreachable")) {
                            //Check if there is internet connection
                            while (!crawler.isThereConnection()) {
                                try {
                                    guiLabels.setOutput("No internet connection");
                                    guiLabels.setOutputMultiple("No internet connection");
                                    //Sleep for 30 seconds, try until connection is found
                                    Thread.sleep(10 * 1000);
                                } catch (InterruptedException ignored) {
                                }
                            }
                        }
                    }
                }
            }
            //Once it is done downloading from all the websites that can be used, add all the proxies that currently
            // exist in the db (To avoid having the same proxies in every instance)
            getAllProxiesFromDB(crawler);

        } catch (IOException e) {
            e.printStackTrace(System.out);
            guiLabels.setAlertPopUp(e.getMessage());
        }
        //Get all the currently available proxies (those that are less than 24 hours old)
        return proxyCounter1;
    }

    /**
     * Gets all the proxies from the database, and delete those that are older than 24 hours
     */
    private void getAllProxiesFromDB(Crawler crawler) {
        HashSet<Proxy> proxies = DatabaseDriver.getInstance(guiLabels).getAllProxiesFromListOfProxies();
        DateTime now = new DateTime();
        for (Proxy proxy : proxies) {
            //Check if it less than 24 hours
            if (now.getMillis() - proxy.getTime().getMillis() <= 24 * 60 * 60 * 1000) {
                if (!crawler.getSetOfAllProxiesEver().contains(proxy)) {
                    crawler.setSetOfProxyGathered(proxy);
                    crawler.setListOfProxiesGathered(proxy);
                    crawler.addToSetOfAllProxiesEver(proxy);
                    proxyCounter1++;
                }
            } else {
                //If it is older than 24h, delete it
                DatabaseDriver.getInstance(guiLabels).deleteProxyFromListOfProxies(proxy);
            }
        }

    }

    /**
     * Retrieve Document from website to parse
     *
     * @param url       URL to use
     * @param baseURI   BaseURI of the current url
     * @param crawler   Crawler instance
     * @param absLink   Absolute link of the current url
     * @param guiLabels GuiLabelManagement obj
     * @param stats
     * @return Document
     */
    private Document getWebsiteDoc(String url, String baseURI, Crawler crawler, String absLink, GUILabelManagement
            guiLabels, StatsGUI stats) throws SQLException {
        Document doc;
        ProxyChanger proxyChanger = new ProxyChanger(guiLabels, crawler, SearchEngine.SupportedSearchEngine
                .GoogleScholar, stats);
        try {

            if (mainPage && !url.contains("http://proxydb.net/?offset=")) {
                doc = proxyChanger.useSelenium(null, url, false, null, false);
                mainPage = false;
            } else {
                //Sleep random periods before requesting info from website
                timeToWait = getTimeToWait();
                Thread.sleep((timeToWait + 6) * 1000);
                doc = proxyChanger.useSelenium(null, baseURI + absLink, false, null, false);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

        if (doc.toString().contains("javascript") && (url.contains("gatherproxy") || url.contains
                ("freeproxylists"))) {

            if (crawler.isSeleniumActive()) {
                try {
                    doc = proxyChanger.useSelenium(null, url, false, null, false);
                } catch (RuntimeException e) {
                    e.printStackTrace(System.out);
                    return null;
                }
            }
        }
        return doc;
    }

    @SuppressWarnings("Duplicates")
    private int getProxiesFromWebsiteHelper(Element elt, Crawler crawler, Logger logger, Pattern ips, Pattern
            ipAndPort, int numberOfProxiesToDownload) throws IOException {

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

            if (!crawler.getSetOfAllProxiesEver().contains(nProxy)) {
                //Add it to db
                try {
                    DatabaseDriver.getInstance(guiLabels).addProxyToListOfProxies(nProxy);
                } catch (IllegalArgumentException e) {
                    return proxyCounter1;
                }
                //See if the set of all proxies does not contain this one, if it does not,
                // then we can add it
                crawler.setSetOfProxyGathered(nProxy);
                crawler.setListOfProxiesGathered(nProxy);
                crawler.addToSetOfAllProxiesEver(nProxy);

                //Increase counter
                proxyCounter1++;
            }
        } else {
            if (found) {
                //Get Port number
                String portNum = elt.toString();
                portNum = portNum.replaceAll("</?td>", "");
                if (portNum.length() > 5) {
                    Pattern portPattern = Pattern.compile("\\d{2,5}");
                    Matcher portMatcher = portPattern.matcher(portNum);
                    if (portMatcher.find()) {
                        portNum = portMatcher.group();
                    }
                }
                array[1] = portNum;
                Proxy nProxy = new Proxy(array[0], Integer.valueOf(array[1]));
                //add as long as it is not already in the set
                if (!crawler.getSetOfAllProxiesEver().contains(nProxy)) {
                    try {
                        DatabaseDriver.getInstance(guiLabels).addProxyToListOfProxies(nProxy);
                    } catch (IllegalArgumentException e) {
                        return proxyCounter1;
                    }
                    crawler.setSetOfProxyGathered(nProxy);
                    crawler.setListOfProxiesGathered(nProxy);
                    crawler.addToSetOfAllProxiesEver(nProxy);

                    proxyCounter1++;
                }
                array = new String[2];
                found = false;
            } else if (matcher.find()) {
                //If an Ip is found, then the next element is the port number
                found = true;
                array[0] = matcher.group();
            }
        }
        return numberOfProxiesToDownload;
    }

    /**
     * Parses a website that is a forum
     */
    private void forumParser(String doc, String baseURI, Crawler crawler, String absLink, StatsGUI stats) {
        int numOfThreadsVisited = 0;
        Pattern pattern = Pattern.compile("(div class=\"listBlock main)([^∞])+?(?=(div class=\"listBlock))");
        Matcher matcher = pattern.matcher(doc);
        ArrayList<String> urlsToVisit = new ArrayList<>();
        while (matcher.find()) {
            //Only get the link for 4 threads.
            if (numOfThreadsVisited > 3) {
                break;
            }
            String thread = matcher.group();
            //We do not care about this ones
            if (thread.contains("pleaseread")) {
                continue;
            }
            Pattern linkPattern = Pattern.compile("(href=\")([^∞])+?(?=(\"))");
            Matcher linkMatcher = linkPattern.matcher(thread);
            String url = null;
            //Get the last url because it contains the link to the last page in the thread
            while (linkMatcher.find()) {
                url = linkMatcher.group();
            }
            if (url!= null) {
                url = url.replaceAll("href=\"","");
                urlsToVisit.add(url);
                numOfThreadsVisited++;
            }
        }
        //Go through each link, and get the last post of each one
        for (String absLink2 : urlsToVisit) {
            try {
                URL urlObj = new URL(baseURI);
                baseURI = urlObj.getProtocol() + "://" + urlObj.getHost()+"/";
                Document newDoc = getWebsiteDoc("", baseURI, crawler, absLink2, guiLabels, stats);
                Pattern postPattern = Pattern.compile("(<li id=\"post)([^∞])+?(?=((<li id=\"post)|(<div " +
                        "class=\"ad_message_below_last\">)))");
                Matcher postMatcher = null;
                if (newDoc != null) {
                    postMatcher = postPattern.matcher(newDoc.toString());
                }
                String post = "";
                while (postMatcher.find()) {
                    post = postMatcher.group();
                }
                //Get all the proxies
                Pattern proxyPattern = Pattern.compile("\\b(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}" +
                        "(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\b:\\d{2,5}");
                Matcher proxyMatcher = proxyPattern.matcher(post);
                while (proxyMatcher.find()) {
                    String proxyStr = proxyMatcher.group();
                    String[] arr = proxyStr.split(":");
                    Proxy proxy = new Proxy(arr[0], Integer.valueOf(arr[1]));
                    if (!crawler.getSetOfAllProxiesEver().contains(proxy)) {
                        try {
                            DatabaseDriver.getInstance(guiLabels).addProxyToListOfProxies(proxy);
                        } catch (IllegalArgumentException ignored) {
                        }
                        crawler.setSetOfProxyGathered(proxy);
                        crawler.setListOfProxiesGathered(proxy);
                        crawler.addToSetOfAllProxiesEver(proxy);

                        proxyCounter1++;
                    }
                }
            } catch (SQLException | NullPointerException | IOException e) {
                e.printStackTrace();
            }
        }


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

    ArrayList<String> getWebsites() {
        proxiesLists.add("https://www.blackhatworld.com/forums/proxy-lists.103/");
        proxiesLists.add("http://www.freeproxylists.net");
        proxiesLists.add("https://nordvpn.com/free-proxy-list/");
        proxiesLists.add("http://www.gatherproxy.com/proxylist/country/?c=United%20States");
        proxiesLists.add("http://www.gatherproxy.com/proxylist/country/?c=Russia");
        proxiesLists.add("http://www.gatherproxy.com/proxylist/country/?c=United%20Kingdom");
        proxiesLists.add("http://www.gatherproxy.com/proxylist/country/?c=Indonesia");
        proxiesLists.add("http://www.gatherproxy.com");
        proxiesLists.add("https://www.socks-proxy.net");
        proxiesLists.add("https://hidester.com/proxylist");
        proxiesLists.add("http://premiumproxy.net/");
        proxiesLists.add("https://premproxy.com/list/");
        proxiesLists.add("https://www.proxio.io/es/");
        //working US only proxy list. All results displayed at once. (< 200)
        proxiesLists.add("https://www.us-proxy.org");
        //Working international proxy list. All results displayed at once. (<300)
        proxiesLists.add("http://www.httptunnel.ge/ProxyListForFree.aspx");
        //Working international proxy list. All results displayed at once. (<150)
        proxiesLists.add("https://www.hidemy.name/en/proxy-list/");
        //Use for backup
        //International list, but it is divided in entries.
        proxiesLists.add("https://www.hide-my-ip.com/proxylist.shtml");
        //This site has over 1000 proxies, but it is divided by entries.
        for (int i = 0; i < 300; i = i + 15) {
            proxiesLists.add("http://proxydb.net/?offset=" + i);
        }
        return proxiesLists;
    }
}

