# Repo split: 2 products + shared core — parallel lanes

Status: approved plan, not implemented.
Decisions: Capacitor/TWA path to Android · mobile kept (not deleted) · pnpm workspaces ·
**bot lane and mobile lane work in parallel after a short shared foundation**.

---

## 0. Parallelism model in one picture

```
        F1 workspace+pnpm+moves        F2 core + contracts + CI
        ────────────────────▶          ───────────────────────▶  gate: PARALLEL-READY
                                                                        │
                                    ┌───────────────────────────────────┴──────────┐
                                    ▼                                              ▼
                        LANE B (bot, ships)                          LANE M (mobile, WIP)
                        B1 handlers split                            M1 dead code + paths
                        B2 renderer merge                            M2 data layer -> core
                        B3 db CRUD unify                             M3 pages -> live arrivals
                        B4 offline tests                             M4 alerts on IndexedDB
                        B5 deploy + smoke                            M5 PWA build green
                                                                     M6 Capacitor + notif.
                                    └──────────────┬───────────────────────────────┘
                                                   ▼
                                        RELEASE: bot prod · mobile beta
```

Two serial phases only (~1–1.5 sessions). After that the lanes touch disjoint paths and
never block each other. Lane M can actually start **M1 during F2** (pure path/dead-code work).

---

## 1. Target layout

```
bus-dbus/
├── apps/
│   ├── bot/                     # PRODUCT 1 — Telegram. Own deps, SQLite, deploy.
│   │   ├── src/{index,handlers,render,session,alerts,db,config}.ts
│   │   ├── tests/
│   │   ├── package.json         # @dbus/bot
│   │   └── AGENTS.md
│   └── mobile/                  # PRODUCT 2 — PWA -> Capacitor -> Android.
│       ├── src/{pages,components,store,i18n,lib}/
│       ├── public/  index.html  vite.config.ts  wrangler.toml
│       ├── tests/               # NEW in M2
│       ├── package.json         # @dbus/mobile
│       ├── KNOWN_ISSUES.md
│       └── AGENTS.md
├── packages/
│   ├── core/                    # SHARED, isomorphic (node + WebView)
│   │   ├── src/{client,lines,arrival,match,types}.ts
│   │   ├── tests/               # unit + fixtures; live behind LIVE=1
│   │   ├── API.md               # frozen public surface
│   │   └── package.json         # @dbus/core — deps: zod only
│   └── contracts/               # SHARED spec (survives Kotlin rewrite)
│       ├── line-aliases.json
│       ├── alert-semantics.md
│       └── fixtures/*.json
├── docs/
│   ├── products/{bot,mobile}.md
│   ├── shared/{core,contracts}.md
│   ├── plan/repo-split.md
│   └── adr/                     # 005 deleted; 002/003 Superseded
├── infra/{bot.service,deploy-bot.sh,deploy-mobile.sh}
├── .github/workflows/{bot.yml,mobile.yml}
├── .github/CODEOWNERS
├── pnpm-workspace.yaml  .npmrc  package.json  tsconfig.base.json  biome.json
└── ESTADO.md  README.md
```

---

## 2. Ownership matrix (the anti-collision contract)

| Path | Lane B | Lane M | Notes |
|---|---|---|---|
| `apps/bot/**` | **own** | never | |
| `apps/mobile/**` | never | **own** | |
| `packages/core/**` | owns by default | may add | additions only; see §4 |
| `packages/contracts/**` | owns by default | may add fixtures | append-only |
| `docs/products/bot.md` | own | read | |
| `docs/products/mobile.md` | read | own | |
| `docs/shared/**`, `infra/`, `.github/`, root config | shared | shared | small dedicated PRs only |
| `pnpm-lock.yaml` | shared | shared | dependency changes in dedicated `chore(deps)` PRs |

`CODEOWNERS` mirrors this so GitHub blocks the wrong lane automatically. Each app's
`AGENTS.md` states the rule in one line: *"Do not edit outside this directory except
`packages/core` additions."*

---

## 3. Shared vs product-specific (binding)

