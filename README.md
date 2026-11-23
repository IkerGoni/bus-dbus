<div align="center">

# Bus Donostia — Badator! (Android) + Telegram Bot

### Know when your bus is coming. Get an alert instead of checking again.

**Live GPS arrivals · Offline stop discovery · One-shot arrival alerts**  
**Native Android application (`Badator!`) + Telegram bot** for Donostia / San Sebastián.

[![Node CI](https://github.com/IkerGoni/bus-dbus/actions/workflows/ci.yml/badge.svg)](https://github.com/IkerGoni/bus-dbus/actions/workflows/ci.yml)
[![Android CI](https://github.com/IkerGoni/bus-dbus/actions/workflows/android.yml/badge.svg)](https://github.com/IkerGoni/bus-dbus/actions/workflows/android.yml)
![Kotlin](https://img.shields.io/badge/Kotlin-Android-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=android&logoColor=white)
![TypeScript](https://img.shields.io/badge/TypeScript-Bot%20%26%20Contracts-3178C6?logo=typescript&logoColor=white)
![Node.js](https://img.shields.io/badge/Node.js-22+-339933?logo=nodedotjs&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-Bot%20Persistence-4169E1?logo=postgresql&logoColor=white)
![License: MIT](https://img.shields.io/badge/License-MIT-147e79.svg)

<sub>Independent open-source project by <strong>Iker Goñi Cuesta (Dev Igoni)</strong>. Not affiliated with Dbus.</sub>

</div>

---

## The problem

I kept reopening and refreshing a transit app just to answer one question: **when should I leave for the bus?**

I wanted something simpler: check live arrival times fast, keep my habitual stops one tap away, and **get a notification when it is time to head out**. That everyday friction became a real-world engineering project: build a reliable, maintainable multi-client product quickly against live transit data using a **parallel, AI-assisted engineering workflow**.

The result is **Bus Donostia**: an operational Telegram bot (`@dbus/bot`) and a native Kotlin/Jetpack Compose Android application (**Badator!**) packaged and verified for Google Play release.

---

## What is inside

| | Telegram bot (`apps/bot`) | Native Android app — **Badator!** (`apps/android`) |
|---|---|---|
| **Purpose** | Check live arrivals and schedule alerts directly from Telegram | Fast everyday access with local adaptive notifications |
| **Features** | Live arrivals, search, lines, favorites, nearby stops (OSM), alert scheduler | Home, nearby/search stops, lines, favorites, one-shot alerts, ES / EU / EN localization |
| **Stack** | Node 22+, TypeScript, Telegraf, PostgreSQL (`pg`) + in-memory test driver, Zod, Vitest, Biome | Kotlin, Jetpack Compose, Material 3, Room, OkHttp, kotlinx.serialization, `minSdk 26`, `targetSdk 36` |
| **Verification** | 93 automated tests + Biome + `tsc -b` (plus 50 shared tests in `packages/`) | 152 JVM/Robolectric tests per variant + Android Lint + signed release AAB |
| **Status** | Production-ready (real Dbus API, single-instance lock, 409 retry) | Feature-complete MVP; signed release AAB and Play Store checklist at 29/29 |

Both clients consume live data from the Dbus JSON API. The bundled stop catalogue (`548` stops, `39` lines, `127` itineraries) powers instant offline discovery; **it never invents arrival times**.

### Badator! (Android) screenshots

| Nearby stops (offline OSM) | Live stop arrivals | One-shot alert setup | Active alerts |
|:---:|:---:|:---:|:---:|
| ![Nearby stops](docs/readme/nearby-stops.png) | ![Stop detail](docs/readme/stop-detail.png) | ![New alert](docs/readme/new-alert.png) | ![My alerts](docs/readme/my-alerts.png) |

---

## Built fast. Improved deliberately.

The Git history is part of the engineering story. The repository went from its first commit to the merge of Android Sprint 4 — including the on-device alert engine — in **22 hours and 6 minutes of elapsed wall-clock time**, followed by targeted architecture audits, localization, token governance and release hardening.

| Elapsed from first commit | Recorded milestone |
|---|---|
| **0h** | First repository commit · **Nov 21, 2026, 18:32 UTC** |
| **17h 24m** | First Android Jetpack Compose foundation |
| **19h 14m** | Stops, stop detail and lines screens |
| **19h 58m** | Home, alerts, settings and onboarding |
| **20h 59m** | Android foreground service alert and notification engine |
| **22h 06m** | Sprint 4 integrated into `main` |
| **Afterwards** | Architecture audits, adaptive polling, ES/EU/EN parity, compiled `DesignTokens`, PostgreSQL migration, Play Store release pipeline |

> **What this measures:** wall-clock time between recorded Git commits — not net human working hours or a shortcut around testing. The speed comes from **parallel multi-agent orchestration under strict engineering guardrails**.

```mermaid
flowchart LR
    A["First commit<br/>21 Nov · 18:32 UTC"] --> B["Android foundation<br/>22 Nov · 11:56 UTC"]
    B --> C["Core screens<br/>22 Nov · 14:31 UTC"]
    C --> D["Alert engine<br/>22 Nov · 15:31 UTC"]
    D --> E["Sprint 4 merged<br/>22 Nov · 16:39 UTC"]
    E --> F["Audits, ES/EU/EN,<br/>Postgres & Play release"]
    classDef milestone fill:#e6f6f4,stroke:#147e79,color:#143b41;
    class A,B,C,D,E,F milestone;
```

---

## Parallel multi-agent orchestration, with a developer in control

High output only matters if the codebase stays coherent, tested and releasable. I built this project by **running multiple AI agents in parallel** while keeping **architecture, scope, integration and verification strictly under human control**:

- **Parallel workstreams without collisions:** while one agent implements a feature or refactor inside an isolated **Git worktree** and its assigned **lane** (`apps/android/**` or `apps/bot/**`), I work in parallel with another agent on architecture decisions (ADRs), diagrams, UX refinements, contract parity or security/release audits.
- **Explicit engineering guardrails:** every agent operates under [`AGENTS.md`](AGENTS.md) (non-negotiable product rules, lane ownership, zero-hardcoded-tokens policy, zero opaque internal IDs in UI) and [`docs/contributing/`](docs/contributing/README.md) (Conventional Commits, branch/worktree isolation and mandatory human approval before destructive or remote Git operations).
- **Automated truth over trust:** cross-language domain rules are locked by shared real-API fixtures in `packages/contracts/` and parity tests across TypeScript and Kotlin, backed by **295 automated tests**, Biome, Android Lint, GitHub Actions CI and automated Play Store verification scripts (`tools/play-store/`).

```mermaid
flowchart LR
    DEV["Developer<br/>Product scope, ADRs & priorities"]

    subgraph PARALLEL["Parallel AI-assisted workstreams"]
        direction TB
        W1["Agent Lane A · Isolated Git worktree<br/>Feature implementation & unit tests"]
        W2["Agent Lane B · Architecture & Design<br/>ADRs, diagrams, UX specs & token guards"]
        W3["Agent Lane C · Audit & Release<br/>Security scans, privacy & Play Store checks"]
    end

    DEV --> W1
    DEV --> W2
    DEV --> W3

    W1 --> REV["Human review + Automated gates<br/>AGENTS.md · Lint · 295 tests · TS/Kotlin parity"]
    W2 --> REV
    W3 --> REV

    REV --> GATE{"Passes all gates?"}
    GATE -- "No" --> DEV
    GATE -- "Yes" --> INT["Clean commit, docs update & release decision"]
```

---

## Architecture at a glance

One monorepo, two native client experiences, **zero custom backend** for Android.

```mermaid
flowchart TB
    EXT["Dbus live arrivals API (JSON)"]

    subgraph TS["Telegram lane — Node.js / TypeScript"]
        BOT["Telegram bot<br/>Telegraf + alert scheduler"]
        CORE["@dbus/core<br/>shared TS domain"]
        PG["PostgreSQL (pg)<br/>+ in-memory test driver"]
        BOT --> CORE
        BOT --> PG
    end

    subgraph KT["Android lane — Kotlin / Compose"]
        APP["Badator! UI<br/>Jetpack Compose + Material 3"]
        DOMAIN["Kotlin domain &<br/>adaptive FGS alert engine"]
        ROOM["Room + DataStore<br/>local-only persistence"]
        APP --> DOMAIN
        DOMAIN --> ROOM
    end

    FIX["@dbus/contracts<br/>Real API fixtures, catalogue & design tokens"]
    EXT --> BOT
    EXT --> DOMAIN
    FIX -.-> CORE
    FIX -.-> DOMAIN
```

The TypeScript bot consumes `@dbus/core` and `@dbus/contracts`. Android maintains a pure, KMP-ready Kotlin domain layer (`domain/` has zero Android or `java.time.*` dependencies) verified against the same real-API contract fixtures.

### Repository map

```text
apps/
  android/             Badator! — native Kotlin + Jetpack Compose app (Gradle)
  bot/                 @dbus/bot — Telegram bot (Node 22+, TypeScript, PostgreSQL)
  mobile/              @dbus/mobile — earlier PWA, frozen by ADR 009
packages/
  core/                @dbus/core — shared TypeScript domain logic
  contracts/           @dbus/contracts — real API fixtures, schemas & design tokens
tools/
  play-store/          Automated privacy, Data Safety, EU review & release checklist scripts
docs/
  03-architecture/     ADRs (including ADR 009: native Android pivot)
  contributing/        Commit conventions, worktree workflow & Git security rules
  privacy/             Published ES / EU / EN privacy policies (GitHub Pages)
  readme/              Screenshots used in this README
.github/workflows/     ci.yml (Node workspaces) and android.yml (Gradle)
AGENTS.md              Engineering guardrails and lane ownership for agents
```

---

## Key engineering decisions

- **One-shot alerts over complex schedules:** the core job is removing repeated manual checks. Pick stop, line and lead time → adaptive Foreground Service monitors (`30s` / `60s` / `120s`) → notifies once → clears automatically (7-day expiration, GPS preference over schedule).
- **Real API behavior over idealized specs:** public GTFS-RT `.pb` feeds returned `403 AccessDenied`, so both clients integrate defensively with Dbus's live JSON endpoint, handling stale data, source type (`tipo=1` GPS vs scheduled), and public vs internal line IDs (e.g., public line `28` vs internal ID `32`).
- **Native Android over PWA ([ADR 009](docs/03-architecture/)):** a browser prototype was built first and deliberately frozen in favor of Kotlin + Compose for reliable local persistence, background alert lifecycle and Android-native UX — without operating a paid backend or Firebase.
- **Privacy by design:** zero analytics, zero ads, zero trackers, `android:allowBackup="false"`, and ephemeral in-memory location used only on demand to sort nearby stops offline.
- **Design & config discipline:** 3-tier configuration model (`assets/config.json` for runtime operational parameters, compile-time generated `DesignTokens`/`MotionTokens` guarded by `DesignTokensGuardTest`, and strict reduced-motion accessibility).

---

## Verification and getting started

### Requirements

- **Node.js 22+** and **pnpm** (bot and shared packages)
- **JDK 17** and **Android SDK** (Android app; see [`apps/android/README.md`](apps/android/README.md))
- A **separate Telegram `BOT_TOKEN`** via environment variable if running your own bot instance (never commit tokens or reuse production credentials)

### Verify TypeScript workspaces (lint + typecheck + 143 tests + build)

```bash
pnpm install --frozen-lockfile
pnpm verify
pnpm run build:ci
```

### Verify and build Android (lint + 152 JVM tests per variant + APK)

```bash
cd apps/android
./gradlew check assembleDebug
```

### Run the Telegram bot locally

```bash
cd apps/bot
export BOT_TOKEN='your_dev_bot_token'
pnpm run build && npm run start
```

> **Operational note:** never launch a second polling instance against the same `BOT_TOKEN` (triggers Telegram `409` conflicts). `git push` does not deploy or restart the production bot.

---

## Status, limitations and external dependencies

- **External data source:** both products rely on Dbus's public, unofficial JSON endpoint (`app.dbus.eus`, currently HTTP-only for that host via scoped `network_security_config.xml`). Availability and schema are controlled by the transit operator.
- **Android notifications:** alert timing on Android is subject to OS battery optimization, notification permissions and network connectivity.
- **Frozen PWA (`apps/mobile`):** kept in the repository for architectural traceability (ADR 009); excluded from CI and not maintained.

---

## Privacy, Google Play and Licensing

- **Developer / Support:** **Iker Goñi Cuesta (Dev Igoni)** · `dev.mail.igoni@gmail.com`
- **Privacy policies (Badator!):**
  - [Español](https://ikergoni.github.io/bus-dbus/privacy/privacy-policy-es.html)
  - [Euskara](https://ikergoni.github.io/bus-dbus/privacy/privacy-policy-eu.html)
  - [English](https://ikergoni.github.io/bus-dbus/privacy/privacy-policy-en.html)
- **License:** original source code in this repository is licensed under the [MIT License](LICENSE). Bundled typography in `apps/android/licenses/` remains under SIL Open Font License 1.1. Stop coordinates use OpenStreetMap data (`© OpenStreetMap contributors, ODbL`). Dbus trademarks and live transit data belong to their respective owners.
- **Further documentation:** [`AGENTS.md`](AGENTS.md) · [`docs/README.md`](docs/README.md) · [`docs/contributing/`](docs/contributing/README.md)
