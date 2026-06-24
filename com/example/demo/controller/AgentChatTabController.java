package com.example.demo.controller;

import com.example.demo.factory.ChatBubbleFactory;
import com.example.demo.model.AgentChatSession;
import com.example.demo.model.ChatCommandOption;
import com.example.demo.model.RuleCardData;
import com.example.demo.model.UseCase;
import com.example.demo.model.User;
import com.example.demo.service.ApiService;
import com.example.demo.util.AlertUtils;
import com.example.demo.util.FormatUtils;
import com.fasterxml.jackson.databind.JsonNode;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.scene.web.WebView;
import javafx.stage.Popup;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class AgentChatTabController {
    private static final String DEFAULT_CHAT_COMMAND_PREFIX = "$";
    private static final int MAX_RECENT_SESSION_MESSAGES = 12;
    private static final List<ChatCommandOption> CHAT_COMMAND_OPTIONS = createChatCommandOptions();
    private static final Map<String, ChatCommandOption> CHAT_COMMAND_OPTIONS_BY_COMMAND = createChatCommandOptionMap();

    @FXML
    private ScrollPane chatScrollPane;
    @FXML
    private VBox chatHistoryBox;
    @FXML
    private FlowPane promptSuggestionsPane;
    @FXML
    private ListView<ChatCommandOption> chatCommandDropdownList;
    @FXML
    private TextField chatInputField;
    @FXML
    private Button chatSendButton;
    private Label chatCommandHintLabel;
    @FXML
    private Button contextDrawerToggleButton;
    @FXML
    private Label agentTypingLabel;
    @FXML
    private ContextDrawerController contextDrawerContentController;

    private Popup chatCommandPopup;

    private Supplier<String> selectedUseCaseSupplier = () -> null;
    private Supplier<Integer> selectedUseCaseIdSupplier = () -> null;
    private Consumer<Integer> selectedUseCaseIdConsumer = ignored -> {};
    private Supplier<User> selectedUserSupplier = () -> null;
    private Function<String, UseCase> useCaseLookup = ignored -> null;
    private Function<User, String> userFormatter = user -> user == null ? "" : String.valueOf(user.getUserID());
    private BiPredicate<User, String> userUseCaseAssignmentValidator = (user, useCase) -> false;
    private BiConsumer<User, String> userUseCaseContextApplier = (user, useCase) -> {};
    private Consumer<Integer> refreshUsersForSelectedUseCaseCallback = ignored -> {};
    private Supplier<List<RuleCardData>> rulesSupplier = List::of;
    private Function<RuleCardData, List<User>> usersAssignedToRuleResolver = rule -> List.of();
    private Supplier<CompletableFuture<Void>> mappingRefreshCallback = () -> CompletableFuture.completedFuture(null);
    private Supplier<String> chatSessionIdSupplier = () -> "";
    private BooleanSupplier allUsersSelectedSupplier = () -> false;
    private final List<Map<String, String>> recentSessionMessages = new ArrayList<>();
    private final Map<String, List<Map<String, String>>> messagesBySessionId = new LinkedHashMap<>();
    private String activeSessionId = "";
    private AgentChatSession activeSession;

    public void setSelectedUseCaseSupplier(Supplier<String> selectedUseCaseSupplier) {
        this.selectedUseCaseSupplier = selectedUseCaseSupplier == null ? () -> null : selectedUseCaseSupplier;
        if (contextDrawerContentController != null) {
            contextDrawerContentController.setSelectedUseCaseSupplier(this.selectedUseCaseSupplier);
        }
    }

    public void setSelectedUseCaseIdSupplier(Supplier<Integer> selectedUseCaseIdSupplier) {
        this.selectedUseCaseIdSupplier = selectedUseCaseIdSupplier == null ? () -> null : selectedUseCaseIdSupplier;
    }

    public void setSelectedUseCaseIdConsumer(Consumer<Integer> selectedUseCaseIdConsumer) {
        this.selectedUseCaseIdConsumer = selectedUseCaseIdConsumer == null ? ignored -> {} : selectedUseCaseIdConsumer;
    }

    public void setSelectedUserSupplier(Supplier<User> selectedUserSupplier) {
        this.selectedUserSupplier = selectedUserSupplier == null ? () -> null : selectedUserSupplier;
        if (contextDrawerContentController != null) {
            contextDrawerContentController.setSelectedUserSupplier(this.selectedUserSupplier);
        }
    }

    public void setAllUsersSelectedSupplier(BooleanSupplier allUsersSelectedSupplier) {
        this.allUsersSelectedSupplier =
                allUsersSelectedSupplier == null ? () -> false : allUsersSelectedSupplier;

        if (contextDrawerContentController != null) {
            contextDrawerContentController.setAllUsersSelectedSupplier(
                    this.allUsersSelectedSupplier
            );
        }
    }

    public void setUseCaseLookup(Function<String, UseCase> useCaseLookup) {
        this.useCaseLookup = useCaseLookup == null ? ignored -> null : useCaseLookup;
    }

    public void setUserFormatter(Function<User, String> userFormatter) {
        this.userFormatter = userFormatter == null ? user -> user == null ? "" : String.valueOf(user.getUserID()) : userFormatter;
    }

    public void setUserUseCaseAssignmentValidator(BiPredicate<User, String> validator) {
        this.userUseCaseAssignmentValidator = validator == null ? (user, useCase) -> false : validator;
    }

    public void setUserUseCaseContextApplier(BiConsumer<User, String> applier) {
        this.userUseCaseContextApplier = applier == null ? (user, useCase) -> {} : applier;
    }

    public void setRefreshUsersForSelectedUseCaseCallback(Consumer<Integer> callback) {
        this.refreshUsersForSelectedUseCaseCallback = callback == null ? ignored -> {} : callback;
    }

    public void setRulesSupplier(Supplier<List<RuleCardData>> rulesSupplier) {
        this.rulesSupplier = rulesSupplier == null ? List::of : rulesSupplier;
        if (contextDrawerContentController != null) {
            contextDrawerContentController.setRulesSupplier(this.rulesSupplier);
        }
    }

    public void setRuleAssignmentResolver(BiPredicate<User, RuleCardData> resolver) {
        if (contextDrawerContentController != null) {
            contextDrawerContentController.setRuleAssignmentResolver(resolver);
        }
    }

    public void setUsersAssignedToRuleResolver(Function<RuleCardData, List<User>> resolver) {
        this.usersAssignedToRuleResolver = resolver == null ? rule -> List.of() : resolver;
    }

    public void setMappingRefreshCallback(Supplier<CompletableFuture<Void>> callback) {
        this.mappingRefreshCallback = callback == null ? () -> CompletableFuture.completedFuture(null) : callback;
    }

    public void setGraphRefreshCallback(Runnable callback) {
        if (contextDrawerContentController != null) {
            contextDrawerContentController.setGraphRefreshCallback(callback);
        }
    }

    public void setMappingsRefreshCallback(Runnable callback) {
        if (contextDrawerContentController != null) {
            contextDrawerContentController.setMappingsRefreshCallback(callback);
        }
    }

    public void setChatSessionIdSupplier(Supplier<String> supplier) {
        this.chatSessionIdSupplier = supplier == null ? () -> "" : supplier;
    }

    public void selectChatSession(AgentChatSession session) {
        if (session == null) {
            return;
        }
        showChatSession(session, null);
    }

    public void initializeChat() {
        setupChatShortcuts();
        setupChatAutocomplete();
        chatSendButton.setOnAction(ignored -> onSendMessage());
        chatInputField.setOnAction(ignored -> onSendMessage());

        chatHistoryBox.heightProperty().addListener((observable, oldValue, newValue) -> chatScrollPane.setVvalue(1.0));
        chatInputField.textProperty().addListener((obs, oldValue, newValue) -> {
            updateChatShortcutHint(newValue);
            updateChatCommandSuggestions(newValue);
        });
        chatInputField.addEventFilter(KeyEvent.KEY_PRESSED, this::handleChatCommandKeyPress);

        setAgentTyping(false);
        if (contextDrawerContentController != null) {
            contextDrawerContentController.initializeDrawer();
        }
        contextDrawerToggleButton.setText("Show Context");
        contextDrawerToggleButton.setOnAction(ignored -> {
            boolean nextVisible = contextDrawerContentController == null || !contextDrawerContentController.isVisible();
            setContextDrawerVisible(nextVisible);
        });
    }

    public void onTabActivated() {
        refreshRuleSummary();
        if (contextDrawerContentController != null) {
            contextDrawerContentController.renderLatestMiniGraph();
        }
    }

    public void updateUseCaseLabel(String selectedUseCase) {
        if (contextDrawerContentController != null) {
            contextDrawerContentController.updateUseCaseLabel(selectedUseCase);
        }
    }

    public void updateUsersLabel(String text) {
        if (contextDrawerContentController != null) {
            contextDrawerContentController.updateUsersLabel(text);
        }
    }

    public void clearMessages() {
        if (chatHistoryBox != null) {
            chatHistoryBox.getChildren().clear();
        }
        if (promptSuggestionsPane != null) {
            promptSuggestionsPane.getChildren().clear();
            promptSuggestionsPane.setVisible(false);
            promptSuggestionsPane.setManaged(false);
        }
        recentSessionMessages.clear();
    }

    public void showChatSession(AgentChatSession session, String emptySessionMessage) {
        if (session == null) {
            return;
        }

        activeSessionId = session.getSessionId();
        activeSession = session;
        setConversationControlsEnabled(true);

        List<Map<String, String>> stored = messagesBySessionId.computeIfAbsent(activeSessionId, ignored -> new ArrayList<>());
        renderStoredMessages(stored);
        if (!hasUserMessage(stored)) {
            if (stored.isEmpty()) {
                renderChatMessage(buildDefaultConversationMessage(emptySessionMessage), false);
            }
            showPromptSuggestions();
        }
    }

    public void showSessionHistoryList(String useCaseName, List<AgentChatSession> sessions,
                                       Consumer<AgentChatSession> onSessionSelected) {
        if (chatHistoryBox == null) {
            return;
        }

        activeSession = null;
        activeSessionId = "";
        setConversationControlsEnabled(false);
        chatHistoryBox.getChildren().clear();
        if (promptSuggestionsPane != null) {
            promptSuggestionsPane.getChildren().clear();
            promptSuggestionsPane.setVisible(false);
            promptSuggestionsPane.setManaged(false);
        }
        recentSessionMessages.clear();

        Label title = new Label("Sessions for " + (useCaseName == null || useCaseName.isBlank() ? "this use case" : useCaseName));
        title.getStyleClass().add("session-history-title");

        Label subtitle = new Label("Select a previous session to reopen its conversation in this chat view.");
        subtitle.getStyleClass().add("session-history-subtitle");
        subtitle.setWrapText(true);

        chatHistoryBox.getChildren().addAll(title, subtitle);

        List<AgentChatSession> sortedSessions = new ArrayList<>(sessions == null ? List.of() : sessions);
        sortedSessions.sort(Comparator.comparing(AgentChatSession::getCreatedAt).reversed());
        if (sortedSessions.isEmpty()) {
            Label empty = new Label("No sessions have been started for this use case yet.");
            empty.getStyleClass().add("session-history-subtitle");
            chatHistoryBox.getChildren().add(empty);
            return;
        }

        for (AgentChatSession session : sortedSessions) {
            chatHistoryBox.getChildren().add(createSessionHistoryRow(session, onSessionSelected));
        }
    }

    public void addSystemMessage(String message) {
        addChatMessage(message, false);
        showPromptSuggestions();
    }

    public void refreshRuleSummary() {
        if (contextDrawerContentController != null) {
            contextDrawerContentController.refreshRuleSummary();
        }
    }

    public void renderMiniGraphFromData(JsonNode dataArray) {
        if (contextDrawerContentController != null) {
            contextDrawerContentController.renderMiniGraphFromData(dataArray);
        }
    }

    public void showMiniGraphLoading(boolean visible) {
        if (contextDrawerContentController != null) {
            contextDrawerContentController.showMiniGraphLoading(visible);
        }
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

    private void onSendMessage() {
        String message = chatInputField.getText() == null ? "" : chatInputField.getText().trim();
        if (message.isEmpty()) {
            return;
        }

        hidePromptSuggestions();

        String selectedUseCase = selectedUseCaseSupplier.get();
        Integer useCaseId = selectedUseCaseIdSupplier.get();

        if ((useCaseId == null || useCaseId <= 0) && selectedUseCase != null && !selectedUseCase.isBlank()) {
            String normalized = FormatUtils.normalizeUseCaseName(selectedUseCase);
            UseCase useCase = useCaseLookup.apply(normalized);
            if (useCase != null) {
                useCaseId = useCase.getId();
            }

            if (useCaseId != null && useCaseId > 0) {
                selectedUseCaseIdConsumer.accept(useCaseId);
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
        User selectedUser = selectedUserSupplier.get();
        boolean allUsersSelected = allUsersSelectedSupplier.getAsBoolean();
        if (selectedUser != null && !userUseCaseAssignmentValidator.test(selectedUser, selectedUseCase)) {
            AlertUtils.showErrorAlert("Invalid User", "Please select a user assigned to " + selectedUseCase + " before chatting.");
            refreshUsersForSelectedUseCaseCallback.accept(null);
            return;
        }
        userUseCaseContextApplier.accept(selectedUser, selectedUseCase);

        Map<String, Object> payload = new HashMap<>();
        payload.put("message", expandedMessage);
        payload.put("session_id", chatSessionIdSupplier.get());
        payload.put("usecase_id", useCaseId);
        payload.put("usecase_name", selectedUseCase);
        payload.put("all_users_selected", allUsersSelected);
        payload.put(
                "analysis_scope",
                allUsersSelected ? "usecase" : "participant"
        );
        payload.put("context", buildSessionContext(
                selectedUseCase,
                useCaseId,
                selectedUser,
                allUsersSelected,
                expandedMessage
        ));
        if (selectedUser != null) {
            payload.put("user_id", selectedUser.getUserID());
            payload.put("user_name", userFormatter.apply(selectedUser));
            payload.put("user_usecase_id", selectedUser.getUsecaseId());
            payload.put("user_usecase_name", selectedUser.getUsecaseName());
            payload.put("user_mapping_id", selectedUser.getMappingId());
        }

        addChatMessage(message, true);
        chatInputField.clear();

        setAgentTyping(true);

        ApiService.getInstance().postWithResponse(ApiService.EP_CHAT_CONFIG, payload)
                .thenAccept(response -> {
                    CompletableFuture<Void> refresh = mappingListRequest
                            ? mappingRefreshCallback.get()
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
            addChatMessage(reply, false);
        } else {
            addChatMessage("AI response did not include reply field.", false);
        }
    }

    private Map<String, Object> buildSessionContext(String selectedUseCase, Integer useCaseId, User selectedUser,
                                                    boolean allUsersSelected, String currentRequest) {
        List<Map<String, String>> recentConversation = buildRecentConversationSnapshot();

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("selected_usecase_id", useCaseId);
        context.put("selected_usecase_name", selectedUseCase);
        context.put("all_users_selected", allUsersSelected);
        context.put("analysis_scope", allUsersSelected ? "usecase" : "participant");
        context.put("recent_conversation", recentConversation);
        context.put("session_memory", buildSessionMemory(currentRequest, recentConversation));

        if (selectedUser != null) {
            context.put("selected_user_id", selectedUser.getUserID());
            context.put("selected_user_name", userFormatter.apply(selectedUser));
            context.put("selected_user_usecase_id", selectedUser.getUsecaseId());
            context.put("selected_user_usecase_name", selectedUser.getUsecaseName());
            context.put("selected_user_mapping_id", selectedUser.getMappingId());
        }

        return context;
    }

    private Map<String, Object> buildSessionMemory(String currentRequest, List<Map<String, String>> recentConversation) {
        Map<String, Object> memory = new LinkedHashMap<>();
        memory.put("current_request", currentRequest == null ? "" : currentRequest.trim());
        memory.put("recent_conversation", recentConversation == null ? List.of() : recentConversation);
        memory.put("previous_user_request", findLastConversationContent(recentConversation, "user"));
        memory.put("previous_assistant_reply", findLastConversationContent(recentConversation, "assistant"));
        return memory;
    }

    private String findLastConversationContent(List<Map<String, String>> conversation, String role) {
        if (conversation == null || role == null || role.isBlank()) {
            return "";
        }

        for (int i = conversation.size() - 1; i >= 0; i--) {
            Map<String, String> item = conversation.get(i);
            if (item == null) {
                continue;
            }

            String itemRole = item.getOrDefault("role", "");
            if (role.equalsIgnoreCase(itemRole)) {
                return item.getOrDefault("content", "");
            }
        }

        return "";
    }

    private List<Map<String, String>> buildRecentConversationSnapshot() {
        List<Map<String, String>> snapshot = new ArrayList<>(recentSessionMessages);
        int fromIndex = Math.max(0, snapshot.size() - MAX_RECENT_SESSION_MESSAGES);
        return List.copyOf(snapshot.subList(fromIndex, snapshot.size()));
    }

    private void rememberConversationMessage(String role, String content) {
        String normalizedRole = role == null ? "" : role.trim();
        String normalizedContent = content == null ? "" : content.trim();
        if (normalizedRole.isEmpty() || normalizedContent.isEmpty()) {
            return;
        }

        recentSessionMessages.add(createConversationMessage(normalizedRole, normalizedContent));
        while (recentSessionMessages.size() > MAX_RECENT_SESSION_MESSAGES) {
            recentSessionMessages.remove(0);
        }
    }

    private Map<String, String> createConversationMessage(String role, String content) {
        Map<String, String> message = new LinkedHashMap<>();
        message.put("role", role);
        message.put("content", content == null ? "" : content);
        return message;
    }

    private boolean isMappingListRequest(String originalMessage, String expandedMessage) {
        String original = originalMessage == null
                ? ""
                : originalMessage.trim().toLowerCase(Locale.ROOT);

        String expanded = expandedMessage == null
                ? ""
                : expandedMessage.trim().toLowerCase(Locale.ROOT);

        // The dedicated command explicitly requests the full mapping list
        // with its assignment summary.
        if (original.startsWith("$mapping")) {
            return true;
        }

        return isExplicitMappingListText(original)
                || isExplicitMappingListText(expanded);
    }

    private boolean isExplicitMappingListText(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }

        // These are analysis or summary requests, not raw mapping-list requests.
        if (text.contains("summarize")
                || text.contains("summary")
                || text.contains("explain")
                || text.contains("review")
                || text.contains("compare")
                || text.contains("analyze")) {
            return false;
        }

        boolean explicitListVerb =
                text.startsWith("show ")
                        || text.startsWith("list ")
                        || text.startsWith("display ")
                        || text.startsWith("give me ");

        return explicitListVerb
                && text.contains("mapping");
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
        List<RuleCardData> filtered = rulesSupplier.get().stream()
                .filter(rule -> selectedUseCaseKey.equals(rule.useCaseKey))
                .toList();
        if (filtered.isEmpty()) {
            return "";
        }

        StringBuilder summary = new StringBuilder("Assigned users by mapping:\n");
        for (RuleCardData rule : filtered) {
            List<User> assignedUsers = usersAssignedToRuleResolver.apply(rule).stream()
                    .sorted(Comparator.comparingInt(User::getUserID))
                    .toList();
            summary.append("- ID ")
                    .append(rule.mappingId)
                    .append(": ")
                    .append(assignedUsers.size())
                    .append(assignedUsers.size() == 1 ? " user" : " users");

            if (!assignedUsers.isEmpty()) {
                summary.append(" - ")
                        .append(assignedUsers.stream()
                                .map(userFormatter)
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
        storeConversationMessage(userMessage ? "user" : "assistant", text);
        WebView bubble = ChatBubbleFactory.createChatBubbleView(text, userMessage);

        HBox row = new HBox(bubble);
        row.getStyleClass().add("chat-message-row");
        row.setFillHeight(false);
        row.setAlignment(userMessage ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        chatHistoryBox.getChildren().add(row);
        rememberConversationMessage(userMessage ? "user" : "assistant", text);
    }

    private void renderStoredMessages(List<Map<String, String>> messages) {
        if (chatHistoryBox != null) {
            chatHistoryBox.getChildren().clear();
        }
        if (promptSuggestionsPane != null) {
            promptSuggestionsPane.getChildren().clear();
            promptSuggestionsPane.setVisible(false);
            promptSuggestionsPane.setManaged(false);
        }
        recentSessionMessages.clear();
        if (messages == null || messages.isEmpty()) {
            return;
        }
        for (Map<String, String> message : messages) {
            String role = message == null ? "" : message.getOrDefault("role", "");
            String content = message == null ? "" : message.getOrDefault("content", "");
            renderChatMessage(content, "user".equalsIgnoreCase(role));
            rememberConversationMessage(role, content);
        }
    }

    private void renderChatMessage(String text, boolean userMessage) {
        WebView bubble = ChatBubbleFactory.createChatBubbleView(text, userMessage);

        HBox row = new HBox(bubble);
        row.getStyleClass().add("chat-message-row");
        row.setFillHeight(false);
        row.setAlignment(userMessage ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        chatHistoryBox.getChildren().add(row);
    }

    private void setConversationControlsEnabled(boolean enabled) {
        if (chatInputField != null) {
            chatInputField.setDisable(!enabled);
        }
        if (chatSendButton != null) {
            chatSendButton.setDisable(!enabled);
        }
        if (contextDrawerToggleButton != null) {
            contextDrawerToggleButton.setDisable(!enabled);
        }
        if (!enabled) {
            setContextDrawerVisible(false);
            hideChatCommandDropdown();
            hidePromptSuggestions();
        }
    }

    private boolean hasUserMessage(List<Map<String, String>> messages) {
        if (messages == null || messages.isEmpty()) {
            return false;
        }
        return messages.stream()
                .anyMatch(message -> "user".equalsIgnoreCase(message == null ? "" : message.getOrDefault("role", "")));
    }

    private String buildDefaultConversationMessage(String detail) {
        String base = "Hello,\n\nType a chat message or start with $ for commands";
        String normalizedDetail = detail == null ? "" : detail.trim();
        if (normalizedDetail.isEmpty()) {
            return base;
        }
        if (normalizedDetail.toLowerCase(Locale.ROOT).startsWith("hello,")) {
            return normalizedDetail;
        }
        return base + "\n\n" + normalizedDetail;
    }

    private void storeConversationMessage(String role, String content) {
        if (activeSessionId == null || activeSessionId.isBlank()) {
            activeSessionId = chatSessionIdSupplier.get();
        }
        if (activeSessionId == null || activeSessionId.isBlank()) {
            return;
        }
        String normalizedRole = role == null ? "" : role.trim();
        String normalizedContent = content == null ? "" : content.trim();
        if (normalizedRole.isEmpty() || normalizedContent.isEmpty()) {
            return;
        }
        updateActiveSessionMetadata(normalizedRole, normalizedContent);
        messagesBySessionId
                .computeIfAbsent(activeSessionId, ignored -> new ArrayList<>())
                .add(createConversationMessage(normalizedRole, normalizedContent));
    }

    private HBox createSessionHistoryRow(AgentChatSession session, Consumer<AgentChatSession> onSessionSelected) {
        Label title = new Label(session.getTitle());
        title.getStyleClass().add("session-history-row-title");
        title.setWrapText(true);

        int messageCount = messagesBySessionId.getOrDefault(session.getSessionId(), List.of()).size();
        Label description = new Label(session.getDescription());
        description.getStyleClass().add("session-history-row-description");
        description.setWrapText(true);

        Label meta = new Label(session.getLabel() + " - " + messageCount + (messageCount == 1 ? " message" : " messages"));
        meta.getStyleClass().add("session-history-row-meta");

        VBox text = new VBox(3, title, description, meta);
        Region spacer = new Region();
        Button openButton = new Button("Open");
        openButton.getStyleClass().add("secondary-button");
        openButton.setOnAction(ignored -> {
            if (onSessionSelected != null) {
                onSessionSelected.accept(session);
            }
        });

        HBox row = new HBox(10, text, spacer, openButton);
        row.getStyleClass().add("session-history-row");
        row.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
        return row;
    }

    private void updateActiveSessionMetadata(String role, String content) {
        if (activeSession == null || !"user".equalsIgnoreCase(role)) {
            return;
        }
        List<Map<String, String>> storedMessages = messagesBySessionId.getOrDefault(activeSession.getSessionId(), List.of());
        if (hasUserMessage(storedMessages)) {
            return;
        }
        String normalized = content == null ? "" : content.replaceAll("\\s+", " ").trim();
        if (normalized.isEmpty()) {
            return;
        }
        activeSession.setTitle(truncateText(normalized, 42));
        activeSession.setDescription("First request: " + truncateText(normalized, 120));
    }

    private String truncateText(String text, int maxLength) {
        String normalized = text == null ? "" : text.trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maxLength - 3)).trim() + "...";
    }

    private void showPromptSuggestions() {
        if (chatHistoryBox == null || promptSuggestionsPane == null) {
            return;
        }

        promptSuggestionsPane.getChildren().clear();
        for (ChatCommandOption option : CHAT_COMMAND_OPTIONS) {
            promptSuggestionsPane.getChildren().add(createPromptSuggestionCard(option));
        }

        if (!chatHistoryBox.getChildren().contains(promptSuggestionsPane)) {
            chatHistoryBox.getChildren().add(promptSuggestionsPane);
        }

        promptSuggestionsPane.setVisible(true);
        promptSuggestionsPane.setManaged(true);
    }

    private VBox createPromptSuggestionCard(ChatCommandOption option) {
        Label titleLabel = new Label(option.title());
        titleLabel.getStyleClass().add("prompt-suggestion-title");
        titleLabel.setWrapText(true);

        Label commandLabel = new Label(option.command());
        commandLabel.getStyleClass().add("prompt-suggestion-command");

        Label descriptionLabel = new Label(option.description());
        descriptionLabel.getStyleClass().add("prompt-suggestion-description");
        descriptionLabel.setWrapText(true);

        VBox card = new VBox(6.0, titleLabel, commandLabel, descriptionLabel);
        card.getStyleClass().add("prompt-suggestion-card");
        card.setFocusTraversable(true);
        card.setCursor(Cursor.HAND);
        card.setOnMouseClicked(event -> submitPromptSuggestion(option));
        card.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.SPACE) {
                submitPromptSuggestion(option);
                event.consume();
            }
        });
        return card;
    }

    private void submitPromptSuggestion(ChatCommandOption option) {
        if (option == null || chatInputField == null) {
            return;
        }

        chatInputField.setText(option.command());
        chatInputField.positionCaret(option.command().length());
        onSendMessage();
    }

    private void hidePromptSuggestions() {
        if (promptSuggestionsPane == null) {
            return;
        }

        promptSuggestionsPane.setVisible(false);
        promptSuggestionsPane.setManaged(false);
        if (chatHistoryBox != null) {
            chatHistoryBox.getChildren().remove(promptSuggestionsPane);
        }
    }

    private void setContextDrawerVisible(boolean visible) {
        if (contextDrawerContentController != null) {
            contextDrawerContentController.setVisible(visible);
        }
        contextDrawerToggleButton.setText(visible ? "Hide Context" : "Show Context");
    }

    private static List<ChatCommandOption> createChatCommandOptions() {
        List<ChatCommandOption> options = new ArrayList<>();
        options.add(new ChatCommandOption("$mapping", "Show Active Mappings", "Show the current feedback mappings", "show me the current feedback mappings"));
        options.add(new ChatCommandOption("$knowledge", "Experiment Background", "What is the background of this experiment", "what is the background of this experiment"));
        options.add(new ChatCommandOption("$dictionary", "Show Use Cases", "Show the use cases in the dictionary", "show me the use cases in the dictionary"));
        options.add(new ChatCommandOption("$schedule", "Show Active Schedules", "Show the active schedules", "show me the active schedules"));
        options.add(new ChatCommandOption("$summarize", "Summarize This Experiment", "Show a summary of the experiment's user data", "summarize this experiment")); 
        //options.add(new ChatCommandOption("$usecase", "Show Use Case Configuration", "Show the use case configuration", "show me the use case configuration"));
        return List.copyOf(options);
    }

    private static Map<String, ChatCommandOption> createChatCommandOptionMap() {
        Map<String, ChatCommandOption> map = new LinkedHashMap<>();
        for (ChatCommandOption option : CHAT_COMMAND_OPTIONS) {
            map.put(option.command().substring(1), option);
        }
        return map;
    }
}