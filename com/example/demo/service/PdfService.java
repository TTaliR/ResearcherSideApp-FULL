package com.example.demo.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import java.awt.*;
import java.io.File;
import java.io.FileOutputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class PdfService {

    public static void generateSensorReport(int userId, String sensorType, String timeRange, JsonNode data) throws Exception {
        String fileName = "Report_User" + userId + "_" + sensorType + ".pdf";
        Document document = new Document();
        PdfWriter.getInstance(document, new FileOutputStream(fileName));

        document.open();
        Font titleFont = new Font(Font.HELVETICA, 18, Font.BOLD);
        Font headerFont = new Font(Font.HELVETICA, 12, Font.BOLD);

        Paragraph title = new Paragraph("Sensor Data Report", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        document.add(title);
        document.add(new Paragraph(" ")); // Spacer

        document.add(new Paragraph("User ID: " + userId));
        document.add(new Paragraph("Sensor: " + sensorType));
        document.add(new Paragraph("Range: " + timeRange));
        document.add(new Paragraph("Generated: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))));
        document.add(new Paragraph(" "));

        PdfPTable table = new PdfPTable(7);
        table.setWidthPercentage(100);

        String[] headers = {"Time", "Value", "Feedback", "Pulses", "Intensity", "Duration", "Interval"};
        for (String h : headers) {
            table.addCell(new Paragraph(h, headerFont));
        }

        if (data.isArray()) {
            for (JsonNode node : data) {
                table.addCell(getText(node, "time"));
                table.addCell(getText(node, "value"));
                table.addCell(node.has("alert_type") ? "Yes" : "No");
                table.addCell(getText(node, "pulses"));
                table.addCell(getText(node, "intensity"));
                table.addCell(getText(node, "duration"));
                table.addCell(getText(node, "interval"));
            }
        }

        document.add(table);
        if (!data.isArray() || data.size() == 0) {
            document.add(new Paragraph("No data found for this selection."));
        }

        document.close();
        openFile(fileName);
    }

    /**
     * Generate a sensor data report PDF for a user
     * @param userId the user ID
     * @param sensorType the sensor name (e.g., "HeartRate")
     * @param timeRange the time range filter
     * @param response the API response (may be wrapped or array)
     * @return file path of generated PDF
     * @throws Exception if PDF generation fails
     */
    public static String generateSensorDataReport(int userId, String sensorType, String timeRange, JsonNode response) throws Exception {
        System.out.println("\n===== generateSensorDataReport START =====");
        System.out.println("Parameters: userId=" + userId + ", sensorType=" + sensorType + ", timeRange=" + timeRange);

        // Validate inputs
        if (response == null) {
            throw new IllegalArgumentException("Response cannot be null");
        }

        System.out.println("Response received: " + (response == null ? "null" : response.getNodeType().name()));

        // Check for API error response
        if (response.has("error")) {
            int errorCode = response.has("error") ? response.get("error").asInt() : 0;
            String errorMsg = response.has("message") ? response.get("message").asText() : "Unknown error";
            throw new IllegalArgumentException("API returned error: " + errorCode + " - " + errorMsg);
        }

        try {
            String fileName = "User_" + userId + sensorType.replace(" ", "") + "_log.pdf";
            Document document = new Document();
            PdfWriter.getInstance(document, new FileOutputStream(fileName));
            document.open();

            Font titleFont = new Font(1, 16.0F, 1);
            Font headingFont = new Font(1, 12.0F, 1);
            Paragraph title = new Paragraph("User Data Report", titleFont);
            title.setAlignment(1);
            document.add(title);
            document.add(new Paragraph(" "));
            document.add(new Paragraph("User ID: " + userId, headingFont));
            document.add(new Paragraph("Use Case: " + sensorType, headingFont));
            document.add(new Paragraph("Time Range: " + timeRange, headingFont));
            document.add(new Paragraph("Generated: " + LocalDateTime.now(), headingFont));
            document.add(new Paragraph(" "));

            PdfPTable table = new PdfPTable(7);
            table.setWidthPercentage(100.0F);
            String[] headers = {"Time", "Value", "Feedback", "Pulses", "Intensity", "Duration (ms)", "Interval (ms)"};
            for (String header : headers) {
                table.addCell(new Paragraph(header, headingFont));
            }

            // Extract array from potentially wrapped response
            System.out.println("Calling extractDataArray()...");
            JsonNode dataArray = extractDataArray(response);
            System.out.println("After extraction: dataArray=" + (dataArray == null ? "null" : ("array, size=" + dataArray.size())));

            int rowCount = 0;
            if (dataArray != null && dataArray.isArray()) {
                System.out.println("Processing array with " + dataArray.size() + " elements");
                for (JsonNode node : dataArray) {
                    try {
                        String time = node.hasNonNull("time") ? node.get("time").asText() : "N/A";
                        double value = node.hasNonNull("value") ? node.get("value").asDouble() : 0.0;
                        String alertType = node.hasNonNull("alert_type") ? node.get("alert_type").asText() : null;
                        table.addCell(time);
                        table.addCell(String.valueOf(value));
                        table.addCell(alertType != null ? "Yes" : "No");
                        table.addCell(node.hasNonNull("pulses") ? String.valueOf(node.get("pulses").asInt()) : "N/A");
                        table.addCell(node.hasNonNull("intensity") ? String.valueOf(node.get("intensity").asInt()) : "N/A");
                        table.addCell(node.hasNonNull("duration") ? String.valueOf(node.get("duration").asInt()) : "N/A");
                        table.addCell(node.hasNonNull("interval") ? String.valueOf(node.get("interval").asInt()) : "N/A");
                        rowCount++;
                    } catch (Exception e) {
                        System.err.println("Error processing row: " + e.getMessage());
                    }
                }
            } else {
                System.out.println("Data array is null or not an array!");
            }

            System.out.println("Final rowCount: " + rowCount);

            if (rowCount == 0) {
                System.out.println("Adding 'No data found' message to PDF");
                document.add(new Paragraph("No data found for the selected user and use case."));
            } else {
                System.out.println("Adding table with " + rowCount + " rows to PDF");
                document.add(table);
            }

            document.close();
            String absolutePath = new File(fileName).getAbsolutePath();
            System.out.println("PDF saved to: " + absolutePath);
            System.out.println("===== generateSensorDataReport END =====\n");
            return absolutePath;
        } catch (Exception e) {
            System.err.println("ERROR: Failed to generate PDF - " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    /**
     * Generate daily heart rate report for a specific user
     * @param userId the user ID
     * @param date the date to filter for
     * @param data the sensor data from API
     * @param sensorId the heart rate sensor ID
     * @return file path of generated PDF
     * @throws Exception if PDF generation fails
     */
    public static String generateDailyHeartRateReport(int userId, LocalDate date, JsonNode data, int sensorId) throws Exception {
        DateTimeFormatter dateOnly = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        String fileName = "HeartRateLog_User" + userId + "_" + date.format(dateOnly) + ".pdf";
        Document document = new Document();
        PdfWriter writer = PdfWriter.getInstance(document, new FileOutputStream(fileName));
        document.open();

        Font titleFont = new Font(1, 14.0F, 1);
        document.add(new Paragraph("Daily Heart Rate Log - User ID: " + userId, titleFont));
        document.add(new Paragraph("Date: " + date.format(dateOnly)));
        document.add(new Paragraph(" "));

        PdfPTable table = new PdfPTable(7);
        table.setWidthPercentage(100.0F);
        table.addCell("Time");
        table.addCell("Heart Rate (BPM)");
        table.addCell("Alert Type");
        table.addCell("Pulses");
        table.addCell("Intensity");
        table.addCell("Duration");
        table.addCell("Max Value");

        int count = 0;
        // Extract array from potentially wrapped response
        JsonNode dataArray = extractDataArray(data);
        if (dataArray != null && dataArray.isArray()) {
            for (JsonNode node : dataArray) {
                try {
                    if (node.has("sensorid") && node.get("sensorid").asInt() == sensorId) {
                        if (node.has("time") && node.has("value")) {
                            Instant instant = Instant.parse(node.get("time").asText());
                            LocalDateTime dateTime = instant.atZone(ZoneId.systemDefault()).toLocalDateTime();
                            LocalDate recordDate = dateTime.toLocalDate();

                            if (recordDate.equals(date)) {
                                table.addCell(dateTime.format(DateTimeFormatter.ofPattern("HH:mm:ss")));
                                table.addCell(node.has("value") ? String.valueOf(node.get("value").asInt()) : "N/A");
                                table.addCell(node.has("alert_type") ? node.get("alert_type").asText() : "N/A");
                                table.addCell(node.has("pulses") ? String.valueOf(node.get("pulses").asInt()) : "N/A");
                                table.addCell(node.has("intensity") ? String.valueOf(node.get("intensity").asInt()) : "N/A");
                                table.addCell(node.has("duration") ? String.valueOf(node.get("duration").asInt()) : "N/A");
                                table.addCell(node.has("maxvalue") ? String.valueOf(node.get("maxvalue").asInt()) : "N/A");
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
            document.add(new Paragraph("No heart rate data found for today."));
        } else {
            document.add(table);
            document.add(new Paragraph("Total records: " + count));
        }

        document.close();
        writer.close();
        return new File(fileName).getAbsolutePath();
    }

    private static String getText(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asText() : "-";
    }

    /**
     * Extract data array from response that may be wrapped in "data", "payload", or be a raw array
     */
    private static JsonNode extractDataArray(JsonNode response) {
        System.out.println("===== extractDataArray DEBUG =====");
        System.out.println("Input response: " + (response == null ? "null" : response.getNodeType().name()));

        if (response == null) {
            System.out.println("Response is null, returning null");
            return null;
        }

        System.out.println("Response toString (first 300 chars): " + response.toString().substring(0, Math.min(300, response.toString().length())));

        // Check if it's already an array
        if (response.isArray()) {
            System.out.println("Response is already an array, size: " + response.size());
            return response;
        }

        // Check if wrapped in "data"
        if (response.has("data")) {
            JsonNode dataField = response.get("data");
            System.out.println("Found 'data' field: " + (dataField == null ? "null" : dataField.getNodeType().name()));
            if (dataField != null && dataField.isArray()) {
                System.out.println("'data' field is an array, size: " + dataField.size());
                return dataField;
            }
        }

        // Check if wrapped in "payload"
        if (response.has("payload")) {
            JsonNode payloadField = response.get("payload");
            System.out.println("Found 'payload' field: " + (payloadField == null ? "null" : payloadField.getNodeType().name()));
            if (payloadField != null && payloadField.isArray()) {
                System.out.println("'payload' field is an array, size: " + payloadField.size());
                return payloadField;
            }
        }

        System.out.println("Could not extract array from response. Available fields: " + response.fieldNames().toString());
        // Return null if we can't extract
        return null;
    }

    private static void openFile(String filePath) {
        try {
            File file = new File(filePath);
            if (Desktop.isDesktopSupported() && file.exists()) {
                Desktop.getDesktop().open(file);
            }
        } catch (Exception e) {
            System.err.println("Could not open PDF automatically: " + e.getMessage());
        }
    }
}
