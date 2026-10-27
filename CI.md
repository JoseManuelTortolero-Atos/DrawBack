# CI Backend (CalendarGenerateBack) · v0.4

| Workflow | Fichero | Qué hace |
|---|---|---|
| Backend CI | `.github/workflows/ci-backend.yml` | PostgreSQL 16 como servicio → tests del motor (sin BD) → `liquibase:validate` + `liquibase:update` → `./mvnw clean verify` → imagen Docker en GHCR (`ghcr.io/es-mev-applications/calendar/calendar-backend`, mismas etiquetas que antes: main/latest, develop/dev, sha-…, pr-N) |
| On demand (corporativo) | `.github/workflows/on_demand_java_maven_docker_workflow.yml` | `ci-workflows/maven-docker-workflow.yml@1.18.32`, Java 21, `package` |
| Release | `.github/workflows/release.yml` | sin cambios |

`ci.yml` y `docker.yml` se sustituyen por `ci-backend.yml` (evita construir y publicar dos veces la misma imagen).
Check obligatorio a actualizar en *Branch protection*: **Build and Test (Java 21 + PostgreSQL 16)**.
