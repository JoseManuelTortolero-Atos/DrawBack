# Continuidad de Calendar Morocco

Antes de cambiar generacion, restricciones, persistencia o arranques, leer
[el contexto del proyecto](../docs/CONTEXTO_PROYECTO.md) y
[los prompts de referencia](../README_PROMPTS.md).

- Mantener separados P1 (sorteo/emparejamientos) y P2 (fechas/horarios).
- No confundir catalogos de tipos de reglas con derbis, relaciones de estadio
  o fechas concretas de una competicion/temporada.
- No introducir gestion de estadios en el frontend; preservar restricciones
  antiguas en backend/motor. La eleccion P1 es inicializacion desde cero antes
  de P2, no un cambio de sorteo con la competicion en marcha.
- Preservar herencia, excepciones, validacion y concurrencia de restricciones.
  La precedencia de duplicados solo compara mismo tipo y mismos parametros.
- No usar arranques que borran la BD para trabajo con datos que deban conservarse.
- Frontend separado en `../CalendarGenerateFrontNew`; no introducir Angular aqui.
- Responder al usuario en espanol. Actualizar el contexto cuando cambie el estado
  verificado; distinguir implementado, probado y pendiente. Nunca guardar secretos.
