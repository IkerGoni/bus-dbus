# ADR 004: Data Model: IndexedDB Schema (idb + zod)

> **Histórico:** ver [ADR 009](009-android-nativo-mvp.md) y [`02-domain-contracts.md`](../../07-android-mvp/docs/02-domain-contracts.md) (Room en Android).

## Status
Accepted

## Context
Need local-first data storage for: GTFS static (stops, routes, trips, stop_times, calendar), GTFS-RT parsed cache (35s TTL), user alerts config, push subscriptions, settings. Must support fast queries (nearest stops, next arrivals), versioning for schema migrations, runtime validation.

## Decision
**IndexedDB via `idb` (Promise wrapper)** with these object stores:

| Store | Key | Indexes | TTL / Notes |
|-------|-----|---------|-------------|
| `stops` | `stop_id` (string) | `stop_name`, `lat_lon` (compound) | GTFS static, ~2k records, 7-day cache |
| `routes` | `route_id` (string) | `route_short_name`, `route_type` | GTFS static |
| `trips` | `trip_id` (string) | `route_id`, `service_id`, `shape_id` | GTFS static |
| `stop_times` | `trip_id+stop_sequence` (compound) | `stop_id`, `departure_time` | GTFS static, largest table (~50k) |
| `calendar` | `service_id` (string) | - | GTFS static |
| `rt_cache` | `feed_id` (string: 'tripUpdates'|'vehiclePositions'|'alerts') | `timestamp` | **TTL 35s**, parsed protobuf -> JSON |
| `alerts` | `alert_id` (uuid) | `stop_id`, `line_id`, `active` | User config, synced to push subscription |
| `subscriptions` | `endpoint` (string) | `alert_id`, `expiration` | Push subscriptions |
| `settings` | `key` (string) | - | theme, language, notificationsEnabled, lastSync |

**Zod schemas** for runtime validation on read/write (src/schemas/*.ts).
**Migration strategy**: `idb` versioning; on version bump, run migration functions (add indexes, transform data).

## Consequences

### Positive
- `idb` = 1.5KB, Promise-based, TypeScript types inferred
- Compound indexes enable efficient queries (nearest stops by lat/lon, next arrivals by time)
- TTL on `rt_cache` enforced by timestamp index + cleanup on write
- Zod validates GTFS-RT parse output before writing to DB
- Single DB connection shared via singleton pattern

### Negative / Risks
- IndexedDB quota varies (~50-100MB); GTFS static + RT cache fits comfortably
- No built-in TTL enforcement -> manual cleanup on each write
- Schema migrations must be backward compatible
- Safari private mode blocks IndexedDB -> detect and fallback to memory store

### Mitigations
- **Safari Private Mode**: Detect `navigator.storage.estimate()` rejection -> use in-memory Map fallback with `sessionStorage` persistence
- **RT Cache Cleanup**: On each `rt_cache` write, delete entries where `timestamp < Date.now() - 35000`
- **Migration Example** (v1->v2 add `occupation` to `rt_cache`):
```ts
export async function migrateV1toV2(db: IDBPDatabase) {
  const tx = db.transaction('rt_cache', 'readwrite');
  const cursor = await tx.store.openCursor();
  while (cursor) {
    if (!cursor.value.occupation) cursor.update({ ...cursor.value, occupation: null });
    cursor = await cursor.continue();
  }
}
```
- **Query Helpers** (src/db/queries.ts):
```ts
export async function getNearestStops(lat: number, lon: number, limit = 10) {
  const db = await getDB();
  return db.getAllFromIndex('stops', 'lat_lon', 
    IDBKeyRange.bound([lat-0.01, lon-0.01], [lat+0.01, lon+0.01]));
}

export async function getNextArrivals(stopId: string, limit = 5) {
  const db = await getDB();
  const rt = await db.get('rt_cache', 'tripUpdates');
}
```

## Related
- ADR 002: PWA offline strategy (Workbox caches GTFS files, client reads from IndexedDB)
- ADR 003: Push architecture (subscriptions store)
- ADR 005: GTFS-RT parsing (writes to rt_cache)
- ADR 006: State management (signals read from IndexedDB)