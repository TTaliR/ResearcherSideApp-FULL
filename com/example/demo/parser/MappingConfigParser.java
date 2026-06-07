package com.example.demo.parser;

import com.example.demo.model.RuleCardData;
import com.example.demo.util.FormatUtils;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MappingConfigParser {

    private final List<RuleCardData> allRules;
    private final java.util.Map<String, String> useCaseDisplayNames;

    public MappingConfigParser(List<RuleCardData> allRules, java.util.Map<String, String> useCaseDisplayNames) {
        this.allRules = allRules;
        this.useCaseDisplayNames = useCaseDisplayNames;
    }

    public void parseMappingsFromConfiguration(JsonNode rootNode) {
        if (rootNode == null || !rootNode.isObject()) {
            return;
        }

        rootNode.fields().forEachRemaining(entry -> parseRulesArray(entry.getKey(), entry.getValue()));
    }

    private void parseRulesArray(String configKey, JsonNode arrayNode) {
        if (arrayNode == null || !arrayNode.isArray()) {
            return;
        }

        for (JsonNode node : arrayNode) {
            String rawType = node.path("usecase_name").asText("").trim();
            if (rawType.isEmpty()) {
                rawType = node.path("type").asText("").trim();
            }
            String useCaseKey = rawType.isEmpty()
                ? inferUseCaseFromConfigKey(configKey)
                : FormatUtils.normalizeUseCaseName(rawType);
            String useCaseLabel = rawType.isEmpty() ? toUseCaseLabel(useCaseKey) : rawType;

            RuleCardData rule = new RuleCardData();
            rule.mappingId = readMappingId(node);
            rule.configKey = configKey;
            rule.useCaseKey = useCaseKey;
            rule.useCaseLabel = useCaseLabel;
            rule.minValue = readInt(node, "minvalue", "min", 0);
            rule.maxValue = readInt(node, "maxvalue", "max", 0);
            rule.minPulses = readInt(node, "minpulses", "", 0);
            rule.maxPulses = readInt(node, "maxpulses", "", 0);
            rule.minIntensity = readInt(node, "minintensity", "", 0);
            rule.maxIntensity = readInt(node, "maxintensity", "", 0);
            rule.minDuration = readInt(node, "minduration", "", 0);
            rule.maxDuration = readInt(node, "maxduration", "", 0);
            rule.minInterval = readInt(node, "mininterval", "", 0);
            rule.maxInterval = readInt(node, "maxinterval", "", 0);
            rule.rangeLabel = readRangeLabel(node, useCaseLabel);
            rule.pulseLabel = readRangeValue(node, "minpulses", "maxpulses", "N/A");
            rule.intensityLabel = readRangeValue(node, "minintensity", "maxintensity", "N/A");
            rule.durationLabel = readRangeValue(node, "minduration", "maxduration", "N/A ms");
            rule.intervalLabel = readRangeValue(node, "mininterval", "maxinterval", "N/A ms");
            allRules.add(rule);
        }
    }

    private String inferUseCaseFromConfigKey(String configKey) {
        String lowered = configKey == null ? "" : configKey.toLowerCase(Locale.ROOT);
        if (lowered.contains("heart")) {
            return "HeartRate";
        }
        if (lowered.contains("sun")) {
            return "SunAzimuth";
        }
        if (lowered.contains("moon")) {
            return "MoonAzimuth";
        }
        if (lowered.contains("pollution")) {
            return "Pollution";
        }

        return FormatUtils.normalizeUseCaseName(configKey);
    }

    private String toUseCaseLabel(String useCaseKey) {
        if (useCaseKey == null || useCaseKey.isBlank()) {
            return "Unknown";
        }

        String explicitLabel = useCaseDisplayNames.get(useCaseKey);
        if (explicitLabel != null && !explicitLabel.isBlank()) {
            return explicitLabel;
        }

        return switch (useCaseKey) {
            case "HeartRate" -> "Heart Rate";
            case "SunAzimuth" -> "Sun Azimuth";
            case "MoonAzimuth" -> "Moon Azimuth";
            case "Pollution" -> "Pollution";
            default -> useCaseKey;
        };
    }

    private String readRangeLabel(JsonNode node, String useCaseLabel) {
        if (node.has("minvalue") && node.has("maxvalue")) {
            return useCaseLabel + ": " + node.path("minvalue").asInt() + " - " + node.path("maxvalue").asInt();
        }
        if (node.has("min") && node.has("max")) {
            return useCaseLabel + ": " + node.path("min").asInt() + " - " + node.path("max").asInt();
        }
        return useCaseLabel + ": rule";
    }

    private String readRangeValue(JsonNode node, String minField, String maxField, String fallback) {
        if (node.has(minField) && node.has(maxField)) {
            return node.path(minField).asInt() + " - " + node.path(maxField).asInt();
        }
        return fallback;
    }

    private int readInt(JsonNode node, String primaryField, String fallbackField, int fallback) {
        if (node == null || !node.isObject()) {
            return fallback;
        }
        if (primaryField != null && !primaryField.isBlank() && node.has(primaryField)) {
            return node.path(primaryField).asInt(fallback);
        }
        if (fallbackField != null && !fallbackField.isBlank() && node.has(fallbackField)) {
            return node.path(fallbackField).asInt(fallback);
        }
        return fallback;
    }

    private int readMappingId(JsonNode node) {
        if (node == null || !node.isObject()) {
            return 0;
        }
        if (node.has("mappingId")) {
            return node.path("mappingId").asInt(0);
        }
        if (node.has("mapping_id")) {
            return node.path("mapping_id").asInt(0);
        }
        if (node.has("feedback_config_rule_id")) {
            return node.path("feedback_config_rule_id").asInt(0);
        }
        if (node.has("id")) {
            return node.path("id").asInt(0);
        }
        return 0;
    }
}
