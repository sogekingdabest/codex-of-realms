[CmdletBinding()]
param(
    [string[]]$Models = @(
        "qwen3:4b",
        "qwen3.5:4b",
        "gemma4:e2b-it-qat",
        "phi4-mini:3.8b"
    ),
    [ValidateRange(1, 10)]
    [int]$Repetitions = 1,
    [string]$OllamaBaseUrl = "http://localhost:11434"
)

$ErrorActionPreference = "Stop"
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$backendDirectory = Join-Path $projectRoot "backend"
$resultDirectory = Join-Path $projectRoot "demo\evaluation\results"
$comparisonRows = @()
$failedModels = @()
$managedEnvironment = @(
    "AI_CHAT_MODEL",
    "OLLAMA_BASE_URL",
    "LOCAL_MODEL_EVALUATION_REPETITIONS",
    "LOCAL_MODEL_EVALUATION_OUTPUT"
)
$previousEnvironment = @{}

foreach ($name in $managedEnvironment) {
    $previousEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, "Process")
}

try {
    $tagsUri = $OllamaBaseUrl.TrimEnd('/') + "/api/tags"
    try {
        $tags = Invoke-RestMethod -Uri $tagsUri -Method Get -TimeoutSec 10
    }
    catch {
        throw "No se puede conectar con Ollama en $OllamaBaseUrl. Inicia Ollama y vuelve a ejecutar el script. Detalle: $($_.Exception.Message)"
    }

    $installedModels = @($tags.models | ForEach-Object { $_.name })
    $missingModels = @($Models | Where-Object { $_ -notin $installedModels })
    if ($missingModels.Count -gt 0) {
        $pullCommands = $missingModels | ForEach-Object { "ollama pull $_" }
        throw "Faltan modelos. El script no descarga pesos automáticamente:`n$($pullCommands -join [Environment]::NewLine)"
    }

    New-Item -ItemType Directory -Force -Path $resultDirectory | Out-Null
    $env:OLLAMA_BASE_URL = $OllamaBaseUrl
    $env:LOCAL_MODEL_EVALUATION_REPETITIONS = $Repetitions.ToString()
    $env:LOCAL_MODEL_EVALUATION_OUTPUT = $resultDirectory

    foreach ($model in $Models) {
        $env:AI_CHAT_MODEL = $model
        $startedAt = Get-Date
        Write-Host "Evaluando $model ($Repetitions repetición/es)..." -ForegroundColor Cyan

        Push-Location $backendDirectory
        try {
            & .\mvnw.cmd --batch-mode --no-transfer-progress -Plocal-model-evaluation verify
            $mavenExitCode = $LASTEXITCODE
        }
        finally {
            Pop-Location
        }

        $safeModel = $model.ToLowerInvariant() -replace '[^a-z0-9._-]+', '-'
        $reportFile = Get-ChildItem -Path $resultDirectory -Filter "$safeModel-*.json" |
            Where-Object { $_.LastWriteTime -ge $startedAt.AddSeconds(-2) } |
            Sort-Object LastWriteTime -Descending |
            Select-Object -First 1

        if ($null -eq $reportFile) {
            $failedModels += $model
            Write-Warning "No se generó un informe JSON para $model."
            continue
        }

        $report = Get-Content -Raw $reportFile.FullName | ConvertFrom-Json
        $comparisonRows += [pscustomobject]@{
            Model = $model
            Eligible = [bool]$report.summary.eligible
            OutcomeAccuracy = [double]$report.summary.outcomeAccuracy
            FactCoverage = [double]$report.summary.expectedFactCoverage
            StructuredOutput = [double]$report.summary.structuredOutputRate
            CitationSuccess = [double]$report.summary.citationSuccessRate
            SecurityFailures = [int]$report.summary.securityFailures
            MedianLatencyMs = [long]$report.summary.medianLatencyMillis
            MeanTokensPerSecond = [double]$report.summary.meanGenerationTokensPerSecond
            Report = $reportFile.Name
        }

        if ($mavenExitCode -ne 0 -or -not [bool]$report.summary.eligible) {
            $failedModels += $model
        }
    }

    $comparisonTimestamp = Get-Date -Format "yyyyMMdd-HHmmss"
    $comparisonPath = Join-Path $resultDirectory "comparison-$comparisonTimestamp.md"
    $lines = @(
        "# Comparación local de modelos",
        "",
        "Generada: $(Get-Date -Format o)",
        "",
        "| Modelo | Elegible | Outcome | Hechos | JSON | Citas | Fallos de seguridad | Mediana | tok/s | Informe |",
        "|---|---:|---:|---:|---:|---:|---:|---:|---:|---|"
    )
    foreach ($row in $comparisonRows) {
        $lines += "| $($row.Model) | $($row.Eligible) | $($row.OutcomeAccuracy.ToString('0.000')) | $($row.FactCoverage.ToString('0.000')) | $($row.StructuredOutput.ToString('0.000')) | $($row.CitationSuccess.ToString('0.000')) | $($row.SecurityFailures) | $($row.MedianLatencyMs) ms | $($row.MeanTokensPerSecond.ToString('0.00')) | $($row.Report) |"
    }
    [IO.File]::WriteAllText($comparisonPath, ($lines -join [Environment]::NewLine) + [Environment]::NewLine)
    Write-Host "Comparación guardada en $comparisonPath" -ForegroundColor Green

    if ($failedModels.Count -gt 0) {
        throw "No superaron todos los umbrales de calidad y seguridad: $($failedModels -join ', ')"
    }
}
finally {
    foreach ($name in $managedEnvironment) {
        [Environment]::SetEnvironmentVariable($name, $previousEnvironment[$name], "Process")
    }
}
