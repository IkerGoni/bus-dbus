# Estado — bus-dbus
Actualizado: 2026-11-22 06:05 · Última sesión: mejoras y endurecimiento del bot Dbus

## Objetivo actual
PWA de llegadas de Dbus (San Sebastián) + bot de Telegram con avisos.
El bot ya opera con datos reales de la API oficial; la PWA sigue bloqueada
en el Sprint 3 por errores preexistentes de TypeScript/lint.

## Hecho en la última sesión
- Auditoría del aviso `❌ No hay paradas configuradas para L32`: solo existía
  en commits antiguos (`f86b23a` y anteriores), nunca en el código/dist actual.
  Causa: `AVAILABLE_LINES` incluía `32` pero `STOPS_BY_LINE` solo tenía 12/17/18.
- Alias de línea: Dbus usa id interno `32` = código público `28`. Se aceptan
  ambos (`28`, `L28`, `32`) y siempre se guarda/muestra el código público.
- Bug real corregido: `updateAlert()` enlazaba booleanos y SQLite fallaba
  (`SQLite3 can only bind numbers...`) — rompía la desactivación de avisos,
  incluido el caducado automático. Ahora convierte `true/false` a `1/0`.
- Avisos: dedup por `(chat, parada, línea)`, caducidad a 7 días, filtro por
  destino tolerante (contención) y por GPS/horario, guard de reentrada del
  scheduler, aislamiento de fallos de envío por chat.
- Favoritos deduplicados; `/lista` con botones `adel_` para borrar cada aviso;
  `/actualizar` recarga catálogo completo; `stop_refresh` invalida solo una
  parada; sesiones con TTL de 10 min y poda; `safeEdit` tolera mensajes
  anticuados/"not modified" y responde como mensaje nuevo cuando hace falta.
- Arranque: lock de instancia única (PID, tolera locks caducados) y retry de
  conflictos 409 con 12 intentos y backoff hasta 30 s.
- Sin mocks en producción: eliminado `FALLBACK_STOPS`; catálogo vacío = error
  explícito. `getJson` reintenta una vez con 300 ms.
- 27 tests en verde (10 unit + 10 integración + 7 E2E); `tsc`, `biome check
  src/` y `build` del bot sin errores. README actualizado.
- Migración previa: GTFS-RT/protobuf (`.pb` con 403 desde S3) → API JSON real
  de Dbus `SSIIMovilWSv2/ws/cons/tiemposParada` (HTTP, sin auth).
- Eliminados `gtfs-client.ts`, `src/proto/` y la dependencia `protobufjs` del bot.

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
- 2026-11-22 · Línea = código público (`28`), nunca el id interno (`32`) ·
  Se acepta el id como alias de entrada por compatibilidad, pero no se
  persiste: evita avisos duplicados para la misma línea con dos identificadores.

## Esperando a Iker
- Enviar `/start` a `@dbus_alarm_bot` para confirmar el round-trip real.

## Git
Base: main 8ce2694 = origin/main · ramas abiertas: ninguna
Los cambios de esta sesión están commiteados localmente, pendientes de push
(y el bot hay que reiniciarlo con `BOT_TOKEN` del entorno).

## Siguiente sesión
- Leer primero: `ESTADO.md`, `README.md`, `bot/src/dbus-client.ts`,
  `bot/src/handlers.ts`.
- Empezar por: arreglar los 30 errores TS de `src/` (Sprint 3) o validar el
  bot con un mensaje real desde Telegram.
- Reglas específicas: el bot nunca usa mocks en producción; el token solo se
  pasa por la variable de entorno `BOT_TOKEN`, nunca se commitea.
