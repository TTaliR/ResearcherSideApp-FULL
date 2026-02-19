package com.example.demo.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import java.awt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * ExportService: Unified service for exporting sensor data to various formats (CSV, PDF).
 * - Centralizes data extraction and formatting logic to avoid code duplication.
 * - Handles JSON response unwrapping from API responses.
 * - Provides proper CSV escaping for values containing commas, quotes, or newlines.
 */
public class ExportService {

    // CSV headers matching the sensor data fields
    private static final String[] HEADERS = {"Time", "Value", "Alert Type", "Alert Given", "Pulses", "Intensity", "Duration (ms)", "Interval (ms)", "Reason"};

    // UTF-8 BOM for Excel compatibility
    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    /**
     * Data record class to hold extracted sensor data for export.
     */
    public static class SensorRecord {
        public final String time;
        public final String value;
        public final String alert_type;
        public final String alert_given;
        public final String pulses;
        public final String intensity;
        public final String duration;
        public final String interval;
        public final String reason;

        public SensorRecord(String time, String value, String alert_type, String pulses,
                            String intensity, String duration, String interval, String reason) {
            this.time = time;
            this.value = value;
            this.alert_type = alert_type;
            this.pulses = pulses;
            this.intensity = intensity;
            this.duration = duration;
            this.interval = interval;
            this.reason = reason;

            alert_given = getAlert_given(pulses);
        }

        private String getAlert_given(String pulses) {
            final String alert_given;
            int pulseCount = 0;
            try {
                if (pulses != null && !pulses.isEmpty()) {
                    pulseCount = Integer.parseInt(pulses);
                }
            } catch (NumberFormatException e) {
                pulseCount = 0;
            }
            alert_given = pulseCount > 0 ? "Yes" : "No";
            return alert_given;
        }
    }

    // ==================== CSV EXPORT ====================

    /**
     * Generate a CSV file from sensor data.
     *
     * @param filePath   The full path where the CSV should be saved
     * @param userId     The user ID for the report header
     * @param sensorType The sensor type name
     * @param timeRange  The time range description
     * @param response   The API response (may be wrapped or array)
     * @return The absolute path of the generated file
     * @throws Exception if generation fails
     */
    public static String generateCsv(String filePath, int userId, String sensorType,
                                      String timeRange, JsonNode response) throws Exception {
        System.out.println("\n===== generateCsv START =====");
        System.out.println("Parameters: filePath=" + filePath + ", userId=" + userId +
                          ", sensorType=" + sensorType + ", timeRange=" + timeRange);

        validateResponse(response);

        List<SensorRecord> records = extractRecords(response);
        System.out.println("Extracted " + records.size() + " records for CSV");

        try (FileOutputStream fos = new FileOutputStream(filePath);
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(fos, StandardCharsets.UTF_8))) {

            // Write UTF-8 BOM for Excel compatibility
            fos.write(UTF8_BOM);

            // Write metadata header as comments
            writer.write("# Sensor Data Export");
            writer.newLine();
            writer.write("# User ID: " + userId);
            writer.newLine();
            writer.write("# Sensor Type: " + sensorType);
            writer.newLine();
            writer.write("# Time Range: " + timeRange);
            writer.newLine();
            writer.write("# Generated: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            writer.newLine();
            writer.newLine();

            // Write CSV header row
            writer.write(String.join(",", HEADERS));
            writer.newLine();

            // Write data rows
            for (SensorRecord record : records) {
                writer.write(formatCsvRow(record));
                writer.newLine();
            }

            writer.flush();
        }

        String absolutePath = new File(filePath).getAbsolutePath();
        System.out.println("CSV saved to: " + absolutePath);
        System.out.println("===== generateCsv END =====\n");
        return absolutePath;
    }

