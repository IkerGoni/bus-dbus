import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { VitePWA } from 'vite-plugin-pwa'
import path from 'path'

export default defineConfig({
  plugins: [
    react(),
    tailwindcss(),
    VitePWA({
      registerType: 'autoUpdate',
      includeAssets: ['favicon.ico', 'robots.txt', 'badge-72.png'],
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
        ],
        screenshots: []
      },
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
      },
      devOptions: {
        enabled: true,
        type: 'module'
      }
    })
  ],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src')
    }
  },
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
  },
  server: {
    port: 3000,
    host: true,
    fs: {
      strict: false
    }
  }
})
