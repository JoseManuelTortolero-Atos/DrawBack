# Calendar Morocco: contexto y continuidad

Actualizado: 2026-10-20. Documento de continuidad de las conversaciones y del
trabajo realizado en backend y frontend. No sustituye la comprobacion del codigo
y de los datos actuales. No contiene credenciales.

Estado vigente: ver los apartados **CHG-023, CHG-024 y CHG-025** y la
[matriz de los 34 properties](AntiguaConfiguracionYRestricines/README.md).
Las notas anteriores sobre seed automatico y APIs legacy desactivadas son
historicas: no describen la implementacion actual.

## Conceptos: federacion, competicion, liga y temporada

- **Federacion**: entidad organizadora que agrupa competiciones y puede definir
  reglas comunes. En los datos actuales aparece FRMF.
- **Competicion**: torneo organizado; puede tener formato de liga, copa u otro.
- **Liga**: tipo de competicion. En las pantallas actuales se utiliza tambien
  como nombre practico de la competicion, no como otra entidad superior.
- **Division**: por ejemplo D1 y D2. En el modelo actual se representan como
  competiciones distintas que pueden estar coordinadas.
- **Temporada**: edicion de una competicion, por ejemplo BOTOLA_D1 / 2026-27.
  Equipos participantes, jornadas, emparejamientos y configuracion se vinculan
  a esa edicion.
- **Grupo de coordinacion**: liga principal y dependientes que necesitan
  coordinarse; no es una federacion nueva.
- **Partido**: emparejamiento concreto de dos equipos dentro de una edicion.

No confundir estos tres tipos de datos:

1. **Catalogos de referencia**: tipos de restricciones, fases P1/P2 y ambitos
   permitidos. Describen que se puede configurar.
2. **Maestros**: federaciones, competiciones, equipos y recintos.
3. **Configuracion concreta**: el derbi entre dos equipos, que equipos comparten
   estadio, las fechas de jornadas o una indisponibilidad. No son un catalogo
   universal de reglas.

Un derbi concreto puede declararse como relacion comun de una federacion si se
quiere reutilizar en sus competiciones; tambien puede configurarse solo para una
competicion/temporada. Su ambito debe elegirse expresamente, no deducirse porque
se cargue como dato inicial. Compartir un estadio es una relacion de equipos y
recinto; su consecuencia para el sorteo o las fechas es una restriccion.
Las fechas de jornadas son propias de una competicion/temporada.

### Decisiones posteriores del usuario: estadios e inicializacion P1

- **Sin gestion de estadios en el frontend**: no introducir pantallas,
  catalogos, formularios ni confirmaciones de estadios compartidos. Mantener
  preparado el modelo para una posible evolucion, sin complicar ahora la UX.
- **Compatibilidad del motor**: si la aplicacion antigua ya tenia restricciones
  relacionadas con estadios, conservarlas en backend/motor. Quitar esa gestion
  del front no autoriza borrar reglas existentes ni desactivar sus efectos.
- **P1 es inicializacion**: se generan candidatos, el cliente elige uno y con
  ese sorteo se empieza desde cero la competicion/temporada. Despues se trabaja
  P2. No se necesita un flujo de cambiar el sorteo de una competicion en marcha.
- Por tanto, las versiones P2 anteriores no forman parte del recorrido de
  negocio previsto. No complicar el front con una gestion de versiones P2
  desfasadas para ese caso.
- Esta decision de negocio no equivale a afirmar que el endpoint actual borra
  todo el estado de una competicion o bloquea reselecciones. Eso requiere una
  implementacion y pruebas explicitas antes de prometerlo. No borrar datos P2
  automaticamente por interpretar esta nota como una orden de limpieza.

Los controles de gestion de estadios se retiraron del frontend durante el
trabajo de idiomas y fichas editables descrito abajo. Se conservan los datos
persistidos y las restricciones antiguas del backend/motor.

**Correccion de terminologia solicitada por el usuario**: el pendiente antes
llamado "precargar catalogos: derbis, jornadas y estadios compartidos" debe
separarse en catalogos de referencia y carga inicial de configuracion concreta,
con procedencia y ambito confirmados. No inventar derbis, relaciones ni fechas.

## Objetivo del usuario

- Configurar desde la BD lo que antes estaba en ficheros properties.
- Separar claramente dos caminos:
  - **P1: emparejamientos / sorteo**. Generar alternativas de partidos.
  - **P2: fechas / horarios**. Planificar los partidos ya elegidos.
- Exigir que el usuario indique por pantalla cuántos candidatos P1 generar en
  cada lote; 1000 o 2000 son ejemplos, no un valor predeterminado ni un maximo.
  Guardarlos, descargarlos en ZIP para el cliente y seleccionar uno. No
  confundirlo con un Excel de calendario completo con fechas y horas: el lote
  implementado es de emparejamientos P1.
- Conservar el elegido y permitir borrar los descartados.
- Gestionar restricciones y asignarlas a federacion, grupo, competicion/temporada
  o partido; los motores deben consumirlas, no solo mostrarlas.
- Tener arranques de laboratorio que borran la BD y arranques que conservan datos.
- Responder al usuario en espanol y con explicaciones claras.

## Ubicacion y antecedentes

- Backend Spring Boot: `CalendarGenerateBackNew`.
- Frontend Angular separado: `../CalendarGenerateFrontNew`.
- Ambos se trabajan directamente en WSL2, bajo `/home/jose/morocco`.
- La ruta Windows antigua `C:\mev\git\morocco\backMorocco` es otro arbol, no el
  backend nuevo. Revisar su correspondencia WSL antes de usarlo.
- Se retiro una copia Angular antigua del backend y se guardo en
  `/home/jose/morocco/_backup-front-en-back`. No volver a introducirla.
- Motores existentes: `AssignPositions` para sorteo y `ScheduleMoroccoAlg`
  para fechas/horarios. La integracion reutiliza los motores.
- El usuario permite editar directamente el frontend o entregar un prompt al
  Copilot de la otra ventana.
- Los prompts historicos estan referenciados en [README_PROMPTS](../README_PROMPTS.md)
  y en `../prompts`. Respetar el flujo documentado de mockup, BD, backend y front.

## Arranques y entorno

En `.vscode/launch.json` hay cuatro configuraciones individuales:

- `Back: Postgres (conserva datos)`.
- `Back: Postgres (BD limpia - BORRA DATOS)`.
- `Back: MSSQL (conserva datos)`.
- `Back: MSSQL (BD limpia - BORRA DATOS)`.

Hay tambien dos compuestos Postgres + MSSQL con los mismos modos.
`scripts/reset-lab.sh postgres|mssql --keep-db` prepara el entorno sin borrar
la BD; sin esa opcion recrea la BD de laboratorio. Se liberan los puertos del
backend como parte de la preparacion. No usar el modo limpio para entregas reales.

Entorno de laboratorio observado:

| Servicio | Puerto |
|----------|--------|
| Backend PostgreSQL | 8080 |
| Backend MSSQL | 18082 |
| Angular | 4200 |
| PostgreSQL, contenedor morocco-new-postgres | 15432 |
| MSSQL, contenedor morocco-new-mssql | 11434 |

Credenciales en `.env.postgres` y `.env.mssql`; no copiarlas a documentacion.
Ejecutar el script con `bash`, no con `sh` (usa pipefail).
El proxy local del front apunta al backend PostgreSQL.

Se diagnostico un problema de reenvio localhost de Windows a WSL. La IP WSL
`172.18.100.120` funcionaba en las pruebas, pero es dinamica: no fijarla como
requisito de arquitectura. ERR_CONNECTION_REFUSED tambien puede indicar que
el frontend no esta arrancado; comprobarlo antes de atribuirlo al backend.

## Sorteos por lotes implementados

- Paquete backend `setup/draw`: `DrawEngine`, `DrawBatchService`,
  `DrawBatchRepository`, `DrawController`, `DrawDtos`.
- Changelog `006-draw-candidates.yaml`: lotes, ligas de lote y candidatos.
- Generacion en memoria con persistencia de candidatos y deduplicacion.
- Listado paginado, detalle, cancelar, ZIP, elegir y eliminar descartados.
- El ZIP incluye CSV por candidato y competicion, agregados y LEEME.
- Elegir un candidato sustituye los fixtures de las competiciones del lote.
  No es por si mismo un flujo completo de inicio administrativo de competicion.
- Recorrido de negocio confirmado: la eleccion P1 inicializa una competicion
  desde cero y precede a P2; no se plantea cambiar P1 sobre una competicion en
  marcha. Como detalle tecnico del endpoint actual, si se invoca sobre datos
  P2 ya existentes sustituye fixtures, pero no se ha verificado un reinicio
  integral ni un bloqueo de ese uso. No confundir el requisito con una garantia
  ya implementada.
- Front: pagina `features/draw/draw-page.ts`; menu separado en P1 y P2.

### Mecanismo antiguo de semillas frente al generador P1 actual (verificado en codigo, 2026-10-19)

- La auditoria completa propiedad → P1/P2 → pantalla, incluidos los huecos de
  UI y las discrepancias de properties, esta en
  [AUDITORIA_CONFIGURACION_LEGACY_P1_P2_2026-10-19.md](./AUDITORIA_CONFIGURACION_LEGACY_P1_P2_2026-10-19.md).
- **Antes**: `AssignPositionsMoroccoCNPFF.properties` declara `nResults=1000`,
  los entornos coordinados y una lista `posfolder.N=carpeta,cantidad`. Cada
  `posfolder` referencia un conjunto precomputado de patrones de jornadas por
  posiciones en `results/<entorno>/<carpeta>/pos_*.txt`; la cantidad sirve para
  elegir una linea al azar. El batch antiguo reutiliza esa plantilla, intenta
  asignar equipos a posiciones cumpliendo los checks P1 y escribe los sorteos
  aceptados en ficheros `assign_*`, de donde despues se calculaban estadisticas
  o se exportaban Excel. Esas carpetas son datos de entrada versionados a mano
  en properties, no estadisticas ni el lote final de candidatos.
- **Ahora**: `DrawEngine` genera en memoria los patrones P1 necesarios con
  `TwoColumnsGenPosAlg`, los combina con asignaciones de equipos que satisfacen
  las restricciones efectivas de BD, deduplica y `DrawBatchRepository` persiste
  cada candidato en las tablas `draw_batch*`. No lee `posfolder` ni requiere
  carpetas `results/pos_*`; no hay un catalogo o mantenimiento manual de
  semillas en esta ruta.
