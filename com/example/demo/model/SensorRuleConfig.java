package com.example.demo.model;

public class SensorRuleConfig {
   private Integer id;
   private Integer minvalue;
   private Integer maxvalue;
   private Integer minpulses;
   private Integer maxpulses;
   private Integer minintensity;
   private Integer maxintensity;
   private Integer minduration;
   private Integer maxduration;
   private Integer mininterval;
   private Integer maxinterval;
   private String type;
   private boolean active = true;

   public SensorRuleConfig() {
   }

   public Integer getId() {
      return this.id;
   }

   public void setId(Integer id) {
      this.id = id;
   }

   public Integer getMinvalue() {
      return this.minvalue;
   }

   public void setMinvalue(Integer minvalue) {
      this.minvalue = minvalue;
   }

   public Integer getMaxvalue() {
      return this.maxvalue;
   }

   public void setMaxvalue(Integer maxvalue) {
      this.maxvalue = maxvalue;
   }

   public Integer getMinpulses() {
      return this.minpulses;
   }

   public void setMinpulses(Integer minpulses) {
      this.minpulses = minpulses;
   }

   public Integer getMaxpulses() {
      return this.maxpulses;
   }

   public void setMaxpulses(Integer maxpulses) {
      this.maxpulses = maxpulses;
   }

   public Integer getMinintensity() {
      return this.minintensity;
   }

   public void setMinintensity(Integer minintensity) {
      this.minintensity = minintensity;
   }

   public Integer getMaxintensity() {
      return this.maxintensity;
   }

   public void setMaxintensity(Integer maxintensity) {
      this.maxintensity = maxintensity;
   }

   public Integer getMinduration() {
      return this.minduration;
   }

   public void setMinduration(Integer minduration) {
      this.minduration = minduration;
   }

   public Integer getMaxduration() {
      return this.maxduration;
   }

   public void setMaxduration(Integer maxduration) {
      this.maxduration = maxduration;
   }

   public Integer getMininterval() {
      return this.mininterval;
   }

   public void setMininterval(Integer mininterval) {
      this.mininterval = mininterval;
   }

   public Integer getMaxinterval() {
      return this.maxinterval;
   }

   public void setMaxinterval(Integer maxinterval) {
      this.maxinterval = maxinterval;
   }

   public String getType() {
      return this.type;
   }

   public void setType(String type) {
      this.type = type;
   }

   public boolean isActive() {
      return this.active;
   }

   public void setActive(boolean active) {
      this.active = active;
   }

   @Override
   public String toString() {
      return "SensorRuleConfig[id="
         + this.id
         + ", type="
         + this.type
         + ", minvalue="
         + this.minvalue
         + ", maxvalue="
         + this.maxvalue
         + ", pulses="
         + this.minpulses
         + "-"
         + this.maxpulses
         + ", intensity="
         + this.minintensity
         + "-"
         + this.maxintensity
         + ", duration="
         + this.minduration
         + "-"
         + this.maxduration
         + ", interval="
         + this.mininterval
         + "-"
         + this.maxinterval
         + ", active="
         + this.active
         + "]";
   }
}

