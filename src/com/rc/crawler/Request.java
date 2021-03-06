package com.rc.crawler;

import java.util.Optional;
import java.util.concurrent.Callable;

/**
 * Assigns a task to a thread.
 * Finds a new working proxy and adds it to the queue.
 */

class Request implements Callable<Proxy> {
    private final String type;
    private Crawler crawler;
    private GUILabelManagement guiLabels;
    private SearchEngine.SupportedSearchEngine engine;

    Request(String type, Crawler crawler, GUILabelManagement guiLabels, SearchEngine.SupportedSearchEngine engine) {
        this.type = type;
        this.crawler = crawler;
        this.guiLabels = guiLabels;
        this.engine = engine;
    }

    @Override
    public Proxy call() throws Exception {
        if (type.equals("getProxies")) {
            crawler.getMoreProxies(engine);
            return null;
        } else if (type.equals("getConnection")) {
            InUseProxies.getInstance().newRequestToGetProxy();
            guiLabels.setConnectionOutput("Thread " + Thread.currentThread().getId() + " is trying to connect");
            // Try to Establish connection
            boolean valid = false;
            while (!valid) {
                try {
                    crawler.changeIP(SearchEngine.testConnectionToWebsite(engine), false, true, engine, Optional.empty());
                    valid = true;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            //If it is at runtime, add it to the queue from here
            Proxy temp = crawler.getMapThreadIdToProxy().get(Thread.currentThread().getId());
            crawler.getQueueOfConnections().add(temp);
            guiLabels.setNumberOfWorkingIPs("add," + temp.getProxy() + " Port: " + temp.getPort());

            InUseProxies.getInstance().requestHasBeenCompleted();
            return crawler.getMapThreadIdToProxy().get(Thread.currentThread().getId());
        }
        return null;
    }


}
