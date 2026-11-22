/**
 * Vistas y acciones compartidas por comandos y callbacks.
 *
 * Aquí viven los textos/teclados, los helpers de `Context` y las acciones que
 * consultan datos reales y escriben en Telegram. Es el nivel que imponen
 * `commands.ts` y `callbacks.ts`; no registra handlers en Telegraf.
 */
import { type Context, Markup } from 'telegraf';
import {
  UNKNOWN_DESTINATION,
  displayStopName,
  escapeMd,
  formatArrivalsList,
  getNextArrival,
  getNextArrivals,
  getStopLinesWithSource,
  matchesLine,
  normalizeLine,
  toPublicLineId,
} from './arrival.js';
import {
  type CatalogLine,
  type CatalogStop,
  getAllStopsForLine,
  getItinerariesForLine,
  getItineraryById,
  getLineByCode,
  getStopsForItinerary,
  listAllLines,
  paginate,
  searchLines,
  searchStops,
} from './catalog.js';
import {
  DuplicateAlertError,
  addFavorite,
  addFavoriteLine,
  createAlert,
  findActiveAlert,
  findFavorite,
  findFavoriteLine,
  getFavoriteLines,
  getFavorites,
} from './db.js';
import { type DbusStopTimes, fetchStopTimes } from './dbus-client.js';
import { clearSession, getSession } from './session.js';
import type { UserAlert } from './types.js';

// --- Helpers de Context ---

export function callbackData(ctx: Context): string {
  return ctx.callbackQuery && 'data' in ctx.callbackQuery
    ? ctx.callbackQuery.data
    : '';
}

export function textOf(ctx: Context): string {
  return ctx.message && 'text' in ctx.message ? ctx.message.text.trim() : '';
}

export async function answer(ctx: Context, text?: string): Promise<void> {
  try {
    await ctx.answerCbQuery(text);
  } catch {
    // La query puede haber expirado; no es crítico.
  }
}

type EditText = string;
type EditExtras = Parameters<Context['editMessageText']>[1];

/**
 * Telegram devuelve 400 si el texto no cambia o el mensaje es demasiado
 * antiguo para editarse. Ninguno de los dos casos debe romper el flujo.
 */
export async function safeEdit(
  ctx: Context,
  text: EditText,
  extras?: EditExtras
): Promise<void> {
  // Un mensaje de texto/comando pertenece al usuario: Telegram no permite
  // editarlo y devuelve 400 "message can't be edited". Editarlo solo tiene
  // sentido cuando venimos de un callback (el mensaje es nuestro).
  if (!ctx.callbackQuery) {
    try {
      await ctx.reply(text, extras);
    } catch {
      // El chat puede estar bloqueado; no hay más que hacer.
    }
    return;
  }
  try {
    await ctx.editMessageText(text, extras);
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    if (/not modified/i.test(message)) return;
    if (
      /message to edit not found|message can't be edited|message id invalid/i.test(
        message
      )
    ) {
      try {
        await ctx.reply(text, extras);
      } catch {
        // El chat puede estar bloqueado; no hay más que hacer.
      }
      return;
    }
    throw error;
  }
}

/**
 * Valida una línea contra los datos reales de la parada. Devuelve el mensaje
 * de error ya formateado, o null si la línea pasa (o no hay datos para juzgar).
 */
export async function validateLineAtStop(
  stopId: string,
  lineId: string,
  times?: DbusStopTimes | null
): Promise<string | null> {
  const { lines } = await getStopLinesWithSource(stopId, times);
  if (!lines.length || lines.some((line) => matchesLine(line, lineId))) {
    return null;
  }
  return (
    `❌ La línea **L${escapeMd(lineId)}** no pasa por la parada **${escapeMd(stopId)}** ahora mismo.\n` +
    `Líneas con datos: ${lines.map((line) => `L${escapeMd(line.lineId)}`).join(', ')}`
  );
}

// --- Textos y teclados ---

