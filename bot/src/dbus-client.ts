/**
 * Cliente de la API JSON pública de Dbus (SSIIMovilWSv2).
 *
 * Es la misma fuente que usa la aplicación oficial. Sustituye al antiguo
 * pipeline GTFS-RT (los feeds .pb de opendata.euskadi.eus redirigen a S3
 * y devuelven 403, por lo que nunca llegaban datos reales al bot).
 *
 * Endpoint principal:
 *   GET /tiemposParada?codParada={codigo_publico}&idioma={eu|es|en|fr}
 *
 * `codParada` es el código visible en la marquesina (p. ej. 1 = Boulevard 9),
 * NO el id interno de la parada.
 */
import { config } from './config.js';

export interface DbusLinea {
  id: string;
  cod: string;
  nombre: string;
  empresa?: string;
  color?: number;
}

export interface DbusItinerario {
  id: string;
  cod: string;
  destino: string;
  nombre: string;
  linea: DbusLinea;
}

export interface DbusTiempo {
  itinerario: DbusItinerario;
  creacion: string;
  /** Minutos hasta la llegada (valor calculado por Dbus, ya incluye GPS si aplica). */
  minutos: number;
  /** true = cabecera de línea. */
  cabecera: boolean;
  /** 1 = estimación GPS en tiempo real, 2 = horario programado. */
  tipo: number;
  /** Hora absoluta prevista, "HH:MM". */
  hora: string;
}

export interface DbusParada {
  id: string;
  cod: string;
  desc: string;
}

export interface DbusStopTimes {
  estado: string;
  parada: DbusParada | null;
  tiempos: DbusTiempo[];
  /** Marca de tiempo del response ("DD/MM/YYYY HH:MM:SS"). */
  creacion: string | null;
}

interface CacheEntry<T> {
  data: T;
  ts: number;
}

const stopCache = new Map<string, CacheEntry<DbusStopTimes>>();
const inflight = new Map<string, Promise<DbusStopTimes | null>>();
let popularCache: CacheEntry<DbusParada[]> | null = null;

const POPULAR_TTL_MS = 12 * 60 * 60 * 1000;
const REQUEST_TIMEOUT_MS = 10_000;
const SEED_ITINERARIES = ['1280', '1281', '450'];

/** Fallback solo si la API no responde al construir el catálogo. */
const FALLBACK_STOPS: DbusParada[] = [
  { id: '64', cod: '1', desc: 'Boulevard 9' },
  { id: '140', cod: '2', desc: 'Urbieta 12' },
  { id: '142', cod: '3', desc: 'Urbieta 58' },
  { id: '144', cod: '4', desc: 'Antso Jakituna / Sancho Sabio 12' },
  { id: '146', cod: '5', desc: 'Antso Jakituna / Sancho Sabio 28' },
  { id: '148', cod: '6', desc: 'Madrid 14' },
];

// --- normalizadores tolerantes a que la API devuelva null en cualquier campo ---

function obj(value: unknown): Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : {};
}

function str(value: unknown): string {
  if (typeof value === 'string') return value;
  if (typeof value === 'number' || typeof value === 'bigint')
    return String(value);
  return '';
}

function num(value: unknown): number {
  const parsed = typeof value === 'number' ? value : Number(value);
  return Number.isFinite(parsed) ? parsed : 0;
}

function arr(value: unknown): unknown[] {
  return Array.isArray(value) ? value : [];
}

function normalizeLinea(value: unknown): DbusLinea {
  const raw = obj(value);
  return {
    id: str(raw.id),
    cod: str(raw.cod),
    nombre: str(raw.nombre),
    empresa: raw.empresa == null ? undefined : str(raw.empresa),
    color: raw.color == null ? undefined : num(raw.color),
  };
}

function normalizeItinerario(value: unknown): DbusItinerario {
  const raw = obj(value);
  return {
    id: str(raw.id),
    cod: str(raw.cod),
    destino: str(raw.destino),
    nombre: str(raw.nombre),
    linea: normalizeLinea(raw.linea),
  };
}

function normalizeParada(value: unknown): DbusParada | null {
  if (value == null) return null;
  const raw = obj(value);
  const cod = str(raw.cod);
  if (!cod) return null;
  return { id: str(raw.id), cod, desc: str(raw.desc).trim() };
}

