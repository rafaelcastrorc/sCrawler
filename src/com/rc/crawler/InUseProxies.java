package com.rc.crawler;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Keeps track of all the proxies that are currently in use
 */
class InUseProxies {
    private static InUseProxies inUseProxies;
    //All the proxies that work for GS (retrieved by establishing new connections method)
    private static Set<Proxy> workingProxiesForGS = Collections.synchronizedSet(new HashSet<Proxy>());
    //Holds All the proxies that work for the current instance
    private static Set<Proxy> currentlyUsedProxies = Collections.synchronizedSet(new HashSet<Proxy>());
    //All the proxies currently being used by a search thread
    private static Set<Proxy> proxiesUsedForSearching = Collections.synchronizedSet(new HashSet<Proxy>());
    //All the proxies currently being used by a download thread
    private static Set<Proxy> proxiesUsedForDownloading = Collections.synchronizedSet(new HashSet<Proxy>());
    private boolean isThreadGettingMoreProxies = false;
    //Keeps track of the number of requests made to get more proxies
    private AtomicCounter counterOfRequests = new AtomicCounter();

    private InUseProxies() {

    }

    static InUseProxies getInstance() {
        if (inUseProxies == null) {
            inUseProxies = new InUseProxies();
        }
        return inUseProxies;

    }

    /**
     * Checks if a thread is currently getting more proxies
     * @return boolean
     */
    synchronized boolean isThreadGettingMoreProxies() {
        return isThreadGettingMoreProxies;
    }

    synchronized void setIsThreadGettingMoreProxies(boolean isThreadGettingMoreProxies) {
        this.isThreadGettingMoreProxies = isThreadGettingMoreProxies;
    }

    /**
     * Verifies if a thread has already connected to this proxy. If add is true and it already contains it, it throws
     * an error
     */
    void hasCrawlerConnectedToProxy(Proxy p, boolean add) throws IllegalArgumentException{
        if (workingProxiesForGS.contains(p)) {
            throw new IllegalArgumentException("Proxy is already in use");
        } else {
            if (add) {
                workingProxiesForGS.add(p);
                currentlyUsedProxies.add(p);
            }
        }
    }

    /**
     * Removes a blocked proxy from the current used proxies (Only for Google Scholar)
     */
    void removeGSProxy(Proxy p) {
        workingProxiesForGS.remove(p);
    }

    /**
     * Returns all the working proxies that this instance has
     */
    Set<Proxy> getCurrentlyUsedProxies() {
        return currentlyUsedProxies;
    }

    /**
     * Removes a blocked proxy from the current used proxies
     */
    void removeProxy(Proxy p) {
        currentlyUsedProxies.remove(p);
    }


    /**
     * Adds a proxy that is currently being used by a thread to search
     */
    void addProxyUsedToSearch(Proxy p) {
        if (proxiesUsedForSearching.contains(p)) {
            throw new IllegalArgumentException("Proxy is already in use");
        } else {
            proxiesUsedForSearching.add(p);
        }
    }

    /**
     * Removes a proxy that is no longer being used to search
     */
    void releaseProxyUsedToSearch(Proxy p) {
        proxiesUsedForSearching.remove(p);
    }


    /**
     * Checks if the current proxy is already been used for searching
     * @return Boolean
     */
    boolean isProxyInUseForSearching(Proxy proxyToUse) {
        return proxiesUsedForSearching.contains(proxyToUse);
    }


    /**
     * Adds a proxy that is currently being used by a thread to download
     */
    void addProxyUsedToDownload(Proxy p) throws IllegalArgumentException{
        if (proxiesUsedForDownloading.contains(p)) {
            throw new IllegalArgumentException("Proxy is already in use");
        } else {
            proxiesUsedForDownloading.add(p);
        }
    }

    /**
     * Removes a proxy that is no longer being used to download
     */
    void releaseProxyUsedToDownload(Proxy p) {
        proxiesUsedForDownloading.remove(p);
    }


    /**
     * Checks if the current proxy is already been used for downloading
     * @return Boolean
     */
    boolean isProxyInUseForDownloading(Proxy proxyToUse) {
        return proxiesUsedForDownloading.contains(proxyToUse);
    }

    /**
     * Increases the counter of requests to get a new working proxy
     */
    void newRequestToGetProxy() {
        counterOfRequests.increment();
    }

    /**
     * Decreases the counter of requests to get a new working proxy
     */
    void requestHasBeenCompleted() {
        counterOfRequests.decrease();
    }


    AtomicCounter getCounterOfRequestsToGetNewProxies() {
        return counterOfRequests;
    }


}



