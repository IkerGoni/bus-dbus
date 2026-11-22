/**
 * Índice de descubrimiento generado desde la API real de Dbus
 * (`scripts/build-catalog.mjs`). NO sustituye a los datos en vivo: solo
 * permite buscar paradas por nombre/código y líneas por código/destino.
 * Las llegadas siempre se consultan a la API.
 *
 * La carga de fichero es propia del bot (Node/fs); la búsqueda y el matching
 * son helpers puros de `@dbus/core`.
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import type { Catalog } from '@dbus/contracts';
import { normalizeLine } from '@dbus/core';
import {
  getCatalogMeta as catalogMeta,
  searchKey as coreSearchKey,
  searchLines as coreSearchLines,
  searchStops as coreSearchStops,
  getItinerariesForLine as itinerariesForLine,
  getItineraryById as itineraryById,
  getLineByCode as lineByCode,
  getStopByCode as stopByCode,
  getStopsForItinerary as stopsForItinerary,
} from '@dbus/core';

export type {
  CatalogItinerary,
  CatalogLine,
  CatalogStop,
} from '@dbus/contracts';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const catalogPath = path.resolve(__dirname, '../data/dbus-catalog.json');
const data = JSON.parse(fs.readFileSync(catalogPath, 'utf8')) as Catalog;

export const searchKey = coreSearchKey;

/** Busca paradas por código exacto/parcial o por nombre (sin acentos). */
export function searchStops(query: string, limit = 8) {
  return coreSearchStops(data, query, limit);
}

/** Busca líneas por código (público o interno) o por destino. */
export function searchLines(query: string, limit = 8) {
  return coreSearchLines(data, query, limit);
}

export function getStopByCode(cod: string) {
  return stopByCode(data, cod);
}

/**
 * Nombre del índice para una parada. Es dato real del catálogo (descubierto
 * desde la API), nunca una invención: solo se usa para rotular cuando la
 * respuesta en vivo no trae `parada.desc` (por ejemplo, sin servicio).
 */
export function getCatalogStopName(cod: string): string {
  return getStopByCode(cod)?.desc ?? '';
}

/** Líneas del índice que pasan por una parada, con destino real del índice. */
export function getCatalogLinesForStop(cod: string): {
  lineId: string;
  internalLineId: string;
  destination: string;
}[] {
  const stop = getStopByCode(cod);
  if (!stop) return [];
  return stop.lines.flatMap((code) => {
    const line = getLineByCode(code);
    return [
      {
        lineId: line?.cod ?? normalizeLine(code),
        internalLineId: line?.id ?? '',
        destination: line?.destinations[0] ?? '',
      },
    ];
  });
}

/** Acepta código público (`28`) o id interno (`32`). */
export function getLineByCode(lineId: string) {
  return lineByCode(data, lineId);
}

export function getItinerariesForLine(lineId: string) {
  return itinerariesForLine(data, lineId);
}

export function getItineraryById(id: string) {
  return itineraryById(data, id);
}

export function getStopsForItinerary(itineraryId: string) {
  return stopsForItinerary(data, itineraryId);
}

export function getCatalogMeta() {
  return catalogMeta(data);
}

/** Todas las líneas públicas, ordenadas por código (5, 8, ..., B9). */
export function listAllLines() {
  return [...data.lines].sort((a, b) =>
    a.cod.localeCompare(b.cod, 'es', { numeric: true, sensitivity: 'base' })
  );
}

/**
 * Todas las paradas de una línea (unión de sus itinerarios, sin duplicados),
 * en el orden del catálogo. Un mismo destino no muestra toda la línea.
 */
export function getAllStopsForLine(lineId: string) {
  const codes = new Set(
    getItinerariesForLine(lineId).flatMap((itinerario) => itinerario.stops)
  );
  return data.stops.filter((stop) => codes.has(stop.cod));
}

/**
 * Código público de una línea. Con datos en vivo la llegada ya trae el código
 * público; sin servicio usamos el índice (id interno 32 → código público 28)
 * para no persistir ni mostrar el id interno como si fuera la línea.
 *
 * Si se conoce la parada, el id interno se resuelve entre las líneas de esa
 * parada: `32` es a la vez línea pública y id interno de la `28`, y en el
 * contexto de una parada debe valer la que realmente pasa por ella.
 */
export function toPublicLineId(lineId: string, stopId?: string): string {
  const key = normalizeLine(lineId);
  if (stopId) {
    const atStop = getCatalogLinesForStop(stopId);
    const byInternal = atStop.find((line) => line.internalLineId === key);
    if (byInternal) return byInternal.lineId;
    if (atStop.some((line) => line.lineId === key)) return key;
  }
  return getLineByCode(key)?.cod ?? key;
}

export interface Page<T> {
  items: T[];
  page: number;
  totalPages: number;
  total: number;
  hasPrev: boolean;
  hasNext: boolean;
}

/** Página 0-based, siempre con al menos una página (aunque esté vacía). */
export function paginate<T>(items: T[], page: number, size: number): Page<T> {
  const total = items.length;
  const totalPages = Math.max(1, Math.ceil(total / size));
  const current = Number.isFinite(page)
    ? Math.min(Math.max(0, Math.trunc(page)), totalPages - 1)
    : 0;
  return {
    items: items.slice(current * size, (current + 1) * size),
    page: current,
    totalPages,
    total,
    hasPrev: current > 0,
    hasNext: current < totalPages - 1,
  };
}
