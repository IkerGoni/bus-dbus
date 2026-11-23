# Auditoría adversarial · Sprint 5 y MVP Android v1

**Fecha:** 2026-11-23 · **Auditor:** Claude Opus 5.5 (sesión distinta de la que
implementó el Sprint 5) · **Modo:** solo lectura. Este informe y una fila en
`docs/README.md` son los únicos cambios.
**Prompt:** [`07-android-mvp/prompts/AUDIT-SPRINT-5.md`](../07-android-mvp/prompts/AUDIT-SPRINT-5.md)

Estado del árbol auditado:

```text
$ git status --short
 M ESTADO.md
 M docs/07-android-mvp/SPRINTS.md
 M docs/README.md
?? docs/07-android-mvp/auditoria-secretos.md
?? docs/07-android-mvp/play/
?? docs/07-android-mvp/prompts/AUDIT-SPRINT-5.md
?? gradle-build.exit
?? state/kanban/... (4 ficheros -shm/-wal)

$ git log --oneline -3
11b7b19 fix(android): nombre de parada desconocido nunca muestra el codigo de poste
6478b67 docs: sincroniza design-tokens.json y DESIGN.md con tokens de F5/F8/F9
9cbf33b refactor(android): StopDetail como LazyListScope único; tokens de componente completos
```

El Sprint 5 no cambió nada en `apps/android/**`: el diff sin commitear es solo
de docs. No hay rama `android/sprint-5` (`git branch -a`: solo `main`).

Entorno: JDK 17, AVD `dbus36` (Android 16, API 36) arrancado con
`-no-snapshot -gpu swiftshader_indirect`. Apareció una vez «System UI isn't
responding»; se cerró con *Wait* y después se pudo interactuar con normalidad.

---

## 1. Veredicto

**NO SHIP**. El filtro de destino de los avisos deja que un bus con otro
destino consuma un aviso de un solo uso (reproducido 2 veces en el emulador con
la API real). Además, el historial git contiene el token real del bot, el mismo
que hay hoy en `.env`, y la ficha en euskera es texto inventado.

## 2. Tabla de afirmaciones

