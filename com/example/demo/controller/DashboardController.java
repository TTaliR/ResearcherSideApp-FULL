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
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Alert;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
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

public class DashboardController {
    private static final List<String> DEFAULT_USE_CASES = List.of("HeartRate", "MoonAzimuth", "SunAzimuth", "Pollution");
    private static final String DELETE_ICON_PATH = "/com/example/demo/images/delete.png";

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
    private Button refreshRuleSummaryButton;
    @FXML
    private Label agentTypingLabel;

    private final DashboardState state = DashboardState.getInstance();
    private final ObservableList<User> users = FXCollections.observableArrayList();
    private final ObservableList<String> useCases = FXCollections.observableArrayList();
    private final Map<String, String> useCaseDescriptions = new HashMap<>();
    private final Map<String, String> useCaseDisplayNames = new HashMap<>();
    private final Map<String, Integer> useCaseIdsByNormalizedName = new HashMap<>();
    private final List<RuleCardData> allRules = new ArrayList<>();
    private final String chatSessionId = UUID.randomUUID().toString();

    private WebView graphWebView;
    private WebView miniGraphWebView;
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
        workspaceTabPane.getSelectionModel().select(agentChatTab);
        workspaceTabPane.getSelectionModel().selectedItemProperty().addListener(onNewValueChanged(newValue -> {
            if (newValue == graphTab) {
                scheduleGraphUpdate();
            } else if (newValue == mappingsTab) {
                renderMappingsForUseCase(state.getSelectedUseCase());
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
            state.setSelectedUseCaseId(0);
            return;
        }

        String normalized = normalizeUseCaseName(selectedUseCase);
        Integer knownId = useCaseIdsByNormalizedName.get(normalized);
        state.setSelectedUseCaseId((knownId != null && knownId > 0) ? knownId : 0);
    }

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
        refreshRuleSummary();
        renderMappingsForUseCase(selectedUseCase);
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
        button.setTooltip(new Tooltip("Delete mapping (coming soon)"));

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
        // TODO: Call ApiService.EP_DELETE_MAPPING when backend contract is finalized.
        String label = (rule == null || rule.rangeLabel == null || rule.rangeLabel.isBlank())
            ? "selected mapping"
            : rule.rangeLabel;
        showInfoAlert("Not Implemented", "Delete mapping for " + label + " will be connected in a future update.");
        int toDelete = allRules.indexOf(rule);

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
        int useCaseId = state.getSelectedUseCaseId();

        payload.put("message", message);     // Maps to {{ $json.body.message }} in n8n
        payload.put("session_id", chatSessionId); // Maps to {{ $json.body.session_id }} in n8n
        payload.put("usecase_id", useCaseId); // Maps to {{ $json.body.usecase_id }} in n8n

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

        if (user == null || useCase == null || useCase.isBlank() || range == null) {
            renderGraphPlaceholder("Select a use case and participant to render graph data.");
            return;
        }

        ApiService.getInstance().getSensorIdByName(useCase)
            .thenAccept(sensorId -> {
                if (sensorId == -1) {
                    Platform.runLater(() -> {
                        renderGraphPlaceholder("Unable to resolve sensor type for use case: " + useCase);
                        renderMiniGraphPlaceholder("No preview data available.");
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
                        });
                        return null;
                    });
            })
            .exceptionally(ex -> {
                ex.printStackTrace();
                Platform.runLater(() -> {
                    renderGraphPlaceholder("Failed to resolve sensor id.");
                    renderMiniGraphPlaceholder("No preview data available.");
                });
                return null;
            });
    }

    private void renderGraph(JsonNode dataArray, int userId, String useCase) {
        ensureGraphWebView();

        String template = loadHtmlTemplate();
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

    private void renderMiniGraphFromData(JsonNode dataArray) {
        ensureMiniGraphWebView();

        if (dataArray == null || !dataArray.isArray() || dataArray.isEmpty()) {
            renderMiniGraphPlaceholder("No preview data available.");
            return;
        }

        String escapedJson = dataArray.toString().replace("\\", "\\\\").replace("\"", "\\\"");
        String html = "<!DOCTYPE html><html><head><meta charset='UTF-8'><script src='https://d3js.org/d3.v7.min.js'></script>"
            + "<style>body{margin:0;background:#fff;}svg{width:100%;height:180px;}text{font-family:sans-serif;font-size:11px;fill:#374151;}</style>"
            + "</head><body><svg></svg><script>"
            + "const data=JSON.parse(\"" + escapedJson + "\");"
            + "if(!data.length){document.body.innerHTML='<div style=\"padding:20px;color:#666\">No data</div>';}" 
            + "const svg=d3.select('svg');const w=svg.node().clientWidth||340;const h=180;"
            + "const m={top:10,right:10,bottom:28,left:32};const g=svg.append('g').attr('transform','translate('+m.left+','+m.top+')');"
            + "const x=d3.scaleLinear().domain([0,data.length-1]).range([0,w-m.left-m.right]);"
            + "const y=d3.scaleLinear().domain(d3.extent(data,d=>d.value)).nice().range([h-m.top-m.bottom,0]);"
            + "const line=d3.line().x((d,i)=>x(i)).y(d=>y(d.value));"
            + "g.append('path').datum(data).attr('fill','none').attr('stroke','#3b82f6').attr('stroke-width',2).attr('d',line);"
            + "g.append('g').attr('transform','translate(0,'+(h-m.top-m.bottom)+')').call(d3.axisBottom(x).ticks(4));"
            + "g.append('g').call(d3.axisLeft(y).ticks(4));"
            + "</script></body></html>";

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
            return;
        }
        graphWebView = new WebView();
        AnchorPane.setTopAnchor(graphWebView, 0.0);
        AnchorPane.setRightAnchor(graphWebView, 0.0);
        AnchorPane.setBottomAnchor(graphWebView, 0.0);
        AnchorPane.setLeftAnchor(graphWebView, 0.0);
        graphAnchor.getChildren().setAll(graphWebView);
    }

    private void ensureMiniGraphWebView() {
        if (miniGraphWebView != null) {
            return;
        }
        miniGraphWebView = new WebView();
        miniGraphWebView.setPrefHeight(180);
        AnchorPane.setTopAnchor(miniGraphWebView, 0.0);
        AnchorPane.setRightAnchor(miniGraphWebView, 0.0);
        AnchorPane.setBottomAnchor(miniGraphWebView, 0.0);
        AnchorPane.setLeftAnchor(miniGraphWebView, 0.0);
        miniGraphAnchor.getChildren().setAll(miniGraphWebView);
    }

    private String loadHtmlTemplate() {
        try {
            InputStream inputStream = this.getClass().getResourceAsStream("/com/example/demo/feedbackGraph.html");
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

    private static class RuleCardData {
        String configKey;
        String useCaseKey;
        String useCaseLabel;
        String rangeLabel;
        String pulseLabel;
        String intensityLabel;
        String durationLabel;
        String intervalLabel;
    }
}

