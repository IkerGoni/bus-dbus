# Brechas entre diseño y código — bus-dbus

Diferencias detectadas entre los wireframes de `docs/02-wireframes/`, el
comportamiento descrito en `AGENTS.md` / `ESTADO.md` y el código actual de
`apps/bot/` y `apps/mobile/`.

Sirve para no borrar UI que funciona confundiéndola con dato falso, y para no
dar por implementado lo que solo está dibujado. Referencias:
[`KNOWN_ISSUES.md`](../../apps/mobile/KNOWN_ISSUES.md) y
[`ESTADO.md`](../../ESTADO.md).

| ID | Brecha | Superficie | Impacto |
|---|---|---|---|
| B1 | Cercanía con distancias en metros | PWA | Medio |
| B2 | Datos de ejemplo en UI | PWA | Medio |
| B3 | Líneas hardcodeadas en el configurador | PWA | Alto |
| B4 | Espacio reservado al `BottomNav` sin componente | PWA | Bajo |
| B5 | Onboarding/permisos no implementado | PWA | Alto |
| B6 | Push real no implementado | PWA | Alto |
| B7 | Widget/lockscreen no implementado | PWA | Medio |
| B8 | Build de la PWA bloqueado | PWA | Alto |
| B9 | «Crear alerta» en detalle de parada sin acción | PWA | Alto |
| B10 | Estados visuales de alertas incompletos | PWA | Medio |
| B11 | Modo offline con valores fijos | PWA | Medio |
| B12 | Favoritos de la PWA sin persistencia | PWA | Alto |
| B13 | Posponer/desactivar solo muestran toast | PWA | Medio |
| B14 | Texto literal sin clave i18n | PWA | Bajo |
| B15 | Cuenta atrás puede no refrescar | PWA | Medio |
| B16 | Criterio de «silenciada» divergente | PWA | Bajo |

<a id="b1"></a>
## B1 · Cercanía con distancias en metros

El wireframe `02-stop-selector.excalidraw` muestra «Loiola 120m · Centro 280m ·
Amara 450m». La API JSON de Dbus no devuelve coordenadas: el GPS fue descartado
y los datos de prueba quedaron fuera del repo (`ESTADO.md`, commit `8445239`).
El código usa `getNearestStops(lat, lon, 20)` sobre un índice local, sin
distancias en la UI. No revivir feeds `.pb` (403, ADR 005).

<a id="b2"></a>
## B2 · Datos de ejemplo en UI

Marcados en `KNOWN_ISSUES.md` como «no borrar sin sustituir»:

| Pantalla | Valor falso | Origen |
|---|---|---|
| S03 Detalle de parada | Saldo Mugi `12,50 €` | `StopDetail.tsx` |
| S03 Detalle de parada | Ocupación derivada de `occupancyStatus` (real solo si hay vehículo) | `StopDetail.tsx` |
| S06 Ajustes | `Caché: 2.3 MB`, `hace 15s`, `GTFS estático: v2026.11` | `Settings.tsx` |
| S06 Ajustes | `Última actualización: hace 2 min` | `Settings.tsx` |
| S01 Inicio | «Datos de hace 2 min» en el wireframe | `01-home.excalidraw` |

Sustituir por datos reales en M3. El bot, en cambio, no tiene mocks en
producción: si no hay datos lo dice.

<a id="b3"></a>
## B3 · Líneas hardcodeadas en el configurador

`AlertConfig.tsx` usa `lines = ['5','16','21','25','33']` y un mapa fijo de
destinos. El catálogo real del bot tiene 39 líneas, 127 itinerarios y 548
paradas. El configurador no consulta `@dbus/core` ni el catálogo.

<a id="b4"></a>
## B4 · Espacio reservado al `BottomNav`

`App.tsx` aplica `pb-20` al `<main>`, pero no existe
`components/layout/BottomNav.tsx`. La navegación real está en `Header` (iconos
+ menú hamburguesa). No dibujar una barra inferior nueva sin decidir antes qué
hace con el menú actual.

<a id="b5"></a>
## B5 · Onboarding/permisos no implementado

Solo existe en `01-user-flows/01-onboarding-permissions.excalidraw`: pantalla de
presentación, gesto de usuario, `Notification.requestPermission()`,
`pushManager.subscribe()` con VAPID, y fallback cuando se deniega el permiso.
No hay ruta ni componente en `apps/mobile/src/`. Las claves `onboarding.*` y
`offline.permissionDenied` están en los JSON de i18n pero no se usan en pantalla.

<a id="b6"></a>
## B6 · Push real no implementado

`KNOWN_ISSUES.md` lo sitúa fuera de alcance hasta M5/M6:

- `vite-plugin-pwa` usa `GenerateSW`, sin service worker propio.
- `functions/api/push-send.js` usa `web-push`, que necesita crypto de Node no
  disponible en Workers.
- No hay banner in-app de push ni acciones `Abrir / Posponer / Descartar`
  (`07-push-received.excalidraw`). El `ToastContainer` es el feedback más
  parecido, pero no es una notificación.

El bot sí envía avisos reales cada minuto desde `alerts.ts`.

<a id="b7"></a>
## B7 · Widget/lockscreen no implementado

`08-widget-lockscreen.excalidraw` describe indicador de frescura (🟢 < 30 s,
🟡 30–60 s, 🔴 > 60 s), intervalo de cliente de 5 min, tap → Home y deep link
`dbus://alert/5/loiola`. No hay widget, ni manejo de deep links, ni medición de
frescura en la PWA.

