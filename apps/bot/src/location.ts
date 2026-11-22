/**
 * Flujo de paradas cercanas: /ubicacion + mensajes de ubicación de Telegram.
 *
 * Las coordenadas se usan solo durante la petición a Overpass: no se escriben
 * en la BD, ni en la sesión, ni en el log. El resultado se muestra con bytes
 * de callback stateless (`srch_s_<cod>`) que llevan a las llegadas en vivo.
 */
import { type Context, Markup } from 'telegraf';
import { config } from './config.js';
import {
  NEARBY_DISABLED_TEXT,
  NEARBY_UNAVAILABLE_TEXT,
  NearbyUnavailableError,
  getNearbyStops,
  isNearbyDisabled,
} from './nearby.js';
import { safeEdit } from './render.js';

export const UBICACION_TEXT =
  '📍 **Paradas cercanas**\n\n' +
  'Pulsa el botón para compartir tu ubicación con el bot. ' +
  'Buscaré paradas Dbus cerca de ti usando OpenStreetMap y te mostraré ' +
  'sus llegadas en tiempo real.\n\n' +
  'No guardo tu ubicación: se usa solo para esta consulta.';

export async function handleUbicacion(ctx: Context): Promise<void> {
  if (isNearbyDisabled()) {
    await safeEdit(ctx, NEARBY_DISABLED_TEXT);
    return;
  }
  if (ctx.callbackQuery) {
    await safeEdit(ctx, UBICACION_TEXT, { parse_mode: 'Markdown' });
    return;
  }
  // El teclado de reply (botón locationRequest) solo aplica a sendMessage;
  // editMessageText de Telegram acepta únicamente teclados inline.
  await ctx.reply(UBICACION_TEXT, {
    parse_mode: 'Markdown',
    ...Markup.keyboard([
      [Markup.button.locationRequest('📍 Compartir mi ubicación')],
    ])
      .oneTime()
      .resize(),
  });
}

export async function handleLocation(ctx: Context): Promise<void> {
  const message = ctx.message;
  if (!message || !('location' in message)) return;
  const { latitude, longitude } = message.location;
  if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) {
    await ctx.reply('📍 Ubicación no válida. Prueba /ubicacion.');
    return;
  }
  if (isNearbyDisabled()) {
    await ctx.reply(NEARBY_DISABLED_TEXT);
    return;
  }

  try {
    const stops = await getNearbyStops(latitude, longitude);
    if (!stops.length) {
      await ctx.reply(
        `📭 No encontré paradas Dbus con referencia en OpenStreetMap a ${config.nearbyRadiusM} m de tu ubicación. Usa /buscar.`
      );
      return;
    }
    const rows = stops.map((stop) => [
      Markup.button.callback(
        `🚏 ${stop.desc.slice(0, 24)} (${stop.cod}) · ${Math.round(stop.distanceM)} m`,
        `srch_s_${stop.cod}`
      ),
    ]);
    await ctx.reply(
      '📍 Paradas cercanas (Dbus + OpenStreetMap):\n' +
        'Elige una para ver llegadas en vivo.',
      Markup.inlineKeyboard(rows)
    );
  } catch (error) {
    // Nunca se rellena con paradas inventadas: si falla la fuente, se dice.
    if (!(error instanceof NearbyUnavailableError)) throw error;
    console.error('[nearby] Overpass no disponible');
    await ctx.reply(NEARBY_UNAVAILABLE_TEXT);
  }
}