- La pantalla P1 del frontend permite introducir la cantidad: valor inicial
  **1.000**, rango actual **1–5.000**, y tiempo maximo **1–240 minutos**
  (inicialmente 30). El backend aplica esos mismos rangos. Por tanto, 100 o
  1.000 se pueden solicitar hoy desde la pantalla; **10.000 no esta admitido**
  sin ampliar de forma coordinada el limite del frontend/backend y revisar
  coste de calculo, almacenamiento, ZIP y pruebas de rendimiento.
- El numero solicitado es un objetivo, no una garantia: el motor esta limitado
  por tiempo e intentos, puede devolver un lote parcial y lo informa como tal.
  Una solicitud hasta 5.000 tampoco prueba por si sola que se obtendran los
  5.000 en todas las competiciones/configuraciones.
- `AssignmentsExcelMoroccoD1.properties` con `nTournaments=1000` pertenece al
  exportador antiguo: lee 1.000 sorteos ya escritos en `assignFolder` y produce
  XLSX desde `templatePath`. No controla la generacion P1 actual. El ZIP actual
  incluye CSV y XLSX por candidato/competicion; para las ligas compatibles
  reutiliza las plantillas legacy.
- **Conclusion funcional**: hoy se configura la cantidad en la pantalla de
  generacion P1; generar las plantillas de posiciones es automatico dentro del
  motor. No hace falta recrear `posfolder` como configuracion para usar el
  flujo actual. Solo considerar cache o semillas persistentes si mediciones
  reales demuestran que generar patrones en cada lote impide alcanzar los
  objetivos de cantidad/tiempo. La prueba MSSQL documentada mas abajo obtuvo
  1.000/1.000; 5.000 y 10.000 no quedan demostrados por esa prueba y 10.000
  sigue fuera del limite actual.

## Restricciones por ambito: comportamiento vigente

Ambitos de general a especifico:
`FEDERATION_DEFAULT`, `COORDINATION_GROUP`, `COMPETITION_SEASON`, `MATCH`.
`LEAGUE_SETUP` identifica reglas configuradas en la pantalla propia de la liga.

### Herencia

- Federacion: se consideran sus reglas para las ligas de esa federacion.
- Grupo: se consideran en la competicion principal y sus dependientes.
- Liga/temporada y partido: se consideran solo en esa edicion.
- Se comprueba la pertenencia de los equipos; una regla no aplicable se muestra
  como tal. Las listas se filtran a los equipos de la liga; SeparateMatches
  necesita al menos dos.

### Excepciones

- Una regla no obligatoria con `allowException` puede excluirse desde un ambito
  mas concreto permitido (competicion/temporada o grupo).
- Una regla obligatoria fuerza `allowException=false`; no puede excluirse.
- La excepcion referencia `exceptionOf`, pertenece a la misma federacion y no
  puede ser una excepcion de otra excepcion.
- El front ofrece "Exclude in <liga>" para los casos permitidos.
- Eliminar una asignacion elimina tambien sus excepciones.

### Duplicados

- Se comparan reglas del mismo tipo **y mismos parametros**, no simplemente del
  mismo tipo. Cambiar parametros no constituye una anulacion automatica.
- Gana la configurada en `LEAGUE_SETUP`.
- En las demas gana el ambito mas especifico; a igual ambito, mayor prioridad.
- Esto elimina duplicados; no es un solucionador general de contradicciones
  entre reglas distintas.

### Validacion y concurrencia

- Tipo existente, habilitado y permitido para el ambito segun el catalogo.
- Federacion y competicion coherentes; temporada obligatoria donde corresponda.
- Equipos existentes y pertenecientes al ambito; MATCH requiere dos distintos.
- Parametros requeridos, fechas ordenadas y jornadas dentro del rango de la
  liga/partido (1..2*(n-1)).
- Reglas P1 convertibles mediante `RuleDefinitionCodec`.
- Ediciones con `rowVersion`; conflictos devuelven 409.
- Errores del backend visibles en la UI, incluido el ejemplo probado:
  `Round 99 out of range 1..30`.

### Implementacion y API

- `setup/rules/RuleScopeService`: validacion, resolucion efectiva y
  `withScopedRules`.
- `RuleScopeRepository`, `RuleScopeDtos`, `RuleScopeController`.
- Changelog `007-rule-scope-assignment.yaml`: parametros, excepciones, versionado,
  catalogo P2 VenueUnavailable / FixedHomeDate y permisos MATCH.
- Integracion con `DrawBatchService` (principal y dependientes),
  `PlanningJobService` y el endpoint de validacion.
- La API parte de `/api/setup`:
  - GET/POST `/federations/{fed}/rule-assignments`.
  - Filtros GET: `competitionId`, `season`, `scopeType`.
  - PUT/DELETE `/rule-assignments/{id}`.
  - GET `/competitions/{cid}/seasons/{season}/effective-rules`.
- Estados efectivos: `APPLIED`, `EXCEPTED`, `NOT_APPLICABLE`, `DUPLICATE`,
  `DISABLED`, `DISABLED_TYPE`; se devuelve origen, motivo, fase y prioridad.
- Front: `/restrictions/scopes` permite alta, edicion, activar/desactivar,
  eliminar y excluir; formularios segun parametros del catalogo, separados P1/P2.
- Front: `/restrictions/effective` consulta el endpoint y muestra origen, estado
  y motivo; ya no muestra el aviso antiguo de que no hay herencia.
- Modelos y llamadas en `core/setup/setup.models.ts` y `setup-api.service.ts`.

## Correcciones relacionadas

- Liquibase inicial: correcciones en 003/004, entre ellas PK compuestas y bloque
  MSSQL con `splitStatements:false`.
- Nuevas funcionalidades: changelogs 006/007. Para corregir errores de changelogs
  iniciales, el usuario pidio corregir los originales, no sumar parches sin fin.
  Distinguir esa peticion de introducir una funcionalidad nueva.
- `CheckProcessor`: corregido indice de MatchNotInRound al pasar a segunda vuelta.
  Con 16 equipos la jornada 16 ya no provoca IndexOutOfBounds; se usa modulo
  sobre el numero de jornadas de la primera vuelta.

## Evidencia de pruebas (historica, no repetir como nueva sin ejecutarla)

- Backend: `RuleScopeServiceTest`, 3 tests; `DrawEngineTest`, 5 tests.
- PostgreSQL limpio con 76 changesets en la prueba de 007.
- End-to-end CNPFF D1+D2: 1000 candidatos, ZIP de unos 4.7 MB y 2003 entradas;
  elegir candidato 7 escribio 132 fixtures. P2 bloqueo por jornadas sin fecha.
- End-to-end Botola D1: 20 candidatos; sin violar restricciones de FAR/RCA en
  jornadas 1,2,16 ni RCA/WAC de la liga.
- Comprobados errores 400/409 y bloqueo optimista.
- Front: `npx ng build` correcto; navegacion 5/5 con
  `npx ng test --watch=false --browsers=ChromeHeadless --include src/app/components/header/navigation.spec.ts`.
- Navegador real con proxy: alta MATCH PlayTogether WAC/RSB, error de jornada 99
  y consulta de reglas efectivas P1/P2.
- Chrome sin headless fallo por falta de DISPLAY; usar ChromeHeadless.
- Otros tests de front habian fallado por un problema previo de zone.js/testing;
  no afirmar que se ha validado toda la suite.
- Se borraron las 7 asignaciones de prueba y los 2 lotes de prueba Botola.
  Listados finales vacios y validate Botola D1 2026-27 devolvio `[]`.
  Esto no significa que toda la BD este vacia ni borra otros datos de negocio.
- Backend y front usados para esas pruebas se pararon. No asumir que siguen
  activos en una sesion posterior.

Tests backend dirigidos:
`sh mvnw -q -Dtest=RuleScopeServiceTest,DrawEngineTest -Dsurefire.failIfNoSpecifiedTests=false test`.

## Pendientes y limites conocidos

1. Separar carga de catalogos de referencia y datos de configuracion concretos.
   Revisar properties antiguos como fuente, pero confirmar temporada, ambito,
   derbis y fechas antes de cargarlos. No crear fechas ficticias. No introducir
   gestion de estadios en el frontend.
2. CNPFF necesita fechas de jornadas para completar P2; el lote P1 no las inventa.
3. Validar en MSSQL el changelog 007 y el sorteo completo. Revisar SQL especifico
   de PostgreSQL en `GenerationRepository` y `SeasonSetupRepository`.
4. Antecedente tecnico Botola D1+D2: cuatro enlaces de estadio se ignoran por
   conflictos con derbis: CODM/IZK, RCA/JSS, RSB/MCO y WST/USYM. El usuario ha
   descartado introducir su gestion o confirmacion en el frontend. Conservar
   esta limitacion documentada del motor; no afirmar que se resolvio ni eliminar
   por ello las restricciones antiguas.
5. Alinear el flujo implementado con la decision confirmada: P1 es solo
   inicializacion desde cero, antes de P2. No desarrollar un flujo de cambiar el
   sorteo con la competicion en marcha ni una UX de versiones P2 desfasadas.

## Interfaz multilingue y fichas editables (peticion del 19 de octubre)

El usuario compartio una captura del mockup y confirmo:

- Selector de idioma arriba a la derecha con **espanol, frances e ingles**.
  El codigo ISO del espanol es `es` (no `sp`).
- Las traducciones se sirven desde archivos del backend; el frontend elige el
  idioma y lo recuerda como preferencia, sin guardar datos de negocio localmente.
- Una fila de entidad debe abrir su **ficha editable**, no solo un detalle de
  consulta. La ficha puede exponer mas campos que la tabla y debe permitir
  consultar/asignar sus restricciones reales.
- No convertir resultados calculados ni logs en entidades editables; no anadir
  un editor de logs. Mantener la decision previa de no gestionar estadios.
- Conservar los codigos, nombres introducidos por el usuario y valores tecnicos
  del motor: se traducen sus etiquetas, no los identificadores persistidos.

### Implementacion de idiomas

- Recursos del backend: `src/main/resources/i18n/es.json`, `fr.json`, `en.json`.
  El punto de partida reutiliza los diccionarios del mockup; se amplian para
  las pantallas nuevas. No cambiar los originales del mockup para adaptar la API.
- `GET /api/i18n/{language}` devuelve JSON UTF-8 y `Content-Language`.
  Solo se permiten `es`, `fr`, `en`; un recurso no permitido devuelve 404.
  No depende del motor PostgreSQL/MSSQL ni requiere cambiar el esquema de BD.
