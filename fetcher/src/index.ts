import { chromium, Browser, BrowserContext, Page, Response } from 'playwright'
import fastify from 'fastify'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)

interface FeedData {
  tripUpdates: Uint8Array | null
  vehiclePositions: Uint8Array | null
  alerts: Uint8Array | null
  timestamp: number
  fetchErrors: string[]
}

let browser: Browser | null = null
let context: BrowserContext | null = null
let page: Page | null = null

let cachedFeed: FeedData = {
  tripUpdates: null,
  vehiclePositions: null,
  alerts: null,
  timestamp: 0,
  fetchErrors: []
}

const FEED_URLS = {
  tripUpdates: 'https://opendata.euskadi.eus/transport/moveuskadi/dbus/mdb-3066.pb',
  vehiclePositions: 'https://opendata.euskadi.eus/transport/moveuskadi/dbus/mdb-3065.pb',
  alerts: 'https://opendata.euskadi.eus/transport/moveuskadi/dbus/mdb-3064.pb'
}

const FETCH_INTERVAL_MS = 30000
let fetchInterval: NodeJS.Timeout | null = null

async function initBrowser() {
  if (browser) return

  console.log('🌐 Starting Playwright Chromium...')
  browser = await chromium.launch({
    headless: true,
    args: [
      '--no-sandbox',
      '--disable-setuid-sandbox',
      '--disable-dev-shm-usage',
      '--disable-accelerated-2d-canvas',
      '--no-first-run',
      '--no-zygote',
      '--disable-gpu'
    ]
  })

  context = await browser.newContext({
    userAgent: 'Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36',
    viewport: { width: 390, height: 844 },
    locale: 'es-ES',
    timezoneId: 'Europe/Madrid',
    permissions: [],
    extraHTTPHeaders: {
      'Accept-Language': 'es-ES,es;q=0.9,en;q=0.8',
      'Accept': 'application/x-protobuf, application/octet-stream, */*'
    }
  })

  page = await context.newPage()

  // Enable response interception to capture protobuf data
  await context.route('**/*.pb', async route => {
    const response = await route.fetch()
    const body = await response.body()
    // Store response for later retrieval
    ;(route.request() as any)._responseBody = body
    await route.fulfill({ response })
  })

  // Initial navigation to establish session
  await warmupSession()
}

async function warmupSession() {
  if (!page) return

  console.log('🔐 Establishing session with opendata.euskadi.eus...')

  try {
    // Visit main page first to get cookies
    await page.goto('https://opendata.euskadi.eus/contenidos/datos_abiertos/transporte/moveuskadi/dbus.html', {
      waitUntil: 'networkidle',
      timeout: 30000
    })
    console.log('✅ Session established')
  } catch (e) {
    console.warn('⚠️ Warmup navigation failed:', e instanceof Error ? e.message : e)
  }
}

async function fetchFeed(url: string): Promise<Uint8Array | null> {
  if (!page) return null

  try {
    console.log(`📡 Fetching ${url}...`)
    
    // Navigate directly to the .pb URL and capture response
    const response = await page.goto(url, {
      waitUntil: 'networkidle',
      timeout: 30000
    })

    if (!response || !response.ok()) {
      const status = response?.status() || 'no response'
      const body = response ? await response.text().catch(() => '') : ''
      console.error(`❌ HTTP ${status}: ${body.substring(0, 200)}`)
      return null
    }

    const buffer = await response.body()
    
    if (!buffer || buffer.length < 100) {
      console.error(`❌ Empty or too small response: ${buffer?.length || 0} bytes`)
      return null
    }

    console.log(`✅ ${buffer.length} bytes`)
    return buffer
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e)
    console.error(`❌ ${msg}`)
    return null
  }
}

