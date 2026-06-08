package com.example.demo.controller;

import com.example.demo.controller.state.DashboardState;
import com.example.demo.factory.MappingUiFactory;
import com.example.demo.model.SensorRuleConfig;
import com.example.demo.model.User;
import com.example.demo.model.UserUseCaseMapping;
import com.example.demo.model.RuleCardData;
import com.example.demo.model.LoggingIntervalDraft;
import com.example.demo.model.UseCase;
import com.example.demo.parser.MappingConfigParser;
import com.example.demo.service.ApiService;
import com.example.demo.util.AlertUtils;
import com.example.demo.util.FormatUtils;
import com.fasterxml.jackson.databind.JsonNode;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.*;
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
    private static final Pattern LOG_INTERVAL_PATTERN = Pattern.compile("^\\d+(\\.\\d+)?\\s+(second|seconds|minute|minutes|hour|hours|day|days)$", Pattern.CASE_INSENSITIVE);

    @FXML
    private LeftSidebarController leftSidebarController;
    @FXML
    private TopBarController topBarController;
    @FXML
    private YellowBookTabController yellowBookTabContentController;
    @FXML
    private SchedulesTabController schedulesTabContentController;
    @FXML
    private GraphTabController graphTabContentController;
    @FXML
    private AgentChatTabController agentChatTabContentController;

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
    private final DashboardState state = DashboardState.getInstance();
    private final ObservableList<User> users = FXCollections.observableArrayList();
    private final ObservableList<String> useCases = FXCollections.observableArrayList();
    private final Map<String, UseCase> useCaseRegistry = new HashMap<>();
    private final List<RuleCardData> allRules = new ArrayList<>();
    private final String chatSessionId = UUID.randomUUID().toString();
    private String activeMonitoringTypeKey = "";
    private RuleCardData selectedMappingForEdit;
    private boolean showingActiveMappingsOnly = true;
    private boolean mappingsRenderRetryScheduled;

    private enum MappingChangeScope {
        ALL_USERS,
        CURRENT_USER_ONLY,
        CANCELLED
    }

    private JsonNode latestGraphData;

    @FXML
    private void initialize() {
        initializeSubcontrollers();
        setupTabs();
        setupAgentChatTab();
        setupGraphTab();
        setupMappingsRuleBuilder();
        setupSchedulesTab();
        setupYellowBookTab();
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
                updateAgentChatUsersLabel(allUsersSelected ? "All Users" : "-");
                leftSidebarController.getUsersSidebarList().getSelectionModel().clearSelection();
                syncMonitoringEditorToSelectedUser(null);
                if (schedulesTabContentController != null) {
                    schedulesTabContentController.clearSelection();
                }
                refreshRuleSummary();
                renderMappingsWhenReady(state.getSelectedUseCase());
                scheduleGraphUpdate();
                return;
            }
            state.setSelectedUsers(newValue);
            updateAgentChatUsersLabel(formatUser(newValue));
            if (leftSidebarController.getUsersSidebarList().getSelectionModel().getSelectedItem() != newValue) {
                leftSidebarController.selectUser(newValue);
            }
            syncMonitoringEditorToSelectedUser(newValue);
            if (schedulesTabContentController != null) {
                schedulesTabContentController.syncSelectedUser(newValue);
            }
            if (newValue.getUsecaseName() != null && !newValue.getUsecaseName().isBlank()) {
                String userUseCase = resolveUseCaseDisplayName(newValue.getUsecaseName());
                state.setSelectedUseCase(userUseCase);
            }
            refreshRuleSummary();
            renderMappingsWhenReady(state.getSelectedUseCase());
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

    private void setupSchedulesTab() {
        if (schedulesTabContentController == null) {
            return;
        }

        schedulesTabContentController.setContextSupplier(() -> new SchedulesTabController.ScheduleContext(
            getSelectedUsecaseName(),
            getSelectedScheduleContextUser(),
            topBarController != null && topBarController.isAllUsersSelected()
        ));
        schedulesTabContentController.setUserFormatter(userId -> {
            User user = findUserById(userId);
            return user == null ? String.valueOf(userId) : formatUser(user);
        });
    }

    private User getSelectedScheduleContextUser() {
        User selected = topBarController == null ? null : topBarController.getSelectedUser();
        return selected == null ? state.getSelectedUsers() : selected;
    }

    private void setupYellowBookTab() {
        if (yellowBookTabContentController == null) {
            return;
        }

        yellowBookTabContentController.setChatSessionId(chatSessionId);
        yellowBookTabContentController.setContextSupplier(() -> new YellowBookTabController.DictionaryContext(
            resolveDictionaryContextUseCaseId(),
            getSelectedUsecaseName()
        ));
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
        renderMappingsWhenReady(state.getSelectedUseCase());
    }

    @FXML
    private void onListActiveMappings() {
        showingActiveMappingsOnly = true;
        renderMappingsWhenReady(state.getSelectedUseCase());
    }

    @FXML
    private void onListAllMappings() {
        showingActiveMappingsOnly = false;
        renderMappingsWhenReady(state.getSelectedUseCase());
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
                renderMappingsWhenReady(state.getSelectedUseCase());
                updateLoggingIntervalDisplay(state.getSelectedUseCase());
            } else if (newValue == agentChatTab) {
                if (agentChatTabContentController != null) {
                    agentChatTabContentController.onTabActivated();
                }
            } else if (newValue == yellowBookTab) {
                yellowBookTabContentController.loadDictionary();
            } else if (newValue == schedulingTab) {
                if (schedulesTabContentController != null) {
                    schedulesTabContentController.refreshSchedulesForCurrentContext(true);
                }
            }
        }));
    }

    private void setupAgentChatTab() {
        if (agentChatTabContentController == null) {
            return;
        }

        agentChatTabContentController.setSelectedUseCaseSupplier(state::getSelectedUseCase);
        agentChatTabContentController.setSelectedUseCaseIdSupplier(state::getSelectedUseCaseId);
        agentChatTabContentController.setSelectedUseCaseIdConsumer(state::setSelectedUseCaseId);
        agentChatTabContentController.setSelectedUserSupplier(() -> topBarController == null ? null : topBarController.getSelectedUser());
        agentChatTabContentController.setAllUsersSelectedSupplier(() -> topBarController != null && topBarController.isAllUsersSelected());
        agentChatTabContentController.setUseCaseLookup(useCaseRegistry::get);
        agentChatTabContentController.setUserFormatter(this::formatUser);
        agentChatTabContentController.setUserUseCaseAssignmentValidator(this::isUserAssignedToUseCase);
        agentChatTabContentController.setUserUseCaseContextApplier(this::applyUserUseCaseContext);
        agentChatTabContentController.setRefreshUsersForSelectedUseCaseCallback(this::refreshUsersForSelectedUseCase);
        agentChatTabContentController.setRulesSupplier(() -> List.copyOf(allRules));
        agentChatTabContentController.setRuleAssignmentResolver(this::isUserAssignedToRule);
        agentChatTabContentController.setUsersAssignedToRuleResolver(this::findUsersAssignedToRule);
        agentChatTabContentController.setMappingRefreshCallback(this::refreshMappingData);
        agentChatTabContentController.setGraphRefreshCallback(this::scheduleGraphUpdate);
        agentChatTabContentController.setMappingsRefreshCallback(this::refreshMappings);
        agentChatTabContentController.setChatSessionIdSupplier(() -> chatSessionId);
        agentChatTabContentController.initializeChat();
    }

    private void setupGraphTab() {
        if (graphTabContentController == null) {
            return;
        }

        graphTabContentController.setContextSupplier(() -> new GraphTabController.GraphContext(
            state.getSelectedUsers(),
            state.getSelectedUseCase(),
            state.getSelectedTimeRange(),
            state.getStartDate(),
            state.getEndDate()
        ));
        graphTabContentController.setDataLoadedCallback(dataArray -> {
            latestGraphData = dataArray;
            if (agentChatTabContentController != null) {
                agentChatTabContentController.renderMiniGraphFromData(dataArray);
                agentChatTabContentController.showMiniGraphLoading(false);
            }
        });
        graphTabContentController.initializeGraph();
    }

    private void setupStateListeners() {
        state.selectedUseCaseProperty().addListener(onChanged((oldValue, newValue) -> {
            resolveSelectedUseCaseId(newValue);
            applySelectedUseCaseUi(newValue);
            scheduleGraphUpdate();
            if (schedulesTabContentController != null) {
                schedulesTabContentController.refreshSchedulesForCurrentContext(false);
            }
        }));

        state.selectedUsersProperty().addListener(onNewValueChanged(newValue -> {
            refreshRuleSummary();
            if (schedulesTabContentController != null) {
                schedulesTabContentController.syncSelectedUser(newValue);
            }
            renderMappingsWhenReady(state.getSelectedUseCase());
            if (schedulesTabContentController != null) {
                schedulesTabContentController.refreshSchedulesForCurrentContext(false);
            }
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
                        renderMappingsWhenReady(state.getSelectedUseCase());
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

        updateAgentChatUseCaseLabel(selectedUseCase);

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
        renderMappingsWhenReady(selectedUseCase);
        if (agentChatTabContentController != null) {
            agentChatTabContentController.clearMessages();
            agentChatTabContentController.addSystemMessage("Selected use case: " + selectedUseCase);
        }
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
            updateAgentChatUsersLabel("All Users");
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
            updateAgentChatUsersLabel(formatUser(nextSelection));
        } else {
            state.setSelectedUsers(null);
            leftSidebarController.getUsersSidebarList().getSelectionModel().clearSelection();
            syncMonitoringEditorToSelectedUser(null);
            updateAgentChatUsersLabel("-");
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
                        renderMappingsWhenReady(state.getSelectedUseCase());
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

    /**
     * Renders filtered use case mappings or empty state
     */
    private void renderMappingsForUseCase(String useCase) {
        if (!isMappingsViewReady()) {
            scheduleMappingsRenderRetry(useCase);
            return;
        }

        mappingsFlowPane.getChildren().clear();

        if (useCase == null || useCase.isBlank()) {
            mappingsEmptyLabel.setVisible(true);
            mappingsEmptyLabel.setManaged(true);
            mappingsEmptyLabel.setText("Select a use case to view mappings.");
            return;
        }

        String selectedUseCaseKey = FormatUtils.normalizeUseCaseName(useCase);
        User selectedUser = topBarController == null ? null : topBarController.getSelectedUser();

        if (selectedUser != null && !showingActiveMappingsOnly) {
             ApiService.getInstance().getUserMappingHistory(selectedUser.getUserID(), useCase)
                     .thenAccept(response -> Platform.runLater(() -> {
                         renderUserMappingHistory(response, useCase, selectedUser);
                     }));
             return;
        }

        List<RuleCardData> filtered = allRules.stream()
            .filter(rule -> selectedUseCaseKey.equals(rule.useCaseKey))
            .filter(rule -> {
                if (!showingActiveMappingsOnly) {
                    return true;
                }
                if (selectedUser != null) {
                    return isUserAssignedToRule(selectedUser, rule);
                }
                return !findUsersAssignedToRule(rule).isEmpty();
            })
            .filter(rule -> !showingActiveMappingsOnly || isRuleActiveForMappingsView(selectedUser, rule))
            .toList();

        if (filtered.isEmpty()) {
            mappingsEmptyLabel.setVisible(true);
            mappingsEmptyLabel.setManaged(true);
            mappingsEmptyLabel.setText(selectedUser == null
                ? "No active mappings found for " + useCase + "."
                : "No active mapping assigned to " + formatUser(selectedUser) + " for " + useCase + ".");
            return;
        }

        mappingsEmptyLabel.setVisible(false);
        mappingsEmptyLabel.setManaged(false);

        // Builds and adds rule detail cards to flow pane
        for (RuleCardData rule : filtered) {
            boolean assignedToSelectedUser = selectedUser == null
                    ? isRuleAssignedToAnyUser(rule)
                    : isUserAssignedToRule(selectedUser, rule);
            VBox card = new VBox(8);
            card.getStyleClass().add("mapping-card");
            if (!assignedToSelectedUser) {
                card.getStyleClass().add("mapping-card-inactive");
            }
            if (selectedMappingForEdit != null && selectedMappingForEdit.mappingId == rule.mappingId) {
                card.getStyleClass().add("mapping-card-selected");
            }

            Label title = new Label(formatMappingCardTitle(rule));
            title.getStyleClass().add("mapping-card-title");

            HBox titleRow = new HBox(8);
            titleRow.setAlignment(Pos.CENTER_LEFT);

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            Button selectButton = MappingUiFactory.createSelectMappingButton(rule, this::selectMappingForEdit);
            Button assignButton = createAssignUsersToMappingButton(rule);
            Button deleteButton = MappingUiFactory.createDeleteMappingButton(rule, this::onDeleteMappingRequested);
            titleRow.getChildren().addAll(title, spacer, assignButton, selectButton, deleteButton);

            Label values = new Label("Values: " + formatMappingValues(rule));
            Label pulses = new Label("Pulse count: " + rule.pulseLabel);
            Label intensity = new Label("Intensity: " + rule.intensityLabel);
            Label duration = new Label("Duration: " + rule.durationLabel);
            Label interval = new Label("Interval: " + rule.intervalLabel);
            Node assignedUsers = createMappingAssignedUsersNode(rule);
            if (!assignedToSelectedUser) {
                Label inactiveHint = new Label("Not assigned to selected user");
                inactiveHint.getStyleClass().add("mapping-assigned-muted");
                card.getChildren().addAll(titleRow, values, pulses, intensity, duration, interval, inactiveHint, assignedUsers);
            } else {
                card.getChildren().addAll(titleRow, values, pulses, intensity, duration, interval, assignedUsers);
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

    private boolean isRuleActiveForMappingsView(User selectedUser, RuleCardData rule) {
        return selectedUser == null
                ? isRuleAssignedToAnyUser(rule)
                : isUserAssignedToRule(selectedUser, rule);
    }

    private void renderUserMappingHistory(JsonNode response, String useCase, User selectedUser) {
        mappingsFlowPane.getChildren().clear();

        if (response == null || !response.isArray() || response.isEmpty()) {
            mappingsEmptyLabel.setVisible(true);
            mappingsEmptyLabel.setManaged(true);
            mappingsEmptyLabel.setText("Failed to load mapping history.");
            return;
        }

        JsonNode data = response.get(0);
        if (data == null || !data.has("status") || "error".equals(data.get("status").asText())) {
            mappingsEmptyLabel.setVisible(true);
            mappingsEmptyLabel.setManaged(true);
            mappingsEmptyLabel.setText("Failed to load mapping history.");
            return;
        }

        JsonNode mappingsArray = data.get("mappings");
        if (mappingsArray == null || mappingsArray.isEmpty()) {
            mappingsEmptyLabel.setVisible(true);
            mappingsEmptyLabel.setManaged(true);
            mappingsEmptyLabel.setText("No mapping history found for " + formatUser(selectedUser) + " and " + useCase + ".");
            return;
        }

         mappingsEmptyLabel.setVisible(false);
         mappingsEmptyLabel.setManaged(false);

         for (JsonNode mappingNode : mappingsArray) {
             VBox card = MappingUiFactory.createHistoryMappingCard(mappingNode, useCase);
             mappingsFlowPane.getChildren().add(card);
         }
    }


    private String formatMappingCardTitle(RuleCardData rule) {
        if (rule == null) {
            return "Unknown: -";
        }

        String useCaseName = rule.useCaseLabel == null || rule.useCaseLabel.isBlank()
            ? toUseCaseLabel(rule.useCaseKey)
            : rule.useCaseLabel.trim();
        return useCaseName + ": " + (rule.mappingId > 0 ? rule.mappingId : "-");
    }

    private String formatMappingValues(RuleCardData rule) {
        if (rule == null) {
            return "-";
        }
        return rule.minValue + "-" + rule.maxValue;
    }

    private void renderMappingsWhenReady(String useCase) {
        if (!isMappingsViewReady()) {
            scheduleMappingsRenderRetry(useCase);
            return;
        }

        renderMappingsForUseCase(useCase);
    }

    private boolean isMappingsViewReady() {
        return mappingsFlowPane != null && mappingsEmptyLabel != null;
    }

    private void scheduleMappingsRenderRetry(String useCase) {
        if (mappingsRenderRetryScheduled) {
            return;
        }

        mappingsRenderRetryScheduled = true;
        Platform.runLater(() -> {
            mappingsRenderRetryScheduled = false;
            if (isMappingsViewReady()) {
                renderMappingsForUseCase(useCase);
            }
        });
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

    private boolean isRuleAssignedToAnyUser(RuleCardData rule) {
        if (rule == null) {
            return false;
        }
        return users.stream().anyMatch(user -> isUserAssignedToRule(user, rule));
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
        renderMappingsWhenReady(state.getSelectedUseCase());
    }

    private Button createAssignUsersToMappingButton(RuleCardData rule) {
        Button button = new Button("Assign");
        button.getStyleClass().add("mapping-assign-button");
        button.setTooltip(new Tooltip("Assign users to this mapping"));
        button.setOnMouseClicked(event -> event.consume());
        button.setOnAction(ignored -> onAssignUsersToMappingRequested(rule));
        return button;
    }

    private void onAssignUsersToMappingRequested(RuleCardData rule) {
        if (rule == null || rule.mappingId <= 0) {
            AlertUtils.showErrorAlert("Missing Mapping", "Could not resolve mapping id for the selected card.");
            return;
        }

        String useCaseName = resolveUseCaseDisplayName(rule.useCaseLabel);
        if (useCaseName == null || useCaseName.isBlank() || "-".equals(useCaseName)) {
            useCaseName = state.getSelectedUseCase();
        }

        int useCaseId = resolveUseCaseId(useCaseName);
        if (useCaseId <= 0) {
            AlertUtils.showErrorAlert("Missing Use Case", "Could not resolve use case id for " + useCaseName + ".");
            return;
        }

        final String resolvedUseCaseName = useCaseName;
        List<User> candidateUsers = users.stream()
                .filter(user -> isUserAssignedToUseCase(user, resolvedUseCaseName))
                .sorted(Comparator.comparingInt(User::getUserID))
                .toList();

        if (candidateUsers.isEmpty()) {
            AlertUtils.showErrorAlert("No Users", "No users are assigned to " + resolvedUseCaseName + ".");
            return;
        }

        List<User> selectedUsers = showAssignUsersToMappingDialog(rule, resolvedUseCaseName, candidateUsers);
        if (selectedUsers.isEmpty()) {
            return;
        }

        assignUsersToMapping(rule, useCaseId, resolvedUseCaseName, selectedUsers);
    }

    private List<User> showAssignUsersToMappingDialog(RuleCardData rule, String useCaseName, List<User> candidateUsers) {
        Dialog<List<User>> dialog = new Dialog<>();
        dialog.setTitle("Assign Users");
        dialog.setHeaderText("Assign users to mapping " + rule.mappingId + " for " + useCaseName + ".");

        ButtonType assignButtonType = new ButtonType("Assign Selected", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(assignButtonType, ButtonType.CANCEL);

        VBox listBox = new VBox(6);
        listBox.getStyleClass().add("mapping-assignment-dialog-list");

        Map<CheckBox, User> selections = new LinkedHashMap<>();
        for (User user : candidateUsers) {
            boolean alreadyAssigned = isUserAssignedToRule(user, rule);
            CheckBox checkBox = new CheckBox(formatUser(user));
            checkBox.getStyleClass().add("mapping-assignment-checkbox");
            checkBox.setSelected(alreadyAssigned);
            checkBox.setDisable(alreadyAssigned);
            if (alreadyAssigned) {
                checkBox.setText(formatUser(user) + " (already assigned)");
            }
            selections.put(checkBox, user);
            listBox.getChildren().add(checkBox);
        }

        Label hint = new Label("Choose users to assign to this mapping.");
        hint.getStyleClass().add("topbar-muted-value");
        hint.setWrapText(true);

        ScrollPane scrollPane = new ScrollPane(listBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefViewportHeight(Math.min(320, Math.max(160, candidateUsers.size() * 34)));
        scrollPane.getStyleClass().add("mappings-scroll");

        VBox content = new VBox(10, hint, scrollPane);
        content.setPadding(new Insets(12, 12, 4, 12));
        content.setPrefWidth(380);
        dialog.getDialogPane().setContent(content);

        Node assignButtonNode = dialog.getDialogPane().lookupButton(assignButtonType);
        Runnable updateAssignState = () -> {
            boolean hasNewSelection = selections.entrySet().stream()
                    .anyMatch(entry -> entry.getKey().isSelected() && !isUserAssignedToRule(entry.getValue(), rule));
            assignButtonNode.setDisable(!hasNewSelection);
        };
        selections.keySet().forEach(checkBox ->
                checkBox.selectedProperty().addListener((obs, oldValue, newValue) -> updateAssignState.run()));
        updateAssignState.run();

        dialog.setResultConverter(buttonType -> {
            if (buttonType != assignButtonType) {
                return List.of();
            }
            return selections.entrySet().stream()
                    .filter(entry -> entry.getKey().isSelected())
                    .map(Map.Entry::getValue)
                    .filter(user -> !isUserAssignedToRule(user, rule))
                    .toList();
        });

        Optional<List<User>> result = dialog.showAndWait();
        return result.orElse(List.of());
    }

    private void assignUsersToMapping(RuleCardData rule, int useCaseId, String useCaseName, List<User> selectedUsers) {
        List<CompletableFuture<Boolean>> assignments = selectedUsers.stream()
                .map(user -> ApiService.getInstance().assignMappingToUser(
                        user.getUserID(),
                        rule.mappingId,
                        useCaseId,
                        useCaseName,
                        chatSessionId
                ))
                .toList();

        CompletableFuture.allOf(assignments.toArray(CompletableFuture[]::new))
                .thenRun(() -> {
                    boolean allSuccessful = assignments.stream().allMatch(CompletableFuture::join);
                    Platform.runLater(() -> handleMappingAssignmentResult(allSuccessful, rule, useCaseName, selectedUsers));
                })
                .exceptionally(ex -> {
                    Platform.runLater(() -> AlertUtils.showErrorAlert("Assignment Failed", "Failed to assign users: " + ex.getMessage()));
                    return null;
                });
    }

    private void handleMappingAssignmentResult(boolean allSuccessful, RuleCardData rule, String useCaseName, List<User> selectedUsers) {
        if (!allSuccessful) {
            AlertUtils.showErrorAlert("Assignment Failed", "Could not assign every selected user to mapping " + rule.mappingId + ".");
            return;
        }

        AlertUtils.showInfoAlert(
                "Users Assigned",
                "Assigned " + selectedUsers.size() + (selectedUsers.size() == 1 ? " user" : " users")
                        + " to mapping " + rule.mappingId + " for " + useCaseName + "."
        );
        state.setSelectedUseCase(useCaseName);
        refreshMappingData();
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

    private void refreshRuleSummary() {
        if (agentChatTabContentController != null) {
            agentChatTabContentController.refreshRuleSummary();
        }
    }

    @FXML
    private void scheduleGraphUpdate() {
        if (graphTabContentController != null) {
            if (agentChatTabContentController != null) {
                agentChatTabContentController.showMiniGraphLoading(true);
            }
            graphTabContentController.scheduleGraphUpdate();
        }
    }

    private void updateAgentChatUsersLabel(String label) {
        if (agentChatTabContentController != null) {
            agentChatTabContentController.updateUsersLabel(label);
        }
    }

    private void updateAgentChatUseCaseLabel(String selectedUseCase) {
        if (agentChatTabContentController != null) {
            agentChatTabContentController.updateUseCaseLabel(selectedUseCase);
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