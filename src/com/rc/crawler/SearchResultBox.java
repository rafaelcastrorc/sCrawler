package com.rc.crawler;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXListView;
import javafx.event.Event;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.util.HashSet;
import java.util.Set;

/**
 * Created by rafaelcastro on 6/14/17.
 * Creates a search result window to display all the search results
 */
public class SearchResultBox {

    ListView<Object> searchResultListView = new ListView<>();
    private Stage dialog;
    JFXButton select = new JFXButton("Select");
    JFXButton doNotDownload = new JFXButton("Do not download");

    Set<Object> set = new HashSet<>();


    SearchResultBox() {
    }

    public ListView<Object> getSearchResultListView() {
        return searchResultListView;
    }
    /**
     * Displays the window
     *
     * @return true
     */
    void display(String queryStr) {
        this.dialog = new Stage();
        dialog.setTitle("ERROR: Multiple results found for a query");
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initStyle(StageStyle.DECORATED);
        dialog.setOnCloseRequest(Event::consume);
        VBox layout = new VBox(5);
        layout.setPadding((new Insets(5, 5, 8, 5)));
        layout.setAlignment(Pos.CENTER);
        layout.setStyle("-fx-background-color:  #afb0af");
        Label title = new Label("Please select the correct search result.");
        Label query = new Label("Query: "+ queryStr);

        title.setStyle("-fx-text-fill: black; -fx-font-size: 12pt");
        query.setStyle("-fx-text-fill: black; -fx-font-size: 12pt");


        select.setStyle("-fx-background-color: #7d9a4f; -fx-font-size: 12pt");
        doNotDownload.setStyle("-fx-background-color: #7d9a4f; -fx-font-size: 12pt");


        HBox hBox = new HBox(30);
        hBox.setAlignment(Pos.CENTER);
        hBox.getChildren().addAll(doNotDownload, select);


        layout.getChildren().addAll(title, query, searchResultListView, hBox);

        Scene dialogScene = new Scene(layout, 400, 250);
        dialogScene.getStylesheets().add("Style.css");
        dialogScene.getStylesheets().add("https://fonts.googleapis.com/css?family=Roboto");

        dialog.setScene(dialogScene);
        dialog.showAndWait();
    }

    void addItemToListView(Object e) {
        if (!set.contains(e)) {
            set.add(e);
            searchResultListView.getItems().add(e);
        }
    }

    /**
     * Closes the window
     */
    void close() {
        dialog.close();
    }

}