export function mainKeyboard() {
  return Markup.inlineKeyboard([
    [Markup.button.callback('🚌 Ver llegadas', 'menu_arrivals')],
    [Markup.button.callback('🔔 Crear aviso', 'menu_alert')],
    [Markup.button.callback('⭐ Favoritos', 'menu_favorites')],
    [Markup.button.callback('📋 Mis avisos', 'menu_my_alerts')],
    [Markup.button.callback('🔎 Buscar parada o línea', 'menu_search')],
    [Markup.button.callback('📃 Todas las líneas', 'lines_all')],
    [
      Markup.button.callback('🔄 Actualizar', 'menu_refresh'),
      Markup.button.callback('ℹ️ Ayuda', 'menu_help'),
    ],
  ]);
}

export const MAIN_TEXT =
  '🚌 **Dbus Donostia — llegadas en tiempo real**\n\nElige una opción:';

export const HELP_TEXT =
  'ℹ️ **Comandos**\n\n' +
  '`/parada <codigo> [linea]` — llegadas de una parada (código de la marquesina)\n' +
  '`/aviso <parada> <linea> <minutos>` — avisar antes de que llegue el bus\n' +
  '`/lista` — tus avisos activos\n' +
  '`/borrar <id>` — eliminar un aviso\n' +
  '`/buscar <texto>` — parada (código o nombre) o línea (número/destino)\n' +
  '`/linea <linea>` — destinos y paradas de una línea\n' +
  '`/lineas` — todas las líneas · `/lineas <parada>` — líneas en esa parada\n' +
  '`/fav` — tus favoritos · `/fav add <parada> [linea]` · `/fav linea <linea>`\n' +
  '`/actualizar` — refrescar datos · `/estado` — diagnóstico\n' +
  '`/ubicacion` — paradas cercanas con OpenStreetMap (sin guardar tu posición)\n\n' +
  'Busca por código (`1`), nombre (`Boulevard`), línea (`28`) o destino ' +
  '(`Ospitaleak`). Sin línea en `/fav add` guardas la parada completa.\n' +
  'También acepto el id interno de Dbus (p. ej. `32` = línea pública `28`).\n' +
  'Datos reales de la API de Dbus 📡';

export async function showMainMenu(ctx: Context): Promise<void> {
  await safeEdit(ctx, MAIN_TEXT, {
    parse_mode: 'Markdown',
    ...mainKeyboard(),
  });
}

export async function askStopCode(
  ctx: Context,
  mode: 'arrivals' | 'alert'
): Promise<void> {
  const chatId = ctx.chat?.id;
  if (!chatId) return;
  const session = getSession(chatId);
  session.step =
    mode === 'alert' ? 'waiting_stop_alert' : 'waiting_stop_arrivals';

  await safeEdit(
    ctx,
    mode === 'alert'
      ? '🔔 **Crear aviso**\n\nEscribe el código de la parada (el número de la marquesina):'
      : '🚌 **Ver llegadas**\n\nEscribe el código de la parada (el número de la marquesina):',
    {
      parse_mode: 'Markdown',
      ...Markup.inlineKeyboard([
        [Markup.button.callback('🔎 Buscar parada o línea', 'menu_search')],
        [Markup.button.callback('⬅️ Menú', 'menu_main')],
      ]),
    }
  );
}

export async function askSearch(ctx: Context): Promise<void> {
  const chatId = ctx.chat?.id;
  if (chatId) getSession(chatId).step = 'searching';
  await safeEdit(
    ctx,
    '🔎 **Buscar parada o línea**\n\n' +
      'Escribe el nombre de la parada o de la calle, el número de la marquesina, ' +
      'el número de línea o su destino. Verás opciones para elegir.\n\n' +
      'Ejemplos: `Boulevard` · `1` · `28` · `Ospitaleak`',
    {
      parse_mode: 'Markdown',
      ...Markup.inlineKeyboard([
        [Markup.button.callback('⬅️ Menú', 'menu_main')],
      ]),
    }
  );
}

/**
 * Un único constructor del teclado de resultados: `/buscar` y el menú de
 * búsqueda comparten el contrato `srch_s_<cod>` / `srch_l_<línea pública>`.
 */
