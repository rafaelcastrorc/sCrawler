package com.rc.crawler;

import org.openqa.selenium.Cookie;

import java.io.FileNotFoundException;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Created by rafaelcastro on 8/18/17.
 * Holds the different objects that are enabled when the crawler can parse Javascript
 */
class JavascriptEnabledCrawler {
    //Is the crawler capable of parsing javascript websites
    private boolean isSeleniumActive = false;
    //Queue of blocked proxies
    private ConcurrentLinkedQueue<Proxy> blockedProxies = new ConcurrentLinkedQueue<>();
    //Maps if a proxy uses selenium or not
    private Map<Proxy, Boolean> mapProxyToSelenium = Collections.synchronizedMap(new HashMap<Proxy, Boolean>());
    //Queue of unlocked proxies
    private ConcurrentLinkedQueue<Proxy> queueOfUnlockedProxies = new ConcurrentLinkedQueue<>();
    //Map of proxy to the cookies of the drive that unlocked it
    private Map<Proxy, Map<SearchEngine.SupportedSearchEngine, Set<Cookie>>> mapProxyToSearchEngineToCookie = Collections.synchronizedMap(new
            HashMap<Proxy, Map<SearchEngine.SupportedSearchEngine, Set<Cookie>>>());

    /**
     * Loads any previously stored cookied
     */
    JavascriptEnabledCrawler(StatsGUI stats, GUILabelManagement guiLabels) throws SQLException {
        Logger logger = Logger.getInstance();
        try {
            mapProxyToSearchEngineToCookie = logger.readCookieFile(guiLabels);
            stats.updateNumberOfUnlocked(mapProxyToSearchEngineToCookie.size());
        } catch (FileNotFoundException ignored) {
        }
    }

    boolean isSeleniumActive() {
        return isSeleniumActive;
    }

    void setSeleniumActive(boolean seleniumActive) {
        isSeleniumActive = seleniumActive;
    }

    ConcurrentLinkedQueue<Proxy> getBlockedProxies() {
        return blockedProxies;
    }

    void setBlockedProxies(ConcurrentLinkedQueue<Proxy> blockedProxies) {
        this.blockedProxies = blockedProxies;
    }

    Map<Proxy, Boolean> getMapProxyToSelenium() {
        return mapProxyToSelenium;
    }

    void setMapProxyToSelenium(Map<Proxy, Boolean> mapProxyToSelenium) {
        this.mapProxyToSelenium = mapProxyToSelenium;
    }

    ConcurrentLinkedQueue<Proxy> getQueueOfUnlockedProxies() {
        return queueOfUnlockedProxies;
    }

    void setQueueOfUnlockedProxies(ConcurrentLinkedQueue<Proxy> queueOfUnlockedProxies) {
        this.queueOfUnlockedProxies = queueOfUnlockedProxies;
    }

    /**
     * Adds cookie to map
     * @param p Proxy
     * @param engine SupportedSearchEngine
     * @param cookies Set of cookies
     */
    void addCookieToMap(Proxy p, SearchEngine.SupportedSearchEngine engine, Set<Cookie> cookies) {
        if (mapProxyToSearchEngineToCookie.containsKey(p)) {
            Map<SearchEngine.SupportedSearchEngine, Set<Cookie>> map = mapProxyToSearchEngineToCookie.get(p);
            map.put(engine, cookies);
            mapProxyToSearchEngineToCookie.put(p, map);
        } else {
            Map<SearchEngine.SupportedSearchEngine, Set<Cookie>> map = new HashMap<>();
            map.put(engine, cookies);
            mapProxyToSearchEngineToCookie.put(p, map);

        }
    }

    /**
     * Retrieves a cookie for a given proxy, based on the search engine being used.
     * @param proxy Proxy
     * @param engine SupportedSearchEngine
     * @return Set<Cookie></Cookie>
     */
    Set<Cookie> getCookie(Proxy proxy, SearchEngine.SupportedSearchEngine engine) {
        if (mapProxyToSearchEngineToCookie.containsKey(proxy)) {
            Map<SearchEngine.SupportedSearchEngine, Set<Cookie>> map = mapProxyToSearchEngineToCookie.get(proxy);
            if (map.containsKey(engine)) {
                return map.get(engine);
            }
        }
        return new HashSet<>();
    }
}
