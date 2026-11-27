# DESIGN MANIFESTO — Bus Donostia (`Badator!`) — v4 (Contrato Técnico Ejecutable Congelado)

> **Estado:** v4 DEFINITIVA Y CONGELADA (Sprint 0.75 — Prueba de Estrés en Compose).
> **Base del sistema:** Jetpack Compose BOM `2025.12.01` · Material 3 · Kotlin `2.2.21` · `compileSdk/targetSdk 36` · `minSdk 26`.

---

## 1. Paleta Cromática y Jerarquía Tonal (WCAG AA Medido ≥ 5.0:1)

### 1.1 Tema Claro — *"Mañana en La Concha"*
| Token | Hex | Rol / Contraste verificado |
|---|---|---|
| `background` | `#F4F6F9` | Lienzo atmosférico frío |
| `surfaceContainerLowest` | `#FFFFFF` | Hero Cards y réplica de notificación |
| `surfaceContainerLow` | `#F9FAFC` | Barras inferiores fijas / contenedores suaves |
| `surface` / `surfaceContainer` | `#FFFFFF` | Tarjetas estándar (`SurfaceCard`) |
| `surfaceContainerHigh` | `#E9EEF5` | `FloatingNavDock`, `SearchPill`, píldoras de métrica |
| `surfaceContainerHighest` | `#DCE4EE` | Estado presionado / pistas inactivas |
| `onBackground` / `onSurface` | `#0F172A` | Texto primario (`16.49:1` / `17.85:1`) |
| `onSurfaceVariant` | `#475569` | Texto secundario (`7.58:1` en `#FFFFFF`, `6.50:1` en `High`) |
| `primary` | `#0052A8` | Acción primaria (`7.57:1` en `#FFFFFF`) |
| `onPrimary` | `#FFFFFF` | Texto sobre primario (`7.57:1`) |
| `primaryContainer` / `secondaryContainer` | `#E6F0FD` | Contenedor de selección y píldora activa |
| `onPrimaryContainer` / `onSecondaryContainer` | `#00448E` | Texto sobre contenedor activo (`8.23:1`) |
| `outline` / `outlineVariant` | `#64748B` / `#E2E8F0` | Contornos accesibles (`4.76:1`) y divisores hairline |
| `error` / `onError` | `#C81E1E` / `#FFFFFF` | Errores (`5.74:1`) |
| `StatusColors.live` | `#047857` | Indicador y texto GPS (`5.48:1` en `#FFFFFF`) |
| `StatusColors.liveContainer` / `onLiveContainer` | `#D1FAE5` / `#065F46` | Píldora inminente (`6.78:1`) |
| `StatusColors.warning` | `#B45309` | Aviso preventivo (`5.02:1` en `#FFFFFF`) |
| `StatusColors.warningContainer` / `onWarningContainer` | `#FEF3C7` / `#78350F` | Banner de advertencia (`8.15:1`) |
| `StatusColors.alarm` / `alarmContainer` / `onAlarmContainer` | `#C81E1E` / `#FEE2E2` / `#991B1B` | Modo alarma (`5.74:1` / `6.80:1`) |
| `StatusColors.scheduled` / `scheduledContainer` | `#475569` / `#EEF2F6` | Llegada programada (`7.58:1`) |
| `StatusColors.favorite` | `#D97706` | Estrella guardada (`3.19:1` icono) |

