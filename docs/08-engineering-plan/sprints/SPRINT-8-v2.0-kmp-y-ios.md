# Sprint 8 — Arquitectura KMP Compartida y App iOS Nativa (`v2.0.0`)

**Objetivo:** Lanzar **Bus Donostia para iOS** con UI 100% nativa en **SwiftUI**, reutilizando el núcleo Kotlin (`domain/`, parsers, Room KMP y tokens de diseño) sin penalizar el rendimiento ni el tamaño de la app Android.

**Esfuerzo estimado:** 4–6 semanas · **Riesgo:** Alto · **Pre-requisito:** `domain/` libre de `java.*` (Sprint 5.0) y `packages/contracts/design-tokens.json` activo (Sprint 6).

---

## Fase 1: Extracción del Módulo Compartido KMP (`shared/`)
- [ ] Crear módulo Gradle KMP `shared/` con targets `androidTarget()` y `iosArm64()` / `iosSimulatorArm64()` exportando `BusDonostiaShared.xcframework`.
- [ ] Mover `domain/` y parsers (`StopFeedParser`, `CatalogParser`, `ConfigParser`) a `shared/src/commonMain/kotlin/`.
- [ ] Migrar capa de red compartida a **Ktor Client** (motor `OkHttp` en Android, motor `Darwin`/`URLSession` en iOS), conservando la regla ADR 009 de datos 100% reales y timeouts desde `config.json`.
- [ ] Migrar base de datos local a **Room KMP** (SQLite nativo en ambas plataformas) manteniendo el mismo esquema v3 (`favorites`, `alerts`, `scheduled_alerts`, `recent_stops`, `line_colors`).
- [ ] Mantener la suite de tests unitarios de dominio/parsers ejecutándose tanto en JVM como en simulador iOS (`commonTest`).

---

## Fase 2: Generador de Tokens Swift y App iOS (SwiftUI)
- [ ] Extender el script de `packages/contracts/design-tokens.json` para generar `DesignTokens.swift` y `MotionTokens.swift` (respetando `@Environment(\.accessibilityReduceMotion)` en SwiftUI).
- [ ] Reutilizar el pipeline `mergeCatalog` para empaquetar `catalog.json` y `config.json` en el bundle de Xcode.
- [ ] Configurar excepción ATS en `Info.plist` exclusivamente para `app.dbus.eus`.
- [ ] Implementar las pantallas nativas en SwiftUI respetando gestos e idiomas (`es`/`eu` en `.xcstrings`):
  - `HomeView`, `StopsView` (CoreLocation), `LinesView`, `LineDetailView`, `StopDetailView` (`maps://` intent), `AlertsView`, `AlertEditView`, `SettingsView`.
- [ ] Integrar `CoreHaptics` / `sensoryFeedback` equivalente a los patrones definidos en Android.

---

## Fase 3: Avisos en Segundo Plano en iOS (`apps/bot` + APNs + Live Activities)
- [ ] Exponer en **`apps/bot`** endpoints ligeros para registrar/cancelar suscripciones de aviso iOS (`deviceToken` APNs, `stopId`, `lineId`, `destination`, `minutesAhead`, programación opcional).
- [ ] Conectar el motor de vigilancia de `apps/bot` con **APNs** (`interruption-level: time-sensitive`, deep link `dbus://stop/{stopId}?highlightLine={lineId}`).
- [ ] Implementar **Live Activity** (WidgetKit + ActivityKit) para mostrar la cuenta atrás GPS real en Dynamic Island y pantalla de bloqueo mientras el usuario espera el autobús.
- [ ] Mantener Android 100% funcional en modo autónomo sin servidor (ADR 009 intacto).

---

## Criterios de Aceptación
- Android `v2.0.0` mantiene exactamente el mismo rendimiento de arranque (Baseline Profiles + R8) y 100% de tests en verde sin regresiones.
- iOS `v2.0.0` pasa pruebas en dispositivo físico: llegadas en vivo, ubicación cercana, cambio de idioma ES/EU, recepción puntual de push APNs con app cerrada/bloqueada y actualización de Live Activity.
