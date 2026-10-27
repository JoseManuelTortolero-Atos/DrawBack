#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
env_file="$root/.env.calendar-morocco-it"

if [[ -e "$env_file" ]]; then
    printf 'Refusing to overwrite existing local environment file: %s\n' "$env_file" >&2
    exit 1
fi

command -v openssl >/dev/null 2>&1 || {
    printf 'openssl is required to generate local test credentials\n' >&2
    exit 1
}

umask 077
pg_password="$(openssl rand -hex 32)"
mssql_password="CmIt-$(openssl rand -hex 24)!z9"
{
    printf 'CALENDAR_PG_PASSWORD=%s\n' "$pg_password"
    printf 'CALENDAR_MSSQL_SA_PASSWORD=%s\n' "$mssql_password"
} > "$env_file"
unset pg_password mssql_password
printf 'Created ignored local test credentials at %s\n' "$env_file"