<a id="b8"></a>
## B8 · Build de la PWA bloqueado

`pnpm --filter @dbus/mobile build` falla por errores preexistentes:

1. **CSS:** `src/styles/tailwind.css` usa `@apply btn`, `@apply chip`, `@apply
   badge`; Tailwind 4 solo acepta utilidades en `@apply`.
2. **TypeScript:** imports con un nivel de más en páginas (`../../store`,
   `../../types`, `../../lib/db`), `useToast` duplicado, `leftIcon/rightIcon`
   mal tipados, `useParams()` sin genérico, `effect(fn, [])` con deps ignoradas,
   `Check` sin importar, `Users`/`CreditCard` duplicados, store `alerts` frente a
   `DBSchema` de `idb`.

Detalle completo en `KNOWN_ISSUES.md`. La UI descrita en
[`03-pwa.md`](03-pwa.md) existe en el árbol de fuentes, pero no compila.

<a id="b9"></a>
## B9 · «Crear alerta» en detalle de parada sin acción

En `StopDetail.tsx`, `handleCreateAlert()` tiene el cuerpo vacío (solo un
comentario: *navigate to alert config with this stop pre-selected*). El botón
primary «Crear alerta para esta parada» no navega. Falta pasar `stopId` a
`/alert/new` y precargar parada en `AlertConfig` (que hoy guarda `stopId: ''`).

<a id="b10"></a>
## B10 · Estados visuales de alertas incompletos

`05-alerts-list.excalidraw` define cuatro estados: `🔴 ACTIVA — countdown verde`,
`🌙 SILENCIADA — luna naranja`, `⚡ DISPARADA — pulso azul`, `⚪ INACTIVA —
toggle gris`. El código cubre activa/inactiva y el prefijo 🌙, pero no el estado
`DISPARADA` (no existe el concepto en el modelo de la PWA) ni el pulso azul. Las
claves `alertsList.status.silenced` y `.triggered` están en i18n sin uso.

<a id="b11"></a>
## B11 · Modo offline con valores fijos

En `Offline.tsx`:

- `hasCache = true` es una constante → la rama «No hay datos en caché» es
  inalcanzable.
- «Datos de hace 15 min» es literal.
- `handleOpenSettings()` abre `about:blank` en la misma pestaña en lugar de un
  deep link a ajustes del sistema.
- El overlay global depende de `navigator.onLine`, que no detecta todos los
  casos de API caída (el bot, en cambio, distingue `SIN_SERVICIO`, `NO DATOS` y
  fallo de API).

<a id="b12"></a>
## B12 · Favoritos de la PWA sin persistencia

- `StopSelector.handleFavoriteToggle()` solo lanza un toast «Añadido a favoritas»;
  la pestaña `Favoritas` siempre llama a `loadFavorites()`, que hace
  `stops.value = []`.
- `StopDetail.handleFavoriteToggle()` solo alterna un signal local
  `isFavorite` con toast; no escribe en la store.

El bot sí persiste tres tipos de favoritos con dedup en SQLite. La PWA no
comparte esa persistencia.

<a id="b13"></a>
## B13 · Posponer/desactivar solo muestran toast

En `Home.tsx`:

- `handleSnooze()` activa `showSnooze` durante 5 min y muestra un toast, pero no
  modifica la alerta ni su horario.
- `handleDisable()` muestra «Alerta desactivada» sin cambiar `alert.active`.

La UI parece realizar la acción, pero el estado no cambia. En el bot, en
cambio, borrar/desactivar avisos sí muta SQLite.

<a id="b14"></a>
## B14 · Texto literal sin clave i18n

Además del euskera (`eu.json`), hay cadenas en español escritas directamente
en componentes: estados vacíos de `StopSelector`, «Parada no encontrada», «No
hay llegadas programadas en la próxima hora», mensajes de
`AlertConfig`/`AlertsList`/`Settings`, y el diálogo «¿Limpiar toda la caché?».
El interruptor de idioma no traduce esas cadenas.

<a id="b15"></a>
## B15 · Cuenta atrás puede no refrescar

`store/index.ts` define `countdown` como `computed()` que lee
`nextArrival.value` y llama a `Date.now()`, pero **no lee** el signal `now` que
el `effect` actualiza cada segundo. Sin leer `now`, el computed no se invalida
por el temporizador; la cuenta atrás podría quedarse quieta hasta otro cambio
de señal. Revisar con build verde.

<a id="b16"></a>
## B16 · Criterio de «silenciada» divergente

- `Home` y `Header` consideran activa una alerta si `a.active && !a.silenced`.
- `AlertsList.isSilenced()` calcula el silencio comparando la hora actual con
  `silenceFrom`/`silenceTo`, sin mirar `a.silenced`.

Dos pantallas pueden mostrar estados distintos para la misma alerta. Unificar
en una única función pura (preferiblemente en `packages/core/`).

## Prioridad sugerida

1. B8 → desbloquear build y typecheck.
2. B9, B12, B13 → que los botones hagan lo que dicen.
3. B3, B2 → datos reales en lugar de catálogo hardcodeado y valores de ejemplo.
4. B5, B6, B7 → decidir en M5/M6 qué se implementa de push/onboarding/widget.
5. B1, B4, B10, B11, B14, B15, B16 → pulido y consistencia.
