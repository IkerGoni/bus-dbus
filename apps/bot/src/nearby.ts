/**
 * Paradas cercanas sobre el índice local de coordenadas: sin Overpass en
 * caliente y sin circuit breaker de red. Fuentes reales y read-only:
 *  - índice local de coordenadas OSM (generado por `apps/bot/scripts/`):
 *    posición y `cod` de la parada;
 *  - catálogo Dbus: código y nombre canónicos de la parada.
 * El `cod` se cruza con el catálogo para no inventar paradas. Las coordenadas
 * del usuario se usan en memoria durante la petición y NO se guardan en
 * SQLite, sesión, ni logs.
 *
 * Si el índice falta o está corrupto, se dice al usuario; nunca se devuelven
 * paradas de relleno.
 */
import { getStopByCode } from './catalog.js';
import { config } from './config.js';
import {
  type StopCoordinates,
  StopCoordinatesError,
  loadStopCoordinates,
} from './stop-coordinates.js';

export const NEARBY_DISABLED_TEXT =
  '⚠️ El servicio de paradas cercanas está desactivado. ' +
  'Vuelve a intentarlo más tarde; mientras tanto usa /buscar.';

export const NEARBY_UNAVAILABLE_TEXT =
  '⚠️ Ahora mismo no puedo consultar el índice de paradas cercanas. ' +
  'No muestro paradas inventadas. ' +
  'Prueba de nuevo en unos minutos o usa /buscar.';

export function isNearbyDisabled(): boolean {
  return !config.nearbyEnabled;
}

/**
 * Consulta QL sobre paradas con `ref` alrededor del punto.
 * Helper puro conservado por compatibilidad; la ruta /ubicacion ya no lo usa.
 */
export function buildOverpassQuery(
  latitude: number,
  longitude: number,
  radiusM: number
): string {
  const lat = latitude.toFixed(6);
  const lon = longitude.toFixed(6);
  const around = `around:${radiusM},${lat},${lon}`;
  return `[out:json][timeout:20];
(
  node["highway"="bus_stop"]["ref"](${around});
  node["public_transport"="platform"]["ref"](${around});
  way["highway"="bus_stop"]["ref"](${around});
  way["public_transport"="platform"]["ref"](${around});
);
out center;`;
}

/** Un `ref` de OSM puede traer varios operadores: "39 (dbus);4779 (TBH)". */
export function parseStopRefs(raw: unknown): string[] {
  if (typeof raw !== 'string') return [];
  const refs = (raw.match(/\d+/g) ?? []).map((value) =>
    value.replace(/^0+(?=\d)/, '')
  );
  return [...new Set(refs)];
}

export interface OverpassElement {
  type: string;
  id: number;
  lat?: number;
  lon?: number;
  center?: { lat: number; lon: number };
  tags?: Record<string, string>;
}

export interface NearbyStop {
  cod: string;
  desc: string;
  distanceM: number;
}

/** Distancia haversine en metros, sin dependencias. */
export function haversineMeters(
  lat1: number,
  lon1: number,
  lat2: number,
  lon2: number
): number {
  const rad = Math.PI / 180;
  const dLat = (lat2 - lat1) * rad;
  const dLon = (lon2 - lon1) * rad;
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(lat1 * rad) * Math.cos(lat2 * rad) * Math.sin(dLon / 2) ** 2;
  return 2 * 6_371_000 * Math.asin(Math.min(1, Math.sqrt(a)));
}

/**
 * Cruza elementos con el índice real de Dbus. El nombre mostrado es el del
 * catálogo; la distancia se calcula de la geometría de la fuente de
 * coordenadas. Dedup por código y orden por distancia.
 */
export function rankNearbyStops(
  elements: OverpassElement[],
  latitude: number,
  longitude: number,
  lookup: (cod: string) => { desc: string } | null,
  limit = 8
): NearbyStop[] {
  const byCode = new Map<string, NearbyStop>();
  for (const element of elements) {
    const point =
      element.lat !== undefined && element.lon !== undefined
        ? { lat: element.lat, lon: element.lon }
        : element.center;
    if (!point || !Number.isFinite(point.lat) || !Number.isFinite(point.lon)) {
      continue;
    }
    for (const cod of parseStopRefs(element.tags?.ref)) {
      const stop = lookup(cod);
      if (!stop) continue;
      const distanceM = haversineMeters(
        latitude,
        longitude,
        point.lat,
        point.lon
      );
      const previous = byCode.get(cod);
      if (!previous || distanceM < previous.distanceM) {
        byCode.set(cod, { cod, desc: stop.desc, distanceM });
      }
    }
  }
  return [...byCode.values()]
    .sort((a, b) => a.distanceM - b.distanceM || a.cod.localeCompare(b.cod))
    .slice(0, limit);
}

export class NearbyUnavailableError extends Error {}

/**
 * Filtra el índice local por distancia y devuelve paradas Dbus cercanas,
 * ordenadas por distancia. Sin fetch: el único error posible es que el
 * índice no se pueda cargar.
 */
export async function getNearbyStops(
  latitude: number,
  longitude: number
): Promise<NearbyStop[]> {
  if (isNearbyDisabled()) throw new NearbyUnavailableError('disabled');

  let coordinates: StopCoordinates;
  try {
    coordinates = loadStopCoordinates();
  } catch (error) {
    if (error instanceof StopCoordinatesError) {
      throw new NearbyUnavailableError(error.message);
    }
    throw error;
  }

  const radiusM = config.nearbyRadiusM;
  // Adaptación al helper puro: cada fila del índice se presenta como un
  // elemento con `ref` para reutilizar parseStopRefs + rankNearbyStops.
  const elements: OverpassElement[] = coordinates.stops
    .filter(
      (stop) =>
        haversineMeters(latitude, longitude, stop.lat, stop.lon) <= radiusM
    )
    .map((stop) => ({
      type: 'node',
      id: 0,
      lat: stop.lat,
      lon: stop.lon,
      tags: { ref: stop.cod },
    }));

  return rankNearbyStops(elements, latitude, longitude, (cod) =>
    getStopByCode(cod)
  );
}
