# Plan de sprints — Android MVP v1

Fuente: ADR 009, `docs/` de este paquete y `prompts/PROMPT-IMPLEMENTACION.md`.
Un sprint = una rama `android/sprint-N` integrada en `main` al cerrar.
Verificación común: `cd apps/android && ./gradlew check` (lint + tests JVM +
build debug). Estado de cada sprint: `ESTADO.md`.

| Sprint | Objetivo | Cierra brechas | Estado |
|---|---|---|---|
| 0 | Esqueleto verde | B4, B8, B14 (base) | hecho 2026-11-22 |
| 1 | Datos y dominio | B1, B3, B12, B15, B16 | pendiente |
| 2 | Consulta: Paradas, Detalle, Líneas | B1, B2, B9, B11 | pendiente |
| 3 | Inicio, Avisos, Ajustes, Onboarding | B5, B10, B13 | pendiente |
| 4 | Motor de avisos y notificaciones | B6 | pendiente |
| 5 | Publicación: repo público + config | — | pendiente |

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

## Sprint 2 · Consulta
- A02 Paradas: buscador offline, Cercanas (GPS coarse, top 20 Haversine),
  Favoritas, Recientes, permiso de ubicación no bloqueante.
- A05 Detalle: llegadas en vivo con refresco 30 s, 5 estados, chips de línea,
  ★ persistente, `Cómo llegar` (`geo:`). La 🔔 por fila llega en Sprint 3
  junto con A06 (nada de botones sin acción mientras tanto).
- A03 Líneas y A03b Itinerario desde el catálogo.
- **DoD:** probado en emulador contra la API real; capturas en `docs/07-android-mvp/capturas/`.

## Sprint 3 · Inicio, Avisos, Ajustes, Onboarding
- A06 Configurar aviso (ONCE / RECURRING con franja), dedup y guardado; 🔔 por
  fila y botón `Crear aviso` en A05.
- A04 Mis avisos (5 estados, switch, editar, borrar con Deshacer).
- A01 Inicio (próximo aviso + favoritas en vivo, Posponer/Pausar reales).
- A07 Ajustes (tema, idioma, datos reales, fuentes, permisos) y A08 Onboarding.
- Badge de Avisos con `computeAlertStatus`.
- **DoD:** todas las pantallas con sus 5 estados; cero literales; capturas.

## Sprint 4 · Motor de avisos
- Foreground Service `dataSync` (sondeo 30 s agrupado por parada).
- Alarmas exactas para RECURRING, reprogramación en boot/cambio de hora.
- Canales, notificación con `Posponer 5'` / `Descartar` por `BroadcastReceiver`.
- `POST_NOTIFICATIONS` con fallback a ajustes; deep link `dbus://stop/{stopId}`.
- **DoD:** flujo completo en emulador: cercana → A05 → 🔔 → guardar ONCE →
  notificación real → `Posponer 5'` → segunda notificación.

## Sprint 5 · Publicación
- Auditar el historial de git en busca de secretos **antes** de hacer público
  el repo (token del bot: nunca commiteado según `ESTADO.md`; verificarlo).
- Repo público: publicar `config.json` (GitHub Pages o raw) y fijar su URL en
  `ApiConfig`.
- Nombre/icono no oficiales, firma de release, ficha de Play (FGS `dataSync`,
  ubicación), pista de pruebas internas.
- **DoD:** APK release firmado instalado en un móvil real; config remota
  descargada y validada.
