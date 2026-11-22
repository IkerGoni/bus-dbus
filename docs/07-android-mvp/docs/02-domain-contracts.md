# 02 · Contratos de dominio y máquina de estados

Modelos canónicos compartidos entre UI, DB local y motor de avisos.

## 1. Entidades principales

```ts
export interface Stop {
  stopId: string;          // Código público de marquesina (ej. "340", "1281")
  name: string;            // Ej. "Pío Baroja 10"
  lat?: number;            // Desde catálogo estático offline (opcional)
  lon?: number;            // Desde catálogo estático offline (opcional)
  lineIds: string[];       // Líneas públicas que pasan por esta parada
}

export interface Line {
  lineId: string;          // Código público ("5", "28", "B1")
  internalAlias?: string;  // Ej. "32" -> pública "28" (paridad con bot)
  name: string;            // Ej. "Benta Berri"
  colorHex?: string;       // Color oficial Dbus
  isNight?: boolean;       // Búhos / líneas nocturnas
  itineraries: Itinerary[];
}

export interface Itinerary {
  itineraryId: string;
  destination: string;     // Ej. "Amara", "Ospitaleak"
  stopIds: string[];       // Orden secuencial de paradas
}

export type ArrivalSource = 'LIVE' | 'SCHEDULED';
export type StopFeedStatus = 'OK' | 'NO_SERVICE' | 'EMPTY' | 'API_ERROR';

export interface Arrival {
  lineId: string;
  destination: string;
  etaSeconds: number;      // Recalculado cada segundo contra reloj reactivo `now`
  arrivalTimeIso?: string; // Hora estimada (HH:mm) si la API la provee
  source: ArrivalSource;
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

export type AlertFrequency = 'ONCE' | 'RECURRING';
export type Recurrence = 'DAILY' | 'WEEKDAYS' | 'WEEKENDS' | 'CUSTOM';

export interface AlertRule {
  id: string;
  stopId: string;
  stopName: string;
  lineId: string;          // Siempre código público
  destination?: string;
  minutesAhead: number;    // Entero 1..30
  frequency: AlertFrequency;
  daysOfWeek: number[];    // 0=Dom .. 6=Sáb (solo aplica si RECURRING)
  recurrence: Recurrence;
  silenceEnabled: boolean;
  silenceFrom: string;     // "HH:mm", por defecto "23:00"
  silenceTo: string;       // "HH:mm", por defecto "07:00"
  active: boolean;
  snoozedUntilMs?: number; // Timestamp real al pulsar "Posponer 5'"
  lastTriggeredAtMs?: number;
}
```

## 2. Máquina de estados única de Alertas (Cierra B10 y B16)

```ts
export type ComputedAlertStatus =
  | 'INACTIVE'   // Pausada o completada (en modo ONCE)
  | 'SNOOZED'    // Pospuesta temporalmente (now < snoozedUntilMs)
  | 'SILENCED'   // Franja nocturna o día de la semana no habilitado (RECURRING)
  | 'TRIGGERED'  // Activa y llegada actual <= minutesAhead (o disparada hace < 2 min)
  | 'ACTIVE';    // Activa y monitorizando

export function computeAlertStatus(
  alert: AlertRule,
  nowMs: number,
  nextArrival?: Arrival
): ComputedAlertStatus {
  if (!alert.active) return 'INACTIVE';
  if (alert.snoozedUntilMs && nowMs < alert.snoozedUntilMs) return 'SNOOZED';
  if (alert.frequency === 'RECURRING') {
    if (!isDayEnabled(alert.daysOfWeek, nowMs)) return 'SILENCED';
    if (alert.silenceEnabled && isWithinSilenceWindow(alert.silenceFrom, alert.silenceTo, nowMs)) {
      return 'SILENCED';
    }
  }
  const withinWindow =
    nextArrival !== undefined && nextArrival.etaSeconds <= alert.minutesAhead * 60;
  const recentlyTriggered =
    alert.lastTriggeredAtMs !== undefined && nowMs - alert.lastTriggeredAtMs < 120_000;

  if (withinWindow || recentlyTriggered) return 'TRIGGERED';
  return 'ACTIVE';
}
```

### Prioridad de ordenación en `Inicio` y `Mis avisos`
1. `TRIGGERED` (prioridad máxima)
2. `ACTIVE` (menor `etaSeconds` primero)
3. `SNOOZED`
4. `SILENCED`
5. `INACTIVE`

## 3. Frescura de datos y cercanía offline (Cierra B1, B2 y B11)

```ts
export type FreshnessLevel = 'FRESH' | 'STALE' | 'EXPIRED';

export function computeFreshness(fetchedAtMs: number, nowMs: number): FreshnessLevel {
  const ageSec = Math.max(0, Math.floor((nowMs - fetchedAtMs) / 1000));
  if (ageSec < 30) return 'FRESH';
  if (ageSec <= 60) return 'STALE';
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