    /**
     * Formats a single record as a CSV row with proper escaping.
     */
    private static String formatCsvRow(SensorRecord record) {
        return String.join(",",
                escapeCsvValue(record.time),
                escapeCsvValue(record.value),
                escapeCsvValue(record.alert_type),
                escapeCsvValue(record.alert_given),
                escapeCsvValue(record.pulses),
                escapeCsvValue(record.intensity),
                escapeCsvValue(record.duration),
                escapeCsvValue(record.interval),
                escapeCsvValue(record.reason)
        );
    }

    /**
     * Escapes a CSV value according to RFC 4180:
     * - If the value contains comma, quote, or newline, wrap in double quotes
     * - Double quotes inside the value are escaped by doubling them
     */
    private static String escapeCsvValue(String value) {
        if (value == null) {
            return "";
        }

        // Check if escaping is needed
        boolean needsEscaping = value.contains(",") || value.contains("\"") ||
                                value.contains("\n") || value.contains("\r");

        if (needsEscaping) {
            // Escape double quotes by doubling them
            String escaped = value.replace("\"", "\"\"");
            // Wrap in double quotes
            return "\"" + escaped + "\"";
        }

        return value;
    }

    // ==================== PDF EXPORT ====================

    /**
     * Generate a PDF report from sensor data.
     *
     * @param filePath   The full path where the PDF should be saved
     * @param userId     The user ID for the report
     * @param sensorType The sensor type name
     * @param timeRange  The time range description
     * @param response   The API response (may be wrapped or array)
     * @return The absolute path of the generated file
     * @throws Exception if generation fails
     */
    public static String generatePdf(String filePath, int userId, String sensorType,
                                      String timeRange, JsonNode response) throws Exception {
        System.out.println("\n===== generatePdf START =====");
        System.out.println("Parameters: filePath=" + filePath + ", userId=" + userId +
                          ", sensorType=" + sensorType + ", timeRange=" + timeRange);

        validateResponse(response);

        List<SensorRecord> records = extractRecords(response);
        System.out.println("Extracted " + records.size() + " records for PDF");

        Document document = new Document();
        PdfWriter.getInstance(document, new FileOutputStream(filePath));
        document.open();

        Font titleFont = new Font(Font.HELVETICA, 16, Font.BOLD);
        Font headingFont = new Font(Font.HELVETICA, 12, Font.BOLD);
        Font normalFont = new Font(Font.HELVETICA, 10, Font.NORMAL);

        // Title
        Paragraph title = new Paragraph("User Data Report", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        document.add(title);
        document.add(new Paragraph(" "));

        // Metadata
        document.add(new Paragraph("User ID: " + userId, headingFont));
        document.add(new Paragraph("Use Case: " + sensorType, headingFont));
        document.add(new Paragraph("Time Range: " + timeRange, headingFont));
        document.add(new Paragraph("Generated: " + LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")), headingFont));
        document.add(new Paragraph(" "));

        if (records.isEmpty()) {
            document.add(new Paragraph("No data found for the selected user and use case.", normalFont));
        } else {
            // Create table
            PdfPTable table = new PdfPTable(HEADERS.length);
            table.setWidthPercentage(100.0F);

            // Add headers
            for (String header : HEADERS) {
                table.addCell(new Paragraph(header, headingFont));
            }

            // Add data rows
            for (SensorRecord record : records) {
                table.addCell(new Paragraph(record.time, normalFont));
                table.addCell(new Paragraph(record.value, normalFont));
                table.addCell(new Paragraph(record.alert_type, normalFont));
                table.addCell(new Paragraph(record.alert_given, normalFont));
                table.addCell(new Paragraph(record.pulses, normalFont));
                table.addCell(new Paragraph(record.intensity, normalFont));
                table.addCell(new Paragraph(record.duration, normalFont));
                table.addCell(new Paragraph(record.interval, normalFont));
                table.addCell(new Paragraph(record.reason, normalFont));
            }

            document.add(table);
            document.add(new Paragraph(" "));
            document.add(new Paragraph("Total records: " + records.size(), normalFont));
        }

        document.close();

        String absolutePath = new File(filePath).getAbsolutePath();
        System.out.println("PDF saved to: " + absolutePath);
        System.out.println("===== generatePdf END =====\n");
        return absolutePath;
    }

    /**
     * Generate daily heart rate report PDF for a specific user and date.
     */
    public static String generateDailyHeartRatePdf(String filePath, int userId, LocalDate date,
                                                    JsonNode response, int sensorId) throws Exception {
        System.out.println("\n===== generateDailyHeartRatePdf START =====");

        validateResponse(response);

        DateTimeFormatter dateOnly = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        DateTimeFormatter timeOnly = DateTimeFormatter.ofPattern("HH:mm:ss");

        Document document = new Document();
        PdfWriter.getInstance(document, new FileOutputStream(filePath));
        document.open();

        Font titleFont = new Font(Font.HELVETICA, 14, Font.BOLD);

        document.add(new Paragraph("Daily Heart Rate Log - User ID: " + userId, titleFont));
        document.add(new Paragraph("Date: " + date.format(dateOnly)));
        document.add(new Paragraph(" "));

        String[] hrHeaders = {"Time", "Heart Rate (BPM)", "Alert Type", "Alert Given", "Pulses", "Intensity", "Duration", "Max Value", "Reason"};
        PdfPTable table = new PdfPTable(hrHeaders.length);
        table.setWidthPercentage(100.0F);

        for (String header : hrHeaders) {
            table.addCell(header);
        }

        JsonNode dataArray = extractDataArray(response);
        int count = 0;

        if (dataArray != null && dataArray.isArray()) {
            for (JsonNode node : dataArray) {
                try {
                    if (node.has("sensorid") && node.get("sensorid").asInt() == sensorId) {
                        if (node.has("time") && node.has("value")) {
                            Instant instant = Instant.parse(node.get("time").asText());
                            LocalDateTime dateTime = instant.atZone(ZoneId.systemDefault()).toLocalDateTime();
                            LocalDate recordDate = dateTime.toLocalDate();

                            if (recordDate.equals(date)) {
                                table.addCell(dateTime.format(timeOnly));
                                table.addCell(node.has("value") ? String.valueOf(node.get("value").asInt()) : "N/A");
                                table.addCell(node.has("alert_type") ? node.get("alert_type").asText() : "N/A");
                                // Compute Alert Given from pulses (Yes if pulses > 0)
                                String pulsesVal = node.has("pulses") ? String.valueOf(node.get("pulses").asInt()) : "N/A";
                                String alertGiven = "No";
                                try {
                                    if (!"N/A".equals(pulsesVal)) {
                                        int pc = Integer.parseInt(pulsesVal);
                                        alertGiven = pc > 0 ? "Yes" : "No";
                                    }
                                } catch (NumberFormatException ignored) {}
                                table.addCell(alertGiven);
                                table.addCell(pulsesVal);
                                table.addCell(node.has("intensity") ? String.valueOf(node.get("intensity").asInt()) : "N/A");
                                table.addCell(node.has("duration") ? String.valueOf(node.get("duration").asInt()) : "N/A");
                                table.addCell(node.has("maxvalue") ? String.valueOf(node.get("maxvalue").asInt()) : "N/A");
                                table.addCell(node.hasNonNull("reason") ? node.get("reason").asText() : "N/A");
                                count++;
                            }
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Error processing node: " + e.getMessage());
                }
            }
        }

        if (count == 0) {
            document.add(new Paragraph("No heart rate data found for this date."));
        } else {
            document.add(table);
            document.add(new Paragraph("Total records: " + count));
        }

        document.close();

        String absolutePath = new File(filePath).getAbsolutePath();
        System.out.println("Daily HR PDF saved to: " + absolutePath);
        System.out.println("===== generateDailyHeartRatePdf END =====\n");
        return absolutePath;
    }

    // ==================== SHARED UTILITIES ====================

    /**
     * Validates the API response and throws appropriate exceptions.
     */
    private static void validateResponse(JsonNode response) throws IllegalArgumentException {
        if (response == null) {
            throw new IllegalArgumentException("Response cannot be null");
        }

        if (response.has("error")) {
            int errorCode = response.get("error").asInt();
            String errorMsg = response.has("message") ? response.get("message").asText() : "Unknown error";
            throw new IllegalArgumentException("API returned error: " + errorCode + " - " + errorMsg);
        }
    }

    /**
     * Extracts sensor records from an API response.
     */
    private static List<SensorRecord> extractRecords(JsonNode response) {
        List<SensorRecord> records = new ArrayList<>();
        JsonNode dataArray = extractDataArray(response);

        if (dataArray != null && dataArray.isArray()) {
            for (JsonNode node : dataArray) {
                try {
                    String time = node.hasNonNull("time") ? node.get("time").asText() : "N/A";
                    String value = node.hasNonNull("value") ? String.valueOf(node.get("value").asDouble()) : "N/A";
                    String alert_type = node.hasNonNull("alert_type") ? node.get("alert_type").asText() : "N/A";
                    String pulses = node.hasNonNull("pulses") ? String.valueOf(node.get("pulses").asInt()) : "N/A";
                    String intensity = node.hasNonNull("intensity") ? String.valueOf(node.get("intensity").asInt()) : "N/A";
                    String duration = node.hasNonNull("duration") ? String.valueOf(node.get("duration").asInt()) : "N/A";
                    String interval = node.hasNonNull("interval") ? String.valueOf(node.get("interval").asInt()) : "N/A";
                    String reason = node.hasNonNull("reason") ? node.get("reason").asText() : "N/A";

                    records.add(new SensorRecord(time, value, alert_type, pulses, intensity, duration, interval, reason));
                } catch (Exception e) {
                    System.err.println("Error extracting record: " + e.getMessage());
                }
            }
        }

        return records;
    }

    /**
     * Extracts the data array from potentially wrapped API responses.
     * Handles responses that are:
     * - Direct arrays
     * - Wrapped in {"data": [...]}
     * - Wrapped in {"payload": [...]}
     */
    private static JsonNode extractDataArray(JsonNode response) {
        if (response == null) {
            return null;
        }

        if (response.has("error")) {
            return null;
        }

        // Check if it's already an array
        if (response.isArray()) {
            return response;
        }

        // Check if wrapped in "data"
        if (response.has("data")) {
            JsonNode dataField = response.get("data");
            if (dataField != null && dataField.isArray()) {
                return dataField;
            }
        }

        // Check if wrapped in "payload"
        if (response.has("payload")) {
            JsonNode payloadField = response.get("payload");
            if (payloadField != null && payloadField.isArray()) {
                return payloadField;
            }
        }

        return null;
    }

    /**
     * Opens a file using the system's default application.
     */
    public static void openFile(String filePath) {
        try {
            File file = new File(filePath);
            if (file.exists()) {
                if (Desktop.isDesktopSupported()) {
                    Desktop desktop = Desktop.getDesktop();
                    if (desktop.isSupported(Desktop.Action.OPEN)) {
                        desktop.open(file);
                    } else {
                        System.out.println("INFO: Desktop OPEN action is not supported on this system");
                    }
                } else {
                    System.out.println("INFO: Desktop is not supported on this system");
                }
            } else {
                System.err.println("ERROR: File not found: " + filePath);
            }
        } catch (Exception e) {
            System.err.println("Could not open file automatically: " + e.getMessage());
        }
    }

    /**
     * Generates a default filename for exports.
     */
    public static String generateDefaultFilename(int userId, String sensorType, String extension) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String cleanSensorType = sensorType.replace(" ", "");
        return "User" + userId + "_" + cleanSensorType + "_" + timestamp + "." + extension;
    }

    /**
     * Gets the default export directory (user's Documents folder).
     */
    public static File getDefaultExportDirectory() {
        String userHome = System.getProperty("user.home");
        File documentsDir = new File(userHome, "Documents");
        if (documentsDir.exists() && documentsDir.isDirectory()) {
            return documentsDir;
        }
        return new File(userHome);
    }
}

