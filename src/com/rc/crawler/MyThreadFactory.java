package com.rc.crawler;

import java.util.concurrent.ThreadFactory;

/**
 * Created by rafaelcastro on 6/16/17.
 */
class MyThreadFactory implements ThreadFactory {
    public Thread newThread(Runnable r) {
        Thread thread = new Thread(r);
        thread.setDaemon(true);
        return thread;
    }
}