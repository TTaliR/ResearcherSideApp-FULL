package com.example.demo.controller;

import com.example.demo.model.AzimuthRange;
import com.example.demo.model.HeartRateRange;
import com.example.demo.model.SunMoonThreshold;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.Tooltip;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.TextFormatter.Change;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class ResearcherController {
   @FXML
   private RadioButton heartRateRadio;
   @FXML
   private RadioButton sunPositionRadio;
   @FXML
   private RadioButton moonPositionRadio;
   @FXML
   private ComboBox<String> monitoringComboBox;
   @FXML
   private CheckBox sunPositionToggle;
   @FXML
   private CheckBox sunsetToggle;
   @FXML
   private TextField sunsetThreshold;
   @FXML
   private TextField heartRateThresholdField;
   @FXML
   private Button heartRateSaveButton;
   @FXML
   private CheckBox sunriseToggle;
   @FXML
   private TextField sunriseThreshold;
   @FXML
   private CheckBox moonPositionToggle;
   @FXML
   private TextField moonAzimuthThreshold;
   @FXML
   private Label activeParticipants;
   @FXML
   private Label dataPointsCollected;
   @FXML
   private LineChart<String, Number> dataCollectionChart;
   @FXML
   private VBox sunAzimuthRangesBox;
   @FXML
   private VBox moonAzimuthRangesBox;
   @FXML
   private TextField sunAzimuthMin;
   @FXML
   private TextField sunAzimuthMax;
   @FXML
   private Button sunDataSave;
   @FXML
   private VBox heartRateMappingsBox;
   @FXML
   private Button addHeartRateMappingButton;
   @FXML
   private Button saveHeartRateMappingsButton;
   String jsonInputString;
   private final List<HeartRateRange.HeartRateThresholdMapping> heartRateMappings = new ArrayList<>();
   private final List<HeartRateRange.HeartRateThresholdMapping> heartRateMappingsTest = new ArrayList<>();
   @FXML
   private VBox savedMappingsBox;
   private final List<AzimuthRange> sunRangeInputs = new ArrayList<>();
   private final List<AzimuthRange> sunRangeInputsTest = new ArrayList<>();
   private final List<AzimuthRange> moonRangeInputsTest = new ArrayList<>();
   private boolean fetchConfigurationClicked = false;

   //ngrok link base:
   private final String URL_BASE = /*"https://marcella-unguerdoned-ayanna.ngrok-free.dev"*/ "http://localhost:5678" + "/webhook";   //tali

   public void initialize() {
      this.populateMonitorTypes();
   }

   @FXML
   public void sendMonitoringType() {
      String selectedType = (String)this.monitoringComboBox.getSelectionModel().getSelectedItem();
      Map<String, String> requestBody = new HashMap<>();
      requestBody.put("monitoringType", selectedType);
      if (this.postJson(requestBody,  URL_BASE+"/set-monitoring-type", "Monitoring type sent successfully!")) {
         this.showAlert("Success", "Data Source was successfully sent");
      } else {
         this.showAlert("Failure", "An Error was encountered while sending the data source");
      }
   }

   private boolean sendSunRangeMappings() {
      if (!this.handleSaveSunData()) {
         return false;
      } else {
         SunMoonThreshold sunThreshold = new SunMoonThreshold();
         sunThreshold.setSunAzimuthRanges(new ArrayList<>(this.sunRangeInputsTest));

         for (HeartRateRange.HeartRateThresholdMapping p : this.heartRateMappingsTest) {
            System.out.println(p + " FROM SEND SUN RANGE MAPPING 1");
         }

         this.postJson(sunThreshold, URL_BASE+"/set-sun-azimuth-threshold", "Sun Azimuth Ranges sent successfully!");

         for (HeartRateRange.HeartRateThresholdMapping p : this.heartRateMappingsTest) {
            System.out.println(p + " FROM SEND SUN RANGE MAPPING 2");
         }

         this.removeInvisibleNodes(this.sunAzimuthRangesBox);
         return true;
      }
   }

   private boolean sendMoonRangeMappings() {
      if (!this.handleSaveMoonData()) {
         return false;
      } else {
         SunMoonThreshold moonThreshold = new SunMoonThreshold();
         moonThreshold.setMoonAzimuthRanges(new ArrayList<>(this.moonRangeInputsTest));
         this.postJson(moonThreshold, URL_BASE+"/set-moon-azimuth-threshold", "Moon Azimuth Ranges sent successfully!");

         for (HeartRateRange.HeartRateThresholdMapping p : this.heartRateMappingsTest) {
            System.out.println(p + " FROM SEND SUN RANGE MAPPING 2");
         }

         this.removeInvisibleNodes(this.moonAzimuthRangesBox);
         return true;
      }
   }

   private boolean sendHeartRateMappings() {
      for (HeartRateRange.HeartRateThresholdMapping p : this.heartRateMappingsTest) {
         System.out.println(p + " FROM SEND HEART RATE MAPPINGS");
      }

      if (this.saveHeartRateMappings()) {
         HeartRateRange heartThreshold = new HeartRateRange();
         heartThreshold.setThresholds(new ArrayList<>(this.heartRateMappingsTest));
         this.postJson(heartThreshold, URL_BASE+"/set-heart-rate-threshold", "Heart Rate mappings sent successfully!");
         this.removeInvisibleNodes(this.heartRateMappingsBox);
         return true;
      } else {
         return false;
      }
   }

   private void removeInvisibleNodes(Pane container) {
      Iterator<Node> iterator = container.getChildren().iterator();

      while (iterator.hasNext()) {
         Node node = iterator.next();
         if (!node.isVisible()) {
            iterator.remove();
         }
      }

      for (HeartRateRange.HeartRateThresholdMapping p : this.heartRateMappingsTest) {
         System.out.println(p + " FROM remove invisible nodes ");
      }
   }

   @FXML
   public void saveConfigurationsToBackend(ActionEvent event) {
      try {
         for (HeartRateRange.HeartRateThresholdMapping p : this.heartRateMappingsTest) {
            System.out.println(p + " FROM SAVE CONFIG");
         }

         Boolean sendSunRangeMappings = this.sendSunRangeMappings();
         Boolean sendHeartRateMappings = this.sendHeartRateMappings();
         Boolean sendMoonRangeMappings = this.sendMoonRangeMappings();
         List<String> successList = new ArrayList<>();
         if (sendSunRangeMappings) {
            successList.add("Sun Rules");
         }

         if (sendHeartRateMappings) {
            successList.add("Heart Rate Rules");
         }

         if (sendMoonRangeMappings) {
            successList.add("Moon Rules");
         }

         if (!successList.isEmpty()) {
            String message = String.join(", ", successList) + " configuration" + (successList.size() == 1 ? " was" : "s were") + " saved successfully!";
            this.showAlert("Success", message);
            this.fetchCurrentConfigurationsWithoutMessage();
         } else {
            this.showAlert("Error", "❌ None of the configurations were saved.");
         }
      } catch (Exception var7) {
         var7.printStackTrace();
         this.showAlert("Error", "An error occurred while saving configurations: " + var7.getMessage());
      }
   }

   private HBox createSunAzimuthInput(String metricName) {
      HBox rangeInput = new HBox(10.0);
      TextField azimuthRangeField = this.createLabeledField("Azimuth Range", 130.0, "Sun azimuth range, e.g., 0-180 (0° = North, 180° = South)");
      TextField pulsesField = this.createLabeledField("Pulses", 80.0, "Range of vibration pulses, e.g., 2-4.");
      TextField intensityField = this.createLabeledField("Intensity", 80.0, "Intensity range, e.g., 2-4.");
      TextField durationField = this.createLabeledField("Duration (ms)", 100.0, "Duration range in ms, e.g., 100-200.");
      TextField intervalField = this.createLabeledField("Interval (ms)", 100.0, "Interval range in ms, e.g., 50-150.");
      Runnable checkDuplicate = () -> {
         String azimuth = azimuthRangeField.getText();
         String pulses = pulsesField.getText();
         String intensity = intensityField.getText();
         String duration = durationField.getText();
         String interval = intervalField.getText();
         if (!azimuth.isEmpty() && !pulses.isEmpty() && !intensity.isEmpty() && !duration.isEmpty() && !interval.isEmpty()) {
            boolean isDuplicate = this.sunAzimuthRangesBox
                    .getChildren()
                    .stream()
                    .filter(node -> node instanceof HBox && node != rangeInput && node.isVisible())
                    .map(node -> (HBox)node)
                    .anyMatch(
                            existing -> {
                               try {
                                  return azimuth.equals(((TextField)existing.getChildren().get(0)).getText())
                                          && pulses.equals(((TextField)existing.getChildren().get(1)).getText())
                                          && intensity.equals(((TextField)existing.getChildren().get(2)).getText())
                                          && duration.equals(((TextField)existing.getChildren().get(3)).getText())
                                          && interval.equals(((TextField)existing.getChildren().get(4)).getText());
                               } catch (Exception var7x) {
                                  return false;
                               }
                            }
                    );
            if (isDuplicate) {
               this.showAlert("Duplicate Mapping", "This Sun Azimuth range already exists.");
               intervalField.clear();
            }
         }
      };
      Stream.of(azimuthRangeField, pulsesField, intensityField, durationField, intervalField)
              .forEach(field -> field.focusedProperty().addListener((obs, wasFocused, nowFocused) -> {
                 if (!nowFocused) {
                    checkDuplicate.run();
                 }
              }));
      Button deleteButton = new Button("❌");
      deleteButton.setStyle("-fx-background-color: #dc3545; -fx-text-fill: white;");
      deleteButton.setOnAction(e -> this.sunAzimuthRangesBox.getChildren().remove(rangeInput));
      rangeInput.getChildren().addAll(new Node[]{azimuthRangeField, pulsesField, intensityField, durationField, intervalField, deleteButton});
      return rangeInput;
   }

   private int[] parseRange(String text, int maxAllowed) {
      if (text != null && !text.trim().isEmpty()) {
         String trimmed = text.trim();
         if (!trimmed.matches("^\\d+$") && !trimmed.matches("^\\d+\\s*-\\s*\\d+$")) {
            throw new IllegalArgumentException("Invalid format. Use a single number or a range like 10-30.");
         } else {
            try {
               int min;
               int max;
               if (trimmed.matches("^\\d+$")) {
                  min = max = Integer.parseInt(trimmed);
               } else {
                  String[] parts = trimmed.split("-");
                  min = Integer.parseInt(parts[0].trim());
                  max = Integer.parseInt(parts[1].trim());
                  if (min > max) {
                     throw new IllegalArgumentException("Minimum value cannot be greater than maximum.");
                  }
               }

               if (max > maxAllowed) {
                  throw new IllegalArgumentException("Maximum value cannot exceed " + maxAllowed + ".");
               } else {
                  return new int[]{min, max};
               }
            } catch (NumberFormatException var7) {
               throw new IllegalArgumentException("Values must be valid integers.");
            }
         }
      } else {
         throw new IllegalArgumentException("Field cannot be empty.");
      }
   }

   @FXML
   private void addSunAzimuthRange() {
      if (!this.fetchConfigurationClicked) {
         this.showAlert("Action Blocked", "Please click 'Fetch Current' before adding new mappings.");
      } else {
         HBox newRange = this.createSunAzimuthInput("Sun");
         if (newRange != null) {
            this.sunAzimuthRangesBox.getChildren().add(newRange);
         }
      }
   }

   @FXML
   private boolean handleSaveSunData() {
      System.out.println("\ud83d\udd01 Updating visibility and saving new Sun Azimuth Ranges...");
      this.sunRangeInputs.clear();

      for (int i = 0; i < this.sunRangeInputsTest.size(); i++) {
         Node node = (Node)this.sunAzimuthRangesBox.getChildren().get(i);
         if (node instanceof HBox) {
            AzimuthRange range = this.sunRangeInputsTest.get(i);
            range.setActive(node.isVisible());
         }
      }

      for (int ix = this.sunRangeInputsTest.size(); ix < this.sunAzimuthRangesBox.getChildren().size(); ix++) {
         Node node = (Node)this.sunAzimuthRangesBox.getChildren().get(ix);
         if (node instanceof HBox rangeInput) {
            try {
               TextField azimuthRangeField = (TextField)rangeInput.getChildren().get(0);
               TextField pulsesField = (TextField)rangeInput.getChildren().get(1);
               TextField intensityField = (TextField)rangeInput.getChildren().get(2);
               TextField durationField = (TextField)rangeInput.getChildren().get(3);
               TextField intervalField = (TextField)rangeInput.getChildren().get(4);
               if (azimuthRangeField.getText().isEmpty()
                       || pulsesField.getText().isEmpty()
                       || intensityField.getText().isEmpty()
                       || durationField.getText().isEmpty()
                       || intervalField.getText().isEmpty()) {
                  this.showAlert("Validation Error", "Please fill in all fields before saving.");
                  return false;
               }

               int[] azimuthRange = this.parseRange(azimuthRangeField.getText(), Integer.MAX_VALUE);
               int[] pulsesRange = this.parseRange(pulsesField.getText(), Integer.MAX_VALUE);
               int[] intensityRange = this.parseRange(intensityField.getText(), 255);
               int[] durationRange = this.parseRange(durationField.getText(), Integer.MAX_VALUE);
               int[] intervalRange = this.parseRange(intervalField.getText(), Integer.MAX_VALUE);
               AzimuthRange azimuthRangeObj = new AzimuthRange();
               azimuthRangeObj.setMinAzimuth(azimuthRange[0]);
               azimuthRangeObj.setMaxAzimuth(azimuthRange[1]);
               azimuthRangeObj.setMinPulses(pulsesRange[0]);
               azimuthRangeObj.setMaxPulses(pulsesRange[1]);
               azimuthRangeObj.setMinIntensity(intensityRange[0]);
               azimuthRangeObj.setMaxIntensity(intensityRange[1]);
               azimuthRangeObj.setMinDuration(durationRange[0]);
               azimuthRangeObj.setMaxDuration(durationRange[1]);
               azimuthRangeObj.setMinInterval(intervalRange[0]);
               azimuthRangeObj.setMaxInterval(intervalRange[1]);
               azimuthRangeObj.setActive(rangeInput.isVisible());
               this.sunRangeInputs.add(azimuthRangeObj);
               this.sunRangeInputsTest.add(azimuthRangeObj);
            } catch (IllegalArgumentException var15) {
               this.showAlert("Input Validation Error", var15.getMessage());
               return false;
            } catch (Exception var16) {
               this.showAlert("Unexpected Error", "An unknown error occurred while saving the input.");
               var16.printStackTrace();
               return false;
            }
         }
      }

      return true;
   }

   private HBox createMoonAzimuthInput(String metricName) {
      HBox rangeInput = new HBox(10.0);
      TextField azimuthRangeField = this.createLabeledField("Azimuth Range", 130.0, "Moon azimuth range, e.g., 0-180 (0° = North, 180° = South)");
      TextField pulsesField = this.createLabeledField("Pulses", 80.0, "Range of vibration pulses, e.g., 2-4.");
      TextField intensityField = this.createLabeledField("Intensity", 80.0, "Intensity range, e.g., 2-4.");
      TextField durationField = this.createLabeledField("Duration (ms)", 100.0, "Duration range in ms, e.g., 100-200.");
      TextField intervalField = this.createLabeledField("Interval (ms)", 100.0, "Interval range in ms, e.g., 50-150.");
      Runnable checkDuplicate = () -> {
         String azimuth = azimuthRangeField.getText();
         String pulses = pulsesField.getText();
         String intensity = intensityField.getText();
         String duration = durationField.getText();
         String interval = intervalField.getText();
         if (!azimuth.isEmpty() && !pulses.isEmpty() && !intensity.isEmpty() && !duration.isEmpty() && !interval.isEmpty()) {
            boolean isDuplicate = this.moonAzimuthRangesBox
                    .getChildren()
                    .stream()
                    .filter(node -> node instanceof HBox && node != rangeInput && node.isVisible())
                    .map(node -> (HBox)node)
                    .anyMatch(
                            existing -> {
                               try {
                                  return azimuth.equals(((TextField)existing.getChildren().get(0)).getText())
                                          && pulses.equals(((TextField)existing.getChildren().get(1)).getText())
                                          && intensity.equals(((TextField)existing.getChildren().get(2)).getText())
                                          && duration.equals(((TextField)existing.getChildren().get(3)).getText())
                                          && interval.equals(((TextField)existing.getChildren().get(4)).getText());
                               } catch (Exception var7x) {
                                  return false;
                               }
                            }
                    );
            if (isDuplicate) {
               this.showAlert("Duplicate Mapping", "This Moon Azimuth range already exists.");
               intervalField.clear();
            }
         }
      };
      Stream.of(azimuthRangeField, pulsesField, intensityField, durationField, intervalField)
              .forEach(field -> field.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
                 if (!isFocused) {
                    checkDuplicate.run();
                 }
              }));
      Button deleteButton = new Button("❌");
      deleteButton.setStyle("-fx-background-color: #dc3545; -fx-text-fill: white;");
      deleteButton.setOnAction(e -> this.moonAzimuthRangesBox.getChildren().remove(rangeInput));
      rangeInput.getChildren().addAll(new Node[]{azimuthRangeField, pulsesField, intensityField, durationField, intervalField, deleteButton});
      return rangeInput;
   }

   @FXML
   private void addMoonAzimuthRange() {
      if (!this.fetchConfigurationClicked) {
         this.showAlert("Action Blocked", "Please click 'Fetch Current' before adding new mappings.");
      } else {
         HBox newRange = this.createMoonAzimuthInput("Moon");
         if (newRange != null) {
            this.moonAzimuthRangesBox.getChildren().add(newRange);
         }
      }
   }

   @FXML
   private boolean handleSaveMoonData() {
      System.out.println("\ud83d\udd01 Updating visibility and saving new Moon Azimuth Ranges...");

      for (int i = 0; i < this.moonRangeInputsTest.size(); i++) {
         Node node = (Node)this.moonAzimuthRangesBox.getChildren().get(i);
         if (node instanceof HBox) {
            AzimuthRange range = this.moonRangeInputsTest.get(i);
            range.setActive(node.isVisible());
         }
      }

      for (int ix = this.moonRangeInputsTest.size(); ix < this.moonAzimuthRangesBox.getChildren().size(); ix++) {
         Node node = (Node)this.moonAzimuthRangesBox.getChildren().get(ix);
         if (node instanceof HBox rangeInput) {
            try {
               TextField azimuthRangeField = (TextField)rangeInput.getChildren().get(0);
               TextField pulsesField = (TextField)rangeInput.getChildren().get(1);
               TextField intensityField = (TextField)rangeInput.getChildren().get(2);
               TextField durationField = (TextField)rangeInput.getChildren().get(3);
               TextField intervalField = (TextField)rangeInput.getChildren().get(4);
               if (azimuthRangeField.getText().isEmpty()
                       || pulsesField.getText().isEmpty()
                       || intensityField.getText().isEmpty()
                       || durationField.getText().isEmpty()
                       || intervalField.getText().isEmpty()) {
                  this.showAlert("Validation Error", "Please fill in all fields before saving.");
                  return false;
               }

               int[] azimuthRange = this.parseRange(azimuthRangeField.getText(), Integer.MAX_VALUE);
               int[] pulsesRange = this.parseRange(pulsesField.getText(), Integer.MAX_VALUE);
               int[] intensityRange = this.parseRange(intensityField.getText(), 255);
               int[] durationRange = this.parseRange(durationField.getText(), Integer.MAX_VALUE);
               int[] intervalRange = this.parseRange(intervalField.getText(), Integer.MAX_VALUE);
               AzimuthRange model = new AzimuthRange();
               model.setMinAzimuth(azimuthRange[0]);
               model.setMaxAzimuth(azimuthRange[1]);
               model.setMinPulses(pulsesRange[0]);
               model.setMaxPulses(pulsesRange[1]);
               model.setMinIntensity(intensityRange[0]);
               model.setMaxIntensity(intensityRange[1]);
               model.setMinDuration(durationRange[0]);
               model.setMaxDuration(durationRange[1]);
               model.setMinInterval(intervalRange[0]);
               model.setMaxInterval(intervalRange[1]);
               model.setActive(rangeInput.isVisible());
               this.moonRangeInputsTest.add(model);
            } catch (IllegalArgumentException var15) {
               this.showAlert("Input Validation Error", var15.getMessage());
               return false;
            } catch (Exception var16) {
               this.showAlert("Unexpected Error", "An unknown error occurred while saving the input.");
               var16.printStackTrace();
               return false;
            }
         }
      }

      return true;
   }

   @FXML
   private void addHeartRateMappingInput() {
      if (!this.fetchConfigurationClicked) {
         this.showAlert("Action Blocked", "Please click 'Fetch Current' before adding new mappings.");
      } else {
         HBox mappingInput = new HBox(10.0);
         TextField heartRateRangeField = this.createLabeledField("Heart Rate Range", 130.0, "Range of heart rate, e.g., 60-100");
         TextField pulsesField = this.createLabeledField("Pulses", 80.0, "Range of vibration pulses, e.g., 2-4");
         TextField intensityField = this.createLabeledField("Intensity", 80.0, "Vibration intensity, e.g., 1-5");
         TextField durationField = this.createLabeledField("Duration (ms)", 100.0, "Pulse duration range in ms, e.g., 100-200");
         TextField intervalField = this.createLabeledField("Interval (ms)", 100.0, "Interval range in ms, e.g., 50-100");
         Runnable checkDuplicate = () -> {
            String hr = heartRateRangeField.getText();
            String pulses = pulsesField.getText();
            String intensity = intensityField.getText();
            String duration = durationField.getText();
            String interval = intervalField.getText();
            if (!hr.isEmpty() && !pulses.isEmpty() && !intensity.isEmpty() && !duration.isEmpty() && !interval.isEmpty()) {
               boolean isDuplicate = this.heartRateMappingsBox
                       .getChildren()
                       .stream()
                       .filter(node -> node instanceof HBox && node != mappingInput && node.isVisible())
                       .map(node -> (HBox)node)
                       .anyMatch(
                               existing -> {
                                  try {
                                     return hr.equals(((TextField)existing.getChildren().get(0)).getText())
                                             && pulses.equals(((TextField)existing.getChildren().get(1)).getText())
                                             && intensity.equals(((TextField)existing.getChildren().get(2)).getText())
                                             && duration.equals(((TextField)existing.getChildren().get(3)).getText())
                                             && interval.equals(((TextField)existing.getChildren().get(4)).getText());
                                  } catch (Exception var7x) {
                                     return false;
                                  }
                               }
                       );
               if (isDuplicate) {
                  this.showAlert("Duplicate Mapping", "This heart rate mapping already exists.");
                  intervalField.clear();
               }
            }
         };
         Stream.of(heartRateRangeField, pulsesField, intensityField, durationField, intervalField)
                 .forEach(field -> field.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
                    if (!isFocused) {
                       checkDuplicate.run();
                    }
                 }));
         Button deleteButton = new Button("❌");
         deleteButton.setStyle("-fx-background-color: #dc3545; -fx-text-fill: white;");
         deleteButton.setOnAction(e -> this.heartRateMappingsBox.getChildren().remove(mappingInput));
         mappingInput.getChildren().addAll(new Node[]{heartRateRangeField, pulsesField, intensityField, durationField, intervalField, deleteButton});
         this.heartRateMappingsBox.getChildren().add(mappingInput);
      }
   }

   @FXML
   private Boolean saveHeartRateMappings() {
      System.out.println("\ud83d\udd01 Updating visibility and saving new Heart Rate Mappings...");
      this.heartRateMappings.clear();

      for (int i = 0; i < this.heartRateMappingsTest.size(); i++) {
         Node node = (Node)this.heartRateMappingsBox.getChildren().get(i);
         if (node instanceof HBox) {
            HeartRateRange.HeartRateThresholdMapping existing = this.heartRateMappingsTest.get(i);
            existing.setActive(node.isVisible());
         }
      }

      for (int ix = this.heartRateMappingsTest.size(); ix < this.heartRateMappingsBox.getChildren().size(); ix++) {
         Node node = (Node)this.heartRateMappingsBox.getChildren().get(ix);
         if (node instanceof HBox input) {
            TextField heartRateField = (TextField)input.getChildren().get(0);
            TextField pulsesField = (TextField)input.getChildren().get(1);
            TextField intensityField = (TextField)input.getChildren().get(2);
            TextField durationField = (TextField)input.getChildren().get(3);
            TextField intervalField = (TextField)input.getChildren().get(4);
            if (heartRateField.getText().isEmpty()
                    || pulsesField.getText().isEmpty()
                    || intensityField.getText().isEmpty()
                    || durationField.getText().isEmpty()
                    || intervalField.getText().isEmpty()) {
               this.showAlert("Validation Error", "Please fill in all fields before saving.");
               return false;
            }

            try {
               int[] hrRange = this.parseRange(heartRateField.getText(), Integer.MAX_VALUE);
               int[] pulsesRange = this.parseRange(pulsesField.getText(), Integer.MAX_VALUE);
               int[] intensityRange = this.parseRange(intensityField.getText(), 255);
               int[] durationRange = this.parseRange(durationField.getText(), Integer.MAX_VALUE);
               int[] intervalRange = this.parseRange(intervalField.getText(), Integer.MAX_VALUE);
               HeartRateRange.HeartRateThresholdMapping mapping = new HeartRateRange.HeartRateThresholdMapping(
                       hrRange[0],
                       hrRange[1],
                       intensityRange[0],
                       intensityRange[1],
                       pulsesRange[0],
                       pulsesRange[1],
                       durationRange[0],
                       durationRange[1],
                       intervalRange[0],
                       intervalRange[1]
               );
               mapping.setActive(input.isVisible());
               System.out.println(mapping);
               this.heartRateMappings.add(mapping);
               this.heartRateMappingsTest.add(mapping);
            } catch (IllegalArgumentException var15) {
               this.showAlert("Range Format Error", var15.getMessage());
               return false;
            } catch (Exception var16) {
               var16.printStackTrace();
               this.showAlert("Unexpected Error", "An error occurred while saving: " + var16.getMessage());
               return false;
            }
         }
      }

      return true;
   }

   public void fetchCurrentConfigurationsWithoutMessage() {
      try {
         JsonNode rootNode = this.getJson(URL_BASE+"/current-configurations");
         System.out.println(rootNode);
         this.parseSunAzimuthRanges(rootNode.path("sunAzimuthRanges"));
         this.parseMoonAzimuthRanges(rootNode.path("moonAzimuthRanges"));
         this.parseHeartRateMappings(rootNode.path("heartRateMappings"));
         System.out.println(rootNode.path("heartRateMappings"));
         this.fetchConfigurationClicked = true;
      } catch (Exception var2) {
         var2.printStackTrace();
         this.showAlert("Error", "An error occurred while fetching configurations: " + var2.getMessage());
      }
   }

   public void fetchCurrentConfigurations() {
      try {
         JsonNode rootNode = this.getJson(URL_BASE+"/current-configurations");
         System.out.println(rootNode);
         this.parseSunAzimuthRanges(rootNode.path("sunAzimuthRanges"));
         this.parseMoonAzimuthRanges(rootNode.path("moonAzimuthRanges"));
         this.parseHeartRateMappings(rootNode.path("heartRateMappings"));

         for (HeartRateRange.HeartRateThresholdMapping p : this.heartRateMappingsTest) {
            System.out.println(p + " HERE v2");
         }

         System.out.println(rootNode.path("heartRateMappings"));
         this.showAlert("Current Configurations", "Successfully fetched and loaded configurations.");
         this.fetchConfigurationClicked = true;
      } catch (Exception var4) {
         var4.printStackTrace();
         this.showAlert("Error", "An error occurred while fetching configurations: " + var4.getMessage());
      }
   }

   private void parseSunAzimuthRanges(JsonNode sunAzimuthRanges) {
      this.sunRangeInputsTest.clear();
      this.sunAzimuthRangesBox.getChildren().clear();
      if (sunAzimuthRanges != null && sunAzimuthRanges.isArray()) {
         for (JsonNode range : sunAzimuthRanges) {
            AzimuthRange model = new AzimuthRange();
            model.setId(range.path("id").asInt());
            model.setMinAzimuth(range.path("minvalue").asInt());
            model.setMaxAzimuth(range.path("maxvalue").asInt());
            model.setMinPulses(range.path("minpulses").asInt());
            model.setMaxPulses(range.path("maxpulses").asInt());
            model.setMinIntensity(range.path("minintensity").asInt());
            model.setMaxIntensity(range.path("maxintensity").asInt());
            model.setMinDuration(range.path("minduration").asInt());
            model.setMaxDuration(range.path("maxduration").asInt());
            model.setMinInterval(range.path("mininterval").asInt());
            model.setMaxInterval(range.path("maxinterval").asInt());
            model.setActive(true);
            this.sunRangeInputsTest.add(model);
            HBox row = new HBox(10.0);
            row.setId("sun_range_" + model.getId());
            String azimuthTooltip = "Sun azimuth range, e.g., 0-180 (0° = North, 180° = South)";
            String pulsesTooltip = "Range of vibration pulses, e.g., 2-4.";
            String intensityTooltip = "Intensity range, e.g., 2-4.";
            String durationTooltip = "Duration range in ms, e.g., 100-200.";
            String intervalTooltip = "Interval range in ms, e.g., 50-150.";
            row.getChildren()
                    .addAll(new Node[]{this.bindRangeField(model.getMinAzimuth(), model.getMaxAzimuth(), 130, Integer.MAX_VALUE, azimuthTooltip, (min, max) -> {
                       model.setMinAzimuth(min);
                       model.setMaxAzimuth(max);
                    }), this.bindRangeField(model.getMinPulses(), model.getMaxPulses(), 80, Integer.MAX_VALUE, pulsesTooltip, (min, max) -> {
                       model.setMinPulses(min);
                       model.setMaxPulses(max);
                    }), this.bindRangeField(model.getMinIntensity(), model.getMaxIntensity(), 80, 255, intensityTooltip, (min, max) -> {
                       model.setMinIntensity(min);
                       model.setMaxIntensity(max);
                    }), this.bindRangeField(model.getMinDuration(), model.getMaxDuration(), 100, Integer.MAX_VALUE, durationTooltip, (min, max) -> {
                       model.setMinDuration(min);
                       model.setMaxDuration(max);
                    }), this.bindRangeField(model.getMinInterval(), model.getMaxInterval(), 100, Integer.MAX_VALUE, intervalTooltip, (min, max) -> {
                       model.setMinInterval(min);
                       model.setMaxInterval(max);
                    }), this.createDeleteButton(row, () -> model.setActive(false))});
            this.sunAzimuthRangesBox.getChildren().add(row);
         }
      }
   }

   private void parseMoonAzimuthRanges(JsonNode moonAzimuthRanges) {
      this.moonRangeInputsTest.clear();
      this.moonAzimuthRangesBox.getChildren().clear();
      if (moonAzimuthRanges != null && moonAzimuthRanges.isArray()) {
         for (JsonNode range : moonAzimuthRanges) {
            AzimuthRange model = new AzimuthRange();
            model.setId(range.path("id").asInt());
            model.setMinAzimuth(range.path("minvalue").asInt());
            model.setMaxAzimuth(range.path("maxvalue").asInt());
            model.setMinPulses(range.path("minpulses").asInt());
            model.setMaxPulses(range.path("maxpulses").asInt());
            model.setMinIntensity(range.path("minintensity").asInt());
            model.setMaxIntensity(range.path("maxintensity").asInt());
            model.setMinDuration(range.path("minduration").asInt());
            model.setMaxDuration(range.path("maxduration").asInt());
            model.setMinInterval(range.path("mininterval").asInt());
            model.setMaxInterval(range.path("maxinterval").asInt());
            model.setActive(true);
            this.moonRangeInputsTest.add(model);
            HBox row = new HBox(10.0);
            row.setId("moon_range_" + model.getId());
            String azimuthTooltip = "Moon azimuth range, e.g., 0-180 (0° = North, 180° = South)";
            String pulsesTooltip = "Range of vibration pulses, e.g., 2-4.";
            String intensityTooltip = "Intensity range, e.g., 2-4.";
            String durationTooltip = "Duration range in ms, e.g., 100-200.";
            String intervalTooltip = "Interval range in ms, e.g., 50-150.";
            row.getChildren()
                    .addAll(new Node[]{this.bindRangeField(model.getMinAzimuth(), model.getMaxAzimuth(), 130, Integer.MAX_VALUE, azimuthTooltip, (min, max) -> {
                       model.setMinAzimuth(min);
                       model.setMaxAzimuth(max);
                    }), this.bindRangeField(model.getMinPulses(), model.getMaxPulses(), 80, Integer.MAX_VALUE, pulsesTooltip, (min, max) -> {
                       model.setMinPulses(min);
                       model.setMaxPulses(max);
                    }), this.bindRangeField(model.getMinIntensity(), model.getMaxIntensity(), 80, Integer.MAX_VALUE, intensityTooltip, (min, max) -> {
                       model.setMinIntensity(min);
                       model.setMaxIntensity(max);
                    }), this.bindRangeField(model.getMinDuration(), model.getMaxDuration(), 100, Integer.MAX_VALUE, durationTooltip, (min, max) -> {
                       model.setMinDuration(min);
                       model.setMaxDuration(max);
                    }), this.bindRangeField(model.getMinInterval(), model.getMaxInterval(), 100, Integer.MAX_VALUE, intervalTooltip, (min, max) -> {
                       model.setMinInterval(min);
                       model.setMaxInterval(max);
                    }), this.createDeleteButton(row, () -> model.setActive(false))});
            this.moonAzimuthRangesBox.getChildren().add(row);
         }
      }
   }

   private void parseHeartRateMappings(JsonNode heartRateMappingsNode) {
      this.heartRateMappingsTest.clear();
      this.heartRateMappingsBox.getChildren().clear();
      if (heartRateMappingsNode != null && heartRateMappingsNode.isArray()) {
         for (JsonNode mapping : heartRateMappingsNode) {
            HeartRateRange.HeartRateThresholdMapping model = new HeartRateRange.HeartRateThresholdMapping();
            model.setId(mapping.path("id").asInt());
            model.setMin(mapping.path("minvalue").asInt());
            model.setMax(mapping.path("maxvalue").asInt());
            model.setMinPulses(mapping.path("minpulses").asInt());
            model.setMaxPulses(mapping.path("maxpulses").asInt());
            model.setMinIntensity(mapping.path("minintensity").asInt());
            model.setMaxIntensity(mapping.path("maxintensity").asInt());
            model.setMinDuration(mapping.path("minduration").asInt());
            model.setMaxDuration(mapping.path("maxduration").asInt());
            model.setMinInterval(mapping.path("mininterval").asInt());
            model.setMaxInterval(mapping.path("maxinterval").asInt());
            model.setActive(true);
            this.heartRateMappingsTest.add(model);
            HBox row = new HBox(10.0);
            row.setId("hr_range_" + model.getId());
            String heartTooltip = "Heart Rate range, e.g., 0-180 (0° = North, 180° = South)";
            String pulsesTooltip = "Range of vibration pulses, e.g., 2-4.";
            String intensityTooltip = "Intensity range, e.g., 2-4.";
            String durationTooltip = "Duration range in ms, e.g., 100-200.";
            String intervalTooltip = "Interval range in ms, e.g., 50-150.";
            row.getChildren().addAll(new Node[]{this.bindRangeField(model.getMin(), model.getMax(), 130, Integer.MAX_VALUE, heartTooltip, (min, max) -> {
               model.setMin(min);
               model.setMax(max);
            }), this.bindRangeField(model.getMinPulses(), model.getMaxPulses(), 80, Integer.MAX_VALUE, pulsesTooltip, (min, max) -> {
               model.setMinPulses(min);
               model.setMaxPulses(max);
            }), this.bindRangeField(model.getMinIntensity(), model.getMaxIntensity(), 80, 255, intensityTooltip, (min, max) -> {
               model.setMinIntensity(min);
               model.setMaxIntensity(max);
            }), this.bindRangeField(model.getMinDuration(), model.getMaxDuration(), 100, Integer.MAX_VALUE, durationTooltip, (min, max) -> {
               model.setMinDuration(min);
               model.setMaxDuration(max);
            }), this.bindRangeField(model.getMinInterval(), model.getMaxInterval(), 100, Integer.MAX_VALUE, intervalTooltip, (min, max) -> {
               model.setMinInterval(min);
               model.setMaxInterval(max);
            }), this.createDeleteButton(row, () -> model.setActive(false))});
            this.heartRateMappingsBox.getChildren().add(row);
         }
      }
   }

   private boolean postJson(Object data, String endpointUrl, String successMessage) {
      HttpURLConnection connection = null;
      try {
         String jsonInputString = new ObjectMapper().writeValueAsString(data);
         System.out.println("Sending JSON to Node-RED (" + endpointUrl + "): " + jsonInputString);

         URL url = new URL(endpointUrl);
         connection = (HttpURLConnection) url.openConnection();
         connection.setRequestMethod("POST");
         connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
         connection.setDoOutput(true);

         try (OutputStream os = connection.getOutputStream()) {
            os.write(jsonInputString.getBytes(java.nio.charset.StandardCharsets.UTF_8));
         }

         int responseCode = connection.getResponseCode();
         if (responseCode != 200) {
            System.out.println("Failed to send data. Response code: " + responseCode);
            return false;
         }

         System.out.println(successMessage);
         return true;

      } catch (Exception e) {
         e.printStackTrace();
         showAlert("Error", "An error occurred: " + e.getMessage());
         return false;
      } finally {
         if (connection != null) connection.disconnect();
      }
   }


   private void enforceIntegerInput(TextField textField) {
      UnaryOperator<Change> rangeFilter = change -> {
         String newText = change.getControlNewText();
         return !newText.matches("\\d*") && !newText.matches("\\d+-?\\d*") ? null : change;
      };
      textField.setTextFormatter(new TextFormatter(rangeFilter));
   }

   private JsonNode getJson(String urlString) throws IOException {
      HttpURLConnection connection = null;
      try {
         URL url = new URL(urlString);
         connection = (HttpURLConnection) url.openConnection();
         connection.setRequestMethod("GET");
         connection.setRequestProperty("Accept", "application/json");

         int responseCode = connection.getResponseCode();
         if (responseCode != 200) {
            throw new IOException("Failed with HTTP code: " + responseCode);
         }

         StringBuilder response = new StringBuilder();
         try (BufferedReader reader = new BufferedReader(
                 new InputStreamReader(connection.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
               response.append(line.trim());
            }
         }

         return new ObjectMapper().readTree(response.toString());
      } finally {
         if (connection != null) connection.disconnect();
      }
   }


   private TextField bindRangeField(int min, int max, int width, int maxAllowed, String tooltipLabel, BiConsumer<Integer, Integer> setter) {
      TextField field = new TextField(min + "-" + max);
      field.setPrefWidth(width);
      field.setDisable(true);
      String tooltipText = "Range of " + tooltipLabel + ", e.g., " + min + "-" + max;
      if (maxAllowed < Integer.MAX_VALUE) {
         tooltipText = tooltipText + ". Max allowed: " + maxAllowed;
      }

      Tooltip.install(field, new Tooltip(tooltipText));
      field.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
         if (!isFocused) {
            try {
               int[] parsed = this.parseRange(field.getText(), maxAllowed);
               setter.accept(parsed[0], parsed[1]);
            } catch (IllegalArgumentException var10) {
               this.showAlert("Invalid Range Format", var10.getMessage());
               field.setText(min + "-" + max);
            }
         }
      });
      return field;
   }

   private Button createDeleteButton(HBox container, Runnable onDelete) {
      Button deleteButton = new Button("❌");
      deleteButton.setStyle("-fx-background-color: #dc3545; -fx-text-fill: white;");
      deleteButton.setOnAction(e -> {
         container.setVisible(false);
         container.setManaged(false);
         onDelete.run();
      });
      return deleteButton;
   }

   private TextField createLabeledField(String prompt, double width, String tooltipText) {
      TextField field = new TextField();
      field.setPromptText(prompt);
      field.setPrefWidth(width);
      this.enforceIntegerInput(field);
      Tooltip.install(field, new Tooltip(tooltipText));
      return field;
   }

   private ComboBox<Integer> createLabeledComboBox(String prompt, double width, String tooltipText) {
      ComboBox<Integer> combo = new ComboBox();
      combo.getItems().addAll(new Integer[]{1, 2, 3, 4, 5});
      combo.setPromptText(prompt);
      combo.setPrefWidth(width);
      Tooltip.install(combo, new Tooltip(tooltipText));
      return combo;
   }

   private void populateMonitorTypes() {
      this.monitoringComboBox.getItems().addAll(new String[]{"HeartRate", "SunAzimuth", "MoonAzimuth"});
   }

   private void showAlert(String title, String message) {
      Alert alert = new Alert(AlertType.INFORMATION);
      alert.setTitle(title);
      alert.setContentText(message);
      alert.showAndWait();
   }

   @FXML
   private void goToVisPage(ActionEvent event) {
      try {
         FXMLLoader loader = new FXMLLoader(this.getClass().getResource("/com/example/demo/view/visPage.fxml"));
         Parent visPageRoot = (Parent)loader.load();
         Stage stage = (Stage)((Node)event.getSource()).getScene().getWindow();
         Scene scene = new Scene(visPageRoot);
         stage.setScene(scene);
         stage.show();
      } catch (IOException var6) {
         var6.printStackTrace();
      }
   }

   @FXML
   private void goToChatBot(ActionEvent event) {
      try {
         FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/demo/view/ChatBotView.fxml"));
         Parent root = loader.load();

         Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
         Scene scene = new Scene(root);
         stage.setScene(scene);
         stage.show();
      } catch (IOException e) {
         e.printStackTrace();
         showAlert("Navigation Error", "Could not load ChatBot page.");
      }
   }
}