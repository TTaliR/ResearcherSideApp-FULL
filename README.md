# Researcher Side Dashboard

A JavaFX/Spring Boot desktop application designed for researchers to manage experiments and monitor user data in real-time.

## 🚀 Features
- **Rule Editor:** A visual interface to Create, Read, Update, and Delete (CRUD) haptic rules in the database.
- **AI Assistent chatbot:** Provides information and can perform CRUD actions on the DB.
- **Data Visualization:** Embedded web views (`feedbackGraph.html`) to see live sensor trends.
- **User Management:** Track active users, their baselines, and assigned hardware.

## 🛠 Setup
1. **Java:** Requires JDK 17.
2. **Properties:** Configure your DB connection in `src/main/resources/application.properties`.
3. **Build:** Run `./gradlew bootRun` or use the provided IDE run configuration.

## 🔗 Project Ecosystem
- [Smartwatch Haptic App (Wear OS)](https://github.com/TTaliR/SmartWatchHapticFeedBackApp1) - Wear OS application serves as the primary edge node for the system.
- [Android Phone App](https://github.com/TTaliR/AndroidPhoneHapticFeedBackApp) - The client that consumes these endpoints.
- [Haptic Backend](https://github.com/liranBecher/Smartwatch-Haptic-Workflow) - The logic engine that decides when this watch should vibrate.
