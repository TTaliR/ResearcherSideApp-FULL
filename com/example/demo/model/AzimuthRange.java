package com.example.demo.model;

public class AzimuthRange {
   private int id;
   private int minAzimuth;
   private int maxAzimuth;
   private int minPulses;
   private int maxPulses;
   private int minIntensity;
   private int maxIntensity;
   private int minDuration;
   private int maxDuration;
   private int minInterval;
   private int maxInterval;
   private boolean active = true;

   public AzimuthRange() {
   }

   public AzimuthRange(
      int minAzimuth,
      int maxAzimuth,
      int minPulses,
      int maxPulses,
      int minIntensity,
      int maxIntensity,
      int minDuration,
      int maxDuration,
      int minInterval,
      int maxInterval
   ) {
      this.minAzimuth = minAzimuth;
      this.maxAzimuth = maxAzimuth;
      this.minPulses = minPulses;
      this.maxPulses = maxPulses;
      this.minIntensity = minIntensity;
      this.maxIntensity = maxIntensity;
      this.minDuration = minDuration;
      this.maxDuration = maxDuration;
      this.minInterval = minInterval;
      this.maxInterval = maxInterval;
   }

   public int getMinAzimuth() {
      return this.minAzimuth;
   }

   public void setMinAzimuth(int minAzimuth) {
      this.minAzimuth = minAzimuth;
   }

   public int getMaxAzimuth() {
      return this.maxAzimuth;
   }

   public void setMaxAzimuth(int maxAzimuth) {
      this.maxAzimuth = maxAzimuth;
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

   public int getId() {
      return this.id;
   }

   public void setId(int id) {
      this.id = id;
   }
}
