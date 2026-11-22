# Estado — bus-dbus

Actualizado: 2026-11-22 12:11 · Última sesión: auditoría, remediación y despliegue

> **`AGENTS.md` es la fuente de verdad.** Este archivo es el estado de sesión;
> si algo aquí contradice a `AGENTS.md`, manda `AGENTS.md`.

## Objetivo actual

Bot de Telegram con datos reales de Dbus operativo y con la remediación de la
auditoría **desplegada**. No hay trabajo en curso en el lane bot. Siguiente
hito: validación conversacional en Telegram y decisión de prioridad (PWA vs.
deuda opcional del bot).

## Hecho en la última sesión

- Auditoría solo lectura de `apps/bot/**` + `packages/{core,contracts}/**`:
  14 problemas reales (13 corregidos; R6 queda para Iker), 5 hallazgos
  opcionales documentados.
- **Remediación committed, pusheada y desplegada** (`484f42b` + docs
  `7823788`, `b9cbae4`, `bbe125b`):
  - avisos reales de Dbus conservados (`SIN_SERVICIO` muestra motivo);
  - una sola consulta en vivo por comando (sin doble/triple fetch);
  - `.catch` en timers, dedup de favoritos con `UNIQUE` + migración v3;
  - lock con `wx` y retry 409 extraído a `telegram-launch.ts`;
  - callbacks de avisos stateless; catálogo validado con Zod;
  - identidad de línea en `@dbus/core`; Overpass muerto eliminado;
  - `escapeMd` y `normalizeLine` corregidos.
- Tests: bot **86** (antes 70), core **14** (antes 12); sin silent-skips de
  red. Auditoría final independiente: 13/13 cerrados, sin P0/P1 nuevos.
- **Reinicio regla 9 completado** con autorización: PID `64221` detenido,
  recompilado, arrancado con `npm run start` y `BOT_TOKEN` solo por entorno.
  Bot vivo **PID `80402`** (npm `80382`), lock `80402`, log sin errores ni
  conflictos desde el arranque.
- Verificación: `pnpm --filter @dbus/bot verify` 86/86 · `pnpm verify` OK ·
  `pnpm --filter @dbus/bot build` OK.

## Pendiente (por prioridad)

- [ ] **Validación conversacional real** con PID `80402`: `/start`, botón
      "Buscar parada o línea", `Boulevard` → parada 340, `/ubicacion` +
      compartir ubicación. No se pudo hacer desde el harness (sin cliente de
      Telegram; `AGENTS.md` prohíbe `getUpdates` a mano con polling activo).
- [ ] **Rotar token de Telegram** (R6): hay un token real en claro en `.env`
      de la raíz (gitignored, no commiteado). Requiere BotFather.
- [ ] Decidir prioridad: PWA Sprint 3 (30 errores TS, 233 lint; lane mobile,
      **no tocar sin acuerdo**) vs. deuda opcional del bot (O1 refresco ~2,7
      req/s, O4 `actions.ts`) — O3 y O4 fueron desaconsejados en la auditoría;
      O1 solo si Dbus presiona.
- [ ] Residuales menores, solo si aparecen: pid-reuse en `pidAlive()` (falsa
      negación rara) y ventana microscópica en el reclaim del lock caducado;
      15 tests de integración siguen dependientes de `app.dbus.eus`.
- [ ] Revisar `docs/06-api-externa/**` (riesgo de corte de acceso a la API):
      no hay plan de contingencia implementado.

## Decisiones tomadas (no reabrir)

- 2026-11-22 · `SIN_SERVICIO` = HTTP 200 + `avisos`, estado operativo real,
  no outage. El motivo se muestra al usuario.
- 2026-11-22 · Reinicio de producción solo con autorización explícita;
  `git push` no despliega. Regla 9 ya ejecutada para la remediación.
- 2026-11-22 · Los callbacks de avisos son stateless: parada y línea viajan
  en `callback_data`; la sesión efímera es solo para texto libre.
- 2026-11-22 · Sin Overpass en caliente: `/ubicacion` usa el índice local
  validado con Zod; sin coordenadas inventadas; atribución ODbL visible.
- 2026-11-22 · No dividir módulos del bot: la arquitectura es sana; los
  problemas eran duplicación local, no límites mal trazados.
- Fuente del bot = API JSON de Dbus sobre HTTP; feeds `.pb` → 403. Sin mocks
  ni fallbacks en producción. Llegadas siempre en vivo.
- `codParada` = código visible en marquesina, no id interno.
- Producción con `npm run start`, nunca `tsx watch`; un solo proceso/polling.
- Línea = código público; id interno solo alias de entrada. En búsquedas gana
  el código público porque `32` también es línea pública.
- Catálogo JSON = índice de descubrimiento regenerable, nunca fuente de
  horarios. Sin "paradas populares": `/buscar` y `/linea` son la UX.
- Docs: `AGENTS.md` manda; `README.md` es uso; ADRs son decisiones históricas.

## Esperando a Iker

- Rotación del token de Telegram (R6).
- Validación conversacional en la app con el bot vivo (PID `80402`).
- Prioridad para la próxima sesión: PWA Sprint 3 o deuda optativa del bot.

## Git

`main bbe125b = origin/main` ✓ · ramas abiertas: ninguna · worktrees: 1 (raíz) ·
stash: vacío · árbol limpio. Commit externo `fbcb70e` (`docs/06-api-externa/**`)
ya integrado en `main` sin solapes.

## Siguiente sesión

- Leer primero: `AGENTS.md`, `ESTADO.md`.
- Empezar por: validación conversacional con PID `80402`; si Iker prioriza
  seguridad, rotar el token antes.
- Reglas específicas: alcance solo `apps/bot/**` y `packages/**` aditivo; no
  tocar `apps/mobile`, CI ni `state/kanban` sin acuerdo; secretos solo por
  entorno; todo cambio termina con `pnpm --filter @dbus/bot verify` y
  `pnpm verify`, y solo se despliega con autorización (regla 9).
