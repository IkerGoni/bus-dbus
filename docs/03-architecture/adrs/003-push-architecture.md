# ADR 003: Push Architecture: Web Push API + VAPID

## Status
Accepted — Superseded por ADR 009 para el cliente móvil (2026-11-22)

## Context
Need native push notifications for arrival alerts without proprietary backend. Requirements: iOS 16.4+ / Android support, VAPID standard (no FCM lock-in), payload encryption, delivery acknowledgment, key rotation procedure, fallback for denied permission / older iOS.

## Decision
**Web Push API + VAPID** (RFC 8030):
- **VAPID Keys**: Generated at build time (`web-push generate-vapid-keys`), public key in `manifest.json` / client env, private key in Cloudflare Pages Function secret
- **Subscription Flow**: User gesture -> `Notification.requestPermission()` -> `pushManager.subscribe({userVisibleOnly: true, applicationServerKey: vapidPublicKey})` -> send subscription to VAPID endpoint (Cloudflare Function) -> store in IndexedDB
- **Payload**: AES-128-GCM encrypted (Web Push standard), TTL 24h, urgency 'high'
- **Payload Content**: `{ line: '5', destination: 'Amara', minutes: 3, stop: 'Loiola', alertId: 'uuid' }` (~200 bytes < 4KB limit)
- **Delivery Ack**: Client `push` event -> `event.waitUntil(acknowledgeDelivery(alertId))` -> Function logs success
- **iOS 16.4+**: Requires PWA installed ("Add to Home Screen"), user gesture for permission, background delivery only when installed
- **Fallback**: If permission denied / iOS <16.4 / not installed -> in-app polling (30s) + visible countdown + toast notifications
- **Key Rotation**: Build-time script generates new keys; old public key kept in `manifest.json` for 30 days; Function accepts both; client re-subscribes on `pushsubscriptionchange`

## Consequences

### Positive
- Open standard, no vendor lock-in (unlike FCM/APNs)
- Works on iOS 16.4+ (Safari) and Android (Chrome/Firefox/Samsung Browser)
- Payload encrypted end-to-end
- VAPID identifies application without central registry
- Cloudflare Pages Functions = serverless, free tier generous, edge deployment

### Negative / Risks
- iOS: Background delivery ONLY if PWA installed on Home Screen
- iOS: No action buttons in push (only title + body + tap)
- iOS: Permission resets if PWA not opened 2-3 weeks
- Safari: `pushsubscriptionchange` event may not fire reliably
- VAPID private key compromise = all subscriptions invalidated
- Payload limit 4KB (our ~200 bytes well within)

### Mitigations
- **Progressive Permission UX**: Onboarding explains value -> "Activar alertas" button (user gesture) -> native prompt
- **iOS Install Prompt**: Detect `beforeinstallprompt` / `window.matchMedia('(display-mode: standalone)')` -> show "Instalar para alertas en pantalla bloqueo"
- **Fallback In-App**: `setInterval(30000)` checks RT -> shows toast/banner if arrival imminent
- **Key Rotation Procedure**: 
  1. `npm run vapid:rotate` -> generates new keys, updates `.env.production`
  2. Deploy Cloudflare Function with BOTH old + new private keys (30-day overlap)
  3. Update `manifest.json` with new public key
  4. Client handles `pushsubscriptionchange` -> re-subscribes with new key
  5. After 30 days, remove old key from Function
- **Subscription Cleanup**: Function endpoint validates subscription on each send; 410/404 -> delete from DB

## Implementation Notes
### VAPID Key Generation (scripts/generate-vapid.js)
```js
import { generateVAPIDKeys } from 'web-push';
const keys = generateVAPIDKeys();
console.log('PUBLIC_KEY:', keys.publicKey);
console.log('PRIVATE_KEY:', keys.privateKey);
// Write to .env.production and Cloudflare Pages secrets
```

### Cloudflare Pages Function (functions/api/push-subscribe.js)
```js
import webPush from 'web-push';

webPush.setVapidDetails(
  'mailto:dev.mail.igoni@gmail.com',
  env.VAPID_PUBLIC_KEY,
  env.VAPID_PRIVATE_KEY
);

export async function onRequestPost({ request, env }) {
  const subscription = await request.json();
  await env.DB.prepare('INSERT OR REPLACE INTO subscriptions (endpoint, keys, alertId) VALUES (?, ?, ?)')
    .bind(subscription.endpoint, JSON.stringify(subscription.keys), subscription.alertId)
    .run();
  return new Response('OK');
}

export async function onRequestDelete({ request, env }) {
  const { endpoint } = await request.json();
  await env.DB.prepare('DELETE FROM subscriptions WHERE endpoint = ?').bind(endpoint).run();
  return new Response('OK');
}
```

### Send Push (functions/api/push-send.js - called by scheduler)
```js
export async function sendPush(alertId, payload) {
  const subs = await env.DB.prepare('SELECT endpoint, keys FROM subscriptions WHERE alertId = ?').bind(alertId).all();
  const results = await Promise.allSettled(subs.results.map(sub => 
    webPush.sendNotification(sub, JSON.stringify(payload), { TTL: 86400, urgency: 'high' })
  ));
  return results;
}
```

### Client Subscription (src/lib/push.ts)
```ts
export async function subscribeToPush(vapidPublicKey: string, alertId: string) {
  const reg = await navigator.serviceWorker.ready;
  const sub = await reg.pushManager.subscribe({
    userVisibleOnly: true,
    applicationServerKey: urlBase64ToUint8Array(vapidPublicKey)
  });
  await fetch('/api/push-subscribe', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ ...sub.toJSON(), alertId })
  });
  return sub;
}

navigator.serviceWorker.addEventListener('push', (event) => {
  const data = event.data.json();
  event.waitUntil(
    self.registration.showNotification(data.line + ' -> ' + data.destination, {
      body: `Llega en ${data.minutes} min a ${data.stop}`,
      icon: '/icon-192.png',
      badge: '/badge-72.png',
      tag: data.alertId,
      requireInteraction: true,
      data: { url: `/?alert=${data.alertId}` }
    })
  );
  fetch('/api/push-ack', { method: 'POST', body: JSON.stringify({ alertId: data.alertId }) });
});

navigator.serviceWorker.addEventListener('pushsubscriptionchange', async (event) => {
  const newSub = await event.newSubscription;
  await fetch('/api/push-subscribe', { method: 'POST', body: JSON.stringify(newSub.toJSON()) });
});
```

## Related
- ADR 001: Frontend stack (web-push dependency)
- ADR 002: PWA offline strategy (SW receives push events)
- ADR 004: Data model (subscriptions table in IndexedDB/KV)
- ADR 007: Hosting/CI/CD (Cloudflare Pages Functions for VAPID endpoints)