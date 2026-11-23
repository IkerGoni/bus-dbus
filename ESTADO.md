# Estado — bus-dbus

Actualizado: 2026-11-23 · Última sesión: preparación publicación Play Store + strings EN + verificación EU + tokens guard

## Objetivo actual

Preparar Android para publicación independiente en Google Play. Sprint 5 de publicación avanzado a 90% en checklist técnico.

## Hecho en esta sesión

- **Identidad de desarrollador confirmada:** Dev Igoni — Iker Goñi Cuesta, Pamplona, España. Email soporte: `dev.mail.igoni@gmail.com`.
- **Interfaz en inglés creada e integrada:** creados recursos en `apps/android/app/src/main/res/values-en/` (`strings.xml`, `strings_settings.xml`, `strings_engine.xml`). `StringsParityTest.kt` extendido para verificar paridad trilingüe (ES, EU, EN) en build y CI.
- **Correcciones de euskera completadas:** aplicadas las 13 correcciones en `values-eu/strings*.xml` y los 6 literalismos en `badator-google-play-pack/01-listing/eu-ES.md`. Script `06-verify-eu.mjs` corregido y 100% verde (0 issues detectados, 0 keys faltantes).
- **Políticas de privacidad regeneradas (ES / EU / EN):** eliminados todos los placeholders («Pendiente/Verificar/⚠️») con evidencia real del código (cleartext HTTP solo `app.dbus.eus`, Room local, sin trackers ni ads, `allowBackup=false`). Desplegables a GitHub Pages (`docs/privacy/privacy-policy-{es,eu,en}.html`).
- **Data Safety auditado y corregido:** `encryptsAtRest: false` (Room no cifra; Android FBE cifra a nivel SO), `encryptsInTransit: false`. Archivos en `docs/play-console/data-safety-responses.json` y pack.
- **Ficha Play Store (`07-store-fields.json` + `01-listing/*.md`):** sincronizados textos trilingües (ES, EU, EN), email de soporte real, case `Dev Igoni`.
- **Ticket A5-11 y Test Guardián Ratchet:** creado `docs/07-android-mvp/tickets/A5-11-DESIGN-TOKENS-SWEEP.md` con inventario detallado de los 165 usos de `XX.dp` en `ui/`. Creado `DesignTokensGuardTest.kt` que bloquea regresiones inline y prohíbe animaciones inline fuera de `MotionTokens`.
- **Checklist Maestro (`08-final-checklist.mjs`):** eliminados silenciamientos de errores (`>/dev/null`), añadida validación real de contenido y tamaños. Resultado: **28/31 checks en verde (90%)**.
- **Build y verificación Android:** `./gradlew check assembleDebug` en verde (0 errores de lint, todos los tests JVM unit/Robolectric pasando, debug APK generado).

## Pendiente (requiere acción del usuario / hardware)

1. [ ] **Keystore de subida + AAB firmado:** crear keystore (`keytool`), configurar `~/.gradle/gradle.properties` y generar AAB firmado (`./gradlew bundleRelease`).
2. [ ] **Capturas de pantalla reales (18 PNGs):** 6 pantallas × 3 idiomas (ES/EU/EN) tomadas en emulador o dispositivo real (`build/outputs/play-store/screenshots/`).
3. [ ] **Vídeo demostración FGS:** grabar pantalla (`adb screenrecord`) siguiendo `04-console/FGS-REVIEW-TEMPLATE.md`, subir a YouTube como no listado y poner URL en la declaración de Play Console.
4. [ ] **Revisión nativa de cortesía en Euskera:** aunque el script automático da 0 issues, se recomienda una lectura rápida por un hablante nativo antes del lanzamiento público.
5. [ ] **Service Account Google Play (opcional):** crear SA en Google Cloud + Play Console si se desea subida automatizada por CLI.

## Decisiones tomadas

- Identidad legal y correo público configurados según los datos reales de la cuenta Play Console de Iker.
- Soporte para idioma inglés (EN) añadido formalmente al código y a la ficha de la tienda.
- `encryptsAtRest: false` en Data Safety para total honestidad técnica.
- Nivel 2 (tokens): congelada línea base en 165 `XX.dp` con test ratchet sin romper la regla Boy Scout en archivos ajenos.