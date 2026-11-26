# 02 · Contratos de dominio y máquina de estados

Modelos canónicos compartidos entre UI, DB local (Room) y motor de avisos.
Se escriben en TS como referencia; la implementación Android es Kotlin
(ADR 009). La paridad con `@dbus/core` se prueba con los fixtures de
`packages/contracts/fixtures/`.

> **Correcciones frente al paquete original** (2026-11-22, revisión contra el
> repo y la API real): ver §5 al final.

## 1. Entidades principales

```ts
export interface Stop {
  stopId: string;          // Código público de marquesina (`cod`, ej. "340"); = codParada
  name: string;            // `desc`, ej. "Pío Baroja 10"
  lat?: number;            // OSM (dbus-stop-coordinates.json): 514 de 548 paradas
  lon?: number;            // Sin coords → fuera de «Cercanas» y sin «Cómo llegar»
  lineIds: string[];       // Códigos públicos de línea (`lines` del catálogo)
}

export interface Line {
  lineId: string;          // Código público ("5", "28", "B4")
  internalAlias?: string;  // `id` interno ("32" → pública "28"). Solo alias de entrada
  destinations: string[];
  colorArgb?: number;      // `linea.color` de Dbus (ARGB con signo): último visto en vivo; si no, neutro
  isNight: boolean;        // Derivado: código empieza por "B" (verificar con Dbus)
  itineraries: Itinerary[];
}

export interface Itinerary {
  itineraryId: string;
  destination: string;     // Ej. "Amara", "Ospitaleak"
  stopIds: string[];       // Orden secuencial de paradas
}

export type ArrivalSource = 'LIVE' | 'SCHEDULED';   // tipo=1 GPS · tipo=2 horario
export type StopFeedStatus = 'OK' | 'NO_SERVICE' | 'EMPTY' | 'API_ERROR';
//  OK = estado DATOS con tiempos · NO_SERVICE = SIN_SERVICIO (mostrar `avisos`)
//  EMPTY = DATOS sin tiempos · API_ERROR = red/HTTP/JSON inválido o NO DESC

export interface Arrival {
  lineId: string;          // Siempre código público
  destination: string;
  minutesAtFetch: number;  // `minutos` de la API: entero, resolución de 1 min
  clockTime?: string;      // `hora` de la API, "HH:mm"
  source: ArrivalSource;
  fetchedAtMs: number;
}

/** Minutos restantes para pintar. Nunca segundos: la API no da esa precisión. */
export function remainingMinutes(a: Arrival, nowMs: number): number {
  const elapsedMin = Math.max(0, Math.floor((nowMs - a.fetchedAtMs) / 60_000)); // reloj atrasado: no sube
  return Math.max(0, a.minutesAtFetch - elapsedMin);
}

export interface StopFeed {
  status: StopFeedStatus;
  arrivals: Arrival[];
  notice?: string;         // Texto de `avisos` de Dbus (supresiones, traslados)
  fetchedAtMs: number;
}

export type FavoriteType = 'STOP' | 'STOP_LINE' | 'LINE';

export interface Favorite {
  id: string;              // Dedup determinista: `${type}:${stopId ?? ''}:${lineId ?? ''}`
  type: FavoriteType;
  stopId?: string;
  lineId?: string;
  createdAtMs: number;
}

/** Aviso de un solo uso (decisión de Iker, 2026-11-22): avisa una vez y se borra. */
export interface AlertRule {
  id: string;
  stopId: string;
  stopName: string;
  lineId: string;          // Siempre código público
  destination?: string;    // Filtro de destino (paridad con el bot)
  minutesAhead: number;    // Entero 1..30
  active: boolean;         // false = pausado por el usuario
  createdAtMs: number;     // Caduca a los 7 días si no avisa (paridad regla 7 del bot)
}
```

Reglas:

- **Dedup** por `(stopId, lineId)`: si existe, A06 ofrece actualizarlo.
- **Un solo uso:** vigila desde que se guarda; al disparar, el motor notifica y
  **borra** el aviso. Si a los 7 días no ha disparado queda `INACTIVE`
  («Caducada») hasta que el usuario lo reactive o lo borre.
- **Sin avisos recurrentes** (días, franja, alarmas) **ni Posponer**: se
  quitaron el 2026-11-22. Room v2 borra los que hubiera (`MIGRATION_1_2`).

## 2. Matching de llegada (paridad con `findMatchingArrival` del bot)

`nextArrival` = primera llegada de `lineId` (y `destination` si está fijado)
con `remainingMinutes <= minutesAhead`, **prefiriendo `LIVE` sobre
`SCHEDULED`** cuando ambas cumplen (regla 7 de `AGENTS.md`).