### 1.2 Tema Oscuro — *"Bahía Nocturna"*
| Token | Hex | Rol / Contraste verificado |
|---|---|---|
| `background` | `#070B12` | Lienzo nocturno profundo |
| `surfaceContainerLowest` | `#0B111C` | Huecos / fondo de skeleton |
| `surfaceContainerLow` | `#101827` | Barras inferiores fijas |
| `surface` / `surfaceContainer` | `#151F30` | Tarjetas estándar (`SurfaceCard`) |
| `surfaceContainerHigh` | `#1D2A40` | `FloatingNavDock`, `SearchPill`, píldoras de métrica |
| `surfaceContainerHighest` | `#263651` | Pistas activas / estados altos |
| `onBackground` / `onSurface` | `#F1F5F9` | Texto primario (`17.99:1` / `15.09:1`) |
| `onSurfaceVariant` | `#9DB0C9` | Texto secundario (`7.47:1` en `#151F30`, `6.51:1` en `High`) |
| `primary` / `onPrimary` | `#6EB2FF` / `#042347` | Acción primaria (`7.46:1` / `7.09:1`) |
| `primaryContainer` / `secondaryContainer` | `#17345A` | Contenedor de selección |
| `onPrimaryContainer` / `onSecondaryContainer` | `#DBEAFE` | Texto sobre contenedor (`10.27:1`) |
| `outline` / `outlineVariant` | `#7E93B0` / `#263449` | Contornos (`5.26:1`) y divisores |
| `error` / `onError` | `#F87171` / `#450A0A` | Errores (`5.98:1`) |
| `StatusColors.live` | `#34D399` | GPS en vivo (`8.60:1`) |
| `StatusColors.liveContainer` / `onLiveContainer` | `#064E3B` / `#A7F3D0` | Píldora inminente (`7.58:1`) |
| `StatusColors.warning` / `warningContainer` / `onWarningContainer` | `#FBBF24` / `#451A03` / `#FDE68A` | Banners (`9.90:1` / `12.03:1`) |
| `StatusColors.alarm` / `alarmContainer` / `onAlarmContainer` | `#F87171` / `#4C1118` / `#FECACA` | Modo alarma (`5.98:1` / `10.36:1`) |
| `StatusColors.scheduled` / `scheduledContainer` | `#9DB0C9` / `#1D2A40` | Horario (`7.47:1`) |
| `StatusColors.favorite` | `#FBBF24` | Estrella guardada (`9.90:1`) |

---

## 2. Tipografía Completa y Formas

### 2.1 `AppTypography` (15 roles M3 en `Plus Jakarta Sans`)
- `displayLarge` 57/64 W700, `displayMedium` 45/52 W700, `displaySmall` 36/44 W700
- `headlineLarge` 32/40 W700, `headlineMedium` 28/36 W700, `headlineSmall` 24/32 W700 (títulos de pantalla)
- `titleLarge` 20/28 W700, `titleMedium` 16/22 W700 (títulos de tarjeta), `titleSmall` 14/20 W600
- `bodyLarge` 16/22 W500, `bodyMedium` 14/20 W500, `bodySmall` 12/16 W500
- `labelLarge` 14/20 W600, `labelMedium` 12/16 W600, `labelSmall` 11/14 W600

### 2.2 Métricas Tabulares (`JetBrains Mono` + `fontFeatureSettings = "tnum"`)
- `CountdownHero`: 44sp / 48sp / W800 (Hero `NextAlertCard`, `NextBusHeroBoard`, Dial `AlertEdit`)
- `CountdownCard`: 24sp / 28sp / W800 (cuentas atrás secundarias destacadas)
- `CountdownRow`: 17sp / 22sp / W700 (filas de llegadas)
- `MonoClock`: 13sp / 18sp / W600 (`HH:mm`, índice de parada en itinerario, metadatos)

### 2.3 `AppShapes` (`ui/theme/Shape.kt`)
- `extraSmall = RoundedCornerShape(DesignTokens.cornerRadiusChip)` (`8.dp`)
- `small = RoundedCornerShape(DesignTokens.cornerRadiusBanner)` (`12.dp`)
- `medium = RoundedCornerShape(DesignTokens.cornerRadiusCard)` (`16.dp`)
- `large = RoundedCornerShape(DesignTokens.cornerRadiusHero)` (`22.dp`)
- `extraLarge = RoundedCornerShape(DesignTokens.cornerRadiusDock)` (`28.dp`)

