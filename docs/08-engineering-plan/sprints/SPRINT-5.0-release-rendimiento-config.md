# Sprint 5.0 — Release Play Store, Rendimiento Base y Config Operativo (`v1.0.0`)

**Objetivo:** Publicar la v1.0.0 en Google Play con rendimiento de nivel producción (R8 Full Mode + Baseline Profiles), eliminar valores operativos inline moviéndolos a `config.json` (incluyendo los escalones de ahorro de batería de 4 y 10 min) y dejar `domain/` limpio de dependencias JVM pensando en KMP/iOS.

**Esfuerzo estimado:** 1–2 días · **Riesgo:** Bajo · **Pre-requisito:** Merge `b8e08f8` en verde.

---

## Tareas de Ingeniería

### 1. Externalización de Parámetros Operativos (`config.json` v2)
- [ ] Actualizar `app/src/main/assets/config.json` siguiendo `contracts-templates/config.v2.template.json` (añadir secciones `polling`, `freshness`, `alerts`, `geo`, `uiLimits`).
- [ ] Añadir data classes `@Serializable` en `domain/` (`PollingConfig`, `AdaptivePollTier`, `FreshnessConfig`, `AlertsConfig`, `GeoConfig`, `UiLimitsConfig`) sin dependencias de Android.
- [ ] Implementar validación defensiva de rangos en `ConfigRepository` (descartar config remota corrupta y mantener bundled/cache válida).
- [ ] Actualizar `AlertEngine.pollDelayMs(...)` para consumir `polling.adaptiveTiers` con los nuevos escalones:
  - Gap `> 30 min` → `10 min` (`600 s`)
  - Gap `15–30 min` → `4 min` (`240 s`)
  - Gap `8–14 min` → `1 min` (`60 s`)
  - Gap `≤ 7 min` → `30 s` (`30 s`)
- [ ] Actualizar `Ticker`, `Freshness`, `LiveFeeds` (historial=3) y `HomeViewModel` (`homeFavoriteMaxArrivals=3`) para leer de `ConfigRepository`.
- [ ] Añadir/actualizar tests unitarios en `AlertEngineTest` y `ConfigRepositoryTest` (incluyendo casos límite de gap en 7, 8, 14, 15, 30, 31 min y rechazo de JSON remoto con rangos inválidos).

### 2. Rendimiento Android (Build & Runtime)
- [ ] Activar **R8 Full Mode** (`isMinifyEnabled = true`, `isShrinkResources = true`) en `buildTypes.release` y verificar `proguard-rules.pro` con `kotlinx.serialization`, Room y OkHttp.
- [ ] Añadir `androidx.profileinstaller` y generar **Baseline Profile** cubriendo: arranque → `HomeScreen` → scroll `StopsScreen` → `StopDetailScreen` → `LineDetailScreen`.
- [ ] Auditar con `StrictMode` (solo en build `debug`) que `assets/catalog.json` y `config.json` se cargan 100% fuera del Main Thread.
- [ ] Confinar `ticker.now` (`nowMs`) en `HomeScreen` y `StopDetailScreen` mediante `distinctUntilChanged()` en ViewModels / nodos hoja para evitar recomposiciones de pantalla completa cada 30 s.
- [ ] Verificar `key` y `contentType` estables en todos los `LazyColumn` (`HomeScreen`, `StopsScreen`, `LinesScreen`, `LineDetailScreen`, `StopDetailScreen`, `AlertsScreen`).

### 3. Blindaje Preventivo KMP en `domain/`
- [ ] Auditar todos los archivos de `domain/` buscando imports `java.time.*`, `java.util.Locale`, `java.text.*` o `String.format`.
- [ ] Sustituir cualquier referencia `java.time.Instant` por `Long` (epoch ms UTC) o `kotlinx.datetime.Instant` y delegar el formateo de texto (`HH:mm`, `1,2 km`) a la capa `ui/`.

### 4. Cierre de Release Play Store
- [ ] Configurar `REMOTE_CONFIG_URL` en producción (HTTPS) y subir `config.json` v2 al endpoint remoto.
- [ ] Configurar firma `release` (keystore fuera de git, variables de entorno / `local.properties`).
- [ ] Preparar assets de ficha Play Store (capturas ES y EU, icono adaptativo, política de privacidad destacando cero recolección de datos y uso local de ubicación).

---

## Criterios de Aceptación y Verificación
- `./gradlew check assembleDebug assembleRelease` en verde (incluye `lintDebug`, `StringsParityTest` y 94+ tests JVM/Robolectric).
- Prueba manual en dispositivo físico con APK `release` (minificado R8):
  - Consulta real a Dbus (`app.dbus.eus`), favoritos, búsqueda, cambio de idioma ES/EU, tema claro/oscuro.
  - Creación y disparo real de un aviso con `AlertMonitorService` y apertura vía deep link `dbus://stop/{stopId}?highlightLine={lineId}`.
