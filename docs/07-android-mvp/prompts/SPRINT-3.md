# Prompt — Sprint 3 · Inicio, Avisos, Ajustes, Onboarding (Android MVP v1)

Pégalo en una sesión nueva de Claude Code en la raíz de `bus-dbus`.

---

Implementa el **Sprint 3** del MVP Android de bus-dbus.

**Lee primero, en este orden:** `AGENTS.md`, `ESTADO.md`,
`docs/07-android-mvp/SPRINTS.md` (§ Sprint 3),
`docs/07-android-mvp/docs/00-agent-guardrails.md`,
`docs/07-android-mvp/docs/03-android-screens.md` (A01, A04, A06, A07, A08 y el
badge de §1), `docs/07-android-mvp/docs/02-domain-contracts.md` §1–§3,
`docs/07-android-mvp/design-system/`, `docs/07-android-mvp/wireframes/` y
`docs/07-android-mvp/stitch-references/inicio/` (referencia visual, no código a portar).

**Entorno (ya instalado, no reinstalar):**
```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
export PATH="$JAVA_HOME/bin:$PATH"
cd apps/android && ./gradlew check assembleDebug   # verde antes de empezar (82 tests por variante)
```
Emulador: AVD `dbus36` (`$ANDROID_HOME/emulator/emulator -avd dbus36 -no-window -no-audio`).
Memoria (Iker): el emulador y los daemons de Gradle pesan ~10 GB juntos. Apaga el
emulador (`adb emu kill`) en cuanto acabes las capturas y `./gradlew --stop` al terminar.

**Rama:** `android/sprint-3` desde `main` (si `android/sprint-2` aún no está
integrada, pregúntale a Iker antes de ramificar). Alcance: `apps/android/**`.

**Ya existe (Sprints 1–2), úsalo y no lo dupliques:**
- `AppContainer` (`BusDonostiaApp.kt`): OkHttp, `AppDatabase`, `ConfigRepository`,
  `DbusClient`, `catalog: StateFlow<Result<Catalog>?>`, `favorites`, `lineColors`,
  `ticker`, `location`, `apiLanguage()`, `appScope`. ViewModels con `appViewModel { }`.
- Dominio: `computeAlertStatus`, `findMatchingArrival`, `remainingMinutes`,
  `stopArrivalsView` (5 vistas), `searchStops`/`searchLines`, `nearbyStops`.
- UI: `ui/components/Components.kt` (`LineBadge`, `StopCodePill`, `FavoriteStar`,
  `SearchPill`, `ScreenHeader`, `BorderedCard`, `MinutesLabel`, `SkeletonList`,
  `SectionLabel`, `InfoBanner`), `EmptyState`. Rutas `DetailRoutes.stop/line` en `AppShell`.
- A05 (`ui/stopdetail/`): sondeo 30 s con `repeatOnLifecycle(STARTED)`; la fila
  de llegada aún no tiene 🔔 ni hay botón `Crear aviso` (llegan ahora).

**Entregables:**
1. **A06 Configurar aviso** (`alert/new?stopId=&lineId=&destination=` y
   `alert/{id}/edit`): líneas reales de la parada, antelación 1–30 con chips
   `2' 3' 5' 8' 10' 15'` + slider, `ONCE` / `RECURRING` (días ISO, presets,
   franja `Desde–Hasta` obligatoria, máx. 3 h), vista previa etiquetada, dedup
   `(stopId, lineId)` ofreciendo actualizar. Sin motor todavía: guardar en Room.
2. A05: 🔔 de 48 dp por fila de llegada (precarga parada, línea y destino) y pie
   fijo `Crear aviso en esta parada`.
3. **A04 Mis avisos:** grupos ACTIVOS / SILENCIADOS Y PAUSADOS, 5 estados con
   `computeAlertStatus`, switch `active`, editar, borrar con `Deshacer`, vacío con CTA.
4. **A01 Inicio:** `TU PRÓXIMO AVISO` (Posponer 5' y Pausar reales) y
   `PARADAS FAVORITAS` con 2–3 llegadas en vivo, refresco 30 s y pull-to-refresh.
5. Badge de Avisos (`ACTIVE | TRIGGERED`, `9+`).
6. **A07 Ajustes** (tema, idioma es/eu con `AppCompatDelegate`/`LocaleManager`,
   estado real de notificaciones, alarmas exactas y batería, datos del catálogo
   y hora de la última consulta real, fuentes y licencias) y **A08 Onboarding**.
7. Lógica en funciones puras/ViewModels con tests JVM; textos `es` + `eu`.

**DoD:** `./gradlew check assembleDebug` verde; todas las pantallas con sus 5
estados probadas en el emulador contra la API real; capturas claro/oscuro en
`docs/07-android-mvp/capturas/sprint-3/`; cero literales y cero datos
inventados (`grep`). Actualiza `SPRINTS.md` y `ESTADO.md`. Delega lo mecánico a
subagentes baratos y verifica tú. Merge a `main`, push y borrado de rama **solo
con autorización de Iker en la sesión**; si no, repórtala con
`git log --oneline main..android/sprint-3`. Deja el prompt del Sprint 4 en
`docs/07-android-mvp/prompts/SPRINT-4.md`.
