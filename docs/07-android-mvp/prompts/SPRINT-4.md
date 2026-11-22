# Prompt — Sprint 4 · Motor de avisos y notificaciones (Android MVP v1)

Pégalo en una sesión nueva de Claude Code en la raíz de `bus-dbus`.

---

Implementa el **Sprint 4** del MVP Android de bus-dbus.

**Lee primero, en este orden:** `AGENTS.md`, `ESTADO.md`,
`docs/07-android-mvp/SPRINTS.md` (§ Sprint 4),
`docs/07-android-mvp/docs/00-agent-guardrails.md`,
`docs/07-android-mvp/docs/04-android-platform.md` §1–§3 (motor, canales, acciones, permisos),
`docs/07-android-mvp/docs/02-domain-contracts.md` §1–§3 (reglas de disparo) y
`docs/07-android-mvp/wireframes/02-android-mvp/08-push-received.excalidraw`.

**Entorno (ya instalado, no reinstalar):**
```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
export PATH="$JAVA_HOME/bin:$PATH"
cd apps/android && ./gradlew check assembleDebug   # verde antes de empezar (95 tests por variante)
```
Emulador: AVD `dbus36` (`$ANDROID_HOME/emulator/emulator -avd dbus36 -no-window -no-audio`).
Memoria (Iker): el emulador y los daemons de Gradle pesan ~10 GB juntos. Apaga el
emulador (`adb emu kill`) en cuanto acabes las pruebas y `./gradlew --stop` al terminar.

**Rama:** `android/sprint-4` desde `main` (si `android/sprint-3` aún no está
integrada, pregúntale a Iker antes de ramificar). Alcance: `apps/android/**`.

**Ya existe (Sprints 1–3), úsalo y no lo dupliques:**
- `AppContainer` (`BusDonostiaApp.kt`): `fetchStop(stopId)` (única puerta a la API:
  guarda en `liveFeeds`, la hora de la última consulta y los colores), `alerts`
  (`StateFlow<List<AlertRule>?>`), `settings`, `ticker`, `appScope`, `database`.
- Dominio puro: `computeAlertStatus`, `currentWindow`, `findMatchingArrival`,
  `alertItem`/`sortAlerts`/`groupAlerts`/`alertBadgeCount` (`domain/AlertList.kt`),
  `withActive`/`snoozed`/`planSave` (`domain/AlertDraft.kt`), `SNOOZE_MS`, `ONCE_TTL_MS`.
- `ui/alerts/AlertsShared.kt`: `fetchStops`, `pollStops`, `stopsToWatch`,
  `setAlertActive`, `snoozeAlert`, `updateAlert`, `deleteAlert`, `restoreAlert`.
- `ui/system/SystemStatus.kt`: `readSystemStatus`, `needsNotificationPermission`,
  intents de ajustes y `openSettings`. Manifest ya declara `POST_NOTIFICATIONS` y
  `SCHEDULE_EXACT_ALARM`; A06 y A07 ya avisan si faltan.
- Textos de la notificación ya en `strings.xml` (es + eu): `notif_title`,
  `notif_title_no_destination`, `notif_body_stop`, `notif_action_snooze`,
  `notif_action_dismiss`. Falta el `{En vivo | Horario}` del cuerpo.
- A06 guarda en Room y vuelve atrás; **todavía no arranca nada**.

**Entregables:**
1. **Foreground Service `dataSync`** (`FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_SYNC`)
   con notificación silenciosa en canal `dbus_monitoring` (`IMPORTANCE_LOW`):
   «Vigilando L5 en Pío Baroja 10». Sondea cada `pollIntervalSec` agrupando por
   parada (`fetchStops(stopsToWatch(...))`), evalúa `alertItem` y, al pasar a
   `TRIGGERED`, notifica, escribe `lastTriggeredAtMs = now` y, si es ONCE,
   `active = false`. Se para sin avisos en vigilancia o tras 60 min seguidos sin
   servicio/errores (y lo notifica). La decisión «disparar / seguir / parar» en
   una función pura con tests JVM.
2. **Arranque:** ONCE al guardar en A06 (gesto del usuario) y al reactivar con el
   switch o Posponer; RECURRING con `AlarmManager.setExactAndAllowWhileIdle` en
   `windowFrom` de cada día marcado (inexacta si falta el permiso), reprogramadas
   en `BOOT_COMPLETED`, `TIME_SET`, `TIMEZONE_CHANGED` y al guardar/editar/borrar.
3. **Canal `dbus_arrivals_high`** (`IMPORTANCE_HIGH`): título `L{line} → {dest} en {min} min`,
   cuerpo `{stopName} ({stopId}) · {En vivo | Horario}`, acciones `Posponer 5'`
   y `Descartar` por `BroadcastReceiver` (sin abrir UI) con la semántica de
   `04-android-platform.md` §2.
4. **Deep link** `dbus://stop/{stopId}?highlightLine={lineId}` al tocar la
   notificación (intent-filter + `navDeepLink` en `DetailRoutes.STOP`).
5. Si `POST_NOTIFICATIONS` está denegado: aviso visible en A04 con enlace a
   `notificationSettingsIntent`.
6. Textos nuevos `es` + `eu`; lógica en funciones puras con tests JVM.

**DoD:** `./gradlew check assembleDebug` verde; flujo completo en el emulador contra
la API real: cercana → A05 → 🔔 → guardar ONCE → notificación real → `Posponer 5'`
→ segunda notificación; RECURRING con franja que empieza en 2 min → alarma →
servicio → notificación; reinicio del emulador → alarmas reprogramadas. Capturas
claro/oscuro en `docs/07-android-mvp/capturas/sprint-4/`; cero literales y cero
datos inventados (`grep`). Actualiza `SPRINTS.md` y `ESTADO.md`. Delega lo
mecánico a subagentes baratos y verifica tú. Merge a `main`, push y borrado de
rama **solo con autorización de Iker en la sesión**; si no, repórtala con
`git log --oneline main..android/sprint-4`. Deja el prompt del Sprint 5 en
`docs/07-android-mvp/prompts/SPRINT-5.md`.
