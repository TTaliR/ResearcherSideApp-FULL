package com.example.demo.controller;

import com.example.demo.factory.MappingUiFactory;
import com.example.demo.model.LoggingIntervalDraft;
import com.example.demo.model.RuleCardData;
import com.example.demo.model.SensorRuleConfig;
import com.example.demo.model.UseCase;
import com.example.demo.model.User;
import com.example.demo.service.ApiService;
import com.example.demo.util.AlertUtils;
import com.example.demo.util.FormatUtils;
import com.fasterxml.jackson.databind.JsonNode;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MappingsTabController {
    private static final Pattern LOG_INTERVAL_PATTERN = Pattern.compile("^\\d+(\\.\\d+)?\\s+(second|seconds|minute|minutes|hour|hours|day|days)$", Pattern.CASE_INSENSITIVE);

    @FXML private FlowPane mappingsFlowPane;
    @FXML private Label mappingsEmptyLabel;
    @FXML private Label ruleSensorTypeLabel;
    @FXML private Label ruleSensorTypeDescriptionLabel;
    @FXML private TextField ruleMinValueField;
    @FXML private TextField ruleMaxValueField;
    @FXML private TextField ruleMinPulsesField;
    @FXML private TextField ruleMaxPulsesField;
    @FXML private TextField ruleMinIntensityField;
    @FXML private TextField ruleMaxIntensityField;
    @FXML private TextField ruleMinDurationField;
    @FXML private TextField ruleMaxDurationField;
    @FXML private TextField ruleMinIntervalField;
    @FXML private TextField ruleMaxIntervalField;
    @FXML private Label selectedMappingLabel;
    @FXML private Label loggingIntervalValueLabel;
    @FXML private Button editLoggingIntervalButton;

    private Supplier<String> selectedUseCaseSupplier = () -> "";
    private Supplier<Integer> selectedUseCaseIdSupplier = () -> null;
    private Consumer<String> selectedUseCaseConsumer = ignored -> {};
    private Supplier<User> selectedUserSupplier = () -> null;
    private Supplier<List<User>> usersSupplier = List::of;
    private Supplier<List<RuleCardData>> rulesSupplier = List::of;
    private Function<String, UseCase> useCaseLookup = ignored -> null;
    private Function<String, String> useCaseDisplayNameResolver = raw -> raw == null ? "-" : raw;
    private Function<String, Integer> useCaseIdResolver = ignored -> 0;
    private Function<String, String> useCaseLabelResolver = key -> key == null ? "Unknown" : key;
    private Function<User, String> userFormatter = user -> user == null ? "-" : String.valueOf(user.getUserID());
    private BiPredicate<User, String> userUseCaseAssignmentValidator = (user, useCase) -> false;
    private BiPredicate<User, RuleCardData> ruleAssignmentResolver = (user, rule) -> false;
    private Function<RuleCardData, List<User>> usersAssignedToRuleResolver = ignored -> List.of();
    private Supplier<CompletableFuture<Void>> mappingRefreshCallback = () -> CompletableFuture.completedFuture(null);
    private Runnable useCasesRefreshCallback = () -> {};
    private Supplier<String> chatSessionIdSupplier = () -> "";

    private RuleCardData selectedMappingForEdit;
    private boolean showingActiveMappingsOnly = true;
    private boolean mappingsRenderRetryScheduled;

    private enum MappingChangeScope {
        ALL_USERS,
        CURRENT_USER_ONLY,
        CANCELLED
    }

    @FXML
    private void initialize() {
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

    public void setSelectedUseCaseSupplier(Supplier<String> selectedUseCaseSupplier) {
        this.selectedUseCaseSupplier = selectedUseCaseSupplier == null ? this.selectedUseCaseSupplier : selectedUseCaseSupplier;
    }

    public void setSelectedUseCaseIdSupplier(Supplier<Integer> selectedUseCaseIdSupplier) {
        this.selectedUseCaseIdSupplier = selectedUseCaseIdSupplier == null ? this.selectedUseCaseIdSupplier : selectedUseCaseIdSupplier;
    }

    public void setSelectedUseCaseConsumer(Consumer<String> selectedUseCaseConsumer) {
        this.selectedUseCaseConsumer = selectedUseCaseConsumer == null ? this.selectedUseCaseConsumer : selectedUseCaseConsumer;
    }

    public void setSelectedUserSupplier(Supplier<User> selectedUserSupplier) {
        this.selectedUserSupplier = selectedUserSupplier == null ? this.selectedUserSupplier : selectedUserSupplier;
    }

    public void setUsersSupplier(Supplier<List<User>> usersSupplier) {
        this.usersSupplier = usersSupplier == null ? this.usersSupplier : usersSupplier;
    }

    public void setRulesSupplier(Supplier<List<RuleCardData>> rulesSupplier) {
        this.rulesSupplier = rulesSupplier == null ? this.rulesSupplier : rulesSupplier;
    }

    public void setUseCaseLookup(Function<String, UseCase> useCaseLookup) {
        this.useCaseLookup = useCaseLookup == null ? this.useCaseLookup : useCaseLookup;
    }

    public void setUseCaseDisplayNameResolver(Function<String, String> useCaseDisplayNameResolver) {
        this.useCaseDisplayNameResolver = useCaseDisplayNameResolver == null ? this.useCaseDisplayNameResolver : useCaseDisplayNameResolver;
    }

    public void setUseCaseIdResolver(Function<String, Integer> useCaseIdResolver) {
        this.useCaseIdResolver = useCaseIdResolver == null ? this.useCaseIdResolver : useCaseIdResolver;
    }

    public void setUseCaseLabelResolver(Function<String, String> useCaseLabelResolver) {
        this.useCaseLabelResolver = useCaseLabelResolver == null ? this.useCaseLabelResolver : useCaseLabelResolver;
    }

    public void setUserFormatter(Function<User, String> userFormatter) {
        this.userFormatter = userFormatter == null ? this.userFormatter : userFormatter;
    }

    public void setUserUseCaseAssignmentValidator(BiPredicate<User, String> userUseCaseAssignmentValidator) {
        this.userUseCaseAssignmentValidator = userUseCaseAssignmentValidator == null ? this.userUseCaseAssignmentValidator : userUseCaseAssignmentValidator;
    }

    public void setRuleAssignmentResolver(BiPredicate<User, RuleCardData> ruleAssignmentResolver) {
        this.ruleAssignmentResolver = ruleAssignmentResolver == null ? this.ruleAssignmentResolver : ruleAssignmentResolver;
    }

    public void setUsersAssignedToRuleResolver(Function<RuleCardData, List<User>> usersAssignedToRuleResolver) {
        this.usersAssignedToRuleResolver = usersAssignedToRuleResolver == null ? this.usersAssignedToRuleResolver : usersAssignedToRuleResolver;
    }

    public void setMappingRefreshCallback(Supplier<CompletableFuture<Void>> mappingRefreshCallback) {
        this.mappingRefreshCallback = mappingRefreshCallback == null ? this.mappingRefreshCallback : mappingRefreshCallback;
    }

    public void setUseCasesRefreshCallback(Runnable useCasesRefreshCallback) {
        this.useCasesRefreshCallback = useCasesRefreshCallback == null ? this.useCasesRefreshCallback : useCasesRefreshCallback;
    }

    public void setChatSessionIdSupplier(Supplier<String> chatSessionIdSupplier) {
        this.chatSessionIdSupplier = chatSessionIdSupplier == null ? this.chatSessionIdSupplier : chatSessionIdSupplier;
    }

    public void onTabActivated() {
        renderMappingsWhenReady(selectedUseCaseSupplier.get());
        updateLoggingIntervalDisplay(selectedUseCaseSupplier.get());
    }

    public void onSelectedUseCaseChanged(String selectedUseCase) {
        updateRuleBuilderUseCaseDisplay(selectedUseCase);
        updateLoggingIntervalDisplay(selectedUseCase);
        updateSelectedMappingForEdit(null);
        renderMappingsWhenReady(selectedUseCase);
    }

    public void renderMappingsWhenReady(String useCase) {
        if (!isMappingsViewReady()) {
            scheduleMappingsRenderRetry(useCase);
            return;
        }

        renderMappingsForUseCase(useCase);
    }

    @FXML
    public void refreshMappings() {
        try {
            String useCase = selectedUseCaseSupplier.get();
            if (useCase == null || useCase.isBlank()) {
                throw new IllegalArgumentException("Please select a use case.");
            }
        } catch (IllegalArgumentException ex) {
            AlertUtils.showErrorAlert("Validation Error", ex.getMessage());
            return;
        }
        mappingRefreshCallback.get();
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
        renderMappingsWhenReady(selectedUseCaseSupplier.get());
    }

    @FXML
    private void onListActiveMappings() {
        showingActiveMappingsOnly = true;
        renderMappingsWhenReady(selectedUseCaseSupplier.get());
    }

    @FXML
    private void onListAllMappings() {
        showingActiveMappingsOnly = false;
        renderMappingsWhenReady(selectedUseCaseSupplier.get());
    }

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
                    mappingRefreshCallback.get();
                    String savedUseCase = useCaseLabelResolver.apply(FormatUtils.normalizeUseCaseName(ruleConfig.getType()));
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

    private SensorRuleConfig buildRuleConfigFromForm() {
        String sensorType = selectedUseCaseSupplier.get();
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

    private void changeSelectedMappingConfig() {
        if (selectedMappingForEdit == null || selectedMappingForEdit.mappingId <= 0) {
            AlertUtils.showErrorAlert("Missing Mapping", "Select an active mapping card before changing it.");
            return;
        }

        try {
            SensorRuleConfig ruleConfig = buildRuleConfigFromForm();
            ruleConfig.setId(selectedMappingForEdit.mappingId);

            Integer useCaseId = selectedUseCaseIdSupplier.get();
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
                User currentUser = selectedUserSupplier.get();
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
                selectedUseCaseSupplier.get(),
                chatSessionIdSupplier.get()
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
                selectedUseCaseSupplier.get(),
                chatSessionIdSupplier.get()
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
        mappingRefreshCallback.get();
        AlertUtils.showInfoAlert("Mapping Updated", successMessage);
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
        UseCase useCase = useCaseLookup.apply(normalizedKey);
        if (useCase != null) {
            ruleSensorTypeLabel.setText(useCase.getRawName());
            String description = useCase.getDescription();
            ruleSensorTypeDescriptionLabel.setText(description.isEmpty() ? "No description available." : description);
        } else {
            ruleSensorTypeLabel.setText(useCaseLabelResolver.apply(normalizedKey));
            ruleSensorTypeDescriptionLabel.setText("No description available.");
        }
    }

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
        User selectedUser = selectedUserSupplier.get();

        if (selectedUser != null && !showingActiveMappingsOnly) {
             ApiService.getInstance().getUserMappingHistory(selectedUser.getUserID(), useCase)
                     .thenAccept(response -> Platform.runLater(() -> renderUserMappingHistory(response, useCase, selectedUser)));
             return;
        }

        List<RuleCardData> filtered = rulesSupplier.get().stream()
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

        for (RuleCardData rule : filtered) {
            boolean assignedToSelectedUser = selectedUser == null
                    ? isRuleAssignedToAnyUser(rule)
                    : isUserAssignedToRule(selectedUser, rule);
            VBox card = new VBox(8);
            card.setUserData(rule.mappingId);
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
            if (!assignedToSelectedUser) {
                deleteButton.setDisable(true);
                deleteButton.setTooltip(new Tooltip("Inactive mapping is already deactivated"));
            }
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
            RuleCardData rule = MappingUiFactory.createRuleFromHistoryMapping(mappingNode, useCase);
            boolean selected = selectedMappingForEdit != null && selectedMappingForEdit.mappingId == rule.mappingId;
            VBox card = MappingUiFactory.createHistoryMappingCard(
                    mappingNode,
                    useCase,
                    this::selectMappingForEdit,
                    this::onDeleteMappingRequested,
                    selected
            );
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
        return usersAssignedToRuleResolver.apply(rule).stream()
                .sorted(Comparator.comparingInt(User::getUserID))
                .toList();
    }

    private boolean isUserAssignedToRule(User user, RuleCardData rule) {
        return ruleAssignmentResolver.test(user, rule);
    }

    private boolean isRuleAssignedToAnyUser(RuleCardData rule) {
        return usersSupplier.get().stream().anyMatch(user -> isUserAssignedToRule(user, rule));
    }

    private boolean isRuleActiveForMappingsView(User selectedUser, RuleCardData rule) {
        return selectedUser == null
                ? isRuleAssignedToAnyUser(rule)
                : isUserAssignedToRule(selectedUser, rule);
    }

    private void selectMappingForEdit(RuleCardData rule) {
        if (rule == null) {
            return;
        }

        updateSelectedMappingForEdit(rule);
        populateRuleBuilderFromMapping(rule);
        updateRenderedMappingSelection(rule);
    }

    private void updateRenderedMappingSelection(RuleCardData selectedRule) {
        if (mappingsFlowPane == null || selectedRule == null) {
            return;
        }

        for (Node child : mappingsFlowPane.getChildren()) {
            if (!(child instanceof Pane pane)) {
                continue;
            }
            pane.getStyleClass().remove("mapping-card-selected");
            if (Objects.equals(child.getUserData(), selectedRule.mappingId)) {
                pane.getStyleClass().add("mapping-card-selected");
            }
        }
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

        String useCaseName = useCaseDisplayNameResolver.apply(rule.useCaseLabel);
        if (useCaseName == null || useCaseName.isBlank() || "-".equals(useCaseName)) {
            useCaseName = selectedUseCaseSupplier.get();
        }

        int useCaseId = useCaseIdResolver.apply(useCaseName);
        if (useCaseId <= 0) {
            AlertUtils.showErrorAlert("Missing Use Case", "Could not resolve use case id for " + useCaseName + ".");
            return;
        }

        final String resolvedUseCaseName = useCaseName;
        List<User> candidateUsers = usersSupplier.get().stream()
                .filter(user -> userUseCaseAssignmentValidator.test(user, resolvedUseCaseName))
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
                        chatSessionIdSupplier.get()
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
        selectedUseCaseConsumer.accept(useCaseName);
        mappingRefreshCallback.get();
    }

    private void onDeleteMappingRequested(RuleCardData rule) {
        if (rule == null || rule.mappingId <= 0) {
            AlertUtils.showErrorAlert("Delete Failed", "Could not resolve mapping id for the selected card.");
            return;
        }

        String label = (rule.rangeLabel == null || rule.rangeLabel.isBlank())
            ? "selected mapping"
            : rule.rangeLabel;

        ApiService.getInstance().deleteMapping(rule.mappingId)
            .thenAccept(success -> Platform.runLater(() -> {
                if (!success) {
                    AlertUtils.showErrorAlert("Delete Failed", "Could not delete mapping for " + label + ".");
                    return;
                }
                AlertUtils.showInfoAlert("Mapping Deleted", "Deleted mapping for " + label + ".");
                mappingRefreshCallback.get();
            }))
            .exceptionally(ex -> {
                AlertUtils.showErrorAlert("Delete Failed", "Failed to delete mapping: " + ex.getMessage());
                return null;
            });
    }

    @FXML
    private void onEditLoggingInterval() {
        String selectedUseCaseName = selectedUseCaseSupplier.get();
        if (selectedUseCaseName == null || selectedUseCaseName.isBlank()) {
            AlertUtils.showErrorAlert("Missing Use Case", "Please select a use case before editing logging interval.");
            return;
        }

        String selectedKey = FormatUtils.normalizeUseCaseName(selectedUseCaseName);
        UseCase useCase = useCaseLookup.apply(selectedKey);
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
                useCasesRefreshCallback.run();
            }))
            .exceptionally(ex -> {
                AlertUtils.showErrorAlert("Update Failed", "Failed to update logging interval: " + ex.getMessage());
                return null;
            });
    }

    private void updateSelectedMappingForEdit(RuleCardData rule) {
        selectedMappingForEdit = rule;

        if (selectedMappingLabel != null) {
            selectedMappingLabel.setText(rule == null ? "None selected" : "Editing ID " + rule.mappingId);
        }
    }

    private void updateLoggingIntervalDisplay(String selectedUseCase) {
        if (loggingIntervalValueLabel == null) {
            return;
        }

        String displayText = "Not set";
        if (selectedUseCase != null && !selectedUseCase.isBlank()) {
            String normalizedKey = FormatUtils.normalizeUseCaseName(selectedUseCase);
            UseCase useCase = useCaseLookup.apply(normalizedKey);
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
            Integer useCaseId = selectedUseCaseIdSupplier.get();
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
        List.of(
                ruleMinValueField,
                ruleMaxValueField,
                ruleMinPulsesField,
                ruleMaxPulsesField,
                ruleMinIntensityField,
                ruleMaxIntensityField,
                ruleMinDurationField,
                ruleMaxDurationField,
                ruleMinIntervalField,
                ruleMaxIntervalField
        ).forEach(TextInputControl::clear);
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

    private String formatMappingCardTitle(RuleCardData rule) {
        if (rule == null) {
            return "Unknown: -";
        }

        String useCaseName = rule.useCaseLabel == null || rule.useCaseLabel.isBlank()
            ? useCaseLabelResolver.apply(rule.useCaseKey)
            : rule.useCaseLabel.trim();
        return useCaseName + ": " + (rule.mappingId > 0 ? rule.mappingId : "-");
    }

    private String formatMappingValues(RuleCardData rule) {
        if (rule == null) {
            return "-";
        }
        return rule.minValue + "-" + rule.maxValue;
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

    private String formatUser(User user) {
        return userFormatter.apply(user);
    }
}
