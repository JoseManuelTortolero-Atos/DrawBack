# v0.4 · Configuración en PostgreSQL y motor sin ficheros

## Qué cambia
- Motor: `PlanningParameters` + `SeasonPlanningInput` + `SetupCalendarBuilder`; `ScheduleMoroccoAlg#plan` no lee ni escribe ficheros. `execute` (legacy) = `plan` + `LegacyResultFiles`.
- Reglas nuevas: Blackout, VenueUnavailable, FixedHomeDate. Cancelación cooperativa y timeout del job (PT30M).
- Liquibase: esquema de 20 tablas + seed 2026-27 (`context:seed`) generado desde properties, sorteo, Excel v0 y resultados v0.
- API `/api/setup/**` (CONTRACT §5), jobs asíncronos con logs en BD, versiones/ficheros en BD, aprobación y restauración.
- Beans de ficheros detrás de `calendar.legacy-files.enabled` (false por defecto).
- CI: `ci-backend.yml` (PG16 → tests motor → liquibase → verify → GHCR) y on-demand corporativo 1.18.32 (Java 21).

## Paridad
D1, D2, F1 y F2 generados desde los datos del seed = `data/results/2026-27/*-v0` (fechas, horas y orden). D2/F2 con la v0 aprobada de D1/F1.

## Hallazgos
- El orden de los partidos dentro de un día cambia las horas: `fixture.list_order` es significativo.
- D1 con 5 jornadas sin días fijados no termina (backtracking exponencial): por eso timeout + cancelar.
- GAP-04 corregido en el seed: D2 carga 17 parejas `teamsNotHomeSameDay` (el parser legacy cargaba 0).

## Fuera de alcance
CHG-002 (`PATCH …/fixtures/lock`), CHG-006, CHG-007, Helm/Azure, front.

## Riesgos
- `failOrphans` marca como FAILED los jobs activos al arrancar: válido con **una réplica**.
- Las jornadas 6+ no tienen fechas en el seed: el operador debe darlas antes de generar más allá de J5.
