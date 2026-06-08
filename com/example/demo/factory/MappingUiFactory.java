package com.example.demo.factory;

import com.example.demo.model.RuleCardData;
import com.example.demo.model.Schedule;
import com.example.demo.util.DateUtils;
import com.fasterxml.jackson.databind.JsonNode;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
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

        boolean isActive = mappingNode.path("assignmentActive").asBoolean(false);

        VBox card = new VBox(8);
        card.getStyleClass().add("mapping-card");
        if (!isActive) {
            card.getStyleClass().add("mapping-card-inactive");
        }

        Label title = new Label(rule.useCaseLabel + ": " + rule.mappingId);
        title.getStyleClass().add("mapping-card-title");

        HBox titleRow = new HBox(8);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        titleRow.getChildren().addAll(title, spacer);

        Label values = new Label("Values: " + rule.minValue + "-" + rule.maxValue);
        Label pulses = new Label("Pulse count: " + rule.pulseLabel);
        Label intensity = new Label("Intensity: " + rule.intensityLabel);
        Label duration = new Label("Duration: " + rule.durationLabel);
        Label interval = new Label("Interval: " + rule.intervalLabel);

        String assignedAt = DateUtils.formatReadable(mappingNode.path("assignedAt").asText("Unknown"));
        String deactivatedAt = DateUtils.formatReadable(mappingNode.path("deactivatedAt").asText());
        
        Label dateInfo = new Label("Assigned: " + assignedAt);
        if (!isActive && !deactivatedAt.isBlank() && !"N/A".equals(deactivatedAt)) {
            dateInfo.setText(dateInfo.getText() + " \nDeactivated: " + deactivatedAt);
        }
        dateInfo.getStyleClass().add("mapping-assigned-muted");

        card.getChildren().addAll(titleRow, values, pulses, intensity, duration, interval, dateInfo);
        if (!isActive) {
            Label inactiveHint = new Label("Historical mapping (Inactive)");
            inactiveHint.getStyleClass().add("mapping-assigned-muted");
            card.getChildren().add(inactiveHint);
        }

        return card;
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