export function buildSearchRows(
  stops: CatalogStop[],
  lines: CatalogLine[],
  withBack: boolean
): KeyboardRow[] {
  const rows: KeyboardRow[] = stops.map((stop) => [
    Markup.button.callback(
      `🚏 ${stop.desc.slice(0, 28)} (${stop.cod})`,
      `srch_s_${stop.cod}`
    ),
  ]);
  for (const line of lines) {
    rows.push([
      Markup.button.callback(
        `🚌 L${line.cod} · ${line.destinations[0]?.slice(0, 22) ?? ''}`,
        `srch_l_${normalizeLine(line.cod)}`
      ),
    ]);
  }
  if (withBack) rows.push([Markup.button.callback('⬅️ Menú', 'menu_main')]);
  return rows;
}

export async function showSearchResults(
  ctx: Context,
  query: string
): Promise<void> {
  const chatId = ctx.chat?.id;
  const q = query.trim();
  if (!chatId || !q) return;
  const stops = searchStops(q, 8);
  const lines = searchLines(q, 5);
  const back = [[Markup.button.callback('⬅️ Menú', 'menu_main')]];

  if (!stops.length && !lines.length) {
    await safeEdit(
      ctx,
      `🔎 Sin resultados para **${escapeMd(q)}**.\nPrueba un código, un nombre de calle o una línea.`,
      { parse_mode: 'Markdown', ...Markup.inlineKeyboard(back) }
    );
    return;
  }

  const rows = buildSearchRows(stops, lines, true);
  await safeEdit(ctx, `🔎 Resultados para **${escapeMd(q)}**`, {
    parse_mode: 'Markdown',
    ...Markup.inlineKeyboard(rows),
  });
}

export async function showLineDestinations(
  ctx: Context,
  lineId: string
): Promise<void> {
  const line = getLineByCode(lineId);
  const itineraries = line ? getItinerariesForLine(lineId) : [];
  const back = [[Markup.button.callback('⬅️ Menú', 'menu_main')]];
  if (!line || !itineraries.length) {
    await safeEdit(ctx, `❌ Línea **${escapeMd(lineId)}** no encontrada.`, {
      parse_mode: 'Markdown',
      ...Markup.inlineKeyboard(back),
    });
    return;
  }
  const buttons = itineraries.map((itinerario) =>
    Markup.button.callback(
      `🚌 ${itinerario.destination.slice(0, 34)}`,
      `ld_${itinerario.id}`
    )
  );
  await safeEdit(ctx, `🚌 **L${escapeMd(line.cod)}** — elige destino:`, {
    parse_mode: 'Markdown',
    ...Markup.inlineKeyboard([
      ...buttons.map((button) => [button]),
      [
        Markup.button.callback(
          `🚏 Todas las paradas (${getAllStopsForLine(line.cod).length})`,
          `la_${normalizeLine(line.cod)}`
        ),
      ],
      [
        Markup.button.callback(
          '⭐ Guardar línea',
          `favline_${normalizeLine(line.cod)}`
        ),
      ],
      ...back,
    ]),
  });
}

export async function showLineStops(
  ctx: Context,
  itineraryId: string
): Promise<void> {
  const itinerary = getItineraryById(itineraryId);
  const stops = getStopsForItinerary(itineraryId);
  const back = [[Markup.button.callback('⬅️ Menú', 'menu_main')]];
  if (!itinerary || !stops.length) {
    await safeEdit(ctx, '❌ Sin paradas para este destino.', {
      parse_mode: 'Markdown',
      ...Markup.inlineKeyboard(back),
    });
    return;
  }
  const buttons = stops.map((stop) =>
    Markup.button.callback(
      `🚏 ${stop.desc.slice(0, 26)} (${stop.cod})`,
      `ls_${stop.cod}_${itinerary.lineCod}`
    )
  );
  await safeEdit(
    ctx,
    `🚏 **L${escapeMd(itinerary.lineCod)}** · ${escapeMd(itinerary.destination)}\nElige tu parada:`,
    {
      parse_mode: 'Markdown',
      ...Markup.inlineKeyboard([...buttons.map((button) => [button]), ...back]),
    }
  );
}

