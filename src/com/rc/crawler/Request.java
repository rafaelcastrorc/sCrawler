package com.rc.crawler;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import org.openqa.selenium.WebDriver;

import java.util.Optional;
import java.util.concurrent.Callable;

/**
 * Assigns a task to a thread.
 * Find new working proxy and adds it to the queue.
 */

class Request implements Callable<Proxy> {
    private final String type;
    private Crawler crawler;
    private GUILabelManagement guiLabels;
    private boolean atRuntime = false;

    Request(boolean atRuntime, String type, Crawler crawler, GUILabelManagement guiLabels) {
        this.atRuntime = atRuntime;
        this.type = type;
        this.crawler = crawler;
        this.guiLabels = guiLabels;
    }

    @Override
    public Proxy call() throws Exception {
        if (type.equals("getProxies")) {
            crawler.getMoreProxies();
            return null;
        } else if (type.equals("getConnection")) {
            guiLabels.setConnectionOutput("Thread " + Thread.currentThread().getId() + " is trying to connect");
            // Try to Establish connection
            boolean valid = false;
            while (!valid) {
                try {
                    crawler.changeIP("https://scholar.google.com/scholar?q=hola&hl=en&as_sdt=0,39", false,
                            true);
                    valid = true;
                } catch (Exception ignored) {
                }
            }
            if (atRuntime) {
                //If it is at runtime, add it to the queue from here
                Proxy temp = crawler.getMapThreadIdToProxy().get(Thread.currentThread().getId());
                crawler.getQueueOfConnections().add(temp);
                guiLabels.setNumberOfWorkingIPs("add," + temp.getProxy() + " Port: " + temp.getPort());
            }
            return crawler.getMapThreadIdToProxy().get(Thread.currentThread().getId());
        }
        return null;
    }

}
