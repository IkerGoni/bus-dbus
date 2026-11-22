import { type Context, Markup, type Telegraf } from 'telegraf';
import { setBotInstance, startAlertScheduler } from './alerts.js';
import {
  findAllNextArrivals,
  formatArrival,
  getCachedArrivals,
} from './arrival.js';
import { config } from './config.js';
import {
  addFavorite,
  createAlert,
  deactivateAlert,
  deleteAlert,
  getAlertById,
  getAlertsByChat,
  getFavorites,
  removeFavorite,
} from './db.js';
import { fetchAndParseGTFSRT, startRTPolling } from './gtfs-client.js';
import type { UserAlert } from './types.js';

// Session storage para flujos interactivos
interface BotSessionData {
  lineId: string;
  stopId: string;
  [key: string]: unknown;
}

interface BotSession {
  step: string;
  data: BotSessionData;
}

const userSessions = new Map<number, BotSession>();

export function setBotReference(bot: Telegraf<Context>) {
  setBotInstance(bot);
}

function getSession(chatId: number) {
  if (!userSessions.has(chatId)) {
    userSessions.set(chatId, {
      step: 'idle',
      data: { lineId: '', stopId: '' },
    });
  }
  return userSessions.get(chatId)!;
}

function clearSession(chatId: number) {
  userSessions.delete(chatId);
}

// Líneas disponibles (en producción vendrían de GTFS estático)
const AVAILABLE_LINES = [
  '12',
  '17',
  '18',
  '21',
  '23',
  '25',
  '26',
  '27',
  '28',
  '29',
  '31',
  '32',
  '33',
];

// Paradas por línea (mock - en producción de GTFS estático)
const STOPS_BY_LINE: Record<string, Array<{ id: string; name: string }>> = {
  '12': [
    { id: '1234', name: 'Amara' },
    { id: '1235', name: 'Gros' },
    { id: '1236', name: 'Intxaurrondo' },
    { id: '1237', name: 'Aiete' },
  ],
  '17': [
    { id: '1234', name: 'Amara' },
    { id: '1238', name: 'Egia' },
    { id: '1239', name: 'Antiguo' },
  ],
  '18': [
    { id: '1240', name: 'Seminario' },
    { id: '1241', name: 'Gros' },
  ],
};

export async function handleStart(ctx: Context) {
  const chatId = ctx.chat?.id;
  if (!chatId) return;

  clearSession(chatId);

  await ctx.reply(
    '🚌 **Dbus Donostia - Bot de llegadas**\n\n' + '¿Qué quieres hacer?',
    Markup.inlineKeyboard([
      [Markup.button.callback('🚌 Ver llegadas', 'menu_arrivals')],
      [Markup.button.callback('🔔 Crear aviso', 'menu_alert')],
      [Markup.button.callback('⭐ Favoritos', 'menu_favorites')],
      [Markup.button.callback('📋 Mis avisos', 'menu_my_alerts')],
      [Markup.button.callback('🔄 Actualizar datos', 'menu_refresh')],
    ])
  );
}

export async function handleMenuCallback(ctx: Context) {
  const chatId = ctx.chat?.id;
  if (!chatId) return;

  const data =
    ctx.callbackQuery && 'data' in ctx.callbackQuery
      ? ctx.callbackQuery.data
      : '';

  switch (data) {
    case 'menu_arrivals':
      await showLineSelector(ctx);
      break;
    case 'menu_alert':
      await showLineSelectorForAlert(ctx);
      break;
    case 'menu_favorites':
      await showFavorites(ctx);
      break;
    case 'menu_my_alerts':
      await handleLista(ctx);
      break;
    case 'menu_refresh':
      await handleActualizar(ctx);
      break;
  }

  await ctx.answerCbQuery();
}

async function showLineSelector(ctx: Context) {
  const session = getSession(ctx.chat!.id);
  session.step = 'select_line_for_arrivals';

  const buttons = AVAILABLE_LINES.map((line) =>
    Markup.button.callback(`L${line}`, `line_${line}_arrivals`)
  );

  // Chunk into rows of 3
  const rows = [];
  for (let i = 0; i < buttons.length; i += 3) {
    rows.push(buttons.slice(i, i + 3));
  }
  rows.push([Markup.button.callback('❌ Cancelar', 'menu_cancel')]);

  await ctx.editMessageText(
    '🚌 **Selecciona una línea:**',
    Markup.inlineKeyboard(rows)
  );
}

