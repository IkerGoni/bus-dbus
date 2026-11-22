import { type Context, Markup, type Telegraf } from 'telegraf';
import { setBotInstance, startAlertScheduler } from './alerts.js';
import {
  UNKNOWN_DESTINATION,
  escapeMd,
  formatArrivalsList,
  getNextArrival,
  getNextArrivals,
  getStopLines,
  matchesLine,
  normalizeLine,
} from './arrival.js';
import {
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
import { config } from './config.js';
import {
  addFavorite,
  addFavoriteLine,
  createAlert,
  deleteAlert,
  findActiveAlert,
  findFavorite,
  findFavoriteLine,
  getActiveAlerts,
  getAlertById,
  getAlertsByChat,
  getFavoriteLines,
  getFavorites,
  removeFavorite,
  removeFavoriteLine,
} from './db.js';
import {
  clearCache,
  fetchPopularStops,
  fetchStopTimes,
  getCacheStats,
  getCachedStopCodes,
  getDataSource,
  getStopName,
  invalidateStop,
} from './dbus-client.js';
import type { UserAlert } from './types.js';

type Step =
  | 'idle'
  | 'searching'
  | 'waiting_stop_arrivals'
  | 'waiting_stop_alert'
  | 'select_line_alert'
  | 'select_minutes_alert';

interface BotSession {
  step: Step;
  stopId: string;
  stopName: string;
  lineId: string;
  lastUsed: number;
}

const SESSION_TTL_MS = 10 * 60 * 1000;
const MAX_SESSIONS = 1000;
const userSessions = new Map<number, BotSession>();

function newSession(): BotSession {
  return {
    step: 'idle',
    stopId: '',
    stopName: '',
    lineId: '',
    lastUsed: Date.now(),
  };
}

function getSession(chatId: number): BotSession {
  const existing = userSessions.get(chatId);
  if (existing && Date.now() - existing.lastUsed < SESSION_TTL_MS) {
    existing.lastUsed = Date.now();
    return existing;
  }
  const session = newSession();
  userSessions.set(chatId, session);
  // Los flujos se abandonan a menudo; evita que el mapa crezca sin límite.
  if (userSessions.size > MAX_SESSIONS) pruneSessions();
  return session;
}

function clearSession(chatId: number): void {
  userSessions.delete(chatId);
}

/** Elimina sesiones caducadas; también usable en tests. */
export function pruneSessions(now = Date.now()): number {
  let removed = 0;
  for (const [chatId, session] of userSessions) {
    if (now - session.lastUsed >= SESSION_TTL_MS) {
      userSessions.delete(chatId);
      removed++;
    }
  }
  return removed;
}

/** Solo para tests: número de sesiones vivas. */
export function getSessionCount(): number {
  return userSessions.size;
}

function callbackData(ctx: Context): string {
  return ctx.callbackQuery && 'data' in ctx.callbackQuery
    ? ctx.callbackQuery.data
    : '';
}

function textOf(ctx: Context): string {
  return ctx.message && 'text' in ctx.message ? ctx.message.text.trim() : '';
}

async function answer(ctx: Context, text?: string): Promise<void> {
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
async function safeEdit(
  ctx: Context,
  text: EditText,
  extras?: EditExtras
): Promise<void> {
  try {
    await ctx.editMessageText(text, extras);
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    if (/not modified|message to edit not found/i.test(message)) {
      if (/message to edit not found/i.test(message)) {
        try {
          await ctx.reply(text, extras);
        } catch {
          // El chat puede estar bloqueado; no hay más que hacer.
        }
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
async function validateLineAtStop(
  stopId: string,
  lineId: string
): Promise<string | null> {
  const lines = await getStopLines(stopId);
  if (!lines.length || lines.some((line) => matchesLine(line, lineId))) {
    return null;
  }
  return (
    `❌ La línea **L${escapeMd(lineId)}** no pasa por la parada **${escapeMd(stopId)}** ahora mismo.\n` +
    `Líneas con datos: ${lines.map((line) => `L${escapeMd(line.lineId)}`).join(', ')}`
  );
}

function mainKeyboard() {
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

const MAIN_TEXT =
  '🚌 **Dbus Donostia — llegadas en tiempo real**\n\nElige una opción:';

const HELP_TEXT =
  'ℹ️ **Comandos**\n\n' +
  '`/parada <codigo> [linea]` — llegadas de una parada (código de la marquesina)\n' +
  '`/aviso <parada> <linea> <minutos>` — avisar antes de que llegue el bus\n' +
  '`/lista` — tus avisos activos\n' +
  '`/borrar <id>` — eliminar un aviso\n' +
  '`/buscar <texto>` — parada (código o nombre) o línea (número/destino)\n' +
  '`/linea <linea>` — destinos y paradas de una línea\n' +
  '`/lineas` — todas las líneas · `/lineas <parada>` — líneas en esa parada\n' +
  '`/fav` — tus favoritos · `/fav add <parada> [linea]` · `/fav linea <linea>`\n' +
  '`/actualizar` — refrescar datos · `/estado` — diagnóstico\n\n' +
  'Busca por código (`1`), nombre (`Boulevard`), línea (`28`) o destino ' +
  '(`Ospitaleak`). Sin línea en `/fav add` guardas la parada completa.\n' +
  'También acepto el id interno de Dbus (p. ej. `32` = línea pública `28`).\n' +
  'Datos reales de la API de Dbus 📡';

export function setBotReference(bot: Telegraf<Context>): void {
  setBotInstance(bot);
}

export async function handleStart(ctx: Context): Promise<void> {
  const chatId = ctx.chat?.id;
  if (chatId) clearSession(chatId);
  await ctx.reply(MAIN_TEXT, {
    parse_mode: 'Markdown',
    ...mainKeyboard(),
  });
}

export async function handleHelp(ctx: Context): Promise<void> {
  if (ctx.callbackQuery) {
    await safeEdit(ctx, HELP_TEXT, {
      parse_mode: 'Markdown',
      ...Markup.inlineKeyboard([
        [Markup.button.callback('⬅️ Menú', 'menu_main')],
      ]),
    });
    await answer(ctx);
    return;
  }
  await ctx.reply(HELP_TEXT, { parse_mode: 'Markdown' });
}

async function showMainMenu(ctx: Context): Promise<void> {
  await safeEdit(ctx, MAIN_TEXT, {
    parse_mode: 'Markdown',
    ...mainKeyboard(),
  });
}

async function askStopCode(
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

async function askSearch(ctx: Context): Promise<void> {
  const chatId = ctx.chat?.id;
  if (chatId) getSession(chatId).step = 'searching';
  await safeEdit(
    ctx,
    '🔎 **Buscar**\n\nEscribe un código de parada, nombre de calle, línea o destino:\n' +
      '`1` · `Boulevard` · `28` · `Ospitaleak`',
    {
      parse_mode: 'Markdown',
      ...Markup.inlineKeyboard([
        [Markup.button.callback('⬅️ Menú', 'menu_main')],
      ]),
    }
  );
}

async function showSearchResults(ctx: Context, query: string): Promise<void> {
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

  const rows: ReturnType<typeof Markup.button.callback>[][] = [];
  for (const stop of stops) {
    rows.push([
      Markup.button.callback(
        `🚏 ${stop.desc.slice(0, 28)} (${stop.cod})`,
        `srch_s_${stop.cod}`
      ),
    ]);
  }
  for (const line of lines) {
    rows.push([
      Markup.button.callback(
        `🚌 L${line.cod} · ${line.destinations[0]?.slice(0, 22) ?? ''}`,
        `srch_l_${line.cod}`
      ),
    ]);
  }
  rows.push(...back);
  await safeEdit(ctx, `🔎 Resultados para **${escapeMd(q)}**`, {
    parse_mode: 'Markdown',
    ...Markup.inlineKeyboard(rows),
  });
}

async function showLineDestinations(
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

async function showLineStops(ctx: Context, itineraryId: string): Promise<void> {
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
async function showAllLines(ctx: Context, page = 0): Promise<void> {
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
async function showAllStopsForLine(
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

function storeLineFavorite(chatId: number, lineId: string): LineFavoriteResult {
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

async function saveLineFavorite(ctx: Context, lineId: string): Promise<void> {
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

async function showArrivalsForStop(
  ctx: Context,
  stopId: string,
  lineId?: string
): Promise<void> {
  const chatId = ctx.chat?.id;
  if (chatId) clearSession(chatId);

  await safeEdit(ctx, '🔍 Consultando datos en tiempo real...');

  const times = await fetchStopTimes(stopId);
  const arrivals = await getNextArrivals(stopId, lineId);
  const stopName = times?.parada?.desc || getStopName(stopId);

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

  await safeEdit(ctx, formatArrivalsList(stopName, arrivals, times?.estado), {
    parse_mode: 'Markdown',
    ...keyboard,
  });
}

async function showStopLines(ctx: Context, stopId: string): Promise<void> {
  const chatId = ctx.chat?.id;
  if (!chatId) return;

  const session = getSession(chatId);
  session.step = 'select_line_alert';
  session.stopId = stopId;
  session.stopName = getStopName(stopId);

  const lines = await getStopLines(stopId);
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
      `aline_${line.lineId}`
    )
  );
  await safeEdit(
    ctx,
    `🔔 **Aviso en ${escapeMd(session.stopName)} (${stopId})**\nSelecciona la línea:`,
    {
      parse_mode: 'Markdown',
      ...Markup.inlineKeyboard([
        ...buttons.map((button) => [button]),
        [Markup.button.callback('⬅️ Menú', 'menu_main')],
      ]),
    }
  );
}

async function showMinutesSelector(ctx: Context): Promise<void> {
  const chatId = ctx.chat?.id;
  if (!chatId) return;
  const session = getSession(chatId);
  session.step = 'select_minutes_alert';

  const options = [2, 3, 5, 8, 10, 15];
  const buttons = options.map((min) =>
    Markup.button.callback(`${min} min`, `minutes_${min}`)
  );

  await safeEdit(
    ctx,
    `🔔 **L${escapeMd(session.lineId)}** en ${escapeMd(session.stopName)}\n¿Cuántos minutos antes te aviso?`,
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

function buildAlertsText(alerts: UserAlert[]): string {
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

type AlertCreationResult = 'created' | 'duplicate' | 'no-chat';

async function createAndConfirmAlert(
  ctx: Context,
  stopId: string,
  lineId: string,
  minutesAhead: number,
  asEdit: boolean
): Promise<AlertCreationResult> {
  const chatId = ctx.chat?.id;
  if (!chatId) return 'no-chat';

  const arrival = await getNextArrival(stopId, lineId);
  // El usuario puede escribir el id interno (32); guardamos siempre el
  // código público (28) para que avisos y mensajes sean coherentes.
  const publicLineId = normalizeLine(arrival?.lineId || lineId);
  const stopName = getStopName(stopId);
  const existing = findActiveAlert(chatId, stopId, publicLineId);
  if (existing) {
    clearSession(chatId);
    const text =
      `⚠️ Ya tienes un aviso activo para **${escapeMd(stopName)} · L${escapeMd(publicLineId)}** ` +
      `(ID: \`${existing.id}\`). Bórralo con \`/borrar ${existing.id}\` antes de crear otro.`;
    if (asEdit) await safeEdit(ctx, text, { parse_mode: 'Markdown' });
    else await ctx.reply(text, { parse_mode: 'Markdown' });
    return 'duplicate';
  }

  const alert = createAlert({
    chatId,
    stopId,
    stopName,
    lineId: publicLineId,
    lineName: `L${publicLineId}`,
    destination: arrival?.destination || UNKNOWN_DESTINATION,
    minutesAhead,
    active: true,
  });

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

// --- Comandos ---

export async function handleParada(ctx: Context): Promise<void> {
  const args = textOf(ctx).split(/\s+/);
  const stopId = args[1];
  if (!stopId || !/^\d+$/.test(stopId)) {
    await ctx.reply(
      'Uso: `/parada <codigo> [linea]`\nEjemplo: `/parada 1` o `/parada 1 28`',
      { parse_mode: 'Markdown' }
    );
    return;
  }
  await ctx.reply('🔍 Consultando datos en tiempo real...');
  const times = await fetchStopTimes(stopId);
  const requestedLine = args[2] ? normalizeLine(args[2]) : undefined;
  if (requestedLine) {
    const error = await validateLineAtStop(stopId, requestedLine);
    if (error) {
      await ctx.reply(error, { parse_mode: 'Markdown' });
      return;
    }
  }
  const arrivals = await getNextArrivals(stopId, requestedLine);
  const stopName = times?.parada?.desc || getStopName(stopId);
  await ctx.reply(formatArrivalsList(stopName, arrivals, times?.estado), {
    parse_mode: 'Markdown',
  });
}

export async function handleLineas(ctx: Context): Promise<void> {
  const stopId = textOf(ctx).split(/\s+/)[1];
  if (!stopId) {
    await showAllLines(ctx);
    return;
  }
  if (!/^\d+$/.test(stopId)) {
    await ctx.reply(
      'Uso: `/lineas` (todas) o `/lineas <codigo>`\nEjemplo: `/lineas 1`',
      { parse_mode: 'Markdown' }
    );
    return;
  }
  const lines = await getStopLines(stopId);
  await ctx.reply(
    lines.length
      ? `🚌 **Líneas en ${escapeMd(getStopName(stopId))} (${stopId}):**\n\n${lines
          .map(
            (l) => `• **L${escapeMd(l.lineId)}** → ${escapeMd(l.destination)}`
          )
          .join('\n')}`
      : `📭 Sin líneas con datos en la parada **${stopId}**.`,
    { parse_mode: 'Markdown' }
  );
}

export async function handleBuscar(ctx: Context): Promise<void> {
  const query = textOf(ctx).split(/\s+/).slice(1).join(' ').trim();
  if (!query) {
    await ctx.reply(
      'Uso: `/buscar <texto>`\nEjemplos: `/buscar 1`, `/buscar Boulevard`, `/buscar 28`',
      { parse_mode: 'Markdown' }
    );
    return;
  }
  const chatId = ctx.chat?.id;
  if (chatId) clearSession(chatId);
  const stops = searchStops(query, 8);
  const lines = searchLines(query, 5);
  if (!stops.length && !lines.length) {
    await ctx.reply(`🔎 Sin resultados para **${escapeMd(query)}**.`, {
      parse_mode: 'Markdown',
    });
    return;
  }
  const rows: ReturnType<typeof Markup.button.callback>[][] = [];
  for (const stop of stops) {
    rows.push([
      Markup.button.callback(
        `🚏 ${stop.desc.slice(0, 28)} (${stop.cod})`,
        `srch_s_${stop.cod}`
      ),
    ]);
  }
  for (const line of lines) {
    rows.push([
      Markup.button.callback(
        `🚌 L${line.cod} · ${line.destinations[0]?.slice(0, 22) ?? ''}`,
        `srch_l_${line.cod}`
      ),
    ]);
  }
  await ctx.reply(`🔎 Resultados para **${escapeMd(query)}**:`, {
    parse_mode: 'Markdown',
    ...Markup.inlineKeyboard(rows),
  });
}

export async function handleLineaCommand(ctx: Context): Promise<void> {
  const lineId = normalizeLine(textOf(ctx).split(/\s+/)[1] || '');
  const line = lineId ? getLineByCode(lineId) : null;
  if (!line) {
    await ctx.reply(
      'Uso: `/linea <linea>`\nEjemplo: `/linea 28` (o `L28`, o el id interno `32`)',
      { parse_mode: 'Markdown' }
    );
    return;
  }
  await showLineDestinations(ctx, line.cod);
}

export async function handleAviso(ctx: Context): Promise<void> {
  const args = textOf(ctx).split(/\s+/);
  const stopId = args[1];
  const lineId = normalizeLine(args[2] || '');
  const minutesAhead = Number.parseInt(args[3] ?? '', 10);

  if (!stopId || !/^\d+$/.test(stopId) || !lineId) {
    await ctx.reply(
      'Uso: `/aviso <parada> <linea> <minutos>`\nEjemplo: `/aviso 1 28 5`',
      { parse_mode: 'Markdown' }
    );
    return;
  }
  if (Number.isNaN(minutesAhead) || minutesAhead < 1 || minutesAhead > 60) {
    await ctx.reply('❌ Los minutos deben estar entre 1 y 60');
    return;
  }
  // Aceptar tanto el código público (28) como el id interno (32), pero no
  // crear avisos para líneas que no pasan por la parada.
  const error = await validateLineAtStop(stopId, lineId);
  if (error) {
    await ctx.reply(error, { parse_mode: 'Markdown' });
    return;
  }
  await createAndConfirmAlert(ctx, stopId, lineId, minutesAhead, false);
}

function alertsKeyboard(alerts: UserAlert[]) {
  const rows = alerts.map((alert) => [
    Markup.button.callback(
      `🗑️ #${alert.id} · L${alert.lineId}`,
      `adel_${alert.id}`
    ),
  ]);
  rows.push([Markup.button.callback('⬅️ Menú', 'menu_main')]);
  return Markup.inlineKeyboard(rows);
}

export async function handleLista(ctx: Context): Promise<void> {
  const chatId = ctx.chat?.id;
  if (!chatId) return;
  const alerts = getAlertsByChat(chatId);
  const msg = buildAlertsText(alerts);
  const extras = { parse_mode: 'Markdown' as const, ...alertsKeyboard(alerts) };
  if (ctx.callbackQuery) {
    await safeEdit(ctx, msg, extras);
    await answer(ctx);
  } else {
    await ctx.reply(msg, extras);
  }
}

export async function handleBorrar(ctx: Context): Promise<void> {
  const chatId = ctx.chat?.id;
  if (!chatId) return;
  const id = Number.parseInt(textOf(ctx).split(/\s+/)[1] ?? '', 10);
  if (Number.isNaN(id)) {
    await ctx.reply('Uso: `/borrar <id>`\nEjemplo: `/borrar 5`', {
      parse_mode: 'Markdown',
    });
    return;
  }
  const alert = getAlertById(id);
  if (!alert || alert.chatId !== chatId) {
    await ctx.reply('❌ Aviso no encontrado o no te pertenece');
    return;
  }
  deleteAlert(id);
  await ctx.reply(`🗑️ Aviso **#${id}** eliminado`, { parse_mode: 'Markdown' });
}

export async function handleActualizar(ctx: Context): Promise<void> {
  // Refresca tanto la caché de paradas como el catálogo completo, no solo
  // la parada de prueba. Así `/actualizar` cumple lo que promete.
  clearCache();
  const [times, stops] = await Promise.all([
    fetchStopTimes('1', true),
    fetchPopularStops(),
  ]);
  const cachedCodes = getCachedStopCodes().length;
  const msg = times
    ? `✅ Datos reales actualizados\n🕒 ${escapeMd(times.creacion || String(new Date()))}\n🚏 ${stops.length} paradas · ${cachedCodes} en caché\n📡 ${escapeMd(getDataSource())}`
    : `⚠️ No se pudo contactar con ${escapeMd(getDataSource())}`;
  if (ctx.callbackQuery) {
    await safeEdit(ctx, msg, {
      parse_mode: 'Markdown',
      ...Markup.inlineKeyboard([
        [Markup.button.callback('⬅️ Menú', 'menu_main')],
      ]),
    });
    await answer(ctx);
  } else {
    await ctx.reply(msg, { parse_mode: 'Markdown' });
  }
}

/** Diagnóstico rápido del estado en memoria y de la fuente de datos. */
export async function handleEstado(ctx: Context): Promise<void> {
  const stats = getCacheStats();
  const msg = `🟢 **Estado del bot**
📡 Fuente: ${escapeMd(getDataSource())}
🚏 Paradas en caché: ${stats.stops} · catálogo: ${stats.popularStops}
🔔 Avisos activos: ${getActiveAlerts().length}
⏱ Proceso activo: ${Math.floor(process.uptime())} s`;
  await ctx.reply(msg, { parse_mode: 'Markdown' });
}

async function showFavorites(ctx: Context): Promise<void> {
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
async function saveFavorite(
  chatId: number,
  stopId: string,
  rawLineId = ''
): Promise<boolean> {
  const lineId = normalizeLine(rawLineId);
  if (findFavorite(chatId, stopId, lineId)) return false;
  const arrival = lineId ? await getNextArrival(stopId, lineId) : null;
  addFavorite({
    chatId,
    stopId,
    stopName: getStopName(stopId),
    lineId,
    lineName: lineId ? `L${lineId}` : 'Todas',
    destination:
      arrival?.destination || (lineId ? `L${lineId}` : 'Todas las líneas'),
  });
  return true;
}

export async function handleFavCommand(ctx: Context): Promise<void> {
  const chatId = ctx.chat?.id;
  if (!chatId) return;
  const args = textOf(ctx).split(/\s+/);

  // Sin línea se guarda la parada completa; con línea, solo esa línea.
  if (args[1] === 'add' && args.length >= 3 && /^\d+$/.test(args[2])) {
    const added = await saveFavorite(chatId, args[2], args[3] ?? '');
    await ctx.reply(
      added ? '⭐ Favorito guardado' : '⭐ Ya tenías este favorito guardado'
    );
    return;
  }
  if (args[1] === 'linea' && args[2]) {
    const result = storeLineFavorite(chatId, args[2]);
    await ctx.reply(
      result === 'added'
        ? '⭐ Línea guardada'
        : result === 'duplicate'
          ? '⭐ Ya tenías esta línea guardada'
          : '❌ Línea no encontrada'
    );
    return;
  }
  if (args[1] === 'remove') {
    const id = Number.parseInt(args[2] ?? '', 10);
    if (!Number.isNaN(id)) {
      if (getFavorites(chatId).some((fav) => fav.id === id)) {
        removeFavorite(id);
        await ctx.reply('🗑️ Favorito eliminado');
        return;
      }
      if (getFavoriteLines(chatId).some((line) => line.id === id)) {
        removeFavoriteLine(id);
        await ctx.reply('🗑️ Línea eliminada');
        return;
      }
    }
  }

  const favs = getFavorites(chatId);
  const lines = getFavoriteLines(chatId);
  if (!favs.length && !lines.length) {
    await ctx.reply(
      '⭐ No tienes favoritos. Usa `/buscar` para encontrar uno.',
      {
        parse_mode: 'Markdown',
      }
    );
    return;
  }
  const parts: string[] = [];
  if (favs.length) {
    parts.push(
      `**Paradas:**\n${favs
        .map(
          (f) =>
            `• ${escapeMd(f.stopName)} (${f.stopId}) · ${f.lineId ? `L${escapeMd(f.lineId)}` : 'todas'} (#${f.id})`
        )
        .join('\n')}`
    );
  }
  if (lines.length) {
    parts.push(
      `**Líneas:**\n${lines
        .map(
          (l) =>
            `• L${escapeMd(l.lineId)} · ${escapeMd(l.destination)} (#${l.id})`
        )
        .join('\n')}`
    );
  }
  await ctx.reply(`⭐ **Tus favoritos:**\n\n${parts.join('\n\n')}`, {
    parse_mode: 'Markdown',
  });
}

// --- Texto libre (flujo interactivo) ---

async function handleText(ctx: Context): Promise<void> {
  const chatId = ctx.chat?.id;
  const text = textOf(ctx);
  if (!chatId || !text || text.startsWith('/')) return;

  const session = getSession(chatId);
  if (session.step === 'searching') {
    clearSession(chatId);
    await showSearchResults(ctx, text);
    return;
  }
  if (
    session.step !== 'waiting_stop_arrivals' &&
    session.step !== 'waiting_stop_alert'
  ) {
    return;
  }

  if (!/^\d+$/.test(text)) {
    // En el flujo de parada también aceptamos nombres: derivamos al buscador.
    session.step = 'searching';
    await showSearchResults(ctx, text);
    return;
  }

  if (session.step === 'waiting_stop_arrivals') {
    clearSession(chatId);
    await showArrivalsForStop(ctx, text);
  } else {
    await showStopLines(ctx, text);
  }
}

// --- Callbacks ---

async function handleMenuCallback(ctx: Context): Promise<void> {
  const data = callbackData(ctx);

  switch (data) {
    case 'menu_main':
      await showMainMenu(ctx);
      break;
    case 'menu_arrivals':
      await askStopCode(ctx, 'arrivals');
      break;
    case 'menu_alert':
      await askStopCode(ctx, 'alert');
      break;
    case 'menu_search':
      await askSearch(ctx);
      break;
    case 'menu_favorites':
      await showFavorites(ctx);
      break;
    case 'menu_my_alerts':
      await handleLista(ctx);
      return; // handleLista ya responde el callback
    case 'menu_refresh':
      await handleActualizar(ctx);
      return;
    case 'menu_help':
      await handleHelp(ctx);
      return;
    case 'menu_cancel':
      await showMainMenu(ctx);
      break;
    default:
      return;
  }
  await answer(ctx);
}

async function handleSearchStopCallback(ctx: Context): Promise<void> {
  const match = callbackData(ctx).match(/^srch_s_(\d+)$/);
  if (!match) return;
  await showArrivalsForStop(ctx, match[1]);
  await answer(ctx);
}

async function handleSearchLineCallback(ctx: Context): Promise<void> {
  const match = callbackData(ctx).match(/^srch_l_(.+)$/);
  if (!match) return;
  await showLineDestinations(ctx, match[1]);
  await answer(ctx);
}

async function handleLineDestCallback(ctx: Context): Promise<void> {
  const match = callbackData(ctx).match(/^ld_(\d+)$/);
  if (!match) return;
  await showLineStops(ctx, match[1]);
  await answer(ctx);
}

async function handleLineStopCallback(ctx: Context): Promise<void> {
  const match = callbackData(ctx).match(/^ls_(\d+)_(.+)$/);
  if (!match) return;
  await showArrivalsForStop(ctx, match[1], match[2]);
  await answer(ctx);
}

async function handleAllLinesCallback(ctx: Context): Promise<void> {
  const data = callbackData(ctx);
  if (data === 'lines_all') {
    await showAllLines(ctx);
  } else {
    const match = data.match(/^lpages_(\d+)$/);
    if (!match) return;
    await showAllLines(ctx, Number.parseInt(match[1], 10));
  }
  await answer(ctx);
}

async function handleLineAllStopsCallback(ctx: Context): Promise<void> {
  const data = callbackData(ctx);
  const direct = data.match(/^la_([^_]+)$/);
  const paged = data.match(/^lapages_([^_]+)_(\d+)$/);
  if (direct) {
    await showAllStopsForLine(ctx, direct[1]);
  } else if (paged) {
    await showAllStopsForLine(ctx, paged[1], Number.parseInt(paged[2], 10));
  } else {
    return;
  }
  await answer(ctx);
}

async function handleFavLineSaveCallback(ctx: Context): Promise<void> {
  const match = callbackData(ctx).match(/^favline_([^_]+)$/);
  if (!match) return;
  await saveLineFavorite(ctx, match[1]);
}

async function handleFavLineViewCallback(ctx: Context): Promise<void> {
  const match = callbackData(ctx).match(/^favline_view_(.+)$/);
  if (!match) return;
  await showLineDestinations(ctx, match[1]);
  await answer(ctx);
}

async function handleFavLineRemoveCallback(ctx: Context): Promise<void> {
  const match = callbackData(ctx).match(/^favlin_remove_(\d+)$/);
  const chatId = ctx.chat?.id;
  if (!match || !chatId) return;
  const id = Number.parseInt(match[1], 10);
  if (!getFavoriteLines(chatId).some((line) => line.id === id)) {
    await answer(ctx, '❌ Línea no encontrada');
    return;
  }
  removeFavoriteLine(id);
  await answer(ctx, '🗑️ Línea eliminada');
  await showFavorites(ctx);
}

async function handleSaveStopFavorite(ctx: Context): Promise<void> {
  const match = callbackData(ctx).match(/^favstop_(\d+)$/);
  const chatId = ctx.chat?.id;
  if (!match || !chatId) return;
  const added = await saveFavorite(chatId, match[1]);
  await answer(
    ctx,
    added ? '⭐ Parada guardada' : '⭐ Ya tenías esta parada guardada'
  );
}

async function handleStopRefresh(ctx: Context): Promise<void> {
  const match = callbackData(ctx).match(/^stop_refresh_(\d+)$/);
  if (!match) return;
  // Solo invalida la parada consultada: no tires el catálogo ni las demás
  // paradas en caché por un refresco puntual.
  invalidateStop(match[1]);
  clearSession(ctx.chat?.id ?? 0);
  await showArrivalsForStop(ctx, match[1]);
  await answer(ctx, '🔄 Actualizado');
}

async function handleStopAlert(ctx: Context): Promise<void> {
  const match = callbackData(ctx).match(/^stop_alert_(\d+)$/);
  if (!match) return;
  await showStopLines(ctx, match[1]);
  await answer(ctx);
}

async function handleLineCallback(ctx: Context): Promise<void> {
  const match = callbackData(ctx).match(/^aline_([^_]+)$/);
  const chatId = ctx.chat?.id;
  if (!match || !chatId) return;
  const session = getSession(chatId);
  // Un callback antiguo (mensajes editados quedan en el historial) no debe
  // reactivar un flujo abandonado ni crear avisos con datos a medias.
  if (session.step !== 'select_line_alert' || !session.stopId) {
    await safeEdit(ctx, '❌ Sesión expirada, vuelve a crear el aviso');
    await answer(ctx);
    return;
  }
  session.lineId = normalizeLine(match[1]);
  await showMinutesSelector(ctx);
  await answer(ctx);
}

async function handleMinutesCallback(ctx: Context): Promise<void> {
  const match = callbackData(ctx).match(/^minutes_(\d+)$/);
  const chatId = ctx.chat?.id;
  if (!match || !chatId) return;
  const session = getSession(chatId);
  if (
    session.step !== 'select_minutes_alert' ||
    !session.stopId ||
    !session.lineId
  ) {
    await safeEdit(ctx, '❌ Sesión expirada, intenta de nuevo');
    await answer(ctx);
    return;
  }
  const minutes = Number.parseInt(match[1], 10);
  if (minutes < 1 || minutes > 60) {
    await answer(ctx, '❌ Minutos fuera de rango');
    return;
  }
  const result = await createAndConfirmAlert(
    ctx,
    session.stopId,
    session.lineId,
    minutes,
    true
  );
  await answer(
    ctx,
    result === 'created'
      ? '✅ Aviso creado'
      : result === 'duplicate'
        ? '⚠️ Ya tenías un aviso para esta línea'
        : '❌ No se pudo crear el aviso'
  );
}

async function handleFavAdd(ctx: Context): Promise<void> {
  const match = callbackData(ctx).match(/^fav_add_(\d+)_([^_]+)$/);
  const chatId = ctx.chat?.id;
  if (!match || !chatId) return;
  const [, stopId, lineId] = match;
  const added = await saveFavorite(chatId, stopId, lineId);
  await answer(ctx, added ? '⭐ Favorito guardado' : '⭐ Ya guardado');
  await showFavorites(ctx);
}

async function handleFavView(ctx: Context): Promise<void> {
  const match = callbackData(ctx).match(/^fav_view_(\d+)$/);
  const chatId = ctx.chat?.id;
  if (!match || !chatId) return;
  const fav = getFavorites(chatId).find(
    (f) => f.id === Number.parseInt(match[1], 10)
  );
  if (!fav) {
    await answer(ctx, '❌ Favorito no encontrado');
    return;
  }
  await showArrivalsForStop(ctx, fav.stopId, fav.lineId || undefined);
  await answer(ctx);
}

async function handleFavRemove(ctx: Context): Promise<void> {
  const match = callbackData(ctx).match(/^fav_remove_(\d+)$/);
  const chatId = ctx.chat?.id;
  if (!match || !chatId) return;
  const id = Number.parseInt(match[1], 10);
  if (!getFavorites(chatId).some((f) => f.id === id)) {
    await answer(ctx, '❌ Favorito no encontrado');
    return;
  }
  removeFavorite(id);
  await answer(ctx, '🗑️ Favorito eliminado');
  await showFavorites(ctx);
}

export async function handleAlertDelete(ctx: Context): Promise<void> {
  const match = callbackData(ctx).match(/^adel_(\d+)$/);
  const chatId = ctx.chat?.id;
  if (!match || !chatId) return;
  const id = Number.parseInt(match[1], 10);
  const alert = getAlertById(id);
  if (!alert || alert.chatId !== chatId) {
    await answer(ctx, '❌ Aviso no encontrado');
    return;
  }
  deleteAlert(id);
  await answer(ctx, '🗑️ Aviso eliminado');
  await handleLista(ctx);
}

export function setupBot(bot: Telegraf<Context>): void {
  setBotReference(bot);

  // Comandos
  bot.start(handleStart);
  bot.command('help', handleHelp);
  bot.command('parada', handleParada);
  bot.command('lineas', handleLineas);
  bot.command('buscar', handleBuscar);
  bot.command('linea', handleLineaCommand);
  bot.command('aviso', handleAviso);
  bot.command('lista', handleLista);
  bot.command('borrar', handleBorrar);
  bot.command('actualizar', handleActualizar);
  bot.command('estado', handleEstado);
  bot.command('fav', handleFavCommand);

  // Menú y flujos
  bot.action(/^menu_.*/, handleMenuCallback);
  bot.action(/^srch_s_\d+$/, handleSearchStopCallback);
  bot.action(/^srch_l_(.+)$/, handleSearchLineCallback);
  bot.action(/^ld_\d+$/, handleLineDestCallback);
  bot.action(/^(lines_all|lpages_\d+)$/, handleAllLinesCallback);
  bot.action(/^(la_[^_]+|lapages_[^_]+_\d+)$/, handleLineAllStopsCallback);
  bot.action(/^ls_\d+_.+$/, handleLineStopCallback);
  bot.action(/^favline_view_(.+)$/, handleFavLineViewCallback);
  bot.action(/^favlin_remove_\d+$/, handleFavLineRemoveCallback);
  bot.action(/^favline_([^_]+)$/, handleFavLineSaveCallback);
  bot.action(/^favstop_\d+$/, handleSaveStopFavorite);
  bot.action(/^stop_refresh_\d+$/, handleStopRefresh);
  bot.action(/^stop_alert_\d+$/, handleStopAlert);
  bot.action(/^aline_([^_]+)$/, handleLineCallback);
  bot.action(/^minutes_\d+$/, handleMinutesCallback);
  bot.action(/^fav_add_\d+_([^_]+)$/, handleFavAdd);
  bot.action(/^fav_view_\d+$/, handleFavView);
  bot.action(/^fav_remove_\d+$/, handleFavRemove);
  bot.action(/^adel_\d+$/, handleAlertDelete);

  // Texto libre (código de parada)
  bot.on('text', handleText);

  startAlertScheduler();
  void fetchStopTimes('1'); // precargar conexión con datos reales

  console.log('🤖 Bot handlers registered');
  console.log(`📡 Data source: ${getDataSource()}`);
  console.log(`🌐 Language: ${config.language}`);
}
