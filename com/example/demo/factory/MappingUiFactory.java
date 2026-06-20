package com.example.demo.factory;

import com.example.demo.model.RuleCardData;
import com.example.demo.model.Schedule;
import com.example.demo.util.DateUtils;
import com.fasterxml.jackson.databind.JsonNode;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.Date;
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

    public static VBox createHistoryMappingCard(JsonNode mappingNode, String useCase) {
        return createHistoryMappingCard(mappingNode, useCase, null, null, false);
    }

    public static VBox createHistoryMappingCard(JsonNode mappingNode, String useCase,
                                                Consumer<RuleCardData> onSelect,
                                                Consumer<RuleCardData> onDelete,
                                                boolean selected) {
        RuleCardData rule = createRuleFromHistoryMapping(mappingNode, useCase);

        VBox card = new VBox(8);
        card.setUserData(rule.mappingId);
        card.getStyleClass().add("mapping-card");
        if (!rule.active) {
            card.getStyleClass().add("mapping-card-unassigned");
        }
        if (selected) {
            card.getStyleClass().add("mapping-card-selected");
        }

        Label title = new Label(rule.useCaseLabel + ": " + rule.mappingId);
        title.getStyleClass().add("mapping-card-title");

        HBox titleRow = new HBox(8);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        titleRow.getChildren().addAll(title, spacer);
        if (onSelect != null) {
            titleRow.getChildren().add(createSelectMappingButton(rule, onSelect));
        }
        if (onDelete != null) {
            titleRow.getChildren().add(createDeleteMappingButton(rule, onDelete));
        }

        Label values = new Label("Values: " + rule.minValue + "-" + rule.maxValue);
        Label pulses = new Label("Pulse count: " + rule.pulseLabel);
        Label intensity = new Label("Intensity: " + rule.intensityLabel);
        Label duration = new Label("Duration: " + rule.durationLabel);
        Label interval = new Label("Interval: " + rule.intervalLabel);

        String assignedAt = DateUtils.formatReadable(mappingNode.path("assignedAt").asText("Unknown"));
        String deactivatedAt = DateUtils.formatReadable(mappingNode.path("deactivatedAt").asText());

        Label dateInfo = new Label("Assigned: " + assignedAt);
        if (!rule.active && !deactivatedAt.isBlank() && !"N/A".equals(deactivatedAt)) {
            dateInfo.setText(dateInfo.getText() + " \nDeactivated: " + deactivatedAt);
        }
        dateInfo.getStyleClass().add("mapping-assigned-muted");

        card.getChildren().addAll(titleRow, values, pulses, intensity, duration, interval, dateInfo);
        if (!rule.active) {
            Label unassignedHint = new Label("Not currently assigned");
            unassignedHint.getStyleClass().add("mapping-assigned-muted");
            card.getChildren().add(unassignedHint);
        }

        if (onSelect != null) {
            card.setOnMouseClicked(event -> {
                if (event.getTarget() instanceof ButtonBase) {
                    return;
                }
                onSelect.accept(rule);
            });
        }

        return card;
    }

    public static RuleCardData createRuleFromHistoryMapping(JsonNode mappingNode, String useCase) {
        RuleCardData rule = new RuleCardData();
        rule.mappingId = mappingNode.path("mappingId").asInt();
        rule.useCaseLabel = mappingNode.path("type").asText(useCase);
        rule.minValue = mappingNode.path("minValue").asInt();
        rule.maxValue = mappingNode.path("maxValue").asInt();
        rule.minPulses = mappingNode.path("minPulses").asInt();
        rule.maxPulses = mappingNode.path("maxPulses").asInt();
        rule.minIntensity = mappingNode.path("minIntensity").asInt();
        rule.maxIntensity = mappingNode.path("maxIntensity").asInt();
        rule.minDuration = mappingNode.path("minDuration").asInt();
        rule.maxDuration = mappingNode.path("maxDuration").asInt();
        rule.minInterval = mappingNode.path("minInterval").asInt();
        rule.maxInterval = mappingNode.path("maxInterval").asInt();

        rule.pulseLabel = rule.minPulses + "-" + rule.maxPulses;
        rule.intensityLabel = rule.minIntensity + "-" + rule.maxIntensity;
        rule.durationLabel = rule.minDuration + "-" + rule.maxDuration;
        rule.intervalLabel = rule.minInterval + "-" + rule.maxInterval;
        rule.rangeLabel = rule.useCaseLabel + ": " + rule.minValue + "-" + rule.maxValue;
        rule.active = isHistoryMappingActive(mappingNode);
        return rule;
    }

    public static boolean isHistoryMappingActive(JsonNode mappingNode) {
        return mappingNode != null && mappingNode.path("assignmentActive").asBoolean(false);
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
