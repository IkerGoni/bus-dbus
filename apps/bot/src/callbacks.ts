/** Callbacks de teclado inline. Todos los datos viajan en `callback_data`. */
import type { Context } from 'telegraf';
import { normalizeLine } from './arrival.js';
import { handleActualizar, handleHelp, handleLista } from './commands.js';
import {
  deleteAlert,
  getAlertById,
  getFavoriteLines,
  getFavorites,
  removeFavorite,
  removeFavoriteLine,
} from './db.js';
import { invalidateStop } from './dbus-client.js';
import {
  answer,
  askSearch,
  askStopCode,
  callbackData,
  createAndConfirmAlert,
  safeEdit,
  saveFavorite,
  saveLineFavorite,
  showAllLines,
  showAllStopsForLine,
  showArrivalsForStop,
  showFavorites,
  showLineDestinations,
  showLineStops,
  showMainMenu,
  showMinutesSelector,
  showStopLines,
} from './render.js';
import { clearSession } from './session.js';

export async function handleMenuCallback(ctx: Context): Promise<void> {
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
    default:
      return;
  }
  await answer(ctx);
}

export async function handleSearchStopCallback(ctx: Context): Promise<void> {
  const match = callbackData(ctx).match(/^srch_s_(\d+)$/);
  if (!match) return;
  await showArrivalsForStop(ctx, match[1]);
  await answer(ctx);
}

export async function handleSearchLineCallback(ctx: Context): Promise<void> {
  const match = callbackData(ctx).match(/^srch_l_(.+)$/);
  if (!match) return;
  await showLineDestinations(ctx, match[1]);
  await answer(ctx);
}

export async function handleLineDestCallback(ctx: Context): Promise<void> {
  const match = callbackData(ctx).match(/^ld_(\d+)$/);
  if (!match) return;
  await showLineStops(ctx, match[1]);
  await answer(ctx);
}

export async function handleLineStopCallback(ctx: Context): Promise<void> {
  const match = callbackData(ctx).match(/^ls_(\d+)_(.+)$/);
  if (!match) return;
  await showArrivalsForStop(ctx, match[1], match[2]);
  await answer(ctx);
}

