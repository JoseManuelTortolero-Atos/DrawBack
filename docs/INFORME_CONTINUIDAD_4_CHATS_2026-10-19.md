# Continuidad de cuatro chats Copilot - 19/10/2026

Fecha de consolidacion: 19/10/2026, despues de las 21:55 (hora local).
Resumen de decisiones, implementaciones y evidencias; no transcripcion literal.
No contiene credenciales ni autoriza reinicios, borrados de BD o commits.

Fuente compartida: [CONTEXTO_PROYECTO](CONTEXTO_PROYECTO.md).
Se han leido las conclusiones de los cuatro chats del backend y el contexto
actual del proyecto. Las pruebas indicadas se ejecutaron en sus respectivos
trabajos; esta consolidacion documental no las ha vuelto a ejecutar.

## 1. Chats incluidos

| Sesion | Tema |
|---|---|
| `82c141b1-8604-488b-8c8d-4555057dbdb2` | Continuidad general: arranques, P1/P2, restricciones, CRUD y pruebas PostgreSQL/MSSQL |
| `49e1aa31-a056-485f-a0d4-ad2f9e219b6f` | Semillas `posfolder`, batch antiguo y cantidad de candidatos actual |
| `88c964f0-68a9-4200-8435-f67ccf6879ef` | Plantillas Excel antiguas y exportacion XLSX de candidatos P1 |
| `87685fcc-6139-4623-b2e5-2b30fe921dc0` | Estadisticas P2 por liga, exportacion y procedimiento de prueba de entregas |

El chat historico de apariencia frontend no se incluye como un quinto chat.
Sus decisiones vigentes ya documentadas en el contexto se conservan.

## 2. Decisiones que deben mantenerse

- Separar P1 (sorteo/emparejamientos) de P2 (fechas/horarios).
- Elegir P1 inicializa la competicion antes de P2; no disenar un cambio de
  sorteo durante una competicion en marcha.
- Sin gestion de estadios en el frontend. Conservar restricciones legacy
  del backend y no activar automaticamente relaciones historicas.
- Catalogos de tipos, maestros y restricciones concretas son datos diferentes.
- Preservar herencia, excepciones, validacion y concurrencia. La precedencia
  de duplicados solo compara mismo tipo y mismos parametros.
- No usar arranques que borran BD para datos que deban conservarse.
- Frontend separado en `../CalendarGenerateFrontNew`; sin Angular en backend.
- Las afirmaciones de pruebas de un chat no certifican otros cambios posteriores.

## 3. Semillas y cantidad de sorteos P1

- Antes, `posfolder.N=carpeta,cantidad` apuntaba a patrones de posiciones
  precomputados, utilizados para construir sorteos. No eran estadisticas.
- Ahora se generan patrones en memoria y se persisten candidatos en BD.
  No es necesario configurar aquellas carpetas en la pantalla nueva.
- La pantalla permite solicitar de 1 a 5.000 candidatos, con 1.000 por defecto.
  Solicitar 10.000 no esta habilitado.
- La cantidad solicitada es un objetivo: tiempo/intentos y restricciones
  pueden producir un lote parcial, que debe explicarse.
- `nTournaments=1000` del exportador legacy lee sorteos existentes; no fija
  la cantidad solicitada al generador actual.
- Este chat comparo y documento codigo; no ejecuto un lote nuevo.

## 4. Restricciones P1 entre ligas

- `NotAtHomeTogether` significa no ser locales simultaneamente en la misma
  jornada, tambien entre ligas de la misma federacion y temporada.
- Se reutiliza el tipo tecnico `DerbyTeams`, presentado como pares no en
  casa juntos. El lote incorpora ligas relacionadas sin convertirlas en
  dependencias P2.
- Comprueba ambas vueltas, herencia, activacion y excepciones permitidas.
- Conflictos de oposicion, como tres pares que forman un triangulo, se
  notifican y bloquean; no se ignoran silenciosamente.
- El motor actual exige igual numero de equipos entre ligas relacionadas.
  No se ha ampliado a ligas impares ni calendarios desiguales.
- Evidencia del chat general: 75 pruebas dirigidas en PostgreSQL y las mismas
  75 en SQL Server; ademas, 1.000 candidatos persistidos y leidos por API en
  cada motor, verificando el par entre dos ligas sinteticas de seis equipos.
  No se usaron ni activaron automaticamente los pares CNPFF historicos.

## 5. Exportacion Excel P1

- Implementado `DrawCandidateExcel`, conectado al ZIP de `DrawBatchService`.
- Conserva CSV y agregados; suma un XLSX por candidato y liga. Descargar no
  regenera sorteos ni modifica la eleccion o la persistencia.
