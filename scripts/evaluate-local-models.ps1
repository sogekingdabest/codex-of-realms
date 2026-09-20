[CmdletBinding()]
param(
    [ValidateSet("Screening", "Final")]
    [string]$Stage = "Screening",
    [string[]]$Models = @(
        "qwen3:4b",
        "qwen3.5:4b",
        "gemma4:e2b-it-qat",
        "granite4.2:3b-q4_K_M",
        "ministral-3:3b-instruct-2512-q4_K_M",
        "nemotron-3-nano:4b",
        "phi4-mini:3.8b-q4_K_M",
        "LiquidAI/lfm2.5-1.2b-instruct:q4_k_m"
    ),
    [ValidateRange(0, 10)]
    [int]$Repetitions = 0,
    [switch]$DedicatedEndpoint,
    [string]$OllamaBaseUrl = "http://localhost:11434"
)

$ErrorActionPreference = "Stop"
if ($Stage -eq "Final" -and -not $PSBoundParameters.ContainsKey("Models")) {
    throw "El modo Final requiere indicar explícitamente los modelos con -Models."
}
if ($Repetitions -eq 0) {
    $Repetitions = if ($Stage -eq "Final") { 3 } else { 1 }
}

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$backendDirectory = Join-Path $projectRoot "backend"
$resultDirectory = Join-Path $projectRoot "demo\evaluation\results"
$comparisonRows = @()
$technicalFailures = @()
$ineligibleModels = @()
$reportsByModel = @{}
$managedEnvironment = @(
    "AI_CHAT_MODEL",
    "OLLAMA_BASE_URL",
    "LOCAL_MODEL_EVALUATION_REPETITIONS",
    "LOCAL_MODEL_EVALUATION_OUTPUT"
)
$previousEnvironment = @{}

function Get-OllamaEndpoint {
    param([Parameter(Mandatory)][string]$Path)

    $uri = $OllamaBaseUrl.TrimEnd('/') + $Path
    Invoke-RestMethod -Uri $uri -Method Get -TimeoutSec 10
}

