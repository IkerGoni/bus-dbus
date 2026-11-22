# Prompt — Sprint 1 · Datos y dominio (Android MVP v1)

Pégalo en una sesión nueva de Claude Code en la raíz de `bus-dbus`.

---

Implementa el **Sprint 1 (Datos y dominio)** del MVP Android de bus-dbus.

**Lee primero, en este orden:** `AGENTS.md`, `ESTADO.md`,
`docs/07-android-mvp/SPRINTS.md` (§ Sprint 1),
`docs/07-android-mvp/prompts/PROMPT-IMPLEMENTACION.md`,
`docs/07-android-mvp/docs/02-domain-contracts.md` (incluida la tabla §5),
`docs/03-architecture/adrs/009-android-nativo-mvp.md` y
`docs/06-api-externa/01-endpoints-y-riesgo.md` §2.1.

**Entorno (ya instalado, no reinstalar):**
```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
export PATH="$JAVA_HOME/bin:$PATH"
cd apps/android && ./gradlew check assembleDebug   # debe estar verde antes de empezar
```
Emulador: AVD `dbus36` (`$ANDROID_HOME/emulator/emulator -avd dbus36 -no-window`).

**Rama:** `android/sprint-1` desde `main`. Alcance: `apps/android/**`; en
`packages/contracts/fixtures/` solo se añaden archivos. No tocar `apps/bot/**`
ni `apps/mobile/**`.

**Entregables:**
1. Tarea Gradle que fusiona `apps/bot/data/dbus-catalog.json` +
   `dbus-stop-coordinates.json` en `assets/catalog.json` en cada build (sin
   commitear el generado). Parser + test de conteos 548 / 514 / 39 / 127.
2. `ApiConfig`: `assets/config.json` por defecto (base URL, paths, parámetros,
   cabeceras, `pollIntervalSec`, `minAppVersion`, `notice`) + loader remoto
   opcional (cada 12 h, timeout 5 s, validación, fallback a última válida o
   empaquetada; `apiBaseUrl` HTTP distinto de `app.dbus.eus` rechazado). URL
   remota vacía por ahora (irá al repo público en Sprint 5).
3. Cliente `tiemposParada` (OkHttp + kotlinx.serialization) → `StopFeed`
   (`OK`, `NO_SERVICE` con `avisos`, `EMPTY`, `API_ERROR`). Tests contra
   fixtures reales: hoy solo existe `tiemposParada-1.json`; captura con `curl`
   respuestas reales `DATOS`, `SIN_SERVICIO` (p. ej. parada `1`) y `NO DESC`, y
   guárdalas en `packages/contracts/fixtures/`. No inventes fixtures.
4. Room (KSP `2.2.21-2.0.5`, Room `2.8.x`): favoritos (3 tipos, id
   determinista), avisos (dedup `(stopId, lineId)`, campos de §1), recientes
   (máx. 10), colores de línea vistos en vivo.
5. Dominio en Kotlin puro (sin Android): `remainingMinutes`,
   `computeAlertStatus` (+ `currentWindow` con `Europe/Madrid`),
   `computeFreshness`, `haversineMeters`, matching con preferencia GPS e
   identidad de línea (`32`→`28`, gana la pública; `B4` válido). Tests de cada
   fila de la tabla §5: DST de marzo y octubre, cruce de medianoche, una vez
   por franja, ONCE caduca a 7 días, Descartar en RECURRING no re-dispara.
6. Reloj reactivo de 1 s (`StateFlow<Instant>`), inyectable para tests.

**Sin UI nueva** en este sprint (las pantallas son el Sprint 2). Sin backend,
sin mocks en producción.

**DoD:** `./gradlew check assembleDebug` verde; tests de dominio, parser,
catálogo y config pasando; la paridad con `@dbus/core` sobre los mismos
fixtures documentada en el test. Actualiza `SPRINTS.md` (estado) y
`ESTADO.md`. Delega lo mecánico a subagentes baratos (CLAUDE.md §6) y verifica
tú el resultado. Al terminar: merge de `android/sprint-1` a `main`, push y
borrado de la rama (Iker lo autoriza para este sprint), y dame el prompt del
Sprint 2 guardado en `docs/07-android-mvp/prompts/SPRINT-2.md`.
