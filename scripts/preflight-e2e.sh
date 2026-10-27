#!/usr/bin/env bash
# Preflight del entorno desechable 03D: identidad, puerto y salud. Aborta si algo no cuadra.
set -euo pipefail
cd /home/jose/morocco/CalendarGenerateBackNew
project=calendar-morocco-03d-e2e
compose=(docker compose --project-name "$project" --env-file .env.calendar-morocco-e2e -f docker-compose.e2e-03d.yml)
check() {
  local service="$1" port="$2" hostport="$3" id meta
  id="$("${compose[@]}" ps -q "$service")"
  [[ -n "$id" ]] || { echo "FAIL $service no esta en marcha"; exit 3; }
  meta="$(docker inspect --format '{{.State.Status}}|{{index .Config.Labels "com.docker.compose.project"}}|{{index .Config.Labels "com.docker.compose.service"}}|{{.State.Health.Status}}' "$id")"
  [[ "$meta" == "running|$project|$service|healthy" ]] || { echo "FAIL $service identidad/salud: $meta"; exit 3; }
  [[ "$(docker port "$id" "$port/tcp")" == "127.0.0.1:$hostport" ]] || { echo "FAIL $service puerto"; exit 3; }
  echo "OK $service $meta 127.0.0.1:$hostport"
}
check postgres 5432 55433
check mssql 1433 11435
docker inspect --format 'volumes: {{range .Mounts}}{{.Name}} {{end}}' "$("${compose[@]}" ps -q postgres)"
docker inspect --format 'volumes: {{range .Mounts}}{{.Name}} {{end}}' "$("${compose[@]}" ps -q mssql)"
echo PREFLIGHT_OK
