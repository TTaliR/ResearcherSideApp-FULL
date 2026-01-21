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

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public String getBaseUrl() {
        return BASE_URL;
    }

    /**
     * Extracts array from response that may be wrapped in "data", "payload", or be a raw array
     */
    public JsonNode extractArray(JsonNode response) {
        if (response.isArray()) {
            return response;
        } else if (response.has("data") && response.get("data").isArray()) {
            return response.get("data");
        } else if (response.has("payload") && response.get("payload").isArray()) {
            return response.get("payload");
        } else if (response.has("userid") || response.has("sensorid")) {
            // Single object, wrap in array
            return mapper.createArrayNode().add(response);
        }
        return null;
    }

    /**
     * Get sensor types with caching to avoid repeated API calls
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
     * Get sensor ID by name with caching
     */
    public CompletableFuture<Integer> getSensorIdByName(String name) {
        return getSensorTypes().thenApply(sensorMap -> sensorMap.getOrDefault(name, -1));
    }
}

