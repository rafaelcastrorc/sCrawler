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
    private ProgressBar progressBarT5;
    private Label articleNameT5;
    private Label statusT5;
    private ProgressBar progressBarT6;
    private Label articleNameT6;
    private Label statusT6;
    private ProgressBar progressBarT7;
    private Label articleNameT7;
    private Label statusT7;
    private ProgressBar progressBarT8;
    private Label articleNameT8;
    private Label statusT8;
    private ProgressBar progressBarT9;
    private Label articleNameT9;
    private Label statusT9;
    private ProgressBar progressBarT10;
    private Label articleNameT10;
    private Label statusT10;
    //Maps a thread to the corresponding group it modifies (T1, T2, T3, or T4)
    private Map<Long, String> mapThreadToGroup;

    /**
     * Resets the counter to 0
     */
    void resetCounter() {
        counter.reset();
    }

    SimultaneousDownloadsGUI() {
        this.counter = new AtomicCounter();
        this.groups = new String[10];
        this.mapThreadToGroup = Collections.synchronizedMap(new HashMap<Long, String>());
        for (int i = 0; i < groups.length; i++) {
            groups[i] = "T" + (i + 1);
        }
    }

    /**
     * Adds the GUI for the multiple downloads to the screen
     *
     * @param scrollPane scroll panel where the GUI will be located
     */
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


        VBox vBoxT5 = new VBox(2);
        vBoxT5.setAlignment(Pos.CENTER);
        this.progressBarT5 = new ProgressBar();
        this.articleNameT5 = new Label("4. Folder: Not set");
        this.statusT5 = new Label("Status: Idle");
        articleNameT5.setStyle("-fx-font-size: 9pt");
        statusT5.setStyle("-fx-font-size: 9pt");
        vBoxT5.getChildren().addAll(articleNameT5, progressBarT5, statusT5);

        VBox vBoxT6 = new VBox(2);
        vBoxT6.setAlignment(Pos.CENTER);
        this.progressBarT6 = new ProgressBar();
        this.articleNameT6 = new Label("4. Folder: Not set");
        this.statusT6 = new Label("Status: Idle");
        articleNameT6.setStyle("-fx-font-size: 9pt");
        statusT6.setStyle("-fx-font-size: 9pt");
        vBoxT6.getChildren().addAll(articleNameT6, progressBarT6, statusT6);


        VBox vBoxT7 = new VBox(2);
        vBoxT7.setAlignment(Pos.CENTER);
        this.progressBarT7 = new ProgressBar();
        this.articleNameT7 = new Label("4. Folder: Not set");
        this.statusT7 = new Label("Status: Idle");
        articleNameT7.setStyle("-fx-font-size: 9pt");
        statusT7.setStyle("-fx-font-size: 9pt");
        vBoxT7.getChildren().addAll(articleNameT7, progressBarT7, statusT7);

        VBox vBoxT8 = new VBox(2);
        vBoxT8.setAlignment(Pos.CENTER);
        this.progressBarT8 = new ProgressBar();
        this.articleNameT8 = new Label("4. Folder: Not set");
        this.statusT8 = new Label("Status: Idle");
        articleNameT8.setStyle("-fx-font-size: 9pt");
        statusT8.setStyle("-fx-font-size: 9pt");
        vBoxT8.getChildren().addAll(articleNameT8, progressBarT8, statusT8);

        VBox vBoxT9 = new VBox(2);
        vBoxT9.setAlignment(Pos.CENTER);
        this.progressBarT9 = new ProgressBar();
        this.articleNameT9 = new Label("4. Folder: Not set");
        this.statusT9 = new Label("Status: Idle");
        articleNameT9.setStyle("-fx-font-size: 9pt");
        statusT9.setStyle("-fx-font-size: 9pt");
        vBoxT9.getChildren().addAll(articleNameT9, progressBarT9, statusT9);


        VBox vBoxT10 = new VBox(2);
        vBoxT10.setAlignment(Pos.CENTER);
        this.progressBarT10 = new ProgressBar();
        this.articleNameT10 = new Label("4. Folder: Not set");
        this.statusT10 = new Label("Status: Idle");
        articleNameT10.setStyle("-fx-font-size: 9pt");
        statusT10.setStyle("-fx-font-size: 9pt");
        vBoxT10.getChildren().addAll(articleNameT10, progressBarT10, statusT10);

        vBox.getChildren().addAll(vBoxT1, vBoxT2, vBoxT3, vBoxT4, vBoxT5, vBoxT6, vBoxT7, vBoxT8, vBoxT9, vBoxT10);
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
        } else return mapThreadToGroup.get(threadID);
    }

    /**
     * Gets a group based on the counter
     *
     * @return T1, T2, T3, ... , Ti
     */
    private synchronized String getGroup() {
        int selection = counter.value();
        counter.increment();
        if (selection > 9) {
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
                case "T5":
                    statusT5.setText("Status: " + s);
                    break;
                case "T6":
                    statusT6.setText("Status: " + s);
                    break;
                case "T7":
                    statusT7.setText("Status: " + s);
                    break;
                case "T8":
                    statusT8.setText("Status: " + s);
                    break;
                case "T9":
                    statusT9.setText("Status: " + s);
                    break;
                case "T10":
                    statusT10.setText("Status: " + s);
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
                case "T5":
                    progressBarT5.progressProperty().setValue(i);
                    break;
                case "T6":
                    progressBarT6.progressProperty().setValue(i);
                    break;
                case "T7":
                    progressBarT7.progressProperty().setValue(i);
                    break;
                case "T8":
                    progressBarT8.progressProperty().setValue(i);
                    break;
                case "T9":
                    progressBarT9.progressProperty().setValue(i);
                    break;
                case "T10":
                    progressBarT10.progressProperty().setValue(i);
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
                case "T5":
                    articleNameT5.setText("Folder: " + s);
                    break;
                case "T6":
                    articleNameT6.setText("Folder: " + s);
                    break;
                case "T7":
                    articleNameT7.setText("Folder: " + s);
                    break;
                case "T8":
                    articleNameT8.setText("Folder: " + s);
                    break;
                case "T9":
                    articleNameT9.setText("Folder: " + s);
                    break;
                case "T10":
                    articleNameT10.setText("Folder: " + s);
                    break;
            }
        });
    }


}
