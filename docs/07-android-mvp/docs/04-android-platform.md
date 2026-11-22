# 04 · Plataforma Android (Push, Background, Ubicación y Roadmap)

Decisión de arquitectura: ADR 009 (`docs/03-architecture/adrs/009-android-nativo-mvp.md`).

## 0. Red
- La API de Dbus solo responde por HTTP. Permitir cleartext **solo** para
  `app.dbus.eus` con `res/xml/network_security_config.xml`
  (`<domain-config cleartextTrafficPermitted="true">`). Nada de
  `usesCleartextTraffic="true"` global.
- Timeout 8 s, sin reintentos automáticos en primer plano (el usuario tiene
  `Reintentar`).
- Base URL, paths, parámetros y cabeceras salen de `ApiConfig`: copia
  empaquetada en `assets/config.json` + config remota estática opcional (ADR 009
  § Configuración remota). La app funciona igual sin ella.

## 1. Motor de avisos «X min antes» (100 % local, sin FCM en v1)
`WorkManager` tiene intervalo mínimo de 15 min: no sirve para avisos de 2–10 min.

- **Foreground Service `dataSync`** (`FOREGROUND_SERVICE_DATA_SYNC`) con
  notificación silenciosa en canal `dbus_monitoring` (`IMPORTANCE_LOW`):
  «Vigilando L5 en Pío Baroja 10».
- Sondea `tiemposParada` cada 30 s **agrupando avisos por parada** (una
  petición por parada, como el bot) y evalúa `computeAlertStatus`.
- Se para cuando no queda ningún aviso en vigilancia, o tras 60 min seguidos
  sin servicio/errores (y entonces lo notifica). Android 15 limita `dataSync` a
  6 h/24 h: si se alcanza, se para y se avisa (`onTimeout`).
- **Avisos de un solo uso** (2026-11-22): al disparar se notifica y el aviso
  se borra. Sin recurrentes, sin `AlarmManager` ni `SCHEDULE_EXACT_ALARM`.
- **Arranque:** al guardar en A06 y al reactivar o posponer en la app (gestos
  del usuario, arranque en primer plano permitido) y al abrir la app. Tras
  reiniciar el móvil, un aviso activo se retoma al abrir la app (Android 15 no
  deja arrancar `dataSync` desde `BOOT_COMPLETED`).
- **FCM queda para v2**: exige Firebase, registro de tokens y un servidor
  siempre encendido (otra IP única ante Dbus).

## 2. Canal y acciones directas de notificación
- **Canal:** `dbus_arrivals_high` (`IMPORTANCE_HIGH`, sonido + vibración, visible en bloqueo).
- **Contenido:** título `L{lineId} → {destination} en {min} min` · cuerpo
  `{stopName} ({stopId}) · {En vivo | Horario}`.
- **Sin botones:** el aviso ya está borrado; deslizar la notificación la cierra.
- **Tap en el cuerpo:** deep link `dbus://stop/{stopId}?highlightLine={lineId}`.

## 3. Permisos y navegación nativa
- `POST_NOTIFICATIONS` (API 33+): solo por gesto del usuario (A08 o al guardar
  el primer aviso). Si se deniega: `Settings.ACTION_APP_NOTIFICATION_SETTINGS`.
- `ACCESS_COARSE_LOCATION` (fine opcional): solo para ordenar «Cercanas»; la
  ubicación no se persiste (igual que el bot). La app funciona sin GPS.
- **Cómo llegar:** `Intent(ACTION_VIEW, Uri.parse("geo:$lat,$lon?q=$lat,$lon(${Uri.encode(name)})"))`;
  oculto si la parada no tiene coordenadas o no hay app que lo resuelva.
- **Edge-to-edge + Predictive Back** (`enableOnBackInvokedCallback`).

## 4. Publicación (antes de Play Store, no bloquea el MVP)
- Nombre y icono que **no** parezcan la app oficial (`com.indra.dbus`): p. ej.
  «Bus Donostia (no oficial)», con aviso en A07.
- Declaración de uso de FGS `dataSync` en Play Console.
- Atribución OpenStreetMap (ODbL) por las coordenadas.

## 5. Roadmap diferido (fuera de MVP v1)
- **v1.1:** Widget Jetpack Glance (`2×1`, `4×1`, deep link `dbus://stop/{stopId}`)
  + TTS en A05 + catálogo actualizable por el mismo hosting estático.
- **v2.0:** mapa vectorial (MapLibre) en A02 · FCM si hace falta.