async function fetchAllFeeds(): Promise<FeedData> {
  if (!page) {
    await initBrowser()
  }

  if (!page) throw new Error('Browser not initialized')

  const errors: string[] = []
  const results: Partial<Record<keyof typeof FEED_URLS, Uint8Array>> = {}

  // Fetch each feed by navigating to the URL
  for (const [key, url] of Object.entries(FEED_URLS)) {
    const data = await fetchFeed(url)
    if (data) {
      results[key as keyof typeof FEED_URLS] = data
    } else {
      errors.push(`${key}: Failed to fetch`)
    }

    // Small delay between fetches
    await new Promise(r => setTimeout(r, 1000))
  }

  const feedData: FeedData = {
    tripUpdates: results.tripUpdates || null,
    vehiclePositions: results.vehiclePositions || null,
    alerts: results.alerts || null,
    timestamp: Date.now(),
    fetchErrors: errors
  }

  cachedFeed = feedData
  return feedData
}

async function startPeriodicFetch() {
  if (fetchInterval) return

  console.log(`⏱️ Starting periodic fetch every ${FETCH_INTERVAL_MS / 1000}s`)

  // Initial fetch
  await fetchAllFeeds().catch(console.error)

  fetchInterval = setInterval(async () => {
    await fetchAllFeeds().catch(console.error)
  }, FETCH_INTERVAL_MS)
}

function stopPeriodicFetch() {
  if (fetchInterval) {
    clearInterval(fetchInterval)
    fetchInterval = null
    console.log('⏹️ Periodic fetch stopped')
  }
}

async function closeBrowser() {
  stopPeriodicFetch()
  if (browser) {
    await browser.close()
    browser = null
    context = null
    page = null
    console.log('🌐 Browser closed')
  }
}

function getCachedFeed(): FeedData {
  return cachedFeed
}

// Fastify server
const server = fastify({ logger: false })

server.get('/health', async () => {
  return { status: 'ok', browser: browser !== null, lastFetch: cachedFeed.timestamp }
})

server.get('/feed/:type', async (request, reply) => {
  const type = (request.params as { type: string }).type as keyof typeof FEED_URLS

  if (!FEED_URLS[type as keyof typeof FEED_URLS]) {
    return reply.code(404).send({ error: 'Invalid feed type' })
  }

  const feed = cachedFeed
  const data = feed[type]

  if (!data) {
    return reply.code(503).send({
      error: 'Feed not available',
      lastFetch: feed.timestamp,
      errors: feed.fetchErrors
    })
  }

  reply.header('Content-Type', 'application/x-protobuf')
  reply.header('Cache-Control', 'public, max-age=30')
  return data
})

server.get('/feeds/all', async () => {
  const feed = cachedFeed
  return {
    timestamp: feed.timestamp,
    tripUpdates: feed.tripUpdates ? feed.tripUpdates.length : 0,
    vehiclePositions: feed.vehiclePositions ? feed.vehiclePositions.length : 0,
    alerts: feed.alerts ? feed.alerts.length : 0,
    errors: feed.fetchErrors
  }
})

async function startServer(port = 3002) {
  await initBrowser()
  await startPeriodicFetch()

  await server.listen({ port, host: '0.0.0.0' })
  console.log(`🚀 Fetcher API running on http://localhost:${port}`)
  console.log(`   GET /feed/tripUpdates`)
  console.log(`   GET /feed/vehiclePositions`)
  console.log(`   GET /feed/alerts`)
  console.log(`   GET /feeds/all`)
  console.log(`   GET /health`)
}

const PORT = parseInt(process.env.FETCHER_PORT || '3002', 10)

startServer(PORT).catch(err => {
  console.error('Failed to start:', err)
  process.exit(1)
})

process.on('SIGINT', async () => {
  console.log('\n🛑 Shutting down...')
  await closeBrowser()
  await server.close()
  process.exit(0)
})

process.on('SIGTERM', async () => {
  console.log('\n🛑 Shutting down...')
  await closeBrowser()
  await server.close()
  process.exit(0)
})