param(
  [ValidateSet("postgres", "mssql", "all")]
  [string]$Target = "all"
)

$ErrorActionPreference = "Stop"

function Stop-Port([int]$Port) {
  $connections = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
  if (-not $connections) {
    Write-Host "[SKIP] Puerto $Port libre"
    return
  }
  $pids = $connections | Select-Object -ExpandProperty OwningProcess -Unique
  foreach ($pidToStop in $pids) {
    $process = Get-Process -Id $pidToStop -ErrorAction SilentlyContinue
    if ($process) {
      Write-Host "[STOP] Puerto $Port ocupado por $($process.ProcessName) PID $pidToStop" -ForegroundColor Yellow
      Stop-Process -Id $pidToStop -Force -ErrorAction SilentlyContinue
    }
  }
}

function Remove-ContainerIfExists([string]$Name) {
  $exists = docker ps -a --format "{{.Names}}" | Select-String -Pattern "^$Name$" -Quiet
  if ($exists) {
    Write-Host "[DOCKER] Eliminando contenedor $Name" -ForegroundColor Yellow
    docker rm -f $Name | Out-Null
  } else {
    Write-Host "[SKIP] Contenedor $Name no existe"
  }
}

function Remove-VolumeIfExists([string]$Name) {
  $exists = docker volume ls --format "{{.Name}}" | Select-String -Pattern "^$Name$" -Quiet
  if ($exists) {
    Write-Host "[DOCKER] Eliminando volumen $Name" -ForegroundColor Yellow
    docker volume rm -f $Name | Out-Null
  } else {
    Write-Host "[SKIP] Volumen $Name no existe"
  }
}

function Reset-PostgresStack {
  Stop-Port 4200
  Stop-Port 8080
  Remove-ContainerIfExists "morocco-new-postgres"
  Remove-VolumeIfExists "morocco-new-lab_pgdata"
}

function Reset-MssqlStack {
  Stop-Port 4200
  Stop-Port 18082
  Remove-ContainerIfExists "morocco-new-mssql"
  Remove-VolumeIfExists "morocco-new-lab_msdata"
}

switch ($Target) {
  "postgres" {
    Write-Host "[RESET] Morocco Postgres stack" -ForegroundColor Cyan
    Reset-PostgresStack
  }
  "mssql" {
    Write-Host "[RESET] Morocco MSSQL stack" -ForegroundColor Cyan
    Reset-MssqlStack
  }
  "all" {
    Write-Host "[RESET] Morocco full stack" -ForegroundColor Cyan
    Reset-PostgresStack
    Reset-MssqlStack
  }
}

Write-Host "[OK] Limpieza completada para target=$Target" -ForegroundColor Green