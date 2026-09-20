[CmdletBinding()]
param([string]$ProjectName = "codex-of-realms")

$ErrorActionPreference = "Stop"
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$settings = Join-Path $repositoryRoot "infra/keycloak/email-verification.json"
Push-Location $repositoryRoot
try {
    $remoteDirectory = (& docker compose -p $ProjectName exec -T keycloak mktemp -d /tmp/codex-email.XXXXXXXX | Out-String).Trim()
    if ($LASTEXITCODE -ne 0 -or $remoteDirectory -notmatch '^/tmp/codex-email\.[A-Za-z0-9]+$') {
        throw "Could not create a private Keycloak work directory."
    }
    & docker compose -p $ProjectName cp $settings "keycloak:$remoteDirectory/settings.json"
    if ($LASTEXITCODE -ne 0) { throw "Could not copy Keycloak verification settings." }
    # Bootstrap credentials stay inside the container and are never printed.
    $configure = @'
set -eu
config_dir="$1"
config="$config_dir/kcadm.config"
trap 'rm -f "$config" "$config_dir/settings.json"; rmdir "$config_dir"' EXIT
/opt/keycloak/bin/kcadm.sh config credentials --config "$config" --server http://localhost:8080 --realm master --user "$KC_BOOTSTRAP_ADMIN_USERNAME" --password "$KC_BOOTSTRAP_ADMIN_PASSWORD" >/dev/null
/opt/keycloak/bin/kcadm.sh update realms/codex-of-realms --config "$config" -f "$config_dir/settings.json"
'@
    & docker compose -p $ProjectName exec -T keycloak sh -ec $configure sh $remoteDirectory
    if ($LASTEXITCODE -ne 0) { throw "Could not update the existing Keycloak realm." }
    Write-Host "Email verification enabled. Local verification mail: http://localhost:8025"
} finally { Pop-Location }
