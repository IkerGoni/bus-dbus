import { config } from './config.js';
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

async function fetchFromFetcher(
  type: 'tripUpdates' | 'vehiclePositions' | 'alerts'
): Promise<Uint8Array | null> {
  try {
    const response = await fetch(`${config.fetcherUrl}/feed/${type}`, {
      headers: { Accept: 'application/x-protobuf' },
    });
    if (!response.ok) return null;
    const arrayBuffer = await response.arrayBuffer();
    return new Uint8Array(arrayBuffer);
  } catch (e) {
    console.error(`Fetch error for ${type}:`, e);
    return null;
  }
}

export async function fetchAndParseGTFSRT(): Promise<ParsedRT | null> {
  // Try to fetch from fetcher
  const [tuBuffer, vpBuffer, alBuffer] = await Promise.all([
    fetchFromFetcher('tripUpdates'),
    fetchFromFetcher('vehiclePositions'),
    fetchFromFetcher('alerts'),
  ]);

  // If fetcher has no data, generate realistic mock data
  if (!tuBuffer || !vpBuffer || !alBuffer || tuBuffer.length < 100) {
    console.warn('Fetcher unavailable or empty, using mock data');
    rtDataCache = generateMockRT();
    return rtDataCache;
  }

  // TODO: Parse protobuf buffers with protobufjs when real data available
  // For now, return mock data with real timestamps
  rtDataCache = generateMockRT();
  return rtDataCache;
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

export function startRTPolling(intervalMs = config.pollIntervalMs) {
  const interval = setInterval(async () => {
    const data = await fetchAndParseGTFSRT();
    if (data) {
      rtDataCache = data;
    }
  }, intervalMs);

  return () => clearInterval(interval);
}

export function getRTCache(): ParsedRT | null {
  return rtDataCache;
}

// Initialize with mock data
rtDataCache = generateMockRT();
