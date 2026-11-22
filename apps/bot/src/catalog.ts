/**
 * Índice de descubrimiento generado desde la API real de Dbus
 * (`scripts/build-catalog.mjs`). NO sustituye a los datos en vivo: solo
 * permite buscar paradas por nombre/código y líneas por código/destino.
 * Las llegadas siempre se consultan a la API.
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { normalizeLine } from './arrival.js';

export interface CatalogStop {
  cod: string;
  desc: string;
  lines: string[];
  destinations: string[];
}

export interface CatalogLine {
  id: string;
  cod: string;
  destinations: string[];
  stops: string[];
}

export interface CatalogItinerary {
  id: string;
  lineId: string;
  lineCod: string;
  destination: string;
  stops: string[];
}

interface CatalogData {
  generatedAt: string;
  source: string;
  attribution: string;
  lines: CatalogLine[];
  itineraries: CatalogItinerary[];
  stops: CatalogStop[];
}

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const catalogPath = path.resolve(__dirname, '../data/dbus-catalog.json');
const data = JSON.parse(fs.readFileSync(catalogPath, 'utf8')) as CatalogData;

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
export function searchStops(query: string, limit = 8): CatalogStop[] {
  const q = searchKey(query);
  if (!q) return [];
  const numeric = /^\d+$/.test(q);
  const ranked: Ranked<CatalogStop>[] = data.stops.map((stop) => {
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
export function searchLines(query: string, limit = 8): CatalogLine[] {
  const q = searchKey(query);
  if (!q) return [];
  const ranked: Ranked<CatalogLine>[] = data.lines.map((line) => {
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

export function getStopByCode(cod: string): CatalogStop | null {
  const key = normalizeLine(cod);
  return data.stops.find((stop) => stop.cod === key) ?? null;
}

/** Acepta código público (`28`) o id interno (`32`). */
export function getLineByCode(lineId: string): CatalogLine | null {
  const key = normalizeLine(lineId);
  // El código público manda: el id interno 28 es la línea pública 26.
  return (
    data.lines.find((line) => normalizeLine(line.cod) === key) ??
    data.lines.find((line) => normalizeLine(line.id) === key) ??
    null
  );
}

export function getItinerariesForLine(lineId: string): CatalogItinerary[] {
  const line = getLineByCode(lineId);
  if (!line) return [];
  return data.itineraries.filter((itinerario) => itinerario.lineId === line.id);
}

export function getItineraryById(id: string): CatalogItinerary | null {
  return data.itineraries.find((itinerario) => itinerario.id === id) ?? null;
}

export function getStopsForItinerary(itineraryId: string): CatalogStop[] {
  const itinerary = getItineraryById(itineraryId);
  if (!itinerary) return [];
  const codes = new Set(itinerary.stops);
  return data.stops.filter((stop) => codes.has(stop.cod));
}

export function getCatalogMeta(): {
  generatedAt: string;
  source: string;
  lines: number;
  stops: number;
} {
  return {
    generatedAt: data.generatedAt,
    source: data.source,
    lines: data.lines.length,
    stops: data.stops.length,
  };
}
