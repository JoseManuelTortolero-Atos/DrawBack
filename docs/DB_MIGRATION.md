# Migración de ficheros a PostgreSQL (v0.4)

## Política actual de Liquibase en este workspace

Mientras la BD de Calendar Morocco no tenga una **primera instalación oficial** en un entorno que se quiera conservar, la estrategia vigente es:

- arrancar siempre desde BD vacía
- mantener una **baseline limpia reescribible**
- evitar una cadena artificial de migraciones incrementales sobre estados efímeros

Eso significa que, en esta fase, se puede rehacer el `master`, el esquema inicial y el seed inicial si con ello queda una instalación desde cero más clara y fiable para PostgreSQL y SQL Server.

La política cambia solo cuando exista una primera instalación oficial a preservar. Desde ese momento:

- la baseline queda congelada
- todo cambio posterior pasa a migración incremental normal

## Esquema (`001-schema.sql`)

| Tabla | Contenido | Antes (fichero) |
|---|---|---|
| `competition`, `season`, `team`, `external_competition` | catálogo | `application.yml`, `Morocco*.properties`, `ScheduleMoroccoAlg.colour.*` |
| `competition_season` | temporada, fechas, límites, Ramadán, sin luz, dependencia, `row_version` | `Sch*.properties` |
| `competition_season_team` | equipos en orden de sorteo, `no_light`, `optimize_times` | `team.N`, `teamsWithoutLight`, `teamsToOptimizeTimes` |
| `timeslot`, `assign_group` | franjas (orden = prioridad) y grupos del motor | `timeslots`, `assignGroups.N`, `roundsForwardLook.N` |
| `league_round`, `round_date` | jornadas y fechas disponibles (MAJ = `label`) | filas del Excel con jornada y sin equipos |
| `fixture` | sorteo; `list_order` **significativo**; `play_date` = partido colocado, `play_hour` = hora confirmada | `tournament` + filas del Excel con equipos |
| `external_commitment` | otras competiciones | filas del Excel de otra competición |
| `blackout`, `rest_rule`, `team_pair_rule`, `constraint_rule`, `rule_type_catalog`, `rule_type_allowed_scope` | reglas | `restDays.*`, `teamsNotHomeSameDay.N`, checks y catálogo P1 |
| `generation_version`, `generated_match`, `generation_file` | versiones, partidos y ficheros (bytea) | `data/results/**` |
| `planning_job`, `planning_job_log` | jobs y logs | `GenerationLogStoreService` en memoria |

Índices parciales únicos: una versión `APPROVED` y un job `QUEUED/RUNNING` por competición y temporada.

## Seed 2026-27 (`002-seed-2026-27.sql`, `context:seed`)

Generado por `tools/seed/generate_seed.py`. Comprobaciones que hace el generador:
- el CSV del sorteo (`season/2026-27/schedule_*.csv`) = `tournament` de los properties, y la 2ª vuelta es espejo;
- las filas D1/F1 del Excel de D2/F2 = la versión v0 de D1/F1.

| Competición | Equipos | Fixtures | Colocados (J1–J5) | Parejas mismo día | v0 |
|---|---|---|---|---|---|
| BOTOLA_D1 | 16 | 240 | 40 | 20 | 40 partidos · 6 ficheros |
| BOTOLA_D2 | 16 | 240 | 40 | **17** (legacy: 0) | 80 filas (40 D2 + 40 D1) · 6 ficheros |
| CNPFF_D1 | 12 | 132 | 30 | 0 | 30 · 6 ficheros |
| CNPFF_D2 | 12 | 132 | 30 | 0 | 60 filas (30 F2 + 30 F1) · 6 ficheros |

Decisiones:
1. **Orden de fixtures**: partidos ya colocados en el orden del Excel v0; el resto en el orden del sorteo. El motor reparte las franjas en ese orden.
2. **GAP-04**: `SchMoroccoD2.properties` numera `teamsNotHomeSameDay` desde `.8`; el parser legacy se paraba en `.1` y no cargaba ninguna. El seed carga las 17. No cambia la paridad de v0 (J1–J5 ya tenían fecha).
3. **Dependencias**: D2 → D1 y F2 → F1 con `include_dependency = true`. Las filas D1/F1 no se guardan como externos: el mapper las toma de la versión APROBADA.
4. `teamsToOptimizeTimes` y `teamsWithoutLight` se guardan como flags por equipo: el motor los usa como conjuntos.
5. Las jornadas 6+ no tienen fechas en el legacy ni en el seed: hay que darlas en Matchdays antes de generar más allá de J5 (el validador lo marca como error).
6. Los checks del sorteo (P1) siguen separados del planificador P2: `constraint_rule` persiste instancias y definición canónica, mientras `rule_type_catalog` y `rule_type_allowed_scope` describen tipos, parámetros, ámbitos permitidos y soporte real.
