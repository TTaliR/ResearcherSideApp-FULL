package com.example.demo.controller;

import com.example.demo.model.SensorRuleConfig;
import com.example.demo.service.ApiService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.TextFormatter.Change;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

public class ResearcherController {
   private static final String DEFAULT_HEART_RATE_TYPE = "HeartRate";
   private static final String DEFAULT_SUN_AZIMUTH_TYPE = "SunAzimuth";
   private static final String DEFAULT_MOON_AZIMUTH_TYPE = "MoonAzimuth";
   @FXML
   private RadioButton heartRateRadio;
   @FXML
   private RadioButton sunPositionRadio;
   @FXML
   private RadioButton moonPositionRadio;
   @FXML
   private ComboBox<String> monitoringComboBox;
   @FXML
   private ComboBox<String> ruleSensorTypeComboBox;
   @FXML
   private TextField ruleMinValueField;
   @FXML
   private TextField ruleMaxValueField;
   @FXML
   private TextField ruleMinPulsesField;
   @FXML
   private TextField ruleMaxPulsesField;
   @FXML
   private TextField ruleIntensityField;
   @FXML
   private TextField ruleDurationField;
   @FXML
   private TextField ruleIntervalField;
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
   private final List<SensorRuleConfig> heartRateMappingsTest = new ArrayList<>();
   @FXML
   private VBox savedMappingsBox;
   private final List<SensorRuleConfig> sunRangeInputsTest = new ArrayList<>();
   private final List<SensorRuleConfig> moonRangeInputsTest = new ArrayList<>();
   private final List<String> availableUseCases = new ArrayList<>();
   private boolean fetchConfigurationClicked = false;

   public void initialize() {
      this.populateMonitorTypes();
      this.populateRuleBuilderSensorTypes();
      this.initializeRuleBuilderFieldFilters();
   }

   private void initializeRuleBuilderFieldFilters() {
      if (this.ruleMinValueField != null) {
         this.enforceIntegerInput(this.ruleMinValueField);
      }
      if (this.ruleMaxValueField != null) {
         this.enforceIntegerInput(this.ruleMaxValueField);
      }
      if (this.ruleMinPulsesField != null) {
         this.enforceIntegerInput(this.ruleMinPulsesField);
      }
      if (this.ruleMaxPulsesField != null) {
         this.enforceIntegerInput(this.ruleMaxPulsesField);
      }
      if (this.ruleIntensityField != null) {
         this.enforceIntegerInput(this.ruleIntensityField);
      }
      if (this.ruleDurationField != null) {
         this.enforceIntegerInput(this.ruleDurationField);
      }
      if (this.ruleIntervalField != null) {
         this.enforceIntegerInput(this.ruleIntervalField);
      }
   }

   private void populateRuleBuilderSensorTypes() {
      ApiService.getInstance().getSensorTypes()
         .thenAccept(sensorMap -> {
            List<String> sensorTypes = new ArrayList<>(sensorMap.keySet());
            sensorTypes.sort(String::compareToIgnoreCase);
            if (sensorTypes.isEmpty()) {
               sensorTypes.add(DEFAULT_HEART_RATE_TYPE);
               sensorTypes.add(DEFAULT_SUN_AZIMUTH_TYPE);
               sensorTypes.add(DEFAULT_MOON_AZIMUTH_TYPE);
            }

            Platform.runLater(() -> {
               if (this.ruleSensorTypeComboBox != null) {
                  this.ruleSensorTypeComboBox.getItems().setAll(sensorTypes);
                  if (this.ruleSensorTypeComboBox.getSelectionModel().isEmpty()) {
                     this.ruleSensorTypeComboBox.getSelectionModel().selectFirst();
                  }
               }
            });
         })
         .exceptionally(ex -> {
            Platform.runLater(() -> {
               if (this.ruleSensorTypeComboBox != null) {
                  this.ruleSensorTypeComboBox.getItems().setAll(DEFAULT_HEART_RATE_TYPE, DEFAULT_SUN_AZIMUTH_TYPE, DEFAULT_MOON_AZIMUTH_TYPE);
                  if (this.ruleSensorTypeComboBox.getSelectionModel().isEmpty()) {
                     this.ruleSensorTypeComboBox.getSelectionModel().selectFirst();
                  }
               }
            });
            return null;
         });
   }

