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
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
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
import javafx.concurrent.Worker;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import com.example.demo.model.Schedule;
import com.example.demo.model.ScheduleApiResponse;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebEvent;
import javafx.scene.web.WebView;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.Base64;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DashboardController {
    private static final List<String> DEFAULT_USE_CASES = List.of("HeartRate", "MoonAzimuth", "SunAzimuth", "Pollution");
    private static final String DEFAULT_CHAT_COMMAND_PREFIX = "$";
    private static final String DELETE_ICON_PATH = "/com/example/demo/images/delete.png";
    private static final String SETTINGS_ICON_PATH = "/com/example/demo/images/settings.png";
    private static final String FEEDBACK_GRAPH_TEMPLATE_PATH = "/com/example/demo/view/templates/feedbackGraph.html";
    private static final String MINI_GRAPH_TEMPLATE_PATH = "/com/example/demo/view/templates/miniFeedbackGraph.html";
    private static final Pattern LOG_INTERVAL_PATTERN = Pattern.compile("^\\d+(\\.\\d+)?\\s+(second|seconds|minute|minutes|hour|hours|day|days)$", Pattern.CASE_INSENSITIVE);
    private static final List<ChatCommandOption> CHAT_COMMAND_OPTIONS = createChatCommandOptions();
    private static final Map<String, ChatCommandOption> CHAT_COMMAND_OPTIONS_BY_COMMAND = createChatCommandOptionMap();

    @FXML
    private ToggleButton useCasesNavButton;
    @FXML
    private ToggleButton UsersNavButton;
    @FXML
    private VBox useCasesSidebarPane;
    @FXML
    private VBox UsersSidebarPane;
    @FXML
    private ListView<String> useCaseListView;
    @FXML
    private ListView<User> UsersSidebarList;
    @FXML
    private Label UsersCountLabel;
    @FXML
    private Label selectedUserForAssignmentLabel;
    @FXML
    private Label currentUserUseCaseLabel;
    @FXML
    private ComboBox<String> userUseCaseComboBox;
    @FXML
    private Button assignUserUseCaseButton;

    @FXML
    private Label selectedUseCaseLabel;
    @FXML
    private ComboBox<User> UsersComboBox;
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
    private Tab schedulingTab;
    @FXML
    private Tab yellowBookTab;

    @FXML
    private ScrollPane chatScrollPane;
    @FXML
    private VBox chatHistoryBox;
    @FXML
    private ListView<ChatCommandOption> chatCommandDropdownList;
    @FXML
    private TextField chatInputField;
    @FXML
    private Button chatSendButton;
    @FXML
    private Label chatCommandHintLabel;
    @FXML
    private VBox contextDrawer;
    @FXML
    private Button contextDrawerToggleButton;
    @FXML
    private Label contextUseCaseLabel;
    @FXML
    private Label contextUsersLabel;
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
    private Button changeRuleButton;
    @FXML
    private Button clearRuleButton;
    @FXML
    private Button refreshRuleButton;
    @FXML
    private Label selectedMappingLabel;
    @FXML
    private Label loggingIntervalValueLabel;
    @FXML
    private Button editLoggingIntervalButton;
    @FXML
    private Button refreshRuleSummaryButton;
    @FXML
    private Label agentTypingLabel;

    // Scheduling UI
    @FXML
    private TableView<Schedule> schedulesTable;
    @FXML
    private TableColumn<Schedule, Integer> colScheduleId;
    @FXML
    private TableColumn<Schedule, Integer> colUserId;
    @FXML
    private TableColumn<Schedule, String> colMeasureType;
    @FXML
    private TableColumn<Schedule, Double> colTrigger;
    @FXML
    private TableColumn<Schedule, String> colNextCheck;
    @FXML
    private TableColumn<Schedule, Integer> colInterval;
    @FXML
    private TableColumn<Schedule, Boolean> colActive;
    @FXML
    private TextField scheduleIdField;
    @FXML
    private TextField userIdField;
    @FXML
    private TextField intervalDaysField;
    @FXML
    private ComboBox<String> measureTypeComboBox;
    @FXML
    private TextField triggerPercentageField;
    @FXML
    private Label scheduleStatusLabel;
    @FXML
    private VBox yellowBookContentBox;
    @FXML
    private Label yellowBookStatusLabel;
    @FXML
    private Label yellowBookSelectedParameterLabel;
    @FXML
    private TextField yellowBookParameterNameField;
    @FXML
    private TextField yellowBookParameterFormatField;
    @FXML
    private TextField yellowBookParameterValueField;
    @FXML
    private TextField yellowBookParameterDescriptionField;
    @FXML
    private CheckBox yellowBookRequiredCheckBox;
    @FXML
    private Button yellowBookSaveButton;
    @FXML
    private Button yellowBookClearButton;

    private final DashboardState state = DashboardState.getInstance();
    private final ObservableList<User> users = FXCollections.observableArrayList();
    private final ObservableList<String> useCases = FXCollections.observableArrayList();
    private final Map<String, String> useCaseDescriptions = new HashMap<>();
    private final Map<String, String> useCaseDisplayNames = new HashMap<>();
    private final Map<String, Integer> useCaseIdsByNormalizedName = new HashMap<>();
    private final Map<String, String> useCaseLoggingInterval = new HashMap<>();
    private final Map<String, List<DictionaryParameterData>> yellowBookEntries = new LinkedHashMap<>();
    private final List<RuleCardData> allRules = new ArrayList<>();
    private final String chatSessionId = UUID.randomUUID().toString();
    private String activeMonitoringTypeKey = "";
    private RuleCardData selectedMappingForEdit;
    private DictionaryParameterData selectedYellowBookParameter;

    private WebView graphWebView;
    private WebView miniGraphWebView;
    private StackPane graphLoadingOverlay;
    private StackPane miniGraphLoadingOverlay;
    private JsonNode latestGraphData;
    private final PauseTransition graphUpdateDebounce = new PauseTransition(Duration.millis(250));
    private static final String GRAPH_EXPORT_ALERT_PREFIX = "__GRAPH_EXPORT__";

    @FXML
    private void initialize() {
        setupSidebar();
        setupTopbar();
        setupTabs();
        setupChat();
        setupGraph();
        setupMappingsRuleBuilder();
        setupSchedulesTable();
        setupYellowBookEditor();
        setupStateListeners();

        loadUserss();
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
        updateSelectedMappingForEdit(null);
    }

    private void setupSchedulesTable() {
        try {
            if (colScheduleId != null) colScheduleId.setCellValueFactory(new PropertyValueFactory<>("scheduleId"));
            if (colUserId != null) colUserId.setCellValueFactory(new PropertyValueFactory<>("userId"));
            if (colMeasureType != null) colMeasureType.setCellValueFactory(new PropertyValueFactory<>("measureType"));
            if (colTrigger != null) colTrigger.setCellValueFactory(new PropertyValueFactory<>("triggerPercentage"));
            if (colNextCheck != null) colNextCheck.setCellValueFactory(new PropertyValueFactory<>("nextCheck"));
            if (colInterval != null) colInterval.setCellValueFactory(new PropertyValueFactory<>("intervalDays"));
            if (colActive != null) colActive.setCellValueFactory(new PropertyValueFactory<>("active"));
            
            // Add selection listener to populate text fields when a row is clicked
            if (schedulesTable != null) {
                schedulesTable.getSelectionModel().selectedItemProperty().addListener(onNewValueChanged(selectedSchedule -> {
                    if (selectedSchedule != null) {
                        populateScheduleFields(selectedSchedule);
                    }
                }));
            }
            if (measureTypeComboBox != null) {
                measureTypeComboBox.getItems().setAll("average", "median", "mode");
                measureTypeComboBox.setValue("average");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setupYellowBookEditor() {
        updateYellowBookEditor(null);
    }
    
    private void populateScheduleFields(Schedule schedule) {
        if (schedule == null) {
            return;
        }
        
        if (scheduleIdField != null) {
            scheduleIdField.setText(String.valueOf(schedule.getScheduleId()));
        }
        if (userIdField != null) {
            userIdField.setText(String.valueOf(schedule.getUserId()));
        }
        if (intervalDaysField != null) {
            intervalDaysField.setText(String.valueOf(schedule.getIntervalDays()));
        }
        if (measureTypeComboBox != null) {
            String measureType = schedule.getMeasureType() == null ? "" : schedule.getMeasureType().trim().toLowerCase(Locale.ROOT);
            measureTypeComboBox.setValue(List.of("average", "median", "mode").contains(measureType) ? measureType : "average");
        }
        if (triggerPercentageField != null) {
            triggerPercentageField.setText(String.valueOf(schedule.getTriggerPercentage()));
        }
    }

    private void updateSchedulesTable(ScheduleApiResponse resp) {
        if (resp == null) return;
        ObservableList<Schedule> list = FXCollections.observableArrayList(resp.getSchedules());
        if (schedulesTable != null) schedulesTable.setItems(list);
        if (scheduleStatusLabel != null) scheduleStatusLabel.setText(resp.getReply() != null && !resp.getReply().isEmpty() ? resp.getReply() : (resp.isSuccess() ? "OK" : resp.getErrorMessage()));
    }

    @FXML
    private void onListAllSchedules() {
        String uc = this.getSelectedUsecaseName();
        ApiService.getInstance().listAllSchedules(uc).thenAccept(resp -> Platform.runLater(() -> updateSchedulesTable(resp)));
    }

    @FXML
    private void onListActiveSchedules() {
        String uc = this.getSelectedUsecaseName();
        ApiService.getInstance().listActiveSchedules(uc).thenAccept(resp -> Platform.runLater(() -> updateSchedulesTable(resp)));
    }

    @FXML
    private void onAddSchedule() {
        try {
            int userId = Integer.parseInt(userIdField.getText().trim());
            int interval = Integer.parseInt(intervalDaysField.getText().trim());
            String measure = getSelectedScheduleMeasure();
            double trigger = Double.parseDouble(triggerPercentageField.getText().trim());
            String uc = this.getSelectedUsecaseName();
            ApiService.getInstance().addSchedule(userId, interval, measure, trigger, uc)
                .thenAccept(resp -> Platform.runLater(() -> updateSchedulesTable(resp)));
        } catch (Exception e) {
            showErrorAlert("Input Error", "Please make sure all schedule fields are valid numbers/text.");
        }
    }

    @FXML
    private void onChangeSchedule() {
        try {
            int schedId = Integer.parseInt(scheduleIdField.getText().trim());
            int userId = Integer.parseInt(userIdField.getText().trim());
            int interval = Integer.parseInt(intervalDaysField.getText().trim());
            String measure = getSelectedScheduleMeasure();
            double trigger = Double.parseDouble(triggerPercentageField.getText().trim());
            String uc = this.getSelectedUsecaseName();
            ApiService.getInstance().changeSchedule(schedId, userId, interval, measure, trigger, uc)
                .thenAccept(resp -> Platform.runLater(() -> updateSchedulesTable(resp)));
        } catch (Exception e) {
            showErrorAlert("Input Error", "Please make sure schedule id and other fields are valid.");
        }
    }

    @FXML
    private void onActivateSchedule() {
        try {
            int schedId = Integer.parseInt(scheduleIdField.getText().trim());
            String uc = this.getSelectedUsecaseName();
            ApiService.getInstance().activateSchedule(schedId, uc)
                .thenAccept(resp -> Platform.runLater(() -> updateSchedulesTable(resp)));
        } catch (Exception e) {
            showErrorAlert("Input Error", "Please provide a valid schedule id to activate.");
        }
    }

    @FXML
    private void onDeactivateSchedule() {
        try {
            int schedId = Integer.parseInt(scheduleIdField.getText().trim());
            String uc = this.getSelectedUsecaseName();
            ApiService.getInstance().deactivateSchedule(schedId, uc)
                .thenAccept(resp -> Platform.runLater(() -> updateSchedulesTable(resp)));
        } catch (Exception e) {
            showErrorAlert("Input Error", "Please provide a valid schedule id to deactivate.");
        }
    }

    private String getSelectedScheduleMeasure() {
        String measure = measureTypeComboBox == null ? "" : measureTypeComboBox.getValue();
        if (measure == null || measure.isBlank()) {
            throw new IllegalArgumentException("Please choose average, median, or mode.");
        }
        return measure.trim().toLowerCase(Locale.ROOT);
    }

     private String getSelectedUsecaseName() {
         // Use the selected use case from the label or state
         String uc = selectedUseCaseLabel == null ? "" : selectedUseCaseLabel.getText();
         if (uc == null || uc.trim().isEmpty() || "-".equals(uc.trim())) {
             uc = state.getSelectedUseCase();
         }
         return uc == null ? "" : uc.trim();
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
                    updateSelectedMappingForEdit(null);
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
        clearRuleBuilderForm();
        updateSelectedMappingForEdit(null);
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

    @FXML
    private void changeSelectedMappingConfig() {
        if (selectedMappingForEdit == null || selectedMappingForEdit.mappingId <= 0) {
            showErrorAlert("Missing Mapping", "Select an active mapping card before changing it.");
            return;
        }

        try {
            SensorRuleConfig ruleConfig = buildRuleConfigFromForm();
            ruleConfig.setId(selectedMappingForEdit.mappingId);

            Integer useCaseId = state.getSelectedUseCaseId();
            if (useCaseId == null || useCaseId <= 0) {
                showErrorAlert("Missing Use Case Id", "Could not resolve use case id. Refresh use cases and try again.");
                return;
            }

            final RuleCardData editingRule = selectedMappingForEdit;
            ApiService.getInstance().changeMapping(
                    editingRule.mappingId,
                    ruleConfig,
                    useCaseId,
                    getSelectedUsecaseName(),
                    chatSessionId
                )
                .thenAccept(success -> Platform.runLater(() -> {
                    if (!success) {
                        showErrorAlert("Update Failed", "Could not change mapping ID " + editingRule.mappingId + ".");
                        return;
                    }

                    clearRuleBuilderForm();
                    updateSelectedMappingForEdit(null);
                    loadMappings();
                    showInfoAlert("Mapping Updated", "Updated mapping ID " + editingRule.mappingId + ".");
                }))
                .exceptionally(ex -> {
                    showErrorAlert("Update Failed", "Failed to change mapping: " + ex.getMessage());
                    return null;
                });
        } catch (IllegalArgumentException ex) {
            showErrorAlert("Validation Error", ex.getMessage());
        }
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

    private void populateRuleBuilderFromMapping(RuleCardData rule) {
        if (rule == null) {
            return;
        }

        setText(ruleMinValueField, rule.minValue);
        setText(ruleMaxValueField, rule.maxValue);
        setText(ruleMinPulsesField, rule.minPulses);
        setText(ruleMaxPulsesField, rule.maxPulses);
        setText(ruleMinIntensityField, rule.minIntensity);
        setText(ruleMaxIntensityField, rule.maxIntensity);
        setText(ruleMinDurationField, rule.minDuration);
        setText(ruleMaxDurationField, rule.maxDuration);
        setText(ruleMinIntervalField, rule.minInterval);
        setText(ruleMaxIntervalField, rule.maxInterval);
    }

    private void setText(TextField field, int value) {
        if (field != null) {
            field.setText(String.valueOf(value));
        }
    }

     private void setupSidebar() {
         useCasesNavButton.setOnAction(ignored -> setSidebarMode(true));
         UsersNavButton.setOnAction(ignored -> setSidebarMode(false));
         setSidebarMode(true);

         setupUsersSidebarListCellFactory();

         useCaseListView.setItems(useCases);
         useCaseListView.getSelectionModel().selectedItemProperty().addListener(onNewValueChanged(newValue -> {
             if (newValue == null || newValue.isBlank()) {
                 return;
             }
             state.setSelectedUseCase(newValue);
         }));

         if (userUseCaseComboBox != null) {
             userUseCaseComboBox.setItems(useCases);
             userUseCaseComboBox.valueProperty().addListener(onNewValueChanged(ignored -> updateAssignUserUseCaseButtonState()));
         }
         updateAssignUserUseCaseButtonState();

          UsersSidebarList.getSelectionModel().selectedItemProperty().addListener(onNewValueChanged(newValue -> {
              if (newValue == null) {
                  return;
              }
              if (newValue != UsersComboBox.getValue()) {
                  UsersComboBox.setValue(newValue);
              }
              updateUserAssignmentPanel(newValue);

              // Automatically select the user's assigned use case and display its mappings
              if (newValue.getUsecaseName() != null && !newValue.getUsecaseName().isBlank()) {
                  String userUseCase = resolveUseCaseDisplayName(newValue.getUsecaseName());
                  state.setSelectedUseCase(userUseCase);
              }
          }));
     }

    private void setupUsersSidebarListCellFactory() {
        UsersSidebarList.setCellFactory(listView -> new ListCell<>() {
            private final Label userLabel = new Label();
            private final Region spacer = new Region();
            private final Button editButton = createEditUserButton();
            private final HBox content = new HBox(8, userLabel, spacer, editButton);

            {
                content.setAlignment(Pos.CENTER_LEFT);
                HBox.setHgrow(spacer, Priority.ALWAYS);
                userLabel.getStyleClass().add("sidebar-user-row-label");
                editButton.setFocusTraversable(false);
            }

            @Override
            protected void updateItem(User item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }

                userLabel.setText(formatUser(item));
                editButton.setOnAction(ignored -> onEditUserRequested(item));
                setText(null);
                setGraphic(content);
            }
        });
    }


    @FXML
    private void onAddUseCaseRequested() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Add Use Case");
        dialog.setHeaderText("Create a new use case. Name is required.");

        ButtonType addButtonType = new ButtonType("Add", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(addButtonType, ButtonType.CANCEL);

        TextField nameField = new TextField();
        nameField.setPromptText("Use case name");

        TextField descriptionField = new TextField();
        descriptionField.setPromptText("Use case description (optional)");

        Label feedbackNote = new Label("To receive feedback, follow the n8n side instructions in 'Vibration Orchastrator', create a new rule and assign it to a user.");
        feedbackNote.setWrapText(true);

        VBox content = new VBox(8,
                new Label("Name *"),
                nameField,
                new Label("Description"),
                descriptionField,
                feedbackNote
        );
        content.setPadding(new Insets(10));
        dialog.getDialogPane().setContent(content);

        Node addButton = dialog.getDialogPane().lookupButton(addButtonType);
        addButton.setDisable(true);
        nameField.textProperty().addListener((obs, oldValue, newValue) ->
                addButton.setDisable(newValue == null || newValue.trim().isEmpty()));

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isEmpty() || result.get() != addButtonType) {
            return;
        }

        String name = nameField.getText() == null ? "" : nameField.getText().trim();
        String description = descriptionField.getText() == null ? "" : descriptionField.getText().trim();
        if (name.isBlank()) {
            showErrorAlert("Validation Error", "Use case name is required.");
            return;
        }

        ApiService.getInstance().createUseCase(name, description)
                .thenAccept(success -> Platform.runLater(() -> {
                    if (!success) {
                        showErrorAlert("Create Failed", "Could not create the use case. Please try again.");
                        return;
                    }

                    state.setSelectedUseCase(name);
                    showInfoAlert(
                            "Use Case Created",
                            "Use case '" + name + "' was created.\n\nTo receive feedback, follow the instructions on the n8n side ('Vibration Orchastrator'), create a new rule, and assign it to a user."
                    );
                    loadUseCases();
                }))
                .exceptionally(ex -> {
                    showErrorAlert("Create Failed", "Failed to create use case: " + ex.getMessage());
                    return null;
                });
    }
    private void setupTopbar() {
        UsersComboBox.setItems(users);
        UsersComboBox.setCellFactory(listView -> new ListCell<>() {
            @Override
            protected void updateItem(User item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : formatUser(item));
            }
        });
        UsersComboBox.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(User item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "Select Users" : formatUser(item));
            }
        });

         UsersComboBox.valueProperty().addListener(onNewValueChanged(newValue -> {
             if (newValue == null) {
                 return;
             }
             state.setSelectedUsers(newValue);
             contextUsersLabel.setText(formatUser(newValue));
             if (UsersSidebarList.getSelectionModel().getSelectedItem() != newValue) {
                 UsersSidebarList.getSelectionModel().select(newValue);
             }

             syncMonitoringEditorToSelectedUser(newValue);

             // Automatically select the user's assigned use case and display its mappings
             if (newValue.getUsecaseName() != null && !newValue.getUsecaseName().isBlank()) {
                 String userUseCase = resolveUseCaseDisplayName(newValue.getUsecaseName());
                 state.setSelectedUseCase(userUseCase);
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
            } else if (newValue == yellowBookTab) {
                loadYellowBookDictionary();
            }
        }));
    }

    private void setupChat() {
        setupChatShortcuts();
        setupChatAutocomplete();
        chatSendButton.setOnAction(ignored -> onSendMessage());
        chatInputField.setOnAction(ignored -> onSendMessage());

        chatHistoryBox.heightProperty().addListener(onNewValueChanged(newValue -> chatScrollPane.setVvalue(1.0)));
        chatInputField.textProperty().addListener((obs, oldValue, newValue) -> {
            updateChatShortcutHint(newValue);
            updateChatCommandSuggestions(newValue);
        });
        chatInputField.addEventFilter(KeyEvent.KEY_PRESSED, this::handleChatCommandKeyPress);

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

    private void setupChatShortcuts() {
        if (chatInputField != null) {
            chatInputField.setPromptText("Type a chat message or start with $ for commands...");
        }

        updateChatShortcutHint(chatInputField == null ? "" : chatInputField.getText());
    }

    private void setupChatAutocomplete() {
        if (chatCommandDropdownList == null) {
            return;
        }

        chatCommandDropdownList.setItems(FXCollections.observableArrayList());
        chatCommandDropdownList.setVisible(false);
        chatCommandDropdownList.setManaged(false);
        chatCommandDropdownList.setFocusTraversable(false);
        chatCommandDropdownList.setMouseTransparent(false);
        chatCommandDropdownList.setFixedCellSize(56.0);
        chatCommandDropdownList.setCellFactory(listView -> new ListCell<>() {
            private final Label commandLabel = new Label();
            private final Label descriptionLabel = new Label();
            private final VBox content = new VBox(2.0, commandLabel, descriptionLabel);

            {
                commandLabel.getStyleClass().add("chat-command-name");
                descriptionLabel.getStyleClass().add("chat-command-description");
                descriptionLabel.setWrapText(true);
                content.getStyleClass().add("chat-command-cell-content");
            }

            @Override
            protected void updateItem(ChatCommandOption item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }

                commandLabel.setText(item.command());
                descriptionLabel.setText(item.description());
                setText(null);
                setGraphic(content);
            }
        });

        chatCommandDropdownList.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                chatCommandDropdownList.scrollTo(chatCommandDropdownList.getItems().indexOf(newValue));
            }
        });

        chatCommandDropdownList.setOnMouseClicked(event -> {
            if (event.getClickCount() >= 1) {
                acceptSelectedChatCommand();
            }
        });
    }

    private void handleChatCommandKeyPress(KeyEvent event) {
        if (chatCommandDropdownList == null || !chatCommandDropdownList.isVisible()) {
            return;
        }

        if (event.getCode() == KeyCode.ESCAPE) {
            hideChatCommandDropdown();
            event.consume();
            return;
        }

        if (event.getCode() == KeyCode.UP) {
            moveChatCommandSelection(-1);
            event.consume();
            return;
        }

        if (event.getCode() == KeyCode.DOWN) {
            moveChatCommandSelection(1);
            event.consume();
            return;
        }

        if (event.getCode() == KeyCode.ENTER) {
            acceptSelectedChatCommand();
            event.consume();
        }
    }

    private void moveChatCommandSelection(int delta) {
        if (chatCommandDropdownList == null || chatCommandDropdownList.getItems().isEmpty()) {
            return;
        }

        int size = chatCommandDropdownList.getItems().size();
        int currentIndex = chatCommandDropdownList.getSelectionModel().getSelectedIndex();
        int nextIndex = currentIndex < 0 ? 0 : (currentIndex + delta + size) % size;
        chatCommandDropdownList.getSelectionModel().select(nextIndex);
        chatCommandDropdownList.scrollTo(nextIndex);
    }

    private void updateChatCommandSuggestions(String inputText) {
        if (chatCommandDropdownList == null) {
            return;
        }

        String normalized = inputText == null ? "" : inputText.stripLeading();
        if (!normalized.startsWith(DEFAULT_CHAT_COMMAND_PREFIX)) {
            hideChatCommandDropdown();
            return;
        }

        String afterPrefix = normalized.substring(DEFAULT_CHAT_COMMAND_PREFIX.length());
        String query = afterPrefix.stripLeading();
        if (query.isEmpty()) {
            showChatCommandDropdown(CHAT_COMMAND_OPTIONS);
            return;
        }

        int spaceIndex = query.indexOf(' ');
        String searchText = spaceIndex >= 0 ? query.substring(0, spaceIndex).trim() : query.trim();
        String trailingText = spaceIndex >= 0 ? query.substring(spaceIndex).trim() : "";

        if (!trailingText.isEmpty() && CHAT_COMMAND_OPTIONS_BY_COMMAND.containsKey(searchText.toLowerCase(Locale.ROOT))) {
            hideChatCommandDropdown();
            return;
        }

        List<ChatCommandOption> filtered = new ArrayList<>();
        String lowerQuery = searchText.toLowerCase(Locale.ROOT);
        for (ChatCommandOption option : CHAT_COMMAND_OPTIONS) {
            if (option.matches(lowerQuery)) {
                filtered.add(option);
            }
        }

        if (filtered.isEmpty()) {
            hideChatCommandDropdown();
            return;
        }

        showChatCommandDropdown(filtered);
    }

    private void showChatCommandDropdown(List<ChatCommandOption> items) {
        if (chatCommandDropdownList == null) {
            return;
        }

        chatCommandDropdownList.getItems().setAll(items);
        chatCommandDropdownList.setVisible(true);
        chatCommandDropdownList.setManaged(true);
        chatCommandDropdownList.getSelectionModel().selectFirst();
        chatCommandDropdownList.scrollTo(0);

        if (chatCommandHintLabel != null) {
            chatCommandHintLabel.setText("Use ↑↓ Enter or click.");
            chatCommandHintLabel.setVisible(true);
            chatCommandHintLabel.setManaged(true);
        }
    }

    private void hideChatCommandDropdown() {
        if (chatCommandDropdownList != null) {
            chatCommandDropdownList.getItems().clear();
            chatCommandDropdownList.getSelectionModel().clearSelection();
            chatCommandDropdownList.setVisible(false);
            chatCommandDropdownList.setManaged(false);
        }

        if (chatCommandHintLabel != null) {
            chatCommandHintLabel.setText("");
            chatCommandHintLabel.setVisible(false);
            chatCommandHintLabel.setManaged(false);
        }
    }

    private void acceptSelectedChatCommand() {
        if (chatCommandDropdownList == null || !chatCommandDropdownList.isVisible() || chatInputField == null) {
            return;
        }

        ChatCommandOption selected = chatCommandDropdownList.getSelectionModel().getSelectedItem();
        if (selected == null && !chatCommandDropdownList.getItems().isEmpty()) {
            selected = chatCommandDropdownList.getItems().getFirst();
        }

        if (selected == null) {
            hideChatCommandDropdown();
            return;
        }

        String currentText = chatInputField.getText() == null ? "" : chatInputField.getText();
        String replacement = selected.command() + " ";
        String newText = replaceLeadingCommandToken(currentText, replacement);

        chatInputField.setText(newText);
        chatInputField.positionCaret(newText.length());
        hideChatCommandDropdown();
    }

    private String replaceLeadingCommandToken(String currentText, String replacement) {
        String text = currentText == null ? "" : currentText;
        String normalized = text.stripLeading();
        if (!normalized.startsWith(DEFAULT_CHAT_COMMAND_PREFIX)) {
            return replacement;
        }

        int firstSpace = normalized.indexOf(' ');
        if (firstSpace < 0) {
            return replacement;
        }

        String suffix = normalized.substring(firstSpace).stripLeading();
        if (suffix.isEmpty()) {
            return replacement;
        }

        return replacement + suffix;
    }

    private void updateChatShortcutHint(String inputText) {
        if (chatCommandHintLabel == null || (chatCommandDropdownList != null && chatCommandDropdownList.isVisible())) {
            return;
        }

        String normalized = inputText == null ? "" : inputText.trim();
        if (!normalized.startsWith(DEFAULT_CHAT_COMMAND_PREFIX)) {
            chatCommandHintLabel.setVisible(false);
            chatCommandHintLabel.setManaged(false);
            chatCommandHintLabel.setText("");
            return;
        }

        chatCommandHintLabel.setText("Use ↑↓ Enter or click.");
        chatCommandHintLabel.setVisible(true);
        chatCommandHintLabel.setManaged(true);
    }

    private String expandChatCommand(String message) {
        String trimmed = message == null ? "" : message.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }

        return expandChatCommandForPrefix(trimmed, DEFAULT_CHAT_COMMAND_PREFIX);
    }

    private String expandChatCommandForPrefix(String message, String prefix) {
        if (prefix == null || prefix.isBlank() || !message.startsWith(prefix)) {
            return message;
        }

        String commandPortion = message.substring(prefix.length()).trim();
        if (commandPortion.isEmpty()) {
            return message;
        }

        String[] parts = commandPortion.split("\\s+", 2);
        String command = parts[0].toLowerCase(Locale.ROOT).replace('-', '_');
        ChatCommandOption option = CHAT_COMMAND_OPTIONS_BY_COMMAND.get(command);
        if (option == null) {
            return message;
        }

        if (parts.length == 1 || parts[1].isBlank()) {
            return option.prompt();
        }

        return option.prompt() + " " + parts[1].trim();
    }

    private static List<ChatCommandOption> createChatCommandOptions() {
        List<ChatCommandOption> options = new ArrayList<>();
        options.add(new ChatCommandOption("$mapping", "Show Active Mappings", "Show the current feedback mappings", "show me the current feedback mappings"));
        options.add(new ChatCommandOption("$knowledge", "Experiment Background", "What is the background of this experiment", "what is the background of this experiment"));
        options.add(new ChatCommandOption("$dictionary", "Show Use Cases", "Show the use cases in the dictionary", "show me the use cases in the dictionary"));
        options.add(new ChatCommandOption("$schedule", "Show Active Schedules", "Show the active schedules", "show me the active schedules"));
        options.add(new ChatCommandOption("$usecase", "Show Use Case Configuration", "Show the use case configuration", "show me the use case configuration"));
        return List.copyOf(options);
    }

    private static Map<String, ChatCommandOption> createChatCommandOptionMap() {
        Map<String, ChatCommandOption> map = new LinkedHashMap<>();
        for (ChatCommandOption option : CHAT_COMMAND_OPTIONS) {
            map.put(option.command().substring(1), option);
        }
        return map;
    }

    private record ChatCommandOption(String command, String title, String description, String prompt) {
        private boolean matches(String query) {
            String normalizedQuery = query == null ? "" : query.trim();
            if (normalizedQuery.isEmpty()) {
                return true;
            }

            String lower = normalizedQuery.toLowerCase(Locale.ROOT);
            return command.toLowerCase(Locale.ROOT).contains(lower)
                || title.toLowerCase(Locale.ROOT).contains(lower)
                || description.toLowerCase(Locale.ROOT).contains(lower);
        }
    }

    private void setupGraph() {
        refreshGraphButton.setOnAction(ignored -> scheduleGraphUpdate());
        exportGraphCsvButton.setOnAction(ignored -> exportGraphData("csv"));
        exportGraphPdfButton.setOnAction(ignored -> exportGraphData("pdf"));
        ensureGraphWebView();
        ensureMiniGraphWebView();
        setGraphLoadingVisible(false);
        setMiniGraphLoadingVisible(false);
        renderGraphPlaceholder("Select a use case and Users to render graph data.");
        renderMiniGraphPlaceholder("Graph preview appears here.");
    }

    private void exportGraphData(String format) {
        User user = state.getSelectedUsers();
        String useCase = state.getSelectedUseCase();
        String range = state.getSelectedTimeRange();

        if (user == null || useCase == null || useCase.isBlank() || range == null || range.isBlank()) {
            showErrorAlert("Missing Selection", "Please select a Users, use case, and time range before exporting.");
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

        state.selectedUsersProperty().addListener(onNewValueChanged(newValue -> refreshRuleSummary()));
    }

    private void loadUserss() {
        User currentSelection = UsersComboBox.getValue();
        Integer selectedUserId = currentSelection == null ? null : currentSelection.getUserID();
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
                    int activeUsecaseId = node.path("active_usecase_id").asInt(0);
                    String usecaseName = node.path("usecase_name").asText("");

                    loaded.add(new User(id, firstName, lastName, activeUsecaseId, usecaseName));
                }

                Platform.runLater(() -> {
                     users.setAll(loaded);
                     UsersCountLabel.setText(String.valueOf(users.size()));

                     UsersSidebarList.getItems().setAll(users);

                     User restoredSelection = findUserById(selectedUserId);
                     if (restoredSelection != null) {
                         UsersComboBox.setValue(restoredSelection);
                         UsersSidebarList.getSelectionModel().select(restoredSelection);
                         syncMonitoringEditorToSelectedUser(UsersComboBox.getValue());
                     } else if (!users.isEmpty()) {
                         UsersComboBox.setValue(users.get(0));
                         UsersSidebarList.getSelectionModel().select(users.get(0));
                         syncMonitoringEditorToSelectedUser(UsersComboBox.getValue());
                     } else {
                         UsersComboBox.setValue(null);
                         UsersSidebarList.getSelectionModel().clearSelection();
                         syncMonitoringEditorToSelectedUser(UsersComboBox.getValue());
                         contextUsersLabel.setText("-");
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
                    User selectedUser = UsersComboBox == null ? null : UsersComboBox.getValue();
                    syncMonitoringEditorToSelectedUser(selectedUser);
                });
            })
            .exceptionally(ex -> {
                ex.printStackTrace();
                return null;
            });
    }



    private String resolveUseCaseDisplayName(String rawUseCase) {
        if (rawUseCase == null || rawUseCase.isBlank()) {
            return "-";
        }

        String normalized = normalizeUseCaseName(rawUseCase);
        for (String candidate : useCases) {
            if (normalized.equals(normalizeUseCaseName(candidate))) {
                return candidate;
            }
        }

        String fromMap = useCaseDisplayNames.get(normalized);
        if (fromMap != null && !fromMap.isBlank()) {
            return fromMap;
        }

        String fallback = toUseCaseLabel(normalized);
        return (fallback == null || fallback.isBlank()) ? rawUseCase.trim() : fallback;
    }

    private void syncMonitoringEditorToSelectedUser(User user) {
         if (user == null) {
             updateUserAssignmentPanel(null);
             return;
         }
         updateUserAssignmentPanel(user);
     }

     private void updateUserAssignmentPanel(User user) {
         if (user == null) {
             selectedUserForAssignmentLabel.setText("-");
             currentUserUseCaseLabel.setText("-");
             userUseCaseComboBox.setValue(null);
             updateAssignUserUseCaseButtonState();
             return;
         }

         String userDisplay = formatUser(user);
         selectedUserForAssignmentLabel.setText(userDisplay);

         String displayUsecase = resolveUseCaseDisplayName(user.getUsecaseName());
         currentUserUseCaseLabel.setText(displayUsecase);

         if (displayUsecase != null && !displayUsecase.isBlank()) {
             userUseCaseComboBox.setValue(displayUsecase);
         } else if (!userUseCaseComboBox.getItems().isEmpty()) {
             userUseCaseComboBox.setValue(userUseCaseComboBox.getItems().get(0));
         }

         updateAssignUserUseCaseButtonState();
     }

     private void updateAssignUserUseCaseButtonState() {
         if (assignUserUseCaseButton == null) {
             return;
         }

         User selectedUser = UsersSidebarList.getSelectionModel().getSelectedItem();
         String selected = userUseCaseComboBox == null ? null : userUseCaseComboBox.getValue();

         if (selectedUser == null || selected == null || selected.isBlank()) {
             assignUserUseCaseButton.setDisable(true);
             return;
         }

         boolean unchanged =
                 normalizeUseCaseName(selected).equals(normalizeUseCaseName(selectedUser.getUsecaseName()));

         assignUserUseCaseButton.setDisable(unchanged);
     }

     @FXML
     private void onAssignUserUseCase() {
         if (userUseCaseComboBox == null) {
             return;
         }

         User selectedUser = UsersSidebarList.getSelectionModel().getSelectedItem();
         if (selectedUser == null) {
             showErrorAlert("Missing User", "Please select a user from the list before assigning a use case.");
             return;
         }

         String selectedUsecase = userUseCaseComboBox.getValue();
         if (selectedUsecase == null || selectedUsecase.isBlank()) {
             showErrorAlert("Missing Use Case", "Please select a use case to assign.");
             return;
         }

         if (assignUserUseCaseButton != null) {
             assignUserUseCaseButton.setDisable(true);
         }

         ApiService.getInstance().setMonitoringType(selectedUser.getUserID(), selectedUsecase)
                 .thenAccept(success -> Platform.runLater(() -> {
                     if (!success) {
                         showErrorAlert("Update Failed", "Could not assign use case to user. Please try again.");
                         updateAssignUserUseCaseButtonState();
                         return;
                     }

                     String resolvedDisplayName = resolveUseCaseDisplayName(selectedUsecase);
                     currentUserUseCaseLabel.setText(resolvedDisplayName);

                     selectedUser.setUsecaseName(resolvedDisplayName);

                     showInfoAlert(
                             "Use Case Assigned",
                             "User " + selectedUser.getUserID() + " is now assigned to " + resolvedDisplayName + "."
                     );

                     // Refresh users list and synchronize the topbar user selection
                     loadUserss();
                     // Set the assigned use case as the selected use case in the main display
                     state.setSelectedUseCase(resolvedDisplayName);
                 }))
                 .exceptionally(ex -> {
                     showErrorAlert("Update Failed", "Failed to assign use case: " + ex.getMessage());
                     Platform.runLater(this::updateAssignUserUseCaseButtonState);
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
        updateSelectedMappingForEdit(null);
        refreshRuleSummary();
        renderMappingsForUseCase(selectedUseCase);
        clearChatMessages();
        addChatMessage("Selected use case: " + selectedUseCase, false);
    }

    private void updateSelectedMappingForEdit(RuleCardData rule) {
        selectedMappingForEdit = rule;

        if (selectedMappingLabel != null) {
            selectedMappingLabel.setText(rule == null ? "None selected" : "Editing ID " + rule.mappingId);
        }

        if (changeRuleButton != null) {
            changeRuleButton.setDisable(rule == null || rule.mappingId <= 0);
        }
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

    @FXML
    private void onRefreshYellowBook() {
        loadYellowBookDictionary();
    }

    private void loadYellowBookDictionary() {
        Integer contextUsecaseId = resolveDictionaryContextUseCaseId();
        if (contextUsecaseId == null || contextUsecaseId <= 0) {
            if (yellowBookStatusLabel != null) {
                yellowBookStatusLabel.setText("Load use cases before opening the Yellow book.");
            }
            return;
        }

        if (yellowBookStatusLabel != null) {
            yellowBookStatusLabel.setText("Loading dictionary...");
        }

        String contextUsecaseName = getSelectedUsecaseName();
        ApiService.getInstance()
            .sendDictionaryRequest("Show me the full use case dictionary.", contextUsecaseId, contextUsecaseName, chatSessionId)
            .thenAccept(response -> Platform.runLater(() -> {
                if (response == null || response.has("error")) {
                    String message = response == null ? "No response from n8n." : response.path("message").asText("Dictionary request failed.");
                    yellowBookStatusLabel.setText(message);
                    return;
                }

                String reply = response.path("reply").asText("");
                yellowBookEntries.clear();
                yellowBookEntries.putAll(parseYellowBookReply(reply));
                renderYellowBook();
            }))
            .exceptionally(ex -> {
                Platform.runLater(() -> yellowBookStatusLabel.setText("Dictionary request failed: " + ex.getMessage()));
                return null;
            });
    }

    private Integer resolveDictionaryContextUseCaseId() {
        Integer selectedId = state.getSelectedUseCaseId();
        if (selectedId != null && selectedId > 0) {
            return selectedId;
        }

        if (!useCaseIdsByNormalizedName.isEmpty()) {
            return useCaseIdsByNormalizedName.values().stream()
                .filter(id -> id != null && id > 0)
                .findFirst()
                .orElse(null);
        }

        return null;
    }

    private Map<String, List<DictionaryParameterData>> parseYellowBookReply(String reply) {
        LinkedHashMap<String, List<DictionaryParameterData>> parsed = new LinkedHashMap<>();
        if (reply == null || reply.isBlank()) {
            return parsed;
        }

        String activeUseCase = null;
        for (String rawLine : reply.split("\\R")) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }

            Matcher headerMatcher = Pattern.compile("\\*\\*(.+?)\\*\\*").matcher(line);
            if (line.contains("**") && headerMatcher.find() && !line.toLowerCase(Locale.ROOT).startsWith("here are")) {
                activeUseCase = headerMatcher.group(1).trim();
                parsed.putIfAbsent(activeUseCase, new ArrayList<>());
                continue;
            }

            if (activeUseCase == null) {
                continue;
            }

            DictionaryParameterData parameter = parseYellowBookParameterLine(activeUseCase, line);
            if (parameter != null) {
                parsed.get(activeUseCase).add(parameter);
            }
        }

        return parsed;
    }

    private DictionaryParameterData parseYellowBookParameterLine(String useCaseName, String rawLine) {
        String line = rawLine
            .replaceAll("^[^A-Za-z0-9_]+", "")
            .replace("[Required]", "")
            .trim();
        boolean required = rawLine.contains("[Required]");

        Matcher matcher = Pattern.compile("^(.+?)\\s*\\((.*)\\)$").matcher(line);
        if (!matcher.matches()) {
            return null;
        }

        String parameterName = matcher.group(1).trim();
        String formatAndValue = matcher.group(2).trim();
        String format = formatAndValue;
        String value = "";

        int valueMarker = formatAndValue.indexOf(" [Specific value!]");
        if (valueMarker >= 0) {
            formatAndValue = formatAndValue.substring(0, valueMarker).trim();
        }

        int equalsIndex = formatAndValue.indexOf(" = ");
        if (equalsIndex >= 0) {
            format = formatAndValue.substring(0, equalsIndex).trim();
            value = formatAndValue.substring(equalsIndex + 3).trim();
        }

        if (parameterName.isBlank()) {
            return null;
        }

        return new DictionaryParameterData(useCaseName, parameterName, format, required, "", value);
    }

    private void renderYellowBook() {
        if (yellowBookContentBox == null || yellowBookStatusLabel == null) {
            return;
        }

        yellowBookContentBox.getChildren().clear();
        if (yellowBookEntries.isEmpty()) {
            yellowBookStatusLabel.setText("No dictionary parameters found.");
            return;
        }

        yellowBookStatusLabel.setText("Loaded " + yellowBookEntries.size() + " use cases.");
        yellowBookEntries.forEach((useCaseName, parameters) -> yellowBookContentBox.getChildren().add(createYellowBookUseCaseCard(useCaseName, parameters)));
    }

    private VBox createYellowBookUseCaseCard(String useCaseName, List<DictionaryParameterData> parameters) {
        VBox card = new VBox(10);
        card.getStyleClass().add("yellow-book-card");

        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label(useCaseName);
        title.getStyleClass().add("yellow-book-usecase-title");
        Label count = new Label(parameters.size() + " parameters");
        count.getStyleClass().add("topbar-muted-value");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button deleteUseCaseButton = new Button("Remove Use Case");
        deleteUseCaseButton.getStyleClass().add("cancel-button");
        deleteUseCaseButton.setOnAction(ignored -> onRemoveYellowBookUseCase(useCaseName));
        header.getChildren().addAll(title, count, spacer, deleteUseCaseButton);

        VBox parameterBox = new VBox(6);
        if (parameters.isEmpty()) {
            Label empty = new Label("No parameters registered.");
            empty.getStyleClass().add("topbar-muted-value");
            parameterBox.getChildren().add(empty);
        } else {
            for (DictionaryParameterData parameter : parameters) {
                parameterBox.getChildren().add(createYellowBookParameterRow(parameter));
            }
        }

        card.getChildren().addAll(header, parameterBox);
        return card;
    }

    private HBox createYellowBookParameterRow(DictionaryParameterData parameter) {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("yellow-book-parameter-row");
        if (isSelectedYellowBookParameter(parameter)) {
            row.getStyleClass().add("yellow-book-parameter-row-selected");
        }

        VBox textBox = new VBox(2);
        Label name = new Label(parameter.parameterName);
        name.getStyleClass().add("mapping-card-title");
        Label details = new Label(buildYellowBookParameterDetails(parameter));
        details.getStyleClass().add("topbar-muted-value");
        textBox.getChildren().addAll(name, details);
        HBox.setHgrow(textBox, Priority.ALWAYS);

        Button editButton = new Button("Edit");
        editButton.getStyleClass().add("mapping-edit-button");
        editButton.setOnAction(ignored -> onEditYellowBookParameter(parameter));

        Button deleteButton = createDeleteIconButton("Remove parameter");
        deleteButton.setOnAction(ignored -> onRemoveYellowBookParameter(parameter));

        row.getChildren().addAll(textBox, editButton, deleteButton);
        row.setOnMouseClicked(event -> {
            if (event.getTarget() instanceof Button) {
                return;
            }
            onEditYellowBookParameter(parameter);
        });
        return row;
    }

    private String buildYellowBookParameterDetails(DictionaryParameterData parameter) {
        List<String> parts = new ArrayList<>();
        if (!parameter.parameterFormat.isBlank()) {
            parts.add("Format: " + parameter.parameterFormat);
        }
        if (!parameter.paramValue.isBlank()) {
            parts.add("Value: " + parameter.paramValue);
        }
        parts.add(parameter.required ? "Required" : "Optional");
        return String.join(" | ", parts);
    }

    private Button createDeleteIconButton(String tooltipText) {
        Button button = new Button();
        button.getStyleClass().add("mapping-delete-button");
        button.setTooltip(new Tooltip(tooltipText));

        Image icon = null;
        try {
            icon = new Image(Objects.requireNonNull(getClass().getResourceAsStream(DELETE_ICON_PATH)));
        } catch (Exception ignored) {
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

        return button;
    }

    private boolean isSelectedYellowBookParameter(DictionaryParameterData parameter) {
        if (selectedYellowBookParameter == null || parameter == null) {
            return false;
        }

        return selectedYellowBookParameter.useCaseName.equals(parameter.useCaseName)
            && selectedYellowBookParameter.parameterName.equals(parameter.parameterName);
    }

    private void onEditYellowBookParameter(DictionaryParameterData parameter) {
        updateYellowBookEditor(parameter);
        renderYellowBook();
    }

    @FXML
    private void onClearYellowBookSelection() {
        updateYellowBookEditor(null);
        renderYellowBook();
    }

    @FXML
    private void onSaveYellowBookParameter() {
        if (selectedYellowBookParameter == null) {
            showErrorAlert("Missing Parameter", "Select a dictionary parameter before saving changes.");
            return;
        }

        DictionaryParameterData updated = new DictionaryParameterData(
            selectedYellowBookParameter.useCaseName,
            readText(yellowBookParameterNameField),
            readText(yellowBookParameterFormatField),
            yellowBookRequiredCheckBox != null && yellowBookRequiredCheckBox.isSelected(),
            readText(yellowBookParameterDescriptionField),
            readText(yellowBookParameterValueField)
        );

        if (updated.parameterName.isBlank() || updated.parameterFormat.isBlank()) {
            showErrorAlert("Invalid Parameter", "Parameter name and format are required.");
            return;
        }

        if (!selectedYellowBookParameter.parameterName.equals(updated.parameterName)) {
            sendYellowBookRenameMutation(selectedYellowBookParameter, updated);
            return;
        }

        sendYellowBookMutation(
            "Edit dictionary parameter " + selectedYellowBookParameter.parameterName
                + " in use case " + selectedYellowBookParameter.useCaseName
                + ". Set usecase_parameter_name to " + updated.parameterName
                + ", parameter_format to " + updated.parameterFormat
                + ", is_required to " + updated.required
                + ", Description to " + updated.description
                + ", and param_value to " + updated.paramValue + ".",
            "Parameter Updated"
        );
    }

    private void sendYellowBookRenameMutation(DictionaryParameterData original, DictionaryParameterData updated) {
        Integer contextUsecaseId = resolveDictionaryContextUseCaseId();
        if (contextUsecaseId == null || contextUsecaseId <= 0) {
            showErrorAlert("Missing Use Case Id", "Could not resolve a use case id for the dictionary request.");
            return;
        }

        if (yellowBookStatusLabel != null) {
            yellowBookStatusLabel.setText("Renaming dictionary parameter...");
        }

        String addMessage = "Add dictionary parameter " + updated.parameterName
            + " to use case " + updated.useCaseName
            + " with usecase_parameter_name " + updated.parameterName
            + ", parameter_format " + updated.parameterFormat
            + ", is_required " + updated.required
            + ", description " + updated.description
            + ", and param_value " + updated.paramValue + ".";
        String deleteMessage = "Delete dictionary parameter " + original.parameterName
            + " from use case " + original.useCaseName + ".";

        ApiService.getInstance()
            .sendDictionaryRequest(addMessage, contextUsecaseId, getSelectedUsecaseName(), chatSessionId)
            .thenAccept(addResponse -> {
                if (isDictionaryErrorResponse(addResponse)) {
                    Platform.runLater(() -> {
                        String error = extractDictionaryError(addResponse, "Could not add the new parameter name.");
                        yellowBookStatusLabel.setText(error);
                        showErrorAlert("Rename Failed", error);
                    });
                    return;
                }

                ApiService.getInstance()
                    .sendDictionaryRequest(deleteMessage, contextUsecaseId, getSelectedUsecaseName(), chatSessionId)
                    .thenAccept(deleteResponse -> Platform.runLater(() -> {
                        if (isDictionaryErrorResponse(deleteResponse)) {
                            String error = extractDictionaryError(deleteResponse, "Added the new name, but could not remove the old parameter.");
                            yellowBookStatusLabel.setText(error);
                            showErrorAlert("Rename Failed", error);
                            loadYellowBookDictionary();
                            return;
                        }

                        showInfoAlert("Parameter Renamed", "Renamed " + original.parameterName + " to " + updated.parameterName + ".");
                        updateYellowBookEditor(null);
                        loadYellowBookDictionary();
                    }))
                    .exceptionally(ex -> {
                        Platform.runLater(() -> showErrorAlert("Rename Failed", ex.getMessage()));
                        return null;
                    });
            })
            .exceptionally(ex -> {
                Platform.runLater(() -> showErrorAlert("Rename Failed", ex.getMessage()));
                return null;
            });
    }

    private boolean isDictionaryErrorResponse(JsonNode response) {
        return response == null || response.has("error");
    }

    private String extractDictionaryError(JsonNode response, String fallback) {
        if (response == null) {
            return "No response from n8n.";
        }
        return response.path("message").asText(fallback);
    }

    private void updateYellowBookEditor(DictionaryParameterData parameter) {
        selectedYellowBookParameter = parameter;

        boolean hasSelection = parameter != null;
        if (yellowBookSelectedParameterLabel != null) {
            yellowBookSelectedParameterLabel.setText(hasSelection ? parameter.useCaseName + " / " + parameter.parameterName : "None selected");
        }
        setText(yellowBookParameterNameField, hasSelection ? parameter.parameterName : "");
        setText(yellowBookParameterFormatField, hasSelection ? parameter.parameterFormat : "");
        setText(yellowBookParameterValueField, hasSelection ? parameter.paramValue : "");
        setText(yellowBookParameterDescriptionField, hasSelection ? parameter.description : "");
        if (yellowBookRequiredCheckBox != null) {
            yellowBookRequiredCheckBox.setSelected(hasSelection && parameter.required);
            yellowBookRequiredCheckBox.setDisable(!hasSelection);
        }
        setDisabled(yellowBookParameterNameField, !hasSelection);
        setDisabled(yellowBookParameterFormatField, !hasSelection);
        setDisabled(yellowBookParameterValueField, !hasSelection);
        setDisabled(yellowBookParameterDescriptionField, !hasSelection);
        if (yellowBookSaveButton != null) {
            yellowBookSaveButton.setDisable(!hasSelection);
        }
        if (yellowBookClearButton != null) {
            yellowBookClearButton.setDisable(!hasSelection);
        }
    }

    private String readText(TextField field) {
        return field == null || field.getText() == null ? "" : field.getText().trim();
    }

    private void setText(TextField field, String value) {
        if (field != null) {
            field.setText(value == null ? "" : value);
        }
    }

    private void setDisabled(Node node, boolean disabled) {
        if (node != null) {
            node.setDisable(disabled);
        }
    }

    private void onRemoveYellowBookParameter(DictionaryParameterData parameter) {
        if (!confirmDestructiveAction("Remove Parameter", "Remove " + parameter.parameterName + " from " + parameter.useCaseName + "?")) {
            return;
        }

        sendYellowBookMutation(
            "Delete dictionary parameter " + parameter.parameterName + " from use case " + parameter.useCaseName + ".",
            "Parameter Removed"
        );
    }

    private void onRemoveYellowBookUseCase(String useCaseName) {
        if (!confirmDestructiveAction("Remove Use Case", "Remove all dictionary parameters for " + useCaseName + "?")) {
            return;
        }

        sendYellowBookMutation("Delete use case " + useCaseName + " from the dictionary.", "Use Case Removed");
    }

    private boolean confirmDestructiveAction(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    private void sendYellowBookMutation(String message, String successTitle) {
        Integer contextUsecaseId = resolveDictionaryContextUseCaseId();
        if (contextUsecaseId == null || contextUsecaseId <= 0) {
            showErrorAlert("Missing Use Case Id", "Could not resolve a use case id for the dictionary request.");
            return;
        }

        if (yellowBookStatusLabel != null) {
            yellowBookStatusLabel.setText("Sending dictionary update...");
        }

        ApiService.getInstance()
            .sendDictionaryRequest(message, contextUsecaseId, getSelectedUsecaseName(), chatSessionId)
            .thenAccept(response -> Platform.runLater(() -> {
                if (response == null || response.has("error")) {
                    String error = response == null ? "No response from n8n." : response.path("message").asText("Dictionary update failed.");
                    showErrorAlert("Dictionary Update Failed", error);
                    yellowBookStatusLabel.setText(error);
                    return;
                }

                String reply = response.path("reply").asText("Dictionary update completed.");
                showInfoAlert(successTitle, reply);
                updateYellowBookEditor(null);
                loadYellowBookDictionary();
            }))
            .exceptionally(ex -> {
                Platform.runLater(() -> showErrorAlert("Dictionary Update Failed", ex.getMessage()));
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
            rule.minValue = readInt(node, "minvalue", "min", 0);
            rule.maxValue = readInt(node, "maxvalue", "max", 0);
            rule.minPulses = readInt(node, "minpulses", "", 0);
            rule.maxPulses = readInt(node, "maxpulses", "", 0);
            rule.minIntensity = readInt(node, "minintensity", "", 0);
            rule.maxIntensity = readInt(node, "maxintensity", "", 0);
            rule.minDuration = readInt(node, "minduration", "", 0);
            rule.maxDuration = readInt(node, "maxduration", "", 0);
            rule.minInterval = readInt(node, "mininterval", "", 0);
            rule.maxInterval = readInt(node, "maxinterval", "", 0);
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
            if (selectedMappingForEdit != null && selectedMappingForEdit.mappingId == rule.mappingId) {
                card.getStyleClass().add("mapping-card-selected");
            }

            Label title = new Label(rule.rangeLabel);
            title.getStyleClass().add("mapping-card-title");

            HBox titleRow = new HBox(8);
            titleRow.setAlignment(Pos.CENTER_LEFT);

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            Button selectButton = createSelectMappingButton(rule);
            Button deleteButton = createDeleteMappingButton(rule);
            titleRow.getChildren().addAll(title, spacer, selectButton, deleteButton);

            Label pulses = new Label("Pulse count: " + rule.pulseLabel);
            Label intensity = new Label("Intensity: " + rule.intensityLabel);
            Label duration = new Label("Duration: " + rule.durationLabel);
            Label interval = new Label("Interval: " + rule.intervalLabel);

            card.getChildren().addAll(titleRow, pulses, intensity, duration, interval);
            card.setOnMouseClicked(event -> {
                if (event.getTarget() instanceof Button) {
                    return;
                }
                selectMappingForEdit(rule);
            });
            mappingsFlowPane.getChildren().add(card);
        }
    }

    private Button createSelectMappingButton(RuleCardData rule) {
        Button button = new Button("Edit");
        button.getStyleClass().add("mapping-edit-button");
        button.setTooltip(new Tooltip("Edit mapping"));
        button.setOnMouseClicked(event -> event.consume());
        button.setOnAction(ignored -> selectMappingForEdit(rule));
        return button;
    }

    private void selectMappingForEdit(RuleCardData rule) {
        if (rule == null) {
            return;
        }

        updateSelectedMappingForEdit(rule);
        populateRuleBuilderFromMapping(rule);
        renderMappingsForUseCase(state.getSelectedUseCase());
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
        button.setOnMouseClicked(event -> event.consume());
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

        String expandedMessage = expandChatCommand(message);
        addChatMessage(message, true);
        chatInputField.clear();

        // Keys must match webhook body fields used by n8n.
        Map<String, Object> payload = new HashMap<>();
        Integer useCaseId = state.getSelectedUseCaseId();

        payload.put("message", expandedMessage);
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
         WebView bubble = createChatBubbleView(text, userMessage);

         HBox row = new HBox(bubble);
         row.getStyleClass().add("chat-message-row");
         row.setFillHeight(false);
         row.setAlignment(userMessage ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

         chatHistoryBox.getChildren().add(row);
     }

    private static final Pattern MARKDOWN_INLINE = Pattern.compile("\\[([^]]+)]\\(([^)]+)\\)|\\*\\*(.+?)\\*\\*|\\*(.+?)\\*|`([^`]+)`");
    private static final double CHAT_BUBBLE_VIEWPORT_WIDTH = 580.0;
    private static final double CHAT_BUBBLE_MAX_WIDTH = 560.0;
    private static final double CHAT_BUBBLE_MIN_HEIGHT = 44.0;

    private WebView createChatBubbleView(String text, boolean userMessage) {
         WebView webView = new WebView();
         webView.setContextMenuEnabled(true);
         webView.setFocusTraversable(false);
         webView.setMinWidth(CHAT_BUBBLE_VIEWPORT_WIDTH);
         webView.setPrefWidth(CHAT_BUBBLE_VIEWPORT_WIDTH);
         webView.setMaxWidth(CHAT_BUBBLE_VIEWPORT_WIDTH);
         webView.setMinHeight(CHAT_BUBBLE_MIN_HEIGHT);
         webView.setPrefHeight(CHAT_BUBBLE_MIN_HEIGHT);
         webView.setMaxHeight(CHAT_BUBBLE_MIN_HEIGHT);
         webView.setStyle("-fx-background-color: transparent;");
         webView.setPageFill(Color.TRANSPARENT);

         // Enable mouse wheel scrolling to pass through to parent ScrollPane
         webView.addEventFilter(javafx.scene.input.ScrollEvent.SCROLL, event -> {
             javafx.scene.input.ScrollEvent scrollEvent = (javafx.scene.input.ScrollEvent) event;
             if (scrollEvent.isControlDown() || scrollEvent.isShiftDown()) {
                 event.consume();
             } else {
                 event.consume();
                 // Re-fire the event on the scroll pane to let it handle scrolling
                 javafx.scene.Parent parent = webView.getParent();
                 if (parent != null) {
                     parent.fireEvent(scrollEvent.copyFor(parent, parent));
                 }
             }
         });

         webView.getEngine().loadContent(buildChatBubbleHtml(text, userMessage), "text/html");
         webView.getEngine().getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
             if (newState == Worker.State.SUCCEEDED) {
                 Platform.runLater(() -> adjustChatBubbleHeight(webView, false));
             }
         });

         return webView;
     }

    private void adjustChatBubbleHeight(WebView webView, boolean secondPass) {
        try {
            Object metrics = webView.getEngine().executeScript("(() => {"
                + "const bubble = document.querySelector('.bubble');"
                + "if (!bubble) return JSON.stringify({height: 0});"
                + "const height = bubble.getBoundingClientRect().height;"
                + "return JSON.stringify({height: Math.ceil(height)});"
                + "})()");

            if (metrics instanceof String metricJson && !metricJson.isBlank()) {
                JsonNode metricNode = ApiService.getInstance().getMapper().readTree(metricJson);
                double measuredHeight = metricNode.path("height").asDouble(0.0);

                double targetHeight = Math.max(CHAT_BUBBLE_MIN_HEIGHT, measuredHeight + 6);

                webView.setMinHeight(targetHeight);
                webView.setPrefHeight(targetHeight);
                webView.setMaxHeight(targetHeight);

                // Re-measure once after WebView paints; fonts and wrapping can settle one pulse later.
                if (!secondPass) {
                    Platform.runLater(() -> adjustChatBubbleHeight(webView, true));
                }
            }
        } catch (Exception ignored) {
            webView.setPrefHeight(120);
        }
    }

    private String buildChatBubbleHtml(String markdown, boolean userMessage) {
        String renderedMarkdown = markdownToHtml(markdown);
        String bubbleBg = userMessage ? "#2563eb" : "#f8fafc";
        String bubbleText = userMessage ? "#ffffff" : "#111827";
        String bubbleBorder = userMessage ? "#1d4ed8" : "#d1d5db";
        String codeBg = userMessage ? "rgba(255,255,255,0.16)" : "#e5e7eb";
        String linkColor = userMessage ? "#dbeafe" : "#1d4ed8";
        String quoteColor = userMessage ? "#e5e7eb" : "#4b5563";
        String horizontalAlign = userMessage ? "flex-end" : "flex-start";

        return """
            <html>
              <head>
                <meta charset="UTF-8">
                <style>
                  html, body { margin: 0; padding: 0; background: transparent; overflow: hidden; }
                  body {
                    font-family: 'Segoe UI', 'Segoe UI Emoji', 'Segoe UI Symbol', sans-serif;
                    box-sizing: border-box;
                    display: flex;
                    justify-content: %s;
                    align-items: flex-start;
                    width: 580px;
                    min-height: 1px;
                  }
                  .bubble {
                    box-sizing: border-box;
                    display: inline-block;
                    width: fit-content;
                    min-width: 48px;
                    max-width: 560px;
                    padding: 10px 12px;
                    border-radius: 12px;
                    background: %s;
                    color: %s;
                    border: 1px solid %s;
                    white-space: normal;
                    overflow-wrap: anywhere;
                    word-break: break-word;
                    user-select: text;
                    -webkit-user-select: text;
                  }
                  .bubble p { margin: 0 0 8px 0; }
                  .bubble p:last-child { margin-bottom: 0; }
                  .bubble pre {
                    margin: 8px 0;
                    padding: 10px;
                    border-radius: 8px;
                    background: %s;
                    color: %s;
                    overflow-x: auto;
                    white-space: pre-wrap;
                    word-break: break-word;
                    font-family: Consolas, 'Courier New', 'Segoe UI Emoji', monospace;
                    font-size: 13px;
                  }
                  .bubble code {
                    font-family: Consolas, 'Courier New', 'Segoe UI Emoji', monospace;
                    background: %s;
                    padding: 1px 4px;
                    border-radius: 4px;
                  }
                  .bubble a { color: %s; }
                  .bubble ul, .bubble ol { margin: 0 0 8px 22px; padding: 0; }
                  .bubble li { margin: 0 0 4px 0; }
                  .bubble blockquote {
                    margin: 8px 0;
                    padding: 0 0 0 10px;
                    border-left: 3px solid rgba(156, 163, 175, 0.8);
                    color: %s;
                  }
                </style>
              </head>
              <body>
                <div class="bubble">%s</div>
              </body>
            </html>
            """.formatted(horizontalAlign, bubbleBg, bubbleText, bubbleBorder, codeBg, bubbleText, codeBg, linkColor, quoteColor, renderedMarkdown);
    }

    private String markdownToHtml(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }

        String normalized = markdown.replace("\r\n", "\n").replace('\r', '\n');
        StringBuilder html = new StringBuilder();
        StringBuilder paragraph = new StringBuilder();
        boolean inCodeBlock = false;
        boolean inUl = false;
        boolean inOl = false;

        String[] lines = normalized.split("\n", -1);
        for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
            String line = lines[lineIndex];
            String trimmed = line.trim();

            if (trimmed.startsWith("```")) {
                if (paragraph.length() > 0) {
                    html.append("<p>").append(renderInlineMarkdown(paragraph.toString())).append("</p>");
                    paragraph.setLength(0);
                }
                if (inUl) {
                    html.append("</ul>");
                    inUl = false;
                }
                if (inOl) {
                    html.append("</ol>");
                    inOl = false;
                }
                if (inCodeBlock) {
                    html.append("</code></pre>");
                    inCodeBlock = false;
                } else {
                    html.append("<pre><code>");
                    inCodeBlock = true;
                }
                continue;
            }

            if (inCodeBlock) {
                html.append(escapeHtml(line)).append("\n");
                continue;
            }

            if (trimmed.isEmpty()) {
                if (paragraph.length() > 0) {
                    html.append("<p>").append(renderInlineMarkdown(paragraph.toString())).append("</p>");
                    paragraph.setLength(0);
                }

                String nextNonEmpty = null;
                for (int nextIndex = lineIndex + 1; nextIndex < lines.length; nextIndex++) {
                    String candidate = lines[nextIndex].trim();
                    if (!candidate.isEmpty()) {
                        nextNonEmpty = candidate;
                        break;
                    }
                }

                boolean nextIsUl = nextNonEmpty != null && nextNonEmpty.matches("^(?:[-*+])\\s+.+");
                boolean nextIsOl = nextNonEmpty != null && nextNonEmpty.matches("^\\d+\\.\\s+.+");

                if (inUl && !nextIsUl) {
                    html.append("</ul>");
                    inUl = false;
                }
                if (inOl && !nextIsOl) {
                    html.append("</ol>");
                    inOl = false;
                }
                continue;
            }

            if (trimmed.matches("^(?:[-*+])\\s+.+")) {
                if (paragraph.length() > 0) {
                    html.append("<p>").append(renderInlineMarkdown(paragraph.toString())).append("</p>");
                    paragraph.setLength(0);
                }
                if (inOl) {
                    html.append("</ol>");
                    inOl = false;
                }
                if (!inUl) {
                    html.append("<ul>");
                    inUl = true;
                }
                html.append("<li>").append(renderInlineMarkdown(trimmed.substring(1).trim())).append("</li>");
                continue;
            }

            if (trimmed.matches("^\\d+\\.\\s+.+")) {
                if (paragraph.length() > 0) {
                    html.append("<p>").append(renderInlineMarkdown(paragraph.toString())).append("</p>");
                    paragraph.setLength(0);
                }
                if (inUl) {
                    html.append("</ul>");
                    inUl = false;
                }
                if (!inOl) {
                    html.append("<ol>");
                    inOl = true;
                }
                int dotIndex = trimmed.indexOf('.');
                String explicitNumber = trimmed.substring(0, dotIndex).trim();
                String itemContent = trimmed.substring(dotIndex + 1).trim();
                html.append("<li value=\"")
                    .append(explicitNumber)
                    .append("\">")
                    .append(renderInlineMarkdown(itemContent))
                    .append("</li>");
                continue;
            }

            if (inUl) {
                html.append("</ul>");
                inUl = false;
            }
            if (inOl) {
                html.append("</ol>");
                inOl = false;
            }

            if (paragraph.length() > 0) {
                paragraph.append('\n');
            }
            paragraph.append(trimmed);
        }

        if (paragraph.length() > 0) {
            html.append("<p>").append(renderInlineMarkdown(paragraph.toString())).append("</p>");
        }
        if (inUl) {
            html.append("</ul>");
        }
        if (inOl) {
            html.append("</ol>");
        }
        if (inCodeBlock) {
            html.append("</code></pre>");
        }

        return html.toString();
    }

    private String renderInlineMarkdown(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        StringBuilder html = new StringBuilder();

        String[] lines = text.split("\\n", -1);
        for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
            String line = lines[lineIndex];
            Matcher matcher = MARKDOWN_INLINE.matcher(line);
            int lastIndex = 0;

            while (matcher.find()) {
                html.append(escapeHtml(line.substring(lastIndex, matcher.start())));

                if (matcher.group(1) != null && matcher.group(2) != null) {
                    String label = escapeHtml(matcher.group(1));
                    String href = sanitizeHref(matcher.group(2));
                    if (href.isBlank()) {
                        html.append(escapeHtml(matcher.group(0)));
                    } else {
                        html.append("<a href=\"")
                            .append(escapeHtml(href))
                            .append("\" target=\"_blank\" rel=\"noopener noreferrer\">")
                            .append(label)
                            .append("</a>");
                    }
                } else if (matcher.group(3) != null) {
                    html.append("<strong>").append(escapeHtml(matcher.group(3))).append("</strong>");
                } else if (matcher.group(4) != null) {
                    html.append("<em>").append(escapeHtml(matcher.group(4))).append("</em>");
                } else if (matcher.group(5) != null) {
                    html.append("<code>").append(escapeHtml(matcher.group(5))).append("</code>");
                }

                lastIndex = matcher.end();
            }

            html.append(escapeHtml(line.substring(lastIndex)));
            if (lineIndex < lines.length - 1) {
                html.append("<br>");
            }
        }
        return html.toString();
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private String sanitizeHref(String href) {
        if (href == null) {
            return "";
        }

        String trimmed = href.trim();
        if (trimmed.isEmpty()) {
            return "";
        }

        try {
            java.net.URI uri = java.net.URI.create(trimmed);
            String scheme = uri.getScheme();
            if (scheme == null || scheme.isBlank()) {
                return trimmed;
            }
            if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme) || "mailto".equalsIgnoreCase(scheme)) {
                return trimmed;
            }
        } catch (Exception ignored) {
            return "";
        }

        return "";
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
        User user = state.getSelectedUsers();
        String useCase = state.getSelectedUseCase();
        String range = state.getSelectedTimeRange();

        setGraphLoadingVisible(true);
        setMiniGraphLoadingVisible(true);

        if (user == null || useCase == null || useCase.isBlank() || range == null) {
            renderGraphPlaceholder("Select a use case and Users to render graph data.");
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
            showErrorAlert("Export Failed", "Could not parse export payload from chart.");
        }
    }

    private void saveGraphPngFromDataUrl(String dataUrl, String suggestedFilename) {
        if (dataUrl == null || !dataUrl.startsWith("data:image/png;base64,")) {
            showErrorAlert("Export Failed", "Received invalid image data from chart export.");
            return;
        }

        String base64Payload = dataUrl.substring("data:image/png;base64,".length());
        byte[] imageBytes;
        try {
            imageBytes = Base64.getDecoder().decode(base64Payload);
        } catch (IllegalArgumentException ex) {
            showErrorAlert("Export Failed", "Could not decode generated PNG data.");
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
            showInfoAlert("Export Successful", "Saved to:\n" + selectedFile.getAbsolutePath());
        } catch (IOException ex) {
            showErrorAlert("Export Failed", "Could not save PNG file: " + ex.getMessage());
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

    private User findUserById(Integer userId) {
        if (userId == null || userId <= 0) {
            return null;
        }
        return users.stream().filter(user -> user.getUserID() == userId).findFirst().orElse(null);
    }

    private Button createEditUserButton() {
        Button button = new Button();
        button.getStyleClass().add("sidebar-user-settings-button");
        button.setTooltip(new Tooltip("Edit user name"));

        Image icon = null;
        try {
            icon = new Image(Objects.requireNonNull(getClass().getResourceAsStream(SETTINGS_ICON_PATH)));
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
            button.setText("Edit");
        }

        return button;
    }

    private void onEditUserRequested(User user) {
        if (user == null) {
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Edit User Name");
        dialog.setHeaderText("Update name for user " + user.getUserID() + ".");

        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        TextField firstNameField = new TextField(user.getFName());
        firstNameField.setPromptText("First Name");
        firstNameField.setPrefWidth(260);
        TextField lastNameField = new TextField(user.getLName());
        lastNameField.setPromptText("Last Name");
        lastNameField.setPrefWidth(260);

        VBox content = new VBox(10,
            new Label("First Name"),
            firstNameField,
            new Label("Last Name"),
            lastNameField
        );
        content.setPadding(new Insets(12, 12, 4, 12));
        content.setPrefWidth(320);
        dialog.getDialogPane().setContent(content);

        Node saveButtonNode = dialog.getDialogPane().lookupButton(saveButtonType);
        if (!(saveButtonNode instanceof Button saveButton)) {
            return;
        }
        Runnable updateSaveState = () -> {
            String fName = firstNameField.getText() == null ? "" : firstNameField.getText().trim();
            String lName = lastNameField.getText() == null ? "" : lastNameField.getText().trim();
            saveButton.setDisable(fName.isBlank() || lName.isBlank());
        };
        firstNameField.textProperty().addListener((obs, oldValue, newValue) -> updateSaveState.run());
        lastNameField.textProperty().addListener((obs, oldValue, newValue) -> updateSaveState.run());
        updateSaveState.run();

        saveButton.setDefaultButton(true);
        Runnable fireSaveIfValid = () -> {
            if (!saveButton.isDisable()) {
                saveButton.fire();
            }
        };
        firstNameField.setOnAction(ignored -> fireSaveIfValid.run());
        lastNameField.setOnAction(ignored -> fireSaveIfValid.run());

        Platform.runLater(firstNameField::requestFocus);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isEmpty() || result.get() != saveButtonType) {
            return;
        }

        String newFirstName = firstNameField.getText() == null ? "" : firstNameField.getText().trim();
        String newLastName = lastNameField.getText() == null ? "" : lastNameField.getText().trim();
        if (newFirstName.isBlank() || newLastName.isBlank()) {
            showErrorAlert("Invalid Name", "First and last name cannot be empty.");
            return;
        }

        ApiService.getInstance().setUserName(user.getUserID(), newFirstName, newLastName)
            .thenAccept(success -> Platform.runLater(() -> {
                if (!success) {
                    showErrorAlert("Update Failed", "Could not update the user name. Please try again.");
                    return;
                }

                showInfoAlert("User Updated", "Updated user " + user.getUserID() + " to " + newFirstName + " " + newLastName + ".");
                loadUserss();
            }))
            .exceptionally(ex -> {
                showErrorAlert("Update Failed", "Failed to update user name: " + ex.getMessage());
                return null;
            });
    }

    private void setSidebarMode(boolean useCasesMode) {
        useCasesNavButton.setSelected(useCasesMode);
        UsersNavButton.setSelected(!useCasesMode);

        useCasesSidebarPane.setVisible(useCasesMode);
        useCasesSidebarPane.setManaged(useCasesMode);

        UsersSidebarPane.setVisible(!useCasesMode);
        UsersSidebarPane.setManaged(!useCasesMode);
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

    private int readInt(JsonNode node, String primaryField, String fallbackField, int fallback) {
        if (node == null || !node.isObject()) {
            return fallback;
        }
        if (primaryField != null && !primaryField.isBlank() && node.has(primaryField)) {
            return node.path(primaryField).asInt(fallback);
        }
        if (fallbackField != null && !fallbackField.isBlank() && node.has(fallbackField)) {
            return node.path(fallbackField).asInt(fallback);
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
        int minValue;
        int maxValue;
        int minPulses;
        int maxPulses;
        int minIntensity;
        int maxIntensity;
        int minDuration;
        int maxDuration;
        int minInterval;
        int maxInterval;
    }

    private static class LoggingIntervalDraft {
        final int amount;
        final String baseUnit;

        private LoggingIntervalDraft(int amount, String baseUnit) {
            this.amount = amount;
            this.baseUnit = baseUnit;
        }
    }

    private static class DictionaryParameterData {
        final String useCaseName;
        final String parameterName;
        final String parameterFormat;
        final boolean required;
        final String description;
        final String paramValue;

        private DictionaryParameterData(String useCaseName, String parameterName, String parameterFormat,
                                        boolean required, String description, String paramValue) {
            this.useCaseName = useCaseName == null ? "" : useCaseName.trim();
            this.parameterName = parameterName == null ? "" : parameterName.trim();
            this.parameterFormat = parameterFormat == null ? "" : parameterFormat.trim();
            this.required = required;
            this.description = description == null ? "" : description.trim();
            this.paramValue = paramValue == null ? "" : paramValue.trim();
        }
    }


}