async function showLineSelectorForAlert(ctx: Context) {
  const session = getSession(ctx.chat!.id);
  session.step = 'select_line_for_alert';

  const buttons = AVAILABLE_LINES.map((line) =>
    Markup.button.callback(`L${line}`, `line_${line}_alert`)
  );

  const rows = [];
  for (let i = 0; i < buttons.length; i += 3) {
    rows.push(buttons.slice(i, i + 3));
  }
  rows.push([Markup.button.callback('❌ Cancelar', 'menu_cancel')]);

  await ctx.editMessageText(
    '🔔 **Crear aviso - Selecciona una línea:**',
    Markup.inlineKeyboard(rows)
  );
}

async function showStopSelector(
  ctx: Context,
  lineId: string,
  forAlert: boolean
) {
  const session = getSession(ctx.chat!.id);
  session.data.lineId = lineId;

  const stops = STOPS_BY_LINE[lineId] || [];
  if (!stops.length) {
    await ctx.editMessageText(`❌ No hay paradas configuradas para L${lineId}`);
    return;
  }

  const suffix = forAlert ? 'alert' : 'arrivals';
  const buttons = stops.map((stop) =>
    Markup.button.callback(
      `${stop.name} (${stop.id})`,
      `stop_${stop.id}_${suffix}`
    )
  );

  const rows = [];
  for (let i = 0; i < buttons.length; i += 2) {
    rows.push(buttons.slice(i, i + 2));
  }
  rows.push([
    Markup.button.callback(
      '⬅️ Volver',
      forAlert ? 'menu_alert' : 'menu_arrivals'
    ),
  ]);

  await ctx.editMessageText(
    forAlert
      ? `🔔 **Crear aviso L${lineId} - Selecciona parada:**`
      : `🚌 **Llegadas L${lineId} - Selecciona parada:**`,
    Markup.inlineKeyboard(rows)
  );
}

async function showMinutesSelector(
  ctx: Context,
  stopId: string,
  lineId: string
) {
  const session = getSession(ctx.chat!.id);
  session.data.stopId = stopId;
  session.data.lineId = lineId;
  session.step = 'select_minutes';

  const buttons = [3, 5, 8, 10].map((min) =>
    Markup.button.callback(`${min} min`, `minutes_${min}`)
  );

  await ctx.editMessageText(
    `🔔 **Aviso L${lineId} - Parada ${stopId}**\n¿Cuántos minutos antes quieres que te avise?`,
    Markup.inlineKeyboard([
      buttons.slice(0, 2),
      buttons.slice(2, 4),
      [Markup.button.callback('⬅️ Volver', `line_${lineId}_alert`)],
    ])
  );
}

async function handleLineCallback(ctx: Context) {
  const data =
    ctx.callbackQuery && 'data' in ctx.callbackQuery
      ? ctx.callbackQuery.data
      : '';
  const match = data.match(/^line_(\d+)_(arrivals|alert)$/);
  if (!match) return;

  const [, lineId, type] = match;
  const forAlert = type === 'alert';

  await showStopSelector(ctx, lineId, forAlert);
  await ctx.answerCbQuery();
}

async function handleStopCallback(ctx: Context) {
  const data =
    ctx.callbackQuery && 'data' in ctx.callbackQuery
      ? ctx.callbackQuery.data
      : '';
  const match = data.match(/^stop_(\d+)_(arrivals|alert)$/);
  if (!match) return;

  const [, stopId, type] = match;
  const forAlert = type === 'alert';
  const session = getSession(ctx.chat!.id);
  const lineId = session.data.lineId;

  if (!lineId) {
    await ctx.editMessageText('❌ Sesión expirada, intenta de nuevo');
    return;
  }

  if (forAlert) {
    await showMinutesSelector(ctx, stopId, lineId);
  } else {
    // Show arrivals directly
    await showArrivalsForStop(ctx, stopId, lineId);
  }
  await ctx.answerCbQuery();
}

