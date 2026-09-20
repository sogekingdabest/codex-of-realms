[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$BackupDirectory,
    [switch]$KeepEnvironment,
    [switch]$ResumePendingJobs
)

$ErrorActionPreference = "Stop"
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$resolvedBackup = (Resolve-Path -LiteralPath $BackupDirectory).Path
$databaseArchive = Join-Path $resolvedBackup "codex-of-realms.dump"
$sourcesDirectory = Join-Path $resolvedBackup "sources"
$manifestPath = Join-Path $resolvedBackup "manifest.json"
$timestamp = Get-Date -Format "yyyyMMddHHmmss"
$projectName = "codex-of-realms-restore-check-$PID-$timestamp".ToLowerInvariant()
$composeArguments = @(
    "compose", "--project-name", $projectName,
    "--file", "compose.yaml",
    "--file", "compose.restore-check.yaml"
)
$created = $false

if ($projectName -notmatch '^codex-of-realms-restore-check-[0-9]+-[0-9]{14}$') {
    throw "Unsafe generated Compose project name: $projectName"
}
if (-not (Test-Path -LiteralPath $databaseArchive -PathType Leaf)) {
    throw "Database archive not found: $databaseArchive"
}
if (-not (Test-Path -LiteralPath $sourcesDirectory -PathType Container)) {
    throw "Source directory not found: $sourcesDirectory"
}
if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) {
    throw "Backup manifest not found: $manifestPath"
}

$manifest = Get-Content -Raw -LiteralPath $manifestPath | ConvertFrom-Json
if ($null -ne $manifest.databaseSha256) {
    $actualArchiveSha256 = (Get-FileHash -LiteralPath $databaseArchive -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actualArchiveSha256 -ne $manifest.databaseSha256) {
        throw "Database archive checksum does not match the backup manifest."
    }
}

function Invoke-DockerCompose {
    param([string[]]$Arguments)
    & docker @composeArguments @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Docker Compose failed with exit code ${LASTEXITCODE}: $($Arguments -join ' ')"
    }
}

function Invoke-DockerComposeCapture {
    param([string[]]$Arguments)
    $output = & docker @composeArguments @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Docker Compose failed with exit code ${LASTEXITCODE}: $($Arguments -join ' ')"
    }
    return ($output | Out-String).Trim()
}

