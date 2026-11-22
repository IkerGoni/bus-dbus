# AGENTS.md — bus-dbus

Fuente de verdad del proyecto. Si este archivo contradice a un doc antiguo,
manda aquí. `README.md` = visión y uso; `ESTADO.md` = estado de sesión;
`docs/README.md` = índice de docs (qué mirar para cada cosa);
`docs/05-ui/02-bot-telegram.md` = UI del bot (el resto de `docs/05-ui/` es la
PWA, histórico); `docs/07-android-mvp/` = spec del MVP Android (ADR 009);
`docs/03-architecture/` = ADRs (001–008 históricos, 009 vigente);
`docs/04-planning/` y `docs/plan/` = planning histórico de la PWA y del split.

## Qué hay hoy

- **Bot de Telegram** (`apps/bot/`): activo, datos reales de la API JSON de
  Dbus. Paquete `@dbus/bot`. SQLite en `apps/bot/bot.db`.
- **App Android** (`apps/android/`): Sprints 0–4 hechos (último merge
  `b8e08f8`); Sprint 5 (publicación) pendiente. MVP v1 nativo, Kotlin +
  Compose, Gradle fuera de pnpm. Spec en `docs/07-android-mvp/`, plan en
  `docs/07-android-mvp/SPRINTS.md`, prompt pendiente en
  `docs/07-android-mvp/prompts/SPRINT-5.md`, decisión en ADR 009.
- **PWA** (`apps/mobile/`): **congelada** por ADR 009 (build roto, ver
  `KNOWN_ISSUES.md`). No arreglar ni borrar sin acuerdo. Paquete `@dbus/mobile`.
- **Shared** (`packages/core/`, `packages/contracts/`): dominio y contratos
  extraídos en el split. `@dbus/core`, `@dbus/contracts`.
- Monorepo pnpm (`pnpm-workspace.yaml`), Node >= 22.

**Convención de lanes:** `apps/bot/**` es propiedad del lane bot;
`apps/android/**` del lane android; `apps/mobile/**` del lane mobile (congelado); `packages/**` es compartido y solo se
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
8. **UX de descubrimiento.** Sin "paradas populares": usar `/buscar`,
   `/linea` y `/lineas` (listado completo de líneas y de sus paradas).
   Callbacks stateless: el dato va en `data` (`srch_s_1`, `ld_1281`,
   `ls_1_28`, `aline_<parada>_<línea>`, `minutes_<parada>_<línea>_<min>`),
   no en sesiones. La única sesión efímera permitida es el texto libre de los
   flujos multi-paso (`session.ts`); nunca se persiste en disco.
9. **Reinicio obligatorio en cada versión nueva del bot.** Todo cambio en
   `apps/bot/**` (o en `packages/**` que el bot consuma) solo existe en
   producción tras reiniciar: commit + push → SIGTERM al PID anterior →
   recompilar → arrancar con `BOT_TOKEN` por entorno y `npm run start` →
   comprobar log y lock con el PID nuevo. `git push` **no** despliega: el
   proceso vivo sigue ejecutando el `dist` anterior. No dar una versión por
   terminada sin este paso; pedir autorización si el reinicio requiere
   intervención en producción.

## Reglas de la app Android

Las reglas 1 (datos reales), 2 (llegadas en vivo), 5 (identidad de línea),
6 (favoritos) y 7 (dedup, caducidad 7 días, preferencia GPS, filtro de
destino) del bot aplican igual. Además:

- Sin SDK de mapas, sin Firebase/FCM y sin servidor propio. Único elemento
  remoto nuestro permitido: un `config.json` estático opcional con copia
  empaquetada (ADR 009); la app nunca depende de él para arrancar.
- Cleartext HTTP solo para `app.dbus.eus` (network security config).
- Nunca mostrar segundos: la API da `minutos` enteros.
- El catálogo empaquetado se genera desde `apps/bot/data/*.json`; no se edita.
- Textos en `strings.xml` `es` + `eu` con las mismas claves.

## Verificación

```bash
pnpm --filter @dbus/bot verify     # tsc -b + biome + 86 tests (BOT_TOKEN + DB_PATH=:memory:)
pnpm --filter @dbus/bot build      # tsc -b -> apps/bot/dist
```

Android (toolchain en `apps/android/README.md`):

```bash
cd apps/android && ./gradlew check assembleDebug   # lint + 94 tests JVM por variante + APK debug
```

Root: `pnpm verify` (lint + typecheck + tests de todos los paquetes). Los
tests E2E/integración usan la API real: si Dbus está caído, decirlo, no
silenciarlo. No dar nada por bueno sin ejecutar la verificación.

## Despliegue (instrucción permanente del usuario)

Aplica a **toda versión nueva del bot**, sin excepción (regla 9): commit +
push, parar el proceso anterior con SIGTERM, recompilar, arrancar desde
`apps/bot` con `BOT_TOKEN` por entorno y `npm run start`, comprobar log, lock y
PID nuevo. Un cambio solo de docs no requiere reiniciar el bot.

## Convenciones

- Mensajes al usuario en español; `escapeMd` antes de interpolar texto externo
  en Markdown de Telegram.
- Preferir funciones puras testeables antes que lógica dentro de handlers.
- No persistir ubicación del usuario: efímera en sesión.
- Tests nuevos en `apps/bot/tests/`; dominio compartido en `packages/core/`.
