# ADR 001: Frontend Stack: Vite 6 + React 19 + TypeScript 5.7 + Tailwind 4

## Status
Accepted

## Context
Need a modern, fast, minimal bundle frontend stack for a PWA targeting mobile web. Requirements: HMR for dev speed, tree-shaking for bundle size, PWA plugin ecosystem, TypeScript strict mode, CSS-in-JS alternative with zero runtime, React 19 compiler optimizations.

## Decision
Standardize on:
- **Vite 6**: Native ESM, fast HMR, built-in PWA plugin (vite-plugin-pwa), Rollup-based production builds
- **React 19**: Compiler auto-memoization, use() hook for async, improved hydration
- **TypeScript 5.7**: Strict mode, type-only imports, satisfies operator
- **Tailwind CSS 4 (Oxide)**: Zero-runtime, JIT compilation, CSS-first config, native CSS variables
- **wouter (1.5KB)**: Minimal router, hooks-based, React 19 compatible
- **i18next + locize**: ICU plurals, namespace lazy-load, browser language detection
- **Biome**: Formatter + linter + import sorting in one Rust binary (10x faster than ESLint+Prettier)

## Consequences

### Positive
- Bundle size target <50MB gzipped achievable (React 19 compiler + Tailwind 4 + wouter + preact/signals)
- Dev experience: HMR <100ms, typecheck parallel, single tool for lint/format
- PWA: vite-plugin-pwa generates manifest, SW, workbox config automatically
- CSS variables design tokens work natively with Tailwind 4
- React 19 compiler reduces need for manual memoization

### Negative / Risks
- React 19 is new (2025); ecosystem plugins may lag
- Tailwind 4 migration from v3 has breaking changes
- Biome has fewer rules than ESLint ecosystem
- wouter less documented than React Router

### Mitigations
- Pin exact versions in package.json; test upgrade path monthly
- Use @tailwindcss/vite plugin (official) for Tailwind 4
- Biome: extend with recommended rules; add custom for a11y (jsx-a11y equivalent)
- Document migration notes in CONTRIBUTING.md

## Implementation Notes
### package.json (key deps)
```json
{
  "dependencies": {
    "react": "^19.0.0",
    "react-dom": "^19.0.0",
    "wouter": "^3.3.0",
    "i18next": "^23.0.0",
    "react-i18next": "^14.0.0",
    "date-fns": "^3.0.0",
    "date-fns-tz": "^3.0.0",
    "zod": "^3.22.0",
    "idb": "^8.0.0",
    "@preact/signals-react": "^2.0.0",
    "web-push": "^3.6.0"
  },
  "devDependencies": {
    "vite": "^6.0.0",
    "@vitejs/plugin-react": "^4.3.0",
    "typescript": "^5.7.0",
    "tailwindcss": "^4.0.0",
    "@tailwindcss/vite": "^4.0.0",
    "vite-plugin-pwa": "^0.21.0",
    "workbox-window": "^7.1.0",
    "biome": "^1.9.0",
    "vitest": "^2.0.0",
    "@playwright/test": "^1.45.0",
    "protobufjs": "^7.3.0"
  }
}
```

