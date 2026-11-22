# Auditoría de arquitectura · bus-dbus · 2026-11-22

Rama `audit/arquitectura` (worktree `../bus-dbus-audit`), base `main` @ `b8e08f8`.
Alcance: `apps/bot/**`, `apps/android/**`, `packages/**`. Fuera: `apps/mobile/**`
(congelada, ADR 009). Mandan `AGENTS.md` y las decisiones «no reabrir» de `ESTADO.md`.

## Resumen ejecutivo

- Arquitectura sana en lo esencial: `domain/` de Android es puro, cada app tiene una sola puerta a Dbus y no hay secretos en el historial (0 coincidencias en 71 commits).
- **Partida en rojo:** Dbus devuelve `tipo=0` (antes `2`) y el test de integración del bot fallaba. El código ya lo trataba bien; arreglado el test y añadida paridad TS/Kotlin con un fixture real.
- **Riesgo principal (H1, alta):** el bot vuelve a pedir a Dbus cada 30 s toda parada consultada alguna vez, haya usuarios o no: hasta 160 peticiones/min. Riesgo de bloqueo de IP. Propuesta P1.
- El sondeo adaptativo de Android sí conviene en el bot, aplicado al refresco y no al cron (P2): 0 peticiones sin avisos ni usuarios y avisos ~30 s antes en vez de hasta ~90 s tarde.
- Android: el motor dependía de `ui/` (arreglado); `AppContainer` mezcla cableado y política; 0 logs y `catch` mudos en el motor (P3).
- Secretos: `BOT_TOKEN` solo por entorno y Telegraf lo redacta (probado). Pendiente: rotarlo (R6), `chmod 600 .env`, firma de release con Play App Signing y lista de hosts para la config remota.
- 3 commits sin cambio de comportamiento; las dos verificaciones en verde (bot 86/86, Android 95 por variante, lint 0 errores). No hace falta reiniciar el bot.
- Sin medir: arranque en frío, recomposiciones y APK release (8 GB de RAM; comandos en la sección de rendimiento).

## Cifras de partida y finales

| Verificación | Partida (`b8e08f8`) | Final (`7d3d556`) |
|---|---|---|
| `pnpm --filter @dbus/bot verify` | **rojo**: 85/86 tests (falla `integration.test.ts:51`, `tipo=0`), `tsc` y biome limpios | **verde**: 86/86, `tsc` y biome limpios |
| `@dbus/core` tests | 14/14 | 15/15 (+1 paridad `tipo=0`) |
| `@dbus/contracts` tests | 7/7 | 7/7 |
| Android `./gradlew check assembleDebug` | verde · 94 tests por variante · lint 0 errores, 29 avisos | verde · 95 tests por variante · lint 0 errores, 29 avisos (mismos ids) |
| Avisos de lint por id | `GradleDependency` 11 · `NewerVersionAvailable` 9 · `UnusedAttribute` 2 · `Typos` 2 · `AndroidGradlePluginVersion` 2 · `ObsoleteSdkInt` 1 · `InlinedApi` 1 · `DataExtractionRules` 1 | idénticos |
| APK debug | 21 074 357 B | 21 145 119 B (+70 762 B; los `.dex` sin comprimir solo +48 B; el resto viene del empaquetado, no investigado) |
| Tiempo de `check assembleDebug` | 38 min 38 s (en frío, con la otra sesión compilando y ~8 GB de swap) | 1 min 13 s (incremental; el daemon de Kotlin se quedó sin heap y Gradle recompiló fuera de él) |

## Inventario (fase 1)

Hecho con tres subagentes (uno por lane) y verificado a mano en los puntos que
sustentan hallazgos (`wc -l`, `grep`, `cmp`).

| Lane | Archivos fuente | Líneas | Mayores (líneas) |
|---|---|---|---|
| bot `apps/bot/src` | 19 `.ts` | 3 410 | `render.ts` 791 · `db.ts` 457 · `commands.ts` 420 · `callbacks.ts` 270 |
| android `app/src/main/kotlin` | 58 `.kt` | 6 318 | `StopDetailScreen.kt` 397 · `HomeScreen.kt` 361 · `StopsScreen.kt` 351 · `AlertEditScreen.kt` 333 |
| packages `core` + `contracts` | 8 `.ts` | 956 | `core/client.ts` 268 · `core/catalog.ts` 191 |

