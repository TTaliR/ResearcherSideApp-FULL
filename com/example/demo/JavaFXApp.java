package com.example.demo;

import com.example.demo.service.ApiService;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.image.Image;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.Optional;

public class JavaFXApp extends Application {
   public void start(Stage primaryStage) {
      try {
         primaryStage.setTitle("Smart Watch Haptic System");
         primaryStage.getIcons().add(new Image(this.getClass().getResourceAsStream("/com/example/demo/images/app-icon.png")));

         // Check connection first, before loading Dashboard
         while (!ApiService.getInstance().checkConnection()) {
            Alert alert = new Alert(Alert.AlertType.NONE);
            alert.initModality(Modality.APPLICATION_MODAL);
            alert.setTitle("No Connection");
            alert.setHeaderText("No connection to the server");
            alert.setContentText("Please check your network or n8n server.\nYou can retry or close the application.");
            alert.setAlertType(Alert.AlertType.WARNING);
            ButtonType retry = new ButtonType("Retry", ButtonBar.ButtonData.OK_DONE);
            ButtonType close = new ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE);
            alert.getButtonTypes().setAll(retry, close);
            Optional<ButtonType> result = alert.showAndWait();
            if (!result.isPresent() || result.get() == close) {
               Platform.exit();
               return;
            }
            // If Retry selected, loop continues and re-checks connection
         }
         System.out.println("Connection Established!");

         // Load Dashboard after connection is verified
         FXMLLoader fxmlLoader = new FXMLLoader(this.getClass().getResource("/com/example/demo/view/Dashboard.fxml"));
         Scene scene = new Scene((Parent)fxmlLoader.load());
         scene.getStylesheets().add(this.getClass().getResource("/com/example/demo/view/dashboard.css").toExternalForm());
         primaryStage.setScene(scene);
         primaryStage.setMaximized(true);
         primaryStage.show();
      } catch (Exception var4) {
         var4.printStackTrace();
      }
   }
}
