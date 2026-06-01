package com.example.demo.model;

import java.util.Locale;

public record ChatCommandOption(String command, String title, String description, String prompt) {
    public boolean matches(String query) {
        String normalizedQuery = query == null ? "" : query.trim();
        if (normalizedQuery.isEmpty()) {
            return true;
        }

        String lower = normalizedQuery.toLowerCase(Locale.ROOT);
        return command.toLowerCase(Locale.ROOT).contains(lower)
            || title.toLowerCase(Locale.ROOT).contains(lower)
            || description.toLowerCase(Locale.ROOT).contains(lower);
    }
}
