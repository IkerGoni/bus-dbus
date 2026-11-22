# Superficie B — PWA / móvil (`@dbus/mobile`)

Inventario de las pantallas de la PWA implementadas en `apps/mobile/src/` y de
las que existen solo como wireframe. Estado: Sprint 3 bloqueado por errores de
TypeScript y Tailwind 4 preexistentes (ver
[`04-brechas.md`](04-brechas.md#b8)); el código de UI está presente y se describe
aquí tal cual.

## 1. Shell de la aplicación

`App.tsx` monta un `Router` de wouter con:

- `Header` persistente (salvo en modo offline global).
- `<main class="pb-20 safe-top safe-bottom">` con el `Switch` de rutas.
- `ToastContainer` global (posición `bottom-right`, 4 tipos, 5 s de duración).
- Overlay `Offline` global cuando `!navigator.onLine && !rtData`.

### Header (`components/layout/Header.tsx`)

| Zona | Elementos |
|---|---|
| Izquierda | Icono 🚌 + «Dbus» → `/` |
| Derecha | Campana → `/alerts` con badge rojo del número de avisos activos y no silenciados (`9+` si > 9); engranaje → `/settings` |
| Móvil (`md:hidden`) | Botón ☰ / ✕ que despliega menú con *Seleccionar parada*, *Mis alertas (n)* y *Ajustes* |

El header es `sticky top-0`, con `backdrop-blur` y `safe-top`. Los targets
tácticos son de 48×48 px.

## 2. Mapa de rutas

| Ruta | Componente | Pantalla |
|---|---|---|
| `/` | `pages/Home.tsx` | S01 · Inicio |
| `/stops` | `pages/StopSelector.tsx` | S02 · Seleccionar parada |
| `/stop/:stopId` | `pages/StopDetail.tsx` | S03 · Detalle de parada |
| `/alert/new` | `pages/AlertConfig.tsx` | S04 · Configurar alerta (nueva) |
| `/alert/:alertId/edit` | `pages/AlertConfig.tsx` | S04 · Configurar alerta (editar) |
| `/alerts` | `pages/AlertsList.tsx` | S05 · Mis alertas |
| `/settings` | `pages/Settings.tsx` | S06 · Ajustes |
| `/offline` | `pages/Offline.tsx` | S07 · Sin conexión (ruta) |
| *(overlay global)* | `pages/Offline.tsx` | S07 · Sin conexión (bloqueo) |

Wireframe asociado: `docs/05-ui/wireframes/02-mobile-web/01-home.excalidraw` …
`09-offline-empty.excalidraw`.

## 3. Detalle por pantalla

### S01 · Inicio (`/`)

**Propósito:** mostrar la próxima llegada del aviso activo y acceso rápido a
detalle, posponer y desactivar. Wireframe: `01-home.excalidraw`.

**Estado vacío** (sin `alert.active && !alert.silenced`):

- Tarjeta centrada con icono 📍, título «No tienes alertas configuradas»,
  subtítulo «Notificaciones push de llegada a parada» y CTA «Crear mi primera
  alerta» → `/stops`.
- Debajo, tres Tarjetas informativas: notificaciones, minutos de antelación y
  modo silencio.

**Estado con aviso activo:**

- Fila superior: badge `EN VIVO` si hay `rtData`, si no `HORARIO`; badge
  `DATOS ANTIGUOS` si `rtData.timestamp` tiene más de 60 s; a la derecha, tiempo
  relativo («hace X min», con locale `es`/`eu`).
- Tarjeta principal: «Próxima llegada», `{lineName} → {destination}`,
  `{stopName}` con 📍, cuenta atrás grande en minutos (o «Ahora»), barra de
  progreso (`100 - minutos*10`, limitada 0–100).
- Acciones: `Posponer 5'` (ghost, pone `showSnooze` 5 min y toast informativo),
  `Ver detalle` (secondary → `/stop/{stopId}`), `Desactivar` (danger, toast
  «Alerta desactivada»).
- Cuadrícula de 3 métricas: avisos activos, estado en vivo (✓/✗) y
  notificaciones (🔔/🔕).
- Enlace inferior «Mis alertas (n)».

**Claves i18n:** `home.*`, `onboarding.subtitle`, `common.now`, `common.live`,
`alertsList.title`, `alertConfig.minutesAhead`, `alertConfig.silence`.

### S02 · Seleccionar parada (`/stops`)

**Propósito:** encontrar una parada por cercanía, texto o favoritos.
Wireframe: `02-stop-selector.excalidraw`.

- Buscador `sticky` arriba: icono 🔍, `Input type="search"` con placeholder
  «Buscar parada...» y botón ✕ para limpiar.
- Tres pestañas `sticky`: `Paradas cerca de ti` (Navigation), `Buscar` (Search) y
  `Favoritas` (Star). La activa lleva borde inferior primary.
- **Cercanía:** si no hay ubicación, tarjeta con «Usar mi ubicación» y botón
  `Locate`; mensaje distinto si hay `geoError`. Con ubicación, `getNearestStops(lat, lon, 20)`.
- **Búsqueda:** `searchStops(query, 20)` y filtrado local por `stop_name` o
  `stop_code`; el texto escribe directamente en la pestaña `search`.
- **Favoritas:** actualmente lista vacía (sin persistencia implementada).
- Carga: 5 `Card` con `Skeleton` circular + dos líneas de texto.
- Vacío: «No se encontraron paradas» / «No tienes favoritas» / «No hay paradas
  cercanas», con texto de ayuda según pestaña.
- Resultado: `Card hoverable` con nombre, código en pill, descripción o
  `Lat/Lon`, botón ★ y chevron → `/stop/{stop_id}`. Teclado: Enter/Espacio.

**Claves i18n:** `stopSelector.*`, `common.search`, `common.loading`,
`common.clear`, `common.error`.

### S03 · Detalle de parada (`/stop/:stopId`)

**Propósito:** líneas, llegadas y acciones sobre una parada. Wireframe:
`03-stop-detail.excalidraw`.

- **Carga:** 1 tarjeta de cabecera + 3 tarjetas de esqueleto.
- **Error:** icono ⚠️, «Error», «Parada no encontrada», botón «Reintentar» → `/stops`.
- **Cabecera sticky:** nombre de la parada y código; botón ★ (relleno si es
  favorita) y botón primary «Crear alerta para esta parada».
- **Líneas que pasan:** sección con contador «N líneas» y badges con cada
  `lineId` (líneas alfanuméricas admitidas).
- **Próximas llegadas:** hasta 5, calculadas de `rtData.tripUpdates`, filtradas
  a `0 ≤ minutos ≤ 60` y ordenadas. Cada fila muestra badge `EN VIVO`/`HORARIO`,
  `L{linea} → destino`, etiqueta del vehículo, badge de ocupación (Baja/Media/Alta)
  y cuenta atrás. Sin datos: «No hay llegadas programadas en la próxima hora».
- **Métricas:** dos tarjetas: «Ocupación» (valor de la primera llegada) y «Saldo
  Mugi estimado» con `12,50 €` (valor de ejemplo).

**Claves i18n:** `stopDetail.*`, `home.live`, `home.scheduled`, `common.now`,
`common.error`, `common.retry`.

### S04 · Configurar alerta (`/alert/new`, `/alert/:alertId/edit`)

**Propósito:** crear o editar una alerta. Wireframe: `04-alert-config.excalidraw`.
Mismo componente para alta y edición; en edición se precargan los valores desde
`alerts` y aparece el botón «Eliminar».

Cabecera sticky con flecha volver a `/alerts`, título «Configurar alerta» y
espaciador derecho.

| Campo | Control | Valores |
|---|---|---|
| Línea | Chips `variant="line"` | `5`, `16`, `21`, `25`, `33` (hardcodeados), con destinos asociados |
| Minutos de antelación | Slider `1–30` | Valor inicial `5`; se muestra `{n} min` |
| Días de la semana | Chips `D L M X J V S` | Por defecto `L–V` (`[1,2,3,4,5]`) |
| Recurrencia | Chips | `Diario`, `Laborables`, `Fines de semana` |
| Silencio nocturno | Toggle + dos `Input type="time"` | Activo por defecto, `23:00` → `07:00` |
| Vista previa | Tarjeta monoespacio | `L{linea} → {destino}` / `Llega en {min} min a Parada seleccionada` |

Acciones: «Guardar» (primary, valida línea seleccionada, guarda en `alerts` y
navega a `/alerts`), «Cancelar» (secondary → `/alerts`) y «Eliminar» (danger,
solo en edición). El guardado muestra toast de éxito.

**Claves i18n:** `alertConfig.*`, `push.title`, `push.body`, `common.*`.

### S05 · Mis alertas (`/alerts`)

**Propósito:** listar, activar/desactivar, editar y borrar alertas. Wireframe:
`05-alerts-list.excalidraw`.

- Cabecera sticky: «Mis alertas» y botón «Nueva alerta» → `/alert/new`.
- Vacío: tarjeta con campana, «No hay alertas activas» y CTA «Nueva alerta».
- Dos secciones: `ACTIVA (n)` e `INACTIVA (n)`.
- Tarjeta de alerta: `{lineName} → {destination}`, badge `ACTIVA`/`INACTIVA`
  (prefijo 🌙 si está en horario de silencio), icono luna, «Llega en {min} min
  • {stopName}» con reloj, días (`D L M X J V S`) y recurrencia.
- Controles: toggle de activación, lápiz → `/alert/{id}/edit`, papelera →
  borrado inmediato; todos con toast de confirmación.

**Claves i18n:** `alertsList.*`, `alertConfig.recurrenceOptions.*`,
`home.arrivingIn`.

### S06 · Ajustes (`/settings`)

**Propósito:** preferencias, datos y sobre la app. Wireframe:
`06-settings.excalidraw`.

| Sección | Contenido |
|---|---|
| Apariencia | Chips `Sistema` (Monitor), `Claro` (Sun), `Oscuro` (Moon); aplica clase `dark` al `<html>` |
| Idioma | Chips `ES` / `EU`; cambia `i18n.changeLanguage` |
| Notificaciones | Toggle + «Permiso concedido» + «Última actualización: hace 2 min» |
| Datos | «Caché: 2.3 MB» + «Última actualización: hace 15s» + botón «Limpiar caché»; «GTFS estático: v2026.11» |
| Sobre la app | «Versión 0.1.0-beta», «Licencia MIT», «Datos: Open Data Euskadi / Moveuskadi», enlaces *Open Data Euskadi*, *GitHub* y *Comprar café al desarrollador* |

Diálogo de confirmación propio (`role="dialog"`, `aria-modal="true"`):
«¿Limpiar toda la caché?» + explicación + «Cancelar» / «Limpiar caché». Al
confirmar llama a `clearAllData()`, muestra toast y recarga la página.

**Claves i18n:** `settings.*`, `common.cancel`, `common.error`, `common.success`.

### S07 · Sin conexión (`/offline` y overlay)

**Propósito:** bloquear la interacción cuando no hay red y no hay datos en
memoria. Wireframes: `09-offline-empty.excalidraw`, `01-home.excalidraw`.

- La ruta `/offline` renderiza el mismo componente que el overlay global.
- `Offline` devuelve `null` si `navigator.onLine` es `true`.
- Overlay `fixed inset-0 z-50` con `role="alertdialog"`, tarjeta centrada,
  icono 📴 y título «Sin conexión».
- Rama con caché (`hasCache = true`, constante): «Datos de hace 15 min», aviso
  «Datos antiguos — toque para actualizar», botones «Reintentar cuando haya
  conexión» (recarga) y «Abrir ajustes del sistema».
- Rama sin caché: «No hay datos en caché» + reintentar (actualmente inalcanzable
  porque `hasCache` está fijo).

**Claves i18n:** `offline.*`.

## 4. Estados visuales transversales

| Estado | Mecanismo | Pantallas |
|---|---|---|
| Carga | `Skeleton` dentro de `Card` | S02, S03 |
| Vacío | `Card` centrado con icono y CTA | S01, S02, S05 |
| Error de datos | `Card` con ⚠️ y reintento | S03 |
| Datos antiguos | Badge `DATOS ANTIGUOS` si > 60 s | S01 |
| En vivo / horario | Badge `rt` / `scheduled` | S01, S03 |
| Ocupación | Badge `occupancy-low/medium/high` | S03 |
| Silenciada | Prefijo 🌙 + icono luna + opacidad | S05 |
| Feedback | `ToastContainer` | Todas |
| Sin red | Overlay `Offline` | Global |

<a id="5-pantallas-solo-diseñadas"></a>
## 5. Pantallas solo diseñadas

Existen en `docs/05-ui/wireframes/` pero **no** en el código de `apps/mobile/`:

### 5.1 Onboarding y permisos

Wireframe: `01-user-flows/01-onboarding-permissions.excalidraw`.

- Presentación «🚌 Dbus Donostia — Llega a tu parada sin esperar» con bullets:
  notificaciones granulares, minutos/días/silencio, offline sin ads,
  «Datos: GTFS-RT tiempo real (~30s)» y «App oficial: 2.0★ → oportunidad».
- CTA «Activar alertas» como gesto de usuario; `Notification.requestPermission()`
  → `pushManager.subscribe()` (VAPID) → prompt nativo de iOS/Android.
- Éxito → selección de parada; denegado → fallback in-app con «Activa en
  Ajustes», deep link a ajustes del sistema, polling visible de 30 s y toast
  «Notificaciones desactivadas».
- Confirmación de config con preview «Línea 5 → Amara».

### 5.2 Push recibida

Wireframe: `02-mobile-web/07-push-received.excalidraw`.

- Notificación: título «Línea 5 → Amara», cuerpo «Llega en 3 min a Loiola»,
  acciones `[Abrir] [Posponer 5'] [Descartar]`.
- Banner in-app con las mismas acciones.
- Limitación documentada: iOS no admite botones de acción en push nativa;
  solo título + cuerpo + tap → abre app.

### 5.3 Widget / pantalla de bloqueo

Wireframe: `02-mobile-web/08-widget-lockscreen.excalidraw`.

- Indicador de frescura: 🟢 < 30 s · 🟡 30–60 s · 🔴 > 60 s.
- Actualización por intervalo de cliente de 5 min.
- Tap → abre la app en Home con la alerta activa; deep link `dbus://alert/5/loiola`.

## 6. Internacionalización

- Archivos: `src/i18n/locales/es.json` y `eu.json`; idioma por defecto `es`.
- Espacios de nombres: `app`, `onboarding`, `home`, `stopSelector`, `stopDetail`,
  `alertConfig`, `alertsList`, `settings`, `push`, `offline`, `common`.
- El selector de idioma está en S06; `Home` usa locale `es`/`eu` de date-fns para
  el tiempo relativo.
- Texto literal en español sin clave detectado en varias pantallas (mensajes
  de error y estados vacíos de S02, S03, S04, S06); ver
  [`04-brechas.md`](04-brechas.md#b14).
