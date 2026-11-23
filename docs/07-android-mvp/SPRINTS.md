# Plan de sprints — Android MVP v1

Fuente: ADR 009, `docs/` de este paquete y `prompts/PROMPT-IMPLEMENTACION.md`.
Un sprint = una rama `android/sprint-N` integrada en `main` al cerrar.
Verificación común: `cd apps/android && ./gradlew check assembleDebug` (lint +
tests JVM + APK debug). Estado de sesión: [`ESTADO.md`](../../ESTADO.md).

> Los §Sprint 3 y §Sprint 4 conservan el alcance original (recurrentes,
> «Posponer») como registro. Desde el 2026-11-22 los avisos son de un solo uso:
> ver «Cambio de Iker» en §Sprint 4.

| Sprint | Objetivo | Cierra brechas | Estado |
|---|---|---|---|
| 0 | Esqueleto verde | B4, B8, B14 (base) | hecho 2026-11-22 |
| 1 | Datos y dominio | B1, B3, B12, B15, B16 | hecho 2026-11-22 |
| 2 | Consulta: Paradas, Detalle, Líneas | B1, B2, B9, B11 | hecho 2026-11-22 |
| 3 | Inicio, Avisos, Ajustes, Onboarding | B5, B10, B13 | hecho 2026-11-22 |
| 4 | Motor de avisos y notificaciones | B6 | hecho 2026-11-22 |
| 5 | Publicación: release firmado, ficha de Play, auditoría de secretos | — | **parcial** (auditoría adversarial 2026-11-23: NO SHIP, ver abajo) |
| 6 | v1.1.0: tokens generados en build-time y motion avanzado | — | hecho 2026-11-23 (`03884b6`…`11b7b19`) |

## Sprint 0 · Esqueleto verde
- Toolchain: JDK 17 + Android SDK (platform 36, build-tools, platform-tools).
- `apps/android/`: Gradle Kotlin DSL + version catalog + wrapper, módulo `app`
  (`eus.busdonostia.app`), minSdk 26, targetSdk 36, Compose + Material 3.
- Tema claro/oscuro desde `design-system/design-tokens.json`; fuentes
  empaquetadas Plus Jakarta Sans y JetBrains Mono (OFL).
- `NavigationBar` fija de 4 destinos (Inicio · Paradas · Líneas · Avisos) con
  pantallas vacías que muestran su estado `empty`; edge-to-edge.
- `strings.xml` `es` (por defecto) + `eu`; lint `MissingTranslation` y
  `HardcodedText` como error.
- Cleartext solo para `app.dbus.eus` (network security config).
- CI: workflow de GitHub Actions con `./gradlew check`.
- **DoD:** `./gradlew check` verde en local y APK debug generado.

## Sprint 1 · Datos y dominio
- Tarea Gradle que fusiona `apps/bot/data/dbus-catalog.json` +
  `dbus-stop-coordinates.json` en `assets/catalog.json`; test de conteos
  548 / 514 / 39 / 127.
- `ApiConfig`: `assets/config.json` + descarga remota opcional (12 h, 5 s,
  validación, fallback). URL remota vacía hasta Sprint 5.
- Cliente `tiemposParada` → `StopFeed` (`OK`, `NO_SERVICE`, `EMPTY`,
  `API_ERROR`) contra fixtures reales de `packages/contracts/fixtures/`.
- Room: favoritos (3 tipos), avisos (dedup `(stopId, lineId)`), recientes (10),
  colores de línea vistos en vivo.
- Dominio puro: `remainingMinutes`, `computeAlertStatus`, `computeFreshness`,
  `haversineMeters`, matching con preferencia GPS, identidad de línea
  (`32`→`28`, gana la pública). Tests de la tabla §5 de `02-domain-contracts.md`.
- Reloj reactivo de 1 s.
- **DoD:** tests de dominio y parser verdes; paridad con `@dbus/core` en los
  fixtures compartidos.
- **Resultado (2026-11-22):** 66 tests JVM verdes (avisos §5 13, dominio 8, Room 12, config 11,
  parser 8, cliente 5, catálogo 4, i18n 2, reloj 3 — por variante). Fixtures
  reales nuevos en `packages/contracts/fixtures/` (ver su `README.md`); `DATOS`
  vacío aún sin capturar. Sin UI nueva.

## Sprint 2 · Consulta
- A02 Paradas: buscador offline, Cercanas (GPS coarse, top 20 Haversine),
  Favoritas, Recientes, permiso de ubicación no bloqueante.
- A05 Detalle: llegadas en vivo con refresco 30 s, 5 estados, chips de línea,
  ★ persistente, `Cómo llegar` (`geo:`). La 🔔 por fila llega en Sprint 3
  junto con A06 (nada de botones sin acción mientras tanto).
