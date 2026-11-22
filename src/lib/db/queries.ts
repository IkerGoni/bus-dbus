import { getDB } from './index'
import type { Stop } from '../types'

export async function getNearestStops(lat: number, lon: number, limit = 10): Promise<Stop[]> {
  const db = await getDB()
  const latMin = lat - 0.01
  const latMax = lat + 0.01
  const lonMin = lon - 0.01
  const lonMax = lon + 0.01
  
  const allStops = await db.getAll('stops')
  return allStops
    .filter(s => s.stop_lat >= latMin && s.stop_lat <= latMax && s.stop_lon >= lonMin && s.stop_lon <= lonMax)
    .sort((a, b) => {
      const da = Math.hypot(a.stop_lat - lat, a.stop_lon - lon)
      const db = Math.hypot(b.stop_lat - lat, b.stop_lon - lon)
      return da - db
    })
    .slice(0, limit)
}

export async function searchStops(query: string, limit = 20): Promise<Stop[]> {
  const db = await getDB()
  const allStops = await db.getAll('stops')
  const lowerQuery = query.toLowerCase()
  return allStops
    .filter(s => s.stop_name.toLowerCase().includes(lowerQuery) || s.stop_code?.includes(query))
    .slice(0, limit)
}

export async function getStop(stopId: string): Promise<Stop | undefined> {
  const db = await getDB()
  return db.get('stops', stopId)
}

export async function getRoutesForStop(stopId: string): Promise<string[]> {
  const db = await getDB()
  const stopTimes = await db.getAllFromIndex('stop_times', 'stop_id', stopId)
  const tripIds = [...new Set(stopTimes.map(st => st.trip_id))]
  const trips = await Promise.all(tripIds.map(id => db.get('trips', id)))
  const routeIds = [...new Set(trips.filter(t => t).map(t => t!.route_id))]
  return routeIds
}
