package com.rc.crawler;

import org.joda.time.DateTime;
import org.openqa.selenium.Cookie;

import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Created by rafaelcastro on 9/7/17.
 * Handles which outside server is the instance going to use. So either the SCrawler W website or user's own database
 */

class OutsideServer {
    private static OutsideServer outsideServer;
    private static boolean usesScrawlerW = false;
    private static SCrawlerWeb scrawlerWeb;
    private static DatabaseDriver databaseDriver;


    private OutsideServer() {
    }


    static OutsideServer getInstance(GUILabelManagement guiLabels) {
        if (outsideServer == null) {
            //If sCrawler W works
            if (checkIfWebsiteWorks()) {
                //Start SCrawlerW class, and let the user decide if he wants to use it
                scrawlerWeb = SCrawlerWeb.getInstance(guiLabels);
                //If null, it means that the user does not want to use it
                if (scrawlerWeb == null) {
                    usesScrawlerW = false;
                    //Use DatabaseDriver instead for them to configure a new db
                    databaseDriver = DatabaseDriver.getInstance(guiLabels);
                } else {
                    usesScrawlerW = true;
                }
            } else {
                usesScrawlerW = false;
                databaseDriver = DatabaseDriver.getInstance(guiLabels);
            }
            outsideServer = new OutsideServer();
        }
        return outsideServer;

    }

    /**
     * Verfies if sCrawlerW website works, if not, then just use the DatabaseDriver
     */
    private static boolean checkIfWebsiteWorks() {
        try {
            URL url = new URL(SCrawlerWeb.SCRAWLERWURL);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.connect();
            if (con.getResponseCode() == 200) {
                return true;
            } else {
                throw new IllegalArgumentException();
            }
        } catch (Exception ignored) {
        }
        return false;
    }


    /**
     * Removes the previous name associated to this instance
     *
     * @param prevName String
     */
    void removeCrawlerInstance(String prevName) {
        if (usesScrawlerW) {
            scrawlerWeb.removeCrawlerInstance(prevName);
        } else {
            databaseDriver.removeCrawlerInstance(prevName);
        }
    }


    /**
     * Adds the current download rate to the db
     */
    void addDownloadRateToDB(Double rate2, Double rate, int i) {
        if (usesScrawlerW) {
            scrawlerWeb.addDownloadRateToDB(rate2, rate, i);
        } else {
            databaseDriver.addDownloadRateToDB(rate2, rate, i);
        }
    }

    /**
     * Adds an error to the db
     *
     * @param message String
     */
    void addError(String message) {
        if (usesScrawlerW) {
            scrawlerWeb.addError(message);
        } else {
            databaseDriver.addError(message);
        }
    }

    /**
     * Adds the current instance ot the db
     */
    void addCrawlerInstance() {
        if (usesScrawlerW) {
            scrawlerWeb.addCrawlerInstance();
        } else {
            databaseDriver.addCrawlerInstance();
        }
    }

    boolean canUseProxy(Proxy proxyToBeUsed) {
        if (usesScrawlerW) {
            return scrawlerWeb.canUseProxy(proxyToBeUsed);
        } else {
            return databaseDriver.canUseProxy(proxyToBeUsed);
        }
    }


    /**
     * Retrieves the latest sCrawler version
     *
     * @return Map
     */
    Map.Entry<String, String> getLatestVersion() {
        if (usesScrawlerW) {
            return scrawlerWeb.getLatestVersion();
        } else {
            return databaseDriver.getLatestVersion();
        }
    }

    /**
     * In case the connection is closed
     */
    void reconnect() {
        if (usesScrawlerW) {
            scrawlerWeb.reconnect();
        } else {
            databaseDriver.reconnect();
        }

    }

    /**
     * Returns the cookies for a given proxy
     *
     * @return Set
     */
    Set<Cookie> getCookies(Proxy proxy, SearchEngine.SupportedSearchEngine engine) {
        if (usesScrawlerW) {
            return scrawlerWeb.getCookies(proxy, engine);
        } else {
            return databaseDriver.getCookies(proxy, engine);
        }

    }

    /***
     * Returns all the unlocked proxies
     * @return HashMap
     */
    HashMap<Proxy, Map<SearchEngine.SupportedSearchEngine, Set<Cookie>>> getAllUnlockedProxies() {
        if (usesScrawlerW) {
            return scrawlerWeb.getAllUnlockedProxies();
        } else {
            return databaseDriver.getAllUnlockedProxies();
        }
    }

    /**
     * Retrieves all the proxy compiling websites
     *
     * @return HashMap
     */
    HashMap<String, DateTime> getAllWebsites() {
        if (usesScrawlerW) {
            return scrawlerWeb.getAllWebsites();
        } else {
            return databaseDriver.getAllWebsites();
        }
    }

    /**
     * Sets the number of proxies found ina given site
     *
     * @param proxiesFoundInThisSite Number of proxies
     * @param url                    URL string of the site
     */
    void setNumberOfProxiesFound(int proxiesFoundInThisSite, String url) {
        if (usesScrawlerW) {
            scrawlerWeb.setNumberOfProxiesFound(proxiesFoundInThisSite, url);
        } else {
            databaseDriver.setNumberOfProxiesFound(proxiesFoundInThisSite, url);
        }
    }

    /**
     * Updates the time a website was visited
     */
    void updateWebsiteTime(String url) {
        if (usesScrawlerW) {
            scrawlerWeb.updateWebsiteTime(url);
        } else {
            databaseDriver.updateWebsiteTime(url);
        }

    }