Dependencias clave (verificadas):

- **Bot:** `index.ts` (composition root) → `handlers.ts` → `commands.ts` / `callbacks.ts` → `render.ts` → `arrival.ts` / `catalog.ts` / `db.ts` → `dbus-client.ts` → `@dbus/core.createDbusClient`. La única puerta HTTP a Dbus es `@dbus/core/client.ts:111` (`getJson`); no hay `fetch` directo en `apps/bot/src`.
- **Android:** `domain/` (13 archivos) no importa `android.*` ni `androidx.*` (grep vacío). La única puerta a `tiemposParada` es `AppContainer.fetchStop` (`BusDonostiaApp.kt:96`), que usan `StopDetailViewModel.kt:122`, `AlertEditViewModel.kt:168` y, vía `fetchStops`, Home, A04 y el motor.
- **Packages:** el bot consume `@dbus/core` (cliente, parseo, matching, catálogo) y `@dbus/contracts` (esquema del catálogo). Android no importa código TS: solo lee `packages/contracts/fixtures/` en sus tests y `apps/bot/data/*.json` en el build.

## Hallazgos (fase 2)

Severidad: alta / media / baja. Esfuerzo: S (< 1 h), M (medio día), L (> 1 día).
Riesgo = riesgo de aplicar el arreglo. Estado: **arreglado** en esta rama, o
**propuesta** (ver «Propuestas priorizadas»).

