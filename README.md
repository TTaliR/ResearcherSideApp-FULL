# ResearcherSideApp

A JavaFX desktop dashboard for preparing, running, and reviewing smartwatch haptic-feedback studies. It communicates with n8n webhook workflows for backend and database operations.

## 🚀 Features

- **AI agent:** Use contextual chat to work with mappings, schedules, use cases, and backend actions.
- **Participants and use cases:** Select the active study context, assign use cases, and monitor participant connectivity.
- **Mappings and haptic feedback:** Convert measured value ranges into pulse, intensity, duration, and interval settings. Mappings may be shared or copied for one participant, with assignment history retained.
- **Schedules:** Create and manage recurring checks or feedback behavior.
- **Graphs and exports:** Review recent or historical readings and feedback events, with CSV and PDF export.
- **Yellow Book dictionary:** Defines the parameters and context required by each use case.

## 🔄 Typical Workflow

1. Select the participant and use case.
2. Confirm the participant's monitoring status, active mapping, and schedules.
3. During the session, verify that readings and feedback events arrive as expected.
4. After the session, review or export the recorded data.

## 🛠 Running Locally

1. Install a JDK 17 or newer distribution with JavaFX support (the current IDE configuration uses Liberica Full 25).
2. Open the repository in IntelliJ IDEA and add the bundled root-level JAR files to the module classpath.
3. Start the study database and the matching n8n environment.
4. Set the n8n webhook URL in [`ApiService.java`](com/example/demo/service/ApiService.java); it defaults to `http://localhost:5678/webhook`.
5. Run `com.example.demo.SmartWatchHapticSystemApplication`.

The application checks its n8n connection before loading the dashboard. It does not connect to the study database directly; database credentials belong to the backend environment.

## 📚 Documentation

See the [ResearcherSideApp project documentation](https://app.notion.com/p/ResearcherSideApp-37a6a244173d80af8e1be324e20c3ae8) for the dashboard guide, mappings, data review, configuration, troubleshooting, use-case creation, and tutorial video.

## 🔗 Project Ecosystem

- [Smartwatch Haptic App (Wear OS)](https://github.com/TTaliR/SmartWatchHapticFeedBackApp1) — collects watch data and receives haptic feedback commands.
- [Android Phone App](https://github.com/TTaliR/AndroidPhoneHapticFeedBackApp) — connects the smartwatch to the n8n workflow layer.
- [Haptic Backend](https://github.com/liranBecher/Smartwatch-Haptic-Workflow) — hosts the workflows that evaluate readings and determine feedback.