`lineId` llega ya resuelto a código público (`toPublicLineId`, con la parada
como contexto: en la parada `1` `32` → `28`). El matching compara solo el código
público; a diferencia de `matchesLine` de `@dbus/core`, no acepta el id interno,
para que un aviso de la línea pública `32` nunca case con la `28`.

## 3. Máquina de estados única de Alertas (cierra B10 y B16)

```ts
export type ComputedAlertStatus =
  | 'INACTIVE'   // Pausada o caducada (la disparada ya no existe: se borra)
  | 'TRIGGERED'  // Hay nextArrival dentro de minutesAhead y no se ha avisado aún
  | 'ACTIVE';    // Vigilando

export function computeAlertStatus(
  alert: AlertRule,
  nowMs: number,
  nextArrival?: Arrival,
  config: AlertsConfig = AlertsConfig(),
): ComputedAlertStatus {
  if (!alert.active) return 'INACTIVE';
  if (nowMs - alert.createdAtMs > config.expirationDays * 86_400_000) return 'INACTIVE';
  const notifyEarlyMs = config.triggerBufferSec * 1_000;
  if (nextArrival !== undefined && remainingMinutes(nextArrival, nowMs + notifyEarlyMs) <= alert.minutesAhead) {
    return 'TRIGGERED';
  }
  return 'ACTIVE';
}
```

El motor de avisos dispara cuando el estado pasa a `TRIGGERED`: notifica y
borra el aviso. El matching (§2) evalúa la llegada con `nowMs + notifyEarlyMs`
(`triggerBufferSec = 0` por defecto, configurable en `config.json`), y la
notificación y la lista de avisos muestran los minutos alineados con los que disparó.

### Prioridad de ordenación en `Inicio` y `Mis avisos`
1. `TRIGGERED`
2. `ACTIVE` (menor `remainingMinutes` primero; sin llegada al final)
3. `INACTIVE`

## 4. Frescura de datos y cercanía offline (cierra B1, B2 y B11)

```ts
export type FreshnessLevel = 'FRESH' | 'STALE' | 'EXPIRED';

// Con refresco cada 30 s, el umbral de FRESH deja margen para la latencia.
export function computeFreshness(fetchedAtMs: number, nowMs: number): FreshnessLevel {
  const ageSec = Math.max(0, Math.floor((nowMs - fetchedAtMs) / 1000));
  if (ageSec <= 45) return 'FRESH';
  if (ageSec <= 90) return 'STALE';
  return 'EXPIRED';
}

export function haversineMeters(lat1: number, lon1: number, lat2: number, lon2: number): number {
  const R = 6_371_000;
  const toRad = (d: number) => (d * Math.PI) / 180;
  const dLat = toRad(lat2 - lat1);
  const dLon = toRad(lon2 - lon1);
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLon / 2) ** 2;
  return Math.round(2 * R * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a)));
}
```

Misma fórmula que `apps/bot/src/nearby.ts`; el fixture de paridad la cubre.
El tiempo a pie (`140 m · 2 min`) se calcula a 80 m/min y se etiqueta como
estimación en línea recta.

## 5. Correcciones frente al paquete original

| Original | Problema | Corregido |
|---|---|---|
| `etaSeconds` + cuenta atrás por segundo | La API da `minutos` enteros: segundos serían dato inventado | `minutesAtFetch` + `remainingMinutes()`; el reloj de 1 s solo decide cuándo baja el minuto |
| `arrivalTimeIso` | La API da `hora` `"HH:mm"`, no ISO | `clockTime` |
| ~548 paradas con `lat/lon` | Solo 514 tienen coordenadas (OSM, ODbL) | `lat/lon` opcionales; 34 sin cercanía ni `geo:` |
| `colorHex` «oficial» en catálogo | El catálogo no guarda color; llega en vivo como `linea.color` ARGB | `colorArgb`; ampliar `build:catalog` para guardarlo |
| RECURRING solo con días + silencio 23–07 | Sin franja horaria avisaría con cada bus del día | Recurrentes eliminados (2026-11-22): todo aviso es de un solo uso |
| `recentlyTriggered` 2 min → `TRIGGERED` | Volvía a disparar con el siguiente bus | Al disparar, el aviso se borra |
| ONCE sin final | Nunca se cerraba | Se borra al disparar; `INACTIVE` a los 7 días |
| Sin preferencia GPS | El bot prefiere `tipo=1` | Matching prefiere `LIVE` |
| FRESH < 30 s | Con refresco a 30 s parpadeaba a STALE | FRESH ≤ 45 s, STALE ≤ 90 s |