---

## 3. Tokens Aditivos en `packages/contracts/design-tokens.json`

Se conservan íntegros todos los tokens actuales (sin romper `DesignTokensParityTest` ni `MotionTokensTest`) y se añaden:
- `motion.transitions.dockVisibilityMs`: `200`
- `motion.livePulse.shimmerDurationMs`: `1200`
- `style.spacingDp.xxl`: `32`
- `style.cornerRadiusDp.hero`: `22`
- `style.cornerRadiusDp.dock`: `28`
- `style.componentsDp`:
  - `floatingDockHeight`: `64`
  - `bottomDockClearance`: `96`
  - `accentRailWidth`: `4`
  - `heroSkeletonHeight`: `144`
  - `alertSkeletonHeight`: `116`
  - `illustrationSize`: `96`
  - `iconMedium`: `20`
  - `iconDefault`: `24`
  - `lineBadgeLarge`: `40`
  - `timelineColumnWidth`: `32`
  - `timelineStroke`: `4`

`GenerateDesignTokensTask`, `DesignTokensParityTest` y `MotionTokensTest` se actualizan en paralelo en Sprint 1. `ResolvedMotionTokens.ZERO` colapsa todos los campos `*Ms` a `0`.

---

## 4. Contrato de Rendimiento a 60/120 fps y Edge-to-Edge

1. **Estabilidad y Lazy Layouts:**
   - Todo `LazyColumn` declara `key` primitivo único y `contentType` explícito por tipo de ítem.
   - Prohibido consultar `LocalSharedTransitionScope` / `LocalAnimatedVisibilityScope` repetidamente sin necesidad; nunca crear `rememberInfiniteTransition` dentro de ítems fuera de vista sin compuerta `!reducedMotion && !LocalInspectionMode.current`.
2. **Fase de Dibujo y Cero Recomposiciones en Animación:**
   - `pressScale`, `FavoriteStar`, `LiveSignalDot`, `LiveRadarBeacon` y `staggerEntrance` operan **exclusivamente** en `Modifier.graphicsLayer { ... }` o `Modifier.drawWithCache`.
   - `LineDetailScreen` reemplaza el `Canvas` por fila con `Modifier.drawWithCache` para la espina dorsal del plano de tránsito.
   - `Illustrations.kt` y el shimmer de `SkeletonList` precalculan `Brush`/`Path` dentro de `Modifier.drawWithCache`.
3. **Edge-to-Edge, IME y Predictive Back:**
   - `MainActivity`: `enableEdgeToEdge()`, `window.isNavigationBarContrastEnforced = false` (API 29+), `android:windowSoftInputMode="adjustResize"` en `AndroidManifest.xml`.
   - `BusDonostiaTheme`: `SideEffect` sincroniza `isAppearanceLightStatusBars` y `isAppearanceLightNavigationBars` = `!darkTheme` ante cambios de tema en Ajustes.
   - `AppShell`: el `NavHost` no recorta con `Modifier.padding(innerPadding)` de `Scaffold`; en su lugar provee `LocalBottomDockInset` para `contentPadding` de listas, aplica `StatusBarScrim` arriba y oculta `FloatingNavDock` en rutas secundarias.
   - `PredictiveBackHandler` (`androidx.activity.compose`) limpia `query` en `HomeScreen` / `StopsScreen` / `LinesScreen` y cierra `pickingStop` en `AlertEditScreen`.
4. **Cero fugas de `stopId` ni datos falsos:**
   - Prohibido renderizar `stopId` o ID interno de línea en cualquier texto o `contentDescription`.
   - Prohibido interpolar segundos entre polls de 30 s.

---

## 5. Matriz de Estados Límite

