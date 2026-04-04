package com.example.demo.controller;

import com.example.demo.controller.state.DashboardState;
import com.example.demo.model.SensorRuleConfig;
import com.example.demo.model.User;
import com.example.demo.service.ApiService;
import com.example.demo.service.ExportService;
import com.fasterxml.jackson.databind.JsonNode;
import javafx.beans.value.ChangeListener;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Alert;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DashboardController {
    private static final List<String> DEFAULT_USE_CASES = List.of("HeartRate", "MoonAzimuth", "SunAzimuth", "Pollution");
    private static final String DELETE_ICON_PATH = "/com/example/demo/images/delete.png";
    private static final String FEEDBACK_GRAPH_TEMPLATE_PATH = "/com/example/demo/view/templates/feedbackGraph.html";
    private static final String MINI_GRAPH_TEMPLATE_PATH = "/com/example/demo/view/templates/miniFeedbackGraph.html";
    private static final Pattern LOG_INTERVAL_PATTERN = Pattern.compile("^\\d+(\\.\\d+)?\\s+(second|seconds|minute|minutes|hour|hours|day|days)$", Pattern.CASE_INSENSITIVE);

    @FXML
    private ToggleButton useCasesNavButton;
    @FXML
    private ToggleButton participantManagementNavButton;
    @FXML
    private VBox useCasesSidebarPane;
    @FXML
    private VBox participantManagementSidebarPane;
    @FXML
    private ListView<String> useCaseListView;
    @FXML
    private ListView<String> participantSidebarList;
    @FXML
    private Label participantCountLabel;

    @FXML
    private Label selectedUseCaseLabel;
    @FXML
    private ComboBox<User> participantComboBox;
    @FXML
    private ComboBox<String> timeRangeComboBox;
    @FXML
    private DatePicker startDatePicker;
    @FXML
    private DatePicker endDatePicker;

    @FXML
    private TabPane workspaceTabPane;
    @FXML
    private Tab agentChatTab;
    @FXML
    private Tab graphTab;
    @FXML
    private Tab mappingsTab;

    @FXML
    private ScrollPane chatScrollPane;
    @FXML
    private VBox chatHistoryBox;
    @FXML
    private TextField chatInputField;
    @FXML
    private Button chatSendButton;
    @FXML
    private VBox contextDrawer;
    @FXML
    private Button contextDrawerToggleButton;
    @FXML
    private Label contextUseCaseLabel;
    @FXML
    private Label contextParticipantLabel;
    @FXML
    private VBox activeRulesSummaryBox;
    @FXML
    private AnchorPane miniGraphAnchor;

    @FXML
    private AnchorPane graphAnchor;
    @FXML
    private Button exportGraphCsvButton;
    @FXML
    private Button exportGraphPdfButton;
    @FXML
    private Button refreshGraphButton;
    @FXML
    private Button refreshedSimplifiedGraphButton;
    @FXML
    private FlowPane mappingsFlowPane;
    @FXML
    private Label mappingsEmptyLabel;
    @FXML
    private Label ruleSensorTypeLabel;
    @FXML
    private Label ruleSensorTypeDescriptionLabel;
    @FXML
    private TextField ruleMinValueField;
    @FXML
    private TextField ruleMaxValueField;
    @FXML
    private TextField ruleMinPulsesField;
    @FXML
    private TextField ruleMaxPulsesField;
    @FXML
    private TextField ruleMinIntensityField;
    @FXML
    private TextField ruleMaxIntensityField;
    @FXML
    private TextField ruleMinDurationField;
    @FXML
    private TextField ruleMaxDurationField;
    @FXML
    private TextField ruleMinIntervalField;
    @FXML
    private TextField ruleMaxIntervalField;
    @FXML
    private Button saveRuleButton;
    @FXML
    private Button clearRuleButton;
    @FXML
    private Button refreshRuleButton;
    @FXML
    private Label loggingIntervalValueLabel;
    @FXML
    private Button editLoggingIntervalButton;
    @FXML
    private Button refreshRuleSummaryButton;
    @FXML
    private Label agentTypingLabel;

    private final DashboardState state = DashboardState.getInstance();
    private final ObservableList<User> users = FXCollections.observableArrayList();
    private final ObservableList<String> useCases = FXCollections.observableArrayList();
    private final Map<String, String> useCaseDescriptions = new HashMap<>();
    private final Map<String, String> useCaseDisplayNames = new HashMap<>();
    private final Map<String, Integer> useCaseIdsByNormalizedName = new HashMap<>();
    private final Map<String, String> useCaseLoggingInterval = new HashMap<>();
    private final List<RuleCardData> allRules = new ArrayList<>();
    private final String chatSessionId = UUID.randomUUID().toString();

    private WebView graphWebView;
    private WebView miniGraphWebView;
    private StackPane graphLoadingOverlay;
    private StackPane miniGraphLoadingOverlay;
    private JsonNode latestGraphData;
    private final PauseTransition graphUpdateDebounce = new PauseTransition(Duration.millis(250));

    @FXML
    private void initialize() {
        setupSidebar();
        setupTopbar();
        setupTabs();
        setupChat();
        setupGraph();
        setupMappingsRuleBuilder();
        setupStateListeners();

        loadParticipants();
        loadUseCases();
        loadMappings();
    }

    /**
     * Configures rule builder and applies integer-only input guards
     */
    private void setupMappingsRuleBuilder() {
        updateRuleBuilderUseCaseDisplay(state.getSelectedUseCase());
        enforceIntegerInput(ruleMinValueField);
        enforceIntegerInput(ruleMaxValueField);
        enforceIntegerInput(ruleMinPulsesField);
        enforceIntegerInput(ruleMaxPulsesField);
        enforceIntegerInput(ruleMinIntensityField);
        enforceIntegerInput(ruleMaxIntensityField);
        enforceIntegerInput(ruleMinDurationField);
        enforceIntegerInput(ruleMaxDurationField);
        enforceIntegerInput(ruleMinIntervalField);
        enforceIntegerInput(ruleMaxIntervalField);
    }

    private void updateRuleBuilderUseCaseDisplay(String selectedUseCase) {
        if (ruleSensorTypeLabel == null || ruleSensorTypeDescriptionLabel == null) {
            return;
        }

        if (selectedUseCase == null || selectedUseCase.isBlank()) {
            ruleSensorTypeLabel.setText("-");
            ruleSensorTypeDescriptionLabel.setText("Select a use case to build a rule.");
            return;
        }

        String normalizedKey = normalizeUseCaseName(selectedUseCase);
        ruleSensorTypeLabel.setText(toUseCaseLabel(normalizedKey));

        String description = useCaseDescriptions.getOrDefault(normalizedKey, "").trim();
        ruleSensorTypeDescriptionLabel.setText(description.isEmpty() ? "No description available." : description);
    }


    private void enforceIntegerInput(TextField textField) {
        if (textField == null) {
            return;
        }
        UnaryOperator<TextFormatter.Change> rangeFilter = change -> {
            String newText = change.getControlNewText();
            return newText.matches("\\d*") ? change : null;
        };
        textField.setTextFormatter(new TextFormatter<>(rangeFilter));
    }

    @FXML
    private void saveRuleBuilderConfig() {
        try {
            SensorRuleConfig ruleConfig = buildRuleConfigFromForm();

            ApiService.getInstance().saveSensorRuleConfig(ruleConfig)
                .thenAccept(success -> Platform.runLater(() -> {
                    if (!success) {
                        showErrorAlert("Save Failed", "Could not save the rule. Please try again.");
                        return;
                    }

                    clearRuleBuilderForm();
                    loadMappings();
                    String savedUseCase = toUseCaseLabel(normalizeUseCaseName(ruleConfig.getType()));
                    showInfoAlert("Save Successful", "Saved mapping for " + savedUseCase + ".");
                    refreshMappings();
                }))
                .exceptionally(ex -> {
                    showErrorAlert("Save Failed", "Could not save rule: " + ex.getMessage());
                    return null;
                });
        } catch (IllegalArgumentException ex) {
            showErrorAlert("Validation Error", ex.getMessage());
        }
    }

    @FXML
    private void clearRuleInputFields() {
        ruleMinValueField.clear();
        ruleMaxValueField.clear();
        ruleMinPulsesField.clear();
        ruleMaxPulsesField.clear();
        ruleMinIntensityField.clear();
        ruleMaxIntensityField.clear();
        ruleMinDurationField.clear();
        ruleMaxDurationField.clear();
        ruleMinIntervalField.clear();
        ruleMaxIntervalField.clear();
    }

    @FXML
    private void refreshMappings() {
        try {
            String useCase = state.getSelectedUseCase();
            if (useCase == null || useCase.isBlank()) {
                throw new IllegalArgumentException("Please select a use case.");
            }
        } catch (IllegalArgumentException ex) {
            showErrorAlert("Validation Error", ex.getMessage());
            return;
        }
        loadMappings();
    }

    private SensorRuleConfig buildRuleConfigFromForm() {
        String sensorType = state.getSelectedUseCase() == null ? null : state.getSelectedUseCase();
        if (sensorType == null || sensorType.isBlank()) {
            throw new IllegalArgumentException("Please select an active use case.");
        }

        int minValue = parseRequiredInt(ruleMinValueField, "Min Value");
        int maxValue = parseRequiredInt(ruleMaxValueField, "Max Value");
        if (minValue > maxValue) {
            throw new IllegalArgumentException("Min Value cannot be greater than Max Value.");
        }

        int minPulses = parseRequiredInt(ruleMinPulsesField, "Min Pulses");
        int maxPulses = parseRequiredInt(ruleMaxPulsesField, "Max Pulses");
        if (minPulses > maxPulses) {
            throw new IllegalArgumentException("Min Pulses cannot be greater than Max Pulses.");
        }

        int minIntensity = parseRequiredInt(ruleMinIntensityField, "Min Intensity");
        int maxIntensity = parseRequiredInt(ruleMaxIntensityField, "Max Intensity");
        if (minIntensity > maxIntensity) {
            throw new IllegalArgumentException("Min Intensity cannot be greater than Max Intensity.");
        }

        int minDuration = parseRequiredInt(ruleMinDurationField, "Min Duration");
        int maxDuration = parseRequiredInt(ruleMaxDurationField, "Max Duration");
        if (minDuration > maxDuration) {
            throw new IllegalArgumentException("Min Duration cannot be greater than Max Duration.");
        }

        int minInterval = parseRequiredInt(ruleMinIntervalField, "Min Interval");
        int maxInterval = parseRequiredInt(ruleMaxIntervalField, "Max Interval");
        if (minInterval > maxInterval) {
            throw new IllegalArgumentException("Min Interval cannot be greater than Max Interval.");
        }

        SensorRuleConfig rule = new SensorRuleConfig();
        rule.setType(sensorType.trim());
        rule.setMinvalue(minValue);
        rule.setMaxvalue(maxValue);
        rule.setMinpulses(minPulses);
        rule.setMaxpulses(maxPulses);
        rule.setMinintensity(minIntensity);
        rule.setMaxintensity(maxIntensity);
        rule.setMinduration(minDuration);
        rule.setMaxduration(maxDuration);
        rule.setMininterval(minInterval);
        rule.setMaxinterval(maxInterval);
        rule.setActive(true);
        return rule;
    }

    private int parseRequiredInt(TextField field, String fieldName) {
        if (field == null || field.getText() == null || field.getText().trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }

        try {
            return Integer.parseInt(field.getText().trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(fieldName + " must be a valid integer.");
        }
    }

    private void clearRuleBuilderForm() {
        if (ruleMinValueField != null) {
            ruleMinValueField.clear();
        }
        if (ruleMaxValueField != null) {
            ruleMaxValueField.clear();
        }
        if (ruleMinPulsesField != null) {
            ruleMinPulsesField.clear();
        }
        if (ruleMaxPulsesField != null) {
            ruleMaxPulsesField.clear();
        }
        if (ruleMinIntensityField != null) {
            ruleMinIntensityField.clear();
        }
        if (ruleMaxIntensityField != null) {
            ruleMaxIntensityField.clear();
        }
        if (ruleMinDurationField != null) {
            ruleMinDurationField.clear();
        }
        if (ruleMaxDurationField != null) {
            ruleMaxDurationField.clear();
        }
        if (ruleMinIntervalField != null) {
            ruleMinIntervalField.clear();
        }
        if (ruleMaxIntervalField != null) {
            ruleMaxIntervalField.clear();
        }
    }

    private void setupSidebar() {
        useCasesNavButton.setOnAction(ignored -> setSidebarMode(true));
        participantManagementNavButton.setOnAction(ignored -> setSidebarMode(false));
        setSidebarMode(true);

        useCaseListView.setItems(useCases);
        useCaseListView.getSelectionModel().selectedItemProperty().addListener(onNewValueChanged(newValue -> {
            if (newValue == null || newValue.isBlank()) {
                return;
            }
            state.setSelectedUseCase(newValue);
        }));

        participantSidebarList.getSelectionModel().selectedItemProperty().addListener(onNewValueChanged(newValue -> {
            if (newValue == null) {
                return;
            }
            User selected = parseSidebarUser(newValue);
            if (selected != null && selected != participantComboBox.getValue()) {
                participantComboBox.setValue(selected);
            }
        }));
    }

    private void setupTopbar() {
        participantComboBox.setItems(users);
        participantComboBox.setCellFactory(_ -> new ListCell<>() {
            @Override
            protected void updateItem(User item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : formatUser(item));
            }
        });
        participantComboBox.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(User item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "Select participant" : formatUser(item));
            }
        });

        participantComboBox.valueProperty().addListener(onNewValueChanged(newValue -> {
            if (newValue == null) {
                return;
            }
            state.setSelectedParticipant(newValue);
            contextParticipantLabel.setText(formatUser(newValue));
            String listValue = formatUser(newValue);
            if (!listValue.equals(participantSidebarList.getSelectionModel().getSelectedItem())) {
                participantSidebarList.getSelectionModel().select(listValue);
            }
            scheduleGraphUpdate();
        }));

        timeRangeComboBox.getItems().setAll("Last 24 Hours", "Last 7 Days", "Last 30 Days", "All Time", "Custom Date Range");
        timeRangeComboBox.setValue("Last 24 Hours");
        timeRangeComboBox.valueProperty().addListener(onNewValueChanged(newValue -> {
            state.setSelectedTimeRange(newValue);
            updateDatePickerVisibility();
            scheduleGraphUpdate();
        }));

        startDatePicker.setValue(LocalDate.now().minusDays(7));
        endDatePicker.setValue(LocalDate.now());
        state.setStartDate(startDatePicker.getValue());
        state.setEndDate(endDatePicker.getValue());

        startDatePicker.valueProperty().addListener(onNewValueChanged(newValue -> {
            state.setStartDate(newValue);
            if ("Custom Date Range".equals(state.getSelectedTimeRange())) {
                scheduleGraphUpdate();
            }
        }));

        endDatePicker.valueProperty().addListener(onNewValueChanged(newValue -> {
            state.setEndDate(newValue);
            if ("Custom Date Range".equals(state.getSelectedTimeRange())) {
                scheduleGraphUpdate();
            }
        }));

        updateDatePickerVisibility();
    }

    private void setupTabs() {
        workspaceTabPane.getSelectionModel().select(mappingsTab);
        workspaceTabPane.getSelectionModel().selectedItemProperty().addListener(onNewValueChanged(newValue -> {
            if (newValue == graphTab) {
                scheduleGraphUpdate();
            } else if (newValue == mappingsTab) {
                renderMappingsForUseCase(state.getSelectedUseCase());
                updateLoggingIntervalDisplay(state.getSelectedUseCase());
            } else if (newValue == agentChatTab) {
                refreshRuleSummary();
                renderMiniGraphFromData(latestGraphData);
            }
        }));
    }

    private void setupChat() {
        chatSendButton.setOnAction(ignored -> onSendMessage());
        chatInputField.setOnAction(ignored -> onSendMessage());

        chatHistoryBox.heightProperty().addListener(onNewValueChanged(newValue -> chatScrollPane.setVvalue(1.0)));

        if (agentTypingLabel != null) {
            agentTypingLabel.setVisible(false);
            agentTypingLabel.setManaged(false);
        }

        contextDrawer.setVisible(false);
        contextDrawer.setManaged(false);
        contextDrawerToggleButton.setText("Show Context");

        contextDrawerToggleButton.setOnAction(ignored -> {
            boolean nextVisible = !contextDrawer.isVisible();
            contextDrawer.setVisible(nextVisible);
            contextDrawer.setManaged(nextVisible);
            contextDrawerToggleButton.setText(nextVisible ? "Hide Context" : "Show Context");
        });
    }

    private void setupGraph() {
        refreshGraphButton.setOnAction(ignored -> scheduleGraphUpdate());
        exportGraphCsvButton.setOnAction(ignored -> exportGraphData("csv"));
        exportGraphPdfButton.setOnAction(ignored -> exportGraphData("pdf"));
        ensureGraphWebView();
        ensureMiniGraphWebView();
        setGraphLoadingVisible(false);
        setMiniGraphLoadingVisible(false);
        renderGraphPlaceholder("Select a use case and participant to render graph data.");
        renderMiniGraphPlaceholder("Graph preview appears here.");
    }

    private void exportGraphData(String format) {
        User user = state.getSelectedParticipant();
        String useCase = state.getSelectedUseCase();
        String range = state.getSelectedTimeRange();

        if (user == null || useCase == null || useCase.isBlank() || range == null || range.isBlank()) {
            showErrorAlert("Missing Selection", "Please select a participant, use case, and time range before exporting.");
            return;
        }

        if ("Custom Date Range".equals(range)) {
            LocalDate start = state.getStartDate();
            LocalDate end = state.getEndDate();
            if (start == null || end == null) {
                showErrorAlert("Missing Date Range", "Please select both start and end dates for custom range export.");
                return;
            }
            if (end.isBefore(start)) {
                showErrorAlert("Invalid Date Range", "End date cannot be before start date.");
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
                    showErrorAlert("Sensor Not Found", "Could not resolve a sensor id for use case: " + useCase);
                    return;
                }

                Map<String, String> params = buildSensorDataParams(user.getUserID(), sensorId, range);
                ApiService.getInstance().get(ApiService.EP_SENSOR_DATA, params)
                    .thenAccept(response -> {
                        try {
                            String fullPath;
                            if ("csv".equals(format)) {
                                fullPath = ExportService.generateCsv(selectedFile.getAbsolutePath(), user.getUserID(), useCase, range, response);
                            } else {
                                fullPath = ExportService.generatePdf(selectedFile.getAbsolutePath(), user.getUserID(), useCase, range, response);
                            }
                            showInfoAlert("Export Successful", "Saved to:\n" + fullPath);
                        } catch (Exception ex) {
                            showErrorAlert("Export Failed", ex.getMessage());
                        }
                    })
                    .exceptionally(ex -> {
                        showErrorAlert("Data Fetch Failed", "Failed to fetch sensor data: " + ex.getMessage());
                        return null;
                    });
            })
            .exceptionally(ex -> {
                showErrorAlert("Sensor Lookup Failed", "Failed to resolve sensor id: " + ex.getMessage());
                return null;
            });
    }

    private Map<String, String> buildSensorDataParams(int userId, int sensorId, String range) {
        Map<String, String> params = new HashMap<>();
        params.put("alert_type", String.valueOf(sensorId));
        params.put("userid", String.valueOf(userId));

        if ("Custom Date Range".equals(range)) {
            params.put("range", "custom");
            LocalDate start = state.getStartDate();
            LocalDate end = state.getEndDate();
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

    private void setupStateListeners() {
        graphUpdateDebounce.setOnFinished(ignored -> refreshGraphData());

        state.selectedUseCaseProperty().addListener(onChanged((oldValue, newValue) -> {
            resolveSelectedUseCaseId(newValue);
            applySelectedUseCaseUi(newValue);
            scheduleGraphUpdate();
        }));

        state.selectedParticipantProperty().addListener(onNewValueChanged(newValue -> refreshRuleSummary()));
    }

    private void loadParticipants() {
        users.clear();
        ApiService.getInstance().get(ApiService.EP_GET_USERS, null)
            .thenAccept(response -> {
                JsonNode usersArray = ApiService.getInstance().extractArray(response);
                if (usersArray == null) {
                    return;
                }

                List<User> loaded = new ArrayList<>();
                for (JsonNode node : usersArray) {
                    if (!node.hasNonNull("userid")) {
                        continue;
                    }
                    int id = node.path("userid").asInt();
                    String firstName = node.path("fname").asText("");
                    String lastName = node.path("lname").asText("");
                    loaded.add(new User(id, firstName, lastName));
                }

                Platform.runLater(() -> {
                    users.setAll(loaded);
                    participantCountLabel.setText(String.valueOf(users.size()));

                    List<String> sidebarUsers = users.stream().map(this::formatUser).toList();
                    participantSidebarList.getItems().setAll(sidebarUsers);

                    if (!users.isEmpty() && participantComboBox.getValue() == null) {
                        participantComboBox.setValue(users.get(0));
                    }
                });
            })
            .exceptionally(ex -> {
                ex.printStackTrace();
                return null;
            });
    }

    private void loadUseCases() {
        // Loads use cases with fallbacks; populates normalized UI collections
        ApiService.getInstance().get(ApiService.EP_GET_USECASES, null)
            .thenAccept(response -> {
                JsonNode useCaseArray = ApiService.getInstance().extractArray(response);
                LinkedHashMap<String, String> normalizedToDisplay = new LinkedHashMap<>();
                Map<String, String> loadedDescriptions = new HashMap<>();
                Map<String, Integer> loadedUseCaseIds = new HashMap<>();
                Map<String, String> loadedLoggingIntervals = new HashMap<>();

                if (useCaseArray != null && useCaseArray.isArray()) {
                    for (JsonNode useCaseNode : useCaseArray) {
                        String rawName = useCaseNode.path("name").asText("").trim();
                        String description = useCaseNode.path("description").asText("").trim();
                        int useCaseId = useCaseNode.path("usecase_id").asInt(0);
                        if (useCaseId <= 0) {
                            useCaseId = useCaseNode.path("usecaseid").asInt(0);
                        }
                        if (useCaseId <= 0) {
                            useCaseId = useCaseNode.path("id").asInt(0);
                        }
                        if (!rawName.isEmpty()) {
                            String normalizedName = normalizeUseCaseName(rawName);
                            normalizedToDisplay.putIfAbsent(normalizedName, rawName);
                            loadedDescriptions.put(normalizedName, description);
                            String loggingInterval = extractLoggingInterval(useCaseNode);
                            if (!loggingInterval.isEmpty()) {
                                loadedLoggingIntervals.put(normalizedName, loggingInterval);
                            }
                            if (useCaseId > 0) {
                                loadedUseCaseIds.putIfAbsent(normalizedName, useCaseId);
                            }
                        }
                    }
                }

                if (normalizedToDisplay.isEmpty()) {
                    for (String defaultUseCase : DEFAULT_USE_CASES) {
                        normalizedToDisplay.put(normalizeUseCaseName(defaultUseCase), defaultUseCase);
                    }
                }

                Platform.runLater(() -> {
                    useCases.setAll(normalizedToDisplay.values());
                    useCaseDescriptions.clear();
                    useCaseDescriptions.putAll(loadedDescriptions);
                    useCaseDisplayNames.clear();
                    useCaseDisplayNames.putAll(normalizedToDisplay);
                    useCaseIdsByNormalizedName.clear();
                    useCaseIdsByNormalizedName.putAll(loadedUseCaseIds);
                    useCaseLoggingInterval.clear();
                    useCaseLoggingInterval.putAll(loadedLoggingIntervals);
                    String selected = state.getSelectedUseCase();
                    if (selected == null || selected.isBlank() || !useCases.contains(selected)) {
                        selected = useCases.isEmpty() ? null : useCases.get(0);
                    }
                    state.setSelectedUseCase(selected);
                    applySelectedUseCaseUi(selected);
                });
            })
            .exceptionally(ex -> {
                ex.printStackTrace();
                return null;
            });
    }

    private void resolveSelectedUseCaseId(String selectedUseCase) {
        if (selectedUseCase == null || selectedUseCase.isBlank()) {
            state.setSelectedUseCaseId(null);
            return;
        }

        String normalized = normalizeUseCaseName(selectedUseCase);
        Integer knownId = useCaseIdsByNormalizedName.get(normalized);
        state.setSelectedUseCaseId((knownId != null && knownId > 0) ? knownId : null);
    }

    /**
     * Updates UI labels, list, and refreshes displays for selected use case
     */
    private void applySelectedUseCaseUi(String selectedUseCase) {
        if (selectedUseCaseLabel != null) {
            selectedUseCaseLabel.setText((selectedUseCase == null || selectedUseCase.isBlank()) ? "-" : selectedUseCase);
            String selectedKey = normalizeUseCaseName(selectedUseCase);
            String desc = useCaseDescriptions.getOrDefault(selectedKey, "").trim();
            selectedUseCaseLabel.setTooltip(desc.isEmpty() ? null : new Tooltip(desc));
        }

        if (contextUseCaseLabel != null) {
            contextUseCaseLabel.setText((selectedUseCase == null || selectedUseCase.isBlank()) ? "-" : selectedUseCase);
        }

        if (useCaseListView != null && selectedUseCase != null && !selectedUseCase.isBlank()) {
            String current = useCaseListView.getSelectionModel().getSelectedItem();
            if (!selectedUseCase.equals(current)) {
                useCaseListView.getSelectionModel().select(selectedUseCase);
            }
        }

        updateRuleBuilderUseCaseDisplay(selectedUseCase);
        updateLoggingIntervalDisplay(selectedUseCase);
        refreshRuleSummary();
        renderMappingsForUseCase(selectedUseCase);
        clearChatMessages();
        addChatMessage("Selected use case: " + selectedUseCase, false);
    }

    private void updateLoggingIntervalDisplay(String selectedUseCase) {
        if (loggingIntervalValueLabel == null) {
            return;
        }

        String displayText = "Not set";
        if (selectedUseCase != null && !selectedUseCase.isBlank()) {
            String normalizedKey = normalizeUseCaseName(selectedUseCase);
            String configuredInterval = useCaseLoggingInterval.getOrDefault(normalizedKey, "").trim();
            if (!configuredInterval.isEmpty()) {
                displayText = configuredInterval;
            }
        }

        loggingIntervalValueLabel.setText(displayText);
        loggingIntervalValueLabel.setTooltip("Not set".equals(displayText) ? null : new Tooltip(displayText));

        if (editLoggingIntervalButton != null) {
            Integer useCaseId = state.getSelectedUseCaseId();
            editLoggingIntervalButton.setDisable(useCaseId == null || useCaseId <= 0);
        }
    }

    private boolean isValidLoggingInterval(String interval) {
        return interval != null && LOG_INTERVAL_PATTERN.matcher(interval.trim()).matches();
    }

    private String formatLoggingInterval(int amount, String baseUnit) {
        int safeAmount = Math.max(1, amount);
        String safeUnit = (baseUnit == null || baseUnit.isBlank()) ? "minute" : baseUnit.trim().toLowerCase(Locale.ROOT);
        String unit = safeAmount == 1 ? safeUnit : safeUnit + "s";
        return safeAmount + " " + unit;
    }

    private LoggingIntervalDraft parseLoggingIntervalDraft(String intervalText) {
        LoggingIntervalDraft fallback = new LoggingIntervalDraft(1, "minute");
        if (intervalText == null || intervalText.isBlank()) {
            return fallback;
        }

        Matcher matcher = LOG_INTERVAL_PATTERN.matcher(intervalText.trim());
        if (!matcher.matches()) {
            return fallback;
        }

        int amount = 1;
        try {
            amount = Integer.parseInt(matcher.group(0).split("\\s+")[0].split("\\.")[0]);
        } catch (Exception ignored) {
            amount = 1;
        }

        String unit = matcher.group(2).toLowerCase(Locale.ROOT);
        if (unit.endsWith("s")) {
            unit = unit.substring(0, unit.length() - 1);
        }

        return new LoggingIntervalDraft(Math.max(1, amount), unit);
    }

    @FXML
    private void onEditLoggingInterval() {
        String selectedUseCase = state.getSelectedUseCase();
        if (selectedUseCase == null || selectedUseCase.isBlank()) {
            showErrorAlert("Missing Use Case", "Please select a use case before editing logging interval.");
            return;
        }

        String selectedKey = normalizeUseCaseName(selectedUseCase);
        Integer useCaseId = state.getSelectedUseCaseId();
        if (useCaseId == null || useCaseId <= 0) {
            useCaseId = useCaseIdsByNormalizedName.get(selectedKey);
            state.setSelectedUseCaseId((useCaseId != null && useCaseId > 0) ? useCaseId : null);
        }

        if (useCaseId == null || useCaseId <= 0) {
            showErrorAlert("Missing Use Case Id", "Could not resolve use case id. Refresh use cases and try again.");
            return;
        }

        LoggingIntervalDraft initialDraft = parseLoggingIntervalDraft(useCaseLoggingInterval.get(selectedKey));

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Edit Logging Interval");
        dialog.setHeaderText("Set logging interval for " + selectedUseCase + ".");

        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        Spinner<Integer> amountSpinner = new Spinner<>();
        amountSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 100000, initialDraft.amount));
        amountSpinner.setEditable(true);
        amountSpinner.setPrefWidth(120);

        ComboBox<String> unitComboBox = new ComboBox<>();
        unitComboBox.getItems().setAll("second", "minute", "hour", "day");
        unitComboBox.setValue(initialDraft.baseUnit);
        unitComboBox.setPrefWidth(140);

        HBox content = new HBox(10, amountSpinner, unitComboBox);
        content.setAlignment(Pos.CENTER_LEFT);
        dialog.getDialogPane().setContent(content);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isEmpty() || result.get() != saveButtonType) {
            return;
        }

        int amount = amountSpinner.getValue();
        String baseUnit = unitComboBox.getValue();
        String interval = formatLoggingInterval(amount, baseUnit);
        if (!isValidLoggingInterval(interval)) {
            showErrorAlert("Invalid Interval", "Interval must match: number + second(s)/minute(s)/hour(s)/day(s).");
            return;
        }

        final int resolvedUseCaseId = useCaseId;
        ApiService.getInstance().setLoggingInterval(resolvedUseCaseId, interval)
            .thenAccept(success -> Platform.runLater(() -> {
                if (!success) {
                    showErrorAlert("Update Failed", "Could not update logging interval. Please try again.");
                    return;
                }

                useCaseLoggingInterval.put(selectedKey, interval);
                updateLoggingIntervalDisplay(selectedUseCase);
                showInfoAlert("Interval Updated", "Logging interval set to " + interval + " for " + selectedUseCase + ".");
                loadUseCases();
            }))
            .exceptionally(ex -> {
                showErrorAlert("Update Failed", "Failed to update logging interval: " + ex.getMessage());
                return null;
            });
    }

    private void loadMappings() {
        ApiService.getInstance().get(ApiService.EP_CURRENT_CONFIGURATION, null)
            .thenAccept(response -> {
                Platform.runLater(() -> {
                    allRules.clear();
                    parseMappingsFromConfiguration(response);
                    renderMappingsForUseCase(state.getSelectedUseCase());
                    refreshRuleSummary();
                });
            })
            .exceptionally(ex -> {
                ex.printStackTrace();
                return null;
            });
    }

    private void parseMappingsFromConfiguration(JsonNode rootNode) {
        if (rootNode == null || !rootNode.isObject()) {
            return;
        }

        rootNode.fields().forEachRemaining(entry -> parseRulesArray(entry.getKey(), entry.getValue()));
    }

    private void parseRulesArray(String configKey, JsonNode arrayNode) {
        if (arrayNode == null || !arrayNode.isArray()) {
            return;
        }

        for (JsonNode node : arrayNode) {
            String rawType = node.path("type").asText("").trim();
            String useCaseKey = rawType.isEmpty()
                ? inferUseCaseFromConfigKey(configKey)
                : normalizeUseCaseName(rawType);
            String useCaseLabel = rawType.isEmpty() ? toUseCaseLabel(useCaseKey) : rawType;

            RuleCardData rule = new RuleCardData();
            rule.mappingId = readMappingId(node);
            rule.configKey = configKey;
            rule.useCaseKey = useCaseKey;
            rule.useCaseLabel = useCaseLabel;
            rule.rangeLabel = readRangeLabel(node, useCaseLabel);
            rule.pulseLabel = readRangeValue(node, "minpulses", "maxpulses", "N/A");
            rule.intensityLabel = readRangeValue(node, "minintensity", "maxintensity", "N/A");
            rule.durationLabel = readRangeValue(node, "minduration", "maxduration", "N/A ms");
            rule.intervalLabel = readRangeValue(node, "mininterval", "maxinterval", "N/A ms");
            allRules.add(rule);
        }
    }

    /**
     * Renders filtered use case mappings or empty state
     */
    private void renderMappingsForUseCase(String useCase) {
        mappingsFlowPane.getChildren().clear();

        if (useCase == null || useCase.isBlank()) {
            mappingsEmptyLabel.setVisible(true);
            mappingsEmptyLabel.setManaged(true);
            mappingsEmptyLabel.setText("Select a use case to view active mappings.");
            return;
        }

        String selectedUseCaseKey = normalizeUseCaseName(useCase);
        List<RuleCardData> filtered = allRules.stream()
            .filter(rule -> selectedUseCaseKey.equals(rule.useCaseKey))
            .toList();

        if (filtered.isEmpty()) {
            mappingsEmptyLabel.setVisible(true);
            mappingsEmptyLabel.setManaged(true);
            mappingsEmptyLabel.setText("No active mappings found for " + useCase + ".");
            return;
        }

        mappingsEmptyLabel.setVisible(false);
        mappingsEmptyLabel.setManaged(false);

        // Builds and adds rule detail cards to flow pane
        for (RuleCardData rule : filtered) {
            VBox card = new VBox(8);
            card.getStyleClass().add("mapping-card");

            Label title = new Label(rule.rangeLabel);
            title.getStyleClass().add("mapping-card-title");

            HBox titleRow = new HBox(8);
            titleRow.setAlignment(Pos.CENTER_LEFT);

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            Button deleteButton = createDeleteMappingButton(rule);
            titleRow.getChildren().addAll(title, spacer, deleteButton);

            Label pulses = new Label("Pulse count: " + rule.pulseLabel);
            Label intensity = new Label("Intensity: " + rule.intensityLabel);
            Label duration = new Label("Duration: " + rule.durationLabel);
            Label interval = new Label("Interval: " + rule.intervalLabel);

            card.getChildren().addAll(titleRow, pulses, intensity, duration, interval);
            mappingsFlowPane.getChildren().add(card);
        }
    }

    private Button createDeleteMappingButton(RuleCardData rule) {
        Button button = new Button();
        button.getStyleClass().add("mapping-delete-button");
        button.setTooltip(new Tooltip("Delete mapping"));

        Image icon = null;
        try {
            icon = new Image(Objects.requireNonNull(getClass().getResourceAsStream(DELETE_ICON_PATH)));
        } catch (Exception ignored) {
            // Fallback text keeps control usable if image is missing from classpath.
        }

        if (icon != null && !icon.isError()) {
            ImageView imageView = new ImageView(icon);
            imageView.setPreserveRatio(true);
            imageView.setFitWidth(14);
            imageView.setFitHeight(14);
            button.setGraphic(imageView);
        } else {
            button.setText("X");
        }

        button.setOnAction(ignored -> onDeleteMappingRequested(rule));
        return button;
    }

    private void onDeleteMappingRequested(RuleCardData rule) {
        if (rule == null || rule.mappingId <= 0) {
            showErrorAlert("Delete Failed", "Could not resolve mapping id for the selected card.");
            return;
        }

        String label = (rule == null || rule.rangeLabel == null || rule.rangeLabel.isBlank())
            ? "selected mapping"
            : rule.rangeLabel;

        ApiService.getInstance().deleteMapping(rule.mappingId)
            .thenAccept(success -> Platform.runLater(() -> {
                if (!success) {
                    showErrorAlert("Delete Failed", "Could not delete mapping for " + label + ".");
                    return;
                }
                showInfoAlert("Mapping Deleted", "Deleted mapping for " + label + ".");
                loadMappings();
            }))
            .exceptionally(ex -> {
                showErrorAlert("Delete Failed", "Failed to delete mapping: " + ex.getMessage());
                return null;
            });
    }

    private void onSendMessage() {
        String message = chatInputField.getText() == null ? "" : chatInputField.getText().trim();
        if (message.isEmpty()) {
            return;
        }

        addChatMessage(message, true);
        chatInputField.clear();

        // Keys must match webhook body fields used by n8n.
        Map<String, Object> payload = new HashMap<>();
        Integer useCaseId = state.getSelectedUseCaseId();

        payload.put("message", message);
        payload.put("session_id", chatSessionId);

        if (useCaseId != null && useCaseId > 0) {
            payload.put("usecase_id", useCaseId);
        } else {
            payload.put("usecase_id", null);
        }

        setAgentTyping(true);

        ApiService.getInstance().postWithResponse(ApiService.EP_CHAT_CONFIG, payload)
            .thenAccept(response -> Platform.runLater(() -> {
                setAgentTyping(false);
                if (response != null && response.has("reply")) {
                    addChatMessage(response.get("reply").asText(), false);
                } else {
                    addChatMessage("AI response did not include reply field.", false);
                }

                // for future usage?
//                if(response.has("action") && response.get("action").asText().equals("newMapping")) {
//                    refreshMappings();
//                }
            }))
            .exceptionally(ex -> {
                Platform.runLater(() -> {
                    setAgentTyping(false);
                    addChatMessage("Error: " + ex.getMessage(), false);
                });
                return null;
            });
    }

    private void setAgentTyping(boolean typing) {
        if (agentTypingLabel == null) {
            return;
        }
        agentTypingLabel.setText("Agent is typing...");
        agentTypingLabel.setVisible(typing);
        agentTypingLabel.setManaged(typing);
    }

    private void addChatMessage(String text, boolean userMessage) {
        Label bubble = new Label(text);
        bubble.setWrapText(true);
        bubble.setMaxWidth(580);
        bubble.getStyleClass().add(userMessage ? "chat-bubble-user" : "chat-bubble-ai");

        HBox row = new HBox(bubble);
        row.getStyleClass().add("chat-message-row");
        row.setFillHeight(true);
        HBox.setHgrow(bubble, Priority.NEVER);
        row.setAlignment(userMessage ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        chatHistoryBox.getChildren().add(row);
    }

    private void clearChatMessages() {
        chatHistoryBox.getChildren().clear();
    }

    private void refreshRuleSummary() {
        activeRulesSummaryBox.getChildren().clear();

        String selectedUseCase = state.getSelectedUseCase();
        if (selectedUseCase == null) {
            activeRulesSummaryBox.getChildren().add(new Label("No use case selected."));
            return;
        }

        String selectedUseCaseKey = normalizeUseCaseName(selectedUseCase);
        List<RuleCardData> filtered = allRules.stream()
            .filter(rule -> selectedUseCaseKey.equals(rule.useCaseKey))
            .limit(5)
            .toList();

        if (filtered.isEmpty()) {
            activeRulesSummaryBox.getChildren().add(new Label("No active rules for this use case."));
            return;
        }

        for (RuleCardData rule : filtered) {
            Label line = new Label(rule.rangeLabel + " | Pulses " + rule.pulseLabel + " | Intensity " + rule.intensityLabel);
            line.getStyleClass().add("drawer-rule-line");
            activeRulesSummaryBox.getChildren().add(line);
        }
    }

    @FXML
    private void scheduleGraphUpdate() {
        graphUpdateDebounce.playFromStart();
    }

    private void refreshGraphData() {
        User user = state.getSelectedParticipant();
        String useCase = state.getSelectedUseCase();
        String range = state.getSelectedTimeRange();

        setGraphLoadingVisible(true);
        setMiniGraphLoadingVisible(true);

        if (user == null || useCase == null || useCase.isBlank() || range == null) {
            renderGraphPlaceholder("Select a use case and participant to render graph data.");
            renderMiniGraphPlaceholder("Graph preview appears here.");
            setGraphLoadingVisible(false);
            setMiniGraphLoadingVisible(false);
            return;
        }

        ApiService.getInstance().getSensorIdByName(useCase)
            .thenAccept(sensorId -> {
                if (sensorId == -1) {
                    Platform.runLater(() -> {
                        renderGraphPlaceholder("Unable to resolve sensor type for use case: " + useCase);
                        renderMiniGraphPlaceholder("No preview data available.");
                        setGraphLoadingVisible(false);
                        setMiniGraphLoadingVisible(false);
                    });
                    return;
                }

                Map<String, String> params = new HashMap<>();
                params.put("alert_type", String.valueOf(sensorId));
                params.put("userid", String.valueOf(user.getUserID()));

                if ("Custom Date Range".equals(range)) {
                    params.put("range", "custom");
                    LocalDate start = state.getStartDate();
                    LocalDate end = state.getEndDate();
                    if (start != null) {
                        params.put("start_date", start.toString());
                    }
                    if (end != null) {
                        params.put("end_date", end.toString());
                    }
                } else {
                    params.put("range", range);
                }

                ApiService.getInstance().get(ApiService.EP_SENSOR_DATA, params)
                    .thenAccept(response -> {
                        JsonNode dataArray = ApiService.getInstance().extractArray(response);
                        latestGraphData = dataArray;
                        Platform.runLater(() -> {
                            if (dataArray == null || !dataArray.isArray() || dataArray.isEmpty()) {
                                renderGraphPlaceholder("No sensor data available for current selection.");
                                renderMiniGraphPlaceholder("No preview data available.");
                                setGraphLoadingVisible(false);
                                setMiniGraphLoadingVisible(false);
                                return;
                            }
                            renderGraph(dataArray, user.getUserID(), useCase);
                            renderMiniGraphFromData(dataArray);
                        });
                    })
                    .exceptionally(ex -> {
                        ex.printStackTrace();
                        Platform.runLater(() -> {
                            renderGraphPlaceholder("Failed to load graph data.");
                            renderMiniGraphPlaceholder("No preview data available.");
                            setGraphLoadingVisible(false);
                            setMiniGraphLoadingVisible(false);
                        });
                        return null;
                    });
            })
            .exceptionally(ex -> {
                ex.printStackTrace();
                Platform.runLater(() -> {
                    renderGraphPlaceholder("Failed to resolve sensor id.");
                    renderMiniGraphPlaceholder("No preview data available.");
                    setGraphLoadingVisible(false);
                    setMiniGraphLoadingVisible(false);
                });
                return null;
            });
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

    /**
     * Renders mini-graph from the external HTML template using validated data
     */
    private void renderMiniGraphFromData(JsonNode dataArray) {
        ensureMiniGraphWebView();

        if (dataArray == null || !dataArray.isArray() || dataArray.isEmpty()) {
            renderMiniGraphPlaceholder("No preview data available.");
            return;
        }

        String template = loadHtmlTemplate(MINI_GRAPH_TEMPLATE_PATH);
        if (template == null) {
            renderMiniGraphPlaceholder("Mini graph template file is missing.");
            return;
        }

        String escapedJson = dataArray.toString().replace("\\", "\\\\").replace("\"", "\\\"");
        String html = template.replace("const data = DATA_PLACEHOLDER;", "const data = JSON.parse(\"" + escapedJson + "\");");

        miniGraphWebView.getEngine().loadContent(html);
    }

    private void renderGraphPlaceholder(String message) {
        ensureGraphWebView();
        graphWebView.getEngine().loadContent(buildPlaceholderHtml(message));
    }

    private void renderMiniGraphPlaceholder(String message) {
        ensureMiniGraphWebView();
        miniGraphWebView.getEngine().loadContent(buildPlaceholderHtml(message));
    }

    private String buildPlaceholderHtml(String message) {
        return "<html><body style='font-family:Segoe UI, sans-serif;display:flex;align-items:center;justify-content:center;height:100%;margin:0;color:#4b5563;background:#ffffff;'>"
            + "<div style='text-align:center;padding:20px;'>" + escapeHtml(message) + "</div></body></html>";
    }

    private void ensureGraphWebView() {
        if (graphWebView != null) {
            ensureGraphLoadingOverlay();
            return;
        }
        graphWebView = new WebView();
        graphWebView.getEngine().getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == javafx.concurrent.Worker.State.SUCCEEDED || newState == javafx.concurrent.Worker.State.FAILED || newState == javafx.concurrent.Worker.State.CANCELLED) {
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

    private void ensureMiniGraphWebView() {
        if (miniGraphWebView != null) {
            ensureMiniGraphLoadingOverlay();
            return;
        }
        miniGraphWebView = new WebView();
        miniGraphWebView.getEngine().getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == javafx.concurrent.Worker.State.SUCCEEDED || newState == javafx.concurrent.Worker.State.FAILED || newState == javafx.concurrent.Worker.State.CANCELLED) {
                setMiniGraphLoadingVisible(false);
            }
        });
        miniGraphWebView.setPrefHeight(180);
        AnchorPane.setTopAnchor(miniGraphWebView, 0.0);
        AnchorPane.setRightAnchor(miniGraphWebView, 0.0);
        AnchorPane.setBottomAnchor(miniGraphWebView, 0.0);
        AnchorPane.setLeftAnchor(miniGraphWebView, 0.0);
        miniGraphAnchor.getChildren().setAll(miniGraphWebView);
        ensureMiniGraphLoadingOverlay();
    }

    private void ensureGraphLoadingOverlay() {
        if (graphLoadingOverlay == null) {
            graphLoadingOverlay = createLoadingOverlay("Loading graph data...");
            AnchorPane.setTopAnchor(graphLoadingOverlay, 0.0);
            AnchorPane.setRightAnchor(graphLoadingOverlay, 0.0);
            AnchorPane.setBottomAnchor(graphLoadingOverlay, 0.0);
            AnchorPane.setLeftAnchor(graphLoadingOverlay, 0.0);
        }

        if (!graphAnchor.getChildren().contains(graphLoadingOverlay)) {
            graphAnchor.getChildren().add(graphLoadingOverlay);
        }
    }

    private void ensureMiniGraphLoadingOverlay() {
        if (miniGraphLoadingOverlay == null) {
            miniGraphLoadingOverlay = createLoadingOverlay("Loading preview...");
            AnchorPane.setTopAnchor(miniGraphLoadingOverlay, 0.0);
            AnchorPane.setRightAnchor(miniGraphLoadingOverlay, 0.0);
            AnchorPane.setBottomAnchor(miniGraphLoadingOverlay, 0.0);
            AnchorPane.setLeftAnchor(miniGraphLoadingOverlay, 0.0);
        }

        if (!miniGraphAnchor.getChildren().contains(miniGraphLoadingOverlay)) {
            miniGraphAnchor.getChildren().add(miniGraphLoadingOverlay);
        }
    }

    private StackPane createLoadingOverlay(String message) {
        ProgressIndicator indicator = new ProgressIndicator();
        indicator.setPrefSize(36, 36);

        Label label = new Label(message);
        label.setStyle("-fx-text-fill: #374151; -fx-font-size: 12px;");

        VBox content = new VBox(8, indicator, label);
        content.setAlignment(Pos.CENTER);

        StackPane overlay = new StackPane(content);
        overlay.setStyle("-fx-background-color: rgba(255,255,255,0.75);");
        overlay.setVisible(false);
        overlay.setManaged(false);
        return overlay;
    }

    private void setGraphLoadingVisible(boolean visible) {
        ensureGraphWebView();
        graphLoadingOverlay.setVisible(visible);
        graphLoadingOverlay.setManaged(visible);
        if (visible) {
            graphLoadingOverlay.toFront();
        }
    }

    private void setMiniGraphLoadingVisible(boolean visible) {
        ensureMiniGraphWebView();
        miniGraphLoadingOverlay.setVisible(visible);
        miniGraphLoadingOverlay.setManaged(visible);
        if (visible) {
            miniGraphLoadingOverlay.toFront();
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

    private String formatUser(User user) {
        return user.getUserID() + " - " + user.getFName() + " " + user.getLName();
    }

    private User parseSidebarUser(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            int userId = Integer.parseInt(text.split("-")[0].trim());
            return users.stream().filter(user -> user.getUserID() == userId).findFirst().orElse(null);
        } catch (Exception ex) {
            return null;
        }
    }

    private void setSidebarMode(boolean useCasesMode) {
        useCasesNavButton.setSelected(useCasesMode);
        participantManagementNavButton.setSelected(!useCasesMode);

        useCasesSidebarPane.setVisible(useCasesMode);
        useCasesSidebarPane.setManaged(useCasesMode);

        participantManagementSidebarPane.setVisible(!useCasesMode);
        participantManagementSidebarPane.setManaged(!useCasesMode);
    }

    private void updateDatePickerVisibility() {
        boolean custom = "Custom Date Range".equals(timeRangeComboBox.getValue());
        startDatePicker.setVisible(custom);
        startDatePicker.setManaged(custom);
        endDatePicker.setVisible(custom);
        endDatePicker.setManaged(custom);
    }

    private String normalizeUseCaseName(String rawUseCase) {
        if (rawUseCase == null) {
            return "";
        }

        String normalized = rawUseCase.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
        if (normalized.contains("heartrate")) {
            return "HeartRate";
        }
        if (normalized.contains("sunazimuth") || (normalized.contains("sun") && normalized.contains("azimuth"))) {
            return "SunAzimuth";
        }
        if (normalized.contains("moonazimuth") || (normalized.contains("moon") && normalized.contains("azimuth"))) {
            return "MoonAzimuth";
        }
        if (normalized.contains("pollution")) {
            return "Pollution";
        }

        return normalized.isBlank() ? "Unknown" : normalized;
    }

    private String inferUseCaseFromConfigKey(String configKey) {
        String lowered = configKey == null ? "" : configKey.toLowerCase(Locale.ROOT);
        if (lowered.contains("heart")) {
            return "HeartRate";
        }
        if (lowered.contains("sun")) {
            return "SunAzimuth";
        }
        if (lowered.contains("moon")) {
            return "MoonAzimuth";
        }
        if (lowered.contains("pollution")) {
            return "Pollution";
        }

        return normalizeUseCaseName(configKey);
    }

    private String toUseCaseLabel(String useCaseKey) {
        if (useCaseKey == null || useCaseKey.isBlank()) {
            return "Unknown";
        }

        String explicitLabel = useCaseDisplayNames.get(useCaseKey);
        if (explicitLabel != null && !explicitLabel.isBlank()) {
            return explicitLabel;
        }

        return switch (useCaseKey) {
            case "HeartRate" -> "Heart Rate";
            case "SunAzimuth" -> "Sun Azimuth";
            case "MoonAzimuth" -> "Moon Azimuth";
            case "Pollution" -> "Pollution";
            default -> useCaseKey;
        };
    }

    private String readRangeLabel(JsonNode node, String useCaseLabel) {
        if (node.has("minvalue") && node.has("maxvalue")) {
            return useCaseLabel + ": " + node.path("minvalue").asInt() + " - " + node.path("maxvalue").asInt();
        }
        if (node.has("min") && node.has("max")) {
            return useCaseLabel + ": " + node.path("min").asInt() + " - " + node.path("max").asInt();
        }
        return useCaseLabel + ": rule";
    }

    private String readRangeValue(JsonNode node, String minField, String maxField, String fallback) {
        if (node.has(minField) && node.has(maxField)) {
            return node.path(minField).asInt() + " - " + node.path(maxField).asInt();
        }
        return fallback;
    }

    private String extractLoggingInterval(JsonNode useCaseNode) {
        if (useCaseNode == null || !useCaseNode.isObject()) {
            return "";
        }

        JsonNode intervalNode = useCaseNode.path("log_interval");
        if (intervalNode.isMissingNode() || intervalNode.isNull()) {
            intervalNode = useCaseNode.path("loginterval");
        }

        if (intervalNode.isMissingNode() || intervalNode.isNull()) {
            return "";
        }

        if (intervalNode.isTextual()) {
            return intervalNode.asText("").trim();
        }

        if (!intervalNode.isObject()) {
            return "";
        }

        String[] unitOrder = new String[] {"seconds", "minutes", "hours", "days"};
        for (String unit : unitOrder) {
            JsonNode valueNode = intervalNode.path(unit);
            if (valueNode.isNumber()) {
                double value = valueNode.asDouble();
                String normalizedUnit = value == 1.0 ? unit.substring(0, unit.length() - 1) : unit;
                String amount = Math.floor(value) == value
                    ? String.valueOf((long) value)
                    : String.valueOf(value);
                return amount + " " + normalizedUnit;
            }
        }

        return "";
    }

    private int readMappingId(JsonNode node) {
        if (node == null || !node.isObject()) {
            return 0;
        }
        if (node.has("mappingId")) {
            return node.path("mappingId").asInt(0);
        }
        if (node.has("mapping_id")) {
            return node.path("mapping_id").asInt(0);
        }
        if (node.has("id")) {
            return node.path("id").asInt(0);
        }
        return 0;
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

    private void showErrorAlert(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(title);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    private void showInfoAlert(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(title);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    private static <T> ChangeListener<T> onChanged(BiConsumer<T, T> handler) {
        return (observable, oldValue, newValue) -> {
            if (observable == null || Objects.equals(oldValue, newValue)) {
                return;
            }
            handler.accept(oldValue, newValue);
        };
    }

    private static <T> ChangeListener<T> onNewValueChanged(Consumer<T> handler) {
        return onChanged((oldValue, newValue) -> handler.accept(newValue));
    }

    private static class RuleCardData {
        int mappingId;
        String configKey;
        String useCaseKey;
        String useCaseLabel;
        String rangeLabel;
        String pulseLabel;
        String intensityLabel;
        String durationLabel;
        String intervalLabel;
    }

    private static class LoggingIntervalDraft {
        final int amount;
        final String baseUnit;

        private LoggingIntervalDraft(int amount, String baseUnit) {
            this.amount = amount;
            this.baseUnit = baseUnit;
        }
    }
}

