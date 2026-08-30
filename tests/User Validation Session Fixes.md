# User Validation Session Fixes

This document records fixes and clarifications that came from the researcher user-validation review on 2026-08-27 and 2026-08-28.

## Mapping values and watch intensity

### Validation findings

- Sensor mapping endpoints could not accept negative values or decimals in the researcher UI.
- Vibration intensity could exceed the Android/watch amplitude limit.
- Negative ranges such as `-10--5` were difficult to read.
- Mapping history and exports could truncate decimal sensor values.

### Changes

- Sensor range endpoints now use `BigDecimal` throughout the UI models, parsing, editing, duplicate detection, API payloads, history, and display paths.
- Mapping endpoint fields accept signed decimal values, including negative values.
- The existing `start <= end` backend rule remains enforced.
- Both intensity endpoints are validated as integers in the inclusive range `1–255` before a mapping is created or updated.
- Mapping cards and history wrap negative values in parentheses, for example `(-10)-(-5)`, while edit fields and backend payloads retain normal numeric values.
- PDF mapping output preserves decimal values instead of converting them to integers.
- Field prompts now communicate signed decimal sensor values and the `1–255` intensity range.

### Compatibility behavior

- The backend contract and field names are unchanged.
- Existing mappings with intensity outside `1–255` still load and display their stored values.
- An existing invalid mapping is not automatically migrated or disabled.
- Attempting to save or update such a mapping is blocked until both intensity endpoints are within `1–255`.

## Graph readability and accuracy

### Validation findings

- Fixed `±10` Y-axis padding was excessive for small ranges such as UV `0–12`.
- The Y-axis label `Value` did not identify the selected use case.
- The context-drawer mini graph connected readings across missing-data periods.
- The grouped-alert legend appeared even when the graph contained no grouped alert marker.

### Changes

- The main graph now pads the Y-axis by 20% of the displayed data span.
- Non-negative datasets keep the lower Y-axis bound at or above zero.
- Constant-value datasets use a small fallback span so the chart remains readable.
- The Y-axis label now uses the spaced use-case name, for example `U V Value` or `Heart Rate Value`.
- Use-case display-name formatting is shared with the left sidebar.
- The mini graph now uses the main graph's gap rule: break the line when an interval exceeds both two minutes and five times the median sampling interval.
- The grouped-alert legend entry appears only when at least one rendered marker represents multiple alerts.
- The legend wording is now `Grouped alerts` rather than `Aggregated points`.

## Schedule terminology

### Validation findings

- `Mode` was mistaken for an application operating mode rather than a statistical measure.
- `Trigger` terminology was unclear in the schedule summary.

### Changes

- The UI displays `Most frequent value` instead of `mode` in the measure selector and schedule cards.
- The backend value remains exactly `mode`, so the schedule API contract is unchanged.
- Schedule summaries and validation messages use `Range` terminology consistently.

## Checks added

- `MappingContractSelfCheck.java` verifies signed decimal mapping endpoints, negative-value formatting, use-case display formatting, accepted intensity boundaries, and rejection of `0` and `256`.
- `MiniGraphGapSelfCheck.js` verifies continuous samples remain connected and anomalous time gaps break the mini-graph line.
- `FeedbackGraphLegendSelfCheck.js` verifies the grouped-alert legend condition for individual and grouped markers.
- Full Java compilation was run with the repository's configured Liberica JDK after the changes.
- `MappingsTab.fxml` and `SchedulesTab.fxml` were parsed as XML after their related changes.

## Repository status at documentation time

- Mapping decimal and intensity changes are committed in `af0bf9a`, with the required mapping history/export follow-up in `99a2d08`.
- Graph, mini-graph, sidebar, schedule terminology, and the two JavaScript self-check changes remain uncommitted.

## Remaining validation work

- Decide whether existing backend mappings outside `1–255` should be migrated, disabled, or visually marked invalid.
- Rename "Schedule", maybe use "personalization" or "personalization settings" instead of "schedule".
- Perform physical-watch validation to confirm only values within the supported amplitude range reach the Android vibration API.
- Allow agent to fetch online/self data that is relevant to the use case. It needs to suggest values for mappings not only based on the participant's data but also on the use case's data and online data. This is important for new participants who have no data yet.
- Fix tooltip in the graph. Make it close to point, consider adding the mapping ID to it if possible
- When the chat is waiting for a response, block sending new messages
- Break line if use case description is too long
- in the mapping card, if the use case name is too long it cuts the buttons in it
