[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$OllamaBaseUrl,
    [Parameter(Mandatory)][switch]$DedicatedEndpoint
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'ia-v4-decision.ps1')
if (-not $DedicatedEndpoint) { throw 'Se requiere una instancia dedicada: la evaluación gestiona su memoria.' }
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$output = Join-Path $root ('demo/evaluation/results/ia-v5-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
$models = @('qwen3.5:4b')
$endpoint = $OllamaBaseUrl.TrimEnd('/')
$tags = Invoke-RestMethod "$endpoint/api/tags" -TimeoutSec 10
$missing = @(@($models) + @('bge-m3') | Where-Object {
    $wanted = $_
    -not @($tags.models | Where-Object { $_.name -eq $wanted -or $_.name -eq "$($wanted):latest" }).Count
})
if ($missing.Count) { throw "Faltan modelos: $($missing -join ', '). No se descargan pesos automáticamente." }
New-Item -ItemType Directory -Force $output | Out-Null
$envNames = @('OLLAMA_BASE_URL','AI_CHAT_MODEL','LOCAL_MODEL_EVALUATION_REPETITIONS','IA_REPORT','IA_SPLIT','IA_PIPELINE','QA_MINIMUM_QUESTION_COVERAGE','AI_HTTP_READ_TIMEOUT','IA_DATASET','IA_CORPUS_VERSION','IA_EMBEDDING_AUDIT','LORE_HYBRID_ENABLED','LORE_LEXICAL_MINIMUM_COVERAGE','QA_SELECTION_PROMPT_VERSION','LORE_CONTEXTUAL_PASSAGES_ENABLED','QA_COMPLETE_SELECTION_ENABLED')
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
    $freeze=Get-Content (Join-Path $root 'demo/evaluation/ia-v5.freeze.json') -Raw | ConvertFrom-Json
    foreach($file in $freeze.files) {
        if((Get-FileHash (Join-Path $root $file.path)).Hash -ne $file.sha256) { throw 'Frozen dataset modified.' }
    }
    $env:IA_CORPUS_VERSION='5'; $env:IA_DATASET='demo/evaluation/ia-v5.json'; $env:IA_EMBEDDING_AUDIT='false'
    @{startedAt=(Get-Date).ToUniversalTime().ToString('o'); models=$tags.models; freeze=$freeze;
      ollama=(Invoke-RestMethod "$endpoint/api/version"); cpu=@(Get-CimInstance Win32_Processor | Select-Object Name);
      gpu=@(Get-CimInstance Win32_VideoController | Select-Object Name,DriverVersion);
      files=@(Get-ChildItem (Join-Path $root 'backend/src'),(Join-Path $root 'demo'),(Join-Path $root 'scripts') -Recurse -File |
          Where-Object {$_.FullName -notmatch '[\\/]results[\\/]'} | Get-FileHash | Select-Object Path,Hash)
    } | ConvertTo-Json -Depth 12 | Set-Content (Join-Path $output 'manifest.json') -Encoding utf8
    $env:LORE_CONTEXTUAL_PASSAGES_ENABLED='false'; $env:QA_COMPLETE_SELECTION_ENABLED='false'
    $reference=Run-Variant 'validation-reference' $false 'v1' 1.0 'validation' 3
    $env:LORE_CONTEXTUAL_PASSAGES_ENABLED='true'; $env:QA_COMPLETE_SELECTION_ENABLED='true'
    $candidate=Run-Variant 'validation-candidate' $true 'v3' 1.0 'validation' 3
    $env:IA_DATASET='demo/evaluation/ia-v3.json'
    $regression=Run-Variant 'regression-v3' $true 'v3' 1.0 'validation' 3
    $eligible=Test-IaV4Acceptance $candidate $reference $regression
    @{status=$(if($eligible){'ELIGIBLE_REQUIRES_CONTEXT_REVIEW'}else{'ACCEPTANCE_BLOCKED'});
      applied=$false; model=$models[0]; prompt='v3'; lexicalCoverage=1.0;
      regressionFailures=@(Get-IaV4RegressionFailures $regression | Select-Object id,repetition,observedOutcome,citedSources,matchedFacts,facts)
    } | ConvertTo-Json -Depth 10 | Set-Content (Join-Path $output 'decision.json') -Encoding utf8
    Write-Host "Resultados: $output"
} catch {
    @{status='EVALUATION_BLOCKED';applied=$false;reason=$_.Exception.Message} | ConvertTo-Json |
        Set-Content (Join-Path $output 'decision.json') -Encoding utf8
    throw
} finally {
    foreach($name in $envNames) { [Environment]::SetEnvironmentVariable($name,$previous[$name],'Process') }
}