| # | Sev. | Área | Hallazgo | Evidencia | Esf. | Riesgo | Estado |
|---|---|---|---|---|---|---|---|
| H1 | alta | rendimiento · bot | El refresco en segundo plano vuelve a pedir a Dbus, cada 30 s y con `forceRefresh`, **toda parada consultada alguna vez** (hasta 80), aunque nadie la mire. Las entradas no caducan: el refresco renueva su `ts`, así que la parada nunca sale del conjunto. Además el bot precarga la parada `2` al arrancar, que queda en el ciclo para siempre. | `apps/bot/src/index.ts:57-105`, `apps/bot/src/handlers.ts:119`, `packages/core/src/client.ts:155-159`, `:235-237` | M | medio | propuesta P1 |
| H2 | alta | contrato · todos | Dbus devuelve hoy `tipo=0` donde antes daba `tipo=2` (sin GPS). El test de integración exigía `[1, 2]` y dejaba `verify` del bot **en rojo de partida**. El dominio ya lo trata bien (solo `tipo=1` es tiempo real) en TS y en Kotlin. La semántica exacta de `0` no está confirmada. | `apps/bot/tests/integration.test.ts:51`; `packages/core/src/arrival.ts:44`; `StopFeedParser.kt:47`; muestreo de 9 paradas a las 18:3x: solo `tipo` 0 y 1 | S | bajo | **arreglado** (C1, C2) |
| H3 | media | rendimiento · bot | El scheduler de avisos es un cron de 1 min que recorre las paradas **en serie** y lee la caché (hasta 30 s de antigüedad), sin margen de antelación. Cota por código: el aviso puede salir hasta ~90 s después de que Dbus diga `minutos ≤ X`. Android avisa 30 s **antes** (`NOTIFY_EARLY_MS`). | `apps/bot/src/alerts.ts:34`, `:90-99`; `domain/Alerts.kt:10` | M | medio | propuesta P2 |
| H4 | media | capas · android | El motor (`engine/`) importa de `ui.alerts` (`feedView`, `fetchStops`): la capa de servicio depende de la de UI. | `engine/AlertMonitorService.kt:21-22` | S | bajo | **arreglado** (C3) |
| H5 | media | robustez · android | Ninguna llamada a `Log` en toda la app (grep: 0). Los `catch` son mudos; el del motor captura `Exception`, devuelve `CONTINUE` y **no cuenta como problema**: un fallo persistente de Room nunca llega a `NO_SERVICE_STOP_MS` y el servicio queda vivo sondeando cada 30 s sin hacer nada útil. | `engine/AlertMonitorService.kt:128-131`; `data/api/DbusClient.kt:40`; `data/config/ConfigRepository.kt:51`, `:69` | S | bajo | propuesta P3 |
| H6 | media | duplicación TS/Kotlin | ADR 009 §4 promete paridad por fixtures de `computeAlertStatus`, `computeFreshness`, `remainingMinutes` y `haversineMeters`, pero **no existen en `@dbus/core`** (el bot hace la caducidad y el matching a su manera). Los tests de core leían 1 fixture; los de Android, 6. La paridad real cubre solo el parseo de `tiemposParada`. | `grep computeFreshness\|computeAlertStatus packages/core/src` vacío; `packages/core/tests/core.test.ts:2`; `apps/android/.../StopFeedParserTest.kt` | M | bajo | parcial (C2) + propuesta P4 |
| H7 | media | robustez · android | Timeouts HTTP: `connect` 10 s y `call` 15 s; la spec dice 8 s. Con el motor en segundo plano, una parada colgada retrasa toda la vuelta hasta 15 s. | `BusDonostiaApp.kt:45-46`; `docs/07-android-mvp/docs/04-android-platform.md` §0 | S | bajo | propuesta P5 |
| H8 | media | publicación · android | `release` sin `signingConfig` ni `isShrinkResources`. No bloquea el MVP, sí el Sprint 5. | `apps/android/app/build.gradle.kts:81-84` | S | bajo | propuesta P6 |
| H9 | media | responsabilidad única · android | `AppContainer` es composition root **y** política: `fetchStop` registra feeds, hora de última consulta y colores; su `init` observa `alerts` y arranca el servicio. | `BusDonostiaApp.kt:96-118` | M | medio | propuesta P7 |
| H10 | baja | rendimiento · android | El `Ticker` de 1 s hace que el estado de A01, A04 y A05 se emita cada segundo y se recompongan las filas; `items(rows)` sin `key`. No medido en dispositivo (ver «Cifras»). | `domain/Ticker.kt:22-24`; `ui/stopdetail/StopDetailScreen.kt:301` | S | bajo | propuesta P8 |
| H11 | baja | lógica en UI · android | Lógica de presentación con reglas dentro de composables: filtrado de llegadas, texto de origen del próximo aviso, dedup de destinos, pista de estado del feed. | `StopDetailScreen.kt:290`; `HomeScreen.kt:268-283`; `AlertEditScreen.kt:255-256`; `AlertsScreen.kt:249-260` | M | bajo | propuesta P8 |
| H12 | baja | responsabilidad única · bot | `render.ts` (791 líneas) mezcla vistas con mutaciones de BD (`createAndConfirmAlert`, `saveFavorite`, `storeLineFavorite`). `ESTADO.md` decidió no dividir módulos del bot: solo se propone sacar funciones puras. | `apps/bot/src/render.ts:434-451`, `:679-700`, `:715-760` | M | bajo | propuesta P9 |
| H13 | baja | secretos · bot | `BOT_TOKEN` solo por entorno y validado con zod (`config.ts:11`, `:29`). **0 coincidencias** del patrón de token de Telegram en `git log --all -p` (71 commits). Telegraf 4.16.3 redacta el token en `message` **y** `stack` (probado con token falso y red caída). Riesgos residuales: `.env` raíz con permisos `0644`; el token es visible en el entorno del proceso para el mismo usuario (`ps eww`). | `apps/bot/src/config.ts:29`; `node_modules/telegraf/lib/core/network/client.js:247-250` | S | bajo | propuesta P10 |
| H14 | baja | concurrencia · bot | Lock de instancia: `pidAlive` trata `EPERM` como proceso muerto, y entre `readLockPid` y `unlinkSync` hay una ventana en la que dos arranques simultáneos pueden creerse dueños. Lo mitiga el reintento del 409. | `apps/bot/src/single-instance.ts:7-14`, `:61-68` | S | bajo | propuesta P11 |
| H15 | baja | concurrencia · android | `running`: si se guarda un aviso entre la decisión `STOP_IDLE` del motor y `running = false`, `ensureMonitoring` ve `running == true`, no arranca y el aviso no se vigila hasta reabrir la app. Ventana de milisegundos (sin red en ese camino). | `engine/AlertMonitorService.kt:102-107`; `engine/AlertMonitoring.kt:12` | S | medio | propuesta P11 |
| H16 | baja | rendimiento · android | Peticiones duplicadas: la UI sondea cada 30 s sin mirar si el motor acaba de consultar; el motor solo se salta la parada si la UI la pidió hace < 25 s. `fetchStop` no deduplica peticiones en vuelo (el cliente TS sí: `client.ts:147-148`). | `ui/alerts/AlertsShared.kt:45-52`; `domain/AlertEngine.kt:45-46` | S | bajo | propuesta P8 |
| H17 | baja | datos · packages | Catálogo duplicado y desfasado: `packages/contracts/data/catalog.json` (`generatedAt` 05:37) ≠ `apps/bot/data/dbus-catalog.json` (08:16); difieren en `generatedAt` y `coverage`. Ya estaba en `ESTADO.md`. | `cmp`: difieren en línea 2 | S | bajo | propuesta P12 |
| H18 | baja | limpieza · packages | `@dbus/core` declara `zod` y no lo importa; exports sin consumidor fuera de tests (`getArrivals`, `getStopLines`, `getLastSync` del cliente; tipos `Line`, `Logger`). | `packages/core/package.json`; `grep zod packages/core/src` vacío | S | bajo | propuesta P12 |
| H19 | baja | privacidad · bot | El log de producción (`$TMPDIR/dbus-bot.log`, `0644`) guarda el `chat_id` de cada aviso. | `apps/bot/src/alerts.ts:112`, `:124` | S | bajo | propuesta P10 |
| H20 | baja | robustez · android | El motor borra el aviso **antes** de notificar: sin permiso de notificaciones, el aviso se consume sin que el usuario lo vea. A04 ya avisa si faltan permisos, por eso es baja. | `engine/AlertMonitorService.kt:150-151` | S | bajo | propuesta P3 |
| H21 | baja | secretos · android | La config remota (Sprint 5) podrá cambiar `apiBaseUrl` a **cualquier** host HTTPS y añadir cabeceras: quien controle ese hosting redirige las consultas de todas las apps. No hay lista de hosts permitidos ni firma. Impacto limitado (solo viajan códigos de parada). | `data/config/ApiConfig.kt:57-60`; `data/api/DbusClient.kt:33` | S | bajo | propuesta P10 |

