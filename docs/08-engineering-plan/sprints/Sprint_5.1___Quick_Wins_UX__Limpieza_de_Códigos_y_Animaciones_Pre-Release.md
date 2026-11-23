# Sprint 5.1 — Quick Wins UX, Limpieza de Códigos y Microanimaciones Pre-Release (`v1.0.0`)

**Objetivo:** Pulir la calidad percibida y la claridad de la app antes de salir a Play Store eliminando los códigos numéricos opacos de parada/línea de toda la UI, eliminando los "pops" visuales al cargar datos reales y añadiendo feedback táctil nativo, con **0 KB extra** y riesgo mínimo.

**Esfuerzo estimado:** ~5–6 horas · **Riesgo:** Bajo · **Pre-requisito:** Sprint 5.0 (o ejecutable en paralelo en rama limpia).

---

## Bloque A: Eliminación de Códigos Opacos en UI (Prioridad UX Pre-Release)

Los usuarios de Donostia reconocen las paradas por su **nombre** (ej. *"Boulevard 19"*, *"Gipuzkoa Plaza"*), su **distancia** y las **líneas** que pasan, no por códigos numéricos internos (`104`, `325`, `StopCodePill`) ni por IDs internos de línea de la API de Dbus (ej. `32` para la línea pública `28`).

### 1. Retirada Total de Códigos de Parada en UI (`stopId` / `StopCodePill`)
- [ ] **`StopsScreen` (A02):** Eliminar el código de parada en todas las filas de las 3 pestañas (*Cercanas*, *Favoritas*, *Recientes*) y resultados de búsqueda. Dejar únicamente: nombre de parada + badges de líneas (código público) + distancia/minutos a pie (cuando aplique) + `★` + chevron.
- [ ] **`LineDetailScreen` (A03b):** Eliminar el código de parada junto a cada nodo del timeline vertical. Mostrar solo: círculo del timeline + nombre de parada + chevron.
- [ ] **`StopDetailScreen` (A05):** Eliminar `StopCodePill` / subtítulo con código de parada del header. Mostrar solo: nombre de parada + `★` parada + acción *"Cómo llegar"* (si hay coordenadas).
- [ ] **`HomeScreen` (A01):** Verificar que ni las tarjetas de paradas favoritas, ni los resultados de búsqueda universal, ni la tarjeta *"TU PRÓXIMO AVISO"* renderizan códigos de parada.
- [ ] **`AlertsScreen` (A04) y `AlertEditScreen`:** Mostrar únicamente el nombre legible de la parada en el subtítulo de cada aviso y en el paso 1 del wizard (*Parada*).
- [ ] **Desambiguación de paradas homónimas:** Si dos paradas comparten exactamente el mismo nombre (ej. sentidos opuestos de una avenida), desambiguar mediante contexto humano (**sentido / destino principal** o **badges de líneas**), nunca volviendo a mostrar el número de código.
- [ ] **Limpieza de código muerto:** Eliminar `StopCodePill` de `Components.kt` si deja de referenciarse y limpiar cadenas obsoletas de `values/strings.xml` y `values-eu/strings.xml` (ej. formatos `"Parada %s"` o placeholders que mencionen *"código"*), manteniendo `StringsParityTest` en verde.
- [ ] **Uso interno preservado (invisible en pantalla):** `stopId` se conserva intacto en `Room`, `DbusClient`, `LazyColumn` `key`, rutas de navegación y deep links (`dbus://stop/{stopId}`). En `Search.kt` puede mantenerse opcionalmente la coincidencia silenciosa por código si un usuario teclea el número de una marquesina, pero **sin mostrar ese código en el placeholder ni en la fila de resultado**.

### 2. Blindaje de Identificadores de Línea (Cero IDs Internos)
- [ ] Auditar `LinesScreen`, `LineDetailScreen`, `StopDetailScreen`, `HomeScreen`, `AlertsScreen` y `AlertEditScreen` para garantizar que **nunca** se renderiza un ID interno de Dbus (`internalId`, ej. `32`), ID de itinerario/sentido ni códigos técnicos autogenerados.
- [ ] Mostrar exclusivamente el **código público oficial** (`28`, `5`, `B4`, `B9`) dentro de `LineBadge` con su color oficial, acompañado siempre del **destino legible** cuando corresponda.
- [ ] Garantizar en el mapeo a UI (`Arrivals` / `Lines` / `StopDetailViewModel`) que cualquier alias interno recibido de la API se resuelve al código público mediante el catálogo (ADR 009) y añadir test unitario en `ScreenLogicTest` verificando que nunca se filtra un `stopId` ni un `internalId` en los textos visibles de los estados UI.

---

