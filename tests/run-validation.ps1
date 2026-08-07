[CmdletBinding()]
param(
    [ValidateSet('smoke', 'core', 'external', 'ai', 'soak', 'formal', 'all')]
    [string]$Mode = 'core',

    [switch]$WithWatch,

    [string]$BaseUrl = 'http://localhost:5678/webhook',
    [string]$OutputRoot = '',
    [int]$UserA = 990001,
    [int]$UserB = 990002,
    [int]$PhoneA = 990201,
    [int]$PhoneB = 990202,
    [int]$WatchA = 990101,
    [int]$WatchB = 990102,
    [string]$ValidationUseCase = 'ValidationHarness',
    [int]$HttpTimeoutSeconds = 30,
    [int]$RouteCooldownSeconds = 61,
    [int]$SoakRequests = 50,

    [string]$N8nWorkflowVersion = '',
    [string]$Operator = $env:USERNAME,

    [string]$AckEndpoint = '',
    [string]$PhoneSerial = '',
    [string]$WatchSerial = '',
    [string]$PhonePackage = '',
    [string]$WatchPackage = '',
    [string]$PhoneTriggerAction = ''
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($OutputRoot)) {
    $OutputRoot = Join-Path $PSScriptRoot 'validation-results'
}

$startedAt = [DateTimeOffset]::UtcNow
$runId = 'validation-{0}' -f $startedAt.ToString('yyyyMMddTHHmmssfffZ')
$runDirectory = Join-Path $OutputRoot $runId
$responseDirectory = Join-Path $runDirectory 'responses'
New-Item -ItemType Directory -Force -Path $responseDirectory | Out-Null

$results = New-Object 'System.Collections.Generic.List[object]'
$testIdsByPhase = [ordered]@{
    smoke = @(
        'smoke.connection', 'smoke.configurations', 'smoke.users', 'smoke.usecases',
        'smoke.sensor_data', 'smoke.mapping_history', 'reject.mapping_action',
        'reject.sensor_type', 'reject.missing_devices'
    )
    core = @(
        'fixture.bootstrap', 'mapping.assign_command', 'mapping.shared', 'mapping.change_shared',
        'mapping.participant_copy', 'mapping.history', 'schedule.lifecycle',
        'monitoring.lifecycle', 'routing.heartrate_boundaries', 'routing.database_log',
        'dashboard.data_consistency'
    )
    external = @('external.temperature', 'external.pollution')
    ai = @(
        'ai.participant_analysis', 'ai.usecase_analysis', 'ai.incomplete_input',
        'ai.empty_message', 'ai.invalid_usecase'
    )
    soak = @('soak.api_delivery')
    watch = @(
        'watch.preflight', 'watch.matching_id', 'watch.nonmatching_id',
        'watch.ack_latency', 'watch.two_watch_isolation'
    )
}
$selectedPhases = switch ($Mode) {
    'smoke' { @('smoke') }
    'core' { @('smoke', 'core') }
    'external' { @('smoke', 'external') }
    'ai' { @('smoke', 'ai') }
    'soak' { @('smoke', 'soak') }
    'formal' { @('smoke', 'core', 'soak') }
    'all' { @('smoke', 'core', 'external', 'ai', 'soak') }
}
$selectedTestIds = @($selectedPhases | ForEach-Object { $testIdsByPhase[$_] })
if ($WithWatch) { $selectedTestIds += $testIdsByPhase.watch }
$plannedTests = $selectedTestIds.Count
$completedTests = 0
$lastApiResponse = $null
$fixture = @{
    BaselineMappingId = $null
    CreatedMappingIds = New-Object 'System.Collections.Generic.List[int]'
    CreatedScheduleId = $null
    HeartRateMappingId = $null
    HeartRateUseCaseId = $null
    HeartRateCases = New-Object 'System.Collections.Generic.List[object]'
    MappingHistoryCount = 0
    ValidationUseCaseId = $null
    MonitoringExpected = @{}
    MappingSnapshot = @()
    ScheduleSnapshot = @()
    Ready = $false
}

function Get-SafeName {
    param([string]$Value)
    return ($Value -replace '[^a-zA-Z0-9._-]', '_').Trim('_')
}

function Protect-Data {
    param($Value)

    if ($null -eq $Value) { return $null }
    if ($Value -is [string] -or $Value -is [ValueType]) { return $Value }

    if ($Value -is [System.Collections.IDictionary]) {
        $copy = [ordered]@{}
        foreach ($key in $Value.Keys) {
            if ([string]$key -match '(?i)authorization|password|secret|token|api.?key|email|fname|lname|first.?name|last.?name') {
                $copy[$key] = '[REDACTED]'
            } else {
                $copy[$key] = Protect-Data $Value[$key]
            }
        }
        return [pscustomobject]$copy
    }

    if ($Value -is [System.Collections.IEnumerable]) {
        return @($Value | ForEach-Object { Protect-Data $_ })
    }

    $copy = [ordered]@{}
    foreach ($property in $Value.PSObject.Properties) {
        if ($property.Name -match '(?i)authorization|password|secret|token|api.?key|email|fname|lname|first.?name|last.?name') {
            $copy[$property.Name] = '[REDACTED]'
        } else {
            $copy[$property.Name] = Protect-Data $property.Value
        }
    }
    return [pscustomobject]$copy
}

function Save-TestArtifact {
    param([string]$TestId, $Value)

    $path = Join-Path $responseDirectory ('{0}.json' -f (Get-SafeName $TestId))
    Protect-Data $Value | ConvertTo-Json -Depth 30 | Set-Content -Encoding UTF8 -Path $path
    return $path
}

function Add-Result {
    param(
        [string]$Id,
        [string]$Name,
        [string]$Category,
        [bool]$RequiresWatch,
        [ValidateSet('PASSED', 'FAILED', 'SKIPPED')][string]$Status,
        [double]$DurationMs,
        [string]$Message,
        [string]$Artifact = '',
        [string]$StartedAtUtc = '',
        [string]$EndedAtUtc = ''
    )

    $recordedAt = [DateTimeOffset]::UtcNow.ToString('o')
    if ([string]::IsNullOrWhiteSpace($StartedAtUtc)) { $StartedAtUtc = $recordedAt }
    if ([string]::IsNullOrWhiteSpace($EndedAtUtc)) { $EndedAtUtc = $recordedAt }

    $results.Add([pscustomobject][ordered]@{
        TestId = $Id
        Name = $Name
        Category = $Category
        RequiresWatch = $RequiresWatch
        StartedAtUtc = $StartedAtUtc
        EndedAtUtc = $EndedAtUtc
        Status = $Status
        DurationMs = [math]::Round($DurationMs, 2)
        Message = $Message
        Artifact = $Artifact
    })

    $color = if ($Status -eq 'PASSED') { 'Green' } elseif ($Status -eq 'FAILED') { 'Red' } else { 'Yellow' }
    if ($selectedTestIds -contains $Id) {
        $script:completedTests++
        $percent = [int][math]::Floor(($completedTests / $plannedTests) * 100)
        Write-Progress -Activity "Haptic Hub validation ($Mode)" -Status "$completedTests/$plannedTests $Id - $Status" -PercentComplete $percent
        Write-Host ('[{0}% | {1}/{2}] {3} - {4}' -f $percent, $completedTests, $plannedTests, $Id, $Status) -ForegroundColor $color
    } else {
        Write-Host ('[{0}] {1} - {2}' -f $Status, $Id, $Message) -ForegroundColor $color
    }
}

