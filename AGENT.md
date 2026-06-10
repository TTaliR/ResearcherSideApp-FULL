# Project AGENT.md: Architecture and Design Overview

## 1. Project Purpose

This project is a JavaFX-based desktop application for the "Researcher Side" of a sensor data monitoring system. Its primary purpose is to provide researchers with a user interface to:

- View and monitor real-time and historical sensor data from users.
- Configure and manage feedback rules (mappings) that determine how and when users receive haptic feedback based on sensor values.
- Interact with a backend service to manage users, use cases, and system configurations.
- Export sensor data for external analysis.

The application is designed to be robust, maintainable, and responsive, using modern Java practices and asynchronous communication.

## 2. Architectural Pattern: Model-View-Controller (MVC)

The application is structured following the Model-View-Controller (MVC) design pattern to separate concerns, improve code organization, and facilitate scalability.

*   **Model**: Represents the application's data and business logic. These are typically Plain Old Java Objects (POJOs) that hold state.
*   **View**: The user interface (UI) of the application. In this project, the View is primarily defined by FXML files and is supported by UI factories and HTML/JavaScript templates for complex visualizations.
*   **Controller**: Acts as the intermediary between the Model and the View. It handles user input, updates the Model, and refreshes the View. The `service` and `parser` packages form the core of the controller logic.

---

## 3. Project Structure and Key Packages

Below is a breakdown of the key packages and how they fit into the MVC pattern.

### `com.example.demo.model`

**(M) Model**

This package contains the data objects that represent the core entities of the application.

-   `RuleCardData`: Represents a single feedback rule/mapping with all its parameters (value ranges, pulse settings, etc.).
-   `DictionaryParameterData`: Represents a single entry in the "Yellow Book" data dictionary.
-   `Schedule`: Represents a scheduled task or measurement.
-   `SensorRuleConfig`: A data object used for sending rule configurations to the API.

### `com.example.demo.view`

**(V) View**

This package contains the FXML files that define the structure and layout of the UI, along with their corresponding JavaFX controller classes that handle direct user interactions (e.g., button clicks).

Additionally, the `com/example/demo/view/templates` directory contains HTML files used within `WebView` components.

-   `feedbackGraph.html`: A sophisticated D3.js-based template for rendering interactive, time-series graphs of sensor data. This demonstrates a hybrid UI approach where complex visualizations are delegated to web technologies.

### `com.example.demo.factory`

**(V) View Helper / Factory**

This package follows the Factory design pattern to programmatically create reusable and complex UI components. This approach keeps the main View controllers clean and decouples the component creation logic from the application flow.

-   `SharedUiFactory.java`: Creates common, shared UI controls like icon buttons (`Delete`, `Copy`, `Settings`) and loading overlays. This promotes UI consistency.
-   `MappingUiFactory.java`: Constructs UI elements specific to feedback rules, such as the "mapping cards" that display rule details.
-   `YellowBookUiFactory.java`: Builds the UI cards and rows for the "Yellow Book" data dictionary feature.
-   `ChatBubbleFactory.java`: Generates `WebView`-based chat bubbles for displaying user and AI messages, handling the conversion of Markdown to styled HTML.

### `com.example.demo.service`

**(C) Controller / Service Layer**

This is a core part of the Controller layer, responsible for application logic, business operations, and communication with external systems.

-   `ApiService.java`: The heart of the network communication layer. It is a singleton responsible for all HTTP requests to the backend API.
    -   It uses `CompletableFuture` to perform all network calls asynchronously, ensuring the UI remains responsive.
    -   It defines all API endpoints and handles the serialization of Java objects to JSON for POST requests.
    -   It centralizes error handling for network operations.
-   `ExportService.java`: A dedicated service for handling data exports.
    -   It provides methods to generate CSV and PDF files from `JsonNode` data returned by the `ApiService`.
    -   It encapsulates the logic for data formatting, file I/O, and proper escaping of values (e.g., for CSV).

### `com.example.demo.parser`

**(C) Controller / Data Transformation**

This package is responsible for parsing and transforming raw data (usually from the `ApiService`) into the application's **Model** objects. This is a critical controller function that decouples the raw data format from the application's internal data representation.

-   `MappingConfigParser.java`: Parses `JsonNode` objects from the API into a `List<RuleCardData>`. It handles various JSON structures and field names to provide a consistent data model.
-   `YellowBookParser.java`: Parses both structured `JsonNode` and unstructured, natural language (Markdown-like) text from the AI service into a `Map` of `DictionaryParameterData` objects. This is essential for interpreting the AI's responses for the data dictionary.

---

## 4. Data Flow Example: Displaying Mapping History

1.  **View**: A user clicks a button in the UI to view the history of mappings for a specific use case.
2.  **Controller (`ApiService`)**: The UI controller calls `ApiService.getUserMappingHistory(userId, useCaseName)`. `ApiService` sends an asynchronous GET request to the `/users-mappings-history` endpoint.
3.  **Backend**: The server processes the request and returns a JSON array of historical mapping data.
4.  **Controller (`ApiService`)**: The `CompletableFuture` completes, and the raw `JsonNode` response is returned.
5.  **View/Controller**: The UI controller receives the `JsonNode`. It then iterates through the data and uses `MappingUiFactory.createHistoryMappingCard()` for each entry.
6.  **Factory (`MappingUiFactory`)**:
    -   The factory method calls `MappingUiFactory.createRuleFromHistoryMapping()` to parse a single `JsonNode` entry into a `RuleCardData` **(Model)** object.
    -   It then uses this model object to construct a `VBox` (a UI component) with styled labels and buttons.
7.  **View**: The generated `VBox` cards are added to the scene graph, and the user sees the formatted mapping history on the screen.