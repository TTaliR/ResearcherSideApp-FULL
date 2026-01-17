package com.example.demo.model;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;

public class User {
   private final SimpleIntegerProperty userID;
   private final SimpleStringProperty fName;
   private final SimpleStringProperty lName;

   public User(int userID, String fName, String lName) {
      this.userID = new SimpleIntegerProperty(userID);
      this.fName = new SimpleStringProperty(fName);
      this.lName = new SimpleStringProperty(lName);
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
}