function Invoke-ValidationTest {
    param(
        [string]$Id,
        [string]$Name,
        [string]$Category,
        [switch]$RequiresWatch,
        [scriptblock]$Body
    )

    if ($RequiresWatch -and -not $WithWatch) {
        $skippedAt = [DateTimeOffset]::UtcNow.ToString('o')
        Add-Result -Id $Id -Name $Name -Category $Category -RequiresWatch $true -Status SKIPPED `
            -DurationMs 0 -Message 'watch_not_requested' -StartedAtUtc $skippedAt -EndedAtUtc $skippedAt
        return
    }

    $script:lastApiResponse = $null
    $testStartedAt = [DateTimeOffset]::UtcNow
    $timer = [Diagnostics.Stopwatch]::StartNew()
    try {
        $actual = & $Body
        $timer.Stop()
        $artifact = Save-TestArtifact -TestId $Id -Value $actual
        Add-Result -Id $Id -Name $Name -Category $Category -RequiresWatch ([bool]$RequiresWatch) `
            -Status PASSED -DurationMs $timer.Elapsed.TotalMilliseconds -Message 'ok' -Artifact $artifact `
            -StartedAtUtc $testStartedAt.ToString('o') -EndedAtUtc ([DateTimeOffset]::UtcNow.ToString('o'))
    } catch {
        $timer.Stop()
        $details = [pscustomobject]@{
            Error = $_.Exception.Message
            ScriptStackTrace = $_.ScriptStackTrace
            LastApiResponse = $lastApiResponse
        }
        $artifact = Save-TestArtifact -TestId $Id -Value $details
        Add-Result -Id $Id -Name $Name -Category $Category -RequiresWatch ([bool]$RequiresWatch) `
            -Status FAILED -DurationMs $timer.Elapsed.TotalMilliseconds -Message $_.Exception.Message -Artifact $artifact `
            -StartedAtUtc $testStartedAt.ToString('o') -EndedAtUtc ([DateTimeOffset]::UtcNow.ToString('o'))
    }
}

function Assert-True {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) { throw $Message }
}

function Assert-Status {
    param($Response, [int[]]$Expected)
    Assert-True ($Expected -contains [int]$Response.StatusCode) `
        ('Expected HTTP {0}, received {1}. Body: {2}' -f ($Expected -join '/'), $Response.StatusCode, $Response.Content)
}

function Get-Value {
    param($Object, [string[]]$Names, $Default = $null)
    if ($null -eq $Object) { return $Default }
    foreach ($name in $Names) {
        $property = $Object.PSObject.Properties[$name]
        if ($null -ne $property -and $null -ne $property.Value) { return $property.Value }
    }
    return $Default
}

function As-Array {
    param($Value)
    if ($null -eq $Value) { return @() }
    return @($Value)
}

function First-OrNull {
    param([AllowNull()][object[]]$Items)
    if ($null -eq $Items -or $Items.Count -eq 0) { return $null }
    return $Items[0]
}

function Invoke-Api {
    param(
        [ValidateSet('GET', 'POST')][string]$Method,
        [string]$Path,
        $Body = $null,
        [string]$RawBody = '',
        [int]$TimeoutSeconds = $HttpTimeoutSeconds
    )

    $uri = if ($Path -match '^https?://') { $Path } else { '{0}/{1}' -f $BaseUrl.TrimEnd('/'), $Path.TrimStart('/') }
    $arguments = @{
        Uri = $uri
        Method = $Method
        UseBasicParsing = $true
        TimeoutSec = $TimeoutSeconds
        ErrorAction = 'Stop'
    }

    if ($Method -eq 'POST') {
        $arguments.ContentType = 'application/json'
        $arguments.Body = if ($RawBody) { $RawBody } else { $Body | ConvertTo-Json -Depth 30 -Compress }
    }

    $timer = [Diagnostics.Stopwatch]::StartNew()
    try {
        $response = Invoke-WebRequest @arguments
        $statusCode = [int]$response.StatusCode
        $content = [string]$response.Content
    } catch {
        if ($null -eq $_.Exception.Response) { throw }
        $statusCode = [int]$_.Exception.Response.StatusCode
        $reader = New-Object IO.StreamReader($_.Exception.Response.GetResponseStream())
        try { $content = $reader.ReadToEnd() } finally { $reader.Dispose() }
    } finally {
        $timer.Stop()
    }

    $data = $null
    if (-not [string]::IsNullOrWhiteSpace($content)) {
        try { $data = $content | ConvertFrom-Json } catch { $data = $content }
    }

    $result = [pscustomobject]@{
        Method = $Method
        Uri = $uri
        StatusCode = $statusCode
        DurationMs = $timer.Elapsed.TotalMilliseconds
        Content = $content
        Data = $data
    }
    $script:lastApiResponse = $result
    return $result
}

function Get-Users {
    $response = Invoke-Api GET 'get-users'
    Assert-Status $response @(200)
    return @(As-Array $response.Data)
}

function Get-User {
    param([int]$UserId)
    return First-OrNull @(Get-Users | Where-Object { [int](Get-Value $_ @('userid', 'userId') 0) -eq $UserId } | Select-Object -First 1)
}

function Get-UseCases {
    $response = Invoke-Api GET 'get-sensor-types'
    Assert-Status $response @(200)
    return @(As-Array $response.Data)
}

function Get-UseCase {
    param([string]$Name)
    return First-OrNull @(Get-UseCases | Where-Object { [string](Get-Value $_ @('name', 'type') '') -eq $Name } | Select-Object -First 1)
}

function Get-Configurations {
    $response = Invoke-Api GET 'current-configurations'
    Assert-Status $response @(200)
    $rows = Get-Value $response.Data @('rows', 'data', 'payload') @()
    return @(As-Array $rows)
}

function Get-MappingId {
    param($Mapping)
    return [int](Get-Value $Mapping @('feedback_config_rule_id', 'mapping_id', 'id') 0)
}

function Get-UserMapping {
    param([int]$UserId, [string]$UseCaseName)

    $user = Get-User $UserId
    if ($null -eq $user) { return $null }
    $mappings = Get-Value $user @('usecase_mappings', 'mappings') @()
    return First-OrNull @(As-Array $mappings | Where-Object {
        [string](Get-Value $_ @('usecase_name', 'usecaseName', 'monitoringType', 'type') '') -eq $UseCaseName
    } | Select-Object -First 1)
}

function Wait-UserMapping {
    param(
        [int]$UserId,
        [string]$UseCaseName,
        [int]$ExpectedMappingId = 0,
        [int]$ExcludedMappingId = 0,
        [int]$TimeoutSeconds = 15
    )

    $timer = [Diagnostics.Stopwatch]::StartNew()
    do {
        $mapping = Get-UserMapping $UserId $UseCaseName
        $mappingId = Get-MappingId $mapping
        if ($mappingId -gt 0 -and
            ($ExpectedMappingId -eq 0 -or $mappingId -eq $ExpectedMappingId) -and
            ($ExcludedMappingId -eq 0 -or $mappingId -ne $ExcludedMappingId)) { return $mapping }
        Start-Sleep -Milliseconds 500
    } while ($timer.Elapsed.TotalSeconds -lt $TimeoutSeconds)

    throw "User $UserId did not reach the expected $UseCaseName mapping state within $TimeoutSeconds seconds."
}

function Ensure-User {
    param([int]$UserId, [string]$LastName)

    $user = Get-User $UserId
    if ($null -ne $user) {
        $first = [string](Get-Value $user @('fname', 'firstName') '')
        $last = [string](Get-Value $user @('lname', 'lastName') '')
        if ($first -eq 'Validation' -and $last -eq $LastName) { return $user }
        Assert-True ([string]::IsNullOrWhiteSpace($first) -and [string]::IsNullOrWhiteSpace($last)) `
            "Reserved user ID $UserId already belongs to a different record."
    }

    if ($null -eq $user) {
        $path = 'add-user?userId={0}&firstName=Validation&lastName={1}' -f $UserId, [uri]::EscapeDataString($LastName)
        $response = Invoke-Api GET $path
        Assert-Status $response @(200)
    }
    $rename = Invoke-Api POST 'edit-users' @{ userId = $UserId; fName = 'Validation'; lName = $LastName }
    Assert-Status $rename @(200)
    $user = Get-User $UserId
    Assert-True ($null -ne $user) "User $UserId was not created."
    Assert-True ([string](Get-Value $user @('fname', 'firstName') '') -eq 'Validation') "User $UserId name was not set."
    Assert-True ([string](Get-Value $user @('lname', 'lastName') '') -eq $LastName) "User $UserId name was not set."
    return $user
}

function Ensure-UseCase {
    param([string]$Name)

    $useCase = Get-UseCase $Name
    if ($null -ne $useCase) { return $useCase }

    $response = Invoke-Api POST 'create-usecase' @{
        name = $Name
        description = 'Reserved for automated system validation'
    }
    Assert-Status $response @(200, 201)
    $useCase = Get-UseCase $Name
    Assert-True ($null -ne $useCase) "Use case $Name was not created."
    return $useCase
}

function New-Mapping {
    param(
        [string]$Type,
        [double]$MinValue,
        [double]$MaxValue,
        [int]$MinPulses = 1,
        [int]$MaxPulses = 3
    )

    $rule = [ordered]@{
        type = $Type
        minvalue = $MinValue
        maxvalue = $MaxValue
        minpulses = $MinPulses
        maxpulses = $MaxPulses
        minintensity = 20
        maxintensity = 60
        minduration = 100
        maxduration = 300
        mininterval = 200
        maxinterval = 600
        active = $true
    }
    $raw = '[[{0}]]' -f ($rule | ConvertTo-Json -Depth 10 -Compress)
    $response = Invoke-Api POST 'set-rules' -RawBody $raw
    Assert-Status $response @(200, 201)

    $mapping = First-OrNull @(Get-Configurations | Where-Object {
        [string](Get-Value $_ @('usecase_name', 'type') '') -eq $Type -and
        [double](Get-Value $_ @('minvalue') 0) -eq $MinValue -and
        [double](Get-Value $_ @('maxvalue') 0) -eq $MaxValue
    } | Sort-Object { Get-MappingId $_ } -Descending | Select-Object -First 1)

    Assert-True ($null -ne $mapping) "Could not find the mapping created for $Type."
    return $mapping
}

function Assign-Mapping {
    param([int]$UserId, [int]$MappingId, [int]$UseCaseId, [string]$UseCaseName)

    $response = Invoke-Api POST 'mapping-commands' @{
        action = 'assign_mapping_to_user'
        mapping_id = $MappingId
        user_id = $UserId
        usecase_id = $UseCaseId
        usecase_name = $UseCaseName
        session_id = $runId
    }
    Assert-Status $response @(200)
    Assert-True (-not ($response.Data -and (Get-Value $response.Data @('success') $true) -eq $false)) `
        "Mapping assignment failed for user $UserId."
    return $response
}

function Assign-UseCase {
    param([int]$UserId, [int]$UseCaseId)
    $response = Invoke-Api POST 'assign-usecase' @{ userId = $UserId; usecaseId = $UseCaseId }
    Assert-Status $response @(200)
    return $response
}

function Set-MappingActive {
    param([int]$MappingId, [bool]$Active)
    $response = Invoke-Api POST 'mapping-activation' @{ mappingId = $MappingId; active = $Active }
    Assert-Status $response @(200)
    return $response
}

function Find-ScheduleId {
    param($Value)

    if ($null -eq $Value) { return 0 }
    if ($Value -is [System.Collections.IEnumerable] -and -not ($Value -is [string])) {
        foreach ($item in $Value) {
            $id = Find-ScheduleId $item
            if ($id -gt 0) { return $id }
        }
        return 0
    }

    $direct = [int](Get-Value $Value @('schedule_id', 'scheduleId', 'id') 0)
    if ($direct -gt 0) { return $direct }
    foreach ($name in @('data', 'payload', 'schedules')) {
        $nested = Get-Value $Value @($name) $null
        if ($null -ne $nested) {
            $id = Find-ScheduleId $nested
            if ($id -gt 0) { return $id }
        }
    }
    return 0
}

function Find-Schedule {
    param($Value, [int]$ScheduleId)

    if ($null -eq $Value) { return $null }
    if ($Value -is [System.Collections.IEnumerable] -and -not ($Value -is [string])) {
        foreach ($item in $Value) {
            $found = Find-Schedule $item $ScheduleId
            if ($null -ne $found) { return $found }
        }
        return $null
    }

    if ([int](Get-Value $Value @('schedule_id', 'scheduleId', 'id') 0) -eq $ScheduleId) { return $Value }
    foreach ($name in @('data', 'payload', 'schedules')) {
        $found = Find-Schedule (Get-Value $Value @($name) $null) $ScheduleId
        if ($null -ne $found) { return $found }
    }
    return $null
}

function Get-MappingHistory {
    param([int]$UserId, [string]$UseCaseName)

    $response = Invoke-Api GET ('users-mappings-history?useCaseName={0}&userId={1}' -f [uri]::EscapeDataString($UseCaseName), $UserId)
    Assert-Status $response @(200)
    $root = First-OrNull @(As-Array $response.Data | Select-Object -First 1)
    return [pscustomobject]@{
        Response = $response
        Entries = @(As-Array (Get-Value $root @('mappings') @()))
        Count = [int](Get-Value $root @('count') 0)
    }
}

function Invoke-Schedule {
    param([string]$Action, [hashtable]$Parameters)
    $response = Invoke-Api POST 'schedule' @{
        action = $Action
        usecase_name = $ValidationUseCase
        usecase_id = $fixture.ValidationUseCaseId
        session_id = $runId
        message = "validation schedule $Action"
        params = $Parameters
    }
    Assert-Status $response @(200)
    Assert-True (-not ($response.Data -and (Get-Value $response.Data @('success') $true) -eq $false)) `
        "Schedule action $Action failed."
    return $response
}

function Initialize-Fixture {
    Ensure-User $UserA 'A' | Out-Null
    Ensure-User $UserB 'B' | Out-Null

    $validation = Ensure-UseCase $ValidationUseCase
    $fixture.ValidationUseCaseId = [int](Get-Value $validation @('usecase_id', 'id') 0)
    Assert-True ($fixture.ValidationUseCaseId -gt 0) 'Validation use-case ID is missing.'

    foreach ($id in @($UserA, $UserB)) {
        $user = Get-User $id
        $fixture.MonitoringExpected[$id] = [bool](Get-Value $user @('monitoring_expected') $false)
    }

    $fixture.MappingSnapshot = @(Get-Configurations)
    $baseline = First-OrNull @($fixture.MappingSnapshot | Where-Object {
        [string](Get-Value $_ @('usecase_name', 'type') '') -eq $ValidationUseCase -and
        [double](Get-Value $_ @('minvalue') 0) -eq -100 -and
        [double](Get-Value $_ @('maxvalue') 0) -eq -99
    } | Sort-Object { Get-MappingId $_ } | Select-Object -First 1)

    if ($null -eq $baseline) { $baseline = New-Mapping $ValidationUseCase -100 -99 1 1 }
    $fixture.BaselineMappingId = Get-MappingId $baseline
    Assert-True ($fixture.BaselineMappingId -gt 0) 'Baseline validation mapping is missing.'
    if (-not [bool](Get-Value $baseline @('active') $false)) {
        Set-MappingActive $fixture.BaselineMappingId $true | Out-Null
    }

    foreach ($oldMapping in @(Get-Configurations | Where-Object {
        [string](Get-Value $_ @('usecase_name', 'type') '') -eq $ValidationUseCase -and
        (Get-MappingId $_) -ne $fixture.BaselineMappingId -and
        [bool](Get-Value $_ @('active') $false)
    })) {
        Set-MappingActive (Get-MappingId $oldMapping) $false | Out-Null
    }

    $fixture.Ready = $true
    Assign-UseCase $UserA $fixture.ValidationUseCaseId | Out-Null
    Assign-UseCase $UserB $fixture.ValidationUseCaseId | Out-Null
    Wait-UserMapping $UserA $ValidationUseCase $fixture.BaselineMappingId | Out-Null
    Wait-UserMapping $UserB $ValidationUseCase $fixture.BaselineMappingId | Out-Null
    $fixture.MappingHistoryCount = (Get-MappingHistory $UserA $ValidationUseCase).Count
    $fixture.ScheduleSnapshot = @(
        (Invoke-Schedule 'listAll' @{ user_id = $UserA }).Data
        (Invoke-Schedule 'listAll' @{ user_id = $UserB }).Data
    )

    return [pscustomobject]@{
        UserA = $UserA
        UserB = $UserB
        ValidationUseCase = $ValidationUseCase
        ValidationUseCaseId = $fixture.ValidationUseCaseId
        BaselineMappingId = $fixture.BaselineMappingId
        MappingSnapshot = $fixture.MappingSnapshot
        ScheduleSnapshot = $fixture.ScheduleSnapshot
    }
}

function Restore-Fixture {
    $errors = New-Object 'System.Collections.Generic.List[string]'

    foreach ($mappingId in @($fixture.CreatedMappingIds | Select-Object -Unique)) {
        if ($mappingId -gt 0 -and $mappingId -ne $fixture.BaselineMappingId) {
            try { Set-MappingActive $mappingId $false | Out-Null }
            catch { $errors.Add($_.Exception.Message) }
        }
    }

    if ($fixture.Ready -and $fixture.BaselineMappingId) {
        foreach ($id in @($UserA, $UserB)) {
            try {
                Assign-UseCase $id $fixture.ValidationUseCaseId | Out-Null
                Wait-UserMapping $id $ValidationUseCase $fixture.BaselineMappingId | Out-Null
            } catch { $errors.Add($_.Exception.Message) }
        }
    }

    if ($fixture.CreatedScheduleId) {
        try { Invoke-Schedule 'deactivate' @{ schedule_id = $fixture.CreatedScheduleId } | Out-Null }
        catch { $errors.Add($_.Exception.Message) }
    }

    foreach ($id in @($UserA, $UserB)) {
        if (-not $fixture.MonitoringExpected.ContainsKey($id)) { continue }
        try {
            if ($fixture.MonitoringExpected[$id]) {
                $response = Invoke-Api GET ('monitoring-config?userId={0}' -f $id)
                Assert-Status $response @(200)
            } else {
                $response = Invoke-Api POST 'stopped-monitoring-alert' @{ userId = $id; reason = 'user_stopped' }
                Assert-Status $response @(200)
            }
        } catch { $errors.Add($_.Exception.Message) }
    }

    if ($fixture.Ready) {
        foreach ($id in @($UserA, $UserB)) {
            try {
                $mapping = Get-UserMapping $id $ValidationUseCase
                Assert-True ((Get-MappingId $mapping) -eq $fixture.BaselineMappingId) `
                    "Cleanup did not restore user $id to the baseline mapping."
            } catch { $errors.Add($_.Exception.Message) }
        }
    }

    if ($errors.Count -gt 0) { throw ('Cleanup failed: {0}' -f ($errors -join ' | ')) }
    return [pscustomobject]@{ Restored = $true; BaselineMappingId = $fixture.BaselineMappingId }
}

