package com.rc.crawler;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Keeps track of all the proxies that are currently in use
 */
class InUseProxies {
    private static InUseProxies inUseProxies;
    private static Set<Proxy> currentlyUsedProxies = Collections.synchronizedSet(new HashSet<Proxy>());

    private InUseProxies() {

    }

    static InUseProxies getInstance() {
        if (inUseProxies == null) {
            inUseProxies = new InUseProxies();
        }
        return inUseProxies;

    }

    /**
     * Verifies if current proxy is currently been used. If add is true and it already contains it, it throws an error
     */
    void isProxyInUse(Proxy p, boolean add) {
        if (currentlyUsedProxies.contains(p)) {
            throw new IllegalArgumentException("Proxy is already in use");
        }
        else {
            if (add) {
                currentlyUsedProxies.add(p);
            }
        }
    }

    /**
     * Adds a proxy from the current used proxies
     */
    void addProxy(Proxy p) {
        currentlyUsedProxies.add(p);
    }

    /**
     * Removes a proxy from the current used proxies
     */
    void removeProxy(Proxy p) {
        currentlyUsedProxies.remove(p);
    }
}
