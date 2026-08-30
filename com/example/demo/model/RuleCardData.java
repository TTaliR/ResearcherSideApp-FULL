package com.example.demo.model;

import java.math.BigDecimal;

public class RuleCardData {
    public int mappingId;
    public String configKey;
    public String useCaseKey;
    public String useCaseLabel;
    public String rangeLabel;
    public String pulseLabel;
    public String intensityLabel;
    public String durationLabel;
    public String intervalLabel;
    public BigDecimal minValue = BigDecimal.ZERO;
    public BigDecimal maxValue = BigDecimal.ZERO;
    public int minPulses;
    public int maxPulses;
    public int minIntensity;
    public int maxIntensity;
    public int minDuration;
    public int maxDuration;
    public int minInterval;
    public int maxInterval;
    public boolean active = true;

    public boolean hasInvalidIntensity() {
        return minIntensity < 1 || minIntensity > 255
                || maxIntensity < 1 || maxIntensity > 255
                || minIntensity > maxIntensity;
    }
}
