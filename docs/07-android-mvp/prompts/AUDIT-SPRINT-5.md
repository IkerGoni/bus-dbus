# Prompt — Auditoría adversarial · Sprint 5 y MVP Android v1

Pégalo en una sesión nueva en la raíz de `bus-dbus`. Usa otro modelo u otro
agente distinto del que implementó el Sprint 5.

---

Eres un auditor adversarial del MVP Android de bus-dbus. Tu trabajo es
**demostrar que la implementación y lo que se cuenta de ella son falsos o
incompletos**. Una afirmación se cuenta como cierta solo si tú mismo la
reproduces en esta sesión con una evidencia: ruta y línea, salida de un
comando o volcado del emulador. Lo que no puedas reproducir se queda como «no
verificado», nunca como «correcto». No te fíes de `ESTADO.md`, de
`SPRINTS.md`, de los mensajes de commit ni de comentarios del código: son las
afirmaciones que tienes que atacar.

**Modo: solo lectura.** No toques el código, el build ni los docs existentes.
Tu único entregable es el informe (ver «Salida»). Nada de commits, push,
ramas, Play Console, repo público ni elegir licencia.

## Lee primero, en este orden

`AGENTS.md` (incluido el anexo hasta el Sprint 8), `ESTADO.md`,
`docs/07-android-mvp/SPRINTS.md` (§ Sprint 5 y su «Resultado»),
`docs/07-android-mvp/prompts/SPRINT-5.md` (alcance y DoD),
`docs/07-android-mvp/docs/00-agent-guardrails.md`,
`docs/07-android-mvp/docs/02-domain-contracts.md`,
`docs/07-android-mvp/docs/04-android-platform.md`,
`docs/03-architecture/adrs/009-android-nativo-mvp.md` y
`docs/auditoria/2026-11-arquitectura.md` (sobre todo P6 y P10).

## Entorno

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"
cd apps/android
```

- Gradle con daemon. **Prohibido `--no-daemon`.** Lanza el build completo una
  sola vez y, para iterar, usa tareas concretas (`testDebugUnitTest --tests …`,
  `lintDebug`).
- AVD `dbus36`. Con `-qemu -machine virt` se ha visto «System UI isn't
  responding», que bloquea la interacción. Arranca con
  `emulator -avd dbus36 -no-snapshot -gpu swiftshader_indirect`. Si aun así no
  hay forma de interactuar, dilo en el informe y marca la prueba como «no
  verificada». No la des por buena.
- Para verificar la UI usa texto (`adb shell uiautomator dump` + `text=` /
  `content-desc=`), no capturas de pantalla.
- Al terminar: `adb emu kill` y `./gradlew --stop`.
- Los cambios del Sprint 5 pueden estar sin commitear. Audita el árbol de
  trabajo tal como está y apunta `git status --short` y `git log --oneline -3`
  al principio del informe.

## Afirmaciones a falsar

Coge cada afirmación, intenta tumbarla y clasifícala como **confirmada**,
**falsa**, **parcial** o **no verificable**, con su evidencia.

1. «Sprint 5 cerrado» y marcado como «hecho» en `SPRINTS.md`, comparado con su
   DoD: `check assembleDebug assembleRelease` en verde **y** el APK release
   instalado con el flujo de avisos funcionando.
2. «127 tests JVM por variante, 0 fallos; lint 0 errores». Cuenta los tests
   desde los XML de `build/test-results/`, no desde los docs. `AGENTS.md` dice
   94: señala la discrepancia.
3. «R8 no rompió `kotlinx.serialization`, Room ni OkHttp». Que compile no lo
   demuestra. Revisa `proguard-rules.pro` comparándolo con todas las clases
   `@Serializable`, entidades y DAOs de Room. Para probar en tiempo de
   ejecución, firma **una copia en `/tmp`** del APK release sin firmar con el
   keystore debug (`~/.android/debug.keystore`, `apksigner`), instálala y
   recorre: Cercanas → A05 en vivo → 🔔 guardar aviso → notificación real → el
   aviso desaparece de A04. No crees un keystore de release ni guardes nada en
   el repo.
4. «Firma release: lee solo de `~/.gradle/gradle.properties` o de `-P`; si
   faltan las claves, falla con un mensaje claro». Pruébalo: lanza
   `assembleRelease` sin propiedades y sin el flag `unsigned`, y comprueba que
   ningún secreto ni ruta de keystore pueda acabar en el repo, en `BuildConfig`
   o en los logs de Gradle.
5. «Auditoría de secretos: 124 commits, 0 coincidencias». Recuenta con
   `git rev-list --all --count`. Repite el escaneo con más patrones: tokens de
   Telegram, claves `AIza…`, `-----BEGIN … PRIVATE KEY-----`, `.jks`/`.keystore`
   en el historial, `.env` alguna vez trackeado (`git log --all -- '*.env'`),
   blobs borrados. Usa `gitleaks`/`trufflehog` si están instalados.
6. «Notificación y "Vigilando …" probadas en euskera en el AVD contra la API
   real». Busca la evidencia de esa prueba. Si no la hay, reprodúcela
   (`adb shell cmd locale set-app-locales eus.busdonostia.app --locales eu`) o
   márcala falsa / no verificable.
7. «Ficha de Play es/eu lista para revisar». Comprueba que cumple la política
   de Google sobre FGS `dataSync` (por qué, duración, lo inicia el usuario), la
   ubicación (solo ordena Cercanas, nunca se guarda), la Data Safety, la
   atribución OSM ODbL y la política de privacidad. Busca permisos con erratas
   y afirmaciones que el manifiesto contradiga (vuelca el manifiesto con
   `aapt2 dump xmltree` o `apkanalyzer manifest print` sobre el APK). Juzga si
   el euskera es correcto o está inventado.
8. «Nombre e icono no oficiales». Comprueba el `app_name` en es/eu, el icono
   adaptativo propio, cualquier parecido con `com.indra.dbus` y el aviso de no
   oficialidad en A07.

## Ejes de ataque además de las afirmaciones

Para cada eje, busca casos que lo rompan, no ejemplos que lo cumplan.

- **Reglas no negociables (`AGENTS.md`).** Datos reales sin mocks ni fallbacks
  en producción. Llegadas siempre en vivo, sin sacar horarios del catálogo.
  Identidad de línea: el código público manda, `32`/`28`, alfanuméricas. GPS
  solo con `tipo == 1`, y ningún test asume un conjunto cerrado de valores de
  `tipo`. Dedup de favoritos y avisos por `(parada, línea)`. Caducidad a 7
  días. Filtro de destino. Avisos de un solo uso: saltan una vez y se borran,
  sin recurrentes, sin Posponer/Descartar.
- **Cero códigos opacos en la UI.** No puede haber `stopId`, números de poste,
  IDs internos de línea ni de itinerario en pantallas, notificaciones,
  `contentDescription`, placeholders, banners ni en el label del intent
  `geo:`. Compruébalo en el código **y** en un volcado real de la UI.
- **Política de configuración en 3 niveles.** Nada de valores operativos
  inline: sondeo, frescura, timeouts, avisos, velocidad de caminata, límites.
  Nada de `tween(n)`, `delay(n)`, escalas ni springs inline fuera de
  `MotionTokens`/`DesignTokens`. Haz un grep de literales numéricos en `ui/`,
  `engine/` y `data/` y justifica cada excepción de nivel 3.
- **Pureza KMP de `domain/`.** Sin `java.time.*`, `java.util.Locale`,
  `java.text.*`, `String.format`, `android.*` ni `Context`.
- **Rendimiento.** Nada de I/O en el hilo principal (`assets/`, `filesDir`,
  Room, `SharedPreferences`; el tema y `filesDir` ya se señalaron). Ticker
  aislado. Estados animados leídos en `graphicsLayer`/`offset`. `key` y
  `contentType` en todos los `items`. `rememberInfiniteTransition`
  condicionado a `!reducedMotion && !LocalInspectionMode.current`.
- **Seguridad y plataforma.** Cleartext solo para `app.dbus.eus`
  (`network_security_config`). Componentes `exported`. Deep link
  `dbus://stop/{stopId}` con entrada maliciosa: id inexistente, inyección,
  longitudes extremas. Config remota: lista de hosts HTTPS permitidos para
  `apiBaseUrl` y validación defensiva (P10 de la auditoría de arquitectura).
  FGS `dataSync`: tipo declarado, permisos de Android 14/15, límite de 6 h de
  `dataSync` en Android 15, qué pasa tras un reinicio, Doze.
