/**
 * Capa de llegadas del bot.
 *
 * La lógica pura (normalización, alias 28↔32, matching de avisos) vive en
 * `@dbus/core`. Aquí quedan solo las funciones que consultan la API del bot y
 * el formateo Markdown propio de Telegram.
 */
import {
  escapeMd,
  findMatchingArrival,
  linesFromStopTimes,
  mapTiemposParada,
  matchesLine,
  normalizeLine,
} from '@dbus/core';
import { fetchStopTimes, getStopName } from './dbus-client.js';
import type { NextArrivalResult } from './types.js';

export {
  escapeMd,
  findMatchingArrival,
  matchesLine,
  normalizeLine,
} from '@dbus/core';
export { UNKNOWN_DESTINATION } from '@dbus/core';
export type { AlertMatchOptions } from '@dbus/core';

export async function getNextArrivals(
  stopId: string,
  lineId?: string,
  forceRefresh = false
): Promise<NextArrivalResult[]> {
  const times = await fetchStopTimes(stopId, forceRefresh);
  return mapTiemposParada(times, {
    stopCode: stopId,
    stopName: getStopName(stopId),
    lineId,
    now: Date.now(),
  });
}

export async function getNextArrival(
  stopId: string,
  lineId: string
): Promise<NextArrivalResult | null> {
  const arrivals = await getNextArrivals(stopId, lineId);
  return arrivals[0] ?? null;
}

export interface StopLine {
  lineId: string;
  internalLineId: string;
  destination: string;
}

/** Líneas que pasan por una parada, según los datos reales del momento. */
export async function getStopLines(stopId: string): Promise<StopLine[]> {
  const times = await fetchStopTimes(stopId);
  return linesFromStopTimes(times);
}

export function formatArrival(arrival: NextArrivalResult): string {
  const line = escapeMd(arrival.lineId);
  const destination = escapeMd(arrival.destination);
  const source = arrival.realtime ? '📡 tiempo real' : '🕒 horario';
  const hora = arrival.hora ? ` · ${escapeMd(arrival.hora)}` : '';
  const mins = arrival.minutes === 0 ? 'ahora' : `${arrival.minutes} min`;

  return `🚌 **L${line}** → ${destination}\n⏱ ${mins}${hora} · ${source}`;
}

export function formatArrivalsList(
  stopName: string,
  arrivals: NextArrivalResult[],
  estado?: string,
  stopId?: string
): string {
  const code = arrivals[0]?.stopId ?? stopId ?? '';
  const header = `🚏 **${escapeMd(stopName)}**${code ? ` (${code})` : ''}`;

  if (!arrivals.length) {
    if (estado === 'SIN_SERVICIO' || estado === 'NO DATOS') {
      return `${header}\n\n🌙 Sin servicio en este momento.`;
    }
    if (estado) {
      return `${header}\n\n📭 No hay llegadas (${escapeMd(estado)}).`;
    }
    return `${header}\n\n⚠️ No se pudo consultar la API de Dbus. Inténtalo de nuevo.`;
  }

  const shown = arrivals.slice(0, 8);
  const body = shown.map(formatArrival).join('\n\n');
  const more =
    arrivals.length > shown.length
      ? `\n\n…y ${arrivals.length - shown.length} más`
      : '';
  return `${header}\n\n${body}${more}`;
}