- A03 Líneas y A03b Itinerario desde el catálogo.
- **DoD:** probado en emulador contra la API real; capturas en `docs/07-android-mvp/capturas/`.
- **Resultado (2026-11-22):** `AppContainer` en `BusDonostiaApp` (sin DI); A02, A05,
  A03 y A03b con ViewModels y lógica pura (`domain/Search.kt`, `domain/StopView.kt`).
  16 tests JVM nuevos por variante (búsqueda con paridad contra `packages/core/dist`,
  cercanas, 5 vistas de A05 con fixtures reales, filtros de A03, favoritas).
  Probado en el AVD `dbus36` contra `app.dbus.eus`: 340 en vivo, parada 1
  `SIN_SERVICIO` con su motivo real, datos viejos (`STALE`) y error en modo avión.
  19 capturas en `capturas/sprint-2/`.

## Sprint 3 · Inicio, Avisos, Ajustes, Onboarding
- A06 Configurar aviso (ONCE / RECURRING con franja), dedup y guardado; 🔔 por
  fila y botón `Crear aviso` en A05.
- A04 Mis avisos (5 estados, switch, editar, borrar con Deshacer).
- A01 Inicio (próximo aviso + favoritas en vivo, Posponer/Pausar reales).
- A07 Ajustes (tema, idioma, datos reales, fuentes, permisos) y A08 Onboarding.
- Badge de Avisos con `computeAlertStatus`.
- **DoD:** todas las pantallas con sus 5 estados; cero literales; capturas.
- **Resultado (2026-11-22):** A06 (`ui/alerts/AlertEditScreen.kt`) con borrador,
  validación, presets y dedup puros en `domain/AlertDraft.kt`; A04, A01 y badge
  sobre `domain/AlertList.kt` (`alertItem` → `computeAlertStatus`, orden de §3).
  Respuestas en vivo compartidas en memoria (`data/live/LiveFeeds.kt`, siempre
  vía `stopArrivalsView`); `AppContainer.fetchStop` es la única puerta a la API.
  A07 y A08 con AppCompat (idioma por app y tema). 13 tests JVM nuevos por
  variante (95). Probado en el AVD `dbus36` contra la API real: 🔔 en 340 →
  ONCE guardado → `Llegando`, Posponer/Pausar, dedup con «Actualizar», RECURRING
  con franja en parada 1 (`Sin servicio`), Deshacer, es/eu, claro/oscuro y modo
  avión. 31 capturas en `capturas/sprint-3/`. Sin motor (Sprint 4).

## Sprint 4 · Motor de avisos
- Foreground Service `dataSync` (sondeo agrupado por parada; adaptativo desde
  `android/favoritas-sondeo`, ver abajo).
- Canales y notificación; deep link `dbus://stop/{stopId}`.
- `POST_NOTIFICATIONS` con fallback a ajustes.
- **DoD:** flujo completo en emulador: cercana → A05 → 🔔 → guardar →
  notificación real.
- **Resultado (2026-11-22):** decisión pura en `domain/AlertEngine.kt`
  (`evaluateTick`: disparar / seguir / parar, 60 min sin servicio).
  `engine/AlertMonitorService` (FGS `dataSync`, una petición por parada vía
  `fetchStop`, sin repetir lo que la UI acaba de consultar, `onTimeout` de
  Android 15), canales `dbus_monitoring` / `dbus_arrivals_high`, deep link
  `dbus://stop/{stopId}?highlightLine={lineId}`, aviso en A04 si faltan
  notificaciones. `AppContainer` arranca el motor en cada cambio de avisos y al
  abrir la app.
- **Cambio de Iker en la misma sesión:** los avisos son **de un solo uso**:
  saltan una vez y se borran. Fuera los recurrentes (días, franja, alarmas
  exactas, receptores de boot/hora) y los botones `Posponer 5'` / `Descartar` de
  la notificación, y `Posponer 5'` de Inicio (solo queda `Pausar`). Room v2 (`MIGRATION_1_2`)
  borra recurrentes y avisos ya disparados. 93 tests JVM por variante (con
  test de migración). Probado en el AVD `dbus36` contra la API real con la app
  en segundo plano: 344 → 🔔 L16 → «Vigilando L16 en Pio Baroja 15» →
  notificación `L16 → Igeldo en 5 min` sin botones → el aviso desaparece de A04
  y el servicio se para; Inicio solo con `Pausar`. 11 capturas en `capturas/sprint-4/`.
- **Tras el Sprint 4** (rama `android/favoritas-sondeo`, merge `b8e08f8`): ★
  por llegada en A05 (favorita parada+línea, `STOP_LINE`, junto a la 🔔) y
  sondeo adaptativo del motor: cada 30 s si faltan ≤ 7 min para avisar, 1 min
  hasta 14, 2 min más allá (y 1 min sin datos de la línea); avisa 30 s antes
  (`NOTIFY_EARLY_MS`). La UI visible sigue a 30 s. 94 tests JVM por variante.

## Sprint 5 · Publicación
- Auditar el historial de git en busca de secretos (token del bot: nunca
  commiteado según `ESTADO.md`; verificarlo), aunque el repo siga privado.
- **Repo privado hasta que Iker decida** (2026-11-22); licencia también
  pendiente. El `config.json` remoto no puede salir de un repo privado: su URL se
  queda vacía (solo la copia empaquetada) hasta que Iker elija hosting.
