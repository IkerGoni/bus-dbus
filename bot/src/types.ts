// Tipos GTFS-RT (compatibles con src/lib/types.ts del proyecto PWA)

export interface GTFSEntity {
  id: string;
}

export interface TripDescriptor {
  tripId: string;
  routeId: string;
  directionId?: number;
  startTime?: string;
  startDate?: string;
  scheduleRelationship?: number;
}

export interface StopTimeUpdate {
  stopSequence?: number;
  stopId?: string;
  arrival?: Time;
  departure?: Time;
  scheduleRelationship?: number;
}

export interface Time {
  delay?: number;
  time?: number | string;
  uncertainty?: number;
}

export interface VehicleDescriptor {
  id?: string;
  label?: string;
  licensePlate?: string;
  occupancyStatus?: number;
  occupancyPercentage?: number;
}

export interface Position {
  latitude?: number;
  longitude?: number;
  bearing?: number;
  odometer?: number;
  speed?: number;
}

export interface TripUpdate extends GTFSEntity {
  trip?: TripDescriptor;
  stopTimeUpdate?: StopTimeUpdate[];
  vehicle?: VehicleDescriptor;
}

export interface VehiclePosition extends GTFSEntity {
  vehicle?: VehicleDescriptor & { trip?: TripDescriptor };
  trip?: TripDescriptor;
  position?: Position;
  currentStopSequence?: number;
  stopId?: string;
  currentStatus?: number;
  timestamp?: number;
  congestionLevel?: number;
  occupancyStatus?: number;
  occupancyPercentage?: number;
}

export interface Alert extends GTFSEntity {
  alert?: {
    activePeriod?: { start?: number; end?: number }[];
    informedEntity?: { routeId?: string; stopId?: string }[];
    cause?: number;
    effect?: number;
    url?: { translation: { text: string; language: string }[] };
    headerText?: { translation: { text: string; language: string }[] };
    descriptionText?: { translation: { text: string; language: string }[] };
  };
}

export interface ParsedRT {
  tripUpdates: TripUpdate[];
  vehiclePositions: VehiclePosition[];
  alerts: Alert[];
  timestamp: number;
  feedVersion: string;
}

// Tipos para alertas de usuario (bot)
export interface UserAlert {
  id: number;
  chatId: number;
  stopId: string;
  stopName: string;
  lineId: string;
  lineName: string;
  destination: string;
  minutesAhead: number;
  active: boolean;
  createdAt: number;
}

export interface NextArrivalResult {
  arrivalTime: number;
  trip: TripDescriptor;
  stopTime: StopTimeUpdate;
  vehiclePosition?: VehiclePosition;
}
