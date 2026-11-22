# Dbus Donostia — Mobile Web App + Telegram Bot

Aplicación PWA móvil para notificaciones push de llegada de autobús Dbus (San Sebastián) + bot de Telegram con avisos de llegada.

**Reglas y estado detallado:** [`AGENTS.md`](AGENTS.md) · [`ESTADO.md`](ESTADO.md).

## Estado actual (2026-11-22)

| Área | Estado | Evidencia |
|------|--------|-----------|
| **Planning (Sprints 0-2)** | ✅ Completo | 3 docs en `04-planning/`, 12 wireframes en `02-wireframes/`, 8 ADRs en `03-architecture/adrs/` |
| **PWA Shell (Sprint 3)** | 🔧 Bloqueado | `npm run typecheck`: 30 errores TS · `npm run lint`: 233 errores (preexistentes, en `src/`) |
| **Bot: datos reales** | ✅ Activo | API JSON de Dbus (`tiemposParada`), sin mocks en producción |
| **Tests del bot** | ✅ 40/40 pasan | `npm run test` (14 unit + 12 integración + 14 E2E de handlers) |
| **TypeScript del bot** | ✅ 0 errores | `npx tsc --noEmit` en `bot/` |
| **Lint del bot** | ✅ 0 errores | `npx biome check bot/src/ bot/tests/` |
| **Kanban** | `t_dbdce90c` (Sprint 3) en `ready` | `state/kanban/kanban.db` |

## Stack tecnológico

| Capa | Tecnología |
|------|------------|
| Frontend | Vite 6 + React 19 + TypeScript 5.7 |
| Styling | Tailwind CSS 4 (Oxide) |
| PWA / SW | Workbox GenerateSW (vite-plugin-pwa) |
| Push | Web Push API + VAPID |
| BD local | IndexedDB vía `idb` |
| Parsing GTFS-RT | protobufjs + Web Worker (PWA, bloqueado; `.pb` dan 403) |
| State | preact/signals |
| Routing | wouter |
| i18n | i18next (ES + EU) |
| Lint/Format | Biome |
| Tests | Vitest + Playwright |
| Hosting | Cloudflare Pages + Functions |
| Bot Telegram | Telegraf + better-sqlite3 + API JSON Dbus (live) |

## Estructura del repositorio

```
.
├── 02-wireframes/          # Wireframes Excalidraw (onboarding, home, stop, alert, settings...)
├── 03-architecture/        # 8 ADRs (001-frontend-stack ... 008-risk-mitigations)
├── 04-planning/            # mvp-scope.md, milestones.md, success-metrics.md, tech-stack-decision.md
├── bot/                    # Telegram bot (Telegraf, SQLite, API JSON Dbus)
│   ├── src/dbus-client.ts  # Cliente live + caché de la API de Dbus
│   ├── src/arrival.ts      # Normalización y formato de llegadas
│   ├── src/catalog.ts      # Índice de búsqueda (paradas/líneas)
│   ├── data/dbus-catalog.json  # Catálogo real generado (39 líneas, 548 paradas)
│   ├── scripts/build-catalog.mjs  # Regenera el catálogo desde la API
│   └── tests/              # Unit + integración + E2E de handlers
├── fetcher/                # Servicio fetcher GTFS-RT (obsoleto: feeds 403)
├── functions/              # Cloudflare Functions (pendiente)
├── public/                 # Assets estáticos PWA
├── scripts/                # Scripts utilidad (VAPID, etc.)
├── src/                    # PWA frontend (React + Vite)
│   ├── components/ui/      # Componentes base (Button, Input, Card, Toast...)
│   ├── pages/              # Home, StopSelector, StopDetail, AlertConfig, AlertsList, Settings, Offline
│   ├── store/              # preact/signals state (rtData, alerts, settings...)
│   ├── lib/                # db (IndexedDB), gtfs-rt, push, i18n, types
│   └── workers/            # gtfs-rt.parser.worker.ts (Web Worker)
├── state/kanban/           # Base de datos del kanban (dbus-app)
├── .github/                # CI/CD workflows (pendiente)
├── package.json            # Scripts: dev, build, lint, typecheck, test
├── vite.config.ts          # Config Vite + PWA + Workbox
├── tsconfig.json           # Config TypeScript
└── biome.json              # Config Biome (lint/format)
```

