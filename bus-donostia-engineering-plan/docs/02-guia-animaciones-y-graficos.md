# 02. Guía de Animaciones, Gráficos y Microinteracciones (Compose M3)

## 1. Política de Activos y Dependencias
1. **Cero Lottie en UI estándar:** Toda transición de pantalla, cambio de estado de datos, toggle o feedback táctil usa APIs nativas de Jetpack Compose (`animate*AsState`, `AnimatedContent`, `AnimatedVisibility`, `Crossfade`, `graphicsLayer`). Coste en APK: **0 KB**.
2. **Mascota e ilustraciones geométricas:** Implementadas en **Canvas nativo de Compose** (`Mascot.kt`) con estados discretos (`Idle`, `Happy`, `Thinking`, `Celebrating`, `Sleeping`).
   - *Excepción única:* Solo se justificará `lottie-compose` (~200 KB) si en el futuro un diseñador entrega un `.json` de After Effects con *morphing* vectorial orgánico inviable en Canvas.
3. **Ubicación estricta de la mascota (UX):** Prohibido colocar la mascota animada en `HomeScreen` donde competiría con la tarjeta prioritaria *"TU PRÓXIMO AVISO"*. Reservarla exclusivamente para **Onboarding** y **estados vacíos** (sin servicio nocturno, sin favoritos, sin avisos).

---

## 2. Presupuesto de Tiempo y Físicas

| Categoría | Duración / Física | Uso en Bus Donostia |
| :--- | :--- | :--- |
| **Micro-feedback táctil** | `80–100 ms` / `pressScale(0.97f)` | Pulsación en tarjetas, filas de parada/itinerario, chips de línea. |
| **Toggles y selección** | `150–200 ms` (`Spring` controlado) | `FavoriteStar`, filtros de líneas, switches de avisos. |
| **Transición de datos** | `200–220 ms` (`FastOutSlowInEasing`) | Cambio de estado `ArrivalsView`, contador de minutos, `InfoBanner`, botón limpiar en `SearchPill`. |
| **Navegación jerárquica** | `260 ms` entrada / `220 ms` salida | Drill-down a `StopDetail`, `LineDetail`, `AlertEdit`, `Settings`. |
| **Límite máximo bloqueante** | **`350 ms`** | Ninguna transición de navegación o carga puede superar este umbral. |

---

## 3. Los 6 Refinamientos Críticos Respecto al Briefing Inicial

| # | Componente | Planteamiento inicial | Implementación definitiva | Motivo técnico / UX |
| :--- | :--- | :--- | :--- | :--- |
| **1** | `NavHost` (`AppShell.kt`) | `slideIntoContainer` global | **Bottom Navigation:** solo `fadeIn(150ms)` / `fadeOut(100ms)`. **Detalle:** `slideIntoContainer(it / 6) + fadeIn(220ms)`. | Material 3 prohíbe deslizamiento lateral entre pestañas principales; desplazamiento corto evita choques con *Predictive Back* (`targetSdk 36`). |
| **2** | Skeleton → Contenido (`Home` / `StopDetail`) | `AnimatedVisibility` | `AnimatedContent(targetState = view, contentKey = { it::class })` | Evita que skeleton y contenido coexistan duplicando altura de la tarjeta y evita re-animar en cada emisión de 30 s del `Ticker`. |
| **3** | `FavoriteStar` (`Components.kt`) | `scale 0.8→1` + rotación `360°` | `scale 0.8→1.15→1` (`Spring.DampingRatioMediumBouncy`) + rotación **`72°`** + `animateColorAsState` + háptica. | Una estrella de 5 puntas es simétrica cada `72°`; `360°` en 200 ms genera desenfoque óptico. |
| **4** | Accesibilidad | `prefersReducedMotion` (CSS) | `Settings.Global.ANIMATOR_DURATION_SCALE == 0f` expuesto vía `LocalReducedMotion`. | API real de Android para respetar *"Quitar animaciones"*. Colapsa duraciones a `0 ms` y apaga animaciones infinitas. |
| **5** | `MinutesLabel` GPS LIVE (v1.1) | Pulso `0.6f → 1f` en el label | Texto numérico al **100% opacidad** con `fontFeatureSettings = "tnum"` + pulso suave (`1f ↔ 0.35f`, `1100 ms`) solo en **punto GPS `8dp`**. | Máxima legibilidad bajo sol directo en la calle y cero vibración horizontal cuando cambia el ancho de los dígitos (`10'` → `9'`). |
| **6** | `sharedElement`, *stagger* y partículas (v1.1–1.2) | Clave simple por `lineId`; `index * 50ms`; partículas al disparar aviso | Clave compuesta `"line-${stopId}-${lineId}"`; stagger acotado `minOf(index, 8) * 35ms`; partículas solo en eventos *in-app* (1ª estrella / crear 1er aviso); wizard direccional en `AlertEditScreen`. | Evita colisiones de clave en `HomeScreen` (misma línea en varias paradas), evita esperas de 1.5 s en líneas de 35 paradas, y refleja que el disparo real ocurre en background. |

---

## 4. Háptica Nativa (`LocalHapticFeedback`)
- **Toggle favorito (`★`) / activar switch de aviso:** `HapticFeedbackType.TextHandleMove` (o `LongPress` en dispositivos < API 30 si se busca mayor firmeza).
- **Stepper de antelación (`1–30 min`) en `AlertEditScreen`:** `HapticFeedbackType.TextHandleMove` en cada paso.
- **Borrar o pausar aviso (con Snackbar "Deshacer"):** `HapticFeedbackType.LongPress`.

---

## 5. Blindaje para los 94 Tests (Robolectric + Lint)
1. **Animaciones infinitas en tests:** `rememberInfiniteTransition` bloquea `composeTestRule.waitForIdle()`. Condicionar **siempre** cualquier bucle infinito (punto GPS, mascota Canvas) a:
   ```kotlin
   val shouldAnimateInfinite = isLive && !reducedMotion && !LocalInspectionMode.current
   ```
   En tests JVM, proveer `LocalReducedMotion provides true` cuando no se esté testando explícitamente el motion.
2. **Lint `HardcodedText = ERROR`:** Ningún elemento visual animado (`LiveSignalDot`, `FavoriteStar`, `Mascot`) puede llevar `contentDescription` literal; usar `stringResource(R.string.xxx)` o `null` si es decorativo (y añadir la clave tanto en `values/` como en `values-eu/` para pasar `StringsParityTest`).
3. **ADR 009 (Solo datos reales):** Prohibido interpolar falsamente segundos entre polls de 30 s o dibujar barras de progreso estimadas de llegada de autobús.
