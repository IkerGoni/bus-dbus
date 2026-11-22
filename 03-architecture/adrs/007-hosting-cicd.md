# ADR 007: Hosting & CI/CD: Cloudflare Pages + GitHub Actions

## Status
Accepted

## Context
Need free, global, edge-hosted PWA with: custom headers (CSP, COOP, COEP, Permissions-Policy), serverless functions for VAPID key rotation / push subscriptions, preview deployments, analytics. GitHub Pages lacks edge functions and header control. Netlify/Vercel have vendor lock-in and function limits.

## Decision
**Cloudflare Pages** (free tier: unlimited sites, requests, bandwidth):
- **Build**: GitHub Actions -> `npm run build` -> upload `dist/` to Pages
- **Functions** (`functions/` directory): 
  - `/api/push-subscribe` (POST/DELETE) - VAPID subscription management
  - `/api/push-send` (POST) - called by scheduler (cron)
  - `/api/push-ack` (POST) - delivery acknowledgment
  - `/api/vapid-rotate` (POST) - key rotation endpoint (admin only)
- **Headers** (`public/_headers`):
```
# Security
Content-Security-Policy: default-src 'self'; script-src 'self' 'wasm-unsafe-eval'; style-src 'self' 'unsafe-inline'; img-src 'self' data: https:; connect-src 'self' https://opendata.euskadi.eus https://ftp.geo.euskadi.net wss:; worker-src 'self' blob:; frame-ancestors 'none'; base-uri 'self'; form-action 'self'
Cross-Origin-Opener-Policy: same-origin
Cross-Origin-Embedder-Policy: require-corp
Permissions-Policy: geolocation=(self), notifications=(self), push=(self)
Referrer-Policy: strict-origin-when-cross-origin
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
```
- **Redirects** (`public/_redirects`): `/alert/:id` -> `/?alert=:id` (deep link)
- **Environment Variables**: `VAPID_PUBLIC_KEY`, `VAPID_PRIVATE_KEY`, `SENTRY_DSN`, `PLAUSIBLE_DOMAIN` (set in Pages dashboard)
- **Analytics**: Cloudflare Web Analytics (free, privacy-first) + Plausible (self-hosted or paid)
- **Custom Domain**: `dbus.donostia.app` (or similar) with Cloudflare DNS

## Consequences

### Positive
- Free tier extremely generous (unlimited requests, bandwidth, sites)
- Edge functions = VAPID endpoints at edge (low latency globally)
- Full header control for security (CSP, COOP, COEP required for push)
- Preview deployments on every PR (automatic)
- GitHub Actions integration native
- Cloudflare Web Analytics free, no cookies
- D1/KV available if backend needed later

### Negative / Risks
- Cloudflare vendor lock-in (but standard Workers API, portable)
- Functions cold start ~50ms (acceptable for push)
- D1/KV not in free tier for production volumes (but we don't need them yet)
- `_headers`/`_redirects` syntax specific to Pages

### Mitigations
- **CSP Tuning**: Start strict; use `report-only` mode first; adjust for inline styles (Tailwind 4), wasm (protobufjs)
- **COOP/COEP**: Required for `SharedArrayBuffer` (not used) but good for isolation; may break some third-party iframes (we have none)
- **Permissions-Policy**: Restrict to only needed features (geolocation, notifications, push)
- **VAPID Private Key**: Store in Pages secret (not env var); rotate via build script
- **Sentry Source Maps**: Upload in GitHub Actions `sentry-cli` step

## Implementation Notes
### .github/workflows/deploy.yml
```yaml
name: Deploy to Cloudflare Pages
on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

jobs:
  build-and-deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with: { node-version: '22', cache: 'npm' }
      - run: npm ci
      - run: npm run lint && npm run typecheck && npm run test
      - run: npm run build
      - name: Upload Sentry source maps
        uses: getsentry/action-release@v1
        if: github.ref == 'refs/heads/main'
        env: { SENTRY_AUTH_TOKEN: ${{ secrets.SENTRY_AUTH_TOKEN }} }
      - name: Deploy to Cloudflare Pages
        uses: cloudflare/pages-action@v1
        if: github.ref == 'refs/heads/main'
        with:
          apiToken: ${{ secrets.CLOUDFLARE_API_TOKEN }}
          accountId: ${{ secrets.CLOUDFLARE_ACCOUNT_ID }}
          projectName: dbus-donostia
          directory: dist
          gitHubToken: ${{ secrets.GITHUB_TOKEN }}
          wranglerVersion: '3'
```

### public/_headers
```
# Security headers as above
```

### wrangler.toml (for Functions)
```toml
name = "dbus-donostia"
compatibility_date = "2026-11-01"
pages_build_output_dir = "dist"

[vars]
VAPID_PUBLIC_KEY = ""
SENTRY_DSN = ""
PLAUSIBLE_DOMAIN = ""
```

## Related
- ADR 001: Frontend stack (build output to `dist/`)
- ADR 002: PWA offline strategy (Workbox config in vite.config.ts)
- ADR 003: Push architecture (Functions endpoints for VAPID)
- ADR 008: Risk mitigations (deployment risks)