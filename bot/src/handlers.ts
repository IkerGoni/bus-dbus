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
import { config } from './config.js';
import {
  addFavorite,
  createAlert,
  deleteAlert,
  findActiveAlert,
  findFavorite,
  getAlertById,
  getAlertsByChat,
  getFavorites,
  removeFavorite,
} from './db.js';
import {
  clearCache,
  fetchPopularStops,
  fetchStopTimes,
  getCachedStopCodes,
  getDataSource,
  getStopName,
  invalidateStop,
} from './dbus-client.js';
import type { UserAlert } from './types.js';

type Step =
  | 'idle'
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

const CATALOG_ERROR =
  '⚠️ No se pudo cargar el catálogo de paradas de Dbus. Prueba `/parada <codigo>`.';

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
    [Markup.button.callback('📍 Paradas populares', 'menu_popular')],
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
  '`/fav` — tus favoritos · `/fav add <parada> <linea>` · `/fav remove <id>`\n' +
  '`/lineas <parada>` — líneas que pasan por una parada\n' +
  '`/populares` — paradas habituales\n' +
  '`/actualizar` — refrescar datos\n\n' +
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
        [
          Markup.button.callback(
            '📍 Ver paradas populares',
            `menu_popular_${mode}`
          ),
        ],
        [Markup.button.callback('⬅️ Menú', 'menu_main')],
      ]),
    }
  );
}

