package com.example.demo.controller;

import com.example.demo.model.User;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

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
   private final String htmlTemplate = "\t\t\t\t\t\t<!DOCTYPE html>\n\t\t\t<html lang=\"en\">\n\t\t\t<head>\n\t\t\t  <meta charset=\"UTF-8\">\n\t\t\t  <title>Sensor Feedback Over Time</title>\n\t\t\t  <script src=\"https://d3js.org/d3.v7.min.js\"></script>\n\t\t\t  <style>\n\t\t\t    body {\n\t\t\t      font-family: sans-serif;\n\t\t\t      margin: 0;\n\t\t\t      padding: 20px;\n\t\t\t    }\n\n\t\t\t    h2 {\n\t\t\t      text-align: center;\n\t\t\t      margin-bottom: 10px;\n\t\t\t    }\n\t\t\t.tooltip {\n\t\t\t  position: absolute;\n\t\t\t  background: rgba(255, 255, 255, 0.95);\n\t\t\t  border: 1px solid #888;\n\t\t\t  border-radius: 6px;\n\t\t\t  padding: 10px;\n\t\t\t  box-shadow: 0px 2px 8px rgba(0,0,0,0.2);\n\t\t\t  font-size: 13px;\n\t\t\t  line-height: 1.4;\n\t\t\t  pointer-events: none;\n\t\t\t  opacity: 0;\n\t\t\t  transition: opacity 0.3s ease, transform 0.3s ease;\n\t\t\t  transform: translateY(-10px);\n\t\t\t}\n\n\t\t\t    .legend {\n\t\t\t      font-size: 12px;\n\t\t\t    }\n\n\t\t\t    svg {\n\t\t\t      width: 100%;\n\t\t\t      height: 600px;\n\t\t\t    }\n\n\t\t\t    /* ✨ Modern axis styles */\n\t\t\t    .axis path,\n\t\t\t    .axis line {\n\t\t\t      stroke: #333;\n\t\t\t      stroke-width: 1.5px;\n\t\t\t      shape-rendering: crispEdges;\n\t\t\t    }\n\n\t\t\t    .axis text {\n\t\t\t      fill: #333;\n\t\t\t      font-size: 13px;\n\t\t\t      font-family: sans-serif;\n\t\t\t    }\n\n\t\t\t    .grid line {\n\t\t\t      stroke: #ddd;\n\t\t\t      stroke-opacity: 0.7;\n\t\t\t      shape-rendering: crispEdges;\n\t\t\t    }\n\n\t\t\t    .grid path {\n\t\t\t      stroke: none;\n\t\t\t    }\n\t\t\t  </style>\n\t\t\t</head>\n\t\t\t<body>\n\t\t\t  <h2>Sensor Feedback Over Time</h2>\n\t\t\t  <h3 id=\"meta-info\" style=\"text-align:center; margin-top: 0; font-weight: normal; color: #555;\"></h3>\n\n\t\t\t  <svg></svg>\n\t\t\t  <div class=\"tooltip\"></div>\n\n\t\t\t  <script>\n\t\t\tconst data = DATA_PLACEHOLDER;\n\n\n\t\t\tconsole.log(data.map(d => d.time));\n\n\n\n\t\t\t    const userId = USER_ID_PLACEHOLDER; //\n\t\t\t    const sensorType = SENSOR_TYPE_PLACEHOLDER;\n\n\t\t\t    document.getElementById(\"meta-info\").textContent = `User ID: ${userId} | Sensor: ${sensorType}`;\n\t\t\tconst parseTime = d3.utcParse(\"%Y-%m-%dT%H:%M:%S.%LZ\");\n\t\t\tconst formatTime = d3.timeFormat(\"%Y-%m-%d %H:%M\");\n\t\t\t    data.forEach(d => d.parsedTime = parseTime(d.time));\n\n\t\t\t    const svg = d3.select(\"svg\");\n\t\t\tconst margin = { top: 40, right: 200, bottom: 60, left: 100 };\n\t\t\t    const width = svg.node().clientWidth - margin.left - margin.right;\n\t\t\t    const height = svg.node().clientHeight - margin.top - margin.bottom;\n\t\t\t// Define a clip path to constrain drawing inside the chart area\n\t\t\tsvg.append(\"defs\").append(\"clipPath\")\n\t\t\t  .attr(\"id\", \"clip\")\n\t\t\t  .append(\"rect\")\n\t\t\t  .attr(\"x\", 0)\n\t\t\t  .attr(\"y\", 0)\n\t\t\t  .attr(\"width\", width)\n\t\t\t  .attr(\"height\", height);\n\n\t\t\t// Main container group\n\t\t\tconst g = svg.append(\"g\")\n\t\t\t  .attr(\"transform\", `translate(${margin.left},${margin.top})`);\n\n\t\t\t// This group will be clipped — for line and circles only\n\t\t\tconst dataLayer = g.append(\"g\")\n\t\t\t  .attr(\"clip-path\", \"url(#clip)\");\n\n\n\t\t\t    const x = d3.scaleTime()\n\t\t\t      .domain(d3.extent(data, d => d.parsedTime))\n\t\t\t      .range([20, width]);\n\n\t\t\t    const y = d3.scaleLinear()\n\t\t\t\t.domain([\n\t\t\t\t    (() => {\n\t\t\t\t        const minVal = d3.min(data, d => d.value);\n\t\t\t\t        return minVal === 0 ? 0 : minVal - 10;\n\t\t\t\t    })(),\n\t\t\t\t    d3.max(data, d => d.value) + 10\n\t\t\t\t])\n\t\t\t    .nice()\n\t\t\t    .range([height, 0]);\n\t\t\tconst intensityThresholds = [20, 50, 100, 150, 200, 230, 255];\n\t\t\tconst intensityColors = [\n\t\t  \"#fceabb\",  // 0–20 → very light yellow\n\t\t  \"#f8c156\",  // 21–50 → light orange\n\t\t  \"#f59e42\",  // 51–100 → orange\n\t\t  \"#ec5e34\",  // 101–150 → red-orange\n\t\t  \"#cd2e3a\",  // 151–200 → red\n\t\t  \"#8e063b\",  // 201–230 → dark red\n\t\t  \"#3b0a45\"   // 231–255 → very dark purple\n\t\t];\nconst color = d3.scaleThreshold()\n  .domain(intensityThresholds)\n  .range(intensityColors);\n\n\t\t\t    const maxPulses = d3.max(data, d => d.pulses || 0) || 1;\n\n\n\tconst radius = d3.scaleSqrt()\n\t  .domain([1, maxPulses])\n\t  .range([2, 8]);  //  Dynamically scaled, but visually safe\n\n\t\t\t    const tooltip = d3.select(\".tooltip\");\n\n\tconst timeExtent = d3.extent(data, d => d.parsedTime);\n\tconst tickCount = 6;\n\tconst step = (timeExtent[1] - timeExtent[0]) / (tickCount - 1);\n\n\tconst tickTimes = d3.range(tickCount).map(i => new Date(timeExtent[0].getTime() + i * step));\n\t\tconst xAxis = d3.axisBottom(x)\n\t\t  .tickValues(tickTimes)      // Use exactly these values\n\t\t  .tickFormat(formatTime);    // Format each tick as \"YYYY-MM-DD HH:mm\"\n\t\t\tconst xAxisG = g.append(\"g\")\n\t\t\t  .attr(\"class\", \"axis x-axis\")\n\t\t\t  .attr(\"transform\", `translate(0,${height})`)\n\t\t\t  .call(xAxis);\n\n\t\t\t    g.append(\"g\")\n\t\t\t      .attr(\"class\", \"axis y-axis grid\")\n\t\t\t      .call(\n\t\t\t        d3.axisLeft(y)\n\t\t\t          .ticks(6)\n\t\t\t          .tickSize(-width)\n\t\t\t      );\n\n\t\t\t    // Line generator\n\t\t\t    const line = d3.line()\n\t\t\t      .x(d => x(d.parsedTime))\n\t\t\t      .y(d => y(d.value))\n\t\t\t      .curve(d3.curveMonotoneX); // optional smoothing\n\n\n\t\t\t        // Group by minute and pick one alert per minute, or fallback to any point in that minute\n\t\t\t    const bucketedData = d3.groups(data, d => d3.timeMinute(d.parsedTime));\n\t\t\t    const reducedData = bucketedData.map(([minute, group]) =>\n\t\t\t    group.find(d => d.alert_type) || group[0]\n\t\t\t    );\n\t\t\t    // Draw animated line\n\t\t\tconst linePath = dataLayer.append(\"path\")\n\t\t\t      .datum(data)\n\t\t\t      .attr(\"fill\", \"none\")\n\t\t\t      .attr(\"stroke\", \"#4682B4\")\n\t\t\t      .attr(\"stroke-width\", 2)\n\t\t\t      .attr(\"d\", line);\n\n\t\t\t    const totalLength = linePath.node().getTotalLength();\n\n\t\t\t    linePath\n\t\t\t      .attr(\"stroke-dasharray\", totalLength + \" \" + totalLength)\n\t\t\t      .attr(\"stroke-dashoffset\", totalLength)\n\t\t\t      .transition()\n\t\t\t      .duration(2000)\n\t\t\t      .ease(d3.easeLinear)\n\t\t\t      .attr(\"stroke-dashoffset\", 0);\n\n\t\t\t    // Feedback circles with animation\n\t\t\tdataLayer.selectAll(\"circle\")\n\t\t\t      .data(data.filter(d => d.alert_type))\n\t\t\t      .enter().append(\"circle\")\n\t\t\t      .attr(\"cx\", d => x(d.parsedTime))\n\t\t\t      .attr(\"cy\", d => y(d.value))\n\t\t\t      .attr(\"r\", 0)\n\t\t\t      .attr(\"fill\", d => color(d.intensity))\n\t\t\t      .attr(\"stroke\", \"#333\")\n\t\t\t      .attr(\"opacity\", 0)\n\t\t\t      .transition()\n\t\t\t      .duration(1000)\n\t\t\t      .delay((d, i) => i * 250)\n\t\t\t      .attr(\"r\", d => radius(d.pulses))\n\t\t\t      .attr(\"opacity\", 1);\n\n\t\t\t    // Re-select for interactivity\n\t\t\tdataLayer.selectAll(\"circle\")\n\t\t\t      .on(\"mouseover\", function (event, d) {\n\t\t\t        tooltip\n\t\t\t        .style(\"transform\", \"translateY(-10px)\")\n\t\t\t        .transition()\n\t\t\t        .duration(200)\n\t\t\t        .style(\"opacity\", 1)\n\t\t\t        .style(\"transform\", \"translateY(0px)\");\n\t\t\t    tooltip.html(\n\t\t\t            `<strong>Time:</strong> ${formatTime(d.parsedTime)}<br>\n\t\t\t            <strong>Value:</strong> ${d.value}<br>\n\t\t\t            <strong>Intensity:</strong> ${d.intensity}<br>\n\t\t\t            <strong>Pulses:</strong> ${d.pulses}<br>\n\t\t\t            <strong>Duration:</strong> ${d.duration || 0} ms<br>\n\t\t\t            <strong>Interval:</strong> ${d.interval || 0} ms`\n\t\t\t        )\n\t\t\t          .style(\"left\", (event.pageX + 10) + \"px\")\n\t\t\t          .style(\"top\", (event.pageY - 30) + \"px\");\n\n\t\t\t        d3.select(this)\n\t\t\t          .transition()\n\t\t\t          .duration(150)\n\t\t\t          .attr(\"r\", radius(d.pulses) * 1.3);\n\t\t\t      })\n\t\t\t      .on(\"mouseout\", function (event, d) {\n\t\t\t        tooltip.transition().duration(200).style(\"opacity\", 0);\n\t\t\t        d3.select(this)\n\t\t\t          .transition()\n\t\t\t          .duration(200)\n\t\t\t        .attr(\"r\", radius(d.pulses));\n\t\t\t      });\n\n\t\t\t    // Legend\n\t\t\t    const legendThresholds = [0, ...intensityThresholds]; // include start 0 explicitly\n\t\t\t\tconst legend = svg.append(\"g\")\n\t\t\t\t  .attr(\"class\", \"legend\")\n\t\t\t\t  .attr(\"transform\", `translate(${width + margin.left + 20},${margin.top})`);\n\t\t\t    legend.append(\"text\")\n\t\t\t  .text(\"Intensity (Color)\")\n\t\t\t  .attr(\"font-weight\", \"bold\");\n\n\t\t\tlegendThresholds.forEach((v, i) => {\n\t\t\t  if (i < intensityColors.length) {\n\t\t\t    const rangeLabel = `${v}–${intensityThresholds[i] ?? 255}`;\n\n\t\t\t    legend.append(\"rect\")\n\t\t\t      .attr(\"x\", 0)\n\t\t\t      .attr(\"y\", 20 + i * 14)\n\t\t\t      .attr(\"width\", 20)\n\t\t\t      .attr(\"height\", 12)\n\t\t\t      .attr(\"fill\", intensityColors[i]);\n\n\t\t\t    legend.append(\"text\")\n\t\t\t      .attr(\"x\", 30)\n\t\t\t      .attr(\"y\", 30 + i * 14)\n\t\t\t      .text(rangeLabel);\n\t\t\t  }\n\t\t\t});\n\n\t\t\t    legend.append(\"text\").text(\"Pulses (Size)\").attr(\"y\", 170).attr(\"font-weight\", \"bold\");\n\t\t\tlet pulseValues;\n\n\t\t\tif (maxPulses <= 10) {\n\t\t\t  pulseValues = [2, 4, 6, 8, 10];\n\t\t\t} else {\n\t\t\t  const step = Math.ceil(maxPulses / 5); // choose 5 buckets\n\t\t\t  pulseValues = d3.range(step, maxPulses + 1, step);\n\t\t\t}\n\t\t\tlet currentY = 190; // starting y-position\n\t\t\tconst pulsePadding = 6; // desired gap between circles\n\t\t\tpulseValues.forEach((v, i) => {\n\t\t\t  const r = radius(v);\n\n\t\t\t  legend.append(\"circle\")\n\t\t\t    .attr(\"cx\", 10)\n\t\t\t    .attr(\"cy\", currentY + r)\n\t\t\t    .attr(\"r\", r)\n\t\t\t    .attr(\"fill\", \"#999\");\n\n\t\t\t  legend.append(\"text\")\n\t\t\t    .attr(\"x\", 30)\n\t\t\t    .attr(\"y\", currentY + r + 4) // text slightly below circle center\n\t\t\t    .text(v);\n\n\t\t\t  currentY += 2 * r + pulsePadding; // move down for next circle\n\t\t\t});\n\n\t\t\tconst zoom = d3.zoom()\n\t\t\t  .scaleExtent([1, 100]) // Zoom in/out limits\n\t\t\t  .translateExtent([[0, 0], [width, height]]) // Pan bounds\n\t\t\t  .extent([[0, 0], [width, height]])\n\t\t\t  .on(\"zoom\", zoomed);\n\n\t\t\tsvg.call(zoom);\n\n\t\t\t// Prevent zoom reset on double-click\n\t\t\tsvg.on(\"dblclick.zoom\", null);\n\n\t\t\tfunction zoomed(event) {\n\t\t\t  const transform = event.transform;\n\t\t\t  const zx = transform.rescaleX(x);\n\n\t\t\t  // Updated line generator using same smoothing\n\t\t\t  const lineWithZoom = d3.line()\n\t\t\t    .x(d => zx(d.parsedTime))\n\t\t\t    .y(d => y(d.value))\n\t\t\t    .curve(d3.curveMonotoneX);\n\n\t\t\t  // Redraw the line using same data\n\t\t\t  linePath.datum(data).attr(\"d\", lineWithZoom);\n\n\t\t\t  // Reposition the alert circles\n\t\t\t  g.selectAll(\"circle\")\n\t\t\t    .attr(\"cx\", d => zx(d.parsedTime));\n\t\t\t}\n\t\t\t  </script>\n\t\t\t</body>\n\t\t\t</html>\n\n";

   //ngrok link base:
   private final String URL_BASE = /*"https://marcella-unguerdoned-ayanna.ngrok-free.dev"*/ "http://localhost:5678" + "/webhook";   //tali

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

      this.userSelector.setOnAction(ex -> this.updateChart());
      this.useCaseSelector.setOnAction(ex -> {
         String selected = (String)this.useCaseSelector.getValue();
         this.isSunPositionSelected = "SunAzimuth".equals(selected);
         this.updateChart();
      });
      this.timeRangeSelector.getItems().addAll(new String[]{"Last 24 Hours", "Last 7 Days", "Last 30 Days", "All Time"});
      this.timeRangeSelector.setValue("All Time");
      this.timeRangeSelector.setOnAction(ex -> this.updateChart());
      if (this.generatePdfButton != null) {
         this.generatePdfButton.setOnAction(ex -> this.generatePdfLog());
      }
   }

   private void loadUserData() {
      this.userData.clear();

      try {
         // Keep your existing URL_BASE setup
         URL url = new URL(URL_BASE + "/get-users");
         HttpURLConnection conn = (HttpURLConnection)url.openConnection();
         conn.setRequestMethod("GET");

         // Add timeouts to prevent hanging
         conn.setConnectTimeout(5000);
         conn.setReadTimeout(5000);

         BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
         StringBuilder response = new StringBuilder();

         String line;
         while ((line = reader.readLine()) != null) {
            response.append(line);
         }
         reader.close();

         ObjectMapper mapper = new ObjectMapper();
         JsonNode root = mapper.readTree(response.toString());

         // --- START OF MISSING LOGIC ---
         JsonNode usersArray = null;

         // Check 1: Is it a raw array? [ {user}, {user} ]
         if (root.isArray()) {
            usersArray = root;
         }
         // Check 2: Is it wrapped in "data"? { "data": [ ... ] }
         else if (root.has("data") && root.get("data").isArray()) {
            usersArray = root.get("data");
         }
         // Check 3: Is it wrapped in "payload"? { "payload": [ ... ] }
         else if (root.has("payload") && root.get("payload").isArray()) {
            usersArray = root.get("payload");
         }
         // Check 4: Is it a single user object? { "userid": 1, ... }
         else if (root.has("userid")) {
            usersArray = mapper.createArrayNode().add(root);
         }

         if (usersArray == null) {
            System.err.println("Unexpected JSON format: " + response.toString());
            return;
         }
         // --- END OF MISSING LOGIC ---

         for (JsonNode userNode : usersArray) {
            // Add safety check for missing ID
            if (userNode.hasNonNull("userid")) {
               int id = userNode.get("userid").asInt();
               String firstName = userNode.hasNonNull("fname") ? userNode.get("fname").asText() : "";
               String lastName = userNode.hasNonNull("lname") ? userNode.get("lname").asText() : "";
               this.userData.add(new User(id, firstName, lastName));
            }
         }

         // Force UI refresh if needed
         Platform.runLater(this::populateUserSelector);

      } catch (Exception e) {
         e.printStackTrace();
      }
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

      // FIX 1: Get the numeric Sensor ID (n8n needs the ID, not the name)
      int sensorID = getSensorIdByName(useCase);

      if (sensorID == -1) {
         System.out.println("Could not find ID for sensor: " + useCase);
         return;
      }

      try {
         // FIX 2: Build the URL with the numeric ID (alert_type=ID)
         String urlStr = URL_BASE + "/sensor-data?range="
                 + URLEncoder.encode(timeRange, StandardCharsets.UTF_8)
                 + "&alert_type=" + sensorID
                 + "&userid=" + selectedUserID;

         System.out.println("Fetching data from: " + urlStr);

         HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
         conn.setRequestMethod("GET");
         conn.setConnectTimeout(5000);
         conn.setReadTimeout(5000);

         int responseCode = conn.getResponseCode();

         // Handle non-200 errors
         if (responseCode != 200) {
            System.err.println("API Request failed with code: " + responseCode);
            return;
         }

         BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
         String json = reader.lines().collect(Collectors.joining());
         reader.close();

         // Check for empty or error responses before loading WebView
         if (json.isEmpty() || json.contains("\"message\":\"No data found\"") || json.contains("\"statusCode\":404")) {
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

         // FIX 3: Safe JSON Injection into JavaScript
         // Escape backslashes and quotes so the JSON string doesn't break the JS syntax
         String escapedJson = json.replace("\\", "\\\\").replace("\"", "\\\"");

         // We inject a small JS snippet that parses the string AND handles the wrapper
         // Logic: Parse string -> Check if it has .payload -> Use that or the raw object
         String jsDataSetup = "const rawRes = JSON.parse(\"" + escapedJson + "\"); " +
                 "const data = Array.isArray(rawRes) ? rawRes : (rawRes.payload || rawRes.data || []);";

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
         e.printStackTrace();
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

      for (User user : this.userData) {
         int userID = user.getUserID();

         try {
            String urlStr = URL_BASE+"/sensor-data?userId=" + userID + "&alert_type=" + getSensorIdByName("HeartRate");            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection)url.openConnection();
            conn.setRequestMethod("GET");
            if (conn.getResponseCode() != 200) {
               System.err.println("HTTP request failed with response code: " + conn.getResponseCode());
            } else {
               BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
               StringBuilder response = new StringBuilder();

               String line;
               while ((line = reader.readLine()) != null) {
                  response.append(line);
               }

               reader.close();
               ObjectMapper mapper = new ObjectMapper();
               JsonNode root = mapper.readTree(response.toString());
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
                        if (node.has("sensorid") && node.get("sensorid").asInt() == 11) {
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
                           } else {
                              System.out.println("Node missing time or value field: " + node);
                           }
                        }
                     } catch (Exception var28) {
                        System.err.println("Error processing node " + node + ": " + var28.getMessage());
                     }
                  }
               }

               if (count == 0) {
                  document.add(new Paragraph("No heart rate data found for today."));
                  document.add(new Paragraph("Debug Info:"));
                  document.add(new Paragraph("Today's date: " + today.format(dateOnly)));
                  document.add(new Paragraph("Total records received: " + root.size()));
                  document.add(new Paragraph("Filtered for sensor ID: 11 (Heart Rate)"));
               } else {
                  document.add(table);
                  document.add(new Paragraph("Total heart rate records for today: " + count));
               }

               document.close();
               writer.close();
               System.out.println("PDF saved: " + new File(fileName).getAbsolutePath());

               try {
                  if (Desktop.isDesktopSupported()) {
                     Desktop.getDesktop().open(new File(fileName));
                  } else {
                     String osName = System.getProperty("os.name").toLowerCase();
                     if (osName.contains("win")) {
                        Runtime.getRuntime().exec("rundll32 url.dll,FileProtocolHandler " + new File(fileName).getAbsolutePath());
                     } else if (osName.contains("mac")) {
                        Runtime.getRuntime().exec("open " + new File(fileName).getAbsolutePath());
                     } else if (osName.contains("nix") || osName.contains("nux")) {
                        String[] browsers = new String[]{"google-chrome", "firefox", "mozilla", "epiphany", "konqueror", "netscape", "opera", "links", "lynx"};
                        String browser = null;
                        String[] var26 = browsers;
                        int var25 = browsers.length;
                        int var34 = 0;

                        while (true) {
                           if (var34 < var25) {
                              String b = var26[var34];
                              if (Runtime.getRuntime().exec(new String[]{"which", b}).waitFor() != 0) {
                                 var34++;
                                 continue;
                              }

                              browser = b;
                           }

                           if (browser != null) {
                              Runtime.getRuntime().exec(new String[]{browser, new File(fileName).getAbsolutePath()});
                           } else {
                              System.out.println("Could not find web browser to open PDF");
                           }
                           break;
                        }
                     }
                  }
               } catch (Exception var27) {
                  System.out.println("Could not open PDF in browser: " + var27.getMessage());
               }
            }
         } catch (Exception var29) {
            System.err.println("Error processing user " + userID + ": " + var29.getMessage());
            var29.printStackTrace();
         }
      }
   }

   public int getSensorIdByName(String name) {
      try {
         URL url = new URL(URL_BASE+"/get-sensor-types");
         HttpURLConnection conn = (HttpURLConnection)url.openConnection();
         conn.setRequestMethod("GET");
         if (conn.getResponseCode() != 200) {
            System.out.println("HTTP error: " + conn.getResponseCode());
            return -1;
         } else {
            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder response = new StringBuilder();

            String line;
            while ((line = in.readLine()) != null) {
               response.append(line);
            }

            in.close();
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(response.toString());
            Map<String, Integer> sensorMap = new HashMap<>();

            for (JsonNode node : root) {
               String sensorName = node.get("name").asText();
               int sensorId = node.get("sensorid").asInt();
               sensorMap.put(sensorName, sensorId);
            }

            return sensorMap.getOrDefault(name, -1);
         }
      } catch (Exception var14) {
         var14.printStackTrace();
         return -1;
      }
   }

   @FXML
   private void generatePdfLog() {
      String userSelection = (String)this.userSelector.getValue();
      String useCase = (String)this.useCaseSelector.getValue();
      if (userSelection != null && useCase != null) {
         int userID = Integer.parseInt(userSelection.split(" ")[0]);
         int sensorID = this.getSensorIdByName(useCase);
         String timeRange = (String)this.timeRangeSelector.getValue();

         try {
            String urlStr = URL_BASE+"/sensor-data?userId=" + userID + "&alert_type=" + sensorID + "&timeRange=" + timeRange.replace(" ", "%20");            URL url = new URL(urlStr);
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
            String[] headers = new String[]{"Time", "Value", "Feedback", "Pulses", "Intensity", "Duration (ms)", "Interval (ms)"};

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
               link.setOnAction(ex -> {
                  try {
                     if (Desktop.isDesktopSupported()) {
                        Desktop.getDesktop().open(pdfFile);
                     }
                  } catch (Exception var3x) {
                     System.out.println("Cannot open PDF: " + var3x.getMessage());
                  }
               });
               VBox content = new VBox(10.0);
               content.getChildren().addAll(new Node[]{new Label("The report has been saved to:"), new Label(fullPath), link});
               alert.getDialogPane().setContent(content);
               alert.showAndWait();
            });
         } catch (Exception var28) {
            var28.printStackTrace();
            Platform.runLater(() -> {
               Alert alert = new Alert(AlertType.ERROR);
               alert.setTitle("Error");
               alert.setHeaderText("Failed to Generate PDF");
               alert.setContentText("Error: " + var28.getMessage());
               alert.showAndWait();
            });
         }
      } else {
         System.out.println("Please select a user and use case.");
      }
   }
}
