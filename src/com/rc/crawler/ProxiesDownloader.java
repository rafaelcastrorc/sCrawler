package com.rc.crawler;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by rafaelcastro on 6/15/17.
 * Finds and downloads proxies from different websites
 */
class ProxiesDownloader {
    private Integer[] listOfTimes;
    private Integer timeToWait;
    private ArrayList<String> listOfUnusedLinks = new ArrayList<>();
    private ArrayList<String> proxiesLists = new ArrayList<>();


    /**
     * Returns a list of websites that compiles proxies
     * @param addMore true if we need to add more proxies (It adds them from the links we did not use before)
     */
    private void getProxiesList(boolean addMore) {
        if (!addMore) {
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
            for (int i = 0; i < 300; i = i + 15) {
                proxiesLists.add("http://proxydb.net/?offset=" + i);
            }
            listOfUnusedLinks.addAll(proxiesLists);
        } else {
            //If  we need to add more proxies, then we use the links that we did not use before.
            proxiesLists.addAll(listOfUnusedLinks);
        }
    }


    /**
     * Downloads proxies from different websites, without duplicates.
     *
     * @param numberOfProxiesToDownload Limit of proxies to download
     * @param proxyCounter              The number of proxies that have been downloaded so far.
     * @param guiLabels                 GUILabelManagement obj
     * @param crawler                   crawler reference
     * @param addMore                   true if the program is trying to add more proxies.
     */
    @SuppressWarnings("Duplicates")
    synchronized int getProxiesFromWebsite(int numberOfProxiesToDownload, int proxyCounter, GUILabelManagement guiLabels, Crawler crawler, boolean addMore) {

        //Set a new file, if there was one before, overwrite it
        Logger logger = Logger.getInstance();
        int proxyCounter1 = proxyCounter;
        try {
            if (proxyCounter1 == 0) {
                //If there were no proxies before, we start a new file
                logger.setListOfProxies(false);
                DateTimeFormatter formatter = DateTimeFormat.forPattern("dd/MM/yyyy HH:mm:ss");
                //Write current date
                DateTime now = new DateTime();
                logger.writeToListOfProxies(now.toString(formatter));
                //Create a new file of working proxies

            } else {
                //If there were proxies in the file, then just append
                logger.setListOfProxies(true);
            }

            if (!addMore) {
                guiLabels.setOutput("Starting to download Proxies...");
            } else {
                guiLabels.setConnectionOutput("Starting to download more Proxies...");
            }

            Document doc;
            //Get the list of websites that compile proxies
            getProxiesList(addMore);
            //Iterate over all websites
            if (proxiesLists.isEmpty()) {
                //If this happens, it means that there were no unused links, so we search through all the websites again
                getProxiesList(false);
            }
            for (String url : proxiesLists) {
                listOfUnusedLinks.remove(url);

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

                                    if (!crawler.getSetOfAllProxiesEver().contains(nProxy)) {
                                        //See if the set of all proxies does not contain this one, if it does not, then we can add it
                                        logger.writeToListOfProxies("\n" + nProxy.getProxy() + "," + nProxy.getPort());
                                        crawler.setSetOfProxyGathered(nProxy);
                                        crawler.setListOfProxiesGathered(nProxy);
                                        crawler.addToSetOfAllProxiesEver(nProxy);
                                        //Increase counter
                                        proxyCounter1++;
                                        if (proxyCounter1 == numberOfProxiesToDownload) {
                                            //Once we have enough proxies, stop
                                            return proxyCounter1;
                                        }
                                    }
                                } else {
                                    if (found) {
                                        //Get Port number
                                        String portNum = elt.toString();
                                        portNum = portNum.replaceAll("</?td>", "");
                                        array[1] = portNum;
                                        Proxy nProxy = new Proxy(array[0], Integer.valueOf(array[1]));
                                        //add as long as it is not already in the set
                                        if (!crawler.getSetOfAllProxiesEver().contains(nProxy)) {
                                            logger.writeToListOfProxies("\n" + nProxy.getProxy() + "," + nProxy.getPort());
                                            crawler.setSetOfProxyGathered(nProxy);
                                            crawler.setListOfProxiesGathered(nProxy);
                                            crawler.addToSetOfAllProxiesEver(nProxy);

                                            proxyCounter1++;
                                            if (proxyCounter1 == numberOfProxiesToDownload) {
                                                //Once we have enough proxies, stop
                                                return proxyCounter1;
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
                        Double d = (proxyCounter1 / (double) numberOfProxiesToDownload) * 0.7;
                        if (!addMore) {
                            guiLabels.setLoadBar(d);
                            guiLabels.setOutput("Proxies downloaded: " + proxyCounter1 + "/" + numberOfProxiesToDownload);
                        } else {
                            guiLabels.setConnectionOutput("Proxies downloaded: " + proxyCounter1 + "/" + numberOfProxiesToDownload);
                        }

                    } catch (IOException e) {
                        guiLabels.setAlertPopUp("There was a problem one of the Proxy Databases. \nPlease make sure you have an internet connection.");
                    } catch (InterruptedException e) {
                        guiLabels.setConnectionOutput(e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            guiLabels.setAlertPopUp(e.getMessage());
        }
        return proxyCounter1;
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

}

