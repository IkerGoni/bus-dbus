function urlBase64ToUint8Array(base64String: string): Uint8Array {
  const padding = '='.repeat((4 - base64String.length % 4) % 4)
  const base64 = (base64String + padding).replace(/-/g, '+').replace(/_/g, '/')
  const rawData = window.atob(base64)
  const outputArray = new Uint8Array(rawData.length)
  for (let i = 0; i < rawData.length; ++i) {
    outputArray[i] = rawData.charCodeAt(i)
  }
  return outputArray
}

export async function subscribeToPush(vapidPublicKey: string, alertId: string): Promise<PushSubscription | null> {
  if (!('serviceWorker' in navigator) || !('PushManager' in window)) {
    console.warn('Push not supported')
    return null
  }
  
  try {
    const registration = await navigator.serviceWorker.ready
    const subscription = await registration.pushManager.subscribe({
      userVisibleOnly: true,
      applicationServerKey: urlBase64ToUint8Array(vapidPublicKey) as unknown as BufferSource
    })
    
    await fetch('/api/push-subscribe', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ ...subscription.toJSON(), alertId })
    })
    
    return subscription
  } catch (err) {
    console.error('Subscribe failed:', err)
    return null
  }
}

export async function unsubscribePush(endpoint: string): Promise<boolean> {
  try {
    await fetch('/api/push-subscribe', {
      method: 'DELETE',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ endpoint })
    })
    return true
  } catch (err) {
    console.error('Unsubscribe failed:', err)
    return false
  }
}

export function setupPushListener() {
  navigator.serviceWorker.addEventListener('push', (event: any) => {
    if (!event.data) return
    
    const data = event.data.json()
    event.waitUntil(
      (self as any).registration.showNotification(`${data.line} → ${data.destination}`, {
        body: `Llega en ${data.minutes} min a ${data.stop}`,
        icon: '/icon-192.png',
        badge: '/badge-72.png',
        tag: data.alertId,
        requireInteraction: true,
        data: { url: `/?alert=${data.alertId}` }
      })
    )
    
    fetch('/api/push-ack', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ alertId: data.alertId })
    }).catch(console.error)
  })
  
  navigator.serviceWorker.addEventListener('pushsubscriptionchange', async (event: any) => {
    const newSubscription = await event.newSubscription
    if (newSubscription) {
      await fetch('/api/push-subscribe', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(newSubscription.toJSON())
      })
    }
  })
}

export function sendTestPush() {
  if ('Notification' in window && Notification.permission === 'granted') {
    new Notification('Prueba Dbus', {
      body: 'Esta es una notificación de prueba',
      icon: '/icon-192.png',
      tag: 'test'
    })
  }
}
