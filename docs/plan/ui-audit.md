# UI / wireframe audit + improvement spec (mobile)

Scope: `src/` (3,378 LOC) + `05-ui/wireframes/` (12 frames, 12.9k doc lines).
Verdict: **not commercial level.** High-fidelity prototype quality — plausible in a
screenshot, fails on build, data honesty, interaction truth, contrast, i18n and states.

---

## 1. Scores

| Dimension | Score | Note |
|---|---|---|
| Wireframe coverage (flows/states) | 8/10 | above hobby level; includes permission, offline, component library |
| Wireframe feasibility | 4/10 | 5 features the data source cannot support |
| Design tokens | 7/10 | real `@theme` tokens, but no semantic status layer |
| Component API | 6/10 | forwardRef + aria, but no Switch/PageHeader/ListRow; custom props untyped |
| Page implementation | 4/10 | inconsistent headers, fake interactions, hardcoded data |
| States (loading/empty/error/offline) | 4/10 | exists per page but inconsistent and partly fake |
| Accessibility | 4/10 | 4 contrast failures, invalid nesting, 44 vs 48px, hardcoded ES labels |
| i18n | 5/10 | 98 ES/EU keys, genuine Basque, but 7 missing + dozens of hardcoded strings |
| Motion | 2/10 | 5 animation classes referenced, **0 keyframes defined** |
| Dark mode | 3/10 | token media query + `.dark` class that nothing reads |
| Commercial readiness | **3/10** | fake cache stats, fake Mugi balance, dead buttons |

---

## 2. What is genuinely good (keep)

- `@theme` token block with real CSS variables, safe-area utilities, `prefers-color-scheme`.
- 48px touch targets on Button/Chip/Input; `touch-manipulation`.
- `Button`/`Input` use `forwardRef`, `aria-invalid`, `aria-describedby`, `role="alert"` on errors.
- `Skeleton` + skeleton screens actually used on StopSelector/StopDetail.
- Genuine ES/EU translation (only 5/98 keys identical) — Basque is not machine filler.
- Wireframes include permission priming, denied fallback, offline, 4 alert states, component library with tokens.

---

## 3. P0 — blocks "commercial"

| # | Defect | Evidence | Fix |
|---|---|---|---|
| 1 | Build fails at CSS | `tailwind.css` `@apply btn/chip/badge` (Tailwind 4 rejects custom classes) | convert to `@utility btn` or plain CSS |
| 2 | Fake financial data | `StopDetail.tsx` hardcodes `12,50 €` Mugi balance | remove (no API), or hide behind "próximamente" |
| 3 | Fake system stats | `Settings.tsx` `cacheSize '2.3'`, `lastUpdate 'hace 15s'`, `GTFS estático: v2026.11` | real: `navigator.storage.estimate()`, core cache stats; delete GTFS version |
| 4 | Dead primary CTA | `StopDetail.handleCreateAlert()` is empty → "Crear alerta" does nothing | navigate to `/alert/new?stop=…&line=…` |
| 5 | Fake favorites | `StopSelector.handleFavoriteToggle` = toast only; `loadFavorites()` returns `[]` | real favorites store or remove the star |
| 6 | Fake snooze/disable | `Home.handleSnooze/handleDisable` = toasts only, no state | implement or remove the buttons |
| 7 | Theme toggle no-op | `.dark` class added, but no `@custom-variant dark`; tokens follow OS only | `@custom-variant dark (&:where(.dark, .dark *))` + token overrides on `.dark` |
| 8 | Sticky headers overlap | `StopSelector` has two `sticky top-16` bars; `top-16` ignores safe-area header height | `--header-h` var + `PageHeader` component, `scroll-margin-top` |
| 9 | Invalid interactive nesting | `<Button><Link/></Button>` in Home ×3, AlertsList ×2, Settings ×4, StopDetail | `Link` styled as button, one interactive element |
| 10 | Contrast failures | `text-muted` on `bg-border` = **3.86:1** (default Badge); `text-warning` #F59E0B = **2.15:1**; `text-success` = **3.30:1**; focus ring #0066CC on dark bg = **3.21:1** | see §5 tokens |
| 11 | Hardcoded Spanish in components | `aria-label="Cerrar"`, `"Añadir a favoritas"`, empty states, confirm dialog, toast messages | move to i18n; add 7 missing keys |
| 12 | Hardcoded line list | `AlertConfig` `lines = ['5','16','21','25','33']` | real lines from `core.getStopLines(stopId)` |
| 13 | Motion missing | `animate-slide-up/down/fade-in/scale-in` used 6×, **no keyframes exist** | define `@keyframes` + `--animate-*` tokens |

