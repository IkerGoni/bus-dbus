# ADR 005: GTFS-RT Parsing: Web Worker + protobufjs

> **Nota 2026-11-22:** los feeds `.pb` de `opendata.euskadi.eus` redirigen a S3
> y devuelven `403 AccessDenied`. El diseño de esta ADR queda como referencia
> histórica de la PWA (Sprint 3, bloqueado); **no aplica al bot**, que usa la
> API JSON de Dbus. Ver `README.md` y `AGENTS.md`.

## Status
Accepted (histórico; fuente no disponible — revisar antes de implementar M3)

## Context
Parse 3 GTFS-RT feeds (mdb-3066 TripUpdates, mdb-3065 VehiclePositions, mdb-3064 Alerts) every 30s. Protobuf binary format. Must not block main thread >100ms. Feed schema may drift. Need validation and fallback to scheduled data.

## Decision
**Web Worker Architecture**:
- **Worker**: `src/workers/gtfs-rt.parser.worker.ts` (separate entry in vite.config.ts)
- **Input**: `ArrayBuffer` (3 feeds fetched in parallel by main thread)
- **Processing**: 
  1. `protobufjs` light build decodes each feed
  2. Zod validates decoded objects against strict schema
  3. Merge VehiclePositions into TripUpdates (enrich with occupancy, lat/lon)
  4. Output typed result: `{ tripUpdates: [], vehiclePositions: [], alerts: [], timestamp: number, feedVersion: string }`
- **Output**: `postMessage` with `Transferable` ArrayBuffer for zero-copy
- **Error Handling**: If parse failure rate >5% across feeds -> log error, return `null` -> main thread falls back to GTFS static (scheduled)
- **Performance**: protobufjs light (~15KB), reuse `root` object, avoid JSON.stringify in worker

## Consequences

### Positive
- Main thread never blocked by protobuf parsing (moves to worker)
- Parallel fetch of 3 feeds + parallel parse in worker
- Zod validation catches schema drift early
- VehiclePositions enrich TripUpdates for occupancy + real-time position
- Transferable ArrayBuffer = zero-copy transfer to main thread

### Negative / Risks
- protobufjs light build still ~15KB; adds to worker bundle
- Web Worker lifecycle: must handle termination gracefully
- Feed schema changes (rare) require protobufjs root rebuild
- iOS Safari: Worker may be throttled in background

### Mitigations
- **Worker Config** (vite.config.ts):
```ts
build: {
  rollupOptions: {
    input: {
      main: 'index.html',
      'gtfs-rt-parser': 'src/workers/gtfs-rt.parser.worker.ts'
    }
  }
}
```
- **Worker Code** (src/workers/gtfs-rt.parser.worker.ts):
```ts
import { load } from 'protobufjs';
import { GTFSRTFeedSchema } from '../schemas/gtfs-rt';

let gtfsRoot: protobuf.Root;

async function init() {
  gtfsRoot = await load('/proto/gtfs-realtime.proto');
}

self.onmessage = async (e: MessageEvent<{ feeds: ArrayBuffer[] }>) => {
  const [tuBuf, vpBuf, alBuf] = e.data.feeds;
  try {
    const tripUpdates = gtfsRoot.lookupType('transit_realtime.FeedMessage').decode(new Uint8Array(tuBuf));
    const vehiclePositions = gtfsRoot.lookupType('transit_realtime.FeedMessage').decode(new Uint8Array(vpBuf));
    const alerts = gtfsRoot.lookupType('transit_realtime.FeedMessage').decode(new Uint8Array(alBuf));
    
    const validated = GTFSRTFeedSchema.parse({ tripUpdates, vehiclePositions, alerts });
    const enriched = enrichWithVehiclePositions(validated.tripUpdates, validated.vehiclePositions);
    
    self.postMessage({ 
      tripUpdates: enriched, 
      vehiclePositions: validated.vehiclePositions, 
      alerts: validated.alerts,
      timestamp: Date.now(),
      feedVersion: '2026.11'
    }, [tuBuf, vpBuf, alBuf]);
  } catch (err) {
    self.postMessage({ error: err.message, timestamp: Date.now() });
  }
};

function enrichWithVehiclePositions(tripUpdates, vehiclePositions) {
  const vpMap = new Map(vehiclePositions.entity.map(e => [e.id, e.vehicle]));
  return tripUpdates.entity.map(tu => {
    const vp = vpMap.get(tu.id);
    return vp ? { ...tu, vehicle: { ...tu.vehicle, ...vp } } : tu;
  });
}

init();
```
- **Main Thread Fetch + Worker Invoke** (src/lib/gtfs-rt.ts):
```ts
const worker = new Worker(new URL('../workers/gtfs-rt.parser.worker.ts', import.meta.url), { type: 'module' });

export async function fetchAndParseGTFRRT(): Promise<ParsedRT | null> {
  const [tuRes, vpRes, alRes] = await Promise.all([
    fetch('https://opendata.euskadi.eus/.../mdb-3066.pb'),
    fetch('https://opendata.euskadi.eus/.../mdb-3065.pb'),
    fetch('https://opendata.euskadi.eus/.../mdb-3064.pb')
  ]);
  const feeds = await Promise.all([tuRes.arrayBuffer(), vpRes.arrayBuffer(), alRes.arrayBuffer()]);
  
  return new Promise((resolve) => {
    worker.onmessage = (e) => {
      if (e.data.error) { console.error('GTFS-RT parse error:', e.data.error); resolve(null); }
      else { resolve(e.data); }
    };
    worker.postMessage({ feeds });
  });
}
```

## Related
- ADR 002: PWA offline strategy (fetch triggers SW cache)
- ADR 004: Data model (writes to rt_cache store)
- ADR 006: State management (signals subscribe to parsed RT)