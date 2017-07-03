package com.rc.crawler;

import java.util.concurrent.ThreadFactory;

/**
 * Created by rafaelcastro on 6/16/17.
 * Custom thread creation to be used by the different executor services.
 */
class MyThreadFactory implements ThreadFactory {
    Thread.UncaughtExceptionHandler h = (th, ex) -> System.out.println("Uncaught exception: " + ex);

    public Thread newThread(Runnable r) {
        Thread thread = new Thread(r);
        thread.setDaemon(true);
        thread.setUncaughtExceptionHandler(h);
        return thread;
    }
}