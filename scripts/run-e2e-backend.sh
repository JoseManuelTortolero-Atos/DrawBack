#!/usr/bin/env bash
# Arranca CalendarGenerateBackNew contra el entorno DESECHABLE 03D (nunca contra otras BD).
#   Uso: run-e2e-backend.sh <postgres|mssql>
# Ejecuta antes el preflight; Liquibase solo toca calendar_morocco_e2e.
set -euo pipefail
engine="${1:?postgres|mssql}"
root=/home/jose/morocco/CalendarGenerateBackNew
"$root/scripts/preflight-e2e.sh" >/dev/null || { echo "preflight fallido"; exit 3; }
set -a; . "$root/.env.calendar-morocco-e2e"; set +a
run=/home/jose/e2e-03d-run/$engine
mkdir -p "$run"
cd "$run"
case "$engine" in
  postgres)
    export SPRING_PROFILES_ACTIVE=postgres
    export DB_URL="jdbc:postgresql://127.0.0.1:55433/calendar_morocco_e2e"
    export DB_USERNAME=calendar_e2e
    export DB_PASSWORD="$CALENDAR_E2E_PG_PASSWORD" ;;
  mssql)
    export SPRING_PROFILES_ACTIVE=mssql
    export DB_URL="jdbc:sqlserver://127.0.0.1:11435;databaseName=calendar_morocco_e2e;encrypt=true;trustServerCertificate=true"
    export DB_USERNAME=sa
    export DB_PASSWORD="$CALENDAR_E2E_MSSQL_SA_PASSWORD" ;;
  *) echo "motor invalido"; exit 2 ;;
esac
export LIQUIBASE_CONTEXTS="catalogs,seed"
# E2E_SEED=1 usa el changelog de integracion de tests (esquema + fixtures historicos + updates).
# E2E_DROP_FIRST=1 solo en la primera arrancada sobre la BD desechable (borra su esquema).
java=/usr/lib/jvm/java-21-openjdk-amd64/bin/java
launcher=(-jar "$root/target/calendarcalculator-0.0.1-SNAPSHOT.jar")
if [[ "${E2E_SEED:-0}" == 1 ]]; then
  export SPRING_LIQUIBASE_CHANGE_LOG=classpath:db/changelog/db.changelog-integration.yaml
  export SPRING_LIQUIBASE_CONTEXTS=legacy-fixtures
  export SPRING_LIQUIBASE_DROP_FIRST="${E2E_DROP_FIRST:-0}"
  launcher=(-Dloader.path=/home/jose/e2e-03d-run/seedcp -cp "$root/target/calendarcalculator-0.0.1-SNAPSHOT.jar" org.springframework.boot.loader.launch.PropertiesLauncher)
fi
export CALENDAR_OIDC_ISSUER_URI=http://localhost:18081/realms/calendar-local
export CALENDAR_OIDC_REQUIRED_AUTHORITY=ROLE_SCHEDULE_TEST
echo "Backend $engine -> ${DB_URL%%;*} (usuario $DB_USERNAME) seed=${E2E_SEED:-0} dropFirst=${E2E_DROP_FIRST:-0}"
exec "$java" "${launcher[@]}"