const LINES_PER_PAGE = 10;
const STOPS_PER_PAGE = 15;

type KeyboardRow = ReturnType<typeof Markup.button.callback>[];

function pagerButtons(
  hasPrev: boolean,
  hasNext: boolean,
  prevData: string,
  nextData: string
): KeyboardRow {
  const buttons: KeyboardRow = [];
  if (hasPrev) buttons.push(Markup.button.callback('⬅️ Anterior', prevData));
  if (hasNext) buttons.push(Markup.button.callback('Siguiente ➡️', nextData));
  return buttons;
}

/** Catálogo completo de líneas, paginado. El índice de búsqueda es real. */
export async function showAllLines(ctx: Context, page = 0): Promise<void> {
  const paged = paginate(listAllLines(), page, LINES_PER_PAGE);
  const rows: KeyboardRow[] = paged.items.map((line) => [
    Markup.button.callback(
      `🚌 L${line.cod} · ${(line.destinations[0] ?? '').slice(0, 22)}`,
      `srch_l_${normalizeLine(line.cod)}`
    ),
  ]);
  const pager = pagerButtons(
    paged.hasPrev,
    paged.hasNext,
    `lpages_${paged.page - 1}`,
    `lpages_${paged.page + 1}`
  );
  if (pager.length) rows.push(pager);
  rows.push([Markup.button.callback('⬅️ Menú', 'menu_main')]);

  await safeEdit(
    ctx,
    `📃 **Todas las líneas** (${paged.total})\n` +
      `Página ${paged.page + 1}/${paged.totalPages}\n\nElige una línea para ver sus destinos y paradas:`,
    { parse_mode: 'Markdown', ...Markup.inlineKeyboard(rows) }
  );
}

/** Todas las paradas de una línea, de todos sus destinos, paginadas. */
export async function showAllStopsForLine(
  ctx: Context,
  lineId: string,
  page = 0
): Promise<void> {
  const line = getLineByCode(lineId);
  const code = normalizeLine(lineId);
  const stops = line ? getAllStopsForLine(line.cod) : [];
  const back: KeyboardRow = [
    Markup.button.callback('⬅️ Destinos', `srch_l_${code}`),
    Markup.button.callback('⬅️ Menú', 'menu_main'),
  ];
  if (!line || !stops.length) {
    await safeEdit(ctx, `❌ Sin paradas para la línea **${escapeMd(code)}**.`, {
      parse_mode: 'Markdown',
      ...Markup.inlineKeyboard([back]),
    });
    return;
  }

  const paged = paginate(stops, page, STOPS_PER_PAGE);
  const rows: KeyboardRow[] = paged.items.map((stop) => [
    Markup.button.callback(
      `🚏 ${stop.desc.slice(0, 26)} (${stop.cod})`,
      `ls_${stop.cod}_${code}`
    ),
  ]);
  const pager = pagerButtons(
    paged.hasPrev,
    paged.hasNext,
    `lapages_${code}_${paged.page - 1}`,
    `lapages_${code}_${paged.page + 1}`
  );
  if (pager.length) rows.push(pager);
  rows.push(back);

  await safeEdit(
    ctx,
    `🚏 **L${escapeMd(code)}** — todas las paradas (${paged.total})\n` +
      `Página ${paged.page + 1}/${paged.totalPages}\n\nElige una parada para ver llegadas y guardarla en favoritos:`,
    { parse_mode: 'Markdown', ...Markup.inlineKeyboard(rows) }
  );
}

type LineFavoriteResult = 'added' | 'duplicate' | 'missing';

export function storeLineFavorite(
  chatId: number,
  lineId: string
): LineFavoriteResult {
  const line = getLineByCode(lineId);
  if (!line) return 'missing';
  const code = normalizeLine(line.cod);
  if (findFavoriteLine(chatId, code)) return 'duplicate';
  addFavoriteLine({
    chatId,
    lineId: code,
    lineName: `L${code}`,
    destination: line.destinations[0] || `L${code}`,
  });
  return 'added';
}

