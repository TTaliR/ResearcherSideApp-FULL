package com.example.demo.service;

import com.example.demo.model.Schedule;
import com.example.demo.model.ScheduleApiResponse;
import com.example.demo.model.SensorRuleConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

// ApiService: Responsible for all HTTP communication with the backend webhook API.
// - All network calls are executed asynchronously (CompletableFuture) so callers must handle results off the FX thread.
// - JSON parsing is done with Jackson's ObjectMapper.
// - Responses may be raw arrays, wrapped in {data: [...]}, {payload: [...]}, or single objects; use extractArray() to normalize.
public class ApiService {

    // should be replaced with ngrok link
    private static final String BASE_URL = "http://localhost:5678/webhook"; //for local testing
    //private static final String BASE_URL = "https://marcella-unguerdoned-ayanna.ngrok-free.dev/webhook";  //server
    //private static final String BASE_URL = "https://vacantly-holmic-etta.ngrok-free.dev/webhook";  //liran
    //private static final String BASE_URL = "https://barbera-astrophotographic-jairo.ngrok-free.dev/webhook";  //tom
    //private static final String BASE_URL = "https://entomb-imprudent-diffusion.ngrok-free.dev"; //tali

    public static final String EP_GET_USERS = "/get-users";
    public static final String EP_GET_USECASES = "/get-sensor-types";
    public static final String EP_SENSOR_DATA = "/sensor-data";
    public static final String EP_CURRENT_CONFIGURATION = "/current-configurations";
    public static final String EP_SET_MONITORING_TYPE = "/set-monitoring-type";
    public static final String EP_GET_MONITORING_CONFIG = "/monitoring-config";
    public static final String EP_SET_SUN = "/set-sun-azimuth-threshold";
    public static final String EP_SET_MOON = "/set-moon-azimuth-threshold";
    public static final String EP_SET_HEART = "/set-heart-rate-threshold";
    public static final String EP_SET_RULES = "/set-rules";
    public static final String EP_MAPPING_ACTIVATION = "/mapping-activation";
    public static final String EP_SET_LOGGING_INTERVAL = "/set-logging-interval";
    public static final String EP_CHAT_CONFIG = "/chat";
    public static final String EP_CHECK_CONNECTION = "/check-connection";
    public static final String EP_SET_USERS = "/edit-users";
    public static final String EP_ADD_USER = "/add-user";
    public static final String EP_SCHEDULE = "/schedule";
    public static final String EP_CREATE_USECASE = "/create-usecase";
    public static final String EP_ASSIGN_USECASE = "/assign-usecase";
    public static final String EP_GET_USER_MAPPING_HISTORY = "/users-mappings-history";


    private static final ApiService INSTANCE = new ApiService();
    private final ObjectMapper mapper;
    private Map<String, Integer> sensorTypeCache;

    private ApiService() {
        this.mapper = new ObjectMapper();
        this.sensorTypeCache = new HashMap<>();
    }

    public static ApiService getInstance() { return INSTANCE; }
    public ObjectMapper getMapper() { return mapper; }

    public static final class RuleRoute {
        private final String endpoint;
        private final String payloadKey;

        public RuleRoute(String endpoint, String payloadKey) {
            this.endpoint = endpoint;
            this.payloadKey = payloadKey;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public String getPayloadKey() {
            return payloadKey;
        }
    }

    public RuleRoute resolveRuleRoute(String ruleType) {
        String normalized = normalizeRuleType(ruleType);
        if ("sunazimuth".equals(normalized)) {
            return new RuleRoute(EP_SET_SUN, "sunAzimuthRanges");
        }
        if ("moonazimuth".equals(normalized)) {
            return new RuleRoute(EP_SET_MOON, "moonAzimuthRanges");
        }
        return new RuleRoute(EP_SET_HEART, "heartRateMappings");
    }

    public CompletableFuture<Boolean> saveSensorRuleConfig(SensorRuleConfig ruleConfig) {
        if (ruleConfig == null) {
            return CompletableFuture.completedFuture(false);
        }

        RuleRoute route = new RuleRoute(EP_SET_RULES, "rules"); // Default route
        Map<String, Object> payload = new HashMap<>();
        payload.put(route.getPayloadKey(), List.of(ruleConfig));
        return post(route.getEndpoint(), payload);
    }

    public CompletableFuture<Boolean> saveSensorRuleConfigs(List<SensorRuleConfig> rules) {
        if (rules == null || rules.isEmpty()) {
            return CompletableFuture.completedFuture(true);
        }

        boolean allSuccessful = true;
        for (SensorRuleConfig rule : rules) {
            try {
                if (!saveSensorRuleConfig(rule).get()) {
                    allSuccessful = false;
                }
            } catch (Exception e) {
                System.err.println("Failed to save rule config: " + e.getMessage());
                allSuccessful = false;
            }
        }
        return CompletableFuture.completedFuture(allSuccessful);
    }

