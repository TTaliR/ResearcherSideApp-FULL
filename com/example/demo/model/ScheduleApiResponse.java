package com.example.demo.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

public class ScheduleApiResponse {
    private boolean success;
    private List<Schedule> schedules = new ArrayList<>();
    private String reply = "";
    private String errorMessage = "";
    private String rawJsonResponse = "";
    private JsonNode rawResponse;

    public ScheduleApiResponse() {
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public List<Schedule> getSchedules() {
        return schedules;
    }

    public void setSchedules(List<Schedule> schedules) {
        this.schedules = schedules == null ? new ArrayList<>() : schedules;
    }

    public String getReply() {
        return reply;
    }

    public void setReply(String reply) {
        this.reply = reply == null ? "" : reply;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage == null ? "" : errorMessage;
    }

    public String getRawJsonResponse() {
        return rawJsonResponse;
    }

    public void setRawJsonResponse(String rawJsonResponse) {
        this.rawJsonResponse = rawJsonResponse == null ? "" : rawJsonResponse;
    }

    public JsonNode getRawResponse() {
        return rawResponse;
    }

    public void setRawResponse(JsonNode rawResponse) {
        this.rawResponse = rawResponse;
    }

    @Override
    public String toString() {
        return "ScheduleApiResponse[success=" + success
            + ", schedules=" + schedules
            + ", reply=" + reply
            + ", errorMessage=" + errorMessage
            + "]";
    }
}