Comprobado y **sin hallazgo**:

- `domain/` de Android sin imports de Android (grep vacío). `fetchStop` es la única puerta de la app a `tiemposParada`; `getJson` (`client.ts:111`) la del bot.
- Cleartext solo para `app.dbus.eus` (`res/xml/network_security_config.xml`: `base-config` en `false`, `includeSubdomains="false"`), sin `usesCleartextTraffic` en el manifest. `ApiConfig` exige HTTPS para cualquier otro host (`data/config/ApiConfig.kt:43`, `:57-60`). `REMOTE_CONFIG_URL` vacío hasta el Sprint 5 (`build.gradle.kts:77`).
- SQLite del bot: las 4 consultas calientes usan índice (`EXPLAIN QUERY PLAN` sobre copia de la BD de producción). Tablas diminutas (2 avisos, 5 favoritos). `idx_alerts_chat` es redundante con el prefijo de `idx_alerts_dedup` (irrelevante a este tamaño).
- Room: índice único `(stopId, lineId)` en `alerts`; el resto son búsquedas por PK o listados completos de tablas de decenas de filas.
- Historial git: ni `.env`, ni keystores, ni `local.properties` se han commiteado nunca; `.gitignore` cubre `.env`, `*.db`, `*.jks`, `*.keystore`, `local.properties`. La única `VAPID_PRIVATE_KEY=` del historial es una plantilla `${…}` de `apps/mobile/scripts/generate-vapid.js`.

