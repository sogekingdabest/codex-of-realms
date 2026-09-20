[CmdletBinding()]
param(
    [string]$BackupDirectory,
    [switch]$WithLiveModel,
    [string[]]$Models = @("qwen3.5:4b"),
    [string]$OllamaBaseUrl = "http://localhost:11434",
    [ValidateRange(1, 10)]
    [int]$Repetitions = 3
)

$ErrorActionPreference = "Stop"
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$backendDirectory = Join-Path $repositoryRoot "backend"
$frontendDirectory = Join-Path $repositoryRoot "frontend"
$resultDirectory = Join-Path $repositoryRoot "demo\evaluation\results"
$startedAt = Get-Date

function Invoke-Checked {
    param(
        [string]$Command,
        [string[]]$Arguments,
        [string]$WorkingDirectory
    )

    Push-Location $WorkingDirectory
    try {
        & $Command @Arguments
        if ($LASTEXITCODE -ne 0) {
            throw "Command failed with exit code ${LASTEXITCODE}: $Command $($Arguments -join ' ')"
        }
    }
    finally {
        Pop-Location
    }
}

Write-Host "[1/6] Validating normal and restore-check Compose topologies..." -ForegroundColor Cyan
Invoke-Checked "docker" @("compose", "config", "--quiet") $repositoryRoot
Invoke-Checked "docker" @(
    "compose", "--file", "compose.yaml", "--file", "compose.restore-check.yaml", "config", "--quiet"
) $repositoryRoot

Write-Host "[2/6] Running the deterministic Java acceptance suite..." -ForegroundColor Cyan
Invoke-Checked ".\mvnw.cmd" @("--batch-mode", "--no-transfer-progress", "verify") $backendDirectory

Write-Host "[3/6] Rebuilding and verifying the browser client..." -ForegroundColor Cyan
Invoke-Checked "npm" @("ci") $frontendDirectory
Invoke-Checked "npm" @("run", "verify") $frontendDirectory

Write-Host "[4/6] Checking the running product topology..." -ForegroundColor Cyan
$runningServices = & docker compose --project-directory $repositoryRoot ps --status running --services
if ($LASTEXITCODE -ne 0) {
    throw "Could not inspect the running Compose stack."
}
$requiredServices = @("postgres", "keycloak", "mailpit", "ollama", "app", "web")
$missingServices = @($requiredServices | Where-Object { $_ -notin $runningServices })
if ($missingServices.Count -gt 0) {
    throw "Start the complete stack before M8.2 acceptance. Missing: $($missingServices -join ', ')"
}

Write-Host "[5/6] Creating or selecting a backup and restoring it in isolation..." -ForegroundColor Cyan
$selectedBackup = $BackupDirectory
if ([string]::IsNullOrWhiteSpace($selectedBackup)) {
    $acceptanceBackupRoot = "backups\m8.2"
    & (Join-Path $PSScriptRoot "backup.ps1") -DestinationRoot $acceptanceBackupRoot
    $backupRootPath = Join-Path $repositoryRoot $acceptanceBackupRoot
    $selectedBackup = Get-ChildItem -LiteralPath $backupRootPath -Directory |
        Where-Object { $_.LastWriteTime -ge $startedAt.AddSeconds(-2) } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1 -ExpandProperty FullName
    if ([string]::IsNullOrWhiteSpace($selectedBackup)) {
        throw "The acceptance backup could not be identified under $backupRootPath."
    }
}
& (Join-Path $PSScriptRoot "verify-restore.ps1") -BackupDirectory $selectedBackup

Write-Host "[6/6] Checking the opt-in live-model gate..." -ForegroundColor Cyan
$modelStatus = "SKIPPED (requires -WithLiveModel and installed Ollama weights)"
if ($WithLiveModel) {
    & (Join-Path $PSScriptRoot "evaluate-local-models.ps1") -Stage Final -Models $Models `
        -Repetitions $Repetitions -OllamaBaseUrl $OllamaBaseUrl
    $modelStatus = "PASSED ($($Models -join ', '), $Repetitions repetitions, $OllamaBaseUrl)"
}
else {
    Write-Host "Live-model evaluation skipped; deterministic acceptance remains complete." -ForegroundColor Yellow
}

New-Item -ItemType Directory -Force -Path $resultDirectory | Out-Null
$finishedAt = Get-Date
$reportPath = Join-Path $resultDirectory "m8.2-acceptance-$($finishedAt.ToString('yyyyMMdd-HHmmss')).md"
$report = @(
    "# M8.2 acceptance result",
    "",
    "- Started: $($startedAt.ToUniversalTime().ToString('o'))",
    "- Finished: $($finishedAt.ToUniversalTime().ToString('o'))",
    "- Git commit: $((& git -c "safe.directory=$repositoryRoot" -C $repositoryRoot rev-parse HEAD).Trim())",
    "- Backend deterministic suite: PASSED",
    "- Frontend lint, tests, coverage thresholds, LCOV, and build (npm run verify): PASSED",
    "- Compose product topology: PASSED",
    "- Isolated restore: PASSED",
    "- Live-model evaluation: $modelStatus",
    "- Backup: $selectedBackup"
)
[IO.File]::WriteAllText($reportPath, ($report -join [Environment]::NewLine) + [Environment]::NewLine)

Write-Host "M8.2 deterministic acceptance passed." -ForegroundColor Green
Write-Host "Report: $reportPath"
