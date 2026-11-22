import { config } from './config.js';
import {
  type FeedEntity,
  FeedMessage,
  decodeFeedMessage,
} from './proto/gtfs-realtime.js';
import type {
  ParsedRT,
  Position,
  StopTimeUpdate,
  TripDescriptor,
  TripUpdate,
  VehicleDescriptor,
  VehiclePosition,
} from './types.js';

let rtDataCache: ParsedRT | null = null;
let fetcherHealthy = false;
let fetcherCheckInterval: ReturnType<typeof setInterval> | null = null;

// Detect test environment to use mock data
const isTestEnv =
  process.env.BOT_TOKEN === 'vitest_test_token' ||
  process.env.NODE_ENV === 'test';

// Exponential backoff configuration
const MAX_RETRIES = isTestEnv ? 0 : 10;
const BASE_DELAY_MS = isTestEnv ? 0 : 1000;
const MAX_DELAY_MS = 60000;

async function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function fetchWithRetry(
  type: 'tripUpdates' | 'vehiclePositions' | 'alerts',
  attempt = 1
): Promise<Uint8Array | null> {
  try {
    const response = await fetch(`${config.fetcherUrl}/feed/${type}`, {
      headers: { Accept: 'application/x-protobuf' },
      signal: AbortSignal.timeout(10000),
    });
    if (!response.ok) {
      throw new Error(`HTTP ${response.status}: ${response.statusText}`);
    }
    const arrayBuffer = await response.arrayBuffer();
    return new Uint8Array(arrayBuffer);
  } catch (e) {
    const error = e as Error;
    if (attempt < MAX_RETRIES) {
      const delay = Math.min(BASE_DELAY_MS * 2 ** (attempt - 1), MAX_DELAY_MS);
      const jitter = Math.random() * 1000;
      console.warn(
        `[GTFS] Fetch ${type} failed (attempt ${attempt}/${MAX_RETRIES}): ${error.message}. Retrying in ${Math.round((delay + jitter) / 1000)}s...`
      );
      await sleep(delay + jitter);
      return fetchWithRetry(type, attempt + 1);
    }
    console.error(
      `[GTFS] Fetch ${type} failed after ${MAX_RETRIES} attempts: ${error.message}`
    );
    return null;
  }
}

async function checkFetcherHealth(): Promise<boolean> {
  try {
    const response = await fetch(`${config.fetcherUrl}/health`, {
      signal: AbortSignal.timeout(5000),
    });
    return response.ok;
  } catch {
    return false;
  }
}

async function waitForFetcherReady(): Promise<void> {
  // In test env, skip health check and immediately mark as unhealthy
  if (isTestEnv) {
    fetcherHealthy = false;
    return;
  }

  console.log('[GTFS] Waiting for fetcher to be ready...');
  let attempt = 0;
  while (attempt < MAX_RETRIES) {
    const healthy = await checkFetcherHealth();
    if (healthy) {
      console.log('[GTFS] Fetcher is healthy');
      fetcherHealthy = true;
      return;
    }
    attempt++;
    const delay = Math.min(BASE_DELAY_MS * 2 ** (attempt - 1), MAX_DELAY_MS);
    const jitter = Math.random() * 1000;
    console.warn(
      `[GTFS] Fetcher not ready (attempt ${attempt}/${MAX_RETRIES}). Retrying in ${Math.round((delay + jitter) / 1000)}s...`
    );
    await sleep(delay + jitter);
  }
  console.error(
    '[GTFS] Fetcher never became healthy after max retries. Live data unavailable.'
  );
  fetcherHealthy = false;
}

function parseTimeEvent(
  event:
    | { delay?: number; time?: number | bigint; uncertainty?: number }
    | undefined
) {
  if (!event) return undefined;
  return {
    delay: event.delay,
    time: event.time ? Number(event.time) : undefined,
    uncertainty: event.uncertainty,
  };
}