### Responsabilidad única: detalle

- **Bot.** Capas limpias: red y caché en `@dbus/core`, persistencia en `db.ts`, programación en `alerts.ts`, Telegram en `render.ts`/`commands.ts`/`callbacks.ts`. El problema es `render.ts` (H12), que hace de vista y de servicio. `handlers.ts` arranca el scheduler y precarga Dbus (`:118-119`): efectos de arranque que pertenecen a `index.ts`.
- **Android.** `domain/` es puro y está testeado (16 archivos de test). Los problemas están en los bordes: el motor depende de UI (H4, arreglado), `AppContainer` decide política (H9) y algunos composables aplican reglas (H11). Los ViewModels grandes (`AlertEditViewModel` 233, `StopsViewModel` 170) siguen delegando en `domain/`; no hace falta partirlos.
- **Duplicación TS ↔ Kotlin.** Es deliberada (ADR 009 §4). Lo que falta es la red de seguridad: hoy solo el parseo tiene paridad probada (H6).

### Rendimiento: cifras medidas

Medido el 2026-11-22 entre las 18:30 y las 19:10 en el Mac de desarrollo
(8 GB de RAM). Lo que no se pudo medir se dice, no se estima.

**Bot**

| Qué | Cifra | Cómo |
|---|---|---|
| Latencia de Dbus `tiemposParada` | p50 174 ms · p90 232 ms · máx. 258 ms · 1 751 B por respuesta | 20 `curl` seguidos a la parada 2 |
| `getNextArrivals` en frío / con caché | 242,7 ms / 2,9 µs | `dist/arrival.js`, `performance.now()` |
| Carga del catálogo + primera búsqueda | 40,5 ms | import de `dist/catalog.js` |
| `searchStops('urbieta')` | 1,1 ms por llamada (media de 1 000) | normaliza las 548 paradas en cada búsqueda; irrelevante frente a la red |
| `listAllLines()` | 0,5 ms | media de 1 000 |
| `getActiveAlerts()` con 200 avisos | 275 µs | SQLite `:memory:`, usa `idx_alerts_active` |
| `getAlertsByChat()` | 53 µs | usa `idx_alerts_chat` |
| Memoria del bot aislado | heap 16,9 MB · RSS 104 MB | mismo script |
| Tráfico del bot en producción (PID 4713) | 12,6 KB/min entrantes · 2,5 KB/min salientes (incluye Telegram) | `nettop` durante 60 s; a ~2 KB por respuesta, del orden de 6 respuestas de Dbus/min con la caché actual |
| Cota de peticiones del refresco (H1) | hasta 160/min (80 paradas × 2) ≈ 230 000/día, con o sin usuarios | `index.ts:47-48`, `:105`; `config.ts:18` |
| Retraso máximo de un aviso (H3) | ~90 s (cron de 60 s + caché de 30 s) | cota por código, no medida |

Conclusión: los comandos del bot cuestan lo que tarda Dbus (~170-250 ms) y nada
más; CPU, SQLite y memoria no son problema. El riesgo es el volumen de peticiones
que el bot hace sin que nadie las pida (H1).

**Android**

| Qué | Cifra | Cómo |
|---|---|---|
| Catálogo empaquetado | 146 244 B · 548 paradas · 39 líneas | `assets/catalog.json` del APK |
| Parseo del catálogo | 31,2 ms en frío · 1,55 ms en caliente (media de 50) | test JVM temporal (no commiteado) en el Mac; en un móvil será varias veces más lento. Va en `Dispatchers.IO` y no bloquea el arranque (`BusDonostiaApp.kt:61-63`) |
| APK debug | 21 074 357 B (20,1 MiB); 15 `.dex` suman 66,3 MB sin comprimir (debug, sin R8) | `outputs/apk/debug/app-debug.apk` |
| Sondeo | UI visible: 1 petición/parada cada 30 s. Motor: 30 s / 60 s / 120 s según el hueco (≤ 7 / 8–14 / ≥ 15 min) | código (`AlertsShared.kt:45-52`, `AlertEngine.kt:29-42`) |
| Arranque en frío, recomposiciones, APK release | **no medido** | ver abajo |