   @FXML
   public void saveRuleBuilderConfig(ActionEvent event) {
      try {
         SensorRuleConfig ruleConfig = this.buildRuleConfigFromForm();
         if (this.persistRuleConfig(ruleConfig)) {
            this.showAlert("Success", "Rule was saved successfully.");
            this.clearRuleBuilderForm();
         } else {
            this.showAlert("Error", "Failed to save rule.");
         }
      } catch (IllegalArgumentException ex) {
         this.showAlert("Validation Error", ex.getMessage());
      } catch (Exception ex) {
         ex.printStackTrace();
         this.showAlert("Error", "Unexpected error while saving rule: " + ex.getMessage());
      }
   }

   private SensorRuleConfig buildRuleConfigFromForm() {
      if (this.ruleSensorTypeComboBox == null || this.ruleSensorTypeComboBox.getValue() == null || this.ruleSensorTypeComboBox.getValue().trim().isEmpty()) {
         throw new IllegalArgumentException("Please select a sensor type.");
      }

      int minValue = this.parseRequiredInt(this.ruleMinValueField, "Min Value");
      int maxValue = this.parseRequiredInt(this.ruleMaxValueField, "Max Value");
      if (minValue > maxValue) {
         throw new IllegalArgumentException("Min Value cannot be greater than Max Value.");
      }

      int minPulses = this.parseRequiredInt(this.ruleMinPulsesField, "Min Pulses");
      int maxPulses = this.parseRequiredInt(this.ruleMaxPulsesField, "Max Pulses");
      if (minPulses > maxPulses) {
         throw new IllegalArgumentException("Min Pulses cannot be greater than Max Pulses.");
      }

      int intensity = this.parseRequiredInt(this.ruleIntensityField, "Intensity");
      int duration = this.parseRequiredInt(this.ruleDurationField, "Duration");
      int interval = this.parseRequiredInt(this.ruleIntervalField, "Interval");

      SensorRuleConfig ruleConfig = new SensorRuleConfig();
      ruleConfig.setType(this.ruleSensorTypeComboBox.getValue().trim());
      ruleConfig.setMinvalue(minValue);
      ruleConfig.setMaxvalue(maxValue);
      ruleConfig.setMinpulses(minPulses);
      ruleConfig.setMaxpulses(maxPulses);
      ruleConfig.setMinintensity(intensity);
      ruleConfig.setMaxintensity(intensity);
      ruleConfig.setMinduration(duration);
      ruleConfig.setMaxduration(duration);
      ruleConfig.setMininterval(interval);
      ruleConfig.setMaxinterval(interval);
      ruleConfig.setActive(true);
      return ruleConfig;
   }

   private int parseRequiredInt(TextField field, String label) {
      if (field == null || field.getText() == null || field.getText().trim().isEmpty()) {
         throw new IllegalArgumentException(label + " is required.");
      }
      try {
         return Integer.parseInt(field.getText().trim());
      } catch (NumberFormatException ex) {
         throw new IllegalArgumentException(label + " must be an integer.");
      }
   }

   private void clearRuleBuilderForm() {
      if (this.ruleMinValueField != null) {
         this.ruleMinValueField.clear();
      }
      if (this.ruleMaxValueField != null) {
         this.ruleMaxValueField.clear();
      }
      if (this.ruleMinPulsesField != null) {
         this.ruleMinPulsesField.clear();
      }
      if (this.ruleMaxPulsesField != null) {
         this.ruleMaxPulsesField.clear();
      }
      if (this.ruleIntensityField != null) {
         this.ruleIntensityField.clear();
      }
      if (this.ruleDurationField != null) {
         this.ruleDurationField.clear();
      }
      if (this.ruleIntervalField != null) {
         this.ruleIntervalField.clear();
      }
   }

   @FXML
   public void sendMonitoringType() {
      String selectedType = (String)this.monitoringComboBox.getSelectionModel().getSelectedItem();
      Map<String, String> requestBody = new HashMap<>();
      requestBody.put("monitoringType", selectedType);
      if (this.postJson(requestBody,  ApiService.getInstance().getBaseUrl() + ApiService.EP_SET_MONITORING_TYPE, "Monitoring type sent successfully!")) {
         this.showAlert("Success", "Data Source was successfully sent");
      } else {
         this.showAlert("Failure", "An Error was encountered while sending the data source");
      }
   }

