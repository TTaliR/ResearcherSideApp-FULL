package com.example.demo.util;

import com.example.demo.model.SensorRuleConfig;

import java.math.BigDecimal;

public final class HapticPreviewCalculator {
    private HapticPreviewCalculator() {}

    public static Result calculate(BigDecimal input, SensorRuleConfig rule, boolean heartRate) {
        if (input == null || rule == null || rule.getMinvalue() == null || rule.getMaxvalue() == null) {
            throw new IllegalArgumentException("Complete the mapping fields to see a preview.");
        }
        if (input.compareTo(rule.getMinvalue()) < 0 || input.compareTo(rule.getMaxvalue()) > 0) {
            throw new IllegalArgumentException("Preview input must be inside the mapping value range.");
        }
        requirePositive(rule.getMinpulses(), rule.getMaxpulses(), "Pulse count");
        requireNonNegative(rule.getMinduration(), rule.getMaxduration(), "Duration");
        requireNonNegative(rule.getMininterval(), rule.getMaxinterval(), "Interval");

        int intensity = interpolate(input, rule.getMinvalue(), rule.getMaxvalue(),
                rule.getMinintensity(), rule.getMaxintensity());
        int duration = interpolate(input, rule.getMinvalue(), rule.getMaxvalue(),
                rule.getMinduration(), rule.getMaxduration());
        int pulses = heartRate ? 10 : interpolate(input, rule.getMinvalue(), rule.getMaxvalue(),
                rule.getMinpulses(), rule.getMaxpulses());
        int interval;
        if (heartRate) {
            if (input.signum() <= 0) {
                throw new IllegalArgumentException("Heart rate preview input must be greater than 0.");
            }
            long derivedInterval = Math.round(60000.0 / input.doubleValue() - duration);
            interval = (int) Math.min(Integer.MAX_VALUE, Math.max(0, derivedInterval));
        } else {
            interval = interpolate(input, rule.getMinvalue(), rule.getMaxvalue(),
                    rule.getMininterval(), rule.getMaxinterval());
        }

        long totalDurationMs = (long) pulses * duration + (long) Math.max(0, pulses - 1) * interval;
        return new Result(pulses, intensity, duration, interval, totalDurationMs, heartRate);
    }

    private static int interpolate(BigDecimal input, BigDecimal minInput, BigDecimal maxInput,
                                   int minOutput, int maxOutput) {
        if (minInput.compareTo(maxInput) == 0) {
            return minOutput;
        }
        double ratio = input.subtract(minInput).doubleValue() / maxInput.subtract(minInput).doubleValue();
        return (int) Math.round(minOutput + ratio * (maxOutput - minOutput));
    }

    private static void requirePositive(Integer min, Integer max, String name) {
        if (min == null || max == null || min < 1 || max < 1) {
            throw new IllegalArgumentException(name + " values must be at least 1.");
        }
    }

    private static void requireNonNegative(Integer min, Integer max, String name) {
        if (min == null || max == null || min < 0 || max < 0) {
            throw new IllegalArgumentException(name + " values cannot be negative.");
        }
    }

    public record Result(int pulses, int intensity, int duration, int interval,
                         long totalDurationMs, boolean heartRate) {}
}
