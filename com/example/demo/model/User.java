package com.example.demo.model;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;

import java.util.ArrayList;
import java.util.List;

public class User {
   private final SimpleIntegerProperty userID;
   private final SimpleStringProperty fName;
   private final SimpleStringProperty lName;

   /*
    * UI-level current use case/mapping.
    * These are NOT database columns.
    * They are derived from usecaseMappings or from the user's current UI selection.
    */
   private final SimpleIntegerProperty currentUsecaseId;
   private final SimpleStringProperty currentUsecaseName;
   private final SimpleIntegerProperty currentMappingId;

   private final List<UserUseCaseMapping> usecaseMappings;

   public User(int userID, String fName, String lName) {
      this(userID, fName, lName, new ArrayList<>());
   }

   public User(int userID, String fName, String lName, List<UserUseCaseMapping> usecaseMappings) {
      this.userID = new SimpleIntegerProperty(userID);
      this.fName = new SimpleStringProperty(fName);
      this.lName = new SimpleStringProperty(lName);
      this.usecaseMappings = usecaseMappings == null ? new ArrayList<>() : new ArrayList<>(usecaseMappings);

      UserUseCaseMapping first = getFirstUsecaseMapping();

      this.currentUsecaseId = new SimpleIntegerProperty(first == null ? 0 : first.getUsecaseId());
      this.currentUsecaseName = new SimpleStringProperty(first == null ? "" : first.getUsecaseName());
      this.currentMappingId = new SimpleIntegerProperty(first == null ? 0 : first.getEffectiveMappingId());
   }

   public User(int userID, String fName, String lName, String usecaseName) {
      this(userID, fName, lName, 0, usecaseName);
   }

   public int getUserID() {
      return this.userID.get();
   }

   public SimpleIntegerProperty userIDProperty() {
      return this.userID;
   }

   public String getFName() {
      return this.fName.get();
   }

   public SimpleStringProperty fNameProperty() {
      return this.fName;
   }

   public String getLName() {
      return this.lName.get();
   }

   public SimpleStringProperty lNameProperty() {
      return this.lName;
   }

   public List<UserUseCaseMapping> getUsecaseMappings() {
      return usecaseMappings;
   }

   public void setUsecaseMappings(List<UserUseCaseMapping> mappings) {
      this.usecaseMappings.clear();

      if (mappings != null) {
         this.usecaseMappings.addAll(mappings);
      }

      UserUseCaseMapping first = getFirstUsecaseMapping();
      if (first != null) {
         setCurrentUsecase(first);
      } else {
         setCurrentUsecaseId(0);
         setUsecaseName("");
         setCurrentMappingId(0);
      }
   }

   public UserUseCaseMapping getFirstUsecaseMapping() {
      return usecaseMappings.isEmpty() ? null : usecaseMappings.get(0);
   }

   public UserUseCaseMapping findMappingForUsecase(int usecaseId) {
      for (UserUseCaseMapping mapping : usecaseMappings) {
         if (mapping.getUsecaseId() == usecaseId) {
            return mapping;
         }
      }
      return null;
   }

   public UserUseCaseMapping findMappingForUsecaseName(String usecaseName) {
      if (usecaseName == null || usecaseName.isBlank()) {
         return null;
      }

      for (UserUseCaseMapping mapping : usecaseMappings) {
         if (usecaseName.equalsIgnoreCase(mapping.getUsecaseName())) {
            return mapping;
         }
      }

      return null;
   }

   public void setCurrentUsecase(UserUseCaseMapping mapping) {
      if (mapping == null) {
         setCurrentUsecaseId(0);
         setUsecaseName("");
         setCurrentMappingId(0);
         return;
      }

      setCurrentUsecaseId(mapping.getUsecaseId());
      setUsecaseName(mapping.getUsecaseName());
      setCurrentMappingId(mapping.getEffectiveMappingId());
   }

   public int getCurrentUsecaseId() {
      return currentUsecaseId.get();
   }

   public void setCurrentUsecaseId(int usecaseId) {
      this.currentUsecaseId.set(usecaseId);
   }

   public SimpleIntegerProperty currentUsecaseIdProperty() {
      return currentUsecaseId;
   }

   /*
    * Compatibility for existing DashboardController code.
    * This is UI state only, not DB active_usecase_id.
    */
   public String getUsecaseName() {
      return currentUsecaseName.get();
   }

   /*
    * Compatibility for existing DashboardController code.
    * This is UI state only, not DB active_usecase_id.
    */
   public void setUsecaseName(String usecaseName) {
      this.currentUsecaseName.set(usecaseName == null ? "" : usecaseName);
   }

   public SimpleStringProperty currentUsecaseNameProperty() {
      return currentUsecaseName;
   }

   public int getUsecaseId() {
      return getCurrentUsecaseId();
   }

   public int getCurrentMappingId() {
      return currentMappingId.get();
   }

   public void setCurrentMappingId(int mappingId) {
      this.currentMappingId.set(mappingId);
   }

   public int getMappingId() {
      return getCurrentMappingId();
   }

   public SimpleIntegerProperty currentMappingIdProperty() {
      return currentMappingId;
   }

   @Override
   public String toString() {
      return getUserID() + " - " + getFName() + " " + getLName();
   }
}
