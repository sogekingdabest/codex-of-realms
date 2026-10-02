<#
.SYNOPSIS
Loads the El Meridiano de Ceniza demo campaign into a running stack.

.DESCRIPTION
Creates the demo accounts listed in demo/campaign.json in Keycloak, then uses the application API as those
people to create the realm, the spoiler group, the sources and the atlas. At the end it checks what each account
can see and prints the sign-in details.

Each run sets a new password for the demo accounts. Content that already exists is kept, so running the script
again completes an interrupted load without duplicating anything.
#>
[CmdletBinding()]
param(
    [string]$ComposeFile = "compose.yaml",
    [string]$WebUrl = "http://localhost:5173",
    [string]$KeycloakUrl = "http://localhost:8180",
    # Leave empty to generate one. The same password is set for every demo account.
    [SecureString]$Password,
    [int]$TimeoutSeconds = 900
)

$ErrorActionPreference = "Stop"
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$demo = Join-Path $repositoryRoot "demo"
$composePath = if ([IO.Path]::IsPathRooted($ComposeFile)) { $ComposeFile } else { Join-Path $repositoryRoot $ComposeFile }
$campaign = Get-Content -Raw -Encoding UTF8 (Join-Path $demo "campaign.json") | ConvertFrom-Json
# Windows tries IPv6 first and waits about 20 seconds before falling back when Docker publishes a port only on IPv4,
# so requests go straight to 127.0.0.1. Keycloak still issues tokens for its configured hostname.
function ConvertTo-LoopbackUrl([string]$Url) {
    $builder = [UriBuilder]::new($Url)
    if ($builder.Host -eq "localhost") { $builder.Host = "127.0.0.1" }
    $builder.Uri.AbsoluteUri.TrimEnd("/")
}
$api = "$(ConvertTo-LoopbackUrl $WebUrl)/api/v1"
$keycloak = ConvertTo-LoopbackUrl $KeycloakUrl
$tokenUrl = "$keycloak/realms/codex-of-realms/protocol/openid-connect/token"
$adminApi = "$keycloak/admin/realms/codex-of-realms"
# The API only accepts tokens issued to a person. This client lets the script sign in with the demo passwords; it
# exists only while the script runs.
$loaderClient = "codex-demo-loader"
$gm = @($campaign.accounts | Where-Object role -eq "OWNER")[0]
$players = @($campaign.accounts | Where-Object role -eq "PLAYER")
Add-Type -AssemblyName System.Net.Http
$http = [Net.Http.HttpClient]::new()
$http.Timeout = [TimeSpan]::FromMinutes(5)

$adminTokenScript = @'
set -eu
config="$1/kcadm.config"
trap 'rm -rf "$1"' EXIT
if ! output="$(/opt/keycloak/bin/kcadm.sh config credentials --config "$config" --server http://localhost:8080 \
    --realm master --user "$KC_BOOTSTRAP_ADMIN_USERNAME" --password "$KC_BOOTSTRAP_ADMIN_PASSWORD" 2>&1)"; then
  printf '%s\n' "$output" >&2
  exit 1
fi
sed -n 's/^ *"token" : "\([^"]*\)".*/\1/p' "$config"
'@

# Signs in inside the Keycloak container, where the bootstrap administrator's credentials stay, and returns an admin
# API token that expires after a minute. One sign-in replaces a dozen admin CLI calls, each of which starts a JVM.
function Get-AdminToken {
    $container = (& docker compose -f $composePath ps -q keycloak | Out-String).Trim()
    if ($LASTEXITCODE -ne 0 -or -not $container) { throw "Keycloak is not running. Start the stack with $ComposeFile first." }
    $work = (& docker exec $container mktemp -d /tmp/codex-demo.XXXXXXXX | Out-String).Trim()
    if ($work -notmatch '^/tmp/codex-demo\.[A-Za-z0-9]+$') { throw "Could not create a private Keycloak work directory." }
    $local = [IO.Path]::GetTempFileName()
    try {
        [IO.File]::WriteAllText($local, ($adminTokenScript -replace "`r", ""), [Text.UTF8Encoding]::new($false))
        & docker cp -q $local "${container}:$work/admin-token.sh"
        if ($LASTEXITCODE -ne 0) { throw "Could not copy the sign-in step into the Keycloak container." }
        $token = (& docker exec $container sh "$work/admin-token.sh" $work | Out-String).Trim()
        if ($LASTEXITCODE -ne 0 -or -not $token) { throw "Could not sign in to Keycloak as the bootstrap administrator." }
        $token
    }
    finally { Remove-Item -LiteralPath $local -Force }
}

