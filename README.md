# CalendarGenerateBack v0.4

Backend del Configurador Calendar Morocco. **La configuración vive en PostgreSQL o SQL Server**; el motor
(`TwoColumnsGenPosAlg` + `AssignPositions`, P1; `ScheduleMoroccoAlg`, P2) recibe snapshots de BD en memoria.
El Excel es solo un puente opcional (importar como vista previa / exportar).

| Pieza | Dónde |
|---|---|
| Motor sin ficheros | `schedules/PlanningParameters`, `SeasonPlanningInput`, `SetupCalendarBuilder`, `ScheduleMoroccoAlg#plan` |
| Reglas nuevas | Blackout, VenueUnavailable, FixedHomeDate (`ScheduleMoroccoAlg#blockedByNewRules`) |
| Cancelación | `ScheduleMoroccoAlg#cancel` → `PlanningCancelledException` (timeout del job: `calendar.planning.timeout`, PT30M) |
| Ficheros legacy | `schedules/LegacyResultFiles` (solo flujo legacy y ficheros que se guardan en BD) |
| BD | Liquibase: esquema, actualizaciones incrementales y catalogos; sin seed historico en produccion |
| API | `setup/SetupController` → `/api/setup/**` (contrato `mockup/CONTRACT.md` §3 y §5) |
| Jobs | `setup/jobs/PlanningJobService` (pool propio, un job activo por competición, logs en `planning_job_log`) |
| Legacy | `/api/calendar`, `/api/config` y batch conservados, activos y conectados a la misma BD y jobs |

## Arrancar en local

```bash
docker run -d --name calendar-pg -e POSTGRES_DB=calendar -e POSTGRES_USER=calendar -e POSTGRES_PASSWORD=calendar -p 5432:5432 postgres:16
bash mvnw spring-boot:run      # Liquibase crea esquema y catalogos, no temporadas ni equipos
curl localhost:8080/api/setup/competitions
```

Variables: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `LIQUIBASE_CONTEXTS` (por defecto `catalogs`),
`CALENDAR_PLANNING_TIMEOUT` (PT30M), `CALENDAR_PLANNING_THREADS` (2).
La conexion y los directorios son infraestructura; los parametros de competicion
no se definen en YAML ni properties. El flag `CALENDAR_LEGACY_FILES_ENABLED`
ya no desactiva funcionalidades ni selecciona otra fuente de datos.

## Configuracion de negocio exclusivamente en BD (CHG-023/024)

- [Matriz completa de los 34 properties](docs/AntiguaConfiguracionYRestricines/README.md):
  clave, funcion, nivel, etapa, pantalla y destino persistido.
- Las pantallas guardan en BD. Los mismos motores legacy consumen equipos,
  checks P1, reglas efectivas, emparejamientos, dependencias y parametros P2.
  No hay fallback a properties; los 18 recursos anteriores son fixtures de test.
- `/configuration/catalogs` muestra referencias. `/restrictions/catalog`
  explica tipos P1/P2 y enlaza a asignaciones de ambitos compatibles.
- `/configuration/legacy-aliases` administra nombres antiguos por competicion,
  con comparacion del valor anterior. Nunca se abren como rutas.
  Las APIs/batch admiten `DB:COMPETITION@YYYY-YY`; si hay varias temporadas,
  omitirla produce error, no una eleccion silenciosa.
- `AssignPositions.main`: argumentos `selector [cantidad=1000] [carpeta]`;
  conserva el ZIP desde candidatos persistidos. `ScheduleMoroccoAlg.main`:
  `selector [ultimaJornada] [carpeta]`; ejecuta el job P2 normal y exporta su version.
  Los exportadores P1 admiten ID de lote o selector con un unico lote exportable.
  Los mains auxiliares requieren selector explicito. Todos requieren `DB_URL`
  o `spring.datasource.url`; nunca usan `drop-first` ni recuperan jobs ajenos.
- P1 elige un sorteo inicial antes de P2. Se rechaza sustituirlo si hay
  trabajos activos, versiones P2 o fixtures fechados/confirmados; no se borra P2.
- La importacion Excel sigue siendo explicita. En la API compatible,
  importar y encolar P2 forman una transaccion; el worker sale tras el commit.

### Ejemplo minimo, solo desarrollo y opt-in

Usar perfil `dev` y `CALENDAR_DEMO_ENABLED=true`. Opcionales:
`CALENDAR_DEMO_SEASON_YEAR=2030`, `CALENDAR_DEMO_TEAM_COUNT=4` (par, 4-16).
Se crea `DEV_MINI`, una temporada y participantes, sin historicos ni resultados.
Son parametros de creacion: reactivar el ejemplo conserva las ediciones.
Su activacion con `prod`, `production` o `test` se rechaza antes de inicializar BD.

Los catalogos son permanentes; FRMF/DEMO son referencias conservadas, no ligas
de ejemplo. El master normal no incluye los 90 changesets de datos historicos,
ni siquiera pasando el antiguo contexto `seed`. Los tests de integracion los
incluyen expresamente y exigen `DB_URL` de una **BD desechable**: usan drop-first.
Un upgrade conserva identidades/checksums aplicados y los datos existentes.

