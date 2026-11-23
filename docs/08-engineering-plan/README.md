# Plan Maestro de Ingeniería: Bus Donostia (Android → KMP + iOS)

> **Estado de partida:** Post-Sprint 4 (merge `b8e08f8`), MVP Android v1 funcional con 94 tests JVM/Robolectric, Kotlin 2.2.21, Jetpack Compose BOM `2025.12.01`, `minSdk 26`, `targetSdk 36`, Room `2.8.4`, OkHttp `4.12.0` y ADR 009 vigente.

Este paquete consolida, audita y reconcilia todas las decisiones técnicas discutidas sobre **microanimaciones en Jetpack Compose, externalización de configuración, rendimiento extremo en Android, batería/background en Android 15, avisos recurrentes y preparación para una futura versión iOS**.

---

## 1. Índice del Paquete

```text
bus-donostia-engineering-plan/
├── README.md                                    # Este documento (valoración integral y razonamiento)
├── docs/
│   ├── 01-arquitectura-config-y-tokens.md       # Estrategia en 3 niveles (config.json + build-time tokens + inline)
│   ├── 02-guia-animaciones-y-graficos.md        # Política 0-Lottie, 6 refinamientos críticos, háptica y Robolectric
│   ├── 03-rendimiento-android.md                # R8 Full Mode, Baseline Profiles, Ticker aislado, 120 fps GPU
│   ├── 04-background-bateria-y-recurrentes.md   # Tail energy 4G/5G, cuota Android 15 (6h) y patrón AlarmManager
│   └── 05-roadmap-ios-y-kmp.md                  # KMP (commonMain) + SwiftUI nativo + APNs + Live Activities
├── sprints/
│   ├── SPRINT-5.0-release-rendimiento-config.md # Release Play Store + R8 + Baseline Profiles + config operativo
│   ├── SPRINT-5.1-quick-wins-animaciones.md     # 5 Quick Wins visuales pre-release (~4h) sin riesgos
│   ├── SPRINT-6-v1.1-motion-avanzado-y-tokens.md# v1.1 (~12h): generador build-time de tokens + sharedElement + pulse
│   ├── SPRINT-7-v1.2-recurrentes-y-marca.md     # v1.2 (~25h): ScheduledAlertEntity + AlarmManager + Mascota Canvas
│   └── SPRINT-8-v2.0-kmp-y-ios.md               # v2.0: Extracción KMP, Room KMP, SwiftUI nativo y backend APNs
├── agent-rules/
│   ├── AGENTS-addendum-config-y-rendimiento.md  # Reglas listas para anexar a AGENTS.md
│   └── compose-motion-SKILL.md                  # Skill lista para .claude/skills/compose-motion/SKILL.md
└── contracts-templates/
    ├── config.v2.template.json                  # Plantilla operativa (polling, freshness, alerts, timeouts)
    └── design-tokens.v1.template.json           # Fuente única en monorepo para generar .kt y .swift en build-time
```

---

## 2. Valoración Crítica de las Decisiones Previas

A lo largo de la revisión técnica surgieron propuestas muy sólidas y también **tensiones reales** entre flexibilidad, rendimiento, velocidad de entrega y comportamiento de agentes de IA. A continuación se justifica qué se adopta tal cual, qué se corrige y qué se descarta.

### A. Gráficos y Animaciones (Evaluación: Muy sólido, con 6 correcciones técnicas obligatorias)
* **Aciertos que se mantienen:**
  * **Cero Lottie en UI estándar:** Usar APIs nativas de Compose aporta `0 KB` al APK, cancelación natural de gestos y 60/120 fps en GPU.
  * **Posponer `sharedElement()` a v1.1:** Evita bloquear la salida a Play Store con bugs de clipping en listas que se actualizan cada 30 segundos.
  * **Sacar la mascota de `HomeScreen`:** En una app de utilidad urbana bajo presión de tiempo, la tarjeta *"TU PRÓXIMO AVISO"* debe dominar la jerarquía visual; la mascota aporta valor en estados vacíos (sin servicio nocturno, sin favoritos) y Onboarding.