async function handleMinutesCallback(ctx: Context) {
  const data =
    ctx.callbackQuery && 'data' in ctx.callbackQuery
      ? ctx.callbackQuery.data
      : '';
  const match = data.match(/^minutes_(\d+)$/);
  if (!match) return;

  const minutesAhead = Number.parseInt(match[1], 10);
  const session = getSession(ctx.chat!.id);
  const { stopId, lineId } = session.data;
  const chatId = ctx.chat!.id;

  if (!stopId || !lineId) {
    await ctx.editMessageText('❌ Sesión expirada, intenta de nuevo');
    return;
  }

  // Verify line exists at stop
  const rt = await fetchAndParseGTFSRT();
  if (!rt) {
    await ctx.editMessageText('❌ Error al verificar datos. Intenta de nuevo.');
    return;
  }

  const arrival = getCachedArrivals(stopId, lineId);
  if (!arrival) {
    await ctx.editMessageText(
      `❌ No hay servicio **L${lineId}** en parada **${stopId}** ahora mismo.`,
      { parse_mode: 'Markdown' }
    );
    return;
  }

  const alert = createAlert({
    chatId,
    stopId,
    stopName: `Parada ${stopId}`,
    lineId,
    lineName: `L${lineId}`,
    destination: arrival.trip.tripId,
    minutesAhead,
    active: true,
  });

  const mins = Math.round((arrival.arrivalTime - Date.now()) / 1000 / 60);

  clearSession(chatId);

  await ctx.editMessageText(
    `✅ **Aviso creado** (ID: \`${alert.id}\`)\n` +
      `🚏 Parada: **${stopId}**\n` +
      `🚌 Línea: **L${lineId}** → ${arrival.trip.tripId}\n` +
      `⏱ Próxima llegada: **${mins} min**\n` +
      `🔔 Te avisaré **${minutesAhead} min** antes`,
    { parse_mode: 'Markdown' }
  );
  await ctx.answerCbQuery();
}

async function showArrivalsForStop(
  ctx: Context,
  stopId: string,
  lineId: string
) {
  await ctx.editMessageText(
    `🔍 Buscando llegadas para parada **${stopId}**...`,
    { parse_mode: 'Markdown' }
  );

  const rt = await fetchAndParseGTFSRT();
  if (!rt) {
    await ctx.editMessageText('❌ Error al obtener datos en tiempo real.');
    return;
  }

  const arrivals = findAllNextArrivals(rt, stopId);

  if (!arrivals.size) {
    await ctx.editMessageText(
      `📭 No hay llegadas programadas para la parada **${stopId}** en este momento.`,
      { parse_mode: 'Markdown' }
    );
    return;
  }

  let msg = `🚏 **Parada ${stopId}** - Próximas llegadas:\n\n`;
  for (const [_lid, arrival] of arrivals) {
    msg += `${formatArrival(arrival)}\n\n`;
  }
  msg += `_Actualizado: ${new Date(rt.timestamp).toLocaleTimeString('es-ES')}_`;

  // Add favorite button
  await ctx.editMessageText(msg, {
    parse_mode: 'Markdown',
    ...Markup.inlineKeyboard([
      [
        Markup.button.callback(
          '⭐ Guardar como favorito',
          `fav_add_${stopId}_${lineId}`
        ),
      ],
      [Markup.button.callback('⬅️ Volver', 'menu_arrivals')],
    ]),
  });
}

async function handleFavAdd(ctx: Context) {
  const data =
    ctx.callbackQuery && 'data' in ctx.callbackQuery
      ? ctx.callbackQuery.data
      : '';
  const match = data.match(/^fav_add_(\d+)_(\d+)$/);
  if (!match) return;

  const [, stopId, lineId] = match;
  const chatId = ctx.chat!.id;

  const rt = await fetchAndParseGTFSRT();
  const arrival = rt ? getCachedArrivals(stopId, lineId) : null;

  addFavorite({
    chatId,
    stopId,
    stopName: `Parada ${stopId}`,
    lineId,
    lineName: `L${lineId}`,
    destination: arrival?.trip.tripId || `L${lineId}`,
  });

  await ctx.answerCbQuery('⭐ Favorito guardado');
  await showFavorites(ctx);
}

async function showFavorites(ctx: Context) {
  const chatId = ctx.chat?.id;
  if (!chatId) return;

  const favs = getFavorites(chatId);

  if (!favs.length) {
    await ctx.editMessageText(
      '⭐ **No tienes favoritos guardados**\n\nUsa "Ver llegadas" y guarda una parada como favorito.',
      Markup.inlineKeyboard([[Markup.button.callback('⬅️ Menú', 'menu_main')]])
    );
    return;
  }

  const msg = '⭐ **Tus favoritos:**\n\n';
  const buttons = favs.map((fav) =>
    Markup.button.callback(
      `🚏 ${fav.stopName} - L${fav.lineId}`,
      `fav_view_${fav.id}`
    )
  );

  const rows = [];
  for (let i = 0; i < buttons.length; i += 1) {
    rows.push([buttons[i]]);
  }
  rows.push([Markup.button.callback('⬅️ Menú', 'menu_main')]);

  await ctx.editMessageText(msg, Markup.inlineKeyboard(rows));
}

