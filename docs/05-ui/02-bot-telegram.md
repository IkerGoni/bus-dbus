# Superficie A — Bot de Telegram (`@dbus/bot`)

Inventario de la UI del bot. Cada «pantalla» es un **estado de conversación**:
un mensaje de Telegram (texto Markdown) más su teclado inline opcional. Los
flujos usan `safeEdit()` para editar el mensaje anterior en lugar de enviar uno
nuevo; si el mensaje es demasiado antiguo, se envía uno nuevo.

Implementación: `apps/bot/src/handlers.ts` (comandos, menús y callbacks),
`apps/bot/src/alerts.ts` (avisos push), `apps/bot/src/arrival.ts` (formateo).

## 1. Comandos

| Comando | Handler | Salida |
|---|---|---|
| `/start` | `handleStart` | Menú principal; limpia la sesión del chat |
| `/help` | `handleHelp` | Texto de ayuda; por callback edita el mensaje y añade «⬅️ Menú» |
| `/parada <codigo> [linea]` | `handleParada` | Lista de llegadas en vivo de la parada |
| `/aviso <parada> <linea> <minutos>` | `handleAviso` | Confirma aviso (1–60 min) o error de validación |
| `/lista` | `handleLista` | Avisos del chat con botones de borrado |
| `/borrar <id>` | `handleBorrar` | Confirma borrado del aviso |
| `/buscar <texto>` | `handleBuscar` | Resultados inline de paradas y líneas |
| `/linea <linea>` | `handleLineaCommand` | Destinos de la línea (acepta id interno como alias) |
| `/lineas [parada]` | `handleLineas` | Sin argumento: catálogo paginado. Con parada: líneas de esa parada |
| `/fav [add\|linea\|remove ...]` | `handleFavCommand` | Listado/gestión de favoritos en texto |
| `/actualizar` | `handleActualizar` | Refresca caché y catálogo; muestra diagnóstico |
| `/estado` | `handleEstado` | Fuente, paradas en caché, avisos activos y uptime |

Texto libre (sin `/`) se maneja con `bot.on('text')`: si la sesión está en
`searching` → resultados de búsqueda; si está en `waiting_stop_arrivals` →
llegadas; si está en `waiting_stop_alert` → selección de línea del aviso. Si
el texto no es numérico en un flujo de parada, se deriva al buscador.

## 2. Estados de conversación

### S01 · Menú principal

**Disparador:** `/start`, callback `menu_main`, `menu_cancel`, o botón «Menú».

```text
🚌 **Dbus Donostia — llegadas en tiempo real**

Elige una opción:
```

| Botón | Callback |
|---|---|
| 🚌 Ver llegadas | `menu_arrivals` |
| 🔔 Crear aviso | `menu_alert` |
| ⭐ Favoritos | `menu_favorites` |
| 📋 Mis avisos | `menu_my_alerts` |
| 🔎 Buscar parada o línea | `menu_search` |
| 📃 Todas las líneas | `lines_all` |
| 🔄 Actualizar | `menu_refresh` |
| ℹ️ Ayuda | `menu_help` |

### S02 · Ayuda

**Disparador:** `/help` o `menu_help`.

```text
ℹ️ **Comandos**

`/parada <codigo> [linea]` — llegadas de una parada (código de la marquesina)
`/aviso <parada> <linea> <minutos>` — avisar antes de que llegue el bus
`/lista` — tus avisos activos
`/borrar <id>` — eliminar un aviso
`/buscar <texto>` — parada (código o nombre) o línea (número/destino)
`/linea <linea>` — destinos y paradas de una línea
`/lineas` — todas las líneas · `/lineas <parada>` — líneas en esa parada
`/fav` — tus favoritos · `/fav add <parada> [linea]` · `/fav linea <linea>`
`/actualizar` — refrescar datos · `/estado` — diagnóstico

Busca por código (`1`), nombre (`Boulevard`), línea (`28`) o destino
(`Ospitaleak`). Sin línea en `/fav add` guardas la parada completa.
También acepto el id interno de Dbus (p. ej. `32` = línea pública `28`).
Datos reales de la API de Dbus 📡
```

Por callback se añade un botón `⬅️ Menú` (`menu_main`).

### S03 · Pedir código de parada (llegadas)

**Disparador:** `menu_arrivals`.

```text
🚌 **Ver llegadas**

Escribe el código de la parada (el número de la marquesina):
```

Botones: `🔎 Buscar parada o línea` (`menu_search`) · `⬅️ Menú` (`menu_main`).
Estado de sesión: `waiting_stop_arrivals`.

### S04 · Pedir código de parada (aviso)

**Disparador:** `menu_alert`.