- Front: `core/services/translation.ts` es la fuente de estado de idioma.
  Carga desde esa API; ya no carga los antiguos assets ingles/arabe.
- `TranslatePipe` admite parametros `{{nombre}}`. Los componentes compartidos
  PageHead y Kpi traducen sus propias etiquetas. Las fechas visibles, nombres
  de meses y dias y titulos de navegador siguen el idioma.
- El shell tiene selector, estado de carga, error explicito y reintento.
  Una descarga fallida no se presenta como exito: se conserva el idioma que
  funcionaba y se puede reintentar el idioma solicitado.
- Las solicitudes de cambio se cancelan con switchMap para impedir que una
  respuesta anterior sobrescriba la ultima eleccion.
- `pnpm check:i18n` comprueba las claves usadas, paridad en los tres archivos y
  parametros de interpolacion. `node scripts/check-i18n.mjs --strict` comprueba
  tambien texto y atributos humanos sin traducir en las plantillas.
- Pruebas del frontend: el runner carga zone.js/testing solo para tests
  existentes con fakeAsync; la aplicacion sigue con deteccion zoneless.
- Los tres archivos tienen 887 claves; la auditoria comprueba 541 frases
  referenciadas. Los diagnosticos arbitrarios del servidor y los datos
  introducidos por el usuario no se convierten en claves de traduccion.

### Alcance de las fichas

Federaciones, competiciones y entidades de configuracion de la temporada
(equipos, partidos, jornadas, bloqueos, partidos externos y franjas horarias).
Usar las APIs y el guardado/versionado existentes; no crear formularios que
simulen guardar. Mantener navegacion de fila con raton y Enter/Espacio y evitar
que los botones o controles de una fila disparen otra navegacion.

Los enlaces de restricciones transportan federacion, competicion y temporada,
y cuando procede `scopeType`, `team`, `a`, `b`, `round`, `assignmentId`.
No crear nuevos ambitos inexistentes solo para representar un enlace de ficha.

Implementado:

- Ruta generica `/entities/:kind/:key`, con `federationCode`, `competitionId`
  y `season` para identificar el contexto. Las colecciones que pueden contener
  duplicados verifican identidad y posicion al resolver su elemento.
- Fichas editables de equipos, partidos, jornadas, bloqueos, partidos externos
  y franjas horarias, reutilizando el guardado real de configuracion.
- Edicion de federaciones y competiciones. Alta de competicion mediante
  `POST /api/federations/{fed}/competitions` con `{id, code, name}`; cambio de
  nombre mediante `PUT /api/competitions/{id}` con `{name, originalName}` para
  detectar cambios concurrentes.
- Enlaces desde las fichas hacia asignaciones y restricciones efectivas con
  contexto y filtros; los partidos usan el ambito MATCH, no un ambito nuevo.
- Navegacion con raton, Enter y Espacio; los controles propios de la fila no
  abren otra ficha. Resultados calculados y logs siguen siendo de consulta.

Limites: no hay API de alta de temporada; modalidad y division se derivan.
Los codigos protegidos permanecen de solo lectura. No se ha anadido gestion de
estadios ni un flujo de sustitucion de P1 durante una competicion en marcha.

### Verificacion final

- Frontend: 58/58 tests dirigidos correctos, incluyendo idiomas, navegacion,
  calendario mensual, shell, guardado de configuracion, fichas y restricciones.
  El test de colapso fija expresamente el modo escritorio para no depender del
  ancho del navegador; no se cambia el comportamiento responsive de la UI.
- Backend: 5/5 tests dirigidos correctos, de I18nController y
  CompetitionAdminController.
- Build de produccion correcto. Auditorias de idiomas normal y estricta
  correctas, con paridad de claves y parametros en es/fr/en.
- Navegador con API simulada: cambios de idioma y titulo, apertura de fichas,
  peticion PUT de equipo y enlaces de restricciones. El formulario de partido
  tiene una regresion automatizada que comprueba los equipos seleccionados
  realmente en sus controles.
- Estos resultados no equivalen a ejecutar toda la suite ni a comprobar de
  nuevo la persistencia en una BD real.

### Limite de la validacion de esta peticion

El primer arranque Java de comprobacion anuncio una sesion de depuracion, pero
esta termino antes de que el backend respondiese en 8080. No se obtuvo evidencia
suficiente para atribuir una causa concreta. El usuario **rechazo otro intento
de arranque**: no reintentar ni recrear la BD para validar esta tarea.
Las pruebas MockMvc no necesitan levantar el servicio. Las pruebas de navegador
se realizan con API simulada explicitamente, no son una verificacion nueva de
persistencia contra la BD real.

### Verificacion con backend real (Postgres, conservando datos)

Comprobado despues, con el backend en 8080 arrancado **sin borrar la BD**:

- Corregido: el selector de idioma mostraba "Espanol" aunque la app estaba en
  otro idioma, porque `[value]` del `<select>` se aplicaba antes de pintar las
  opciones. Ahora cada `<option>` usa `[selected]` en `app.html`.
- Corregidas dobles traducciones: `app-kpi` acepta `labelParams` y
  `app-page-head` acepta `subParams`, en lugar de recibir texto ya traducido
  (dashboard y horarios).
- Los motivos de las reglas efectivas que genera `RuleScopeService` en ingles
  se traducen en el front con `core/setup/rule-reason.ts`, con 12 claves nuevas
  en es/fr/en (899 claves).
- Navegador: es/fr/en cargan desde `/api/i18n/*` y no quedan avisos de
  traduccion en dashboard, horarios ni reglas efectivas. La fila de cada
  pantalla abre su ficha con enlaces de restricciones:
  - Competiciones: probado editar y guardar en la BD real el nombre de
    BOTOLA_D1, y despues restaurarlo.
  - Federaciones, equipos, partidos, reglas horarias y temporada abren su ficha.
  - Jornadas, otras competiciones y asignaciones por ambito no tenian filas con
    datos en esta BD.
- Tests: front completo 64/67. Los 3 fallos de `SetupContext` ya fallaban sin
  estos cambios (peticion `rule-types` pendiente o timeout). Backend:
  `I18nControllerTest` 2/2, `CompetitionAdminControllerTest` 3/3 y
  `RuleScopeServiceTest` 3/3.
- El frontend no tiene formularios ni controles para gestionar estadios. El
  tipo heredado `VenueUnavailable` sigue en el catalogo API, pero no se puede
  asignar, editar ni eliminar desde el formulario de restricciones. La pantalla
  general de restricciones aun lo muestra como fila informativa de catalogo;
  no es un editor.
- En esta verificacion Postgres aun quedaban pendientes MSSQL y tres tests de
  `SetupContext`; la validacion MSSQL posterior se documenta mas abajo. Los
  tres fallos de `SetupContext` se reprodujeron como preexistentes.

### Inspeccion MSSQL de solo lectura previa al arranque (19 de octubre)

- El contenedor MSSQL responde en el puerto 11434 y la base consultada es
  `calendar_ms_test`.
- La tabla `DATABASECHANGELOG` tiene aplicados los cambios hasta
  `005-chg007-federation-persistence`; el changeSet
  `rule-scope-assignment-params` de `007-rule-scope-assignment.yaml` aun no
  aparece aplicado. La tabla `rule_scope_assignment` solo contiene las columnas
  del esquema anterior.
- Se hicieron consultas SQL de solo lectura; no se arranco el backend MSSQL ni
  se modifico la base. Este estado quedo superado por la migracion y pruebas
  posteriores descritas en **Estado auditado frente a la peticion integral**.

### Verificacion de controles de estadios en el frontend

- Comprobacion visual en `/teams`, `/draw`, `/time-rules`,
  `/restrictions/scopes` y `/constraints`: no aparecen formularios ni controles
  de gestion de estadios. La pantalla general `/constraints` mantiene la fila
  informativa `VenueUnavailable`; las pantallas de asignacion no permiten
  gestionarla.
- Las pruebas dirigidas `constraints-page.spec.ts` y
  `rule-assignments-page.spec.ts` pasan (9/9).

## Requisito de flujo integral confirmado (19 de octubre de 2026)

El usuario confirma que el configurador debe ser operativo de extremo a extremo,
no solo una coleccion de pantallas:

- Administrar federaciones, ligas/competiciones y equipos con persistencia real.
  Distinguir el maestro de equipo de su inscripcion/configuracion en cada
  competicion-temporada.
- Separar claramente **configuracion** (temporada, jornadas/fechas, franjas,
  participantes, compromisos externos, bloqueos) de **restricciones** (tipos y
  asignaciones P1/P2 por federacion, grupo, liga/temporada o partido).
  Validar que cada motor aplica las restricciones efectivas.
- **P1**: generar aproximadamente 1000 candidatos de emparejamientos, guardar
  el lote, exportarlo en ZIP y permitir elegir uno. La eleccion inicializa la
  competicion antes de ejecutar P2; no sustituir un sorteo en una competicion
  en marcha.
- **P2**: generar fechas y horas dia a dia, considerando partidos externos
  (Champions/CL, FIFA, etc.) y sus descansos. Permitir al operador cambiar fecha
  y hora de un partido concreto, validar los conflictos y conservar el cambio
  al volver a ejecutar el planificador.
- Criterio de movimiento P2 adoptado del valor recomendado del formulario:
  conflictos estructurales/obligatorios bloquean; los avisos de reglas no
  obligatorias solo se aceptan tras mostrar el motivo y pedir confirmacion.
  Aun no esta implementado ni probado.
- Importar/exportar configuracion **y** restricciones. Debe existir previsualizacion,
  validacion y guardado transaccional; el JSON `SeasonSetup` y el Excel actual
  no incluyen por si solos las asignaciones de restricciones por ambito.
- Aclaracion del usuario (19/10/2026): el paquete sigue el ambito activo y su
  jerarquia. Federacion = todo el subarbol; liga/competicion = todo lo que
  cuelga de esa liga; equipo = datos asociados a ese equipo. En la pantalla de
  restricciones se exportan las reglas del ambito de federacion o liga que se
  esta administrando. La importacion debe exigir el mismo tipo y ambito: no se
  puede aplicar un paquete de reglas de liga estando en el ambito de federacion,
  ni viceversa. No crear un ambito `TEAM`; mantener los tipos de asignacion
  existentes y empaquetar solo sus datos asociados validos.
