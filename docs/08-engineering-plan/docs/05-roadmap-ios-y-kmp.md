# 05. Roadmap Técnico para Futuro iOS + Kotlin Multiplatform (KMP)

## 1. Decisión Arquitectónica: KMP (`commonMain`) + UI 100% Nativa
Para garantizar **máximo rendimiento** y sensación completamente nativa tanto en Android como en iOS:
- **Compartido en Kotlin Multiplatform (KMP):** Capa `domain/`, modelos `@Serializable`, parsers (`StopFeedParser`, `CatalogParser`), lógica de búsqueda/geo/avisos y persistencia local (`Room KMP`). Se compila a `.aar`/JAR en Android y a **`XCFramework`** nativo en iOS.
- **Nativo por plataforma:** **Jetpack Compose** en Android y **SwiftUI** en iOS.
- **Contratos compartidos en monorepo (`packages/contracts/`):**
  - `config.json` (remoto HTTPS + bundled) consumido por ambas plataformas.
  - `design-tokens.json` compilado en *build-time* a `DesignTokens.kt` / `MotionTokens.kt` (Android) y `DesignTokens.swift` / `MotionTokens.swift` (iOS).
  - `catalog.json` generado desde `apps/bot/data/` e incluido en los assets/bundle de ambas apps.

---

## 2. Preparación Preventiva en Android Hoy (Coste Casi Cero)
Reglas a aplicar ya desde **Sprint 5.0** para que `domain/` pueda moverse a `commonMain` sin reescritura:

| Qué revisar en `domain/` | Estado actual / Riesgo | Estándar KMP-ready |
| :--- | :--- | :--- |
| **Fechas y `Ticker`** | Posible uso de `java.time.Instant` | Usar `Long` (milisegundos epoch UTC) o `kotlinx.datetime.Instant`. |
| **Reloj del sistema** | `System.currentTimeMillis()` | Inyectar interfaz `fun interface Clock { fun nowMs(): Long }`. |
| **Formateo de texto** | `String.format(Locale, ...)` | Prohibido en `domain/`. Devolver tipos puros (`Double`, `Int`) y formatear en UI (`1,2 km`, `HH:mm`). |
| **Regex / Normalización** | `java.text.Normalizer` (acentos en `Search.kt`) | Encapsular la normalización de acentos tras una pequeña interfaz o mapa ASCII/latino en Kotlin puro (soporte es/eu: `áéíóúüñ`). |

---

## 3. Mapa de Migración por Capas (Android → KMP + iOS)

| Capa | Android Actual (MVP v1) | KMP + iOS (v2.0) | Nivel de Reutilización |
| :--- | :--- | :--- | :--- |
| **UI y Navegación** | Jetpack Compose M3 + Navigation Compose | Compose (Android) + SwiftUI (iOS) | 0% código UI · 100% tokens (`design-tokens.json`) |
| **ViewModels / Estado** | `ViewModel` + `StateFlow` | Shared State Holders en KMP (opcional con SKIE para `AsyncSequence` en Swift) | 70–85% |
| **Dominio** | `domain/` Kotlin puro | `shared:domain` (`commonMain`) | **100%** |
| **Serialización y Parsers** | `kotlinx.serialization` | `shared:data` (`commonMain`) | **100%** |
| **Cliente HTTP** | `OkHttp 4.12.0` | `Ktor Client` (motor `OkHttp` en Android, motor `Darwin`/`URLSession` en iOS) | **90%** |
| **Base de Datos** | `Room 2.8.4` | `Room KMP` (SQLite nativo en Android e iOS) | **95%** |
| **Background / Avisos** | `AlertMonitorService` (`dataSync`) + `AlarmManager` | Android mantiene ejecución local (ADR 009); iOS usa `apps/bot` + **APNs** + **Live Activities** | Lógica `AlertEngine` 100%; ejecución distinta por SO |

---

## 4. Particularidades Obligatorias de iOS a Tener en Cuenta

### A. Red HTTP Cleartext (`app.dbus.eus`)
iOS bloquea peticiones HTTP no cifradas mediante **App Transport Security (ATS)**. Al igual que el `network_security_config.xml` en Android, en iOS será obligatorio añadir una excepción acotada únicamente al dominio de Dbus en `Info.plist`:
```xml
<key>NSAppTransportSecurity</key>
<dict>
    <key>NSExceptionDomains</key>
    <dict>
        <key>app.dbus.eus</key>
        <dict>
            <key>NSExceptionAllowsInsecureHTTPLoads</key>
            <true/>
        </dict>
    </dict>
</dict>
```
*(Nota: Al subir a App Store Review, documentar brevemente que el endpoint oficial público de Dbus Donostia solo opera en HTTP y que no se transmiten datos personales).*

### B. Avisos en Segundo Plano (Diferencia Crítica con Android)
- En iOS **no existe un Foreground Service continuo de red**. Cuando el usuario bloquea el teléfono o cambia de app, iOS suspende el proceso en ~30 segundos; `BGAppRefreshTask` es oportunista (puede retrasarse 30–60 minutos) y **no sirve** para avisar 5 minutos antes de un autobús.
- **Solución arquitectónica (aprovechando el monorepo actual):**
  1. Reutilizar **`apps/bot`** como servicio de vigilancia para clientes iOS (registro efímero del aviso con `deviceToken` APNs, `stopId`, `lineId`, `destination`, `minutesAhead`).
  2. Cuando `apps/bot` detecta la llegada GPS (`tipo = 1.0`), envía notificación push vía **APNs** (con `interruption-level: time-sensitive`).
  3. **Valor añadido exclusivo en iOS:** Aprovechar el mismo canal APNs push-to-update para actualizar una **Live Activity** (Dynamic Island y pantalla de bloqueo) mostrando la cuenta atrás real del autobús sin abrir la app.

### C. Idiomas (`es` / `eu`) y Mapas
- iOS soporta euskera (`eu`) y español (`es`) nativamente vía `String Catalogs (.xcstrings)` con paridad validable en CI contra `values/` y `values-eu/`.
- Botón *"Cómo llegar"* en `StopDetailScreen`: abrir Apple Maps (`maps://?daddr=lat,lon`) o selector si Google Maps está instalado, manteniendo la filosofía sin SDK de mapas embebido (0 MB extra).
