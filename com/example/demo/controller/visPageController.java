package com.example.demo.controller;

import com.example.demo.model.User;
import com.example.demo.service.ApiService;
import com.fasterxml.jackson.databind.JsonNode;
import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
               JsonNode usersArray = ApiService.getInstance().extractArray(response);

               if (usersArray == null) {
                  System.err.println("Unexpected JSON format in users response");
                  return;
               }

               for (JsonNode userNode : usersArray) {
                  if (userNode.hasNonNull("userid")) {
                     int id = userNode.get("userid").asInt();
                     String firstName = userNode.hasNonNull("fname") ? userNode.get("fname").asText() : "";
                     String lastName = userNode.hasNonNull("lname") ? userNode.get("lname").asText() : "";
                     this.userData.add(new User(id, firstName, lastName));
                  }
               }

               Platform.runLater(this::populateUserSelector);

            } catch (Exception e) {
               System.err.println("Failed to parse users response: " + e.getMessage());
            }
         })
         .exceptionally(e -> {
            System.err.println("Failed to load user data: " + e.getMessage());
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
         System.out.println("Please select user, use case, and time range.");
         return;
      }

      int selectedUserID = Integer.parseInt(userSelection.split(" ")[0]);

      // Get sensor ID asynchronously
      ApiService.getInstance().getSensorIdByName(useCase)
         .thenAccept(sensorID -> {
            if (sensorID == -1) {
               System.err.println("Could not find ID for sensor: " + useCase);
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
                     // Check for empty response
                     if (response.has("error") || response.size() == 0) {
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
                     String json = response.toString();
                     String escapedJson = json.replace("\\", "\\\\").replace("\"", "\\\"");

                     String jsDataSetup = "const rawRes = JSON.parse(\"" + escapedJson + "\"); " +
                        "const data = Array.isArray(rawRes) ? rawRes : (rawRes.payload || rawRes.data || []);";

                     // Load HTML template
                     String htmlTemplate = loadHtmlTemplate();
                     if (htmlTemplate == null) {
                        System.err.println("Failed to load HTML template");
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
                     System.err.println("Failed to process chart data: " + e.getMessage());
                  }
               })
               .exceptionally(e -> {
                  System.err.println("Failed to fetch sensor data: " + e.getMessage());
                  return null;
               });
         })
         .exceptionally(e -> {
            System.err.println("Failed to get sensor ID: " + e.getMessage());
            return null;
         });
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
      DateTimeFormatter dateOnly = DateTimeFormatter.ofPattern("yyyy-MM-dd");
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
               params.put("userId", String.valueOf(userID));
               params.put("alert_type", String.valueOf(heartRateSensorId));

               ApiService.getInstance().get(ApiService.EP_SENSOR_DATA, params)
                  .thenAccept(response -> {
                     try {
                        JsonNode root = response;
                        String fileName = "HeartRateLog_User" + userID + "_" + today.format(dateOnly) + ".pdf";
                        Document document = new Document();
                        PdfWriter writer = PdfWriter.getInstance(document, new FileOutputStream(fileName));
                        document.open();

                        Font titleFont = new Font(1, 14.0F, 1);
                        document.add(new Paragraph("Daily Heart Rate Log - User ID: " + userID, titleFont));
                        document.add(new Paragraph("Date: " + today.format(dateOnly)));
                        document.add(new Paragraph(" "));

                        PdfPTable table = new PdfPTable(7);
                        table.setWidthPercentage(100.0F);
                        table.addCell("Time");
                        table.addCell("Heart Rate (BPM)");
                        table.addCell("Alert Type");
                        table.addCell("Pulses");
                        table.addCell("Intensity");
                        table.addCell("Duration");
                        table.addCell("Max Value");

                        int count = 0;
                        if (root.isArray()) {
                           for (JsonNode node : root) {
                              try {
                                 if (node.has("sensorid") && node.get("sensorid").asInt() == heartRateSensorId) {
                                    if (node.has("time") && node.has("value")) {
                                       Instant instant = Instant.parse(node.get("time").asText());
                                       LocalDateTime dateTime = instant.atZone(ZoneId.systemDefault()).toLocalDateTime();
                                       LocalDate recordDate = dateTime.toLocalDate();

                                       if (recordDate.equals(today)) {
                                          table.addCell(dateTime.format(DateTimeFormatter.ofPattern("HH:mm:ss")));
                                          table.addCell(node.has("value") ? String.valueOf(node.get("value").asInt()) : "N/A");
                                          table.addCell(node.has("alert_type") ? node.get("alert_type").asText() : "N/A");
                                          table.addCell(node.has("pulses") ? String.valueOf(node.get("pulses").asInt()) : "N/A");
                                          table.addCell(node.has("intensity") ? String.valueOf(node.get("intensity").asInt()) : "N/A");
                                          table.addCell(node.has("duration") ? String.valueOf(node.get("duration").asInt()) : "N/A");
                                          table.addCell(node.has("maxvalue") ? String.valueOf(node.get("maxvalue").asInt()) : "N/A");
                                          count++;
                                       }
                                    }
                                 }
                              } catch (Exception e) {
                                 System.err.println("Error processing node: " + e.getMessage());
                              }
                           }
                        }

                        if (count == 0) {
                           document.add(new Paragraph("No heart rate data found for today."));
                        } else {
                           document.add(table);
                           document.add(new Paragraph("Total records: " + count));
                        }

                        document.close();
                        writer.close();
                        System.out.println("PDF saved: " + new File(fileName).getAbsolutePath());

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
         System.out.println("Please select a user, use case, and time range.");
         return;
      }

      int userID = Integer.parseInt(userSelection.split(" ")[0]);

      // Get sensor ID and fetch data asynchronously
      ApiService.getInstance().getSensorIdByName(useCase)
         .thenAccept(sensorID -> {
            if (sensorID == -1) {
               System.err.println("Could not find sensor: " + useCase);
               return;
            }

            Map<String, String> params = new HashMap<>();
            params.put("userId", String.valueOf(userID));
            params.put("alert_type", String.valueOf(sensorID));
            params.put("timeRange", timeRange);

            ApiService.getInstance().get(ApiService.EP_SENSOR_DATA, params)
               .thenAccept(response -> {
                  try {
                     JsonNode root = response;
                     Document document = new Document();
                     String fileName = "User_" + userID + useCase.replace(" ", "") + "_log.pdf";
                     PdfWriter.getInstance(document, new FileOutputStream(fileName));
                     document.open();

                     Font titleFont = new Font(1, 16.0F, 1);
                     Font headingFont = new Font(1, 12.0F, 1);
                     Paragraph title = new Paragraph("User Data Report", titleFont);
                     title.setAlignment(1);
                     document.add(title);
                     document.add(new Paragraph(" "));
                     document.add(new Paragraph("User ID: " + userID, headingFont));
                     document.add(new Paragraph("Use Case: " + useCase, headingFont));
                     document.add(new Paragraph("Time Range: " + timeRange, headingFont));
                     document.add(new Paragraph("Generated: " + LocalDateTime.now(), headingFont));
                     document.add(new Paragraph(" "));

                     PdfPTable table = new PdfPTable(7);
                     table.setWidthPercentage(100.0F);
                     String[] headers = {"Time", "Value", "Feedback", "Pulses", "Intensity", "Duration (ms)", "Interval (ms)"};
                     for (String header : headers) {
                        table.addCell(new Paragraph(header, headingFont));
                     }

                     int rowCount = 0;
                     if (root.isArray()) {
                        for (JsonNode node : root) {
                           rowCount++;
                           String time = node.hasNonNull("time") ? node.get("time").asText() : "N/A";
                           double value = node.hasNonNull("value") ? node.get("value").asDouble() : 0.0;
                           String alertType = node.hasNonNull("alert_type") ? node.get("alert_type").asText() : null;
                           table.addCell(time);
                           table.addCell(String.valueOf(value));
                           table.addCell(alertType != null ? "Yes" : "No");
                           table.addCell(node.hasNonNull("pulses") ? String.valueOf(node.get("pulses").asInt()) : "N/A");
                           table.addCell(node.hasNonNull("intensity") ? String.valueOf(node.get("intensity").asInt()) : "N/A");
                           table.addCell(node.hasNonNull("duration") ? String.valueOf(node.get("duration").asInt()) : "N/A");
                           table.addCell(node.hasNonNull("interval") ? String.valueOf(node.get("interval").asInt()) : "N/A");
                        }
                     }

                     if (rowCount == 0) {
                        document.add(new Paragraph("No data found for the selected user and use case."));
                     } else {
                        document.add(table);
                     }

                     document.close();
                     File pdfFile = new File(fileName).getAbsoluteFile();
                     String fullPath = pdfFile.getAbsolutePath();
                     System.out.println("PDF generated: " + fullPath);

                     Platform.runLater(() -> {
                        Alert alert = new Alert(AlertType.INFORMATION);
                        alert.setTitle("PDF Generated");
                        alert.setHeaderText("PDF Report Created Successfully");
                        alert.setContentText("The report has been saved to:\n" + fullPath);
                        Hyperlink link = new Hyperlink("Open PDF");
                        link.setOnAction(e -> {
                           try {
                              if (Desktop.isDesktopSupported()) {
                                 Desktop.getDesktop().open(pdfFile);
                              }
                           } catch (Exception ex) {
                              System.out.println("Cannot open PDF: " + ex.getMessage());
                           }
                        });
                        VBox content = new VBox(10.0);
                        content.getChildren().addAll(new Label("The report has been saved to:"), new Label(fullPath), link);
                        alert.getDialogPane().setContent(content);
                        alert.showAndWait();
                     });

                  } catch (Exception e) {
                     System.err.println("Failed to generate PDF: " + e.getMessage());
                     Platform.runLater(() -> {
                        Alert alert = new Alert(AlertType.ERROR);
                        alert.setTitle("Error");
                        alert.setHeaderText("Failed to Generate PDF");
                        alert.setContentText("Error: " + e.getMessage());
                        alert.showAndWait();
                     });
                  }
               })
               .exceptionally(e -> {
                  System.err.println("Failed to fetch data: " + e.getMessage());
                  return null;
               });
         })
         .exceptionally(e -> {
            System.err.println("Failed to get sensor ID: " + e.getMessage());
            return null;
         });
   }
}
