# 03. Rendimiento Extremo en Android (Arranque, 120 fps y Memoria)

Objetivo: Que **Bus Donostia** abra casi instantáneamente en la calle (incluso en terminales gama media con `minSdk 26`), mantenga **60/120 fps sin dropped frames** y consuma mínima batería y memoria.

---

## 1. Optimizaciones de Build y Arranque (Mayor Retorno de Inversión)

### A. R8 Full Mode + Shrinking en Release
En `app/build.gradle.kts` (`buildTypes.release`):
- `isMinifyEnabled = true`
- `isShrinkResources = true`
- `proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")`
- Verificar reglas mínimas para `kotlinx.serialization` y Room (ambos ya traen consumer rules, pero validar en APK release firmado antes de subir a Play Store).
- **Beneficio:** Reduce tamaño del APK, elimina clases sin usar e *inlinea* las constantes de `MotionTokens` y `DesignTokens` (coste 0 en runtime).

### B. Baseline Profiles (`androidx.profileinstaller`)
Jetpack Compose sufre penalización de compilación JIT en el primer arranque y el primer scroll tras instalar o actualizar la app.
- Añadir `implementation("androidx.profileinstaller:profileinstaller:<version>")` al módulo `app`.
- Crear módulo `:baselineprofile` con el plugin `androidx.baselineprofile` que ejercite el *Critical User Journey*:
  1. Arranque en frío → `HomeScreen`.
  2. Cambio de pestaña → `StopsScreen` + scroll rápido por la lista de 548 paradas.
  3. Navegación a `StopDetailScreen` y filtrado por chips de línea.
  4. Cambio a `LinesScreen` → `LineDetailScreen` + scroll en timeline.
- **Beneficio medible:** Reducción del **30–40 %** en arranque en frío y eliminación de *jank* inicial en listas.

### C. Cero I/O en el Hilo Principal al Arrancar
- `assets/catalog.json` (548 paradas + itinerarios + coordenadas OSM) y `assets/config.json` / `filesDir/remote-config.json` deben leerse y parsearse exclusivamente en `Dispatchers.IO` / `Dispatchers.Default`.
- `BusDonostiaApp.onCreate()` y `MainActivity.onCreate()` solo instancian referencias ligeras en `AppContainer`; la UI muestra el estado `catalogReady == false` (o skeleton ligero) mientras el catálogo termina de cargar en pocos milisegundos.
- Verificar en debug con `StrictMode.ThreadPolicy` (detectDiskReads / detectDiskWrites en Main Thread) durante pruebas locales.

---

## 2. Optimización de Recomposición en Jetpack Compose (120 fps)

### A. Confinar el `Ticker` de 30 segundos a Nodos Hoja
**Anti-patrón:** Leer `nowMs` en la raíz de `HomeScreen` o `StopDetailScreen` y pasarlo hacia abajo provoca recomponer toda la pantalla cada 30 segundos aunque ningún minuto haya cambiado.
**Patrón obligatorio:**
1. En el ViewModel, derivar el estado de presentación aplicando `distinctUntilChanged()` sobre los valores ya calculados (`remainingMinutes`, `freshnessLevel`, texto `"hace HH:mm"`).
2. Si un subcomponente necesita `nowMs` directamente, pasarlo vía lambda `nowMsProvider: () -> Long` únicamente al composable hoja que lo muestra.

### B. Animar en Fase de Dibujo (`graphicsLayer` / `drawBehind`)
Para cualquier animación recurrente o ligada a gestos (`pressScale`, `LiveSignalDot`, `FavoriteStar`, partículas Canvas):
- Leer `State<Float>` **dentro** de `Modifier.graphicsLayer { scaleX = ...; alpha = ... }` o `Modifier.offset { IntOffset(...) }`.
- Esto salta por completo las fases de **Composición** y **Layout**, ejecutándose directamente en el hilo de renderizado/GPU.

### C. Estabilidad y Claves en Listas (`LazyColumn`)
- Aunque Kotlin `2.2.21` habilita *Strong Skipping Mode* por defecto, anotar los modelos de UI de estado (`HomeUiState`, `StopRowUi`, `ArrivalRowUi`) como `@Immutable` y evitar crear lambdas inestables o listas filtradas dentro de la fase de composición (filtrar siempre en el ViewModel en `Dispatchers.Default` si la lista supera ~100 elementos, como las 548 paradas en `StopsScreen`).
- Asignar siempre `key = { ... }` y `contentType = { ... }` en cada `items(...)` de `LazyColumn` para maximizar el reciclaje de nodos de Compose (ej. `contentType = "header"` vs `"stop_row"` vs `"alert_card"`).

### D. Búsqueda sin Bloqueos (`searchStops` / `searchLines`)
- En `HomeViewModel` y `StopsViewModel`, aplicar `debounce(120)` (opcional o ejecución en `Dispatchers.Default` mediante `flowOn(Dispatchers.Default)`) sobre el `StateFlow<String>` de la query para que el tecleo rápido no compita con el frame de renderizado del teclado.

---

## 3. Checklist de Validación de Rendimiento
- [ ] Macrobenchmark o perfilado en dispositivo físico (modo `release`, **nunca** `debug`) verificando `FrameDurationCpuMs < 8ms` (120 Hz) / `< 16ms` (60 Hz) durante scroll en `StopsScreen` y `LineDetailScreen`.
- [ ] `StrictMode` en debug sin lecturas de disco (`catalog.json` / `SharedPreferences`) bloqueando el Main Thread durante el arranque.
- [ ] APK `release` con R8 Full Mode probado de principio a fin (verificando parseo de JSON real de Dbus, Room y deep links `dbus://stop/...`).
- [ ] Inspección con Layout Inspector (Recomposition Counts) comprobando que un tick de 30 s sin cambios de minutos no recompone las tarjetas ni la Bottom Bar.
