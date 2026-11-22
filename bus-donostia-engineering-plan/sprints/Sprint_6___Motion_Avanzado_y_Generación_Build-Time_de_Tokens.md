# Sprint 6 — Motion Avanzado y Generación Build-Time de Tokens (`v1.1.0`)

**Objetivo:** Elevar la fluidez de la navegación contextual con *shared elements* sobre elementos legibles para el usuario (`LineBadge` y nombre de parada), añadir señalización GPS en vivo de alta legibilidad, mejorar listas interactivas y unificar los tokens de diseño en `packages/contracts/` con generación automática en *build-time* lista para iOS.

**Esfuerzo estimado:** ~12 horas · **Riesgo:** Medio · **Pre-requisito:** `v1.0.0` publicada y estable en Play Store (con códigos internos de parada/línea ya eliminados de la UI en Sprint 5.1).

---

## Tareas de Ingeniería

### 1. Fuente Única de Tokens en Monorepo (`packages/contracts/design-tokens.json`)
- [ ] Copiar `contracts-templates/design-tokens.v1.template.json` a `packages/contracts/design-tokens.json`.
- [ ] Registrar una tarea Gradle `generateDesignTokens` (análoga a `merge<Variant>Catalog`) que lea `packages/contracts/design-tokens.json` y genere `DesignTokens.kt` y `MotionTokens.kt` en `build/generated/source/tokens/`.
- [ ] Eliminar las copias manuales de `ui/theme/MotionTokens.kt` y `ui/theme/DesignTokens.kt` (fuente única garantizada, 0 ms de parseo en runtime, `@Preview` intacto).

### 2. Indicador GPS LIVE + Dígitos Tabulares (`MinutesLabel` / `LiveSignalDot`)
- [ ] Aplicar `fontFeatureSettings = "tnum"` a todos los contadores de minutos (`remainingMinutes`) y horas `HH:mm` (`JetBrains Mono` / `Plus Jakarta Sans`) en `HomeScreen`, `StopDetailScreen` y tarjeta *"TU PRÓXIMO AVISO"*.
- [ ] Implementar `LiveSignalDot` (`8dp`): pulso suave de opacidad (`1.0f ↔ 0.30f`, `1100 ms`, `FastOutSlowInEasing`) **solo** cuando `source == ArrivalSource.LIVE && !reducedMotion && !LocalInspectionMode.current`. Mantener el texto numérico siempre al `100%` de opacidad.
- [ ] Añadir `AnimatedMinutesText` con deslizamiento vertical sutil (`200 ms`) cuando cambia el número entero de minutos.

### 3. Transiciones `Modifier.sharedElement()` (`Home`/`Stops` → `StopDetail`)
- [ ] Envolver el `NavHost` en `SharedTransitionLayout` y exponer `LocalSharedTransitionScope` + `LocalAnimatedVisibilityScope`.
- [ ] Conectar **`LineBadge`** (solo código público de línea, ej. `28`, `B4`) y **`StopTitle`** (nombre legible de la parada; **no** reintroducir `StopCodePill` ni códigos numéricos) desde las tarjetas/filas de `HomeScreen` y `StopsScreen` hacia la cabecera de `StopDetailScreen`.
- [ ] **Regla anti-colisión obligatoria:** Usar claves compuestas con contexto de parada (`"line-${stopId}-${lineId}"` y `"stop-title-${stopId}"` — el `stopId` solo se usa como clave interna invisible) para evitar choques cuando la misma línea o un nombre de parada aparece en varias tarjetas.
- [ ] Desactivar `sharedElement()` automáticamente si `reducedMotion == true`.
- [ ] Probar específicamente la combinación: *shared element* + *Predictive Back* + llegada de un tick del `Ticker` de 30 s en mitad de la transición.

### 4. Interacciones de Lista y Controles
- [ ] **`LineDetailScreen` (A03b):**
  - `Crossfade(tween(lineDirectionCrossfadeMs))` al cambiar el chip de sentido.
  - Animación de entrada escalonada acotada a `minOf(index, 8) * 35ms` (nunca `index * 50ms` sin tope).
  - `Modifier.pressScale` en cada fila de parada del timeline (solo nombre legible, sin códigos).
- [ ] **TabChips y filtros (`StopsScreen`, `LinesScreen`, `StopDetailScreen`):** Indicador de selección animado con *spring* sin rebote + `Modifier.animateItem()` en los `LazyColumn` con `key` estable.
- [ ] **Pull-to-refresh personalizado:** Integrar indicador Material 3 PullToRefresh coherente con el color de marca y feedback háptico al alcanzar el umbral de disparo.
- [ ] **Badge de avisos (`AppShell.kt`):** Animar cambios en `alertBadgeCount` (`0..9+`) con `AnimatedContent` y escala *spring*.

---

## Criterios de Aceptación y Verificación
- `./gradlew check assembleDebug` en verde (confirmando que `LiveSignalDot` no cuelga ningún test de Robolectric).
- Inspección de rendimiento en APK `release` (dispositivo físico): cero *clipping* roto al volver atrás desde `StopDetailScreen` con gesto predictivo, cero códigos de parada o IDs internos visibles, y `< 16ms/frame` en scroll de `LineDetailScreen` (líneas de 35+ paradas).