## Comandos disponibles

```bash
# Frontend (PWA)
npm run dev           # Servidor de desarrollo (puerto 3000)
npm run build         # Build de producción (tsc + vite build) — ❌ bloqueado por errores TS
npm run lint          # Biome check
npm run lint:fix      # Biome autocorregible
npm run typecheck     # tsc --noEmit — ❌ bloqueado por errores TS
npm run test          # Vitest (tests del bot: 40/40 pasan)

# Bot de Telegram (carpeta bot/)
cd bot && npm run dev   # tsx watch
cd bot && npm run build # tsc build
cd bot && npm run start # node dist/index.js
cd bot && npm run build:catalog  # regenerar data/dbus-catalog.json desde Dbus
```

## Configuración del bot de Telegram

El token del bot se lee de la variable de entorno `BOT_TOKEN`. No está hardcodeado.

```bash
cd bot
export BOT_TOKEN='...'           # solo en el entorno; nunca en .env ni en el repo
npm run build && npm run start   # producción (sin watch, evita conflictos de polling)
```

`bot/.env.example` es una plantilla de referencia para el resto de variables,
pero el token real **nunca** se escribe en `bot/.env` (está en `.gitignore`).
Para desarrollo local se puede usar `npm run dev`, pero `tsx watch` reinicia
el polling y provoca conflictos `409`: usarlo solo en scripting.

Variables de entorno:
- `BOT_TOKEN` — Token del bot de Telegram (requerido)
- `DBUS_BASE_URL` — API JSON de Dbus (default: `http://app.dbus.eus/SSIIMovilWSv2/ws/cons`, HTTP, sin auth)
- `BOT_LANGUAGE` — Idioma de nombres/destinos: `eu|es|en|fr` (default: `es`)
- `CACHE_TTL_MS` — TTL de caché de llegadas (default: `30000`)
- `POLL_INTERVAL_MS` — Refresco en segundo plano (default: `30000`)
- `DEFAULT_ALERT_MINUTES` — Minutos antelación por defecto (default: `5`)
- `DB_PATH` — Ruta de la base de datos SQLite (default: `./bot.db`)

Comandos del bot: `/start`, `/buscar <texto>`, `/linea <linea>`,
`/parada <codigo> [linea]`, `/lineas <codigo>`,
`/aviso <parada> <linea> <minutos>`, `/lista`, `/borrar <id>`, `/fav`,
`/actualizar`, `/estado`, `/help`. El código de parada es el
número visible en la marquesina (p. ej. `1` = Boulevard 9). `/estado` muestra
la fuente de datos, el tamaño de las cachés y los avisos activos (sin secretos).

**Cómo encontrar tu parada o línea.** No hay lista de "populares": es arbitraria
e incompleta. El buscador (`/buscar` o el botón 🔎) acepta código (`1`), nombre
de calle (`Boulevard`), línea (`28`, `L28`) o destino (`Ospitaleak`) y devuelve
botones con las coincidencias reales del catálogo. `/linea 28` muestra los
destinos de la línea y, al elegir uno, todas sus paradas. Desde cualquier vista
de llegadas se puede guardar la **parada completa** (todas las líneas) o solo
**una línea**; `/fav add <parada> [linea]` (línea opcional) y `/fav linea <linea>`
permiten lo mismo por comando. Los favoritos son la pantalla de inicio: un toque
muestra las llegadas en vivo.

`data/dbus-catalog.json` es un **índice de descubrimiento** generado con
`npm run build:catalog` desde la API real de Dbus (39 líneas, 127 itinerarios,
548 paradas). No contiene horarios ni llegadas: esos siempre se consultan en
vivo; regenerar el catálogo actualiza nombres/códigos cuando Dbus cambia su red.

