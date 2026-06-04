package com.example.demo;

import com.example.demo.service.ApiService;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.image.Image;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.util.Optional;

public class JavaFXApp extends Application {
   public void start(Stage primaryStage) {
      Platform.setImplicitExit(true);
      primaryStage.setTitle("Smart Watch Haptic System");
      primaryStage.setOnCloseRequest(event -> Platform.exit());
      loadApplicationIcon(primaryStage);
      checkConnectionAndLoadDashboard(primaryStage);
   }

   private void checkConnectionAndLoadDashboard(Stage primaryStage) {
      Stage loadingStage = createLoadingStage();
      loadingStage.show();

      ApiService.getInstance().checkConnectionAsync()
         .whenComplete((connected, error) -> Platform.runLater(() -> {
            if (error != null || !Boolean.TRUE.equals(connected)) {
               closeLoadingStage(loadingStage);
               showNoConnectionDialog(primaryStage);
               return;
            }

            System.out.println("Connection Established!");
            loadDashboard(primaryStage, loadingStage);
         }));
   }

   private Stage createLoadingStage() {
      ProgressIndicator progressIndicator = new ProgressIndicator();
      progressIndicator.setPrefSize(44, 44);
      Label message = new Label("Checking connection to the n8n server...");
      VBox content = new VBox(12, progressIndicator, message);
      content.setAlignment(Pos.CENTER);
      content.setMinSize(320, 140);

      Stage stage = new Stage(StageStyle.UTILITY);
      stage.initModality(Modality.APPLICATION_MODAL);
      stage.setTitle("Connecting");
      stage.setResizable(false);
      stage.setScene(new Scene(content));
      stage.setOnCloseRequest(event -> event.consume());
      return stage;
   }

   private void showNoConnectionDialog(Stage primaryStage) {
      Alert alert = new Alert(Alert.AlertType.WARNING);
      alert.initModality(Modality.APPLICATION_MODAL);
      if (primaryStage.getScene() != null) {
         alert.initOwner(primaryStage);
      }
      alert.setTitle("No Connection");
      alert.setHeaderText("No connection to the server");
      alert.setContentText("Please check your network or n8n server.\nYou can retry or close the application.");

      ButtonType retry = new ButtonType("Retry", ButtonBar.ButtonData.OK_DONE);
      ButtonType close = new ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE);
      alert.getButtonTypes().setAll(retry, close);

      Optional<ButtonType> result = alert.showAndWait();
      if (result.isPresent() && result.get() == retry) {
         checkConnectionAndLoadDashboard(primaryStage);
         return;
      }

      Platform.exit();
   }

   private void loadDashboard(Stage primaryStage, Stage loadingStage) {
      try {
         FXMLLoader fxmlLoader = new FXMLLoader(this.getClass().getResource("/com/example/demo/view/Dashboard.fxml"));
         Parent root = fxmlLoader.load();
         Scene scene = new Scene(root);
         scene.getStylesheets().add(this.getClass().getResource("/com/example/demo/view/dashboard.css").toExternalForm());
         primaryStage.setScene(scene);
         primaryStage.setMaximized(true);
         primaryStage.show();
      } catch (Exception ex) {
         ex.printStackTrace();
         closeLoadingStage(loadingStage);
         showStartupError(ex);
      } finally {
         closeLoadingStage(loadingStage);
      }
   }

   private void closeLoadingStage(Stage loadingStage) {
      if (loadingStage == null) {
         return;
      }
      loadingStage.hide();
      loadingStage.close();
   }

   private void loadApplicationIcon(Stage primaryStage) {
      try {
         Image icon = new Image(this.getClass().getResourceAsStream("/com/example/demo/images/app-icon.png"));
         if (!icon.isError()) {
            primaryStage.getIcons().add(icon);
         }
      } catch (Exception ignored) {
      }
   }

   private void showStartupError(Exception ex) {
      Alert alert = new Alert(Alert.AlertType.ERROR);
      alert.initModality(Modality.APPLICATION_MODAL);
      alert.setTitle("Startup Error");
      alert.setHeaderText("Could not start the application");
      alert.setContentText(ex.getMessage() == null ? "Unexpected startup failure." : ex.getMessage());
      alert.showAndWait();
      Platform.exit();
   }
}
