package com.example.demo.factory;

import com.example.demo.model.RuleCardData;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;

import java.util.function.Consumer;

public class MappingUiFactory {

    public static Button createSelectMappingButton(RuleCardData rule, Consumer<RuleCardData> onSelect) {
        Button button = new Button("Edit");
        button.getStyleClass().add("mapping-edit-button");
        button.setTooltip(new Tooltip("Edit mapping"));
        button.setOnMouseClicked(event -> event.consume());
        button.setOnAction(ignored -> onSelect.accept(rule));
        return button;
    }

    public static Button createDeleteMappingButton(RuleCardData rule, Consumer<RuleCardData> onDelete) {
        Button button = SharedUiFactory.createDeleteIconButton("Delete mapping");
        button.setOnAction(ignored -> onDelete.accept(rule));
        button.setOnMouseClicked(event -> event.consume());
        return button;
    }
}