```text
🔔 **Crear aviso**

Escribe el código de la parada (el número de la marquesina):
```

Botones: `🔎 Buscar parada o línea` · `⬅️ Menú`. Estado: `waiting_stop_alert`.

### S05 · Buscar

**Disparador:** `menu_search`.

```text
🔎 **Buscar**

Escribe un código de parada, nombre de calle, línea o destino:
`1` · `Boulevard` · `28` · `Ospitaleak`
```

Botón: `⬅️ Menú`. Estado: `searching`.

### S06 · Resultados de búsqueda

**Disparador:** texto libre en `searching` o `/buscar <texto>`.

```text
🔎 Resultados para **{consulta}**
```

Un botón por parada (`🚏 {desc} ({cod})` → `srch_s_{cod}`, máx. 8) y por línea
(`🚌 L{cod} · {destino}` → `srch_l_{cod}`, máx. 5), más `⬅️ Menú`.

Sin resultados:

```text
🔎 Sin resultados para **{consulta}**.
Prueba un código, un nombre de calle o una línea.
```

### S07 · Destinos de una línea

**Disparador:** `srch_l_{cod}`, `/linea <linea>`, `favline_view_{linea}`.

```text
🚌 **L{cod}** — elige destino:
```

- Un botón por itinerario: `🚌 {destino}` → `ld_{itinerarioId}`.
- `🚏 Todas las paradas (N)` → `la_{code}`.
- `⭐ Guardar línea` → `favline_{code}`.
- `⬅️ Menú` → `menu_main`.

Error: `❌ Línea **{linea}** no encontrada.`

### S08 · Paradas de un destino

**Disparador:** `ld_{itinerarioId}`.

```text
🚏 **L{linea}** · {destino}
Elige tu parada:
```

Un botón por parada `🚏 {desc} ({cod})` → `ls_{cod}_{linea}` y `⬅️ Menú`.
Error: `❌ Sin paradas para este destino.`

### S09 · Todas las líneas (paginado)

**Disparador:** `lines_all` o `lpages_{n}`. 10 líneas por página.

```text
📃 **Todas las líneas** ({total})
Página {p}/{totalPages}

Elige una línea para ver sus destinos y paradas:
```

Botones de línea → `srch_l_{code}`; paginación `⬅️ Anterior` / `Siguiente ➡️`
→ `lpages_{n}`; `⬅️ Menú`.

### S10 · Todas las paradas de una línea (paginado)

**Disparador:** `la_{code}` o `lapages_{code}_{n}`. 15 paradas por página.

```text
🚏 **L{code}** — todas las paradas ({total})
Página {p}/{totalPages}

Elige una parada para ver llegadas y guardarla en favoritos:
```

Botones → `ls_{cod}_{code}`; paginación → `lapages_{code}_{n}`;
`⬅️ Destinos` → `srch_l_{code}`; `⬅️ Menú`.
Error: `❌ Sin paradas para la línea **{code}**.`

### S11 · Llegadas de una parada

**Disparador:** `srch_s_{cod}`, `ls_{cod}_{linea}`, `fav_view_{id}`,
`stop_refresh_{cod}`, texto numérico en `waiting_stop_arrivals`, `/parada`.

Mensaje transitorio: `🔍 Consultando datos en tiempo real...`

Cabecera: `🚏 **{stopName}** ({stopId})`. Cada llegada:

```text
🚌 **L{linea}** → {destino}
⏱ {ahora|N min}{ · hora} · {📡 tiempo real|🕒 horario}
```

Hasta 8 llegadas, con `…y N más` si hay más. Variantes sin datos:

| Situación | Texto |
|---|---|
| `SIN_SERVICIO` / `NO DATOS` | `🌙 Sin servicio en este momento.` |
| Otro estado | `📭 No hay llegadas ({estado}).` |
| Fallo de API | `⚠️ No se pudo consultar la API de Dbus. Inténtalo de nuevo.` |

Teclado: `🔄 Refrescar` (`stop_refresh_{stopId}`) · `🔔 Crear aviso aquí`
(`stop_alert_{stopId}`) · `⭐ Guardar parada` (`favstop_{stopId}`) ·
`⭐ Guardar línea` (`fav_add_{stopId}_{linea}`, solo si hay línea) · `⬅️ Menú`.

### S12 · Aviso: seleccionar línea

**Disparador:** `stop_alert_{stopId}` o texto en `waiting_stop_alert`.

```text
🔔 **Aviso en {stopName} ({stopId})**
Selecciona la línea:
```

Un botón por línea con datos reales: `L{lineId} → {destino}` →
`aline_{lineId}`. Estado: `select_line_alert`.
Sin líneas: `❌ No hay líneas con datos en la parada **{stopId}**.`

