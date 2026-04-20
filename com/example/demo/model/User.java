package com.example.demo.model;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;

public class User {
   private final SimpleIntegerProperty userID;
   private final SimpleStringProperty fName;
   private final SimpleStringProperty lName;
   private final SimpleIntegerProperty activeUsecaseId;
   private final SimpleStringProperty usecaseName;

   public User(int userID, String fName, String lName, int activeUsecaseId, String usecaseName) {
      this.userID = new SimpleIntegerProperty(userID);
      this.fName = new SimpleStringProperty(fName);
      this.lName = new SimpleStringProperty(lName);
      this.activeUsecaseId = new SimpleIntegerProperty(activeUsecaseId);
      this.usecaseName = new SimpleStringProperty(usecaseName == null ? "" : usecaseName);
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

   public int getActiveUsecaseId() {
      return this.activeUsecaseId.get();
   }

   public SimpleIntegerProperty activeUsecaseIdProperty() {
      return this.activeUsecaseId;
   }

   public void setActiveUsecaseId(int activeUsecaseId) {
      this.activeUsecaseId.set(activeUsecaseId);
   }

   public String getUsecaseName() {
      return this.usecaseName.get();
   }

   public SimpleStringProperty usecaseNameProperty() {
      return this.usecaseName;
   }

   public void setUsecaseName(String usecaseName) {
      this.usecaseName.set(usecaseName == null ? "" : usecaseName);
   }
}