---

## 4. P1 — quality

1. Toggle switch duplicated 3× inline (`Settings`, `AlertsList`, `AlertConfig`) with `w-10` vs `w-11` → extract `Switch`.
2. `min-h-[44px]` on AlertsList icon buttons vs 48px everywhere else.
3. Emoji used as status icons (`🌙 ✓ ✗ 🔔 🔕` + toast `✓ ✕ ℹ ⚠`) next to lucide icons → replace with lucide + tokens.
4. Offline page: `handleOpenSettings` opens `about:blank`; `hasCache = true` hardcoded; stale time hardcoded `'15 min'`.
5. `Header` bell `aria-label` = `home.actions.viewDetail`; hamburger labels hardcoded; menu duplicates 2 of 3 header links.
6. `role="list"` without `role="listitem"` on AlertsList; `aria-pressed="false"` hardcoded on StopSelector star.
7. `isSilenced()` (`AlertsList:39`) is wrong for non-crossing windows (`13:00–15:00` matches almost everything).
8. Countdown uses `date-fns` relative time for data freshness but `'` suffix for minutes — two time idioms.
9. `AlertConfig` range 1–30 min while bot allows 1–60; no focus-visible style on the custom slider thumb.
10. `AlertConfig` preview uses `font-mono` and `'Parada seleccionada'` literal → not a real push preview.
11. `Button` duplicates `baseClasses` (defined, never used) and re-inlines it; `loading` state lacks `aria-busy`.
12. Page titles: `h1` inside cards on Home, no `h1` on Header, two `h1` on StopDetail path.
13. `effect(fn, [deps])` used in 5 files — `@preact/signals-react` `effect` takes no deps; the second arg is ignored (dead code, misleading).
14. `any` everywhere in pages (`alerts.value.filter((a: any) => …)`) — should be `AlertSpec`/`UserAlert`.
15. Unused imports (`effect`, `Skeleton`, `Sun`, `Check`, duplicate `Users/CreditCard` at `StopDetail:265`).
16. No error boundary; a thrown render blanks the app.
17. No `aria-live` for arrival updates; countdown changes are silent to screen readers (acceptable) but arrival completion should be announced once.

---

## 5. Design System v2 (tokens with verified contrast)

Strategy: keep `#0066CC` identity, add a semantic status layer, fix the 4 failures.
Dark mode via class variant so "Claro/Oscuro/Sistema" actually works.

