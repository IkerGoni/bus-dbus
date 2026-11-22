import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { describe, expect, it } from 'vitest';
import {
  UNKNOWN_DESTINATION,
  findMatchingArrival,
  matchesLine,
  normalizeLine,
} from '../src/arrival.js';
import {
  getCatalogMeta,
  getLineByCode,
  getStopsForItinerary,
  searchLines,
  searchStops,
} from '../src/catalog.js';
import { evictOldestEntries } from '../src/dbus-client.js';
import { getSessionCount, pruneSessions } from '../src/handlers.js';
import {
  acquireInstanceLock,
  releaseInstanceLock,
} from '../src/single-instance.js';
import type { NextArrivalResult } from '../src/types.js';

function arrival(
  overrides: Partial<NextArrivalResult> = {}
): NextArrivalResult {
  return {
    arrivalTime: Date.now() + 4 * 60_000,
    minutes: 4,
    lineId: '28',
    internalLineId: '32',
    destination: 'Ospitaleak',
    stopId: '1',
    stopName: 'Boulevard 9',
    realtime: false,
    hora: '08:15',
    itineraryId: '1280',
    ...overrides,
  };
}

describe('normalizeLine / matchesLine', () => {
  it('normalizes L prefix, whitespace, case and zero padding', () => {
    expect(normalizeLine(' L28 ')).toBe('28');
    expect(normalizeLine('l028')).toBe('28');
    expect(normalizeLine('28')).toBe('28');
  });

  it('matches both public code and Dbus internal id', () => {
    expect(matchesLine(arrival(), '28')).toBe(true);
    expect(matchesLine(arrival(), 'L28')).toBe(true);
    expect(matchesLine(arrival(), '32')).toBe(true);
    expect(matchesLine(arrival(), '21')).toBe(false);
    expect(matchesLine(arrival(), '')).toBe(false);
  });
});

describe('findMatchingArrival', () => {
  const base = { lineId: '28', minutesAhead: 5 };

  it('prefers GPS estimates over scheduled times', () => {
    const result = findMatchingArrival(
      [arrival({ minutes: 2 }), arrival({ minutes: 4, realtime: true })],
      base
    );
    expect(result?.minutes).toBe(4);
    expect(result?.realtime).toBe(true);
  });

  it('picks the soonest within the window and ignores the rest', () => {
    const result = findMatchingArrival(
      [
        arrival({ minutes: 1 }),
        arrival({ minutes: 3 }),
        arrival({ minutes: 9 }),
      ],
      base
    );
    expect(result?.minutes).toBe(1);
    expect(findMatchingArrival([arrival({ minutes: 9 })], base)).toBeNull();
  });

  it('accepts the internal id as the alert line', () => {
    expect(
      findMatchingArrival([arrival()], { ...base, lineId: '32' })?.lineId
    ).toBe('28');
  });

  it('filters by destination tolerantly', () => {
    const result = findMatchingArrival(
      [
        arrival({ destination: 'Amara - Ospitaleak - Miramon', minutes: 3 }),
        arrival({ destination: 'Ospitaleak', minutes: 1 }),
      ],
      { ...base, destination: 'Ospitaleak' }
    );
    // Ambas coinciden por contención; gana la más próxima.
    expect(result?.minutes).toBe(1);
  });

  it('does not alert for the opposite direction', () => {
    expect(
      findMatchingArrival([arrival({ destination: 'Miramon' })], {
        ...base,
        destination: 'Ospitaleak',
      })
    ).toBeNull();
  });

  it('does not silence alerts created without a known destination', () => {
    expect(
      findMatchingArrival([arrival()], {
        ...base,
        destination: UNKNOWN_DESTINATION,
      })?.destination
    ).toBe('Ospitaleak');
  });
});

describe('session helpers', () => {
  it('prunes expired sessions without touching live ones', () => {
    const before = getSessionCount();
    const removed = pruneSessions(Date.now());
    expect(removed).toBe(0);
    expect(getSessionCount()).toBe(before);
    expect(pruneSessions(0)).toBe(0);
  });
});

describe('catálogo de búsqueda (datos reales de Dbus)', () => {
  it('busca paradas por código exacto y por nombre sin acentos', () => {
    expect(searchStops('1')[0]?.cod).toBe('1');
    expect(searchStops('boulevard').some((s) => s.desc === 'Boulevard 9')).toBe(
      true
    );
    expect(searchStops('URBIETA')[0]?.desc.toLowerCase()).toContain('urbieta');
  });

  it('busca líneas por código público, id interno y destino', () => {
    expect(searchLines('28')[0]?.cod).toBe('28');
    // '32' es tanto una línea pública como el id interno de la 28: gana la
    // pública, pero el alias sigue apareciendo en los resultados.
    expect(searchLines('32')[0]?.cod).toBe('32');
    expect(searchLines('32').some((line) => line.cod === '28')).toBe(true);
    expect(searchLines('ospitaleak').length).toBeGreaterThan(0);
    expect(getLineByCode('L28')?.cod).toBe('28');
    expect(getLineByCode('32')?.cod).toBe('32');
  });

  it('devuelve paradas de un itinerario real y metadata coherente', () => {
    expect(getStopsForItinerary('1281').length).toBeGreaterThan(5);
    const meta = getCatalogMeta();
    expect(meta.lines).toBeGreaterThan(30);
    expect(meta.stops).toBeGreaterThan(500);
  });
});

describe('stop cache eviction', () => {
  it('evicts the oldest entries and keeps the newest ones', () => {
    const cache = new Map<string, { data: number; ts: number }>();
    for (const code of ['1', '2', '3', '4', '5']) {
      cache.set(code, { data: Number(code), ts: Number(code) });
    }
    expect(evictOldestEntries(cache, 2)).toBe(3);
    expect([...cache.keys()]).toEqual(['4', '5']);
    expect(evictOldestEntries(cache, 2)).toBe(0);
    expect([...cache.keys()]).toEqual(['4', '5']);
  });
});

describe('single-instance lock', () => {
  const file = path.join(os.tmpdir(), `dbus-lock-test-${process.pid}.tmp`);

  it('acquires, refuses a live owner and releases a stale lock', () => {
    try {
      fs.rmSync(file, { force: true });
      expect(acquireInstanceLock(file, process.pid)).toBe(true);
      expect(fs.readFileSync(file, 'utf8').trim()).toBe(String(process.pid));
      // Otro PID, pero el dueño del archivo (este proceso) sigue vivo.
      expect(acquireInstanceLock(file, 999999)).toBe(false);
      releaseInstanceLock(file, process.pid);
      expect(fs.existsSync(file)).toBe(false);

      // PID inexistente => lock caducado, se puede reclamar.
      fs.writeFileSync(file, '999999');
      expect(acquireInstanceLock(file, process.pid)).toBe(true);
      releaseInstanceLock(file, 999999); // no era suyo: no lo borra
      expect(fs.existsSync(file)).toBe(true);
      releaseInstanceLock(file, process.pid);
      expect(fs.existsSync(file)).toBe(false);
    } finally {
      fs.rmSync(file, { force: true });
    }
  });
});
