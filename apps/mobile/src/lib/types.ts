export interface GTFSEntity {
  id: string
}

export interface TripUpdate extends GTFSEntity {
  trip?: TripDescriptor
  stopTimeUpdate?: StopTimeUpdate[]
  vehicle?: VehicleDescriptor
}

export interface TripDescriptor {
  tripId: string
  routeId: string
  directionId?: number
  startTime?: string
  startDate?: string
  scheduleRelationship?: number
}

export interface StopTimeUpdate {
  stopSequence?: number
  stopId?: string
  arrival?: Time
  departure?: Time
  scheduleRelationship?: number
}

export interface Time {
  delay?: number
  time?: number | string
  uncertainty?: number
}

export interface VehicleDescriptor {
  id?: string
  label?: string
  licensePlate?: string
  occupancyStatus?: number
  occupancyPercentage?: number
}

export interface VehiclePosition extends GTFSEntity {
  vehicle?: VehicleDescriptor
  trip?: TripDescriptor
  position?: Position
  currentStopSequence?: number
  stopId?: string
  currentStatus?: number
  timestamp?: number
  congestionLevel?: number
  occupancyStatus?: number
  occupancyPercentage?: number
}

export interface Position {
  latitude?: number
  longitude?: number
  bearing?: number
  odometer?: number
  speed?: number
}

export interface Alert extends GTFSEntity {
  alert?: {
    activePeriod?: { start?: number; end?: number }[]
    informedEntity?: { routeId?: string; stopId?: string }[]
    cause?: number
    effect?: number
    url?: { translation: { text: string; language: string }[] }
    headerText?: { translation: { text: string; language: string }[] }
    descriptionText?: { translation: { text: string; language: string }[] }
  }
}

export interface ParsedRT {
  tripUpdates: TripUpdate[]
  vehiclePositions: VehiclePosition[]
  alerts: Alert[]
  timestamp: number
  feedVersion: string
}

export interface UserAlert {
  alertId: string
  stopId: string
  stopName: string
  lineId: string
  lineName: string
  destination: string
  minutesAhead: number
  days: number[] // 0=Sun ... 6=Sat
  recurrence: 'daily' | 'weekdays' | 'weekends'
  silenceEnabled: boolean
  silenceFrom: string // HH:mm
  silenceTo: string // HH:mm
  active: boolean
  silenced: boolean
  createdAt: number
  updatedAt: number
}

export interface Settings {
  theme: 'system' | 'light' | 'dark'
  lang: 'es' | 'eu'
  notificationsEnabled: boolean
  lastSync?: number
}

export interface PushSubscription {
  endpoint: string
  keys: {
    p256dh: string
    auth: string
  }
  alertId: string
  expirationTime?: number
}

export interface Stop {
  stop_id: string
  stop_code?: string
  stop_name: string
  stop_desc?: string
  stop_lat: number
  stop_lon: number
  zone_id?: string
  stop_url?: string
  location_type?: number
  parent_station?: string
}

export interface Route {
  route_id: string
  agency_id?: string
  route_short_name: string
  route_long_name?: string
  route_desc?: string
  route_type: number
  route_url?: string
  route_color?: string
  route_text_color?: string
}

export interface Trip {
  trip_id: string
  route_id: string
  service_id: string
  trip_headsign?: string
  trip_short_name?: string
  direction_id?: number
  block_id?: string
  shape_id?: string
}

export interface StopTime {
  trip_id: string
  arrival_time: string
  departure_time: string
  stop_id: string
  stop_sequence: number
  stop_headsign?: string
  pickup_type?: number
  drop_off_type?: number
  shape_dist_traveled?: number
}

export interface Calendar {
  service_id: string
  monday: number
  tuesday: number
  wednesday: number
  thursday: number
  friday: number
  saturday: number
  sunday: number
  start_date: string
  end_date: string
}
