# Technical validation

## 1. Automated / Scriptable Validation (Data Flows & Logic)

Status updated 2026-08-08. The expanded runner now contains 38 selectable software tests. Latest evidence: `validation-20260808T094246617Z` passed 28/29 core tests, `validation-20260808T094220109Z` passed all stress tests, and `validation-20260808T094435581Z` exposed both AI routing-contract failures. Unchecked items are partially automated, currently failing, or still require manual/hardware validation.

### End-to-End Data & Routing Logic
- [x] **Physiological-data flow** — `routing.heartrate_boundaries`, `routing.database_log`
- [x] **Contextual-data flow** — `external.temperature`, `external.pollution`
- [x] **Correct routing validation** — `routing.heartrate_boundaries`, `external.temperature`, `external.pollution`
- [ ] **Translation correctness** — `mapping.linear_interpolation`, `mapping.zero_width`, and `mapping.outside_range` pass exact routed checks; `mapping.reversed_input` is implemented but currently fails because descending input endpoints are not selected by the live workflow
- [x] **Performance & reliability** — `soak.api_delivery`, `stress.concurrent_ingestion`, `interval.concurrent_gate`

### Configuration, Mappings & Schedules
- [ ] **Value-range and boundary behavior** — `routing.heartrate_boundaries`, `mapping.linear_interpolation`, `mapping.zero_width`, `mapping.outside_range`, `reject.sensor_type`, and `reject.missing_devices` pass; `mapping.reversed_input` currently fails
- [x] **Global vs. participant-specific mappings** — `mapping.participant_copy`
- [x] **Shared mapping behavior** — `mapping.shared`, `mapping.change_shared`
- [x] **Mapping assignment and history** — `mapping.assign_command`, `mapping.history`
- [x] **Schedule configuration lifecycle** — `schedule.lifecycle`
- [x] **End monitoring behavior** — `monitoring.lifecycle`
- [x] **Vibration interval behavior** — `interval.configuration`, `interval.immediate_limit`, `interval.expiry`, `interval.user_isolation`, `interval.concurrent_gate`
- [ ] **Database consistency** — partial: `routing.database_log` and `dashboard.data_consistency` verify API-visible records and active mappings; direct PostgreSQL/cache comparison is not automated

### Assistant Safety & Input Validation
- [x] **Safe handling of ambiguous, incomplete, or unsupported requests** — `reject.mapping_action`, `reject.sensor_type`, `reject.missing_devices`, `ai.incomplete_input`, `ai.empty_message`, `ai.invalid_usecase`
- [ ] **AI Assistant routing contract** — `ai.participant_analysis` currently returns an empty HTTP 200 response; `ai.usecase_analysis` currently returns `clarify` instead of `knowledge` routed to `expert_panel`

## 2. Manual & Hardware Validation (Physical Watch Behavior & Operations)

### Physical Watch & Hardware Behaviour
- [ ] **Physical watch vibration output** (physically verifying that the watch vibrates in response to data triggers)
- [ ] **Targeted watch verification** (physically verifying that only the correct watch vibrates and not others)
- [ ] **Simultaneous two-watch physical isolation** (verifying isolation when multiple physical devices are connected)

### Operations, Deployment & Resiliency
- [ ] **Clean installation and deployment** (verifying deployment from scratch using only documentation)
- [ ] **Independent reproduction by another team member** (assessing installation guidelines and documentation clarity)
- [ ] **Recovery from connection, application, and backend failures** (manually simulating failures like database drops, network cuts, or service crashes and verifying recovery)
- [ ] **Data integrity after interruption** (manually checking for data loss/corruption after sudden power or network cuts)
- [ ] **Backup, restoration, and version traceability processes** (testing database backup dump and restore procedures)

### New Use Case Addition
- [ ] **Use-case and input definition** (defining new sensor inputs and configuration schemas)
- [ ] **Dictionary and mapping configuration** (adding mapping rules and testing them manually)
- [ ] **Backend workflow extension** (manually modifying/extending the Vibration Orchestrator workflow)
- [ ] **End-to-end execution of a new case**
- [ ] **Regression testing of existing functionality**
- [ ] **Extension effort and required developer expertise evaluation**

### AI Assistant Semantic Accuracy & Action Routing
- [ ] **Correct interpretation of researcher requests** (checking if the AI correctly reasons and responds in natural language)
- [ ] **Accurate use of selected context** (manually checking if the AI utilizes the correct participant, usecase, and mapping details in its chat context)
- [ ] **Correct explanation of mappings, schedules, and dictionary entries**
- [ ] **Correct routing of actions via chat** (manually asking the AI to add, edit, or remove mappings/schedules and checking if the changes are successfully applied to the database)

Use this Notion page as reference and create a script that runs all test curls, and outputs a CSV file with the test results [https://app.notion.com/p/n8n-Webhook-Tests-Breakdown-3a66a244173d801aa181d61e128330fb](https://app.notion.com/p/n8n-Webhook-Tests-Breakdown-3a66a244173d801aa181d61e128330fb)

# User validation

## Before the workshop (or during?)

* Install the system using only the documentation.  
* Record installation time, failed steps, missing information, and help required.  
* Complete one standard test confirming that the dashboard, phone, watch, backend, and database are connected.

## During the workshop

### Configure an existing study

* Create two test participants and assign their devices.  
* Select an existing physiological or contextual use case.  
* Assign both participants a shared haptic mapping.  
* Create and activate a schedule.

### Personalize the study

* Modify the mapping for only one participant.  
* Confirm that the other participant remains on the original mapping.  
* Check the mapping-assignment history.

### Run and inspect the study

* Send or generate test data.  
* Confirm that the correct watch vibrates.  
* Locate the corresponding sensor reading and feedback event in the dashboard.  
* Confirm that the correct participant, use case, and mapping were recorded.

### Use the AI assistant

* Ask it to explain the active mapping.  
* Ask it to list or modify a schedule or mapping.  
* Confirm that its answer and any resulting action match the actual system state.

### Propose a new use case that will be implemented later?

* Add its dictionary entry and haptic mapping.  
* Extend the Vibration Orchestrator using the provided template.  
* Run the new use case end to end.  
* Confirm that the existing use cases still work.
