import { getRTCache } from './gtfs-client.js';
import type {
  NextArrivalResult,
  ParsedRT,
  StopTimeUpdate,
  TripDescriptor,
  VehiclePosition,
} from './types.js';

function parseArrivalTime(time: number | string | undefined): number | null {
  if (time === undefined) return null;
  if (typeof time === 'number') return time * 1000; // epoch seconds to ms
  const parsed = new Date(time).getTime();
  return Number.isNaN(parsed) ? null : parsed;
}

export function findNextArrival(
  rt: ParsedRT,
  stopId: string,
  lineId: string
): NextArrivalResult | null {
  const relevant = rt.tripUpdates?.filter(
    (tu) =>
      tu.trip?.routeId === lineId &&
      tu.stopTimeUpdate?.some((stu) => stu.stopId === stopId)
  );

  if (!relevant?.length) return null;

  let earliest: NextArrivalResult | null = null;

  for (const tu of relevant) {
    for (const stu of tu.stopTimeUpdate || []) {
      if (stu.stopId === stopId) {
        const arrivalMs = parseArrivalTime(stu.arrival?.time);
        if (arrivalMs && arrivalMs > Date.now()) {
          if (!earliest || arrivalMs < earliest.arrivalTime) {
            // Buscar vehicle position para este trip
            const vp = rt.vehiclePositions.find(
              (v) => v.vehicle?.trip?.tripId === tu.trip?.tripId
            );

            earliest = {
              arrivalTime: arrivalMs,
              trip: tu.trip!,
              stopTime: stu,
              vehiclePosition: vp,
            };
          }
        }
      }
    }
  }

  return earliest;
}

export function findAllNextArrivals(
  rt: ParsedRT,
  stopId: string
): Map<string, NextArrivalResult> {
  const results = new Map<string, NextArrivalResult>();

  for (const tu of rt.tripUpdates || []) {
    const lineId = tu.trip?.routeId;
    if (!lineId) continue;

    const arrival = findNextArrival(rt, stopId, lineId);
    if (arrival) {
      const existing = results.get(lineId);
      if (!existing || arrival.arrivalTime < existing.arrivalTime) {
        results.set(lineId, arrival);
      }
    }
  }

  return results;
}

export function getCachedArrivals(
  stopId: string
): Map<string, NextArrivalResult>;
export function getCachedArrivals(
  stopId: string,
  lineId: string
): NextArrivalResult | null;
export function getCachedArrivals(
  stopId: string,
  lineId?: string
): Map<string, NextArrivalResult> | NextArrivalResult | null {
  const rt = getRTCache();
  if (!rt) return lineId ? null : new Map();

  return lineId
    ? findNextArrival(rt, stopId, lineId)
    : findAllNextArrivals(rt, stopId);
}

export function formatArrival(arrival: NextArrivalResult): string {
  const mins = Math.max(
    0,
    Math.round((arrival.arrivalTime - Date.now()) / 1000 / 60)
  );
  const line = arrival.trip.routeId;
  const destination = arrival.trip.tripId; // o trip_headsign si está disponible
  const vehicle = arrival.vehiclePosition?.vehicle?.label || '';
  const occupancy = arrival.vehiclePosition?.occupancyPercentage;

  let msg = `🚌 **L${line}** → ${destination}`;
  if (vehicle) msg += ` (${vehicle})`;
  msg += `\n⏱ Llega en **${mins} min**`;
  if (occupancy !== undefined) {
    const occLabel = occupancy < 30 ? '🟢' : occupancy < 70 ? '🟡' : '🔴';
    msg += ` ${occLabel} ${occupancy}%`;
  }
  return msg;
}
