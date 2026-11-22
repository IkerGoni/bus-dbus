# 04 · Plataforma Android (Push, Background, Ubicación y Roadmap)

## 1. Motor de avisos «X min antes» (Anti-Doze)
`WorkManager` tiene intervalo mínimo de 15 min (insuficiente para avisos de 2–10 min).
Implementar una de estas dos estrategias:

1. **Opción A — Push remoto FCM (Recomendada, mínimo consumo):**
   - Reutilizar el scheduler de 1 minuto ya probado en `@dbus/bot` (`alerts.ts`)
     para enviar un **FCM high-priority data message** al dispositivo Android.
   - El receptor local construye la notificación nativa con botones de acción.
2. **Opción B — 100% local (Fallback sin servidor push):**
   - Al activar un aviso `ONCE` (o entrar en ventana horaria), levantar un
     **Foreground Service** ligero con notificación silenciosa (`Monitorizando L5...`)
     que consulta Dbus cada 30–60 s y se detiene al disparar o tras 60 min sin servicio.

## 2. Canal y acciones directas de notificación
- **Canal:** `dbus_arrivals_high` (`IMPORTANCE_HIGH`, sonido + vibración, visible en pantalla de bloqueo).
- **Contenido:** Título `L{lineId} → {destination} en {etaMin} min` · Cuerpo `{stopName} ({stopId}) · {EN VIVO | HORARIO}`.
- **Action buttons (BroadcastReceiver sin abrir UI):**
  1. `Posponer 5'` → escribe `snoozedUntilMs = now + 300_000` y cierra la notificación.
  2. `Descartar` → si es `ONCE`, pone `active = false`; si es `RECURRING`, guarda `lastTriggeredAtMs = now`.
- **Tap en el cuerpo:** deep link `dbus://stop/{stopId}?highlightLine={lineId}`.

## 3. Permisos y navegación nativa
- `POST_NOTIFICATIONS` (API 33+): solicitar solo por gesto del usuario (`A08` o al guardar primer aviso). Si se deniega, ofrecer deep link a `Settings.ACTION_APP_NOTIFICATION_SETTINGS`.
- `ACCESS_COARSE_LOCATION` / `ACCESS_FINE_LOCATION`: opcional, solo para ordenar `A02 · Paradas cercanas` en local. La app funciona 100% sin GPS.
- **Cómo llegar (sin mapa embebido en v1):** `Intent(ACTION_VIEW, Uri.parse("geo:$lat,$lon?q=$lat,$lon($encodedName)"))`.
- **Edge-to-edge + Predictive Back:** `WindowInsets` obligatorios en barras y hojas.

## 4. Roadmap diferido (Fuera de MVP v1)
- **v1.1:** Widget Jetpack Glance (`2×1` y `4×1` con semáforo de frescura y deep link `dbus://stop/{stopId}`) + lectura por voz (TTS) en `A05`.
- **v2.0:** Mapa vectorial interactivo (MapLibre) como toggle dentro de `A02 · Paradas`.
