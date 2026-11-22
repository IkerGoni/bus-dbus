import { getStopByCode } from './catalog.js';
/**
 * Paradas cercanas con coordenadas efímeras.
 *
 * Fuentes reales y read-only:
 *  - OpenStreetMap / Overpass: posición de las paradas y su `ref`.
 *  - Catálogo Dbus: código y nombre canónicos de la parada.
 * El ref de OSM se cruza con el catálogo para no inventar paradas. Las
 * coordenadas del usuario se usan en memoria durante la petición y NO se
 * guardan en SQLite, sesión, ni logs.
 *
 * Si Overpass falla, el feature se desactiva temporalmente (circuit breaker) y
 * se dice al usuario; nunca se devuelven paradas de relleno.
 */
import { config } from './config.js';

const DISABLE_AFTER_FAILURES = 2;
const DISABLE_MINUTES = 15;

export const NEARBY_DISABLED_TEXT =
  '⚠️ El servicio de paradas cercanas está desactivado temporalmente ' +
  '(OpenStreetMap/Overpass no responde). Vuelve a intentarlo más tarde; ' +
  'mientras tanto usa /buscar.';

export const NEARBY_UNAVAILABLE_TEXT =
  '⚠️ Ahora mismo no puedo consultar paradas cercanas ' +
  '(OpenStreetMap/Overpass no disponible). No muestro paradas inventadas. ' +
  'Prueba de nuevo en unos minutos o usa /buscar.';

let consecutiveFailures = 0;
let disabledUntil = 0;

export function isNearbyDisabled(): boolean {
  return !config.nearbyEnabled || Date.now() < disabledUntil;
}

function noteSuccess(): void {
  consecutiveFailures = 0;
}

function noteFailure(): void {
  consecutiveFailures++;
  if (consecutiveFailures >= DISABLE_AFTER_FAILURES) {
    disabledUntil = Date.now() + DISABLE_MINUTES * 60_000;
    consecutiveFailures = 0;
  }
}

/** Consulta QL sobre paradas con `ref` alrededor del punto. */
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
 * Cruza elementos de Overpass con el índice real de Dbus. El nombre mostrado
 * es el del catálogo Dbus; la distancia se calcula de la geometría de OSM.
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

/** Consulta Overpass y devuelve paradas Dbus cercanas, ordenadas por distancia. */
export async function getNearbyStops(
  latitude: number,
  longitude: number
): Promise<NearbyStop[]> {
  if (isNearbyDisabled()) throw new NearbyUnavailableError('disabled');
  const query = buildOverpassQuery(latitude, longitude, config.nearbyRadiusM);
  const url = `${config.overpassUrl}?${new URLSearchParams({ data: query })}`;
  let response: Response;
  try {
    response = await fetch(url, {
      headers: {
        Accept: 'application/json',
        'User-Agent': 'dbus-telegram-bot/nearby (read-only)',
      },
      signal: AbortSignal.timeout(config.nearbyTimeoutMs),
    });
  } catch (error) {
    noteFailure();
    // No incluir la URL: lleva las coordenadas del usuario.
    throw new NearbyUnavailableError(
      error instanceof Error ? error.name : 'unknown'
    );
  }
  if (!response.ok) {
    noteFailure();
    throw new NearbyUnavailableError(`HTTP ${response.status}`);
  }
  let body: unknown;
  try {
    body = await response.json();
  } catch (error) {
    noteFailure();
    throw new NearbyUnavailableError(
      `JSON: ${error instanceof Error ? error.name : 'unknown'}`
    );
  }
  const elements = (body as { elements?: unknown })?.elements;
  if (!Array.isArray(elements)) {
    noteFailure();
    throw new NearbyUnavailableError('respuesta sin elements');
  }
  noteSuccess();
  return rankNearbyStops(
    elements as OverpassElement[],
    latitude,
    longitude,
    (cod) => getStopByCode(cod)
  );
}