| Concern | Owner |
|---|---|
| `tiemposParada` fetch, timeout, retry, TTL cache, inflight dedupe | core |
| `normalizeLine` / `matchesLine` / alias 28↔32 | core + contracts |
| arrival normalization (`tipo===1` realtime, `codParada` = public code) | core |
| `findMatchingArrival`, GPS preference, destination tolerance, minutes window | core |
| dedup key `(ownerId, stopId, lineId)`, 7-day expiry, `AlertSpec` | core + contracts |
| Telegram Markdown, keyboards, commands, FSM | apps/bot |
| React pages, Tailwind, i18n, signals, IndexedDB | apps/mobile |
| SQLite schema (`user_alerts`, `user_favorites`) | apps/bot |
| Web Push / Capacitor notifications | apps/mobile (decision in M6) |

Schema split (kills the duplicate `UserAlert`):
core `AlertSpec { stopId, lineId, destination, minutesAhead, active, createdAt }`;
bot `+ chatId`; mobile `+ days[], recurrence, silenceFrom/To`.

---

## 4. Interface freeze + transport injection (why lanes can't block each other)

**`packages/core/API.md` is frozen at the end of F2.** Public surface:

```ts
// client.ts
createDbusClient(opts: {
  baseUrl: string
  language: 'eu'|'es'|'en'|'fr'
  cacheTtlMs?: number
  timeoutMs?: number
  maxCachedStops?: number
  fetch?: typeof fetch          // ← injection point
}): {
  fetchStopTimes(code: string, force?: boolean): Promise<DbusStopTimes|null>
  fetchPopularStops(): Promise<DbusStop[]>
  getStopName(code: string): string
  invalidateStop(code: string): void
  clearCache(): void
  getCacheStats(): { stops: number; popularStops: number }
}
// lines.ts / arrival.ts / match.ts: pure functions over plain data
```

Two consequences that make parallelism real:

1. **`fetch` injection** — mobile and all tests run against `packages/contracts/fixtures`
   with zero network. No lane waits on the live Dbus API or on the other lane.
2. **Additive-only rule** — a lane needing a new core function adds it *without* changing
   existing signatures, updates `API.md` + a fixture test in the same PR. Breaking changes
   need both lanes' agreement and a note in `ESTADO.md`. This is the only shared file
   either lane edits routinely, and it stays cheap.

Config stays in `apps/bot/src/config.ts` (reads `process.env`); core never touches
`process`, `fs`, `path`, DOM globals, or module-level singletons.

Enforced by lint: biome `noRestrictedImports` on `packages/**` bans `telegraf`, `react`,
`react-dom`, `idb`, `better-sqlite3`, `node:*`, `@dbus/bot`, `@dbus/mobile`.

---

## 5. pnpm

Root `package.json`:
```json
{
  "name": "bus-dbus",
  "private": true,
  "packageManager": "pnpm@<pin>",
  "scripts": {
    "verify": "pnpm -r verify",
    "build": "pnpm -r build",
    "typecheck": "pnpm -r typecheck",
    "lint": "biome check .",
    "test": "pnpm -r test",
    "bot:dev": "pnpm --filter @dbus/bot dev",
    "bot:start": "pnpm --filter @dbus/bot start",
    "mobile:dev": "pnpm --filter @dbus/mobile dev"
  }
}
```

Per-app `verify` = `typecheck && biome check . && test` → one command per lane.

`pnpm-workspace.yaml`:
```yaml
packages: [apps/*, packages/*]
onlyBuiltDependencies: [better-sqlite3, esbuild, '@biomejs/biome']
```

`.npmrc`: `node-linker=hoisted`, `strict-peer-dependencies=false`.

Migration: `corepack enable && corepack use pnpm@latest-10`; delete all three
`package-lock.json`; commit only `pnpm-lock.yaml`; never mix npm/pnpm in the tree.

Risks: pnpm 10 blocks postinstall by default → `better-sqlite3` needs
`onlyBuiltDependencies` (verify with bot tests). Concurrent `pnpm add` from two lanes
conflicts on the single lockfile → dependency additions only in dedicated `chore(deps)` PRs.

---

## 6. Phases

