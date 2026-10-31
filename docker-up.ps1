<#
.SYNOPSIS
  Demarre les stacks Docker de SaasModduleOS (par defaut : les 5 hubs --
  compliance-engine, fleet-hub, logistix, finance, portail).

.DESCRIPTION
  Chaque hub est un produit independant (voir docs/07-integration-fleet-hub.md :
  jamais fusionnes, integration par API uniquement). Ce script ne modifie ni ne
  fusionne leurs docker-compose.yml respectifs : il les lance comme des projets
  Compose isoles (un nom de projet dedie par hub), chacun avec son propre .env.

  Le portail (Praxio) agrege les 4 autres hubs via leurs URLs publiees sur
  l'hote (host.docker.internal) -- il est donc demarre en dernier, et ses
  .env.example par defaut supposent que chaque hub tourne avec ses ports par
  defaut (voir hubs/portal/.env.example). S'il n'a besoin que d'un
  sous-ensemble, passe -Hubs avec seulement les hubs concernes.

.PARAMETER InfraOnly
  Ne demarre que Postgres/Redis/MinIO de compliance-engine (via son docker-compose.dev.yml
  existant), pour un developpement avec le backend/frontend lances en local
  (mvn spring-boot:run, npm run dev) plutot qu'en conteneur. Les autres hubs ne sont PAS
  demarres dans ce mode : leurs docker-compose.yml ne publient aucun port host pour leur
  base/leur cache (acces interne au reseau Docker uniquement), donc inutilisables depuis
  un backend lance en local de toute facon -- lancer leur backend en local avec H2 en
  memoire (profil par defaut) reste la bonne approche en mode InfraOnly.

.PARAMETER Hubs
  Sous-ensemble de hubs a demarrer (defaut : tous). Valeurs possibles :
  ComplianceEngine, FleetHub, Logistix, Finance, Portal. Ignore avec -InfraOnly
  (qui ne concerne que compliance-engine).

.EXAMPLE
  .\docker-up.ps1
  .\docker-up.ps1 -InfraOnly
  .\docker-up.ps1 -Hubs ComplianceEngine,FleetHub
#>
param(
    [switch]$InfraOnly,
    [ValidateSet("ComplianceEngine", "FleetHub", "Logistix", "Finance", "Portal")]
    [string[]]$Hubs = @("ComplianceEngine", "FleetHub", "Logistix", "Finance", "Portal")
)

$ErrorActionPreference = "Stop"
$root = $PSScriptRoot

$ceDir = Join-Path $root "hubs\compliance-engine\infrastructure\docker"
$ceCompose = Join-Path $ceDir "docker-compose.yml"
$ceDevCompose = Join-Path $ceDir "docker-compose.dev.yml"
$ceEnvExample = Join-Path $ceDir ".env.example"
$ceEnv = Join-Path $ceDir ".env"

$fhDir = Join-Path $root "hubs\fleet-hub"
$fhCompose = Join-Path $fhDir "docker-compose.yml"
$fhEnvExample = Join-Path $fhDir ".env.example"
$fhEnv = Join-Path $fhDir ".env"

$loDir = Join-Path $root "hubs\logistix"
$loCompose = Join-Path $loDir "docker-compose.yml"
$loEnvExample = Join-Path $loDir ".env.example"
$loEnv = Join-Path $loDir ".env"

$fiDir = Join-Path $root "hubs\finance"
$fiCompose = Join-Path $fiDir "docker-compose.yml"
$fiEnvExample = Join-Path $fiDir ".env.example"
$fiEnv = Join-Path $fiDir ".env"

$poDir = Join-Path $root "hubs\portal"
$poCompose = Join-Path $poDir "docker-compose.yml"
$poEnvExample = Join-Path $poDir ".env.example"
$poEnv = Join-Path $poDir ".env"

function New-RandomSecret {
    param([int]$Bytes = 48)
    $buf = [byte[]]::new($Bytes)
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $rng.GetBytes($buf)
    } finally {
        $rng.Dispose()
    }
    return ([Convert]::ToBase64String($buf) -replace '[/+=]', '')
}

