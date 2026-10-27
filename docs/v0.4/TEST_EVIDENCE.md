# Evidencias de prueba v0.4 (entorno de generación)

El entorno donde se generó v0.4 **no tiene acceso a Maven Central** (ni Spring, ni POI, ni JUnit) pero sí
JDK 17 y un PostgreSQL 16.2 real. Esto es lo que se ejecutó de verdad y lo que queda para CI.

## Ejecutado aquí

| Qué | Cómo | Resultado |
|---|---|---|
| Motor + builder + mapper + validador | `javac --release 17` de las clases reales (sin Spring/POI) | compila |
| Tests JUnit del repo: `EngineParityTest`, `PlanningParametersTest`, `SetupToEngineMapperTest`, `SetupValidatorTest`, `EngineTest` | compilados con un stub mínimo de `org.junit.jupiter.api` y ejecutados con un runner por reflexión | **24/24 PASS** (`test-output-engine.txt`) |
| Paridad independiente desde las fuentes legacy | harness que lee properties + Excel v0 y compara con `data/results/2026-27/*-v0` | D1 40/40, D2 40/40, F1 30/30, F2 30/30 (`parity-harness-output.txt`) |
| Esquema + seed | `001-schema.sql` y `002-seed-2026-27.sql` aplicados con `psql` en PostgreSQL 16.2 | OK, 20 tablas, 4 versiones v0 APROBADAS |
| SQL de los repositorios | las 53 sentencias de `setup/**` preparadas (`PREPARE`) contra ese esquema | 53/53 OK |
| Índice de job activo | dos inserts QUEUED/RUNNING para la misma competición | el 2º falla con `uq_planning_job_active` (→ 409) |
| Capa Spring (`setup/**`) | `javac` contra stubs de firmas de Spring/Jackson/Logback | compila (las firmas reales se comprueban en CI) |
| YAML / XML | `yaml.safe_load` de workflows y application*.yml, parseo del `pom.xml` | OK |

## Pendiente de CI (`ci-backend.yml`, PostgreSQL 16 de servicio)

- `./mvnw clean verify` completo con Spring Boot 3.5.16, POI, JUnit y AssertJ reales.
- `liquibase:validate` + `liquibase:update` con el plugin Maven.
- `SetupDatabaseIntegrationTest` (seed por API, 409 por `rowVersion`, job completo D1 = v0, 422).
- Tests legacy (`CalendarControllerTest`, `CompetitionControllerTest`, …) con `calendar.legacy-files.enabled=true`.
