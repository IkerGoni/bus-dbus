import { formatDistanceToNow } from 'date-fns'
import { es, eu } from 'date-fns/locale'

export function formatTimeAgo(timestamp: number, lang: 'es' | 'eu' = 'es'): string {
  return formatDistanceToNow(timestamp, { addSuffix: true, locale: lang === 'eu' ? eu : es })
}

export function formatMinutes(minutes: number): string {
  if (minutes <= 0) return 'Ahora'
  if (minutes === 1) return '1 min'
  return `${minutes} min`
}

export function formatOccupancy(status?: number): { label: string; variant: 'occupancy-low' | 'occupancy-medium' | 'occupancy-high' } | null {
  if (!status) return null
  if (status <= 2) return { label: 'Baja', variant: 'occupancy-low' }
  if (status <= 4) return { label: 'Media', variant: 'occupancy-medium' }
  return { label: 'Alta', variant: 'occupancy-high' }
}

export function isSilenced(silenceEnabled: boolean, silenceFrom: string, silenceTo: string, tz = 'Europe/Madrid'): boolean {
  if (!silenceEnabled) return false
  const now = new Date()
  const current = now.toLocaleTimeString('es-ES', { hour: '2-digit', minute: '2-digit', hour12: false, timeZone: tz })
  return current >= silenceFrom || current <= silenceTo
}

export function generateId(): string {
  return `${Date.now()}_${Math.random().toString(36).slice(2, 9)}`
}

export function debounce<T extends (...args: any[]) => any>(fn: T, ms: number): T {
  let timeoutId: ReturnType<typeof setTimeout>
  return ((...args: any[]) => {
    clearTimeout(timeoutId)
    timeoutId = setTimeout(() => fn(...args), ms)
  }) as T
}