- Aclaracion del usuario sobre aplicar paquetes: la importacion es aditiva
  (upsert), nunca borra registros que no vengan en el paquete. Una restriccion
  con el mismo tipo y los mismos parametros dentro del mismo ambito se
  actualiza; si no existe, se agrega; el mismo tipo con parametros diferentes
  se conserva como otra regla. Mostrar altas/cambios en la previsualizacion y
  aplicar todo el paquete validado en una transaccion.
- Probar recorridos completos en PostgreSQL y MSSQL, sin usar arranques que
  borren la BD.

### Estado auditado frente a la peticion integral

- **Implementado**: paginas/APIs de alta-edicion-estado de federaciones; alta y
  cambio de nombre de competiciones; equipos/participantes editables dentro de
  la configuracion de temporada (no hay aun CRUD de maestro independiente ni
  API de alta de temporada); guardado real de `SeasonSetup`;
  asignacion/edicion/exclusion/herencia de restricciones; lotes P1 con 1000
  por defecto, persistencia, ZIP, eleccion y borrado de descartados; P2 usa
  eventos externos y restricciones heredadas.
- El Front `/draw` ya esta conectado a los lotes: crea y sigue trabajos,
  muestra progreso, pagina candidatos, descarga ZIP, elige candidato y ofrece
  cancelar/eliminar/limpiar descartados. Esto confirma wiring de codigo, no aun
  E2E navegador del flujo completo.
- **Probado en MSSQL con conservacion de datos**: Liquibase aplico los dos
  changesets pendientes, incluido `rule-scope-assignment-params`; siguen 48
  equipos y 744 fixtures. Crear/listar/aplicar/borrar una asignacion temporal
  funciono y no dejo filas de prueba. `effective-rules` y `draw/validate`
  respondieron HTTP 200. `DrawEngineTest` 5/5 y `RuleScopeServiceTest` 3/3.
- La validacion MSSQL de draw devuelve cuatro avisos sobre enlaces de estadio
  incompatibles con derbis. Es una limitacion del motor ya conocida; no
  gestionarla en el front ni ocultar su efecto en backend.
- **Probado en MSSQL**: un lote temporal genero los 1000 candidatos solicitados
  (1000/1000), el endpoint de exportacion produjo un ZIP valido de 7,866,611
  bytes y el lote de prueba fue borrado sin elegir candidato. No quedaron
  candidatos temporales en la base.
- **Pendiente**: el detalle de fixture incluye campos para fecha y hora, pero
  el flujo seguro para mover manualmente un partido sigue sin estar resuelto.
  El PATCH acepta fecha/hora y las persiste en `fixture`/`round_date`, pero las
  escribe directamente, no compara ni incrementa `rowVersion` y no valida
  conflictos de calendario; no debe considerarse implementado el movimiento
  manual seguro. Las pruebas legacy confirman que CL/FIFA son compromisos
  externos y que el motor programa alrededor de partidos ya confirmados.
- **Pendiente**: el import/export actual cubre `SeasonSetup` (JSON) y puente
  Excel, no paquetes jerarquicos de administracion ni el conjunto de
  asignaciones de restricciones. El alcance y el comportamiento upsert ya
  fueron definidos por el usuario; falta disenar y probar la previsualizacion
  transaccional y el rechazo de paquetes con ambito incompatible.
- El contrato mockup se actualizo con CHG-018 (P1), CHG-019 (movimiento manual
  P2) y CHG-020 (paquetes jerarquicos). El mockup visual sigue pendiente de
  alineacion antes de modificar Front/Back para esos CHG.

### Catalogo P1 y datos de demostracion

Comprobacion de solo lectura en los backends PostgreSQL (8080) y MSSQL (18082):

- FRMF expone los nueve tipos P1 solicitados: `DerbyTeams`,
  `MatchNotInRound`, `SeparateMatches`, `LimitHomeTogether`, `PlayTogether`,
  `NotConsecutiveFarAway`, `LimitAgainstTeamsInRounds`,
  `LimitAgainstTeamsInRoundsHome` y `ForcePositions`. El catalogo tambien
  conserva tipos de P2 e internos ya existentes.
- BOTOLA_D1 / 2026-27 ya tiene 16 equipos, 240 fixtures, siete derbis y dos
  reglas P1 de liga (`SeparateMatches` y `MatchNotInRound`). El endpoint de
  reglas efectivas devuelve nueve reglas aplicadas en ambos motores: esas dos
  reglas mas los siete derbis. Los datos coinciden con la configuracion legacy
  proporcionada; no se añadieron restricciones especulativas.
- El lote MSSQL de 1000 candidatos y su ZIP ya se verificaron anteriormente
  con la configuracion existente. No se ha probado en lote una restriccion
  creada recientemente desde el front.
- Decision del usuario (19/10, 18:07): **hasta nueva orden se pueden borrar las
  BD de laboratorio**, porque aun no contienen datos reales. Los catalogos deben
  cargarse desde CSV al inicializar la BD.
- Catalogos de referencia en `src/main/resources/db/changelog/data/`:
  `federation.csv` (FRMF, DEMO), `rule_type_catalog.csv` (12 tipos FRMF: nueve P1,
  `HalfMatchesAtHome` interno y dos P2; DEMO con los nueve P1) y
  `rule_type_allowed_scope.csv` (57 filas). Se cargan con `loadData` dentro
  de los changesets existentes 004 (catalogo) y 005 (federacion); se quitaron
  los `insert` de 004, 005 y 007 y se elimino el changeset 008 para no
  multiplicar changelogs. Cambian los checksums: las BD creadas antes deben
  recrearse con el arranque "BD limpia".
- Crear una federacion por API copia los nueve tipos P1 de FRMF y sus ambitos
  en la misma transaccion. Los errores de lectura de catalogo ya no se
  ocultan como lista vacia.
- Verificado con reset de `calendar_pg_test` y `calendar_ms_test`: en ambos
  motores FRMF 12 tipos/30 ambitos, DEMO 9/27, JSON de parametros y booleanos
  correctos; cuatro competiciones y nueve reglas efectivas de BOTOLA_D1; crear
  una federacion nueva dio 9 tipos/27 ambitos (federaciones de prueba borradas).
  Compilacion y tests dirigidos `RuleScopeServiceTest` y
  `CompetitionAdminControllerTest` correctos; `SetupDatabaseIntegrationTest`
  compilado pero no ejecutado.

## Copia de recuperacion solicitada (19 de octubre de 2026)

- Se volvieron a leer desde disco el repositorio, el changeset 008, su include
  en Liquibase y las pruebas nuevas. Los archivos conservan los cambios.
- Los backends en 8080 y 18082 respondieron HTTP 200 a una consulta de
  competiciones. Eso no verifica el acceso desde otra ventana o desde Windows.
- Hay cambios sin commit en backend y frontend, incluidos archivos nuevos.
  Mockup y prompts son carpetas sin repositorio Git propio.
- Copia local de fuentes, cambios y contexto:
  [guia de recuperacion](../../backups/calendar-recovery-20261019-174732/RECUPERACION.md).
  Consultar el manifiesto y la verificacion de esa copia para comprobar su
  integridad. Se excluyen credenciales, caches y salidas generadas.
- No es una copia de las bases de datos. No se hicieron resets, reinicios,
  commits ni restauraciones sobre archivos existentes.
- `README_PROMPTS.md` del backend y otros documentos antiguos ya aparecian
  borrados en Git antes de esta comprobacion; no es una perdida nueva detectada
  ahora. No se restauraron esas eliminaciones automaticamente.

## Retomar tras reinicio (guardado 19/10/2026 18:27)

- Todo el trabajo esta en disco y SIN commit en backend y frontend. Copia
  verificada en `/home/jose/morocco/backups/` (usar la `calendar-*-recovery-*` o `calendar-recovery-*`
  mas reciente; incluye guia, manifiesto y SHA-256).
- Tras reiniciar: comprobar Docker (`morocco-new-postgres` 15432,
  `morocco-new-mssql` 11434) y lanzar el compuesto
  `Back: Postgres + MSSQL (conserva datos)`. Las BD ya estan creadas con los
  catalogos CSV. Si Liquibase rechaza checksums, usar el compuesto de BD limpia
  (permitido hasta nueva orden). El front (4200) se lanza aparte.
- Pendientes, en orden: smoke del mockup; lote P1 con una regla nueva creada en
  el front; CHG-019 movimiento manual P2 seguro; CHG-020 paquetes jerarquicos;
  ejecutar `SetupDatabaseIntegrationTest`.

## Pruebas de administracion y planificacion incremental (19/10/2026)

Peticion del usuario: probar altas/bajas/ediciones de federaciones, ligas y
participantes, y generar fechas/horas por tramos sin perder los confirmados.

### Implementado y probado

- `ApplicationFlowsDatabaseIntegrationTest`: 14 pruebas nuevas con MockMvc,
  JDBC y motor real. Cada caso crea y elimina su federacion, competicion,
  temporada sintetica de seis equipos y diez jornadas. La temporada padre se
  inserta como fixture SQL porque todavia no hay API de alta de temporada.
- Federacion: alta, edicion, baja logica INACTIVE, filtro de activos, reactivacion,
  rechazo de estado invalido y conservacion de sus ligas/datos.
- Liga: alta, lectura, cambio de nombre, conflicto de edicion obsoleta, ID
  duplicado, federacion inexistente y codigos invalidos. Se comprueba que DELETE
  devuelve 405: es una prueba de la limitacion actual, NO una baja implementada.
- Equipos: alta del maestro al agregar un participante, lectura/edicion de
  nombre y atributos, baja/reinscripcion en temporada sin borrar el maestro
  compartido; rechazo de generacion cuando la baja deja el sorteo inconsistente.
  Tambien se verifica que un guardado obsoleto no sobrescribe el nuevo estado.
- P2: generar cinco jornadas (15 partidos), confirmar cada partido por PATCH,
  aprobar la version y ampliar a diez (30 partidos). Se comprueban exactamente
  fechas/horas de los primeros 15, emparejamientos, dias disponibles, descansos,
  maximo por dia y nuevas franjas para los restantes. Al aprobar la segunda
  version solo queda una APPROVED y la anterior queda ARCHIVED.
- El Excel P2 descargado se abre con POI y sus 30 filas de partidos coinciden
  con el resultado persistido (jornada, fecha, hora, local, visitante).
- Se prueba FIFA (blackout), un partido externo CL con tres dias completos de
  descanso (distancia > 3 dias) y la franja 22:00 de Ramadan, asi como fechas
  ausentes y jornadas fuera de rango.