### F1 — Workspace skeleton (serial, bot stays green)
1. `git mv bot apps/bot`; `git mv src apps/mobile/src`; move `index.html vite.config.ts
   public/ functions/ wrangler.toml lighthouserc.json scripts/ tsconfig.node.json` →
   `apps/mobile/`; `git mv 02-wireframes 03-architecture 04-planning docs/` (flatten ADRs
   to `docs/adr/`).
2. Root workspace files (`package.json`, `pnpm-workspace.yaml`, `.npmrc`,
   `tsconfig.base.json`), per-app `package.json` (`@dbus/bot`, `@dbus/mobile`), per-app
   `verify` scripts.
3. **DB path fix:** `apps/bot/src/db.ts` → `path.resolve(process.cwd(), config.dbPath)`;
   move root `bot.db` → `apps/bot/bot.db`; `.gitignore` += `*.db`.
4. Audit cleanup: `git rm bot-code.zip state/kanban/*.lock state/kanban/dbus-app/`
   (keep one board); `.gitignore` += `*.zip`, `*.db`, `state/kanban/*.lock`.

Verify: `pnpm install` · `pnpm --filter @dbus/bot verify` = 31 green + typecheck + lint ·
`pnpm --filter @dbus/bot build`.

### F2 — Core + contracts + CI split (serial, ~1 session)
1. Extract `packages/core`: `dbus-client.ts` → `client.ts` (factory + `fetch` injection),
   `arrival.ts` domain half → `{lines,arrival,match}.ts`, domain types → `types.ts`.
2. Bot keeps `escapeMd`/`formatArrival`/`formatArrivalsList` → `apps/bot/src/render.ts`;
   imports `@dbus/core`; `config.ts` builds the client.
3. Tests: pure domain tests → `packages/core/tests/`; session/lock/db stay in bot; live
   network tests → `core/tests/live.test.ts` behind `LIVE=1`.
4. `packages/contracts`: `line-aliases.json`, `fixtures/*.json` (real public responses +
   expected `NextArrivalResult[]`), `alert-semantics.md`. Core tests consume fixtures via
   injected `fetch`.
5. TS project references (`tsc -b`); `packages/core/API.md` written and frozen.
6. CI split: `.github/workflows/bot.yml` (paths `apps/bot/**`, `packages/**` → install,
   lint, `tsc -b`, `pnpm --filter @dbus/bot test`) and `mobile.yml`
   (`workflow_dispatch` + `apps/mobile/**` paths, **not required** until M5).
7. `CODEOWNERS` per §2; `docs/products/*.md`, `docs/shared/*.md` skeletons;
   `AGENTS.md` per app; commit this plan as `docs/plan/repo-split.md`.

**Gate — PARALLEL-READY checklist**
- [ ] `pnpm --filter @dbus/bot verify` green from a clean clone
- [ ] `pnpm --filter @dbus/core test` green **offline**
- [ ] `packages/core/API.md` frozen; fixtures committed
- [ ] bot workflow required on `main`; mobile workflow not required
- [ ] CODEOWNERS in place; both `AGENTS.md` state the boundary
- [ ] `apps/bot` and `apps/mobile` share zero files outside `packages/**`

### Lane B — bot (ships)
| Step | Work | Verify |
|---|---|---|
| B1 | Split `handlers.ts` (990) → `session.ts` (FSM) + `render.ts` (pure) + thin handlers | 31 tests green, no behavior change |
| B2 | Merge the 4 command/callback renderer pairs (`populares`, `fav`, `borrar/adel`, `lista`) into one renderer per feature returning `{text, keyboard?}` | tests + one live `/start` smoke |
| B3 | Unify alert/favorite CRUD in `db.ts` (~120 duplicated LOC) | tests green; DB migration guarded |
| B4 | Offline-ify tests: default suite no network (`fetch` injected), live behind `LIVE=1` | `pnpm --filter @dbus/bot verify` passes with network off |
| B5 | `infra/bot.service` (WorkingDirectory=`apps/bot`, `EnvironmentFile`), deploy + real `/start` round-trip | prod process restarts clean, no 409 |

