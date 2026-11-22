# ADR 002: PWA Offline Strategy: Workbox GenerateSW

## Status
Accepted

## Context
PWA must work offline-first: shell cached, GTFS static cached for weeks, GTFS-RT cached for 35s with stale-while-revalidate. iOS Safari lacks periodicBackgroundSync and kills SW aggressively. Need fallback strategy for background updates.

## Decision
Use **Workbox GenerateSW** mode (not InjectManifest) for zero-config SW generation with:
- **Precache**: All static assets (JS, CSS, HTML, icons, manifest) via `globPatterns`
- **Runtime Caching**:
  - GTFS-RT feeds (`*.pb`): `NetworkFirst` with 10s timeout, cache 35s (`maxAgeSeconds: 35`), max 10 entries
  - GTFS Static (`*.zip`): `CacheFirst`, cache 7 days, max 5 entries
  - Navigation: `NetworkFirst` with offline fallback to cached `index.html`
- **Navigation Preload**: Enabled for faster navigation responses
- **Offline Fallback**: `offline.html` (minimal shell) for navigation requests when offline
- **Client-side fallback for iOS**: `setInterval` in main thread (30s) -> `navigator.serviceWorker.ready.then(reg => reg.active?.postMessage({type: 'SYNC'}))` to wake SW

## Consequences

### Positive
- Workbox handles SW lifecycle complexity (install, activate, fetch, message)
- GenerateSW = no custom SW code to maintain
- Cache strategies match data freshness requirements exactly
- Navigation preload improves perceived performance
- Automatic cache cleanup via `expiration` config

### Negative / Risks
- iOS Safari: SW terminated aggressively; no `periodicBackgroundSync`; `backgroundFetch` not supported
- `NetworkFirst` with timeout may show stale data briefly
- Workbox GenerateSW less flexible than InjectManifest for complex logic
- Cache storage quota varies by browser (typically ~50-100MB)

### Mitigations
- **iOS fallback**: Main thread `setInterval(30000)` calls `registration.active?.postMessage({type: 'FETCH_RT'})` -> SW fetches GTFS-RT -> updates IndexedDB -> client reads via `idb`
- **Stale-while-revalidate UI**: Show cached data with badge "Datos de hace X min" (color: green <30s, yellow 30-60s, red >60s)
- **Cache quota monitoring**: `navigator.storage.estimate()` on startup; warn if >80% used
- **SW registration**: `registerType: 'autoUpdate'` in vite-plugin-pwa; handle `controllerchange` for seamless updates

## Implementation Notes
### workbox config (in vite.config.ts - see ADR 001)
```ts
workbox: {
  globPatterns: ['**/*.{js,css,html,ico,png,svg,woff2}'],
  runtimeCaching: [
    {
      urlPattern: /^https:\/\/opendata\.euskadi\.eus\/.*\.pb$/,
      handler: 'NetworkFirst',
      options: {
        cacheName: 'gtfs-rt',
        networkTimeoutSeconds: 10,
        expiration: { maxEntries: 10, maxAgeSeconds: 35 },
        cacheableResponse: { statuses: [0, 200] }
      }
    },
    {
      urlPattern: /^https:\/\/ftp\.geo\.euskadi\.net\/.*\.zip$/,
      handler: 'CacheFirst',
      options: {
        cacheName: 'gtfs-static',
        expiration: { maxEntries: 5, maxAgeSeconds: 604800 },
        cacheableResponse: { statuses: [0, 200] }
      }
    },
    {
      urlPattern: ({ request }) => request.mode === 'navigate',
      handler: 'NetworkFirst',
      options: {
        cacheName: 'navigation',
        networkTimeoutSeconds: 5,
        expiration: { maxEntries: 5, maxAgeSeconds: 86400 }
      }
    }
  ],
  navigationPreload: true,
  offlineGoogleAnalytics: false
}
```

### iOS Fallback (src/lib/ios-fallback.ts)
```ts
export function startIOSFallback(registration: ServiceWorkerRegistration) {
  if (!navigator.userAgent.includes('iPhone') && !navigator.userAgent.includes('iPad')) return;
  const interval = setInterval(() => {
    registration.active?.postMessage({ type: 'FETCH_RT' });
  }, 30000);
  return () => clearInterval(interval);
}
```

### SW Message Handler (in generated SW - extend via vite-plugin-pwa manifest.transformManifest)
```js
self.addEventListener('message', (event) => {
  if (event.data.type === 'FETCH_RT') {
    event.waitUntil(fetchGTFRRTFeeds());
  }
});
```

## Related
- ADR 001: Frontend stack (vite-plugin-pwa config)
- ADR 003: Push architecture (SW handles push events)
- ADR 004: Data model (IndexedDB updated by SW)
- ADR 005: GTFS-RT parsing (SW fetches, client parses in Worker)