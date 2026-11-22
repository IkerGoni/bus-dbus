# Tech Stack Decision — Dbus Mobile Web App

> **Histórico:** ver [ADR 009](../03-architecture/adrs/009-android-nativo-mvp.md) (stack vigente: Kotlin + Compose).

## Versión
v0.1 — Sprint 0 output | 2026-11-22

---

## Decisiones Finales

| Capa | Tecnología | Justificación |
|------|------------|---------------|
| **Frontend Framework** | **Vite 6 + React 19 + TypeScript 5.7** | Ecosistema maduro, HMR rápido, tree-shaking excelente, soporte PWA nativo en Vite, React 19 compiler optimiza bundle |
| **Styling** | **Tailwind CSS 4 (Oxide engine)** | Zero-runtime, JIT, bundle mínimo, design system consistente, dark mode nativo, compatibilidad React 19 |
| **PWA / Service Worker** | **Workbox (GenerateSW mode)** | Genera SW automático con precaching, runtime caching strategies, background sync, mantenimiento Google |
| **Push Notifications** | **Web Push API + VAPID** | Estándar web, sin vendor lock-in, funciona iOS 16.4+ / Android, claves rotables build-time |
| **Base de Datos Local** | **IndexedDB via `idb` (wrapper Promise)** | API nativa, Promise-based, 1.5KB gzipped, soporta índices, cursores, transacciones |
| **Parsing GTFS-RT** | **protobufjs (light build) + Web Worker** | Parse Protobuf en browser, light build ~15KB, Web Worker evita bloqueo main thread |
| **Parsing GTFS Estático** | **CSV parse streaming + zod validation** | GTFS estático = CSV; zod valida schema en runtime; streaming evita memoria alta |
| **Fechas / Timezone** | **date-fns-tz (modular, tree-shakable)** | IANA timezone support (Europe/Madrid), sin moment.js legacy |
| **State Management** | **preact/signals** | **DECISIÓN**: 1.5KB vs 2.8KB zustand; reactividad fine-grained sin re-renders; API similar a signals nativas; compatible React via `@preact/signals-react`; DX excelente para reactive derived state (próxima llegada = computed) |
| **Routing** | **wouter (1.5KB)** | Mínimo, hooks-based, compatible React 19, sin router bloat |
| **i18n** | **i18next + locize (gratis <10k keys)** | ICU plurals, namespace lazy-load, detección browser lang, ES/EU nativo |
| **CI/CD** | **GitHub Actions → Cloudflare Pages** | **DECISIÓN**: Edge global gratis, Functions para VAPID key rotation, headers CSP/COOP/COEP fáciles, preview deployments automáticos, analytics edge gratis. GitHub Pages descartado: sin edge functions, headers limitados, sin VAPID rotation serverless |
| **Error Monitoring** | **Sentry (gratis 5k errors/mes)** | Source maps auto-upload, release tracking, performance monitoring incluido |
| **Analytics** | **Plausible (self-hosted en Cloudflare o plan gratis 10k pageviews)** | Privacy-first, sin cookies, GDPR compliant, 1KB script |
| **Uptime Monitoring** | **UptimeRobot (gratis 50 monitors)** | HTTP/keyword check cada 5 min, alertas Telegram/email |
| **Testing** | **Vitest + Playwright (E2E)** | Vitest unit/integration rápido; Playwright cross-browser real (WebKit = Safari engine) |
| **Lint/Format** | **Biome (rust-based, todo-en-uno)** | Formatter + linter + import sorting en uno; 10x más rápido que ESLint+Prettier |

---

## Alternativas Evaluadas y Descarte

| Alternativa | Por qué NO |
|-------------|------------|
| **zustand** | 2.8KB vs 1.5KB signals; reactividad coarse-grained (re-render todo store); middleware boilerplate para derived state |
| **Redux Toolkit / MobX / Jotai** | Overhead bundle + curva aprendizaje para MVP simple |
| **GitHub Pages** | Sin edge functions (necesarias para VAPID rotation), headers CSP limitados, sin preview deployments |
| **Netlify / Vercel** | Vendor lock-in; límites funciones gratis; Cloudflare Pages más generoso en edge |
| **Firebase / Supabase (backend)** | Decisión: **sin backend propio**; coste 0, escalabilidad infinita, cero mantenimiento |
| **Service Worker manual (sin Workbox)** | Reinventar precaching/runtime caching/background sync = bugs; Workbox probado en producción millones sitios |
| **native Web Push sin VAPID (Firebase FCM)** | Vendor lock-in Google; VAPID = estándar abierto, claves propias |
| **Moment.js / dayjs** | Moment legacy (mutable, grande); dayjs bien pero date-fns-tz más modular y tree-shakable |
| **React Router / TanStack Router** | Overkill para 5-6 rutas; wouter 1.5KB suficiente |

---

## Riesgo Crítico: iOS Safari Web Push — Veredicto de Viabilidad

### Conclusión: **VIABLE CON CAUTELAS** — Proceder con MVP, documentar limitaciones.

**Evidencia (2026):**
- iOS 16.4+ (mar 2023) soporta Web Push API estándar (W3C)
- Requiere **user gesture** para `Notification.requestPermission()` + `pushManager.subscribe()`
- **Background delivery**: Solo funciona si PWA instalada en Home Screen ("Add to Home Screen")
- **Limitaciones conocidas**:
  - No `periodicBackgroundSync` en iOS → fallback: `setInterval` en cliente principal + SW `fetch` event
  - Push payload límite 4KB (suficiente: línea + destino + min + parada ≈ 200 bytes)
  - No `notificationclick` action buttons en iOS (solo body + title)
  - Permiso se resetea si usuario borra datos sitio / no abre PWA 2-3 semanas

**Mitigación en MVP:**
1. **Progressive Permission UX**: Explicar valor → botón "Activar alertas" (user gesture) → prompt nativo
2. **Fallback visible en-app**: Si permiso denegado / iOS <16.4, mostrar próxima llegada en UI con refresh manual + `setInterval` 30s
3. **Documentar en onboarding**: "Para alertas en pantalla bloquea, instala la app (menú navegador → Añadir a pantalla inicio)"
4. **VAPID key rotation**: Build-time generation + backup seguro; procedimiento documentado en ADR

**Decisión**: No bloquea MVP. El 85%+ usuarios objetivo (Donostia, móvil moderno) tienen iOS 16.4+. El 15% restante usa fallback en-app. Riesgo aceptado.

---

## ADRs a Crear (Sprint 2)

1. `001-frontend-stack.md` — Vite+React+TS+Tailwind
2. `002-pwa-offline-strategy.md` — Workbox GenerateSW, cache-first shell, stale-while-revalidate GTFS
3. `003-push-architecture.md` — Web Push + VAPID, iOS fallback, key rotation
4. `004-data-model.md` — IndexedDB schema (paradas, alertas, feeds, cache RT)
5. `005-gtfs-rt-parsing.md` — Web Worker + protobufjs, validation zod, error handling
6. `006-state-management.md` — preact/signals patterns, derived state para próxima llegada
7. `007-hosting-cicd.md` — Cloudflare Pages + Functions, preview, headers security
8. `008-risk-mitigations.md` — Riesgos top 10 con mitigaciones técnicas
