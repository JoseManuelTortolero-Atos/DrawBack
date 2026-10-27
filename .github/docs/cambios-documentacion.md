# Cambios de documentación frente al Word original

## ACTUALIZADO

- El flujo web Angular pasa a ser el modo principal.
- Se documentan tres pantallas operativas: Generate Calendar, Excel Manager y Generated Results.
- Se añade API REST real, `generationId`, logs y descarga XLSX/ZIP.
- Se documenta la encapsulación `CalendarController -> CalendarGenerationService -> ScheduleFacade -> ScheduleMoroccoAlg`.
- Se añade configuración por `application.yml`, catálogo, properties, overrides runtime, filesystem y PVC.
- Se añaden Docker/Nginx y Helm/Kubernetes.
- Se separan TEST EXISTENTE y TEST RECOMENDADO.
- Se añade troubleshooting y runbook.

## CONSERVADO COMO LEGACY

- Preparación incremental y versionado del Excel.
- Selección manual de competición mediante `SchEnvironment.properties`.
- Edición de `datesFile`, `resultsFolder` y `lastRoundToAssign`.
- Ejecución directa de `ScheduleMoroccoAlg`.
- Ajustes manuales cuando el algoritmo no puede asignar.
- Generación primera antes que segunda e inclusión de partidos consolidados de primera en el Excel de segunda, tal como describe el Word.
- Competiciones externas, Ramadán, horarios y equipos sin iluminación.

## CORREGIDO

- `ScheduleMorocoAlg` -> `ScheduleMoroccoAlg`.
- `properies` -> `properties`.
- `rchivo` -> `archivo`.
- Numeración duplicada/rota y repeticiones del texto legacy.
- Se elimina la presentación del flujo manual como única vía.

## NO ENCONTRADO

- Base de datos como parte de la aplicación.
- Autenticación/autorización.
- Auditoría persistente externa.
- Tests frontend específicos suficientes.
- Fórmulas completas de coste del algoritmo.

## PENDIENTE DE VALIDACIÓN

- Contenido complementario SharePoint: el enlace no fue accesible en la sesión.
- Versión Maven Wrapper exacta.
- Toolchain Node única (CI 20 / Docker 22 según auditoría).
- Contexto/namespace TST definitivo por discrepancia de evidencia Helm posterior.
- Patrón exacto de nombres Excel femeninos.
- Inserción de capturas actuales y legacy originales: se inspeccionaron, pero no estaban disponibles como binarios reutilizables para el DOCX generado.