   private boolean saveRuleSection(VBox rulesBox, List<SensorRuleConfig> ruleBuffer, String fallbackType, String sectionLabel) {
      if (!this.collectRulesFromSection(rulesBox, ruleBuffer, fallbackType)) {
         return false;
      }

      for (SensorRuleConfig ruleConfig : ruleBuffer) {
         if (!this.persistRuleConfig(ruleConfig)) {
            this.showAlert("Error", "Failed to save " + sectionLabel + " rules.");
            return false;
         }
      }

      this.removeInvisibleNodes(rulesBox);
      return true;
   }

   private boolean collectRulesFromSection(VBox rulesBox, List<SensorRuleConfig> ruleBuffer, String fallbackType) {
      for (int i = 0; i < ruleBuffer.size() && i < rulesBox.getChildren().size(); i++) {
         Node node = rulesBox.getChildren().get(i);
         if (node instanceof HBox) {
            SensorRuleConfig existingRule = ruleBuffer.get(i);
            existingRule.setActive(node.isVisible());
         }
      }

      for (int ix = ruleBuffer.size(); ix < rulesBox.getChildren().size(); ix++) {
         Node node = rulesBox.getChildren().get(ix);
         if (node instanceof HBox inputRow) {
            try {
               SensorRuleConfig createdRule = this.parseRuleFromInput(inputRow, fallbackType);
               ruleBuffer.add(createdRule);
            } catch (IllegalArgumentException ex) {
               this.showAlert("Validation Error", ex.getMessage());
               return false;
            } catch (Exception ex) {
               this.showAlert("Unexpected Error", "An unknown error occurred while saving the input.");
               ex.printStackTrace();
               return false;
            }
         }
      }

      return true;
   }

   private SensorRuleConfig parseRuleFromInput(HBox inputRow, String fallbackType) {
      TextField primaryRangeField = (TextField) inputRow.getChildren().get(0);
      TextField pulsesField = (TextField) inputRow.getChildren().get(1);
      TextField intensityField = (TextField) inputRow.getChildren().get(2);
      TextField durationField = (TextField) inputRow.getChildren().get(3);
      TextField intervalField = (TextField) inputRow.getChildren().get(4);

      if (primaryRangeField.getText().isEmpty()
         || pulsesField.getText().isEmpty()
         || intensityField.getText().isEmpty()
         || durationField.getText().isEmpty()
         || intervalField.getText().isEmpty()) {
         throw new IllegalArgumentException("Please fill in all fields before saving.");
      }

      int[] valueRange = this.parseRange(primaryRangeField.getText(), Integer.MAX_VALUE);
      int[] pulsesRange = this.parseRange(pulsesField.getText(), Integer.MAX_VALUE);
      int[] intensityRange = this.parseRange(intensityField.getText(), 255);
      int[] durationRange = this.parseRange(durationField.getText(), Integer.MAX_VALUE);
      int[] intervalRange = this.parseRange(intervalField.getText(), Integer.MAX_VALUE);

      SensorRuleConfig ruleConfig = new SensorRuleConfig();
      ruleConfig.setType(this.resolveRuleType(fallbackType));
      ruleConfig.setMinvalue(valueRange[0]);
      ruleConfig.setMaxvalue(valueRange[1]);
      ruleConfig.setMinpulses(pulsesRange[0]);
      ruleConfig.setMaxpulses(pulsesRange[1]);
      ruleConfig.setMinintensity(intensityRange[0]);
      ruleConfig.setMaxintensity(intensityRange[1]);
      ruleConfig.setMinduration(durationRange[0]);
      ruleConfig.setMaxduration(durationRange[1]);
      ruleConfig.setMininterval(intervalRange[0]);
      ruleConfig.setMaxinterval(intervalRange[1]);
      ruleConfig.setActive(inputRow.isVisible());
      return ruleConfig;
   }

