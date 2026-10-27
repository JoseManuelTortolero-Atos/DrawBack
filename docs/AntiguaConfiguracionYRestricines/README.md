# Configuracion antigua, pantallas actuales y motor unico

## Decision y alcance

Se conserva el motor legacy: `TwoColumnsGenPosAlg` + `AssignPositions` en P1,
`ScheduleMoroccoAlg` en P2. No se aparta su funcionalidad ni se ejecuta otro motor.
La fuente de negocio es la BD; las pantallas y las APIs/batch antiguos la usan.
`java.util.Properties` permanece solo como adaptador en memoria y fixture de
paridad. Ningun properties de negocio se empaqueta en recursos de produccion.

**P1** genera emparejamientos candidatos para inicializar una temporada.
**P2** calcula fechas/horas de los emparejamientos elegidos. Elegir P1 no cambia
el sorteo de una competicion en marcha: se bloquea ante P2 activo, versiones
existentes, fixtures fechados/confirmados u otro sorteo activo.

Catalogos de tipos != restricciones concretas. Los tipos tienen etapa y ambitos
compatibles fijos; no se convierten libremente de P1 a P2. Se mantienen herencia,
excepciones, precedencia por mismo tipo/parametros y validacion. No se introduce
gestion de estadios en el frontend; sus restricciones antiguas siguen en motor/BD.

## Matriz de todas las familias de claves

