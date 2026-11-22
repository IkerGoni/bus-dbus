# Estado — bus-dbus

Actualizado: 2026-11-22 10:19 · Última sesión: remate de A+B, verificación y reinicio en producción

> **`AGENTS.md` es la fuente de verdad.** Este archivo es el estado de sesión;
> si algo aquí contradice a `AGENTS.md`, manda `AGENTS.md`.

## Objetivo actual

Bot de Telegram operativo con datos reales de Dbus. A (índice local de
coordenadas) y B (`/ubicacion` sobre índice local) están hechos, verificados y
desplegados. Ya no hay trabajo en curso en el lane bot.

## Hecho en esta sesión

- **Consolidación (ya existía al abrir la sesión).** `main = 84db974 =
  origin/main`, árbol limpio. El historial ya contenía A (`bc296b2` → merge
  `b99eeab`) y B (`3f2cb80` → merge `9689ba6`), más los commits de docs
  `3791453` (PID 60268) y `84db974` (wireframes en `docs/05-ui`). No existían
  los refs `task/coords` / `consolidate/main` ni los worktrees `../bus-dbus-*`:
  se eliminaron al fusionar. No hubo nada que integrar ni merge nuevo.
  **Los cambios de docs que antes estaban sin commitear ya están commiteados en
  `84db974`**; el árbol está limpio.
- **Verificaciones sobre el árbol integrado** ( todas OK ):
  - `pnpm --filter @dbus/bot verify` → 70/70 tests (4 ficheros).
  - `pnpm verify` → lint + typecheck + tests de contratos/core/bot/mobile OK.
  - `pnpm --filter @dbus/bot build` → `tsc -b` OK.
- **Contrato del índice A** (`apps/bot/data/dbus-stop-coordinates.json`):
  - `generatedAt` 2026-11-22T08:40:01.885Z · `source` OpenStreetMap (Overpass) ·
    `attribution` "© OpenStreetMap contributors, ODbL".
  - `coverage` 548 catálogo = 514 con coords + 34 sin coords (suma correcta;
    514 filas en `stops`).
  - 514/514 `cod` numéricos sin ceros a la izquierda, presentes en
    `dbus-catalog.json`, únicos, con lat/lon válidos. No hizo falta tocar
    `nearby.ts`: `parseStopRefs` ya normaliza los ceros a la izquierda.
- **Smoke test local del índice real** (sin red, sin producción, script desechable
  con `tsx`, no commiteado): `getNearbyStops(43.32, -1.982)` → 8 paradas reales
  del catálogo, todas a ≤ 500 m (`config.nearbyRadiusM`) y ordenadas por
  distancia: 321 (43.7 m), 103 (52.0 m), 207 (60.0 m), 495 (63.8 m), 80 (79.8 m),
  157 (123.4 m), 32 (154.1 m), 225 (154.6 m). Descripción coincide con el
  catálogo en todas.
- **Rutas degradadas validadas en local**: índice ausente y JSON corrupto
  producen `NearbyUnavailableError` (sin paradas inventadas) y el texto graceful
  sugiere `/buscar`. El mensaje de `/ubicacion` incluye atribución ODbL.
- **Dato de búsqueda**: "Boulevard" → líneas 5 y 16 incluyen la parada 340
  "Pío Baroja 10" (catálogo real). `searchStops("Boulevard")` devuelve las 6
  paradas con "Boulevard" en su nombre; la 340 se alcanza por destino de línea.
- **Despliegue (regla 9) con autorización explícita de Iker en esta sesión**:
  - Git limpio, `HEAD = origin/main = 84db974`, todo pusheado.
  - PID vivo comprobado con lock + `ps`: lock `60766`, `node dist/index.js`
    PID `60766` (padre npm `60739`). **No era 31382 ni 60268**.
  - SIGTERM a `60766`: proceso muerto en 2 s, lock liberado, sin procesos
    residuales.
  - Recompilado desde `apps/bot` (`pnpm --filter @dbus/bot build`).
  - Arrancado desde `apps/bot` con `npm run start` y `BOT_TOKEN` solo por entorno
    (leído del `.env` de la raíz, sin imprimirlo), desacoplado con doble `fork` +
    `setsid` (sin `exec`) para que no muera al cerrar el shell del harness.
  - **Proceso vivo: PID `64221`** (padre `64195` = npm), lock `64221`, un solo
    `node dist/index.js`. Log (`$TMPDIR/dbus-bot.log`) con `Bot handlers
    registered` / `Dbus Telegram Bot started` y ningún error tras el arranque.

## Pendiente (por prioridad)

- [ ] **Validación conversacional real en Telegram**: `/start`, botón "Buscar
      parada o línea", "Boulevard" → parada 340, `/ubicacion` + compartir
      ubicación. No se pudo ejecutar desde el harness: no hay cliente de
      Telegram ni chat de prueba, y `AGENTS.md` prohíbe llamar `getUpdates` a
      mano mientras hay polling. Queda para Iker en la app. Las rutas de código
      equivalentes sí se validaron en local con datos reales (ver arriba).
- [ ] Rotar el token de Telegram (aviso de seguridad de sesiones previas).
- [ ] PWA Sprint 3: 30 errores TS y 233 de lint (kanban `dbus-app`, lane mobile).
- [ ] Confirmar con Iker que el commit de docs `84db974` es el destino final
      del lane de docs (ya no hay cambios sin commitear; no se descartó nada).

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
- Índice de coordenadas = fuente única para `/ubicacion`; sin Overpass en
  caliente. Atribución ODbL visible en el mensaje.
- Docs: `AGENTS.md` manda; `README.md` es uso; ADRs son decisiones históricas.

## Esperando a Iker

- Rotación del token de Telegram (no bloquea nada).
- Validación conversacional real en Telegram con el bot vivo (PID 64221).
- Confirmar el destino del commit de docs `84db974`.

## Git

`main 84db974 = origin/main` ✓ · rama local: solo `main` · worktrees: 1 (raíz) ·
stash: vacío · árbol limpio. Sin refs de trabajo (`task/coords`,
`consolidate/main`): se eliminaron tras los merges. No se hicieron pushes ni
merges nuevos en esta sesión: todo ya estaba integrado y pusheado.

## Siguiente sesión

- Leer primero: `AGENTS.md`, `ESTADO.md`.
- Bot desplegado en PID `64221`; comprobar lock + `ps` antes de cualquier
  reinicio (los PIDs cambian).
- Reglas específicas: alcance solo `apps/bot/` y `packages/**` aditivo; no
  tocar `apps/mobile`, CI ni state/kanban; secretos solo por entorno; todo
  cambio termina con `pnpm --filter @dbus/bot verify` y `pnpm verify`, commit +
  push y reinicio **autorizado**.
