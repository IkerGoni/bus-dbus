# Ticket A5-11 — Barrido de dp inline a DesignTokens

**Estado:** Planificado (post-publicación o refactorización dedicada)  
**Severidad:** Nivel 2 (Reglas AGENTS.md §1: Política de Configuración y Tokens)  
**Archivos afectados:** 11 archivos en `apps/android/app/src/main/kotlin/eus/busdonostia/app/ui/`  
**Ocurrencias totales detectadas:** 133 literales `XX.dp`  

---

## 1. Contexto y Regla Violada

Según `AGENTS.md` (§1 Nivel 2 · Animaciones y Tokens Visuales):
> *Queda prohibido declarar inline duraciones, fracciones de desplazamiento, escalas, constantes de spring, parámetros de stagger, opacidades de pulso o espaciados/radios estándar. Todos estos valores se consumen desde `MotionTokens` y `DesignTokens`.*

Excepciones permitidas (Nivel 3):
- Identidades matemáticas (`0`, `0.dp`).
- Micro-ajustes ópticos locales irrepetibles (`1.dp` hairline, `2.dp` ajuste óptico de fuente).

Actualmente existen 133 usos de `.dp` inline en la capa `ui/` (excluyendo `ui/theme/`), repartidos en componentes y pantallas que fueron desarrollados antes de la consolidación de `design-tokens.json` en build-time.

---

## 2. Inventario por Archivo (153 ocurrencias restantes; `SettingsScreen.kt` ya migrado)

| Archivo | Ocurrencias `XX.dp` | Principales valores |
|---|---|---|
| `ui/home/HomeScreen.kt` | 32 | `6.dp`, `8.dp`, `12.dp`, `16.dp`, `24.dp`, `36.dp`, `56.dp`, `120.dp` |
| `ui/alerts/AlertEditScreen.kt` | 30 | `4.dp`, `6.dp`, `8.dp`, `12.dp`, `16.dp`, `20.dp`, `24.dp`, `48.dp`, `56.dp` |
| `ui/stops/StopsScreen.kt` | 27 | `4.dp`, `8.dp`, `12.dp`, `16.dp`, `24.dp`, `48.dp`, `56.dp` |
| `ui/lines/LinesScreen.kt` | 25 | `4.dp`, `6.dp`, `8.dp`, `12.dp`, `16.dp`, `24.dp`, `48.dp`, `56.dp` |
| `ui/alerts/AlertsScreen.kt` | 20 | `8.dp`, `12.dp`, `16.dp`, `24.dp`, `48.dp` |
| `ui/onboarding/OnboardingScreen.kt` | 13 | `8.dp`, `12.dp`, `16.dp`, `24.dp`, `48.dp` |
| `ui/settings/SettingsScreen.kt` | 0 (migrado) | `DesignTokens.spacingSm`, `spacingLg`, `spacingXl`, `touchTargetMin` |
| `ui/components/EmptyState.kt` | 3 | `16.dp`, `24.dp`, `48.dp` |
| `ui/alerts/AlertComponents.kt` | 3 | `8.dp`, `12.dp`, `16.dp` |

---

## 3. Tabla de Mapeo a `DesignTokens`

| Valor Inline | Token Canónico en `DesignTokens` | Equivalente en `design-tokens.json` |
|---|---|---|
| `2.dp` | Excepción Nivel 3 (ajuste óptico) o `DesignTokens.spacingXxs` | `spacingDp.xxs` |
| `4.dp` | `DesignTokens.spacingXs` | `spacingDp.xs` |
| `8.dp` | `DesignTokens.spacingSm` | `spacingDp.sm` |
| `12.dp` | `DesignTokens.spacingMd` | `spacingDp.md` |
| `16.dp` | `DesignTokens.spacingLg` | `spacingDp.lg` |
| `24.dp` | `DesignTokens.spacingXl` | `spacingDp.xl` |
| `36.dp` | `DesignTokens.lineBadgeSize` | `componentsDp.lineBadgeSize` |
| `48.dp` | `DesignTokens.touchTargetMin` | `componentsDp.touchTargetMin` |
| `56.dp` | `DesignTokens.headerMinHeight` | `componentsDp.headerMinHeight` |
| `120.dp` | `DesignTokens.skeletonCardHeight` (añadir a `design-tokens.json`) | `componentsDp.skeletonCardHeight` |

---

## 4. Estrategia de Ejecución (Sin romper Boy Scout)

1. **Guardia Ratchet (`DesignTokensGuardTest.kt`):**
   - Asegura que el número de literales `dp` inline (excluyendo excepciones permitidas `0.dp`, `1.dp`, `2.dp` y `ui/theme/`) **no puede superar 133**.
   - Si un desarrollador añade un nuevo `16.dp` inline en lugar de usar `DesignTokens.spacingLg`, el test falla.
2. **Migración por Pantalla:**
   - Sprint de refactorización dedicado: pantalla a pantalla reemplazando los literales por tokens.
   - En cada pantalla migrada, se decrementa el límite del test guardián.
3. **Cierre del Ticket:**
   - Cuando el contador llega a 0 (excepto excepciones Nivel 3), el test fija `maxAllowed = 0`.