export async function handleAllLinesCallback(ctx: Context): Promise<void> {
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

export async function handleLineAllStopsCallback(ctx: Context): Promise<void> {
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

export async function handleFavLineSaveCallback(ctx: Context): Promise<void> {
  const match = callbackData(ctx).match(/^favline_([^_]+)$/);
  if (!match) return;
  await saveLineFavorite(ctx, match[1]);
}

export async function handleFavLineViewCallback(ctx: Context): Promise<void> {
  const match = callbackData(ctx).match(/^favline_view_(.+)$/);
  if (!match) return;
  await showLineDestinations(ctx, match[1]);
  await answer(ctx);
}

export async function handleFavLineRemoveCallback(ctx: Context): Promise<void> {
  const match = callbackData(ctx).match(/^favlin_remove_(\d+)$/);
  const chatId = ctx.chat?.id;
  if (!match || !chatId) return;
  const id = Number.parseInt(match[1], 10);
  const lines = await getFavoriteLines(chatId);
  if (!lines.some((line) => line.id === id)) {
    await answer(ctx, '❌ Línea no encontrada');
    return;
  }
  await removeFavoriteLine(id);
  await answer(ctx, '🗑️ Línea eliminada');
  await showFavorites(ctx);
}

export async function handleSaveStopFavorite(ctx: Context): Promise<void> {
  const match = callbackData(ctx).match(/^favstop_(\d+)$/);
  const chatId = ctx.chat?.id;
  if (!match || !chatId) return;
  const added = await saveFavorite(chatId, match[1]);
  await answer(
    ctx,
    added ? '⭐ Parada guardada' : '⭐ Ya tenías esta parada guardada'
  );
}

export async function handleStopRefresh(ctx: Context): Promise<void> {
  const match = callbackData(ctx).match(/^stop_refresh_(\d+)$/);
  if (!match) return;
  // Solo invalida la parada consultada: no tires el catálogo ni las demás
  // paradas en caché por un refresco puntual.
  invalidateStop(match[1]);
  clearSession(ctx.chat?.id ?? 0);
  await showArrivalsForStop(ctx, match[1]);
  await answer(ctx, '🔄 Actualizado');
}

export async function handleStopAlert(ctx: Context): Promise<void> {
  const match = callbackData(ctx).match(/^stop_alert_(\d+)$/);
  if (!match) return;
  await showStopLines(ctx, match[1]);
  await answer(ctx);
}

/**
 * Callbacks stateless (regla 8): parada y línea viajan en `callback_data`.
 * Un botón antiguo ya no puede crear un aviso en la parada equivocada aunque
 * el chat esté en otro flujo.
 */
export async function handleLineCallback(ctx: Context): Promise<void> {
  const match = callbackData(ctx).match(/^aline_(\d+)_([^_]+)$/);
  if (!match) return;
  const [, stopId, lineId] = match;
  await showMinutesSelector(ctx, stopId, normalizeLine(lineId));
  await answer(ctx);
}

export async function handleMinutesCallback(ctx: Context): Promise<void> {
  const match = callbackData(ctx).match(/^minutes_(\d+)_([^_]+)_(\d+)$/);
  if (!match) return;
  const [, stopId, lineId, rawMinutes] = match;
  const minutes = Number.parseInt(rawMinutes, 10);
  if (minutes < 1 || minutes > 60) {
    await answer(ctx, '❌ Minutos fuera de rango');
    return;
  }
  const result = await createAndConfirmAlert(
    ctx,
    stopId,
    normalizeLine(lineId),
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

export async function handleFavAdd(ctx: Context): Promise<void> {
  const match = callbackData(ctx).match(/^fav_add_(\d+)_([^_]+)$/);
  const chatId = ctx.chat?.id;
  if (!match || !chatId) return;
  const [, stopId, lineId] = match;
  const added = await saveFavorite(chatId, stopId, lineId);
  await answer(ctx, added ? '⭐ Favorito guardado' : '⭐ Ya guardado');
  await showFavorites(ctx);
}

export async function handleFavView(ctx: Context): Promise<void> {
  const match = callbackData(ctx).match(/^fav_view_(\d+)$/);
  const chatId = ctx.chat?.id;
  if (!match || !chatId) return;
  const favs = await getFavorites(chatId);
  const fav = favs.find((f) => f.id === Number.parseInt(match[1], 10));
  if (!fav) {
    await answer(ctx, '❌ Favorito no encontrado');
    return;
  }
  await showArrivalsForStop(ctx, fav.stopId, fav.lineId || undefined);
  await answer(ctx);
}

export async function handleFavRemove(ctx: Context): Promise<void> {
  const match = callbackData(ctx).match(/^fav_remove_(\d+)$/);
  const chatId = ctx.chat?.id;
  if (!match || !chatId) return;
  const id = Number.parseInt(match[1], 10);
  const favs = await getFavorites(chatId);
  if (!favs.some((f) => f.id === id)) {
    await answer(ctx, '❌ Favorito no encontrado');
    return;
  }
  await removeFavorite(id);
  await answer(ctx, '🗑️ Favorito eliminado');
  await showFavorites(ctx);
}

export async function handleAlertDelete(ctx: Context): Promise<void> {
  const match = callbackData(ctx).match(/^adel_(\d+)$/);
  const chatId = ctx.chat?.id;
  if (!match || !chatId) return;
  const id = Number.parseInt(match[1], 10);
  const alert = await getAlertById(id);
  if (!alert || alert.chatId !== chatId) {
    await answer(ctx, '❌ Aviso no encontrado');
    return;
  }
  await deleteAlert(id);
  await answer(ctx, '🗑️ Aviso eliminado');
  await handleLista(ctx);
}
