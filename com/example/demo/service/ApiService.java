package com.example.demo.service;

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

    //should be replaced with ngrok link
    //private static final String BASE_URL = "http://localhost:5678/webhook"; //for local testing
    private static final String BASE_URL = "https://marcella-unguerdoned-ayanna.ngrok-free.dev/webhook";  //tali
    //private static final String BASE_URL = "https://vacantly-holmic-etta.ngrok-free.dev/webhook";  //liran

    public static final String EP_GET_USERS = "/get-users";
    public static final String EP_GET_USECASES = "/get-sensor-types";
    public static final String EP_SENSOR_DATA = "/sensor-data";
    public static final String EP_CURRENT_CONFIGURATION = "/current-configurations";
    public static final String EP_SET_MONITORING_TYPE = "/set-monitoring-type";
    public static final String EP_SET_SUN = "/set-sun-azimuth-threshold";
    public static final String EP_SET_MOON = "/set-moon-azimuth-threshold";
    public static final String EP_SET_HEART = "/set-heart-rate-threshold";
    public static final String EP_SET_RULES = "/set-rules";
    public static final String EP_DELETE_MAPPING = "/deactivate-mapping";
    public static final String EP_CHAT_CONFIG = "/chat";
    public static final String EP_CHECK_CONNECTION = "/check-connection";

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

    public CompletableFuture<Boolean> deleteMapping(int mappingId) {
        if (mappingId <= 0) {
            return CompletableFuture.completedFuture(false);
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("mappingId", mappingId);
        return post(EP_DELETE_MAPPING, payload);
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
                conn.setReadTimeout(30000);     // 30 seconds for reading response (large payloads may take time)

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
                conn.setReadTimeout(30000);     // 30 seconds for response

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
    public boolean checkConnection() {
        try {
            // Match n8n Respond to Webhook config that only guarantees HTTP 200
            boolean ok = post(EP_CHECK_CONNECTION, new HashMap<>()).get();

            if (!ok) {
                System.err.println("Connection check failed: non-200 response");
                return false;
            }

            System.out.println("Connection check successful");
            return true;
        } catch (Exception e) {
            System.err.println("Connection check failed: " + e.getMessage());
            return false;
        }
    }
}