export async function saveLineFavorite(
  ctx: Context,
  lineId: string
): Promise<void> {
  const chatId = ctx.chat?.id;
  if (!chatId) return;
  const result = storeLineFavorite(chatId, lineId);
  await answer(
    ctx,
    result === 'added'
      ? '⭐ Línea guardada'
      : result === 'duplicate'
        ? '⭐ Ya tenías esta línea guardada'
        : '❌ Línea no encontrada'
  );
}

export async function showArrivalsForStop(
  ctx: Context,
  stopId: string,
  lineId?: string,
  /** Respuesta ya consultada por el comando: una sola llamada en vivo. */
  times?: DbusStopTimes | null
): Promise<void> {
  const chatId = ctx.chat?.id;
  if (chatId) clearSession(chatId);

  await safeEdit(ctx, '🔍 Consultando datos en tiempo real...');

  const data = times !== undefined ? times : await fetchStopTimes(stopId);
  const arrivals = await getNextArrivals(stopId, lineId, false, data);
  const stopName = displayStopName(stopId, data?.parada?.desc);

  const favoriteLine = lineId || arrivals[0]?.lineId;
  const keyboard = Markup.inlineKeyboard([
    [Markup.button.callback('🔄 Refrescar', `stop_refresh_${stopId}`)],
    [Markup.button.callback('🔔 Crear aviso aquí', `stop_alert_${stopId}`)],
    [
      Markup.button.callback('⭐ Guardar parada', `favstop_${stopId}`),
      ...(favoriteLine
        ? [
            Markup.button.callback(
              '⭐ Guardar línea',
              `fav_add_${stopId}_${favoriteLine}`
            ),
          ]
        : []),
    ],
    [Markup.button.callback('⬅️ Menú', 'menu_main')],
  ]);

  // `avisos` viene en la respuesta real de Dbus y ya se normaliza en @dbus/core.
  const avisos = data?.avisos;
  let text = formatArrivalsList(
    stopName,
    arrivals,
    data?.estado,
    stopId,
    avisos
  );
  if (!arrivals.length) {
    // Sin servicio la API no devuelve líneas. El índice sí sabe qué líneas
    // pasan por la parada: se muestra como dato de descubrimiento, jamás como
    // llegada ni horario.
    const { lines, source } = await getStopLinesWithSource(stopId, data);
    if (source === 'catalog' && lines.length) {
      text += `\n\n🚌 Líneas que pasan por la parada (índice Dbus, sin horarios ahora):\n${lines
        .map(
          (line) =>
            `• **L${escapeMd(line.lineId)}**${line.destination ? ` → ${escapeMd(line.destination)}` : ''}`
        )
        .join('\n')}`;
    }
  }

  await safeEdit(ctx, text, {
    parse_mode: 'Markdown',
    ...keyboard,
  });
}

export async function showStopLines(
  ctx: Context,
  stopId: string
): Promise<void> {
  const stopName = displayStopName(stopId);
  const { lines, source } = await getStopLinesWithSource(stopId);
  if (!lines.length) {
    await safeEdit(
      ctx,
      `❌ No hay líneas con datos en la parada **${escapeMd(stopId)}**.`,
      {
        parse_mode: 'Markdown',
        ...Markup.inlineKeyboard([
          [Markup.button.callback('⬅️ Menú', 'menu_main')],
        ]),
      }
    );
    return;
  }

  const buttons = lines.map((line) =>
    Markup.button.callback(
      `L${line.lineId} → ${line.destination.slice(0, 26)}`,
      `aline_${stopId}_${normalizeLine(line.lineId)}`
    )
  );
  const sourceNote =
    source === 'catalog'
      ? '\n(Mostrando líneas del índice: ahora mismo no hay datos en vivo.)'
      : '';
  await safeEdit(
    ctx,
    `🔔 **Aviso en ${escapeMd(stopName)} (${stopId})**\nSelecciona la línea:${sourceNote}`,
    {
      parse_mode: 'Markdown',
      ...Markup.inlineKeyboard([
        ...buttons.map((button) => [button]),
        [Markup.button.callback('⬅️ Menú', 'menu_main')],
      ]),
    }
  );
}

