import { describe, it, expect, beforeAll, afterAll } from 'vitest'
import { fetchAndParseGTFSRT, getRTCache } from '../src/gtfs-client.js'
import { createAlert, getAlertsByChat, getActiveAlerts, deleteAlert, addFavorite, getFavorites, removeFavorite } from '../src/db.js'
import { findNextArrival, findAllNextArrivals, formatArrival } from '../src/arrival.js'

describe('GTFS Client', () => {
  it('should return parsed RT data', async () => {
    const rt = await fetchAndParseGTFSRT()
    expect(rt).not.toBeNull()
    expect(rt!.tripUpdates).toBeDefined()
    expect(rt!.vehiclePositions).toBeDefined()
    expect(rt!.timestamp).toBeGreaterThan(0)
  })

  it('should have trip updates with correct structure', async () => {
    const rt = await fetchAndParseGTFSRT()
    expect(rt!.tripUpdates.length).toBeGreaterThan(0)
    const tu = rt!.tripUpdates[0]
    expect(tu.trip).toBeDefined()
    expect(tu.trip!.routeId).toBeDefined()
    expect(tu.stopTimeUpdate).toBeDefined()
    expect(tu.stopTimeUpdate!.length).toBeGreaterThan(0)
  })

  it('should have vehicle positions with occupancy', async () => {
    const rt = await fetchAndParseGTFSRT()
    expect(rt!.vehiclePositions.length).toBeGreaterThan(0)
    const vp = rt!.vehiclePositions[0]
    expect(vp.vehicle).toBeDefined()
    expect(vp.vehicle!.occupancyPercentage).toBeDefined()
  })
})

describe('Arrival Logic', () => {
  let rt: Awaited<ReturnType<typeof fetchAndParseGTFSRT>>

  beforeAll(async () => {
    rt = await fetchAndParseGTFSRT()
  })

  it('should find next arrival for a stop and line', () => {
    const arrival = findNextArrival(rt!, '1234', '12')
    expect(arrival).not.toBeNull()
    expect(arrival!.arrivalTime).toBeGreaterThan(Date.now())
    expect(arrival!.trip.routeId).toBe('12')
  })

  it('should return null for non-existent line at stop', () => {
    const arrival = findNextArrival(rt!, '1234', '99')
    expect(arrival).toBeNull()
  })

  it('should find all arrivals for a stop', () => {
    const arrivals = findAllNextArrivals(rt!, '1234')
    expect(arrivals.size).toBeGreaterThan(0)
    for (const [lineId, arrival] of arrivals) {
      expect(lineId).toBeDefined()
      expect(arrival.arrivalTime).toBeGreaterThan(Date.now())
    }
  })

  it('should format arrival correctly', () => {
    const arrival = findNextArrival(rt!, '1234', '12')
    expect(arrival).not.toBeNull()
    const formatted = formatArrival(arrival!)
    expect(formatted).toContain('L12')
    expect(formatted).toContain('min')
  })
})

describe('Database - Alerts', () => {
  const testChatId = 999999999

  beforeAll(() => {
    // Clean up any existing test data
    const alerts = getAlertsByChat(testChatId)
    for (const alert of alerts) {
      deleteAlert(alert.id)
    }
  })

  it('should create and retrieve alert', () => {
    const alert = createAlert({
      chatId: testChatId,
      stopId: '1234',
      stopName: 'Test Stop',
      lineId: '12',
      lineName: 'L12',
      destination: 'Amara',
      minutesAhead: 5,
      active: true
    })

    expect(alert.id).toBeDefined()
    expect(alert.chatId).toBe(testChatId)
    expect(alert.stopId).toBe('1234')
    expect(alert.lineId).toBe('12')
    expect(alert.minutesAhead).toBe(5)
    expect(alert.active).toBe(true)
  })

  it('should retrieve alerts by chat', () => {
    const alerts = getAlertsByChat(testChatId)
    expect(alerts.length).toBeGreaterThan(0)
    expect(alerts[0].chatId).toBe(testChatId)
  })

  it('should get active alerts', () => {
    const active = getActiveAlerts()
    const testAlerts = active.filter(a => a.chatId === testChatId)
    expect(testAlerts.length).toBeGreaterThan(0)
    expect(testAlerts[0].active).toBe(true)
  })

  it('should delete alert', () => {
    const alerts = getAlertsByChat(testChatId)
    const alertToDelete = alerts[0]
    const deleted = deleteAlert(alertToDelete.id)
    expect(deleted).toBe(true)
    
    const remaining = getAlertsByChat(testChatId)
    expect(remaining.find(a => a.id === alertToDelete.id)).toBeUndefined()
  })
})

describe('Database - Favorites', () => {
  const testChatId = 888888888

  beforeAll(() => {
    // Clean up any existing test data
    const favs = getFavorites(testChatId)
    for (const fav of favs) {
      removeFavorite(fav.id)
    }
  })

  it('should create and retrieve favorite', () => {
    const fav = addFavorite({
      chatId: testChatId,
      stopId: '1234',
      stopName: 'Amara',
      lineId: '12',
      lineName: 'L12',
      destination: 'Amara'
    })

    expect(fav.id).toBeDefined()
    expect(fav.chatId).toBe(testChatId)
    expect(fav.stopId).toBe('1234')
    expect(fav.lineId).toBe('12')
  })

  it('should retrieve favorites by chat', () => {
    const favs = getFavorites(testChatId)
    expect(favs.length).toBeGreaterThan(0)
    expect(favs[0].chatId).toBe(testChatId)
  })

  it('should remove favorite', () => {
    const favs = getFavorites(testChatId)
    const favToRemove = favs[0]
    const removed = removeFavorite(favToRemove.id)
    expect(removed).toBe(true)
    
    const remaining = getFavorites(testChatId)
    expect(remaining.find(f => f.id === favToRemove.id)).toBeUndefined()
  })
})

describe('Integration', () => {
  it('should have working fetcher connection config', async () => {
    const rt = await fetchAndParseGTFSRT()
    // Should at least return mock data when fetcher unavailable
    expect(rt).not.toBeNull()
    expect(rt!.feedVersion).toContain('fetcher')
  })
})