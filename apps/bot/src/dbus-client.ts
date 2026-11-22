/**
 * Cliente de la API JSON pública de Dbus (SSIIMovilWSv2).
 *
 * Adaptador del bot sobre `@dbus/core`: la lógica de red, caché, reintentos y
 * normalización vive en el paquete compartido. Aquí solo se inyecta la
 * configuración del bot para mantener la API síncrona que usan los handlers.
 */
import { createDbusClient } from '@dbus/core';
import { config } from './config.js';

export type {
  CacheEntry,
  DbusItinerario,
  DbusLinea,
  DbusParada,
  DbusStopTimes,
  DbusTiempo,
} from '@dbus/core';
export { evictOldestEntries } from '@dbus/core';

/**
 * El refresco en segundo plano solo cubre las paradas que alguien ha mirado en
 * estos 2 min (más las de avisos activos, en index.ts). Antes refrescaba toda
 * parada consultada alguna vez, para siempre: hasta 160 peticiones/min (H1).
 */
export const RECENT_STOP_MS = 2 * 60_000;

const client = createDbusClient({
  baseUrl: config.dbusBaseUrl,
  language: config.language,
  ttlMs: config.cacheTtlMs,
  timeoutMs: 10_000,
  retryDelayMs: 300,
  maxCachedStops: 200,
  maxIdleMs: RECENT_STOP_MS,
});

export const fetchStopTimes: (
  codParada: string,
  forceRefresh?: boolean
) => ReturnType<typeof client.getStopTimes> = client.getStopTimes;
export const fetchPopularStops = client.fetchPopularStops;
export const getStopName = client.getStopName;
/** Paradas pedidas por algún usuario en los últimos `RECENT_STOP_MS`. */
export const getCachedStopCodes = client.getCachedStopCodes;
/** Refresco en segundo plano: pide a Dbus sin contar la parada como vista. */
export const refreshStopTimes = client.refreshStop;
export const invalidateStop = client.invalidateStop;
export const clearCache = client.clearCache;
export const getDataSource = client.getDataSource;
export const getCacheStats = client.getCacheStats;
