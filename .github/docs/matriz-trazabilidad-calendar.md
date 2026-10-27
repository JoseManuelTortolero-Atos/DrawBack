# Matriz de trazabilidad - Calendar Morocco

| Afirmación | Sección documento | Evidencia | Estado |
| --- | --- | --- | --- |
| Flujo web principal | 3, 7, 8 | Auditoría secciones 1, 4.4, 5, 6 | Demostrado |
| Backend Spring Boot 3.5.16 / Java 21 | 4.1, 22 | pom.xml según auditoría; ejecución Java 21 | Demostrado |
| POI 5.4.0 | 18, 22 | pom.xml/dependency tree según auditoría | Demostrado |
| Tres pantallas operativas | 8, 21 | routing, componentes y capturas revisadas | Demostrado |
| Generate Calendar -> /api/calendar/generate | 7, 8.2, 20 | CalendarController + componente Angular | Demostrado |
| Logs por generationId | 8.5, 27 | GenerationLogStoreService + X-Generation-Id | Demostrado |
| Excel Manager CRUD | 8.3, 20 | CalendarController + ExcelManager | Demostrado |
| Generated Results XLSX/ZIP | 8.4, 19, 20 | GeneratedCalendarStorageService + componente | Demostrado |
| Competiciones BOTOLA/CNPFF | 9 | application.yml líneas aprox. 17-41 según auditoría | Demostrado |
| Preservación de filas/estilos Excel | 10, 18 | SchCalendarExcelIOTest | Demostrado |
| ZIP excluye out*.csv | 19 | GeneratedCalendarStorageServiceTest | Demostrado |
| Configuración filesystem | 11 | CompetitionPropertiesServiceTest | Demostrado |
| Flujo primera->segunda | 13, 32 | Word legacy + preservación otras competiciones por test | Legacy + apoyo técnico |
| Ramadán / iluminación | 16 | properties observadas + auditoría algoritmo | Demostrado a nivel de configuración |
| Contexto TST definitivo | 25 | evidencia divergente audit/logs | Pendiente |
| Autenticación/autorización | 2, 5, 27 | sin evidencia | No encontrado |
| SharePoint complementario | 34 | enlace no accesible | Pendiente |

## Convención

- **Demostrado**: respaldado por código/config/test/captura según auditoría.
- **Legacy**: procede del Word histórico y se conserva etiquetado.
- **Pendiente**: evidencia insuficiente o contradictoria.
- **No encontrado**: se aplica la frase obligatoria cuando corresponde.
