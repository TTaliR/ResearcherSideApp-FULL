package com.example.demo.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Schedule {
    @JsonProperty("schedule_id")
    private int scheduleId;

    @JsonProperty("user_id")
    private int userId;

    @JsonProperty("measure_type")
    private String measureType = "";

    @JsonProperty("trigger_percentage")
    private double triggerPercentage;

    @JsonProperty("next_check")
    private String nextCheck = "";

    @JsonProperty("interval_days")
    private int intervalDays;

    @JsonProperty("active")
    private boolean active;

    public Schedule() {
    }

    public int getScheduleId() {
        return scheduleId;
    }

    public void setScheduleId(int scheduleId) {
        this.scheduleId = scheduleId;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getMeasureType() {
        return measureType;
    }

    public void setMeasureType(String measureType) {
        this.measureType = measureType == null ? "" : measureType;
    }

    public double getTriggerPercentage() {
        return triggerPercentage;
    }

    public void setTriggerPercentage(double triggerPercentage) {
        this.triggerPercentage = triggerPercentage;
    }

    public String getNextCheck() {
        return nextCheck;
    }

    public void setNextCheck(String nextCheck) {
        this.nextCheck = nextCheck == null ? "" : nextCheck;
    }

    public int getIntervalDays() {
        return intervalDays;
    }

    public void setIntervalDays(int intervalDays) {
        this.intervalDays = intervalDays;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    @Override
    public String toString() {
        return "Schedule[scheduleId=" + scheduleId
            + ", userId=" + userId
            + ", measureType=" + measureType
            + ", triggerPercentage=" + triggerPercentage
            + ", nextCheck=" + nextCheck
            + ", intervalDays=" + intervalDays
            + ", active=" + active
            + "]";
    }
}