- **Motor de avisos.** Sondeo adaptativo en 4 escalones desde `config.json`
  (≤ 7 min → 30 s, 8–14 → 1 min, 15–30 → 4 min, ≥ 31 → 10 min; sin datos →
  base × 2). Aviso 30 s antes. Reentrada. Aislamiento entre avisos.
  Comprueba que los tests cubren los límites exactos (7/8, 14/15, 30/31) y
  que ninguno pasa por suerte con el tiempo (sleeps, relojes reales).
- **Calidad de los tests.** Busca tests que no pueden fallar, mocks que
  sustituyen justo lo que se afirma probar, aserciones sobre texto o prosa, y
  `StringsParityTest` comparado con las claves reales de `values/` y
  `values-eu/`.
- **Coherencia de los docs.** `AGENTS.md`, `ESTADO.md`, `SPRINTS.md`,
  `apps/android/README.md` y el índice `docs/README.md` comparados con el
  código y entre sí (número de tests, escalones de sondeo, estado de los
  sprints, HEAD citado).

## Salida

Escribe `docs/auditoria/2026-11-sprint5-adversarial.md` con:

1. **Veredicto**: `SHIP` / `SHIP CON CONDICIONES` / `NO SHIP` para la pista
   interna de Play, en una frase con el motivo.
2. **Tabla de afirmaciones** (1–8): afirmación · veredicto · evidencia.
3. **Hallazgos** ordenados por severidad:
   - **P0**: rompe una regla no negociable, filtra un secreto, crashea o
     bloquea la publicación.
   - **P1**: incumple el DoD o una afirmación es falsa.
   - **P2**: incumple el anexo o la guía de Play con riesgo real.
   - **P3**: menor.
   Cada hallazgo lleva un id (`A5-NN`), dónde (`ruta:línea`), la evidencia
   (comando + salida recortada), por qué importa y el arreglo mínimo
   propuesto. Sin evidencia no hay hallazgo: va a «Sospechas no confirmadas».
4. **No verificado**: lo que no pudiste comprobar y qué haría falta (móvil
   real, keystore de Iker, hosting…).
5. **Lo que resistió el ataque**: afirmaciones que intentaste tumbar y
   aguantaron, con la prueba que usaste.

Añade la entrada del informe al índice `docs/README.md` (regla de
documentación de `AGENTS.md`). Termina con un resumen en el chat: veredicto,
recuento por severidad y los tres hallazgos más graves.
