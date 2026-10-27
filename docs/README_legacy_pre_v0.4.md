# CalendarMorocco

## Situación actual del proyecto

Actualmente el proyecto **CalendarMorocco** tiene dos bloques de código separados que conviven en paralelo y que es necesario unificar.

---

## Código histórico (algoritmo original)

- **Paquete:** `net.atos.mev.calendarcalculator`
- **Clase principal:** `ScheduleMoroccoAlg.java`

Características:
- Contiene el algoritmo original de cálculo de calendarios
- Se ejecuta de forma standalone
- Usa el archivo de configuración clásico
- No está integrado con Spring Boot
- Es la base funcional y validada del sistema

Este código es el que realmente genera el calendario hoy en día.

---

## Nueva arquitectura (Spring Boot)

- **Paquete:** `net.atos.mev.calendarcalculator`
- **Arranque:** Spring Boot
- **Uso:** Backend que consume el front-end

Características:
- Arranca como aplicación Spring Boot
- Expone servicios REST
- Es el punto de entrada del front-end
- Todavía no integra completamente el algoritmo histórico

---

## Problema actual

- El algoritmo real vive en `calendarcalculator`
- El backend moderno vive en `calendar`
- Ambos están desacoplados
- Se duplica configuración y lógica
- La evolución del proyecto se complica

---

## Objetivo pendiente

Unificar ambos mundos:

- Integrar `ScheduleMoroccoAlg` dentro del contexto Spring Boot
- Convertir el algoritmo en un servicio reutilizable
- Centralizar configuración y modelo
- Dejar Spring Boot como único punto de entrada
- Eliminar la ejecución standalone cuando sea posible

---

## Nota

Este documento describe el estado actual del proyecto.
La explicación detallada y el plan de migración se definirán más adelante.

---

## Infraestructura Helm

Los charts y scripts de Helm están separados en otro repositorio Git:

- `../helmMorocco`
- Chart principal: `../helmMorocco/charts/calendar-morocco-cli`

Este repositorio (`backMorocco`) contiene solo backend Spring Boot.



make lint OK para los 3 charts.
helm template OK en dev y prod para backend/frontend/ingress.
Render de ingress en prod confirma TLS (calendar-tls) y ruta / a calendar-frontend.
Uso rápido

make dev-up
make dev-status
make dev-port-forward PORT=8888