async function handleFavView(ctx: Context) {
  const data =
    ctx.callbackQuery && 'data' in ctx.callbackQuery
      ? ctx.callbackQuery.data
      : '';
  const match = data.match(/^fav_view_(\d+)$/);
  if (!match) return;

  const favId = Number.parseInt(match[1], 10);
  const chatId = ctx.chat!.id;
  const favs = getFavorites(chatId);
  const fav = favs.find((f) => f.id === favId);

  if (!fav) {
    await ctx.answerCbQuery('❌ Favorito no encontrado');
    return;
  }

  await showArrivalsForStop(ctx, fav.stopId, fav.lineId);
  await ctx.answerCbQuery();
}

async function handleFavRemove(ctx: Context) {
  const data =
    ctx.callbackQuery && 'data' in ctx.callbackQuery
      ? ctx.callbackQuery.data
      : '';
  const match = data.match(/^fav_remove_(\d+)$/);
  if (!match) return;

  const favId = Number.parseInt(match[1], 10);
  const _chatId = ctx.chat!.id;

  removeFavorite(favId);
  await ctx.answerCbQuery('🗑️ Favorito eliminado');
  await showFavorites(ctx);
}

async function handleMainMenu(ctx: Context) {
  await ctx.editMessageText(
    '🚌 **Dbus Donostia - Bot de llegadas**\n\n¿Qué quieres hacer?',
    Markup.inlineKeyboard([
      [Markup.button.callback('🚌 Ver llegadas', 'menu_arrivals')],
      [Markup.button.callback('🔔 Crear aviso', 'menu_alert')],
      [Markup.button.callback('⭐ Favoritos', 'menu_favorites')],
      [Markup.button.callback('📋 Mis avisos', 'menu_my_alerts')],
      [Markup.button.callback('🔄 Actualizar datos', 'menu_refresh')],
    ])
  );
}

export async function handleParada(ctx: Context) {
  const chatId = ctx.chat?.id;
  if (!chatId) return;

  const args =
    ctx.message && 'text' in ctx.message ? ctx.message.text.split(' ') : [];
  if (args.length < 2) {
    await ctx.reply('Uso: `/parada <código_parada>`\nEjemplo: `/parada 1234`', {
      parse_mode: 'Markdown',
    });
    return;
  }

  const stopId = args[1];
  await showArrivalsForStop(ctx, stopId, args[2] || '12');
}

export async function handleAviso(ctx: Context) {
  const chatId = ctx.chat?.id;
  if (!chatId) return;

  const args =
    ctx.message && 'text' in ctx.message ? ctx.message.text.split(' ') : [];
  if (args.length < 4) {
    await ctx.reply(
      'Uso: `/aviso <parada> <línea> <minutos>`\n' +
        'Ejemplo: `/aviso 1234 L12 5` (avisa 5 min antes del bus L12 en parada 1234)',
      { parse_mode: 'Markdown' }
    );
    return;
  }

  const stopId = args[1];
  const lineId = args[2].toUpperCase().replace(/^L/, '');
  const minutesAhead = Number.parseInt(args[3], 10);

  if (Number.isNaN(minutesAhead) || minutesAhead < 1 || minutesAhead > 60) {
    await ctx.reply('❌ Minutos debe ser un número entre 1 y 60');
    return;
  }

  const rt = await fetchAndParseGTFSRT();
  if (!rt) {
    await ctx.reply('❌ Error al verificar datos. Intenta de nuevo.');
    return;
  }

  const arrival = getCachedArrivals(stopId, lineId);
  if (!arrival) {
    await ctx.reply(
      `❌ No hay servicio **L${lineId}** en parada **${stopId}** ahora mismo.`,
      { parse_mode: 'Markdown' }
    );
    return;
  }

  const alert = createAlert({
    chatId,
    stopId,
    stopName: `Parada ${stopId}`,
    lineId,
    lineName: `L${lineId}`,
    destination: arrival.trip.tripId,
    minutesAhead,
    active: true,
  });

  const mins = Math.round((arrival.arrivalTime - Date.now()) / 1000 / 60);
  await ctx.reply(
    `✅ **Aviso creado** (ID: \`${alert.id}\`)\n` +
      `🚏 Parada: **${stopId}**\n` +
      `🚌 Línea: **L${lineId}** → ${arrival.trip.tripId}\n` +
      `⏱ Próxima llegada: **${mins} min**\n` +
      `🔔 Te avisaré **${minutesAhead} min** antes`,
    { parse_mode: 'Markdown' }
  );
}

