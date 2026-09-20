# Self-contained contract test: no Docker process or container is started.
$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$global:codexBackupContractEvents = [System.Collections.Generic.List[string]]::new()
$global:codexBackupContractRunning = $true
$global:codexBackupContractFailCopy = $false
$global:codexBackupContractLegacy = $false
function docker {
    $arguments = @($args)
    $global:LASTEXITCODE = 0
    $operation = $arguments -join ' '
    $global:codexBackupContractEvents.Add($operation)
    if ($operation -like 'compose ps*') {
        'postgres'
        if ($global:codexBackupContractRunning) { 'app' }
    }
    elseif ($operation -like 'compose cp postgres:*') {
        if ($global:codexBackupContractFailCopy) { $global:LASTEXITCODE = 1; return }
        Set-Content -LiteralPath $arguments[-1] -Value 'test database archive'
    }
    elseif ($operation -like '*to_regclass*') { if (-not $global:codexBackupContractLegacy) { 'source_job' } }
    elseif ($operation -like '*json_agg*') {
        if ($global:codexBackupContractLegacy) { throw 'Legacy databases have no source_job table' }
        '[{"id":"pending-job","versionId":"pending-version","state":"QUEUED"}]'
    }
}
try {
    foreach ($scenario in @('running', 'stopped', 'copy-failure', 'legacy-schema')) {
        $global:codexBackupContractEvents.Clear()
        $global:codexBackupContractRunning = $scenario -ne 'stopped'
        $global:codexBackupContractFailCopy = $scenario -eq 'copy-failure'
        $global:codexBackupContractLegacy = $scenario -eq 'legacy-schema'
        $destination = 'backend/target/backup-contract-' + [Guid]::NewGuid().ToString()
        $failed = $false
        try { & (Join-Path $repositoryRoot 'scripts/backup.ps1') -DestinationRoot $destination }
        catch { $failed = $true; if (-not $global:codexBackupContractFailCopy) { throw } }
        if ($failed -ne $global:codexBackupContractFailCopy) { throw 'Unexpected backup outcome' }
        if (-not $failed) {
            $manifestPath = Get-ChildItem -Path (Join-Path $repositoryRoot $destination) -Filter manifest.json -Recurse | Select-Object -First 1 -ExpandProperty FullName
            $manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
            $expectedJobs = if ($global:codexBackupContractLegacy) { 0 } else { 1 }
            if (@($manifest.pendingSourceJobs).Count -ne $expectedJobs) { throw 'Pending jobs were not preserved in the manifest' }
        }
        $stopCalls = @($global:codexBackupContractEvents | Where-Object { $_ -like 'compose stop*' })
        $startCalls = @($global:codexBackupContractEvents | Where-Object { $_ -eq 'compose start app' })
        $expected = if ($global:codexBackupContractRunning) { 1 } else { 0 }
        if ($stopCalls.Count -ne $expected -or $startCalls.Count -ne $expected) { throw "Backend state was not restored in $scenario" }
        if ($global:codexBackupContractRunning -and $global:codexBackupContractEvents[-1] -ne 'compose start app') { throw 'Backend restart must run in finally' }
        Write-Host "PASS backup contract: $scenario"
    }
} finally { Remove-Item Function:docker }
