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
import {
  getCatalogLinesForStop,
  getCatalogStopName,
  toPublicLineId,
} from './catalog.js';
import {
  type DbusStopTimes,
  fetchStopTimes,
  getStopName,
} from './dbus-client.js';
import type { NextArrivalResult } from './types.js';

export { getCatalogLinesForStop, toPublicLineId };

/**
 * Nombre para mostrar. La respuesta en vivo manda; si no trae descripción
 * (SIN_SERVICIO / NO DATOS) se usa el nombre real del índice, nunca un invento.
 */
export function displayStopName(
  stopId: string,
  liveName?: string | null
): string {
  const live = liveName?.trim();
  if (live) return live;
  const cached = getStopName(stopId);
  if (cached && cached !== `Parada ${stopId}`) return cached;
  return getCatalogStopName(stopId) || cached;
}

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
  forceRefresh = false,
  /** Respuesta ya consultada: evita una segunda llamada a la API en vivo. */
  times?: DbusStopTimes | null
): Promise<NextArrivalResult[]> {
  const data =
    times !== undefined ? times : await fetchStopTimes(stopId, forceRefresh);
  return mapTiemposParada(data, {
    stopCode: stopId,
    stopName: getStopName(stopId),
    lineId,
    now: Date.now(),
  });
}

export async function getNextArrival(
  stopId: string,
  lineId: string,
  times?: DbusStopTimes | null
): Promise<NextArrivalResult | null> {
  const arrivals = await getNextArrivals(stopId, lineId, false, times);
  return arrivals[0] ?? null;
}

export interface StopLine {
  lineId: string;
  internalLineId: string;
  destination: string;
}

/** Líneas que pasan por una parada, según los datos reales del momento. */
export async function getStopLines(
  stopId: string,
  times?: DbusStopTimes | null
): Promise<StopLine[]> {
  const data = times !== undefined ? times : await fetchStopTimes(stopId);
  return linesFromStopTimes(data);
}

/**
 * Líneas de una parada y su procedencia. Sin servicio, la API no devuelve
 * líneas: el índice (generado de la API real) permite seguir mostrando qué
 * líneas pasan, siempre etiquetado como índice y sin horarios inventados.
 */
export async function getStopLinesWithSource(
  stopId: string,
  times?: DbusStopTimes | null
): Promise<{ lines: StopLine[]; source: 'live' | 'catalog' }> {
  const live = await getStopLines(stopId, times);
  if (live.length) return { lines: live, source: 'live' };
  return { lines: getCatalogLinesForStop(stopId), source: 'catalog' };
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
  stopId?: string,
  /** Avisos de servicio reales devueltos por la API de Dbus. */
  avisos?: string[] | null
): string {
  const code = arrivals[0]?.stopId ?? stopId ?? '';
  const header = `🚏 **${escapeMd(stopName)}**${code ? ` (${code})` : ''}`;
  const realAvisos = Array.isArray(avisos) ? avisos : [];
  const notice = realAvisos.length
    ? `\n\n📢 ${realAvisos
        .map((aviso) => escapeMd(aviso.trim()))
        .filter(Boolean)
        .join('\n📢 ')}`
    : '';

  if (!arrivals.length) {
    if (estado === 'SIN_SERVICIO' || estado === 'NO DATOS') {
      return `${header}\n\n🌙 Sin servicio en este momento.${notice}`;
    }
    if (estado) {
      return `${header}\n\n📭 No hay llegadas (${escapeMd(estado)}).${notice}`;
    }
    return `${header}\n\n⚠️ No se pudo consultar la API de Dbus. Inténtalo de nuevo.${notice}`;
  }

  const shown = arrivals.slice(0, 8);
  const body = shown.map(formatArrival).join('\n\n');
  const more =
    arrivals.length > shown.length
      ? `\n\n…y ${arrivals.length - shown.length} más`
      : '';
  return `${header}\n\n${body}${more}${notice}`;
}