No se midió en emulador ni se ensambló `release`: el equipo tiene 8 GB y, con la
otra sesión compilando a la vez, el primer `./gradlew check assembleDebug` tardó
38 min 38 s con ~8 GB de swap. Levantar además el AVD (≈ 10 GB entre Gradle y
emulador) no era viable. Queda como pendiente con comandos concretos:

```bash
# Arranque en frío (5 repeticiones), con el AVD dbus36 arrancado y el APK instalado
for i in 1 2 3 4 5; do adb shell am force-stop eus.busdonostia.app; \
  adb shell am start -W -n eus.busdonostia.app/.MainActivity | grep TotalTime; done
# Tamaño del APK release (R8) antes y después de isShrinkResources
./gradlew assembleRelease && ls -l app/build/outputs/apk/release/
```

### ¿Debe el bot adoptar el sondeo adaptativo?

Sí, pero no en el scheduler sino en lo que pide a Dbus. Hoy la carga del bot no
la pone el scheduler (lee caché) sino el refresco de 30 s sobre todas las paradas
vistas (H1). Propuesta P1 + P2:

1. El refresco solo cubre (a) paradas con aviso activo y (b) paradas consultadas
   por un usuario en los últimos 2 min. Las demás salen de la caché por TTL.
2. Para (a), la cadencia por parada la da la misma regla que Android:
   `pollDelayMs(gap)` con `gap = minutos − antelación` (≤ 7 → 30 s, 8–14 → 60 s,
   ≥ 15 → 120 s, sin dato → 60 s), tomando el aviso más urgente de la parada.
3. Tras refrescar una parada con avisos, se evalúan sus avisos en ese momento
   (sin esperar al cron) y con `NOTIFY_EARLY_MS = 30 s`.
4. La regla se añade a `@dbus/core` como funciones nuevas (`pollDelayMs`,
   `nextPollDelayMs`), sin tocar las existentes, con un fixture JSON de casos
   que ejecuten vitest y JUnit (cierra parte de H6).

Efecto esperado: con 0 avisos y nadie usando el bot, 0 peticiones/min (hoy
hasta 160). Con un aviso a 20 min, 1 petición cada 2 min en vez de 2/min.
Es un cambio de comportamiento del bot en producción: necesita reinicio (regla 9)
y visto bueno de Iker.

## Arreglos aplicados (fase 3)

Solo cambios sin efecto en el comportamiento, uno por commit, con la
verificación antes y después.

| Commit | Qué | Hallazgo | Antes | Después |
|---|---|---|---|---|
| C1 `014471b` | `test(bot)`: el test de integración acepta `tipo` 0, 1 y 2. Solo cambia el test: el código ya trataba `tipo≠1` como horario. | H2 | `integration.test.ts`: 14/15 · bot 85/86 (rojo) | 15/15 · bot 86/86 (verde) |
| C2 `266c2f3` | `test(contracts)`: fixture real `tiemposParada-2-datos-tipo0.json` (capturado con `curl`, sin editar) y dos tests de paridad que fijan el comportamiento: `packages/core/tests/tipo0.test.ts` y `StopFeedParserTest.tipo0EsHorario`. Aditivo en `packages/**`. | H2, H6 | core 14 · Android 94 por variante | core 15 · Android 95 por variante (en verde a la primera: documentan lo que ya pasaba) |
| C3 `7d3d556` | `refactor(android)`: `feedView` pasa a `data/live/LiveFeeds.kt` y `fetchStops` a miembro de `AppContainer`. El motor ya no importa nada de `ui/` (`grep "import eus.busdonostia.app.ui" engine/` vacío). Solo se mueven funciones. | H4 | 2 imports `engine → ui` · 95 tests · lint 0/29 | 0 imports · 95 tests · lint 0/29 |

