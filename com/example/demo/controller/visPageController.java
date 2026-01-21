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
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import javafx.stage.Stage;

import java.awt.*;
import java.io.*;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

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
   private Button generatePdfButton;
   private boolean isSunPositionSelected = false;
   private WebView webView;

   @FXML
   private void initialize() {
      this.loadUserData();
      this.populateUseCaseSelector();
      this.populateUserSelector();
      Tooltip.install(this.backButton, new Tooltip("Back to Main Menu"));
      if (this.sunIcon != null) {
         try {
            Image sunImage = new Image(Objects.requireNonNull(this.getClass().getResourceAsStream("/com/example/demo/images/sun.png")));
            this.sunIcon.setImage(sunImage);
            this.sunIcon.setVisible(false);
         } catch (Exception var2) {
            System.err.println("Could not load sun icon: " + var2.getMessage());
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
   }

   private void loadUserData() {
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

   public void updateChart() {
      String userSelection = userSelector.getValue();
      String useCase = useCaseSelector.getValue();
      String timeRange = timeRangeSelector.getValue();

      if (userSelection == null || useCase == null || timeRange == null) {
         System.out.println("ERROR: Please select user, use case, and time range.");
         return;
      }

      try {
         int selectedUserID = Integer.parseInt(userSelection.split(" ")[0]);

         // Get sensor ID asynchronously
         ApiService.getInstance().getSensorIdByName(useCase)
            .thenAccept(sensorID -> {
               if (sensorID == -1) {
                  System.err.println("ERROR: Could not find ID for sensor: " + useCase);
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
                           Platform.runLater(() -> {
                              ensureWebViewInitialized();
                              webView.getEngine().loadContent(
                                 "<html><body style='font-family:sans-serif; text-align:center; padding-top:50px; color:#d32f2f;'>" +
                                    "<h3>Error: Failed to load data</h3>" +
                                    "<p>API Error " + errorCode + ": " + errorMsg + "</p>" +
                                    "</body></html>"
                              );
                           });
                           return;
                        }

                        // Extract array from potentially wrapped response
                        JsonNode dataArray = ApiService.getInstance().extractArray(response);

                        // Check for empty response
                        if (dataArray == null || dataArray.size() == 0) {
                           System.out.println("INFO: No data available for user " + selectedUserID + " and sensor " + useCase);
                           Platform.runLater(() -> {
                              ensureWebViewInitialized();
                              webView.getEngine().loadContent(
                                 "<html><body style='font-family:sans-serif; text-align:center; padding-top:50px; color:#555;'>" +
                                    "<h3>No data available for this user and sensor type.</h3>" +
                                    "</body></html>"
                              );
                           });
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
                        });

                     } catch (Exception e) {
                        System.err.println("ERROR: Failed to process chart data: " + e.getMessage());
                        e.printStackTrace();
                     }
                  })
                  .exceptionally(e -> {
                     System.err.println("ERROR: Failed to fetch sensor data: " + e.getMessage());
                     e.printStackTrace();
                     return null;
                  });
            })
            .exceptionally(e -> {
               System.err.println("ERROR: Failed to get sensor ID: " + e.getMessage());
               e.printStackTrace();
               return null;
            });
      } catch (NumberFormatException nfe) {
         System.err.println("ERROR: Invalid user ID format: " + nfe.getMessage());
      } catch (Exception e) {
         System.err.println("ERROR: Unexpected error in updateChart: " + e.getMessage());
         e.printStackTrace();
      }
   }

   // Helper to load the HTML template from the feedbackGraph.html file
   private String loadHtmlTemplate() {
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
      }
   }

   public void generateDailyHeartRatePdfForAllUsers() {
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
                           Hyperlink link = new Hyperlink("Open PDF");
                           link.setOnAction(e -> {
                              try {
                                 if (Desktop.isDesktopSupported()) {
                                    Desktop.getDesktop().open(pdfFile);
                                 } else {
                                    showErrorAlert("Cannot Open File", "Desktop is not supported on this system");
                                 }
                              } catch (Exception ex) {
                                 System.out.println("ERROR: Cannot open PDF: " + ex.getMessage());
                                 showErrorAlert("File Error", "Cannot open PDF: " + ex.getMessage());
                              }
                           });
                           VBox content = new VBox(10.0);
                           content.getChildren().addAll(new Label("The report has been saved to:"), new Label(fullPath), link);
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
    * Helper method to show error alerts to the user
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
}
