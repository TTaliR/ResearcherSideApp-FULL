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
         FXMLLoader fxmlLoader = new FXMLLoader(this.getClass().getResource("/com/example/demo/view/ResearcherInterface.fxml"));
         Scene scene = new Scene((Parent)fxmlLoader.load());
         primaryStage.getIcons().add(new Image(this.getClass().getResourceAsStream("/com/example/demo/images/app-icon.png")));
         primaryStage.setTitle("Smart Watch Haptic System");
         primaryStage.setScene(scene);
         primaryStage.show();

         // Show a modal dialog that blocks user interaction until connection is available or app is closed
         while (!ApiService.getInstance().checkConnection()) {
            Alert alert = new Alert(Alert.AlertType.NONE);
            alert.initOwner(primaryStage);
            alert.initModality(Modality.WINDOW_MODAL);
            alert.setTitle("No Connection");
            alert.setHeaderText("No connection to the server");
            alert.setContentText("Please check your network. You can retry or close the application.");
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
      } catch (Exception var4) {
         var4.printStackTrace();
      }
   }
}
