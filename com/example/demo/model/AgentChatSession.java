package com.example.demo.model;

import java.time.LocalDateTime;

public class AgentChatSession {
    private final String sessionId;
    private final String useCaseName;
    private final String useCaseKey;
    private final String label;
    private final LocalDateTime createdAt;
    private String title;
    private String description;

    public AgentChatSession(String sessionId, String useCaseName, String useCaseKey, String label, LocalDateTime createdAt) {
        this.sessionId = sessionId == null ? "" : sessionId;
        this.useCaseName = useCaseName == null ? "" : useCaseName;
        this.useCaseKey = useCaseKey == null ? "" : useCaseKey;
        this.label = label == null ? "" : label;
        this.createdAt = createdAt == null ? LocalDateTime.now() : createdAt;
        this.title = this.label;
        this.description = "No researcher messages yet.";
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getUseCaseName() {
        return useCaseName;
    }

    public String getUseCaseKey() {
        return useCaseKey;
    }

    public String getLabel() {
        return label;
    }

    public String getTitle() {
        return title == null || title.isBlank() ? label : title;
    }

    public void setTitle(String title) {
        this.title = title == null || title.isBlank() ? label : title.trim();
    }

    public String getDescription() {
        return description == null ? "" : description;
    }

    public void setDescription(String description) {
        this.description = description == null ? "" : description.trim();
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