- Fallos encontrados y corregidos: ausencia opcional de Ramadan provocaba NPE;
  ahora no se aplica el intervalo si no esta configurado. El mapper P2 de BD
  activa `failOnNoFeasibleAssignment`: un calendario sin solucion queda FAILED
  con motivo y sin version parcial guardada como correcta. Los parametros
  legacy por properties conservan su comportamiento. Prueba unitaria nueva
  para asegurar ese flag y prueba API para el resultado imposible.
- Resultado final PostgreSQL: **46 pruebas, cero fallos/errores/omitidas** en
  ApplicationFlowsDatabaseIntegrationTest, SetupDatabaseIntegrationTest,
  EngineParityTest (cuatro ligas identicas al legacy), EngineTest,
  SetupToEngineMapperTest, PlanningParametersTest, CompetitionAdminControllerTest
  y RuleScopeServiceTest. No es la suite completa ni una prueba de navegador.
- Dos tests existentes que modificaban federacion/configuracion ahora hacen
  rollback transaccional. Los trabajos asincronos se prueban sin transaccion
  envolvente para que el motor vea los datos confirmados en BD.

### Limitaciones verificadas, no resueltas

- Aprobar NO confirma automaticamente los fixtures de esa liga. Una prueba de
  caracterizacion demuestra que sin PATCH de confirmacion, ampliar de 5 a 10
  puede cambiar las primeras cinco (se cambian franjas para no depender de que
  un calculo determinista repita casualmente el mismo resultado). La integracion
  automatica de version aprobada en la siguiente planificacion sigue pendiente.
- No hay baja de liga ni CRUD independiente de maestro de equipos ni API de
  alta de temporada. No confundir las pruebas de participante/baja logica con
  operaciones que no existen.
- Sonda MSSQL en BD exclusiva: 8 pruebas ejecutadas, 4 pasan, 1 falla y 3 dan
  error. Los catalogos/seed y alta de federacion pasan. Alta de liga falla en
  `SetupCatalogRepository` por `rtc.enabled = TRUE`; guardado de temporada
  falla en `SeasonSetupRepository` por `CASE WHEN :maj THEN`; job P2 falla en
  `PlanningJobService` por `now()`. Tambien existen otras expresiones especificas
  de PostgreSQL (`RETURNING`, `ON CONFLICT`, `jsonb`) en los repositorios, sin
  afirmar que ya se hayan recorrido todas. Falta adaptar y volver a probar
  esas operaciones en MSSQL; cargar CSV correctamente no garantiza P2 en MSSQL.
- Se usaron exclusivamente `calendar_regression_test` en los contenedores
  morocco-new-postgres y morocco-new-mssql. `spring.liquibase.drop-first=true`
  sigue activo en tests: NUNCA apuntar estas pruebas a una BD que deba conservarse.
  Las BD de demo calendar_pg_test/calendar_ms_test no se recrearon en este turno.
- Ejecucion reproducible: configurar DB_URL, DB_USERNAME y DB_PASSWORD para una
  BD PostgreSQL exclusiva de pruebas y ejecutar `bash ./mvnw -Dtest=ApplicationFlowsDatabaseIntegrationTest,SetupDatabaseIntegrationTest,EngineParityTest,EngineTest,SetupToEngineMapperTest,PlanningParametersTest,CompetitionAdminControllerTest,RuleScopeServiceTest test`.
  Para MSSQL, usar su URL/credenciales y `-Dspring.profiles.active=mssql` (fallos
  anteriores pendientes). No copiar credenciales en documentacion ni prompts.

## CRUD protegido y compatibilidad PostgreSQL/MSSQL (19/10/2026, posterior)

Esta seccion sustituye las limitaciones historicas sobre CRUD ausente y SQL
no portable de la seccion anterior. Peticion expresa: implementar las operaciones
faltantes, borrar SOLO cuando no existan referencias e informar del motivo y
de como resolver el bloqueo. No se permite cascada automatica desde estas APIs.

### Implementado y verificado en backend

- `MasterDataController` / `MasterDataService`: listado, ficha, alta, edicion y
  baja del maestro de equipos (`/api/teams`); borrado de federacion y liga;
  listado y alta/baja de temporadas. Se conservan alta/edicion/desactivacion de
  federacion y alta/edicion de liga ya existentes. Nueva ficha individual
  `GET /api/federations/{code}` y validacion de nombre, codigo, pais y zona horaria.
- Alta de temporada por `POST /api/setup/competitions/{cid}/seasons/{season}`,
  con anios consecutivos YYYY-YY y fechas coherentes. Crea configuracion vacia,
  sin inventar equipos/jornadas/fixtures; no gestiona estadios. Las pruebas
  ahora usan esta API en vez de insertar el padre por SQL.
- Equipo: PUT requiere nombre y ciudad originales (ciudad nullable) y rechaza
  ediciones obsoletas. Los codigos no cambian; baja de participante y baja de
  maestro son operaciones distintas. Una baja de participante conserva el
  maestro compartido, las referencias existentes y la validacion del sorteo.
- Borrado protegido transaccional SERIALIZABLE: verifica temporadas,
  participantes, partidos, reglas, listas de equipos, dependencias, versiones,
  trabajos y lotes P1. No borra datos relacionados. FRMF se conserva como fuente
  del catalogo inicial de nueve tipos P1 para nuevas federaciones; puede
  desactivarse. Los catalogos de una federacion realmente vacia se retiran con
  esa federacion. El identificador global de temporada compartido se conserva.
- Las restricciones padre con excepciones ahora devuelven 409, en vez de
  eliminar tambien sus excepciones. Quitar primero las excepciones o desactivar
  el padre. Creacion/edicion de asignaciones y guardado de configuracion usan
  SERIALIZABLE para preservar referencias ante operaciones concurrentes.
- `SetupProblemHandler`: errores RFC7807 con `detail`, incluidos los
  ResponseStatusException de administracion. Borrados bloqueados contienen
  `blockers: string[]` con cantidades y etiquetas legibles, motivo, orientacion
  y confirmacion de que nada se borro. Duplicados, integridad y concurrencia
  se devuelven como 409 explicitos sin exponer SQL ni credenciales.
- SQL portable para booleanos, timestamps y claves generadas. Snapshot JSONB
  en PostgreSQL/NVARCHAR en MSSQL con tipo JDBC adecuado; sin CAST JSONB en
  el INSERT ni operadores `::text`, `RETURNING` u `ON CONFLICT` en estos
  recorridos. Actualizacion/insercion del maestro de equipo dentro del
  reemplazo transaccional. Sin nuevos cambios de esquema ni Liquibase.

### Pruebas ejecutadas, no solo compilacion

- **60 pruebas dirigidas pasan en PostgreSQL y las mismas 60 en SQL Server**,
  cero fallos, errores u omitidas: ApplicationFlowsDatabaseIntegrationTest (28),
  SetupDatabaseIntegrationTest (6), EngineParityTest (4), EngineTest (7),
  SetupToEngineMapperTest (5), PlanningParametersTest (4),
  CompetitionAdminControllerTest (3), RuleScopeServiceTest (3).
- Incluyen CRUD real, duplicados, inexistentes, campos invalidos, concurrencia
  optimista, borrado vacio 204, borrados referenciados 409 con motivo/cantidades
  y datos conservados; referencia a equipo solo desde regla de federacion;
  regla padre/excepcion y borrado explicito; restauracion de snapshot sin
  perder partidos/ficheros; cancelacion de job en cola sin version ni cambios;
  proteccion de equipos referenciados solo desde definiciones legacy, incluso
  sin columnas de parametros rellenas. Se reutiliza RuleDefinitionCodec, no
  una busqueda de subcadenas que confunda codigos con numeros de jornadas.
  Verifican tambien 5 -> confirmar -> aprobar -> 10, Excel P2 con 30 partidos
  exactos, FIFA, Champions y Ramadan.
- Tras completar el handler de errores, se repitieron tres casos de API en
  cada motor: estado invalido/nulo, conflictos de liga y datos invalidos,
  comprobando `application/problem+json` y `detail`.
- Exclusivamente `calendar_regression_test` en morocco-new-postgres (15432) y
  morocco-new-mssql (11434). Las BD de demo no se borraron para estas pruebas.
  No se necesito H2. No se arranco una nueva instancia Java de servidor.
- Comando reproducible y endpoints en [README](../README.md). Los tests siguen
  usando drop-first=true: nunca apuntarlos a una BD que deba conservarse.

### Frontend y traducciones

- CRUD integrado en el frontend separado: catalogo maestro de equipos y
  participantes por temporada diferenciados, temporadas desde la ficha de liga,
  borrados protegidos y confirmaciones antes de solicitar la baja.
- El checkout actual usa Angular Material / UiControls, NO PrimeNG instalado.
  Se preservaron sus componentes; no se hizo migracion de biblioteca ni se
  agregaron dependencias. Los errores muestran detail original y cada blocker,
  con orientacion localizada en 409, aviso de que nada fue eliminado y sin
  exito optimista. Una restriccion padre no anuncia borrado en cascada.
- Informe de validacion frontend: 31/31 pruebas dirigidas y build de produccion
  correctos; se conservan advertencias preexistentes de presupuesto bundle/CSS.
  Sin servidores ni pruebas en watch de esa tarea.
- Diccionarios backend es/fr/en ampliados a 946 claves por idioma (47 nuevas
  para CRUD/borrados); I18nControllerTest 2/2 correcto: paridad, parametros y
  servicio HTTP. Auditorias frontend `pnpm check:i18n` y
  `node scripts/check-i18n.mjs --strict` correctas: 566 frases referenciadas,
  paridad en/es/fr y parametros verificados, sin texto visible sin traducir.
- La orientacion de baja de equipo incluye inscripciones Y otras referencias:
  un equipo sin inscripciones puede estar bloqueado por una regla global,
  definicion legacy o candidato. El detalle y los blockers originales se
  conservan completos; el encabezado y los pasos se muestran traducidos.
- Los avisos son los componentes compartidos de error ya existentes, multilinea,
  desplazables y con mayor tiempo de lectura; no una migracion a PrimeNG.

### Estado pendiente, sin atribuirlo a estas pruebas

- Aprobar P2 sigue sin confirmar automaticamente los fixtures: la prueba de
  caracterizacion conserva esta limitacion. Hay que confirmar los partidos
  por PATCH antes de ampliar el tramo si se quieren conservar sus fechas/horas.
- CHG019/020, prueba P1 con restriccion recien creada desde frontend y smoke del
  mockup.
- No se afirma haber ejecutado toda la suite, un navegador contra servidor
  real ni un nuevo lote de 1.000 P1 en este turno.
