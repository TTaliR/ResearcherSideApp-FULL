# Haptic Hub validation tests

`run-validation.ps1` validates the n8n/PostgreSQL backend and can optionally append physical phone/watch delivery tests. Watch tests are off by default, so routine and CI runs do not require the hardware.

## Requirements

Software-only tests require:

- Windows PowerShell 5.1 or newer
- n8n running at `http://localhost:5678/webhook` unless `-BaseUrl` is supplied
- PostgreSQL reachable through the active n8n workflows

Some modes add requirements:

| Mode or option | Additional requirement |
|---|---|
| `external` | Internet access for Temperature and Pollution services |
| `ai` | Working AI credentials in n8n |
| `all` | Internet access plus working Temperature, Pollution and AI integrations |
| `-WithWatch` | Android phone, Wear OS watch, `adb`, debug apps and ACK endpoint |

## Running the tests

From the project root:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\tests\run-validation.ps1 -Mode core
```

When the prompt already ends in `\tests>`:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\run-validation.ps1 -Mode core
```

`-ExecutionPolicy Bypass` applies only to that PowerShell process. It does not change the machine's permanent execution policy.

### Modes

Smoke and rejection tests run at the start of every mode.

| Mode | What it runs |
|---|---|
| `smoke` | Connection, endpoint schemas, read-only endpoints and rejection cases |
| `core` | Smoke plus mappings, exact interpolation, vibration-interval behavior, participant isolation, schedules, monitoring, HeartRate routing/logging and dashboard data consistency |
| `external` | Smoke plus Temperature and Pollution routing; reserved users are temporarily assigned matching mappings and then restored, with a temporary inactive rule retained only when a use case had no rule |
| `ai` | Smoke plus participant-wide and use-case-wide AI routing contracts and deterministic rejection of incomplete, empty and invalid-use-case requests |
| `soak` | Smoke plus latency, loss and duplicate checks; 50 requests by default |
| `stress` | Smoke plus concurrent ingestion, loss and duplicate checks; 30 requests at concurrency 10 by default |
| `formal` | Core plus soak with required metadata and a clean Git worktree |
| `all` | Smoke, core, external, AI, soak and stress in one run; watch tests remain optional |

## What the automated tests validate

### Smoke and request rejection

| Test ID | Main checks |
|---|---|
| `smoke.connection` | The DB Manager connection webhook is reachable and returns HTTP 200 |
| `smoke.configurations` | Current configurations return HTTP 200 and include a `rows` collection |
| `smoke.users` | The users endpoint returns at least one user and exposes its schema |
| `smoke.usecases` | The sensor/use-case endpoint returns at least one named use case |
| `smoke.sensor_data` | The HeartRate sensor-data endpoint returns HTTP 200 with a payload |
| `smoke.mapping_history` | Mapping history can be read for the reserved HeartRate participant |
| `reject.mapping_action` | An unsupported mapping command is rejected with HTTP 400 |
| `reject.sensor_type` | An unsupported sensor type returns `unsupported_sensor_type` without entering a valid route |
| `reject.missing_devices` | A routing request missing phone and watch identifiers is rejected or returns no routed response |

### Fixture lifecycle

| Test ID | Main checks |
|---|---|
| `fixture.bootstrap` | Reserved users and the validation use case exist; current mappings, schedules and intervals are snapshotted for safe restoration |
| `fixture.cleanup` | Reserved users return to the baseline mapping, the original HeartRate interval is restored, and mappings or schedules created by the run are deactivated |

### Mapping behavior

| Test ID | Main checks |
|---|---|
| `mapping.assign_command` | A newly created mapping can be assigned through the mapping command workflow |
| `mapping.shared`, `mapping.change_shared` | Two synthetic users receive the same mapping and both still reference it after a shared value is changed |
| `mapping.participant_copy` | Starting with two users on one shared mapping, requests a participant-specific duplicate for user A; verifies user A is assigned a new mapping ID while user B remains assigned to the original mapping ID |
| `mapping.history` | Mappings created during the run and their assignment timestamps appear in history |
| `mapping.linear_interpolation` | Ascending input, ascending output, descending output, and start/mid/end values produce exact intensity, duration and HeartRate interval values |
| `mapping.zero_width` | Creates a HeartRate mapping with an input range of exactly `90–90`, routes the value `90`, and verifies interpolation avoids division by zero by returning minimum intensity `33`, minimum duration `222`, and the exact derived HeartRate interval |
| `mapping.outside_range` | Values outside both endpoints do not select a mapping or generate haptics |

### Scheduling and monitoring

