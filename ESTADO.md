# Estado — bus-dbus
Actualizado: 2026-11-22 06:20 · Última sesión: análisis de features del bot, optimizaciones y QoL

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
- Consolidación: `validateLineAtStop` y `CATALOG_ERROR` unifican la validación
  de línea y el error de catálogo en `handlers.ts`; `findRow<T>` agrupa los
  finders de `db.ts`; `normalizeLine` se usa también en `/aviso` (acepta `L28`,
  `028`). Sin cambios de comportamiento: 27/27 tests siguen en verde.
- Eliminados `gtfs-client.ts`, `src/proto/` y la dependencia `protobufjs` del bot.
- Bot reconstruido y arrancado con `BOT_TOKEN` del entorno: `node dist/index.js`
  en PID 1054, sin conflictos 409, scheduler y refresco de 30 s activos.
  `f1ed9a0` ya pusheado a `origin/main`.

## Hecho en esta sesión (análisis + mejoras del bot)
- Optimización: `fetchPopularStops` pide los 3 itinerarios semilla en paralelo
  (`Promise.all`) en vez de en serie: carga en frío de ~3 latencias a 1.
- Optimización: caché de paradas limitada a 200 entradas; `evictOldestEntries<T>`
  elimina las más antiguas (por inserción) tras cada consulta. Evita que el
  refresco de 30 s visite un mapa que crece sin límite.
- QoL/correctitud: `createAndConfirmAlert` devuelve `created | duplicate |
  no-chat`; `handleMinutesCallback` ya no responde "✅ Aviso creado" cuando el
  aviso fue rechazado por duplicado. El mensaje emergente distingue ambos casos.
- QoL: la confirmación de aviso incluye la ETA de la próxima llegada
  (`min`/`ahora` + 📡/🕒), con fallback determinista sin servicio.
- QoL: comando `/estado` (diagnóstico: fuente, cachés, avisos activos, uptime,
  sin secretos) documentado en `/help` y README.
- Tests: 31/31 en verde (11 unit + 10 integración + 10 E2E). `tsc`, `biome
  check src/ tests/` y `build` sin errores. **Cambios aún no commiteados ni
  desplegados** (el bot en PID 1054 sigue con el build anterior).

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
`8ce2694` → `f1ed9a0` (mejoras del bot) → `65d2b08` (consolidación + docs),
ambos ya pusheados. Los cambios de mejoras/QoL de esta sesión están en el
working tree, **sin commitear ni pushear** a la espera de revisión. Ramas
abiertas: ninguna.
El token solo se pasa por entorno a `npm run start`; nunca en archivos ni en
commits. Recomendación de seguridad: rotar el token porque se compartió en
texto plano en el chat.

## Siguiente sesión
- Leer primero: `ESTADO.md`, `README.md`, `bot/src/dbus-client.ts`,
  `bot/src/handlers.ts`.
- Empezar por: arreglar los 30 errores TS de `src/` (Sprint 3) o validar el
  bot con un mensaje real desde Telegram.
- Reglas específicas: el bot nunca usa mocks en producción; el token solo se
  pasa por la variable de entorno `BOT_TOKEN`, nunca se commitea.