| # | Afirmación | Veredicto | Evidencia |
|---|---|---|---|
| 1 | «Sprint 5 cerrado», «hecho» en `SPRINTS.md`, DoD cumplido | **Parcial / sin evidencia** | La orden del DoD sin `-P…unsigned` falla (falta keystore, es lo esperado). Con `unsigned=true` da `BUILD SUCCESSFUL`. No hay capturas `sprint-5`, logs ni rama. El paquete instalado en el AVD al empezar era `DEBUGGABLE` y tenía `POST_NOTIFICATIONS: granted=false`. El entregable 4 del prompt (nombre e icono) desaparece de la lista de «Resultado». El flujo release sí funciona: lo reproduje yo (§5). Ver A5-04. |
| 2 | «127 tests JVM por variante, 0 fallos; lint 0 errores» | **Falsa** | XML de `build/test-results/` tras `--rerun`: **debug 144** tests, **release 138** (26 y 23 clases; `AlertBadgeTest`, `AnimatedMinutesTextTest` y `LiveSignalDotTest` solo están en `testDebug`). 0 fallos. Lint 0 errores (`lintDebug` ejecutado, `check` verde). `AGENTS.md` y `apps/android/README.md` dicen 94. Ver A5-05. |
| 3 | «R8 no rompió kotlinx.serialization, Room ni OkHttp» | **Confirmada en ejecución** (no por los docs) | Firmé una copia en `/tmp/dbus-audit/` con el keystore debug y la instalé. Recorrido completo en release: Cercanas (catálogo `@Serializable`), A05 en vivo (OkHttp + parser), guardar aviso (Room), FGS en segundo plano, notificación real, aviso borrado de A04. Ningún `FATAL` ni aviso de `ConfigRepository` en logcat. Matiz en A5-16. |
| 4 | «Firma: solo `~/.gradle/gradle.properties` o `-P`; si falta, falla con mensaje claro» | **Parcial** | El código falla bien: `./gradlew assembleRelease` → `Falta la firma de release: dbus.release.storeFile, …storePassword, …keyAlias, …keyPassword…`. `BuildConfig` solo lleva `REMOTE_CONFIG_URL=""`, y `*.jks` y `*.keystore` están ignorados. Pero los nombres de propiedad que citan `SPRINTS.md` y `ESTADO.md` (`dbus.keystore`, `-Pdbus.release.keystore/password/keyalias/keypassword`) no existen. Además, el código también lee variables `DBUS_RELEASE_*`. Ver A5-06. |
| 5 | «Auditoría de secretos: 124 commits, 0 coincidencias; no hay que rotar» | **Falsa** | `git rev-list --all --count` = 124, pero con `--reflog` son 125. El blob borrado `bot-code.zip` (commit `bef1873`) contiene un token de Telegram real en `bot/src/config.ts:12`. Es el **mismo** token que hay hoy en `.env`, y está en `origin/main`. `git grep` no mira dentro de los zip. La línea «Mensajes de commit: 0» también es falsa: el grep del propio informe da 35. Ver A5-01. |
| 6 | «Notificación y "Vigilando …" probadas en euskera en el AVD» | **Sin evidencia original; confirmada al reproducirla** | No hay capturas ni logs, y la cita del informe («Vigilando L16 en Pio Baroja 15») está en castellano. Reproducido con `set-app-locales … eu`. FGS: `L36 zaintzen, Askatasuna  21 geltokian` / `Dbus 30 s-ro kontsultatzen du autobusa iritsi arte.` Aviso: `L36 → Aldakonea, 10 min barru` / `Askatasuna  21 · Ordutegia`. |
| 7 | «Ficha de Play es/eu lista para revisar» | **Falsa** | El euskera es inventado: lleva caracteres coreanos y árabes («웃음을», «رت»). La descripción corta tiene 182 caracteres (el límite de Play es 80). Hay una errata en el permiso (`ACCCESS_FINE_LOCATION`). La versión dice 1.0.0 y el APK es 1.1.0. El nombre dice «(no oficial)» y el APK no. La declaración de FGS no trae vídeo y la duración que da no es la real. Ver A5-03, A5-07 y A5-09. |
| 8 | «Nombre e icono no oficiales» | **Parcial** | `aapt2 dump badging`: `application-label-es:'Bus Donostia'`, `application-label-eu:'Bus Donostia'`, sin «no oficial». El icono es adaptativo, pero es el autobús genérico de Material Symbols sobre `#005BB5` (el propio fichero dice «provisional»). No hay rastro de `com.indra.dbus`. En A07 solo aparece una línea de créditos ambigua («Tiempos: servicio de la app de Dbus (no oficial, sin afiliación)»). Ver A5-08. |

## 3. Hallazgos

### P0

**A5-01 · El token real del bot de Telegram está en el historial y la auditoría de secretos dice que no**
- **Dónde:** blob `da8522ef…` = `bot-code.zip`, añadido en `bef1873` (2026-11-22 05:04) y borrado en `482b53f`. Dentro, `bot/src/config.ts:12` (`botToken: '9133****:AAFc***'`, censurado). Informe que lo niega: `docs/07-android-mvp/auditoria-secretos.md`.
- **Evidencia:**
  ```text
  $ git log --all --diff-filter=D --name-only --format= | grep zip
  bot-code.zip
  $ git cat-file -p da8522e… > z.zip && unzip … && grep -E '[0-9]{8,10}:[A-Za-z0-9_-]{35}'
  bot/src/config.ts:12:  botToken: '9133****:AAFc***REDACTED',
  $ grep -qF "$Z" .env && echo ".env contains zip token"
  .env contains zip token
  $ git branch -r --contains bef1873
  origin/main
  $ gh repo view --json visibility → "PRIVATE"
  ```
  El patrón ampliado, pasado sobre `git log --all --reflog -p --text` (20 MB), da 0 en todo lo demás: `AIza…`, `-----BEGIN … PRIVATE KEY-----`, `AKIA…`, `ghp_…`, `xox…`, `sk-…` y `api.telegram.org/bot<n>`. Las 15 coincidencias de `BOT_TOKEN=` son `vitest_test_token` y plantillas. Nunca se trackeó un `.jks`, `.keystore`, `.p12` ni `.pem`. `.env.example` sí se trackeó, y es solo plantilla.