function Stop-RunningOllamaModels {
    try {
        $running = Get-OllamaEndpoint -Path "/api/ps"
        foreach ($entry in @($running.models)) {
            $runningName = if ($entry.name) { $entry.name } else { $entry.model }
            if ([string]::IsNullOrWhiteSpace($runningName)) { continue }
            $body = @{ model = $runningName; keep_alive = 0 } | ConvertTo-Json -Compress
            Invoke-RestMethod -Uri ($OllamaBaseUrl.TrimEnd('/') + "/api/generate") `
                -Method Post -ContentType "application/json" -Body $body -TimeoutSec 30 | Out-Null
        }
    }
    catch {
        Write-Warning "No se pudieron descargar todos los modelos de memoria: $($_.Exception.Message)"
    }
}

function Get-ReportLong {
    param(
        [Parameter(Mandatory)]$Summary,
        [Parameter(Mandatory)][string]$Preferred,
        [Parameter(Mandatory)][string]$Fallback
    )

    $value = $Summary.$Preferred
    if ($null -eq $value) { $value = $Summary.$Fallback }
    [long]$value
}

function Select-ScreeningFinalists {
    param([Parameter(Mandatory)][object[]]$Rows)

    $incumbent = @($Rows | Where-Object Model -IEQ "qwen3.5:4b" | Select-Object -First 1)
    $safeChallengers = @($Rows | Where-Object {
        $_.Model -ine "qwen3.5:4b" -and $_.SecurityFailures -eq 0
    })
    $challengers = @($safeChallengers | Where-Object Eligible)
    if ($safeChallengers.Count -eq 0) { return $incumbent }

    $qualityBand = @()
    if ($challengers.Count -gt 0) {
        $bestOutcome = ($challengers | Measure-Object OutcomeAccuracy -Maximum).Maximum
        $bestFacts = ($challengers | Measure-Object FactCoverage -Maximum).Maximum
        $qualityBand = @($challengers | Where-Object {
            $_.OutcomeAccuracy -ge ($bestOutcome - 0.05) -and $_.FactCoverage -ge ($bestFacts - 0.05)
        } | Sort-Object `
            @{ Expression = "WarmP95LatencyMs"; Ascending = $true },
            @{ Expression = "OutcomeAccuracy"; Descending = $true },
            @{ Expression = "FactCoverage"; Descending = $true })
    }

    $selected = [System.Collections.Generic.List[object]]::new()
    foreach ($row in $qualityBand) {
        if ($selected.Count -ge 3) { break }
        $selected.Add($row)
    }
    if ($selected.Count -lt 3) {
        $remaining = @($safeChallengers | Where-Object { $_.Model -notin @($selected.Model) } |
            Sort-Object `
                @{ Expression = {
                    [Math]::Max(0.0, 0.80 - $_.OutcomeAccuracy) +
                    [Math]::Max(0.0, 0.70 - $_.FactCoverage) +
                    [Math]::Max(0.0, 0.95 - $_.StructuredOutput) +
                    [Math]::Max(0.0, 0.95 - $_.CitationSuccess)
                }; Ascending = $true },
                @{ Expression = "OutcomeAccuracy"; Descending = $true },
                @{ Expression = "FactCoverage"; Descending = $true },
                @{ Expression = "WarmP95LatencyMs"; Ascending = $true })
        foreach ($row in $remaining) {
            if ($selected.Count -ge 3) { break }
            $selected.Add($row)
        }
    }
    @($incumbent + @($selected))
}

function Write-ManualReviewTemplate {
    param(
        [Parameter(Mandatory)][string]$Timestamp,
        [Parameter(Mandatory)][hashtable]$Reports
    )

    $baseline = Get-Content -Raw (Join-Path $projectRoot "demo\evaluation\baseline.json") |
        ConvertFrom-Json
    $casesById = @{}
    foreach ($case in $baseline.cases) { $casesById[$case.id] = $case }

    $shuffledModels = @($Reports.Keys | Sort-Object { Get-Random })
    $aliases = @("A", "B", "C", "D", "E", "F", "G", "H", "I", "J")
    $key = @()
    $review = @()
    for ($index = 0; $index -lt $shuffledModels.Count; $index++) {
        $model = $shuffledModels[$index]
        $alias = $aliases[$index]
        $key += [pscustomobject]@{ Alias = $alias; Model = $model }
        foreach ($result in @($Reports[$model].cases | Where-Object stage -NE "AUTHORIZATION_SUITE")) {
            $case = $casesById[$result.id]
            $review += [pscustomobject]@{
                Alias = $alias
                Case = $result.id
                Run = $result.repetition
                ExpectedOutcome = $result.expectedOutcome
                ObservedOutcome = $result.observedOutcome
                ExpectedSources = @($case.expectedSources) -join ";"
                ExpectedFacts = @($case.expectedFacts) -join " | "
                ForbiddenFacts = @($case.forbiddenFacts) -join " | "
                CitedSources = @($result.citedSources) -join ";"
                Answer = $result.answer
                RawModelOutput = $result.rawModelOutput
                FactualSupport = ""
                CitationSupport = ""
                AbstentionCorrect = ""
                SpanishClarity1To5 = ""
                Notes = ""
            }
        }
    }

    $reviewPath = Join-Path $resultDirectory "manual-review-final-$Timestamp.csv"
    $keyPath = Join-Path $resultDirectory "manual-review-key-final-$Timestamp.json"
    $review | Export-Csv -Path $reviewPath -NoTypeInformation -Encoding utf8
    $key | ConvertTo-Json -Depth 4 | Set-Content -Path $keyPath -Encoding utf8
    Write-Host "Plantilla de revisión ciega: $reviewPath" -ForegroundColor Green
    Write-Host "Clave de revisión: $keyPath" -ForegroundColor DarkGray
}

foreach ($name in $managedEnvironment) {
    $previousEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, "Process")
}

try {
    try {
        $tags = Get-OllamaEndpoint -Path "/api/tags"
        $version = Get-OllamaEndpoint -Path "/api/version"
    }
    catch {
        throw "No se puede conectar con Ollama en $OllamaBaseUrl. Inicia Ollama y vuelve a ejecutar el script. Detalle: $($_.Exception.Message)"
    }

    $installedModels = @($tags.models | ForEach-Object { if ($_.name) { $_.name } else { $_.model } })
    $missingModels = @($Models | Where-Object { $_ -notin $installedModels })
    if ($missingModels.Count -gt 0) {
        $pullCommands = $missingModels | ForEach-Object { "ollama pull $_" }
        throw "Faltan modelos. El script no descarga pesos automáticamente:`n$($pullCommands -join [Environment]::NewLine)"
    }

    New-Item -ItemType Directory -Force -Path $resultDirectory | Out-Null
    $env:OLLAMA_BASE_URL = $OllamaBaseUrl
    $env:LOCAL_MODEL_EVALUATION_REPETITIONS = $Repetitions.ToString()
    $env:LOCAL_MODEL_EVALUATION_OUTPUT = $resultDirectory

    if ($DedicatedEndpoint) { Stop-RunningOllamaModels }
    foreach ($model in $Models) {
        $env:AI_CHAT_MODEL = $model
        $startedAt = Get-Date
        $mavenExitCode = 1
        Write-Host "Evaluando $model [$Stage] ($Repetitions repetición/es)..." -ForegroundColor Cyan

        try {
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
                $technicalFailures += $model
                Write-Warning "No se generó un informe JSON para $model."
                continue
            }

            $report = Get-Content -Raw $reportFile.FullName | ConvertFrom-Json
            $reportsByModel[$model] = $report
            $modelTag = @($tags.models | Where-Object {
                ($_.name -ieq $model) -or ($_.model -ieq $model)
            } | Select-Object -First 1)
            $warmMedian = Get-ReportLong -Summary $report.summary `
                -Preferred "warmMedianLatencyMillis" -Fallback "medianLatencyMillis"
            $warmP95 = Get-ReportLong -Summary $report.summary `
                -Preferred "warmP95LatencyMillis" -Fallback "p95LatencyMillis"
            $comparisonRows += [pscustomobject]@{
                Model = $model
                ResolvedName = $report.runtime.resolvedName
                Digest = if ($report.runtime.digest) { $report.runtime.digest } else { $modelTag.digest }
                SizeBytes = if ($report.runtime.sizeBytes) { [long]$report.runtime.sizeBytes } else { [long]$modelTag.size }
                ParameterSize = if ($report.runtime.parameterSize) { $report.runtime.parameterSize } else { $modelTag.details.parameter_size }
                Quantization = if ($report.runtime.quantizationLevel) { $report.runtime.quantizationLevel } else { $modelTag.details.quantization_level }
                Eligible = [bool]$report.summary.eligible
                OutcomeAccuracy = [double]$report.summary.outcomeAccuracy
                FactCoverage = [double]$report.summary.expectedFactCoverage
                StructuredOutput = [double]$report.summary.structuredOutputRate
                CitationSuccess = [double]$report.summary.citationSuccessRate
                SecurityFailures = [int]$report.summary.securityFailures
                ColdStartLatencyMs = [long]$report.summary.coldStartLatencyMillis
                WarmMedianLatencyMs = $warmMedian
                WarmP95LatencyMs = $warmP95
                MeanTokensPerSecond = [double]$report.summary.meanGenerationTokensPerSecond
                Report = $reportFile.Name
            }

            if (-not [bool]$report.summary.eligible) {
                $ineligibleModels += $model
            }
            elseif ($mavenExitCode -ne 0) {
                $technicalFailures += $model
            }
        }
        finally {
            if ($DedicatedEndpoint) { Stop-RunningOllamaModels }
        }
    }

    $comparisonTimestamp = Get-Date -Format "yyyyMMdd-HHmmss"
    $stageName = $Stage.ToLowerInvariant()
    $selectedFinalists = if ($Stage -eq "Screening") {
        @(Select-ScreeningFinalists -Rows $comparisonRows)
    } else {
        @($comparisonRows | Where-Object { $_.Eligible -and $_.SecurityFailures -eq 0 } |
            Sort-Object `
                @{ Expression = "OutcomeAccuracy"; Descending = $true },
                @{ Expression = "FactCoverage"; Descending = $true },
                @{ Expression = "WarmP95LatencyMs"; Ascending = $true })
    }

    $comparisonBase = "comparison-$stageName-$comparisonTimestamp"
    $comparisonJsonPath = Join-Path $resultDirectory "$comparisonBase.json"
    $comparisonMarkdownPath = Join-Path $resultDirectory "$comparisonBase.md"
    [ordered]@{
        schemaVersion = 1
        generatedAt = (Get-Date).ToString("o")
        stage = $Stage
        repetitions = $Repetitions
        ollamaBaseUrl = $OllamaBaseUrl
        ollamaVersion = $version.version
        candidates = $comparisonRows
        selectedFinalists = @($selectedFinalists.Model)
        ineligibleModels = @($ineligibleModels)
        technicalFailures = @($technicalFailures)
    } | ConvertTo-Json -Depth 8 | Set-Content -Path $comparisonJsonPath -Encoding utf8

    $lines = @(
        "# Comparación local de modelos — $Stage",
        "",
        "- Generada: $(Get-Date -Format o)",
        "- Ollama: ``$($version.version)``",
        "- Repeticiones: $Repetitions",
        "",
        "| Modelo | Elegible | Outcome | Hechos | JSON | Citas | Seguridad | Frío | Mediana caliente | p95 caliente | tok/s | Digest |",
        "|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|"
    )
    foreach ($row in $comparisonRows) {
        $shortDigest = if ($row.Digest) { $row.Digest.Substring(0, [Math]::Min(12, $row.Digest.Length)) } else { "-" }
        $lines += "| $($row.Model) | $($row.Eligible) | $($row.OutcomeAccuracy.ToString('0.000')) | $($row.FactCoverage.ToString('0.000')) | $($row.StructuredOutput.ToString('0.000')) | $($row.CitationSuccess.ToString('0.000')) | $($row.SecurityFailures) | $($row.ColdStartLatencyMs) ms | $($row.WarmMedianLatencyMs) ms | $($row.WarmP95LatencyMs) ms | $($row.MeanTokensPerSecond.ToString('0.00')) | ``$shortDigest`` |"
    }
    if ($Stage -eq "Screening") {
        $lines += "", "## Finalistas propuestos", ""
        $lines += @($selectedFinalists | ForEach-Object { "- ``$($_.Model)``" })
    } else {
        $lines += "", "> La clasificación final requiere completar la revisión manual ciega antes de promover un modelo."
    }
    [IO.File]::WriteAllText(
        $comparisonMarkdownPath,
        ($lines -join [Environment]::NewLine) + [Environment]::NewLine
    )
    Write-Host "Comparación JSON: $comparisonJsonPath" -ForegroundColor Green
    Write-Host "Comparación Markdown: $comparisonMarkdownPath" -ForegroundColor Green

    if ($Stage -eq "Final") {
        Write-ManualReviewTemplate -Timestamp $comparisonTimestamp -Reports $reportsByModel
    }
    if ($ineligibleModels.Count -gt 0) {
        Write-Warning "Modelos no elegibles: $($ineligibleModels -join ', ')"
    }
    if ($technicalFailures.Count -gt 0) {
        throw "Fallos técnicos sin informe elegible: $($technicalFailures -join ', ')"
    }
}
finally {
    foreach ($name in $managedEnvironment) {
        [Environment]::SetEnvironmentVariable($name, $previousEnvironment[$name], "Process")
    }
}
