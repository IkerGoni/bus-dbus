import type {
  Catalog,
  CatalogItinerary,
  CatalogLine,
  CatalogStop,
} from '@dbus/contracts';
import { normalizeLine } from './lines.js';

/** Clave de búsqueda sin acentos, casos ni espacios duplicados. */
export function searchKey(value: string): string {
  return normalizeLine(value)
    .normalize('NFD')
    .replace(/\p{M}/gu, '')
    .toLocaleLowerCase('es')
    .replace(/\s+/g, ' ')
    .trim();
}

interface Ranked<T> {
  item: T;
  rank: number;
}

function best<T>(ranked: Ranked<T>[], limit: number): T[] {
  return ranked
    .filter((entry) => entry.rank < Number.POSITIVE_INFINITY)
    .sort((a, b) => a.rank - b.rank)
    .slice(0, limit)
    .map((entry) => entry.item);
}

/** Busca paradas por código exacto/parcial o por nombre (sin acentos). */
export function searchStops(
  catalog: Catalog,
  query: string,
  limit = 8
): CatalogStop[] {
  const q = searchKey(query);
  if (!q) return [];
  const numeric = /^\d+$/.test(q);
  const ranked: Ranked<CatalogStop>[] = catalog.stops.map((stop) => {
    const cod = searchKey(stop.cod);
    const desc = searchKey(stop.desc);
    let rank = Number.POSITIVE_INFINITY;
    if (cod === q) rank = 0;
    else if (numeric && cod.startsWith(q)) rank = 1;
    else if (desc.startsWith(q)) rank = 2;
    else if (desc.includes(q)) rank = 3;
    return { item: stop, rank };
  });
  return best(ranked, limit);
}

/** Busca líneas por código (público o interno) o por destino. */
export function searchLines(
  catalog: Catalog,
  query: string,
  limit = 8
): CatalogLine[] {
  const q = searchKey(query);
  if (!q) return [];
  const ranked: Ranked<CatalogLine>[] = catalog.lines.map((line) => {
    const publicCode = searchKey(line.cod);
    const internalId = searchKey(line.id);
    const destinations = line.destinations.map(searchKey);
    let rank = Number.POSITIVE_INFINITY;
    if (publicCode === q) rank = 0;
    else if (internalId === q) rank = 1;
    else if (publicCode.startsWith(q) || internalId.startsWith(q)) rank = 2;
    else if (destinations.some((destination) => destination.includes(q)))
      rank = 3;
    return { item: line, rank };
  });
  return best(ranked, limit);
}

export function getStopByCode(
  catalog: Catalog,
  cod: string
): CatalogStop | null {
  const key = normalizeLine(cod);
  return catalog.stops.find((stop) => stop.cod === key) ?? null;
}

/** Acepta código público (`28`) o id interno (`32`). */
export function getLineByCode(
  catalog: Catalog,
  lineId: string
): CatalogLine | null {
  const key = normalizeLine(lineId);
  // El código público manda: el id interno 28 es la línea pública 26.
  return (
    catalog.lines.find((line) => normalizeLine(line.cod) === key) ??
    catalog.lines.find((line) => normalizeLine(line.id) === key) ??
    null
  );
}

/** Líneas del índice que pasan por una parada (descubrimiento, sin horarios). */
export function getLinesForStop(
  catalog: Catalog,
  cod: string
): { lineId: string; internalLineId: string; destination: string }[] {
  const stop = getStopByCode(catalog, cod);
  if (!stop) return [];
  return stop.lines.flatMap((code) => {
    const line = getLineByCode(catalog, code);
    return [
      {
        lineId: line?.cod ?? normalizeLine(code),
        internalLineId: line?.id ?? '',
        destination: line?.destinations[0] ?? '',
      },
    ];
  });
}

/**
 * Código público de una línea. Con datos en vivo la llegada ya trae el código
 * público; sin servicio el índice resuelve el alias (id interno 32 → 28).
 * En contexto de parada manda la línea pública que realmente pasa por ella.
 */
export function toPublicLineId(
  catalog: Catalog,
  lineId: string,
  stopId?: string
): string {
  const key = normalizeLine(lineId);
  if (stopId) {
    const atStop = getLinesForStop(catalog, stopId);
    if (atStop.some((line) => line.lineId === key)) return key;
    const byInternal = atStop.find((line) => line.internalLineId === key);
    if (byInternal) return byInternal.lineId;
  }
  return getLineByCode(catalog, key)?.cod ?? key;
}

/** Todas las paradas de una línea (unión de itinerarios, sin duplicados). */
export function getAllStopsForLine(
  catalog: Catalog,
  lineId: string
): CatalogStop[] {
  const codes = new Set(
    getItinerariesForLine(catalog, lineId).flatMap(
      (itinerario) => itinerario.stops
    )
  );
  return catalog.stops.filter((stop) => codes.has(stop.cod));
}

export function getItinerariesForLine(
  catalog: Catalog,
  lineId: string
): CatalogItinerary[] {
  const line = getLineByCode(catalog, lineId);
  if (!line) return [];
  return catalog.itineraries.filter(
    (itinerario) => itinerario.lineId === line.id
  );
}

export function getItineraryById(
  catalog: Catalog,
  id: string
): CatalogItinerary | null {
  return catalog.itineraries.find((itinerario) => itinerario.id === id) ?? null;
}

export function getStopsForItinerary(
  catalog: Catalog,
  itineraryId: string
): CatalogStop[] {
  const itinerary = getItineraryById(catalog, itineraryId);
  if (!itinerary) return [];
  const codes = new Set(itinerary.stops);
  return catalog.stops.filter((stop) => codes.has(stop.cod));
}

export function getCatalogMeta(catalog: Catalog): {
  generatedAt: string;
  source: string;
  lines: number;
  stops: number;
} {
  return {
    generatedAt: catalog.generatedAt,
    source: catalog.source,
    lines: catalog.lines.length,
    stops: catalog.stops.length,
  };
}
