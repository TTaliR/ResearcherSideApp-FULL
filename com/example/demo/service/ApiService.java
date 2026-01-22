package com.example.demo.service;

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
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

// ApiService: Responsible for all HTTP communication with the backend webhook API.
// - All network calls are executed asynchronously (CompletableFuture) so callers must handle results off the FX thread.
// - JSON parsing is done with Jackson's ObjectMapper.
// - Responses may be raw arrays, wrapped in {data: [...]}, {payload: [...]}, or single objects; use extractArray() to normalize.
public class ApiService {

    //should be replaced with ngrok link
    private static final String BASE_URL = "http://localhost:5678/webhook";
    //private static final String BASE_URL = "https://marcella-unguerdoned-ayanna.ngrok-free.dev/webhook";  //tali
    //private static final String BASE_URL = "https://vacantly-holmic-etta.ngrok-free.dev/";  //liran
    public static final String EP_GET_USERS = "/get-users";
    public static final String EP_GET_SENSOR_TYPES = "/get-sensor-types";
    public static final String EP_SENSOR_DATA = "/sensor-data";
    public static final String EP_CURRENT_CONFIGURATION = "/current-configurations";
    public static final String EP_SET_MONITORING_TYPE = "/set-monitoring-type";
    public static final String EP_SET_SUN = "/set-sun-azimuth-threshold";
    public static final String EP_SET_MOON = "/set-moon-azimuth-threshold";
    public static final String EP_SET_HEART = "/set-heart-rate-threshold";
    public static final String EP_CHAT_CONFIG = "/chat-config"; //

    private static final ApiService INSTANCE = new ApiService();
    private final ObjectMapper mapper;
    private Map<String, Integer> sensorTypeCache;

    private ApiService() {
        this.mapper = new ObjectMapper();
        this.sensorTypeCache = new HashMap<>();
    }

    public static ApiService getInstance() { return INSTANCE; }
    public ObjectMapper getMapper() { return mapper; }

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
                    System.err.println("POST Failed - HTTP " + code + " for endpoint: " + endpoint);
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
                JsonNode response = get(EP_GET_SENSOR_TYPES, null).get();
                Map<String, Integer> sensorMap = new HashMap<>();

                if (response.isArray()) {
                    for (JsonNode node : response) {
                        String sensorName = node.get("name").asText();
                        int sensorId = node.get("sensorid").asInt();
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
}

