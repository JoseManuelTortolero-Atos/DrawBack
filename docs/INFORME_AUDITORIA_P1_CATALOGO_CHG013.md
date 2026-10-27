# Informe de auditoria P1 - catalogo CHG-006 / CHG-013 / CHG-016

## Alcance auditado

- Mockup offline en `mockup`
- Frontend Angular 22 en `CalendarGenerateFrontNew`
- Backend Java y persistencia Liquibase en `CalendarGenerateBackNew`
- Regla de no confundir fila de catalogo con soporte real del motor

## Resumen ejecutivo

Se verificaron los nueve tipos P1 obligatorios del catalogo de restricciones. Antes de esta correccion existia una divergencia real entre capas:

- El backend Java ya conocia los checks P1 del motor legacy.
- La base de datos solo admitia cinco tipos en `constraint_rule.rule_type`.
- Angular 22 solo trataba como persistibles un subconjunto de tipos.
- El mockup dinamico FRMF no exponia `DerbyTeams` en su catalogo configurable.

Tras la correccion:

- Los nueve tipos P1 quedan catalogados y persistibles en base de datos mediante un changeset nuevo, sin modificar changesets ya aplicados.
- Angular 22 refleja los nueve tipos P1 reales, sus parametros y sus ambitos permitidos.
- El mockup FRMF vuelve a mostrar `DerbyTeams` y deja `HalfMatchesAtHome` marcado de facto como Java-only no editable.
- El backend compila con exito y persiste definiciones P1 canonicas con soporte de `int_param`.

## Matriz de los nueve tipos P1 obligatorios

| Tipo P1 | Ambitos permitidos | Parametros editables | Catalogo BD | Frontend Angular 22 | Mockup | Persistencia efectiva | Soporte efectivo del motor | Observaciones |
|---|---|---|---|---|---|---|---|---|
| DerbyTeams | FEDERATION_DEFAULT, COMPETITION_SEASON, COORDINATION_GROUP | a, b | Si | Si | Si | Si | Si | Faltaba en el catalogo dinamico del mockup; corregido. |
| MatchNotInRound | FEDERATION_DEFAULT, COMPETITION_SEASON, COORDINATION_GROUP | a, b, rounds | Si | Si | Si | Si | Si | Check ya reconocido por el parser legacy. |
| SeparateMatches | FEDERATION_DEFAULT, COMPETITION_SEASON, COORDINATION_GROUP | teams, minRounds | Si | Si | Si | Si | Si | Se mantiene como representacion canonica basada en grupo de equipos y separacion minima. |
| LimitHomeTogether | FEDERATION_DEFAULT, COMPETITION_SEASON, COORDINATION_GROUP | a, b, intParam | Si | Si | Si | Si | Si | Requiere entero adicional; ahora se persiste en `int_param`. |
| PlayTogether | FEDERATION_DEFAULT, COMPETITION_SEASON, COORDINATION_GROUP | a, b | Si | Si | Si | Si | Si | Sin gap funcional detectado en el motor legacy. |
| NotConsecutiveFarAway | FEDERATION_DEFAULT, COMPETITION_SEASON | teams | Si | Si | Si | Si | Si | Soportado como lista de equipos. |
| LimitAgainstTeamsInRounds | FEDERATION_DEFAULT, COMPETITION_SEASON | intParam, teams, rounds | Si | Si | Si | Si | Si | Requiere entero adicional; ahora se persiste en `int_param`. |
| LimitAgainstTeamsInRoundsHome | FEDERATION_DEFAULT, COMPETITION_SEASON | intParam, teams, rounds | Si | Si | Si | Si | Si | Variante home-only; mismo refuerzo de persistencia. |
| ForcePositions | COMPETITION_SEASON, COORDINATION_GROUP | team, intParam | Si | Si | Si | Si | Si | Faltaba en el contrato tipado Angular; corregido. |

## Nota separada sobre HalfMatchesAtHome

`HalfMatchesAtHome` no se considera uno de los nueve tipos P1 obligatorios de catalogo editable. Se conserva solo como referencia Java-only en el mockup/catalogo tecnico:

- visible como tipo informativo en mockup, pero deshabilitado
- no incluido en `BACKEND_RULE_TYPES`
- no presentado como regla editable de negocio en Angular

Esta separacion evita vender como funcionalidad de catalogo algo que hoy solo existe como check tecnico heredado.

## Correcciones aplicadas

### Base de datos y backend

- Nuevo changeset Liquibase `004-chg013-p1-rule-catalog.yaml`
- Nuevas tablas:
  - `rule_type_catalog`
  - `rule_type_allowed_scope`
- Nueva columna:
  - `constraint_rule.int_param`
- Ampliacion del conjunto permitido en `constraint_rule.rule_type` para cubrir los nueve tipos P1 mas los tipos P2 ya existentes
- Nuevo `RuleDefinitionCodec` para hidratar y serializar definiciones P1 canonicas
- `SeasonSetupRepository` actualizado para leer y guardar `definition` e `int_param`
- `SetupDtos.Rule` ampliado con `intParam`

### Frontend Angular 22

- Ampliado `RuleType` con los nueve tipos P1 reales
- `Rule` ampliado con `intParam`
- `RuleParam` ampliado para permitir `intParam`
- Catalogo estatico alineado con ambitos permitidos, descripcion tecnica y estado real de soporte
- `BACKEND_RULE_TYPES` alineado con persistencia backend real
- Vista de restricciones enriquecida para mostrar ambitos permitidos y estado de soporte

### Mockup

- `CM_SEED.federation.ruleCatalog` ahora incluye `DerbyTeams`
- `HalfMatchesAtHome` queda reflejado como tipo no editable
- Smoke actualizado para verificar el estado real del catalogo FRMF

## Pruebas efectuadas

### Backend Java

- `mvnw.cmd -DskipTests compile` -> `BUILD SUCCESS`
- Diagnosticos del editor (`get_errors`) sin errores en los archivos backend modificados

### Test unitario nuevo del codec

- Se anadio `RuleDefinitionCodecTest`
- La ejecucion Maven enfocada de tests no llego a ejecutar el test porque `testCompile` falla antes por dependencias de test ya ausentes en el proyecto (`org.springframework.boot.test.autoconfigure.web.servlet`, `com.fasterxml.jackson.core.type`, `com.fasterxml.jackson.databind`)
- Ese bloqueo se reporta como gap previo del arbol de tests, no como fallo introducido por este cambio

### Frontend Angular 22

- `npm exec tsc -p tsconfig.app.json --noEmit` sin salida de error
- Diagnosticos del editor (`get_errors`) sin errores en los archivos frontend modificados

### Mockup

- `node tests/smoke.mjs` -> `ALL PASSED`
- Verificacion relevante: el catalogo FRMF del mockup pasa de 11 a 10 acciones de alta al deshabilitar un tipo, mantiene `DerbyTeams` activo y conserva `HalfMatchesAtHome` fuera del alta editable

## Conclusion

La carencia verificada no estaba en el motor legacy de checks P1, sino en la alineacion entre catalogo, persistencia y contratos de edicion. Esa brecha queda cerrada en BD, backend, Angular y mockup, manteniendo compatibilidad con PostgreSQL y SQL Server y sin reescribir changesets ya aplicados.