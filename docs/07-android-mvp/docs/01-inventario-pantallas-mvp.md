# 01 · Inventario de superficies y pantallas — bus-dbus (MVP v1)

Actualización del inventario tras sustituir el alcance de la PWA bloqueada por
el **MVP Android v1** (sin mapa embebido, centrado en cercanía offline, favoritos
en vivo, 39 líneas y avisos «X min antes»).

## 1. Superficies del proyecto

| Superficie | Paquete | Estado | Tipo de UI |
|---|---|---|---|
| Bot de Telegram | `@dbus/bot` (`apps/bot/`) | Activo, datos reales | Mensajes Markdown + inline keyboards (sin cambios) |
| App Android (MVP v1) | `apps/android/` (Gradle, Kotlin + Compose, ADR 009) | Especificación ejecutiva | Material 3 · 4 pestañas fijas + Push nativo |
| PWA | `@dbus/mobile` (`apps/mobile/`) | Congelada (ADR 009) | — |

## 2. Mapa de pantallas Android MVP v1

```text
bus-dbus (Android MVP v1)
├── Shell: TopAppBar contextual + NavigationBar fija (4 destinos) + SnackbarHost
│   ├── Tab 1: A01 · Inicio (dbus://home) — Hero próximo aviso + Favoritas en vivo
│   ├── Tab 2: A02 · Paradas (dbus://stops) — Cercanas (GPS offline) · Favoritas · Recientes · Buscador
│   ├── Tab 3: A03 · Líneas (dbus://lines) — 39 líneas oficiales Dbus
│   │   └── Subpantalla: A03b · Itinerario (dbus://line/{lineId}) — Sentidos + timeline paradas
│   └── Tab 4: A04 · Mis Avisos (dbus://alerts) — 5 estados + FAB nuevo aviso
├── Pantallas de detalle / flujo (sin NavigationBar inferior)
│   ├── A05 · Detalle de parada (dbus://stop/{stopId}) — Llegadas RT + 🔔 por fila + Cómo llegar (geo:)
│   ├── A06 · Configurar aviso (dbus://alert/new | /edit) — X min antes · un solo uso
│   ├── A07 · Ajustes (dbus://settings) — Tema · ES/EU · Notificaciones · Caché real
│   └── A08 · Onboarding ligero (primera apertura) — 1 pantalla + POST_NOTIFICATIONS
├── Superficie de sistema
│   └── Push Android local (dbus_arrivals_high) — sin botones; tap → A05
└── Diferido explícitamente (fuera de v1)
    ├── v1.1: Widget Jetpack Glance (2×1 / 4×1) + Lectura por voz (TTS)
    └── v2.0: Mapa vectorial interactivo (MapLibre) dentro de A02
```

## 3. Flujos principales v1
1. **Consulta en 1 segundo:** `A01 Inicio` → ver cuenta atrás de tu aviso activo
   y próximas 2–3 llegadas de tus paradas favoritas.
2. **Parada cercana a aviso en 3 toques:** `A02 Paradas (Cercanas)` → tocar parada
   → `A05 Detalle de parada` → tocar `🔔` junto a tu autobús → `A06` (`5 min`;
   avisa una vez y se borra) → `Guardar aviso`.
3. **Explorar línea y guardar parada:** `A03 Líneas` → elegir línea con color Dbus
   → `A03b Itinerario` → elegir sentido → tocar parada → `A05` (★ favorita o 🔔 aviso).
4. **Aviso disparado (Push):** el Foreground Service local dispara cuando `remainingMinutes <= minutesAhead` →
   notificación Android sin botones y el aviso se borra (un solo uso); tocarla abre `A05`.