function Initialize-EnvFile {
    param(
        [string]$ExamplePath,
        [string]$TargetPath,
        [string[]]$SecretKeys,
        [hashtable]$Overrides = @{}
    )
    if (Test-Path $TargetPath) {
        Write-Host "  .env deja present ($TargetPath) - conserve tel quel" -ForegroundColor DarkGray
        return
    }
    if (-not (Test-Path $ExamplePath)) {
        throw "Fichier modele introuvable: $ExamplePath"
    }
    Write-Host "  Generation de $TargetPath (secrets locaux auto-generes, jamais commite)" -ForegroundColor Yellow
    $lines = Get-Content -Path $ExamplePath -Encoding UTF8
    $out = New-Object System.Collections.Generic.List[string]
    foreach ($line in $lines) {
        $written = $false
        foreach ($key in $SecretKeys) {
            if ($line -match "^$key=") {
                $out.Add("$key=$(New-RandomSecret)")
                $written = $true
                break
            }
        }
        if (-not $written -and $Overrides.ContainsKey(($line -split '=', 2)[0])) {
            $key = ($line -split '=', 2)[0]
            $out.Add("$key=$($Overrides[$key])")
            $written = $true
        }
        if (-not $written) {
            $out.Add($line)
        }
    }
    # -Encoding utf8 in Windows PowerShell 5.1 always writes a BOM, which garbles the
    # accented comments in these .env.example templates when later re-read as plain UTF-8
    # (cosmetic only -- docker compose still parses the values fine -- but avoid it anyway).
    [System.IO.File]::WriteAllLines($TargetPath, $out, (New-Object System.Text.UTF8Encoding $false))
}

Write-Host "=== SaasModduleOS :: demarrage Docker ===" -ForegroundColor Cyan
if ($InfraOnly) {
    Write-Host "Mode infra seule : compliance-engine (Postgres/Redis/MinIO) uniquement" -ForegroundColor Cyan
} else {
    Write-Host ("Hubs : " + ($Hubs -join ", ")) -ForegroundColor Cyan
}
Write-Host ""

if ($InfraOnly) {
    Write-Host "[compliance-engine] preparation .env" -ForegroundColor Cyan
    $ceOverrides = @{ CORS_ORIGINS = "http://localhost:5174,http://localhost:5173,http://localhost:8080,https://localhost:8443" }
    Initialize-EnvFile -ExamplePath $ceEnvExample -TargetPath $ceEnv -SecretKeys @("POSTGRES_PASSWORD", "MINIO_ROOT_PASSWORD", "JWT_SECRET", "API_KEY_SALT") -Overrides $ceOverrides
    Write-Host ""
    Write-Host "[compliance-engine] docker compose up (infra)" -ForegroundColor Cyan
    docker compose -p compliance-engine -f $ceDevCompose --env-file $ceEnv up -d
    if ($LASTEXITCODE -ne 0) { throw "Echec du demarrage de compliance-engine (infra)" }

    Write-Host ""
    Write-Host "=== Stack infra demarree ===" -ForegroundColor Green
    Write-Host "compliance-engine : Postgres localhost:5432, Redis localhost:6379, MinIO localhost:9000/9001"
    Write-Host "  -> backend en local : mvn spring-boot:run -Dspring-boot.run.profiles=dev (hubs/compliance-engine/backend)"
    Write-Host "Statut : docker compose -p compliance-engine ps"
    Write-Host "Arret  : .\docker-down.ps1 -InfraOnly"
    return
}

if ($Hubs -contains "ComplianceEngine") {
    Write-Host "[compliance-engine] preparation .env" -ForegroundColor Cyan
    # CORS_ORIGINS doit inclure le port reellement servi au navigateur. Le .env.example par
    # defaut ne liste que 5173/5174 (dev local direct) -- sans ce port 8080/8443 (celui que ce
    # script utilise pour nginx, voir plus bas), l'inscription/connexion echoue en 403 des que
    # le frontend tourne derriere nginx plutot qu'en direct. Trouve la premiere fois via un
    # vrai test d'inscription apres deploiement complet, pas une supposition.
    $ceOverrides = @{ CORS_ORIGINS = "http://localhost:5174,http://localhost:5173,http://localhost:8080,https://localhost:8443" }
    Initialize-EnvFile -ExamplePath $ceEnvExample -TargetPath $ceEnv -SecretKeys @("POSTGRES_PASSWORD", "MINIO_ROOT_PASSWORD", "JWT_SECRET", "API_KEY_SALT") -Overrides $ceOverrides

    Write-Host ""
    Write-Host "[compliance-engine] docker compose up" -ForegroundColor Cyan
    $env:HTTP_PORT = "8080"
    $env:HTTPS_PORT = "8443"
    try {
        docker compose -p compliance-engine -f $ceCompose --env-file $ceEnv up -d --build
        if ($LASTEXITCODE -ne 0) { throw "Echec du demarrage de compliance-engine" }
    } finally {
        Remove-Item Env:\HTTP_PORT -ErrorAction SilentlyContinue
        Remove-Item Env:\HTTPS_PORT -ErrorAction SilentlyContinue
    }
}

