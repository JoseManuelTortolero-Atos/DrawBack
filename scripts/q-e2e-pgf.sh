#!/usr/bin/env bash
# Ejecuta un fichero SQL sobre la BD DESECHABLE 03D (postgres). Uso: q-e2e-pg.sh fichero.sql
set -euo pipefail
docker exec -i calendar-morocco-03d-e2e-postgres-1 psql -U calendar_e2e -d calendar_morocco_e2e -P pager=off < "$1"