### vite.config.ts
```ts
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { VitePWA } from 'vite-plugin-pwa'

export default defineConfig({
  plugins: [
    react(),
    tailwindcss(),
    VitePWA({
      registerType: 'autoUpdate',
      includeAssets: ['favicon.ico', 'robots.txt'],
      manifest: {
        name: 'Dbus Donostia',
        short_name: 'Dbus',
        description: 'Notificaciones push de llegada a parada',
        theme_color: '#0066CC',
        background_color: '#FFFFFF',
        display: 'standalone',
        orientation: 'portrait',
        scope: '/',
        start_url: '/',
        icons: [
          { src: '/icon-192.png', sizes: '192x192', type: 'image/png', purpose: 'any maskable' },
          { src: '/icon-512.png', sizes: '512x512', type: 'image/png', purpose: 'any maskable' }
        ],
        categories: ['transport', 'utilities'],
        shortcuts: [
          { name: 'Crear alerta', url: '/alert/new', description: 'Nueva alerta de llegada' }
        ]
      },
      workbox: {
        globPatterns: ['**/*.{js,css,html,ico,png,svg,woff2}'],
        runtimeCaching: [
          { urlPattern: /^https:\/\/opendata\.euskadi\.eus\/.*\.pb$/, handler: 'NetworkFirst', options: { cacheName: 'gtfs-rt', networkTimeoutSeconds: 10, expiration: { maxEntries: 10, maxAgeSeconds: 35 } } },
          { urlPattern: /^https:\/\/ftp\.geo\.euskadi\.net\/.*\.zip$/, handler: 'CacheFirst', options: { cacheName: 'gtfs-static', expiration: { maxEntries: 5, maxAgeSeconds: 604800 } } }
        ]
      }
    })
  ],
  build: {
    target: 'es2022',
    minify: 'esbuild',
    cssCodeSplit: true,
    rollupOptions: {
      output: {
        manualChunks: {
          vendor: ['react', 'react-dom', 'wouter', 'i18next'],
          data: ['protobufjs', 'zod', 'date-fns', 'date-fns-tz'],
          push: ['web-push', 'idb']
        }
      }
    }
  }
})
```

### tailwind.css (CSS-first config)
```css
@import "tailwindcss";

@theme {
  --color-primary: #0066CC;
  --color-primary-hover: #0052A3;
  --color-success: #16A34A;
  --color-warning: #F59E0B;
  --color-error: #DC2626;
  --color-bg: #FFFFFF;
  --color-bg-secondary: #F8FAFC;
  --color-surface: #FFFFFF;
  --color-border: #E2E8F0;
  --color-text: #0F172A;
  --color-text-muted: #64748B;
  --radius-sm: 6px;
  --radius-md: 10px;
  --radius-lg: 16px;
  --shadow-sm: 0 1px 2px rgb(0 0 0 / 0.05);
  --shadow-md: 0 4px 6px -1px rgb(0 0 0 / 0.1);
  --shadow-lg: 0 10px 15px -3px rgb(0 0 0 / 0.1);
  --space-1: 4px; --space-2: 8px; --space-3: 12px; --space-4: 16px; --space-5: 24px; --space-6: 32px;
  --font-sans: system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
  --font-mono: ui-monospace, SFMono-Regular, "SF Mono", Menlo, monospace;
}

@media (prefers-color-scheme: dark) {
  :root {
    --color-bg: #0F172A;
    --color-bg-secondary: #1E293B;
    --color-surface: #1E293B;
    --color-border: #334155;
    --color-text: #F1F5F9;
    --color-text-muted: #94A3B8;
  }
}

@layer base {
  * { box-sizing: border-box; }
  html { -webkit-text-size-adjust: 100%; }
  body { margin: 0; font-family: var(--font-sans); background: var(--color-bg); color: var(--color-text); line-height: 1.5; -webkit-font-smoothing: antialiased; }
  button, input, select, textarea { font: inherit; }
  :focus-visible { outline: 2px solid var(--color-primary); outline-offset: 2px; }
  @media (max-width: 430px) { html { font-size: 14px; } }
}
```

### biome.json
```json
{
  "$schema": "https://biomejs.dev/schemas/1.9.0/schema.json",
  "formatter": { "enabled": true, "indentStyle": "space", "indentWidth": 2 },
  "linter": { "enabled": true, "rules": { "recommended": true, "a11y": { "useAltText": "error", "useButtonType": "error" } } },
  "javascript": { "formatter": { "semicolons": "always", "quoteStyle": "single", "trailingCommas": "es5" } },
  "files": { "ignore": ["dist", "node_modules", ".vite", "*.excalidraw"] }
}
```

## Related
- ADR 002: PWA offline strategy (Workbox config extends this)
- ADR 003: Push architecture (web-push library used)
- ADR 004: Data model (idb, zod schemas)
- ADR 006: State management (preact/signals-react integration)
- ADR 007: Hosting/CI/CD (Cloudflare Pages build output)