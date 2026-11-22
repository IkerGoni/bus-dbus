import { matchesLine, normalizeLine } from './lines.js';
import type {
  AlertMatchOptions,
  Arrival,
  DbusStopTimes,
  DbusTiempo,
  Line,
} from './types.js';

/** Escapa los caracteres especiales del Markdown clásico de Telegram. */
export function escapeMd(text: string): string {
  // Telegram Markdown clásico: hay que escapar también '\' y ']', si no un
  // texto que termina en '\' deja el entity en negrita sin cerrar.
  return text.replace(/([\\_*`[\]])/g, '\\$1');
}

function lineIdFromNombre(nombre: string): string {
  const match = nombre.match(/\d+/);
  return match ? match[0] : nombre;
}

/** Convierte un `tiempo` de Dbus en una llegada normalizada. */
export function toArrival(
  tiempo: DbusTiempo,
  stopId: string,
  stopName: string,
  now: number = Date.now()
): Arrival {
  const minutes = Math.max(0, Math.round(tiempo.minutos));
  const linea = tiempo.itinerario.linea;
  const lineId = linea.cod || lineIdFromNombre(linea.nombre);

  return {
    arrivalTime: now + minutes * 60_000,
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

/**
 * Llegadas reales de una respuesta `tiemposParada`, ordenadas por proximidad.
 * `lineId` (opcional) acepta "28" o "L28".
 */
export function mapTiemposParada(
  times: DbusStopTimes | null,
  options: {
    stopCode: string;
    stopName?: string;
    lineId?: string;
    now?: number;
  }
): Arrival[] {
  if (!times || !times.tiempos.length) return [];

  const { stopCode } = options;
  const now = options.now ?? Date.now();
  const stopName =
    times.parada?.desc || options.stopName || `Parada ${stopCode}`;
  const wanted = options.lineId ? normalizeLine(options.lineId) : undefined;

  return times.tiempos
    .map((tiempo) => toArrival(tiempo, stopCode, stopName, now))
    .filter((arrival) => !wanted || matchesLine(arrival, wanted))
    .sort((a, b) => a.arrivalTime - b.arrivalTime);
}

/** Líneas que pasan por una parada, según los datos reales del momento. */
export function linesFromStopTimes(times: DbusStopTimes | null): Line[] {
  if (!times) return [];

  const lines = new Map<string, Line>();
  for (const tiempo of times.tiempos) {
    const linea = tiempo.itinerario.linea;
    const rawId = linea.cod || lineIdFromNombre(linea.nombre);
    if (!rawId) continue;
    if (!lines.has(rawId)) {
      lines.set(rawId, {
        lineId: rawId,
        internalLineId: linea.id,
        code: rawId,
        id: linea.id,
        name: linea.nombre || `L${rawId}`,
        color: linea.color ?? null,
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
export function findMatchingArrival<T extends Arrival>(
  arrivals: T[],
  options: AlertMatchOptions
): T | null {
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
  return pool.reduce<T | null>(
    (best, arrival) =>
      !best || arrival.minutes < best.minutes ? arrival : best,
    null
  );
}
