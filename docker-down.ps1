<#
.SYNOPSIS
  Arrete les stacks Docker de SaasModduleOS (par defaut : les 5 hubs --
  compliance-engine, fleet-hub, logistix, finance, portail).

.PARAMETER InfraOnly
  N'arrete que le stack infra-seule de compliance-engine (docker-compose.dev.yml),
  a utiliser si demarre avec .\docker-up.ps1 -InfraOnly.

.PARAMETER Hubs
  Sous-ensemble de hubs a arreter (defaut : tous). Valeurs possibles :
  ComplianceEngine, FleetHub, Logistix, Finance, Portal. Ignore avec -InfraOnly.

.PARAMETER RemoveVolumes
  Supprime aussi les volumes Docker (donnees Postgres/Redis/MinIO perdues). A utiliser
  uniquement pour repartir d'une base propre.

.EXAMPLE
  .\docker-down.ps1
  .\docker-down.ps1 -InfraOnly
  .\docker-down.ps1 -Hubs ComplianceEngine,FleetHub
  .\docker-down.ps1 -RemoveVolumes
#>
param(
    [switch]$InfraOnly,
    [ValidateSet("ComplianceEngine", "FleetHub", "Logistix", "Finance", "Portal")]
    [string[]]$Hubs = @("ComplianceEngine", "FleetHub", "Logistix", "Finance", "Portal"),
    [switch]$RemoveVolumes
)

$ErrorActionPreference = "Stop"
$root = $PSScriptRoot

$ceDir = Join-Path $root "hubs\compliance-engine\infrastructure\docker"
$ceCompose = Join-Path $ceDir "docker-compose.yml"
$ceDevCompose = Join-Path $ceDir "docker-compose.dev.yml"
$ceEnv = Join-Path $ceDir ".env"

$fhDir = Join-Path $root "hubs\fleet-hub"
$fhCompose = Join-Path $fhDir "docker-compose.yml"
$fhEnv = Join-Path $fhDir ".env"

$loDir = Join-Path $root "hubs\logistix"
$loCompose = Join-Path $loDir "docker-compose.yml"
$loEnv = Join-Path $loDir ".env"

$fiDir = Join-Path $root "hubs\finance"
$fiCompose = Join-Path $fiDir "docker-compose.yml"
$fiEnv = Join-Path $fiDir ".env"

$poDir = Join-Path $root "hubs\portal"
$poCompose = Join-Path $poDir "docker-compose.yml"
$poEnv = Join-Path $poDir ".env"

$downArgs = @("down")
if ($RemoveVolumes) {
    $downArgs += "-v"
    Write-Host "ATTENTION : -RemoveVolumes va supprimer les donnees Postgres/Redis/MinIO." -ForegroundColor Red
}

Write-Host "=== SaasModduleOS :: arret Docker ===" -ForegroundColor Cyan
Write-Host ""

if ($InfraOnly) {
    Write-Host "[compliance-engine] docker compose down (infra)" -ForegroundColor Cyan
    docker compose -p compliance-engine -f $ceDevCompose --env-file $ceEnv @downArgs
    Write-Host ""
    Write-Host "=== Stack infra arretee ===" -ForegroundColor Green
    return
}

if ($Hubs -contains "ComplianceEngine") {
    Write-Host "[compliance-engine] docker compose down" -ForegroundColor Cyan
    docker compose -p compliance-engine -f $ceCompose --env-file $ceEnv @downArgs
}

if ($Hubs -contains "FleetHub") {
    Write-Host ""
    Write-Host "[fleet-hub] docker compose down" -ForegroundColor Cyan
    docker compose -p saas-fleethub -f $fhCompose --env-file $fhEnv @downArgs
}

if ($Hubs -contains "Logistix") {
    Write-Host ""
    Write-Host "[logistix] docker compose down" -ForegroundColor Cyan
    docker compose -p logistix -f $loCompose --env-file $loEnv @downArgs
}

if ($Hubs -contains "Finance") {
    Write-Host ""
    Write-Host "[finance] docker compose down" -ForegroundColor Cyan
    docker compose -p finance -f $fiCompose --env-file $fiEnv @downArgs
}

if ($Hubs -contains "Portal") {
    Write-Host ""
    Write-Host "[portal] docker compose down" -ForegroundColor Cyan
    docker compose -p portal -f $poCompose --env-file $poEnv @downArgs
}

Write-Host ""
Write-Host "=== Stacks arretees ===" -ForegroundColor Green