async function showPopularStops(
  ctx: Context,
  mode: 'arrivals' | 'alert'
): Promise<void> {
  const stops = await fetchPopularStops();
  if (!stops.length) {
    await safeEdit(ctx, CATALOG_ERROR, {
      parse_mode: 'Markdown',
      ...Markup.inlineKeyboard([
        [Markup.button.callback('⬅️ Menú', 'menu_main')],
      ]),
    });
    return;
  }
  const suffix = mode === 'alert' ? 'alert' : 'arrivals';
  const buttons = stops
    .slice(0, 18)
    .map((stop) =>
      Markup.button.callback(
        `${stop.desc.slice(0, 28)} (${stop.cod})`,
        `popular_${stop.cod}_${suffix}`
      )
    );
  const rows: ReturnType<typeof Markup.button.callback>[][] = [];
  for (let i = 0; i < buttons.length; i += 2) {
    rows.push(buttons.slice(i, i + 2));
  }
  rows.push([Markup.button.callback('⬅️ Menú', 'menu_main')]);

  await safeEdit(ctx, '📍 **Paradas populares** — selecciona una:', {
    parse_mode: 'Markdown',
    ...Markup.inlineKeyboard(rows),
  });
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
      ...(favoriteLine
        ? [
            Markup.button.callback(
              '⭐ Guardar favorito',
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

async function createAndConfirmAlert(
  ctx: Context,
  stopId: string,
  lineId: string,
  minutesAhead: number,
  asEdit: boolean
): Promise<void> {
  const chatId = ctx.chat?.id;
  if (!chatId) return;

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
    return;
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

  const msg =
    `✅ **Aviso creado** (ID: \`${alert.id}\`)\n` +
    `🚏 ${escapeMd(stopName)} (${escapeMd(stopId)})\n` +
    `🚌 **L${escapeMd(publicLineId)}** → ${escapeMd(alert.destination)}\n` +
    `🔔 Te avisaré **${minutesAhead} min** antes`;

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
  if (!stopId || !/^\d+$/.test(stopId)) {
    await ctx.reply('Uso: `/lineas <codigo>`\nEjemplo: `/lineas 1`', {
      parse_mode: 'Markdown',
    });
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

export async function handlePopulares(ctx: Context): Promise<void> {
  const stops = await fetchPopularStops();
  if (!stops.length) {
    await ctx.reply(CATALOG_ERROR, { parse_mode: 'Markdown' });
    return;
  }
  const msg = `📍 **Paradas populares** (código entre paréntesis):\n\n${stops
    .slice(0, 18)
    .map((s) => `• ${escapeMd(s.desc)} — \`${s.cod}\``)
    .join('\n')}`;
  await ctx.reply(msg, { parse_mode: 'Markdown' });
}

export async function handleAviso(ctx: Context): Promise<void> {
  const args = textOf(ctx).split(/\s+/);
  const stopId = args[1];
  const lineId = normalizeLine(args[2] || '');
  const minutesAhead = Number.parseInt(args[3] ?? '', 10);

  if (!stopId || !/^\d+$/.test(stopId) || !/^\d+$/.test(lineId)) {
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

async function showFavorites(ctx: Context): Promise<void> {
  const chatId = ctx.chat?.id;
  if (!chatId) return;
  const favs = getFavorites(chatId);

  if (!favs.length) {
    await safeEdit(
      ctx,
      '⭐ **No tienes favoritos**\n\nUsa "Ver llegadas" y guarda una parada.',
      {
        parse_mode: 'Markdown',
        ...Markup.inlineKeyboard([
          [Markup.button.callback('⬅️ Menú', 'menu_main')],
        ]),
      }
    );
    return;
  }

  const rows = favs.map((fav) => [
    Markup.button.callback(
      `🚏 ${fav.stopName.slice(0, 22)} · L${fav.lineId}`,
      `fav_view_${fav.id}`
    ),
    Markup.button.callback('🗑️', `fav_remove_${fav.id}`),
  ]);
  rows.push([Markup.button.callback('⬅️ Menú', 'menu_main')]);

  await safeEdit(ctx, '⭐ **Tus favoritos:**', {
    parse_mode: 'Markdown',
    ...Markup.inlineKeyboard(rows),
  });
}

/** Guarda un favorito evitando duplicados por (parada, línea). */
async function saveFavorite(
  chatId: number,
  stopId: string,
  rawLineId: string
): Promise<boolean> {
  const lineId = normalizeLine(rawLineId);
  if (findFavorite(chatId, stopId, lineId)) return false;
  const arrival = await getNextArrival(stopId, lineId);
  addFavorite({
    chatId,
    stopId,
    stopName: getStopName(stopId),
    lineId,
    lineName: `L${lineId}`,
    destination: arrival?.destination || `L${lineId}`,
  });
  return true;
}

export async function handleFavCommand(ctx: Context): Promise<void> {
  const chatId = ctx.chat?.id;
  if (!chatId) return;
  const args = textOf(ctx).split(/\s+/);

  if (args[1] === 'add' && args.length >= 4 && /^\d+$/.test(args[2])) {
    const added = await saveFavorite(chatId, args[2], args[3]);
    await ctx.reply(
      added ? '⭐ Favorito guardado' : '⭐ Ya tenías este favorito guardado'
    );
    return;
  }
  if (args[1] === 'remove') {
    const id = Number.parseInt(args[2] ?? '', 10);
    if (!Number.isNaN(id)) {
      const fav = getFavorites(chatId).find((f) => f.id === id);
      if (fav) {
        removeFavorite(id);
        await ctx.reply('🗑️ Favorito eliminado');
        return;
      }
    }
  }
  const favs = getFavorites(chatId);
  await ctx.reply(
    favs.length
      ? `⭐ **Tus favoritos:**\n\n${favs
          .map(
            (f) =>
              `• ${escapeMd(f.stopName)} · L${escapeMd(f.lineId)} (#${f.id})`
          )
          .join('\n')}`
      : '⭐ No tienes favoritos.',
    { parse_mode: 'Markdown' }
  );
}

// --- Texto libre (flujo interactivo) ---

async function handleText(ctx: Context): Promise<void> {
  const chatId = ctx.chat?.id;
  const text = textOf(ctx);
  if (!chatId || !text || text.startsWith('/')) return;

  const session = getSession(chatId);
  if (
    session.step !== 'waiting_stop_arrivals' &&
    session.step !== 'waiting_stop_alert'
  ) {
    return;
  }

  if (!/^\d+$/.test(text)) {
    await ctx.reply(
      '❌ Escribe solo el código numérico de la parada (p. ej. `1`).',
      {
        parse_mode: 'Markdown',
      }
    );
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
  const modeMatch = data.match(/^menu_popular_(arrivals|alert)$/);

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
    case 'menu_popular':
      await showPopularStops(ctx, 'arrivals');
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
      if (modeMatch)
        await showPopularStops(ctx, modeMatch[1] as 'arrivals' | 'alert');
      else return;
  }
  await answer(ctx);
}

async function handlePopularCallback(ctx: Context): Promise<void> {
  const match = callbackData(ctx).match(/^popular_(\d+)_(arrivals|alert)$/);
  if (!match) return;
  const [, stopId, mode] = match;
  if (mode === 'alert') await showStopLines(ctx, stopId);
  else await showArrivalsForStop(ctx, stopId);
  await answer(ctx);
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
  const match = callbackData(ctx).match(/^aline_(\d+)$/);
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
  await createAndConfirmAlert(
    ctx,
    session.stopId,
    session.lineId,
    minutes,
    true
  );
  await answer(ctx, '✅ Aviso creado');
}

async function handleFavAdd(ctx: Context): Promise<void> {
  const match = callbackData(ctx).match(/^fav_add_(\d+)_(\d+)$/);
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
  await showArrivalsForStop(ctx, fav.stopId, fav.lineId);
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
  bot.command('populares', handlePopulares);
  bot.command('aviso', handleAviso);
  bot.command('lista', handleLista);
  bot.command('borrar', handleBorrar);
  bot.command('actualizar', handleActualizar);
  bot.command('fav', handleFavCommand);

  // Menú y flujos
  bot.action(/^menu_.*/, handleMenuCallback);
  bot.action(/^popular_\d+_(arrivals|alert)$/, handlePopularCallback);
  bot.action(/^stop_refresh_\d+$/, handleStopRefresh);
  bot.action(/^stop_alert_\d+$/, handleStopAlert);
  bot.action(/^aline_\d+$/, handleLineCallback);
  bot.action(/^minutes_\d+$/, handleMinutesCallback);
  bot.action(/^fav_add_\d+_\d+$/, handleFavAdd);
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