### Lane M — mobile (parallel; M1 can start during F2)
| Step | Work | Verify |
|---|---|---|
| M1 | Delete provably dead files (`lib/gtfs-rt.ts`, `workers/`, `ui/{Modal,BottomSheet,Spinner}`, `hooks/usePushPermission`, `lib/utils.ts`); fix 14 wrong relative paths | `tsc --noEmit` error count 30 → ~16 |
| M2 | Replace data layer with `@dbus/core`: delete GTFS types + 6 IndexedDB stores, `store` reads arrivals from `createDbusClient({fetch})`; add `apps/mobile/tests/` on fixtures | typecheck 0 errors; mobile tests green offline |
| M3 | Wire pages to live arrivals (`Home`, `StopSelector`, `StopDetail`), remove `ParsedRT` plumbing | manual dev-server smoke, 3 stops |
| M4 | Alerts on IndexedDB using core `AlertSpec` + shared dedup/expiry rules; `days/recurrence/silence` stay mobile-only | parity test vs core fixtures |
| M5 | PWA build green (`vite build`), `biome check` clean, mobile workflow becomes **required**, Lighthouse on a real served dist | build artifact + LH score |
| M6 | Capacitor wrap: `npx cap add android`, local notifications, decide Web Push vs Capacitor push (rewrite `functions/` or drop) | APK runs on device, one notification |

Shared lane S (either lane, small PRs): core additions + fixture updates + `API.md` note.
Only rule: additive, tests in the same PR.

### Release
Bot to prod when B5 done (independent of M). Mobile beta when M5 (PWA) / M6 (APK) done.

---

## 7. CI / deploy independence

| Workflow | Trigger paths | Required | Deploy |
|---|---|---|---|
| `bot.yml` | `apps/bot/**`, `packages/**` | yes | `infra/deploy-bot.sh` on `main` |
| `mobile.yml` | `apps/mobile/**`, `packages/**` | after M5 | `infra/deploy-mobile.sh` on `main` |

- A `packages/**` change runs **both** workflows (that's the guard against silent breakage).
- A bot-only PR never builds the PWA; a mobile-only PR never installs `better-sqlite3`.
- Skipped jobs (path filters) must not be listed as required checks — use two workflows,
  not one with `if:` jobs, so branch protection stays predictable.
- Delete from active CI: lighthouse, Sentry sourcemaps, Cloudflare preview/prod until M5.
  (Current 7-job pipeline is red and references secrets/hosts that don't exist.)

---

## 8. Git / two-agent protocol

- Trunk-based: `main` always green for bot. Short-lived branches:
  `feat/bot-<topic>`, `feat/mobile-<topic>`, `chore/deps-<topic>`.
- Parallel sessions use separate worktrees, not the same checkout:
  `opencode.worktree_create({name:"bot-split"})` / `{name:"mobile-core"}`.
- Merge order is irrelevant by construction: disjoint paths. Rebase before merge.
- Shared-file edits (`root package.json`, `biome.json`, `.github/**`, `infra/**`,
  `pnpm-lock.yaml`) go in their own PR, never bundled with lane work.
- If a core signature must change: PR titled `feat(core)!:`, both lanes' checks run,
  `API.md` + fixtures updated, note in `ESTADO.md`.

---

## 9. Risks

| Risk | Mitigation |
|---|---|
| pnpm 10 blocks `better-sqlite3` postinstall | `onlyBuiltDependencies`; verify via bot tests in F1 |
| Bot DB relocates after `git mv` | F1 step 3 + systemd `WorkingDirectory` |
| Core grows Node/React imports | biome `noRestrictedImports` on `packages/**` |
| Mobile keeps bot CI red | separate workflow, not required until M5 |
| Core API churn blocks a lane | frozen `API.md`, additive-only, fixtures as contract |
| Lockfile conflicts from two lanes | dependency PRs only, `chore(deps)` |
| Behavior drift during core extraction | 31 existing tests must pass unchanged in F2 |
| Two lockfile formats during migration | delete all `package-lock.json` in F1, never mix |

---

## 10. Explicitly out of scope

1. Native Kotlin rewrite (contracts/ is the insurance policy if it happens).
2. Mobile push stack decision (Web Push vs Capacitor/FCM) — M6.
3. Bot feature work beyond consolidation.
4. ADR renumbering after deletions.
