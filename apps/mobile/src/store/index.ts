import { signal, computed, effect } from '@preact/signals-react'
import type { ParsedRT, UserAlert, Settings, PushSubscription } from '../lib/types'

export const rtData = signal<ParsedRT | null>(null)
export const alerts = signal<UserAlert[]>([])
export const settings = signal<Settings>({
  theme: 'system',
  lang: 'es',
  notificationsEnabled: true
})
export const pushSubscriptions = signal<Map<string, PushSubscription>>(new Map())
export const now = signal(Date.now())

// Derived: next arrival for active alert
export const nextArrival = computed(() => {
  const activeAlert = alerts.value.find(a => a.active && !a.silenced)
  if (!activeAlert || !rtData.value) return null
  return findNextArrival(rtData.value, activeAlert.stopId, activeAlert.lineId)
})

// Countdown: updates every second via effect
export const countdown = computed(() => {
  const arrival = nextArrival.value
  if (!arrival) return null
  const diff = Math.max(0, Math.round((arrival.arrivalTime - Date.now()) / 1000 / 60))
  return diff
})

// Timer for countdown
effect(() => {
  const id = setInterval(() => { now.value = Date.now() }, 1000)
  return () => clearInterval(id)
})

// Persist alerts + settings to IndexedDB
effect(() => {
  if (typeof window !== 'undefined') {
    import('../lib/db').then(({ saveAlerts, saveSettings }) => {
      saveAlerts(alerts.value)
      saveSettings(settings.value)
    }).catch(console.error)
  }
})

function findNextArrival(rt: ParsedRT, stopId: string, lineId: string) {
  const relevant = rt.tripUpdates?.filter(tu => 
    tu.trip?.routeId === lineId &&
    tu.stopTimeUpdate?.some(stu => stu.stopId === stopId)
  )
  if (!relevant?.length) return null
  
  let earliest: { arrivalTime: number; trip: any; stopTime: any } | null = null
  for (const tu of relevant) {
    for (const stu of tu.stopTimeUpdate || []) {
      if (stu.stopId === stopId && stu.arrival?.time) {
        const time = typeof stu.arrival.time === 'number' ? stu.arrival.time * 1000 : new Date(stu.arrival.time).getTime()
        if (!earliest || time < earliest.arrivalTime) {
          earliest = { arrivalTime: time, trip: tu.trip, stopTime: stu }
        }
      }
    }
  }
  return earliest
}
