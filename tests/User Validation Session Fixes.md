# User Validation Session Fixes

This document records fixes, clarifications, and scope decisions that came from the researcher user-validation review on 2026-08-27 through 2026-08-30.

Participant tags identify who raised or demonstrated each finding: `[P1]`, `[P2]`, and `[P3]`. `[Technical follow-up]` marks an issue discovered while tracing or implementing a participant-reported problem rather than something a participant directly mentioned.

## Mapping values and watch intensity

### Validation findings

- [P3] Sensor mapping endpoints could not accept negative values or decimals in the researcher UI.
- [P1] Vibration intensity could exceed the Android/watch amplitude limit, contributing to uncertainty when a test produced no vibration.
- [P3] Negative ranges such as `-10--5` were difficult to read.
- [Technical follow-up] Mapping history and exports could truncate decimal sensor values.
- [Technical follow-up] Existing mappings with invalid intensity values were difficult to identify.

### Changes

- Sensor range endpoints now use `BigDecimal` throughout the UI models, parsing, editing, duplicate detection, API payloads, history, and display paths.
- Mapping endpoint fields accept signed decimal values, including negative values.
- The existing `start <= end` backend rule remains enforced.
- Both intensity endpoints are validated as integers in the inclusive range `1–255` before a mapping is created or updated.
- Mapping cards and history wrap negative values in parentheses, for example `(-10)-(-5)`, while edit fields and backend payloads retain normal numeric values.
- PDF mapping output preserves decimal values instead of converting them to integers.
- Field prompts now communicate signed decimal sensor values and the `1–255` intensity range.
- Legacy mappings with intensity outside `1–255`, or with reversed intensity endpoints, are visually marked invalid. They remain readable but cannot be saved again until corrected.

### Compatibility behavior

- The backend contract and field names are unchanged.
- Existing invalid mappings are not automatically migrated or disabled.
- Attempting to save or update an invalid mapping is blocked until both intensity endpoints are within `1–255` and ordered correctly.

## Mapping creation and editing workflow

### Validation findings

- [P2] After saving a mapping, the editor cleared and returned the researcher to the top, making the mapping appear to have disappeared.
- [P1, P2] A new mapping could be difficult to locate among all mappings, especially when the current filters hid it.
- [P1, P3] It was unclear whether the editor contained saved or unsaved values.
- [Technical follow-up] Long use-case descriptions and names could wrap poorly or push mapping-card actions out of view.

### Changes

- Saving or updating a mapping preserves the editor values, selected mapping, and scroll position.
- After creating a mapping, the success dialog offers `Show Mapping` and `Stay Here` actions.
- `Show Mapping` switches to `All Users` and `List All`, selects the new mapping, and scrolls it into view.
- The editor displays a green `All changes saved` indicator or an orange `Unsaved changes` indicator and updates it as fields change.
- Long use-case descriptions and mapping titles wrap, and mapping-card action buttons remain in a separate visible row.

## Haptic pattern preview

### Validation finding

- [P2, P3] Mapping parameters were numeric only, making it difficult to understand what happens between the minimum and maximum values or what a specific intermediate mapping value means.
- [Technical follow-up] The numeric fields did not make the resulting vibration rhythm or overall pattern length easy to inspect while editing.

### Changes

- The mapping editor now includes a collapsible `Haptic Pattern Preview`, collapsed by default to avoid taking permanent space.
- The preview updates automatically while editing both new and saved mappings; it does not require saving first.
- A preview input can be changed with a synchronized slider or text field and defaults to the midpoint of the mapping input range.
- The preview linearly interpolates pulses, intensity, duration, and interval using the same mapping field names shown in the editor.
- Pulse width represents `Duration`, space between pulses represents `Interval`, and pulse height and opacity represent `Intensity`; the UI explains these encodings.
- The calculated `Pulses`, `Intensity`, `Duration`, `Interval`, and human-readable total length are shown below the pattern.
- Total length excludes a trailing interval and is calculated as `pulses × duration + (pulses - 1) × interval`.
- Very large pulse counts render at most 16 representative pulses plus a `+N more` label, preventing horizontal overflow while preserving the exact value in the summary.
- Invalid or incomplete editor values produce an inline preview message instead of an alert.
- Heart-rate mappings mirror the workflow behavior: 10 pulses, interpolated intensity and duration, and an interval derived from BPM.

### Scope decision

This preview is a local researcher-side explanation tool only. Physical mapping testing remains a phone feature; no ResearcherSide listener, polling channel, WebSocket, or direct phone-control path was added.

## Agent chat request states

### Validation findings

- [P3] Researchers could submit overlapping prompts while a response was still in progress, causing response-order confusion.
- [P3] The response delay was noticeable, while the existing typing state was easy to miss.
- [Technical follow-up] Failures lacked sufficiently specific explanations.

### Changes