| Estado / Escenario | Comportamiento Contratado |
|---|---|
| **Vacío (Home, Stops, Lines, Alerts)** | `EmptyState` con ilustración vectorial cívica (`Illustrations.kt`) + copy claro + CTA real navegable. |
| **1 elemento (`StopDetail` con 1 llegada)** | `NextBusHeroBoard` muestra esa llegada como protagonista; la lista inferior omite divisor vacío y renderiza solo atribución si aplica. |
| **35+ elementos (`LineDetail` o búsqueda)** | `staggerEntrance` limitado a `minOf(index, 8) * 35ms`; `LazyColumn` recicla con `key` compuesto `"$index-${stop.stopId}"`. |
| **Cargando** | `SkeletonList` con silueta coincidente (`HERO`, `CARD`, `ARRIVAL_ROW`) y shimmer tonal (estático bajo `LocalReducedMotion` / tests). |
| **Sin red / `API_ERROR` / `NO_SERVICE` / `STALE`** | `Column` dedicada dentro de `AnimatedContent` (cero solapamientos); mensajes con ilustración/icono semántico y CTA `Reintentar`. |
| **Aviso real de Dbus (`notice`)** | `InfoBanner` apilado verticalmente junto a `StatusMessage` o `STALE` sin solapamiento. |
| **Textos largos ES / EU / EN + `fontScale = 1.5x`** | Contenedores con `heightIn(min = ...)` (nunca altura fija); `maxLines = 1, overflow = Ellipsis` en títulos junto a métricas; `FloatingNavDock` con padding compacto y `Ellipsis`. |
| **Teclado / IME abierto** | `adjustResize` + `imePadding()` en pantallas con `SearchPill` o formulario (`A01`, `A02`, `A03`, `A06`). |
| **Gesto Predictive Back** | Transición lateral con `detailSlideFraction`; `PredictiveBackHandler` intercepta búsqueda activa y picker de parada. |
| **Reduced Motion (`ANIMATOR_DURATION_SCALE == 0f`)** | `MotionTokens.resolve(true)` colapsa duraciones a `0`, desactiva `rememberInfiniteTransition`, `sharedElement` y `stagger`. |

---

## 6. Mapa Exacto de Archivos (Sprints 1–4)

- **Sprint 1 (Tokens, Tema y Primitivas):**
  - `packages/contracts/design-tokens.json`, `apps/android/app/build.gradle.kts`
  - `apps/android/app/src/main/AndroidManifest.xml`, `MainActivity.kt`
  - `apps/android/app/src/main/res/values/colors.xml`, `res/values-night/colors.xml`
  - `ui/theme/{Color.kt, Type.kt, Shape.kt, Theme.kt}`
  - `ui/components/{TimeFormat.kt, Motion.kt, Components.kt, Illustrations.kt}`
  - `ui/screens/EmptyState.kt`
  - Tests de tokens: `DesignTokensParityTest.kt`, `MotionTokensTest.kt`
- **Sprint 2 (Experiencia Principal y Tiempo Real):**
  - `ui/home/HomeScreen.kt`
  - `ui/stopdetail/StopDetailScreen.kt`
- **Sprint 3 (Navegación y Flujos Secundarios):**
  - `ui/AppShell.kt`
  - `ui/stops/StopsScreen.kt`
  - `ui/lines/LinesScreen.kt`
  - `ui/alerts/{AlertsScreen.kt, AlertComponents.kt, AlertEditScreen.kt}`
  - `ui/settings/SettingsScreen.kt`
  - `ui/onboarding/OnboardingScreen.kt`
- **Sprint 4 (Tactilidad, Estados Límite, i18n, Previews y Ratchet):**
  - `res/values*/{strings.xml, strings_settings.xml}` (paridad ES/EU/EN + fix errata + copy onboarding)
  - `@Preview` y pulido en componentes y pantallas clave
  - `ui/theme/DesignTokensGuardTest.kt` (ratchet → `0`)
  - Nuevos tests Compose/Robolectric en `src/testDebug/kotlin/eus/busdonostia/app/ui/`