function Invoke-KeycloakAdmin([string]$Token, [string]$Path, [string]$Method = "GET", $Body) {
    Send-Request -Uri "$adminApi$Path" -Token $Token -Method $Method -Body $Body
}

function Remove-LoaderClient([string]$Token) {
    foreach ($client in @(Invoke-KeycloakAdmin $Token "/clients?clientId=$loaderClient")) {
        [void](Invoke-KeycloakAdmin $Token "/clients/$($client.id)" DELETE)
    }
}

function Initialize-Accounts {
    $token = Get-AdminToken
    # Also removes a client left behind by an interrupted run.
    Remove-LoaderClient $token
    $credential = @{ type = "password"; value = $plainPassword; temporary = $false }
    foreach ($account in $campaign.accounts) {
        $user = @(Invoke-KeycloakAdmin $token "/users?exact=true&email=$([uri]::EscapeDataString($account.email))")[0]
        if (-not $user) {
            [void](Invoke-KeycloakAdmin $token "/users" POST @{
                username = $account.email; email = $account.email; firstName = $account.firstName; lastName = $account.lastName
                enabled = $true; emailVerified = $true; credentials = @($credential)
            })
            continue
        }
        # The whole representation goes back: Keycloak clears profile fields missing from an update.
        $user.enabled = $true
        $user.emailVerified = $true
        $user.requiredActions = @()
        [void](Invoke-KeycloakAdmin $token "/users/$($user.id)" PUT $user)
        [void](Invoke-KeycloakAdmin $token "/users/$($user.id)/reset-password" PUT $credential)
    }
    [void](Invoke-KeycloakAdmin $token "/clients" POST @{
        clientId = $loaderClient; name = "Codex demo loader (temporary)"; publicClient = $true
        standardFlowEnabled = $false; directAccessGrantsEnabled = $true
        protocolMappers = @(@{
            name = "codex-api audience"; protocol = "openid-connect"; protocolMapper = "oidc-audience-mapper"
            config = @{ "included.custom.audience" = "codex-api"; "access.token.claim" = "true"; "id.token.claim" = "false" }
        })
    })
}

function New-DemoPassword {
    # Easy to type in several browsers: no characters that look alike, in groups of four.
    $alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789"
    $limit = 256 - 256 % $alphabet.Length
    $random = [Security.Cryptography.RandomNumberGenerator]::Create()
    $byte = [byte[]]::new(1)
    $characters = [Collections.Generic.List[char]]::new()
    while ($characters.Count -lt 16) {
        $random.GetBytes($byte)
        # Rejection sampling keeps every character equally likely.
        if ($byte[0] -lt $limit) { $characters.Add($alphabet[$byte[0] % $alphabet.Length]) }
    }
    $groups = for ($i = 0; $i -lt 16; $i += 4) { -join $characters[$i..($i + 3)] }
    $groups -join "-"
}

$tokens = @{}
function Get-AccessToken([string]$Email) {
    $cached = $tokens[$Email]
    if ($cached -and $cached.Expires -gt [DateTime]::UtcNow) { return $cached.Value }
    $fields = [Collections.Generic.Dictionary[string, string]]::new()
    $fields["grant_type"] = "password"
    $fields["client_id"] = $loaderClient
    $fields["username"] = $Email
    $fields["password"] = $plainPassword
    $response = $http.PostAsync($tokenUrl, [Net.Http.FormUrlEncodedContent]::new($fields)).GetAwaiter().GetResult()
    $body = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
    if (-not $response.IsSuccessStatusCode) { throw "Keycloak did not sign in ${Email}: $body" }
    $token = ConvertFrom-Json $body
    $tokens[$Email] = @{ Value = $token.access_token; Expires = [DateTime]::UtcNow.AddSeconds($token.expires_in - 30) }
    $token.access_token
}

function Send-Request {
    param(
        [Parameter(Mandatory)][string]$Uri,
        [Parameter(Mandatory)][string]$Token,
        [string]$Method = "GET",
        $Body,
        [Net.Http.HttpContent]$Content,
        [string]$IdempotencyKey
    )
    $request = [Net.Http.HttpRequestMessage]::new([Net.Http.HttpMethod]::new($Method), $Uri)
    $request.Headers.Authorization = [Net.Http.Headers.AuthenticationHeaderValue]::new("Bearer", $Token)
    if ($IdempotencyKey) { [void]$request.Headers.TryAddWithoutValidation("Idempotency-Key", $IdempotencyKey) }
    if ($null -ne $Body) {
        $json = ConvertTo-Json -InputObject $Body -Compress -Depth 6
        $request.Content = [Net.Http.StringContent]::new($json, [Text.Encoding]::UTF8, "application/json")
    }
    elseif ($Content) { $request.Content = $Content }
    $response = $http.SendAsync($request).GetAwaiter().GetResult()
    $text = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
    if (-not $response.IsSuccessStatusCode) { throw "$Method $Uri answered $([int]$response.StatusCode): $text" }
    if ($text) {
        $parsed = ConvertFrom-Json $text
        # Lists are written element by element, as Windows PowerShell and PowerShell 7 parse them differently.
        if ($null -ne $parsed) { $parsed }
    }
}