- Para Botola D1/D2 con 16 posiciones y CNPFF D1/D2 con 12 utiliza las cuatro
  plantillas originales, con estilos, imagenes y distribucion historica.
- Otras competiciones/tamanos reciben formato tabular identificado. Una
  plantilla compatible ausente o ilegible produce error explicito.
- Evidencia declarada en el chat de Excel: 22 pruebas distintas correctas,
  incluyendo las cuatro plantillas, multiliga, BYE del exportador y HTTP.
  Prueba de volumen: 1.000 candidatos del motor real y 1.000 XLSX reabiertos;
  los 240 partidos por libro coinciden con el candidato correspondiente.
- Esa prueba usa repositorio simulado, no certifica persistencia ni BD.
  ZIP observado: 413.322.568 bytes para una liga con imagenes originales.
  Renderizado mas verificacion: 122,72 segundos, no tiempo de descarga HTTP.
- Timeout especifico de descarga ampliado a 30 minutos; revisar limites de
  proxies en despliegue. Pendiente descarga con servidor actualizado y
  revision visual en Excel/LibreOffice. No se reinicio el backend.

## 6. Estadisticas P2 y entregas por jornadas

- Antes existian CSV de horarios por equipo (local y visitante) y dias como
  local, con total, lunes-viernes y sabado-domingo. Seguian guardandose con
  cada version, aunque la pantalla solo mostraba un resumen operativo.
- CHG-021 recupera tablas en `/statistics` para la ultima version de la
  liga/temporada: local/visitante, horarios, lunes-domingo, entre semana y
  fin de semana. Incluye carga, error/reintento y cancelacion al cambiar contexto.
- API por version: `GET .../versions/{n}/statistics` y `statistics.csv`.
  Calcula desde partidos persistidos; excluye eventos externos y otras ligas.
  Usa snapshot historico cuando existe y conserva hora sin asignar.
- En Resultados generados se descargan calendario y CSV legacy de cada
  version. En Estadisticas se descarga el CSV ampliado de la ultima version.
  No hay descarga automatica conjunta de calendario y estadisticas P2.
- El requisito de entrega es acumulativo: hasta jornada 5 = 1-5; al ampliar
  hasta 10 = 1-10, no solo 6-10; despues hasta 15 = 1-15.
- Advertencia: aprobar una version no confirma/bloquea sus fixtures por si
  solo. Para preservar lo ya entregado al ampliar, hay que bloquearlos.
- Evidencia: 11 pruebas backend correctas y 25 frontend; comparacion celda
  a celda con CSV legacy de las cuatro ligas; mockup ALL PASSED, tipos,
  auditoria i18n y build de produccion correctos, con avisos de bundle/CSS.
- No confundir con `AssignmentStatistics`: son frecuencias de cruces por
  jornada entre candidatos P1; ese informe no se recupero en la pantalla P2.

## 7. Prueba integral pendiente, no ejecutada

1. Usar BD aislada de pruebas, nunca borrar los datos actuales.
2. Generar hasta jornada 5 y descargar realmente calendario y estadisticas.
3. Comparar partidos con tablas y CSV: local + visitante = total; suma de
   horarios y suma de dias = total por equipo; sin compromisos externos.
4. Bloquear los partidos de 1-5 y ampliar hasta jornada 10.
5. Comprobar que 1-5 conserva fechas/horas, estadisticas acumula 1-10 y la
   entrega anterior sigue descargandose sin cambios.
6. Repetir desde navegador, cambiando liga/temporada, y cotejar descargas.

Referencia para liga de 16 equipos sin descansos: 40 partidos en cinco
jornadas y 80 participaciones; diez jornadas = 80 partidos y 160 participaciones.

La ultima comprobacion de este chat encontro Angular en 4200 pero no los
backends de 8080/18082, con errores 502 del proxy. No es una comprobacion del
estado de los servicios en el momento de reanudar. No afirmar que el recorrido
integral esta probado ni reiniciar automaticamente para intentarlo.

## 8. Otros pendientes conservados

- Confirmacion automatica de fixtures al aprobar P2.
- CHG-019: movimiento manual seguro de fecha/hora con conflictos y concurrencia.
- CHG-020: import/export jerarquico por ambito con previsualizacion y upsert.
- Cargar codigo actualizado con arranque conserva datos solo cuando corresponda
  y validar entregas reales. No atribuir pruebas antiguas a estos pendientes.

Las notas historicas que dicen que faltan los 1.000 XLSX quedan superadas por
la implementacion y prueba automatizada de la seccion 5, no por una prueba
manual de descarga o certificacion en BD que todavia no se ha realizado.