export async function handleLista(ctx: Context) {
  const chatId = ctx.chat?.id;
  if (!chatId) return;

  const alerts = getAlertsByChat(chatId);

  if (!alerts.length) {
    await ctx.reply(
      '📭 No tienes avisos activos. Usa `/aviso` para crear uno.'
    );
    return;
  }

  let msg = '📋 **Tus avisos:**\n\n';
  for (const alert of alerts) {
    const status = alert.active ? '🟢 Activo' : '🔴 Inactivo';
    msg += `#${alert.id} | ${status}\n`;
    msg += `  🚏 ${alert.stopId} | 🚌 L${alert.lineId} | 🔔 ${alert.minutesAhead} min\n\n`;
  }

  await ctx.reply(msg, { parse_mode: 'Markdown' });
}

export async function handleBorrar(ctx: Context) {
  const chatId = ctx.chat?.id;
  if (!chatId) return;

  const args =
    ctx.message && 'text' in ctx.message ? ctx.message.text.split(' ') : [];
  if (args.length < 2) {
    await ctx.reply('Uso: `/borrar <id>`\nEjemplo: `/borrar 5`', {
      parse_mode: 'Markdown',
    });
    return;
  }

  const id = Number.parseInt(args[1], 10);
  if (Number.isNaN(id)) {
    await ctx.reply('❌ ID inválido');
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

export async function handleActualizar(ctx: Context) {
  await ctx.reply('🔄 Actualizando datos GTFS-RT...', {
    parse_mode: 'Markdown',
  });

  const rt = await fetchAndParseGTFSRT();
  if (rt) {
    await ctx.reply(
      `✅ Datos actualizados (${new Date(rt.timestamp).toLocaleTimeString('es-ES')})`,
      { parse_mode: 'Markdown' }
    );
  } else {
    await ctx.reply('❌ Error al actualizar');
  }
}

export async function handleFavCommand(ctx: Context) {
  const chatId = ctx.chat?.id;
  if (!chatId) return;

  const args =
    ctx.message && 'text' in ctx.message ? ctx.message.text.split(' ') : [];
  if (args.length < 2) {
    await showFavorites(ctx);
    return;
  }

  if (args[1] === 'add' && args.length >= 4) {
    const stopId = args[2];
    const lineId = args[3].toUpperCase().replace(/^L/, '');
    const rt = await fetchAndParseGTFSRT();
    const arrival = rt ? getCachedArrivals(stopId, lineId) : null;

    addFavorite({
      chatId,
      stopId,
      stopName: `Parada ${stopId}`,
      lineId,
      lineName: `L${lineId}`,
      destination: arrival?.trip.tripId || `L${lineId}`,
    });

    await ctx.reply('⭐ Favorito guardado');
  } else if (args[1] === 'remove' && args.length >= 3) {
    const favId = Number.parseInt(args[2], 10);
    removeFavorite(favId);
    await ctx.reply('🗑️ Favorito eliminado');
  } else {
    await showFavorites(ctx);
  }
}

export function setupBot(bot: Telegraf<Context>) {
  setBotReference(bot);

  // Comandos
  bot.start(handleStart);
  bot.command('parada', handleParada);
  bot.command('aviso', handleAviso);
  bot.command('lista', handleLista);
  bot.command('borrar', handleBorrar);
  bot.command('actualizar', handleActualizar);
  bot.command('fav', handleFavCommand);

  // Callbacks
  bot.action('menu_main', handleMainMenu);
  bot.action('menu_arrivals', showLineSelector);
  bot.action('menu_alert', showLineSelectorForAlert);
  bot.action('menu_favorites', showFavorites);
  bot.action('menu_my_alerts', handleLista);
  bot.action('menu_refresh', handleActualizar);
  bot.action('menu_cancel', handleMainMenu);

  // Line selection
  bot.action(/line_\d+_(arrivals|alert)/, handleLineCallback);

  // Stop selection
  bot.action(/stop_\d+_(arrivals|alert)/, handleStopCallback);

  // Minutes selection
  bot.action(/minutes_\d+/, handleMinutesCallback);

  // Favorites
  bot.action(/fav_add_\d+_\d+/, handleFavAdd);
  bot.action(/fav_view_\d+/, handleFavView);
  bot.action(/fav_remove_\d+/, handleFavRemove);

  // Iniciar polling GTFS-RT y scheduler de alertas
  startRTPolling(config.pollIntervalMs);
  startAlertScheduler();

  // Fetch inicial
  fetchAndParseGTFSRT();

  console.log('🤖 Bot handlers registered');
}