- Nombre/icono no oficiales, firma de release, ficha de Play (FGS `dataSync`,
  ubicación), pista de pruebas internas.
- **DoD:** el de [`prompts/SPRINT-5.md`](prompts/SPRINT-5.md): `./gradlew check
  assembleDebug assembleRelease` verde y APK release con el flujo de avisos
  funcionando. La config remota se valida solo si Iker elige hosting.

**Resultado (2026-11-23, CORREGIDO tras la auditoría adversarial):** el Sprint 5
se dio por cerrado autónomamente, pero la auditoría
([`A5-01…A5-18`](../auditoria/2026-11-sprint5-adversarial.md)) demostró que
varias afirmaciones eran falsas o parciales. **Estado: parcial / NO SHIP** hasta
cerrar A5-01 (token), A5-02 (filtro de destino), A5-03 (ficha eu) y A5-08
(nombre). Seguimiento: [`remediacion`](../auditoria/2026-11-sprint5-remediacion.md).
El Sprint 5 no cambió código de `apps/android/**`; su diff fue solo de docs.

1. **Auditoría de secretos** → **FALSA en su versión original.** El token real
   del bot estaba en el blob borrado `bot-code.zip` (commit `bef1873`, en
   `origin/main`); `git grep` no mira dentro de los zip. Informe corregido:
   [`auditoria-secretos.md`](auditoria-secretos.md). Token rotado por Iker el
   2026-11-23 (verificado); purga del historial pendiente de su decisión.
2. **Firma de release** → el código es correcto y falla con mensaje claro si
   faltan las claves (reproducido por el auditor). Los nombres de propiedad que
   citaba este doc no existían: los reales son `dbus.release.storeFile`,
   `dbus.release.storePassword`, `dbus.release.keyAlias`,
   `dbus.release.keyPassword` en `~/.gradle/gradle.properties`, o las variables
   `DBUS_RELEASE_STORE_FILE`, `DBUS_RELEASE_STORE_PASSWORD`,
   `DBUS_RELEASE_KEY_ALIAS`, `DBUS_RELEASE_KEY_PASSWORD` (ver
   `apps/android/README.md`, que siempre fue correcto). Flag
   `-Pdbus.release.unsigned=true` construye el APK sin firmar.
3. **Build + R8** → `check assembleDebug assembleRelease -Pdbus.release.unsigned=true`
   verde. «127 tests por variante» era falso: el auditor midió **144 (debug) /
   138 (release)**; tras la remediación son **152 (debug) / 146 (release)**
   (ver `AGENTS.md`, `apps/android/README.md` y los XML de
   `build/test-results/`). R8 no rompió nada en ejecución: el auditor instaló
   una copia release firmada
   con la clave debug y recorrió Cercanas, A05 en vivo, guardar aviso, FGS en
   segundo plano, notificación en eu y borrado del aviso (§5 del informe).
4. **Ficha de Play** → **NO estaba lista.** La eu era texto inventado (A5-03) y
   la es incumplía límites de Play (A5-07). Reescritas el 2026-11-23; la eu
   queda pendiente de revisión euskaldun. Vídeo del FGS y política de privacidad
   alojada: pendientes de Iker.
5. **Notificación en euskera** → sin evidencia original (la cita estaba en
   castellano); el auditor la reprodujo en el AVD con `set-app-locales eu` y
   confirmó que funciona (§2, afirmación 6 del informe).
6. **Nombre e icono no oficiales** → el entregable desapareció del resultado
   original: el label seguía siendo «Bus Donostia» sin «(no oficial)» (A5-08).
   Corregido en la remediación: label del launcher «Bus Donostia (no oficial)» /
   «(ez-ofiziala)» y descargo explícito en A07. Icono: sigue el provisional.

## Sprint 6 · v1.1.0 (tokens build-time y motion avanzado)

- `GenerateDesignTokensTask`: MotionTokens/DesignTokens generados en build-time
  desde `packages/contracts/design-tokens.json` (cacheable, validada, con test
  de paridad `DesignTokensParityTest`). `versionName` 1.1.0, `versionCode` 2.
- Motion avanzado: badge de avisos con `AnimatedContent` + spring, chips con
  indicador spring, pull-to-refresh de marca con háptica, `pressScale` en
  timeline, stagger acotado `minOf(index, 8)`, shared elements con origen
  explícito y claves sin colisión, motion seguro en reduced motion
  (`motionAnimateItem` / `rememberMotionSpring`).
- Tests Compose nuevos (solo en `testDebug`): badge, minutos animados y pulso
  LIVE con reduced motion (`AlertBadgeTest`, `AnimatedMinutesTextTest`,
  `LiveSignalDotTest`): por eso debug tiene más tests que release.
- StopDetail como `LazyListScope` único; tokens de componente completos;
  `design-tokens.json` y `DESIGN.md` sincronizados (F5/F8/F9).
- **Resultado (2026-11-23):** `03884b6` + 11 commits hasta `11b7b19` en `main`.
  El residual del label `geo:` quedó cerrado en `11b7b19` (usa `stop_unnamed`,
  nunca el código de poste).
