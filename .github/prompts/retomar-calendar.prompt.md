---
description: "Retomar el backend Calendar Morocco con el contexto, decisiones y pendientes de sorteo y restricciones."
agent: "agent"
---

Lee [el contexto persistente](../../docs/CONTEXTO_PROYECTO.md) y
[los prompts historicos](../../README_PROMPTS.md).

Retoma la tarea concreta que indique el usuario. Antes de implementar:

- Lee en `docs/CONTEXTO_PROYECTO.md` la seccion **Requisito de flujo integral
  confirmado**: administracion de federaciones/competiciones/equipos,
  configuracion separada de restricciones, P1 de ~1000 candidatos, P2 con
  compromisos externos y movimiento manual, e import/export de ambos tipos.
- Distingue implementado, probado y pendiente; el estado descrito en prompts
  historicos puede estar desfasado respecto al codigo.
- Contrasta el estado documentado con el codigo actual, sin borrar datos ni
  arrancar el modo de BD limpia para comprobarlo.
- Distingue P1 (lotes de emparejamientos) y P2 (fechas/horarios).
- Comprueba que las restricciones efectivas alimentan ambos motores; P2 debe
  contemplar CL/FIFA y guardar movimientos manuales validados.
- El import/export debe respetar la jerarquia definida en el contexto
  compartido: paquete de federacion incluye su subarbol, paquete de liga su
  subarbol, y un paquete de equipo solo datos asociados al equipo; los paquetes
  de reglas requieren coincidencia exacta de ambito. No inventar scope TEAM.
  Importar con upsert no destructivo; duplicados solo comparan tipo y
  parametros en el mismo ambito. Definir contrato de previsualizacion.
- Preserva las reglas de ambito, excepcion y duplicados documentadas.
- Si se trata de datos iniciales, separa catalogos, maestros y configuracion de
  competicion/temporada. Pide confirmacion de datos de negocio desconocidos;
  no inventes fechas, derbis ni enlaces de estadio.
- No incorporar gestion de estadios al frontend. Conservar las restricciones
  antiguas del backend. P1 se elige solo para inicializar desde cero antes de P2;
  no confundir esa decision con un borrado integral ya implementado.
- No des por validado MSSQL ni todo el flujo del cliente por las pruebas en
  PostgreSQL. Ejecuta la validacion minima correspondiente a la tarea.
- Para cambios de generacion, persistencia o pantallas, actualiza y valida
  primero el CHG/contrato mockup y prueba PostgreSQL/MSSQL sin reinicios
  destructivos.

Al terminar, actualiza el contexto con cambios, evidencia y pendientes.
