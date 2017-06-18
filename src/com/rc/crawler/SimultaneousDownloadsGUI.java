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
 * simultaneous downloads at a time, and each download has its own GUI group (T1, T2, T3, and T4). A GUI group consists
 * of a label display the title of the article, a progress bar, and a label to display the status.
 */
class SimultaneousDownloadsGUI {

    private AtomicCounter counter = null;
    private final String[] groups;
    private Label articleNameT1;
    private ProgressBar progressBarT1;
    private Label statusT1;
    private ProgressBar progressBarT2;
    private Label articleNameT2;
    private Label statusT2;
    private ProgressBar progressBarT3;
    private Label articleNameT3;
    private Label statusT3;
    private ProgressBar progressBarT4;
    private Label articleNameT4;
    private Label statusT4;
    //Maps a thread to the corresponding group it modifies (T1, T2, T3, or T4)
    private Map<Long, String> mapThreadToGroup;

    void resetCounter() {
        counter.reset();
    }

    SimultaneousDownloadsGUI() {
        this.counter = new AtomicCounter();
        this.groups = new String[4];
        this.mapThreadToGroup = Collections.synchronizedMap(new HashMap<Long, String>());
        groups[0] = "T1";
        groups[1] = "T2";
        groups[2] = "T3";
        groups[3] = "T4";
    }

    void addGUI(ScrollPane scrollPane) {
        VBox vBox = new VBox(30);
        vBox.setAlignment(Pos.CENTER);

        VBox vBoxT1 = new VBox(2);
        vBoxT1.setAlignment(Pos.CENTER);

        this.progressBarT1 = new ProgressBar();
        this.articleNameT1 = new Label("1. Folder: Not set");
        this.statusT1 = new Label("Status: Idle");
        articleNameT1.setStyle("-fx-font-size: 9pt");
        statusT1.setStyle("-fx-font-size: 9pt");

        vBoxT1.getChildren().addAll(articleNameT1, progressBarT1, statusT1);

        VBox vBoxT2 = new VBox(2);
        vBoxT2.setAlignment(Pos.CENTER);

        this.progressBarT2 = new ProgressBar();
        this.articleNameT2 = new Label("2. Folder: Not set");
        this.statusT2 = new Label("Status: Idle");
        articleNameT2.setStyle("-fx-font-size: 9pt");
        statusT2.setStyle("-fx-font-size: 9pt");

        vBoxT2.getChildren().addAll(articleNameT2, progressBarT2, statusT2);

        VBox vBoxT3 = new VBox(2);
        vBoxT3.setAlignment(Pos.CENTER);
        this.progressBarT3 = new ProgressBar();
        this.articleNameT3 = new Label("3. Folder: Not set");
        this.statusT3 = new Label("Status: Idle");
        articleNameT3.setStyle("-fx-font-size: 9pt");
        statusT3.setStyle("-fx-font-size: 9pt");

        vBoxT3.getChildren().addAll(articleNameT3, progressBarT3, statusT3);

        VBox vBoxT4 = new VBox(2);
        vBoxT4.setAlignment(Pos.CENTER);
        this.progressBarT4 = new ProgressBar();
        this.articleNameT4 = new Label("4. Folder: Not set");
        this.statusT4 = new Label("Status: Idle");
        articleNameT4.setStyle("-fx-font-size: 9pt");
        statusT4.setStyle("-fx-font-size: 9pt");

        vBoxT4.getChildren().addAll(articleNameT4, progressBarT4, statusT4);

        vBox.getChildren().addAll(vBoxT1, vBoxT2, vBoxT3, vBoxT4);
        scrollPane.setContent(vBox);

    }


    /**
     * Adds a thread to a group
     *
     * @param threadID the current thread id
     * @return group the thread belongs to
     */
    synchronized String addThreadToGroup(Long threadID) {
        if (mapThreadToGroup.get(threadID) == null) {
            String group = getGroup();
            mapThreadToGroup.put(threadID, group);
            return group;
        }
        else return mapThreadToGroup.get(threadID);
    }

    /**
     * Gets a group based on the counter
     *
     * @return T1, T2, T3, or T4
     */
    private synchronized String getGroup() {
        int selection = counter.value();
        counter.increment();
        if (selection > 3) {
            counter.reset();
            selection = 0;
        }

        return groups[selection];

    }

    /**
     * Updates the status label of a given group based on the thread group affiliation
     *
     * @param s string to output
     */
    void updateStatus(String s) {
        String group = mapThreadToGroup.get(Thread.currentThread().getId());
        Platform.runLater((() -> {
            switch (group) {
                case "T1":
                    statusT1.setText("Status: " + s);
                    break;
                case "T2":
                    statusT2.setText("Status: " + s);
                    break;
                case "T3":
                    statusT3.setText("Status: " + s);
                    break;
                case "T4":
                    statusT4.setText("Status: " + s);
                    break;
            }
        }));

    }

    /**
     * Updates the progress bar of a given group based on the thread group affiliation
     *
     * @param i double with the current progress from 0 to 1
     */
    void updateProgressBar(double i) {
        String group = mapThreadToGroup.get(Thread.currentThread().getId());
        Platform.runLater(() -> {
            switch (group) {
                case "T1":
                    progressBarT1.progressProperty().setValue(i);
                    break;
                case "T2":
                    progressBarT2.progressProperty().setValue(i);
                    break;
                case "T3":
                    progressBarT3.progressProperty().setValue(i);
                    break;
                case "T4":
                    progressBarT4.progressProperty().setValue(i);
                    break;
            }
        });
    }

    /**
     * Updates the article name label of a given group based on the thread group affiliation
     *
     * @param s string to output
     */
    void updateArticleName(String s) {
        String currGroup = mapThreadToGroup.get(Thread.currentThread().getId());
        Platform.runLater(() -> {
            switch (currGroup) {
                case "T1":
                    articleNameT1.setText("Folder: " + s);
                    break;
                case "T2":
                    articleNameT2.setText("Folder: " + s);
                    break;
                case "T3":
                    articleNameT3.setText("Folder: " + s);
                    break;
                case "T4":
                    articleNameT4.setText("Folder: " + s);
                    break;
            }
        });
    }


}
