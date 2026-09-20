$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'ia-v4-decision.ps1')
function Assert($Condition, $Message) { if (-not $Condition) { throw $Message } }
function Report([double]$Latency=1000) {
    [pscustomobject]@{ repetitions=@(1..3 | ForEach-Object {
        [pscustomobject]@{eligible=$true;outcomeAccuracy=1.0;factCoverage=1.0;structuredOutput=1.0;citationSuccess=1.0;warmP95Millis=$Latency;securityFailures=0;unexpectedAnswers=0;answerableRefusals=0}
    }); cases=@(1..3 | ForEach-Object { $rep=$_; foreach($id in @('gm-002','gm-003')) {
        [pscustomobject]@{id=$id;repetition=$rep;mandatory=$true;expectedOutcome='ANSWERED';observedOutcome='ANSWERED';contextPassed=$true;citationSuccess=$true;matchedFacts=2;facts=2;securityFailure=$false;unexpectedAnswer=$false}
    }}) }
}
$reference=Report; $candidate=Report; $regression=Report
Assert (Test-IaV4Acceptance $candidate $reference $regression) 'Valid candidate rejected'
Assert (-not (Test-IaV4Acceptance (Report 1201) $reference $regression)) 'Latency regression accepted'
$candidate.repetitions[1].eligible=$false
Assert (-not (Test-IaV4Acceptance $candidate $reference $regression)) 'Failed repetition accepted'
$candidate=Report; $regression.cases[0].matchedFacts=0
Assert (-not (Test-IaV4Acceptance $candidate $reference $regression)) 'Incomplete causal answer accepted'
$regression=Report; $regression.cases=@($regression.cases | Where-Object id -NE 'gm-003')
Assert (-not (Test-IaV4Acceptance $candidate $reference $regression)) 'Missing mandatory case accepted'
$variants=@(0.8,0.9,1.0 | ForEach-Object { [pscustomobject]@{prompt='v2';threshold=$_;report=(Report)} })
Assert (@(Select-IaV4Calibration $reference $variants)[0].threshold -eq 1.0) 'Conservative tie break failed'
$variants[2].report.repetitions[0].unexpectedAnswers=1
Assert (@(Select-IaV4Calibration $reference $variants)[0].threshold -eq 0.9) 'Unsafe calibration accepted'
Write-Host 'IA v4 decision contracts passed.'
