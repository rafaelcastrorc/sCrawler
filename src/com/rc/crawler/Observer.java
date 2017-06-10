package com.rc.crawler;

/**
 * Created by rafaelcastro on 6/1/17.
 * Observer class to handle updating the information of the view
 */
public abstract class Observer {
    protected Crawler subject;
    protected abstract void update();
}

