package com.example.demo.factory;

import com.example.demo.model.DictionaryParameterData;
import javafx.geometry.Pos;
import javafx.animation.PauseTransition;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.List;
import java.util.function.Consumer;

public class YellowBookUiFactory {

    public static VBox createYellowBookUseCaseCard(String useCaseName, List<DictionaryParameterData> parameters,
                                                   Consumer<String> onRemoveUseCase,
                                                   Consumer<DictionaryParameterData> onEditParameter,
                                                   Consumer<DictionaryParameterData> onRemoveParameter,
                                                   DictionaryParameterData selectedParameter) {
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
        deleteUseCaseButton.setOnAction(ignored -> onRemoveUseCase.accept(useCaseName));
        header.getChildren().addAll(title, count, spacer, deleteUseCaseButton);

        VBox parameterBox = new VBox(6);
        if (parameters.isEmpty()) {
            Label empty = new Label("No parameters registered.");
            empty.getStyleClass().add("topbar-muted-value");
            parameterBox.getChildren().add(empty);
        } else {
            for (DictionaryParameterData parameter : parameters) {
                parameterBox.getChildren().add(createYellowBookParameterRow(parameter, onEditParameter, onRemoveParameter, selectedParameter));
            }
        }

        card.getChildren().addAll(header, parameterBox);
        return card;
    }

    private static HBox createYellowBookParameterRow(DictionaryParameterData parameter,
                                                     Consumer<DictionaryParameterData> onEdit,
                                                     Consumer<DictionaryParameterData> onRemove,
                                                     DictionaryParameterData selectedParameter) {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("yellow-book-parameter-row");
        if (selectedParameter != null && selectedParameter.equals(parameter)) {
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
        editButton.setOnAction(ignored -> onEdit.accept(parameter));

        Button deleteButton = SharedUiFactory.createDeleteIconButton("Remove parameter");
        deleteButton.setOnAction(ignored -> onRemove.accept(parameter));

        Button copyButton = SharedUiFactory.createCopyIconButton("Copy name=value");
        copyButton.setOnAction(ignored -> {
            String value = parameter.paramValue != null ? parameter.paramValue : "";
            String textToCopy = parameter.parameterName + "=" + value;
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(textToCopy);
            clipboard.setContent(content);

            // Inline "toast" feedback on the button itself
            String originalText = copyButton.getText();
            Node originalGraphic = copyButton.getGraphic();
            copyButton.setGraphic(null);
            copyButton.setText("Copied!");

            PauseTransition pause = new PauseTransition(Duration.seconds(1.5));
            pause.setOnFinished(e -> {
                copyButton.setText(originalText);
                copyButton.setGraphic(originalGraphic);
            });
            pause.play();
        });

        row.getChildren().addAll(textBox, copyButton, editButton, deleteButton);
        row.setOnMouseClicked(event -> {
            if (event.getTarget() instanceof Button) {
                return;
            }
            onEdit.accept(parameter);
        });
        return row;
    }

    private static String buildYellowBookParameterDetails(DictionaryParameterData parameter) {
        List<String> parts = new java.util.ArrayList<>();
        if (!parameter.parameterFormat.isBlank()) {
            parts.add("Format: " + parameter.parameterFormat);
        }
        if (!parameter.paramValue.isBlank()) {
            parts.add("Value: " + parameter.paramValue);
        }
        if (!parameter.description.isBlank()) {
            parts.add("Description: " + parameter.description);
        }
        parts.add(parameter.required ? "Required" : "Optional");
        return String.join(" | ", parts);
    }
}
