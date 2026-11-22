export function startIOSFallback(registration: ServiceWorkerRegistration): () => void {
  // Only on iOS/iPadOS
  const isIOS = /iPad|iPhone|iPod/.test(navigator.userAgent) && !(window as any).MSStream
  if (!isIOS) return () => {}
  
  const interval = setInterval(() => {
    if (registration.active) {
      registration.active.postMessage({ type: 'FETCH_RT' })
    }
  }, 30000) // Every 30 seconds
  
  // Also fetch on visibility change (app comes to foreground)
  const handleVisibilityChange = () => {
    if (!document.hidden && registration.active) {
      registration.active.postMessage({ type: 'FETCH_RT' })
    }
  }
  document.addEventListener('visibilitychange', handleVisibilityChange)
  
  return () => {
    clearInterval(interval)
    document.removeEventListener('visibilitychange', handleVisibilityChange)
  }
}
