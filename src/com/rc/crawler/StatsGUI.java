package com.rc.crawler;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

/**
 * Created by rafaelcastro on 11/10/17.
 * GUI for the Statistics section.
 */
class StatsGUI {
    private IntegerProperty numberOfBlockedProxies = new SimpleIntegerProperty();
    private IntegerProperty numberOfRelockedProxies = new SimpleIntegerProperty();
    private IntegerProperty numberOfUnlockedProxies = new SimpleIntegerProperty();
    private IntegerProperty numberOfLockedByProvider = new SimpleIntegerProperty();

    private StringProperty startTime = new SimpleStringProperty();

    StatsGUI () {
        numberOfBlockedProxies.setValue(0);
        numberOfRelockedProxies.setValue(0);
        numberOfUnlockedProxies.setValue(0);
    }

    /**
     * Creates a new StatsGUI obj. Saves the current time the download process starts.
     */
    void setStartTime() {
        //Write curr dae
        DateTime now = new DateTime();
        DateTimeFormatter formatter = DateTimeFormat.forPattern("dd/MM/yyyy HH:mm:ss");
        startTime.set(now.toString(formatter));
    }

    /**
     * Updates the number of proxies blocked by Google
     *
     * @param currNumber curr number of locked proxies
     */
    void updateNumberOfBlockedProxies(int currNumber) {
            numberOfBlockedProxies.setValue(currNumber);
    }

    /**
     * Updates the number of proxies that were unlocked, but are now locked again
     *
     * @param currNumber curr number of relocked proxies
     */
    void updateNumberOfRelockedProxies(int currNumber) {
        numberOfRelockedProxies.setValue(currNumber);
    }

    /**
     * Updates the number of unlocked proxies
     *
     * @param currNumber curr number of unlcoked proxies
     */
    void updateNumberOfUnlocked(int currNumber) {
        numberOfUnlockedProxies.setValue(currNumber);
    }

    /**
     * Updates the number of locked proxies by the proxy provider
     *
     * @param curr curr number of proxies blocked
     */
    void updateNumberOfLockedByProvider(int curr) {
        numberOfLockedByProvider.setValue(curr);
    }

    IntegerProperty getNumberOfBlockedProxies() {
        return numberOfBlockedProxies;
    }

    IntegerProperty getNumberOfRelockedProxies() {
        return numberOfRelockedProxies;
    }

    IntegerProperty getNumberOfUnlockedProxies() {
        return numberOfUnlockedProxies;
    }

    IntegerProperty getNumberOfLockedByProvider() {
        return numberOfLockedByProvider;
    }
    StringProperty getStartTime() {
        return startTime;
    }



}






