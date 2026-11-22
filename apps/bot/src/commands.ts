/** Comandos del bot y texto libre (flujo interactivo). */
import { type Context, Markup } from 'telegraf';
import { getAlertStats } from './alerts.js';
import {
  displayStopName,
  escapeMd,
  getStopLinesWithSource,
  normalizeLine,
} from './arrival.js';
import {
  getCatalogCoverage,
  getCatalogMeta,
  getLineByCode,
  searchLines,
  searchStops,
} from './catalog.js';
import {
  deleteAlert,
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
} from './dbus-client.js';
import {
  HELP_TEXT,
  MAIN_TEXT,
  alertsKeyboard,
  answer,
  buildAlertsText,
  createAndConfirmAlert,
  mainKeyboard,
  safeEdit,
  saveFavorite,
  showAllLines,
  showArrivalsForStop,
  showLineDestinations,
  showSearchResults,
  showStopLines,
  storeLineFavorite,
  textOf,
  validateLineAtStop,
} from './render.js';
import { clearSession, getSession } from './session.js';

const REFRESH_COOLDOWN_MS = 30_000;
const lastRefreshByChat = new Map<number, number>();

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
  const requestedLine = args[2] ? normalizeLine(args[2]) : undefined;
  if (requestedLine) {
    const error = await validateLineAtStop(stopId, requestedLine);
    if (error) {
      await ctx.reply(error, { parse_mode: 'Markdown' });
      return;
    }
  }
  // Misma vista que por callback: botones de refrescar, aviso y favorito.
  await showArrivalsForStop(ctx, stopId, requestedLine);
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
  const { lines, source } = await getStopLinesWithSource(stopId);
  const note =
    source === 'catalog' && lines.length
      ? '\n\n_(índice Dbus: ahora mismo no hay datos en vivo; sin horarios)_'
      : '';
  await ctx.reply(
    lines.length
      ? `🚌 **Líneas en ${escapeMd(displayStopName(stopId))} (${stopId}):**\n\n${lines
          .map(
            (l) =>
              `• **L${escapeMd(l.lineId)}**${l.destination ? ` → ${escapeMd(l.destination)}` : ''}`
          )
          .join('\n')}${note}`
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
  const chatId = ctx.chat?.id;
  const now = Date.now();
  if (
    chatId &&
    now - (lastRefreshByChat.get(chatId) ?? 0) < REFRESH_COOLDOWN_MS
  ) {
    const wait = Math.ceil(
      (REFRESH_COOLDOWN_MS - (now - (lastRefreshByChat.get(chatId) ?? 0))) /
        1000
    );
    const msg = `Espera ${wait} s antes de volver a actualizar.`;
    if (ctx.callbackQuery) {
      await answer(ctx, msg);
    } else {
      await ctx.reply(msg);
    }
    return;
  }
  if (chatId) {
    if (lastRefreshByChat.size > 500) lastRefreshByChat.clear();
    lastRefreshByChat.set(chatId, now);
  }

  // Refresca tanto la caché de paradas como el catálogo completo, no solo
  // la parada de prueba. Así `/actualizar` cumple lo que promete.
  clearCache();
  const [times, stops] = await Promise.all([
    fetchStopTimes('2', true),
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
  const catalog = getCatalogMeta();
  const coverage = getCatalogCoverage();
  const ageDays = Math.max(
    0,
    Math.floor(
      (Date.now() - Date.parse(catalog.generatedAt)) / (24 * 60 * 60 * 1000)
    )
  );
  const alertsStats = getAlertStats();
  const msg = `🟢 **Estado del bot**
 📡 Fuente: ${escapeMd(getDataSource())}
 🚏 Paradas en caché: ${stats.stops} · catálogo: ${stats.popularStops}
 📚 Índice: ${catalog.lines} líneas · ${catalog.stops} paradas · ${ageDays} d${
   ageDays > 7 ? ' ⚠️ regenerar con `npm run build:catalog`' : ''
 }
  🔎 Rango sondeado: ids 1..${coverage.lineIdsScanned} · último con datos: ${coverage.lastLineIdWithData}
  🔔 Avisos activos: ${getActiveAlerts().length} · enviados: ${alertsStats.sent} · fallidos: ${alertsStats.failed}
 ⏱ Proceso activo: ${Math.floor(process.uptime())} s`;
  await ctx.reply(msg, { parse_mode: 'Markdown' });
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

export async function handleText(ctx: Context): Promise<void> {
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