- **Por qué importa:** el token del bot en producción está en GitHub (privado) y en cada clon. La regla 3 de `AGENTS.md` lo prohíbe. El informe «No hay que rotar nada» es falso, y el repo no puede hacerse público tal como está.
- **Arreglo mínimo:** Iker rota el token en BotFather y lo cambia en `.env`. Después decide si purga `bot-code.zip` del historial (`git filter-repo`, reescritura y force-push: irreversible). Hay que corregir `auditoria-secretos.md` y meter en el método la inspección de blobs binarios o comprimidos (`gitleaks`/`trufflehog`, no instalados en esta máquina).

**A5-02 · El filtro de destino acepta un destino que contiene a otro: un bus con otro destino dispara y borra el aviso**
- **Dónde:** `apps/android/app/src/main/kotlin/eus/busdonostia/app/domain/Arrivals.kt:25` (`a == b || a.contains(b) || b.contains(a)`). Lo usan `findMatchingArrival` (`:52`) y `nextArrivalFor` (`AlertList.kt:39`). La misma lógica está en el bot: `packages/core/src/arrival.ts:117`.
- **Evidencia (APK release, API real, Askatasuna 21, línea 13):**
  ```text
  A06: destino seleccionado = «Buenavista-Altza» (checked [42,1021][426,1147]), antelación 15'
  → al guardar, notificación inmediata:
    android.title=String (L13 → Altza, 1 min barru)
    android.text=String (Askatasuna  21 · Ordutegia)
  Repetido con antelación 2' → «L13 → Altza, 2 min barru». A04 queda vacío: el aviso se ha borrado.
  ```
  En la línea 13 de esa parada hay estos destinos reales: «Altza», «Buenavista - Altza», «Bidebieta - Altza» y «Bidebieta - Buenavista - Altza». `"buenavista-altza".contains("altza")` → coincide. Ningún test cubre el caso: `grep destinationsMatch app/src/test` → 0. Los tests de `DomainTest.kt:107` solo prueban mayúsculas y espacios.
- **Por qué importa:** rompe el «filtro de destino» de las reglas 7 del bot y de la app. Como el aviso es de un solo uso, el bus equivocado lo consume y el usuario pierde el que quería.
- **Arreglo mínimo:** comparar con igualdad tras normalizar: minúsculas, espacios colapsados y espacios quitados alrededor de `-` y `/`. Nada de contención. Hacen falta tests con los 4 destinos reales de la 13 en el 103, en Kotlin y en `@dbus/core` (paridad), y el bot hay que reiniciarlo (regla 9).

### P1

**A5-03 · Ficha de Play en euskera inventada**
- **Dónde:** `docs/07-android-mvp/play/ficha-play-eu.md` (entero) y el bloque «Descripción larga (eu)» de `ficha-play-es.md`.
- **Evidencia:** «Android ekintza 웃음을 izango dut: Behind process-ak galdera-Ne-Na egiteko, algorithm-eko zerbitzura…», «abisua bat sortzeakرتxatzean», «Prezioa: Komunak», «abisua bat eventosu, 7 egunera caduka», «katta realetan», «Datuak ajalgia bakarra». En cambio, las cadenas de la app en `values-eu/` son euskera correcto (lo verifiqué en el AVD).
- **Por qué importa:** si se sube tal cual a Play, la ficha queda ilegible para el público euskaldun, que es justo para quien se hace la versión en euskera. Y el Sprint 5 dio la ficha por «lista para revisar».
- **Arreglo mínimo:** reescribirla desde las `values-eu` que ya existen, o que la revise una persona euskaldun. Mientras tanto, marcar el fichero como «NO USAR».