function normalizeTiempo(value: unknown): DbusTiempo | null {
  if (value == null) return null;
  const raw = obj(value);
  const itinerario = normalizeItinerario(raw.itinerario);
  if (!itinerario.id && !itinerario.linea.cod) return null;
  return {
    itinerario,
    creacion: str(raw.creacion),
    minutos: num(raw.minutos),
    cabecera: Boolean(raw.cabecera),
    tipo: num(raw.tipo),
    hora: str(raw.hora),
  };
}

function normalizeStopTimes(value: unknown): DbusStopTimes | null {
  if (value == null || typeof value !== 'object') return null;
  const raw = obj(value);
  const estado = str(raw.estado);
  if (!estado) return null;

  const tiempos = arr(raw.tiempos)
    .map(normalizeTiempo)
    .filter((t): t is DbusTiempo => t !== null);

  return {
    estado,
    parada: normalizeParada(raw.parada),
    tiempos,
    creacion: raw.creacion == null ? null : str(raw.creacion),
  };
}

async function getJson(path: string): Promise<unknown | null> {
  const url = `${config.dbusBaseUrl}${path}`;
  try {
    const response = await fetch(url, {
      headers: { Accept: 'application/json' },
      signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
    });
    if (!response.ok) {
      console.error(`[Dbus] HTTP ${response.status} for ${path}`);
      return null;
    }
    return (await response.json()) as unknown;
  } catch (error) {
    console.error(
      `[Dbus] Request failed (${path}): ${error instanceof Error ? error.message : String(error)}`
    );
    return null;
  }
}

/**
 * Devuelve los tiempos de llegada de una parada. Datos reales, con caché
 * de corta duración y deduplicación de peticiones concurrentes.
 */
export async function fetchStopTimes(
  codParada: string,
  forceRefresh = false
): Promise<DbusStopTimes | null> {
  const code = str(codParada).trim();
  if (!/^\d+$/.test(code)) return null;

  const cached = stopCache.get(code);
  if (!forceRefresh && cached && Date.now() - cached.ts < config.cacheTtlMs) {
    return cached.data;
  }

  const pending = inflight.get(code);
  if (pending) return pending;

  const request = (async () => {
    const raw = await getJson(
      `/tiemposParada?codParada=${encodeURIComponent(code)}&idioma=${config.language}`
    );
    const parsed = normalizeStopTimes(raw);
    if (parsed) {
      stopCache.set(code, { data: parsed, ts: Date.now() });
    }
    return parsed;
  })();

  inflight.set(code, request);
  try {
    return await request;
  } finally {
    inflight.delete(code);
  }
}

/**
 * Catálogo de paradas "populares" reales, construido desde los itinerarios
 * de la API (no hardcodeado como datos de relleno).
 */
export async function fetchPopularStops(): Promise<DbusParada[]> {
  if (popularCache && Date.now() - popularCache.ts < POPULAR_TTL_MS) {
    return popularCache.data;
  }

  const collected = new Map<string, DbusParada>();
  for (const itinerarioId of SEED_ITINERARIES) {
    const raw = await getJson(
      `/paradasItinerario?idItinerario=${itinerarioId}`
    );
    if (raw == null) continue;
    const rawObj = obj(raw);
    for (const item of arr(rawObj.lista)) {
      const parada = normalizeParada(item);
      if (parada) collected.set(parada.cod, parada);
    }
  }

  const stops = [...collected.values()].slice(0, 24);
  const data = stops.length ? stops : FALLBACK_STOPS;
  popularCache = { data, ts: Date.now() };
  return data;
}

export function getStopName(code: string): string {
  const cached = stopCache.get(code)?.data.parada?.desc;
  if (cached) return cached;
  const known = popularCache?.data.find((p) => p.cod === code);
  if (known?.desc) return known.desc;
  return `Parada ${code}`;
}

export function getCachedStopCodes(): string[] {
  return [...stopCache.keys()];
}

export function clearCache(): void {
  stopCache.clear();
  popularCache = null;
}

/** Útil para diagnóstico: estado de la conexión con la API de Dbus. */
export function getDataSource(): string {
  return config.dbusBaseUrl;
}
