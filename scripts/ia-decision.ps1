function Worst($Report, [string]$Metric) {
    ($Report.repetitions | Measure-Object -Property $Metric -Minimum).Minimum
}
function Slowest($Report) {
    ($Report.repetitions | Measure-Object -Property warmP95Millis -Maximum).Maximum
}
function Select-IaCalibration($Calibration) {
    $conservative = ($Calibration | Where-Object threshold -EQ 0.70 | Select-Object -First 1).summary
    if ($null -eq $conservative) { throw 'Falta la referencia de calibración 0.70.' }
    @($Calibration | Where-Object {
        $_.summary.securityFailures -eq 0 -and $_.summary.unexpectedAnswers -le $conservative.unexpectedAnswers -and
        $_.summary.factCoverage -ge $conservative.factCoverage
    } | Sort-Object @{Expression={$_.summary.answerableRefusals};Ascending=$true}, @{Expression={$_.threshold};Descending=$true})
}
function Select-IaWinner($Validation, $Reference) {
    $incumbent = ($Validation | Where-Object model -EQ 'qwen3.5:4b' | Select-Object -First 1).report
    if ($null -eq $incumbent -or @($Reference.repetitions).Count -ne 3) { throw 'Referencia incompleta.' }
    @($Validation | Where-Object {
        $candidate = $_.report
        $safe = @($candidate.repetitions).Count -eq 3 -and @($candidate.repetitions | Where-Object { -not $_.eligible }).Count -eq 0
        foreach ($metric in @('outcomeAccuracy','factCoverage','structuredOutput','citationSuccess')) {
            $safe = $safe -and (Worst $candidate $metric) -ge (Worst $Reference $metric) -and
                (Worst $candidate $metric) -ge (Worst $incumbent $metric)
        }
        $safe -and (Slowest $candidate) -le 1.20 * (Slowest $Reference) -and
            (Slowest $candidate) -le 1.20 * (Slowest $incumbent)
    } | Sort-Object @{Expression={Worst $_.report 'factCoverage'};Descending=$true},
        @{Expression={Worst $_.report 'outcomeAccuracy'};Descending=$true},
        @{Expression={Worst $_.report 'citationSuccess'};Descending=$true},
        @{Expression={Slowest $_.report};Ascending=$true})
}
