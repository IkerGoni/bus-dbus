import webPush from 'web-push'

export async function onRequestPost({ request, env }) {
  try {
    const subscription = await request.json()
    
    // Validate required fields
    if (!subscription.endpoint || !subscription.keys?.p256dh || !subscription.keys?.auth) {
      return new Response(JSON.stringify({ error: 'Invalid subscription' }), { 
        status: 400, 
        headers: { 'Content-Type': 'application/json' } 
      })
    }
    
    // Store in KV (or D1 when available)
    const key = `sub:${subscription.endpoint}`
    await env.DBUS_KV.put(key, JSON.stringify({
      ...subscription,
      createdAt: Date.now()
    }))
    
    // Also index by alertId for easy lookup
    if (subscription.alertId) {
      const alertKey = `alert_subs:${subscription.alertId}`
      const existing = await env.DBUS_KV.get(alertKey)
      const subs = existing ? JSON.parse(existing) : []
      if (!subs.includes(subscription.endpoint)) {
        subs.push(subscription.endpoint)
        await env.DBUS_KV.put(alertKey, JSON.stringify(subs))
      }
    }
    
    return new Response(JSON.stringify({ success: true }), { 
      status: 200, 
      headers: { 'Content-Type': 'application/json' } 
    })
  } catch (err) {
    console.error('Subscribe error:', err)
    return new Response(JSON.stringify({ error: 'Internal error' }), { 
      status: 500, 
      headers: { 'Content-Type': 'application/json' } 
    })
  }
}

export async function onRequestDelete({ request, env }) {
  try {
    const { endpoint } = await request.json()
    if (!endpoint) {
      return new Response(JSON.stringify({ error: 'Missing endpoint' }), { 
        status: 400, 
        headers: { 'Content-Type': 'application/json' } 
      })
    }
    
    const key = `sub:${endpoint}`
    await env.DBUS_KV.delete(key)
    
    return new Response(JSON.stringify({ success: true }), { 
      status: 200, 
      headers: { 'Content-Type': 'application/json' } 
    })
  } catch (err) {
    console.error('Unsubscribe error:', err)
    return new Response(JSON.stringify({ error: 'Internal error' }), { 
      status: 500, 
      headers: { 'Content-Type': 'application/json' } 
    })
  }
}
