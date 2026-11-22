/**
 * Punto de composición del bot: registra los handlers en Telegraf.
 *
 * La lógica vive en cuatro módulos con dependencias en una sola dirección:
 * `session` (estado) → `render` (vistas/acciones) → `commands` → `callbacks`.
 * Este fichero conserva la API pública que usan `index.ts` y los tests.
 */
import type { Context, Telegraf } from 'telegraf';
import { setBotInstance, startAlertScheduler } from './alerts.js';
import {
  handleAlertDelete,
  handleAllLinesCallback,
  handleFavAdd,
  handleFavLineRemoveCallback,
  handleFavLineSaveCallback,
  handleFavLineViewCallback,
  handleFavRemove,
  handleFavView,
  handleLineAllStopsCallback,
  handleLineCallback,
  handleLineDestCallback,
  handleLineStopCallback,
  handleMenuCallback,
  handleMinutesCallback,
  handleSaveStopFavorite,
  handleSearchLineCallback,
  handleSearchStopCallback,
  handleStopAlert,
  handleStopRefresh,
} from './callbacks.js';
import {
  handleActualizar,
  handleAviso,
  handleBorrar,
  handleBuscar,
  handleEstado,
  handleFavCommand,
  handleHelp,
  handleLineaCommand,
  handleLineas,
  handleLista,
  handleParada,
  handleStart,
  handleText,
} from './commands.js';
import { config } from './config.js';
import { fetchStopTimes, getDataSource } from './dbus-client.js';

export {
  getSessionCount,
  pruneSessions,
} from './session.js';
export {
  handleActualizar,
  handleAviso,
  handleBorrar,
  handleBuscar,
  handleEstado,
  handleFavCommand,
  handleHelp,
  handleLineaCommand,
  handleLineas,
  handleLista,
  handleParada,
  handleStart,
} from './commands.js';
export { handleAlertDelete } from './callbacks.js';

export function setBotReference(bot: Telegraf<Context>): void {
  setBotInstance(bot);
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
  void fetchStopTimes('2'); // precargar conexión con una parada con servicio

  console.log('🤖 Bot handlers registered');
  console.log(`📡 Data source: ${getDataSource()}`);
  console.log(`🌐 Language: ${config.language}`);
}