### S13 · Aviso: seleccionar minutos

**Disparador:** `aline_{lineId}`.

```text
🔔 **L{lineId}** en {stopName}
¿Cuántos minutos antes te aviso?
```

Opciones: `2 min`, `3 min`, `5 min`, `8 min`, `10 min`, `15 min` →
`minutes_{n}` (dos filas de tres) y `⬅️ Menú`. Estado: `select_minutes_alert`.

### S14 · Aviso creado

**Disparador:** `minutes_{n}` o `/aviso ...`.

```text
✅ **Aviso creado** (ID: `{id}`)
🚏 {stopName} ({stopId})
🚌 **L{linea}** → {destino}
🔔 Te avisaré **{min} min** antes
🔎 Próxima llegada: **{ahora|N min}** ({📡 tiempo real|🕒 horario})
```

Sin estimación: `🔎 Próxima llegada: sin estimación ahora mismo`.
Cuando se crea desde el flujo inline se añade botón `⬅️ Menú`; desde `/aviso`
es un mensaje nuevo sin teclado. Se guarda siempre el código público de línea
(alias interno `32` → pública `28`).

### S15 · Aviso duplicado

```text
⚠️ Ya tienes un aviso activo para **{stopName} · L{linea}**
(ID: `{id}`). Bórralo con `/borrar {id}` antes de crear otro.
```

Dedup por `(chat, parada, línea)`.

### S16 · Mis avisos

**Disparador:** `/lista` o `menu_my_alerts`. Sin avisos:

```text
📭 No tienes avisos activos. Usa `/aviso` o el botón 🔔 para crear uno.
```

Con avisos:

```text
📋 **Tus avisos:**

#1 · 🟢 Activo
  🚏 Loiola (1281)
  🚌 L5 · 🔔 5 min
```

Un botón `🗑️ #{id} · L{linea}` por aviso (`adel_{id}`) y `⬅️ Menú`.

### S17 · Favoritos (teclado inline)

**Disparador:** `menu_favorites`, `fav_add_*`, `fav_remove_*`, `favlin_remove_*`.

Sin favoritos:

```text
⭐ **No tienes favoritos**

Usa "🔎 Buscar" para encontrar tu parada o guárdala.
```

Con favoritos: `⭐ **Tus favoritos** ({n} paradas · {m} líneas):`

| Fila | Botones |
|---|---|
| Parada | `🚏 {nombre} · L{linea}\|todas` → `fav_view_{id}`; `🗑️` → `fav_remove_{id}` |
| Línea | `🚌 L{linea}` → `favline_view_{linea}`; `🗑️` → `favlin_remove_{id}` |
| Pie | `⬅️ Menú` |

### S18 · Favoritos (comando `/fav`)

Salida en texto, no inline:

```text
⭐ **Tus favoritos:**

**Paradas:**
• {nombre} ({stopId}) · L{linea}|todas (#{id})

**Líneas:**
• L{linea} · {destino} (#{id})
```

Sin favoritos: `⭐ No tienes favoritos. Usa `/buscar` para encontrar uno.`

Subcomandos: `/fav add <parada> [linea]` (guarda; sin línea = parada completa),
`/fav linea <linea>`, `/fav remove <id>`. Respuestas: `⭐ Favorito guardado`,
`⭐ Ya tenías este favorito guardado`, `⭐ Línea guardada`,
`⭐ Ya tenías esta línea guardada`, `❌ Línea no encontrada`,
`🗑️ Favorito eliminado`, `🗑️ Línea eliminada`.

### S19 · Actualizar

**Disparador:** `/actualizar` o `menu_refresh`.

```text
✅ Datos reales actualizados
🕒 {creacion}
🚏 {n} paradas · {m} en caché
📡 {fuente}
```

Si falla: `⚠️ No se pudo contactar con {fuente}`.

### S20 · Estado

**Disparador:** `/estado`.

```text
🟢 **Estado del bot**
📡 Fuente: {fuente}
🚏 Paradas en caché: {n} · catálogo: {m}
🔔 Avisos activos: {n}
⏱ Proceso activo: {s} s
```

### S21 · Aviso disparado (push)

**Disparador:** scheduler `cron '* * * * *'` en `alerts.ts` (comprobación cada
minuto, con guard de reentrada).

```text
🔔 **¡Aviso! L{linea} llega en {min} min**
🚌 **L{linea}** → {destino}
⏱ {min} min · {hora} · {📡 tiempo real|🕒 horario}
```

