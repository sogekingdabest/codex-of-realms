[CmdletBinding()]
param(
    [switch]$StartStack,
    [switch]$WithObservability,
    [switch]$SkipVerification
)

$ErrorActionPreference = "Stop"
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$backend = Join-Path $repositoryRoot "backend"

function Invoke-Checked {
    param([string]$Command, [string[]]$Arguments)
    & $Command @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Command failed with exit code ${LASTEXITCODE}: $Command $($Arguments -join ' ')"
    }
}

Push-Location $repositoryRoot
try {
    if (-not $SkipVerification) {
        Write-Host "[1/3] Running the deterministic portfolio verification..."
        Push-Location $backend
        try {
            Invoke-Checked ".\mvnw.cmd" @("--batch-mode", "--no-transfer-progress", "verify")
        }
        finally {
            Pop-Location
        }
    }

    Write-Host "[2/3] Validating the local deployment definition..."
    if (-not $StartStack) {
        if ([string]::IsNullOrWhiteSpace($env:POSTGRES_PASSWORD)) {
            $env:POSTGRES_PASSWORD = "portfolio-validation-only"
        }
        if ([string]::IsNullOrWhiteSpace($env:KEYCLOAK_ADMIN_PASSWORD)) {
            $env:KEYCLOAK_ADMIN_PASSWORD = "portfolio-validation-only"
        }
        if ([string]::IsNullOrWhiteSpace($env:GRAFANA_ADMIN_PASSWORD)) {
            $env:GRAFANA_ADMIN_PASSWORD = "portfolio-validation-only"
        }
    }
    $composeArguments = @("compose", "-f", "compose.yaml")
    if ($WithObservability) {
        $composeArguments += @("-f", "compose.observability.yaml", "--profile", "observability")
    }
    Invoke-Checked "docker" ($composeArguments + @("config", "--quiet"))

    if ($StartStack) {
        Write-Host "[3/3] Starting the portfolio stack..."
        Invoke-Checked "docker" ($composeArguments + @("up", "--build", "--detach", "--wait"))
        Invoke-Checked "docker" ($composeArguments + @("ps"))
    }
    else {
        Write-Host "[3/3] Stack startup skipped. Add -StartStack when .env is configured."
    }

    Write-Host "API health:    http://localhost:8080/actuator/health"
    Write-Host "Web UI:        http://localhost:5173"
    Write-Host "OpenAPI:       http://localhost:8080/swagger-ui.html"
    Write-Host "Keycloak:      http://localhost:8180"
    if ($WithObservability) {
        Write-Host "Prometheus:    http://localhost:9090"
        Write-Host "Grafana:       http://localhost:3000"
    }
    Write-Host "Report:        backend/target/portfolio-reports/deterministic-baseline.md"
    Write-Host "Module docs:   backend/target/spring-modulith-docs"
}
finally {
    Pop-Location
}
