package com.example.demo.model;

public class UseCase {
    private int id;
    private String rawName;
    private String normalizedName;
    private String description;
    private String loggingInterval;

    public UseCase(int id, String rawName, String normalizedName, String description, String loggingInterval) {
        this.id = id;
        this.rawName = rawName;
        this.normalizedName = normalizedName;
        this.description = description;
        this.loggingInterval = loggingInterval;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getRawName() {
        return rawName;
    }

    public void setRawName(String rawName) {
        this.rawName = rawName;
    }

    public String getNormalizedName() {
        return normalizedName;
    }

    public void setNormalizedName(String normalizedName) {
        this.normalizedName = normalizedName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getLoggingInterval() {
        return loggingInterval;
    }

    public void setLoggingInterval(String loggingInterval) {
        this.loggingInterval = loggingInterval;
    }
}