#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
compose_file="$root/docker-compose.isolated-it.yml"
env_file="$root/.env.calendar-morocco-it"
project="calendar-morocco-p02-it-20261025"
database="calendar_morocco_it"

usage() {
    printf 'Usage: %s <postgres|mssql> <calendar_morocco_it|calendar_morocco_fresh|calendar_morocco_upgrade[_NNN]> <TestClass[,OtherTestClass...]>\n' "$0" >&2
}

if [[ $# -ne 3 ]] || [[ ! "$1" =~ ^(postgres|mssql)$ ]] \
    || [[ ! "$2" =~ ^calendar_morocco_(it|fresh|upgrade|upgrade_[0-9]+)$ ]] \
    || [[ ! "$3" =~ ^[A-Za-z0-9_]+(#[A-Za-z0-9_]+)?(,[A-Za-z0-9_]+(#[A-Za-z0-9_]+)?)*$ ]]; then
    usage
    exit 2
fi

engine="$1"
database="$2"
test_classes="$3"
if [[ "$test_classes" == "BootstrapUpgradeDatabaseIntegrationTest" && "$database" != calendar_morocco_upgrade* ]]; then
    printf 'BootstrapUpgradeDatabaseIntegrationTest requires a new isolated upgrade database\n' >&2
    exit 2
fi
if [[ "$database" == calendar_morocco_upgrade* && "$test_classes" != "BootstrapUpgradeDatabaseIntegrationTest" ]]; then
    printf 'The upgrade database is reserved for BootstrapUpgradeDatabaseIntegrationTest\n' >&2
    exit 2
fi
if [[ "$test_classes" == "BootstrapDatabaseIntegrationTest" && "$database" != "calendar_morocco_fresh" ]]; then
    printf 'BootstrapDatabaseIntegrationTest requires calendar_morocco_fresh\n' >&2
    exit 2
fi
if [[ "$test_classes" == "MatchStateRestartIntegrationTest" && "$database" != "calendar_morocco_it" ]]; then
    printf 'MatchStateRestartIntegrationTest requires the existing calendar_morocco_it database\n' >&2
    exit 2
fi
[[ -f "$env_file" ]] || {
    printf 'Missing ignored local credentials; run scripts/create-isolated-db-env.sh first\n' >&2
    exit 2
}
command -v docker >/dev/null 2>&1 || {
    printf 'Docker CLI is required\n' >&2
    exit 2
}

compose=(docker compose --project-name "$project" --env-file "$env_file" -f "$compose_file")
"${compose[@]}" config --quiet

check_container() {
    local service="$1" port="$2" expected_port="$3" id metadata
    id="$("${compose[@]}" ps --quiet "$service")"
    [[ -n "$id" ]] || {
        printf 'Isolated service %s is not running\n' "$service" >&2
        exit 3
    }
    metadata="$(docker inspect --format '{{.State.Status}}|{{index .Config.Labels "com.docker.compose.project"}}|{{index .Config.Labels "com.docker.compose.service"}}' "$id")"
    [[ "$metadata" == "running|$project|$service" ]] || {
        printf 'Service identity mismatch for %s; refusing to run migrations\n' "$service" >&2
        exit 3
    }
    [[ "$(docker port "$id" "$port/tcp")" == "127.0.0.1:$expected_port" ]] || {
        printf 'Port binding mismatch for %s; refusing to run migrations\n' "$service" >&2
        exit 3
    }
    [[ "$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$id")" == "healthy" ]] || {
        printf 'Service %s is not healthy; refusing to run migrations\n' "$service" >&2
        exit 3
    }
}

check_named_resource() {
    local kind="$1" name="$2" label
    if [[ "$kind" == network ]]; then
        label="$(docker network inspect --format '{{index .Labels "com.docker.compose.project"}}' "$name")"
    else
        label="$(docker volume inspect --format '{{index .Labels "com.docker.compose.project"}}' "$name")"
    fi
    [[ "$label" == "$project" ]] || {
        printf '%s %s does not belong to the isolated Compose project; refusing to run migrations\n' "$kind" "$name" >&2
        exit 3
    }
}

case "$engine" in
    postgres)
        jdbc_url="jdbc:postgresql://127.0.0.1:55432/$database"
        check_container postgres 5432 55432
        check_named_resource network "$project-net"
        check_named_resource volume "$project-postgres-data"
        db_exists="$("${compose[@]}" exec -T postgres psql -U calendar_it -d postgres -Aqt -c \
            "SELECT 1 FROM pg_database WHERE datname = '$database'")"
        if [[ "$test_classes" == "MatchStateRestartIntegrationTest" && "$db_exists" != "1" ]]; then
            printf 'Restart test requires an already existing isolated database; refusing to create it\n' >&2
            exit 3
        fi
        if [[ "$database" == calendar_morocco_upgrade* && "$db_exists" == "1" ]]; then
            printf 'Upgrade database already exists; refusing to alter prior test data\n' >&2
            exit 3
        fi
        if [[ "$db_exists" != "1" ]]; then
            "${compose[@]}" exec -T postgres psql -U calendar_it -d postgres -v ON_ERROR_STOP=1 -c "CREATE DATABASE \"$database\""
        fi
        actual_db="$("${compose[@]}" exec -T postgres psql -U calendar_it -d "$database" -Aqt -c 'SELECT current_database()')"
        db_username="calendar_it"
        db_password="$(sed -n 's/^CALENDAR_PG_PASSWORD=//p' "$env_file")"
        ;;
    mssql)
        jdbc_url="jdbc:sqlserver://127.0.0.1:11434;databaseName=$database;encrypt=true;trustServerCertificate=true"
        check_container mssql 1433 11434
        check_named_resource network "$project-net"
        check_named_resource volume "$project-mssql-data"
        db_password="$(sed -n 's/^CALENDAR_MSSQL_SA_PASSWORD=//p' "$env_file")"
        [[ "$db_password" =~ ^[A-Za-z0-9!._-]{20,}$ ]] || {
            printf 'Local SQL Server credential is missing or malformed\n' >&2
            exit 3
        }
        db_exists="$("${compose[@]}" exec -T mssql /opt/mssql-tools18/bin/sqlcmd -C -S localhost -U sa -P "$db_password" \
            -d master -h -1 -W -b -Q "SET NOCOUNT ON; SELECT DB_ID(N'$database')" | tr -d '\r[:space:]')"
        if [[ "$test_classes" == "MatchStateRestartIntegrationTest" && ( "$db_exists" == "NULL" || -z "$db_exists" ) ]]; then
            printf 'Restart test requires an already existing isolated database; refusing to create it\n' >&2
            exit 3
        fi
        if [[ "$database" == calendar_morocco_upgrade* && "$db_exists" != "NULL" ]]; then
            printf 'Upgrade database already exists; refusing to alter prior test data\n' >&2
            exit 3
        fi
        if [[ "$db_exists" == "NULL" || -z "$db_exists" ]]; then
            "${compose[@]}" exec -T mssql /opt/mssql-tools18/bin/sqlcmd -C -S localhost -U sa -P "$db_password" \
                -d master -b -Q "CREATE DATABASE [$database]"
        fi
        actual_db="$("${compose[@]}" exec -T mssql /opt/mssql-tools18/bin/sqlcmd -C -S localhost -U sa -P "$db_password" \
            -d "$database" -h -1 -W -b -Q 'SET NOCOUNT ON; SELECT DB_NAME()' | tr -d '\r[:space:]')"
        db_username="sa"
        ;;
esac

[[ "$actual_db" == "$database" ]] || {
    printf 'Database identity mismatch for %s; refusing to run migrations\n' "$engine" >&2
    exit 3
}

printf 'Verified isolated JDBC target: %s (%s/%s, Compose project %s)\n' "$jdbc_url" "$engine" "$database" "$project"
printf 'Running test selector: %s\n' "$test_classes"
cd "$root"
if [[ "$test_classes" == "BootstrapDatabaseIntegrationTest" ]]; then
    DB_URL="$jdbc_url" DB_USERNAME="$db_username" DB_PASSWORD="$db_password" \
        CALENDAR_BOOTSTRAP_FRESH_URL="$jdbc_url" \
        mvn -f "$root/pom.xml" "-Dtest=$test_classes" test
elif [[ "$test_classes" == "BootstrapUpgradeDatabaseIntegrationTest" ]]; then
    DB_URL="$jdbc_url" DB_USERNAME="$db_username" DB_PASSWORD="$db_password" \
        CALENDAR_BOOTSTRAP_TEST_URL="$jdbc_url" \
        mvn -f "$root/pom.xml" "-Dtest=$test_classes" test
elif [[ "$test_classes" == "MatchStateRestartIntegrationTest" ]]; then
    DB_URL="$jdbc_url" DB_USERNAME="$db_username" DB_PASSWORD="$db_password" SPRING_LIQUIBASE_DROP_FIRST=false \
        mvn -f "$root/pom.xml" "-Dtest=$test_classes" test
else
    DB_URL="$jdbc_url" DB_USERNAME="$db_username" DB_PASSWORD="$db_password" \
        mvn -f "$root/pom.xml" "-Dtest=$test_classes" test
fi
