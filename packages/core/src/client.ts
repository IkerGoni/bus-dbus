/**
 * Cliente de la API JSON pública de Dbus (SSIIMovilWSv2).
 *
 * Isomorfo: no importa nada de Node ni del DOM. `fetch`, `baseUrl`, `language`
 * y el reloj se inyectan, de modo que el bot (Node) y la PWA (navegador) usan
 * el mismo código, y los tests corren offline con `fetch` falso.
 *
 * Endpoint principal:
 *   GET /tiemposParada?codParada={codigo_publico}&idioma={eu|es|en|fr}
 */
import { linesFromStopTimes, mapTiemposParada } from './arrival.js';
import { parseStopTimes } from './parse.js';
import type {
  Arrival,
  CacheEntry,
  DbusParada,
  DbusStopTimes,
  Language,
  Line,
  Logger,
} from './types.js';

export interface DbusClientOptions {
  baseUrl: string;
  language?: Language;
  /** Punto de inyección para tests y para entornos sin `fetch` global. */
  fetch?: typeof fetch;
  /** TTL de la caché de paradas en ms. */
  ttlMs?: number;
  /** Reloj inyectable. */
  now?: () => number;
  timeoutMs?: number;
  retryDelayMs?: number;
  maxCachedStops?: number;
  logger?: Logger;
  /**
   * Si se indica, `getCachedStopCodes()` solo devuelve las paradas pedidas
   * (`getStopTimes`, `getArrivals`, `getStopLines`) en los últimos `maxIdleMs`;
   * `refreshStop` no cuenta como uso. Sin él, todas las de la caché (como antes).
   */
  maxIdleMs?: number;
}

export interface GetArrivalsOptions {
  lineId?: string;
  forceRefresh?: boolean;
}

export interface DbusClient {
  getStopTimes(
    stopCode: string,
    forceRefresh?: boolean
  ): Promise<DbusStopTimes | null>;
  getArrivals(
    stopCode: string,
    options?: GetArrivalsOptions
  ): Promise<Arrival[]>;
  getStopLines(stopCode: string): Promise<Line[]>;
  /** Pide la parada a Dbus ignorando la caché, sin contarla como uso (refresco en segundo plano). */
  refreshStop(stopCode: string): Promise<DbusStopTimes | null>;
  fetchPopularStops(): Promise<DbusParada[]>;
  getStopName(code: string): string;
  getCachedStopCodes(): string[];
  invalidateStop(code: string): void;
  clearCache(): void;
  getCacheStats(): { stops: number; popularStops: number };
  getDataSource(): string;
  /** epoch ms de la última respuesta válida de la API, `null` si no hubo. */
  getLastSync(): number | null;
}

const DEFAULT_TTL_MS = 15_000;
const DEFAULT_TIMEOUT_MS = 10_000;
const DEFAULT_RETRY_DELAY_MS = 300;
const DEFAULT_MAX_CACHED_STOPS = 200;
const POPULAR_TTL_MS = 12 * 60 * 60 * 1000;
const SEED_ITINERARIES = ['1280', '1281', '450'];

/**
 * Evicta las entradas más antiguas (orden de inserción) cuando la caché
 * supera el límite. El refresco en segundo plano visita todas las paradas
 * vistas, así que sin tope el mapa crece sin control en sesiones largas.
 */
export function evictOldestEntries<T>(
  cache: Map<string, CacheEntry<T>>,
  maxEntries: number
): number {
  let removed = 0;
  while (cache.size > maxEntries) {
    const oldest = cache.keys().next().value;
    if (oldest === undefined) break;
    cache.delete(oldest);
    removed++;
  }
  return removed;
}

function trimBaseUrl(baseUrl: string): string {
  return baseUrl.replace(/\/+$/, '');
}

