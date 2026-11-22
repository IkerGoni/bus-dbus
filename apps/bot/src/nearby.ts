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

/** Punto real del índice local de coordenadas (sin elementos Overpass falsos). */
export interface StopPoint {
  cod: string;
  lat: number;
  lon: number;
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
 * Cruza puntos del índice local con el catálogo real de Dbus. El nombre
 * mostrado es el del catálogo; la distancia sale de la geometría del índice.
 * Dedup por código y orden por distancia.
 */
export function rankNearbyStops(
  points: StopPoint[],
  latitude: number,
  longitude: number,
  lookup: (cod: string) => { desc: string } | null,
  limit = 8
): NearbyStop[] {
  const byCode = new Map<string, NearbyStop>();
  for (const point of points) {
    if (!Number.isFinite(point.lat) || !Number.isFinite(point.lon)) continue;
    const stop = lookup(point.cod);
    if (!stop) continue;
    const distanceM = haversineMeters(
      latitude,
      longitude,
      point.lat,
      point.lon
    );
    const previous = byCode.get(point.cod);
    if (!previous || distanceM < previous.distanceM) {
      byCode.set(point.cod, { cod: point.cod, desc: stop.desc, distanceM });
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
  const points: StopPoint[] = coordinates.stops
    .filter(
      (stop) =>
        haversineMeters(latitude, longitude, stop.lat, stop.lon) <= radiusM
    )
    .map((stop) => ({ cod: stop.cod, lat: stop.lat, lon: stop.lon }));

  return rankNearbyStops(points, latitude, longitude, (cod) =>
    getStopByCode(cod)
  );
}
