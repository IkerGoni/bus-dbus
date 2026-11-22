# Estado — bus-dbus

Actualizado: 2026-11-22 09:30 · Última sesión: reinicio en producción y diagnosis de SIN_SERVICIO

> **`AGENTS.md` es la fuente de verdad.** Este archivo es el estado de sesión;
> si algo aquí contradice a `AGENTS.md`, manda `AGENTS.md`.

## Objetivo actual

Bot de Telegram operativo con datos reales de Dbus. Cerrada la versión `a7ff1a4`
(catálogo con escaneo de ids 1..1000) ya desplegada. Prioridad siguiente: dos
tareas en paralelo — (A) índice local de coordenadas de paradas desde OSM y
(B) `/ubicacion` sin depender de Overpass en caliente.

## Hecho en la última sesión

- **Reinicio de producción (regla 9) con autorización explícita del usuario.**
  - Git limpio, `HEAD = origin/main = a7ff1a4`.
  - `pnpm --filter @dbus/bot verify` → 62/62; `pnpm verify` → OK. `build` → OK.
  - SIGTERM a PID `12954`, lock liberado; arranque desde `apps/bot` con
    `npm run start` y `BOT_TOKEN` por entorno (clave literal `$BOT_TOKEN=` en
    `.env` de la raíz; parseada sin imprimirla).
  - Proceso vivo: **PID `31382`**, un solo `node dist/index.js`, lock `31382`,
    log con `Bot handlers registered` / `Dbus Telegram Bot started` y sin
    errores tras el arranque.
- **Diagnosis de `SIN_SERVICIO`**: no fuimos nosotros. Llamadas directas con
  `curl` (sin pasar por el bot) devuelven HTTP 200 y el estado literal de Dbus,
  con `avisos` reales de supresiones/traslados. `/itinerariosLinea?idLinea=28`
  sigue en `DATOS`. Muestreo de 20 paradas del índice: 12 `DATOS` con tiempos
  reales (p. ej. parada 100 → L9 a Boulevard en 14 min) y 8 `SIN_SERVICIO`.
  Nuestro build solo consulta `/itinerariosLinea`, nunca `/tiemposParada`, y el
  patrón ya existía antes del reinicio de hoy. No hay indicios de sobrecarga
  provocada por nuestro escaneo (ni 429/403 ni 5xx).
- **`/ubicacion`**: el bot respondió correctamente sin inventar paradas cuando
  Overpass no respondió. Sondeo directo a `overpass-api.de` dio HTTP 406.
  Cookbook: migrar a índice local de coordenadas (tarea A + B).

## Pendiente (por prioridad)

- [ ] **A · Índice local de coordenadas**: script en `apps/bot/scripts/` que
      consiga una vez las coordenadas de todas las paradas desde OSM y guarde
      `apps/bot/data/dbus-stop-coordinates.json` (solo datos reales, sin
      relleno; con `generatedAt`, `source`, `attribution` y cobertura). Luego
      commit + push. Ver prompts en el chat de la sesión anterior.
- [ ] **B · `/ubicacion` sobre índice local**: que `nearby.ts` use ese JSON en
      vez de Overpass en caliente; eliminar el circuit breaker de red; test de
      regresión; `verify` + commit + push + **reinicio con autorización**.
      A y B son paralelizables si B define primero el contrato del JSON.
- [ ] Rotar el token de Telegram (aviso de seguridad de sesiones previas).
- [ ] Validar conversación real: `/start`, botón "Buscar parada o línea",
      "Boulevard" → parada 340 (Pío Baroja 10), `/ubicacion`.
- [ ] PWA Sprint 3: 30 errores TS y 233 de lint (kanban `dbus-app`).

## Decisiones tomadas (no reabrir)

- 2026-11-22 · `SIN_SERVICIO` es el estado operativo real que devuelve Dbus
  (HTTP 200 + `avisos`), no un outage ni un efecto de nuestro escaneo.
- 2026-11-22 · Reinicio de producción solo con autorización explícita; `git
  push` no despliega.
- Fuente del bot = API JSON de Dbus sobre HTTP; feeds `.pb` → 403. Sin mocks.
- `codParada` = código visible en marquesina, no id interno.
- Producción con `npm run start`, nunca `tsx watch`; un solo proceso/polling.
- Línea = código público; id interno solo alias de entrada. En búsquedas gana
  el código público porque `32` también es línea pública.
- Catálogo JSON = índice de descubrimiento regenerable; llegadas siempre en vivo.
- Sin "paradas populares": `/buscar` y `/linea` son la UX de descubrimiento.
- Sin coordenadas inventadas: si falla la fuente, se dice y se sugiere `/buscar`.
- Docs: `AGENTS.md` manda; `README.md` es uso; ADRs son decisiones históricas.

## Esperando a Iker

- Rotación del token de Telegram (no bloquea A ni B).
- Lanzar las dos sesiones paralelas con los prompts entregados en el chat.

## Git

`main a7ff1a4 = origin/main` ✓ · ramas abiertas: ninguna · worktrees: 1 (raíz)
· stash: vacío · working tree limpio (salvo este `ESTADO.md` del cierre).

## Siguiente sesión

- Leer primero: `AGENTS.md`, `ESTADO.md`.
- Empezar por: ejecutar los prompts A y B en paralelo (están completos en el
  chat de la sesión anterior).
- Reglas específicas: alcance solo `apps/bot/` y `packages/**` aditivo; no
  tocar `apps/mobile`, CI ni state/kanban; secretos solo por entorno; todo
  cambio termina con `pnpm --filter @dbus/bot verify` y `pnpm verify`, commit +
  push y reinicio **autorizado**.
