package com.rc.crawler;

import org.openqa.selenium.Cookie;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
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
    private Map<Proxy, Set<Cookie>> mapProxyToCookie = Collections.synchronizedMap(new HashMap<Proxy, Set<Cookie>>());

    /**
     * Loads any previously stored cookied
     */
    JavascriptEnabledCrawler() {
        Logger logger = Logger.getInstance();
        try {
            mapProxyToCookie = logger.readCookieFile();
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

    Map<Proxy, Set<Cookie>> getMapProxyToCookie() {
        return mapProxyToCookie;
    }

    void setMapProxyToCookie(Map<Proxy, Set<Cookie>> mapProxyToCookie) {
        this.mapProxyToCookie = mapProxyToCookie;
    }
}