export function createDbusClient(options: DbusClientOptions): DbusClient {
  const baseUrl = trimBaseUrl(options.baseUrl);
  const language = options.language ?? 'es';
  const ttlMs = options.ttlMs ?? DEFAULT_TTL_MS;
  const timeoutMs = options.timeoutMs ?? DEFAULT_TIMEOUT_MS;
  const retryDelayMs = options.retryDelayMs ?? DEFAULT_RETRY_DELAY_MS;
  const maxCachedStops = options.maxCachedStops ?? DEFAULT_MAX_CACHED_STOPS;
  const now = options.now ?? Date.now;
  const maxIdleMs = options.maxIdleMs;
  const log = options.logger ?? console;
  const fetchImpl: typeof fetch =
    options.fetch ?? ((input, init) => globalThis.fetch(input, init));

  const stopCache = new Map<string, CacheEntry<DbusStopTimes>>();
  const inflight = new Map<string, Promise<DbusStopTimes | null>>();
  let popularCache: CacheEntry<DbusParada[]> | null = null;
  let lastSync: number | null = null;
  /** Último uso de cada parada (solo con `maxIdleMs`). */
  const lastUse = new Map<string, number>();

  async function getJson(path: string): Promise<unknown | null> {
    const url = `${baseUrl}${path}`;
    for (let attempt = 1; attempt <= 2; attempt++) {
      try {
        const response = await fetchImpl(url, {
          headers: { Accept: 'application/json' },
          signal: AbortSignal.timeout(timeoutMs),
        });
        if (!response.ok) {
          throw new Error(`HTTP ${response.status}`);
        }
        return (await response.json()) as unknown;
      } catch (error) {
        const message = error instanceof Error ? error.message : String(error);
        if (attempt === 2) {
          log.error(`[Dbus] Request failed (${path}): ${message}`);
          return null;
        }
        await new Promise((resolve) => setTimeout(resolve, retryDelayMs));
      }
    }
    return null;
  }

  async function getStopTimes(
    codParada: string,
    forceRefresh = false
  ): Promise<DbusStopTimes | null> {
    const code = String(codParada ?? '').trim();
    if (!/^\d+$/.test(code)) return null;
    if (maxIdleMs !== undefined) lastUse.set(code, now());
    return loadStopTimes(code, forceRefresh);
  }

  async function refreshStop(stopCode: string): Promise<DbusStopTimes | null> {
    const code = String(stopCode ?? '').trim();
    if (!/^\d+$/.test(code)) return null;
    return loadStopTimes(code, true);
  }

  async function loadStopTimes(
    code: string,
    forceRefresh: boolean
  ): Promise<DbusStopTimes | null> {
    const cached = stopCache.get(code);
    if (!forceRefresh && cached && now() - cached.ts < ttlMs) {
      return cached.data;
    }

    const pending = inflight.get(code);
    if (pending) return pending;

    const request = (async () => {
      const raw = await getJson(
        `/tiemposParada?codParada=${encodeURIComponent(code)}&idioma=${language}`
      );
      const parsed = parseStopTimes(raw);
      if (parsed) {
        const ts = now();
        stopCache.set(code, { data: parsed, ts });
        lastSync = ts;
        evictOldestEntries(stopCache, maxCachedStops);
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

  async function getArrivals(
    stopCode: string,
    arrivalOptions: GetArrivalsOptions = {}
  ): Promise<Arrival[]> {
    const times = await getStopTimes(stopCode, arrivalOptions.forceRefresh);
    return mapTiemposParada(times, {
      stopCode,
      lineId: arrivalOptions.lineId,
      stopName: getStopName(stopCode),
      now: now(),
    });
  }

  async function getStopLines(stopCode: string): Promise<Line[]> {
    const times = await getStopTimes(stopCode);
    return linesFromStopTimes(times);
  }

  /**
   * Catálogo de paradas "populares" reales, construido desde los itinerarios
   * de la API (no hardcodeado como datos de relleno).
   */
  async function fetchPopularStops(): Promise<DbusParada[]> {
    if (popularCache && now() - popularCache.ts < POPULAR_TTL_MS) {
      return popularCache.data;
    }

    // Los tres itinerarios semilla son independientes: pedirlos en paralelo
    // reduce la carga en frío de ~3 latencias a 1.
    const responses = await Promise.all(
      SEED_ITINERARIES.map((itinerarioId) =>
        getJson(`/paradasItinerario?idItinerario=${itinerarioId}`)
      )
    );
    const collected = new Map<string, DbusParada>();
    for (const raw of responses) {
      if (raw == null) continue;
      const list =
        raw !== null && typeof raw === 'object'
          ? (raw as { lista?: unknown }).lista
          : undefined;
      for (const item of Array.isArray(list) ? list : []) {
        const parsed = parseStopTimes({ estado: 'DATOS', parada: item });
        const parada = parsed?.parada ?? null;
        if (parada) collected.set(parada.cod, parada);
      }
    }

    const data = [...collected.values()].slice(0, 24);
    // Si la API falla, devolver catálogo vacío: mejor decirlo que mostrar
    // paradas inventadas. El error se hace visible en los handlers.
    popularCache = { data, ts: data.length ? now() : 0 };
    return data;
  }

  function getStopName(code: string): string {
    const cached = stopCache.get(code)?.data.parada?.desc;
    if (cached) return cached;
    const known = popularCache?.data.find((p) => p.cod === code);
    if (known?.desc) return known.desc;
    return `Parada ${code}`;
  }

  function getCachedStopCodes(): string[] {
    const codes = [...stopCache.keys()];
    if (maxIdleMs === undefined) return codes;
    const t = now();
    for (const [code, used] of lastUse) {
      if (t - used > maxIdleMs) lastUse.delete(code);
    }
    return codes.filter((code) => lastUse.has(code));
  }

  function invalidateStop(code: string): void {
    stopCache.delete(code.trim());
  }

  function clearCache(): void {
    stopCache.clear();
    popularCache = null;
  }

  function getCacheStats(): { stops: number; popularStops: number } {
    return {
      stops: stopCache.size,
      popularStops: popularCache?.data.length ?? 0,
    };
  }

  return {
    getStopTimes,
    getArrivals,
    getStopLines,
    refreshStop,
    fetchPopularStops,
    getStopName,
    getCachedStopCodes,
    invalidateStop,
    clearCache,
    getCacheStats,
    getDataSource: () => baseUrl,
    getLastSync: () => lastSync,
  };
}
