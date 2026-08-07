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
| `core` | Smoke plus mappings, participant isolation, schedules, monitoring, HeartRate routing/logging and dashboard data consistency |
| `external` | Smoke plus Temperature and Pollution routing; reserved users are temporarily assigned matching mappings and then restored, with a temporary inactive rule retained only when a use case had no rule |
| `ai` | Smoke plus participant-wide and use-case-wide AI analysis and incomplete-input rejection |
| `soak` | Smoke plus latency, loss and duplicate checks; 50 requests by default |
| `formal` | Core plus soak with required metadata and a clean Git worktree |
| `all` | Smoke, core, external, AI and soak in one run; watch tests remain optional |

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
[39% | 11/28] mapping.shared - PASSED
```

The progress percentage counts software tests selected by the mode. Watch preflight and watch cases join the total only when `-WithWatch` is supplied. Cleanup and fatal-run records are reported separately.

The final summary also prints a pass rate based on selected tests that produced a pass or fail result. Skipped tests do not lower the pass rate, and completion remains separate so an interrupted run cannot look complete.

### Useful options

```powershell
# Use another n8n host
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\run-validation.ps1 -Mode smoke -BaseUrl "http://server:5678/webhook"

# Change the soak sample size
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\run-validation.ps1 -Mode soak -SoakRequests 100

# Write evidence somewhere else
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\run-validation.ps1 -Mode core -OutputRoot "C:\validation-evidence"

# Increase the HTTP timeout
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\run-validation.ps1 -Mode external -HttpTimeoutSeconds 60
```

The default HeartRate routing cooldown is 61 seconds. Only override `-RouteCooldownSeconds` when the n8n HeartRate vibration interval has also been changed; otherwise valid requests may remain rate-limited.

## What the automated tests validate

| Test ID | Main checks |
|---|---|
| `fixture.bootstrap` | Reserved users and validation use case exist; mappings and schedules are snapshotted |
| `mapping.assign_command` | A newly created mapping can be assigned through the mapping command workflow |
| `mapping.shared`, `mapping.change_shared` | Two synthetic users receive the same mapping and shared changes |
| `mapping.participant_copy` | A user-specific copy does not change the second user |
| `mapping.history` | Mappings created during the run and their assignment timestamps appear in history |
| `schedule.lifecycle` | List, add, read-back, change, deactivate and activate operations succeed with the expected values |
| `monitoring.lifecycle` | Monitoring start/stop works and an invalid stop reason is rejected |
| `routing.heartrate_boundaries` | Values at 30, 125 and 220 produce mapped haptics for the expected participant, phone and watch; 29 and 221 are rejected |
| `routing.database_log` | HeartRate values sent by the current run appear in sensor data |
| `dashboard.data_consistency` | User mapping IDs exist in the current configuration data consumed by the JavaFX dashboard |
| `external.temperature`, `external.pollution` | Contextual routes return data for the expected identities and exposed values are correlated with sensor-data records |
| `ai.participant_analysis`, `ai.usecase_analysis` | The chat endpoint accepts participant-wide and use-case-wide requests and returns HTTP 200 with a non-empty response |
| `ai.incomplete_input`, `ai.empty_message`, `ai.invalid_usecase` | Invalid AI requests are rejected by HTTP status or an explicit failure response |
| `soak.api_delivery` | Every unique request returns HTTP 200, is stored once, and direct API p95 stays within two seconds |
| `fixture.cleanup` | Both reserved users return to the baseline mapping and created mappings/schedules are deactivated |

The six `smoke.*` IDs verify endpoint availability and schemas. `reject.mapping_action`, `reject.sensor_type`, and `reject.missing_devices` verify deterministic rejection at the public webhook boundary.

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

### Latest verified software coverage

On 2026-08-07, all 28 selectable software tests, plus cleanup, passed across these retained evidence runs:

- `validation-20260807T185148376Z`: 27 of 28 passed; only `external.temperature` failed before contextual fixture assignment was corrected.
- `validation-20260807T192632740Z`: all 11 selected external-mode tests passed, including `external.temperature` and `external.pollution`.

This establishes aggregate automated coverage, not a single final acceptance run. Run `-Mode all` again to produce one post-fix report and ZIP in which all 28 selected software tests and cleanup pass together.

## Reserved validation data and cleanup

The runner uses:

- users `990001` and `990002`
- synthetic watches `990101` and `990102`
- synthetic phones `990201` and `990202`
- use case `ValidationHarness`

Do not use these IDs for real participants. The runner restores both users to the reserved baseline mapping and deactivates state created during the run. A cleanup failure invalidates the run and appears as `fixture.cleanup = FAILED`.
