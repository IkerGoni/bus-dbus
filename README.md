# Dbus Donostia — Mobile Web App + Telegram Bot

Aplicación PWA móvil para notificaciones push de llegada de autobús Dbus (San Sebastián) + bot de Telegram con avisos de llegada.

## Estado actual (2026-11-22)

| Área | Estado | Evidencia |
|------|--------|-----------|
| **Planning (Sprints 0-2)** | ✅ Completo | 3 docs en `04-planning/`, 12 wireframes en `02-wireframes/`, 8 ADRs en `03-architecture/adrs/` |
| **PWA Shell (Sprint 3)** | 🔧 Bloqueado | `npm run build` falla por ~26 errores TS en `src/` |
| **Tests del bot** | ✅ 15/15 pasan | `npm run test` |
| **TypeScript del bot** | ✅ 0 errores | `npx tsc --noEmit` en `bot/` |
| **Lint del bot** | ✅ 0 errores | `npx biome check bot/src/` |
| **Kanban** | `t_dbdce90c` (Sprint 3) en `ready` | `state/kanban/kanban.db` |

## Stack tecnológico

| Capa | Tecnología |
|------|------------|
| Frontend | Vite 6 + React 19 + TypeScript 5.7 |
| Styling | Tailwind CSS 4 (Oxide) |
| PWA / SW | Workbox GenerateSW (vite-plugin-pwa) |
| Push | Web Push API + VAPID |
| BD local | IndexedDB vía `idb` |
| Parsing GTFS-RT | protobufjs + Web Worker |
| State | preact/signals |
| Routing | wouter |
| i18n | i18next (ES + EU) |
| Lint/Format | Biome |
| Tests | Vitest + Playwright |
| Hosting | Cloudflare Pages + Functions |
| Bot Telegram | Telegraf + better-sqlite3 + protobufjs |

## Estructura del repositorio

```
.
├── 02-wireframes/          # Wireframes Excalidraw (onboarding, home, stop, alert, settings...)
├── 03-architecture/        # 8 ADRs (001-frontend-stack ... 008-risk-mitigations)
├── 04-planning/            # mvp-scope.md, milestones.md, success-metrics.md, tech-stack-decision.md
├── bot/                    # Telegram bot (Telegraf, SQLite, GTFS-RT)
├── fetcher/                # Servicio fetcher GTFS-RT (pendiente)
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
npm run test          # Vitest (tests del bot: 15/15 pasan)

# Bot de Telegram (carpeta bot/)
cd bot && npm run dev   # tsx watch
cd bot && npm run build # tsc build
cd bot && npm run start # node dist/index.js
```

## Configuración del bot de Telegram

El token del bot se lee de la variable de entorno `BOT_TOKEN`. No está hardcodeado.

```bash
# Copiar el ejemplo
cp bot/.env.example bot/.env
# Editar con tu token real
BOT_TOKEN=tu_token_aqui

# Ejecutar
cd bot && npm run dev
```

Variables de entorno:
- `BOT_TOKEN` — Token del bot de Telegram (requerido)
- `FETCHER_URL` — URL del servicio fetcher GTFS-RT (default: `http://localhost:3002`)
- `POLL_INTERVAL_MS` — Intervalo de polling GTFS-RT (default: `30000`)
- `DEFAULT_ALERT_MINUTES` — Minutos antelación por defecto (default: `5`)
- `DB_PATH` — Ruta de la base de datos SQLite (default: `./bot.db`)

## Roadmap (Milestones)

| Hito | Sprint | Objetivo | Fecha estimada |
|------|--------|----------|----------------|
| M1: Planning Complete | 0-2 | Scope, stack, wireframes, ADRs firmados | ✅ DONE |
| M2: PWA Shell Ready | 3 | Vite+React+TS+Tailwind, SW, routing, i18n, CI | ~2026-12-06 |
| M3: Real-time Engine | 4 | GTFS-RT parser (3 feeds), IndexedDB cache, Web Worker | ~2026-12-20 |
| M4: Push Notifications | 5 | Web Push VAPID, config granular, iOS fallback | ~2026-01-03 |
| M5: Beta Launch | 6 | 20 testers, 2 semanas, métricas Go/No-Go | ~2026-01-17 |
| M6: Production Release | 6+ | Deploy prod, docs handoff, monitoring activo | ~2026-01-24 |

## Bloqueos conocidos

**Sprint 3 (PWA Shell):** `npm run build` falla por errores TypeScript en `src/`. Las tareas de corrección están creadas en el kanban (board `dbus-app`):
- `t_408a2dd2` — Imports faltantes en páginas
- `t_06703f31` — Componentes UI (props inexistentes, duplicados)
- `t_5e745f77` — Schema IndexedDB (índice `alerts` incompatible)
- `t_9c098959` — Worker `postMessage` transfer list inválido

## Datos GTFS-RT

- Feeds: `opendata.euskadi.eus/transport/moveuskadi/dbus/mdb-{3066,3065,3064}.pb` (3 feeds, Protobuf, sin auth, ~30s)
- GTFS Estático: `ftp.geo.euskadi.net/.../google_transit.zip`
- El bot de Telegram usa mock data cuando el fetcher no está disponible

## Licencia

Privado — Proyecto personal de Iker Goñi.