function Get-Percentile {
    param([double[]]$Values, [double]$Percentile)
    if ($Values.Count -eq 0) { return 0 }
    $sorted = @($Values | Sort-Object)
    $index = [math]::Ceiling(($Percentile / 100) * $sorted.Count) - 1
    return [double]$sorted[[math]::Max(0, $index)]
}

function Assert-Haptic {
    param($Actual, $Mapping, [double]$Value, [int]$UserId, [int]$WatchId, [int]$PhoneId)

    if ($Value -lt 30 -or $Value -gt 220) {
        Assert-True ([string](Get-Value $Actual @('reason') '') -eq 'invalid_heart_rate_value') 'Invalid HeartRate value was not rejected.'
        Assert-True ([int](Get-Value $Actual @('pulses') -1) -eq 0) 'Invalid HeartRate value generated pulses.'
        Assert-True ([string](Get-Value $Actual @('alertGiven') '') -eq 'no') 'Invalid HeartRate value generated an alert.'
        return
    }

    Assert-True ([int](Get-Value $Actual @('pulses') -1) -eq 10) 'HeartRate output did not use the ten-pulse heartbeat pattern.'
    Assert-True ([string](Get-Value $Actual @('alertGiven') '') -eq 'yes') 'Valid HeartRate value did not generate an alert.'
    Assert-True ([int](Get-Value $Actual @('fbRangeID') 0) -eq (Get-MappingId $Mapping)) 'Routing used the wrong mapping.'
    foreach ($field in @('intensity', 'duration')) {
        $actualValue = [int](Get-Value $Actual @($field) -1)
        $low = [math]::Min([int](Get-Value $Mapping @("min$field") 0), [int](Get-Value $Mapping @("max$field") 0))
        $high = [math]::Max([int](Get-Value $Mapping @("min$field") 0), [int](Get-Value $Mapping @("max$field") 0))
        Assert-True ($actualValue -ge $low -and $actualValue -le $high) "$field is outside the assigned mapping bounds."
    }
    Assert-True ([int](Get-Value $Actual @('interval') -1) -ge 0) 'HeartRate interval is negative.'
    Assert-RouteIdentity $Actual $UserId $WatchId $PhoneId
}

function Assert-RouteIdentity {
    param($Actual, [int]$UserId, [int]$WatchId, [int]$PhoneId)

    Assert-True ([int](Get-Value $Actual @('userID', 'userId') 0) -eq $UserId) 'Unexpected user ID in routing response.'
    Assert-True ([int](Get-Value $Actual @('watchID', 'SmartWatchID', 'smartWatchId') 0) -eq $WatchId) 'Unexpected watch ID.'
    Assert-True ([int](Get-Value $Actual @('phoneID', 'AndroidID', 'androidId') 0) -eq $PhoneId) 'Unexpected phone ID.'
}

function Invoke-RouteCase {
    param([int]$UserId, [int]$WatchId, [int]$PhoneId, [double]$Value, $Mapping)

    $sentAt = [DateTimeOffset]::UtcNow
    $response = Invoke-Api POST 'usecase-routing' @{
        userId = $UserId
        smartWatchId = $WatchId
        androidId = $PhoneId
        type = 'HeartRate'
        value = $Value
        validationRunId = $runId
    }
    Assert-Status $response @(200)

    if ([string](Get-Value $response.Data @('reason') '') -eq 'rate_limited') {
        Start-Sleep -Seconds $RouteCooldownSeconds
        $response = Invoke-Api POST 'usecase-routing' @{
            userId = $UserId
            smartWatchId = $WatchId
            androidId = $PhoneId
            type = 'HeartRate'
            value = $Value
            validationRunId = $runId
        }
        Assert-Status $response @(200)
    }

    Assert-True ([string](Get-Value $response.Data @('reason') '') -ne 'rate_limited') 'Routing remained rate-limited after the configured cooldown.'
    Assert-Haptic $response.Data $Mapping $Value $UserId $WatchId $PhoneId
    if ($Value -ge 30 -and $Value -le 220) {
        $fixture.HeartRateCases.Add([pscustomobject]@{ UserId = $UserId; Value = $Value; SentAt = $sentAt })
    }
    return $response
}