- Copia final de recuperacion de este trabajo:
  [calendar-crud-recovery-20261019-203722](../../backups/calendar-crud-recovery-20261019-203722/).
  583 archivos de backend, frontend y memoria; manifiesto SHA-256 verificado.
  No incluye credenciales, dependencias, compilados ni bases de datos.

## Plantillas Excel P1: comprobacion inicial (2026-10-19; estado inicial superado)

- Se conservan cuatro plantillas en `src/main/resources/exportTemplate`:
  `MoroccoBotolaD1.xlsx`, `MoroccoBotolaD2.xlsx`, `MoroccoCNPFF1.xlsx` y
  `MoroccoCNPFF2.xlsx`. Comprobada su estructura XLSX/XML legible, con una hoja
  y dos imagenes por fichero; no equivale a una prueba visual ni de exportacion.
- El exportador antiguo `AssignmentsExcelMorocco` lee `templatePath` desde
  properties, carga sorteos de `assignFolder` y rellena la plantilla con el
  identificador del candidato y los equipos de ida/vuelta, omitiendo BYE.
  Sus coordenadas son fijas; existen variantes para distintos tamanos de liga.
- Son plantillas de presentacion de emparejamientos P1, no una entrada del
  motor de fechas/horarios P2. Conservarlas para recuperar el formato antiguo.
- En esta comprobacion inicial, `DrawBatchService.writeZip` solo exportaba CSV.
  Ese estado fue sustituido por la integracion descrita a continuacion.
- El puente `LegacyExcelBridge.export` exporta la configuracion actual mediante
  `LegacyResultFiles.fullCalendarXlsx`; esta ruta crea un libro nuevo con
  `SchCalendarExcelIO`, sin usar las cuatro plantillas P1.

### XLSX P1 integrado y probado (2026-10-19, 21:51)

- Implementado `DrawCandidateExcel` y conectado a `DrawBatchService.writeZip`.
  El ZIP conserva todos los CSV/agregados y suma un XLSX por candidato/liga
  en `candidatos/NNNN/calendar_<codigo>_NNNN.xlsx`. No requiere regenerar
  lotes existentes ni cambia el sorteo, restricciones, eleccion o persistencia.
- Para `BOTOLA_D1`/`BOTOLA_D2` con 16 posiciones y
  `CNPFF_D1`/`CNPFF_D2` con 12, usa las cuatro plantillas originales.
  Mantiene estilos, imagenes y distribucion de jornadas; actualiza temporada
  y numero de candidato. Coordenadas historicas compartidas con los
  exportadores legacy de 16 y 12 posiciones, con prueba de equivalencia.
  Omite BYE y compacta partidos por jornada como antes.
- Otras competiciones/tamanos reciben XLSX tabular, indicado en el propio
  libro y en LEEME. No se elige plantilla por codigo D1/F1 solamente.
  Si una plantilla compatible falta o no puede leerse, falla explicitamente;
  no se reemplaza por tabular para esconder el error.
- No se ha ampliado el motor a ligas impares: P1 sigue exigiendo equipos pares.
  Probar BYE en el exportador no implica soporte nuevo de generacion.
- `DrawController.zip` conserva URL, MIME y nombre de descarga; usa una
  tarea asincrona con timeout de 30 minutos para los ZIP grandes, sin
  cambiar el timeout general de MVC. Proxies deben permitir descargas largas.
- Verificado: 22 pruebas distintas, sin fallos (21 en la primera ejecucion
  mas el nuevo test HTTP; 13 repetidas/dirigidas tras ajustar el controlador).
  Incluyen las cuatro plantillas, estilos/imagenes/areas de impresion,
  temporada/identificador, BYE, tabular, rechazo de jornada fuera de rango,
  ZIP multiliga y regresion del motor P1.
- La prueba de volumen genero 1.000 candidatos reales con `DrawEngine`,
  escribio un ZIP con exactamente 1.000 XLSX + 1.000 CSV y reabrio cada XLSX
  con POI: 240 partidos por libro coinciden con el candidato correspondiente.
  Comprueba tambien todos los CSV y las 240.000 filas del agregado.
  Repositorio simulado; no es una prueba de persistencia ni de BD.
- Volumen observado: 413.322.568 bytes (unos 413 MB decimales) para una liga
  de 16 equipos con imagenes originales; renderizado y verificacion conjunta
  122,72 segundos. No afirmar que ese sea el tiempo de descarga HTTP.
  Se procesa un libro cada vez y se limpian los archivos temporales del test.
- Comando reproducible en README. No se ha reiniciado el backend en
  ejecucion, tocado ninguna BD ni hecho prueba visual en Excel/LibreOffice
  o E2E navegador. Falta cargar el codigo nuevo en el servidor y comprobar
  manualmente la descarga desde `/draw`, usando modo conserva datos.
  El pendiente anterior "ZIP de 1.000 XLSX" queda resuelto a nivel de codigo
  y prueba automatizada sin BD, no certificado todavia en servidor real.

## Pares P1 federados entre divisiones (19/10/2026, 21:14)

### Significado confirmado por el usuario

- `teams.NotAtHomeTogether`: dos equipos NO pueden jugar como locales en la
  misma jornada. Restriccion de AssignPositions / P1, no de fechas/horas P2.
  Puede relacionar equipos de D1 y D2 u otras ligas de la misma federacion.
  No hace falta conocer el estadio, la ciudad ni el motivo de la relacion.
- El usuario cita la documentacion funcional y aclara que los pares CNPFF
  historicos estaban comentados/inactivos en aquella version. No se consideran
  automaticamente autorizados todos los pares de la lista actual. No se han
  activado, importado ni inventado relaciones historicas en este trabajo.
- `posfolder.N=pos_YYYYMMDD-HH.mm.ss,cantidad`: referencia antigua a una carpeta
  de plantillas de posiciones y numero de alternativas que puede leer el motor
  aleatoriamente. No es una restriccion ni fecha de partido. El P1 nuevo genera
  plantillas en memoria con TwoColumnsGenPosAlg; no necesita esas carpetas.
- Los properties mostrados repiten sufijos `.1`, `.2`, etc. entre bloques.
  Java Properties sobrescribe las claves anteriores: los comentarios D1/D2 no
  crean ambitos. Tambien hay un triangulo ASFAR/FUS/UTS (y otro ANAFF/USYM/GINGA):
  si se exige oposicion por jornada sin descansos, no pueden activarse las tres
  relaciones simultaneamente. No resolverlo cambiando la regla a P2 sin permiso.
- La instantanea adjunta del usuario es solo lectura; no se edito, ni se
  modificaron sus properties de referencia para activar/desactivar pares.

### Implementado

- Reutilizado el tipo del catalogo `DerbyTeams` / DerbyTeamsCheck para la
  semantica de oposicion por jornada, sin un tipo duplicado de nombre distinto.
  Etiqueta visible traducida «Pares no en casa juntos (P1)» y ayuda expresa
  para elegir FEDERATION_DEFAULT cuando los equipos juegan en ligas distintas.
  Los nueve tipos P1 exigidos siguen siendo nueve; sin migraciones de esquema.
- RuleScopeService resuelve estos pares en el universo coordinado del sorteo,
  no descartando la relacion porque un equipo pertenezca a otra liga. Las
  reglas con equipos ajenos NO se pasan como checks locales de esa liga ni
  contaminan el planificador P2. La API de reglas efectivas indica la relacion
  cruzada relevante para la liga consultada, no pares de equipos ajenos a ella.
- La validacion de alta impide pares consigo mismo y equipos inscritos
  exclusivamente en otra federacion. Un equipo maestro aun sin inscripcion
  puede configurarse, pero bloquea P1 hasta inscribirlo en la misma federacion
  y temporada o corregir la regla. No se ignora ese requisito.
- DrawBatchService incluye automaticamente las ligas relacionadas de la misma
  federacion/temporada, ademas de dependientes, y recorre enlaces transitivos.
  No crea dependencias P2 para hacerlo. `includeDependents=false` con una
  relacion cruzada activa devuelve error de validacion y 422 al iniciar.
  La opcion frontend ahora habla de ligas dependientes Y relacionadas.
- Herencia, tipo/asignacion desactivados, excepciones permitidas y precedencia
  de duplicados por mismo tipo/parametros se conservan. Si cualquiera de las
  ligas excluye explicitamente un par heredado permitido, no se fuerza ese
  par entre ellas; las restricciones obligatorias no permiten esa excepcion.
- DrawEngine.RequiredPair verifica cada candidato completo por liga/equipo
  y jornada, en ambas vueltas; soporta tambien pares entre ligas secundarias.
  Las relaciones federadas nuevas no pasan por el descarte tolerante de enlaces
  de estadio heredados. Dicho comportamiento legacy y sus avisos se mantienen.
- Triangulos y otros conflictos de oposicion se notifican antes de generar,
  sin borrar ni ignorar los pares. El motor actual requiere igual numero de
  equipos en las ligas relacionadas; si no lo hay, bloqueo explicito, NO una
  promesa de soporte a calendarios desiguales o jornadas con descansos.
- Sin pantallas/maestros de estadios, cambios de fixtures por generar candidatos,
  activaciones de pares concretos ni nuevas traducciones servidas desde assets:
  los diccionarios siguen en el backend.

### Verificacion ejecutada

- Mismas **75 pruebas** dirigidas correctas en PostgreSQL y SQL Server:
  ApplicationFlowsDatabaseIntegrationTest32, SetupDatabaseIntegrationTest6,
  EngineParityTest4, EngineTest7, SetupToEngineMapperTest5,
  PlanningParametersTest4, CompetitionAdminControllerTest3, RuleScopeServiceTest4,
  DrawEngineTest8 e I18nControllerTest2. Cero fallos/errores/omitidas.
- Despues se amplio y repitio el caso API de lote a **1.000 candidatos
  persistidos en cada motor**, no solo una prueba unitaria: se leyeron los
  1.000 candidatos por API y se comprobo la oposicion del par D1/D2 en las diez
  jornadas de cada uno. Dos ligas sinteticas de seis equipos, misma federacion
  y temporada, sin dependencia P2. Lotes temporales eliminados sin elegir
  candidato. No es una prueba con la lista CNPFF historica ni exportacion XLSX.
- Prueba de motor adicional genera 1.000 candidatos distintos con ese efecto;
  tests de pares entre ligas secundarias, tamanios incompatibles, triangulos,
  equipos de otra federacion, contraparte sin inscripcion, reglas desactivadas
  y excepciones. La paridad legacy de las cuatro ligas sigue pasando.
