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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart.Data;
import javafx.scene.chart.XYChart.Series;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import javafx.util.Duration;

public class SunPositionTracker {
   private Stage stage;
   private int selectedUserID;
   private ScheduledExecutorService scheduler;
   private LineChart<Number, Number> sunChart;
   private Series<Number, Number> sunTrajectory;
   private Series<Number, Number> currentPosition;
   private Series<Number, Number> feedbackPoints;
   private Circle sunCircle;
   private Label azimuthLabel;
   private Label elevationLabel;
   private Label timeLabel;
   private Label userFeedbackLabel;
   private List<SunPositionTracker.SunPosition> sunPositionHistory = new ArrayList<>();
   private List<SunPositionTracker.FeedbackEvent> feedbackEvents = new ArrayList<>();

   //ngrok link base:
   private final String URL_BASE = /*"https://marcella-unguerdoned-ayanna.ngrok-free.dev"*/ "http://localhost:5678" + "/webhook";   //tali

   public SunPositionTracker(int userID) {
      this.selectedUserID = userID;
      this.initializeStage();
      this.startTracking();
   }

   private void initializeStage() {
      try {
         this.stage = new Stage();
         this.stage.setTitle("Live Sun Position Tracker - User " + this.selectedUserID);
         BorderPane root = new BorderPane();
         NumberAxis xAxis = new NumberAxis(0.0, 360.0, 30.0);
         NumberAxis yAxis = new NumberAxis(0.0, 90.0, 10.0);
         xAxis.setLabel("Azimuth (degrees)");
         yAxis.setLabel("Elevation (degrees)");
         this.sunChart = new LineChart(xAxis, yAxis);
         this.sunChart.setTitle("Sun Position");
         this.sunChart.setAnimated(false);
         this.sunChart.setCreateSymbols(true);
         this.sunTrajectory = new Series();
         this.sunTrajectory.setName("Sun Path Today");
         this.currentPosition = new Series();
         this.currentPosition.setName("Current Position");
         this.feedbackPoints = new Series();
         this.feedbackPoints.setName("Feedback Events");
         this.sunChart.getData().addAll(new Series[]{this.sunTrajectory, this.currentPosition, this.feedbackPoints});
         VBox infoPanel = new VBox(10.0);
         infoPanel.setStyle("-fx-padding: 15; -fx-background-color: #f4f4f4;");
         this.timeLabel = new Label("Time: --:--:--");
         this.timeLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
         this.azimuthLabel = new Label("Azimuth: -- degrees");
         this.azimuthLabel.setStyle("-fx-font-size: 14px;");
         this.elevationLabel = new Label("Elevation: -- degrees");
         this.elevationLabel.setStyle("-fx-font-size: 14px;");
         this.userFeedbackLabel = new Label("No recent feedback for User " + this.selectedUserID);
         this.userFeedbackLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: blue;");
         infoPanel.getChildren().addAll(new Node[]{this.timeLabel, this.azimuthLabel, this.elevationLabel, new Label(""), this.userFeedbackLabel});
         root.setCenter(this.sunChart);
         root.setRight(infoPanel);
         Scene scene = new Scene(root, 900.0, 600.0);
         this.stage.setScene(scene);
         this.stage.setOnCloseRequest(event -> this.stopTracking());
      } catch (Exception var6) {
         var6.printStackTrace();
      }
   }

   public void show() {
      this.stage.show();
   }

   private void startTracking() {
      this.scheduler = Executors.newScheduledThreadPool(1);
      this.scheduler.scheduleAtFixedRate(this::updateSunPosition, 0L, 10L, TimeUnit.SECONDS);
   }

   private void stopTracking() {
      if (this.scheduler != null && !this.scheduler.isShutdown()) {
         this.scheduler.shutdown();
      }
   }

   private void updateSunPosition() {
      try {
         URL url = new URL(URL_BASE+"/sensor-data");
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
         this.processSunPositionData(dataNode);
      } catch (Exception var8) {
         var8.printStackTrace();
      }
   }

   private void processSunPositionData(JsonNode dataNode) {
      if (dataNode.isArray()) {
         boolean foundCurrentPosition = false;
         LocalDateTime latestFeedbackTime = null;
         SunPositionTracker.FeedbackEvent latestFeedback = null;

         for (JsonNode node : dataNode) {
            int sensorId = node.get("sensorid").asInt();
            if (sensorId == 2) {
               String timeStr = node.get("time").asText();
               Instant instant = Instant.parse(timeStr);
               LocalDateTime dateTime = instant.atZone(ZoneId.systemDefault()).toLocalDateTime();
               double azimuth = node.get("value").asDouble();
               double elevation = this.calculateElevation(dateTime);
               this.sunPositionHistory.add(new SunPositionTracker.SunPosition(azimuth, elevation, dateTime));

               while (this.sunPositionHistory.size() > 144) {
                  this.sunPositionHistory.remove(0);
               }

               int userId = node.get("userid").asInt();
               String alertType = node.hasNonNull("alert_type") ? node.get("alert_type").asText() : null;
               if (userId == this.selectedUserID && alertType != null) {
                  int pulses = node.hasNonNull("pulses") ? node.get("pulses").asInt() : 0;
                  int intensity = node.hasNonNull("intensity") ? node.get("intensity").asInt() : 0;
                  SunPositionTracker.FeedbackEvent event = new SunPositionTracker.FeedbackEvent(azimuth, elevation, dateTime, alertType, pulses, intensity);
                  this.feedbackEvents.add(event);
                  if (latestFeedbackTime == null || dateTime.isAfter(latestFeedbackTime)) {
                     latestFeedbackTime = dateTime;
                     latestFeedback = event;
                  }
               }

               if (!foundCurrentPosition || dateTime.isAfter(this.sunPositionHistory.get(this.sunPositionHistory.size() - 1).timestamp)) {
                  foundCurrentPosition = true;
                  SunPositionTracker.FeedbackEvent finalLatestFeedback = latestFeedback;
                  Platform.runLater(() -> this.updateUI(dateTime, azimuth, elevation, finalLatestFeedback));
               }
            }
         }
      }
   }