Push-Location $repositoryRoot
try {
    Write-Host "[1/5] Validating the isolated Compose topology..."
    Invoke-DockerCompose @("config", "--quiet")

    Write-Host "[2/5] Starting an isolated PostgreSQL volume..."
    $created = $true
    Invoke-DockerCompose @("up", "-d", "--wait", "postgres")
    Invoke-DockerCompose @("run", "--rm", "--no-deps", "keycloak-db-init")

    Write-Host "[3/5] Restoring the database archive..."
    Invoke-DockerCompose @("cp", $databaseArchive, "postgres:/tmp/codex-of-realms.dump")
    Invoke-DockerCompose @(
        "exec", "-T", "postgres",
        "pg_restore", "--username=codex", "--dbname=codex_of_realms",
        "--clean", "--if-exists", "--no-owner", "/tmp/codex-of-realms.dump"
    )
    Invoke-DockerCompose @("exec", "-T", "postgres", "rm", "-f", "/tmp/codex-of-realms.dump")

    $migrationCount = Invoke-DockerComposeCapture @(
        "exec", "-T", "postgres", "psql", "--username=codex", "--dbname=codex_of_realms",
        "--tuples-only", "--no-align", "--command=SELECT count(*) FROM flyway_schema_history WHERE success"
    )
    if ([int]$migrationCount -lt 1) {
        throw "The restored archive contains no successful Flyway migrations."
    }

    Write-Host "[4/5] Restoring and comparing immutable source files..."
    $expectedSourceCount = @(Get-ChildItem -LiteralPath $sourcesDirectory -Recurse -File).Count
    if ($null -ne $manifest.sourceFileCount -and [int]$manifest.sourceFileCount -ne $expectedSourceCount) {
        throw "The backup source count does not match its manifest."
    }
    Invoke-DockerCompose @("create", "source-verifier")
    Invoke-DockerCompose @("cp", "$sourcesDirectory/.", "source-verifier:/restore/sources")
    $actualSourceCount = Invoke-DockerComposeCapture @(
        "run", "--rm", "--no-deps", "source-verifier", "sh", "-c",
        "find /restore/sources -type f -print | wc -l"
    )
    if ([int]$actualSourceCount -ne $expectedSourceCount) {
        throw "Source restore mismatch: expected $expectedSourceCount file(s), found $actualSourceCount."
    }
    if ($manifest.PSObject.Properties.Name -contains "sourceFiles") {
        $restoredHashesOutput = Invoke-DockerComposeCapture @(
            "run", "--rm", "--no-deps", "source-verifier", "sh", "-c",
            "find /restore/sources -type f -exec sha256sum {} +"
        )
        $restoredHashes = @{}
        foreach ($line in @($restoredHashesOutput -split "`r?`n" | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })) {
            if ($line -notmatch '^([a-f0-9]{64})\s+(.+)$') {
                throw "Could not parse a restored source checksum: $line"
            }
            $relativePath = $Matches[2].Replace('/restore/sources/', '')
            $restoredHashes[$relativePath] = $Matches[1]
        }
        foreach ($sourceFile in @($manifest.sourceFiles)) {
            if ($restoredHashes[$sourceFile.path] -ne $sourceFile.sha256) {
                throw "Restored source checksum mismatch: $($sourceFile.path)"
            }
        }
    }

    $restoredJobs = @()
    if ($manifest.PSObject.Properties.Name -contains "pendingSourceJobs") {
        $jobsTable = Invoke-DockerComposeCapture @(
            "exec", "-T", "postgres", "psql", "--username=codex", "--dbname=codex_of_realms",
            "--tuples-only", "--no-align", "--command=SELECT to_regclass('public.source_job')"
        )
        $pendingJobsJson = '[]'
        if ($jobsTable -eq 'source_job') { $pendingJobsJson = Invoke-DockerComposeCapture @(
            "exec", "-T", "postgres", "psql", "--username=codex", "--dbname=codex_of_realms",
            "--tuples-only", "--no-align", "--command=SELECT coalesce(json_agg(json_build_object('id',j.id,'versionId',j.version_id,'state',j.state,'storageKey',v.storage_key,'checksum',v.checksum_sha256)), '[]'::json) FROM source_job j JOIN document_version v ON v.id=j.version_id WHERE j.state IN ('UPLOADING','QUEUED','RUNNING')"
        ) }
        $restoredJobs = @($pendingJobsJson | ConvertFrom-Json)
        if ($restoredJobs.Count -ne @($manifest.pendingSourceJobs).Count) { throw "Pending jobs changed during restore." }
        foreach ($job in $restoredJobs) {
            $expected = @($manifest.pendingSourceJobs | Where-Object { $_.id -eq $job.id -and $_.versionId -eq $job.versionId -and $_.state -eq $job.state })
            if ($expected.Count -ne 1) { throw "A pending operation was not restored exactly." }
            if ($job.state -ne "UPLOADING" -and $restoredHashes[$job.storageKey] -ne $job.checksum) {
                throw "A queued or running operation is missing its intact original file."
            }
        }
        Write-Host "Restored $($restoredJobs.Count) pending source operations with their execution state and original files."
    }

    Write-Host "[5/5] Booting Keycloak against the restored schema..."
    Invoke-DockerCompose @("up", "-d", "--wait", "keycloak")
    $realmCount = Invoke-DockerComposeCapture @(
        "exec", "-T", "postgres", "psql", "--username=codex", "--dbname=codex_of_realms",
        "--tuples-only", "--no-align", "--command=SELECT count(*) FROM keycloak.realm WHERE name = 'codex-of-realms'"
    )
    if ([int]$realmCount -ne 1) {
        throw "The restored Keycloak schema does not contain exactly one codex-of-realms realm."
    }

    if ($ResumePendingJobs -and $restoredJobs.Count -gt 0) {
        Write-Host "Resuming restored operations with the configured embedding service..."
        Invoke-DockerCompose @("up", "-d", "--build", "--no-deps", "--wait", "app")
        $pendingIds = @($restoredJobs | ForEach-Object { "'" + ([Guid]$_.id).ToString() + "'" }) -join ','
        $deadline = (Get-Date).AddMinutes(10)
        do {
            $statesJson = Invoke-DockerComposeCapture @(
                "exec", "-T", "postgres", "psql", "--username=codex", "--dbname=codex_of_realms",
                "--tuples-only", "--no-align", "--command=SELECT json_agg(json_build_object('state',j.state,'ready',v.active AND v.processing_status='READY')) FROM source_job j JOIN document_version v ON v.id=j.version_id WHERE j.id IN ($pendingIds)"
            )
            $states = @($statesJson | ConvertFrom-Json)
            if (@($states | Where-Object { $_.state -in @('FAILED','CANCELLED') }).Count -gt 0) {
                throw "A restored operation could not publish. Check compatible processing configuration, original files and requester permissions."
            }
            $unfinished = @($states | Where-Object { $_.state -ne 'SUCCEEDED' -or -not $_.ready }).Count
            if ($unfinished -eq 0) { break }
            Start-Sleep -Seconds 5
        } while ((Get-Date) -lt $deadline)
        if ($unfinished -ne 0) { throw "Restored operations did not complete within ten minutes." }
        Write-Host "All $($states.Count) restored operations completed and published their versions."
    }

    Write-Host "Restore verification passed in isolated project $projectName" -ForegroundColor Green
    Write-Host "Backup commit: $($manifest.gitCommit); migrations: $migrationCount; source files: $actualSourceCount"
}
finally {
    Pop-Location
    if ($created -and -not $KeepEnvironment) {
        Push-Location $repositoryRoot
        try {
            Write-Host "Removing isolated verification volumes..."
            Invoke-DockerCompose @("down", "--volumes", "--remove-orphans")
        }
        finally {
            Pop-Location
        }
    }
    elseif ($created) {
        Write-Warning "Isolated project retained for inspection: $projectName"
    }
}