- Frontend: seis tests del editor de asignaciones correctos con ChromeHeadless,
  incluido guardar un par federado sin limitarlo a los participantes de la
  liga seleccionada. Build produccion correcto, con los avisos conocidos de
  bundle/CSS. Auditorias i18n normal/estricta correctas: 567 frases y 949 claves
  por idioma, paridad/parametros es/fr/en.
- Solo BDs desechables `calendar_regression_test`; no se reiniciaron servidores
  ni se tocaron las BD de demo. Pendientes anteriores sin cambio: ZIP 1.000
  XLSX, aprobacion P2 -> confirmacion automatica, CHG019/020 y smoke mockup.
- Copia mas reciente de este estado:
  [calendar-p1-relations-recovery-20261019-211431](../../backups/calendar-p1-relations-recovery-20261019-211431/),
  583 archivos verificados con SHA-256 de backend/frontend/memoria, sin
  credenciales, compilados, dependencias ni bases de datos.

## Estadisticas por liga recuperadas (CHG-021, 19/10/2026)

### Implementado

- Se localizaron las estadisticas antiguas de P2 en `SchCalendarExcelIO`:
  `statisticsDaysAtHome*.csv` cuenta lunes-domingo, total como local,
  lunes-viernes (Midweek) y sabado-domingo (Weekend);
  `statisticsTime*.csv` cuenta horarios por equipo como local y visitante.
  `LegacyResultFiles` ya seguia generando y guardando estos CSV en las
  versiones. No se han cambiado esos formatos ni el motor.
- La pantalla Angular `/statistics` solo mostraba versiones, ficheros y
  equipos. Ahora conserva ese resumen y muestra, para la ultima version de la
  liga/temporada seleccionada, totales local/visitante, horarios y dias de la
  semana por equipo, con filas All/Home/Away y entre semana/fin de semana.
- Backend: `GenerationStatisticsService` y `GenerationStatisticsController`.
  `GET .../versions/{n}/statistics` devuelve los calculos y
  `GET .../versions/{n}/statistics.csv` permite entregarlos como CSV UTF-8.
  Ambos se calculan desde los mismos partidos persistidos; no requieren
  nuevas tablas, regenerar calendarios ni guardar estadisticas duplicadas.
- Se excluyen compromisos externos y partidos de otras ligas que aparecen
  en el calendario completo. Los partidos sin hora tienen columna UNASSIGNED.
  Se usan codigo y equipos del snapshot historico, incluidos equipos con
  cero partidos. Las importaciones sin snapshot usan el codigo actual de la
  liga y los equipos presentes en sus propios partidos, sin inventar nombres.
- La pantalla cancela peticiones al cambiar liga/temporada y limpia las tablas
  anteriores. Los errores de versiones/estadisticas se muestran con reintento,
  no como ceros ni como ausencia de generacion. Traducciones ES/EN/FR.
- Las estadisticas P1 de `AssignmentStatistics` son frecuencias de cruces por
  jornada entre sorteos/candidatos, no horarios P2. Se localizaron pero no se
  ha modificado ni recuperado ese informe de lotes en esta pantalla.

### Probado

- Mockup actualizado antes de backend/front: `node ../mockup/tests/smoke.mjs`
  devuelve ALL PASSED, con comprobacion nueva de las tres tablas.
- Maven focalizado: 11 pruebas correctas (10 del servicio/API y una legacy).
  Incluye comparacion de cada celda de dias como local y horarios totales de
  los CSV antiguos de BOTOLA D1/D2 y CNPFF D1/D2, snapshot historico, importacion
  sin snapshot, eventos externos, ceros, horas ausentes y respuesta JSON/CSV.
- ChromeHeadless: 25 pruebas correctas entre pantalla, cliente API y
  localizacion de pantallas. Incluye carga, conteos del backend, enlace CSV,
  error/reintento, ausencia de version y cancelacion de peticiones antiguas.
- TypeScript sin errores; auditoria i18n correcta (589 frases, 963 claves
  por idioma); build Angular de produccion correcto con avisos de presupuesto
  bundle/CSS. No se atribuyen los cambios previos de otros trabajos a este CHG.

### Pendiente

- Smoke de navegador con datos vivos. Angular respondia en 4200, pero los
  backends en 8080 y 18082 no respondian durante la comprobacion; el proxy
  devolvia 502. No se lanzaron/reiniciaron servicios, no se borro ninguna BD
  y no se afirma haber probado este cambio contra PostgreSQL/MSSQL reales.

## Consolidacion de cuatro chats Copilot (19/10/2026, despues de las 21:55)

- Guardada una sintesis conjunta de las cuatro conversaciones del backend
  en [INFORME_CONTINUIDAD_4_CHATS_2026-10-19](INFORME_CONTINUIDAD_4_CHATS_2026-10-19.md):
  continuidad general/restricciones, semillas P1, plantillas Excel P1 y
  estadisticas P2. Incluye identificadores de sesion y procedencia de pruebas.
- Memoria de esta sesion guardada en su carpeta persistente `files/`.
  No se promete memoria global del modelo ni transcripcion literal.
- Estado vigente: ZIP P1 con 1.000 XLSX implementado y probado automaticamente
  sin BD; notas antiguas que lo marcan pendiente quedan superadas por la
  seccion XLSX P1 integrado. Sigue pendiente descarga real y revision visual.
- Estadisticas P2 implementadas y exportables, con descargas separadas del
  calendario. Requisito de entrega acumulativa 1-5, 1-10, 1-15 documentado.
- La prueba completa de entrega 5->10 con backend/BD/navegador y descargas
  reales se ha descrito, NO ejecutado. Debe confirmar fixtures de 1-5 antes
  de ampliar: aprobar P2 aun no los bloquea automaticamente.
- Esta consolidacion no ejecuto nuevas pruebas ni arranco/reinicio servicios.

## Prompts de continuidad

- Backend: [retomar-calendar](../.github/prompts/retomar-calendar.prompt.md).
- Frontend: `../CalendarGenerateFrontNew/.github/prompts/retomar-calendar.prompt.md`.
- Se conserva una sintesis de objetivos y solicitudes, no una transcripcion
  literal completa de conversaciones cuyo texto ya fue resumido.

## Configuracion P2 completa y ayuda por nivel (CHG-022, 20/10/2026)

### Implementado

- `/season` -> detalle de temporada: parametros generales P2, franjas/Ramadan
  y configuracion avanzada de grupos de calculo, grupos MAJ, orden de jornadas
  y colores XLSX. Ayuda visible ES/EN/FR en cada parametro de temporada,
  horarios y opciones P2 del participante.
- Los grupos de calculo son internos de `ScheduleMoroccoAlg`, no grupos de
  coordinacion ni reglas federativas. El primer grupo comparte horizonte con
  `general.roundsForwardLook`; los siguientes tienen su propio horizonte.
  Con orden personalizado, el objetivo N procesa las primeras N entradas.
  La validacion comprueba fechas de las jornadas realmente seleccionadas.
- `groupToOptimizeTimes` solo se muestra para compatibilidad, de solo lectura,
  con aviso de que no selecciona equipos en el motor actual. La seleccion
  efectiva sigue siendo `teams[].optimizeTimes`, editable en el participante
  de esta competicion/temporada, igual que `noLight`.
- `/time-rules` conserva las franjas repetidas. Se corrigio el deduplicado al
  guardar `15:00,15:00,15:00,15:00`, que eliminaba informacion del motor.
- `/restrictions/p2`, accesible desde restricciones de calendario: descansos,
  parejas de localia por fecha y reglas P2 sin mezclar editores P1. Se conserva
  `/constraints` como ruta mixta de compatibilidad. Las parejas P2 no se
  convierten en derbis P1 ni se crean nuevos ambitos.
- `SeasonSetupRepository` guarda realmente `assignGroups` y la lista MAJ;
  anteriormente el PUT conservaba los grupos antiguos. El mapper respeta
  `groupsCanGoToMAJ=0` (ninguno), sin convertirlo en grupo 1; `allowMAJ=false`
  sigue bloqueando MAJ para todos. Se admiten espacios en listas CSV al mapear.
- `engine.exportColours` es un mapa opcional de presentacion por temporada.
  Migracion incremental `008-p2-export-colours.yaml`, tabla
  `season_export_colour`; sin modificar changesets aplicados ni precargar
  relaciones o colores desde el adjunto. Si se omite el mapa, conserva los
  colores guardados; un mapa vacio los elimina. Llegan al XLSX de configuracion
  y a los artefactos P2 del job; no afectan a P1.
- Validacion estructural antes de escribir y antes de planificar: numeros de
  grupo, cobertura de participantes, horizontes, orden completo sin duplicados,
  grupos MAJ, parejas y nombres IndexedColors. Errores 422 explicitos con
  causas visibles; guardado transaccional y control `rowVersion` conservados.
- No se anadio gestion de estadios ni se retiraron restricciones del motor.

### Verificado

- Backend: 23 pruebas dirigidas correctas (`P2SettingsTest`, `SetupValidatorTest`,
  `SetupToEngineMapperTest`, `PlanningParametersTest`, `EngineParityTest`).
  Incluyen el color real en el XLSX, MAJ=0 y validacion de fechas con orden
  personalizado; conservan la paridad del motor con los seeds.
- Frontend: 34 pruebas ChromeHeadless correctas sobre formularios, store,
  restricciones, parametros y navegacion. Guardado de grupos/MAJ/colores,
  franjas repetidas, ayuda, separacion P1/P2 y causas de errores 422.
- Build Angular de desarrollo correcto y auditoria i18n correcta:
  629 frases referenciadas y 1021 claves por idioma ES/EN/FR.
- Migracion y pruebas PUT/GET dirigidas en bases efimeras PostgreSQL y SQL
  Server: grupos, MAJ, colores, rollback ante errores y rechazo 409 de version
  antigua, sin tocar las bases de trabajo. La prueba de persistencia incluye
  ademas franjas repetidas y parejas P2 consumidas por el mapper.
- Smoke del mockup correcto, incluidos los controles avanzados y la ruta P2.

### Pendiente operativo

- No se arrancaron/reiniciaron los servicios de trabajo ni se aplico la
  migracion a sus bases conservables. Para usar esta version hay que cargar
  backend/frontend nuevos con arranque conserva datos y Liquibase incremental.
- No se afirma smoke navegador contra backend vivo ni nueva generacion de una
  temporada completa con configuracion avanzada introducida desde la UI.
