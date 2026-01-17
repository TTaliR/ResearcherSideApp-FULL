package com.example.demo.model;

import java.util.List;

public class HeartRateRange {
   private List<HeartRateRange.HeartRateThresholdMapping> thresholds;

   public List<HeartRateRange.HeartRateThresholdMapping> getThresholds() {
      return this.thresholds;
   }

   public void setThresholds(List<HeartRateRange.HeartRateThresholdMapping> thresholds) {
      this.thresholds = thresholds;
   }

   public static class HeartRateThresholdMapping {
      private int id;
      private int min;
      private int max;
      private int minPulses;
      private int maxPulses;
      private int minIntensity;
      private int maxIntensity;
      private int minDuration;
      private int maxDuration;
      private int minInterval;
      private int maxInterval;
      private boolean active = true;

      public HeartRateThresholdMapping() {
      }

      public HeartRateThresholdMapping(
         int min, int max, int minIntensity, int maxIntensity, int minPulses, int maxPulses, int minDuration, int maxDuration, int minInterval, int maxInterval
      ) {
         this.min = min;
         this.max = max;
         this.minIntensity = minIntensity;
         this.maxIntensity = maxIntensity;
         this.minPulses = minPulses;
         this.maxPulses = maxPulses;
         this.minDuration = minDuration;
         this.maxDuration = maxDuration;
         this.minInterval = minInterval;
         this.maxInterval = maxInterval;
      }

      public int getId() {
         return this.id;
      }

      public void setId(int id) {
         this.id = id;
      }

      public int getMin() {
         return this.min;
      }

      public void setMin(int min) {
         this.min = min;
      }

      public int getMax() {
         return this.max;
      }

      public void setMax(int max) {
         this.max = max;
      }

      public int getMinPulses() {
         return this.minPulses;
      }

      public void setMinPulses(int minPulses) {
         this.minPulses = minPulses;
      }

      public int getMaxPulses() {
         return this.maxPulses;
      }

      public void setMaxPulses(int maxPulses) {
         this.maxPulses = maxPulses;
      }

      public int getMinIntensity() {
         return this.minIntensity;
      }

      public void setMinIntensity(int minIntensity) {
         this.minIntensity = minIntensity;
      }

      public int getMaxIntensity() {
         return this.maxIntensity;
      }

      public void setMaxIntensity(int maxIntensity) {
         this.maxIntensity = maxIntensity;
      }

      public int getMinDuration() {
         return this.minDuration;
      }

      public void setMinDuration(int minDuration) {
         this.minDuration = minDuration;
      }

      public int getMaxDuration() {
         return this.maxDuration;
      }

      public void setMaxDuration(int maxDuration) {
         this.maxDuration = maxDuration;
      }

      public int getMinInterval() {
         return this.minInterval;
      }

      public void setMinInterval(int minInterval) {
         this.minInterval = minInterval;
      }

      public int getMaxInterval() {
         return this.maxInterval;
      }

      public void setMaxInterval(int maxInterval) {
         this.maxInterval = maxInterval;
      }

      public boolean isActive() {
         return this.active;
      }

      public void setActive(boolean active) {
         this.active = active;
      }

      @Override
      public String toString() {
         return "HeartRateThresholdMapping [id="
            + this.id
            + ", minHR="
            + this.min
            + ", maxHR="
            + this.max
            + ", pulses="
            + this.minPulses
            + "-"
            + this.maxPulses
            + ", intensity="
            + this.minIntensity
            + "-"
            + this.maxIntensity
            + ", duration="
            + this.minDuration
            + "-"
            + this.maxDuration
            + ", interval="
            + this.minInterval
            + "-"
            + this.maxInterval
            + ", active="
            + this.active
            + "]";
      }
   }
}
