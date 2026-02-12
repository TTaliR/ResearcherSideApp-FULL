package com.example.demo.controller;

import com.example.demo.model.User;
import com.example.demo.service.ApiService;
import com.example.demo.service.PdfService;
import com.fasterxml.jackson.databind.JsonNode;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import javafx.stage.Stage;

import java.awt.*;
import java.io.*;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

// visPageController: Controller for the visualization page.
// - Fetches user list, sensor types and sensor data, then renders a D3 visualization inside a WebView.
// - All network calls are async; UI updates must be done on the JavaFX thread via Platform.runLater(...).
// - This controller also exposes PDF generation helpers that rely on PdfService.
public class visPageController {
   private final ObservableList<User> userData = FXCollections.observableArrayList();
   @FXML
   private AnchorPane chartAnchor;
   @FXML
   private LineChart<String, Number> heartRateChart;
   @FXML
   private ComboBox<String> userSelector;
   @FXML
   private ComboBox<String> useCaseSelector;
   @FXML
   private ComboBox<String> timeRangeSelector;
   @FXML
   private Button backButton;
   @FXML
   private ImageView sunIcon;
   @FXML
   private Label elevationLabel;
   @FXML
   private Label azimuthLabel;
   @FXML
   private Button dailyHeartPdfBtn;
   @FXML
   private Button refreshGraphButton;
   @FXML
   private Label loadingLabel;
   @FXML
   private Button generatePdfButton;
   private boolean isSunPositionSelected = false;
   private WebView webView;
   private StackPane loadingOverlay;
   private ProgressIndicator progressIndicator;

   @FXML
   private void initialize() {
      // initialize() runs on the JavaFX thread during FXML load.
      // It performs lightweight setup and triggers the initial load of users (async).
      this.loadUserData();
      this.populateUseCaseSelector();
      this.populateUserSelector();
      Tooltip.install(this.backButton, new Tooltip("Back to Main Menu"));
      if (this.sunIcon != null) {
         try {
            InputStream sunImageStream = this.getClass().getResourceAsStream("/com/example/demo/images/sun.png");
            if (sunImageStream != null) {
               Image sunImage = new Image(sunImageStream);
               this.sunIcon.setImage(sunImage);
               this.sunIcon.setVisible(false);
            } else {
               System.err.println("WARNING: sun.png resource not found at /com/example/demo/images/sun.png");
               this.sunIcon.setVisible(false);
            }
         } catch (Exception var2) {
            System.err.println("ERROR: Could not load sun icon: " + var2.getMessage());
            this.sunIcon.setVisible(false);
         }
      }

      if (this.elevationLabel != null) {
         this.elevationLabel.setVisible(false);
      }

      if (this.azimuthLabel != null) {
         this.azimuthLabel.setVisible(false);
      }

      this.userSelector.setOnAction(_ -> this.updateChart());
      this.useCaseSelector.setOnAction(_ -> {
         String selected = this.useCaseSelector.getValue();
         this.isSunPositionSelected = "SunAzimuth".equals(selected);
         this.updateChart();
      });
      this.timeRangeSelector.getItems().addAll("Last 24 Hours", "Last 7 Days", "Last 30 Days", "All Time");
      this.timeRangeSelector.setValue("Last 24 Hours");
      this.timeRangeSelector.setOnAction(ex -> this.updateChart());
      if (this.generatePdfButton != null) {
         this.generatePdfButton.setOnAction(ex -> this.generatePdfLog());
      }

      // Disable action buttons until valid selections are made
      updateButtonStates();
      this.userSelector.valueProperty().addListener((obs, oldVal, newVal) -> updateButtonStates());
      this.useCaseSelector.valueProperty().addListener((obs, oldVal, newVal) -> updateButtonStates());

      // Initialize loading overlay
      initializeLoadingOverlay();
   }

