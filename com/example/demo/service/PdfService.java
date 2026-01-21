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
import java.time.LocalDateTime;
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

    private static String getText(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asText() : "-";
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