**A5-04 · Sprint 5 marcado «hecho» sin evidencia del DoD y con un entregable que desaparece**
- **Dónde:** `docs/07-android-mvp/SPRINTS.md` (diff, fila 5 y «Resultado»), `ESTADO.md`.
- **Evidencia:** `git diff --stat` → solo `ESTADO.md`, `SPRINTS.md` y `docs/README.md`. No existe `docs/07-android-mvp/capturas/sprint-5*`. Al empezar, `dumpsys package` daba `flags=[ DEBUGGABLE …]` y `POST_NOTIFICATIONS: granted=false`: lo último instalado era debug, y con ese permiso no se podía ver ninguna notificación. El prompt pedía 6 entregables y el «4. Nombre e icono» se sustituye en el resultado por «6. Docs de estado».
- **Por qué importa:** el estado «hecho» se basa en afirmaciones que nadie reprodujo, y el nombre «no oficial» sigue sin estar (A5-08).
- **Arreglo mínimo:** dejar el Sprint 5 como «parcial» hasta cerrar A5-01, A5-02, A5-03 y A5-08, y adjuntar las evidencias del flujo release (este informe sirve).

**A5-05 · Recuento de tests falso y distinto en cada doc**
- **Dónde:** `ESTADO.md` y `SPRINTS.md` (127), `AGENTS.md` § Verificación (94), `apps/android/README.md:28` (94).
- **Evidencia:** `./gradlew :app:testDebugUnitTest --rerun :app:testReleaseUnitTest --rerun` → `BUILD SUCCESSFUL in 56s`. XML: `testDebugUnitTest tests=144 failures=0 errors=0`; `testReleaseUnitTest tests=138 failures=0 errors=0`.
- **Arreglo mínimo:** poner «144 (debug) / 138 (release)» en los cuatro docs, o mejor no escribir la cifra y remitir a los XML.

**A5-06 · Los docs de firma dan nombres de propiedad que no existen**
- **Dónde:** `SPRINTS.md` «Resultado» punto 2 y `ESTADO.md`. El código está en `apps/android/app/build.gradle.kts:436-441` y `:541-549`.
- **Evidencia:** los docs hablan de `dbus.keystore` y `-Pdbus.release.keystore/password/keyalias/keypassword`. El código lee `dbus.release.{storeFile,storePassword,keyAlias,keyPassword}` o `DBUS_RELEASE_*`. `apps/android/README.md:37-40` sí tiene los nombres correctos.
- **Por qué importa:** si Iker sigue `SPRINTS.md` al crear el keystore, el build falla.
- **Arreglo mínimo:** copiar los nombres del README a `SPRINTS.md` y `ESTADO.md`.

