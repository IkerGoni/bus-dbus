interface FeedEntity {
  id: string
  trip_update?: any
  vehicle?: any
  alert?: any
}

interface TripUpdate {
  trip?: any
  stop_time_update?: any[]
  vehicle?: any
}

interface VehiclePosition {
  trip?: any
  vehicle?: any
  position?: any
  current_stop_sequence?: number
  stop_id?: string
  current_status?: number
  timestamp?: number
  congestion_level?: number
  occupancy_status?: number
  occupancy_percentage?: number
}

interface Alert {
  active_period?: { start?: number; end?: number }[]
  informed_entity?: { route_id?: string; stop_id?: string }[]
  cause?: number
  effect?: number
  url?: { translation: { text: string; language: string }[] }
  header_text?: { translation: { text: string; language: string }[] }
  description_text?: { translation: { text: string; language: string }[] }
}

function enrichTripUpdates(tripUpdates: TripUpdate[], vehiclePositions: VehiclePosition[]): TripUpdate[] {
  const vpMap = new Map<string, VehiclePosition>()
  
  for (const vp of vehiclePositions) {
    if (vp.trip?.trip_id) {
      vpMap.set(vp.trip.trip_id, vp)
    }
  }
  
  return tripUpdates.map(tu => {
    const tripId = tu.trip?.trip_id
    if (tripId && vpMap.has(tripId)) {
      const vp = vpMap.get(tripId)!
      return {
        ...tu,
        vehicle: {
          ...tu.vehicle,
          ...vp.vehicle,
          position: vp.position,
          current_stop_sequence: vp.current_stop_sequence,
          stop_id: vp.stop_id,
          current_status: vp.current_status,
          timestamp: vp.timestamp,
          congestion_level: vp.congestion_level,
          occupancy_status: vp.occupancy_status,
          occupancy_percentage: vp.occupancy_percentage
        }
      }
    }
    return tu
  })
}

self.onmessage = async (e: MessageEvent<{ feeds: ArrayBuffer[] }>) => {
  const { feeds } = e.data
  
  try {
    const [tuBuffer, vpBuffer, alBuffer] = feeds
    
    const tripUpdates: TripUpdate[] = []
    const vehiclePositions: VehiclePosition[] = []
    const alerts: Alert[] = []
    
    const enriched = enrichTripUpdates(tripUpdates, vehiclePositions)
    
    // Use structured clone for transfer
    self.postMessage({
      tripUpdates: enriched,
      vehiclePositions,
      alerts,
      timestamp: Date.now(),
      feedVersion: '2026.11'
    }, [tuBuffer, vpBuffer, alBuffer])
  } catch (err) {
    self.postMessage({ error: err instanceof Error ? err.message : 'Parse error', timestamp: Date.now() })
  }
}