| Test ID | Main checks |
|---|---|
| `schedule.lifecycle` | List, add, read-back, change, deactivate and activate operations succeed with the expected schedule ID, participant, configuration and active state |
| `monitoring.lifecycle` | Monitoring start and intentional stop succeed, while an invalid stop reason is rejected |

### Vibration interval and rate limiting

| Test ID | Main checks |
|---|---|
| `interval.configuration` | A short validation interval is applied, read back and restored during cleanup |
| `interval.immediate_limit`, `interval.expiry` | Immediate repeats are suppressed and routing resumes after the configured interval |
| `interval.user_isolation`, `interval.concurrent_gate` | Rate-limit state is participant-specific and concurrent requests atomically consume one slot |

### Routing and stored data

| Test ID | Main checks |
|---|---|
| `routing.heartrate_boundaries` | Values at 30, 125 and 220 produce mapped haptics for the expected participant, phone and watch; 29 and 221 are rejected |
| `routing.database_log` | HeartRate values sent by the current run appear in sensor data |
| `dashboard.data_consistency` | User mapping IDs exist in the current configuration data consumed by the JavaFX dashboard |

### External contextual services

| Test ID | Main checks |
|---|---|
| `external.temperature`, `external.pollution` | Contextual routes return data for the expected identities and exposed values are correlated with sensor-data records |

### AI routing and rejection

| Test ID | Main checks |
|---|---|
| `ai.participant_analysis`, `ai.usecase_analysis` | The chat endpoint returns a non-empty reply classified as `knowledge`, routed to `expert_panel`, and marked read-only |
| `ai.incomplete_input`, `ai.empty_message`, `ai.invalid_usecase` | Invalid AI requests are rejected by HTTP status or an explicit failure response |

### Reliability and load

| Test ID | Main checks |
|---|---|
| `soak.api_delivery` | Every unique request returns HTTP 200, is stored once, and direct API p95 stays within two seconds |
| `stress.concurrent_ingestion` | Concurrent request batches all return HTTP 200 and every unique value is stored exactly once |

Examples:

```powershell
# Fast endpoint check
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\run-validation.ps1 -Mode smoke

# Main software validation
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\run-validation.ps1 -Mode core

# External-service validation
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\run-validation.ps1 -Mode external

# AI validation
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\run-validation.ps1 -Mode ai

# Backend reliability with the default 50 requests
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\run-validation.ps1 -Mode soak

# Concurrent ingestion with the default 30 requests at concurrency 10
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\run-validation.ps1 -Mode stress

# Publishable software-only run
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\run-validation.ps1 `
    -Mode formal `
    -N8nWorkflowVersion "workflow-version-or-export-hash" `
    -Operator "researcher-name"

# Comprehensive software run with one report and evidence archive
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\run-validation.ps1 -Mode all
```

Formal mode stops before mutation when metadata is missing or the Git worktree is dirty.
All mode requires the backend, PostgreSQL, Temperature and Pollution services, and working AI credentials. The watch is still optional; add `-WithWatch` only when its hardware requirements are available.

During a run, each selected test prints completion progress:

```text
[43% | 12/28] mapping.shared - PASSED
```

The progress percentage counts software tests selected by the mode. Watch preflight and watch cases join the total only when `-WithWatch` is supplied. Cleanup and fatal-run records are reported separately.

The final summary also prints a pass rate based on selected tests that produced a pass or fail result. Skipped tests do not lower the pass rate, and completion remains separate so an interrupted run cannot look complete.

### Useful options

```powershell
# Use another n8n host
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\run-validation.ps1 -Mode smoke -BaseUrl "http://server:5678/webhook"

# Change the soak sample size
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\run-validation.ps1 -Mode soak -SoakRequests 100

# Change the stress size and concurrency
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\run-validation.ps1 -Mode stress `
    -StressRequests 100 -StressConcurrency 10

# Write evidence somewhere else
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\run-validation.ps1 -Mode core -OutputRoot "C:\validation-evidence"

# Increase the HTTP timeout
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\run-validation.ps1 -Mode external -HttpTimeoutSeconds 60

# Run one named test only
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\run-validation.ps1 -OnlyTest ai.usecase_analysis
```

Core mapping and interval contracts temporarily set the HeartRate vibration interval to two seconds and restore its original value during cleanup. `-RouteCooldownSeconds` is the maximum time the runner polls for an accepted route; `-TestVibrationIntervalSeconds` changes the temporary interval when needed.

The soak test sends unique valid HeartRate readings and requires:

- every request to return HTTP 200
- exactly one stored row per request
- zero lost or duplicate events
- direct API p95 latency of at most 2 seconds

