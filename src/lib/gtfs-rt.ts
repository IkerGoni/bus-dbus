import type { ParsedRT } from '../types'

let worker: Worker | null = null

function getWorker(): Worker {
  if (!worker) {
    worker = new Worker(new URL('../workers/gtfs-rt.parser.worker.ts', import.meta.url), { type: 'module' })
  }
  return worker
}

export async function fetchAndParseGTFRRT(): Promise<ParsedRT | null> {
  const controller = new AbortController()
  const timeoutId = setTimeout(() => controller.abort(), 10000)
  
  try {
    const [tuRes, vpRes, alRes] = await Promise.all([
      fetch('https://opendata.euskadi.eus/transport/moveuskadi/dbus/mdb-3066.pb', { signal: controller.signal }),
      fetch('https://opendata.euskadi.eus/transport/moveuskadi/dbus/mdb-3065.pb', { signal: controller.signal }),
      fetch('https://opendata.euskadi.eus/transport/moveuskadi/dbus/mdb-3064.pb', { signal: controller.signal })
    ])
    
    clearTimeout(timeoutId)
    
    if (!tuRes.ok || !vpRes.ok || !alRes.ok) {
      throw new Error(`HTTP errors: ${tuRes.status}, ${vpRes.status}, ${alRes.status}`)
    }
    
    const feeds = await Promise.all([
      tuRes.arrayBuffer(),
      vpRes.arrayBuffer(),
      alRes.arrayBuffer()
    ])
    
    return new Promise((resolve) => {
      const w = getWorker()
      w.onmessage = (e) => {
        if (e.data.error) {
          console.error('GTFS-RT parse error:', e.data.error)
          resolve(null)
        } else {
          resolve(e.data)
        }
      }
      w.onerror = (err) => {
        console.error('Worker error:', err)
        resolve(null)
      }
      w.postMessage({ feeds })
    })
  } catch (err) {
    console.error('Fetch error:', err)
    return null
  }
}

export function startRTPolling(intervalMs = 30000) {
  const interval = setInterval(async () => {
    const data = await fetchAndParseGTFRRT()
    if (data) {
      const { rtData } = await import('../store')
      rtData.value = data
    }
  }, intervalMs)
  
  return () => clearInterval(interval)
}
