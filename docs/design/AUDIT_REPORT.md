# AUDIT REPORT — Auditoría Adversarial de Diseño e Ingeniería (Sprint 5)

> **Alcance:** Auditoría archivo por archivo de toda la capa UI rediseñada en `apps/android/app/src/main/kotlin/eus/busdonostia/app/ui/` y recursos `res/` frente a `DESIGN_MANIFESTO.md` v4, `.agents/rules/compose-design-bridge.md`, `jetpack-compose-performance`, `better-colors`, `better-layout`, `better-motion`, `ui-ux-pro-max` y la salida real de `./gradlew lintDebug` (`0` errores, `38` warnings).

---

## Resumen por Áreas

1. **Dilución creativa y tokens:** `DesignTokensGuardTest` en `0` literales inline `> 2.dp` y `0` `tween()` inline; sin embargo, `FloatingNavDock` (`AppShell.kt`) usa un coeficiente de escala `0.85f` y una elevación `spacingSm` (`8.dp`) ligeramente pesada frente al resto del sistema tonal.
2. **Rendimiento Compose (`jetpack-compose-performance`):** detectados 3 cuellos de botella reales en listas (`IntrinsicSize.Min` en `AccentRailCard` y `TimelineRow`, asignación por fotograma de `Brush.linearGradient` en `SkeletonList` y `StatusBarScrim`, y lookups repetidos de `LocalHapticFeedback.current` dentro de ítems lazy).
3. **Accesibilidad y ergonomía en la calle:** en `NextAlertCard` (`HomeScreen.kt`) y `NextBusHeroBoard` (`StopDetailScreen.kt`), el destino comparte una sola fila horizontal con `CountdownHero` (`42.sp`), comprometiendo el espacio de lectura en pantallas de `360 dp` a `fontScale = 1.5x`. Además, el icono de línea nocturna en `LinesScreen.kt` carece de `contentDescription`.
4. **Paridad i18n y Lint:** `StringsParityTest` y `MissingTranslation`/`ExtraTranslation`/`HardcodedText` en `0` errores, pero `lintDebug` reporta 3 warnings `ModifierParameter` en `SkeletonList`, `InfoBanner` y `EmptyState`, más el warning KT-73255 en `Destination.kt` y un import sin uso en `AlertEditScreen.kt`.
5. **Contratos funcionales:** verificación íntegra sin regresiones en `domain/`, `data/`, `engine/`, `AppDatabase`, `AlertMonitorService`, `AlertNotifications`, deep link `dbus://stop/{stopId}?highlightLine={line}`, permisos (`POST_NOTIFICATIONS`, ubicación con rationale), `AlertModeSelector` y `Preview` (`AlertModeUiTest` + `ScreenLogicTest` + `DesignTokensParityTest` en verde).

---

## Hallazgos Concretos (A1–A10)

### A1 · [HIGH · Performance / Layouts] `IntrinsicSize.Min` y nodo extra en `AccentRailCard` — **[FIXED]**
- **Archivo:** `apps/android/app/src/main/kotlin/eus/busdonostia/app/ui/components/Components.kt` (`AccentRailCard`)
- **Problema:** `Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min))` junto a un `Box` hijo para el raíl lateral de `4.dp` fuerza medición intrínseca de doble pasada en cada ítem reciclado de `LazyColumn` (`LinesScreen`, `AlertsScreen`).
- **Remediación [FIXED]:** eliminados `IntrinsicSize.Min` y el `Box` interno; el raíl lateral se dibuja directamente en `Modifier.drawBehind` (con soporte RTL) y se añade `padding(start = railWidth)` al contenido en `Components.kt`.

### A2 · [HIGH · Performance / Graphics] Asignación de `Brush.linearGradient` por fotograma en `SkeletonList` — **[FIXED]**
- **Archivo:** `apps/android/app/src/main/kotlin/eus/busdonostia/app/ui/components/Components.kt` (`SkeletonList`)
- **Problema:** `Brush.linearGradient(...)` se instancia dentro de `onDrawBehind` usando `shimmerOffset.value`, generando asignaciones en cada frame de animación (`60–120 fps`).
- **Remediación [FIXED]:** `shimmerBrush` cacheado en el bloque de inicialización de `Modifier.drawWithCache` y trasladado en `onDrawBehind` mediante `withTransform({ translate(left = shift) })` en `Components.kt`.

### A3 · [HIGH · Performance / Layouts] `IntrinsicSize.Min` y `Box` adicional en `TimelineRow` — **[FIXED]**
- **Archivo:** `apps/android/app/src/main/kotlin/eus/busdonostia/app/ui/lines/LinesScreen.kt` (`TimelineRow`)
- **Problema:** cada fila de un itinerario largo (35+ paradas) usa `Modifier.height(IntrinsicSize.Min)` y un `Box` extra para la espina de tránsito.
- **Remediación [FIXED]:** eliminados `IntrinsicSize.Min` y el `Box` interno; la espina y los nodos se dibujan directamente sobre el `Row` mediante `Modifier.drawWithCache` (con soporte RTL) + `Spacer(Modifier.width(columnWidthToken))` en `LinesScreen.kt`.

