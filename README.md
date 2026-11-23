# Bus Donostia — bot de Telegram + app Android

Llegadas en vivo y avisos «X min antes» de los autobuses Dbus (San Sebastián).
Dos productos sobre la API JSON de Dbus: un bot de Telegram en producción y una
app Android nativa en desarrollo. Proyecto no oficial, sin afiliación con Dbus.

**Reglas:** [`AGENTS.md`](AGENTS.md) (mandan) · **estado de sesión:**
[`ESTADO.md`](ESTADO.md) · **índice de docs:** [`docs/README.md`](docs/README.md).

## Estado (2026-11-22)

| Área | Estado | Evidencia |
|---|---|---|
| Bot de Telegram (`apps/bot`) | ✅ En producción, datos reales | `pnpm --filter @dbus/bot verify`: 86 tests (15 contra la API real) |
| App Android (`apps/android`) | 🔧 Sprints 0–4 hechos; Sprint 5 (publicación) pendiente | `./gradlew check assembleDebug`: 94 tests JVM por variante, 0 errores de lint |
| Paquetes compartidos (`packages/`) | ✅ `@dbus/core` (14 tests), `@dbus/contracts` (7 tests) | `pnpm verify` |
| PWA (`apps/mobile`) | 🧊 Congelada (ADR 009), build roto | [`apps/mobile/KNOWN_ISSUES.md`](apps/mobile/KNOWN_ISSUES.md) |

## Stack

| Producto | Tecnología |
|---|---|
| Bot | Node ≥ 22, TypeScript, Telegraf, better-sqlite3, node-cron, Zod, Vitest, Biome |
| Android | Kotlin + Jetpack Compose + Material 3, Room, OkHttp + kotlinx.serialization; minSdk 26, targetSdk 36 (versiones en [`apps/android/README.md`](apps/android/README.md)) |
| Compartido | `@dbus/core` (dominio TS), `@dbus/contracts` (fixtures reales de la API) |
| Datos | API JSON de Dbus, HTTP sin auth; catálogo offline regenerable desde la misma API |
| CI | GitHub Actions: `.github/workflows/ci.yml` (Node) y `android.yml` (Gradle) |

## Estructura

```
.
├── apps/
│   ├── bot/            # @dbus/bot — Telegram, SQLite, API JSON (producción)
│   │   ├── src/        # handlers, commands, callbacks, render, alerts, db…
│   │   ├── data/       # dbus-catalog.json (548 paradas, 39 líneas, 127 itinerarios)
│   │   │               # + dbus-stop-coordinates.json (514 con coordenadas OSM)
│   │   ├── scripts/    # build-catalog.mjs, build-stop-coordinates.mjs, refresco semanal
│   │   └── tests/      # 8 archivos, 86 tests
│   ├── android/        # App Android (Gradle, fuera de pnpm)
│   └── mobile/         # @dbus/mobile — PWA congelada (ADR 009)
├── packages/
│   ├── core/           # @dbus/core — dominio compartido
│   └── contracts/      # @dbus/contracts — fixtures reales y catálogo
├── docs/               # ver docs/README.md
├── .github/workflows/  # ci.yml, android.yml
├── AGENTS.md · ESTADO.md · README.md
└── package.json · pnpm-workspace.yaml · tsconfig.json · biome.json
```

## Comandos

```bash
# Raíz (pnpm workspaces)
pnpm verify                      # lint + typecheck + tests de todos los paquetes

# Bot (apps/bot)
pnpm --filter @dbus/bot verify   # tsc -b + biome + 86 tests
pnpm --filter @dbus/bot build    # -> apps/bot/dist
cd apps/bot && npm run build:catalog   # regenerar el catálogo desde Dbus

# Android (toolchain en apps/android/README.md)
cd apps/android && ./gradlew check assembleDebug
```

Despliegue del bot: [`AGENTS.md`](AGENTS.md) § Despliegue (`git push` no despliega).

## Bot de Telegram

El token se lee solo de la variable de entorno `BOT_TOKEN`:

```bash
cd apps/bot
export BOT_TOKEN='...'           # solo en el entorno; nunca en .env ni en el repo
pnpm run build && npm run start  # producción (sin watch, evita conflictos 409)
```

