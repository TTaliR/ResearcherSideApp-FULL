package com.example.demo.controller;

import com.example.demo.controller.state.DashboardState;
import com.example.demo.factory.ChatBubbleFactory;
import com.example.demo.factory.MappingUiFactory;
import com.example.demo.factory.SharedUiFactory;
import com.example.demo.factory.YellowBookUiFactory;
import com.example.demo.model.Schedule;
import com.example.demo.model.ScheduleApiResponse;
import com.example.demo.model.SensorRuleConfig;
import com.example.demo.model.User;
import com.example.demo.model.UserUseCaseMapping;
import com.example.demo.model.RuleCardData;
import com.example.demo.model.DictionaryParameterData;
import com.example.demo.model.LoggingIntervalDraft;
import com.example.demo.model.ChatCommandOption;
import com.example.demo.model.UseCase;
import com.example.demo.parser.MappingConfigParser;
import com.example.demo.parser.YellowBookParser;
import com.example.demo.service.ApiService;
import com.example.demo.service.ExportService;
import com.example.demo.util.AlertUtils;
import com.example.demo.util.FormatUtils;
import com.fasterxml.jackson.databind.JsonNode;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.scene.web.WebEvent;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class DashboardController {
    private static final List<String> DEFAULT_USE_CASES = List.of("HeartRate", "MoonAzimuth", "SunAzimuth", "Pollution");
    private static final String DEFAULT_CHAT_COMMAND_PREFIX = "$";
    private static final String FEEDBACK_GRAPH_TEMPLATE_PATH = "/com/example/demo/view/templates/feedbackGraph.html";
    private static final String MINI_GRAPH_TEMPLATE_PATH = "/com/example/demo/view/templates/miniFeedbackGraph.html";
    private static final Pattern LOG_INTERVAL_PATTERN = Pattern.compile("^\\d+(\\.\\d+)?\\s+(second|seconds|minute|minutes|hour|hours|day|days)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SCHEDULE_RELATIVE_NEXT_CHECK_PATTERN = Pattern.compile(
        "^\\s*(?:in\\s+)?(-?\\d+)\\s+(second|seconds|minute|minutes|hour|hours|day|days|week|weeks|month|months)\\s*$",
        Pattern.CASE_INSENSITIVE
    );
    private static final List<DateTimeFormatter> SCHEDULE_NEXT_CHECK_FORMATTERS = List.of(
        DateTimeFormatter.ISO_INSTANT,
        DateTimeFormatter.ISO_OFFSET_DATE_TIME,
        DateTimeFormatter.ISO_ZONED_DATE_TIME,
        DateTimeFormatter.ISO_LOCAL_DATE_TIME,
        DateTimeFormatter.ISO_LOCAL_DATE,
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"),
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"),
        DateTimeFormatter.ofPattern("dd/MM/yyyy"),
        DateTimeFormatter.ofPattern("yyyy-M-d HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-M-d HH:mm"),
        DateTimeFormatter.ofPattern("yyyy-M-d"),
        DateTimeFormatter.ofPattern("d/M/yyyy HH:mm:ss"),
        DateTimeFormatter.ofPattern("d/M/yyyy HH:mm"),
        DateTimeFormatter.ofPattern("d/M/yyyy"),
        DateTimeFormatter.ofPattern("M/d/yyyy HH:mm:ss"),
        DateTimeFormatter.ofPattern("M/d/yyyy HH:mm"),
        DateTimeFormatter.ofPattern("M/d/yyyy"),
        DateTimeFormatter.ofPattern("d-M-yyyy HH:mm:ss"),
        DateTimeFormatter.ofPattern("d-M-yyyy HH:mm"),
        DateTimeFormatter.ofPattern("d-M-yyyy"),
        DateTimeFormatter.ofPattern("d.M.yyyy HH:mm:ss"),
        DateTimeFormatter.ofPattern("d.M.yyyy HH:mm"),
        DateTimeFormatter.ofPattern("d.M.yyyy")
    );
    private static final List<ChatCommandOption> CHAT_COMMAND_OPTIONS = createChatCommandOptions();
    private static final Map<String, ChatCommandOption> CHAT_COMMAND_OPTIONS_BY_COMMAND = createChatCommandOptionMap();

    @FXML
    private LeftSidebarController leftSidebarController;
    @FXML
    private TopBarController topBarController;

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
    private Popup chatCommandPopup;
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
    private FlowPane schedulesFlowPane;
    @FXML
    private Label schedulesEmptyLabel;
    @FXML
    private TextField scheduleIdField;
    @FXML
    private TextField userIdField;
    @FXML
    private Spinner<Integer> intervalDaysSpinner;
    @FXML
    private ComboBox<String> measureTypeComboBox;
    @FXML
    private Spinner<Integer> triggerPercentageSpinner;
    @FXML
    private Button saveScheduleButton;
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
    private final Map<String, UseCase> useCaseRegistry = new HashMap<>();
    private final Map<String, List<DictionaryParameterData>> yellowBookEntries = new LinkedHashMap<>();
    private final List<RuleCardData> allRules = new ArrayList<>();
    private final ObservableList<Schedule> schedules = FXCollections.observableArrayList();
    private final String chatSessionId = UUID.randomUUID().toString();
    private String activeMonitoringTypeKey = "";
    private RuleCardData selectedMappingForEdit;
    private Schedule selectedScheduleForEdit;
    private DictionaryParameterData selectedYellowBookParameter;
    private boolean showingActiveSchedulesOnly;
    private boolean showingActiveMappingsOnly = true;

    private enum MappingChangeScope {
        ALL_USERS,
        CURRENT_USER_ONLY,
        CANCELLED
    }

    private WebView graphWebView;
    private WebView miniGraphWebView;
    private StackPane graphLoadingOverlay;
    private StackPane miniGraphLoadingOverlay;
    private JsonNode latestGraphData;
    private final PauseTransition graphUpdateDebounce = new PauseTransition(Duration.millis(250));
    private static final String GRAPH_EXPORT_ALERT_PREFIX = "__GRAPH_EXPORT__";

    @FXML
    private void initialize() {
        initializeSubcontrollers();
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

    private void initializeSubcontrollers() {
        // Setup sidebar callbacks
        leftSidebarController.setOnUseCaseSelected(newValue -> {
            state.setSelectedUseCase(newValue);
        });

        leftSidebarController.setOnUserSelected(newValue -> {
            if (newValue != null) {
                topBarController.setSelectedUser(newValue);
                updateUserAssignmentPanel(newValue);
                if (newValue.getUsecaseName() != null && !newValue.getUsecaseName().isBlank()) {
                    String userUseCase = resolveUseCaseDisplayName(newValue.getUsecaseName());
                    state.setSelectedUseCase(userUseCase);
                }
            }
        });

        leftSidebarController.setOnAddUseCaseRequested(this::onAddUseCaseRequested);
        leftSidebarController.setOnAssignUserUseCaseRequested(this::onAssignUserUseCase);
        leftSidebarController.setOnEditUserRequested(this::onEditUserRequested);

        // Setup topbar callbacks
        topBarController.setOnUserSelected(newValue -> {
            if (newValue == null) {
                boolean allUsersSelected = topBarController.isAllUsersSelected();
                state.setSelectedUsers(null);
                contextUsersLabel.setText(allUsersSelected ? "All Users" : "-");
                leftSidebarController.getUsersSidebarList().getSelectionModel().clearSelection();
                syncMonitoringEditorToSelectedUser(null);
                selectScheduleForEdit(null);
                if (userIdField != null) {
                    userIdField.clear();
                }
                refreshRuleSummary();
                renderMappingsForUseCase(state.getSelectedUseCase());
                scheduleGraphUpdate();
                return;
            }
            state.setSelectedUsers(newValue);
            contextUsersLabel.setText(formatUser(newValue));
            if (leftSidebarController.getUsersSidebarList().getSelectionModel().getSelectedItem() != newValue) {
                leftSidebarController.selectUser(newValue);
            }
            syncMonitoringEditorToSelectedUser(newValue);
            if (newValue.getUsecaseName() != null && !newValue.getUsecaseName().isBlank()) {
                String userUseCase = resolveUseCaseDisplayName(newValue.getUsecaseName());
                state.setSelectedUseCase(userUseCase);
            }
            refreshRuleSummary();
            renderMappingsForUseCase(state.getSelectedUseCase());
            scheduleGraphUpdate();
        });

        topBarController.setOnGraphUpdateRequested(this::scheduleGraphUpdate);
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
            if (scheduleIdField != null) {
                scheduleIdField.setDisable(true);
                scheduleIdField.setEditable(false);
            }
            if (userIdField != null) {
                userIdField.setDisable(true);
                userIdField.setEditable(false);
            }

            if (measureTypeComboBox != null) {
                measureTypeComboBox.getItems().setAll("average", "median", "mode");
                measureTypeComboBox.setValue("average");
            }
            setupPositiveIntegerSpinner(intervalDaysSpinner, 1);
            setupTriggerPercentageSpinner();
            selectScheduleForEdit(null);
            renderScheduleCards();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setupTriggerPercentageSpinner() {
        if (triggerPercentageSpinner == null) {
            return;
        }

        triggerPercentageSpinner.setValueFactory(
            new SpinnerValueFactory.IntegerSpinnerValueFactory(Integer.MIN_VALUE, Integer.MAX_VALUE, 1, 1)
        );
        triggerPercentageSpinner.setEditable(true);
        triggerPercentageSpinner.getEditor().setTextFormatter(new TextFormatter<>(change -> {
            String newText = change.getControlNewText();
            return newText.matches("-?\\d*") ? change : null;
        }));
    }

    private Instant getScheduleNextCheckSortInstant(Schedule schedule) {
        if (schedule == null) {
            return null;
        }

        return parseScheduleNextCheckInstant(schedule.getNextRunDate())
            .or(() -> parseScheduleNextCheckInstant(schedule.getNextCheck()))
            .orElse(null);
    }

    private Optional<Instant> parseScheduleNextCheckInstant(String value) {
        if (value == null || value.trim().isEmpty()) {
            return Optional.empty();
        }

        String trimmed = value.trim();
        Optional<Instant> relativeInstant = parseRelativeScheduleNextCheckInstant(trimmed);
        if (relativeInstant.isPresent()) {
            return relativeInstant;
        }

        for (DateTimeFormatter formatter : SCHEDULE_NEXT_CHECK_FORMATTERS) {
            Optional<Instant> parsed = parseScheduleNextCheckInstant(trimmed, formatter);
            if (parsed.isPresent()) {
                return parsed;
            }
        }

        return Optional.empty();
    }

    private Optional<Instant> parseRelativeScheduleNextCheckInstant(String value) {
        Matcher matcher = SCHEDULE_RELATIVE_NEXT_CHECK_PATTERN.matcher(value);
        if (!matcher.matches()) {
            return Optional.empty();
        }

        long amount;
        try {
            amount = Long.parseLong(matcher.group(1));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }

        String unit = matcher.group(2).toLowerCase(Locale.ROOT);
        long seconds;
        if (unit.startsWith("second")) {
            seconds = amount;
        } else if (unit.startsWith("minute")) {
            seconds = amount * 60L;
        } else if (unit.startsWith("hour")) {
            seconds = amount * 60L * 60L;
        } else if (unit.startsWith("day")) {
            seconds = amount * 24L * 60L * 60L;
        } else if (unit.startsWith("week")) {
            seconds = amount * 7L * 24L * 60L * 60L;
        } else {
            seconds = amount * 30L * 24L * 60L * 60L;
        }

        return Optional.of(Instant.EPOCH.plusSeconds(seconds));
    }

    private Optional<Instant> parseScheduleNextCheckInstant(String value, DateTimeFormatter formatter) {
        try {
            if (formatter == DateTimeFormatter.ISO_INSTANT) {
                return Optional.of(Instant.from(formatter.parse(value)));
            }
            if (formatter == DateTimeFormatter.ISO_OFFSET_DATE_TIME) {
                return Optional.of(OffsetDateTime.parse(value, formatter).toInstant());
            }
            if (formatter == DateTimeFormatter.ISO_ZONED_DATE_TIME) {
                return Optional.of(ZonedDateTime.parse(value, formatter).toInstant());
            }
            if (formatter == DateTimeFormatter.ISO_LOCAL_DATE_TIME || value.contains(":")) {
                return Optional.of(LocalDateTime.parse(value, formatter).atZone(ZoneId.systemDefault()).toInstant());
            }
            return Optional.of(LocalDate.parse(value, formatter).atStartOfDay(ZoneId.systemDefault()).toInstant());
        } catch (DateTimeParseException e) {
            return Optional.empty();
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
        if (intervalDaysSpinner != null && intervalDaysSpinner.getValueFactory() != null) {
            intervalDaysSpinner.getValueFactory().setValue(Math.max(1, schedule.getIntervalDays()));
        }
        if (measureTypeComboBox != null) {
            String measureType = schedule.getMeasureType() == null ? "" : schedule.getMeasureType().trim().toLowerCase(Locale.ROOT);
            measureTypeComboBox.setValue(List.of("average", "median", "mode").contains(measureType) ? measureType : "average");
        }
        if (triggerPercentageSpinner != null && triggerPercentageSpinner.getValueFactory() != null) {
            triggerPercentageSpinner.getValueFactory().setValue((int) Math.round(schedule.getTriggerPercentage()));
        }
    }

    private void updateSchedulesTable(ScheduleApiResponse resp) {
        if (resp == null) return;
        schedules.setAll(resp.getSchedules());
        if (selectedScheduleForEdit != null && schedules.stream().noneMatch(this::isSelectedSchedule)) {
            selectScheduleForEdit(null);
        }
        renderScheduleCards();
        updateScheduleStatus(resp);
    }

    private void updateScheduleStatus(ScheduleApiResponse resp) {
        if (resp == null || scheduleStatusLabel == null) {
            return;
        }
        scheduleStatusLabel.setText(resp.getReply() != null && !resp.getReply().isEmpty() ? resp.getReply() : (resp.isSuccess() ? "OK" : resp.getErrorMessage()));
    }

    private void renderScheduleCards() {
        if (schedulesFlowPane == null || schedulesEmptyLabel == null) {
            return;
        }

        schedulesFlowPane.getChildren().clear();
        if (schedules.isEmpty()) {
            schedulesEmptyLabel.setVisible(true);
            schedulesEmptyLabel.setManaged(true);
            schedulesEmptyLabel.setText("No schedules found.");
            return;
        }

        schedulesEmptyLabel.setVisible(false);
        schedulesEmptyLabel.setManaged(false);
        schedules.stream()
            .sorted(Comparator
                .comparing((Schedule schedule) -> getScheduleNextCheckSortInstant(schedule), Comparator.nullsLast(Instant::compareTo))
                .thenComparingInt(Schedule::getScheduleId))
            .map(this::createScheduleCard)
            .forEach(card -> schedulesFlowPane.getChildren().add(card));
    }

    private Node createScheduleCard(Schedule schedule) {
        VBox card = new VBox(8);
        card.getStyleClass().add("mapping-card");
        card.getStyleClass().add("schedule-card");
        if (!schedule.isActive()) {
            card.getStyleClass().add("schedule-card-inactive");
        }
        if (isSelectedSchedule(schedule)) {
            card.getStyleClass().add("mapping-card-selected");
        }

        Label title = new Label("Schedule #" + schedule.getScheduleId());
        title.getStyleClass().add("mapping-card-title");

        ToggleButton activeToggle = MappingUiFactory.createScheduleActiveToggle(schedule, this::onScheduleActiveToggleRequested);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox titleRow = new HBox(8, title, spacer, activeToggle);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        VBox details = new VBox(5,
            createScheduleDetailRow("User", schedule.getUserId() <= 0 ? "-" : String.valueOf(schedule.getUserId())),
            createScheduleDetailRow("Measure", normalizeScheduleDisplayValue(schedule.getMeasureType())),
            createScheduleDetailRow("Trigger", formatScheduleTrigger(schedule)),
            createScheduleDetailRow("Interval", Math.max(0, schedule.getIntervalDays()) + " days"),
            createScheduleDetailRow("Next check", formatScheduleNextCheck(schedule))
        );

        card.getChildren().addAll(titleRow, details);
        card.setOnMouseClicked(event -> {
            if (event.getTarget() instanceof ButtonBase) {
                return;
            }
            selectScheduleForEdit(schedule);
        });
        return card;
    }

    private Node createScheduleDetailRow(String labelText, String valueText) {
        HBox row = new HBox(6);
        row.setAlignment(Pos.CENTER_LEFT);

        Label label = new Label(labelText + ":");
        label.getStyleClass().add("topbar-label");
        label.setMinWidth(72);

        Label value = new Label(valueText == null || valueText.isBlank() ? "-" : valueText);
        value.getStyleClass().add("topbar-value");
        value.setWrapText(true);
        row.getChildren().addAll(label, value);
        return row;
    }

    private String normalizeScheduleDisplayValue(String value) {
        return value == null || value.isBlank() ? "-" : value.trim();
    }

    private String formatScheduleTrigger(Schedule schedule) {
        return String.valueOf((int) Math.round(schedule.getTriggerPercentage())) + "%";
    }

    private String formatScheduleNextCheck(Schedule schedule) {
        String nextCheck = schedule.getNextCheck();
        if (nextCheck == null || nextCheck.isBlank()) {
            nextCheck = schedule.getNextRunDate();
        }
        return nextCheck == null || nextCheck.isBlank() ? "-" : nextCheck;
    }

    private void selectScheduleForEdit(Schedule schedule) {
        selectedScheduleForEdit = schedule;
        if (schedule != null) {
            populateScheduleFields(schedule);
        } else {
            if (scheduleIdField != null) {
                scheduleIdField.clear();
            }
            User currentUser = topBarController == null ? state.getSelectedUsers() : topBarController.getSelectedUser();
            if (currentUser == null) {
                currentUser = state.getSelectedUsers();
            }
            if (currentUser != null && userIdField != null) {
                userIdField.setText(String.valueOf(currentUser.getUserID()));
            }
        }
        renderScheduleCards();
    }

    private boolean isSelectedSchedule(Schedule schedule) {
        if (schedule == null || selectedScheduleForEdit == null) {
            return false;
        }
        return schedule.getScheduleId() > 0 && schedule.getScheduleId() == selectedScheduleForEdit.getScheduleId();
    }

    private void refreshSchedulesForCurrentContext(boolean activeOnly) {
        showingActiveSchedulesOnly = activeOnly;
        String uc = getSelectedUsecaseName();
        User selectedUser = topBarController == null ? null : topBarController.getSelectedUser();
        if (selectedUser == null || uc.isBlank()) {
            schedules.clear();
            selectScheduleForEdit(null);
            renderScheduleCards();
            if (scheduleStatusLabel != null) {
                scheduleStatusLabel.setText("Select a user and use case to view schedules.");
            }
            return;
        }

        CompletableFuture<ScheduleApiResponse> request = activeOnly
            ? ApiService.getInstance().listActiveSchedules(uc, selectedUser.getUserID())
            : ApiService.getInstance().listAllSchedules(uc, selectedUser.getUserID());

        request.thenAccept(resp -> Platform.runLater(() -> updateSchedulesTable(resp)));
    }

    @FXML
    private void onListAllSchedules() {
        String uc = this.getSelectedUsecaseName();
        int userId = getSelectedScheduleUserId();
        showingActiveSchedulesOnly = false;
        ApiService.getInstance().listAllSchedules(uc, userId).thenAccept(resp -> Platform.runLater(() -> updateSchedulesTable(resp)));
    }

    @FXML
    private void onListActiveSchedules() {
        String uc = this.getSelectedUsecaseName();
        int userId = getSelectedScheduleUserId();
        showingActiveSchedulesOnly = true;
        ApiService.getInstance().listActiveSchedules(uc, userId).thenAccept(resp -> Platform.runLater(() -> updateSchedulesTable(resp)));
    }

    @FXML
    private void onAddSchedule() {
        try {
            int userId = getSelectedScheduleUserId();
            int interval = getIntervalDaysValue();
            String measure = getSelectedScheduleMeasure();
            int trigger = getTriggerPercentageValue();
            String uc = requireSelectedUsecaseName();
            ApiService.getInstance().addSchedule(userId, interval, measure, trigger, uc)
                .thenAccept(resp -> Platform.runLater(() -> handleScheduleMutationResponse(resp)));
        } catch (Exception e) {
            showScheduleInputError(e);
        }
    }

    @FXML
    private void onSaveSchedule() {
        try {
            if (getCurrentScheduleIdForSave() > 0) {
                onChangeSchedule();
            } else {
                onAddSchedule();
            }
        } catch (Exception e) {
            showScheduleInputError(e);
        }
    }

    @FXML
    private void onChangeSchedule() {
        try {
            int schedId = getSelectedScheduleId();
            int userId = getSelectedScheduleUserId();
            int interval = getIntervalDaysValue();
            String measure = getSelectedScheduleMeasure();
            int trigger = getTriggerPercentageValue();
            String uc = requireSelectedUsecaseName();
            ApiService.getInstance().changeSchedule(schedId, userId, interval, measure, trigger, uc)
                .thenAccept(resp -> Platform.runLater(() -> handleScheduleMutationResponse(resp)));
        } catch (Exception e) {
            showScheduleInputError(e);
        }
    }

    @FXML
    private void onActivateSchedule() {
        try {
            int schedId = getSelectedScheduleId();
            String uc = requireSelectedUsecaseName();
            ApiService.getInstance().activateSchedule(schedId, uc)
                .thenAccept(resp -> Platform.runLater(() -> handleScheduleMutationResponse(resp)));
        } catch (Exception e) {
            showScheduleInputError(e);
        }
    }

    @FXML
    private void onDeactivateSchedule() {
        try {
            int schedId = getSelectedScheduleId();
            String uc = requireSelectedUsecaseName();
            ApiService.getInstance().deactivateSchedule(schedId, uc)
                .thenAccept(resp -> Platform.runLater(() -> handleScheduleMutationResponse(resp)));
        } catch (Exception e) {
            showScheduleInputError(e);
        }
    }

    private void onScheduleActiveToggleRequested(Schedule schedule, ToggleButton toggle) {
        if (schedule == null || toggle == null) {
            return;
        }

        boolean requestedActive = !schedule.isActive();
        String action = requestedActive ? "activate" : "deactivate";
        toggle.setSelected(schedule.isActive());
        toggle.setText(schedule.isActive() ? "Active" : "Inactive");

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle(requestedActive ? "Activate Schedule" : "Deactivate Schedule");
        confirm.setHeaderText("Are you sure you want to " + action + " schedule #" + schedule.getScheduleId() + "?");
        confirm.setContentText("This will send the existing " + action + " schedule request.");

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) {
            return;
        }

        selectScheduleForEdit(schedule);
        try {
            String uc = requireSelectedUsecaseName();
            CompletableFuture<ScheduleApiResponse> request = requestedActive
                ? ApiService.getInstance().activateSchedule(schedule.getScheduleId(), uc)
                : ApiService.getInstance().deactivateSchedule(schedule.getScheduleId(), uc);
            request.thenAccept(resp -> Platform.runLater(() -> handleScheduleMutationResponse(resp)));
        } catch (Exception e) {
            showScheduleInputError(e);
        }
    }

    private String getSelectedScheduleMeasure() {
        String measure = measureTypeComboBox == null ? "" : measureTypeComboBox.getValue();
        if (measure == null || measure.isBlank()) {
            throw new IllegalArgumentException("Please choose average, median, or mode.");
        }
        return measure.trim().toLowerCase(Locale.ROOT);
    }

    private void handleScheduleMutationResponse(ScheduleApiResponse resp) {
        if (resp == null) {
            return;
        }
        updateScheduleStatus(resp);
        if (resp.isSuccess()) {
            refreshSchedulesForCurrentContext(showingActiveSchedulesOnly);
        }
    }

    private int getTriggerPercentageValue() {
        if (triggerPercentageSpinner == null || triggerPercentageSpinner.getValueFactory() == null) {
            throw new IllegalStateException("Trigger percentage control is not available.");
        }

        String editorText = triggerPercentageSpinner.getEditor() == null
            ? ""
            : triggerPercentageSpinner.getEditor().getText().trim();
        if (!editorText.isEmpty()) {
            int typedValue = parseScheduleInteger(editorText, "Trigger percentage");
            if (typedValue == 0) {
                throw new IllegalArgumentException("Trigger percentage cannot be 0.");
            }
            triggerPercentageSpinner.getValueFactory().setValue(typedValue);
            return typedValue;
        }

        Integer value = triggerPercentageSpinner.getValue();
        if (value == null) {
            throw new IllegalArgumentException("Trigger percentage is required.");
        }
        if (value == 0) {
            throw new IllegalArgumentException("Trigger percentage cannot be 0.");
        }
        return value;
    }

    private int getIntervalDaysValue() {
        return getRequiredSpinnerValue(intervalDaysSpinner, "Interval days", 1);
    }

    private int getRequiredSpinnerValue(Spinner<Integer> spinner, String fieldName, int minValue) {
        if (spinner == null || spinner.getValueFactory() == null) {
            throw new IllegalStateException(fieldName + " control is not available.");
        }

        String editorText = spinner.getEditor() == null ? "" : spinner.getEditor().getText().trim();
        int value;
        if (!editorText.isEmpty()) {
            value = parseScheduleInteger(editorText, fieldName);
        } else {
            Integer spinnerValue = spinner.getValue();
            if (spinnerValue == null) {
                throw new IllegalArgumentException(fieldName + " is required.");
            }
            value = spinnerValue;
        }

        if (value < minValue) {
            throw new IllegalArgumentException(fieldName + " must be " + minValue + " or greater.");
        }

        spinner.getValueFactory().setValue(value);
        return value;
    }

    private void setupPositiveIntegerSpinner(Spinner<Integer> spinner, int initialValue) {
        if (spinner == null) {
            return;
        }

        spinner.setValueFactory(
            new SpinnerValueFactory.IntegerSpinnerValueFactory(1, Integer.MAX_VALUE, Math.max(1, initialValue), 1)
        );
        spinner.setEditable(true);
        spinner.getEditor().setTextFormatter(new TextFormatter<>(change -> {
            String newText = change.getControlNewText();
            return newText.matches("\\d*") ? change : null;
        }));
    }

    private void showScheduleInputError(Exception e) {
        String message = e == null || e.getMessage() == null || e.getMessage().isBlank()
            ? "Please make sure all schedule values are valid."
            : e.getMessage();
        AlertUtils.showErrorAlert("Input Error", message);
    }

    private int parseScheduleInteger(String text, String fieldName) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(fieldName + " must be a whole number.");
        }
    }

    private int getSelectedScheduleId() {
        int selectedId = getCurrentScheduleIdForSave();
        if (selectedId > 0) {
            return selectedId;
        }
        throw new IllegalArgumentException("Select a schedule first.");
    }

    private int getCurrentScheduleIdForSave() {
        if (selectedScheduleForEdit != null && selectedScheduleForEdit.getScheduleId() > 0) {
            return selectedScheduleForEdit.getScheduleId();
        }
        String text = scheduleIdField == null ? "" : scheduleIdField.getText().trim();
        if (text.isEmpty()) {
            return 0;
        }
        int scheduleId = parseScheduleInteger(text, "Schedule ID");
        if (scheduleId <= 0) {
            throw new IllegalArgumentException("Schedule ID must be greater than 0.");
        }
        return scheduleId;
    }

    private int getSelectedScheduleUserId() {
        User selected = topBarController == null ? null : topBarController.getSelectedUser();
        if (selected == null) {
            selected = state.getSelectedUsers();
        }
        if (selected != null && selected.getUserID() > 0) {
            return selected.getUserID();
        }
        String text = userIdField == null ? "" : userIdField.getText().trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("Select a user first.");
        }
        int userId = parseScheduleInteger(text, "User ID");
        if (userId <= 0) {
            throw new IllegalArgumentException("User ID must be greater than 0.");
        }
        return userId;
    }

     private String getSelectedUsecaseName() {
         String uc = topBarController == null || topBarController.getSelectedUseCaseLabel() == null
             ? ""
             : topBarController.getSelectedUseCaseLabel().getText();
         if (uc == null || uc.trim().isEmpty() || "-".equals(uc.trim())) {
             uc = state.getSelectedUseCase();
         }
         return uc == null ? "" : uc.trim();
      }

    private String requireSelectedUsecaseName() {
        String usecaseName = getSelectedUsecaseName();
        if (usecaseName.isBlank()) {
            throw new IllegalArgumentException("Select a use case first.");
        }
        return usecaseName;
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

        String normalizedKey = FormatUtils.normalizeUseCaseName(selectedUseCase);
        UseCase useCase = useCaseRegistry.get(normalizedKey);
        if (useCase != null) {
            ruleSensorTypeLabel.setText(useCase.getRawName());
            String description = useCase.getDescription();
            ruleSensorTypeDescriptionLabel.setText(description.isEmpty() ? "No description available." : description);
        } else {
            ruleSensorTypeLabel.setText(toUseCaseLabel(normalizedKey));
            ruleSensorTypeDescriptionLabel.setText("No description available.");
        }
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
                        AlertUtils.showErrorAlert("Save Failed", "Could not save the rule. Please try again.");
                        return;
                    }

                    clearRuleBuilderForm();
                    updateSelectedMappingForEdit(null);
                    loadMappings();
                    String savedUseCase = toUseCaseLabel(FormatUtils.normalizeUseCaseName(ruleConfig.getType()));
                    AlertUtils.showInfoAlert("Save Successful", "Saved mapping for " + savedUseCase + ".");
                    refreshMappings();
                }))
                .exceptionally(ex -> {
                    AlertUtils.showErrorAlert("Save Failed", "Could not save rule: " + ex.getMessage());
                    return null;
                });
        } catch (IllegalArgumentException ex) {
            AlertUtils.showErrorAlert("Validation Error", ex.getMessage());
        }
    }

    @FXML
    private void onSaveMappingConfig() {
        if (selectedMappingForEdit != null && selectedMappingForEdit.mappingId > 0) {
            changeSelectedMappingConfig();
        } else {
            saveRuleBuilderConfig();
        }
    }

    @FXML
    private void clearRuleInputFields() {
        clearRuleBuilderForm();
        updateSelectedMappingForEdit(null);
        renderMappingsForUseCase(state.getSelectedUseCase());
    }

    @FXML
    private void onListActiveMappings() {
        showingActiveMappingsOnly = true;
        renderMappingsForUseCase(state.getSelectedUseCase());
    }

    @FXML
    private void onListAllMappings() {
        showingActiveMappingsOnly = false;
        renderMappingsForUseCase(state.getSelectedUseCase());
    }

    @FXML
    private void refreshMappings() {
        try {
            String useCase = state.getSelectedUseCase();
            if (useCase == null || useCase.isBlank()) {
                throw new IllegalArgumentException("Please select a use case.");
            }
        } catch (IllegalArgumentException ex) {
            AlertUtils.showErrorAlert("Validation Error", ex.getMessage());
            return;
        }
        refreshMappingData();
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
            AlertUtils.showErrorAlert("Missing Mapping", "Select an active mapping card before changing it.");
            return;
        }

        try {
            SensorRuleConfig ruleConfig = buildRuleConfigFromForm();
            ruleConfig.setId(selectedMappingForEdit.mappingId);

            Integer useCaseId = state.getSelectedUseCaseId();
            if (useCaseId == null || useCaseId <= 0) {
                AlertUtils.showErrorAlert("Missing Use Case Id", "Could not resolve use case id. Refresh use cases and try again.");
                return;
            }

            final RuleCardData editingRule = selectedMappingForEdit;
            MappingChangeScope scope = resolveActiveMappingChangeScope(editingRule);
            if (scope == MappingChangeScope.CANCELLED) {
                return;
            }

            if (scope == MappingChangeScope.CURRENT_USER_ONLY) {
                User currentUser = state.getSelectedUsers();
                if (currentUser == null || currentUser.getUserID() <= 0) {
                    AlertUtils.showErrorAlert("Missing User", "Select the user who should receive the mapping change before choosing Apply to Selected User Only.");
                    return;
                }
                if (!isUserAssignedToRule(currentUser, editingRule)) {
                    AlertUtils.showErrorAlert("User Not Assigned", "The selected user is not assigned to mapping ID " + editingRule.mappingId + ".");
                    return;
                }
                changeMappingForCurrentUserOnly(editingRule, currentUser, ruleConfig, useCaseId);
                return;
            }

            changeMappingForAllUsers(editingRule, ruleConfig, useCaseId);
        } catch (IllegalArgumentException ex) {
            AlertUtils.showErrorAlert("Validation Error", ex.getMessage());
        }
    }

    private MappingChangeScope resolveActiveMappingChangeScope(RuleCardData rule) {
        if (workspaceTabPane != null && mappingsTab != null
                && workspaceTabPane.getSelectionModel().getSelectedItem() != mappingsTab) {
            return MappingChangeScope.ALL_USERS;
        }

        List<User> assignedUsers = findUsersAssignedToRule(rule);
        if (assignedUsers.size() < 2) {
            return MappingChangeScope.ALL_USERS;
        }

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Shared Mapping");
        alert.setHeaderText("This mapping is assigned to " + assignedUsers.size() + " users.");
        alert.setContentText("Choose how to apply your changes.");

        ButtonType applyToAll = new ButtonType("Apply to All Users", ButtonBar.ButtonData.YES);
        ButtonType applyToMeOnly = new ButtonType("Apply to Selected User Only", ButtonBar.ButtonData.NO);
        ButtonType cancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(applyToAll, applyToMeOnly, cancel);

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isEmpty() || result.get() == cancel) {
            return MappingChangeScope.CANCELLED;
        }
        return result.get() == applyToMeOnly
                ? MappingChangeScope.CURRENT_USER_ONLY
                : MappingChangeScope.ALL_USERS;
    }

    private void changeMappingForAllUsers(RuleCardData editingRule, SensorRuleConfig ruleConfig, int useCaseId) {
        ApiService.getInstance().changeMapping(
                editingRule.mappingId,
                ruleConfig,
                useCaseId,
                getSelectedUsecaseName(),
                chatSessionId
            )
            .thenAccept(success -> Platform.runLater(() -> handleMappingChangeResult(
                    success,
                    "Could not change mapping ID " + editingRule.mappingId + ".",
                    "Updated mapping ID " + editingRule.mappingId + "."
            )))
            .exceptionally(ex -> {
                Platform.runLater(() -> AlertUtils.showErrorAlert("Update Failed", "Failed to change mapping: " + ex.getMessage()));
                return null;
            });
    }

    private void changeMappingForCurrentUserOnly(RuleCardData editingRule, User currentUser,
                                                 SensorRuleConfig ruleConfig, int useCaseId) {
        ApiService.getInstance().changeMappingForUserOnly(
                editingRule.mappingId,
                currentUser.getUserID(),
                ruleConfig,
                useCaseId,
                getSelectedUsecaseName(),
                chatSessionId
            )
            .thenAccept(success -> Platform.runLater(() -> handleMappingChangeResult(
                    success,
                    "Could not duplicate mapping ID " + editingRule.mappingId + " for user " + currentUser.getUserID() + ".",
                    "Applied mapping changes only to user " + currentUser.getUserID() + "."
            )))
            .exceptionally(ex -> {
                Platform.runLater(() -> AlertUtils.showErrorAlert("Update Failed", "Failed to change mapping for the selected user: " + ex.getMessage()));
                return null;
            });
    }

    private void handleMappingChangeResult(boolean success, String failureMessage, String successMessage) {
        if (!success) {
            AlertUtils.showErrorAlert("Update Failed", failureMessage);
            return;
        }

        clearRuleBuilderForm();
        updateSelectedMappingForEdit(null);
        refreshMappingData();
        AlertUtils.showInfoAlert("Mapping Updated", successMessage);
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

        Label feedbackNote = new Label(
                "Create the use case now. After creation, you can discuss it with the agent, ask for API parameters or code, or go directly to n8n and build the flow yourself."
        );
        feedbackNote.setWrapText(true);

        // Validation label shown when the name is invalid (empty or contains whitespace)
        Label validationLabel = new Label();
        validationLabel.getStyleClass().add("validation-error");
        // Fallback inline style so the message is visible without a stylesheet
        validationLabel.setStyle("-fx-text-fill: #b91c1c; -fx-font-size: 12px;");
        validationLabel.setWrapText(true);
        validationLabel.setVisible(false);

        VBox content = new VBox(8,
                new Label("Name *"),
                nameField,
                validationLabel,
                new Label("Description"),
                descriptionField,
                feedbackNote
        );
        content.setPadding(new Insets(10));
        dialog.getDialogPane().setContent(content);

        Node addButton = dialog.getDialogPane().lookupButton(addButtonType);
        addButton.setDisable(true);

        nameField.textProperty().addListener((obs, oldValue, newValue) -> {
                boolean empty = newValue == null || newValue.trim().isEmpty();
                boolean hasWhitespace = newValue != null && newValue.matches(".*\\s.*");
                if (empty) {
                    validationLabel.setText("Use case name is required.");
                    validationLabel.setVisible(true);
                } else if (hasWhitespace) {
                    validationLabel.setText("Use case name must not contain spaces.");
                    validationLabel.setVisible(true);
                } else {
                    validationLabel.setText("");
                    validationLabel.setVisible(false);
                }
                addButton.setDisable(empty || hasWhitespace);
        });
        userIdField.textProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue != null && !newValue.isBlank()) {
                String userUseCase = resolveUseCaseDisplayName(newValue);
                state.setSelectedUseCase(userUseCase);
            }
        });

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isEmpty() || result.get() != addButtonType) {
            return;
        }

        String name = nameField.getText() == null ? "" : nameField.getText().trim();
        String description = descriptionField.getText() == null ? "" : descriptionField.getText().trim();

        if (name.isBlank()) {
            AlertUtils.showErrorAlert("Validation Error", "Use case name is required.");
            return;
        }

        if (name.matches(".*\\s.*")) {
            AlertUtils.showErrorAlert("Validation Error", "Use case name must not contain spaces.");
            return;
        }

        String normalizedRequestedName = FormatUtils.normalizeUseCaseName(name);
        boolean duplicateUseCase = useCases.stream()
            .map(FormatUtils::normalizeUseCaseName)
            .anyMatch(normalizedRequestedName::equals);
        if (duplicateUseCase || useCaseRegistry.containsKey(normalizedRequestedName)) {
            AlertUtils.showErrorAlert("Create Failed", "A use case with this name already exists.");
            return;
        }

        ApiService.getInstance().createUseCase(name, description)
                .thenAccept(response -> Platform.runLater(() -> {
                    if (response == null || response.has("error")) {
                        String errorMessage = response == null
                                ? "Could not create the use case. Please try again."
                                : response.path("error").asText("Could not create the use case. Please try again.");

                        AlertUtils.showErrorAlert("Create Failed", errorMessage);
                        return;
                    }

                    /*
                     * n8n may return:
                     * 1. { "usecase_id": 12, "name": "test2", ... }
                     * 2. [ { "usecase_id": 12, "name": "test2", ... } ]
                     * 3. { "data": [ { "usecase_id": 12, ... } ] }
                     * 4. { "rows": [ { "usecase_id": 12, ... } ] }
                     *
                     * This block normalizes all of these into one object.
                     */
                    JsonNode created = response;

                    if (response.isArray() && response.size() > 0) {
                        created = response.get(0);
                    } else if (response.has("data") && response.path("data").isArray() && response.path("data").size() > 0) {
                        created = response.path("data").get(0);
                    } else if (response.has("data") && response.path("data").isObject()) {
                        created = response.path("data");
                    } else if (response.has("rows") && response.path("rows").isArray() && response.path("rows").size() > 0) {
                        created = response.path("rows").get(0);
                    }

                    int createdUseCaseId = created.path("usecase_id").asInt(0);
                    if (createdUseCaseId <= 0) {
                        createdUseCaseId = created.path("usecaseid").asInt(0);
                    }
                    if (createdUseCaseId <= 0) {
                        createdUseCaseId = created.path("id").asInt(0);
                    }
                    if (createdUseCaseId <= 0) {
                        createdUseCaseId = created.path("usecaseId").asInt(0);
                    }

                    String createdName = created.path("name").asText(name);
                    String createdDescription = created.path("description").asText(description);

                    if (createdUseCaseId <= 0) {
                        AlertUtils.showErrorAlert(
                                "Create Failed",
                                "The use case was created, but the response did not include a valid usecase_id. Check the n8n /create-usecase response."
                        );
                        loadUseCases();
                        return;
                    }

                    String normalizedName = FormatUtils.normalizeUseCaseName(createdName);
                    UseCase newUseCase = new UseCase(createdUseCaseId, createdName, normalizedName, createdDescription, "");
                    useCaseRegistry.put(normalizedName, newUseCase);

                    if (!useCases.contains(createdName)) {
                        useCases.add(createdName);
                    }

                    leftSidebarController.getUseCases().setAll(useCases);
                    leftSidebarController.getUserUseCaseComboBox().setItems(useCases);

                    /*
                     * Important order:
                     * First put ID in the map.
                     * Then set selected use case.
                     * The selected-use-case listener calls resolveSelectedUseCaseId(),
                     * which depends on useCaseRegistry.
                     */
                    state.setSelectedUseCase(createdName);
                    state.setSelectedUseCaseId(createdUseCaseId);

                    applySelectedUseCaseUi(createdName);

                    AlertUtils.showInfoAlert(
                            "Use Case Created",
                            "Use case '" + createdName + "' was created. You can now discuss it with the agent or go directly to n8n."
                    );

                    loadUseCases();
                }))
                .exceptionally(ex -> {
                    Platform.runLater(() ->
                            AlertUtils.showErrorAlert("Create Failed", "Failed to create use case: " + ex.getMessage())
                    );
                    return null;
                });
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
            } else if (newValue == schedulingTab) {
                refreshSchedulesForCurrentContext(true);
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

        detachChatCommandDropdownFromComposer();
        chatCommandPopup = new Popup();
        chatCommandPopup.setAutoHide(true);
        chatCommandPopup.setHideOnEscape(false);
        chatCommandPopup.getContent().add(chatCommandDropdownList);
        chatCommandPopup.setOnHidden(event -> clearChatCommandDropdownState());

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

    private void detachChatCommandDropdownFromComposer() {
        if (chatCommandDropdownList.getParent() instanceof Pane parentPane) {
            parentPane.getChildren().remove(chatCommandDropdownList);
        }
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
        showChatCommandPopup();

        if (chatCommandHintLabel != null) {
            chatCommandHintLabel.setText("Use Up/Down, Enter, or click.");
            chatCommandHintLabel.setVisible(true);
            chatCommandHintLabel.setManaged(true);
        }
    }

    private void hideChatCommandDropdown() {
        if (chatCommandPopup != null) {
            chatCommandPopup.hide();
        }

        clearChatCommandDropdownState();
    }

    private void clearChatCommandDropdownState() {
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

    private void showChatCommandPopup() {
        if (chatCommandPopup == null
                || chatCommandDropdownList == null
                || chatInputField == null
                || chatInputField.getScene() == null
                || chatInputField.getScene().getWindow() == null) {
            return;
        }

        if (chatCommandDropdownList.getStylesheets().isEmpty()) {
            chatCommandDropdownList.getStylesheets().addAll(chatInputField.getScene().getStylesheets());
        }

        int visibleRows = Math.min(chatCommandDropdownList.getItems().size(), 4);
        double popupHeight = Math.max(56.0, visibleRows * chatCommandDropdownList.getFixedCellSize() + 2.0);
        chatCommandDropdownList.setPrefHeight(popupHeight);
        chatCommandDropdownList.setMaxHeight(popupHeight);
        chatCommandDropdownList.setPrefWidth(Math.max(280.0, chatInputField.getWidth()));

        Bounds inputBounds = chatInputField.localToScreen(chatInputField.getBoundsInLocal());
        if (inputBounds == null) {
            return;
        }

        double x = inputBounds.getMinX();
        double y = Math.max(0.0, inputBounds.getMinY() - popupHeight - 6.0);
        if (!chatCommandPopup.isShowing()) {
            chatCommandPopup.show(chatInputField.getScene().getWindow());
        }
        chatCommandPopup.setX(x);
        chatCommandPopup.setY(y);
    }

    private void acceptSelectedChatCommand() {
        if (chatCommandDropdownList == null || !chatCommandDropdownList.isVisible() || chatInputField == null) {
            return;
        }

        ChatCommandOption selected = chatCommandDropdownList.getSelectionModel().getSelectedItem();
        if (selected == null && !chatCommandDropdownList.getItems().isEmpty()) {
            selected = chatCommandDropdownList.getItems().get(0);
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

        chatCommandHintLabel.setText("Use Up/Down, Enter, or click.");
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
            AlertUtils.showErrorAlert("Missing Selection", "Please select a Users, use case, and time range before exporting.");
            return;
        }

        if ("Custom Date Range".equals(range)) {
            LocalDate start = state.getStartDate();
            LocalDate end = state.getEndDate();
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
            refreshSchedulesForCurrentContext(false);
        }));

        state.selectedUsersProperty().addListener(onNewValueChanged(newValue -> {
            refreshRuleSummary();
            if (newValue != null && selectedScheduleForEdit == null) {
                if (userIdField != null) {
                    userIdField.setText(String.valueOf(newValue.getUserID()));
                }
            }
            renderMappingsForUseCase(state.getSelectedUseCase());
            refreshSchedulesForCurrentContext(false);
        }));
    }

    private void loadUserss() {
        loadUsersAsync();
    }

    private CompletableFuture<Void> loadUsersAsync() {
        User currentSelection = topBarController.getSelectedUser();
        Integer selectedUserId = currentSelection == null ? null : currentSelection.getUserID();
        users.clear();
        return ApiService.getInstance().get(ApiService.EP_GET_USERS, null)
            .thenCompose(response -> {
                JsonNode usersArray = ApiService.getInstance().extractArray(response);
                if (usersArray == null) {
                    return CompletableFuture.completedFuture(null);
                }

                List<User> loaded = new ArrayList<>();
                for (JsonNode node : usersArray) {
                    if (!node.hasNonNull("userid")) {
                        continue;
                    }
                    loaded.add(parseUser(node));
                }

                CompletableFuture<Void> uiUpdate = new CompletableFuture<>();
                Platform.runLater(() -> {
                    try {
                        users.setAll(loaded);
                        leftSidebarController.getUsersCountLabel().setText(String.valueOf(users.size()));

                        leftSidebarController.getUsersSidebarList().getItems().setAll(users);
                        refreshUsersForSelectedUseCase(selectedUserId);
                        renderMappingsForUseCase(state.getSelectedUseCase());
                        refreshRuleSummary();
                        uiUpdate.complete(null);
                    } catch (Exception ex) {
                        uiUpdate.completeExceptionally(ex);
                    }
                });
                return uiUpdate;
            })
            .exceptionally(ex -> {
                ex.printStackTrace();
                return null;
            });
    }

    private User parseUser(JsonNode node) {
        int userId = node.path("userid").asInt();
        String fName = node.path("fname").asText("");
        String lName = node.path("lname").asText("");

        List<UserUseCaseMapping> mappings = new ArrayList<>();

        JsonNode mappingsNode = node.path("usecase_mappings");
        if (mappingsNode != null && mappingsNode.isArray()) {
            for (JsonNode mappingNode : mappingsNode) {
                int mappingId = readUserMappingRowId(mappingNode);
                int feedbackConfigRuleId = readUserMappingFeedbackRuleId(mappingNode, mappingId);
                int usecaseId = readUserMappingUseCaseId(mappingNode);
                String usecaseName = readUserMappingUseCaseName(mappingNode);
                String usecaseDescription = readNestedText(mappingNode, "usecase", "description");

                int minvalue = mappingNode.path("minvalue").asInt(0);
                int maxvalue = mappingNode.path("maxvalue").asInt(0);
                int minpulses = mappingNode.path("minpulses").asInt(0);
                int maxpulses = mappingNode.path("maxpulses").asInt(0);
                int minintensity = mappingNode.path("minintensity").asInt(0);
                int maxintensity = mappingNode.path("maxintensity").asInt(0);
                int minduration = mappingNode.path("minduration").asInt(0);
                int maxduration = mappingNode.path("maxduration").asInt(0);
                int mininterval = mappingNode.path("mininterval").asInt(0);
                int maxinterval = mappingNode.path("maxinterval").asInt(0);

                mappings.add(new UserUseCaseMapping(
                        mappingId,
                        feedbackConfigRuleId,
                        usecaseId,
                        usecaseName,
                        usecaseDescription,
                        minvalue,
                        maxvalue,
                        minpulses,
                        maxpulses,
                        minintensity,
                        maxintensity,
                        minduration,
                        maxduration,
                        mininterval,
                        maxinterval
                ));
            }
        }
        if (mappings.isEmpty()) {
            UserUseCaseMapping flatMapping = parseFlatUserMapping(node);
            if (flatMapping != null) {
                mappings.add(flatMapping);
            }
        }

        return new User(userId, fName, lName, mappings);
    }

    private UserUseCaseMapping parseFlatUserMapping(JsonNode node) {
        int usecaseId = readUserMappingUseCaseId(node);
        String usecaseName = readUserMappingUseCaseName(node);
        if (usecaseId <= 0 && usecaseName.isBlank()) {
            return null;
        }

        int mappingId = readUserMappingRowId(node);
        int feedbackConfigRuleId = readUserMappingFeedbackRuleId(node, mappingId);
        String usecaseDescription = node.path("usecase_description").asText("");
        if (usecaseDescription.isBlank()) {
            usecaseDescription = node.path("usecaseDescription").asText("");
        }

        return new UserUseCaseMapping(
                mappingId,
                feedbackConfigRuleId,
                usecaseId,
                usecaseName,
                usecaseDescription,
                node.path("minvalue").asInt(0),
                node.path("maxvalue").asInt(0),
                node.path("minpulses").asInt(0),
                node.path("maxpulses").asInt(0),
                node.path("minintensity").asInt(0),
                node.path("maxintensity").asInt(0),
                node.path("minduration").asInt(0),
                node.path("maxduration").asInt(0),
                node.path("mininterval").asInt(0),
                node.path("maxinterval").asInt(0)
        );
    }

    private int readUserMappingRowId(JsonNode mappingNode) {
        int mappingId = mappingNode.path("mapping_id").asInt(0);
        if (mappingId <= 0) {
            mappingId = mappingNode.path("user_mapping_id").asInt(0);
        }
        if (mappingId <= 0) {
            mappingId = mappingNode.path("userMappingId").asInt(0);
        }
        if (mappingId <= 0 && mappingNode.has("id") && !mappingNode.has("userid")) {
            mappingId = mappingNode.path("id").asInt(0);
        }
        return mappingId;
    }

    private int readUserMappingFeedbackRuleId(JsonNode mappingNode, int fallback) {
        int feedbackRuleId = mappingNode.path("feedback_config_rule_id").asInt(0);
        if (feedbackRuleId <= 0) {
            feedbackRuleId = mappingNode.path("feedbackConfigRuleId").asInt(0);
        }
        if (feedbackRuleId <= 0) {
            feedbackRuleId = mappingNode.path("rule_id").asInt(0);
        }
        if (feedbackRuleId <= 0) {
            feedbackRuleId = readNestedInt(mappingNode, "feedback_config_rule", "id");
        }
        if (feedbackRuleId <= 0) {
            feedbackRuleId = readNestedInt(mappingNode, "rule", "id");
        }
        return feedbackRuleId <= 0 ? fallback : feedbackRuleId;
    }

    private int readUserMappingUseCaseId(JsonNode mappingNode) {
        int usecaseId = mappingNode.path("usecase_id").asInt(0);
        if (usecaseId <= 0) {
            usecaseId = mappingNode.path("usecaseId").asInt(0);
        }
        if (usecaseId <= 0) {
            usecaseId = mappingNode.path("rule_usecase_id").asInt(0);
        }
        if (usecaseId <= 0) {
            usecaseId = readNestedInt(mappingNode, "feedback_config_rule", "usecase_id");
        }
        if (usecaseId <= 0) {
            usecaseId = readNestedInt(mappingNode, "rule", "usecase_id");
        }
        if (usecaseId <= 0) {
            usecaseId = readNestedInt(mappingNode, "usecase", "usecase_id");
        }
        return usecaseId;
    }

    private String readUserMappingUseCaseName(JsonNode mappingNode) {
        String usecaseName = mappingNode.path("usecase_name").asText("");
        if (usecaseName.isBlank()) {
            usecaseName = mappingNode.path("usecaseName").asText("");
        }
        if (usecaseName.isBlank()) {
            usecaseName = mappingNode.path("rule_usecase_name").asText("");
        }
        if (usecaseName.isBlank()) {
            usecaseName = mappingNode.path("monitoringType").asText("");
        }
        if (usecaseName.isBlank()) {
            usecaseName = readNestedText(mappingNode, "usecase", "name");
        }
        if (usecaseName.isBlank()) {
            usecaseName = readNestedText(mappingNode, "feedback_config_rule", "usecase_name");
        }
        if (usecaseName.isBlank()) {
            usecaseName = readNestedText(mappingNode, "rule", "usecase_name");
        }
        return usecaseName;
    }

    private int readNestedInt(JsonNode node, String objectName, String fieldName) {
        JsonNode nested = node.path(objectName);
        return nested == null || nested.isMissingNode() ? 0 : nested.path(fieldName).asInt(0);
    }

    private String readNestedText(JsonNode node, String objectName, String fieldName) {
        JsonNode nested = node.path(objectName);
        return nested == null || nested.isMissingNode() ? "" : nested.path(fieldName).asText("");
    }

    private void loadUseCases() {
        ApiService.getInstance().get(ApiService.EP_GET_USECASES, null)
            .thenAccept(response -> {
                JsonNode useCaseArray = ApiService.getInstance().extractArray(response);
                useCaseRegistry.clear();

                if (useCaseArray != null && useCaseArray.isArray()) {
                    for (JsonNode useCaseNode : useCaseArray) {
                        String rawName = useCaseNode.path("name").asText("").trim();
                        if (rawName.isEmpty()) continue;

                        String normalizedName = FormatUtils.normalizeUseCaseName(rawName);
                        int useCaseId = useCaseNode.path("usecase_id").asInt(0);
                        if (useCaseId <= 0) useCaseId = useCaseNode.path("usecaseid").asInt(0);
                        if (useCaseId <= 0) useCaseId = useCaseNode.path("id").asInt(0);

                        UseCase useCase = new UseCase(
                            useCaseId,
                            rawName,
                            normalizedName,
                            useCaseNode.path("description").asText("").trim(),
                            extractLoggingInterval(useCaseNode)
                        );
                        useCaseRegistry.put(normalizedName, useCase);
                    }
                }

                if (useCaseRegistry.isEmpty()) {
                    for (String defaultUseCase : DEFAULT_USE_CASES) {
                        String normalized = FormatUtils.normalizeUseCaseName(defaultUseCase);
                        useCaseRegistry.put(normalized, new UseCase(0, defaultUseCase, normalized, "", ""));
                    }
                }

                Platform.runLater(() -> {
                    useCases.setAll(useCaseRegistry.values().stream().map(UseCase::getRawName).collect(Collectors.toList()));
                    leftSidebarController.getUseCases().setAll(useCases);
                    leftSidebarController.getUserUseCaseComboBox().setItems(useCases);

                    String selected = state.getSelectedUseCase();
                    if (selected == null || selected.isBlank() || !useCases.contains(selected)) {
                        selected = useCases.isEmpty() ? null : useCases.get(0);
                    }
                    state.setSelectedUseCase(selected);
                    applySelectedUseCaseUi(selected);
                    User selectedUser = topBarController.getSelectedUser();
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

        String normalized = FormatUtils.normalizeUseCaseName(rawUseCase);
        UseCase useCase = useCaseRegistry.get(normalized);
        if (useCase != null) {
            return useCase.getRawName();
        }

        for (UseCase candidate : useCaseRegistry.values()) {
            if (normalized.equals(candidate.getNormalizedName())) {
                return candidate.getRawName();
            }
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
            leftSidebarController.getSelectedUserForAssignmentLabel().setText("-");
            leftSidebarController.getCurrentUserUseCaseLabel().setText("-");
            leftSidebarController.getUserUseCaseComboBox().setValue(null);
            updateAssignUserUseCaseButtonState();
            return;
        }

        String userDisplay = formatUser(user);
        leftSidebarController.getSelectedUserForAssignmentLabel().setText(userDisplay);

        UserUseCaseMapping assignedMapping = resolveAssignedMapping(user);
        String displayUsecase = resolveUseCaseDisplayName(
                assignedMapping == null ? user.getUsecaseName() : resolveMappingUseCaseName(assignedMapping)
        );

        if (displayUsecase == null || displayUsecase.isBlank() || "-".equals(displayUsecase)) {
            leftSidebarController.getCurrentUserUseCaseLabel().setText("No use case assigned");
        } else {
            user.setUsecaseName(displayUsecase);
            leftSidebarController.getCurrentUserUseCaseLabel().setText(displayUsecase);
        }

        if (displayUsecase != null && !displayUsecase.isBlank() && !"-".equals(displayUsecase)) {
            leftSidebarController.getUserUseCaseComboBox().setValue(displayUsecase);
        } else if (!leftSidebarController.getUserUseCaseComboBox().getItems().isEmpty()) {
            leftSidebarController.getUserUseCaseComboBox().setValue(leftSidebarController.getUserUseCaseComboBox().getItems().get(0));
        }

        updateAssignUserUseCaseButtonState();
    }

      private void updateAssignUserUseCaseButtonState() {
          if (leftSidebarController.getAssignUserUseCaseButton() == null) {
              return;
          }

          User selectedUser = leftSidebarController.getSelectedUser();
          String selected = leftSidebarController.getUserUseCaseComboBox().getValue();

          if (selectedUser == null || selected == null || selected.isBlank()) {
              leftSidebarController.getAssignUserUseCaseButton().setDisable(true);
              return;
          }

          boolean unchanged =
                  FormatUtils.normalizeUseCaseName(selected).equals(FormatUtils.normalizeUseCaseName(getAssignedUseCaseName(selectedUser)));
          leftSidebarController.getAssignUserUseCaseButton().setDisable(unchanged);
      }

    @FXML
    private void onAssignUserUseCase() {
        User selectedUser = leftSidebarController.getSelectedUser();
        if (selectedUser == null) {
            AlertUtils.showErrorAlert("Missing User", "Please select a user from the list before assigning a use case.");
            return;
        }

        String selectedUsecase = leftSidebarController.getUserUseCaseComboBox().getValue();
        if (selectedUsecase == null || selectedUsecase.isBlank()) {
            AlertUtils.showErrorAlert("Missing Use Case", "Please select a use case to assign.");
            return;
        }

        RuleCardData selectedRule = findFirstRuleForUseCase(selectedUsecase);
        if (selectedRule == null || selectedRule.mappingId <= 0) {
            AlertUtils.showErrorAlert(
                    "Missing Mapping",
                    "Create an active mapping for " + selectedUsecase + " before assigning it to a user."
            );
            return;
        }

        int selectedUsecaseId = resolveUseCaseId(selectedUsecase);
        if (selectedUsecaseId <= 0) {
            AlertUtils.showErrorAlert("Missing Use Case", "Could not resolve use case id for " + selectedUsecase + ".");
            return;
        }

        ApiService.getInstance().assignUseCaseToUser(selectedUser.getUserID(), selectedUsecaseId)
                .thenAccept(success -> Platform.runLater(() -> {
                    if (!success) {
                        AlertUtils.showErrorAlert("Update Failed", "Could not assign use case to user. Please try again.");
                        updateAssignUserUseCaseButtonState();
                        return;
                    }

                    String resolvedDisplayName = resolveUseCaseDisplayName(selectedUsecase);

                    selectedUser.setUsecaseName(resolvedDisplayName);
                    selectedUser.setCurrentUsecaseId(selectedUsecaseId);

                    leftSidebarController.getCurrentUserUseCaseLabel().setText(resolvedDisplayName);

                    AlertUtils.showInfoAlert(
                            "Use Case Assigned",
                            "User " + selectedUser.getUserID() + " is now assigned to " + resolvedDisplayName + "."
                    );

                    loadUserss();
                    state.setSelectedUseCase(resolvedDisplayName);
                }))
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        AlertUtils.showErrorAlert("Update Failed", "Failed to assign use case: " + ex.getMessage());
                        updateAssignUserUseCaseButtonState();
                    });
                    return null;
                });
    }

    private void resolveSelectedUseCaseId(String selectedUseCase) {
        if (selectedUseCase == null || selectedUseCase.isBlank()) {
            state.setSelectedUseCaseId(null);
            return;
        }

        String normalized = FormatUtils.normalizeUseCaseName(selectedUseCase);
        UseCase useCase = useCaseRegistry.get(normalized);
        if (useCase != null) {
            state.setSelectedUseCaseId(useCase.getId());
        } else {
            state.setSelectedUseCaseId(null);
        }
    }

    /**
     * Updates UI labels, list, and refreshes displays for selected use case
     */
    private void applySelectedUseCaseUi(String selectedUseCase) {
        if (topBarController.getSelectedUseCaseLabel() != null) {
            topBarController.getSelectedUseCaseLabel().setText((selectedUseCase == null || selectedUseCase.isBlank()) ? "-" : selectedUseCase);
            String selectedKey = FormatUtils.normalizeUseCaseName(selectedUseCase);
            UseCase useCase = useCaseRegistry.get(selectedKey);
            if (useCase != null) {
                String desc = useCase.getDescription();
                topBarController.getSelectedUseCaseLabel().setTooltip(desc.isEmpty() ? null : new Tooltip(desc));
            }
        }

        if (contextUseCaseLabel != null) {
            contextUseCaseLabel.setText((selectedUseCase == null || selectedUseCase.isBlank()) ? "-" : selectedUseCase);
        }

        if (leftSidebarController.getUseCaseListView() != null && selectedUseCase != null && !selectedUseCase.isBlank()) {
            String current = leftSidebarController.getUseCaseListView().getSelectionModel().getSelectedItem();
            if (!selectedUseCase.equals(current)) {
                leftSidebarController.getUseCaseListView().getSelectionModel().select(selectedUseCase);
            }
        }

        updateRuleBuilderUseCaseDisplay(selectedUseCase);
        updateLoggingIntervalDisplay(selectedUseCase);
        updateSelectedMappingForEdit(null);
        refreshUsersForSelectedUseCase(null);
        refreshRuleSummary();
        renderMappingsForUseCase(selectedUseCase);
        clearChatMessages();
        addChatMessage("Selected use case: " + selectedUseCase, false);
    }

    private void refreshUsersForSelectedUseCase(Integer preferredUserId) {
        if (topBarController == null) {
            return;
        }

        boolean restoreAllUsers = preferredUserId == null && topBarController.isAllUsersSelected();
        Integer userIdToRestore = preferredUserId;
        if (userIdToRestore == null && !restoreAllUsers) {
            User currentSelection = topBarController.getSelectedUser();
            userIdToRestore = currentSelection == null ? null : currentSelection.getUserID();
        }

        String selectedUseCase = state.getSelectedUseCase();
        List<User> filteredUsers = users.stream()
                .filter(user -> isUserAssignedToUseCase(user, selectedUseCase))
                .toList();

        topBarController.setUsers(filteredUsers, true);

        if (restoreAllUsers || userIdToRestore == null) {
            applyUserUseCaseContext(null, selectedUseCase);
            topBarController.selectAllUsers();
            state.setSelectedUsers(null);
            leftSidebarController.getUsersSidebarList().getSelectionModel().clearSelection();
            syncMonitoringEditorToSelectedUser(null);
            contextUsersLabel.setText("All Users");
            return;
        }

        User nextSelection = null;
        if (userIdToRestore != null) {
            final int restoredUserId = userIdToRestore;
            nextSelection = filteredUsers.stream()
                    .filter(user -> user.getUserID() == restoredUserId)
                    .findFirst()
                    .orElse(null);
        }
        if (nextSelection == null && !filteredUsers.isEmpty()) {
            nextSelection = filteredUsers.get(0);
        }

        applyUserUseCaseContext(nextSelection, selectedUseCase);
        topBarController.setSelectedUser(nextSelection);
        if (nextSelection != null) {
            state.setSelectedUsers(nextSelection);
            leftSidebarController.selectUser(nextSelection);
            syncMonitoringEditorToSelectedUser(nextSelection);
            contextUsersLabel.setText(formatUser(nextSelection));
        } else {
            state.setSelectedUsers(null);
            leftSidebarController.getUsersSidebarList().getSelectionModel().clearSelection();
            syncMonitoringEditorToSelectedUser(null);
            contextUsersLabel.setText("-");
        }
    }

    private boolean isUserAssignedToUseCase(User user, String useCase) {
        if (user == null || useCase == null || useCase.isBlank()) {
            return false;
        }

        String selectedKey = FormatUtils.normalizeUseCaseName(useCase);
        return user.getUsecaseMappings().stream()
                .anyMatch(mapping -> selectedKey.equals(resolveMappingUseCaseKey(mapping)));
    }

    private void applyUserUseCaseContext(User user, String useCase) {
        if (user == null || useCase == null || useCase.isBlank()) {
            return;
        }

        String selectedKey = FormatUtils.normalizeUseCaseName(useCase);
        UserUseCaseMapping mapping = user.getUsecaseMappings().stream()
                .filter(candidate -> selectedKey.equals(resolveMappingUseCaseKey(candidate)))
                .findFirst()
                .orElse(null);
        if (mapping != null) {
            user.setCurrentUsecase(mapping);
            String resolvedUseCaseName = resolveMappingUseCaseName(mapping);
            if (resolvedUseCaseName != null && !resolvedUseCaseName.isBlank()) {
                user.setUsecaseName(resolvedUseCaseName);
            }
        }
    }

    private UserUseCaseMapping resolveAssignedMapping(User user) {
        if (user == null || user.getUsecaseMappings().isEmpty()) {
            return null;
        }

        int currentRuleId = user.getMappingId();
        if (currentRuleId > 0) {
            UserUseCaseMapping current = user.getUsecaseMappings().stream()
                    .filter(mapping -> mapping.getFeedbackConfigRuleId() == currentRuleId || mapping.getMappingId() == currentRuleId)
                    .findFirst()
                    .orElse(null);
            if (current != null) {
                return current;
            }
        }

        return user.getFirstUsecaseMapping();
    }

    private String getAssignedUseCaseName(User user) {
        UserUseCaseMapping mapping = resolveAssignedMapping(user);
        if (mapping == null) {
            return "";
        }
        return resolveMappingUseCaseName(mapping);
    }

    private String resolveMappingUseCaseKey(UserUseCaseMapping mapping) {
        String name = resolveMappingUseCaseName(mapping);
        return FormatUtils.normalizeUseCaseName(name);
    }

    private String resolveMappingUseCaseName(UserUseCaseMapping mapping) {
        if (mapping == null) {
            return "";
        }

        if (mapping.getUsecaseId() > 0) {
            String displayName = resolveUseCaseDisplayName(toUseCaseLabelById(mapping.getUsecaseId()));
            if (displayName != null && !displayName.isBlank() && !"-".equals(displayName)) {
                return displayName;
            }
        }

        if (mapping.getUsecaseName() != null && !mapping.getUsecaseName().isBlank()) {
            return resolveUseCaseDisplayName(mapping.getUsecaseName());
        }

        RuleCardData rule = findRuleByMappingId(mapping.getFeedbackConfigRuleId());
        if (rule == null) {
            rule = findRuleByMappingId(mapping.getMappingId());
        }
        if (rule != null) {
            return resolveUseCaseDisplayName(rule.useCaseLabel);
        }

        return "";
    }

    private RuleCardData findRuleByMappingId(int mappingId) {
        if (mappingId <= 0) {
            return null;
        }

        return allRules.stream()
                .filter(rule -> rule.mappingId == mappingId)
                .findFirst()
                .orElse(null);
    }

    private RuleCardData findFirstRuleForUseCase(String useCase) {
        if (useCase == null || useCase.isBlank()) {
            return null;
        }

        String selectedKey = FormatUtils.normalizeUseCaseName(useCase);
        return allRules.stream()
                .filter(rule -> selectedKey.equals(rule.useCaseKey))
                .findFirst()
                .orElse(null);
    }

    private int resolveUseCaseId(String useCase) {
        if (useCase == null || useCase.isBlank()) {
            return 0;
        }

        UseCase registered = useCaseRegistry.get(FormatUtils.normalizeUseCaseName(useCase));
        return registered == null ? 0 : registered.getId();
    }

    private String toUseCaseLabelById(int useCaseId) {
        return useCaseRegistry.values().stream()
                .filter(useCase -> useCase.getId() == useCaseId)
                .map(UseCase::getRawName)
                .findFirst()
                .orElse("");
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
            String normalizedKey = FormatUtils.normalizeUseCaseName(selectedUseCase);
            UseCase useCase = useCaseRegistry.get(normalizedKey);
            if (useCase != null) {
                String configuredInterval = useCase.getLoggingInterval();
                if (!configuredInterval.isEmpty()) {
                    displayText = configuredInterval;
                }
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
        String selectedUseCaseName = state.getSelectedUseCase();
        if (selectedUseCaseName == null || selectedUseCaseName.isBlank()) {
            AlertUtils.showErrorAlert("Missing Use Case", "Please select a use case before editing logging interval.");
            return;
        }

        String selectedKey = FormatUtils.normalizeUseCaseName(selectedUseCaseName);
        UseCase useCase = useCaseRegistry.get(selectedKey);
        if (useCase == null || useCase.getId() <= 0) {
            AlertUtils.showErrorAlert("Missing Use Case Id", "Could not resolve use case id. Refresh use cases and try again.");
            return;
        }

        LoggingIntervalDraft initialDraft = parseLoggingIntervalDraft(useCase.getLoggingInterval());

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Edit Logging Interval");
        dialog.setHeaderText("Set logging interval for " + selectedUseCaseName + ".");

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
            AlertUtils.showErrorAlert("Invalid Interval", "Interval must match: number + second(s)/minute(s)/hour(s)/day(s).");
            return;
        }

        ApiService.getInstance().setLoggingInterval(useCase.getId(), interval)
            .thenAccept(success -> Platform.runLater(() -> {
                if (!success) {
                    AlertUtils.showErrorAlert("Update Failed", "Could not update logging interval. Please try again.");
                    return;
                }

                useCase.setLoggingInterval(interval);
                updateLoggingIntervalDisplay(selectedUseCaseName);
                AlertUtils.showInfoAlert("Interval Updated", "Logging interval set to " + interval + " for " + selectedUseCaseName + ".");
                loadUseCases();
            }))
            .exceptionally(ex -> {
                AlertUtils.showErrorAlert("Update Failed", "Failed to update logging interval: " + ex.getMessage());
                return null;
            });
    }

    private void loadMappings() {
        loadMappingsAsync();
    }

    private CompletableFuture<Void> loadMappingsAsync() {
        return ApiService.getInstance().get(ApiService.EP_CURRENT_CONFIGURATION, null)
            .thenCompose(response -> {
                CompletableFuture<Void> uiUpdate = new CompletableFuture<>();
                Platform.runLater(() -> {
                    try {
                        allRules.clear();
                        new MappingConfigParser(allRules, useCaseRegistry.values().stream().collect(Collectors.toMap(UseCase::getNormalizedName, UseCase::getRawName))).parseMappingsFromConfiguration(response);
                        refreshUsersForSelectedUseCase(null);
                        updateUserAssignmentPanel(leftSidebarController.getSelectedUser());
                        renderMappingsForUseCase(state.getSelectedUseCase());
                        refreshRuleSummary();
                        uiUpdate.complete(null);
                    } catch (Exception ex) {
                        uiUpdate.completeExceptionally(ex);
                    }
                });
                return uiUpdate;
            })
            .exceptionally(ex -> {
                ex.printStackTrace();
                return null;
            });
    }

    private CompletableFuture<Void> refreshMappingData() {
        return loadMappingsAsync().thenCompose(ignored -> loadUsersAsync());
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
                JsonNode dictionaryEntriesNode = response.path("dictionaryEntries");
                yellowBookEntries.clear();
                if (dictionaryEntriesNode.isArray() && !dictionaryEntriesNode.isEmpty()) {
                    yellowBookEntries.putAll(YellowBookParser.parseEntries(dictionaryEntriesNode));
                } else {
                    yellowBookEntries.putAll(YellowBookParser.parse(reply));
                }
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

        return useCaseRegistry.values().stream()
            .map(UseCase::getId)
            .filter(id -> id > 0)
            .findFirst()
            .orElse(null);
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
        yellowBookEntries.forEach((useCaseName, parameters) -> yellowBookContentBox.getChildren().add(YellowBookUiFactory.createYellowBookUseCaseCard(useCaseName, parameters, this::onRemoveYellowBookUseCase, this::onEditYellowBookParameter, this::onRemoveYellowBookParameter, selectedYellowBookParameter)));
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
            AlertUtils.showErrorAlert("Missing Parameter", "Select a dictionary parameter before saving changes.");
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
            AlertUtils.showErrorAlert("Invalid Parameter", "Parameter name and format are required.");
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
            AlertUtils.showErrorAlert("Missing Use Case Id", "Could not resolve a use case id for the dictionary request.");
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
                        AlertUtils.showErrorAlert("Rename Failed", error);
                    });
                    return;
                }

                ApiService.getInstance()
                    .sendDictionaryRequest(deleteMessage, contextUsecaseId, getSelectedUsecaseName(), chatSessionId)
                    .thenAccept(deleteResponse -> Platform.runLater(() -> {
                        if (isDictionaryErrorResponse(deleteResponse)) {
                            String error = extractDictionaryError(deleteResponse, "Added the new name, but could not remove the old parameter.");
                            yellowBookStatusLabel.setText(error);
                            AlertUtils.showErrorAlert("Rename Failed", error);
                            loadYellowBookDictionary();
                            return;
                        }

                        AlertUtils.showInfoAlert("Parameter Renamed", "Renamed " + original.parameterName + " to " + updated.parameterName + ".");
                        updateYellowBookEditor(null);
                        loadYellowBookDictionary();
                    }))
                    .exceptionally(ex -> {
                        Platform.runLater(() -> AlertUtils.showErrorAlert("Rename Failed", ex.getMessage()));
                        return null;
                    });
            })
            .exceptionally(ex -> {
                Platform.runLater(() -> AlertUtils.showErrorAlert("Rename Failed", ex.getMessage()));
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
            AlertUtils.showErrorAlert("Missing Use Case Id", "Could not resolve a use case id for the dictionary request.");
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
                    AlertUtils.showErrorAlert("Dictionary Update Failed", error);
                    yellowBookStatusLabel.setText(error);
                    return;
                }

                String reply = response.path("reply").asText("Dictionary update completed.");
                AlertUtils.showInfoAlert(successTitle, reply);
                updateYellowBookEditor(null);
                loadYellowBookDictionary();
            }))
            .exceptionally(ex -> {
                Platform.runLater(() -> AlertUtils.showErrorAlert("Dictionary Update Failed", ex.getMessage()));
                return null;
            });
    }

    /**
     * Renders filtered use case mappings or empty state
     */
    private void renderMappingsForUseCase(String useCase) {
        mappingsFlowPane.getChildren().clear();

        if (useCase == null || useCase.isBlank()) {
            mappingsEmptyLabel.setVisible(true);
            mappingsEmptyLabel.setManaged(true);
            mappingsEmptyLabel.setText("Select a use case to view mappings.");
            return;
        }

        String selectedUseCaseKey = FormatUtils.normalizeUseCaseName(useCase);
        User selectedUser = topBarController == null ? null : topBarController.getSelectedUser();
        List<RuleCardData> filtered = allRules.stream()
            .filter(rule -> selectedUseCaseKey.equals(rule.useCaseKey))
            .filter(rule -> !showingActiveMappingsOnly || selectedUser == null || isUserAssignedToRule(selectedUser, rule))
            .toList();

        if (filtered.isEmpty()) {
            mappingsEmptyLabel.setVisible(true);
            mappingsEmptyLabel.setManaged(true);
            mappingsEmptyLabel.setText(selectedUser == null
                ? "No mappings found for " + useCase + "."
                : "No active mapping assigned to " + formatUser(selectedUser) + " for " + useCase + ".");
            return;
        }

        mappingsEmptyLabel.setVisible(false);
        mappingsEmptyLabel.setManaged(false);

        // Builds and adds rule detail cards to flow pane
        for (RuleCardData rule : filtered) {
            boolean assignedToSelectedUser = selectedUser == null || isUserAssignedToRule(selectedUser, rule);
            VBox card = new VBox(8);
            card.getStyleClass().add("mapping-card");
            if (!assignedToSelectedUser) {
                card.getStyleClass().add("mapping-card-inactive");
            }
            if (selectedMappingForEdit != null && selectedMappingForEdit.mappingId == rule.mappingId) {
                card.getStyleClass().add("mapping-card-selected");
            }

            Label title = new Label(rule.rangeLabel);
            title.getStyleClass().add("mapping-card-title");

            HBox titleRow = new HBox(8);
            titleRow.setAlignment(Pos.CENTER_LEFT);

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            Button selectButton = MappingUiFactory.createSelectMappingButton(rule, this::selectMappingForEdit);
            Button deleteButton = MappingUiFactory.createDeleteMappingButton(rule, this::onDeleteMappingRequested);
            titleRow.getChildren().addAll(title, spacer, selectButton, deleteButton);

            Label pulses = new Label("Pulse count: " + rule.pulseLabel);
            Label intensity = new Label("Intensity: " + rule.intensityLabel);
            Label duration = new Label("Duration: " + rule.durationLabel);
            Label interval = new Label("Interval: " + rule.intervalLabel);
            Node assignedUsers = createMappingAssignedUsersNode(rule);
            if (!assignedToSelectedUser) {
                Label inactiveHint = new Label("Not assigned to selected user");
                inactiveHint.getStyleClass().add("mapping-assigned-muted");
                card.getChildren().addAll(titleRow, pulses, intensity, duration, interval, inactiveHint, assignedUsers);
            } else {
                card.getChildren().addAll(titleRow, pulses, intensity, duration, interval, assignedUsers);
            }

            card.setOnMouseClicked(event -> {
                if (event.getTarget() instanceof Button) {
                    return;
                }
                selectMappingForEdit(rule);
            });
            mappingsFlowPane.getChildren().add(card);
        }
    }

    private Node createMappingAssignedUsersNode(RuleCardData rule) {
        List<User> assignedUsers = findUsersAssignedToRule(rule);

        if (assignedUsers.isEmpty()) {
            Label none = new Label("Assigned user: None");
            none.getStyleClass().add("mapping-assigned-muted");
            return none;
        }

        if (assignedUsers.size() == 1) {
            Label single = new Label("Assigned user: " + formatUser(assignedUsers.get(0)));
            single.getStyleClass().add("mapping-assigned-label");
            single.setWrapText(true);
            return single;
        }

        VBox usersBox = new VBox(4);
        usersBox.getStyleClass().add("mapping-assigned-list");
        for (User user : assignedUsers) {
            Label userLabel = new Label(formatUser(user));
            userLabel.getStyleClass().add("mapping-assigned-user");
            userLabel.setWrapText(true);
            usersBox.getChildren().add(userLabel);
        }

        TitledPane usersPane = new TitledPane("Assigned users (" + assignedUsers.size() + ")", usersBox);
        usersPane.getStyleClass().add("mapping-assigned-pane");
        usersPane.setExpanded(false);
        usersPane.setAnimated(false);
        usersPane.setOnMouseClicked(event -> event.consume());
        return usersPane;
    }

    private List<User> findUsersAssignedToRule(RuleCardData rule) {
        if (rule == null || rule.mappingId <= 0) {
            return List.of();
        }

        return users.stream()
                .filter(user -> user.getUsecaseMappings().stream()
                        .anyMatch(mapping -> isUserMappingAssignedToRule(mapping, rule)))
                .sorted(Comparator.comparingInt(User::getUserID))
                .toList();
    }

    private boolean isUserAssignedToRule(User user, RuleCardData rule) {
        if (user == null || rule == null) {
            return false;
        }
        return user.getUsecaseMappings().stream()
            .anyMatch(mapping -> isUserMappingAssignedToRule(mapping, rule));
    }

    private boolean isUserMappingAssignedToRule(UserUseCaseMapping mapping, RuleCardData rule) {
        if (mapping == null || rule == null || rule.mappingId <= 0) {
            return false;
        }

        boolean sameMapping = mapping.getFeedbackConfigRuleId() == rule.mappingId
                || mapping.getMappingId() == rule.mappingId
                || mapping.getEffectiveMappingId() == rule.mappingId;
        if (!sameMapping) {
            return false;
        }

        String ruleUseCaseKey = rule.useCaseKey == null ? "" : rule.useCaseKey;
        String mappingUseCaseKey = resolveMappingUseCaseKey(mapping);
        return ruleUseCaseKey.isBlank() || mappingUseCaseKey.isBlank() || ruleUseCaseKey.equals(mappingUseCaseKey);
    }

    private void selectMappingForEdit(RuleCardData rule) {
        if (rule == null) {
            return;
        }

        updateSelectedMappingForEdit(rule);
        populateRuleBuilderFromMapping(rule);
        renderMappingsForUseCase(state.getSelectedUseCase());
    }

    private void onDeleteMappingRequested(RuleCardData rule) {
        if (rule == null || rule.mappingId <= 0) {
            AlertUtils.showErrorAlert("Delete Failed", "Could not resolve mapping id for the selected card.");
            return;
        }

        String label = (rule == null || rule.rangeLabel == null || rule.rangeLabel.isBlank())
            ? "selected mapping"
            : rule.rangeLabel;

        ApiService.getInstance().deleteMapping(rule.mappingId)
            .thenAccept(success -> Platform.runLater(() -> {
                if (!success) {
                    AlertUtils.showErrorAlert("Delete Failed", "Could not delete mapping for " + label + ".");
                    return;
                }
                AlertUtils.showInfoAlert("Mapping Deleted", "Deleted mapping for " + label + ".");
                loadMappings();
            }))
            .exceptionally(ex -> {
                AlertUtils.showErrorAlert("Delete Failed", "Failed to delete mapping: " + ex.getMessage());
                return null;
            });
    }

    private void onSendMessage() {
        String message = chatInputField.getText() == null ? "" : chatInputField.getText().trim();
        if (message.isEmpty()) {
            return;
        }

        String selectedUseCase = state.getSelectedUseCase();
        Integer useCaseId = state.getSelectedUseCaseId();

        if ((useCaseId == null || useCaseId <= 0) && selectedUseCase != null && !selectedUseCase.isBlank()) {
            String normalized = FormatUtils.normalizeUseCaseName(selectedUseCase);
            UseCase useCase = useCaseRegistry.get(normalized);
            if (useCase != null) {
                useCaseId = useCase.getId();
            }

            if (useCaseId != null && useCaseId > 0) {
                state.setSelectedUseCaseId(useCaseId);
            }
        }

        if (useCaseId == null || useCaseId <= 0) {
            addChatMessage(
                    "I could not resolve the ID for the selected use case '" + selectedUseCase + "'. Please refresh the use cases and try again.",
                    false
            );
            return;
        }

        String expandedMessage = expandChatCommand(message);
        boolean mappingListRequest = isMappingListRequest(message, expandedMessage);
        User selectedUser = topBarController.getSelectedUser();
        if (selectedUser != null && !isUserAssignedToUseCase(selectedUser, selectedUseCase)) {
            AlertUtils.showErrorAlert("Invalid User", "Please select a user assigned to " + selectedUseCase + " before chatting.");
            refreshUsersForSelectedUseCase(null);
            return;
        }
        applyUserUseCaseContext(selectedUser, selectedUseCase);

        addChatMessage(message, true);
        chatInputField.clear();

        Map<String, Object> payload = new HashMap<>();
        payload.put("message", expandedMessage);
        payload.put("session_id", chatSessionId);
        payload.put("usecase_id", useCaseId);
        payload.put("usecase_name", selectedUseCase);
        if (selectedUser != null) {
            payload.put("user_id", selectedUser.getUserID());
            payload.put("user_name", formatUser(selectedUser));
            payload.put("user_usecase_id", selectedUser.getUsecaseId());
            payload.put("user_usecase_name", selectedUser.getUsecaseName());
            payload.put("user_mapping_id", selectedUser.getMappingId());
        }

        setAgentTyping(true);

        ApiService.getInstance().postWithResponse(ApiService.EP_CHAT_CONFIG, payload)
                .thenAccept(response -> {
                    CompletableFuture<Void> refresh = mappingListRequest
                            ? refreshMappingData()
                            : CompletableFuture.completedFuture(null);
                    refresh.thenRun(() -> Platform.runLater(() -> handleChatResponse(response, mappingListRequest, selectedUseCase)))
                            .exceptionally(ex -> {
                                Platform.runLater(() -> {
                                    setAgentTyping(false);
                                    addChatMessage("Error refreshing mappings: " + ex.getMessage(), false);
                                });
                                return null;
                            });
                })
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        setAgentTyping(false);
                        addChatMessage("Error: " + ex.getMessage(), false);
                    });
                    return null;
                });
    }

    private void handleChatResponse(JsonNode response, boolean mappingListRequest, String selectedUseCase) {
        setAgentTyping(false);
        if (response != null && response.has("reply")) {
            String reply = response.get("reply").asText();
            addChatMessage(mappingListRequest ? appendMappingAssignmentSummary(reply, selectedUseCase) : reply, false);
        } else {
            addChatMessage("AI response did not include reply field.", false);
        }
    }

    private boolean isMappingListRequest(String originalMessage, String expandedMessage) {
        String original = originalMessage == null ? "" : originalMessage.trim().toLowerCase(Locale.ROOT);
        String expanded = expandedMessage == null ? "" : expandedMessage.trim().toLowerCase(Locale.ROOT);

        if (original.startsWith("$mapping")) {
            return true;
        }

        return isMappingListText(expanded) || isMappingListText(original);
    }

    private boolean isMappingListText(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }

        return text.contains("mapping")
                && (text.contains("show") || text.contains("list") || text.contains("current") || text.contains("active"));
    }

    private String appendMappingAssignmentSummary(String reply, String useCase) {
        String summary = buildMappingAssignmentSummary(useCase);
        if (summary.isBlank()) {
            return reply == null ? "" : reply;
        }

        String baseReply = reply == null ? "" : reply.trim();
        if (baseReply.toLowerCase(Locale.ROOT).contains("assigned users by mapping")) {
            return baseReply;
        }
        if (baseReply.isBlank()) {
            return summary;
        }

        return baseReply + "\n\n" + summary;
    }

    private String buildMappingAssignmentSummary(String useCase) {
        if (useCase == null || useCase.isBlank()) {
            return "";
        }

        String selectedUseCaseKey = FormatUtils.normalizeUseCaseName(useCase);
        List<RuleCardData> filtered = allRules.stream()
                .filter(rule -> selectedUseCaseKey.equals(rule.useCaseKey))
                .toList();
        if (filtered.isEmpty()) {
            return "";
        }

        StringBuilder summary = new StringBuilder("Assigned users by mapping:\n");
        for (RuleCardData rule : filtered) {
            List<User> assignedUsers = findUsersAssignedToRule(rule);
            summary.append("- ID ")
                    .append(rule.mappingId)
                    .append(": ")
                    .append(assignedUsers.size())
                    .append(assignedUsers.size() == 1 ? " user" : " users");

            if (!assignedUsers.isEmpty()) {
                summary.append(" - ")
                        .append(assignedUsers.stream()
                                .map(this::formatUser)
                                .collect(Collectors.joining(", ")));
            }
            summary.append('\n');
        }

        return summary.toString().trim();
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
         WebView bubble = ChatBubbleFactory.createChatBubbleView(text, userMessage);

         HBox row = new HBox(bubble);
         row.getStyleClass().add("chat-message-row");
         row.setFillHeight(false);
         row.setAlignment(userMessage ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

         chatHistoryBox.getChildren().add(row);
     }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private void clearChatMessages() {
        chatHistoryBox.getChildren().clear();
    }

    private void refreshRuleSummary() {
        activeRulesSummaryBox.getChildren().clear();

        String selectedUseCase = state.getSelectedUseCase();
        if (selectedUseCase == null || selectedUseCase.isBlank()) {
            activeRulesSummaryBox.getChildren().add(new Label("No use case selected."));
            return;
        }

        User selectedUser = topBarController == null ? null : topBarController.getSelectedUser();
        boolean allUsersSelected = topBarController != null && topBarController.isAllUsersSelected();
        if (selectedUser == null && !allUsersSelected) {
            activeRulesSummaryBox.getChildren().add(new Label("No user selected."));
            return;
        }

        String selectedUseCaseKey = FormatUtils.normalizeUseCaseName(selectedUseCase);
        List<RuleCardData> filtered = allRules.stream()
            .filter(rule -> selectedUseCaseKey.equals(rule.useCaseKey))
            .filter(rule -> selectedUser == null || isUserAssignedToRule(selectedUser, rule))
            .toList();

        if (filtered.isEmpty()) {
            activeRulesSummaryBox.getChildren().add(new Label(allUsersSelected
                ? "No mappings for this use case."
                : "No active mapping for this user."));
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

    private void ensureMiniGraphLoadingOverlay() {
        if (miniGraphLoadingOverlay == null) {
            miniGraphLoadingOverlay = SharedUiFactory.createLoadingOverlay("Loading preview...");
            AnchorPane.setTopAnchor(miniGraphLoadingOverlay, 0.0);
            AnchorPane.setRightAnchor(miniGraphLoadingOverlay, 0.0);
            AnchorPane.setBottomAnchor(miniGraphLoadingOverlay, 0.0);
            AnchorPane.setLeftAnchor(miniGraphLoadingOverlay, 0.0);
        }

        if (!miniGraphAnchor.getChildren().contains(miniGraphLoadingOverlay)) {
            miniGraphAnchor.getChildren().add(miniGraphLoadingOverlay);
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
        String name = (user.getFName() + " " + user.getLName()).trim();
        return name.isEmpty() ? String.valueOf(user.getUserID()) : user.getUserID() + " - " + name;
    }

    private User findUserById(Integer userId) {
        if (userId == null || userId <= 0) {
            return null;
        }
        return users.stream().filter(user -> user.getUserID() == userId).findFirst().orElse(null);
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
            AlertUtils.showErrorAlert("Invalid Name", "First and last name cannot be empty.");
            return;
        }

        ApiService.getInstance().setUserName(user.getUserID(), newFirstName, newLastName)
            .thenAccept(success -> Platform.runLater(() -> {
                if (!success) {
                    AlertUtils.showErrorAlert("Update Failed", "Could not update the user name. Please try again.");
                    return;
                }

                AlertUtils.showInfoAlert("User Updated", "Updated user " + user.getUserID() + " to " + newFirstName + " " + newLastName + ".");
                loadUserss();
            }))
            .exceptionally(ex -> {
                AlertUtils.showErrorAlert("Update Failed", "Failed to update user name: " + ex.getMessage());
                return null;
            });
    }

    private String toUseCaseLabel(String useCaseKey) {
        if (useCaseKey == null || useCaseKey.isBlank()) {
            return "Unknown";
        }

        UseCase useCase = useCaseRegistry.get(useCaseKey);
        if (useCase != null) {
            return useCase.getRawName();
        }

        return switch (useCaseKey) {
            case "HeartRate" -> "Heart Rate";
            case "SunAzimuth" -> "Sun Azimuth";
            case "MoonAzimuth" -> "Moon Azimuth";
            case "Pollution" -> "Pollution";
            default -> useCaseKey;
        };
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

}