if ($Hubs -contains "FleetHub") {
    Write-Host ""
    Write-Host "[fleet-hub] preparation .env" -ForegroundColor Cyan
    Initialize-EnvFile -ExamplePath $fhEnvExample -TargetPath $fhEnv -SecretKeys @("DB_PASSWORD", "JWT_SECRET", "ADMIN_PASSWORD", "GESTIONNAIRE_PASSWORD", "INTEGRATION_SECRET_KEY")

    Write-Host ""
    Write-Host "[fleet-hub] docker compose up" -ForegroundColor Cyan
    docker compose -p saas-fleethub -f $fhCompose --env-file $fhEnv up -d --build
    if ($LASTEXITCODE -ne 0) { throw "Echec du demarrage de fleet-hub" }
}

if ($Hubs -contains "Logistix") {
    Write-Host ""
    Write-Host "[logistix] preparation .env" -ForegroundColor Cyan
    $loOverrides = @{ APP_CORS_ALLOWED_ORIGINS = "http://localhost:5181" }
    Initialize-EnvFile -ExamplePath $loEnvExample -TargetPath $loEnv -SecretKeys @("DB_PASSWORD", "JWT_SECRET", "ADMIN_PASSWORD", "CREDENTIAL_ENCRYPTION_KEY") -Overrides $loOverrides

    Write-Host ""
    Write-Host "[logistix] docker compose up" -ForegroundColor Cyan
    docker compose -p logistix -f $loCompose --env-file $loEnv up -d --build
    if ($LASTEXITCODE -ne 0) { throw "Echec du demarrage de logistix" }
}

if ($Hubs -contains "Finance") {
    Write-Host ""
    Write-Host "[finance] preparation .env" -ForegroundColor Cyan
    $fiOverrides = @{ APP_CORS_ALLOWED_ORIGINS = "http://localhost:5399" }
    Initialize-EnvFile -ExamplePath $fiEnvExample -TargetPath $fiEnv -SecretKeys @("DB_PASSWORD", "JWT_SECRET", "ADMIN_PASSWORD") -Overrides $fiOverrides

    Write-Host ""
    Write-Host "[finance] docker compose up" -ForegroundColor Cyan
    docker compose -p finance -f $fiCompose --env-file $fiEnv up -d --build
    if ($LASTEXITCODE -ne 0) { throw "Echec du demarrage de finance" }
}

if ($Hubs -contains "Portal") {
    # Demarre en dernier : agrege les 4 autres hubs via host.docker.internal (voir
    # hubs/portal/.env.example) -- ses valeurs par defaut supposent les ports par defaut de
    # chaque hub, deja corrects si les .env ci-dessus n'ont pas ete modifies a la main.
    Write-Host ""
    Write-Host "[portal] preparation .env" -ForegroundColor Cyan
    $poOverrides = @{ APP_CORS_ALLOWED_ORIGINS = "http://localhost:5180" }
    Initialize-EnvFile -ExamplePath $poEnvExample -TargetPath $poEnv -SecretKeys @("DB_PASSWORD", "ADMIN_PASSWORD", "SSO_ENCRYPTION_KEY") -Overrides $poOverrides

    Write-Host ""
    Write-Host "[portal] docker compose up" -ForegroundColor Cyan
    docker compose -p portal -f $poCompose --env-file $poEnv up -d --build
    if ($LASTEXITCODE -ne 0) { throw "Echec du demarrage du portail" }
}

Write-Host ""
Write-Host "=== Stacks demarrees ===" -ForegroundColor Green
if ($Hubs -contains "ComplianceEngine") { Write-Host "compliance-engine : http://localhost:8080" }
if ($Hubs -contains "FleetHub") { Write-Host "fleet-hub         : http://localhost" }
if ($Hubs -contains "Logistix") { Write-Host "logistix          : http://localhost:5181" }
if ($Hubs -contains "Finance") { Write-Host "finance           : http://localhost:5399" }
if ($Hubs -contains "Portal") { Write-Host "portail (Praxio)  : http://localhost:5180" }
Write-Host ""
Write-Host "Statut : docker compose -p <nom-projet> ps   (compliance-engine / saas-fleethub / logistix / finance / portal)"
Write-Host "Arret  : .\docker-down.ps1"
