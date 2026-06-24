package com.example.demo.service;

import com.example.demo.model.AgentChatSession;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AgentChatSessionStore {
    private static final Path STORE_PATH = Path.of(
            System.getProperty("user.home"),
            ".researcher-side-app",
            "agent-chat-sessions.json"
    );

    private final ObjectMapper mapper = new ObjectMapper();

    public StoredChatSessions load() {
        if (!Files.exists(STORE_PATH)) {
            return new StoredChatSessions(List.of(), Map.of());
        }

        try {
            JsonNode root = mapper.readTree(STORE_PATH.toFile());
            List<AgentChatSession> sessions = new ArrayList<>();
            Map<String, List<Map<String, String>>> messagesBySessionId = new LinkedHashMap<>();

            JsonNode sessionsNode = root.path("sessions");
            if (sessionsNode.isArray()) {
                for (JsonNode sessionNode : sessionsNode) {
                    AgentChatSession session = parseSession(sessionNode);
                    if (session.getSessionId().isBlank()) {
                        continue;
                    }
                    sessions.add(session);
                    messagesBySessionId.put(session.getSessionId(), parseMessages(sessionNode.path("messages")));
                }
            }

            return new StoredChatSessions(sessions, messagesBySessionId);
        } catch (Exception ex) {
            ex.printStackTrace();
            return new StoredChatSessions(List.of(), Map.of());
        }
    }

    public void save(List<AgentChatSession> sessions, Map<String, List<Map<String, String>>> messagesBySessionId) {
        try {
            Files.createDirectories(STORE_PATH.getParent());

            ObjectNode root = mapper.createObjectNode();
            ArrayNode sessionsNode = mapper.createArrayNode();
            for (AgentChatSession session : sessions == null ? List.<AgentChatSession>of() : sessions) {
                if (session == null || session.getSessionId().isBlank()) {
                    continue;
                }

                ObjectNode sessionNode = mapper.createObjectNode();
                sessionNode.put("sessionId", session.getSessionId());
                sessionNode.put("useCaseName", session.getUseCaseName());
                sessionNode.put("useCaseKey", session.getUseCaseKey());
                sessionNode.put("label", session.getLabel());
                sessionNode.put("title", session.getTitle());
                sessionNode.put("description", session.getDescription());
                sessionNode.put("createdAt", session.getCreatedAt().toString());

                ArrayNode messagesNode = mapper.createArrayNode();
                List<Map<String, String>> messages = messagesBySessionId == null
                        ? List.of()
                        : messagesBySessionId.getOrDefault(session.getSessionId(), List.of());
                for (Map<String, String> message : messages) {
                    if (message == null) {
                        continue;
                    }
                    ObjectNode messageNode = mapper.createObjectNode();
                    messageNode.put("role", message.getOrDefault("role", ""));
                    messageNode.put("content", message.getOrDefault("content", ""));
                    messagesNode.add(messageNode);
                }
                sessionNode.set("messages", messagesNode);
                sessionsNode.add(sessionNode);
            }

            root.set("sessions", sessionsNode);
            mapper.writerWithDefaultPrettyPrinter().writeValue(STORE_PATH.toFile(), root);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private AgentChatSession parseSession(JsonNode sessionNode) {
        AgentChatSession session = new AgentChatSession(
                sessionNode.path("sessionId").asText(""),
                sessionNode.path("useCaseName").asText(""),
                sessionNode.path("useCaseKey").asText(""),
                sessionNode.path("label").asText(""),
                parseDateTime(sessionNode.path("createdAt").asText(""))
        );
        session.setTitle(sessionNode.path("title").asText(session.getLabel()));
        session.setDescription(sessionNode.path("description").asText("No researcher messages yet."));
        return session;
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return LocalDateTime.now();
        }
        try {
            return LocalDateTime.parse(value);
        } catch (Exception ignored) {
            return LocalDateTime.now();
        }
    }

    private List<Map<String, String>> parseMessages(JsonNode messagesNode) {
        List<Map<String, String>> messages = new ArrayList<>();
        if (!messagesNode.isArray()) {
            return messages;
        }
        for (JsonNode messageNode : messagesNode) {
            Map<String, String> message = new LinkedHashMap<>();
            message.put("role", messageNode.path("role").asText(""));
            message.put("content", messageNode.path("content").asText(""));
            messages.add(message);
        }
        return messages;
    }

    public record StoredChatSessions(
            List<AgentChatSession> sessions,
            Map<String, List<Map<String, String>>> messagesBySessionId
    ) {
    }
}
