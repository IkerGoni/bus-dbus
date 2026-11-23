# 01. Arquitectura de Configuración Externa y Tokens de Diseño

## 1. Objetivo
Eliminar *magic numbers* y valores dispersos en el código sin sacrificar:
- **Rendimiento de arranque y ejecución** (cero parseo de estilos en runtime, inlining por R8).
- **Flujo de desarrollo en Compose** (`@Preview` funcionando sin inicializar `AppContainer`).
- **Paridad futura con iOS** (una única fuente de verdad en el monorepo `packages/contracts/`).
- **Control de cambios con agentes de IA** (evitar refactorizaciones globales no solicitadas).

---

## 2. Separación en 3 Niveles

### Nivel 1: Parámetros Operativos y de Negocio (`config.json` — Runtime + Remoto HTTPS)
Todo parámetro que afecte a consumo de red, batería, frescura de datos, ventanas de avisos o límites funcionales va en `config.json`, gestionado por el `ConfigRepository` existente (bundled en `assets/config.json` + actualización remota HTTPS cada 12 h con validación defensiva).

**Qué incluye:**
- API: `apiBaseUrl`, `endpoints`, `params`, `headers`, `connectTimeoutSec`, `callTimeoutSec`, `remoteConfigCacheHours`, `remoteConfigTimeoutSec`.
- Sondeo (`polling`): `uiTickerSec` (30), `foregroundVisibleSec` (30), `baseBackgroundSec` (30), `noServiceOrErrorMultiplier` (2), y tabla `adaptiveTiers` (`>30m → 600s`, `15–30m → 240s`, `8–14m → 60s`, `≤7m → 30s`).
- Frescura (`freshness`): `freshMaxSec` (30), `staleMaxSec` (300), `liveFeedHistorySize` (3).
- Avisos (`alerts`): `triggerBufferSec` (30), `expirationDays` (7), `minMinutesAhead` (1), `maxMinutesAhead` (30), `maxBadgeCount` (9), `recurringWakeupBeforeMin` (25), `recurringExpiryAfterMin` (25).
- Geo y límites UI: `walkingSpeedKmh` (5.0), `homeFavoriteMaxArrivals` (3).

**Regla para `domain/` (pureza y KMP):**
Las funciones puras (`AlertEngine.pollDelayMs`, `Freshness`, `Geo`) **nunca** leen `assets/` ni `Context`. Reciben un sub-objeto inmutable (`PollingConfig`, `FreshnessConfig`, `AlertsConfig`) como parámetro explícito con valores por defecto idénticos a los de `assets/config.json` para simplificar tests unitarios.

**Validación defensiva en `ConfigRepository`:**
Si una configuración remota descargada por HTTPS viola invariantes mínimos, se descarta y se conserva la última válida o el archivo empaquetado:
- `baseBackgroundSec in 15..120`
- `freshMaxSec >= 10 && staleMaxSec > freshMaxSec`
- `adaptiveTiers` no vacío, con todos los `delaySec >= 15` y ordenado descendentemente por `minGapMin`.
- `expirationDays in 1..30` y `minMinutesAhead < maxMinutesAhead`.

---

### Nivel 2: Tokens de Animación y Diseño (`design-tokens.json` → Build-Time a `.kt` y `.swift`)
En lugar de parsear un `ui-config.json` en runtime al abrir la app, seguimos **exactamente el mismo patrón build-time que ya usa `merge<Variant>Catalog`**:

```text
packages/contracts/design-tokens.json   (Única fuente de verdad en el monorepo)
          │
          ├──► Tarea Gradle (generateDesignTokens) ──► MotionTokens.kt & DesignTokens.kt (R8 inline, 0 ms)
          └──► Script Swift (futuro iOS v2.0)      ──► MotionTokens.swift & DesignTokens.swift
```

**Transición pragmática por fases:**
- **En Sprint 5.1 (Pre-release):** Crear `ui/theme/MotionTokens.kt` y `ui/theme/DesignTokens.kt` manualmente con la misma estructura exacta de `contracts-templates/design-tokens.v1.template.json` para no añadir complejidad de build-script antes de publicar en Play Store.
- **En Sprint 6 (v1.1):** Mover el JSON a `packages/contracts/design-tokens.json` y añadir la tarea Gradle `generateDesignTokens` que genere los archivos Kotlin automáticamente (marcados como generados, sin editar a mano).

**Beneficios técnicos:**
1. **Coste 0 en runtime:** R8 inlina `const val` y primitives directamente en bytecode.
2. **`@Preview` intacto:** No depende de `Context`, `AssetManager` ni `AppContainer`.
3. **Reduced Motion trivial:** `MotionTokens.resolve(reducedMotion: Boolean)` devuelve una instancia con duraciones en `0` y pulsos desactivados.
4. **Paridad garantizada con iOS:** Cuando se cree la app SwiftUI, compartirá al milisegundo las mismas curvas, duraciones y radios de esquina.

---

### Nivel 3: Micro-ajustes Ópticos Locales (Inline permitido)
Quedan explícitamente exentos de extracción a config/tokens para evitar sobreingeniería:
- Identidades matemáticas neutras (`0`, `1`, `0f`, `1f`, `0.5f`).
- Índices de colecciones o contadores estructurales.
- Micro-ajustes ópticos únicos de un composable específico (ej. `1.dp` para un divisor hairline o `2.dp` de compensación baseline de Plus Jakarta Sans).
- Constantes de protocolo externas inmutables de la API de Dbus (ej. `tipo == 1.0` para GPS, `"DATOS"`, `"SIN_SERVICIO"`).

---

## 3. Referencia de Plantillas Incluidas
Ver los esquemas JSON completos y validados en:
- `contracts-templates/config.v2.template.json`
- `contracts-templates/design-tokens.v1.template.json`
