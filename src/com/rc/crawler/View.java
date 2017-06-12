package com.rc.crawler;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

/**
 * Created by rafaelcastro on 6/7/17.
 */
public class View extends Application {

    private static Crawler crawler;

    public static void setCrawler(Crawler crawler) {
        View.crawler = crawler;
    }

    public static Crawler getCrawler() {
        return crawler;
    }

    public static void main(String[] args) {
        launch(args);
    }

    public View() {
        System.out.println("View constructor");
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("View.fxml"));
        Parent root =  loader.load();
        primaryStage.setTitle("Crawler");
        Scene loadingScene = new Scene(root);
        loadingScene.getStylesheets().add("Style.css");
        primaryStage.setScene(loadingScene);
        primaryStage.setResizable(false);
        primaryStage.show();

    }


    //Classs to handle alert box. No communiaction needed back
    public static class AlertBox {
        public static void display(String title, String message) {
            Stage window = new Stage();
            window.initModality(Modality.APPLICATION_MODAL);
            window.setTitle(title);
            window.setMinWidth(250);

            Label label= new Label();
            label.setText(message);
            Button closeButton = new Button("Close the window");

            VBox layout = new VBox(10);
            layout.getChildren().addAll(label, closeButton);
            layout.setAlignment(Pos.CENTER);

            Scene scene = new Scene(layout);
            window.setScene(scene);
            window.showAndWait();
        }
    }



}
