#!/bin/bash
# Crea la BD desechable calendar_morocco_e2e en el MSSQL del proyecto calendar-morocco-03d-e2e (si no existe).
set -euo pipefail
root=/home/jose/morocco/CalendarGenerateBackNew
"$root/scripts/preflight-e2e.sh" >/dev/null
set -a; . "$root/.env.calendar-morocco-e2e"; set +a
c=$(docker ps --filter label=com.docker.compose.project=calendar-morocco-03d-e2e --format '{{.Names}}' | grep -i mssql | head -1)
echo "contenedor: $c"
sq=/opt/mssql-tools18/bin/sqlcmd
docker exec -e SQLCMDPASSWORD="$CALENDAR_E2E_MSSQL_SA_PASSWORD" "$c" $sq -C -S localhost -U sa -Q "IF DB_ID('calendar_morocco_e2e') IS NULL CREATE DATABASE calendar_morocco_e2e; SELECT name FROM sys.databases"
