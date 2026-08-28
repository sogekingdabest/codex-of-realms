[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$BackupDirectory,
    [switch]$ConfirmRestore
)

$ErrorActionPreference = "Stop"
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$resolvedBackup = (Resolve-Path -LiteralPath $BackupDirectory).Path
$databaseArchive = Join-Path $resolvedBackup "codex-of-realms.dump"
$sourcesDirectory = Join-Path $resolvedBackup "sources"

if (-not $ConfirmRestore) {
    throw "Restore replaces the current database and source volume. Re-run with -ConfirmRestore after verifying the backup path."
}
if (-not (Test-Path -LiteralPath $databaseArchive -PathType Leaf)) {
    throw "Database archive not found: $databaseArchive"
}
if (-not (Test-Path -LiteralPath $sourcesDirectory -PathType Container)) {
    throw "Source directory not found: $sourcesDirectory"
}

function Invoke-Checked {
    param([string]$Command, [string[]]$Arguments)
    & $Command @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Command failed with exit code ${LASTEXITCODE}: $Command $($Arguments -join ' ')"
    }
}

Push-Location $repositoryRoot
try {
    Write-Host "[1/4] Preparing PostgreSQL and stopping database clients..."
    Invoke-Checked "docker" @("compose", "up", "-d", "postgres", "keycloak-db-init")
    & docker compose stop app keycloak 2>$null

    Write-Host "[2/4] Restoring PostgreSQL..."
    Invoke-Checked "docker" @("compose", "cp", $databaseArchive, "postgres:/tmp/codex-of-realms.dump")
    Invoke-Checked "docker" @(
        "compose", "exec", "-T", "postgres",
        "pg_restore", "--username=codex", "--dbname=codex_of_realms",
        "--clean", "--if-exists", "--no-owner", "/tmp/codex-of-realms.dump"
    )
    Invoke-Checked "docker" @(
        "compose", "exec", "-T", "postgres", "rm", "-f", "/tmp/codex-of-realms.dump"
    )

    Write-Host "[3/4] Restoring source files..."
    Invoke-Checked "docker" @("compose", "create", "app")
    Invoke-Checked "docker" @(
        "compose", "run", "--rm", "--no-deps", "--entrypoint", "sh", "app",
        "-c", "find /app/data/sources -mindepth 1 -maxdepth 1 -exec rm -rf -- {} +"
    )
    Invoke-Checked "docker" @("compose", "cp", "$sourcesDirectory/.", "app:/app/data/sources")

    Write-Host "[4/4] Starting the restored stack..."
    Invoke-Checked "docker" @("compose", "up", "-d", "--wait")
    Write-Host "Restore completed from $resolvedBackup"
}
finally {
    Pop-Location
}
