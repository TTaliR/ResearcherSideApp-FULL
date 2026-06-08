package com.example.demo.util;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class DateUtils {

    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_ZONED_DATE_TIME;
    private static final DateTimeFormatter READABLE_FORMATTER = DateTimeFormatter.ofPattern("MMM dd, yyyy, HH:mm:ss", Locale.US);

    public static String formatReadable(String isoDateString) {
        if (isoDateString == null || isoDateString.isBlank() || "null".equalsIgnoreCase(isoDateString)) {
            return "N/A";
        }
        try {
            ZonedDateTime zonedDateTime = ZonedDateTime.parse(isoDateString, ISO_FORMATTER);
            return zonedDateTime.format(READABLE_FORMATTER);
        } catch (Exception e) {
            // Fallback for non-ISO strings
            return isoDateString;
        }
    }
}