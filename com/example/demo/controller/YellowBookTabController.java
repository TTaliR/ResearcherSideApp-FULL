package com.example.demo.controller;

import com.example.demo.factory.YellowBookUiFactory;
import com.example.demo.model.DictionaryParameterData;
import com.example.demo.parser.YellowBookParser;
import com.example.demo.service.ApiService;
import com.example.demo.util.AlertUtils;
import com.example.demo.util.ButtonLoadingState;
import com.fasterxml.jackson.databind.JsonNode;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public class YellowBookTabController {
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
    private Button yellowBookRefreshButton;
    @FXML
    private Button yellowBookSaveButton;
    @FXML
    private Button yellowBookClearButton;

    private final Map<String, List<DictionaryParameterData>> yellowBookEntries = new LinkedHashMap<>();
    private Supplier<DictionaryContext> contextSupplier = () -> null;
    private String chatSessionId = "";
    private DictionaryParameterData selectedYellowBookParameter;

    public record DictionaryContext(Integer useCaseId, String useCaseName) {
    }

    @FXML
    private void initialize() {
        setupYellowBookEditor();
    }

    public void setContextSupplier(Supplier<DictionaryContext> contextSupplier) {
        this.contextSupplier = contextSupplier == null ? () -> null : contextSupplier;
    }

    public void setChatSessionId(String chatSessionId) {
        this.chatSessionId = chatSessionId == null ? "" : chatSessionId;
    }

    public void loadDictionary() {
        loadYellowBookDictionary();
    }

    public void clearSelection() {
        updateYellowBookEditor(null);
        renderYellowBook();
    }

    @FXML
    private void onRefreshYellowBook() {
        loadYellowBookDictionary();
    }

    private void setupYellowBookEditor() {
        updateYellowBookEditor(null);
    }

    private void loadYellowBookDictionary() {
        DictionaryContext context = getDictionaryContext();
        Integer contextUsecaseId = context == null ? null : context.useCaseId();
        if (contextUsecaseId == null || contextUsecaseId <= 0) {
            if (yellowBookStatusLabel != null) {
                yellowBookStatusLabel.setText("Load use cases before opening the Yellow book.");
            }
            return;
        }

        if (yellowBookStatusLabel != null) {
            yellowBookStatusLabel.setText("Loading dictionary...");
        }

        ButtonLoadingState loading = ButtonLoadingState.start(yellowBookRefreshButton, "Loading...");
        String contextUsecaseName = context.useCaseName();
        ApiService.getInstance()
            .sendDictionaryRequest("Show me the full use case dictionary.", contextUsecaseId, contextUsecaseName, chatSessionId)
            .thenAccept(response -> Platform.runLater(() -> {
                loading.close();
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
                Platform.runLater(() -> {
                    loading.close();
                    yellowBookStatusLabel.setText("Dictionary request failed: " + ex.getMessage());
                });
                return null;
            });
    }

    private DictionaryContext getDictionaryContext() {
        return contextSupplier == null ? null : contextSupplier.get();
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
        yellowBookEntries.forEach((useCaseName, parameters) -> yellowBookContentBox.getChildren().add(
            YellowBookUiFactory.createYellowBookUseCaseCard(
                useCaseName,
                parameters,
                this::onRemoveYellowBookUseCase,
                this::onEditYellowBookParameter,
                this::onRemoveYellowBookParameter,
                selectedYellowBookParameter
            )
        ));
    }

    private void onEditYellowBookParameter(DictionaryParameterData parameter) {
        updateYellowBookEditor(parameter);
        renderYellowBook();
    }

    @FXML
    private void onClearYellowBookSelection() {
        clearSelection();
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
        DictionaryContext context = getDictionaryContext();
        Integer contextUsecaseId = context == null ? null : context.useCaseId();
        if (contextUsecaseId == null || contextUsecaseId <= 0) {
            AlertUtils.showErrorAlert("Missing Use Case Id", "Could not resolve a use case id for the dictionary request.");
            return;
        }

        if (yellowBookStatusLabel != null) {
            yellowBookStatusLabel.setText("Renaming dictionary parameter...");
        }

        ButtonLoadingState loading = ButtonLoadingState.start(yellowBookSaveButton, "Saving...");
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
            .sendDictionaryRequest(addMessage, contextUsecaseId, context.useCaseName(), chatSessionId)
            .thenAccept(addResponse -> {
                if (isDictionaryErrorResponse(addResponse)) {
                    Platform.runLater(() -> {
                        loading.close();
                        String error = extractDictionaryError(addResponse, "Could not add the new parameter name.");
                        yellowBookStatusLabel.setText(error);
                        AlertUtils.showErrorAlert("Rename Failed", error);
                    });
                    return;
                }

                ApiService.getInstance()
                    .sendDictionaryRequest(deleteMessage, contextUsecaseId, context.useCaseName(), chatSessionId)
                    .thenAccept(deleteResponse -> Platform.runLater(() -> {
                        loading.close();
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
                        Platform.runLater(() -> {
                            loading.close();
                            AlertUtils.showErrorAlert("Rename Failed", ex.getMessage());
                        });
                        return null;
                    });
            })
            .exceptionally(ex -> {
                Platform.runLater(() -> {
                    loading.close();
                    AlertUtils.showErrorAlert("Rename Failed", ex.getMessage());
                });
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
        DictionaryContext context = getDictionaryContext();
        Integer contextUsecaseId = context == null ? null : context.useCaseId();
        if (contextUsecaseId == null || contextUsecaseId <= 0) {
            AlertUtils.showErrorAlert("Missing Use Case Id", "Could not resolve a use case id for the dictionary request.");
            return;
        }

        if (yellowBookStatusLabel != null) {
            yellowBookStatusLabel.setText("Sending dictionary update...");
        }

        ButtonLoadingState loading = ButtonLoadingState.start(yellowBookSaveButton, "Saving...");
        ApiService.getInstance()
            .sendDictionaryRequest(message, contextUsecaseId, context.useCaseName(), chatSessionId)
            .thenAccept(response -> Platform.runLater(() -> {
                loading.close();
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
                Platform.runLater(() -> {
                    loading.close();
                    AlertUtils.showErrorAlert("Dictionary Update Failed", ex.getMessage());
                });
                return null;
            });
    }
}
