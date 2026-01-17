package com.example.demo.model;

import java.util.List;

public class SunMoonThreshold {
   private List<AzimuthRange> sunAzimuthRanges;
   private List<AzimuthRange> moonAzimuthRanges;

   public List<AzimuthRange> getSunAzimuthRanges() {
      return this.sunAzimuthRanges;
   }

   public void setSunAzimuthRanges(List<AzimuthRange> sunAzimuthRanges) {
      this.sunAzimuthRanges = sunAzimuthRanges;
   }

   public List<AzimuthRange> getMoonAzimuthRanges() {
      return this.moonAzimuthRanges;
   }

   public void setMoonAzimuthRanges(List<AzimuthRange> moonAzimuthRanges) {
      this.moonAzimuthRanges = moonAzimuthRanges;
   }
}
