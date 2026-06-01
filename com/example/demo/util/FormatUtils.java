package com.example.demo.util;

import java.util.Locale;

public class FormatUtils {

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