   private double calculateElevation(LocalDateTime time) {
      int hour = time.getHour();
      int minute = time.getMinute();
      double hourDecimal = hour + minute / 60.0;
      double dayProgress = (hourDecimal - 6.0) / 12.0;
      if (dayProgress < 0.0) {
         dayProgress = 0.0;
      }

      if (dayProgress > 1.0) {
         dayProgress = 0.0;
      }

      return Math.sin(dayProgress * Math.PI) * 90.0;
   }

   private void updateUI(LocalDateTime time, double azimuth, double elevation, SunPositionTracker.FeedbackEvent latestFeedback) {
      DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
      this.timeLabel.setText("Time: " + time.format(formatter));
      this.azimuthLabel.setText(String.format("Azimuth: %.2f degrees", azimuth));
      this.elevationLabel.setText(String.format("Elevation: %.2f degrees", elevation));
      this.updateChartData(azimuth, elevation, time);
      if (latestFeedback != null) {
         LocalDateTime feedbackTime = latestFeedback.timestamp;
         if (feedbackTime.isAfter(time.minusMinutes(5L))) {
            this.userFeedbackLabel
               .setText(
                  String.format(
                     "Recent feedback at %s: %s (Pulses: %d, Intensity: %d)",
                     feedbackTime.format(formatter),
                     latestFeedback.type,
                     latestFeedback.pulses,
                     latestFeedback.intensity
                  )
               );
            this.userFeedbackLabel.setTextFill(Color.RED);
         } else {
            this.userFeedbackLabel.setText(String.format("Last feedback at %s: %s", feedbackTime.format(formatter), latestFeedback.type));
            this.userFeedbackLabel.setTextFill(Color.BLUE);
         }
      }
   }

   private void updateChartData(double azimuth, double elevation, LocalDateTime time) {
      this.sunTrajectory.getData().clear();
      LocalDateTime today = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);

      for (SunPositionTracker.SunPosition pos : this.sunPositionHistory) {
         if (pos.timestamp.isAfter(today)) {
            Data<Number, Number> point = new Data(pos.azimuth, pos.elevation);
            this.sunTrajectory.getData().add(point);
         }
      }

      this.currentPosition.getData().clear();
      Data<Number, Number> currentPoint = new Data(azimuth, elevation);
      this.currentPosition.getData().add(currentPoint);
      Platform.runLater(
         () -> {
            if (currentPoint.getNode() != null) {
               currentPoint.getNode().setStyle("-fx-background-color: gold; -fx-background-radius: 10px; -fx-padding: 5px;");
               currentPoint.getNode().setScaleX(1.5);
               currentPoint.getNode().setScaleY(1.5);
               Tooltip tooltip = new Tooltip(
                  String.format(
                     "Current Position\nTime: %s\nAzimuth: %.2f°\nElevation: %.2f°", time.format(DateTimeFormatter.ofPattern("HH:mm:ss")), azimuth, elevation
                  )
               );
               tooltip.setShowDelay(Duration.millis(50.0));
               Tooltip.install(currentPoint.getNode(), tooltip);
            }
         }
      );
      this.feedbackPoints.getData().clear();

      for (SunPositionTracker.FeedbackEvent event : this.feedbackEvents) {
         if (event.timestamp.isAfter(today)) {
            Data<Number, Number> point = new Data(event.azimuth, event.elevation);
            point.setExtraValue(
               String.format(
                  "Feedback: %s\nTime: %s\nAzimuth: %.2f°\nElevation: %.2f°\nPulses: %d\nIntensity: %d",
                  event.type,
                  event.timestamp.format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                  event.azimuth,
                  event.elevation,
                  event.pulses,
                  event.intensity
               )
            );
            this.feedbackPoints.getData().add(point);
         }
      }

      Platform.runLater(() -> {
         for (Data<Number, Number> pointx : this.feedbackPoints.getData()) {
            if (pointx.getNode() != null) {
               pointx.getNode().setStyle("-fx-background-color: red; -fx-background-radius: 6px;");
               pointx.getNode().setScaleX(1.2);
               pointx.getNode().setScaleY(1.2);
               Tooltip tooltip = new Tooltip((String)pointx.getExtraValue());
               tooltip.setShowDelay(Duration.millis(50.0));
               Tooltip.install(pointx.getNode(), tooltip);
            }
         }
      });
   }

   private static class FeedbackEvent {
      private final double azimuth;
      private final double elevation;
      private final LocalDateTime timestamp;
      private final String type;
      private final int pulses;
      private final int intensity;

      public FeedbackEvent(double azimuth, double elevation, LocalDateTime timestamp, String type, int pulses, int intensity) {
         this.azimuth = azimuth;
         this.elevation = elevation;
         this.timestamp = timestamp;
         this.type = type;
         this.pulses = pulses;
         this.intensity = intensity;
      }
   }

   private static class SunPosition {
      private final double azimuth;
      private final double elevation;
      private final LocalDateTime timestamp;

      public SunPosition(double azimuth, double elevation, LocalDateTime timestamp) {
         this.azimuth = azimuth;
         this.elevation = elevation;
         this.timestamp = timestamp;
      }
   }
}