export async function showMinutesSelector(
  ctx: Context,
  stopId: string,
  lineId: string
): Promise<void> {
  const code = normalizeLine(lineId);
  const stopName = displayStopName(stopId);

  const options = [2, 3, 5, 8, 10, 15];
  const buttons = options.map((min) =>
    Markup.button.callback(`${min} min`, `minutes_${stopId}_${code}_${min}`)
  );

  await safeEdit(
    ctx,
    `🔔 **L${escapeMd(code)}** en ${escapeMd(stopName)}\n¿Cuántos minutos antes te aviso?`,
    {
      parse_mode: 'Markdown',
      ...Markup.inlineKeyboard([
        buttons.slice(0, 3),
        buttons.slice(3, 6),
        [Markup.button.callback('⬅️ Menú', 'menu_main')],
      ]),
    }
  );
}

export function buildAlertsText(alerts: UserAlert[]): string {
  if (!alerts.length) {
    return '📭 No tienes avisos activos. Usa `/aviso` o el botón 🔔 para crear uno.';
  }
  let msg = '📋 **Tus avisos:**\n\n';
  for (const alert of alerts) {
    const status = alert.active ? '🟢 Activo' : '🔴 Inactivo';
    msg += `#${alert.id} · ${status}\n`;
    msg += `  🚏 ${escapeMd(alert.stopName)} (${escapeMd(alert.stopId)})\n`;
    msg += `  🚌 L${escapeMd(alert.lineId)} · 🔔 ${alert.minutesAhead} min\n\n`;
  }
  return msg;
}

export function alertsKeyboard(alerts: UserAlert[]) {
  const rows = alerts.map((alert) => [
    Markup.button.callback(
      `🗑️ #${alert.id} · L${alert.lineId}`,
      `adel_${alert.id}`
    ),
  ]);
  rows.push([Markup.button.callback('⬅️ Menú', 'menu_main')]);
  return Markup.inlineKeyboard(rows);
}

export async function showFavorites(ctx: Context): Promise<void> {
  const chatId = ctx.chat?.id;
  if (!chatId) return;
  const favs = getFavorites(chatId);
  const lines = getFavoriteLines(chatId);

  if (!favs.length && !lines.length) {
    await safeEdit(
      ctx,
      '⭐ **No tienes favoritos**\n\nUsa "🔎 Buscar" para encontrar tu parada o línea y guárdala.',
      {
        parse_mode: 'Markdown',
        ...Markup.inlineKeyboard([
          [Markup.button.callback('⬅️ Menú', 'menu_main')],
        ]),
      }
    );
    return;
  }

  const rows = [
    ...favs.map((fav) => [
      Markup.button.callback(
        `🚏 ${fav.stopName.slice(0, 20)} · ${fav.lineId ? `L${fav.lineId}` : 'todas'}`,
        `fav_view_${fav.id}`
      ),
      Markup.button.callback('🗑️', `fav_remove_${fav.id}`),
    ]),
    ...lines.map((line) => [
      Markup.button.callback(
        `🚌 L${line.lineId}`,
        `favline_view_${line.lineId}`
      ),
      Markup.button.callback('🗑️', `favlin_remove_${line.id}`),
    ]),
    [Markup.button.callback('⬅️ Menú', 'menu_main')],
  ];

  await safeEdit(
    ctx,
    `⭐ **Tus favoritos** (${favs.length} paradas · ${lines.length} líneas):`,
    {
      parse_mode: 'Markdown',
      ...Markup.inlineKeyboard(rows),
    }
  );
}

/**
 * Guarda un favorito evitando duplicados por (parada, línea).
 * Con línea vacía guarda la parada completa (todas las líneas).
 */
export async function saveFavorite(
  chatId: number,
  stopId: string,
  rawLineId = ''
): Promise<boolean> {
  const requestedLine = toPublicLineId(rawLineId, stopId);
  const arrival = requestedLine
    ? await getNextArrival(stopId, requestedLine)
    : null;
  const lineId = arrival ? normalizeLine(arrival.lineId) : requestedLine;
  if (findFavorite(chatId, stopId, lineId)) return false;
  addFavorite({
    chatId,
    stopId,
    stopName: displayStopName(stopId),
    lineId,
    lineName: lineId ? `L${lineId}` : 'Todas',
    destination:
      arrival?.destination || (lineId ? `L${lineId}` : 'Todas las líneas'),
  });
  return true;
}

