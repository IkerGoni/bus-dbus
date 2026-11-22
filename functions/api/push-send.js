import webPush from 'web-push'

// Configure VAPID
webPush.setVapidDetails(
  'mailto:dev.mail.igoni@gmail.com',
  env.VAPID_PUBLIC_KEY,
  env.VAPID_PRIVATE_KEY
)

export async function onRequestPost({ request, env }) {
  try {
    const { alertId, payload } = await request.json()
    
    if (!alertId || !payload) {
      return new Response(JSON.stringify({ error: 'Missing alertId or payload' }), { 
        status: 400, 
        headers: { 'Content-Type': 'application/json' } 
      })
    }
    
    // Get subscriptions for this alert
    const alertKey = `alert_subs:${alertId}`
    const existing = await env.DBUS_KV.get(alertKey)
    if (!existing) {
      return new Response(JSON.stringify({ sent: 0, message: 'No subscriptions' }), { 
        status: 200, 
        headers: { 'Content-Type': 'application/json' } 
      })
    }
    
    const endpoints = JSON.parse(existing)
    const results = await Promise.allSettled(
      endpoints.map(async (endpoint: string) => {
        const subKey = `sub:${endpoint}`
        const subData = await env.DBUS_KV.get(subKey)
        if (!subData) return { success: false, reason: 'Subscription not found' }
        
        const subscription = JSON.parse(subData)
        try {
          await webPush.sendNotification(subscription, JSON.stringify(payload), {
            TTL: 86400, // 24 hours
            urgency: 'high'
          })
          return { success: true, endpoint }
        } catch (err) {
          // Handle expired/invalid subscriptions
          if (err.statusCode === 410 || err.statusCode === 404) {
            await env.DBUS_KV.delete(subKey)
            // Also remove from alert index
            const alertSubs = JSON.parse(await env.DBUS_KV.get(alertKey) || '[]')
            const filtered = alertSubs.filter((e: string) => e !== endpoint)
            await env.DBUS_KV.put(alertKey, JSON.stringify(filtered))
          }
          return { success: false, endpoint, error: err.message }
        }
      })
    )
    
    const sent = results.filter(r => r.status === 'fulfilled' && r.value.success).length
    const failed = results.length - sent
    
    return new Response(JSON.stringify({ 
      sent, 
      failed, 
      total: results.length,
      results: results.map(r => r.status === 'fulfilled' ? r.value : { success: false, error: r.reason })
    }), { 
      status: 200, 
      headers: { 'Content-Type': 'application/json' } 
    })
  } catch (err) {
    console.error('Push send error:', err)
    return new Response(JSON.stringify({ error: 'Internal error' }), { 
      status: 500, 
      headers: { 'Content-Type': 'application/json' } 
    })
  }
}
