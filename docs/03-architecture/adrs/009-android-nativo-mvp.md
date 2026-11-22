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
5. **Catálogo offline:** se empaqueta en `assets/` a partir de
   `apps/bot/data/dbus-catalog.json` + `dbus-stop-coordinates.json` mediante una
   tarea Gradle de copia/fusión. No se edita a mano.

## Consequences
### Positive
- Cero backend nuevo; tráfico distribuido por usuario.
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
