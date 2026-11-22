export async function registerSW(): Promise<ServiceWorkerRegistration> {
  if (!('serviceWorker' in navigator)) {
    throw new Error('Service Worker not supported')
  }
  
  const registration = await navigator.serviceWorker.register('/sw.js', {
    type: 'module'
  })
  
  // Handle controller change for seamless updates
  let refreshing = false
  navigator.serviceWorker.addEventListener('controllerchange', () => {
    if (refreshing) return
    refreshing = true
    window.location.reload()
  })
  
  // Listen for updates
  registration.addEventListener('updatefound', () => {
    const newWorker = registration.installing
    if (newWorker) {
      newWorker.addEventListener('statechange', () => {
        if (newWorker.state === 'installed' && navigator.serviceWorker.controller) {
          // New version available - could show toast
          console.log('New version available, will reload on next navigation')
        }
      })
    }
  })
  
  return registration
}

export function unregisterSW(): Promise<boolean> {
  if (!('serviceWorker' in navigator)) return Promise.resolve(false)
  return navigator.serviceWorker.getRegistration().then(reg => {
    if (reg) return reg.unregister()
    return false
  })
}