```css
@custom-variant dark (&:where(.dark, .dark *));

@theme {
  /* brand */
  --color-primary: #0066CC;        /* 5.57:1 on white */
  --color-primary-hover: #0052A3;
  --color-focus: #0066CC;          /* light */
  /* text */
  --color-text: #0F172A;
  --color-text-subtle: #475569;    /* 7.58:1 white, 7.24:1 on #F8FAFC  (NEW, for small text) */
  --color-text-muted: #64748B;     /* 4.76:1 white — never on #E2E8F0 */
  /* surfaces */
  --color-bg: #FFFFFF;  --color-bg-secondary: #F8FAFC;
  --color-surface: #FFFFFF;  --color-border: #E2E8F0;
  --color-border-strong: #CBD5E1;
  /* status (light) */
  --color-live: #166534;       --color-live-bg: #DCFCE7;      /* 6.49:1 */
  --color-scheduled: #334155;  --color-scheduled-bg: #E2E8F0; /* 8.40:1 */
  --color-silenced: #92400E;   --color-silenced-bg: #FEF3C7;  /* 6.37:1 */
  --color-danger-text: #B91C1C;
  --color-warning-text: #B45309;  /* 5.02:1 — replaces #F59E0B for text */
  --color-success-text: #15803D;  /* 5.02:1 — replaces #16A34A for text */
  /* type */
  --text-caption: 0.75rem;  --text-body: 1rem;  --text-title: 1.25rem;
  --text-display: 3rem;     /* countdown hero */
  /* motion */
  --animate-slide-up: slide-up 200ms cubic-bezier(.2,.8,.2,1);
  --animate-fade-in: fade-in 150ms ease-out;
  --animate-scale-in: scale-in 180ms cubic-bezier(.2,.8,.2,1);
}

.dark {
  --color-primary: #4D9FFF;      /* 6.56:1 on #0F172A, 5.38:1 on #1E293B */
  --color-focus: #7CC0FF;        /* 9.22:1 */
  --color-text: #F1F5F9;
  --color-text-subtle: #CBD5E1;
  --color-text-muted: #94A3B8;
  --color-bg: #0F172A;  --color-surface: #1E293B;
  --color-border: #334155;       /* decorative only */
  --color-border-strong: #64748B;/* 3.07:1 vs surface — use on interactive edges */
  --color-live: #86EFAC;   --color-live-bg: #14532D;      /* 6.49:1 */
  --color-scheduled: #CBD5E1; --color-scheduled-bg: #334155; /* 6.97:1 */
  --color-silenced: #FCD34D;  --color-silenced-bg: #451A03;
}

@keyframes slide-up { from { transform: translateY(8px); opacity: 0 } to { transform: none; opacity: 1 } }
@keyframes fade-in  { from { opacity: 0 } to { opacity: 1 } }
@keyframes scale-in { from { transform: scale(.96); opacity: 0 } to { transform: none; opacity: 1 } }

@media (prefers-reduced-motion: reduce) { *, *::before, *::after { animation-duration: .01ms !important; transition-duration: .01ms !important } }
```

Rules:
- `text-muted` only on `bg`/`surface`; small text uses `text-subtle`.
- Status colors always as a pair (`--color-X` + `--color-X-bg`), never raw `green-100`.
- One focus treatment: `outline: 2px solid var(--color-focus); outline-offset: 2px` (drop `focus:ring-*`).
- 4pt spacing scale; only `p-3/4/6`, `gap-2/3`, `space-y-3/6`.
- **Line pills from real API color**: `DbusLinea.color` exists and is unused. Render `L28` as a pill with that color; pick `#fff`/`#0F172A` text by computing relative luminance ≥ 0.45 → dark text. This is the single strongest "real transit app" signal and it's free.

---

## 6. Components to add / fix

| Component | Why |
|---|---|
| `Switch` | 3 duplicated inline toggles, 2 widths, off-system border color |
| `PageHeader` | 6 duplicated `sticky top-16 … safe-top` blocks; fixes overlap + `--header-h` |
| `ListRow` | stop/arrival/alert rows share layout 3× with different padding |
| `EmptyState` | 5 near-identical empty cards with hardcoded copy |
| `LinePill` | API line color + guaranteed contrast |
| `Stat` | Home/AlertsList/StopDetail stat tiles |
| `Button asChild` (or `LinkButton`) | removes `<button><a>` nesting |
| `Badge` → status variants | replace raw palette + add dark pairs |
| `Toast` icons → lucide | replace emoji |
| `Countdown` | tabular-nums, `aria-live="polite"` once at 0 |

---

## 7. Page scope

| Page | Work |
|---|---|
| **Onboarding** (missing) | 3 steps: value prop → permission priming (user gesture) → pick stop. Wireframe exists, no screen |
| **Home** | hero countdown (display type), real snooze/disable, data-freshness dot (🟢<30s/🟡30–60s/🔴>60s from wireframe 08), remove fake stat row |
| **StopSelector** | real favorites, real stops from `core`, fix double sticky, 44→48px, tabs `aria-selected` |
| **StopDetail** | wire "Crear alerta" with `?stop=&line=`, line pills, remove Mugi/occupancy or mark "próximamente" |
| **AlertConfig** | real lines from stop, 1–60 min, preview styled as actual notification (not mono), stop context in header, no `w-10` spacer hack |
| **AlertsList** | fix `isSilenced` window logic, per-alert `Disparada` state, swipe-free but real toggle, `listitem` roles |
| **Settings** | real storage estimate, real last-sync, remove GTFS version, theme via `.dark` class, permission status from `Notification.permission` |
| **Offline** | real cache presence + real timestamp; remove fake "abrir ajustes" or use `navigator.permissions` guidance |