### A4 · [MEDIUM · Lint / Compose API] 3 warnings `ModifierParameter` en `lint-results-debug.txt` — **[FIXED]**
- **Archivos:** `Components.kt` (`SkeletonList`, `InfoBanner`), `EmptyState.kt` (`EmptyState`)
- **Problema:** `lintDebug` marca `ModifierParameter` porque `modifier: Modifier = Modifier` no es el primer parámetro opcional en esas tres funciones.
- **Remediación [FIXED]:** reordenado `modifier: Modifier = Modifier` como primer parámetro opcional en `SkeletonList`, `InfoBanner` (`Components.kt`) y `EmptyState` (`EmptyState.kt`).

### A5 · [MEDIUM · Visual Craft & Graphics] `StatusBarScrim` sin `drawWithCache` y elevación/escala en `FloatingNavDock` — **[FIXED]**
- **Archivos:** `Motion.kt` (`StatusBarScrim`), `AppShell.kt` (`FloatingNavDock`, `FloatingDockItem`)
- **Problema:** `StatusBarScrim` recrea `Brush.verticalGradient` dentro de `drawBehind` y consulta `LocalDensity.current` dos veces; `FloatingNavDock` usa `shadowElevation = DesignTokens.spacingSm` (`8.dp`) y un literal `0.85f` en la altura de la píldora activa.
- **Remediación [FIXED]:** `StatusBarScrim` cachea el `Brush` en `drawWithCache` con una sola lectura de `LocalDensity.current` (`Motion.kt`); `FloatingNavDock` reduce `shadowElevation` a `DesignTokens.spacingXs` y `FloatingDockItem` usa `motion.chipIndicatorStartScale` (`AppShell.kt`).

### A6 · [MEDIUM · Ergonomía de Calle & `1.5x` Font Scale] Compresión horizontal en Hero Boards — **[FIXED]**
- **Archivos:** `HomeScreen.kt` (`NextAlertCard`), `StopDetailScreen.kt` (`NextBusHeroBoard`)
- **Problema:** `LineBadge`, el título de destino y el `CountdownHero` (`42.sp`) comparten la misma `Row` horizontal. A `fontScale = 1.5x` en anchos de `360 dp`, el destino se recorta agresivamente a pocos caracteres.
- **Remediación [FIXED]:** separadas la fila superior de identidad (`LineBadge` + destino `maxLines = 2`) y la fila focal de cuenta atrás `CountdownHero` en `HomeScreen.kt` (`NextAlertCard`) y `StopDetailScreen.kt` (`NextBusHeroBoard`).

### A7 · [MEDIUM · Performance / Lazy Items] Lookups de `LocalHapticFeedback.current` dentro de ítems lazy — **[FIXED]**
- **Archivos:** `AlertsScreen.kt` (`AlertCard`), `HomeScreen.kt` (`NextAlertCard`)
- **Problema:** `LocalHapticFeedback.current` se consulta dentro de ítems repetidos en `LazyColumn` en lugar de elevarse a la pantalla contenedora.
- **Remediación [FIXED]:** elevado `LocalHapticFeedback.current` a `AlertsContentView` (`AlertsScreen.kt`) y `HomeContent` (`HomeScreen.kt`).

### A8 · [LOW · Motion Craft] Pulso artificial `1f - 0.0001f` en `FavoriteStar` — **[FIXED]**
- **Archivo:** `Components.kt` (`FavoriteStar`)
- **Problema:** `animateFloatAsState` usa `targetValue = if (checked) 1f else 1f - 0.0001f` para re-disparar keyframes al activar la estrella.
- **Remediación [FIXED]:** sustituido por `Animatable(1f)` + `LaunchedEffect(checked)` con seguimiento de `previousChecked` para animar solo la transición `false -> true` en `Components.kt`.

### A9 · [LOW · Compiler & Code Hygiene] Warning KT-73255 en `Destination.kt` e import sin uso — **[FIXED]**
- **Archivos:** `Destination.kt:19`, `AlertEditScreen.kt`
- **Problema:** el compilador Kotlin emite warning KT-73255 por `@StringRes val label: Int` en constructor primario; `AlertEditScreen.kt` conserva un import sin uso.
- **Remediación [FIXED]:** cambiado a `@param:StringRes val label: Int` en `Destination.kt` y limpiados imports muertos en `AlertEditScreen.kt` y `Components.kt`.

### A10 · [LOW · Accesibilidad] Icono nocturno sin descripción semántica en `LineCard` — **[FIXED]**
- **Archivo:** `LinesScreen.kt` (`LineCard`)
- **Problema:** `Icons.Outlined.NightsStay` tiene `contentDescription = null`, ocultando a TalkBack que la línea es nocturna en la pestaña `Todas`.
- **Remediación [FIXED]:** asignado `contentDescription = stringResource(R.string.lines_filter_night)` en `LinesScreen.kt`.
