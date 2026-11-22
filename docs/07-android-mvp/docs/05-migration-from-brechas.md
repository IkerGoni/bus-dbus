# 05 · Checklist de cierre de brechas (B1–B16) — Android MVP v1

- [ ] **B1 (Cercanía sin feeds `.pb`):** Catálogo estático offline de 548 paradas (514 con `lat/lon` OSM) + ordenación Haversine en `A02 · Paradas` (sin mapa embebido en v1; intent externo `geo:` en `A05`).
- [ ] **B2 (Datos falsos):** Eliminar saldo Mugi (`12,50 €`), `2.3 MB`, `hace 15s`, `hace 2 min`, `GTFS v2026.11`. Ocultar ocupación si la API no la devuelve.
- [ ] **B3 (Líneas hardcodeadas):** Alimentar `A02`, `A03`, `A05` y `A06` desde el catálogo real de 39 líneas / 127 itinerarios.
- [ ] **B4 (`BottomNav`):** `NavigationBar` fija de 4 destinos (`Inicio`, `Paradas`, `Líneas`, `Avisos`); retirar menú hamburguesa y `pb-20` huérfano.
- [ ] **B5 (Onboarding):** `A08` de 1 pantalla + solicitud `POST_NOTIFICATIONS` con fallback real a ajustes Android.
- [ ] **B6 (Push real):** Implementar `04-android-platform.md` (Foreground Service local + alarmas exactas; FCM fuera de v1) (2026-11-22: avisos de un solo uso, sin alarmas ni botones en la notificación).
- [ ] **B7 (Widget):** Diferido explícitamente a v1.1 (Jetpack Glance); cero placeholders muertos en v1.
- [ ] **B8 (Build verde):** en Android no se arregla la PWA: `apps/android` nace con `./gradlew check` (build + lint + tests JVM) en verde y CI. La PWA queda congelada (ADR 009).
- [ ] **B9 (Crear alerta desde parada):** Botón `Crear aviso` y campana 🔔 por fila en `A05` pasan `stopId` y `lineId` a `A06`.
- [ ] **B10 (Estados de alerta):** Renderizar los 5 estados de `ComputedAlertStatus` (`TRIGGERED`, `ACTIVE`, `SNOOZED`, `INACTIVE`; `SILENCED` desapareció con los recurrentes).
- [ ] **B11 (Offline real):** Banner superior no bloqueante cuando hay caché; distinguir `NO_SERVICE`, `EMPTY` y `API_ERROR`.
- [ ] **B12 (Favoritos persistentes):** Persistir `STOP`, `STOP_LINE` y `LINE` en DB local con dedup determinista y estrellas rellenas `#F59E0B`.
- [ ] **B13 (Posponer/Pausar reales):** `Posponer 5'` escribe `snoozedUntilMs`; `Pausar` muta `active = false` con `Deshacer`.
- [ ] **B14 (i18n estricto):** Test en CI que falla si faltan claves entre `es` y `eu` o si existen literales en UI.
- [ ] **B15 (Countdown reactivo):** Recalcular minutos restantes contra reloj reactivo de 1 s (`remainingMinutes`), sin mostrar segundos.
- [ ] **B16 (Silencio unificado):** Usar únicamente `computeAlertStatus` en toda la app y worker.