- CHG-019 (movimiento manual seguro de fechas/horas) y CHG-020 (paquetes
  jerarquicos) no quedan resueltos por estos editores.

## CHG-023 y CHG-024: configuracion exclusivamente BD (20/10/2026)

Decision expresa del usuario: reutilizar toda la funcionalidad del motor
legacy con BD/pantallas actuales, no aparcarla como un flujo distinto.
Compatibilidad total para APIs y ejecutables batch antiguos.

### Implementado

- `DatabaseEngineConfiguration` construye snapshots efectivos, entornos P1 y
  entradas/parametros P2 desde repositorios. `Properties` solo es un adaptador
  en memoria, nunca un fichero de entrada. La serializacion conserva el
  snapshot capturado aunque despues cambien los datos.
- `Environment`, `ClassEnvLoader`, `SchEnvironment`, `AssignPositions.main`,
  el main P2 y los exportadores ya no leen properties de negocio.
  Se conservan `TwoColumnsGenPosAlg`, `AssignPositions`, `ScheduleMoroccoAlg`,
  sus checks y los puentes TXT/Excel explicitos. Los mains auxiliares aceptan
  un selector BD en vez de entornos Test6/Test16 de ficheros.
- `/api/config` y `/api/calendar` permanecen activos, incluso si se envia el
  flag antiguo `legacy-files=false`. Catalogos de competiciones/temporadas y
  configuracion son JDBC. Retiradas las competiciones de application.yml y la
  semantica del flag; no hay precarga automatica de Excel historicos.
- Generacion compatible mediante el mismo `PlanningJobService`.
  Importacion + CAS + alta del job en transaccion SERIALIZABLE; worker solo
  tras commit. Un conflicto o rollback no deja una importacion aplicada a medias.
  Se mantienen fixtures confirmados, cancelacion, logs, snapshots y artefactos.
- Batch P1/P2: contexto Spring minimo, sin recuperacion de trabajos huerfanos
  del servidor. Exige conexion explicita y fuerza `drop-first=false` y demo
  desactivada. Los servicios se crean despues del refresh. P1 persiste el lote
  y exporta ZIP en `draw-ID`; P2 persiste version/ficheros y exporta en
  `planning-ID`, sin sobreescribir artefactos existentes.
- P1 sigue siendo inicializacion antes de P2: la eleccion bloquea todas las
  ligas del candidato con orden estable y transaccion SERIALIZABLE. Rechaza
  P2 activo, versiones existentes, fixtures fechados/confirmados y sorteos
  activos. No borra versiones P2 ni permite cambiar el sorteo en marcha.
- Aliases opacos en `competition_alias`, migracion incremental 010.
  Selector `DB:ID@YYYY-YY`; IDs/codigos/aliases existentes tambien se resuelven.
  Si hay varias temporadas o competiciones posibles, error explicito.
  `/configuration/legacy-aliases` administra solo `LEGACY_*` al nivel
  competicion, incluso sin temporadas, con CAS, borrado confirmado y errores
  visibles. No son rutas ni asignaciones de etapa.
- Catalogos permanentes: 12 tipos por nueva federacion (9 P1 configurables,
  `HalfMatchesAtHome` interno deshabilitado, 2 P2), ambitos admitidos, 19
  referencias externas y colores XLSX. `/configuration/catalogs` y
  `/restrictions/catalog` los explican y enlazan al ambito P1/P2 compatible.
  No se ha introducido gestion de estadios en el frontend.
- Bootstrap seguro: master de produccion separado de 90 changesets de datos
  historicos, conservando logicalFilePath/identidades/checksums antiguos.
  Los historicos y sus SQL son fixtures exclusivos de integracion; el contexto
  antiguo `seed` no los incluye en el master normal. Migracion 009 completa
  catalogos sin sobrescribir datos. FRMF y DEMO siguen como referencias,
  no como competiciones de ejemplo.
- Demo opt-in: solo `dev` + `CALENDAR_DEMO_ENABLED=true`. `DEV_MINI` con una
  temporada y 4 participantes por defecto; anno y numero par 4-16 son
  parametros de creacion. Sin historicos/resultados. Reejecutar no pisa
  ediciones. Activar con prod/production/test falla antes de inicializar BD,
  incluso si tambien esta presente dev.
- Inventario completo: 34 properties de fuentes, con familias activas y
  comentadas documentadas en la matriz. Los 18 antiguos recursos de main
  estan ahora en test, 15 archivos historicos siguen en la carpeta de
  auditoria y 1 corresponde al wrapper Maven. Este ultimo es infraestructura.
- Parametros, pantallas y niveles P2 de CHG-022 conservados: grupos/MAJ,
  horizontes, franjas repetidas, iluminacion por participante, descansos,
  parejas de misma fecha, orden y colores. `groupToOptimizeTimes` sigue
  identificado como inactivo; no se ha inventado un efecto. El adaptador
  admite Ramadan ausente y rechaza intervalos incompletos.

### Probado

- **75 casos unitarios dirigidos correctos**, incluidos contratos compatibles,
  Excel, checks/sorteo y cuatro calendarios con fechas, horas y orden
  exactamente iguales al legacy. Dos casos adicionales verifican Ramadan
  opcional/incompleto y descansos vacios en el adaptador en memoria.
- **PostgreSQL y SQL Server, en cada uno**: 78 casos de regresion, integracion
  y validacion correctos, mas 4 de bootstrap/demo y 1 de upgrade. Cero fallos,
  errores u omitidos. No es una declaracion de suite completa.
- Esas regresiones incluyen P1 y P2 CLI reales: lote persistido, ZIP con XLSX
  por candidato, eleccion inicial, job P2 y cinco artefactos de version.
  Tambien prueban que abrir el contexto compatible no altera trabajos ajenos,
  la ambiguedad/CAS de aliases, snapshots estables, guardas P1 y 13 casos de
  atomicidad importacion/job. El fixture CP de esos tests se completo con su
  descanso; no se debilito el validador.
- Alta/edicion/borrado protegido, herencia/excepciones, P2 incremental con
  confirmados, exportacion real y catalogos de nuevas federaciones incluidos
  en las regresiones de ambas BD. Se actualizo la expectativa antigua de
  nueve tipos: ahora son doce, manteniendose al bloquear un borrado.
- Fresh: esquema/catalogos sin historicos; demo directa e idempotente; arranque
  real del runner dev con commit visible desde otra conexion y sin pisar
  cambios al reactivar. Upgrade: mismas identidades/checksums y filas de
  negocio anteriores, solo 009+010 nuevas; no resembrar pasando `seed`.
- Frontend: **21 pruebas ChromeHeadless correctas** de aliases, catalogos,
  asignaciones, compromisos y navegacion; build de desarrollo correcto.
  Auditoria ES/EN/FR: **651 frases, 1053 claves**, paridad correcta.
- Mockup: smoke completo correcto, incluida compatibilidad y cabeceras.
- `clean package` y paquete final correctos. Inspeccion del JAR: **cero
  properties de negocio y cero SQL de seed historico en recursos de produccion**.
  Checks de whitespace correctos sobre fuentes/documentacion de esta tarea.
- Eliminadas y comprobada la ausencia de las cuatro BD efimeras propias
  `calendar_chg023_fresh_49e1aa31` / `calendar_chg023_upgrade_49e1aa31`
  de PostgreSQL y SQL Server. No se tocaron otras BD.

### Pendiente operativo y limites reales

- No se reiniciaron servicios de trabajo, no se aplicaron migraciones a BD
  conservables y no se hicieron commits/push/despliegues. Para usar el backend
  nuevo hay que cargarlo con arranque **conserva datos** y Liquibase incremental.
  Ante un error de checksum se investiga; no se utiliza un reset como remedio.
- No se afirma una prueba de navegador contra los servicios de trabajo vivos.
  Build/ChromeHeadless y endpoints/motor reales se probaron de forma aislada.
- P1 exige indicar en pantalla un entero positivo para cada lote; no hay
  cantidad predeterminada ni máximo funcional fijo. El tiempo/intentos puede
  hacer que el lote termine parcial, lo cual se comunica. La API y el motor
  aceptan cantidades superiores a 1000; `posfolder` ya no requiere
  configuración manual. El Excel aportado es un ejemplo de calendario, no fija
  cuántos emparejamientos candidatos se generan.
- **Implementado y probado (2026-10-20)**: campo vacío y obligatorio por lote
  en `/draw`, bloqueo de inicio si falta o no es un entero positivo, rechazo
  equivalente en API, y retirada del límite funcional 1000 del servicio y el
  motor. Verificado con test Java focalizado, 4 tests ChromeHeadless, build
  Angular, check i18n y smoke del mockup. **Pendiente**: repetir la integración
  backend completa en PostgreSQL y SQL Server con bases de prueba desechables;
  los resultados duales anteriores son de la versión que aún imponía 1000.
- Un catalogo describe tipos/soporte/ambitos; no es un editor de algoritmos.
  Valores derivados y rutas tecnicas no se convierten en restricciones ni
  formularios de federacion/equipo. CHG-019/CHG-020 siguen fuera de este alcance.

### CHG-025 — modo de simetria de segunda vuelta en P1

- **Mockup implementado** en `/home/jose/morocco/mockup`: selector FULL_MIRROR,
  ASYMMETRIC y PARTIAL_MIRROR; objetivo parcial de 0 a 100; metricas separadas
  `Second-leg symmetry` y `Home/Away balance`; y ordenacion demostrativa de
  candidatos. La simulacion se identifica como simulada y conserva el fixture
  original.
- **Contrato documentado** en el mockup: FULL_MIRROR conserva la jornada espejo;
  ASYMMETRIC no la exige; PARTIAL_MIRROR usa el porcentaje como objetivo de
  optimizacion, no como restriccion dura. La simetria mide retornos en jornada
  espejo y no representa el equilibrio Casa/Fuera.
- **Verificado en mockup**: smoke completo, modos, metricas independientes,
  cambio visible de jornadas de vuelta, ordenacion por balance frente a
  puntuacion, objetivo parcial (incluidos 0 y 100) y fixture intacto.
- **Pendiente en producto**: persistencia por lote/BD, generacion en backend P1
  y pantalla Angular. El motor Java actual genera la vuelta espejo; no afirmar
  que soporte ya las tres modalidades. No se han cambiado reglas P1 existentes,
  motor, BD ni Angular en este CHG. La integracion debe preservar las
  restricciones obligatorias actuales y mantener este alcance separado de P2.
