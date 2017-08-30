package com.rc.crawler;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by rafaelcastro on 6/17/17.
 * GUI for the Simultaneous Downloads section, which keeps track of each individual download. There are at most 4
 * simultaneous downloads at a time, and each download has its own GUI group (T1, T2, T3, ... Ti). A GUI group consists
 * of a label show the title of the article, a progress bar, and a label to show the status.
 */
class SimultaneousDownloadsGUI {
    private int numOfSimultaneousDownloads;
    private AtomicCounter counter = null;
    //Maps a thread to the corresponding group it modifies (T1, T2, T3, or T4)
    private Map<Long, Integer> mapThreadToGroup;
    private ProgressBar[] progressBarT;
    private Label[] statusT;
    private Label[] articleNameT;

    /**
     * Resets the counter to 0
     */
    void resetCounter() {
        counter.reset();
    }

    SimultaneousDownloadsGUI() {
        this.counter = new AtomicCounter();
        this.mapThreadToGroup = Collections.synchronizedMap(new HashMap<Long, Integer>());
    }

    /**
     * Adds the GUI for the Multiple Downloads mode to the screen, depending on the number of simultaneous downloads
     * allowed.
     *
     * @param scrollPane scroll panel where the GUI will be located
     */
    void addGUI(ScrollPane scrollPane) {
        //Main VBox
        VBox vBox = new VBox(30);
        vBox.setAlignment(Pos.CENTER);

        VBox[] vBoxT = new VBox[numOfSimultaneousDownloads];
        this.progressBarT = new ProgressBar[numOfSimultaneousDownloads];
        this.articleNameT = new Label[numOfSimultaneousDownloads];
        this.statusT = new Label[numOfSimultaneousDownloads];

        //Create all the necessary GUI objects depending on the number of simultaneous downloads allowed.
        for (int i = 0; i < vBoxT.length; i++) {
            VBox tempVBox = new VBox(2);
            tempVBox.setAlignment(Pos.CENTER);
            ProgressBar tempProgressBar = new ProgressBar();
            Label tempArticleName = new Label((i + 1) + ". Folder: Not set");
            tempArticleName.setStyle("-fx-font-size: 9pt");
            Label tempStatus = new Label("Status: Idle");
            tempStatus.setStyle("-fx-font-size: 9pt");
            progressBarT[i] = tempProgressBar;
            articleNameT[i] = tempArticleName;
            statusT[i] = tempStatus;
            tempVBox.getChildren().addAll(articleNameT[i], progressBarT[i], statusT[i]);
            vBoxT[i] = tempVBox;
        }

        vBox.getChildren().addAll(vBoxT);
        scrollPane.setContent(vBox);

    }


    /**
     * Adds a thread to a group
     *
     * @param threadID the current thread id
     * @return group the thread belongs to
     */
    synchronized int addThreadToGroup(Long threadID) {
        if (mapThreadToGroup.get(threadID) == null) {
            int group = getGroup();
            mapThreadToGroup.put(threadID, group);
            return group;
        } else return mapThreadToGroup.get(threadID);
    }

    /**
     * Gets a group based on the counter
     *
     * @return 1, 2, 3, ... n
     */
    private synchronized int getGroup() {
        int selection = counter.value();
        counter.increment();
        if (selection > numOfSimultaneousDownloads - 1) {
            counter.reset();
            selection = 0;
        }
        return selection;

    }

    /**
     * Updates the status label of a given group based on the thread group affiliation
     *
     * @param s string to output
     */
    void updateStatus(String s) {
        try {
            int group = mapThreadToGroup.get(Thread.currentThread().getId());
            Platform.runLater(() -> statusT[group].setText("Status: " + s));
        } catch (NullPointerException ignored) {
        }
    }

    /**
     * Updates the progress bar of a given group based on the thread group affiliation
     *
     * @param i double with the current progress from 0 to 1
     */
    void updateProgressBar(double i) {
        int group = mapThreadToGroup.get(Thread.currentThread().getId());
        Platform.runLater(() -> progressBarT[group].progressProperty().setValue(i));
    }

    /**
     * Updates the article name label of a given group based on the thread group affiliation
     *
     * @param s string to output
     */
    void updateArticleName(String s) {
        int currGroup = mapThreadToGroup.get(Thread.currentThread().getId());
        Platform.runLater(() -> articleNameT[currGroup].setText("Folder: " + s));
    }

    /**
     * Sets the number of allowed simultaneous downloads
     *
     * @param numOfSimultaneousDownloads int
     */
    void setNumOfSimultaneousDownloads(int numOfSimultaneousDownloads) {
        this.numOfSimultaneousDownloads = numOfSimultaneousDownloads;
    }
}