    public CompletableFuture<JsonNode> createUseCase(String name, String description) {
        String trimmedName = name == null ? "" : name.trim();
        if (trimmedName.isEmpty()) {
            return CompletableFuture.completedFuture(
                    mapper.createObjectNode().put("error", "Use case name is required")
            );
        }

        // Disallow any whitespace characters in the use case name (no spaces, tabs, newlines, etc.)
        if (trimmedName.matches(".*\\s.*")) {
            return CompletableFuture.completedFuture(
                    mapper.createObjectNode().put("error", "Use case name must not contain spaces")
            );
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("name", trimmedName);
        payload.put("description", description == null ? "" : description.trim());

        return postWithResponse(EP_CREATE_USECASE, payload);
    }

    public CompletableFuture<Boolean> assignUseCaseToUser(int userId, int usecaseId) {
        if (userId <= 0 || usecaseId <= 0) {
            return CompletableFuture.completedFuture(false);
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", userId);
        payload.put("usecaseId", usecaseId);

        return post(EP_ASSIGN_USECASE, payload);
    }

    public CompletableFuture<Boolean> assignMappingToUser(int userId, int mappingId,
                                                          int usecaseId, String usecaseName,
                                                          String sessionId) {
        String trimmedUsecaseName = usecaseName == null ? "" : usecaseName.trim();
        String trimmedSessionId = sessionId == null ? "" : sessionId.trim();
        if (userId <= 0 || mappingId <= 0 || usecaseId <= 0
                || trimmedUsecaseName.isEmpty() || trimmedSessionId.isEmpty()) {
            return CompletableFuture.completedFuture(false);
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("session_id", trimmedSessionId);
        payload.put("usecase_id", usecaseId);
        payload.put("usecase_name", trimmedUsecaseName);
        payload.put("action", "assign_mapping_to_user");
        payload.put("user_id", userId);
        payload.put("mapping_id", mappingId);
        payload.put("feedback_config_rule_id", mappingId);
        payload.put("message", "Assign mapping ID " + mappingId + " for " + trimmedUsecaseName
            + " to user " + userId + ".");

        return postWithResponse(EP_CHAT_CONFIG, payload)
            .thenApply(response -> {
                if (response == null || response.has("error")) {
                    return false;
                }
                String reply = extractTextField(response, "reply").toLowerCase();
                String message = extractTextField(response, "message").toLowerCase();
                String combined = reply + " " + message;
                return combined.contains("assigned")
                    || combined.contains("updated")
                    || combined.contains("now active")
                    || combined.contains("success");
            })
            .exceptionally(ex -> false);
    }

    public CompletableFuture<Boolean> setMappingActive(int mappingId, boolean active) {
        if (mappingId <= 0) {
            return CompletableFuture.completedFuture(false);
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("mappingId", mappingId);
        payload.put("active", active);
        return post(EP_MAPPING_ACTIVATION, payload);
    }

    public CompletableFuture<Boolean> deleteMapping(int mappingId) {
        return setMappingActive(mappingId, false);
    }

    public CompletableFuture<Boolean> changeMapping(int mappingId, SensorRuleConfig ruleConfig,
                                                    int usecaseId, String usecaseName,
                                                    String sessionId) {
        String trimmedUsecaseName = usecaseName == null ? "" : usecaseName.trim();
        String trimmedSessionId = sessionId == null ? "" : sessionId.trim();
        if (mappingId <= 0 || ruleConfig == null || usecaseId <= 0 || trimmedUsecaseName.isEmpty() || trimmedSessionId.isEmpty()) {
            return CompletableFuture.completedFuture(false);
        }

        Map<String, Object> params = buildMappingChangeParams(mappingId, ruleConfig);

        Map<String, Object> payload = new HashMap<>();
        payload.put("session_id", trimmedSessionId);
        payload.put("usecase_id", usecaseId);
        payload.put("usecase_name", trimmedUsecaseName);
        payload.put("action", "change");
        payload.put("apply_scope", "all_users");
        payload.put("original_mapping_id", mappingId);
        payload.put("params", params);
        payload.put("message", buildMappingChangeForAllUsersMessage(trimmedUsecaseName, params));

        return postWithResponse(EP_CHAT_CONFIG, payload)
            .thenApply(response -> {
                if (response == null || response.has("error")) {
                    return false;
                }
                String reply = extractTextField(response, "reply").toLowerCase();
                return !reply.isBlank()
                    && (reply.contains("updated") || reply.contains("mapping id " + mappingId) || reply.contains("has been"));
            })
            .exceptionally(ex -> false);
    }

    public CompletableFuture<Boolean> changeMappingForUserOnly(int mappingId, int userId, SensorRuleConfig ruleConfig,
                                                               int usecaseId, String usecaseName,
                                                               String sessionId) {
        String trimmedUsecaseName = usecaseName == null ? "" : usecaseName.trim();
        String trimmedSessionId = sessionId == null ? "" : sessionId.trim();
        if (mappingId <= 0 || userId <= 0 || ruleConfig == null || usecaseId <= 0
                || trimmedUsecaseName.isEmpty() || trimmedSessionId.isEmpty()) {
            return CompletableFuture.completedFuture(false);
        }

        Map<String, Object> params = buildMappingChangeParams(mappingId, ruleConfig);

        Map<String, Object> payload = new HashMap<>();
        payload.put("session_id", trimmedSessionId);
        payload.put("usecase_id", usecaseId);
        payload.put("usecase_name", trimmedUsecaseName);
        payload.put("action", "duplicate_mapping_for_user");
        payload.put("apply_scope", "current_user");
        payload.put("user_id", userId);
        payload.put("original_mapping_id", mappingId);
        payload.put("params", params);
        payload.put("message", buildMappingChangeForUserOnlyMessage(trimmedUsecaseName, userId, params));

        return postWithResponse(EP_CHAT_CONFIG, payload)
            .thenApply(response -> {
                if (response == null || response.has("error")) {
                    return false;
                }
                String reply = extractTextField(response, "reply").toLowerCase();
                return !reply.isBlank()
                    && (reply.contains("duplicated")
                        || reply.contains("created")
                        || reply.contains("assigned")
                        || reply.contains("updated"));
            })
            .exceptionally(ex -> false);
    }

    private Map<String, Object> buildMappingChangeParams(int mappingId, SensorRuleConfig ruleConfig) {
        Map<String, Object> params = new HashMap<>();
        params.put("id", mappingId);
        params.put("minvalue", ruleConfig.getMinvalue());
        params.put("maxvalue", ruleConfig.getMaxvalue());
        params.put("minpulses", ruleConfig.getMinpulses());
        params.put("maxpulses", ruleConfig.getMaxpulses());
        params.put("minintensity", ruleConfig.getMinintensity());
        params.put("maxintensity", ruleConfig.getMaxintensity());
        params.put("minduration", ruleConfig.getMinduration());
        params.put("maxduration", ruleConfig.getMaxduration());
        params.put("mininterval", ruleConfig.getMininterval());
        params.put("maxinterval", ruleConfig.getMaxinterval());
        return params;
    }

    private String buildMappingChangeForAllUsersMessage(String usecaseName, Map<String, Object> params) {
        return "Apply to all users: change mapping ID " + params.get("id") + " for " + usecaseName
            + " with minvalue " + params.get("minvalue")
            + ", maxvalue " + params.get("maxvalue")
            + ", minpulses " + params.get("minpulses")
            + ", maxpulses " + params.get("maxpulses")
            + ", minintensity " + params.get("minintensity")
            + ", maxintensity " + params.get("maxintensity")
            + ", minduration " + params.get("minduration")
            + ", maxduration " + params.get("maxduration")
            + ", mininterval " + params.get("mininterval")
            + ", and maxinterval " + params.get("maxinterval") + ".";
    }

    private String buildMappingChangeForUserOnlyMessage(String usecaseName, int userId, Map<String, Object> params) {
        return "Duplicate mapping ID " + params.get("id") + " for user " + userId
            + " only, assign the duplicate to that user, and change the duplicate for " + usecaseName
            + " with minvalue " + params.get("minvalue")
            + ", maxvalue " + params.get("maxvalue")
            + ", minpulses " + params.get("minpulses")
            + ", maxpulses " + params.get("maxpulses")
            + ", minintensity " + params.get("minintensity")
            + ", maxintensity " + params.get("maxintensity")
            + ", minduration " + params.get("minduration")
            + ", maxduration " + params.get("maxduration")
            + ", mininterval " + params.get("mininterval")
            + ", and maxinterval " + params.get("maxinterval")
            + ". Leave the original mapping unchanged for all other users.";
    }

    public CompletableFuture<Boolean> setLoggingInterval(int usecaseId, String interval) {
        if (usecaseId <= 0 || interval == null || interval.isBlank()) {
            return CompletableFuture.completedFuture(false);
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("usecase_id", usecaseId);
        payload.put("interval", interval.trim());
        return post(EP_SET_LOGGING_INTERVAL, payload);
    }

    public CompletableFuture<JsonNode> getUserMappingHistory(int userId, String useCaseName) {
        if (userId <= 0 || useCaseName == null || useCaseName.isBlank()) {
            return CompletableFuture.completedFuture(
                mapper.createObjectNode().put("status", "error").put("message", "Missing required parameters.")
            );
        }

        Map<String, String> params = new HashMap<>();
        params.put("userId", String.valueOf(userId));
        params.put("useCaseName", useCaseName);

        return get(EP_GET_USER_MAPPING_HISTORY, params);
    }

    public CompletableFuture<JsonNode> sendDictionaryRequest(String message, int contextUsecaseId,
                                                             String contextUsecaseName, String sessionId) {
        String trimmedMessage = message == null ? "" : message.trim();
        String trimmedSessionId = sessionId == null ? "" : sessionId.trim();
        String trimmedUsecaseName = contextUsecaseName == null ? "" : contextUsecaseName.trim();
        if (trimmedMessage.isEmpty() || contextUsecaseId <= 0 || trimmedSessionId.isEmpty()) {
            return CompletableFuture.completedFuture(
                mapper.createObjectNode().put("error", "Invalid dictionary request")
            );
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("session_id", trimmedSessionId);
        payload.put("usecase_id", contextUsecaseId);
        payload.put("usecase_name", trimmedUsecaseName);
        payload.put("message", trimmedMessage);
        return postWithResponse(EP_CHAT_CONFIG, payload);
    }

    public CompletableFuture<Boolean> setUserName(int userId, String fName, String lName) {
        String trimmedFirstName = fName == null ? "" : fName.trim();
        String trimmedLastName = lName == null ? "" : lName.trim();
        if (userId <= 0 || trimmedFirstName.isBlank() || trimmedLastName.isBlank()) {
            return CompletableFuture.completedFuture(false);
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", userId);
        payload.put("fName", trimmedFirstName);
        payload.put("lName", trimmedLastName);
        return post(EP_SET_USERS, payload);
    }

    public CompletableFuture<Boolean> addUser(int userId, String firstName, String lastName) {
        String trimmedFirstName = firstName == null ? "" : firstName.trim();
        String trimmedLastName = lastName == null ? "" : lastName.trim();
        if (userId <= 0) {
            return CompletableFuture.completedFuture(false);
        }

        Map<String, String> params = new HashMap<>();
        params.put("userId", String.valueOf(userId));
        params.put("firstName", trimmedFirstName);
        params.put("lastName", trimmedLastName);

        return get(EP_ADD_USER, params)
            .thenApply(response -> {
                if (response == null || response.has("error")) {
                    return false;
                }
                int responseCode = response.path("code").asInt(response.path("statusCode").asInt(200));
                return responseCode >= 200 && responseCode < 300;
            })
            .exceptionally(ex -> false);
    }

    public CompletableFuture<ScheduleApiResponse> listAllSchedules(String usecaseName) {
        return listAllSchedules(usecaseName, 0);
    }

    public CompletableFuture<ScheduleApiResponse> listAllSchedules(String usecaseName, int userId) {
        Map<String, Object> params = new HashMap<>();
        if (userId > 0) {
            params.put("user_id", userId);
        }
        return sendScheduleRequest("listAll", params, usecaseName,
            "manual schedule list from researcher UI");
    }

    public CompletableFuture<ScheduleApiResponse> listActiveSchedules(String usecaseName) {
        return listActiveSchedules(usecaseName, 0);
    }

    public CompletableFuture<ScheduleApiResponse> listActiveSchedules(String usecaseName, int userId) {
        Map<String, Object> params = new HashMap<>();
        if (userId > 0) {
            params.put("user_id", userId);
        }
        return sendScheduleRequest("listActive", params, usecaseName,
            "manual schedule active list from researcher UI");
    }

    public CompletableFuture<ScheduleApiResponse> addSchedule(int userId, int intervalDays, String measureType,
                                                              int triggerPercentage, String usecaseName) {
        String trimmedMeasureType = measureType == null ? "" : measureType.trim();
        if (userId <= 0 || intervalDays <= 0 || trimmedMeasureType.isEmpty() || triggerPercentage == 0) {
            return CompletableFuture.completedFuture(
                failedScheduleResponse("Invalid schedule add request: userId, intervalDays, measureType, and triggerPercentage must be valid")
            );
        }

        Map<String, Object> params = new HashMap<>();
        params.put("user_id", userId);
        params.put("interval_days", intervalDays);
        params.put("measure_type", trimmedMeasureType);
        params.put("trigger_percentage", toScheduleTriggerPayloadValue(triggerPercentage));
        return sendScheduleRequest("add", params, usecaseName, "manual schedule add from researcher UI");
    }

    public CompletableFuture<ScheduleApiResponse> changeSchedule(int scheduleId, int userId, int intervalDays,
                                                                 String measureType, int triggerPercentage,
                                                                 String usecaseName) {
        String trimmedMeasureType = measureType == null ? "" : measureType.trim();
        if (scheduleId <= 0 || userId <= 0 || intervalDays <= 0 || trimmedMeasureType.isEmpty() || triggerPercentage == 0) {
            return CompletableFuture.completedFuture(
                failedScheduleResponse("Invalid schedule change request: scheduleId, userId, intervalDays, measureType, and triggerPercentage must be valid")
            );
        }

        Map<String, Object> params = new HashMap<>();
        params.put("schedule_id", scheduleId);
        params.put("user_id", userId);
        params.put("interval_days", intervalDays);
        params.put("measure_type", trimmedMeasureType);
        params.put("trigger_percentage", toScheduleTriggerPayloadValue(triggerPercentage));
        return sendScheduleRequest("change", params, usecaseName, "manual schedule edit from researcher UI");
    }

    private double toScheduleTriggerPayloadValue(int triggerPercentage) {
        return triggerPercentage / 100.0;
    }

    public CompletableFuture<ScheduleApiResponse> deactivateSchedule(int scheduleId, String usecaseName) {
        if (scheduleId <= 0) {
            return CompletableFuture.completedFuture(failedScheduleResponse("Invalid schedule deactivate request: scheduleId must be valid"));
        }

        Map<String, Object> params = new HashMap<>();
        params.put("schedule_id", scheduleId);
        return sendScheduleRequest("deactivate", params, usecaseName, "manual schedule deactivate from researcher UI");
    }

    public CompletableFuture<ScheduleApiResponse> activateSchedule(int scheduleId, String usecaseName) {
        if (scheduleId <= 0) {
            return CompletableFuture.completedFuture(failedScheduleResponse("Invalid schedule activate request: scheduleId must be valid"));
        }

        Map<String, Object> params = new HashMap<>();
        params.put("schedule_id", scheduleId);
        return sendScheduleRequest("activate", params, usecaseName, "manual schedule activate from researcher UI");
    }

    private CompletableFuture<ScheduleApiResponse> sendScheduleRequest(String action, Map<String, Object> params,
                                                                       String usecaseName, String message) {
        String trimmedUsecaseName = usecaseName == null ? "" : usecaseName.trim();
        if (trimmedUsecaseName.isEmpty()) {
            return CompletableFuture.completedFuture(failedScheduleResponse("usecaseName is required"));
        }

        return getSensorIdByName(trimmedUsecaseName)
            .thenCompose(usecaseId -> {
                if (usecaseId <= 0) {
                    return CompletableFuture.completedFuture(
                        failedScheduleResponse("Could not resolve use case id for " + trimmedUsecaseName)
                    );
                }

                Map<String, Object> payload = new HashMap<>();
                payload.put("action", action);
                payload.put("params", params == null ? new HashMap<>() : params);
                payload.put("session_id", "researcher-ui");
                payload.put("usecase_id", usecaseId);
                payload.put("usecase_name", trimmedUsecaseName);
                payload.put("message", message == null ? "" : message);

                return postWithResponse(EP_SCHEDULE, payload)
                    .thenApply(this::parseScheduleResponse);
            })
            .exceptionally(ex -> failedScheduleResponse("Schedule request failed: " + ex.getMessage()));
    }

    private ScheduleApiResponse parseScheduleResponse(JsonNode response) {
        ScheduleApiResponse result = new ScheduleApiResponse();
        result.setRawResponse(response);
        result.setRawJsonResponse(response == null ? "" : response.toString());

        if (response == null || response.isNull()) {
            result.setSuccess(false);
            result.setErrorMessage("Empty schedule response");
            return result;
        }

        boolean hasSchedules = false;
        JsonNode scheduleArray = extractScheduleArrayNode(response);
        if (scheduleArray != null && scheduleArray.isArray()) {
            hasSchedules = true;
            result.setSchedules(parseSchedules(scheduleArray));
        } else {
            result.setSchedules(new java.util.ArrayList<>());
        }

        String reply = extractTextField(response, "reply");
        String message = extractTextField(response, "message");
        String error = extractTextField(response, "error");

        if (!reply.isBlank()) {
            result.setReply(reply);
        } else if (!message.isBlank() && (response.path("success").isBoolean() ? response.path("success").asBoolean(false) : true)) {
            result.setReply(message);
        }

        boolean explicitSuccess = response.has("success") ? response.path("success").asBoolean(false) : (hasSchedules || !reply.isBlank() || !message.isBlank());
        if (response.has("success") && !response.path("success").asBoolean(false)) {
            explicitSuccess = false;
        }
        if (!error.isBlank()) {
            explicitSuccess = false;
        }

        result.setSuccess(explicitSuccess);

        if (!result.isSuccess()) {
            String errorMessage = firstNonBlank(error, message, reply, "Schedule request failed");
            result.setErrorMessage(errorMessage);
        } else {
            result.setErrorMessage("");
        }

        if (!hasSchedules && reply.isBlank() && message.isBlank() && error.isBlank()) {
            result.setSuccess(false);
            result.setErrorMessage("Invalid schedule response format");
        }

        return result;
    }

    private JsonNode extractScheduleArrayNode(JsonNode response) {
        if (response == null) {
            return null;
        }

        if (response.isArray()) {
            return response;
        }

        if (response.has("data") && response.get("data").isArray()) {
            return response.get("data");
        }

        if (response.has("payload") && response.get("payload").isArray()) {
            return response.get("payload");
        }

        if (response.has("schedules") && response.get("schedules").isArray()) {
            return response.get("schedules");
        }

        return null;
    }

    private List<Schedule> parseSchedules(JsonNode scheduleArray) {
        List<Schedule> schedules = new java.util.ArrayList<>();
        if (scheduleArray == null || !scheduleArray.isArray()) {
            return schedules;
        }

        for (JsonNode node : scheduleArray) {
            schedules.add(parseSchedule(node));
        }
        return schedules;
    }

    private Schedule parseSchedule(JsonNode node) {
        Schedule schedule = new Schedule();
        if (node == null || node.isNull() || !node.isObject()) {
            return schedule;
        }

        schedule.setScheduleId(node.path("schedule_id").asInt(node.path("id").asInt(0)));
        schedule.setUserId(node.path("user_id").asInt(node.path("userId").asInt(0)));
        schedule.setMeasureType(node.path("measure_type").asText(node.path("measureType").asText("")));
        schedule.setTriggerPercentage(node.path("trigger_percentage").asDouble(node.path("triggerPercentage").asDouble(0.0)));
        schedule.setNextCheck(node.path("next_check").asText(node.path("nextCheck").asText("")));
        schedule.setNextRunDate(node.path("next_run_date").asText(node.path("nextRunDate").asText("")));
        schedule.setIntervalDays(node.path("interval_days").asInt(node.path("intervalDays").asInt(0)));
        schedule.setActive(node.path("active").asBoolean(node.path("isActive").asBoolean(false)));
        return schedule;
    }

    private String extractTextField(JsonNode response, String fieldName) {
        if (response == null || fieldName == null || !response.has(fieldName)) {
            return "";
        }

        JsonNode node = response.get(fieldName);
        if (node == null || node.isNull()) {
            return "";
        }

        if (node.isTextual()) {
            return node.asText("").trim();
        }

        return node.asText("").trim();
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }

        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }

    private ScheduleApiResponse failedScheduleResponse(String message) {
        ScheduleApiResponse response = new ScheduleApiResponse();
        response.setSuccess(false);
        response.setSchedules(new java.util.ArrayList<>());
        response.setErrorMessage(message == null ? "Schedule request failed" : message);
        response.setReply("");
        response.setRawJsonResponse("");
        return response;
    }

    public CompletableFuture<String> getMonitoringType() {
        return get(EP_GET_MONITORING_CONFIG, null)
            .thenApply(this::extractMonitoringTypeFromResponse)
            .thenCompose(type -> {
                if (type != null && !type.isBlank()) {
                    return CompletableFuture.completedFuture(type);
                }

                // Some webhook configurations only return monitoring config on POST.
                return postWithResponse(EP_GET_MONITORING_CONFIG, new HashMap<>())
                    .thenApply(this::extractMonitoringTypeFromResponse)
                    .exceptionally(ignored -> "");
            })
            .exceptionally(ignored -> "");
    }

    public CompletableFuture<Boolean> setMonitoringType(int userId, String monitoringType) {
        String trimmedType = monitoringType == null ? "" : monitoringType.trim();
        if (userId <= 0 || trimmedType.isEmpty()) {
            return CompletableFuture.completedFuture(false);
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", userId);
        payload.put("monitoringType", trimmedType);
        return post(EP_SET_MONITORING_TYPE, payload);
    }

    private String extractMonitoringTypeFromResponse(JsonNode response) {
        if (response == null) {
            return "";
        }

        if (response.isObject() && response.has("error")) {
            return "";
        }

        String directValue = extractMonitoringTypeValue(response);
        if (!directValue.isEmpty()) {
            return directValue;
        }

        JsonNode configNode = response;
        if (response.has("data")) {
            configNode = response.get("data");
        } else if (response.has("payload")) {
            configNode = response.get("payload");
        }

        return extractMonitoringTypeValue(configNode);
    }

    private String extractMonitoringTypeValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }

        if (node.isTextual()) {
            return node.asText("").trim();
        }

        if (node.isArray()) {
            for (JsonNode element : node) {
                String value = extractMonitoringTypeValue(element);
                if (!value.isEmpty()) {
                    return value;
                }
            }
            return "";
        }

        if (!node.isObject()) {
            return "";
        }

        String[] possibleKeys = new String[] {
            "monitoringType",
            "monitoring_type",
            "activeMonitoringType",
            "active_monitoring_type",
            "type",
            "name"
        };

        for (String key : possibleKeys) {
            JsonNode valueNode = node.path(key);
            if (valueNode.isTextual()) {
                String value = valueNode.asText("").trim();
                if (!value.isEmpty()) {
                    return value;
                }
            }
        }

        // Fallback: search nested objects/arrays for any known key.
        if (node.has("data")) {
            String nested = extractMonitoringTypeValue(node.get("data"));
            if (!nested.isEmpty()) {
                return nested;
            }
        }
        if (node.has("payload")) {
            String nested = extractMonitoringTypeValue(node.get("payload"));
            if (!nested.isEmpty()) {
                return nested;
            }
        }

        return "";
    }

    private String normalizeRuleType(String ruleType) {
        if (ruleType == null) {
            return "";
        }

        String normalized = ruleType.replaceAll("[^A-Za-z0-9]", "").toLowerCase();
        if (normalized.contains("sun") && normalized.contains("azimuth")) {
            return "sunazimuth";
        }
        if (normalized.contains("moon") && normalized.contains("azimuth")) {
            return "moonazimuth";
        }
        if (normalized.contains("heartrate")) {
            return "heartrate";
        }
        return normalized;
    }

    // --- GET Request ---
    /**
     * Perform an asynchronous HTTP GET.
     * - endpoint: endpoint path (uses BASE_URL internally)
     * - params: optional query parameters (encoded)
     * Returns CompletableFuture<JsonNode> holding parsed JSON. If the API responds with an error, the returned JsonNode will have an "error" field.
     * Note: This method runs off the JavaFX thread; call Platform.runLater(...) when updating UI.
     */
    public CompletableFuture<JsonNode> get(String endpoint, Map<String, String> params) {
        return CompletableFuture.supplyAsync(() -> {
            HttpURLConnection conn = null;
            try {
                String urlStr = BASE_URL + endpoint;
                if (params != null && !params.isEmpty()) {
                    String queryString = params.entrySet().stream()
                            .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                            .collect(Collectors.joining("&"));
                    urlStr += "?" + queryString;
                }

                System.out.println("GET: " + urlStr);
                URL url = new URL(urlStr);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(10000);  // 10 seconds for initial connection
                conn.setReadTimeout(45000);     // 30 seconds for reading response (large payloads may take time)

                int status = conn.getResponseCode();
                if (status != 200) {
                    System.err.println("API Error: HTTP " + status + " - " + conn.getResponseMessage());
                    System.err.println("Endpoint: " + endpoint);
                    System.err.println("Parameters: " + params);
                    return mapper.createObjectNode().put("error", status).put("message", conn.getResponseMessage());
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                String body = response.toString();
                if (body.trim().isEmpty()) {
                    System.out.println("GET returned empty response");
                    return mapper.createArrayNode();
                }
                System.out.println("GET Response: Successfully received " + body.length() + " bytes");
                return mapper.readTree(body);

            } catch (Exception e) {
                System.err.println("GET Request Failed - " + e.getClass().getSimpleName() + ": " + e.getMessage());
                System.err.println("Endpoint: " + endpoint);
                e.printStackTrace();
                throw new RuntimeException("Connection failed: " + e.getMessage());
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    // --- POST Request ---
    /**
     * Asynchronous POST that returns success boolean.
     * Use when you only need to know whether the request succeeded (HTTP 200).
     * Runs off the UI thread.
     */
    public CompletableFuture<Boolean> post(String endpoint, Object data) {
        return CompletableFuture.supplyAsync(() -> {
            HttpURLConnection conn = null;
            try {
                String jsonInputString = mapper.writeValueAsString(data);
                String urlStr = BASE_URL + endpoint;

                System.out.println("POST: " + urlStr);
                URL url = new URL(urlStr);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setDoOutput(true);
                conn.setConnectTimeout(10000);  // 10 seconds for connection
                conn.setReadTimeout(45000);     // 30 seconds for response

                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                int code = conn.getResponseCode();
                System.out.println("POST Response Code: " + code + " - " + conn.getResponseMessage());

                if (code != 200) {
                    System.err.println("POST Failed - HTTP " + code + " for endpoint: " + endpoint + " - " + conn.getResponseMessage());
                }
                return code == 200;

            } catch (Exception e) {
                System.err.println("POST Request Failed - " + e.getClass().getSimpleName() + ": " + e.getMessage());
                System.err.println("Endpoint: " + endpoint);
                e.printStackTrace();
                return false;
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    /**
     * Asynchronous POST that returns parsed JSON response.
     * Use when the endpoint returns JSON you need to consume.
     * Returns a JsonNode containing the response or an object with an "error" field on failure.
     */
    public CompletableFuture<JsonNode> postWithResponse(String endpoint, Object data) {
        return CompletableFuture.supplyAsync(() -> {
            HttpURLConnection conn = null;
            try {
                String jsonInputString = mapper.writeValueAsString(data);
                URL url = new URL(BASE_URL + endpoint);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setDoOutput(true);
                conn.setConnectTimeout(10000);  // 10 seconds for connection
                conn.setReadTimeout(30000);     // 30 seconds for response

                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                int code = conn.getResponseCode();
                System.out.println("POST Response Code: " + code + " - " + conn.getResponseMessage());

                if (code == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();
                    return mapper.readTree(response.toString());
                } else {
                    System.err.println("POST Failed - HTTP " + code + " for endpoint: " + endpoint);
                    return mapper.createObjectNode().put("error", code).put("message", conn.getResponseMessage());
                }
            } catch (Exception e) {
                System.err.println("POST Request Failed - " + e.getClass().getSimpleName() + ": " + e.getMessage());
                System.err.println("Endpoint: " + endpoint);
                e.printStackTrace();
                return mapper.createObjectNode().put("error", "Request failed").put("message", e.getMessage());
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    // Helper to URL-encode parameter keys/values
    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public String getBaseUrl() {
        return BASE_URL;
    }

    /**
     * Normalizes multiple possible API response shapes into a Json array:
     * - raw array -> returned as-is
     * - { "data": [...] } -> returns data array
     * - { "payload": [...] } -> returns payload array
     * - single object (e.g. contains userid or sensorid) -> wrapped into a single-element array
     * Returns null if response contains an "error" field or shape is unrecognized.
     *
     * Important: callers should check for null before iterating.
     */
    public JsonNode extractArray(JsonNode response) {
        if (response == null) {
            System.err.println("WARNING: extractArray called with null response");
            return null;
        }

        // Check for API error in response first
        if (response.has("error")) {
            int errorCode = response.has("error") ? response.get("error").asInt() : 0;
            String errorMsg = response.has("message") ? response.get("message").asText() : "Unknown error";
            System.err.println("ERROR: Response contains error - Code: " + errorCode + ", Message: " + errorMsg);
            return null;
        }

        // If already an array, return as-is
        if (response.isArray()) {
            return response;
        }

        // Try to extract from "data" wrapper
        if (response.has("data") && response.get("data").isArray()) {
            return response.get("data");
        }

        // Try to extract from "payload" wrapper
        if (response.has("payload") && response.get("payload").isArray()) {
            return response.get("payload");
        }

        // Single object, wrap in array
        if (response.has("userid") || response.has("sensorid")) {
            return mapper.createArrayNode().add(response);
        }

        System.err.println("WARNING: Could not extract array from response - format not recognized");
        return null;
    }

    /**
     * Fetch sensor types and cache them locally to avoid repeated network calls.
     * Returns a CompletableFuture<Map<String,Integer>> mapping sensor name -> sensor id.
     */
    public CompletableFuture<Map<String, Integer>> getSensorTypes() {
        return CompletableFuture.supplyAsync(() -> {
            // Return cached data if available
            if (!sensorTypeCache.isEmpty()) {
                return new HashMap<>(sensorTypeCache);
            }

            // Otherwise fetch from API
            try {
                JsonNode response = get(EP_GET_USECASES, null).get();
                Map<String, Integer> sensorMap = new HashMap<>();

                if (response.isArray()) {
                    for (JsonNode node : response) {
                        String sensorName = node.get("name").asText();
                        int sensorId = node.get("usecase_id").asInt();
                        sensorMap.put(sensorName, sensorId);
                    }
                }

                // Cache the results
                sensorTypeCache = new HashMap<>(sensorMap);
                return sensorMap;

            } catch (Exception e) {
                System.err.println("Failed to get sensor types: " + e.getMessage());
                return new HashMap<>();
            }
        });
    }

    /**
     * Convenience wrapper that returns sensor ID by name using the cached map.
     * Returns -1 when not found.
     */
    public CompletableFuture<Integer> getSensorIdByName(String name) {
        return getSensorTypes().thenApply(sensorMap -> sensorMap.getOrDefault(name, -1));
    }

    /**
     * Verifies connection via endpoint response; logs and returns status
     */
    public CompletableFuture<Boolean> checkConnectionAsync() {
        return post(EP_CHECK_CONNECTION, new HashMap<>())
            .thenApply(ok -> {
                if (!ok) {
                    System.err.println("Connection check failed: non-200 response");
                    return false;
                }

                System.out.println("Connection check successful");
                return true;
            })
            .exceptionally(ex -> {
                System.err.println("Connection check failed: " + ex.getMessage());
                return false;
            });
    }

    /**
     * Synchronous convenience wrapper. Do not call from the JavaFX application thread.
     */
    public boolean checkConnection() {
        try {
            return checkConnectionAsync().get();
        } catch (Exception e) {
            System.err.println("Connection check failed: " + e.getMessage());
            return false;
        }
    }
}