## Endpoints `/api/setup`

| Método | Ruta | Uso |
|---|---|---|
| GET | `/competitions` | competiciones y temporadas |
| GET / POST / PUT / DELETE | `/competitions/{cid}/seasons/{s}` | cargar / crear / guardar / borrar una temporada vacía; PUT con `rowVersion` (409 si cambió) |
| POST | `…/validate?lastRoundToAssign=` | validar borrador (cuerpo opcional) |
| POST | `…/planning-jobs` `{lastRoundToAssign}` | 202; 409 si ya hay un job activo; 422 con `issues` si hay errores |
| GET | `/planning-jobs/{id}` · `/planning-jobs/{id}/logs?from=` | estado y logs |
| POST | `/planning-jobs/{id}/cancel` | cancelar |
| PATCH | `…/fixtures/lock` | confirmar fecha/hora de un partido para conservarlas en planificaciones posteriores |
| GET | `…/versions` · `…/versions/{n}/matches` · `…/versions/{n}/files/{name}` | versiones, partidos, ficheros |
| POST | `…/versions/{n}/approve` · `…/versions/{n}/restore` | aprobar (la anterior pasa a ARCHIVED) · restaurar configuración |
| POST / GET | `…/import-excel` (multipart `file`) · `…/export-excel` | puente Excel opcional (import = vista previa, no guarda) |

### Administración `/api`

| Método | Ruta | Uso |
|---|---|---|
| GET | `/federations?includeInactive=true` | listar federaciones, incluidas las inactivas si se solicita |
| GET / POST / PUT / DELETE | `/federations/{code}` | ficha, alta, edición y borrado protegido |
| PATCH | `/federations/{code}/status` | activar/desactivar sin perder datos |
| GET / POST | `/federations/{code}/competitions` | listar/crear ligas |
| GET / PUT / DELETE | `/competitions/{id}` | ficha, edición y borrado protegido |
| GET | `/competitions/{id}/seasons` | temporadas existentes de una liga |
| GET | `/teams` | catálogo maestro de equipos y número de inscripciones |
| GET / POST / PUT / DELETE | `/teams/{code}` | ficha, alta, edición y borrado protegido del maestro |

La creación de temporada recibe `seasonStart`, `seasonEnd`, `firstRoundDate`
(opcional) y `startSecondLeg`; el identificador usa años consecutivos (`2026-27`).
Se crea vacía: no inventa equipos, jornadas ni partidos. La edición de liga exige
`originalName`; la de equipo, `originalName` y `originalCity` (puede ser `null`)
para detectar cambios concurrentes.

Errores en RFC 7807 (`application/problem+json`), con `detail` explicativo.
El borrado bloqueado devuelve **409 y `blockers: string[]`**, identifica referencias
y cantidades, explica cómo resolverlo y no elimina ningún dato. No hay borrados
en cascada desde estas APIs. Una restricción con excepciones tampoco se elimina
hasta quitar esas excepciones explícitamente. FRMF se conserva como fuente del
catálogo inicial para nuevas federaciones; puede desactivarse.

Estado funcional y limitaciones verificadas: [contexto del proyecto](docs/CONTEXTO_PROYECTO.md).

### Pares P1 entre ligas de una federación

El antiguo `teams.NotAtHomeTogether` significa que dos equipos no pueden ser
locales en la misma **jornada**, aunque jueguen en ligas diferentes. Se configura
con el tipo existente `DerbyTeams` (mostrado como «Pares no en casa juntos (P1)»),
sin crear estadios:

```http
POST /api/setup/federations/FRMF/rule-assignments
Content-Type: application/json

{"scopeType":"FEDERATION_DEFAULT","ruleType":"DerbyTeams","a":"ASFAR","b":"USYM","mandatory":true,"allowException":false}
```

Los códigos son ilustrativos: ambos deben existir y no pertenecer exclusivamente
a otra federación. Las ligas relacionadas en la misma temporada se incluyen
automáticamente en el lote P1 con `includeDependents=true` (valor por defecto),
sin cambiar sus dependencias P2. Con `false`, si hace falta otra liga, validar
devuelve un error y generar responde 422. Se respetan desactivaciones, excepciones
permitidas y precedencia por tipo/parámetros; no se ignoran pares federados activos.

Se verifica cada candidato completo, incluidas ambas vueltas. Un triángulo de
tres equipos que deban ser opuestos entre sí se rechaza con explicación.
El motor P1 actual exige igual número de equipos en ligas relacionadas por
estos pares; configuraciones de distinto tamaño se bloquean explícitamente.
Esta regla no significa «mismo día» ni asigna fechas/horas.

### Excel de candidatos P1

`GET /api/setup/draw-batches/{id}/zip` incluye un XLSX y el CSV anterior por
candidato y competición, los CSV agregados y `LEEME.txt`. Descargar no genera
otro sorteo ni cambia el candidato elegido; utiliza los candidatos del lote.
El botón **Download ZIP for the client** de `/draw` usa el mismo endpoint.