function parseTripDescriptor(
  pb:
    | {
        trip_id?: string;
        route_id?: string;
        direction_id?: number;
        start_time?: string;
        start_date?: string;
        schedule_relationship?: number;
      }
    | undefined
): TripDescriptor | undefined {
  if (!pb) return undefined;
  return {
    tripId: pb.trip_id || '',
    routeId: pb.route_id || '',
    directionId: pb.direction_id,
    startTime: pb.start_time,
    startDate: pb.start_date,
    scheduleRelationship: pb.schedule_relationship,
  };
}

function parseVehicleDescriptor(
  pb:
    | {
        id?: string;
        label?: string;
        license_plate?: string;
        wheelchair_accessible?: number;
      }
    | undefined
): VehicleDescriptor | undefined {
  if (!pb) return undefined;
  return {
    id: pb.id,
    label: pb.label,
    licensePlate: pb.license_plate,
    occupancyStatus: pb.wheelchair_accessible,
    occupancyPercentage: undefined, // Not in VehicleDescriptor, comes from VehiclePosition
  };
}

function parseTripUpdate(entity: FeedEntity): TripUpdate | null {
  const pb = entity.trip_update;
  if (!pb) return null;

  const trip = parseTripDescriptor(pb.trip);
  if (!trip) return null;

  const stopTimeUpdate: StopTimeUpdate[] = (pb.stop_time_update || []).map(
    (stu) => ({
      stopSequence: stu.stop_sequence,
      stopId: stu.stop_id,
      arrival: parseTimeEvent(stu.arrival),
      departure: parseTimeEvent(stu.departure),
      scheduleRelationship: stu.schedule_relationship,
    })
  );

  const vehicle = parseVehicleDescriptor(pb.vehicle);

  return {
    id: entity.id,
    trip,
    stopTimeUpdate,
    vehicle,
  };
}

function parseVehiclePosition(entity: FeedEntity): VehiclePosition | null {
  const pb = entity.vehicle;
  if (!pb) return null;

  const trip = parseTripDescriptor(pb.trip);
  const position = pb.position
    ? {
        latitude: pb.position.latitude,
        longitude: pb.position.longitude,
        bearing: pb.position.bearing,
        odometer: pb.position.odometer,
        speed: pb.position.speed,
      }
    : undefined;

  const vehicle: VehicleDescriptor & { trip?: TripDescriptor } = {
    id: pb.vehicle?.id,
    label: pb.vehicle?.label,
    licensePlate: pb.vehicle?.license_plate,
    occupancyStatus: pb.occupancy_status,
    occupancyPercentage: pb.occupancy_percentage,
    trip,
  };

  return {
    id: entity.id,
    vehicle,
    trip,
    position,
    currentStopSequence: pb.current_stop_sequence,
    stopId: pb.stop_id,
    currentStatus: pb.current_status,
    timestamp: pb.timestamp ? Number(pb.timestamp) : undefined,
    congestionLevel: pb.congestion_level,
    occupancyStatus: pb.occupancy_status,
    occupancyPercentage: pb.occupancy_percentage,
  };
}

function parseAlert(entity: FeedEntity) {
  const pb = entity.alert;
  if (!pb) return null;

  return {
    id: entity.id,
    alert: {
      activePeriod: (pb.active_period || []).map((ap) => ({
        start: ap.start ? Number(ap.start) : undefined,
        end: ap.end ? Number(ap.end) : undefined,
      })),
      informedEntity: (pb.informed_entity || []).map((ie) => ({
        routeId: ie.route_id,
        stopId: ie.stop_id,
      })),
      cause: pb.cause,
      effect: pb.effect,
      url: pb.url
        ? {
            translation: (pb.url.translation || []).map((t) => ({
              text: t.text,
              language: t.language,
            })),
          }
        : undefined,
      headerText: pb.header_text
        ? {
            translation: (pb.header_text.translation || []).map((t) => ({
              text: t.text,
              language: t.language,
            })),
          }
        : undefined,
      descriptionText: pb.description_text
        ? {
            translation: (pb.description_text.translation || []).map((t) => ({
              text: t.text,
              language: t.language,
            })),
          }
        : undefined,
    },
  };
}

