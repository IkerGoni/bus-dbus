# AGENTS.md — bus-dbus

Fuente de verdad del proyecto. Si este archivo contradice a un doc antiguo,
manda aquí. `README.md` = visión y uso; `ESTADO.md` = estado de sesión;
`docs/` = wireframes, ADRs y planning; `docs/plan/repo-split.md` = plan de
separación en workspaces (trabajo en curso).

## Qué hay hoy

- **Bot de Telegram** (`apps/bot/`): activo, datos reales de la API JSON de
  Dbus. Paquete `@dbus/bot`. SQLite en `apps/bot/bot.db`.
- **PWA / móvil** (`apps/mobile/`): Sprint 3 bloqueado por errores
  preexistentes de TS/lint. Paquete `@dbus/mobile`.
- **Shared** (`packages/core/`, `packages/contracts/`): dominio y contratos
  extraídos en el split. `@dbus/core`, `@dbus/contracts`.
- Monorepo pnpm (`pnpm-workspace.yaml`), Node >= 22.

**Convención de lanes:** `apps/bot/**` es propiedad del lane bot;
`apps/mobile/**` del lane mobile; `packages/**` es compartido y solo se
modifica de forma aditiva. No tocar el lane ajeno salvo acuerdo explícito.

## Reglas del bot (no negociables)

1. **Datos reales únicamente.** Fuente:
   `http://app.dbus.eus/SSIIMovilWSv2/ws/cons` (JSON, HTTP, sin auth).
   Prohibido mock/fallback en producción. Si no hay datos, se dice. Los feeds
   `.pb` de `opendata.euskadi.eus` dan 403: no revivirlos para el bot.
2. **Llegadas siempre en vivo.** `apps/bot/data/dbus-catalog.json` es solo
   índice de descubrimiento (nombre/código/destino), nunca fuente de horarios.
   Regenerar con `cd apps/bot && npm run build:catalog`; no editar a mano;
   commitear el JSON resultante.
3. **Secretos.** `BOT_TOKEN` solo por entorno. Nunca escribirlo en
   `apps/bot/.env`, imprimirlo, loguearlo ni commitearlo. Si se compartió en
   texto plano, rotarlo. `.env.example` es plantilla, no credencial.
   Los tests inyectan `BOT_TOKEN=vitest_test_token` en el script `test`.
4. **Instancia única.** Lock por PID y retry de 409 de Telegram. Producción
   con `npm run start` desde `apps/bot` (nunca `tsx watch`). No llamar
   `getUpdates` a mano ni levantar un segundo proceso mientras hay polling.
5. **Identidad de línea.** Manda el código público (`28`); el id interno
   (`32`) es alias de entrada. `32` también es línea pública: en búsquedas
   gana la pública. Aceptar líneas alfanuméricas (`B4`, `B9`).
6. **Favoritos.** Parada completa (`line_id=''`), parada+línea y línea
   favorita (`user_favorite_lines`). Dedup por `(chat, parada, línea)`.
7. **Avisos.** Dedup por `(chat, parada, línea)`, caducidad a 7 días,
   preferencia GPS (`tipo=1`), filtro de destino, aislamiento de envíos y
   guard de reentrada del scheduler. No quitar estas garantías.
8. **UX de descubrimiento.** Sin "paradas populares": usar `/buscar` y
   `/linea`. Callbacks stateless: el dato va en `data` (`srch_s_1`, `ld_1281`,
   `ls_1_28`), no en sesiones.

## Verificación

```bash
pnpm --filter @dbus/bot verify     # tsc -b + biome + 40 tests (con BOT_TOKEN)
pnpm --filter @dbus/bot build      # tsc -b -> apps/bot/dist
```

Root: `pnpm verify` (lint + typecheck + tests de todos los paquetes). Los
tests E2E/integración usan la API real: si Dbus está caído, decirlo, no
silenciarlo. No dar nada por bueno sin ejecutar la verificación.

## Despliegue (instrucción permanente del usuario)

Tras cualquier cambio de código: commit + push, parar el proceso anterior con
SIGTERM, recompilar, arrancar desde `apps/bot` con `BOT_TOKEN` por entorno y
`npm run start`, comprobar log y lock. Un cambio solo de docs no requiere
reiniciar el bot.

## Convenciones

- Mensajes al usuario en español; `escapeMd` antes de interpolar texto externo
  en Markdown de Telegram.
- Preferir funciones puras testeables antes que lógica dentro de handlers.
- No persistir ubicación del usuario: efímera en sesión.
- Tests nuevos en `apps/bot/tests/`; dominio compartido en `packages/core/`.