    /**
     * Returns all the proxies compiled from the list of proxies
     *
     * @return Set of proxies
     */
    HashSet<Proxy> getAllProxiesFromListOfProxies() {
        if (usesScrawlerW) {
            return scrawlerWeb.getAllProxiesFromListOfProxies();
        } else {
            return databaseDriver.getAllProxiesFromListOfProxies();
        }
    }

    /**
     * Deletes a proxy from the list of proxies table
     *
     * @param proxy Proxy obj to delete
     */
    void deleteProxyFromListOfProxies(Proxy proxy) {
        if (usesScrawlerW) {
            scrawlerWeb.deleteProxyFromListOfProxies(proxy);
        } else {
            databaseDriver.deleteProxyFromListOfProxies(proxy);
        }
    }

    /**
     * Adds a proxy to the list of proxies
     *
     * @param proxy Proxy obj to add
     */
    void addProxyToListOfProxies(Proxy proxy) {
        if (usesScrawlerW) {
            scrawlerWeb.addProxyToListOfProxies(proxy);
        } else {
            databaseDriver.addProxyToListOfProxies(proxy);
        }
    }

    /**
     * Adds a proxy to the current instance
     */
    void addProxyToCurrentInstance(Proxy proxy) {
        if (usesScrawlerW) {
            scrawlerWeb.addProxyToCurrentInstance(proxy);
        } else {
            databaseDriver.addProxyToCurrentInstance(proxy);
        }
    }

    /**
     * Checks if the current instance is already using the proxy
     *
     * @param proxyToBeUsed Proxy
     * @return boolean, true if it is already being used, false otherwise
     */
    boolean isCurrentInstanceUsingProxy(Proxy proxyToBeUsed) {
        if (usesScrawlerW) {
            return scrawlerWeb.isCurrentInstanceUsingProxy(proxyToBeUsed);
        } else {
            return databaseDriver.isCurrentInstanceUsingProxy(proxyToBeUsed);
        }
    }

    /**
     * Adds an unlocked proxy to the proxies tables
     *
     * @param proxy   Proxy to add
     * @param cookies Cookie string associated to this proxy
     * @param engine  Search_Engine used
     * @param stats   - Stats Obj
     */
    void addUnlockedProxy(Proxy proxy, String cookies, SearchEngine.SupportedSearchEngine engine, StatsGUI stats) {
        if (usesScrawlerW) {
            scrawlerWeb.addUnlockedProxy(proxy, cookies, engine, stats);
        } else {
            databaseDriver.addUnlockedProxy(proxy, cookies, engine, stats);
        }
    }

    /**
     * Marks a proxy as locked in the db
     */
    void addLockedProxy(Proxy proxyToUse) {
        if (usesScrawlerW) {
            scrawlerWeb.addLockedProxy(proxyToUse);
        } else {
            databaseDriver.addLockedProxy(proxyToUse);
        }
    }

    /**
     * Returns the number of times a proxy has failed to load
     *
     * @return int
     */
    int getFailureToLoad(Proxy proxyToUse) {
        if (usesScrawlerW) {
            return scrawlerWeb.getFailureToLoad(proxyToUse);
        } else {
            return databaseDriver.getFailureToLoad(proxyToUse);
        }
    }

    /**
     * Increase the # of failures in a proxy
     */
    void addFailureToLoad(Proxy proxyToUse) {
        if (usesScrawlerW) {
            scrawlerWeb.addFailureToLoad(proxyToUse);
        } else {
            databaseDriver.addFailureToLoad(proxyToUse);
        }
    }

    /**
     * Retrieves the operation that this instance is supposed to perform
     *
     * @return String with the operation
     */
    String getOperationToPerform() {
        if (usesScrawlerW) {
            return scrawlerWeb.getOperationToPerform();
        } else {
            return databaseDriver.getOperationToPerform();
        }
    }

    /**
     * Returns the last time the server was mantained
     *
     * @return DateTime
     */
    DateTime getLastMaintenanceTime() {
        if (usesScrawlerW) {
            return scrawlerWeb.getLastMaintenanceTime();
        } else {
            return databaseDriver.getLastMaintenanceTime();
        }
    }

    String getInstanceName() {
        if (usesScrawlerW) {
            return scrawlerWeb.getInstanceName();
        } else {
            return databaseDriver.getInstanceName();
        }
    }

    /**
     * Closes the connection if we are using a database directly
     */
    void closeConnection() throws SQLException {
        if (!usesScrawlerW) {
            databaseDriver.closeConnection();
        }
    }

    /**
     * Cleans the proxies table if we are using a database directly
     */
    void cleanProxiesTable() {
        if (!usesScrawlerW) {
            databaseDriver.cleanProxiesTable();
        }
    }

    /**
     * Updates the last maintenance time if we are using a database directly
     */
    void updateMaintenanceTime() {
        if (!usesScrawlerW) {
            databaseDriver.updateMaintenanceTime();
        }
    }

    /**
     * Performs  an operation in an instance if we are using a database directly
     */
    void performOperation(String instanceName, WebServer.SupportedOperations operation) {
        if (!usesScrawlerW) {
            databaseDriver.performOperation(instanceName, operation);
        }
    }

    /**
     * Returns all instances in the server if we are using a database directly
     * @return HashSet with all the instances
     */
     HashSet<WebServer.ScrawlerInstance> getAllInstances() {
        if (!usesScrawlerW) {
            return databaseDriver.getAllInstances();
        } else return new HashSet<>();
    }
}