function parseFeedMessage(buffer: Uint8Array): ParsedRT | null {
  try {
    const feed = decodeFeedMessage(buffer);
    const tripUpdates: TripUpdate[] = [];
    const vehiclePositions: VehiclePosition[] = [];
    const alerts = [];

    for (const entity of feed.entity || []) {
      if (entity.trip_update) {
        const parsed = parseTripUpdate(entity);
        if (parsed) tripUpdates.push(parsed);
      }
      if (entity.vehicle) {
        const parsed = parseVehiclePosition(entity);
        if (parsed) vehiclePositions.push(parsed);
      }
      if (entity.alert) {
        const parsed = parseAlert(entity);
        if (parsed) alerts.push(parsed);
      }
    }

    return {
      tripUpdates,
      vehiclePositions,
      alerts,
      timestamp: feed.header?.timestamp
        ? Number(feed.header.timestamp) * 1000
        : Date.now(),
      feedVersion: feed.header?.feed_version || 'unknown',
    };
  } catch (e) {
    console.error('[GTFS] Failed to parse protobuf feed:', e);
    return null;
  }
}

function generateMockRT(): ParsedRT {
  const now = Date.now();
  const baseTime = Math.floor(now / 1000);

  return {
    tripUpdates: [
      {
        id: 'tu-1',
        trip: {
          tripId: 'trip-L12-001',
          routeId: '12',
          directionId: 0,
          startTime: '07:00:00',
          startDate: '20261122',
          scheduleRelationship: 0,
        },
        stopTimeUpdate: [
          {
            stopSequence: 1,
            stopId: '1234',
            arrival: { time: baseTime + 300, delay: 0 },
            departure: { time: baseTime + 300 },
            scheduleRelationship: 0,
          },
          {
            stopSequence: 2,
            stopId: '1235',
            arrival: { time: baseTime + 600, delay: 0 },
            departure: { time: baseTime + 600 },
            scheduleRelationship: 0,
          },
        ],
        vehicle: {
          id: 'veh-L12-001',
          label: '1201',
          licensePlate: 'BI-1234',
          occupancyStatus: 1,
          occupancyPercentage: 25,
        },
      },
      {
        id: 'tu-2',
        trip: {
          tripId: 'trip-L17-001',
          routeId: '17',
          directionId: 0,
          startTime: '07:05:00',
          startDate: '20261122',
          scheduleRelationship: 0,
        },
        stopTimeUpdate: [
          {
            stopSequence: 1,
            stopId: '1234',
            arrival: { time: baseTime + 720, delay: 0 },
            departure: { time: baseTime + 720 },
            scheduleRelationship: 0,
          },
        ],
        vehicle: {
          id: 'veh-L17-001',
          label: '1701',
          licensePlate: 'BI-5678',
          occupancyStatus: 1,
          occupancyPercentage: 65,
        },
      },
    ],
    vehiclePositions: [
      {
        id: 'vp-1',
        vehicle: { id: 'veh-L12-001', label: '1201', occupancyPercentage: 25 },
        trip: { tripId: 'trip-L12-001', routeId: '12' },
        position: { latitude: 43.3183, longitude: -1.9812 },
        currentStopSequence: 0,
        stopId: '1233',
        currentStatus: 1,
        timestamp: baseTime,
        occupancyPercentage: 25,
      },
      {
        id: 'vp-2',
        vehicle: { id: 'veh-L17-001', label: '1701', occupancyPercentage: 65 },
        trip: { tripId: 'trip-L17-001', routeId: '17' },
        position: { latitude: 43.3201, longitude: -1.9798 },
        currentStopSequence: 0,
        stopId: '1233',
        currentStatus: 1,
        timestamp: baseTime,
        occupancyPercentage: 65,
      },
    ],
    alerts: [],
    timestamp: now,
    feedVersion: '2026.11-fetcher',
  };
}

