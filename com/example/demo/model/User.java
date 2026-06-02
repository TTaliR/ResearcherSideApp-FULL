package com.example.demo.model;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;

import java.util.ArrayList;
import java.util.List;

public class User {
   private final SimpleIntegerProperty userID;
   private final SimpleStringProperty fName;
   private final SimpleStringProperty lName;
   private final List<UserUseCaseMapping> usecaseMappings;

   public User(int userID, String fName, String lName) {
      this.userID = new SimpleIntegerProperty(userID);
      this.fName = new SimpleStringProperty(fName);
      this.lName = new SimpleStringProperty(lName);
      this.usecaseMappings = new ArrayList<>();
   }

   public User(int userID, String fName, String lName, List<UserUseCaseMapping> usecaseMappings) {
      this.userID = new SimpleIntegerProperty(userID);
      this.fName = new SimpleStringProperty(fName);
      this.lName = new SimpleStringProperty(lName);
      this.usecaseMappings = usecaseMappings == null ? new ArrayList<>() : new ArrayList<>(usecaseMappings);
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
   }

   public UserUseCaseMapping getFirstUsecaseMapping() {
      return usecaseMappings.isEmpty() ? null : usecaseMappings.get(0);
   }

   // Compatibility helper for existing controller code
   public String getUsecaseName() {
      UserUseCaseMapping first = getFirstUsecaseMapping();
      return first == null ? "" : first.getUsecaseName();
   }

   // Compatibility helper for existing controller code
   public int getUsecaseId() {
      UserUseCaseMapping first = getFirstUsecaseMapping();
      return first == null ? 0 : first.getUsecaseId();
   }

   // Compatibility helper for existing controller code
   public int getMappingId() {
      UserUseCaseMapping first = getFirstUsecaseMapping();
      return first == null ? 0 : first.getMappingId();
   }
}
