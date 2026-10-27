#!/usr/bin/env bash
# Prepara el lab antes de arrancar el back: libera el puerto, arranca el contenedor y
# (salvo --keep-db) recrea la BD vacía.
# Uso: scripts/reset-lab.sh postgres|mssql [--keep-db]
set -euo pipefail
cd "$(dirname "$0")/.."

target="${1:?Uso: $0 postgres|mssql [--keep-db]}"
keep_db=false
[ "${2:-}" = "--keep-db" ] && keep_db=true

case "$target" in
  postgres) port=8080;  container=morocco-new-postgres; db=calendar_pg_test; envfile=.env.postgres ;;
  mssql)    port=18082; container=morocco-new-mssql;    db=calendar_ms_test; envfile=.env.mssql ;;
  *) echo "Destino desconocido: $target" >&2; exit 1 ;;
esac

[ -f "$envfile" ] || { echo "Falta $envfile (ver .env.example)" >&2; exit 1; }
DB_PASSWORD="$(grep -E '^DB_PASSWORD=' "$envfile" | cut -d= -f2-)"

pids="$(lsof -ti "tcp:$port" -sTCP:LISTEN || true)"
if [ -n "$pids" ]; then
  echo "[PORT] Matando PID(s) en $port: $pids"
  kill $pids 2>/dev/null || true
  for _ in $(seq 1 20); do lsof -ti "tcp:$port" -sTCP:LISTEN >/dev/null || break; sleep 0.5; done
  pids="$(lsof -ti "tcp:$port" -sTCP:LISTEN || true)"
  [ -z "$pids" ] || kill -9 $pids 2>/dev/null || true
fi

echo "[DOCKER] Arrancando $container"
docker start "$container" >/dev/null

if [ "$keep_db" = true ]; then
  echo "[OK] $target listo: puerto $port libre, BD $db conservada"
  exit 0
fi

if [ "$target" = postgres ]; then
  psqlc() { docker exec -e PGPASSWORD="$DB_PASSWORD" "$container" psql -v ON_ERROR_STOP=1 -U calendar -d postgres -qAtc "$1"; }
  for _ in $(seq 1 60); do psqlc "SELECT 1" >/dev/null 2>&1 && break; sleep 1; done
  echo "[DB] Recreando $db"
  psqlc "DROP DATABASE IF EXISTS $db WITH (FORCE)"
  psqlc "CREATE DATABASE $db OWNER calendar"
else
  sqlc() { docker exec "$container" /opt/mssql-tools18/bin/sqlcmd -C -b -S localhost -U sa -P "$DB_PASSWORD" -d master -Q "$1"; }
  for _ in $(seq 1 60); do sqlc "SELECT 1" >/dev/null 2>&1 && break; sleep 1; done
  echo "[DB] Recreando $db"
  sqlc "IF DB_ID(N'$db') IS NOT NULL BEGIN ALTER DATABASE [$db] SET SINGLE_USER WITH ROLLBACK IMMEDIATE; DROP DATABASE [$db]; END; CREATE DATABASE [$db];" >/dev/null
fi

echo "[OK] $target listo: puerto $port libre, BD $db vacía"