   /**
    * Updates the enabled/disabled state of action buttons based on current selections.
    */
   private void updateButtonStates() {
      boolean hasValidSelections = userSelector.getValue() != null && useCaseSelector.getValue() != null;
      if (refreshGraphButton != null) {
         refreshGraphButton.setDisable(!hasValidSelections);
      }
      if (generatePdfButton != null) {
         generatePdfButton.setDisable(!hasValidSelections);
      }
   }

   /**
    * Creates a loading overlay with a progress indicator for visual feedback during async operations.
    */
   private void initializeLoadingOverlay() {
      progressIndicator = new ProgressIndicator();
      progressIndicator.setMaxSize(80, 80);

      Label loadingText = new Label("Loading chart data...");
      loadingText.setStyle("-fx-font-size: 14; -fx-text-fill: #1976d2; -fx-font-weight: bold;");

      VBox loadingContent = new VBox(15);
      loadingContent.setAlignment(Pos.CENTER);
      loadingContent.getChildren().addAll(progressIndicator, loadingText);

      loadingOverlay = new StackPane();
      loadingOverlay.setStyle("-fx-background-color: rgba(255, 255, 255, 0.85);");
      loadingOverlay.getChildren().add(loadingContent);
      loadingOverlay.setVisible(false);
      loadingOverlay.setManaged(false);

      // Add overlay to chartAnchor
      AnchorPane.setTopAnchor(loadingOverlay, 0.0);
      AnchorPane.setBottomAnchor(loadingOverlay, 0.0);
      AnchorPane.setLeftAnchor(loadingOverlay, 0.0);
      AnchorPane.setRightAnchor(loadingOverlay, 0.0);
      this.chartAnchor.getChildren().add(loadingOverlay);
   }

   private void loadUserData() {
      // loadUserData uses ApiService.get(...) which returns a CompletableFuture.
      // The response handling below executes off the FX thread; when we need to mutate UI controls we call Platform.runLater.
      this.userData.clear();

      ApiService.getInstance().get(ApiService.EP_GET_USERS, null)
         .thenAccept(response -> {
            try {
               // Check for API error
               if (response.has("error")) {
                  int errorCode = response.get("error").asInt();
                  String errorMsg = response.has("message") ? response.get("message").asText() : "Unknown error";
                  System.err.println("ERROR: Failed to load users - API returned error " + errorCode + ": " + errorMsg);
                  return;
               }

               JsonNode usersArray = ApiService.getInstance().extractArray(response);

               if (usersArray == null) {
                  System.err.println("ERROR: Unexpected JSON format in users response. No 'data' or 'payload' field found.");
                  return;
               }

               if (usersArray.size() == 0) {
                  System.out.println("WARNING: No users returned from API");
               }

               int loadedCount = 0;
               for (JsonNode userNode : usersArray) {
                  try {
                     if (userNode.hasNonNull("userid")) {
                        int id = userNode.get("userid").asInt();
                        String firstName = userNode.hasNonNull("fname") ? userNode.get("fname").asText() : "";
                        String lastName = userNode.hasNonNull("lname") ? userNode.get("lname").asText() : "";
                        this.userData.add(new User(id, firstName, lastName));
                        loadedCount++;
                     } else {
                        System.err.println("WARNING: User node missing userid field");
                     }
                  } catch (Exception e) {
                     System.err.println("ERROR: Failed to parse user node: " + e.getMessage());
                  }
               }

               System.out.println("Successfully loaded " + loadedCount + " users");
               Platform.runLater(this::populateUserSelector);

            } catch (Exception e) {
               System.err.println("ERROR: Failed to parse users response: " + e.getMessage());
               e.printStackTrace();
            }
         })
         .exceptionally(e -> {
            System.err.println("ERROR: Failed to load user data: " + e.getMessage());
            e.printStackTrace();
            return null;
         });
   }

