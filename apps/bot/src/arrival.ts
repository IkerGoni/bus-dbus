import { type DbusTiempo, fetchStopTimes, getStopName } from './dbus-client.js';
import type { NextArrivalResult } from './types.js';

/** Escapa los caracteres especiales del Markdown clásico de Telegram. */
export function escapeMd(text: string): string {
  return text.replace(/([_*`[])/g, '\\$1');
}

/**
 * Normaliza un identificador de línea introducido por el usuario.
 * Acepta `28`, `L28` y `028`.
 */
export function normalizeLine(lineId: string): string {
  return lineId
    .trim()
    .toUpperCase()
    .replace(/^L/, '')
    .replace(/^0+(?=\d)/, '');
}

/** Dbus usa un id interno (32) distinto del código público (28). */
export function matchesLine(
  arrival: Pick<NextArrivalResult, 'lineId' | 'internalLineId'>,
  lineId: string
): boolean {
  const wanted = normalizeLine(lineId);
  return Boolean(
    wanted &&
      (normalizeLine(arrival.lineId) === wanted ||
        normalizeLine(arrival.internalLineId) === wanted)
  );
}

function toArrival(
  tiempo: DbusTiempo,
  stopId: string,
  stopName: string
): NextArrivalResult {
  const minutes = Math.max(0, Math.round(tiempo.minutos));
  const linea = tiempo.itinerario.linea;
  const lineId = linea.cod || lineIdFromNombre(linea.nombre);

  return {
    arrivalTime: Date.now() + minutes * 60_000,
    minutes,
    lineId,
    internalLineId: linea.id,
    destination:
      tiempo.itinerario.destino ||
      tiempo.itinerario.nombre ||
      'Destino desconocido',
    stopId,
    stopName,
    realtime: tiempo.tipo === 1,
    hora: tiempo.hora,
    itineraryId: tiempo.itinerario.id,
  };
}

function lineIdFromNombre(nombre: string): string {
  const match = nombre.match(/\d+/);
  return match ? match[0] : nombre;
}

/**
 * Llegadas reales de una parada, ordenadas por proximidad.
 * `lineId` (opcional) acepta "28" o "L28".
 */
export async function getNextArrivals(
  stopId: string,
  lineId?: string,
  forceRefresh = false
): Promise<NextArrivalResult[]> {
  const times = await fetchStopTimes(stopId, forceRefresh);
  if (!times || !times.tiempos.length) return [];

  const stopName = times.parada?.desc || getStopName(stopId);
  const wanted = lineId ? normalizeLine(lineId) : undefined;

  return times.tiempos
    .map((tiempo) => toArrival(tiempo, stopId, stopName))
    .filter((arrival) => !wanted || matchesLine(arrival, wanted))
    .sort((a, b) => a.arrivalTime - b.arrivalTime);
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
  if (!times) return [];

  const lines = new Map<string, StopLine>();
  for (const tiempo of times.tiempos) {
    const rawId =
      tiempo.itinerario.linea.cod ||
      lineIdFromNombre(tiempo.itinerario.linea.nombre);
    if (!rawId) continue;
    if (!lines.has(rawId)) {
      lines.set(rawId, {
        lineId: rawId,
        internalLineId: tiempo.itinerario.linea.id,
        destination:
          tiempo.itinerario.destino ||
          tiempo.itinerario.nombre ||
          'Destino desconocido',
      });
    }
  }
  return [...lines.values()].sort((a, b) =>
    a.lineId.localeCompare(b.lineId, 'es', { numeric: true })
  );
}

export interface AlertMatchOptions {
  /** Código público o id interno de Dbus. */
  lineId: string;
  /** Si se indicó al crear el aviso, evita avisar de la dirección contraria. */
  destination?: string;
  minutesAhead: number;
}

/** Marcador usado cuando no había datos de destino al crear el aviso. */
export const UNKNOWN_DESTINATION = 'Servicio programado';

function normalizeDestination(value: string): string {
  return value.trim().toLocaleLowerCase('es').replace(/\s+/g, ' ');
}

/** Comparación tolerante: Dbus varía el texto del destino entre responses. */
function destinationsMatch(actual: string, expected: string): boolean {
  const a = normalizeDestination(actual);
  const b = normalizeDestination(expected);
  return Boolean(a && b && (a === b || a.includes(b) || b.includes(a)));
}

/**
 * Elige la llegada que dispara un aviso. Prefiere estimaciones GPS sobre
 * horarios programados y, entre ellas, la más próxima. Pura y testeable.
 */
export function findMatchingArrival(
  arrivals: NextArrivalResult[],
  options: AlertMatchOptions
): NextArrivalResult | null {
  // Si no se guardó un destino real, no filtrar: mejor avisar que silenciar.
  const destination =
    options.destination &&
    normalizeDestination(options.destination) !==
      normalizeDestination(UNKNOWN_DESTINATION)
      ? options.destination
      : undefined;
  const candidates = arrivals.filter((arrival) => {
    if (!matchesLine(arrival, options.lineId)) return false;
    if (arrival.minutes < 0 || arrival.minutes > options.minutesAhead)
      return false;
    if (destination && !destinationsMatch(arrival.destination, destination)) {
      return false;
    }
    return true;
  });

  const realtime = candidates.filter((arrival) => arrival.realtime);
  const pool = realtime.length ? realtime : candidates;
  return pool.reduce<NextArrivalResult | null>(
    (best, arrival) =>
      !best || arrival.minutes < best.minutes ? arrival : best,
    null
  );
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
  estado?: string
): string {
  const header = `🚏 **${escapeMd(stopName)}** (${arrivals[0]?.stopId ?? ''})`;

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
