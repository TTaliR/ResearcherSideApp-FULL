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
| `external` | Smoke plus Temperature and Pollution routing |
| `ai` | Smoke plus participant-wide and use-case-wide AI analysis and incomplete-input rejection |
| `soak` | Smoke plus latency, loss and duplicate checks; 50 requests by default |
| `formal` | Core plus soak with required metadata and a clean Git worktree |

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
```

Formal mode stops before mutation when metadata is missing or the Git worktree is dirty.

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

## What the core tests validate

| Test area | Main checks |
|---|---|
| Fixture | Reserved users and validation use case exist; mappings and schedules are snapshotted |
| Mapping assignment | A selected existing mapping can be assigned to a user |
| Shared mapping | Two synthetic users receive the same mapping and shared changes |
| Participant isolation | A user-specific copy does not change the second user |
| Mapping history | Mapping changes appear in history |
| Schedules | List, add, change, deactivate and activate operations succeed |
| Monitoring | Monitoring start/stop works and an invalid stop reason is rejected |
| HeartRate routing | Values at 30, 125 and 220 produce bounded haptics; 29 and 221 are rejected |
| Database logging | HeartRate requests appear in sensor data |
| Dashboard consistency | User mapping IDs exist in the current configuration data consumed by the JavaFX dashboard |
| Cleanup | Both reserved users return to the baseline mapping and created mappings/schedules are deactivated |

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
| `summary.csv` | Compact result table for Excel or PowerShell |
| `results.json` | Full machine-readable test results |
| `run-metadata.json` | Mode, timestamps, Git commit, thresholds, totals and cleanup status |
| `responses\*.json` | Sanitized response or error evidence for each executed test |

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

## Reserved validation data and cleanup

The runner uses:

- users `990001` and `990002`
- synthetic watches `990101` and `990102`
- synthetic phones `990201` and `990202`
- use case `ValidationHarness`

Do not use these IDs for real participants. The runner restores both users to the reserved baseline mapping and deactivates state created during the run. A cleanup failure invalidates the run and appears as `fixture.cleanup = FAILED`.

## Known backend issue

At the time this README was written, `mapping.assign_command` exposes a real n8n defect: assigning a newly created mapping through `mapping-commands` can return HTTP 500. The runner keeps this as a failing test while allowing unrelated tests and cleanup to continue. A core run therefore exits with code `1` until that backend workflow is fixed.
