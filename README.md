# Researcher Side Dashboard

A JavaFX/Spring Boot desktop application designed for researchers to manage experiments, configure feedback mappings, and monitor user sensor data in real-time.

## 🚀 Features
- **Rule & Mapping Editor:** A visual interface to Create, Read, Update, and Delete (CRUD) haptic feedback rules and view historical user mappings.
- **AI Assistant Chatbot:** Provides contextual information, manages the "Yellow Book" data dictionary, and performs DB CRUD actions using natural language.
- **Data Visualization & Export:** Embedded D3.js web views (`feedbackGraph.html`) for interactive sensor trends, with the ability to export data reports to CSV and PDF formats.
- **User & Schedule Management:** Track active users, assign specific hardware/use cases, and manage active measurement schedules.

## 🛠 Setup
1. **Java:** Requires JDK 17.
2. **Properties:** Configure your DB connection in `src/main/resources/application.properties`.
3. **Build:** Run `./gradlew bootRun` or use the provided IDE run configuration.



## 🔗 Project Ecosystem
- [Smartwatch Haptic App (Wear OS)](https://github.com/TTaliR/SmartWatchHapticFeedBackApp1) - Wear OS application serves as the primary edge node for the system.
- [Android Phone App](https://github.com/TTaliR/AndroidPhoneHapticFeedBackApp) - The client that consumes these endpoints.
- [Haptic Backend](https://github.com/liranBecher/Smartwatch-Haptic-Workflow) - The logic engine that decides when this watch should vibrate.