   private void populateUserSelector() {
      // Simple helper to populate the ComboBox from the cached userData list.
      // This is expected to be called on the FX thread.
      this.userSelector.getItems().clear();

      for (User u : this.userData) {
         this.userSelector.getItems().add(u.getUserID() + " - " + u.getFName() + " " + u.getLName());
      }
   }

   private void populateUseCaseSelector() {
      this.useCaseSelector.getItems().addAll(new String[]{"HeartRate", "SunAzimuth", "MoonAzimuth"});
   }

   @FXML
   private void handleBackButton(ActionEvent event) {
      // Loads the previous scene. Runs on the FX thread (triggered by button press).
      try {
         FXMLLoader loader = new FXMLLoader(this.getClass().getResource("/com/example/demo/view/ResearcherInterface.fxml"));
         Parent root = (Parent)loader.load();
         Stage stage = (Stage)((Node)event.getSource()).getScene().getWindow();
         stage.setScene(new Scene(root));
         stage.show();
      } catch (IOException var5) {
         var5.printStackTrace();
      }
   }

   @FXML
   private void refreshGraph(ActionEvent event) {
      // Refresh graph button handler - simply calls updateChart
      this.updateChart();
   }

   public void updateChart() {
      // updateChart runs on the FX thread (called from UI events).
      // High-level flow:
      // 1. Parse selections and lookup sensor ID (async).
      // 2. Build params and fetch sensor data (async).
      // 3. Normalize response with ApiService.extractArray().
      // 4. Escape JSON and inject into the HTML template loaded from resources.
      // 5. All UI updates (webView.loadContent) are wrapped in Platform.runLater.
      String userSelection = userSelector.getValue();
      String useCase = useCaseSelector.getValue();
      String timeRange = timeRangeSelector.getValue();

      if (userSelection == null && useCase == null && timeRange == null) {
         System.out.println("INFO: Selections incomplete - user: " + userSelection + ", useCase: " + useCase + ", timeRange: " + timeRange);
         if (userSelection == null || useCase == null) {
            showInfoAlert("Selection Required", "Please select a user and use case to view the chart.");
         }
         return;
      }

      // Show loading feedback
      showLoadingOverlay(true);

      try {
         int selectedUserID = Integer.parseInt(userSelection.split(" ")[0]);

         // Get sensor ID asynchronously
         ApiService.getInstance().getSensorIdByName(useCase)
            .thenAccept(sensorID -> {
               if (sensorID == -1) {
                  System.err.println("ERROR: Could not find ID for sensor: " + useCase);
                  showWebViewError("Sensor Not Found", "Could not find sensor: " + useCase, true);
                  return;
               }

               // Build query params
               Map<String, String> params = new HashMap<>();
               params.put("range", timeRange);
               params.put("alert_type", String.valueOf(sensorID));
               params.put("userid", String.valueOf(selectedUserID));

               // Fetch sensor data
               ApiService.getInstance().get(ApiService.EP_SENSOR_DATA, params)
                  .thenAccept(response -> {
                     try {
                        // Check for API error
                        if (response.has("error")) {
                           int errorCode = response.get("error").asInt();
                           String errorMsg = response.has("message") ? response.get("message").asText() : "Unknown error";
                           System.err.println("ERROR: API returned error " + errorCode + ": " + errorMsg);
                           showWebViewError("Failed to Load Data", "API Error " + errorCode + ": " + errorMsg, true);
                           return;
                        }

                        // Extract array from potentially wrapped response
                        JsonNode dataArray = ApiService.getInstance().extractArray(response);

                        // Check for null or empty response
                        if (dataArray == null) {
                           System.err.println("ERROR: Could not extract array from API response - unexpected format");
                           showWebViewError("Invalid Data Format", "Server returned unexpected data format", true);
                           return;
                        }

                        if (dataArray.size() == 0) {
                           System.out.println("INFO: No data available for user " + selectedUserID + " and sensor " + useCase);
                           showWebViewError("No Data Available", "No data available for this user and sensor type.", false);
                           return;
                        }

                        // Escape JSON for safe injection into JavaScript
                        String json = dataArray.toString();
                        String escapedJson = json.replace("\\", "\\\\").replace("\"", "\\\"");

                        String jsDataSetup = "const data = JSON.parse(\"" + escapedJson + "\");";

                        // Load HTML template
                        String htmlTemplate = loadHtmlTemplate();
                        if (htmlTemplate == null) {
                           System.err.println("ERROR: Failed to load HTML template");
                           showWebViewError("Template Not Found", "Could not load visualization template", true);
                           return;
                        }

                        String html = htmlTemplate
                           .replace("const data = DATA_PLACEHOLDER;", jsDataSetup)
                           .replace("USER_ID_PLACEHOLDER", String.valueOf(selectedUserID))
                           .replace("SENSOR_TYPE_PLACEHOLDER", "\"" + useCase + "\"");

                        Platform.runLater(() -> {
                           ensureWebViewInitialized();
                           System.out.println("Loading D3 Chart into WebView...");
                           webView.getEngine().loadContent(html);
                           showLoadingOverlay(false);
                        });

                     } catch (Exception e) {
                        System.err.println("ERROR: Failed to process chart data: " + e.getMessage());
                        e.printStackTrace();
                        showWebViewError("Processing Failed", e.getMessage(), true);
                     }
                  })
                  .exceptionally(e -> {
                     System.err.println("ERROR: Failed to fetch sensor data: " + e.getMessage());
                     e.printStackTrace();
                     showWebViewError("Failed to Fetch Data", e.getMessage(), true);
                     return null;
                  });
            })
            .exceptionally(e -> {
               System.err.println("ERROR: Failed to get sensor ID: " + e.getMessage());
               e.printStackTrace();
               showWebViewError("Sensor Lookup Failed", e.getMessage(), true);
               return null;
            });
      } catch (NumberFormatException nfe) {
         System.err.println("ERROR: Invalid user ID format: " + nfe.getMessage());
      } catch (Exception e) {
         System.err.println("ERROR: Unexpected error in updateChart: " + e.getMessage());
         e.printStackTrace();
      }
   }