---

## 8. State matrix (every page must define all 6)

`loading` · `empty` · `error` · `offline` · `stale` · `success`
Current: loading ✅ (2 pages), empty ⚠️ (hardcoded copy), error ⚠️ (toast only), offline ✅ (fake), stale ⚠️ (badge only on Home), success ✅.

---

## 9. Copy + i18n

- Move every literal to `es.json`/`eu.json`; add the 7 missing keys (`common.back/clear/lines/live/notifications/search/snooze`).
- Use ICU plurals: `{{minutes}} min` → `_one`/`_other`; Basque needs its own plural forms.
- Tone: second person, no exclamation marks, no emoji in strings (icons carry meaning).
- Dates via `Intl.RelativeTimeFormat` with the active locale, not `date-fns` + hardcoded `'hace 15s'`.
- All `aria-label`s in i18n.

---

## 10. Accessibility checklist (WCAG 2.2 AA)

- [ ] every text pair ≥4.5:1, icons ≥3:1, focus ring ≥3:1 in both themes
- [ ] one interactive element per action (no nested button/link)
- [ ] 48×48 targets (drop the 44px outliers)
- [ ] `aria-current="page"` on nav; `aria-selected` on tabs; `aria-pressed` reflects state
- [ ] visible focus everywhere, including range thumb and switches
- [ ] `prefers-reduced-motion` respected
- [ ] heading order: one `h1` per screen
- [ ] toasts: `role="status"` for info/success, `role="alert"` for errors, one live region
- [ ] form errors linked via `aria-describedby` (already correct in `Input`)

---

## 11. Wireframe audit

**Keep:** permission-priming flow, denied fallback, offline/stale, 4 alert states, component
library with token values, iOS push limitation note.

**Remove or mark "native later" (data/API cannot support):**
1. Occupancy (`●●○ Media`) — not in `tiemposParada`.
2. Mugi balance (`12,50 €`) — requires user's fare-card account.
3. Lock-screen widget — impossible as PWA; needs Capacitor plugin.
4. "GTFS estático v2026.11" — no static feed pipeline (403).
5. "DISPARADA — pulso azul" state — no trigger event in the client.

**Missing frames to add:**
1. Onboarding as 3 real screens (not a flow diagram).
2. Permission denied → persistent banner + settings guidance.
3. API failure (500/timeout) with retry, distinct from offline.
4. Alert triggered (in-app banner) — the payoff screen.
5. Long Basque copy (EU strings ~20–30% longer) + 320px width.
6. Dark mode frames for Home/StopDetail/AlertConfig.
7. Tablet/landscape layout (or explicitly "phone only").
8. Spacing/type annotations (current frames have no grid, no type scale).

---

## 12. Acceptance criteria — "commercial"

1. `vite build` green; mobile CI required.
2. `Lighthouse ≥ 95` accessibility + best practices on the built dist.
3. Zero hardcoded UI strings (`grep` gate in CI) and zero fake values.
4. Zero dead buttons: every interactive element changes state or navigates.
5. Every page implements all 6 states.
6. axe-core: 0 violations; contrast verified by script in CI.
7. Screenshot review at 320/390/430px + dark, no layout shift.
8. ES and EU complete (no missing keys, plural forms correct).

---

## 13. Effort + lane mapping

| Work | Days |
|---|---|
| Design system v2 (tokens, keyframes, dark variant) | 0.5 |
| New components (Switch, PageHeader, ListRow, EmptyState, LinePill, Stat, LinkButton) | 0.5 |
| Page fixes P0 (1–13) | 1.5 |
| States + copy + i18n | 0.5 |
| A11y pass + axe/contrast gate | 0.5 |
| Wireframe refresh (remove 5, add 8) | 0.5 |
| **Total** | **~4 days** |

Fits lane M: `M1b` (tokens/build) → `M3b` (pages real data) → `M5b` (a11y gate + Lighthouse).
No dependency on bot lane.

---

## 14. Out of scope

- Home-screen widget (Capacitor plugin, M6+).
- Occupancy and Mugi (no API).
- Native push stack (M6 decision).
