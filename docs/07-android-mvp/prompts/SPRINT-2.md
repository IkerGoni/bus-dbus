# Prompt — Sprint 2 · Consulta: Paradas, Detalle, Líneas (Android MVP v1)

Pégalo en una sesión nueva de Claude Code en la raíz de `bus-dbus`.

---

Implementa el **Sprint 2 (Consulta)** del MVP Android de bus-dbus.

**Lee primero, en este orden:** `AGENTS.md`, `ESTADO.md`,
`docs/07-android-mvp/SPRINTS.md` (§ Sprint 2),
`docs/07-android-mvp/prompts/PROMPT-IMPLEMENTACION.md`,
`docs/07-android-mvp/docs/00-agent-guardrails.md`,
`docs/07-android-mvp/docs/03-android-screens.md` (A02, A03, A03b, A05 y las 5
vistas de estado), `docs/07-android-mvp/docs/02-domain-contracts.md` §1, §2 y §4,
`docs/07-android-mvp/design-system/`, `docs/07-android-mvp/wireframes/` y
`docs/07-android-mvp/stitch-references/paradas/` (referencia visual, no código a portar),
y `apps/android/README.md` § Datos.

**Entorno (ya instalado, no reinstalar):**
```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
export PATH="$JAVA_HOME/bin:$PATH"
cd apps/android && ./gradlew check assembleDebug   # debe estar verde antes de empezar (66 tests)
```
Emulador: AVD `dbus36` (`$ANDROID_HOME/emulator/emulator -avd dbus36 -no-window`).

**Rama:** `android/sprint-2` desde `main`. Alcance: `apps/android/**`. No tocar
`apps/bot/**`, `apps/mobile/**` ni `packages/**` (salvo añadir fixtures reales).

**Ya existe (Sprint 1), úsalo y no lo dupliques:**
- `domain/`: `Catalog` + `toPublicLineId`/`lineByCode`/`stopByCode`/`isNightLine`,
  `remainingMinutes`, `computeFreshness`, `haversineMeters`, `findMatchingArrival`,
  `Ticker` (`StateFlow<Instant>` de 1 s), modelos `StopFeed`/`Arrival`/`Favorite`.
- `data/catalog/loadCatalog()` (assets), `data/config/ConfigRepository`
  (+ `loadBundled`, `refreshIfDue`), `data/api/DbusClient.tiemposParada()` →
  `StopFeed` (`OK`, `NO_SERVICE` con `notice`, `EMPTY`, `API_ERROR`; `notice`
  también puede venir con `OK`).
- `data/db/AppDatabase`: favoritos (3 tipos, `favoriteId`), recientes
  (`record`, máx. 10), colores de línea (`StopFeed.lineColors()` + `upsertAll`),
  avisos (aún sin UI).

**Entregables:**
1. Cableado sin framework de DI: un contenedor en una `Application` que crea
   OkHttp, `AppDatabase`, `ConfigRepository` (llama a `refreshIfDue` al abrir,
   sin bloquear), `DbusClient` y el catálogo (carga fuera del hilo principal).
2. **A02 Paradas:** buscador offline (código exacto/parcial y nombre sin acentos,
   paridad con `searchStops` de `@dbus/core`), Cercanas (permiso de ubicación
   coarse no bloqueante, top 20 por Haversine, solo las 514 con coordenadas,
   distancia y «X min a pie» a 80 m/min marcado como estimación en línea recta),
   Favoritas, Recientes. Atribución ODbL visible donde se usan coordenadas.
3. **A05 Detalle:** llegadas en vivo con refresco cada `pollIntervalSec` (30 s)
   mientras la pantalla está visible; minutos con `remainingMinutes` + `Ticker`
   (nunca segundos); `●` verde para `LIVE`, reloj gris para `SCHEDULED`; las 5
   vistas (`loading`, `empty`, `live`, `degraded` = datos viejos según
   `computeFreshness` o `NO_SERVICE` con su motivo, `error`); `notice` visible
   también con datos; chips de línea con el color real visto en vivo (neutro si
   nunca se vio); ★ persistente (STOP y STOP_LINE); registrar en recientes al
   abrir; `Cómo llegar` con `geo:` solo si la parada tiene coordenadas. Sin 🔔
   todavía (Sprint 3): nada de botones sin acción.
4. **A03 Líneas y A03b Itinerario** desde el catálogo: buscador por número,
   alias interno (`32` → gana la pública) o destino; filtros Todas / Favoritas
   ★ / Nocturnas; ★ de línea (`LINE`); itinerarios reales con timeline de
   paradas; tap en parada → A05.
5. Lógica de pantalla en funciones puras o ViewModels testeables (búsqueda,
   ranking de cercanas, mapeo `StopFeed` + frescura → vista de estado) con tests
   JVM. Todo texto en `strings.xml` `es` + `eu`.

**DoD:** `./gradlew check assembleDebug` verde; probado en el emulador contra la
API real (si Dbus está caído, decirlo); capturas de A02, A05 (al menos `live`,
`degraded`/`NO_SERVICE` con la parada `1` y `error` en modo avión), A03 y A03b
en claro y oscuro en `docs/07-android-mvp/capturas/sprint-2/`; cero datos
inventados (revisión con `grep` de literales). Actualiza `SPRINTS.md` (estado)
y `ESTADO.md`. Delega lo mecánico a subagentes baratos (CLAUDE.md §6) y verifica
tú el resultado. Al terminar: merge de `android/sprint-2` a `main`, push y
borrado de la rama **solo si Iker lo autoriza en la sesión** (si no, déjala
reportada con `git log --oneline main..android/sprint-2`), y deja el prompt del
Sprint 3 en `docs/07-android-mvp/prompts/SPRINT-3.md`.
