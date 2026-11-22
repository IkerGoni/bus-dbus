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
| 5 | Publicación: release firmado, ficha de Play, auditoría de secretos | — | pendiente |

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