**A5-07 · La ficha en castellano no cumple los límites de Play y contradice al APK**
- **Dónde:** `docs/07-android-mvp/play/ficha-play-es.md`.
- **Evidencia:**
  - La descripción corta tiene `wc -m` = **182** (Play admite 80).
  - En «Ubicación» pone `ACCCESS_FINE_LOCATION`. El manifiesto tiene `ACCESS_FINE_LOCATION`.
  - Pone «Versión 1.0.0 (versiónCode 2)», pero `aapt2` da `versionName="1.1.0"`, `versionCode=2`.
  - El nombre «Bus Donostia (no oficial)» no coincide con el label `Bus Donostia`.
  - La categoría «Mapas y navegación / Transporte» no existe en Play: hay que elegir una.
  - La declaración de FGS no trae vídeo. Play Console pide un vídeo por cada función con FGS ([Play Console Help 13392821](https://support.google.com/googleplay/android-developer/answer/13392821)).
  - «¿Cuánto se ejecuta?» calla que un aviso puede vigilar hasta 7 días (A5-09).
  - La política de privacidad es solo un resumen, sin URL alojada.
- **Arreglo mínimo:** descripción corta de 80 caracteres o menos, quitar la errata, alinear versión y nombre con el APK, grabar el vídeo del flujo 🔔 → «Vigilando» → aviso, y alojar la política (depende del hosting de Iker).

### P2

**A5-08 · Nombre sin «no oficial» y aviso de no oficialidad ambiguo**
- **Dónde:** `res/values*/strings.xml` `app_name`; A07 `settings_credit_times` (`res/values/strings_settings.xml:29`).
- **Evidencia:** `aapt2 dump badging` → `application-label-es:'Bus Donostia'`, `application-label-eu:'Bus Donostia'`. En el volcado de A07 (eu): `'Denborak: Dbusen aplikazioaren zerbitzua (ez-ofiziala, loturarik gabe)'`. Se puede leer como que el servicio de Dbus es el no oficial. No hay ninguna frase clara del tipo «Esta app no es de Dbus».
- **Por qué importa:** la política de suplantación de Play. Usar «Dbus» y «Donostia» sin un descargo claro es un riesgo real de rechazo.
- **Arreglo mínimo:** que `app_name` sea «Bus Donostia (no oficial)» o lo que apruebe Iker, y una línea propia en A07: «App no oficial, sin relación con Dbus ni con el Ayuntamiento».

**A5-09 · El FGS `dataSync` puede vigilar hasta 7 días sondeando cada 60 s, y sin retomar tras reiniciar**
- **Dónde:** `engine/AlertMonitorService.kt:82` (`START_NOT_STICKY`), `:85-91` (`onTimeout`, comentario «Con franjas ≤ 3 h no debería pasar»), `domain/AlertEngine.kt:45-47` (sin bus de la línea → `base × 2` = 60 s), `config.json` `expirationDays: 7`. No hay `BOOT_COMPLETED` (`grep` → 0).
- **Evidencia:** nada en el código limita la vigilancia a 3 h. Un aviso de una línea que no aparece en la respuesta (sin servicio de noche, destino raro) no cuenta como `trouble` porque la parada sí responde. El servicio sigue hasta la caducidad de 7 días o hasta el `onTimeout` de 6 h de Android 15+. Tras reiniciar, el aviso solo vuelve si el usuario abre la app (`AlertMonitoring.kt:45`).
- **Por qué importa:** gasto de batería y revisión de Play. La ficha dice que solo corre «mientras haya al menos un aviso activo», pero no dice cuánto puede durar eso.
- **Arreglo mínimo:** una ventana máxima de vigilancia en `config.json` (p. ej. parar a las N h sin un bus que coincida y notificarlo), y documentar en la ficha el reinicio y el tope.

**A5-10 · Valores operativos de nivel 1 escritos en el código y en las cadenas**
- **Dónde:**
  - `domain/AlertEngine.kt:10` `NO_SERVICE_STOP_MS = 60 * 60_000L` y `:13` `FETCH_SLACK_MS = 5_000L`.
  - `data/location/LocationProvider.kt:21` (120 s) y `:40` (`timeoutMs = 10_000L`).
  - `data/config/ConfigRepository.kt:145` `REFRESH_INTERVAL_MS` (constante muerta, no se usa).
  - Cadenas con valores de config copiados a mano: `strings.xml:51` «80 m/min» (sale de `walkingSpeedKmh` 4,8), `:61` «últimas 10 paradas», `:164` «entre 1 y 30 minutos» (`min/maxMinutesAhead`), `strings_engine.xml:18` «60 min».
  - `config.json` mantiene `recurringWakeupBeforeMin`, `recurringExpiryAfterMin` y `recurringWindowToleranceMin` aunque los avisos sean de un solo uso.
- **Evidencia:** `grep -rn "NO_SERVICE_STOP_MS\|FETCH_SLACK_MS\|MAX_LAST_KNOWN_AGE_NS\|REFRESH_INTERVAL_MS" app/src/main/kotlin` (salida en la sesión).
- **Por qué importa:** si se cambia la config remota, la UI muestra datos falsos (p. ej. «1 y 30» con `maxMinutesAhead` distinto), y se incumple el anexo §1 nivel 1.
- **Arreglo mínimo:** llevar esas constantes a `PollingConfig`, `AlertsConfig` y `GeoConfig`, pasar los números a las cadenas como `%1$d`, y quitar las claves `recurring*`.

**A5-11 · 165 literales `dp` en las pantallas de `ui/` (nivel 2)**
- **Dónde:** `HomeScreen.kt` (32), `AlertEditScreen.kt` (30), `StopsScreen.kt` (27), `LinesScreen.kt` (25), `AlertsScreen.kt` (20), etc.
- **Evidencia:** `grep -rnoE "\b([3-9]|[1-9][0-9]+)\.dp\b" ui | grep -v ui/theme | wc -l` → 165. Desglose: `16.dp`×45, `8.dp`×29, `48.dp`×24, `12.dp`×19, `4.dp`×15, `24.dp`×12… Por ejemplo, `HomeScreen.kt:147` `PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp)` o `LinesScreen.kt:227` `PaddingValues(top = 8.dp, bottom = 16.dp)`.
- **Por qué importa:** el anexo §1 nivel 2 prohíbe los espaciados estándar inline, y ya existe `DesignTokens` generado en build. Va contra lo que dice `ESTADO.md` («tokens en 3 niveles»).
- **Arreglo mínimo:** ticket de barrido explícito para cambiarlos por `DesignTokens.spacing*` y sumar un test o lint que cuente los literales.

**A5-12 · El deep link `dbus://stop/…` pinta texto externo y acepta paradas que no existen**
- **Dónde:** `AndroidManifest.xml:43-50` (`BROWSABLE`, exportado) y `ui/AppShell.kt:273-279`.
- **Evidencia (AVD, release):**
  ```text
  am start -d "dbus://stop/103%26idioma%3Dxx%27%22%3Cscript%3E?highlightLine=13%27%3B--"
  → 'Gorde 13';-- linea geltoki honetan'   (texto de la URL en un botón)
  am start -d "dbus://stop/99999999"
  → 'Geltokia' + 'Errorea Dbus kontsultatzean' + 'Egiaztatu zure konexioa…' y ★/«Sortu abisua» activos
  am start -d "dbus://stop/<20 000 × '9'>" → sin crash (logcat -b crash: 0)
  ```
  En la URL hacia Dbus no hay inyección: `addQueryParameter` codifica (`DbusClient.kt:33`).
- **Por qué importa:** cualquier web puede abrir la app con un texto suyo en un botón, o hacer que el usuario guarde un favorito o un aviso de una parada inexistente. Y el mensaje «comprueba tu conexión» es falso en ese caso.
- **Arreglo mínimo:** validar `stopId` contra el catálogo (si no existe, mostrar «Parada no encontrada») y `highlightLine` contra las líneas de esa parada; si no está, ignorarlo.

**A5-13 · I/O en el hilo principal al crear el contenedor (sigue abierto)**
- **Dónde:** `BusDonostiaApp.kt:54` (`File(app.filesDir, …)`) y `:83` (`app.getSharedPreferences("settings", …)`), los dos en el init del contenedor en `Application.onCreate`.
- **Por qué importa:** anexo §3.1. `ESTADO.md` ya lo apuntaba, pero sigue sin corregir.
- **Arreglo mínimo:** inicialización perezosa en `Dispatchers.IO` o DataStore.

### P3

- **A5-14 · La búsqueda anima a escribir códigos de poste.** `strings.xml:42` «Código o calle», `:57` y `:114` «Prueba con el código de la marquesina». El anexo §2 solo admite coincidir con el código en silencio. Arreglo: «Parada o calle».
- **A5-15 · Atribución del catálogo sin traducir y con nombre técnico.** `ui/settings/SettingsScreen.kt:180` pinta `catalog.attribution` tal cual. En la UI en euskera aparece `'Datos de paradas: Dbus Donostia (API pública SSIIMovilWSv2)'`. Arreglo: un string `es`/`eu` propio, sin el nombre del endpoint.
- **A5-16 · Un fallo de R8 al leer la config no se notaría.** Si falla el parseo, `ConfigRepository.initializeFromDisk` (`:66-70`) vuelve a `bundled`, y los valores por defecto de Kotlin (`domain/ApiConfig.kt:12-37`) son idénticos a `config.json`. En el AVD no hubo aviso en logcat, pero ningún test ni comprobación en ejecución lo distingue. Las reglas propias de serialización de `proguard-rules.pro:3-37` repiten las que ya trae la librería, y `-keep @Entity class * { *; }` desactiva el shrink de las entidades. Arreglo: un test que cargue `config.json` y compare con un valor distinto del de por defecto, o un `schemaVersion` obligatorio sin valor por defecto.
- **A5-17 · Docs incoherentes entre sí.**
  - `AGENTS.md` dice «Sprint 5 pendiente, último merge `b8e08f8`, 94 tests».
  - `apps/android/README.md:5,28` dice «Sprint 5 pendiente» y «94».
  - `docs/README.md:5` está revisado contra `b8e08f8`, pero HEAD es `11b7b19`.
  - `ESTADO.md` da el Sprint 6 por pendiente, pero `03884b6 feat(android): Sprint 6 v1.1.0` y 11 commits más ya están en `main`, y `versionName` es `1.1.0`.
  - `ESTADO.md` da por abierto el «residual del label `geo:`», que ya está arreglado (`StopDetailScreen.kt:116` usa `stop_unnamed`).
  - `SPRINTS.md` no tiene fila del Sprint 6.
- **A5-18 · `keystore.properties` no está ignorado.** `git check-ignore apps/android/keystore.properties` → nada. Hoy no se usa, pero es el nombre habitual de ese fichero. Arreglo: añadirlo a `apps/android/.gitignore`.

### Sospechas no confirmadas

- **SILENT:** las notificaciones de llegada aparecen con `flags=AUTO_CANCEL|SILENT` cuando Android las agrupa sola (`AUTOGROUP_SUMMARY`). Con 2 o más avisos disparados, el segundo podría no sonar. Para confirmarlo hay que oírlo en un móvil real.
- **Tras el `onTimeout` de 6 h:** al volver a abrir la app se llama a `startForeground` de `dataSync`. Si Android no ha reiniciado el cupo, podría lanzar una excepción que `AlertMonitorService.start` no captura, porque el `catch` solo cubre `startForegroundService`. No lo reproduje (haría falta esperar 6 h).
- **Hora de llegada vieja en A05:** la línea 36 a Aldakonea mostró «12:40» con «1 min» hacia las 12:41. Podría ser un resto de frescura o redondeo. No lo investigué más.

## 4. No verificado

| Qué | Qué haría falta |
|---|---|
| APK firmado con el keystore de subida de Iker y AAB para Play | El keystore de Iker (no se crea aquí) |
| Si el token filtrado sigue vivo en Telegram | Que Iker llame a `getMe` o lo rote directamente; aquí no se usó el token |
| Doze, fabricantes que cortan procesos en segundo plano, ubicación aproximada y primera posición | Móvil real |
| Límite de 6 h de `dataSync` y comportamiento tras reiniciar | Móvil o AVD con Android 15+ durante más de 6 h, y un reinicio |
| Parecido del icono con el de `com.indra.dbus` | Ver el icono oficial (Play o APK); aquí solo se comprobó que es un glifo genérico de Material |
| Si Play acepta la declaración `dataSync` y lo que exige la pista interna (privacidad, Data Safety) | Play Console (fuera de alcance) |
| Config remota alojada | Hosting HTTPS que elija Iker. Hoy `REMOTE_CONFIG_URL=""`, y la lista de hosts permitidos existe (`data/config/ApiConfig.kt:78-86`) |

## 5. Lo que resistió el ataque

- **Flujo completo con R8.** Lo probé con la copia release firmada con la clave debug (`/tmp/dbus-audit/release-debugsigned.apk`, `apksigner verify` OK) en API 36:
  - Cercanas: «Askatasuna 21 … 152 m · 2 min».
  - A05 en vivo: «Zuzenean, 22 min».
  - 🔔 con antelación de 10' y la app en segundo plano: el FGS arrancó (`isForeground=true types=0x00000001`, `L36 zaintzen…`), saltó `L36 → Aldakonea, 10 min barru` y el servicio se paró solo (`AlertMonitorService` 0).
  - A04 quedó vacío («Ez duzu abisurik»).
  - Ningún `FATAL`.
- **Build y lint:** `check assembleDebug assembleRelease -Pdbus.release.unsigned=true` → `BUILD SUCCESSFUL`. Lint con `abortOnError`, y `HardcodedText`, `MissingTranslation` y `ExtraTranslation` como error.
- **Firma:** el mensaje de error es claro, no hay secretos en `BuildConfig` y nunca se trackeó un `.jks` ni un `.keystore`.
- **Secretos (fuera del zip):** 0 coincidencias con 10 patrones en todo el historial, incluido el commit que solo está en el reflog (`6fe9b89`). Tampoco hay restos de `.env` (solo `.env.example`).
- **Red y componentes:** `base-config cleartextTrafficPermitted="false"` con la única excepción de `app.dbus.eus` (sin subdominios). Exportados: `MainActivity`, que es necesario, y `ProfileInstallReceiver`, protegido con `android.permission.DUMP`. Nada más.
- **Config remota (P10):** lista de hosts permitidos, HTTPS obligatorio salvo para `app.dbus.eus` y validación de rutas (`ApiConfig.validate`).
- **Pureza KMP de `domain/`:** 0 imports `java.time`, `java.text`, `java.util.Locale` o `android.*` y 0 `String.format`.
- **Rendimiento en listas:** todos los `items` e `itemsIndexed` llevan `key` y `contentType`. `rememberInfiniteTransition` está condicionado a `isLive && !reducedMotion && !LocalInspectionMode.current` (`Motion.kt:223`). El alpha se lee dentro de `graphicsLayer`.
- **Motor:** los escalones (`config.json` y `PollingConfig`) dan 31→600 s, 15→240 s, 8→60 s, 0→30 s, y sin datos 30 × 2. Los tests cubren 7/8, 14/15 y 30/31 y `null` (`AlertEngineTest.kt:112-149`). No hay `Thread.sleep`, `currentTimeMillis` ni `delay(n)` en los tests. Los avisos son de un solo uso: se borran tras notificar y solo si se puede notificar (`AlertMonitorService.kt:180-181`).
- **Dedup de avisos** con índice único `(stopId, lineId)` en `AlertEntity.kt:15`.
- **`tipo`:** el parser marca LIVE solo con `tipo == 1.0` (`StopFeedParser.kt:47`). Hay test de `tipo=0` como horario y ninguno asume un conjunto cerrado de valores.
- **Paridad de cadenas:** 186/186 claves en `es`/`eu`, contando `plurals` y `string-array` con mi propio script. `StringsParityTest` solo cuenta `<string>`, pero hoy coincide igualmente.
- **Cero códigos de poste:** en los volcados de A01, A02, A04, A05, A06 y A07 (es/eu), en las notificaciones y en el FGS no hay `stopId` ni ids internos de línea (salvo el texto inyectado de A5-12). El label de `geo:` usa el nombre o `stop_unnamed`.
- **Euskera dentro de la app:** las pantallas, el FGS y la notificación se ven en euskera correcto.