- Sending is blocked while a request is active.
- A more visible activity row uses a spinner and rotating status text such as `Thinking`, `Typing`, `Checking context`, and `Reviewing details`.
- Timeout, rate-limit, authentication, server, connectivity, and malformed-response failures receive distinct messages.
- A failed prompt is restored and can be submitted again with `Retry`.
- A successful assistant reply remains visible even if the subsequent mapping refresh fails.

## Graph readability and accuracy

### Validation findings

- [P2] Fixed `±10` Y-axis padding was excessive for small ranges such as UV `0–12`.
- [P2] The Y-axis label `Value` did not identify the selected use case and could be mistaken for haptic intensity.
- [Technical follow-up] The context-drawer mini graph connected readings across missing-data periods.
- [Technical follow-up] The grouped-alert legend appeared even when the graph contained no grouped alert marker.
- [P2] `Aggregated alerts` or `Aggregated points` did not clearly explain grouped markers.

### Changes

- The main graph now pads the Y-axis by 20% of the displayed data span.
- Non-negative datasets keep the lower Y-axis bound at or above zero.
- Constant-value datasets use a small fallback span so the chart remains readable.
- The Y-axis label now uses the spaced use-case name, for example `U V Value` or `Heart Rate Value`.
- Use-case display-name formatting is shared with the left sidebar.
- The mini graph now uses the main graph's gap rule: break the line when an interval exceeds both two minutes and five times the median sampling interval.
- The grouped-alert legend entry appears only when at least one rendered marker represents multiple alerts.
- User-facing graph copy now consistently says `Grouped alerts` instead of `Aggregated alerts` or `Aggregated points`.

## Schedule terminology and constraints

### Validation findings

- [P2] `Mode` was mistaken for an application operating mode rather than a statistical measure.
- [P1] The purpose of `Schedules` was unclear, and the feature name did not seem to describe personalization or recalibration.
- [P2] `Trigger` and `Range` did not explain when recalculation happens or how the new mapping bounds are produced.
- [Technical follow-up] Negative or zero schedule ranges have no meaningful behavior.

### Changes

- The workspace remains named `Schedules`.
- The UI displays `Most frequent value` instead of `mode`; the backend value remains exactly `mode`, so the API contract is unchanged.
- Explanatory text states that every `Interval` days the selected `Measure` summarizes the participant's recorded values and recalculates the mapping input boundaries.
- Labels now use `Recalculate every`, `Measure`, and `Range around result (%)`.
- The range explanation includes an example: a measured result of 100 with a 10% range produces boundaries of 90–110.
- Schedule intervals and ranges accept positive whole numbers only. New and edited schedules reject zero and negative values, while invalid legacy values are identified for correction.

## n8n and phone-testing scope

- Mapping testing remains owned by the phone app.
- The intended phone result should show the mapping ID, input value, and calculated pulses, intensity, duration, and interval rather than only a generic success message.
- The phone result must distinguish successful calculation from physical delivery and expose useful n8n failure details. These phone/n8n integration changes are outside the ResearcherSide UI changes documented above.

## Checks added

- `MappingContractSelfCheck.java` verifies signed decimal mapping endpoints, negative-value formatting, use-case display formatting, accepted intensity boundaries, and rejection of `0` and `256`.
- `MappingIntensityValidationSelfCheck.js` verifies invalid legacy intensity marking and validation.
- `MappingEditorStateSelfCheck.js` verifies editor state preservation and the new-mapping reveal action.
- `MappingEditorDirtyStateSelfCheck.js` verifies the saved/unsaved indicator behavior.
- `MappingCardLayoutSelfCheck.js` verifies wrapping and action layout safeguards.
- `HapticPreviewCalculatorSelfCheck.java` verifies interpolation, total length, and heart-rate preview calculations.
- `HapticPreviewUiSelfCheck.js` verifies the collapsible preview and its UI/controller integration.
- `AgentChatRequestStateSelfCheck.js` verifies active-request blocking, status, retry, and error handling.
- `MiniGraphGapSelfCheck.js` verifies continuous samples remain connected and anomalous time gaps break the mini-graph line.
- `FeedbackGraphLegendSelfCheck.js` verifies the grouped-alert legend condition for individual and grouped markers.
- `GraphGroupedAlertsCopySelfCheck.js` verifies the updated grouped-alert terminology.
- `ScheduleRangeValidationSelfCheck.js` verifies positive schedule constraints and explanatory copy.
- Full Java compilation was run with the repository's configured Liberica JDK after the changes.
- Relevant FXML files were parsed as XML and the JavaScript self-checks were run after their related changes.

## Remaining validation work

- Perform physical-watch validation to confirm only values within the supported amplitude range reach the Android vibration API.
- Complete and validate the phone/n8n test result details described above on the phone and workflow repositories.
- Decide whether invalid legacy backend mappings should eventually be migrated or disabled instead of only being marked for correction.
- Allow the agent to use relevant use-case and online information when suggesting mappings for participants with little or no personal data.
- Improve graph tooltip positioning and evaluate showing the mapping ID in the tooltip.
