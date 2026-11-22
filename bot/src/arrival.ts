import { type DbusTiempo, fetchStopTimes, getStopName } from './dbus-client.js';
import type { NextArrivalResult } from './types.js';

/** Escapa los caracteres especiales del Markdown clásico de Telegram. */
export function escapeMd(text: string): string {
  return text.replace(/([_*`[])/g, '\\$1');
}

function normalizeLine(lineId: string): string {
  return lineId.trim().toUpperCase().replace(/^L/, '');
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
    .filter((arrival) => !wanted || arrival.lineId === wanted)
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