## Optional watch tests

Add `-WithWatch` only when the hardware is available. The runner performs hardware preflight before any database mutation and does not silently fall back to software-only mode.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\run-validation.ps1 -Mode core -WithWatch `
    -AckEndpoint "http://localhost:5678/webhook/haptic-ack" `
    -PhoneSerial "PHONE_ADB_SERIAL" `
    -WatchSerial "WATCH_ADB_SERIAL" `
    -PhonePackage "your.phone.package" `
    -WatchPackage "your.watch.package" `
    -PhoneTriggerAction "your.debug.TRIGGER_HAPTIC"
```

Preflight checks `adb`, both device serials, both installed debug packages and the ACK endpoint. The hardware phase then validates:

- a matching watch ID produces an ACK
- participant, phone, watch, event, mapping and pulse data match
- a nonmatching watch ID produces no ACK
- ACK p95 is at most 5 seconds

Simultaneous two-watch isolation is always reported as skipped until a second watch is available. An ACK proves that the watch invoked its vibration API; it does not measure motor force.

Without `-WithWatch`, hardware cases are recorded as `SKIPPED` with reason `watch_not_requested` and do not count as software failures.

## Results

Each run prints an `Evidence:` path. By default the files are written to:

```text
tests\validation-results\validation-<UTC timestamp>\
```

| File | Purpose |
|---|---|
| `summary.csv` | Compact result table with per-test UTC start/end timestamps for Excel or PowerShell |
| `results.json` | Full machine-readable test results |
| `run-metadata.json` | Mode, timestamps, Git commit, thresholds, progress totals and cleanup status |
| `responses\*.json` | Sanitized response or error evidence for each executed test |
| `report.html` | Self-contained readable report with pass percentage, progress, per-test UTC timestamps, categories and evidence links |

The runner also creates `validation-<UTC timestamp>.zip` beside the result directory. Share that ZIP to include the HTML report, CSV, JSON, metadata and sanitized response evidence together.

At the end of the run, the terminal prints separate `Report:` and `Share:` paths. Open `report.html` directly for the clearest local view; extract the ZIP before opening it when received on another computer.

From the `tests` directory, open the newest run:

```powershell
$latest = Get-ChildItem .\validation-results -Directory |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1

Invoke-Item $latest.FullName
```

If the prompt is already inside a specific `validation-...` folder, simply run:

```powershell
Invoke-Item .
```

Display a readable summary:

```powershell
Import-Csv "$($latest.FullName)\summary.csv" |
    Format-Table TestId, Status, DurationMs, Message -AutoSize
```

The process exits with code `0` when all executed tests pass and cleanup succeeds. It exits with code `1` when any test, preflight or cleanup check fails. Evidence is still written after ordinary test failures.

### Latest verification of the expanded tests

On 2026-08-08:

- `validation-20260808T094220109Z`: all 10 selected stress-mode tests passed; 30 concurrent requests were stored exactly once.
- `validation-20260808T094246617Z`: all 28 supported core-mode tests passed; the additional descending-input experiment was later removed because sensor input bounds are ordered, while descending vibration outputs remain supported and tested.
- `validation-20260808T094435581Z`: both strengthened AI analysis contracts failed. Participant analysis returned an empty HTTP 200 response; use-case analysis returned `clarify` instead of `knowledge` routed to `expert_panel`. All three deterministic AI rejection cases passed.
- `validation-20260808T133153594Z`: the final software-only `all` run completed all 37 selected tests with a 100% pass rate and successful cleanup.

Run `-Mode all` to produce one acceptance report containing all 37 software tests.

## Physical haptic test requests

The automated routing tests verify the haptic response returned by n8n; they do not make a physical watch vibrate. The system is request-response based, so the response returns to whichever component sent the request. A ResearcherSide GUI request therefore returns to the GUI, not to the phone/watch path.

For a researcher to feel a proposed mapping, the shortest current design is a Test control in the phone app: assign the participant and mapping in ResearcherSide, enter a sensor value on the phone, and let the phone send the normal routing request. A GUI-triggered physical test would require a new push channel from n8n or ResearcherSide to the phone.

## Reserved validation data and cleanup

The runner uses:

- users `990001` and `990002`
- synthetic watches `990101` and `990102`
- synthetic phones `990201` and `990202`
- use case `ValidationHarness`

Do not use these IDs for real participants. The runner restores both users to the reserved baseline mapping and deactivates state created during the run. A cleanup failure invalidates the run and appears as `fixture.cleanup = FAILED`.