* **Correcciones aplicadas respecto al briefing inicial:**
  1. **`NavHost` diferenciado (`AppShell.kt`):** No usar `slideIntoContainer` global; las 4 pestañas de Bottom Navigation usan *crossfade* puro (`150/100 ms`) y solo las rutas de detalle usan deslizamiento corto (`it / 6`) compatible con *Predictive Back* (`targetSdk 36`).
  2. **`AnimatedContent` en lugar de `AnimatedVisibility` para Skeleton → Contenido:** Con `contentKey = { it::class }` se evita que el card duplique altura durante la transición y que vuelva a animar cada 30 s con el `Ticker`.
  3. **`FavoriteStar` con rotación de `72°` (no `360°`):** Una estrella de 5 puntas es simétrica cada `72°`; girar `360°` en ~200 ms produce borrosidad óptica. Se reemplaza por `72°` + *spring* (`0.8f → 1.15f → 1f`) + háptica nativa.
  4. **Accesibilidad real en Android:** Sustituir el concepto web `prefersReducedMotion` por lectura de `Settings.Global.ANIMATOR_DURATION_SCALE == 0f` expuesta vía `CompositionLocal`.
  5. **Legibilidad exterior en `MinutesLabel` (v1.1):** No pulsar la opacidad del texto numérico (`0.6f → 1f`); mantener el número al `100%` con dígitos tabulares (`fontFeatureSettings = "tnum"`) y pulsar únicamente un punto indicador GPS (`8dp`).
  6. **Seguridad en tests Robolectric y lógica de eventos:** Condicionar cualquier `rememberInfiniteTransition` para no bloquear `waitForIdle()`; usar claves compuestas `line-${stopId}-${lineId}` en `sharedElement()`; acotar el stagger de `LineDetailScreen` a `minOf(index, 8) * 35ms`; y limitar partículas celebratorias a eventos *in-app* (el disparo real del aviso sucede en background vía `AlertMonitorService`).

---

### B. Externalización de Valores Inline (Evaluación: Excelente intención; corregido el mecanismo)
Inicialmente se evaluó mover **todo** (tiempos operativos, estilos y animaciones) a dos archivos JSON leídos en runtime (`assets/config.json` + `assets/ui-config.json`) y dar al agente una regla estricta de refactorizar cualquier literal inline que encontrara.

Al cruzar esa propuesta con los objetivos de **máximo rendimiento**, **estabilidad de Sprint 5** y **futuro iOS**, el análisis reveló dos problemas claros:
1. **Leer JSON de estilos/animaciones en runtime penaliza arranque y rompe `@Preview`:** Nadie cambia el radio de un botón o un *spring* en caliente por HTTPS, y Android Studio `@Preview` no inicializa `AppContainer`.
2. **Una regla "refactoriza cualquier literal que veas" provoca explosiones de diffs con agentes de IA:** En pleno cierre de Sprint 5, un agente que toca `StopDetailScreen` acabaría reescribiendo 10 archivos no relacionados y creando claves artificiales (`iconOpticalPaddingDp: 2`).

**Decisión final adoptada (Modelo en 3 Niveles):**

| Nivel | Qué incluye | Dónde vive | Mecanismo | Coste Runtime | Reutilizable en iOS |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Nivel 1 · Operativo** | Escalones `pollDelayMs`, `Ticker`, `Freshness`, timeouts, ventanas de aviso, `walkingSpeedKmh`. | `packages/contracts/config.json` → `assets/config.json` | Remoto HTTPS (cache 12h) + bundled fallback (`ConfigRepository`). | Mínimo (parseo asíncrono en IO). | **100 %** (mismo JSON). |
| **Nivel 2 · Diseño y Motion** | Duraciones, *springs*, escalas, *stagger*, espaciados, radios, tamaños de badges/nodos. | `packages/contracts/design-tokens.json` | **Generación en build-time** a `MotionTokens.kt` / `DesignTokens.kt` (y `DesignTokens.swift` en iOS), igual que `mergeCatalog`. En Sprint 5 se arranca con los archivos Kotlin y en v1.1 se conecta el generador desde `packages/contracts/`. | **0 ms / 0 KB** (constantes inlineadas por R8). | **100 %** (mismo JSON fuente). |
| **Nivel 3 · Óptico local** | Micro-ajustes de 1–2 dp de alineación tipográfica, bordes `1.dp`, identidades `0f`/`1f`, constantes de protocolo Dbus (`tipo == 1.0`). | Inline en el componente / parser | Directo en código, sin sobreingeniería. | **0 ms**. | N/A (específico de plataforma). |

> **Regla para el agente (Boy Scout acotado):** El agente **sí** debe extraer valores mágicos de Nivel 1 y Nivel 2 a su sitio correspondiente, pero **exclusivamente dentro de los archivos que modifica su tarea asignada**, nunca en barridos globales no solicitados.

---

