#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
env_file="$root/.env.calendar-morocco-e2e"

if [[ -e "$env_file" ]]; then
    printf 'Refusing to overwrite existing local environment file: %s\n' "$env_file" >&2
    exit 1
fi
command -v openssl >/dev/null 2>&1 || { printf 'openssl is required\n' >&2; exit 1; }

umask 077
{
    printf 'CALENDAR_E2E_PG_PASSWORD=%s\n' "$(openssl rand -hex 32)"
    printf 'CALENDAR_E2E_MSSQL_SA_PASSWORD=%s\n' "CmE2e-$(openssl rand -hex 24)!z9"
} > "$env_file"
printf 'Created ignored local credentials at %s\n' "$env_file"
