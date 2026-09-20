[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$OllamaBaseUrl,
    [Parameter(Mandatory)][switch]$DedicatedEndpoint
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'ia-v4-decision.ps1')
if (-not $DedicatedEndpoint) { throw 'Se requiere una instancia dedicada: la evaluación gestiona su memoria.' }
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$output = Join-Path $root ('demo/evaluation/results/ia-v4-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
$models = @('qwen3.5:4b')
$endpoint = $OllamaBaseUrl.TrimEnd('/')
$tags = Invoke-RestMethod "$endpoint/api/tags" -TimeoutSec 10
$missing = @(@($models) + @('bge-m3') | Where-Object {
    $wanted = $_
    -not @($tags.models | Where-Object { $_.name -eq $wanted -or $_.name -eq "$($wanted):latest" }).Count
})
if ($missing.Count) { throw "Faltan modelos: $($missing -join ', '). No se descargan pesos automáticamente." }
New-Item -ItemType Directory -Force $output | Out-Null
$envNames = @('OLLAMA_BASE_URL','AI_CHAT_MODEL','LOCAL_MODEL_EVALUATION_REPETITIONS','IA_REPORT','IA_SPLIT','IA_PIPELINE','QA_MINIMUM_QUESTION_COVERAGE','AI_HTTP_READ_TIMEOUT','IA_DATASET','IA_CORPUS_VERSION','IA_EMBEDDING_AUDIT','LORE_HYBRID_ENABLED','LORE_LEXICAL_MINIMUM_COVERAGE','QA_SELECTION_PROMPT_VERSION')
$previous = @{}
foreach ($name in $envNames) { $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
function Run-Evaluation([string]$Name, [string]$Model, [string]$Pipeline, [string]$Split, [double]$Coverage, [int]$Runs) {
    $env:OLLAMA_BASE_URL = $endpoint
    $env:AI_HTTP_READ_TIMEOUT = '10m'
    $env:AI_CHAT_MODEL = $Model
    $env:IA_PIPELINE = $Pipeline
    $env:IA_SPLIT = $Split
    $env:QA_MINIMUM_QUESTION_COVERAGE = $Coverage.ToString([Globalization.CultureInfo]::InvariantCulture)
    $env:LOCAL_MODEL_EVALUATION_REPETITIONS = "$Runs"
    $env:IA_REPORT = Join-Path $output "$Name.json"
    # Only the explicitly dedicated endpoint is ever unloaded.
    $running = Invoke-RestMethod "$endpoint/api/ps" -TimeoutSec 10
    foreach ($entry in $running.models) {
        Invoke-RestMethod "$endpoint/api/generate" -Method Post -ContentType 'application/json' `
            -Body (@{model=$entry.name; keep_alive=0} | ConvertTo-Json -Compress) -TimeoutSec 60 | Out-Null
    }
    Write-Host "IA: $Name ($Model, $Split, $Runs repeticiones)"
    Push-Location (Join-Path $root 'backend')
    try {
        & .\mvnw.cmd --batch-mode --no-transfer-progress -Pend-to-end-model-evaluation verify | Out-Host
        if ($LASTEXITCODE -ne 0) { throw "Fallo técnico en $Name. No se puede promover ningún modelo." }
    } finally { Pop-Location }
    if (-not (Test-Path -LiteralPath $env:IA_REPORT)) { throw "Falta el informe $Name" }
    return Get-Content -Raw -LiteralPath $env:IA_REPORT | ConvertFrom-Json
}
function Run-Variant([string]$Name, [bool]$Hybrid, [string]$Prompt, [double]$Lexical, [string]$Split, [int]$Runs) {
    $env:LORE_HYBRID_ENABLED = "$Hybrid".ToLowerInvariant()
    $env:QA_SELECTION_PROMPT_VERSION = $Prompt
    $env:LORE_LEXICAL_MINIMUM_COVERAGE = $Lexical.ToString([Globalization.CultureInfo]::InvariantCulture)
    Run-Evaluation $Name $models[0] 'current' $Split 0.70 $Runs
}
try {
    $freeze = Get-Content (Join-Path $root 'demo/evaluation/ia-v4.freeze.json') -Raw | ConvertFrom-Json
    foreach ($file in $freeze.files) {
        if ((Get-FileHash (Join-Path $root $file.path)).Hash -ne $file.sha256) { throw "Dataset congelado alterado: $($file.path)" }
    }
    $env:IA_CORPUS_VERSION = '4'
    $env:IA_DATASET = 'demo/evaluation/ia-v4.json'
    @{startedAt=(Get-Date).ToUniversalTime().ToString('o'); models=$tags.models; freeze=$freeze;
      ollama=(Invoke-RestMethod "$endpoint/api/version");
      cpu=@(Get-CimInstance Win32_Processor | Select-Object Name);
      gpu=@(Get-CimInstance Win32_VideoController | Select-Object Name,DriverVersion);
      files=@(Get-ChildItem (Join-Path $root 'backend/src'),(Join-Path $root 'demo/lore'),(Join-Path $root 'demo/evaluation/ia-sources'),(Join-Path $root 'scripts') -Recurse -File | Get-FileHash | Select-Object Path,Hash)
    } | ConvertTo-Json -Depth 12 | Set-Content (Join-Path $output 'manifest.json') -Encoding utf8
    $env:IA_EMBEDDING_AUDIT = 'true'
    $baseline = Run-Variant 'calibration-reference' $false 'v1' 1.0 'calibration' 1
    $env:IA_EMBEDDING_AUDIT = 'false'
    $variants = @()
    foreach ($prompt in @('v1','v2')) { foreach ($threshold in @(1.0,0.9,0.8)) {
        $name = 'calibration-' + $prompt + '-' + $threshold.ToString('F2',[Globalization.CultureInfo]::InvariantCulture)
        $report = Run-Variant $name $true $prompt $threshold 'calibration' 1
        $variants += [pscustomobject]@{prompt=$prompt; threshold=$threshold; report=$report}
    }}
    $safe = @(Select-IaV4Calibration $baseline $variants)
    if (-not $safe.Count) { throw 'Ninguna configuración segura en calibración. Recuperación complementaria desactivada.' }
    $chosen = $safe[0]
    @{prompt=$chosen.prompt; threshold=$chosen.threshold; frozenAt=(Get-Date).ToUniversalTime().ToString('o'); dataset=$freeze} |
        ConvertTo-Json -Depth 12 | Set-Content (Join-Path $output 'frozen-configuration.json') -Encoding utf8
    $reference = Run-Variant 'validation-reference' $false 'v1' 1.0 'validation' 3
    $candidate = Run-Variant 'validation-candidate' $true $chosen.prompt $chosen.threshold 'validation' 3
    $env:IA_DATASET = 'demo/evaluation/ia-v3.json'
    $regression = Run-Variant 'regression-v3' $true $chosen.prompt $chosen.threshold 'validation' 3
    $regressionFailures = @(Get-IaV4RegressionFailures $regression)
    $eligible = Test-IaV4Acceptance $candidate $reference $regression
    @{status=$(if($eligible){'ELIGIBLE_REQUIRES_CONTEXT_REVIEW'}else{'ACCEPTANCE_BLOCKED'});
      hybridEnabled=$false; applied=$false; model=$models[0]; prompt=$chosen.prompt; threshold=$chosen.threshold;
      regressionFailures=@($regressionFailures | Select-Object id,repetition,observedOutcome,citedSources,matchedFacts,facts)
    } | ConvertTo-Json -Depth 10 | Set-Content (Join-Path $output 'decision.json') -Encoding utf8
    Write-Host "Resultados: $output"
} catch {
    @{status='EVALUATION_BLOCKED'; applied=$false; hybridEnabled=$false; reason=$_.Exception.Message} |
        ConvertTo-Json | Set-Content (Join-Path $output 'decision.json') -Encoding utf8
    throw
} finally {
    foreach ($name in $envNames) { [Environment]::SetEnvironmentVariable($name,$previous[$name],'Process') }
}