Ninguno toca `apps/bot/src`: **el bot de producción no necesita reinicio por
esta rama.** Si más adelante se aplica P1/P2, entonces sí (regla 9): commit +
push → `kill -TERM <PID>` (hoy 4713) → `cd apps/bot && pnpm build` →
`BOT_TOKEN=… npm run start` con el token por entorno → comprobar en el log
«Dbus Telegram Bot started» y que el lock (`$TMPDIR/dbus-alarm-bot.lock`)
tiene el PID nuevo.

## Propuestas priorizadas (no implementadas)

| Prio | Propuesta | Cierra | Esf. | Riesgo | Quién decide |
|---|---|---|---|---|---|
| P1 | Refresco del bot solo de paradas con aviso activo + paradas vistas en los últimos 2 min; quitar la precarga permanente de la parada `2` (`handlers.ts:119`); reinsertar en la caché al refrescar (`delete` + `set`) para que el orden sea por uso. La parte de `@dbus/core` debe ser aditiva (opción nueva `maxIdleMs` en `createDbusClient`, sin cambiar el comportamiento por defecto). | H1 | M | medio (latencia de la primera consulta de una parada fría: ~170 ms p50 medidos) | Iker (cambia producción; regla 9) |
| P2 | Sondeo adaptativo del bot (ver sección anterior): `pollDelayMs`/`nextPollDelayMs` nuevos en `@dbus/core`, evaluar avisos tras refrescar su parada y `NOTIFY_EARLY_MS = 30 s`. | H3, H6 | M | medio | Iker |
| P3 | Android: `Log.w` con tag en los `catch` del motor, `DbusClient` y `ConfigRepository`; en el motor, contar un fallo inesperado como «problema» para que actúe `NO_SERVICE_STOP_MS`. Notificar **antes** de borrar el aviso, o no borrarlo si `areNotificationsEnabled()` es `false`. | H5, H20 | S | bajo | lane android |
| P4 | Paridad real: que `packages/core/tests` lean los 7 fixtures, y un fixture JSON de casos de dominio (`remainingMinutes`, frescura, matching con destino y preferencia GPS, haversine) que ejecuten vitest y JUnit. Si el bot no usa `computeFreshness`, documentar que esa paridad no aplica. | H6 | M | bajo | lanes bot + android |
| P5 | Alinear timeouts: `callTimeout(8 s)` en `AppContainer.http` o cambiar la spec a 15 s. Recomendado 8 s: con el motor en segundo plano, un timeout largo retrasa todas las paradas de la vuelta. | H7 | S | bajo | Iker / lane android |
| P6 | Firma de release (Sprint 5), sin keystore en el repo: `signingConfigs.release` que lea `storeFile`, `storePassword`, `keyAlias` y `keyPassword` de `~/.gradle/gradle.properties` o de variables de entorno, con fallo explícito si faltan al ensamblar `release`. Keystore fuera del repo (ya ignorado: `*.jks`, `*.keystore`) y con copia cifrada; activar **Play App Signing** (Google guarda la clave de firma, nosotros solo la de subida). Añadir `isShrinkResources = true` y medir el APK release antes/después. | H8 | S | bajo | Iker (crea y custodia la clave) |
| P7 | Sacar de `AppContainer` un `LiveRepository` (`fetchStop`, `fetchStops`, `liveFeeds`, registro de hora y colores) y un `MonitoringStarter` (el observador de `alerts`). `AppContainer` queda como cableado. | H9 | M | medio | lane android |
| P8 | Recomposición y lógica en UI: `items(rows, key = …)` con clave única (línea + destino + `minutesAtFetch` + índice), derivar el estado de A05 con `nowMs` redondeado al minuto que cambia, y mover a funciones puras de `domain/` (con test) `nextAlertSourceText`, `feedHint`, la dedup de destinos y el filtrado. Deduplicar en `fetchStop` las peticiones en vuelo por parada (como `client.ts:147`). Medir antes con Layout Inspector o un Macrobenchmark en un equipo con memoria suficiente. | H10, H11, H16 | M | bajo | lane android |
| P9 | Bot: sacar de `render.ts` a funciones puras/servicio (`alert-service.ts`) la creación de avisos y favoritos con su dedup; `render.ts` solo pinta. Mover `startAlertScheduler()` y la precarga de `handlers.ts:118-119` a `index.ts`. Respeta la decisión de no dividir módulos: es extraer, no reorganizar. | H12 | M | bajo | lane bot |
| P10 | Secretos: `chmod 600 .env` (raíz) y del log del bot; dejar de loguear `chat_id` o truncarlo; opcional, leer `BOT_TOKEN` del Llavero de macOS al arrancar (`security find-generic-password -w …`) en vez de `.env`. Config remota: lista de hosts HTTPS permitidos para `apiBaseUrl` (p. ej. solo `app.dbus.eus` y sus variantes) y lista blanca de cabeceras. **Rotar el token (R6)**, pendiente desde antes de esta auditoría. | H13, H19, H21 | S | bajo | Iker |
| P11 | Concurrencia: en `pidAlive`, tratar `EPERM` como vivo; en `acquireInstanceLock`, renombrar atómicamente (`rename` de un fichero temporal) en vez de `unlink` + `wx`. Android: volver a comprobar `isWatching` sobre la BD justo después de `running = false` y, si hay algo, seguir en el bucle. | H14, H15 | S | bajo | lanes bot + android |
| P12 | Limpieza: un solo catálogo (que `packages/contracts/data/catalog.json` se genere desde `apps/bot/data/` o se borre si nadie lo usa fuera de su test); quitar `zod` de `@dbus/core`. Ojo: tocar `packages/**` no aditivamente requiere acuerdo. | H17, H18 | S | bajo | Iker |

