# MVP Scope — Dbus Mobile Web App

## Versión
v0.1 — Sprint 0 output | 2026-11-22

---

## 1. Problem Statement
La app oficial dbus (San Sebastián) tiene 2.0★ en Android / 2.3★ en iOS, con fallos constantes, tiempos erráticos y retirada temporal de Play Store (sep 2024). La funcionalidad de "alarma programable" nativa es poco fiable. Los datos GTFS-RT son públicos, completos y actualizados ~30s. No existe app especializada en notificaciones push granulares para ciudades medias españolas.

## 2. MVP Scope — INCLUIDO

### Core Features (Must Have)

| ID | Feature | Descripción | Criterio de Aceptación (Done) |
|----|---------|-------------|------------------------------|
| F1 | **Parada favorita + alerta llegada** | Usuario selecciona parada → configura alerta "Bus X llega en N min" | Alerta se dispara ±2 min de llegada real en 95% casos (beta 20 testers, 1 semana) |
| F2 | **Configuración granular de alertas** | Minutos antelación (1-30), días semana (L-D), recurrencia (diario/laborables/fines), silencio nocturno (ej. 23-07) | Todos los controles funcionales en UI; persistencia IndexedDB; respeto silencio nocturno verificado |
| F3 | **Push nativo Web Push API (VAPID)** | Notificación en pantalla bloqueo / centro notificaciones sin app abierta | Delivery rate >95% cuando permiso concedido; payload incluye línea, destino, min llegada, parada |
| F4 | **Consumo GTFS-RT directo (3 feeds)** | Parse protobuf mdb-3066/3065/3064 c/30s, cache IndexedDB, fallback a scheduled si RT falla | Parse 3 feeds/30s sin bloquear main thread >100ms (Web Worker); cache hit rate >90% |
| F5 | **PWA instalable + offline-first** | `manifest.json`, Service Worker (Workbox), cache shell + datos estáticos GTFS | Lighthouse PWA >90; arranque offline <3s; datos paradas/horarios disponibles sin red |
| F6 | **Widget pantalla bloqueo (iOS 16.4+ / Android)** | Próxima llegada en lock screen sin abrir app | Visible en 80% dispositivos beta; actualización <5 min tras llegada real |
| F7 | **Info ocupación + tarjeta Mugi (si feed disponible)** | Badge ocupación (baja/media/alta) + saldo Mugi estimado | Mostrado cuando feed incluye `VehicleDescriptor.occupancy_status` y datos Mugi públicos |

### UX / UI Requirements
- Material 3 / iOS nativo feel (adaptive)
- <50MB bundle (gzipped)
- Cold start <2s en móvil medio (Snapdragon 778G / A14 Bionic baseline)
- Arranque <1s subsequent (SW cache)
- Accesibilidad WCAG 2.1 AA (contraste, touch targets 48dp, screen reader)

### Data Sources
- GTFS-RT: `opendata.euskadi.eus/transport/moveuskadi/dbus/*.pb` (3 feeds, Protobuf, sin auth, ~30s)
- GTFS Estático: `ftp.geo.euskadi.net/.../google_transit.zip` (paradas, rutas, horarios planificados)

---

## 3. EXCLUIDO — Parking Lot v2 (Fase 2+)

| Feature | Razón exclusión |
|---------|-----------------|
| Mapa interactivo con tracking tiempo real | Complejidad alta; no core para "alerta llegada"; Google Maps ya lo hace |
| Favoritos avanzados (carpetas, alias, export/import) | MVP = 1 parada favorita + 1 alerta; v2: múltiples |
| Multi-ciudad (Madrid, Bilbao, Málaga, Córdoba, Gijón) | Validar Donostia primero; arquitectura multi-ciudad en Sprint 2 |
| Auth / cuentas / sync cloud | Sin backend propio; v2 opcional con Firebase/Supabase |
| Pagos / recarga Mugi / abonos | Fuera de scope; solo info saldo si dato público |
| Planificador de viajes / multimodal (bus + tren + bici) | Compite con Citymapper/Google Maps; no diferenciador |
| Compartir llegada / ETA con contactos | Nice-to-have; v2 |
| Modo conductor / vista flota | Usuario final = pasajero |
| Analytics detallados / funnels | Plausible/Umami básico suficiente MVP |
| A/B testing framework | Overhead; v2 si tracción |
| Internacionalización (más ES/EU) | MVP: ES + EU (Donostia); v2: EN, FR |

---

## 4. Métricas de Éxito MVP (Go/No-Go para Fase 2)

| Métrica | Umbral Go | Medición |
|---------|-----------|----------|
| **Lighthouse Performance** | >90 | CI en cada PR |
| **Cold start (median)** | <2s | Web Vitals (LCP) en beta real |
| **Bundle size (gzipped)** | <50MB | `vite build --mode production` |
| **Push delivery rate** | >95% (permiso concedido) | Logs VAPID + client ack en beta |
| **Parse GTFS-RT main thread block** | <100ms/30s | Performance.mark en Web Worker |
| **Retención D7 (beta 20 testers)** | >40% | Evento "alerta recibida" día 7 |
| **Crash-free sessions** | >99.5% | Sentry |
| **Permiso notificaciones concedido** | >60% de instalados | Prompt contextual en onboarding |

---

## 5. Definición de "Done" Global
- [ ] 3 archivos planning creados y versionados
- [ ] Sprint 1 (wireframes) y Sprint 2 (ADRs) pueden arrancar sin bloqueos
- [ ] Decisiones firmes en stack (sin "TBD")
- [ ] Parking lot v2 firmado por stakeholder (tú)
