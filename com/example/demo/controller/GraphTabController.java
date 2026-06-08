package com.example.demo.controller;

import com.example.demo.factory.SharedUiFactory;
import com.example.demo.model.User;
import com.example.demo.service.ApiService;
import com.example.demo.service.ExportService;
import com.example.demo.util.AlertUtils;
import com.fasterxml.jackson.databind.JsonNode;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.StackPane;
import javafx.scene.web.WebEvent;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class GraphTabController {
    private static final String FEEDBACK_GRAPH_TEMPLATE_PATH = "/com/example/demo/view/templates/feedbackGraph.html";
    private static final String GRAPH_EXPORT_ALERT_PREFIX = "__GRAPH_EXPORT__";

    @FXML
    private AnchorPane graphAnchor;
    @FXML
    private Button exportGraphCsvButton;
    @FXML
    private Button exportGraphPdfButton;
    @FXML
    private Button refreshGraphButton;

    private WebView graphWebView;
    private StackPane graphLoadingOverlay;
    private JsonNode latestGraphData;
    private final PauseTransition graphUpdateDebounce = new PauseTransition(Duration.millis(250));
    private Supplier<GraphContext> contextSupplier = () -> new GraphContext(null, null, null, null, null);
    private Consumer<JsonNode> dataLoadedCallback = ignored -> {
    };

    @FXML
    private void initialize() {
        graphUpdateDebounce.setOnFinished(ignored -> refreshGraphData());
    }

    public void setContextSupplier(Supplier<GraphContext> contextSupplier) {
        this.contextSupplier = contextSupplier == null
            ? () -> new GraphContext(null, null, null, null, null)
            : contextSupplier;
    }

    public void setDataLoadedCallback(Consumer<JsonNode> dataLoadedCallback) {
        this.dataLoadedCallback = dataLoadedCallback == null ? ignored -> {
        } : dataLoadedCallback;
    }

    public void initializeGraph() {
        refreshGraphButton.setOnAction(ignored -> scheduleGraphUpdate());
        exportGraphCsvButton.setOnAction(ignored -> exportGraphData("csv"));
        exportGraphPdfButton.setOnAction(ignored -> exportGraphData("pdf"));
        ensureGraphWebView();
        setGraphLoadingVisible(false);
        renderGraphPlaceholder("Select a use case and Users to render graph data.");
    }

    public void scheduleGraphUpdate() {
        graphUpdateDebounce.playFromStart();
    }

    public void refreshGraphData() {
        GraphContext context = contextSupplier.get();
        User user = context == null ? null : context.selectedUser();
        String useCase = context == null ? null : context.useCase();
        String range = context == null ? null : context.timeRange();

        setGraphLoadingVisible(true);

        if (user == null || useCase == null || useCase.isBlank() || range == null) {
            latestGraphData = null;
            renderGraphPlaceholder("Select a use case and Users to render graph data.");
            notifyDataLoaded(null);
            setGraphLoadingVisible(false);
            return;
        }

        ApiService.getInstance().getSensorIdByName(useCase)
            .thenAccept(sensorId -> {
                if (sensorId == -1) {
                    Platform.runLater(() -> {
                        latestGraphData = null;
                        renderGraphPlaceholder("Unable to resolve sensor type for use case: " + useCase);
                        notifyDataLoaded(null);
                        setGraphLoadingVisible(false);
                    });
                    return;
                }

                Map<String, String> params = buildSensorDataParams(user.getUserID(), sensorId, context);
                ApiService.getInstance().get(ApiService.EP_SENSOR_DATA, params)
                    .thenAccept(response -> {
                        JsonNode dataArray = ApiService.getInstance().extractArray(response);
                        latestGraphData = dataArray;
                        Platform.runLater(() -> {
                            if (dataArray == null || !dataArray.isArray() || dataArray.isEmpty()) {
                                renderGraphPlaceholder("No sensor data available for current selection.");
                                notifyDataLoaded(null);
                                setGraphLoadingVisible(false);
                                return;
                            }
                            renderGraph(dataArray, user.getUserID(), useCase);
                            notifyDataLoaded(dataArray);
                        });
                    })
                    .exceptionally(ex -> {
                        ex.printStackTrace();
                        Platform.runLater(() -> {
                            latestGraphData = null;
                            renderGraphPlaceholder("Failed to load graph data.");
                            notifyDataLoaded(null);
                            setGraphLoadingVisible(false);
                        });
                        return null;
                    });
            })
            .exceptionally(ex -> {
                ex.printStackTrace();
                Platform.runLater(() -> {
                    latestGraphData = null;
                    renderGraphPlaceholder("Failed to resolve sensor id.");
                    notifyDataLoaded(null);
                    setGraphLoadingVisible(false);
                });
                return null;
            });
    }

    private void exportGraphData(String format) {
        GraphContext context = contextSupplier.get();
        User user = context == null ? null : context.selectedUser();
        String useCase = context == null ? null : context.useCase();
        String range = context == null ? null : context.timeRange();

        if (user == null || useCase == null || useCase.isBlank() || range == null || range.isBlank()) {
            AlertUtils.showErrorAlert("Missing Selection", "Please select a Users, use case, and time range before exporting.");
            return;
        }

        if ("Custom Date Range".equals(range)) {
            LocalDate start = context.startDate();
            LocalDate end = context.endDate();
            if (start == null || end == null) {
                AlertUtils.showErrorAlert("Missing Date Range", "Please select both start and end dates for custom range export.");
                return;
            }
            if (end.isBefore(start)) {
                AlertUtils.showErrorAlert("Invalid Date Range", "End date cannot be before start date.");
                return;
            }
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("csv".equals(format) ? "Save CSV Export" : "Save PDF Export");
        fileChooser.setInitialDirectory(ExportService.getDefaultExportDirectory());
        fileChooser.setInitialFileName(ExportService.generateDefaultFilename(user.getUserID(), useCase, format));
        if ("csv".equals(format)) {
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        } else {
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
        }

        Stage stage = (Stage) graphAnchor.getScene().getWindow();
        File selectedFile = fileChooser.showSaveDialog(stage);
        if (selectedFile == null) {
            return;
        }

        ApiService.getInstance().getSensorIdByName(useCase)
            .thenAccept(sensorId -> {
                if (sensorId == -1) {
                    AlertUtils.showErrorAlert("Sensor Not Found", "Could not resolve a sensor id for use case: " + useCase);
                    return;
                }

                Map<String, String> params = buildSensorDataParams(user.getUserID(), sensorId, context);
                ApiService.getInstance().get(ApiService.EP_SENSOR_DATA, params)
                    .thenAccept(response -> {
                        try {
                            String fullPath;
                            if ("csv".equals(format)) {
                                fullPath = ExportService.generateCsv(selectedFile.getAbsolutePath(), user.getUserID(), useCase, range, response);
                            } else {
                                fullPath = ExportService.generatePdf(selectedFile.getAbsolutePath(), user.getUserID(), useCase, range, response);
                            }
                            AlertUtils.showInfoAlert("Export Successful", "Saved to:\n" + fullPath);
                        } catch (Exception ex) {
                            AlertUtils.showErrorAlert("Export Failed", ex.getMessage());
                        }
                    })
                    .exceptionally(ex -> {
                        AlertUtils.showErrorAlert("Data Fetch Failed", "Failed to fetch sensor data: " + ex.getMessage());
                        return null;
                    });
            })
            .exceptionally(ex -> {
                AlertUtils.showErrorAlert("Sensor Lookup Failed", "Failed to resolve sensor id: " + ex.getMessage());
                return null;
            });
    }

    private Map<String, String> buildSensorDataParams(int userId, int sensorId, GraphContext context) {
        Map<String, String> params = new HashMap<>();
        params.put("alert_type", String.valueOf(sensorId));
        params.put("userid", String.valueOf(userId));

        String range = context == null ? null : context.timeRange();
        if ("Custom Date Range".equals(range)) {
            params.put("range", "custom");
            LocalDate start = context.startDate();
            LocalDate end = context.endDate();
            if (start != null) {
                params.put("start_date", start.toString());
            }
            if (end != null) {
                params.put("end_date", end.toString());
            }
        } else {
            params.put("range", range);
        }

        return params;
    }

    private void renderGraph(JsonNode dataArray, int userId, String useCase) {
        ensureGraphWebView();

        String template = loadHtmlTemplate(FEEDBACK_GRAPH_TEMPLATE_PATH);
        if (template == null) {
            renderGraphPlaceholder("Graph template file is missing.");
            return;
        }

        String escapedJson = dataArray.toString().replace("\\", "\\\\").replace("\"", "\\\"");
        String html = template
            .replace("const data = DATA_PLACEHOLDER;", "const data = JSON.parse(\"" + escapedJson + "\");")
            .replace("USER_ID_PLACEHOLDER", String.valueOf(userId))
            .replace("SENSOR_TYPE_PLACEHOLDER", "\"" + useCase + "\"");

        graphWebView.getEngine().loadContent(html);
    }

    private void renderGraphPlaceholder(String message) {
        ensureGraphWebView();
        graphWebView.getEngine().loadContent(buildPlaceholderHtml(message));
    }

    private String buildPlaceholderHtml(String message) {
        return "<html><body style='font-family:Segoe UI, sans-serif;display:flex;align-items:center;justify-content:center;height:100%;margin:0;color:#4b5563;background:#ffffff;'>"
            + "<div style='text-align:center;padding:20px;'>" + escapeHtml(message) + "</div></body></html>";
    }

    private String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
    }

    private void ensureGraphWebView() {
        if (graphWebView != null) {
            ensureGraphLoadingOverlay();
            return;
        }
        graphWebView = new WebView();
        graphWebView.getEngine().setOnAlert((WebEvent<String> event) -> handleGraphAlert(event == null ? null : event.getData()));
        graphWebView.getEngine().getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == javafx.concurrent.Worker.State.SUCCEEDED) {
                setGraphLoadingVisible(false);
            } else if (newState == javafx.concurrent.Worker.State.FAILED || newState == javafx.concurrent.Worker.State.CANCELLED) {
                setGraphLoadingVisible(false);
            }
        });
        AnchorPane.setTopAnchor(graphWebView, 0.0);
        AnchorPane.setRightAnchor(graphWebView, 0.0);
        AnchorPane.setBottomAnchor(graphWebView, 0.0);
        AnchorPane.setLeftAnchor(graphWebView, 0.0);
        graphAnchor.getChildren().setAll(graphWebView);
        ensureGraphLoadingOverlay();
    }

    private void handleGraphAlert(String payload) {
        if (payload == null || !payload.startsWith(GRAPH_EXPORT_ALERT_PREFIX)) {
            return;
        }
        try {
            String json = payload.substring(GRAPH_EXPORT_ALERT_PREFIX.length());
            JsonNode root = ApiService.getInstance().getMapper().readTree(json);
            if (!"png-export".equals(root.path("type").asText(""))) {
                return;
            }
            String dataUrl = root.path("dataUrl").asText("");
            String filename = root.path("filename").asText("sensor_chart.png");
            saveGraphPngFromDataUrl(dataUrl, filename);
        } catch (Exception ex) {
            AlertUtils.showErrorAlert("Export Failed", "Could not parse export payload from chart.");
        }
    }

    private void saveGraphPngFromDataUrl(String dataUrl, String suggestedFilename) {
        if (dataUrl == null || !dataUrl.startsWith("data:image/png;base64,")) {
            AlertUtils.showErrorAlert("Export Failed", "Received invalid image data from chart export.");
            return;
        }

        String base64Payload = dataUrl.substring("data:image/png;base64,".length());
        byte[] imageBytes;
        try {
            imageBytes = Base64.getDecoder().decode(base64Payload);
        } catch (IllegalArgumentException ex) {
            AlertUtils.showErrorAlert("Export Failed", "Could not decode generated PNG data.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Graph PNG");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG Files", "*.png"));
        fileChooser.setInitialDirectory(ExportService.getDefaultExportDirectory());
        fileChooser.setInitialFileName(ensurePngExtension(suggestedFilename));

        Stage stage = (graphAnchor != null && graphAnchor.getScene() != null)
            ? (Stage) graphAnchor.getScene().getWindow()
            : null;
        File selectedFile = fileChooser.showSaveDialog(stage);
        if (selectedFile == null) {
            return;
        }

        try {
            Files.write(selectedFile.toPath(), imageBytes);
            AlertUtils.showInfoAlert("Export Successful", "Saved to:\n" + selectedFile.getAbsolutePath());
        } catch (IOException ex) {
            AlertUtils.showErrorAlert("Export Failed", "Could not save PNG file: " + ex.getMessage());
        }
    }

    private String ensurePngExtension(String filename) {
        String fallbackName = "sensor_chart.png";
        if (filename == null || filename.isBlank()) {
            return fallbackName;
        }
        String trimmed = filename.trim();
        return trimmed.toLowerCase(Locale.ROOT).endsWith(".png") ? trimmed : trimmed + ".png";
    }

    private void ensureGraphLoadingOverlay() {
        if (graphLoadingOverlay == null) {
            graphLoadingOverlay = SharedUiFactory.createLoadingOverlay("Loading graph data...");
            AnchorPane.setTopAnchor(graphLoadingOverlay, 0.0);
            AnchorPane.setRightAnchor(graphLoadingOverlay, 0.0);
            AnchorPane.setBottomAnchor(graphLoadingOverlay, 0.0);
            AnchorPane.setLeftAnchor(graphLoadingOverlay, 0.0);
        }

        if (!graphAnchor.getChildren().contains(graphLoadingOverlay)) {
            graphAnchor.getChildren().add(graphLoadingOverlay);
        }
    }

    private void setGraphLoadingVisible(boolean visible) {
        ensureGraphWebView();
        graphLoadingOverlay.setVisible(visible);
        graphLoadingOverlay.setManaged(visible);
        if (visible) {
            graphLoadingOverlay.toFront();
        }
    }

    private String loadHtmlTemplate(String resourcePath) {
        try {
            InputStream inputStream = this.getClass().getResourceAsStream(resourcePath);
            if (inputStream == null) {
                return null;
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            StringBuilder htmlContent = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                htmlContent.append(line).append("\n");
            }
            return htmlContent.toString();
        } catch (IOException ex) {
            ex.printStackTrace();
            return null;
        }
    }

    private void notifyDataLoaded(JsonNode dataArray) {
        dataLoadedCallback.accept(dataArray);
    }

    public record GraphContext(User selectedUser, String useCase, String timeRange, LocalDate startDate, LocalDate endDate) {
    }
}
