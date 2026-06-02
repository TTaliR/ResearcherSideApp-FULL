package com.example.demo.model;

public class UserUseCaseMapping {
    private final int mappingId;
    private final int feedbackConfigRuleId;
    private final int usecaseId;
    private final String usecaseName;
    private final String usecaseDescription;

    private final int minvalue;
    private final int maxvalue;
    private final int minpulses;
    private final int maxpulses;
    private final int minintensity;
    private final int maxintensity;
    private final int minduration;
    private final int maxduration;
    private final int mininterval;
    private final int maxinterval;

    public UserUseCaseMapping(
            int mappingId,
            int feedbackConfigRuleId,
            int usecaseId,
            String usecaseName,
            String usecaseDescription,
            int minvalue,
            int maxvalue,
            int minpulses,
            int maxpulses,
            int minintensity,
            int maxintensity,
            int minduration,
            int maxduration,
            int mininterval,
            int maxinterval
    ) {
        this.mappingId = mappingId;
        this.feedbackConfigRuleId = feedbackConfigRuleId;
        this.usecaseId = usecaseId;
        this.usecaseName = usecaseName == null ? "" : usecaseName;
        this.usecaseDescription = usecaseDescription == null ? "" : usecaseDescription;
        this.minvalue = minvalue;
        this.maxvalue = maxvalue;
        this.minpulses = minpulses;
        this.maxpulses = maxpulses;
        this.minintensity = minintensity;
        this.maxintensity = maxintensity;
        this.minduration = minduration;
        this.maxduration = maxduration;
        this.mininterval = mininterval;
        this.maxinterval = maxinterval;
    }

    public int getMappingId() {
        return mappingId;
    }

    public int getFeedbackConfigRuleId() {
        return feedbackConfigRuleId;
    }

    public int getUsecaseId() {
        return usecaseId;
    }

    public String getUsecaseName() {
        return usecaseName;
    }

    public String getUsecaseDescription() {
        return usecaseDescription;
    }

    public int getMinvalue() {
        return minvalue;
    }

    public int getMaxvalue() {
        return maxvalue;
    }

    public int getMinpulses() {
        return minpulses;
    }

    public int getMaxpulses() {
        return maxpulses;
    }

    public int getMinintensity() {
        return minintensity;
    }

    public int getMaxintensity() {
        return maxintensity;
    }

    public int getMinduration() {
        return minduration;
    }

    public int getMaxduration() {
        return maxduration;
    }

    public int getMininterval() {
        return mininterval;
    }

    public int getMaxinterval() {
        return maxinterval;
    }

    @Override
    public String toString() {
        return usecaseName == null || usecaseName.isBlank()
                ? "Unassigned"
                : usecaseName + " / Mapping " + mappingId;
    }
}