   // Helper to show/hide the loading overlay
   private void showLoadingOverlay(boolean show) {
      if (loadingOverlay != null) {
         loadingOverlay.setVisible(show);
         loadingOverlay.setManaged(show);
         // Ensure overlay is on top
         if (show) {
            loadingOverlay.toFront();
         }
      }
      // Also update the legacy loading label for backwards compatibility
      if (loadingLabel != null) {
         loadingLabel.setVisible(show);
         loadingLabel.setManaged(show);
      }
   }

   // Helper to load the HTML template from the feedbackGraph.html file
   private String loadHtmlTemplate() {
      // Reads resource feedbackGraph.html and returns as string.
      // Keep this method synchronous: it is fast (reads a bundled resource).
      try {
         InputStream inputStream = this.getClass().getResourceAsStream("/com/example/demo/feedbackGraph.html");
         if (inputStream == null) {
            System.err.println("feedbackGraph.html not found in resources");
            return null;
         }
         BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
         StringBuilder htmlContent = new StringBuilder();
         String line;
         while ((line = reader.readLine()) != null) {
            htmlContent.append(line).append("\n");
         }
         reader.close();
         return htmlContent.toString();
      } catch (IOException e) {
         e.printStackTrace();
         return null;
      }
   }

   // Helper to make sure the WebView exists in the scene
   private void ensureWebViewInitialized() {
      // Create and anchor the WebView into chartAnchor if absent.
      // This method modifies JavaFX scene graph and must run on the FX thread (it is invoked via Platform.runLater where necessary).
      if (this.webView == null) {
         this.webView = new WebView();
         this.webView.setPrefHeight(600.0);
         this.webView.setPrefWidth(1000.0);
         AnchorPane.setTopAnchor(this.webView, 0.0);
         AnchorPane.setBottomAnchor(this.webView, 0.0);
         AnchorPane.setLeftAnchor(this.webView, 0.0);
         AnchorPane.setRightAnchor(this.webView, 0.0);
         this.chartAnchor.getChildren().clear();
         this.chartAnchor.getChildren().add(this.webView);

         // Re-add loading overlay on top of WebView so it can show over the chart
         if (this.loadingOverlay != null) {
            AnchorPane.setTopAnchor(loadingOverlay, 0.0);
            AnchorPane.setBottomAnchor(loadingOverlay, 0.0);
            AnchorPane.setLeftAnchor(loadingOverlay, 0.0);
            AnchorPane.setRightAnchor(loadingOverlay, 0.0);
            this.chartAnchor.getChildren().add(this.loadingOverlay);
         }

         // Set up JavaScript-to-Java bridge for image export when page loads
         final WebView wv = this.webView;
         final ExportBridge bridge = this.exportBridge;
         this.webView.getEngine().getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == javafx.concurrent.Worker.State.SUCCEEDED) {
               setupJavaScriptBridge(wv, bridge);
            }
         });
      }
   }

   /**
    * Sets up the JavaScript-to-Java bridge for export functionality.
    * Uses deprecated JSObject API which is still functional.
    */
   @SuppressWarnings("removal")
   private void setupJavaScriptBridge(WebView wv, ExportBridge bridge) {
      try {
         netscape.javascript.JSObject window = (netscape.javascript.JSObject) wv.getEngine().executeScript("window");
         window.setMember("javaExportBridge", bridge);
         System.out.println("Export bridge registered successfully");
      } catch (Exception e) {
         System.err.println("Warning: Could not set up export bridge: " + e.getMessage());
      }
   }

   /**
    * Inner class to hold export bridge instance for JavaScript callback.
    */
   private final ExportBridge exportBridge = new ExportBridge();

   /**
    * Java bridge class for exporting images from JavaScript.
    */
   public class ExportBridge {
      public void saveImage(String dataUrl, String filename) {
         Platform.runLater(() -> {
            try {
               javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
               fileChooser.setTitle("Save Chart Image");
               fileChooser.setInitialFileName(filename);
               fileChooser.getExtensionFilters().add(
                  new javafx.stage.FileChooser.ExtensionFilter("PNG Image", "*.png")
               );

               javafx.stage.Window ownerWindow = chartAnchor.getScene().getWindow();
               java.io.File file = fileChooser.showSaveDialog(ownerWindow);

               if (file != null) {
                  String base64Data = dataUrl.substring(dataUrl.indexOf(",") + 1);
                  byte[] imageBytes = java.util.Base64.getDecoder().decode(base64Data);

                  try (java.io.FileOutputStream fos = new java.io.FileOutputStream(file)) {
                     fos.write(imageBytes);
                  }

                  Alert alert = new Alert(AlertType.INFORMATION);
                  alert.setTitle("Export Successful");
                  alert.setHeaderText("Chart Exported!");
                  alert.setContentText("Saved to: " + file.getAbsolutePath());
                  alert.showAndWait();
               }
            } catch (Exception e) {
               System.err.println("Export error: " + e.getMessage());
               showErrorAlert("Export Failed", "Could not save: " + e.getMessage());
            }
         });
      }
   }

   public void generateDailyHeartRatePdfForAllUsers() {
      // Iterates over cached userData and requests daily heart rate data then delegates to PdfService.
      // Note: PdfService methods may block briefly while writing files; they run from the completable-stage so UI thread will not be blocked.
      LocalDate today = LocalDate.now();

      // Get HeartRate sensor ID once
      ApiService.getInstance().getSensorIdByName("HeartRate")
         .thenAccept(heartRateSensorId -> {
            if (heartRateSensorId == -1) {
               System.err.println("Could not find HeartRate sensor");
               return;
            }

            // Process each user
            for (User user : this.userData) {
               int userID = user.getUserID();

               Map<String, String> params = new HashMap<>();
               params.put("userid", String.valueOf(userID));
               params.put("alert_type", String.valueOf(heartRateSensorId));

               ApiService.getInstance().get(ApiService.EP_SENSOR_DATA, params)
                  .thenAccept(response -> {
                     try {
                        // Delegate to PdfService (will handle unwrapping internally)
                        String filePath = PdfService.generateDailyHeartRateReport(userID, today, response, heartRateSensorId);
                        System.out.println("PDF saved: " + filePath);

                     } catch (Exception e) {
                        System.err.println("Error generating PDF for user " + userID + ": " + e.getMessage());
                     }
                  })
                  .exceptionally(e -> {
                     System.err.println("Failed to fetch data for user " + userID + ": " + e.getMessage());
                     return null;
                  });
            }
         })
         .exceptionally(e -> {
            System.err.println("Failed to get HeartRate sensor ID: " + e.getMessage());
            return null;
         });
   }


   @FXML
   private void generatePdfLog() {
      // Triggered by UI action. Validates selections, obtains sensorID, fetches data, then calls PdfService.generateSensorDataReport.
      // All operations that update UI are executed via Platform.runLater.
      String userSelection = this.userSelector.getValue();
      String useCase = this.useCaseSelector.getValue();
      String timeRange = this.timeRangeSelector.getValue();

      if (userSelection == null || useCase == null || timeRange == null) {
         showErrorAlert("Missing Selection", "Please select a user, use case, and time range.");
         System.out.println("ERROR: Missing required selections");
         return;
      }

      try {
         int userID = Integer.parseInt(userSelection.split(" ")[0]);

         // Get sensor ID and fetch data asynchronously
         ApiService.getInstance().getSensorIdByName(useCase)
            .thenAccept(sensorID -> {
               if (sensorID == -1) {
                  showErrorAlert("Sensor Not Found", "Could not find sensor type: " + useCase);
                  System.err.println("ERROR: Could not find sensor: " + useCase);
                  return;
               }

               Map<String, String> params = new HashMap<>();
               params.put("userid", String.valueOf(userID));
               params.put("alert_type", String.valueOf(sensorID));
               params.put("range", timeRange);

               ApiService.getInstance().get(ApiService.EP_SENSOR_DATA, params)
                  .thenAccept(response -> {
                     try {
                        System.out.println("DEBUG: generatePdfLog received response: " + response.getNodeType());

                        // Delegate PDF generation to PdfService (will handle unwrapping internally)
                        String fullPath = PdfService.generateSensorDataReport(userID, useCase, timeRange, response);
                        System.out.println("PDF generated: " + fullPath);

                        // Show success alert with file open link
                        Platform.runLater(() -> {
                           File pdfFile = new File(fullPath);
                           Alert alert = new Alert(AlertType.INFORMATION);
                           alert.setTitle("PDF Generated");
                           alert.setHeaderText("PDF Report Created Successfully");
                           alert.setContentText("The report has been saved to:\n" + fullPath);

                           VBox content = new VBox(10.0);
                           content.getChildren().addAll(new Label("The report has been saved to:"), new Label(fullPath));

                           // Only add open button if Desktop is supported
                           if (Desktop.isDesktopSupported()) {
                              Hyperlink link = new Hyperlink("Open PDF");
                              link.setOnAction(e -> {
                                 try {
                                    Desktop.getDesktop().open(pdfFile);
                                 } catch (Exception ex) {
                                    System.out.println("ERROR: Cannot open PDF: " + ex.getMessage());
                                    showErrorAlert("File Error", "Cannot open PDF: " + ex.getMessage());
                                 }
                              });
                              content.getChildren().add(link);
                           } else {
                              content.getChildren().add(new Label("(Desktop file open not supported on this system)"));
                           }

                           alert.getDialogPane().setContent(content);
                           alert.showAndWait();
                        });

                     } catch (IllegalArgumentException iae) {
                        System.err.println("ERROR: Invalid input - " + iae.getMessage());
                        showErrorAlert("Invalid Input", iae.getMessage());
                     } catch (Exception e) {
                        System.err.println("ERROR: Failed to generate PDF: " + e.getMessage());
                        e.printStackTrace();
                        showErrorAlert("PDF Generation Failed", "Error: " + e.getMessage());
                     }
                  })
                  .exceptionally(e -> {
                     System.err.println("ERROR: Failed to fetch data: " + e.getMessage());
                     showErrorAlert("Data Fetch Failed", "Failed to fetch sensor data: " + e.getMessage());
                     return null;
                  });
            })
            .exceptionally(e -> {
               System.err.println("ERROR: Failed to get sensor ID: " + e.getMessage());
               showErrorAlert("Sensor Lookup Failed", "Failed to get sensor ID: " + e.getMessage());
               return null;
            });
      } catch (NumberFormatException nfe) {
         System.err.println("ERROR: Invalid user ID format: " + nfe.getMessage());
         showErrorAlert("Invalid User ID", "Could not parse user ID from selection");
      } catch (Exception e) {
         System.err.println("ERROR: Unexpected error in generatePdfLog: " + e.getMessage());
         e.printStackTrace();
         showErrorAlert("Unexpected Error", "An unexpected error occurred: " + e.getMessage());
      }
   }

   /**
    * Helper method to show error alerts to the user.
    * Always runs on the FX thread using Platform.runLater to be safe when called from background threads.
    */
   private void showErrorAlert(String title, String message) {
      Platform.runLater(() -> {
         Alert alert = new Alert(AlertType.ERROR);
         alert.setTitle(title);
         alert.setHeaderText(title);
         alert.setContentText(message);
         alert.showAndWait();
      });
   }

   /**
    * Helper method to show informational alerts to the user.
    * Always runs on the FX thread using Platform.runLater to be safe when called from background threads.
    */
   private void showInfoAlert(String title, String message) {
      Platform.runLater(() -> {
         Alert alert = new Alert(AlertType.INFORMATION);
         alert.setTitle(title);
         alert.setHeaderText(title);
         alert.setContentText(message);
         alert.showAndWait();
      });
   }

   /**
    * Helper method to display error messages in the WebView with consistent styling.
    * Centralizes the error HTML generation to avoid repetition and ensure consistency.
    *
    * @param title   The error title to display
    * @param message The error message/details
    * @param isError If true, uses red error styling; otherwise uses neutral gray
    */
   private void showWebViewError(String title, String message, boolean isError) {
      Platform.runLater(() -> {
         ensureWebViewInitialized();
         String color = isError ? "#d32f2f" : "#555";
         String icon = isError ? "❌" : "ℹ️";
         webView.getEngine().loadContent(
            "<html><body style='font-family: -apple-system, BlinkMacSystemFont, \"Segoe UI\", Roboto, sans-serif; " +
            "text-align: center; padding-top: 80px; color: " + color + "; background: #fafafa;'>" +
            "<div style='font-size: 48px; margin-bottom: 20px;'>" + icon + "</div>" +
            "<h3 style='margin: 0 0 10px 0;'>" + escapeHtml(title) + "</h3>" +
            "<p style='color: #666; max-width: 400px; margin: 0 auto;'>" + escapeHtml(message) + "</p>" +
            "</body></html>"
         );
         showLoadingOverlay(false);
      });
   }

   /**
    * Helper method to escape HTML special characters to prevent injection.
    */
   private String escapeHtml(String text) {
      if (text == null) return "";
      return text
         .replace("&", "&amp;")
         .replace("<", "&lt;")
         .replace(">", "&gt;")
         .replace("\"", "&quot;")
         .replace("'", "&#39;");
   }
}

