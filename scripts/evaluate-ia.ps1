[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$OllamaBaseUrl,
    [Parameter(Mandatory)][switch]$DedicatedEndpoint
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'ia-decision.ps1')
if (-not $DedicatedEndpoint) { throw 'Se requiere una instancia dedicada: la evaluación gestiona su memoria.' }
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$output = Join-Path $root ('demo/evaluation/results/ia-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
$models = @('qwen3.5:4b', 'ministral-3:3b-instruct-2512-q4_K_M', 'gemma4:e2b-it-qat')
$endpoint = $OllamaBaseUrl.TrimEnd('/')
$tags = Invoke-RestMethod "$endpoint/api/tags" -TimeoutSec 10
$missing = @(@($models) + @('bge-m3') | Where-Object {
    $wanted = $_
    -not @($tags.models | Where-Object { $_.name -eq $wanted -or $_.name -eq "$($wanted):latest" }).Count
})
if ($missing.Count) { throw "Faltan modelos: $($missing -join ', '). No se descargan pesos automáticamente." }
New-Item -ItemType Directory -Force $output | Out-Null
$envNames = @('OLLAMA_BASE_URL','AI_CHAT_MODEL','LOCAL_MODEL_EVALUATION_REPETITIONS','IA_REPORT','IA_SPLIT','IA_PIPELINE','QA_MINIMUM_QUESTION_COVERAGE','AI_HTTP_READ_TIMEOUT')
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
try {
    $manifest = [ordered]@{
        startedAt = (Get-Date).ToUniversalTime().ToString('o'); endpoint = $endpoint
        models = $tags.models; ollama = (Invoke-RestMethod "$endpoint/api/version" -TimeoutSec 10)
        gitCommit = (& git -c "safe.directory=$root" -C $root rev-parse HEAD); gitStatus = @(& git -c "safe.directory=$root" -C $root status --porcelain)
        os = [Environment]::OSVersion.VersionString; processors = [Environment]::ProcessorCount
        cpu = @(Get-CimInstance Win32_Processor | Select-Object Name)
        gpu = @(Get-CimInstance Win32_VideoController | Select-Object Name,DriverVersion)
        ramBytes = (Get-CimInstance Win32_ComputerSystem).TotalPhysicalMemory
        files = @(Get-ChildItem (Join-Path $root 'backend/src'), (Join-Path $root 'demo/lore'), (Join-Path $root 'demo/evaluation/ia-sources') -Recurse -File |
            Get-FileHash -Algorithm SHA256 | Select-Object Path,Hash)
        dataset = (Get-FileHash (Join-Path $root 'demo/evaluation/ia-v3.json') -Algorithm SHA256).Hash
    }
    $manifest | ConvertTo-Json -Depth 12 | Set-Content (Join-Path $output 'manifest.json') -Encoding utf8
    # Frozen test-only implementation makes the pre-change baseline reproducible on the same corpus.
    $reference = Run-Evaluation 'reference-validation' $models[0] 'reference' 'validation' 0.70 3
    $calibration = @()
    foreach ($threshold in @(0.70,0.60,0.50,0.40)) {
        $name = 'calibration-' + $threshold.ToString('F2',[Globalization.CultureInfo]::InvariantCulture)
        $report = Run-Evaluation $name $models[0] 'current' 'calibration' $threshold 1
        $calibration += [pscustomobject]@{ threshold=$threshold; report=$report; summary=$report.repetitions[0] }
    }
    $allowed = @(Select-IaCalibration $calibration)
    if (-not $allowed.Count) { throw 'Ninguna calibración segura. Mantener 0.70 y el modelo actual.' }
    $threshold = $allowed[0].threshold
    @{threshold=$threshold; dataset=$manifest.dataset; frozenAt=(Get-Date).ToUniversalTime().ToString('o')} |
        ConvertTo-Json | Set-Content (Join-Path $output 'frozen-calibration.json') -Encoding utf8
    $validation = @()
    for ($i=0; $i -lt $models.Count; $i++) {
        $report = Run-Evaluation "validation-$i" $models[$i] 'current' 'validation' $threshold 3
        $validation += [pscustomobject]@{model=$models[$i]; report=$report}
    }
    $eligible = @(Select-IaWinner $validation $reference)
    $decision = [ordered]@{status='NO_ELIGIBLE_MODEL'; model=$models[0]; threshold=0.70; applied=$false}
    if ($eligible.Count) {
        $winner = $eligible[0].model
        $decision.status = 'ELIGIBLE_REQUIRES_CONTEXT_REVIEW'
        $decision.model = $winner; $decision.threshold = $threshold
        $decision.digest = ($tags.models | Where-Object name -eq $winner | Select-Object -First 1).digest
    }
    $decision | ConvertTo-Json | Set-Content (Join-Path $output 'decision.json') -Encoding utf8
    Write-Host "Resultados: $output. Revisar citas/contexto antes de aplicar decision.json."
} catch {
    @{status='EVALUATION_BLOCKED'; model=$models[0]; threshold=0.70; applied=$false; reason=$_.Exception.Message} |
        ConvertTo-Json | Set-Content (Join-Path $output 'decision.json') -Encoding utf8
    throw
} finally {
    foreach ($name in $envNames) { [Environment]::SetEnvironmentVariable($name,$previous[$name],'Process') }
}
