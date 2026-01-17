package com.example.demo;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

public class JavaFXApp extends Application {
   public void start(Stage primaryStage) {
      try {
         FXMLLoader fxmlLoader = new FXMLLoader(this.getClass().getResource("/com/example/demo/view/ResearcherInterface.fxml"));
         Scene scene = new Scene((Parent)fxmlLoader.load());
         primaryStage.getIcons().add(new Image(this.getClass().getResourceAsStream("/com/example/demo/images/app-icon.png")));
         primaryStage.setTitle("Smart Watch Haptic System");
         primaryStage.setScene(scene);
         primaryStage.show();
      } catch (Exception var4) {
         var4.printStackTrace();
      }
   }
}
