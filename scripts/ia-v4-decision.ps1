# Pure decision rules for IA v4. No service calls or mutations.
. (Join-Path $PSScriptRoot 'ia-decision.ps1')
function Select-IaV4Calibration($Baseline, $Variants) {
    $b = $Baseline.repetitions[0]
    @($Variants | Where-Object {
        $r=$_.report.repetitions[0]
        $r.securityFailures -eq 0 -and $r.unexpectedAnswers -le $b.unexpectedAnswers -and
        $r.factCoverage -ge $b.factCoverage -and $r.outcomeAccuracy -ge $b.outcomeAccuracy
    } | Sort-Object @{Expression={$_.report.repetitions[0].answerableRefusals};Ascending=$true},
        @{Expression={$_.threshold};Descending=$true}, @{Expression={$_.prompt};Descending=$true})
}
function Get-IaV4RegressionFailures($Regression) {
    @($Regression.cases | Where-Object {
        ($_.id -in @('gm-002','gm-003') -or $_.mandatory) -and
        ($_.observedOutcome -ne $_.expectedOutcome -or -not $_.contextPassed -or
        ($_.expectedOutcome -eq 'ANSWERED' -and (-not $_.citationSuccess -or $_.matchedFacts -ne $_.facts)))
    })
}
function Test-IaV4Acceptance($candidate, $reference, $regression) {
    $eligible = @($candidate.repetitions).Count -eq 3 -and @($reference.repetitions).Count -eq 3 -and @($regression.repetitions).Count -eq 3 -and @($candidate.repetitions | Where-Object {-not $_.eligible}).Count -eq 0
    foreach ($metric in @('outcomeAccuracy','factCoverage','structuredOutput','citationSuccess')) {
        $eligible = $eligible -and (Worst $candidate $metric) -ge (Worst $reference $metric)
    }
    $eligible = $eligible -and (Slowest $candidate) -le 1.2 * (Slowest $reference)
    $failures = @(Get-IaV4RegressionFailures $regression)
    foreach ($id in @('gm-002','gm-003')) {
        $rows = @($regression.cases | Where-Object id -EQ $id)
        $eligible = $eligible -and $rows.Count -eq 3 -and
            (@($rows.repetition | Sort-Object -Unique) -join ',') -eq '1,2,3'
    }
    return $eligible -and $failures.Count -eq 0 -and
        @($regression.cases | Where-Object {$_.securityFailure -or $_.unexpectedAnswer}).Count -eq 0
}
