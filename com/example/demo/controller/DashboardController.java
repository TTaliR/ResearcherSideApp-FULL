package com.example.demo.controller;

import com.example.demo.controller.state.DashboardState;
import com.example.demo.model.AgentChatSession;
import com.example.demo.model.User;
import com.example.demo.model.UserUseCaseMapping;
import com.example.demo.model.RuleCardData;
import com.example.demo.model.UseCase;
import com.example.demo.factory.UserDialogFactory;
import com.example.demo.parser.MappingConfigParser;
import com.example.demo.service.ApiService;
import com.example.demo.util.AlertUtils;
import com.example.demo.util.ButtonLoadingState;
import com.example.demo.util.FormatUtils;
import com.fasterxml.jackson.databind.JsonNode;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.*;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class DashboardController {
    private static final List<String> DEFAULT_USE_CASES = List.of("HeartRate", "MoonAzimuth", "SunAzimuth", "Pollution");

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
    private MappingsTabController mappingsTabContentController;

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

    private final DashboardState state = DashboardState.getInstance();
    private final ObservableList<User> users = FXCollections.observableArrayList();
    private final ObservableList<String> useCases = FXCollections.observableArrayList();
    private final ObservableList<AgentChatSession> chatSessions = FXCollections.observableArrayList();
    private final Map<String, UseCase> useCaseRegistry = new HashMap<>();
    private final List<RuleCardData> allRules = new ArrayList<>();
    private final Map<String, AgentChatSession> activeChatSessionsByUseCase = new HashMap<>();
    private AgentChatSession fallbackChatSession;
    private String activeMonitoringTypeKey = "";

    private JsonNode latestGraphData;

    @FXML
    private void initialize() {
        initializeSubcontrollers();
        setupTabs();
        setupAgentChatTab();
        setupGraphTab();
        setupMappingsTab();
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
        leftSidebarController.setOnAddUserRequested(this::onAddUserRequested);
        leftSidebarController.setOnAssignUserUseCaseRequested(this::onAssignUserUseCase);
        leftSidebarController.setOnEditUserRequested(this::onEditUserRequested);
        leftSidebarController.setOnChatSessionSelected(this::selectChatSession);
        leftSidebarController.setOnNewChatSessionRequested(this::startNewChatSessionForUseCase);
        leftSidebarController.setOnViewAllChatSessionsRequested(this::showAllChatSessionsForUseCase);

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
                renderMappingsWhenReadyInTab(state.getSelectedUseCase());
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
            renderMappingsWhenReadyInTab(state.getSelectedUseCase());
            scheduleGraphUpdate();
        });

        topBarController.setOnGraphUpdateRequested(this::scheduleGraphUpdate);
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

        yellowBookTabContentController.setChatSessionId(getCurrentChatSessionId());
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

    private String getCurrentChatSessionId() {
        return getOrCreateCurrentChatSession().getSessionId();
    }

    private AgentChatSession getOrCreateCurrentChatSession() {
        String selectedUseCase = state.getSelectedUseCase();
        String key = normalizeChatSessionUseCaseKey(selectedUseCase);
        if (key == null || key.isBlank()) {
            if (fallbackChatSession == null) {
                fallbackChatSession = createChatSession("Current Context", "currentcontext");
            }
            return fallbackChatSession;
        }
        return activeChatSessionsByUseCase.computeIfAbsent(key, ignored -> createChatSession(selectedUseCase, key));
    }

    private void startNewChatSessionForUseCase(String useCaseName) {
        String selectedUseCase = useCaseName == null || useCaseName.isBlank()
                ? state.getSelectedUseCase()
                : useCaseName;
        String key = normalizeChatSessionUseCaseKey(selectedUseCase);
        AgentChatSession session;
        if (key == null || key.isBlank()) {
            session = createChatSession("Current Context", "currentcontext");
            fallbackChatSession = session;
        } else {
            session = createChatSession(selectedUseCase, key);
            activeChatSessionsByUseCase.put(key, session);
        }
        refreshSidebarChatSessions();

        String currentKey = normalizeChatSessionUseCaseKey(state.getSelectedUseCase());
        if (!key.isBlank() && !key.equals(currentKey)) {
            state.setSelectedUseCase(session.getUseCaseName());
        }

        if (yellowBookTabContentController != null) {
            yellowBookTabContentController.setChatSessionId(session.getSessionId());
        }
        if (agentChatTabContentController != null) {
            agentChatTabContentController.showChatSession(session, "Started a new session for "
                    + (selectedUseCase == null || selectedUseCase.isBlank() ? "the current context" : selectedUseCase)
                    + ".");
        }
    }

    private AgentChatSession createChatSession(String useCaseName, String useCaseKey) {
        String displayUseCase = useCaseName == null || useCaseName.isBlank() ? "Current Context" : useCaseName;
        String key = useCaseKey == null || useCaseKey.isBlank()
                ? FormatUtils.normalizeUseCaseName(displayUseCase)
                : useCaseKey;
        long sessionNumber = chatSessions.stream()
                .filter(session -> key.equals(session.getUseCaseKey()))
                .count() + 1;
        AgentChatSession session = new AgentChatSession(
                UUID.randomUUID().toString(),
                displayUseCase,
                key,
                "Session " + sessionNumber,
                LocalDateTime.now()
        );
        chatSessions.add(session);
        refreshSidebarChatSessions();
        return session;
    }

    private String normalizeChatSessionUseCaseKey(String useCaseName) {
        if (useCaseName == null || useCaseName.isBlank() || "-".equals(useCaseName.trim())) {
            return "";
        }
        return FormatUtils.normalizeUseCaseName(useCaseName);
    }

    private void selectChatSession(AgentChatSession session) {
        if (session == null) {
            return;
        }

        activeChatSessionsByUseCase.put(session.getUseCaseKey(), session);
        if (yellowBookTabContentController != null) {
            yellowBookTabContentController.setChatSessionId(session.getSessionId());
        }

        String selectedKey = normalizeChatSessionUseCaseKey(state.getSelectedUseCase());
        if (!session.getUseCaseKey().equals(selectedKey)) {
            state.setSelectedUseCase(session.getUseCaseName());
            return;
        }

        if (agentChatTabContentController != null) {
            agentChatTabContentController.selectChatSession(session);
        }
    }

    private void showAllChatSessionsForUseCase(String useCaseName) {
        String key = normalizeChatSessionUseCaseKey(useCaseName);
        if (!key.isBlank() && !key.equals(normalizeChatSessionUseCaseKey(state.getSelectedUseCase()))) {
            state.setSelectedUseCase(useCaseName);
        }

        if (workspaceTabPane != null && agentChatTab != null) {
            workspaceTabPane.getSelectionModel().select(agentChatTab);
        }

        if (agentChatTabContentController != null) {
            agentChatTabContentController.showSessionHistoryList(
                    useCaseName,
                    chatSessionsForUseCase(useCaseName),
                    this::selectChatSession
            );
        }
    }

    private List<AgentChatSession> chatSessionsForUseCase(String useCaseName) {
        String key = normalizeChatSessionUseCaseKey(useCaseName);
        return chatSessions.stream()
                .filter(session -> key.equals(session.getUseCaseKey()))
                .toList();
    }

    private void refreshSidebarChatSessions() {
        if (leftSidebarController != null) {
            leftSidebarController.setChatSessions(List.copyOf(chatSessions));
        }
    }

    private String requireSelectedUsecaseName() {
        String usecaseName = getSelectedUsecaseName();
        if (usecaseName.isBlank()) {
            throw new IllegalArgumentException("Select a use case first.");
        }
        return usecaseName;
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

        ButtonLoadingState loading = ButtonLoadingState.start(leftSidebarController.getAddUseCaseButton(), "Creating...");
        ApiService.getInstance().createUseCase(name, description)
                .thenAccept(response -> Platform.runLater(() -> {
                    loading.close();
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
                    Platform.runLater(() -> {
                            loading.close();
                            AlertUtils.showErrorAlert("Create Failed", "Failed to create use case: " + ex.getMessage());
                    });
                    return null;
                });
    }

    private void setupTabs() {
        workspaceTabPane.getSelectionModel().select(mappingsTab);
        workspaceTabPane.getSelectionModel().selectedItemProperty().addListener(onNewValueChanged(newValue -> {
            if (newValue == graphTab) {
                scheduleGraphUpdate();
            } else if (newValue == mappingsTab) {
                if (mappingsTabContentController != null) {
                    mappingsTabContentController.onTabActivated();
                }
            } else if (newValue == agentChatTab) {
                if (agentChatTabContentController != null) {
                    agentChatTabContentController.onTabActivated();
                }
            } else if (newValue == yellowBookTab) {
                yellowBookTabContentController.loadDictionary();
            } else if (newValue == schedulingTab) {
                if (schedulesTabContentController != null) {
                    schedulesTabContentController.refreshSchedulesForCurrentFilter();
                }
            }
        }));
    }

    private void setupMappingsTab() {
        if (mappingsTabContentController == null) {
            return;
        }

        mappingsTabContentController.setSelectedUseCaseSupplier(state::getSelectedUseCase);
        mappingsTabContentController.setSelectedUseCaseIdSupplier(state::getSelectedUseCaseId);
        mappingsTabContentController.setSelectedUseCaseConsumer(state::setSelectedUseCase);
        mappingsTabContentController.setSelectedUserSupplier(() -> topBarController == null ? null : topBarController.getSelectedUser());
        mappingsTabContentController.setUsersSupplier(() -> List.copyOf(users));
        mappingsTabContentController.setRulesSupplier(() -> List.copyOf(allRules));
        mappingsTabContentController.setUseCaseLookup(useCaseRegistry::get);
        mappingsTabContentController.setUseCaseDisplayNameResolver(this::resolveUseCaseDisplayName);
        mappingsTabContentController.setUseCaseIdResolver(this::resolveUseCaseId);
        mappingsTabContentController.setUseCaseLabelResolver(this::toUseCaseLabel);
        mappingsTabContentController.setUserFormatter(this::formatUser);
        mappingsTabContentController.setUserUseCaseAssignmentValidator(this::isUserAssignedToUseCase);
        mappingsTabContentController.setRuleAssignmentResolver(this::isUserAssignedToRule);
        mappingsTabContentController.setUsersAssignedToRuleResolver(this::findUsersAssignedToRule);
        mappingsTabContentController.setMappingRefreshCallback(this::refreshMappingData);
        mappingsTabContentController.setUseCasesRefreshCallback(this::loadUseCases);
        mappingsTabContentController.setChatSessionIdSupplier(this::getCurrentChatSessionId);
        mappingsTabContentController.onSelectedUseCaseChanged(state.getSelectedUseCase());
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
        agentChatTabContentController.setMappingsRefreshCallback(() -> {
            if (mappingsTabContentController != null) {
                mappingsTabContentController.refreshMappings();
            }
        });
        agentChatTabContentController.setChatSessionIdSupplier(this::getCurrentChatSessionId);
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
                schedulesTabContentController.refreshSchedulesForCurrentFilter();
            }
        }));

        state.selectedUsersProperty().addListener(onNewValueChanged(newValue -> {
            refreshRuleSummary();
            if (schedulesTabContentController != null) {
                schedulesTabContentController.syncSelectedUser(newValue);
            }
            renderMappingsWhenReadyInTab(state.getSelectedUseCase());
            if (schedulesTabContentController != null) {
                schedulesTabContentController.refreshSchedulesForCurrentFilter();
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
                        renderMappingsWhenReadyInTab(state.getSelectedUseCase());
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

        ButtonLoadingState loading = ButtonLoadingState.start(leftSidebarController.getAssignUserUseCaseButton(), "Assigning...");
        ApiService.getInstance().assignUseCaseToUser(selectedUser.getUserID(), selectedUsecaseId)
                .thenAccept(success -> Platform.runLater(() -> {
                    loading.close();
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
                        loading.close();
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
        AgentChatSession session = getOrCreateCurrentChatSession();
        if (yellowBookTabContentController != null) {
            yellowBookTabContentController.setChatSessionId(session.getSessionId());
        }

        if (leftSidebarController.getUseCaseListView() != null && selectedUseCase != null && !selectedUseCase.isBlank()) {
            String current = leftSidebarController.getUseCaseListView().getSelectionModel().getSelectedItem();
            if (!selectedUseCase.equals(current)) {
                leftSidebarController.getUseCaseListView().getSelectionModel().select(selectedUseCase);
            }
        }

        refreshUsersForSelectedUseCase(null);
        refreshRuleSummary();
        if (mappingsTabContentController != null) {
            mappingsTabContentController.onSelectedUseCaseChanged(selectedUseCase);
        }
        if (agentChatTabContentController != null) {
            agentChatTabContentController.showChatSession(session, "Selected use case: " + selectedUseCase);
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
                        renderMappingsWhenReadyInTab(state.getSelectedUseCase());
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

    private void renderMappingsWhenReadyInTab(String useCase) {
        if (mappingsTabContentController != null) {
            mappingsTabContentController.renderMappingsWhenReady(useCase);
        }
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

    private void onAddUserRequested() {
        Optional<UserDialogFactory.AddUserInput> result = UserDialogFactory.showAddUserDialog();
        if (result.isEmpty()) {
            return;
        }

        UserDialogFactory.AddUserInput input = result.get();
        int userId = input.getUserId();
        String firstName = input.getFirstName();
        String lastName = input.getLastName();

        if (findUserById(userId) != null) {
            AlertUtils.showErrorAlert("User Already Exists", "User " + userId + " already exists. Use the edit button to update their name.");
            return;
        }

        ApiService.getInstance().addUser(userId, firstName, lastName)
            .thenAccept(success -> Platform.runLater(() -> {
                if (!success) {
                    AlertUtils.showErrorAlert("Add User Failed", "Could not add the user. Please try again.");
                    return;
                }

                String displayName = (firstName + " " + lastName).trim();
                String message = displayName.isEmpty()
                    ? "Added user " + userId + "."
                    : "Added user " + userId + " - " + displayName + ".";
                AlertUtils.showInfoAlert("User Added", message);
                loadUserss();
            }))
            .exceptionally(ex -> {
                Platform.runLater(() -> AlertUtils.showErrorAlert("Add User Failed", "Failed to add user: " + ex.getMessage()));
                return null;
            });
    }

    private void onEditUserRequested(User user) {
        if (user == null) {
            return;
        }

        Optional<UserDialogFactory.EditUserInput> result = UserDialogFactory.showEditUserDialog(user);
        if (result.isEmpty()) {
            return;
        }

        String newFirstName = result.get().getFirstName();
        String newLastName = result.get().getLastName();
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
                Platform.runLater(() -> AlertUtils.showErrorAlert("Update Failed", "Failed to update user name: " + ex.getMessage()));
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