## Bloque B: Base de Tokens y Accesibilidad (Prerrequisito de Motion)
- [ ] Crear `ui/theme/MotionTokens.kt` y `ui/theme/DesignTokens.kt` con las constantes exactas de `contracts-templates/design-tokens.v1.template.json` (cero duraciones ni factores inline en las pantallas).
- [ ] Crear `ui/components/Motion.kt` con:
  - `LocalReducedMotion` alimentado desde `Settings.Global.ANIMATOR_DURATION_SCALE == 0f` (y `true` en `LocalInspectionMode.current`).
  - `rememberEffectiveMotionTokens()` que devuelve duraciones en `0` cuando `reducedMotion == true`.
  - `Modifier.pressScale(interactionSource)` operando exclusivamente en `graphicsLayer` (sin recomposiciones ni cambios de layout).

---

## Bloque C: Los 5 Quick Wins de Microanimaciones (Con Refinamientos Aplicados)

### 1. Transiciones Diferenciadas en `NavHost` (`AppShell.kt`)
- [ ] **Pestañas principales (`HOME`, `STOPS`, `LINES`, `ALERTS`):** Solo `fadeIn(tween(bottomTabFadeInMs))` y `fadeOut(tween(bottomTabFadeOutMs))`. Prohibido slide horizontal entre pestañas de la Bottom Bar.
- [ ] **Rutas de detalle (`stop/{stopId}`, `line/{lineId}`, `alert/new`, `alert/{id}/edit`, `settings`):**
  - Entrada: `slideIntoContainer(Towards.Start, tween(detailSlideInMs)) { (it * detailSlideFraction).toInt() } + fadeIn(tween(detailFadeInMs))`.
  - Salida / Pop: `slideOutOfContainer(Towards.End, tween(detailSlideOutMs)) { (it * detailSlideFraction).toInt() } + fadeOut(tween(detailFadeOutMs))`.
- [ ] Verificar compatibilidad visual con el gesto **Predictive Back** en Android 15/16 (`targetSdk 36`).

### 2. `FavoriteStar` con Física Spring, `72°` y Háptica (`Components.kt`)
- [ ] Reemplazar el toggle estático de la estrella (`★`) por:
  - Animación de escala `0.80f → 1.15f → 1.0f` (`Spring.DampingRatioMediumBouncy`, `StiffnessMedium`) aplicada vía `graphicsLayer`.
  - Rotación de **`72f`** grados al activar (simetría de 5 puntas, sin desenfoque de `360°`).
  - `animateColorAsState` entre estado inactivo y activo.
  - Disparo háptico nativo `LocalHapticFeedback.current.performHapticFeedback(HapticFeedbackType.TextHandleMove)` solo en interacción del usuario (no al recomponer).

### 3. Transición Suave `Skeleton → Contenido` (`HomeScreen` y `StopDetailScreen`)
- [ ] Envolver el renderizado de `ArrivalsView` (5 estados: `Loading`, `Live`, `Empty`, `Degraded`, `Error`) en **`AnimatedContent`**:
  ```kotlin
  AnimatedContent(
      targetState = arrivalsView,
      contentKey = { it::class },
      transitionSpec = {
          (fadeIn(tween(motion.arrivalsStateFadeInMs)) + slideInVertically(tween(motion.arrivalsStateFadeInMs)) { it / 16 })
              .togetherWith(fadeOut(tween(motion.arrivalsStateFadeOutMs)))
      },
      label = "arrivalsViewState"
  )
  ```
- [ ] Confirmar que `contentKey = { it::class }` evita re-animar la tarjeta en cada sondeo de 30 s cuando se mantiene en estado `Live`.

### 4. Botón Limpiar (`×`) en `SearchPill`
- [ ] Mostrar/ocultar el botón de limpiar búsqueda mediante `AnimatedVisibility(visible = query.isNotEmpty())` con `fadeIn + scaleIn(initialScale = 0.8f)` / `fadeOut + scaleOut(targetScale = 0.8f)` (`searchClearButtonMs = 160 ms`).

### 5. Entrada y Salida Suave de `InfoBanner`
- [ ] Animar banners de estado (`Degraded / STALE`, permiso de notificaciones en `AlertsScreen`, `notice` de config remota) con `AnimatedVisibility` usando `expandVertically() + fadeIn()` y `shrinkVertically() + fadeOut()` (`infoBannerMs = 200 ms`).

---

## Reglas de Control y Verificación
- **Alcance estricto (Boy Scout acotado):** Solo refactorizar valores inline en los archivos tocados por estas tareas (`AppShell.kt`, `Components.kt`, `HomeScreen.kt`, `StopsScreen.kt`, `LinesScreen.kt`, `LineDetailScreen.kt`, `StopDetailScreen.kt`, `AlertsScreen.kt`, `AlertEditScreen.kt`).
- **Lint `HardcodedText = ERROR`:** Todo `contentDescription` y texto visible usa `stringResource(...)` con paridad ES/EU o `null` si es decorativo.
- **Verificación:**
  - `./gradlew check assembleDebug` en verde (94+ tests sin timeouts + `StringsParityTest` + `lintDebug`).
  - Inspección visual en todas las pantallas (ES y EU): cero códigos de parada (`stopId`) ni IDs internos de línea visibles en pantalla.
  - Prueba manual con *"Quitar animaciones"* activado en Ajustes de Accesibilidad de Android: todas las transiciones colapsan instantáneamente sin parpadeos.