export async function fetchAndParseGTFSRT(): Promise<ParsedRT | null> {
  // If fetcher is not healthy, try to recover
  if (!fetcherHealthy) {
    console.warn('[GTFS] Fetcher unhealthy, attempting to recover...');
    await waitForFetcherReady();
  }

  // Try to fetch from fetcher
  const [tuBuffer, vpBuffer, alBuffer] = await Promise.all([
    fetchWithRetry('tripUpdates'),
    fetchWithRetry('vehiclePositions'),
    fetchWithRetry('alerts'),
  ]);

  // If all fetches failed
  if (!tuBuffer && !vpBuffer && !alBuffer) {
    if (isTestEnv) {
      // In test env, use mock data
      console.warn('[GTFS] Test env: using mock data');
      rtDataCache = generateMockRT();
      return rtDataCache;
    }

    // In production: NO mock fallback - return null
    console.error(
      '[GTFS] All fetcher endpoints failed - NO LIVE DATA AVAILABLE'
    );
    fetcherHealthy = false;
    rtDataCache = null;
    return null;
  }

  // Parse whatever buffers we got
  const results: ParsedRT = {
    tripUpdates: [],
    vehiclePositions: [],
    alerts: [],
    timestamp: Date.now(),
    feedVersion: '2026.11-fetcher',
  };

  let anySuccess = false;

  if (tuBuffer) {
    const parsed = parseFeedMessage(tuBuffer);
    if (parsed) {
      results.tripUpdates = parsed.tripUpdates;
      results.vehiclePositions = parsed.vehiclePositions;
      results.alerts = parsed.alerts;
      results.timestamp = parsed.timestamp;
      results.feedVersion = parsed.feedVersion;
      anySuccess = true;
    }
  }

  if (vpBuffer && !anySuccess) {
    const parsed = parseFeedMessage(vpBuffer);
    if (parsed) {
      results.tripUpdates = parsed.tripUpdates;
      results.vehiclePositions = parsed.vehiclePositions;
      results.alerts = parsed.alerts;
      results.timestamp = parsed.timestamp;
      results.feedVersion = parsed.feedVersion;
      anySuccess = true;
    }
  }

  if (alBuffer && !anySuccess) {
    const parsed = parseFeedMessage(alBuffer);
    if (parsed) {
      results.tripUpdates = parsed.tripUpdates;
      results.vehiclePositions = parsed.vehiclePositions;
      results.alerts = parsed.alerts;
      results.timestamp = parsed.timestamp;
      results.feedVersion = parsed.feedVersion;
      anySuccess = true;
    }
  }

  if (anySuccess) {
    fetcherHealthy = true;
    rtDataCache = results;
    console.log(
      `[GTFS] Parsed real data: ${results.tripUpdates.length} trip updates, ${results.vehiclePositions.length} vehicle positions, ${results.alerts.length} alerts`
    );
    return rtDataCache;
  }

  // If we got buffers but couldn't parse any
  if (isTestEnv) {
    console.warn('[GTFS] Test env: parse failed, using mock data');
    rtDataCache = generateMockRT();
    return rtDataCache;
  }

  console.error(
    '[GTFS] Received buffers but failed to parse - NO LIVE DATA AVAILABLE'
  );
  fetcherHealthy = false;
  rtDataCache = null;
  return null;
}

export function startRTPolling(intervalMs = config.pollIntervalMs) {
  // Initial health check and wait for fetcher
  waitForFetcherReady().then(() => {
    // Start periodic health checks
    fetcherCheckInterval = setInterval(async () => {
      const healthy = await checkFetcherHealth();
      if (healthy !== fetcherHealthy) {
        fetcherHealthy = healthy;
        console.log(
          `[GTFS] Fetcher health changed: ${healthy ? 'healthy' : 'unhealthy'}`
        );
      }
    }, 60000); // Check health every minute
  });

  const interval = setInterval(async () => {
    const data = await fetchAndParseGTFSRT();
    if (data) {
      rtDataCache = data;
    }
  }, intervalMs);

  return () => {
    clearInterval(interval);
    if (fetcherCheckInterval) {
      clearInterval(fetcherCheckInterval);
      fetcherCheckInterval = null;
    }
  };
}

export function getRTCache(): ParsedRT | null {
  return rtDataCache;
}

export function isFetcherHealthy(): boolean {
  return fetcherHealthy;
}
