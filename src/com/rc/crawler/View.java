package com.rc.crawler;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Dialog;
import javafx.stage.Stage;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Scanner;

/**
 * Created by rafaelcastro on 6/7/17.
 * Extends Application.
 */
public class View extends Application {

    public View() {
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        //Loads all the necessary components to start the application
        FXMLLoader loader = new FXMLLoader(getClass().getResource("View.fxml"));
        Parent root = loader.load();
        primaryStage.setTitle("" + getClass().getProtectionDomain().getCodeSource().getLocation());
        Scene loadingScene = new Scene(root);
        loadingScene.getStylesheets().add("Style.css");
        loadingScene.getStylesheets().add("https://fonts.googleapis.com/css?family=Roboto");
        primaryStage.setScene(loadingScene);
        primaryStage.setResizable(false);
        primaryStage.show();
        close(primaryStage);

    }

    /**
     * Closes the app properly
     */
    private void close(Stage primaryStage) {
        //Close the app correctly
        primaryStage.setOnCloseRequest(e -> {
            WebServer.getInstance(new GUILabelManagement()).close(false);
        });
    }
}