- `BOTOLA_D1` / `BOTOLA_D2`, con 16 posiciones: plantillas
  `MoroccoBotolaD1.xlsx` / `MoroccoBotolaD2.xlsx`.
- `CNPFF_D1` / `CNPFF_D2`, con 12 posiciones: plantillas
  `MoroccoCNPFF1.xlsx` / `MoroccoCNPFF2.xlsx`.
- Se conservan imágenes, estilos y casillas históricas de ida/vuelta, se
  actualizan temporada e identificador y se omiten los partidos `BYE`.
- Otras competiciones o tamaños reciben un XLSX expresamente identificado
  como tabular, sin plantilla antigua compatible. No se selecciona una
  plantilla solo porque otra competición comparta el código `D1`.
- El motor P1 sigue exigiendo un número par de equipos: exportar no añade
  soporte de generación para ligas impares ni modifica restricciones.
- Una plantilla que debería existir pero no puede leerse produce un error;
  no se sustituye silenciosamente por un libro vacío o tabular.

Ejemplo: `candidatos/0042/calendar_D1_0042.xlsx` corresponde al candidato 42,
igual que `candidatos/0042/schedule_D1_0042.csv`. Son emparejamientos sin
fechas ni horarios P2. El puente `…/export-excel` sigue siendo otro flujo.

Las imágenes originales hacen grandes los ZIP: en la prueba de 1.000
candidatos de una liga, XLSX + CSV ocuparon unos **413 MB decimales**.
Renderizar y reabrir los 1.000 XLSX para verificar sus partidos llevó unos
123 segundos en el entorno de prueba; no es un SLA de descarga.
El endpoint tiene un timeout asíncrono de 30 minutos; los proxies deben
permitir también descargas largas. Se procesa un libro cada vez.

Prueba manual: reiniciar el backend en modo **conserva datos**, abrir `/draw`,
descargar un lote ya existente (no hace falta generarlo otra vez) y comprobar
que el número del XLSX coincide con el candidato mostrado y sus partidos.
Empezar con un lote pequeño. No elegir un candidato solo para probar la
descarga: elegir inicializa los emparejamientos de la temporada.

Pruebas sin BD (incluyen generación real de 1.000 candidatos y comparación
de los XLSX/CSV; el repositorio se simula y los ficheros temporales se limpian):

```bash
bash ./mvnw -Dtest=DrawCandidateExcelTest,DrawBatchZipTest,DrawControllerTest,DrawEngineTest test
```

## Regenerar el seed

```bash
pip install openpyxl
python tools/seed/generate_seed.py   # escribe 002-seed-2026-27.sql y src/test/resources/seed/seed-2026-27.tsv
```

## Tests

Las pruebas de integración **borran la BD indicada** mediante
`spring.liquibase.drop-first=true`. Usar exclusivamente una BD desechable, como
`calendar_regression_test`, nunca datos que deban conservarse.

CRUD protegido, restricciones, restauración de snapshots y P2 incremental:
75 pruebas dirigidas verificadas en PostgreSQL y SQL Server. Configurar
`DB_URL`, `DB_USERNAME` y `DB_PASSWORD` para la BD exclusiva y ejecutar:

```bash
bash ./mvnw -Dtest=ApplicationFlowsDatabaseIntegrationTest,SetupDatabaseIntegrationTest,EngineParityTest,EngineTest,SetupToEngineMapperTest,PlanningParametersTest,CompetitionAdminControllerTest,RuleScopeServiceTest,DrawEngineTest,I18nControllerTest test
# En SQL Server añadir: -Dspring.profiles.active=mssql
```

`./mvnw verify` necesita PostgreSQL (los tests con contexto Spring). Los tests del motor no:

```bash
./mvnw test -Dtest='EngineParityTest,PlanningParametersTest,SetupToEngineMapperTest,SetupValidatorTest,EngineTest' -Dsurefire.failIfNoSpecifiedTests=false
```
# Prompt 03 — Calendar Morocco

Preparado tras el informe de Copilot que comunica cierre funcional del Prompt 02 (89/89 tests de regresión en PostgreSQL y SQL Server, con pendientes documentados). **No se ha recibido en este chat un ZIP actualizado del backend después del Prompt 02**; por eso los prompts ordenan leer el contrato REST y controladores directamente del checkout del usuario antes de hacer llamadas.

Empezar por `00_LEEME_ORDEN.md` y ejecutar consecutivamente 03A, 03B, 03C y 03D. Separar en fases previene cortes de mensajes largos por Copilot. Mantener todas las modificaciones locales válidas. El mockup rojo manda visualmente; el backend actual es el contrato de datos y seguridad. No recrear FrontNew ni copiar el login demo.

Fuentes públicas oficiales de decisiones técnicas:
- https://angular.dev/guide/http/http-resource
- https://angular.dev/guide/forms/signals/overview
- https://www.keycloak.org/securing-apps/javascript-adapter
