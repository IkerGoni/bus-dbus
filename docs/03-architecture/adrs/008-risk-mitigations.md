# ADR 008: Risk Register & Mitigations

> **Histórico:** ver [`01-endpoints-y-riesgo.md`](../../06-api-externa/01-endpoints-y-riesgo.md) (riesgos de la API) y [ADR 009](009-android-nativo-mvp.md).

## Status
Accepted

## Context
Top 10 risks identified in research and planning phases. Each must have concrete technical mitigation, not just process.

## Decision
See mitigation column per risk.

## Consequences

### Positive
- All mitigations are code/config-level, not process-only
- Monitoring (Sentry, Plausible, UptimeRobot) catches regressions automatically
- Runbook documents operational procedures for bus factor
- Parking lot v2 prevents scope creep by design

### Negative / Risks
See risk table below.

### Mitigations
| ID | Risk | Probability | Impact | Technical Mitigation |
|----|------|-------------|--------|---------------------|
| **R1** | **iOS Safari Web Push**: Requires user gesture, PWA installed, 16.4+, background limited | High | Critical | 1. Progressive permission UX (value prop -> gesture button -> native prompt)
2. Install prompt detection (`beforeinstallprompt` + `display-mode: standalone`)
3. Fallback: in-app polling (30s) + visible countdown + toast/banner
4. Document limitation in onboarding: "Instala para alertas en pantalla bloqueo"
5. Handle `pushsubscriptionchange` for re-subscription |
| **R2** | **GTFS-RT Protobuf parsing performance** (3 feeds/30s on mobile) | Medium | High | 1. Web Worker (off main thread) - ADR 005
2. protobufjs light build (~15KB)
3. Transferable ArrayBuffer (zero-copy)
4. Reuse `protobuf.Root` instance
5. Zod validation only on parsed output, not raw bytes
6. Performance.mark in worker; alert if p95 >100ms |
| **R3** | **Service Worker lifecycle on iOS** (terminated aggressively, no periodicBackgroundSync) | High | High | 1. Client-side `setInterval(30000)` -> `registration.active?.postMessage({type: 'FETCH_RT'})` - ADR 002
2. SW `message` handler fetches RT -> updates IndexedDB
3. `navigator.serviceWorker.ready` polling on app focus
4. `controllerchange` listener for seamless SW updates
5. Stale-while-revalidate UI with freshness badge |
| **R4** | **Web Push / FCM quota limits** (10k/day free, burst limits) | Low | Medium | 1. Coalesce alerts: batch multiple arrivals in single push (max 1 per minute per user)
2. Exponential backoff on 429/5xx from push service
3. Monitor quota via Cloudflare Functions logs
4. Alert if daily sends >8k |
| **R5** | **GTFS-RT feed changes without notice** (schema drift, field removal) | Medium | Medium | 1. Zod strict schema validation on parse - ADR 005
2. Versioned cache key (`rt_cache:feedVersion`)
3. Alert if parse failure rate >5% (Sentry)
4. Fallback to GTFS static (scheduled) automatically
5. Feed health check endpoint in CI (weekly) |
| **R6** | **VAPID key rotation / compromise** | Low | Critical | 1. Build-time generation (`npm run vapid:rotate`) - ADR 003
2. 30-day key overlap in Cloudflare Function (accept old + new)
3. Client handles `pushsubscriptionchange` -> auto re-subscribe
4. Private key in Pages secret (not repo/env)
5. Documented rotation procedure in RUNBOOK.md |
| **R7** | **dbus data incomplete/incorrect** (ghost stops, trips without vehicle, occupancy missing) | Medium | Medium | 1. Cross-validation: GTFS static + RT merge
2. Confidence scoring: RT=high, scheduled=medium, stale=low
3. UI shows confidence badge (GREEN EN VIVO / YELLOW HORARIO / RED ANTIGUO)
4. Filter out trips without vehicle position after 5 min
5. User feedback: "Reportar error" -> logs to Sentry |
| **R8** | **Scope creep** (map, favorites, multi-city, auth, payments) | High | Medium | 1. MVP scope document = contract
2. Parking lot v2 explicitly listed in mvp-scope.md
3. Any new feature -> create GitHub Issue with "v2" label
4. Sprint planning: only MVP scope cards in "ready" |
| **R9** | **App Store rejection** (PWA not native, web push) | Low | Medium | 1. **No App Store publication for v1** - PWA installable via web only
2. Document "Add to Home Screen" flow in onboarding
3. If native needed later: Capacitor wrapper (separate Sprint) |
| **R10** | **Single point of failure: one maintainer** | Medium | High | 1. Comprehensive ADRs = bus factor >=2 for ops
2. RUNBOOK.md: deploy, rotate keys, debug push, rollback
3. GitHub Actions = automated deploy (no manual steps)
4. Sentry + Plausible + UptimeRobot = monitoring without maintainer
5. Second maintainer onboarding checklist in CONTRIBUTING.md |

## Implementation Notes
### RUNBOOK.md (key procedures)
```markdown
# Dbus PWA - Runbook

## Deploy
- Push to main -> GitHub Actions -> Cloudflare Pages (auto)
- Rollback: Pages dashboard -> "Rollback to previous deployment"

## VAPID Key Rotation (monthly or on compromise)
1. `npm run vapid:rotate` -> updates .env.production
2. Cloudflare Pages dashboard -> Functions secrets -> add NEW private key (keep OLD)
3. Deploy (auto on push)
4. After 30 days: remove OLD key from secrets

## Debug Push Delivery
1. Sentry -> search "push" tag -> check delivery ack failures
2. Cloudflare Functions logs -> filter /api/push-send
3. Client: check `pushsubscriptionchange` events in console

## GTFS-RT Feed Down
1. UptimeRobot alerts (5-min checks on feed URLs)
2. App automatically falls back to scheduled (badge shows HORARIO)
3. Check feed health: `curl -I https://opendata.euskadi.eus/.../mdb-3066.pb`

## Emergency Cache Clear
- User: Settings -> "Limpiar cache" -> reloads
- Dev: `caches.keys().then(keys => keys.forEach(k => caches.delete(k)))`
```

### Monitoring Stack
- **Sentry**: Errors + performance (free 5k errors/mo)
- **Plausible**: Privacy analytics (self-hosted on Cloudflare or paid)
- **UptimeRobot**: 5-min checks on feed URLs + PWA URL (free 50 monitors)
- **Cloudflare Web Analytics**: Free, no cookies, edge

## Related
- ADR 001: Frontend stack (Sentry/Plausible integration)
- ADR 002: PWA offline strategy (SW lifecycle, iOS fallback)
- ADR 003: Push architecture (VAPID rotation, delivery ack)
- ADR 004: Data model (confidence scoring, data quality)
- ADR 005: GTFS-RT parsing (schema validation, fallback)
- ADR 006: State management (countdown derived state)
- ADR 007: Hosting/CI/CD (deployment, headers, secrets)