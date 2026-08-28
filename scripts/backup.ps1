[CmdletBinding()]
param(
    [string]$DestinationRoot = "backups"
)

$ErrorActionPreference = "Stop"
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$destination = Join-Path (Join-Path $repositoryRoot $DestinationRoot) $timestamp
$databaseArchive = Join-Path $destination "codex-of-realms.dump"
$sourcesDirectory = Join-Path $destination "sources"

function Invoke-Checked {
    param([string]$Command, [string[]]$Arguments)
    & $Command @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Command failed with exit code ${LASTEXITCODE}: $Command $($Arguments -join ' ')"
    }
}

Push-Location $repositoryRoot
try {
    New-Item -ItemType Directory -Force -Path $destination | Out-Null
    New-Item -ItemType Directory -Force -Path $sourcesDirectory | Out-Null

    $running = docker compose ps --status running --services
    if ($LASTEXITCODE -ne 0 -or $running -notcontains "postgres" -or $running -notcontains "app") {
        throw "Start the stack before creating a backup: docker compose up -d"
    }

    Write-Host "[1/3] Exporting PostgreSQL, including the application and Keycloak schemas..."
    Invoke-Checked "docker" @(
        "compose", "exec", "-T", "postgres",
        "pg_dump", "--username=codex", "--dbname=codex_of_realms",
        "--format=custom", "--file=/tmp/codex-of-realms.dump"
    )
    Invoke-Checked "docker" @(
        "compose", "cp", "postgres:/tmp/codex-of-realms.dump", $databaseArchive
    )
    Invoke-Checked "docker" @(
        "compose", "exec", "-T", "postgres", "rm", "-f", "/tmp/codex-of-realms.dump"
    )

    Write-Host "[2/3] Copying immutable source files..."
    Invoke-Checked "docker" @("compose", "cp", "app:/app/data/sources/.", $sourcesDirectory)

    Write-Host "[3/3] Writing the backup manifest..."
    $gitCommit = & git -c "safe.directory=$repositoryRoot" rev-parse HEAD
    if ($LASTEXITCODE -ne 0) {
        throw "Could not resolve the Git commit for the backup manifest."
    }
    @{
        createdAt = (Get-Date).ToUniversalTime().ToString("o")
        gitCommit = $gitCommit.Trim()
        database = "codex-of-realms.dump"
        sources = "sources"
    } | ConvertTo-Json | Set-Content -Path (Join-Path $destination "manifest.json") -Encoding utf8

    Write-Host "Backup created at $destination"
}
finally {
    Pop-Location
}
