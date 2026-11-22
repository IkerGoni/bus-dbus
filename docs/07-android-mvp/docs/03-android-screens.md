# 03 · Especificación de pantallas — Android MVP v1 (Material 3)

## 1. Shell de navegación
**NavigationBar inferior fija de 4 destinos** (sin menú hamburguesa, sin scroll horizontal):

| Posición | Destino | Deep Link | Icono | Badge |
|---|---|---|---|---|
| 1 | Inicio | `dbus://home` | `home` | Ninguno |
| 2 | Paradas | `dbus://stops` | `location_on` | Ninguno |
| 3 | Líneas | `dbus://lines` | `directions_bus` | Ninguno |
| 4 | Avisos | `dbus://alerts` | `notifications` | Contador `ACTIVE \| TRIGGERED` (`9+` si >9) |

---

## 2. Detalle por pantalla

### A01 · Inicio (`dbus://home`)
- **Referencia visual:** `stitch-references/inicio/code.html`.
- **Cabecera (56 dp):** título `Dbus` a la izquierda y botón Ajustes (`48×48dp`) a la derecha → `A07`.
- **Buscador rápido:** pill único limpio sin borde interior → abre búsqueda universal (`código`, `calle`, `línea`, `destino`).
- **Sección `TU PRÓXIMO AVISO` (solo si existe alerta `TRIGGERED` o `ACTIVE`):**
  - Fila 1: badge oficial Dbus + destino (`maxLines = 1`, elipsis) | cuenta atrás grande `JetBrains Mono` (`tabular-nums`, `nowrap`).
  - Fila 2: `{stopName} ({stopId})` | `● En vivo · Aviso {min}'` (o reloj `Horario`).
  - Fila 3: botón real `Pausar` (`min-h 48dp`) → persiste `active = false` + Snackbar con botón `Deshacer`. Sin `Posponer` (2026-11-22).
  - Tap en tarjeta → `A05` con `stopId`.
- **Sección `PARADAS FAVORITAS`:** tarjetas compactas con estrella rellena `#F59E0B` (`48×48dp`) y próximas 2–3 llegadas reales (`● 2 min` verde / reloj `9 min` gris). Auto-refresco 30 s + *pull-to-refresh*.
- **Estado vacío (sin alertas ni favoritos):** dos CTAs útiles: `Ver paradas cercanas` (`A02`) y `Explorar líneas` (`A03`).

### A02 · Paradas (`dbus://stops`)
- **Referencia visual:** `stitch-references/paradas/code.html`.
- **Cabecera (56 dp):** título `Paradas` + botón refrescar ubicación (`48×48dp`). Sin barra superior duplicada ni avatar.
- **Buscador persistente:** filtra offline las 548 paradas por código (`340`) o calle (`Boulevard`, `Pío Baroja`).
- **Filtros:** `Cercanas` (por defecto) · `Favoritas ★` · `Recientes`.
- **Modo `Cercanas`:** con GPS ordena (solo paradas con coordenadas: 514 de 548) top 20 por `haversineMeters` mostrando `140 m · 2 min`, badges de líneas Dbus y estrella ★ (`48×48dp`, persiste `STOP`). Sin permiso GPS muestra tarjeta discreta para activarlo + `Recientes` y buscador (nunca bloquea).
- **Modo `Favoritas ★`:** listado persistente real desde DB (cierra B12).
- **Modo `Recientes`:** últimas 10 paradas abiertas localmente.
- Tap en cualquier tarjeta → `A05`.

### A03 · Líneas (`dbus://lines`) y A03b · Itinerario (`dbus://line/{lineId}`)
- **A03:** cabecera `Líneas` (`39 líneas`), buscador por número (`28`), alias interno (`32`) o destino (`Ospitaleak`), filtros `Todas` / `Favoritas ★` / `Nocturnas`, lista con cuadrado oficial Dbus y estrella persistente (`LINE`).
- **A03b:** selector de sentido/destino (itinerarios reales) + timeline vertical ordenada de paradas. Tap en parada → `A05` destacando esa línea arriba.