   private boolean persistRuleConfig(SensorRuleConfig ruleConfig) {
      try {
         return ApiService.getInstance().saveSensorRuleConfig(ruleConfig).get();
      } catch (Exception ex) {
         ex.printStackTrace();
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

      for (SensorRuleConfig p : this.heartRateMappingsTest) {
         System.out.println(p + " FROM remove invisible nodes ");
      }
   }

   @FXML
   public void saveConfigurationsToBackend(ActionEvent event) {
      try {
         for (SensorRuleConfig p : this.heartRateMappingsTest) {
            System.out.println(p + " FROM SAVE CONFIG");
         }

         boolean sendSunRangeMappings = this.saveRuleSection(this.sunAzimuthRangesBox, this.sunRangeInputsTest, DEFAULT_SUN_AZIMUTH_TYPE, "Sun");
         boolean sendHeartRateMappings = this.saveRuleSection(this.heartRateMappingsBox, this.heartRateMappingsTest, DEFAULT_HEART_RATE_TYPE, "Heart Rate");
         boolean sendMoonRangeMappings = this.saveRuleSection(this.moonAzimuthRangesBox, this.moonRangeInputsTest, DEFAULT_MOON_AZIMUTH_TYPE, "Moon");
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

   public void fetchCurrentConfigurationsWithoutMessage() {
      try {
         JsonNode rootNode = this.getJson(ApiService.getInstance().getBaseUrl()+ApiService.EP_CURRENT_CONFIGURATION);
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
         JsonNode rootNode = this.getJson(ApiService.getInstance().getBaseUrl()+ApiService.EP_CURRENT_CONFIGURATION);
         System.out.println(rootNode);
         this.parseSunAzimuthRanges(rootNode.path("sunAzimuthRanges"));
         this.parseMoonAzimuthRanges(rootNode.path("moonAzimuthRanges"));
         this.parseHeartRateMappings(rootNode.path("heartRateMappings"));

         for (SensorRuleConfig p : this.heartRateMappingsTest) {
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
      if (this.sunAzimuthRangesBox == null) {
         return;
      }
      this.sunRangeInputsTest.clear();
      this.sunAzimuthRangesBox.getChildren().clear();
      if (sunAzimuthRanges != null && sunAzimuthRanges.isArray()) {
         for (JsonNode range : sunAzimuthRanges) {
            SensorRuleConfig model = new SensorRuleConfig();
            model.setId(range.path("id").asInt());
            model.setType(range.path("type").asText(this.resolveRuleType(DEFAULT_SUN_AZIMUTH_TYPE)));
            model.setMinvalue(range.path("minvalue").asInt());
            model.setMaxvalue(range.path("maxvalue").asInt());
            model.setMinpulses(range.path("minpulses").asInt());
            model.setMaxpulses(range.path("maxpulses").asInt());
            model.setMinintensity(range.path("minintensity").asInt());
            model.setMaxintensity(range.path("maxintensity").asInt());
            model.setMinduration(range.path("minduration").asInt());
            model.setMaxduration(range.path("maxduration").asInt());
            model.setMininterval(range.path("mininterval").asInt());
            model.setMaxinterval(range.path("maxinterval").asInt());
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
                    .addAll(new Node[]{this.bindRangeField(model.getMinvalue(), model.getMaxvalue(), 130, Integer.MAX_VALUE, azimuthTooltip, (min, max) -> {
                       model.setMinvalue(min);
                       model.setMaxvalue(max);
                    }), this.bindRangeField(model.getMinpulses(), model.getMaxpulses(), 80, Integer.MAX_VALUE, pulsesTooltip, (min, max) -> {
                       model.setMinpulses(min);
                       model.setMaxpulses(max);
                    }), this.bindRangeField(model.getMinintensity(), model.getMaxintensity(), 80, 255, intensityTooltip, (min, max) -> {
                       model.setMinintensity(min);
                       model.setMaxintensity(max);
                    }), this.bindRangeField(model.getMinduration(), model.getMaxduration(), 100, Integer.MAX_VALUE, durationTooltip, (min, max) -> {
                       model.setMinduration(min);
                       model.setMaxduration(max);
                    }), this.bindRangeField(model.getMininterval(), model.getMaxinterval(), 100, Integer.MAX_VALUE, intervalTooltip, (min, max) -> {
                       model.setMininterval(min);
                       model.setMaxinterval(max);
                    }), this.createDeleteButton(row, () -> model.setActive(false))});
            this.sunAzimuthRangesBox.getChildren().add(row);
         }
      }
   }

   private void parseMoonAzimuthRanges(JsonNode moonAzimuthRanges) {
      if (this.moonAzimuthRangesBox == null) {
         return;
      }
      this.moonRangeInputsTest.clear();
      this.moonAzimuthRangesBox.getChildren().clear();
      if (moonAzimuthRanges != null && moonAzimuthRanges.isArray()) {
         for (JsonNode range : moonAzimuthRanges) {
            SensorRuleConfig model = new SensorRuleConfig();
            model.setId(range.path("id").asInt());
            model.setType(range.path("type").asText(this.resolveRuleType(DEFAULT_MOON_AZIMUTH_TYPE)));
            model.setMinvalue(range.path("minvalue").asInt());
            model.setMaxvalue(range.path("maxvalue").asInt());
            model.setMinpulses(range.path("minpulses").asInt());
            model.setMaxpulses(range.path("maxpulses").asInt());
            model.setMinintensity(range.path("minintensity").asInt());
            model.setMaxintensity(range.path("maxintensity").asInt());
            model.setMinduration(range.path("minduration").asInt());
            model.setMaxduration(range.path("maxduration").asInt());
            model.setMininterval(range.path("mininterval").asInt());
            model.setMaxinterval(range.path("maxinterval").asInt());
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
                    .addAll(new Node[]{this.bindRangeField(model.getMinvalue(), model.getMaxvalue(), 130, Integer.MAX_VALUE, azimuthTooltip, (min, max) -> {
                       model.setMinvalue(min);
                       model.setMaxvalue(max);
                    }), this.bindRangeField(model.getMinpulses(), model.getMaxpulses(), 80, Integer.MAX_VALUE, pulsesTooltip, (min, max) -> {
                       model.setMinpulses(min);
                       model.setMaxpulses(max);
                    }), this.bindRangeField(model.getMinintensity(), model.getMaxintensity(), 80, Integer.MAX_VALUE, intensityTooltip, (min, max) -> {
                       model.setMinintensity(min);
                       model.setMaxintensity(max);
                    }), this.bindRangeField(model.getMinduration(), model.getMaxduration(), 100, Integer.MAX_VALUE, durationTooltip, (min, max) -> {
                       model.setMinduration(min);
                       model.setMaxduration(max);
                    }), this.bindRangeField(model.getMininterval(), model.getMaxinterval(), 100, Integer.MAX_VALUE, intervalTooltip, (min, max) -> {
                       model.setMininterval(min);
                       model.setMaxinterval(max);
                    }), this.createDeleteButton(row, () -> model.setActive(false))});
            this.moonAzimuthRangesBox.getChildren().add(row);
         }
      }
   }

   private void parseHeartRateMappings(JsonNode heartRateMappingsNode) {
      if (this.heartRateMappingsBox == null) {
         return;
      }
      this.heartRateMappingsTest.clear();
      this.heartRateMappingsBox.getChildren().clear();
      if (heartRateMappingsNode != null && heartRateMappingsNode.isArray()) {
         for (JsonNode mapping : heartRateMappingsNode) {
            SensorRuleConfig model = new SensorRuleConfig();
            model.setId(mapping.path("id").asInt());
            model.setType(mapping.path("type").asText(this.resolveRuleType(DEFAULT_HEART_RATE_TYPE)));
            model.setMinvalue(mapping.path("minvalue").asInt());
            model.setMaxvalue(mapping.path("maxvalue").asInt());
            model.setMinpulses(mapping.path("minpulses").asInt());
            model.setMaxpulses(mapping.path("maxpulses").asInt());
            model.setMinintensity(mapping.path("minintensity").asInt());
            model.setMaxintensity(mapping.path("maxintensity").asInt());
            model.setMinduration(mapping.path("minduration").asInt());
            model.setMaxduration(mapping.path("maxduration").asInt());
            model.setMininterval(mapping.path("mininterval").asInt());
            model.setMaxinterval(mapping.path("maxinterval").asInt());
            model.setActive(true);
            this.heartRateMappingsTest.add(model);
            HBox row = new HBox(10.0);
            row.setId("hr_range_" + model.getId());
            String heartTooltip = "Heart Rate range, e.g., 0-180 (0° = North, 180° = South)";
            String pulsesTooltip = "Range of vibration pulses, e.g., 2-4.";
            String intensityTooltip = "Intensity range, e.g., 2-4.";
            String durationTooltip = "Duration range in ms, e.g., 100-200.";
            String intervalTooltip = "Interval range in ms, e.g., 50-150.";
            row.getChildren().addAll(new Node[]{this.bindRangeField(model.getMinvalue(), model.getMaxvalue(), 130, Integer.MAX_VALUE, heartTooltip, (min, max) -> {
               model.setMinvalue(min);
               model.setMaxvalue(max);
            }), this.bindRangeField(model.getMinpulses(), model.getMaxpulses(), 80, Integer.MAX_VALUE, pulsesTooltip, (min, max) -> {
               model.setMinpulses(min);
               model.setMaxpulses(max);
            }), this.bindRangeField(model.getMinintensity(), model.getMaxintensity(), 80, 255, intensityTooltip, (min, max) -> {
               model.setMinintensity(min);
               model.setMaxintensity(max);
            }), this.bindRangeField(model.getMinduration(), model.getMaxduration(), 100, Integer.MAX_VALUE, durationTooltip, (min, max) -> {
               model.setMinduration(min);
               model.setMaxduration(max);
            }), this.bindRangeField(model.getMininterval(), model.getMaxinterval(), 100, Integer.MAX_VALUE, intervalTooltip, (min, max) -> {
               model.setMininterval(min);
               model.setMaxinterval(max);
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
      ApiService.getInstance().get(ApiService.EP_GET_USECASES, null)
         .thenAccept(response -> {
            JsonNode useCasesArray = ApiService.getInstance().extractArray(response);
            List<String> loadedUseCases = new ArrayList<>();

            if (useCasesArray != null && useCasesArray.isArray()) {
               for (JsonNode useCaseNode : useCasesArray) {
                  String rawName = useCaseNode.path("name").asText("").trim();
                  if (!rawName.isEmpty()) {
                     loadedUseCases.add(rawName);
                  }
               }
            }

            if (loadedUseCases.isEmpty()) {
               loadedUseCases.add(DEFAULT_HEART_RATE_TYPE);
               loadedUseCases.add(DEFAULT_SUN_AZIMUTH_TYPE);
               loadedUseCases.add(DEFAULT_MOON_AZIMUTH_TYPE);
            }

            Platform.runLater(() -> {
               this.monitoringComboBox.getItems().setAll(loadedUseCases);
               this.availableUseCases.clear();
               this.availableUseCases.addAll(loadedUseCases);
               if (!this.monitoringComboBox.getItems().isEmpty() && this.monitoringComboBox.getSelectionModel().isEmpty()) {
                  this.monitoringComboBox.getSelectionModel().selectFirst();
               }
            });
         })
         .exceptionally(ex -> {
            Platform.runLater(() -> {
               this.monitoringComboBox.getItems().setAll(DEFAULT_HEART_RATE_TYPE, DEFAULT_SUN_AZIMUTH_TYPE, DEFAULT_MOON_AZIMUTH_TYPE);
               this.availableUseCases.clear();
               this.availableUseCases.addAll(this.monitoringComboBox.getItems());
               if (this.monitoringComboBox.getSelectionModel().isEmpty()) {
                  this.monitoringComboBox.getSelectionModel().selectFirst();
               }
            });
            return null;
         });
   }

   private String resolveRuleType(String fallbackType) {
      String normalizedFallback = this.normalizeUseCaseName(fallbackType);
      for (String useCaseName : this.availableUseCases) {
         if (this.normalizeUseCaseName(useCaseName).equals(normalizedFallback)) {
            return useCaseName;
         }
      }
      return fallbackType;
   }

   private String normalizeUseCaseName(String rawUseCase) {
      if (rawUseCase == null) {
         return "";
      }

      String normalized = rawUseCase.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
      if (normalized.contains("heartrate")) {
         return DEFAULT_HEART_RATE_TYPE;
      }
      if (normalized.contains("sunazimuth") || (normalized.contains("sun") && normalized.contains("azimuth"))) {
         return DEFAULT_SUN_AZIMUTH_TYPE;
      }
      if (normalized.contains("moonazimuth") || (normalized.contains("moon") && normalized.contains("azimuth"))) {
         return DEFAULT_MOON_AZIMUTH_TYPE;
      }
      return normalized;
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