Envío directo por `bot.telegram.sendMessage()` al `chatId`; al enviar con éxito
el aviso pasa a inactivo y se reinicia su contador de fallos. Los avisos con más
de 7 días se desactivan solos. Los fallos de envío (chat bloqueado) se aislan
por chat y se acumulan: tras 3 fallos consecutivos el aviso se desactiva para no
repetir el mismo error cada minuto.

### S22 · Error no manejado (global)

`bot.catch()` en `index.ts` responde a cualquier excepción de handler con:

```text
⚠️ Ha ocurrido un error al procesar tu solicitud. Inténtalo de nuevo.
```

El detalle (stack) solo va al log; no se muestra al usuario. Es un mensaje nuevo,
sin teclado, y no altera la sesión.

### S23 · Validación y errores

| Situación | Texto |
|---|---|
| Línea no pasa por la parada | `❌ La línea **L{linea}** no pasa por la parada **{stopId}** ahora mismo.\nLíneas con datos: L…` |
| Sesión caducada en `aline_` | `❌ Sesión expirada, vuelve a crear el aviso` |
| Sesión caducada en `minutes_` | `❌ Sesión expirada, intenta de nuevo` |
| Minutos fuera de rango (callback) | `❌ Minutos fuera de rango` |
| Minutos fuera de rango (`/aviso`) | `❌ Los minutos deben estar entre 1 y 60` |
| Aviso ajeno o inexistente | `❌ Aviso no encontrado o no te pertenece` |
| Favorito ausente | `❌ Favorito no encontrado` |
| Mensajes de uso | `Uso: /parada <codigo> [linea]`, `/aviso <parada> <linea> <minutos>`, `/buscar <texto>`, `/linea <linea>`, `/borrar <id>`, `/lineas <codigo>` |

Respuestas efímeras vía `answerCbQuery` (toast de Telegram):
`⭐ Línea guardada`, `⭐ Ya tenías esta línea guardada`, `❌ Línea no encontrada`,
`⭐ Parada guardada`, `⭐ Ya tenías esta parada guardada`, `🔄 Actualizado`,
`✅ Aviso creado`, `⚠️ Ya tenías un aviso para esta línea`,
`❌ No se pudo crear el aviso`, `🗑️ Aviso eliminado`, `🗑️ Línea eliminada`,
`🗑️ Favorito eliminado`.

## 3. Sesión y ciclo de vida

Estados (`Step`): `idle`, `searching`, `waiting_stop_arrivals`,
`waiting_stop_alert`, `select_line_alert`, `select_minutes_alert`.

- TTL de sesión: 10 minutos; mapa limitado a 1000 entradas con poda.
- `clearSession()` al llegar a resultados, confirmar aviso, consultar llegadas
  o pulsar `/start`.
- Callbacks **stateless**: el dato viaja en `data` (`srch_s_1281`, `ld_42`,
  `ls_1281_28`, `minutes_5`). No se guarda el dato en sesión salvo el estado
  del flujo de aviso.
- Un callback antiguo no reactiva un flujo abandonado: `aline_` y `minutes_`
  comprueban el paso esperado antes de crear el aviso.

## 4. Mapa de callbacks

| Patrón | Acción |
|---|---|
| `menu_main`, `menu_cancel` | Volver al menú principal |
| `menu_arrivals`, `menu_alert` | Pedir código de parada |
| `menu_search` | Pedir texto de búsqueda |
| `menu_favorites` | Ver favoritos inline |
| `menu_my_alerts` | Ver avisos |
| `menu_refresh` | Actualizar datos |
| `menu_help` | Ayuda |
| `srch_s_\d+` | Llegadas de la parada |
| `srch_l_(.+)` | Destinos de la línea |
| `ld_\d+` | Paradas del destino |
| `ls_\d+_.+` | Llegadas de parada en esa línea |
| `lines_all`, `lpages_\d+` | Catálogo paginado de líneas |
| `la_[^_]+`, `lapages_[^_]+_\d+` | Todas las paradas de la línea |
| `aline_([^_]+)` | Elegir línea del aviso |
| `minutes_\d+` | Elegir antelación y crear aviso |
| `stop_refresh_\d+` | Invalidar caché de la parada y recargar |
| `stop_alert_\d+` | Iniciar aviso desde llegadas |
| `favstop_\d+` | Guardar parada favorita |
| `fav_add_\d+_([^_]+)` | Guardar parada+línea favorita |
| `fav_view_\d+`, `fav_remove_\d+` | Ver/borrar favorito de parada |
| `favline_([^_]+)` | Guardar línea favorita |
| `favline_view_(.+)`, `favlin_remove_\d+` | Ver/borrar línea favorita |
| `adel_\d+` | Borrar aviso |
