import { openDB, DBSchema, IDBPDatabase } from 'idb'
import type { Stop, Route, Trip, StopTime, Calendar, ParsedRT, UserAlert, Settings } from '../types'

interface DbusDBSchema extends DBSchema {
  stops: {
    key: string
    value: Stop
    indexes: { 'stop_name': string; 'lat_lon': [number, number] }
  }
  routes: {
    key: string
    value: Route
    indexes: { 'route_short_name': string; 'route_type': number }
  }
  trips: {
    key: string
    value: Trip
    indexes: { 'route_id': string; 'service_id': string; 'shape_id': string }
  }
  stop_times: {
    key: number
    value: StopTime
    indexes: { 'stop_id': string; 'departure_time': number }
  }
  calendar: {
    key: string
    value: Calendar
  }
  rt_cache: {
    key: string
    value: { feed_id: string; tripUpdates: ParsedRT['tripUpdates']; vehiclePositions: ParsedRT['vehiclePositions']; alerts: ParsedRT['alerts']; timestamp: number; feedVersion: string }
    indexes: { 'timestamp': number }
  }
  alerts: {
    key: string
    value: UserAlert
    indexes: { 'stop_id': string; 'line_id': string; 'active': boolean }
  }
  subscriptions: {
    key: string
    value: { endpoint: string; keys: any; alertId: string; expirationTime?: number }
    indexes: { 'alert_id': string; 'expiration': number }
  }
  settings: {
    key: string
    value: { key: string; value: any }
  }
}

let dbInstance: IDBPDatabase<DbusDBSchema> | null = null

export async function getDB(): Promise<IDBPDatabase<DbusDBSchema>> {
  if (dbInstance) return dbInstance
  
  dbInstance = await openDB<DbusDBSchema>('dbus-donostia', 1, {
    upgrade(db) {
      const stops = db.createObjectStore('stops', { keyPath: 'stop_id' })
      stops.createIndex('stop_name', 'stop_name')
      stops.createIndex('lat_lon', ['stop_lat', 'stop_lon'])
      
      const routes = db.createObjectStore('routes', { keyPath: 'route_id' })
      routes.createIndex('route_short_name', 'route_short_name')
      routes.createIndex('route_type', 'route_type')
      
      const trips = db.createObjectStore('trips', { keyPath: 'trip_id' })
      trips.createIndex('route_id', 'route_id')
      trips.createIndex('service_id', 'service_id')
      trips.createIndex('shape_id', 'shape_id')
      
      const stopTimes = db.createObjectStore('stop_times', { keyPath: 'id', autoIncrement: true })
      stopTimes.createIndex('stop_id', 'stop_id')
      stopTimes.createIndex('departure_time', 'departure_time')
      
      db.createObjectStore('calendar', { keyPath: 'service_id' })
      
      const rtCache = db.createObjectStore('rt_cache', { keyPath: 'feed_id' })
      rtCache.createIndex('timestamp', 'timestamp')
      
      const alerts = db.createObjectStore('alerts', { keyPath: 'alertId' })
      alerts.createIndex('stop_id', 'stop_id')
      alerts.createIndex('line_id', 'line_id')
      alerts.createIndex('active', 'active')
      
      const subscriptions = db.createObjectStore('subscriptions', { keyPath: 'endpoint' })
      subscriptions.createIndex('alert_id', 'alertId')
      subscriptions.createIndex('expiration', 'expirationTime')
      
      db.createObjectStore('settings', { keyPath: 'key' })
    }
  })
  
  return dbInstance
}

export async function saveRTCache(feedId: string, data: ParsedRT) {
  const db = await getDB()
  await db.put('rt_cache', { ...data, feed_id: feedId })
  const tx = db.transaction('rt_cache', 'readwrite')
  const index = tx.store.index('timestamp')
  const cutoff = Date.now() - 35000
  let cursor = await index.openCursor(IDBKeyRange.upperBound(cutoff))
  while (cursor) {
    await cursor.delete()
    cursor = await cursor.continue()
  }
}

export async function getRTCache(feedId: string) {
  const db = await getDB()
  return db.get('rt_cache', feedId)
}

export async function saveAlerts(alerts: any[]) {
  const db = await getDB()
  const tx = db.transaction('alerts', 'readwrite')
  for (const alert of alerts) {
    await tx.store.put(alert)
  }
  await tx.done
}

export async function getAlerts(): Promise<any[]> {
  const db = await getDB()
  return db.getAll('alerts')
}

export async function saveSettings(settings: Settings) {
  const db = await getDB()
  for (const [key, value] of Object.entries(settings)) {
    await db.put('settings', { key, value })
  }
}

export async function getSettings(): Promise<Partial<Settings>> {
  const db = await getDB()
  const entries = await db.getAll('settings')
  return Object.fromEntries(entries.map(e => [e.key, e.value]))
}

export async function saveSubscription(sub: { endpoint: string; keys: any; alertId: string; expirationTime?: number }) {
  const db = await getDB()
  await db.put('subscriptions', sub)
}

export async function getSubscriptions() {
  const db = await getDB()
  return db.getAll('subscriptions')
}

export async function deleteSubscription(endpoint: string) {
  const db = await getDB()
  await db.delete('subscriptions', endpoint)
}

export async function clearAllData() {
  const db = await getDB()
  const stores = ['stops', 'routes', 'trips', 'stop_times', 'calendar', 'rt_cache', 'alerts', 'subscriptions', 'settings'] as const
  const tx = db.transaction(stores, 'readwrite')
  await Promise.all(stores.map(s => tx.objectStore(s).clear()))
  await tx.done
}
