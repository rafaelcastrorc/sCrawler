package com.rc.crawler;

import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 * Created by rafaelcastro on 6/9/17.
 */
public class LoadingBox {


    Stage window;

    LoadingBox() {


        }

        public  boolean display() {
            window = new Stage();
            window.initModality(Modality.APPLICATION_MODAL);
            window.initStyle(StageStyle.TRANSPARENT);


            window.setMinWidth(400);


            //Ignore closing window
            window.setOnCloseRequest(e -> {

                e.consume();
            });

            ProgressIndicator progressIndicator = new ProgressIndicator();

            VBox layout = new VBox(20);
            layout.setStyle("-fx-background-color: rgba(255, 255, 255, 0.5);" +
                    "-fx-background-insets: 50;"
            );
            layout.getChildren().addAll(progressIndicator);

            layout.setAlignment(Pos.CENTER);

            Scene scene = new Scene(layout, 200, 200);
            scene.setFill(Color.TRANSPARENT);
            window.setScene(scene);
            window.showAndWait();

            return true;
        }

        void close() {
            window.close();
        }


}