| Clave antigua | Funcion y etapa | Nivel y pantalla actual | Persistencia o tratamiento |
|---|---|---|---|
| `name`, `code` | Identidad; comun | Maestro competicion, `/competitions/:id` | `competition`; ID/codigo inmutables, nombre editable con valor original |
| `nTeams`, `nRounds` | Tamano, jornadas por vuelta; P1/P2 | Participantes `/teams` y jornadas `/matchdays` | Derivados, no duplicar contadores editables |
| `team.N`, `team.CODIGO` | Participantes y nombres; P1/P2 | Competicion/temporada, `/teams`; maestro `/teams/catalog` | `competition_season_team` + `team`; orden significativo |
| `nChecks`, `check.N` | Checks del sorteo; P1 | `/restrictions/p1`, `/restrictions/scopes`, `/restrictions/effective` | `constraint_rule`, asignaciones y excepciones; contador derivado |
| `teams.NotAtHomeTogether.N` | No locales en la misma jornada, tambien entre ligas; P1 | Ambito elegido, `DerbyTeams`, `/restrictions/scopes?stage=P1` | Reglas efectivas y coordinacion de ligas; NO pareja de misma fecha P2 |
| `nResults` | Objetivo obligatorio de candidatos por ejecucion; P1 | Pantalla del lote `/draw` | `draw_batch.requested`; entero positivo indicado en cada lote, sin maximo funcional fijo |
| `posfolder.N` | Patrones de posiciones precalculados; P1 | Automatico, no pedir carpetas al usuario | El mismo generador produce patrones en memoria; candidatos en `draw_candidate_league` |
| `env`, `env.N`, `env.N.M`, `environment` | Seleccion de competicion y ligas relacionadas | Maestro y temporada; compatibilidad `/configuration/legacy-aliases` | ID/codigo/alias BD; dependencias y relaciones persistidas, no rutas |
| `assign.file`, `configFile` | Indireccion a configuracion | `/configuration/legacy-aliases` | Alias opaco; nuevo selector `DB:ID@YYYY-YY` |
| `tournament` | Sorteo con posiciones/equipos; P1 -> P2 | Elegir candidato `/draw`, consultar `/fixtures` | `fixture`; ida/vuelta y orden conservados |
| `start`, `end` | Limites de temporada; P2 | Competicion/temporada, detalle `/season` | `competition_season.season_start/season_end` |
| `datesFile` | Entrada Excel explicita, no configuracion del motor | `/io`; API compatible de importacion | Preview/aplicacion BD con validacion; sin precarga de Excel de ejemplo |
| `resultsFolder`, `assignFolder` | Ubicacion de artefactos, no restriccion | `/results`, ZIP `/draw` | Artefactos persistidos en BD; directorio CLI/infraestructura, nunca parametro de equipo |
| `nTournaments` | Cantidad exportada; P1 | Lote `/draw` | Derivada de candidatos persistidos, no un segundo contador manual |
| `templatePath` | Presentacion XLSX; P1 | Descargar ZIP `/draw` | Plantillas existentes segun competicion/tamano; exportadores conservados, sin rutas arbitrarias en formularios |
| `ScheduleMoroccoAlg.assignGroups.N` | Fases de equipos a calcular; P2 | Competicion/temporada, detalle `/season` | `assign_group`; pueden incluir equipos externos; NO grupos de coordinacion |
| `groupsToRun` | Cuantas fases ejecutar; P2 | Detalle `/season` | `competition_season.groups_to_run` |
| `groupToOptimizeTimes` | Parametro legacy sin efecto efectivo | Detalle `/season`, solo lectura y explicado | Se conserva valor; no fingir una funcionalidad inexistente |
| `teamsToOptimizeTimes` | Participantes con optimizacion horaria; P2 | Participante de temporada, `/teams` | `competition_season_team.optimize_times`; no del maestro global |
| `groupsCanGoToMAJ` | Fases que admiten recuperaciones; P2 | Detalle `/season`; `0` = ninguna | Lista persistida; `allowMAJ=false` bloquea todas sin reconvertir a grupo 1 |
| `roundsForwardLook.N` | Horizonte de jornadas por fase; P2 | Detalle `/season` | `assign_group.rounds_forward_look`; grupo 1 usa horizonte general |
| `roundCalculationOrder` | Orden de calculo de jornadas; P2 | Detalle `/season` | Configuracion de temporada; objetivo N = primeras N entradas |
| `lastRoundToAssign` | Objetivo por ejecucion; P2 | Valor de temporada y `/generate` | `competition_season` + `planning_job.last_round_to_assign` |
| `timeAssignsForwardLook` | Horizonte de asignaciones horarias; P2 | Detalle `/season` | Temporada; cuenta ASIGNACIONES, no minutos ni dias |
| `maxDaysAdvanceMatch`, `maxDaysPostponeMatch` | Margen para adelantar/aplazar; P2 | Detalle `/season` | Temporada; son configuracion de busqueda con efecto restrictivo |
| `competitions` | Codigos implicados en descansos/eventos; P2 | `/configuration/catalogs`, `/external`, `/restrictions/p2` | Catalogo `external_competition`, compromisos y descansos concretos de temporada |
| `restDays.CODIGO` | Descanso frente a cada competicion; P2 | `/restrictions/p2` | `rest_rule`; no asignar valores de una liga a toda la federacion |
| `colour.CODIGO` | Color de salida XLSX; P2 | Catalogo y detalle `/season` | `season_export_colour`; omitir conserva, mapa vacio elimina; no afecta restricciones |
| `maxMatchesPerDay` | Capacidad diaria; P2 | Detalle `/season` | Temporada, no equipo/federacion por defecto |
| `timeslots` | Franjas normales, repetibles; P2 | `/time-rules` | `timeslot`; duplicados significativos, no deduplicar |
| `ramadan.start/end/timeslots` | Intervalo y franja especial; P2 | `/time-rules`, temporada | Temporada; opcional, fechas completas o ausentes |
| `teamsWithoutLight` | Participantes sin iluminacion; P2 | `/teams`, participante de temporada | `competition_season_team.no_light`; sin gestion de estadios |
| `timeslotWithoutLight` | Franja de esos participantes; P2 | `/time-rules` | Temporada; el motor la aplica despues de Ramadan |
| `teamsNotHomeSameDay.N` | No locales en la misma FECHA; P2 | `/restrictions/p2` | `not_home_same_day`; diferente de derbis P1; sin huecos de indices de properties |
| `startSecondLeg` | Inicio de vuelta; P2 | Detalle `/season` | `competition_season.start_second_leg` |
| `outputDatesCSV/Excel`, `outputTeamCSV/Excel`, `outputStatisticsCSV`, `outputTimeStatisticsCSV` | Nombres de salida; P2 | `/results`, versiones y descargas | Nombres estables del exportador; bytes en `generation_file`; no restricciones |
| `wrapperVersion`, `distributionType`, `distributionUrl` | Herramienta Maven | Infraestructura, sin pantalla de negocio | `.mvn/wrapper/maven-wrapper.properties`; NO P1/P2 ni datos de competicion |

Desde `assignGroups.N` hasta las claves `output*`, los parametros de
planificacion llevan prefijo `ScheduleMoroccoAlg.`; las demas claves se muestran
completas. La matriz cubre tambien claves
comentadas, no solo las activas. El test `LegacyPropertiesCoverageTest` controla
34 archivos y todas las familias (mas de 1000 asignaciones, incluidos comentarios).

### Por que desaparece posfolder

Las lineas `posfolder.N=carpeta,cantidad` indicaban archivos con patrones de
posiciones para que el batch no volviera a calcularlos. No eran estadisticas ni
un Excel de calendario, ni 1000 calendarios completos con fechas. El generador
legacy sigue ejecutandose en memoria. En la pantalla `/draw` se indica
obligatoriamente el numero objetivo de emparejamientos para cada lote; no hay un
maximo funcional fijo, aunque el tiempo limite puede producir menos y se informa
al usuario. El Excel es un ejemplo/salida de calendario y no determina ese
numero. No se editan semillas ni el numero de lineas de un TXT.

### Compatibilidad y concurrencia

- `/api/config` y `/api/calendar` siguen disponibles y leen la BD.
- Un nombre terminado en `.properties` es solo un alias almacenado; no se abre.
  Se administra por competicion en `/configuration/legacy-aliases`.