type AlertCreationResult = 'created' | 'duplicate' | 'no-chat';

function duplicateAlertText(
  stopName: string,
  lineId: string,
  id: number
): string {
  return (
    `⚠️ Ya tienes un aviso activo para **${escapeMd(stopName)} · L${escapeMd(lineId)}** ` +
    `(ID: \`${id}\`). Bórralo con \`/borrar ${id}\` antes de crear otro.`
  );
}

export async function createAndConfirmAlert(
  ctx: Context,
  stopId: string,
  lineId: string,
  minutesAhead: number,
  asEdit: boolean,
  /** Respuesta ya consultada por `/aviso`: una sola llamada en vivo. */
  times?: DbusStopTimes | null
): Promise<AlertCreationResult> {
  const chatId = ctx.chat?.id;
  if (!chatId) return 'no-chat';

  const arrival = await getNextArrival(stopId, lineId, times);
  // Con llegada real, su `lineId` YA es el código público: no se re-resuelve
  // contra el índice (en paradas con 26 y 28, un "28" se convertía en 26).
  // Sin datos en vivo, el índice resuelve el alias en contexto de parada.
  const publicLineId = arrival
    ? normalizeLine(arrival.lineId)
    : toPublicLineId(lineId, stopId);
  const stopName = displayStopName(stopId);
  const existing = findActiveAlert(chatId, stopId, publicLineId);
  if (existing) {
    clearSession(chatId);
    const text = duplicateAlertText(stopName, publicLineId, existing.id);
    if (asEdit) await safeEdit(ctx, text, { parse_mode: 'Markdown' });
    else await ctx.reply(text, { parse_mode: 'Markdown' });
    return 'duplicate';
  }

  let alert: UserAlert;
  try {
    alert = createAlert({
      chatId,
      stopId,
      stopName,
      lineId: publicLineId,
      lineName: `L${publicLineId}`,
      destination: arrival?.destination || UNKNOWN_DESTINATION,
      minutesAhead,
      active: true,
    });
  } catch (error) {
    // Carrera entre dos comandos: el guard rápido de arriba no la ve, pero la
    // transacción en db.ts sí. El usuario recibe el mismo mensaje.
    if (!(error instanceof DuplicateAlertError)) throw error;
    clearSession(chatId);
    const text = duplicateAlertText(stopName, publicLineId, error.alert.id);
    if (asEdit) await safeEdit(ctx, text, { parse_mode: 'Markdown' });
    else await ctx.reply(text, { parse_mode: 'Markdown' });
    return 'duplicate';
  }

  // Contexto útil al crear el aviso: cuándo llega el próximo bus. Con sin
  // servicio no hay estimación, pero el mensaje sigue siendo informativo.
  const eta = arrival
    ? `🔎 Próxima llegada: **${arrival.minutes === 0 ? 'ahora' : `${arrival.minutes} min`}** ` +
      `(${arrival.realtime ? '📡 tiempo real' : '🕒 horario'})`
    : '🔎 Próxima llegada: sin estimación ahora mismo';
  const msg = `✅ **Aviso creado** (ID: \`${alert.id}\`)
 🚏 ${escapeMd(stopName)} (${escapeMd(stopId)})
 🚌 **L${escapeMd(publicLineId)}** → ${escapeMd(alert.destination)}
 🔔 Te avisaré **${minutesAhead} min** antes
 ${eta}`;

  clearSession(chatId);
  if (asEdit) {
    await safeEdit(ctx, msg, {
      parse_mode: 'Markdown',
      ...Markup.inlineKeyboard([
        [Markup.button.callback('⬅️ Menú', 'menu_main')],
      ]),
    });
  } else {
    await ctx.reply(msg, { parse_mode: 'Markdown' });
  }
  return 'created';
}
