package com.example.demo.controller;

import com.example.demo.service.ApiService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ChatBotController {
    @FXML private VBox chatHistoryBox;
    @FXML private TextField userInputField;
    @FXML private ScrollPane scrollPane;

    // Generate a unique session ID for this chat instance
    private final String sessionId = UUID.randomUUID().toString();

    @FXML
    public void initialize() {
        // Ensure auto-scroll happens whenever a new message is added
        chatHistoryBox.heightProperty().addListener((observable, oldValue, newValue) -> {
            scrollPane.setVvalue(1.0);
        });
    }

    @FXML
    private void onSendMessage() {
        String message = userInputField.getText().trim();
        if (message.isEmpty()) return;

        addMessage(message, Pos.CENTER_LEFT, "#dcf8c6");
        userInputField.clear();

        // The keys "query" and "sessionId" must match what is in the n8n expressions
        Map<String, Object> payload = new HashMap<>();
        payload.put("chatInput", message);     // Maps to {{ $json.body.query }} in n8n
        payload.put("sessionId", sessionId); // Maps to {{ $json.body.sessionId }} in n8n

        ApiService.getInstance().postWithResponse(ApiService.EP_CHAT_CONFIG, payload)
                .thenAccept(response -> {
                    Platform.runLater(() -> {
                        if (response != null && response.has("output")) {
                            addMessage(response.get("output").asText(), Pos.CENTER_RIGHT, "#ffffff");
                        } else {
                            addMessage("AI: I processed the request but 'output' was missing.", Pos.CENTER_RIGHT, "#ffcccc");
                        }
                    });
                })
                .exceptionally(ex -> {
                    Platform.runLater(() -> addMessage("Error: " + ex.getMessage(), Pos.CENTER_RIGHT, "#ffcccc"));
                    return null;
                });
    }

    private void addMessage(String text, Pos alignment, String color) {
        Label msgLabel = new Label(text);
        msgLabel.setWrapText(true);
        msgLabel.setMaxWidth(500);
        msgLabel.setStyle("-fx-background-color: " + color + "; " +
                "-fx-padding: 10; " +
                "-fx-background-radius: 12; " +
                "-fx-border-color: #d1d1d1; " +
                "-fx-border-radius: 12; " +
                "-fx-font-size: 14;");

        HBox container = new HBox(msgLabel);
        container.setPadding(new javafx.geometry.Insets(5, 10, 5, 10));
        container.setAlignment(alignment);
        chatHistoryBox.getChildren().add(container);
    }

    @FXML
    private void goBack(javafx.event.ActionEvent event) {
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/com/example/demo/view/ResearcherInterface.fxml"));
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}