function Invoke-WatchPreflight {
    foreach ($required in @{
        AckEndpoint = $AckEndpoint
        PhoneSerial = $PhoneSerial
        WatchSerial = $WatchSerial
        PhonePackage = $PhonePackage
        WatchPackage = $WatchPackage
        PhoneTriggerAction = $PhoneTriggerAction
    }.GetEnumerator()) {
        Assert-True (-not [string]::IsNullOrWhiteSpace([string]$required.Value)) `
            "-$($required.Key) is required with -WithWatch."
    }

    $adb = Get-Command adb -ErrorAction SilentlyContinue
    Assert-True ($null -ne $adb) 'adb was not found on PATH.'
    $devices = & adb devices
    Assert-True (($devices -join "`n") -match ('(?m)^{0}\s+device$' -f [regex]::Escape($PhoneSerial))) 'The requested phone is not connected through adb.'
    Assert-True (($devices -join "`n") -match ('(?m)^{0}\s+device$' -f [regex]::Escape($WatchSerial))) 'The requested watch is not connected through adb.'
    & adb -s $PhoneSerial shell pm path $PhonePackage | Out-Null
    Assert-True ($LASTEXITCODE -eq 0) "Phone package $PhonePackage is not installed."
    & adb -s $WatchSerial shell pm path $WatchPackage | Out-Null
    Assert-True ($LASTEXITCODE -eq 0) "Watch package $WatchPackage is not installed."

    $separator = if ($AckEndpoint.Contains('?')) { '&' } else { '?' }
    $response = Invoke-Api GET ('{0}{1}eventId=preflight' -f $AckEndpoint, $separator)
    Assert-True ($response.StatusCode -lt 500) 'The haptic ACK query endpoint is unavailable.'
    return [pscustomobject]@{ PhoneSerial = $PhoneSerial; WatchSerial = $WatchSerial; AckEndpoint = $AckEndpoint }
}

function Get-WatchAck {
    param([string]$EventId, [int]$TimeoutSeconds = 5)
    $deadline = [DateTimeOffset]::UtcNow.AddSeconds($TimeoutSeconds)
    $separator = if ($AckEndpoint.Contains('?')) { '&' } else { '?' }
    do {
        $response = Invoke-Api GET ('{0}{1}eventId={2}' -f $AckEndpoint, $separator, [uri]::EscapeDataString($EventId)) -TimeoutSeconds 5
        if ($response.StatusCode -eq 200 -and $null -ne $response.Data) { return $response }
        Start-Sleep -Milliseconds 250
    } while ([DateTimeOffset]::UtcNow -lt $deadline)
    return $null
}

function Send-WatchEvent {
    param([int]$TargetWatchId, [double]$Value)

    $eventId = '{0}-{1}' -f $runId, [guid]::NewGuid().ToString('N')
    $timer = [Diagnostics.Stopwatch]::StartNew()
    & adb -s $PhoneSerial shell am broadcast -a $PhoneTriggerAction `
        --es validationRunId $runId --es eventId $eventId --ei userId $UserA `
        --ei smartWatchId $TargetWatchId --ei androidId $PhoneA --es sensorType HeartRate --ef value $Value | Out-Null
    Assert-True ($LASTEXITCODE -eq 0) 'The phone validation broadcast failed.'
    $ack = Get-WatchAck $eventId 5
    $timer.Stop()
    return [pscustomobject]@{ EventId = $eventId; Ack = if ($ack) { $ack.Data } else { $null }; DurationMs = $timer.Elapsed.TotalMilliseconds }
}

function Invoke-SmokeTests {
    Invoke-ValidationTest 'smoke.connection' 'DB Manager is reachable' 'smoke' {
        $response = Invoke-Api POST 'check-connection' @{}
        Assert-Status $response @(200)
        return $response
    }
    Invoke-ValidationTest 'smoke.configurations' 'Configuration schema is available' 'smoke' {
        $response = Invoke-Api GET 'current-configurations'
        Assert-Status $response @(200)
        Assert-True ($null -ne (Get-Value $response.Data @('rows') $null)) 'Response is missing rows.'
        return [pscustomobject]@{ StatusCode = $response.StatusCode; Count = @(As-Array (Get-Value $response.Data @('rows') @())).Count }
    }
    Invoke-ValidationTest 'smoke.users' 'Users schema is available' 'smoke' {
        $users = Get-Users
        Assert-True ($users.Count -gt 0) 'No users were returned.'
        return [pscustomobject]@{ Count = $users.Count; SampleKeys = @($users[0].PSObject.Properties.Name) }
    }
    Invoke-ValidationTest 'smoke.usecases' 'Use-case schema is available' 'smoke' {
        $useCases = Get-UseCases
        Assert-True ($useCases.Count -gt 0) 'No use cases were returned.'
        return [pscustomobject]@{ Count = $useCases.Count; Names = @($useCases | ForEach-Object { Get-Value $_ @('name') '' }) }
    }
    Invoke-ValidationTest 'smoke.sensor_data' 'Sensor-data endpoint responds' 'smoke' {
        $response = Invoke-Api GET ('sensor-data?userid={0}&alert_type=HeartRate' -f $UserA)
        Assert-Status $response @(200)
        return [pscustomobject]@{ StatusCode = $response.StatusCode; HasPayload = $null -ne (Get-Value $response.Data @('payload') $null) }
    }
    Invoke-ValidationTest 'smoke.mapping_history' 'Mapping-history endpoint responds' 'smoke' {
        $response = Invoke-Api GET ('users-mappings-history?useCaseName=HeartRate&userId={0}' -f $UserA)
        Assert-Status $response @(200)
        return $response
    }
    Invoke-ValidationTest 'reject.mapping_action' 'Unsupported mapping action is rejected' 'rejection' {
        $response = Invoke-Api POST 'mapping-commands' @{ action = 'unsupported'; usecase_id = 3; usecase_name = 'HeartRate' }
        Assert-Status $response @(400)
        return $response
    }
    Invoke-ValidationTest 'reject.sensor_type' 'Unsupported sensor type is rejected safely' 'rejection' {
        $response = Invoke-Api POST 'usecase-routing' @{
            userId = $UserA; smartWatchId = $WatchA; androidId = $PhoneA
            type = 'UnsupportedValidationType'; value = 42
        }
        Assert-Status $response @(200)
        Assert-True ([string](Get-Value $response.Data @('reason') '') -eq 'unsupported_sensor_type') 'Unexpected unsupported-sensor response.'
        return $response
    }
    Invoke-ValidationTest 'reject.missing_devices' 'Missing device identifiers do not reach routing' 'rejection' {
        $response = Invoke-Api POST 'usecase-routing' @{ userId = $UserA; type = 'HeartRate'; value = 82 }
        Assert-True ($response.StatusCode -ge 400 -or [string]::IsNullOrWhiteSpace($response.Content)) 'Missing device IDs were not rejected.'
        return $response
    }
}

function Invoke-CoreTests {
    Invoke-ValidationTest 'fixture.bootstrap' 'Dedicated validation fixture is ready' 'fixture' {
        return Initialize-Fixture
    }

    Invoke-ValidationTest 'mapping.assign_command' 'Mapping command assigns an existing mapping' 'mapping' {
        $seed = 10000 + ([int]([DateTimeOffset]::UtcNow.ToUnixTimeSeconds() % 100000))
        $mapping = New-Mapping $ValidationUseCase $seed ($seed + 100) 1 3
        $mappingId = Get-MappingId $mapping
        $fixture.CreatedMappingIds.Add($mappingId)
        Assign-Mapping $UserA $mappingId $fixture.ValidationUseCaseId $ValidationUseCase | Out-Null
        $a = Get-MappingId (Get-UserMapping $UserA $ValidationUseCase)
        Assert-True ($a -eq $mappingId) 'User A was not assigned to the selected mapping.'
        return [pscustomobject]@{ MappingId = $mappingId; UserAMapping = $a }
    }

    Invoke-ValidationTest 'mapping.shared' 'Two users share one validation mapping' 'mapping' {
        $mappingId = [int]$fixture.CreatedMappingIds[$fixture.CreatedMappingIds.Count - 1]
        Set-MappingActive $fixture.BaselineMappingId $false | Out-Null
        $a = Get-MappingId (Wait-UserMapping $UserA $ValidationUseCase $mappingId)
        $b = Get-MappingId (Wait-UserMapping $UserB $ValidationUseCase $mappingId)
        Set-MappingActive $fixture.BaselineMappingId $true | Out-Null
        return [pscustomobject]@{ MappingId = $mappingId; UserAMapping = $a; UserBMapping = $b }
    }

    Invoke-ValidationTest 'mapping.change_shared' 'Shared mapping change reaches both users' 'mapping' {
        $mappingId = [int]$fixture.CreatedMappingIds[$fixture.CreatedMappingIds.Count - 1]
        $response = Invoke-Api POST 'mapping-commands' @{
            action = 'change'; usecase_id = $fixture.ValidationUseCaseId; usecase_name = $ValidationUseCase
            session_id = $runId; params = @{ id = $mappingId; minpulses = 4 }
        }
        Assert-Status $response @(200)
        $updated = First-OrNull @(Get-Configurations | Where-Object { (Get-MappingId $_) -eq $mappingId } | Select-Object -First 1)
        Assert-True ([int](Get-Value $updated @('minpulses') 0) -eq 4) 'The shared mapping value was not updated.'
        Wait-UserMapping $UserA $ValidationUseCase $mappingId | Out-Null
        Wait-UserMapping $UserB $ValidationUseCase $mappingId | Out-Null
        return $response
    }

    Invoke-ValidationTest 'mapping.participant_copy' 'Participant-specific mapping remains isolated' 'mapping' {
        $originalId = [int]$fixture.CreatedMappingIds[$fixture.CreatedMappingIds.Count - 1]
        $response = Invoke-Api POST 'mapping-commands' @{
            action = 'duplicate_mapping_for_user'; original_mapping_id = $originalId; user_id = $UserA
            usecase_id = $fixture.ValidationUseCaseId; usecase_name = $ValidationUseCase
            session_id = $runId; params = @{ minpulses = 7 }
        }
        Assert-Status $response @(200)
        $a = Get-MappingId (Wait-UserMapping $UserA $ValidationUseCase -ExcludedMappingId $originalId)
        $fixture.CreatedMappingIds.Add($a)
        $b = Get-MappingId (Wait-UserMapping $UserB $ValidationUseCase $originalId)
        return [pscustomobject]@{ OriginalMappingId = $originalId; UserACopyId = $a; UserBMappingId = $b }
    }

    Invoke-ValidationTest 'mapping.history' 'Mapping history records the validation changes' 'mapping' {
        $history = Get-MappingHistory $UserA $ValidationUseCase
        Assert-True ($history.Count -gt $fixture.MappingHistoryCount) 'Mapping history did not grow after validation assignments.'
        foreach ($mappingId in @($fixture.CreatedMappingIds | Select-Object -Unique)) {
            $entry = First-OrNull @($history.Entries | Where-Object {
                [int](Get-Value $_ @('mappingId', 'mapping_id', 'feedback_config_rule_id') 0) -eq $mappingId
            } | Select-Object -First 1)
            Assert-True ($null -ne $entry) "Mapping $mappingId is absent from user A history."
            Assert-True (-not [string]::IsNullOrWhiteSpace([string](Get-Value $entry @('assignedAt', 'assigned_at') ''))) `
                "Mapping $mappingId has no assignment timestamp."
        }
        return $history.Response
    }

    Invoke-ValidationTest 'schedule.lifecycle' 'Schedule lifecycle and activation succeed' 'schedule' {
        $list = Invoke-Schedule 'listAll' @{ user_id = $UserA }
        $add = Invoke-Schedule 'add' @{ user_id = $UserA; interval_days = 7; measure_type = 'average'; trigger_percentage = 0.10 }
        $scheduleId = Find-ScheduleId $add.Data
        Assert-True ($scheduleId -gt 0) 'The added schedule ID was not returned.'
        $fixture.CreatedScheduleId = $scheduleId

        $added = Find-Schedule (Invoke-Schedule 'listAll' @{ user_id = $UserA }).Data $scheduleId
        Assert-True ($null -ne $added) 'The added schedule was not returned by listAll.'
        Assert-True ([int](Get-Value $added @('user_id', 'userId') 0) -eq $UserA) 'The schedule belongs to the wrong participant.'
        Assert-True ([int](Get-Value $added @('interval_days', 'intervalDays') 0) -eq 7) 'The added schedule interval was not stored.'
        Assert-True ([string](Get-Value $added @('measure_type', 'measureType') '') -eq 'average') 'The added schedule measure type was not stored.'
        Assert-True ([math]::Abs([double](Get-Value $added @('trigger_percentage', 'triggerPercentage') -1) - 10) -lt 0.0001) `
            'The added schedule trigger percentage was not stored.'

        $change = Invoke-Schedule 'change' @{ schedule_id = $scheduleId; user_id = $UserA; interval_days = 14; measure_type = 'median'; trigger_percentage = 0.15 }
        $changed = Find-Schedule (Invoke-Schedule 'listAll' @{ user_id = $UserA }).Data $scheduleId
        Assert-True ([int](Get-Value $changed @('interval_days', 'intervalDays') 0) -eq 14) 'The changed schedule interval was not stored.'
        Assert-True ([string](Get-Value $changed @('measure_type', 'measureType') '') -eq 'median') 'The changed schedule measure type was not stored.'
        Assert-True ([math]::Abs([double](Get-Value $changed @('trigger_percentage', 'triggerPercentage') -1) - 15) -lt 0.0001) `
            'The changed schedule trigger percentage was not stored.'

        $deactivate = Invoke-Schedule 'deactivate' @{ schedule_id = $scheduleId }
        $inactive = Find-Schedule (Invoke-Schedule 'listAll' @{ user_id = $UserA }).Data $scheduleId
        $inactiveValue = Get-Value $inactive @('active', 'isActive') $null
        Assert-True ($null -ne $inactive -and $null -ne $inactiveValue -and -not [System.Convert]::ToBoolean($inactiveValue)) `
            'The schedule remained active after deactivation.'

        $activate = Invoke-Schedule 'activate' @{ schedule_id = $scheduleId }
        $active = Find-Schedule (Invoke-Schedule 'listAll' @{ user_id = $UserA }).Data $scheduleId
        $activeValue = Get-Value $active @('active', 'isActive') $null
        Assert-True ($null -ne $active -and $null -ne $activeValue -and [System.Convert]::ToBoolean($activeValue)) `
            'The schedule remained inactive after activation.'
        return [pscustomobject]@{ ScheduleId = $scheduleId; List = $list.Data; Add = $add.Data; Change = $change.Data; Deactivate = $deactivate.Data; Activate = $activate.Data }
    }

    Invoke-ValidationTest 'monitoring.lifecycle' 'Monitoring start and intentional stop succeed' 'monitoring' {
        $start = Invoke-Api GET ('monitoring-config?userId={0}' -f $UserA)
        Assert-Status $start @(200)
        $stop = Invoke-Api POST 'stopped-monitoring-alert' @{ userId = $UserA; reason = 'user_stopped' }
        Assert-Status $stop @(200)
        $invalid = Invoke-Api POST 'stopped-monitoring-alert' @{ userId = $UserA; reason = 'invalid_reason' }
        Assert-Status $invalid @(400)
        return [pscustomobject]@{ Start = $start; Stop = $stop; InvalidStop = $invalid }
    }

    Invoke-ValidationTest 'routing.heartrate_boundaries' 'HeartRate boundaries generate expected haptics' 'routing' {
        $heartRate = Get-UseCase 'HeartRate'
        $fixture.HeartRateUseCaseId = [int](Get-Value $heartRate @('usecase_id', 'id') 0)
        Assert-True ($fixture.HeartRateUseCaseId -gt 0) 'HeartRate use case is missing.'
        Assign-UseCase $UserA $fixture.HeartRateUseCaseId | Out-Null
        Assign-UseCase $UserB $fixture.HeartRateUseCaseId | Out-Null
        $userMappingA = Wait-UserMapping $UserA 'HeartRate'
        $userMappingB = Wait-UserMapping $UserB 'HeartRate'
        $mappings = Get-Configurations
        $mappingA = First-OrNull @($mappings | Where-Object { (Get-MappingId $_) -eq (Get-MappingId $userMappingA) } | Select-Object -First 1)
        $mappingB = First-OrNull @($mappings | Where-Object { (Get-MappingId $_) -eq (Get-MappingId $userMappingB) } | Select-Object -First 1)
        Assert-True ($null -ne $mappingA -and $null -ne $mappingB) 'A user has no active HeartRate mapping.'
        $fixture.HeartRateMappingId = Get-MappingId $mappingA

        $cases = @(
            @{ User = $UserA; Watch = $WatchA; Phone = $PhoneA; Value = 30; Mapping = $mappingA },
            @{ User = $UserB; Watch = $WatchB; Phone = $PhoneB; Value = 125; Mapping = $mappingB },
            @{ User = $UserA; Watch = $WatchA; Phone = $PhoneA; Value = 220; Mapping = $mappingA },
            @{ User = $UserB; Watch = $WatchB; Phone = $PhoneB; Value = 29; Mapping = $mappingB },
            @{ User = $UserA; Watch = $WatchA; Phone = $PhoneA; Value = 221; Mapping = $mappingA }
        )
        $lastRun = @{}
        $responses = @()
        foreach ($case in $cases) {
            if ($lastRun.ContainsKey($case.User)) {
                $elapsed = ([DateTimeOffset]::UtcNow - $lastRun[$case.User]).TotalSeconds
                if ($elapsed -lt $RouteCooldownSeconds) { Start-Sleep -Seconds ([math]::Ceiling($RouteCooldownSeconds - $elapsed)) }
            }
            $responses += Invoke-RouteCase $case.User $case.Watch $case.Phone $case.Value $case.Mapping
            $lastRun[$case.User] = [DateTimeOffset]::UtcNow
        }
        return [pscustomobject]@{ UserAMappingId = Get-MappingId $mappingA; UserBMappingId = Get-MappingId $mappingB; Cases = $responses }
    }

    Invoke-ValidationTest 'routing.database_log' 'HeartRate results are visible in sensor data' 'routing' {
        $evidence = @()
        foreach ($userId in @($UserA, $UserB)) {
            $response = Invoke-Api GET ('sensor-data?userid={0}&alert_type=HeartRate' -f $userId)
            Assert-Status $response @(200)
            $rows = @(As-Array (Get-Value $response.Data @('payload') @()))
            foreach ($expected in @($fixture.HeartRateCases | Where-Object UserId -eq $userId)) {
                $match = First-OrNull @($rows | Where-Object {
                    $rowTime = try { [DateTimeOffset](Get-Value $_ @('time', 'timestamp', 'created_at') [DateTimeOffset]::MinValue) } catch { [DateTimeOffset]::MinValue }
                    [int](Get-Value $_ @('userid', 'userId') 0) -eq $userId -and
                    [math]::Abs([double](Get-Value $_ @('value') -999999) - [double]$expected.Value) -lt 0.000001 -and
                    $rowTime -ge $expected.SentAt.AddSeconds(-1)
                } | Select-Object -First 1)
                Assert-True ($null -ne $match) "HeartRate value $($expected.Value) for user $userId was not recorded by this run."
            }
            $evidence += [pscustomobject]@{ UserId = $userId; Expected = @($fixture.HeartRateCases | Where-Object UserId -eq $userId); Latest = @($rows | Select-Object -First 5) }
        }
        return $evidence
    }

    Invoke-ValidationTest 'dashboard.data_consistency' 'Dashboard API data agrees on active mappings' 'dashboard' {
        $configIds = @(Get-Configurations | ForEach-Object { Get-MappingId $_ })
        $a = Get-MappingId (Get-UserMapping $UserA 'HeartRate')
        $b = Get-MappingId (Get-UserMapping $UserB 'HeartRate')
        Assert-True ($a -gt 0 -and $configIds -contains $a) 'User A dashboard mapping is absent from current configurations.'
        Assert-True ($b -gt 0 -and $configIds -contains $b) 'User B dashboard mapping is absent from current configurations.'
        return [pscustomobject]@{ UserAMappingId = $a; UserBMappingId = $b }
    }
}

function Invoke-ExternalTests {
    Invoke-ValidationTest 'external.temperature' 'Temperature route contacts its external service' 'external' {
        return Invoke-ExternalRouteCase 'Temperature' $UserA $WatchA $PhoneA
    }
    Invoke-ValidationTest 'external.pollution' 'Pollution route contacts its external service' 'external' {
        return Invoke-ExternalRouteCase 'Pollution' $UserB $WatchB $PhoneB
    }
}

function Invoke-ExternalRouteCase {
    param([string]$Type, [int]$UserId, [int]$WatchId, [int]$PhoneId)

    $user = Get-User $UserId
    $original = First-OrNull @(As-Array (Get-Value $user @('usecase_mappings', 'mappings') @()) | Select-Object -First 1)
    $originalMappingId = Get-MappingId $original
    $originalUseCaseId = [int](Get-Value $original @('usecase_id', 'usecaseId') 0)
    $originalUseCaseName = [string](Get-Value $original @('usecase_name', 'usecaseName', 'monitoringType', 'type') '')

    Assert-True ($originalMappingId -gt 0 -and $originalUseCaseId -gt 0 -and -not [string]::IsNullOrWhiteSpace($originalUseCaseName)) `
        "User $UserId has no active mapping to restore after the $Type test."

    $useCase = Get-UseCase $Type
    $useCaseId = [int](Get-Value $useCase @('usecase_id', 'id') 0)
    Assert-True ($useCaseId -gt 0) "$Type use case is missing."

    $createdMappingId = 0
    try {
        $target = First-OrNull @(Get-Configurations | Where-Object {
            [int](Get-Value $_ @('usecase_id', 'usecaseId') 0) -eq $useCaseId -or
            [string](Get-Value $_ @('usecase_name', 'usecaseName', 'type') '') -eq $Type
        } | Sort-Object { Get-MappingId $_ } | Select-Object -First 1)
        if ($null -eq $target) {
            $target = New-Mapping $Type -100 100
            $createdMappingId = Get-MappingId $target
        }
        $targetMappingId = Get-MappingId $target
        Assert-True ($targetMappingId -gt 0) "$Type has no mapping available for validation."

        Assign-Mapping $UserId $targetMappingId $useCaseId $Type | Out-Null
        Wait-UserMapping $UserId $Type $targetMappingId | Out-Null

        $sentAt = [DateTimeOffset]::UtcNow
        $response = Invoke-Api POST 'usecase-routing' @{
            userId = $UserId; smartWatchId = $WatchId; androidId = $PhoneId
            type = $Type; lat = 32.794; lon = 34.989; validationRunId = $runId
        } -TimeoutSeconds ([math]::Max(60, $HttpTimeoutSeconds))
        Assert-Status $response @(200)
        Assert-True ($null -ne $response.Data) "$Type returned no JSON result."
        return Assert-ExternalRoute $response $Type $UserId $WatchId $PhoneId $sentAt
    } finally {
        Assign-Mapping $UserId $originalMappingId $originalUseCaseId $originalUseCaseName | Out-Null
        Wait-UserMapping $UserId $originalUseCaseName $originalMappingId | Out-Null
        if ($createdMappingId -gt 0) { Set-MappingActive $createdMappingId $false | Out-Null }
    }
}

function Assert-ExternalRoute {
    param($Response, [string]$Type, [int]$UserId, [int]$WatchId, [int]$PhoneId, [DateTimeOffset]$SentAt)

    $hasHaptic = $null -ne (Get-Value $Response.Data @('pulses', 'intensity', 'fbRangeID') $null)
    if ($hasHaptic) { Assert-RouteIdentity $Response.Data $UserId $WatchId $PhoneId }

    $value = Get-Value $Response.Data @('value', 'sensorValue', 'temperature', 'pollution', 'aqi') $null
    $recorded = $null
    if ($null -ne $value) {
        $timer = [Diagnostics.Stopwatch]::StartNew()
        do {
            $sensor = Invoke-Api GET ('sensor-data?userid={0}&alert_type={1}' -f $UserId, [uri]::EscapeDataString($Type))
            Assert-Status $sensor @(200)
            $recorded = First-OrNull @(As-Array (Get-Value $sensor.Data @('payload') $sensor.Data) | Where-Object {
                $rowTime = try { [DateTimeOffset](Get-Value $_ @('time', 'timestamp', 'created_at') [DateTimeOffset]::MinValue) } catch { [DateTimeOffset]::MinValue }
                [int](Get-Value $_ @('userid', 'userId') 0) -eq $UserId -and
                [math]::Abs([double](Get-Value $_ @('value') -999999) - [double]$value) -lt 0.000001 -and
                $rowTime -ge $SentAt.AddSeconds(-1)
            } | Select-Object -First 1)
            if ($null -eq $recorded) { Start-Sleep -Milliseconds 250 }
        } while ($null -eq $recorded -and $timer.Elapsed.TotalSeconds -lt 5)
        Assert-True ($null -ne $recorded) "$Type value $value was not recorded for user $UserId."
    }

    return [pscustomobject]@{
        Response = $Response
        RoutingIdentityChecked = $hasHaptic
        DatabaseCorrelation = if ($null -eq $value) { 'not_available_in_response' } else { 'matched' }
        RecordedRow = $recorded
    }
}

function Invoke-AiTests {
    $session = [guid]::NewGuid().ToString()
    Invoke-ValidationTest 'ai.participant_analysis' 'AI uses participant context' 'ai' {
        $heartRateId = [int](Get-Value (Get-UseCase 'HeartRate') @('usecase_id', 'id') 0)
        Assert-True ($heartRateId -gt 0) 'HeartRate use case is missing.'
        $response = Invoke-Api POST 'chat' @{
            session_id = $session; usecase_id = $heartRateId; usecase_name = 'HeartRate'
            user_id = $UserA; user_name = 'Validation A'; analysis_scope = 'participant'
            message = 'Explain the active HeartRate mapping without changing anything.'
        } -TimeoutSeconds ([math]::Max(90, $HttpTimeoutSeconds))
        Assert-Status $response @(200)
        Assert-True (-not [string]::IsNullOrWhiteSpace($response.Content)) 'AI returned an empty response.'
        return $response
    }
    Invoke-ValidationTest 'ai.usecase_analysis' 'AI uses use-case-wide context' 'ai' {
        $heartRateId = [int](Get-Value (Get-UseCase 'HeartRate') @('usecase_id', 'id') 0)
        Assert-True ($heartRateId -gt 0) 'HeartRate use case is missing.'
        $response = Invoke-Api POST 'chat' @{
            session_id = $session; usecase_id = $heartRateId; usecase_name = 'HeartRate'
            all_users_selected = $true; analysis_scope = 'usecase'
            message = 'Summarize the active HeartRate mappings without changing anything.'
        } -TimeoutSeconds ([math]::Max(90, $HttpTimeoutSeconds))
        Assert-Status $response @(200)
        Assert-True (-not [string]::IsNullOrWhiteSpace($response.Content)) 'AI returned an empty response.'
        return $response
    }
    Invoke-ValidationTest 'ai.incomplete_input' 'AI rejects incomplete context' 'ai' {
        $response = Invoke-Api POST 'chat' @{ session_id = [guid]::NewGuid().ToString(); message = 'This intentionally omits the use-case ID.' } `
            -TimeoutSeconds ([math]::Max(90, $HttpTimeoutSeconds))
        $failedBody = $response.Data -and ((Get-Value $response.Data @('success') $true) -eq $false -or $null -ne (Get-Value $response.Data @('error') $null))
        Assert-True ($response.StatusCode -ge 400 -or $failedBody) 'Incomplete AI input was not rejected.'
        return $response
    }
    Invoke-ValidationTest 'ai.empty_message' 'AI rejects an empty message' 'ai' {
        $heartRateId = [int](Get-Value (Get-UseCase 'HeartRate') @('usecase_id', 'id') 0)
        Assert-True ($heartRateId -gt 0) 'HeartRate use case is missing.'
        $response = Invoke-Api POST 'chat' @{
            session_id = [guid]::NewGuid().ToString(); usecase_id = $heartRateId; usecase_name = 'HeartRate'
            user_id = $UserA; message = ''
        } -TimeoutSeconds ([math]::Max(90, $HttpTimeoutSeconds))
        $failedBody = $response.Data -and ((Get-Value $response.Data @('success') $true) -eq $false -or $null -ne (Get-Value $response.Data @('error') $null))
        Assert-True ($response.StatusCode -ge 400 -or $failedBody) 'An empty AI message was not rejected.'
        return $response
    }
    Invoke-ValidationTest 'ai.invalid_usecase' 'AI rejects an invalid use-case ID' 'ai' {
        $response = Invoke-Api POST 'chat' @{
            session_id = [guid]::NewGuid().ToString(); usecase_id = -1; usecase_name = 'UnsupportedValidationUseCase'
            user_id = $UserA; message = 'Explain this invalid use case.'
        } -TimeoutSeconds ([math]::Max(90, $HttpTimeoutSeconds))
        $failedBody = $response.Data -and ((Get-Value $response.Data @('success') $true) -eq $false -or $null -ne (Get-Value $response.Data @('error') $null))
        Assert-True ($response.StatusCode -ge 400 -or $failedBody) 'An invalid AI use-case ID was not rejected.'
        return $response
    }
}

function Invoke-SoakTests {
    if (-not $fixture.Ready) { Initialize-Fixture | Out-Null }
    if (-not $fixture.HeartRateUseCaseId) {
        $heartRate = Get-UseCase 'HeartRate'
        $fixture.HeartRateUseCaseId = [int](Get-Value $heartRate @('usecase_id', 'id') 0)
        Assign-UseCase $UserA $fixture.HeartRateUseCaseId | Out-Null
        $fixture.HeartRateMappingId = Get-MappingId (Wait-UserMapping $UserA 'HeartRate')
        Assert-True ($fixture.HeartRateMappingId -gt 0) 'User A has no HeartRate mapping for the soak run.'
    }

    Invoke-ValidationTest 'soak.api_delivery' "Backend records $SoakRequests unique requests" 'soak' {
        $values = @()
        $latencies = @()
        $responses = @()
        $soakStartedAt = [DateTimeOffset]::UtcNow
        $baseValue = 80
        for ($index = 0; $index -lt $SoakRequests; $index++) {
            $value = $baseValue + ($index / 1000.0)
            $values += $value
            $response = Invoke-Api POST 'usecase-routing' @{
                userId = $UserA; smartWatchId = $WatchA; androidId = $PhoneA
                type = 'HeartRate'; value = $value; validationRunId = $runId
            }
            Assert-Status $response @(200)
            $latencies += [double]$response.DurationMs
            $responses += [pscustomobject]@{ Value = $value; StatusCode = $response.StatusCode; DurationMs = $response.DurationMs; Reason = Get-Value $response.Data @('reason') '' }
        }

        Start-Sleep -Seconds 1
        $sensor = Invoke-Api GET ('sensor-data?userid={0}&alert_type=HeartRate' -f $UserA)
        Assert-Status $sensor @(200)
        $rows = @(As-Array (Get-Value $sensor.Data @('payload') @()))
        $matches = @($rows | Where-Object {
            $values -contains [double](Get-Value $_ @('value') -1) -and
            [DateTimeOffset](Get-Value $_ @('time') [DateTimeOffset]::MinValue) -ge $soakStartedAt
        })
        $duplicates = @($matches | Group-Object { [double](Get-Value $_ @('value') -1) } | Where-Object Count -ne 1)
        $p95 = Get-Percentile $latencies 95
        Assert-True ($matches.Count -eq $SoakRequests) "Expected $SoakRequests stored events, found $($matches.Count)."
        Assert-True ($duplicates.Count -eq 0) 'Duplicate or missing values were found in stored sensor events.'
        Assert-True ($p95 -le 2000) "Direct API p95 was $([math]::Round($p95,2)) ms, above 2000 ms."
        return [pscustomobject]@{ RequestCount = $SoakRequests; StoredCount = $matches.Count; P95Ms = $p95; Requests = $responses }
    }
}

function Invoke-WatchTests {
    if ($WithWatch) {
        if (-not $fixture.Ready) { Initialize-Fixture | Out-Null }
        if (-not $fixture.HeartRateUseCaseId) {
            $heartRate = Get-UseCase 'HeartRate'
            $fixture.HeartRateUseCaseId = [int](Get-Value $heartRate @('usecase_id', 'id') 0)
        }
        Assign-UseCase $UserA $fixture.HeartRateUseCaseId | Out-Null
        $watchMapping = Wait-UserMapping $UserA 'HeartRate'
        $fixture.HeartRateMappingId = Get-MappingId $watchMapping
    }

    Invoke-ValidationTest 'watch.matching_id' 'Matching watch produces an ACK' 'watch' -RequiresWatch {
        $result = Send-WatchEvent $WatchA 82
        Assert-True ($null -ne $result.Ack) 'No ACK was received from the matching watch.'
        Assert-True ([string](Get-Value $result.Ack @('eventId') '') -eq $result.EventId) 'ACK event ID does not match.'
        Assert-True ([int](Get-Value $result.Ack @('userId', 'participantId') 0) -eq $UserA) 'ACK participant does not match.'
        Assert-True ([int](Get-Value $result.Ack @('smartWatchId', 'watchId') 0) -eq $WatchA) 'ACK came from an unexpected watch.'
        Assert-True ([int](Get-Value $result.Ack @('androidId', 'phoneId') 0) -eq $PhoneA) 'ACK phone does not match.'
        Assert-True ([int](Get-Value $result.Ack @('mappingId', 'fbRangeID') 0) -eq $fixture.HeartRateMappingId) 'ACK mapping does not match.'
        Assert-True ([int](Get-Value $result.Ack @('pulses') 0) -eq 10) 'ACK haptic pulse count does not match the HeartRate pattern.'
        Assert-True ($result.DurationMs -le 5000) 'Watch ACK exceeded five seconds.'
        return $result
    }
    Invoke-ValidationTest 'watch.nonmatching_id' 'Nonmatching watch ID produces no ACK' 'watch' -RequiresWatch {
        $result = Send-WatchEvent $WatchB 82
        Assert-True ($null -eq $result.Ack) 'The connected watch acknowledged an event addressed to another watch.'
        return $result
    }
    Invoke-ValidationTest 'watch.ack_latency' 'Watch ACK latency meets the threshold' 'watch' -RequiresWatch {
        $latencies = @()
        $events = @()
        foreach ($value in @(70, 82, 95)) {
            $result = Send-WatchEvent $WatchA $value
            Assert-True ($null -ne $result.Ack) "No ACK was received for value $value."
            $latencies += $result.DurationMs
            $events += $result
        }
        $p95 = Get-Percentile $latencies 95
        Assert-True ($p95 -le 5000) "Watch ACK p95 was $([math]::Round($p95,2)) ms."
        return [pscustomobject]@{ P95Ms = $p95; Events = $events }
    }
    Add-Result -Id 'watch.two_watch_isolation' -Name 'Simultaneous two-watch isolation' -Category 'watch' `
        -RequiresWatch $true -Status SKIPPED -DurationMs 0 `
        -Message $(if ($WithWatch) { 'second_watch_unavailable' } else { 'watch_not_requested' })
}

function Get-GitMetadata {
    try {
        $commit = (& git rev-parse HEAD 2>$null).Trim()
        $dirty = -not [string]::IsNullOrWhiteSpace((& git status --porcelain 2>$null) -join '')
        return [pscustomobject]@{ Commit = $commit; Dirty = $dirty }
    } catch {
        return [pscustomobject]@{ Commit = ''; Dirty = $true }
    }
}

function ConvertTo-HtmlText {
    param($Value)
    return [System.Net.WebUtility]::HtmlEncode([string]$Value)
}

function Write-HtmlReport {
    param($Metadata, [string]$Path)

    $categoryRows = @($results | Group-Object Category | ForEach-Object {
        $group = @($_.Group)
        '<tr><td>{0}</td><td>{1}</td><td class="passed">{2}</td><td class="failed">{3}</td><td class="skipped">{4}</td></tr>' -f `
            (ConvertTo-HtmlText $_.Name), $group.Count,
            @($group | Where-Object Status -eq PASSED).Count,
            @($group | Where-Object Status -eq FAILED).Count,
            @($group | Where-Object Status -eq SKIPPED).Count
    }) -join [Environment]::NewLine

    $testRows = @($results | ForEach-Object {
        $statusClass = ([string]$_.Status).ToLowerInvariant()
        $artifact = if ([string]::IsNullOrWhiteSpace($_.Artifact)) {
            ''
        } else {
            '<a href="responses/{0}">evidence</a>' -f (ConvertTo-HtmlText ([IO.Path]::GetFileName($_.Artifact)))
        }
        '<tr><td>{0}</td><td>{1}</td><td>{2}</td><td>{3}</td><td>{4}</td><td class="{5}">{6}</td><td>{7}</td><td>{8}</td><td>{9}</td></tr>' -f `
            (ConvertTo-HtmlText $_.TestId), (ConvertTo-HtmlText $_.Name), (ConvertTo-HtmlText $_.Category),
            (ConvertTo-HtmlText $_.StartedAtUtc), (ConvertTo-HtmlText $_.EndedAtUtc),
            $statusClass, (ConvertTo-HtmlText $_.Status), (ConvertTo-HtmlText $_.DurationMs),
            (ConvertTo-HtmlText $_.Message), $artifact
    }) -join [Environment]::NewLine

    $hardwareSelection = if ($WithWatch) { 'Selected' } else { 'Not selected (use -WithWatch to include it)' }
    $overallClass = ([string]$Metadata.OverallStatus).ToLowerInvariant()
    $html = @"
<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Haptic Hub validation - $(ConvertTo-HtmlText $Metadata.RunId)</title>
<style>
body{font-family:Segoe UI,Arial,sans-serif;margin:0;background:#f4f6f8;color:#17202a}main{max-width:1200px;margin:auto;padding:32px}h1{margin-bottom:6px}.muted{color:#627080}.cards{display:flex;flex-wrap:wrap;gap:12px;margin:22px 0}.card{background:white;border-radius:8px;padding:15px 18px;min-width:135px;box-shadow:0 1px 4px #0002}.card strong{display:block;font-size:24px}.passed{color:#16763c;font-weight:600}.failed{color:#b42318;font-weight:600}.skipped{color:#9a6700;font-weight:600}.status{font-size:28px}table{width:100%;border-collapse:collapse;background:white;margin:14px 0 28px}th,td{text-align:left;padding:10px;border-bottom:1px solid #dde2e7;vertical-align:top}th{background:#e9eef3}tr:hover{background:#f8fafb}code{background:#e9eef3;padding:2px 5px;border-radius:3px}a{color:#075aa8}.meta{display:grid;grid-template-columns:max-content 1fr;gap:6px 16px;background:white;padding:16px;border-radius:8px}.meta dt{font-weight:600}.meta dd{margin:0;overflow-wrap:anywhere}@media(max-width:700px){main{padding:18px}.meta{grid-template-columns:1fr}table{font-size:13px;display:block;overflow-x:auto}}
</style>
</head>
<body><main>
<h1>Haptic Hub validation</h1>
<div class="status">$(ConvertTo-HtmlText $Metadata.PassPercent)% passed</div>
<p class="muted">Gate: <span class="$overallClass">$(ConvertTo-HtmlText $Metadata.OverallStatus)</span> - $(ConvertTo-HtmlText $Metadata.RunId)</p>
<div class="cards">
  <div class="card"><span>Pass rate</span><strong>$(ConvertTo-HtmlText $Metadata.PassPercent)%</strong><small>$(ConvertTo-HtmlText $Metadata.SelectedPassed) / $(ConvertTo-HtmlText $Metadata.EvaluatedSelectedTests) evaluated selected tests</small></div>
  <div class="card"><span>Progress</span><strong>$(ConvertTo-HtmlText $Metadata.CompletionPercent)%</strong><small>$(ConvertTo-HtmlText $Metadata.CompletedTests) / $(ConvertTo-HtmlText $Metadata.PlannedTests) selected tests</small></div>
  <div class="card"><span>Passed</span><strong class="passed">$(ConvertTo-HtmlText $Metadata.Passed)</strong></div>
  <div class="card"><span>Failed</span><strong class="failed">$(ConvertTo-HtmlText $Metadata.Failed)</strong></div>
  <div class="card"><span>Skipped</span><strong class="skipped">$(ConvertTo-HtmlText $Metadata.Skipped)</strong></div>
</div>
<h2>Run details</h2>
<dl class="meta">
<dt>Mode</dt><dd>$(ConvertTo-HtmlText $Metadata.Mode)</dd>
<dt>Started (UTC)</dt><dd>$(ConvertTo-HtmlText $Metadata.StartedAtUtc)</dd>
<dt>Duration</dt><dd>$(ConvertTo-HtmlText $Metadata.DurationSeconds) seconds</dd>
<dt>Operator</dt><dd>$(ConvertTo-HtmlText $Metadata.Operator)</dd>
<dt>Backend</dt><dd>$(ConvertTo-HtmlText $Metadata.BaseUrl)</dd>
<dt>Application commit</dt><dd><code>$(ConvertTo-HtmlText $Metadata.ApplicationCommit)</code></dd>
<dt>n8n workflow version</dt><dd>$(ConvertTo-HtmlText $Metadata.N8nWorkflowVersion)</dd>
<dt>Cleanup</dt><dd>$(ConvertTo-HtmlText $Metadata.CleanupStatus)</dd>
<dt>Gate status</dt><dd>$(ConvertTo-HtmlText $Metadata.OverallStatus)</dd>
<dt>Watch testing</dt><dd>$(ConvertTo-HtmlText $hardwareSelection) — $(ConvertTo-HtmlText $Metadata.HardwareStatement)</dd>
</dl>
<h2>Categories</h2>
<table><thead><tr><th>Category</th><th>Total</th><th>Passed</th><th>Failed</th><th>Skipped</th></tr></thead><tbody>
$categoryRows
</tbody></table>
<h2>Test results</h2>
<table><thead><tr><th>Test ID</th><th>Name</th><th>Category</th><th>Started (UTC)</th><th>Ended (UTC)</th><th>Status</th><th>Duration (ms)</th><th>Message</th><th>Artifact</th></tr></thead><tbody>
$testRows
</tbody></table>
<p class="muted">Response artifacts are sanitized before being written. Share the generated ZIP to include this report and its evidence links.</p>
</main></body></html>
"@
    Set-Content -Encoding UTF8 -Path $Path -Value $html
}

function Write-RunOutputs {
    param([string]$CleanupStatus)

    $endedAt = [DateTimeOffset]::UtcNow
    $passed = @($results | Where-Object Status -eq PASSED).Count
    $failed = @($results | Where-Object Status -eq FAILED).Count
    $skipped = @($results | Where-Object Status -eq SKIPPED).Count
    $hardwareSkipped = @($results | Where-Object { $_.Status -eq 'SKIPPED' -and $_.RequiresWatch }).Count
    $watchPreflightFailed = @($results | Where-Object { $_.TestId -eq 'watch.preflight' -and $_.Status -eq 'FAILED' }).Count -gt 0
    $completedWatchTests = @($results | Where-Object { $testIdsByPhase.watch -contains $_.TestId }).Count
    $git = Get-GitMetadata
    $selectedResults = @($results | Where-Object { $selectedTestIds -contains $_.TestId })
    $selectedPassed = @($selectedResults | Where-Object Status -eq PASSED).Count
    $selectedFailed = @($selectedResults | Where-Object Status -eq FAILED).Count
    $selectedSkipped = @($selectedResults | Where-Object Status -eq SKIPPED).Count
    $evaluatedSelectedTests = $selectedPassed + $selectedFailed
    $passPercent = if ($evaluatedSelectedTests -eq 0) { 0 } else { [math]::Round(($selectedPassed / $evaluatedSelectedTests) * 100, 1) }
    $completionPercent = if ($plannedTests -eq 0) { 100 } else { [int][math]::Floor(($completedTests / $plannedTests) * 100) }
    $overallStatus = if ($failed -gt 0 -or $CleanupStatus -eq 'failed') { 'FAILED' } else { 'PASSED' }
    $message = if (-not $WithWatch) {
        'Physical watch delivery was not evaluated in this run.'
    } elseif ($watchPreflightFailed) {
        'Watch validation was requested, but preflight failed before physical delivery tests ran.'
    } elseif ($completedWatchTests -lt $testIdsByPhase.watch.Count) {
        'Watch validation was requested, but the run ended before all physical delivery tests ran.'
    } elseif ($hardwareSkipped -gt 0) {
        'Single-watch delivery was evaluated; simultaneous two-watch isolation was not evaluated.'
    } else {
        'All selected watch tests were evaluated.'
    }

    $metadata = [pscustomobject][ordered]@{
        RunId = $runId
        Mode = $Mode
        WithWatch = [bool]$WithWatch
        BaseUrl = $BaseUrl
        StartedAtUtc = $startedAt.ToString('o')
        EndedAtUtc = $endedAt.ToString('o')
        DurationSeconds = [math]::Round(($endedAt - $startedAt).TotalSeconds, 2)
        Operator = $Operator
        ApplicationCommit = $git.Commit
        ApplicationWorktreeDirty = $git.Dirty
        N8nWorkflowVersion = $N8nWorkflowVersion
        TestUsers = @($UserA, $UserB)
        TestPhones = @($PhoneA, $PhoneB)
        TestWatches = @($WatchA, $WatchB)
        CleanupStatus = $CleanupStatus
        OverallStatus = $overallStatus
        PlannedTests = $plannedTests
        CompletedTests = $completedTests
        CompletionPercent = $completionPercent
        SelectedPassed = $selectedPassed
        SelectedFailed = $selectedFailed
        SelectedSkipped = $selectedSkipped
        EvaluatedSelectedTests = $evaluatedSelectedTests
        PassPercent = $passPercent
        Passed = $passed
        Failed = $failed
        Skipped = $skipped
        HardwareSkipped = $hardwareSkipped
        HardwareStatement = $message
        Thresholds = [pscustomobject]@{
            DirectApiP95Ms = 2000
            WatchAckP95Ms = 5000
            SoakRequests = $SoakRequests
        }
        ReportFile = 'report.html'
        ShareableArchive = "$runId.zip"
    }

    $results | ConvertTo-Json -Depth 10 | Set-Content -Encoding UTF8 -Path (Join-Path $runDirectory 'results.json')
    $results | Export-Csv -NoTypeInformation -Encoding UTF8 -Path (Join-Path $runDirectory 'summary.csv')
    $metadata | ConvertTo-Json -Depth 10 | Set-Content -Encoding UTF8 -Path (Join-Path $runDirectory 'run-metadata.json')
    $reportPath = Join-Path $runDirectory 'report.html'
    Write-HtmlReport $metadata $reportPath
    $archivePath = Join-Path $OutputRoot "$runId.zip"
    Compress-Archive -LiteralPath $runDirectory -DestinationPath $archivePath -CompressionLevel Optimal

    Write-Progress -Activity "Haptic Hub validation ($Mode)" -Completed

    Write-Host ''
    Write-Host ('Run: {0}' -f $runId)
    Write-Host ('Progress: {0}% ({1}/{2} selected tests)' -f $completionPercent, $completedTests, $plannedTests)
    Write-Host ('Pass rate: {0}% ({1}/{2} evaluated selected tests)' -f $passPercent, $selectedPassed, $evaluatedSelectedTests)
    Write-Host ('Passed: {0}  Failed: {1}  Skipped: {2}' -f $passed, $failed, $skipped)
    Write-Host $message
    Write-Host ('Evidence: {0}' -f $runDirectory)
    Write-Host ('Report: {0}' -f $reportPath)
    Write-Host ('Share: {0}' -f $archivePath)
    return $failed
}

$cleanupStatus = 'not_required'
$fatalError = $null

try {
    if ($Mode -eq 'formal') {
        Assert-True (-not [string]::IsNullOrWhiteSpace($N8nWorkflowVersion)) '-N8nWorkflowVersion is required in formal mode.'
        Assert-True (-not [string]::IsNullOrWhiteSpace($Operator)) '-Operator is required in formal mode.'
        $git = Get-GitMetadata
        Assert-True (-not $git.Dirty) 'Formal mode requires a clean Git worktree.'
    }

    if ($WithWatch) {
        Invoke-ValidationTest 'watch.preflight' 'Phone, watch, apps and ACK endpoint are ready' 'watch' -RequiresWatch {
            return Invoke-WatchPreflight
        }
        if (@($results | Where-Object { $_.TestId -eq 'watch.preflight' -and $_.Status -eq 'FAILED' }).Count -gt 0) {
            throw 'Watch preflight failed; no state-changing tests were started.'
        }
    }

    Invoke-SmokeTests

    if ($Mode -in @('core', 'formal', 'all')) { Invoke-CoreTests }
    if ($Mode -in @('external', 'all')) { Invoke-ExternalTests }
    if ($Mode -in @('ai', 'all')) { Invoke-AiTests }
    if ($Mode -in @('soak', 'formal', 'all')) { Invoke-SoakTests }
    Invoke-WatchTests
} catch {
    $fatalError = $_
    Add-Result -Id 'run.fatal' -Name 'Validation run completed without a fatal error' -Category 'runner' `
        -RequiresWatch $false -Status FAILED -DurationMs 0 -Message $_.Exception.Message `
        -Artifact (Save-TestArtifact 'run.fatal' ([pscustomobject]@{ Error = $_.Exception.Message; ScriptStackTrace = $_.ScriptStackTrace }))
} finally {
    if ($fixture.Ready) {
        try {
            $cleanup = Restore-Fixture
            $cleanupStatus = 'passed'
            Add-Result -Id 'fixture.cleanup' -Name 'Validation fixture state was restored' -Category 'fixture' `
                -RequiresWatch $false -Status PASSED -DurationMs 0 -Message 'ok' `
                -Artifact (Save-TestArtifact 'fixture.cleanup' $cleanup)
        } catch {
            $cleanupStatus = 'failed'
            Add-Result -Id 'fixture.cleanup' -Name 'Validation fixture state was restored' -Category 'fixture' `
                -RequiresWatch $false -Status FAILED -DurationMs 0 -Message $_.Exception.Message `
                -Artifact (Save-TestArtifact 'fixture.cleanup' ([pscustomobject]@{ Error = $_.Exception.Message }))
        }
    }
}

$failedCount = Write-RunOutputs $cleanupStatus
if ($failedCount -gt 0 -or $null -ne $fatalError) { exit 1 }
exit 0
