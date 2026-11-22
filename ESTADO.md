# Estado — bus-dbus
Actualizado: 2026-11-22 05:49 · Última sesión: bot Dbus con datos reales

## Objetivo actual
PWA de llegadas de Dbus (San Sebastián) + bot de Telegram con avisos.
El bot ya opera con datos reales de la API oficial; la PWA sigue bloqueada
en el Sprint 3 por errores preexistentes de TypeScript/lint.

## Hecho en la última sesión
- Bot migrado de GTFS-RT/protobuf (feeds `.pb` con 403 desde S3) a la API JSON
  real de Dbus `SSIIMovilWSv2/ws/cons/tiemposParada` (HTTP, sin auth).
- Llegadas reales: línea, destino, minutos, hora y flag GPS vs. horario;
  caché de 30 s, deduplicación de peticiones y catálogo de paradas desde
  `paradasItinerario`.
- Flujos del bot reescritos alrededor del código público de parada; avisos y
  favoritos usan datos reales. Nuevos comandos: `/lineas`, `/populares`, `/help`.
- Retry con backoff ante conflictos 409 de long-polling de Telegram.
- Eliminados `gtfs-client.ts`, `src/proto/` y la dependencia `protobufjs` del bot.
- 13 tests en verde (8 integración + 5 E2E de handlers); `tsc`, `biome` y
  `build` del bot sin errores. README y `.env.example` actualizados.

## Pendiente (por prioridad)
- [ ] PWA Shell (Sprint 3): 30 errores TS y 233 errores de lint en `src/`
      (bloqueo preexistente; tareas en el kanban `dbus-app`).
- [ ] Validar una conversación real: enviar `/start` a `@dbus_alarm_bot`.
- [ ] Comprobar disparo de avisos con buses en servicio (tipo=1, GPS).
- [ ] M4/M5: notificaciones push y beta con testers.

## Decisiones tomadas (no reabrir)
- 2026-11-22 · Fuente del bot = API JSON de Dbus sobre HTTP
  (`http://app.dbus.eus/SSIIMovilWSv2/ws/cons`) · Los feeds GTFS-RT redirigen
  a S3 y dan 403; esta API es la que usa la app oficial y no necesita auth.
- 2026-11-22 · `codParada` = código público de la marquesina, no id interno ·
  Es lo que espera `tiemposParada`.
- 2026-11-22 · Sin fallback a datos mock en producción · Evita avisos falsos.
- 2026-11-22 · Ejecutar el bot con `npm run start`, no `npm run dev` ·
  Los reinicios de `tsx watch` provocaban 409; además existe retry automático.

## Esperando a Iker
- Enviar `/start` a `@dbus_alarm_bot` para confirmar el round-trip real.

## Git
main 63ba889 = origin/main · ramas abiertas: ninguna
(HEAD incluye además el commit de este ESTADO.md)

## Siguiente sesión
- Leer primero: `ESTADO.md`, `README.md`, `bot/src/dbus-client.ts`,
  `bot/src/handlers.ts`.
- Empezar por: arreglar los 30 errores TS de `src/` (Sprint 3) o validar el
  bot con un mensaje real desde Telegram.
- Reglas específicas: el bot nunca usa mocks en producción; el token solo se
  pasa por la variable de entorno `BOT_TOKEN`, nunca se commitea.
