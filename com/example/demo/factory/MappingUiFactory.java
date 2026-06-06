package com.example.demo.factory;

import com.example.demo.model.RuleCardData;
import com.example.demo.model.Schedule;
import javafx.scene.control.Button;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;

import java.util.function.BiConsumer;
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

    public static ToggleButton createScheduleActiveToggle(Schedule schedule, BiConsumer<Schedule, ToggleButton> onToggle) {
        ToggleButton toggle = new ToggleButton(schedule != null && schedule.isActive() ? "Active" : "Inactive");
        toggle.getStyleClass().add("schedule-active-toggle");
        toggle.setSelected(schedule != null && schedule.isActive());
        toggle.setTooltip(new Tooltip("Activate or deactivate schedule"));
        toggle.setOnMouseClicked(event -> event.consume());
        toggle.setOnAction(ignored -> onToggle.accept(schedule, toggle));
        return toggle;
    }
}