`apps/bot/.env.example` es plantilla para el resto de variables. `pnpm run dev`
usa `tsx watch`, que reinicia el polling y provoca conflictos `409`: no usarlo
con el bot de producción en marcha.

Variables de entorno (default entre paréntesis):
- `BOT_TOKEN` — token de Telegram (requerido)
- `DBUS_BASE_URL` — API JSON (`http://app.dbus.eus/SSIIMovilWSv2/ws/cons`)
- `BOT_LANGUAGE` — `eu|es|en|fr` (`es`)
- `CACHE_TTL_MS` (`30000`) · `POLL_INTERVAL_MS` (`30000`)
- `DEFAULT_ALERT_MINUTES` (`5`) · `DB_PATH` (`./bot.db`)
- `NEARBY_ENABLED` (`1`) · `NEARBY_RADIUS_M` (`500`, 50–2000) para `/ubicacion`
- `DBUS_CATALOG_PATH`, `DBUS_STOP_COORDINATES_PATH` — rutas de los índices (`apps/bot/data/`)
- `LOG_LEVEL` — `debug|info|warn|error|silent` (`info`)

Comandos: `/start`, `/buscar <texto>`, `/linea <linea>`, `/lineas [parada]`,
`/parada <codigo> [linea]`, `/aviso <parada> <linea> <minutos>`, `/lista`,
`/borrar <id>`, `/fav`, `/ubicacion`, `/actualizar`, `/estado`, `/help`. El
código de parada es el número de la marquesina (p. ej. `1` = Boulevard 9).
Detalle de pantallas, textos y callbacks:
[`docs/05-ui/02-bot-telegram.md`](docs/05-ui/02-bot-telegram.md).

**Cómo encontrar tu parada o línea.** Sin lista de «populares». `/buscar` (o
🔎) acepta código (`1`), calle (`Boulevard`), línea (`28`, `L28`) o destino
(`Ospitaleak`). `/linea 28` muestra los destinos y, al elegir uno, sus paradas.
`/ubicacion` lista paradas cercanas con el índice local de OpenStreetMap; la
ubicación no se guarda. Desde las llegadas se guarda la parada completa o una
sola línea; los favoritos son la pantalla de inicio.

El catálogo `apps/bot/data/dbus-catalog.json` es solo un índice de
descubrimiento: las llegadas siempre se consultan en vivo. Identidad de línea,
favoritos y avisos (dedup, caducidad a 7 días, preferencia GPS): reglas 5–7 de
[`AGENTS.md`](AGENTS.md).

Calidad de vida: caché en memoria de 200 paradas con evicción; `stop_refresh`
invalida solo la parada consultada; lock de instancia única y reintento de los
`409` de Telegram con backoff (12 intentos, hasta 30 s).

## App Android

Kotlin + Compose, sin backend ni Firebase (ADR 009). Cuatro pestañas: Inicio,
Paradas (cercanas offline), Líneas y Avisos. Cada llegada de A05 tiene ★
(favorita parada+línea) y 🔔 (aviso). Los avisos son de **un solo uso**: un
servicio en primer plano sondea (30 s si faltan ≤ 7 min para avisar, 1 min
hasta 14, 2 min más allá), avisa 30 s antes y borra el aviso. Inicio solo
ofrece «Pausar».

Spec: [`docs/07-android-mvp/`](docs/07-android-mvp/README.md) · plan:
[`SPRINTS.md`](docs/07-android-mvp/SPRINTS.md) · toolchain:
[`apps/android/README.md`](apps/android/README.md).

## Datos en tiempo real

- **Fuente única:** `GET http://app.dbus.eus/SSIIMovilWSv2/ws/cons/tiemposParada?codParada={codigo}&idioma=es`
  (HTTP, sin auth; GPS `tipo=1` u horario). Endpoints y riesgos:
  [`docs/06-api-externa/`](docs/06-api-externa/README.md).
- **Descartado:** los feeds GTFS-RT `.pb` de `opendata.euskadi.eus` dan
  `403 AccessDenied`.

## Licencia

El código de este repositorio se distribuye bajo la [Licencia MIT](LICENSE).
Las fuentes tipográficas empaquetadas en `apps/android/licenses/` conservan su propia licencia SIL Open Font License 1.1.
