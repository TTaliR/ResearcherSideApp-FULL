package com.example.demo.util;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

public class FormatUtils {

    public static String formatDecimal(BigDecimal value) {
        return value == null ? "0" : value.stripTrailingZeros().toPlainString();
    }

    public static String formatMappingValue(BigDecimal value) {
        String formatted = formatDecimal(value);
        return value != null && value.signum() < 0 ? "(" + formatted + ")" : formatted;
    }

    public static String formatUseCaseName(String useCase) {
        if (useCase == null || useCase.isBlank()) {
            return useCase;
        }
        return Arrays.stream(useCase.split("(?=[A-Z])|_"))
                .filter(word -> !word.isEmpty())
                .map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1).toLowerCase())
                .collect(Collectors.joining(" "));
    }

    public static String normalizeUseCaseName(String rawUseCase) {
        if (rawUseCase == null) {
            return "";
        }

        String normalized = rawUseCase.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
        if (normalized.contains("heartrate")) {
            return "HeartRate";
        }
        if (normalized.contains("sunazimuth") || (normalized.contains("sun") && normalized.contains("azimuth"))) {
            return "SunAzimuth";
        }
        if (normalized.contains("moonazimuth") || (normalized.contains("moon") && normalized.contains("azimuth"))) {
            return "MoonAzimuth";
        }
        if (normalized.contains("pollution")) {
            return "Pollution";
        }

        return normalized.isBlank() ? "Unknown" : normalized;
    }
}
