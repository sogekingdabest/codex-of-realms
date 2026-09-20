$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'ia-decision.ps1')
function Report([double]$Quality, [int]$Latency, [bool]$Eligible = $true) {
    @{ repetitions = @(1..3 | ForEach-Object { [pscustomobject]@{
        outcomeAccuracy=$Quality; factCoverage=$Quality; structuredOutput=1; citationSuccess=1
        eligible=$Eligible; warmP95Millis=$Latency
    } }) }
}
$reference = Report 0.9 1000
$incumbent = [pscustomobject]@{model='qwen3.5:4b';report=$reference}
$fast = [pscustomobject]@{model='fast';report=(Report 0.9 900)}
$slow = [pscustomobject]@{model='slow';report=(Report 1.0 1201)}
$unsafe = [pscustomobject]@{model='unsafe';report=(Report 1.0 800)}
$unsafe.report.repetitions[1].eligible = $false
$winner = @(Select-IaWinner @($incumbent,$fast,$slow,$unsafe) $reference)
if ($winner[0].model -ne 'fast' -or $winner.Count -ne 2) { throw 'Quality, per-run safety or latency guard failed.' }
$calibration = @(0.70,0.60,0.50,0.40 | ForEach-Object { [pscustomobject]@{threshold=$_;summary=[pscustomobject]@{
    securityFailures=0;unexpectedAnswers=0;factCoverage=0.9;answerableRefusals=1
}} })
$calibration[0].summary.answerableRefusals = 2
$calibration[3].summary.unexpectedAnswers = 1
$allowed = @(Select-IaCalibration $calibration)
if ($allowed[0].threshold -ne 0.60 -or $allowed.Count -ne 3) { throw 'Calibration tie break or false-positive guard failed.' }
Write-Host 'IA decision contracts passed.'