- `GET /api/setup/competitions/{id}/legacy-aliases` lista; `PUT .../{type}`
  recibe `{value,expectedValue}`. Crear: esperado `null`; cambiar: valor anterior
  exacto; borrar: `value=null` y anterior exacto. Conflicto 409, sin sobrescribir.
- Se rechazan nombres ambiguos entre competiciones. No se escoge la ultima
  temporada automaticamente si hay varias: usar `DB:ID@YYYY-YY`.
- Importar y encolar P2 en API compatible es atomico; el worker se envia tras
  commit, no mientras el nuevo snapshot siga sin confirmar.
- Los mains P1/P2 usan los mismos servicios de lotes/jobs. Su contexto minimo
  no recupera como huerfanos los trabajos de otros procesos, ni usa drop-first.
- Se mantienen TXT/Excel como artefactos explicitos. Eso no es volver a leer
  configuracion properties.

## Catalogos y ejemplo opcional de desarrollo

El master normal crea esquema, catalogos y actualizaciones, sin participantes,
temporadas, fixtures ni resultados historicos. Se conservan referencias FRMF y
DEMO; no significa que se creen sus ligas de ejemplo. Cada nueva federacion
recibe los tipos compatibles P1/P2. `HalfMatchesAtHome` es interno y deshabilitado.

Solo perfil `dev` + `CALENDAR_DEMO_ENABLED=true` crea `DEV_MINI`: una temporada,
4 participantes por defecto y configuracion minima sin resultados. Anno
2000-2098 y participantes pares 4-16 se parametrizan por entorno **al crear**;
reactivar no pisa ediciones. Los perfiles prod/production/test rechazan la
activacion incluso combinados con dev. Los fixtures historicos son solo de test
explicito; pasar `LIQUIBASE_CONTEXTS=seed` al master normal no los recarga.

## Inventario exacto: 34 archivos

**18 fixtures en `src/test/resources`**, antes recursos de produccion:

- `AssignPositions.properties`
- `AssignPositionsMorocco16.properties`
- `AssignPositionsMoroccoCNPFF.properties`
- `AssignmentsExcelMoroccoD1.properties`
- `AssignmentsExcelMoroccoD2.properties`
- `AssignmentsExcelMoroccoFemD1.properties`
- `AssignmentsExcelMoroccoFemD2.properties`
- `MoroccoDiv1.properties`
- `MoroccoDiv1_Old.properties`
- `MoroccoDiv2v1.properties`
- `MoroccoFemDiv1.properties`
- `MoroccoFemDiv2.properties`
- `SchEnvironment.properties`
- `SpainTest.properties`
- `schBotolaD1/SchMoroccoD1.properties`
- `schBotolaD2/SchMoroccoD2.properties`
- `schCNPFF1/MoroccoCNPFF1.properties`
- `schCNPFF2/MoroccoCNPFF2.properties`

**15 documentos historicos en esta carpeta**, conservados sin convertirlos en
una fuente de runtime:

- `AssignPositionsMoroccoBotola.properties`
- `p1_AssignPositionsMoroccoCNPFF.properties`
- `SchEnvironment.properties`
- `SchMoroccoD1.properties`
- `SchMoroccoD2.properties`
- `SpainTest.properties`
- `ConfiguracionLiga/AssignmentsExcelMoroccoD1.properties`
- `ConfiguracionLiga/AssignmentsExcelMoroccoD2.properties`
- `ConfiguracionLiga/AssignmentsExcelMoroccoFemD1.properties`
- `ConfiguracionLiga/AssignmentsExcelMoroccoFemD2.properties`
- `ConfiguracionLiga/MoroccoCNPFF1.properties`
- `ConfiguracionLiga/MoroccoDiv1.properties`
- `ConfiguracionLiga/MoroccoDiv2v1.properties`
- `ConfiguracionLiga/MoroccoFemDiv1.properties`
- `ConfiguracionLiga/MoroccoFemDiv2.properties`

**1 archivo de infraestructura**: `.mvn/wrapper/maven-wrapper.properties`.

Los ejemplos historicos no se importan automaticamente: hay claves repetidas,
checks con huecos y referencias invalidas en `SpainTest`. El parser P2 antiguo
paraba en el primer hueco de parejas; la BD elimina esa perdida de restricciones.
La separacion de fixtures no elimina ni desactiva las funcionalidades del motor.

## Estado de verificacion

Implementacion y pruebas evolucionan por separado. Consultar la seccion mas
reciente de [CONTEXTO_PROYECTO](../CONTEXTO_PROYECTO.md) para resultados exactos y
pendientes operativos; no interpretar el inventario como una prueba ejecutada.
No se aplican cambios a BD conservables ni se reinician servicios de trabajo
como parte de una prueba de migracion.
