# Prompt Maestro para Agentes de Código — Migración a Arquitectura Android MVP v1 (bus-dbus)

Copia y pega este prompt directamente a tu orquestador / agente builder junto con
la carpeta completa de este paquete (`design-system/`, `docs/`, `stitch-references/`
y `wireframes/`).

---

```markdown
Eres el ingeniero principal encargado de implementar la aplicación Android MVP v1
de **Dbus Donostia** dentro del proyecto `bus-dbus`, tomando como fuente de verdad
el paquete `dbus_donostia_android_mvp_ui_package/`.

## 1. Cambio de arquitectura y alcance (Obligatorio)
Abandona las suposiciones de la PWA anterior bloqueada y aplica este cambio de
arquitectura para el MVP v1 de Android:

1. **Sin mapa embebido en v1:** NO instales ni integres SDKs de mapas (ni MapLibre,
   ni Google Maps SDK, ni OSMDroid). Sustituye la pantalla de Mapa por **`A02 ·
   Paradas`** (Cercanas por GPS + Buscador universal + Favoritas + Recientes). En
   `A05 · Detalle de parada`, añade un botón `Cómo llegar` que lance el intent
   nativo `geo:{lat},{lon}?q={lat},{lon}({stopName})`.
2. **Arquitectura dual de datos (Cero feeds `.pb`, Cero mocks):**
   - **Catálogo estático offline empaquetado:** ~548 paradas (`stopId`, `name`,
     `lat`, `lon`, `lineIds`), las 39 líneas reales con sus colores oficiales Dbus
     y los 127 itinerarios. Sirve para ordenar paradas cercanas con `haversineMeters`,
     buscar al instante sin red y alimentar el selector de líneas de `A03`, `A05` y `A06`.
   - **Tiempo real en vivo:** consultar únicamente la API JSON real de Dbus (misma
     fuente que `@dbus/bot`). Prohibido reactivar feeds `.pb` (403, ADR 005).
   - **Cero datos inventados:** eliminar saldo Mugi (`12,50 €`), ocupación falsa,
     tamaño de caché hardcodeado (`2.3 MB`), timestamps falsos (`hace 15s`) y
     `GTFS estático v2026.11`.
3. **Nueva estructura de navegación (4 destinos fijos):**
   - Retirar el menú hamburguesa del `Header` y el `pb-20` huérfano.
   - Implementar una `NavigationBar` inferior Material 3 fija con 4 destinos:
     `Inicio (A01)` · `Paradas (A02)` · `Líneas (A03)` · `Avisos (A04)`.
   - Usar el indicador activo unificado (`stitch-references/inicio/code.html` y
     `design-system/DESIGN.md`): píldora `48×28dp` envolviendo solo el icono
     (`FILL 1`) + etiqueta `Plus Jakarta Sans` (`nowrap`).
4. **Feature principal («Avísame X min antes»):**
   - Accesible en 1 toque mediante el botón `🔔` (`48×48dp`) en cada llegada de
     `A05 · Detalle de parada` (pasando `stopId`, `lineId` y `destination` a `A06`).
   - Dos modos en `A06`: **`Solo esta vez` (`ONCE`)** —se dispara y pasa a inactiva—
     y **`Recurrente` (`RECURRING`)** con días `L–D` y silencio nocturno (`23:00–07:00`).
   - Chips rápidos (`2'`, `3'`, `5'`, `8'`, `10'`, `15'`) sincronizados con slider `1–30`.
   - Dedup determinista por `(stopId, lineId)` y función pura única
     `computeAlertStatus(alert, now, nextArrival)` compartida por `A01`, `A04`,
     badge inferior y motor push (cierra B10 y B16).
   - Notificación Android nativa en canal `dbus_arrivals_high` (`IMPORTANCE_HIGH`)
     con acciones reales `Posponer 5'` y `Descartar` vía `BroadcastReceiver`
     (sin abrir la app) y estrategia Anti-Doze según `docs/04-android-platform.md`
     (Opción A: FCM conectado al scheduler de `@dbus/bot`; Opción B: Foreground Service).

## 2. Sistema de diseño y correcciones sobre Stitch
Aplica estrictamente `design-system/design-tokens.json` y `design-system/DESIGN.md`:
- Paleta unificada (Claro + Oscuro): `#F8FAFC` / `#0B1120` canvas, `#FFFFFF` /
  `#1E293B` tarjetas (`radius 16dp`, borde `1px`), `#005BB5` / `#60A5FA` primary,
  `#059669` / `#34D399` live RT, `#F59E0B` favoritos.
- Tipografía: `Plus Jakarta Sans` en toda la UI; `JetBrains Mono` (`tabular-nums`)
  solo en cifras y códigos. Aplica `nowrap` en tiempos, códigos y pestañas, y
  `maxLines = 1` + elipsis en destinos largos.
- Accesibilidad WCAG AA: targets táctiles mínimos `48×48dp`, contraste `>= 4.5:1`,
  punto verde `●` en tiempo real e icono reloj gris en horario (nunca solo color),
  estrellas rellenas `#F59E0B` en favoritos guardados.
- Bilingüismo estricto `es` y `eu`: cero strings literales en componentes.

## 3. Plan de ejecución por fases (orden obligatorio)
- **Fase 0 — Desbloqueo (B8):** dejar build, typecheck/lint y test de claves `es`/`eu` en verde.
- **Fase 1 — Core de datos y contratos (`02-domain-contracts.md`):** empaquetar
  catálogo estático (~548 paradas con `lat/lon`, 39 líneas, 127 itinerarios),
  cliente API JSON Dbus, persistencia local real (Favoritos 3 tipos + Alertas +
  Recientes), `computeAlertStatus`, `computeFreshness`, `haversineMeters` y reloj
  reactivo de 1 s (cierra B1, B3, B12, B15, B16).
- **Fase 2 — Shell y 8 pantallas (`A01`–`A08`):** implementar todas las vistas con
  los 5 estados obligatorios (`loading`, `empty`, `live`, `degraded`, `error`)
  siguiendo `docs/03-android-screens.md` y los HTML corregidos de `stitch-references/`
  (cierra B2, B4, B5, B9, B10, B11, B13, B14).
- **Fase 3 — Motor Push Android (`04-android-platform.md`):** canal de alta
  importancia, permiso `POST_NOTIFICATIONS` con fallback a ajustes del sistema,
  acciones `Posponer 5'` y `Descartar`, y deep link `dbus://stop/{stopId}` (cierra B6).
- **Fase 4 — Verificación y QA:** comprobar cada punto de `docs/05-migration-from-brechas.md`
  (B1–B16), probar modo claro/oscuro, cambio de idioma `es` ↔ `eu`, estado
  `NO_SERVICE`, modo offline con/sin caché y build final verde.

## 4. Definition of Done (No entregar hasta cumplir todo)
1. Build y tests unitarios de `computeAlertStatus`, `computeFreshness`, dedup y
   paridad de claves `es`/`eu` pasando al 100%.
2. Cero mocks o placeholders visibles en ninguna pantalla.
3. Flujo completo probado de extremo a extremo: buscar parada cercana → abrir
   detalle → pulsar 🔔 en una llegada → guardar aviso `5 min` → disparar notificación
   → pulsar `Posponer 5'` y verificar mutación real en DB y en `A01 Inicio`.
```
