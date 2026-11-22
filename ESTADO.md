# Estado — bus-dbus

Actualizado: 2026-11-22 07:00 · Última sesión: auditoría y refinado de documentación

> **`AGENTS.md` es la fuente de verdad.** Este archivo es el estado de sesión;
> si algo aquí contradice a `AGENTS.md`, manda `AGENTS.md`.

## Resumen actual

| Área | Estado |
|------|--------|
| Bot de Telegram | ✅ Activo con datos reales, commit `8445239`, PID `51205`, `node dist/index.js`, lock y log verificados |
| Calidad del bot | ✅ 40/40 tests · `tsc` 0 errores · `biome check src/ tests/` limpio · `build` OK |
| Descubrimiento | ✅ `/buscar` + `/linea` sobre catálogo real (39 líneas, 127 itinerarios, 548 paradas) |
| Favoritos | ✅ parada completa, parada+línea y línea favorita |
| PWA (Sprint 3) | 🔧 Bloqueada: 30 errores TS y 233 de lint en `src/` (preexistente) |
| Docs | ✅ `README.md` y `AGENTS.md` refinados; ADR 005 y planning avisan de que los `.pb` dan 403 |
| Seguridad | ⚠️ Token compartido en texto plano: pendiente de rotación por Iker |

## Objetivo

PWA de llegadas Dbus (San Sebastián) + bot de Telegram con avisos. Hoy solo
el bot está operativo y desplegado. La PWA sigue fuera del alcance salvo
petición explícita.

## Historial (comprimido)

### Datos reales y endurecimiento (`f1ed9a0`)
- Migración de GTFS-RT/protobuf (`.pb` → S3 con 403) a la API JSON de Dbus
  `SSIIMovilWSv2/ws/cons/tiemposParada` (HTTP, sin auth). Sin mocks.
- Alias de línea: id interno `32` = código público `28`; se acepta como entrada,
  se persiste el código público.
- Bug real: `updateAlert()` enlazaba booleanos y SQLite fallaba; ahora `1/0`.
  Esto rompía desactivación y caducidad de avisos.
- Avisos: dedup por `(chat, parada, línea)`, 7 días, preferencia GPS, filtro de
  destino, guard de reentrada y aislamiento de envíos por chat.
- Arranque: lock PID + retry de 409 (12 intentos, hasta 30 s). Sin `tsx watch`.

### Consolidación y QoL (`65d2b08`, `e10eb3c`)
- `validateLineAtStop`/`CATALOG_ERROR`, `findRow<T>` y `normalizeLine`
  centralizados. Sin cambios de comportamiento.
- Catálogo en paralelo, caché limitada a 200 entradas con evicción, ETA en la
  confirmación de avisos, feedback correcto ante duplicados y `/estado`.

### Búsqueda y favoritos libres (`8445239`)
- Eliminadas "paradas populares": lista arbitraria de 3 itinerarios semilla.
- `bot/data/dbus-catalog.json` generado por `npm run build:catalog`: índice de
  descubrimiento, **no** fuente de llegadas (estas siguen en vivo).
- `/buscar` (código, nombre, línea o destino) y `/linea 28` → destinos →
  paradas → llegadas. Callbacks stateless.
- Favoritos: parada completa (`line_id=''`), parada+línea y línea
  (`user_favorite_lines`). Aceptadas líneas alfanuméricas (`B4`, `B9`).
- GPS descartado: la API de Dbus no devuelve coordenadas; datos de prueba
  quedaron solo en TMPDIR, nunca en el repo.

### Documentación (sesión actual)
- `AGENTS.md` nuevo con reglas, verificación y despliegue.
- `README.md`: recuento de tests 40/40, rutas de lint correctas, secretos por
  entorno (sin `.env`), identidad de línea, semántica real de favoritos y
  estado de GTFS-RT.
- `ESTADO.md`: reescrito como estado actual + historial comprimido.
- ADR 005 y `04-planning/mvp-scope.md`: aviso de que el diseño `.pb` no aplica
  al bot porque los feeds devuelven 403.

## Pendiente (por prioridad)

- [ ] Rotar el token de Telegram y reiniciar con el nuevo valor por entorno.
- [ ] Validar conversación real: enviar `/start` a `@dbus_alarm_bot`.
- [ ] Comprobar disparo de aviso con bus en servicio (`tipo=1`, GPS).
- [ ] PWA Sprint 3: 30 errores TS y 233 de lint (tareas en kanban `dbus-app`).
- [ ] M4/M5: notificaciones push y beta con testers.

## Decisiones tomadas (no reabrir)

- Fuente del bot = API JSON de Dbus sobre HTTP; los feeds `.pb` redirigen a S3
  y dan 403. Sin fallback a mocks en producción.
- `codParada` = código visible en marquesina, no id interno.
- Producción con `npm run start`, nunca `tsx watch`; un solo proceso/polling.
- Línea = código público; id interno solo alias de entrada. En búsquedas gana
  el código público porque `32` también es una línea pública.
- El catálogo JSON es índice de descubrimiento regenerable; las llegadas
  siempre se consultan en vivo.
- Sin "paradas populares": `/buscar` y `/linea` son la UX de descubrimiento.
- Docs: `AGENTS.md` manda; `README.md` es uso; ADRs son decisiones históricas.

## Git

`8ce2694` → `f1ed9a0` → `65d2b08` → `e10eb3c` → `8445239`, todos pusheados.
Esta sesión añade cambios de documentación pendientes de commit/push.
Ramas abiertas: ninguna. El token solo se pasa por entorno; nunca en archivos
ni commits.

## Siguiente sesión

- Leer primero: `AGENTS.md`, `README.md`, `ESTADO.md`.
- Empezar por: rotar el token y validar el round-trip real en Telegram.
- No reabrir: mocks, `.pb`, `tsx watch` en producción, listas de paradas
  "populares", ni escribir el token en archivos.
