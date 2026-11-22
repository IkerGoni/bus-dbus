# ADR 009: MVP Android nativo (Kotlin + Compose) en lugar de PWA

## Status
Accepted (2026-11-22). Sustituye a ADR 001, 002, 003 y 007 para el cliente
móvil. La PWA de `apps/mobile/` queda congelada (no se borra).

## Context
- La API de Dbus (`http://app.dbus.eus/SSIIMovilWSv2/ws/cons`) solo responde
  por **HTTP** y **sin `Access-Control-Allow-Origin`**
  (`docs/06-api-externa/01-endpoints-y-riesgo.md` §9). Un navegador no puede
  leerla: una web app necesita un proxy propio, que concentra todo el tráfico
  en una IP bloqueable (§7.1) y perdemos la ventaja de «una IP por usuario»
  (§7.2). Una app nativa la llama directamente.
- La función principal («Avísame X min antes») necesita trabajo en segundo
  plano. En web eso exige servidor de push (VAPID) que sondee Dbus. En Android
  se resuelve en el dispositivo sin servidor.
- La PWA tiene el build roto (30 errores TS, 233 de lint, `KNOWN_ISSUES.md`) y
  su diseño quedó superado por el paquete `docs/07-android-mvp/`.
- El bot ya cubre el uso «web/chat» con avisos por Telegram.

## Decision
1. **Cliente:** app Android nativa en `apps/android/` (lane `android`), Kotlin
   + Jetpack Compose + Material 3. Gradle, fuera del workspace pnpm.
   minSdk 26, targetSdk 36 (requisito de Play en 2026). Room (DB), OkHttp + kotlinx.serialization (red),
   Navigation Compose, sin DI framework en v1.
2. **Sin web app previa.** Las referencias `stitch-references/*/code.html`
   sirven como prototipo visual; no se construye otra PWA.
3. **Motor de avisos 100 % local** (Opción B del paquete): Foreground Service
   `dataSync` que sondea Dbus cada 30 s solo mientras hay un aviso en ventana.
   **Sin FCM en v1**: exige Firebase, registro de tokens y un servidor siempre
   encendido que vuelve a ser una IP única.
4. **Lógica de dominio duplicada a propósito en Kotlin** (parseo de
   `tiemposParada`, identidad de línea, `computeAlertStatus`,
   `computeFreshness`, `haversineMeters`, matching de llegada). La paridad con
   `@dbus/core` se garantiza con **fixtures JSON compartidos** en
   `packages/contracts/fixtures/` que ejecutan tanto vitest como JUnit.
5. **Sin backend.** La app funciona completa sin ningún servidor nuestro:
   catálogo empaquetado, tiempo real directo a Dbus desde el dispositivo,
   DB local, avisos locales. Lo único externo opcional es un **archivo de
   configuración remota estático** (ver abajo); si no se puede descargar, la
   app usa la copia empaquetada y sigue igual.
6. **Catálogo offline:** se empaqueta en `assets/` a partir de
   `apps/bot/data/dbus-catalog.json` + `dbus-stop-coordinates.json` mediante una
   tarea Gradle de copia/fusión. No se edita a mano.

## Configuración remota (estática, opcional)
Mitigación de `docs/06-api-externa/01-endpoints-y-riesgo.md` §8: si Dbus
cambia la base URL, un path, el nombre de un parámetro o exige una cabecera,
se arregla editando un JSON, no publicando una versión en la tienda.

- **Qué es:** un `config.json` en hosting estático HTTPS (GitHub Pages de un
  repo público mínimo, o Cloudflare Pages). Sin código de servidor, sin base
  de datos, sin cuentas de usuario, sin telemetría: el servidor no sabe nada
  del usuario salvo la IP de la descarga.
- **Contenido (`schemaVersion` 1):** `apiBaseUrl`, paths de endpoints, nombres
  de parámetros, cabeceras extra, `pollIntervalSec` (mín. 20), `minAppVersion`
  y un `notice` opcional (`es`/`eu`) para avisar de incidencias.
- **Cliente:** una copia por defecto va empaquetada en `assets/`. Descarga como
  mucho cada 12 h al abrir la app, timeout 5 s, valida esquema y rangos; si
  falla cualquier cosa, se queda con la última válida o la empaquetada. Nunca
  bloquea el arranque.
- **Seguridad:** solo HTTPS; un `apiBaseUrl` nuevo solo puede ser HTTPS (el
  cleartext sigue limitado a `app.dbus.eus`). Los avisos y favoritos nunca
  dependen de este archivo.
- **No cubre:** Play Integrity / attestation ni la vía legal; para eso no hay
  arreglo de configuración.

## Consequences
### Positive
- Cero backend nuevo; tráfico distribuido por usuario. Coste de hosting de la
  config: 0 €.
- La spec del paquete (dp, `NavigationBar`, `BroadcastReceiver`, canales) se
  implementa tal cual, sin capa de emulación.

### Negative
- Toolchain nuevo (JDK 17 + Android SDK) que hoy no está instalado.
- Dos implementaciones del dominio (TS y Kotlin); se mitiga con fixtures.
- Sin iOS ni web en v1.

### Riesgos abiertos
- Política de Google Play para Foreground Service `dataSync` y para el nombre
  de la app: no debe parecer la app oficial (`com.indra.dbus`).
- La API no es pública ni tiene contrato (`docs/06-api-externa/`).

## Actualizaciones (2026-11-22, tras los Sprints 0–4)
La decisión no cambia. Hechos posteriores:
- **Sondeo adaptativo** (sustituye al «cada 30 s» de la Decisión 3): el motor
  sondea cada 30 s si faltan ≤ 7 min para avisar, 1 min hasta 14 y 2 min más
  allá; avisa 30 s antes (`NOTIFY_EARLY_MS`). La UI visible sigue a 30 s.
  Detalle: [`04-android-platform.md`](../../07-android-mvp/docs/04-android-platform.md) §1.
- **Avisos de un solo uso:** saltan una vez y se borran; sin recurrentes ni
  alarmas exactas ni `BroadcastReceiver` propios.
- **Hosting de `config.json` sin decidir:** el repo es privado y la licencia
  está pendiente (decisión de Iker). `REMOTE_CONFIG_URL` sigue vacía y la app
  usa la copia empaquetada. Las opciones de la sección «Configuración remota»
  son candidatas, no un repo público decidido.
- El toolchain (JDK 17 + Android SDK) ya está instalado: ver
  [`apps/android/README.md`](../../../apps/android/README.md).
