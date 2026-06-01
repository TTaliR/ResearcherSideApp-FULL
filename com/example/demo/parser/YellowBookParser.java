package com.example.demo.parser;

import com.example.demo.model.DictionaryParameterData;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class YellowBookParser {

    public static Map<String, List<DictionaryParameterData>> parseEntries(JsonNode entries) {
        LinkedHashMap<String, List<DictionaryParameterData>> parsed = new LinkedHashMap<>();
        if (entries == null || !entries.isArray()) {
            return parsed;
        }

        for (JsonNode entry : entries) {
            String useCaseName = entry.path("usecase_name").asText("").trim();
            String parameterName = entry.path("usecase_parameter_name").asText("").trim();
            if (useCaseName.isBlank() || parameterName.isBlank()) {
                continue;
            }

            String paramValue = entry.path("param_value").isNull() ? "" : entry.path("param_value").asText("");
            DictionaryParameterData parameter = new DictionaryParameterData(
                useCaseName,
                parameterName,
                entry.path("parameter_format").asText(""),
                entry.path("is_required").asBoolean(false),
                entry.path("description").asText(""),
                paramValue
            );
            parsed.computeIfAbsent(useCaseName, ignored -> new ArrayList<>()).add(parameter);
        }

        return parsed;
    }

    public static Map<String, List<DictionaryParameterData>> parse(String reply) {
        LinkedHashMap<String, List<DictionaryParameterData>> parsed = new LinkedHashMap<>();
        if (reply == null || reply.isBlank()) {
            return parsed;
        }

        String activeUseCase = null;
        for (String rawLine : reply.split("\\R")) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }

            Matcher headerMatcher = Pattern.compile("\\*\\*(.+?)\\*\\*").matcher(line);
            if (line.contains("**") && headerMatcher.find() && !line.toLowerCase(Locale.ROOT).startsWith("here are")) {
                activeUseCase = headerMatcher.group(1).trim();
                parsed.putIfAbsent(activeUseCase, new ArrayList<>());
                continue;
            }

            if (activeUseCase == null) {
                continue;
            }

            DictionaryParameterData parameter = parseParameterLine(activeUseCase, line);
            if (parameter != null) {
                parsed.get(activeUseCase).add(parameter);
            }
        }

        return parsed;
    }

    private static DictionaryParameterData parseParameterLine(String useCaseName, String rawLine) {
        String line = rawLine
            .replaceAll("^[^A-Za-z0-9_]+", "")
            .replace("[Required]", "")
            .trim();
        boolean required = rawLine.contains("[Required]");

        Matcher matcher = Pattern.compile("^(.+?)\\s*\\((.*)\\)$").matcher(line);
        if (!matcher.matches()) {
            return null;
        }

        String parameterName = matcher.group(1).trim();
        String formatAndValue = matcher.group(2).trim();
        String format = formatAndValue;
        String value = "";

        int valueMarker = formatAndValue.indexOf(" [Specific value!]");
        if (valueMarker >= 0) {
            formatAndValue = formatAndValue.substring(0, valueMarker).trim();
        }

        int equalsIndex = formatAndValue.indexOf(" = ");
        if (equalsIndex >= 0) {
            format = formatAndValue.substring(0, equalsIndex).trim();
            value = formatAndValue.substring(equalsIndex + 3).trim();
        }

        if (parameterName.isBlank()) {
            return null;
        }

        return new DictionaryParameterData(useCaseName, parameterName, format, required, "", value);
    }
}