function Invoke-Api {
    param(
        [Parameter(Mandatory)][string]$As,
        [Parameter(Mandatory)][string]$Path,
        [string]$Method = "GET",
        $Body,
        [Net.Http.HttpContent]$Content,
        [string]$IdempotencyKey
    )
    Send-Request -Uri "$api$Path" -Token (Get-AccessToken $As) -Method $Method -Body $Body -Content $Content -IdempotencyKey $IdempotencyKey
}

function Get-PolicyId([string]$Visibility) {
    $policy = switch -Regex ($Visibility) {
        '^public$' { $policies | Where-Object classification -eq "PUBLIC" }
        '^gm-only$' { $policies | Where-Object classification -eq "GM_ONLY" }
        '^spoiler:(.+)$' { $name = $Matches[1]; $policies | Where-Object { $_.classification -eq "SPOILER" -and $_.name -eq $name } }
    }
    $policy = @($policy)[0]
    if (-not $policy) { throw "The realm has no visibility '$Visibility'." }
    $policy.id
}

# The API stores relation types as identifiers: "Se encuentra en" becomes SE_ENCUENTRA_EN.
function ConvertTo-RelationType([string]$Value) {
    $type = $Value.Trim().Normalize([Text.NormalizationForm]::FormD).ToUpperInvariant()
    $type = [regex]::Replace([regex]::Replace($type, '\p{M}+', ''), '[^A-Z0-9]+', '_')
    $type.Trim('_')
}

function Test-Visible([string]$Visibility, $Account) {
    if ($Account.role -eq "OWNER" -or $Visibility -eq "public") { return $true }
    if ($Visibility -like "spoiler:*") {
        $group = @($campaign.spoilerGroups | Where-Object name -eq $Visibility.Substring(8))[0]
        return @($group.players) -contains $Account.email
    }
    $false
}

$chunks = @{}
function Get-EvidenceIds($Evidence) {
    foreach ($item in @($Evidence)) {
        $document = $sourcesByTitle[$item.source]
        if (-not $document) { throw "The source '$($item.source)' is not published." }
        if (-not $chunks.ContainsKey($document.id)) {
            $chunks[$document.id] = @(Invoke-Api -As $gm.email -Path "$realmPath/sources/$($document.id)/chunks")
        }
        $chunk = @($chunks[$document.id] | Where-Object heading -eq $item.heading)[0]
        if (-not $chunk) { throw "'$($item.source)' has no fragment under '$($item.heading)'." }
        $chunk.id
    }
}

function Add-Source($Source) {
    $path = Join-Path $demo $Source.file
    $form = [Net.Http.MultipartFormDataContent]::new()
    $form.Add([Net.Http.StringContent]::new($Source.title, [Text.Encoding]::UTF8), "title")
    $form.Add([Net.Http.StringContent]::new((Get-PolicyId $Source.visibility)), "accessPolicyId")
    $file = [Net.Http.ByteArrayContent]::new([IO.File]::ReadAllBytes($path))
    $file.Headers.ContentType = [Net.Http.Headers.MediaTypeHeaderValue]::new("text/markdown")
    $form.Add($file, "file", [IO.Path]::GetFileName($path))
    [void](Invoke-Api -As $gm.email -Method POST -Path "$realmPath/sources" -Content $form -IdempotencyKey ([guid]::NewGuid().ToString("N")))
}

function Get-LatestJobs {
    $latest = @{}
    foreach ($job in @(Invoke-Api -As $gm.email -Path "$realmPath/source-jobs" | Sort-Object createdAt)) { $latest[$job.title] = $job }
    $latest
}

$plainPassword = if ($Password) { [Net.NetworkCredential]::new("", $Password).Password } else { New-DemoPassword }

