package com.example.demo.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart.Data;
import javafx.scene.chart.XYChart.Series;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.transform.Rotate;
import javafx.util.Duration;
import org.springframework.stereotype.Component;

@Component
public class SunPositionController {
   @FXML
   private LineChart<String, Number> sunChart;
   @FXML
   private ImageView sunIcon;
   @FXML
   private Label elevationLabel;
   @FXML
   private Label azimuthLabel;
   private ScheduledExecutorService chartScheduler;
   private ScheduledExecutorService compassScheduler;
   private int selectedUserID;

   public void initializeLiveView(int userId) {
      this.selectedUserID = userId;
      Image sunImage = new Image(this.getClass().getResourceAsStream("/com/example/demo/images/sun.png"));
      this.sunIcon.setImage(sunImage);
      this.chartScheduler = Executors.newSingleThreadScheduledExecutor();
      this.chartScheduler.scheduleAtFixedRate(() -> Platform.runLater(this::fetchAndUpdateChart), 0L, 15L, TimeUnit.SECONDS);
      this.compassScheduler = Executors.newSingleThreadScheduledExecutor();
      this.compassScheduler.scheduleAtFixedRate(this::updateCompass, 0L, 60L, TimeUnit.SECONDS);
   }

   private void fetchAndUpdateChart() {
      try {
         URL url = new URL("http://localhost:1880/get-data");
         HttpURLConnection conn = (HttpURLConnection)url.openConnection();
         conn.setRequestMethod("GET");
         BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
         StringBuilder response = new StringBuilder();

         String line;
         while ((line = reader.readLine()) != null) {
            response.append(line);
         }

         reader.close();
         ObjectMapper mapper = new ObjectMapper();
         JsonNode dataNode = mapper.readTree(response.toString());
         this.sunChart.getData().clear();
         Series<String, Number> azimuthSeries = new Series();
         azimuthSeries.setName("Sun Azimuth");
         Series<String, Number> feedbackSeries = new Series();
         feedbackSeries.setName("Feedback for User " + this.selectedUserID);
         DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");

         for (JsonNode node : dataNode) {
            if (node.get("sensorid").asInt() == 2) {
               LocalDateTime dateTime = Instant.parse(node.get("time").asText()).atZone(ZoneId.systemDefault()).toLocalDateTime();
               String formattedTime = dateTime.format(formatter);
               double azimuth = node.get("value").asDouble();
               Data<String, Number> point = new Data(formattedTime, azimuth);
               azimuthSeries.getData().add(point);
               if (node.get("userid").asInt() == this.selectedUserID && node.hasNonNull("alert_type")) {
                  Data<String, Number> feedback = new Data(formattedTime, azimuth);
                  StringBuilder tooltipText = new StringBuilder();
                  tooltipText.append("Feedback Triggered\n");
                  tooltipText.append("Time: ").append(dateTime).append("\n");
                  tooltipText.append("Azimuth: ").append(azimuth).append("\n");
                  tooltipText.append("Type: ").append(node.get("alert_type").asText()).append("\n");
                  if (node.has("pulses")) {
                     tooltipText.append("Pulses: ").append(node.get("pulses").asInt()).append("\n");
                  }

                  if (node.has("intensity")) {
                     tooltipText.append("Intensity: ").append(node.get("intensity").asInt()).append("\n");
                  }

                  feedback.setExtraValue(tooltipText.toString());
                  feedbackSeries.getData().add(feedback);
               }
            }
         }

         this.sunChart.getData().add(azimuthSeries);
         if (!feedbackSeries.getData().isEmpty()) {
            this.sunChart.getData().add(feedbackSeries);
         }

         Platform.runLater(() -> {
            for (Data<String, Number> d : azimuthSeries.getData()) {
               if (d.getNode() != null) {
                  Tooltip.install(d.getNode(), new Tooltip("Azimuth: " + d.getYValue()));
               }
            }

            for (Data<String, Number> dx : feedbackSeries.getData()) {
               if (dx.getNode() != null) {
                  Tooltip tip = new Tooltip((String)dx.getExtraValue());
                  tip.setShowDelay(Duration.millis(50.0));
                  Tooltip.install(dx.getNode(), tip);
                  dx.getNode().setStyle("-fx-background-color: red; -fx-background-radius: 5px;");
                  dx.getNode().setScaleX(1.5);
                  dx.getNode().setScaleY(1.5);
               }
            }
         });
      } catch (Exception var20) {
         var20.printStackTrace();
      }
   }

   private void updateCompass() {
      try {
         URL url = new URL("http://localhost:1880/get-sun");
         HttpURLConnection conn = (HttpURLConnection)url.openConnection();
         conn.setRequestMethod("GET");
         BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
         StringBuilder response = new StringBuilder();

         String line;
         while ((line = reader.readLine()) != null) {
            response.append(line);
         }

         reader.close();
         ObjectMapper mapper = new ObjectMapper();
         JsonNode root = mapper.readTree(response.toString());
         double azimuth = root.get("azimuth").asDouble();
         double elevation = root.get("elevation").asDouble();
         Platform.runLater(() -> {
            this.rotateSunIcon(azimuth);
            this.elevationLabel.setText(String.format("Elevation: %.1f°", elevation));
            this.azimuthLabel.setText(String.format("Azimuth: %.1f°", azimuth));
         });
      } catch (Exception var12) {
         var12.printStackTrace();
      }
   }

   private void rotateSunIcon(double azimuth) {
      Rotate rotate = new Rotate(azimuth, this.sunIcon.getFitWidth() / 2.0, this.sunIcon.getFitHeight() / 2.0);
      this.sunIcon.getTransforms().clear();
      this.sunIcon.getTransforms().add(rotate);
   }

   public void onClose() {
      if (this.chartScheduler != null && !this.chartScheduler.isShutdown()) {
         this.chartScheduler.shutdownNow();
      }

      if (this.compassScheduler != null && !this.compassScheduler.isShutdown()) {
         this.compassScheduler.shutdownNow();
      }
   }
}
