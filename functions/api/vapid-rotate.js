import { generateVAPIDKeys } from 'web-push'

export async function onRequestPost({ request, env }) {
  // Simple auth check - in production use proper auth
  const authHeader = request.headers.get('Authorization')
  const expectedToken = env.VAPID_ROTATE_TOKEN
  
  if (!expectedToken || authHeader !== `Bearer ${expectedToken}`) {
    return new Response(JSON.stringify({ error: 'Unauthorized' }), { 
      status: 401, 
      headers: { 'Content-Type': 'application/json' } 
    })
  }
  
  try {
    const newKeys = generateVAPIDKeys()
    
    // Store new keys (in production, update secrets and keep old for 30 days)
    // This is a placeholder - actual rotation requires updating Cloudflare Pages secrets
    console.log('New VAPID keys generated:', {
      publicKey: newKeys.publicKey,
      privateKey: newKeys.privateKey
    })
    
    return new Response(JSON.stringify({ 
      success: true, 
      message: 'Keys generated. Update Cloudflare Pages secrets manually.',
      publicKey: newKeys.publicKey,
      privateKey: newKeys.privateKey
    }), { 
      status: 200, 
      headers: { 'Content-Type': 'application/json' } 
    })
  } catch (err) {
    console.error('VAPID rotate error:', err)
    return new Response(JSON.stringify({ error: 'Internal error' }), { 
      status: 500, 
      headers: { 'Content-Type': 'application/json' } 
    })
  }
}