Push-Location $repositoryRoot
try {
    Write-Host "[1/6] Preparing the demo accounts in Keycloak..."
    Initialize-Accounts
    try {
        # Signing in once records each person in the application and accepts any pending invitation.
        foreach ($account in $campaign.accounts) { [void](Invoke-Api -As $account.email -Path "/me") }

        Write-Host "[2/6] Creating '$($campaign.realm)' and its visibility..."
        $realm = @(Invoke-Api -As $gm.email -Path "/realms" | Where-Object { $_.name -eq $campaign.realm -and $_.role -eq "OWNER" })[0]
        if (-not $realm) { $realm = Invoke-Api -As $gm.email -Method POST -Path "/realms" -Body @{ name = $campaign.realm } }
        $realmPath = "/realms/$($realm.id)"
        $policies = @(Invoke-Api -As $gm.email -Path "$realmPath/access-policies")
        foreach ($group in $campaign.spoilerGroups) {
            if (-not ($policies | Where-Object { $_.classification -eq "SPOILER" -and $_.name -eq $group.name })) {
                $policies += Invoke-Api -As $gm.email -Method POST -Path "$realmPath/access-policies" -Body @{
                    classification = "SPOILER"; name = $group.name; description = $group.description
                }
            }
        }

        Write-Host "[3/6] Inviting the players and revealing the spoilers..."
        $members = @(Invoke-Api -As $gm.email -Path "$realmPath/memberships")
        $pending = @(Invoke-Api -As $gm.email -Path "$realmPath/invitations" | Where-Object status -eq "PENDING")
        foreach ($player in $players) {
            if ($members.email -contains $player.email) { continue }
            if ($pending.email -notcontains $player.email) {
                [void](Invoke-Api -As $gm.email -Method POST -Path "$realmPath/invitations" -Body @{ email = $player.email; role = "PLAYER" })
            }
            [void](Invoke-Api -As $player.email -Path "/me")
        }
        $members = @(Invoke-Api -As $gm.email -Path "$realmPath/memberships")
        foreach ($player in $players) {
            if ($members.email -notcontains $player.email) { throw "$($player.email) did not join the realm." }
        }
        foreach ($group in $campaign.spoilerGroups) {
            $policyId = Get-PolicyId "spoiler:$($group.name)"
            $granted = @(Invoke-Api -As $gm.email -Path "$realmPath/access-policies/$policyId/grants")
            foreach ($email in $group.players) {
                $member = @($members | Where-Object email -eq $email)[0]
                if ($granted.userId -notcontains $member.userId) {
                    [void](Invoke-Api -As $gm.email -Method PUT -Path "$realmPath/access-policies/$policyId/grants/$($member.userId)")
                }
            }
        }

        Write-Host "[4/6] Uploading the sources..."
        $published = @(Invoke-Api -As $gm.email -Path "$realmPath/sources").title
        $jobs = Get-LatestJobs
        foreach ($source in $campaign.sources) {
            if ($published -contains $source.title) { continue }
            $job = $jobs[$source.title]
            if ($job -and $job.state -eq "FAILED") {
                [void](Invoke-Api -As $gm.email -Method POST -Path "$realmPath/source-jobs/$($job.id)/retry")
            }
            elseif (-not $job -or $job.state -in "SUCCEEDED", "CANCELLED") { Add-Source $source }
        }
        $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
        $reported = -1
        while ($true) {
            $ready = @(Invoke-Api -As $gm.email -Path "$realmPath/sources")
            $missing = @($campaign.sources | Where-Object { $ready.title -notcontains $_.title })
            if ($ready.Count -ne $reported) {
                Write-Host "      $($campaign.sources.Count - $missing.Count) of $($campaign.sources.Count) published"
                $reported = $ready.Count
            }
            if ($missing.Count -eq 0) { break }
            $jobs = Get-LatestJobs
            foreach ($source in $missing) {
                $job = $jobs[$source.title]
                if ($job -and $job.state -in "FAILED", "CANCELLED") {
                    throw "'$($source.title)' could not be processed ($($job.errorCode)). Check that the models are available, then run the script again to retry it."
                }
            }
            if ([DateTime]::UtcNow -gt $deadline) {
                throw "The sources were not published within $TimeoutSeconds seconds. The first upload waits for the embedding model to load; run the script again to keep waiting."
            }
            Start-Sleep -Seconds 2
        }
        $sourcesByTitle = @{}
        foreach ($document in $ready) { $sourcesByTitle[$document.title] = $document }

        Write-Host "[5/6] Filling the atlas..."
        $entities = @{}
        foreach ($entity in @(Invoke-Api -As $gm.email -Path "$realmPath/catalogue/entities")) { $entities[$entity.displayName] = $entity }
        foreach ($entry in $campaign.entities) {
            $entity = $entities[$entry.name]
            if (-not $entity) {
                $entity = Invoke-Api -As $gm.email -Method POST -Path "$realmPath/catalogue/entities" -Body @{
                    type = $entry.type; displayName = $entry.name; aliases = @(if ($entry.aliases) { $entry.aliases })
                    description = $entry.description; accessPolicyId = Get-PolicyId $entry.visibility
                    evidenceChunkIds = @(Get-EvidenceIds $entry.evidence)
                }
            }
            if ($entry.canon -and $entity.canonStatus -ne "CANON") {
                $entity = Invoke-Api -As $gm.email -Method POST -Path "$realmPath/catalogue/entities/$($entity.id)/promotion"
            }
            $entities[$entry.name] = $entity
        }
        $relations = @(Invoke-Api -As $gm.email -Path "$realmPath/catalogue/relations")
        foreach ($entry in $campaign.relations) {
            $source = $entities[$entry.from]
            $target = $entities[$entry.to]
            $type = ConvertTo-RelationType $entry.type
            $relation = @($relations | Where-Object {
                $_.sourceEntityId -eq $source.id -and $_.targetEntityId -eq $target.id -and $_.relationType -eq $type
            })[0]
            if (-not $relation) {
                $relation = Invoke-Api -As $gm.email -Method POST -Path "$realmPath/catalogue/relations" -Body @{
                    sourceEntityId = $source.id; targetEntityId = $target.id; relationType = $entry.type
                    description = $entry.description; accessPolicyId = Get-PolicyId $entry.visibility
                    evidenceChunkIds = @(Get-EvidenceIds $entry.evidence)
                }
            }
            if ($entry.canon -and $relation.canonStatus -ne "CANON") {
                [void](Invoke-Api -As $gm.email -Method POST -Path "$realmPath/catalogue/relations/$($relation.id)/promotion")
            }
        }

        Write-Host "[6/6] Checking what each person can see..."
        $entityVisibility = @{}
        foreach ($entry in $campaign.entities) { $entityVisibility[$entry.name] = $entry.visibility }
        # Only the campaign's own content is compared, so material added later in the application does not count.
        $checks = @(
            @{ Kind = "sources"; Path = "sources"; Items = $campaign.sources; Label = { $_.title }
               Visible = { param($item, $account) Test-Visible $item.visibility $account } }
            @{ Kind = "atlas entries"; Path = "catalogue/entities"; Items = $campaign.entities; Label = { $_.displayName }
               Visible = { param($item, $account) Test-Visible $item.visibility $account }; Name = { $_.name } }
            @{ Kind = "relations"; Path = "catalogue/relations"; Items = $campaign.relations
               Label = { "$($_.sourceEntityName) / $($_.relationType) / $($_.targetEntityName)" }; Name = { "$($_.from) / $(ConvertTo-RelationType $_.type) / $($_.to)" }
               Visible = { param($item, $account)
                   (Test-Visible $item.visibility $account) -and (Test-Visible $entityVisibility[$item.from] $account) -and
                   (Test-Visible $entityVisibility[$item.to] $account) } }
        )
        $summary = foreach ($account in $campaign.accounts) {
            $counts = foreach ($check in $checks) {
                $nameOf = if ($check.Name) { $check.Name } else { $check.Label }
                $all = @($check.Items | ForEach-Object $nameOf)
                $expected = @($check.Items | Where-Object { & $check.Visible $_ $account } | ForEach-Object $nameOf)
                $actual = @(Invoke-Api -As $account.email -Path "$realmPath/$($check.Path)" | ForEach-Object $check.Label | Where-Object { $all -contains $_ })
                $leaked = @($actual | Where-Object { $expected -notcontains $_ })
                $absent = @($expected | Where-Object { $actual -notcontains $_ })
                if ($leaked.Count -gt 0) { throw "$($account.email) can see $($check.Kind) meant for others: $($leaked -join '; ')" }
                if ($absent.Count -gt 0) { throw "$($account.email) cannot see $($check.Kind): $($absent -join '; ')" }
                "$($actual.Count) $($check.Kind)"
            }
            [pscustomobject]@{ Account = $account.email; Role = $account.role; Sees = $counts -join ", " }
        }
    }
    finally {
        Remove-LoaderClient (Get-AdminToken)
    }

    Write-Host ""
    Write-Host "Demo campaign ready: $WebUrl"
    $summary | Format-Table -AutoSize | Out-String | Write-Host
    Write-Host "Password for the three accounts: $plainPassword"
    Write-Host "Running the script again sets a new password. The first question can take a minute while the chat model loads."
}
finally {
    Pop-Location
    $http.Dispose()
}
