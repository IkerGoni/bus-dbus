# ADR 006: State Management: preact/signals

## Status
Accepted

## Context
Need reactive state for: next arrival countdown (derived from RT + alerts + now), alert config form, settings, push subscription status. Must be minimal bundle, fine-grained reactivity (no unnecessary re-renders), TypeScript-first, React 19 compatible.

## Decision
**preact/signals** via `@preact/signals-react`:
- **Global Store** (src/store/index.ts):
```ts
import { signal, computed, effect } from '@preact/signals-react';

export const rtData = signal<ParsedRT | null>(null);
export const alerts = signal<Alert[]>([]);
export const settings = signal<Settings>({ theme: 'system', lang: 'es', notifications: true });
export const pushSubscriptions = signal<Map<string, PushSubscription>>(new Map());
export const now = signal(Date.now());

// Derived: next arrival for active alert
export const nextArrival = computed(() => {
  const activeAlert = alerts.value.find(a => a.active && !a.silenced);
  if (!activeAlert || !rtData.value) return null;
  return findNextArrival(rtData.value, activeAlert.stopId, activeAlert.lineId);
});

// Countdown: updates every second via effect
export const countdown = computed(() => {
  const arrival = nextArrival.value;
  if (!arrival) return null;
  return Math.max(0, Math.round((arrival.time - Date.now()) / 1000 / 60));
});

// Persist alerts + settings to IndexedDB
effect(() => { saveToIDB('alerts', alerts.value); });
effect(() => { saveToIDB('settings', settings.value); });

// Timer for countdown
effect(() => { 
  const id = setInterval(() => { now.value = Date.now(); }, 1000); 
  return () => clearInterval(id); 
});
```
- **React Components**: `useSignal()`, `useComputed()` for subscription
- **Bundle**: 1.5KB vs 2.8KB zustand; fine-grained = no re-render on unrelated changes

## Consequences

### Positive
- 1.5KB gzipped (vs 2.8KB zustand, 12KB Redux Toolkit)
- Fine-grained reactivity: only components using `countdown.value` re-render each second
- `computed` automatically tracks dependencies (rtData, alerts, now)
- `@preact/signals-react` = drop-in for React, `useSignal`/`useComputed` hooks
- TypeScript inference excellent

### Negative / Risks
- Less community examples than zustand/Redux
- `effect` runs synchronously; async side effects need `effect(() => { promise.then(...) })` pattern
- Debugging: signals not visible in React DevTools (use `@preact/signals-devtools`)
- Migration from useState/useReducer requires mindset shift

### Mitigations
- **Countdown Timer**: `now` signal drives `countdown` computed; updates every second
- **Form Binding**: `const minutes = signal(5);` -> `<input value={minutes.value} onChange={e => minutes.value = +e.target.value} />`
- **Persistence**: `effect` writes to IndexedDB; debounce with `setTimeout` if frequent
- **DevTools**: Add `@preact/signals-devtools` in dev

## Related
- ADR 001: Frontend stack (@preact/signals-react dependency)
- ADR 004: Data model (signals read/write IndexedDB)
- ADR 005: GTFS-RT parsing (rtData signal updated by worker)