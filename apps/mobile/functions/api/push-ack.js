export async function onRequestPost({ request, env }) {
  try {
    const { alertId } = await request.json()
    
    if (!alertId) {
      return new Response(JSON.stringify({ error: 'Missing alertId' }), { 
        status: 400, 
        headers: { 'Content-Type': 'application/json' } 
      })
    }
    
    // Log delivery acknowledgment
    const ackKey = `ack:${alertId}:${Date.now()}`
    await env.DBUS_KV.put(ackKey, JSON.stringify({ 
      alertId, 
      timestamp: Date.now() 
    }), { expirationTtl: 86400 * 7 }) // Keep for 7 days
    
    return new Response(JSON.stringify({ success: true }), { 
      status: 200, 
      headers: { 'Content-Type': 'application/json' } 
    })
  } catch (err) {
    console.error('Ack error:', err)
    return new Response(JSON.stringify({ error: 'Internal error' }), { 
      status: 500, 
      headers: { 'Content-Type': 'application/json' } 
    })
  }
}
