package com.rc.crawler;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

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

        //Close the app correctly
        primaryStage.setOnCloseRequest(e -> {
            //Kil the phantomjs process
            Runtime rt = Runtime.getRuntime();
            if (System.getProperty("os.name").toLowerCase().contains("windows")) {
                try {
                    rt.exec("taskkill /F /IM phantomjs.exe");
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            } else {
                try {
                    rt.exec("pkill -f phantomjs");
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }
            Platform.exit();
            System.exit(0);
        });


    }
}