## Cambios de documentación propuestos

No se tocan aquí (reparto con la sesión de documentación). Para quien lleve los docs:

| Documento | Cambio |
|---|---|
| `ESTADO.md` | El bot de producción ya no es el PID `80402`: hoy corre el `4713` (arrancado el 2026-11-22 13:35, cwd `apps/bot`, log en `$TMPDIR/dbus-bot.log`). |
| `ESTADO.md` | Añadir a pendientes: reinicio del bot tras integrar esta rama (solo si se aplica P1/P2; los commits de esta rama no cambian `apps/bot/src`). Añadir P1, P2 y P10 como decisiones para Iker. |
| `AGENTS.md` § Reglas del bot | Regla 7: «preferencia GPS (`tipo=1`)»; añadir que `tipo` 0 y 2 son sin GPS (Dbus da `0` desde el 2026-11-22). |
| `docs/07-android-mvp/docs/02-domain-contracts.md` §1 | `ArrivalSource`: `tipo=1 GPS · cualquier otro (0, 2) horario`. |
| `packages/contracts/fixtures/README.md` | Nueva fila: `tiemposParada-2-datos-tipo0.json` · 2 Urbieta 12 · `DATOS` · 5 tiempos mezclando `tipo=0` y `tipo=1` · 2026-11-22 18:37. |
| `docs/07-android-mvp/docs/04-android-platform.md` §0 | Timeout: dice 8 s y el código usa 10 s de conexión y 15 s de llamada (H7). Corregir el que pierda en P5. |
| `docs/03-architecture/adrs/009-android-nativo-mvp.md` §4 | La lista de lógica «duplicada a propósito» incluye `computeAlertStatus` y `computeFreshness`, que no existen en `@dbus/core`. Precisar que la paridad probada hoy es la del parseo de `tiemposParada` (H6). |
| `docs/07-android-mvp/docs/02-domain-contracts.md` §4 | «el fixture de paridad la cubre» (haversine): no hay fixture compartido; cada lado tiene su test. |
| `docs/07-android-mvp/SPRINTS.md` / prompt Sprint 5 | Incluir P6 (firma con Play App Signing, credenciales fuera del repo) y P10 (lista de hosts de la config remota). La auditoría de secretos del historial ya está hecha (H13): 0 hallazgos. |