### C. Batería, Background y Avisos Recurrentes (Evaluación: Imprescindible y bien diseñado)
* **Problema real:** El gasto energético no es la RAM del servicio sino el **tail energy del módem 4G/5G** (~10–15 s tras cada request HTTP) sumado al límite estricto de **6 horas / 24 horas** de `ForegroundService(dataSync)` en Android 15 (`targetSdk 36`).
* **Solución en dos tiempos:**
  * **Inmediato (Sprint 5.0):** Ampliar `AlertEngine.pollDelayMs` con escalones de **4 min** (gap 15–30 min) y **10 min** (gap > 30 min), gobernados desde `config.json`.
  * **Medio plazo (Sprint 7 / v1.2):** Implementar el patrón híbrido para avisos recurrentes diarios (`ScheduledAlertEntity` + hibernación `AlarmManager` 0% batería + vigilia corta de `AlertMonitorService` 20–25 min antes, con ventana de expiración de salvaguarda + `BOOT_COMPLETED`).

---

### D. Máximo Rendimiento Android + Futuro iOS (Evaluación: Arquitectura KMP + UI 100% Nativa)
* **Por qué NO Flutter / React Native / Compose Multiplatform para UI:** Si la prioridad explícita es **máximo rendimiento nativo** y la app Android ya está terminada en Jetpack Compose con 94 tests y `0` dependencias pesadas, la ruta óptima es **Kotlin Multiplatform (KMP) para `domain/` + parsers + Room KMP**, manteniendo **UI 100% nativa** (Jetpack Compose en Android y SwiftUI en iOS).
* **Implicación crítica de iOS descubierta en el análisis:** iOS **no permite** un servicio en primer plano persistente tipo `AlertMonitorService` (mata procesos de red en ~30 s). En Android se mantiene ADR 009 (cero servidor propio); cuando se construya iOS (v2.0), los avisos en segundo plano se apoyarán en el backend **`apps/bot`** ya existente en el monorepo + **APNs** + **Live Activities** (Dynamic Island / pantalla de bloqueo).
* **Inversión preventiva hoy (coste casi cero en Sprint 5.0):**
  1. Activar **R8 Full Mode** + **Baseline Profiles** en Android (mayor salto de rendimiento real: -30/40% arranque en frío y scroll sin *jank*).
  2. Aislar el `Ticker` de 30 s para que no recomponga pantallas completas.
  3. Blindar `domain/` eliminando cualquier import `java.time.*`, `java.util.Locale` o `String.format()` (usar `Long` epoch ms o `kotlinx.datetime`), dejando la migración a KMP trivial cuando llegue v2.0.

---

## 3. Matriz Consolidada de Sprints y Esfuerzo

| Sprint | Versión | Foco Principal | Esfuerzo Est. | Riesgo | Bloqueante para Play Store |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Sprint 5.0** | `v1.0.0` | Release Play Store (`REMOTE_CONFIG_URL`, firma, assets) + R8 Full Mode + Baseline Profiles + `config.json` v2 (escalones 4/10 min) + blindaje KMP en `domain/`. | 1–2 días | Bajo | **Sí** |
| **Sprint 5.1** | `v1.0.0` | 5 Quick Wins de animaciones + `MotionTokens.kt` / `DesignTokens.kt` + `ANIMATOR_DURATION_SCALE`. | ~4 h | Bajo | Recomendado pre-release |
| **Sprint 6** | `v1.1.0` | Generador build-time `packages/contracts/design-tokens.json` → `.kt` + `sharedElement()` seguro + punto GPS pulsante + `tnum` + stagger acotado + pull-to-refresh. | ~12 h | Medio | No (Post-launch) |
| **Sprint 7** | `v1.2.0` | Avisos recurrentes (`ScheduledAlertEntity` + `AlarmManager`) + Mascota Canvas (empty/onboarding) + partículas *in-app* + wizard direccional `AlertEditScreen`. | ~25 h | Medio | No (Retención & Marca) |
| **Sprint 8** | `v2.0.0` | Módulo KMP compartido (`XCFramework`), Room KMP, Ktor (OkHttp/Darwin), app iOS SwiftUI (`DesignTokens.swift`) + avisos vía `apps/bot` + APNs + Live Activities. | 4–6 semanas | Alto | No (Expansión iOS) |

---

## 4. Cómo Usar Este Paquete con tus Agentes de Código

1. Copia `agent-rules/AGENTS-addendum-config-y-rendimiento.md` dentro del `AGENTS.md` de tu repositorio.
2. Instala `agent-rules/compose-motion-SKILL.md` en `.claude/skills/compose-motion/SKILL.md` (o `.agents/skills/compose-motion/SKILL.md`).
3. Ejecuta los sprints en orden estricto (`SPRINT-5.0` → `SPRINT-5.1` → `SPRINT-6` → `SPRINT-7` → `SPRINT-8`). Cada archivo de sprint contiene **criterios de aceptación, archivos exactos a tocar, pruebas exigidas y verificación `./gradlew check assembleDebug`**.