### A04 · Mis Avisos (`dbus://alerts`)
- **Cabecera:** `Mis avisos` + Ajustes. FAB inferior derecho: `+ Nuevo aviso` → `A06`.
- **Agrupación:** `ACTIVOS` (`TRIGGERED`, `ACTIVE`) y `PAUSADOS` (`INACTIVE`: pausados o caducados). Los que ya avisaron no aparecen: se borran al avisar.
- **Tarjeta:** badge Dbus + línea/destino + minutos actuales, subtítulo `{stopName} ({stopId}) · Aviso {min}'`, estado (`⚡ Llegando`, `● Activa`, `Caducada`, `Pausada`), switch `active`, Editar (`A06`) y Eliminar (con `Deshacer`).
- **Estado vacío:** explicación breve + CTA `Elegir parada y crear aviso` → `A02`.

### A05 · Detalle de parada (`dbus://stop/{stopId}`)
- **Cabecera sticky:** volver, `{stopName}` + pill `{stopId}`, botón `Cómo llegar` (intent `geo:lat,lon`; oculto si la parada no tiene coordenadas) y estrella ★ persistente (`STOP`).
- **Filtro de líneas:** chips `Todas` + líneas reales de la parada con color oficial Dbus.
- **Próximas llegadas (auto-refresco 30 s + botón refrescar):**
  - Cada fila: badge Dbus + destino (hora estimada debajo) + `● X min` / reloj `X min` + **★ (`48×48dp`)** que guarda la favorita parada+línea (`STOP_LINE`) y **campana 🔔 (`48×48dp`)** que abre `A06` con `stopId`, `lineId` y `destination` precargados.
  - Estados degradados: `NO_SERVICE` (`🌙 Sin servicio en este momento` + texto real de `avisos` de Dbus si llega), `EMPTY` (`📭 No hay llegadas próximas`), `API_ERROR` (`⚠️ Error al consultar Dbus` + `Reintentar`).
- **Pie fijo:** botón primary full-width `🔔 Crear aviso en esta parada` → `A06?stopId={stopId}`.
- **Prohibido:** saldo Mugi y ocupación ficticia.

### A06 · Configurar Aviso (`dbus://alert/new?stopId=&lineId=` · `dbus://alert/{id}/edit`)
- **Paso 1 — Parada y Línea:** tarjeta de parada (precargada + `Cambiar`) + chips solo de las líneas reales de esa parada (más selector de destino si hay varios).
- **Paso 2 — Antelación:** display grande `{n} min antes`, 6 chips rápidos (`2'`, `3'`, `5'`, `8'`, `10'`, `15'`) sincronizados con slider `1–30 min`.
- **Paso 3 — Un solo uso** (2026-11-22): sin selector de frecuencia; texto `Vigila desde que lo guardas, avisa una vez y se borra. Caduca a los 7 días si no llega a avisar.` Sin avisos recurrentes ni alarmas exactas.
- **Vista previa:** tarjeta con el texto que tendrá la notificación construido con los valores elegidos (`L5 → Amara en 5 min`, sin botones); etiquetada como vista previa.
- **Guardar:** dedup por `(stopId, lineId)` (ofrecer actualizar si ya existe), solicitar `POST_NOTIFICATIONS` si falta permiso y persistir en DB.

### A07 · Ajustes (`dbus://settings`)
- Apariencia (`Sistema / Claro / Oscuro`), Idioma (`Español / Euskara` al 100%).
- Notificaciones: estado real leído de Android + `Ajustes del sistema` (`ACTION_APP_NOTIFICATION_SETTINGS`).
- Datos: conteo real del catálogo offline (leído del asset, hoy `548 paradas · 39 líneas`), fecha `generatedAt` del catálogo y hora real de la última consulta en vivo. Sin «tamaño de caché» en v1 (no hay caché que medir).
- Avisos: estado real de `SCHEDULE_EXACT_ALARM` y de optimización de batería, con enlace a ajustes.
- Acerca de: versión de build (`BuildConfig.VERSION_NAME`), licencia MIT, fuentes: «Tiempos: servicio de la app de Dbus (no oficial, sin afiliación)» y «Coordenadas de paradas © OpenStreetMap (ODbL)».

### A08 · Onboarding ligero (primera apertura)
- Pantalla única (sin carrusel): icono + 3 bullets (llegadas reales y paradas cercanas · avisos `X min antes` · sin anuncios ES/EU).
- Acciones: `Permitir notificaciones y empezar` (`POST_NOTIFICATIONS`) y `Empezar sin notificaciones`.