El bot acepta el código público (`28`, `L28`, `B4`) y, como alias de entrada,
el id interno de Dbus (`32` era el id de la pública `28`). En búsquedas gana
el código público: `32` también existe como línea pública. Siempre se guarda
y muestra el código público.

Avisos: dedup por `(chat, parada, línea)`, caducidad a 7 días, preferencia por
estimaciones GPS y aislamiento de fallos de envío por chat. Favoritos: parada
completa, parada+línea y línea favorita; dedup por `(chat, parada, línea)`.
`/lista` permite borrar cada aviso. `/actualizar` recarga la caché de la API;
el botón Refrescar (`stop_refresh`) invalida solo la parada consultada. El
arranque tiene lock de instancia única y reintenta los conflictos `409` de
Telegram con backoff (12 intentos, hasta 30 s).

Optimizaciones y calidad de vida: el catálogo se genera en paralelo desde la
API; la caché en memoria está limitada a 200 paradas con evicción de las más
antiguas (el refresco de 30 s no crece sin control); la confirmación de un
aviso incluye la próxima llegada estimada; y el flujo interactivo distingue un
aviso creado de uno duplicado en el mensaje emergente. `/estado` muestra la
fuente, el tamaño de las cachés, los avisos activos y el uptime.

## Roadmap (Milestones)

| Hito | Sprint | Objetivo | Fecha estimada |
|------|--------|----------|----------------|
| M1: Planning Complete | 0-2 | Scope, stack, wireframes, ADRs firmados | ✅ DONE |
| M2: PWA Shell Ready | 3 | Vite+React+TS+Tailwind, SW, routing, i18n, CI | ~2026-12-06 |
| M3: Real-time Engine | 4 | ⚠️ Revisar fuente: los feeds `.pb` dan 403 (ADR 005); IndexedDB + Web Worker | ~2026-12-20 |
| M4: Push Notifications | 5 | Web Push VAPID, config granular, iOS fallback | ~2026-01-03 |
| M5: Beta Launch | 6 | 20 testers, 2 semanas, métricas Go/No-Go | ~2026-01-17 |
| M6: Production Release | 6+ | Deploy prod, docs handoff, monitoring activo | ~2026-01-24 |

## Bloqueos conocidos

**Sprint 3 (PWA Shell):** `npm run typecheck` falla con 30 errores TypeScript y `npm run lint` con 233 errores en `src/` (bloqueo preexistente, no relacionado con el bot). Las tareas de corrección están creadas en el kanban (board `dbus-app`):
- `t_408a2dd2` — Imports faltantes en páginas
- `t_06703f31` — Componentes UI (props inexistentes, duplicados)
- `t_5e745f77` — Schema IndexedDB (índice `alerts` incompatible)
- `t_9c098959` — Worker `postMessage` transfer list inválido

## Datos en tiempo real

- **Fuente activa del bot:** API JSON que usa la app oficial de Dbus:
  `GET http://app.dbus.eus/SSIIMovilWSv2/ws/cons/tiemposParada?codParada={codigo}&idioma=es`
  (HTTP, sin auth, datos reales con indicador de GPS vs. horario).
- **Obsoleto:** los feeds `.pb` de `opendata.euskadi.eus` redirigen a S3 y
  devuelven `403 AccessDenied`; el fetcher Playwright ya no es necesario para el bot.
- GTFS Estático (PWA): `ftp.geo.euskadi.net/.../google_transit.zip`

## Documentación

- [`AGENTS.md`](AGENTS.md) — reglas del repo, verificación y despliegue. Mandan sobre docs antiguos.
- [`ESTADO.md`](ESTADO.md) — estado de sesión, decisiones y pendientes.
- `03-architecture/adrs/` — decisiones de arquitectura (001-008). ADR 005 describe el diseño GTFS-RT de la PWA; **los feeds `.pb` ya no responden**, no aplicar al bot.
- `04-planning/` — alcance, hitos, métricas y stack planeado; asumir que M3 necesita revisión de fuente.

## Licencia

Privado — Proyecto personal de Iker Goñi.
