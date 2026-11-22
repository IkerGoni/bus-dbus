# 00 · Guardrails de implementación — Dbus Donostia (Android MVP v1)

Fuente de verdad para agentes de código y QA. Ninguna PR o tarea puede marcarse
como completada sin cumplir estas reglas.

## 1. Alcance estricto de v1 (Sin mapa embebido)
- **PROHIBIDO** añadir SDKs de mapas embebidos (MapLibre, Google Maps SDK,
  OSMDroid) en la v1. El mapa interactivo se difiere explícitamente a v2.
- **Cercanía offline:** empaquetar un catálogo estático JSON en assets con las
  ~548 paradas (`stopId`, `name`, `lat`, `lon`), las 39 líneas (con color oficial
  Dbus) y los 127 itinerarios. Las distancias se calculan en local con Haversine
  en milisegundos, sin red.
- **Atajo externo de mapa:** en Detalle de parada (`A05`), si la parada tiene
  `lat/lon`, mostrar botón `Cómo llegar` que lance un intent nativo
  `geo:{lat},{lon}?q={lat},{lon}({stopName})`.
- **PROHIBIDO** reactivar feeds `.pb` (devuelven 403; ver ADR 005).

## 2. Reglas de datos reales (Cero mocks)
- **PROHIBIDO** renderizar datos inventados: saldo Mugi (`12,50 €`), ocupación
  simulada, accesibilidad por vehículo, tamaños de caché fijos (`2.3 MB`),
  timestamps literales (`hace 15s`, `hace 2 min`), versiones falsas (`GTFS v2026.11`),
  velocidad media, paradas de distancia o listas parciales de 5 líneas.
- **Llegadas en vivo:** consultar siempre la API JSON real de Dbus (misma fuente
  que `@dbus/bot`). Si un campo opcional no llega, ocultar su bloque.

## 3. Reglas de comportamiento (Sin acciones "teatro")
- Ningún botón puede limitarse a mostrar un Toast/Snackbar sin mutar persistencia
  local o navegar con parámetros reales (`stopId`, `lineId`).
- Favoritos (3 tipos: `STOP`, `STOP_LINE`, `LINE`) y Alertas persisten en DB local
  real con dedup determinista.
- La cuenta atrás se recalcula cada segundo contra un reloj reactivo (`now`) y se
  resincroniza con red cada 30 s en primer plano.
- `Inicio`, `Mis avisos`, badge de navegación y worker de notificaciones DEBEN
  consumir la misma función pura `computeAlertStatus(alert, now, nextArrival)` de
  `02-domain-contracts.md`.

## 4. Correcciones sobre las exportaciones originales de Stitch
- Usar exclusivamente la paleta unificada de `design-system/design-tokens.json`
  (`#F8FAFC`, `#FFFFFF`, `#E2E8F0`, `#005BB5`, `#059669`, `#F59E0B`).
- No heredar la barra duplicada con avatar, el `opacity: 0` en `CERCA DE TI`, la
  fuente serif de fallback ni los saltos de línea en tiempos/pestañas (`nowrap`).
- Todo botón interactivo (estrella, campana, GPS, ajustes) debe medir **≥ 48×48dp**.
- No depender solo del color para `EN VIVO` vs `HORARIO`: punto verde (`●`) en
  tiempo real e icono de reloj gris en horario.

## 5. Matriz de 5 estados, accesibilidad e i18n
- Toda pantalla con datos implementa y testea: `loading` (skeleton), `empty`
  (con CTA útil), `live`, `degraded` (`HORARIO` / `NO_SERVICE` / banner offline
  no bloqueante) y `error` (con botón `Reintentar`).
- Cero strings hardcodeados: todas las claves existen en `es` y `eu` (verificado en CI).
- Contraste **≥ 4.5:1**, `tabular-nums` en cifras, **edge-to-edge** (`WindowInsets`)
  y **Predictive Back**.
