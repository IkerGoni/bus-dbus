import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  UNKNOWN_DESTINATION,
  escapeMd,
  findMatchingArrival,
  formatArrivalsList,
  getNextArrivals,
  matchesLine,
  normalizeLine,
  toPublicLineId,
} from '../src/arrival.js';
import {
  getAllStopsForLine,
  getCatalogCoverage,
  getCatalogLinesForStop,
  getCatalogMeta,
  getLineByCode,
  getStopsForItinerary,
  listAllLines,
  paginate,
  searchLines,
  searchStops,
} from '../src/catalog.js';
import { config, configSchema } from '../src/config.js';
import { evictOldestEntries } from '../src/dbus-client.js';
import { getSessionCount, pruneSessions } from '../src/handlers.js';
import { logger } from '../src/logger.js';
import { haversineMeters, rankNearbyStops } from '../src/nearby.js';
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

describe('aislamiento de BD en tests', () => {
  it('usa una BD en memoria, nunca apps/bot/bot.db', () => {
    expect(config.dbPath).toBe(':memory:');
  });
});

describe('browse de catálogo (todas las líneas y paradas)', () => {
  it('lista todas las líneas ordenadas por código público', () => {
    const lines = listAllLines();
    expect(lines).toHaveLength(39);
    expect(lines[0].cod).toBe('5');
    expect(lines.map((l) => l.cod)).toContain('28');
    expect(lines.map((l) => l.cod)).toContain('B9');
    expect(lines.findIndex((l) => l.cod === '5')).toBeLessThan(
      lines.findIndex((l) => l.cod === '28')
    );
  });

  it('devuelve todas las paradas de una línea sin duplicados', () => {
    const stops = getAllStopsForLine('28');
    expect(stops.length).toBeGreaterThan(20);
    expect(new Set(stops.map((s) => s.cod)).size).toBe(stops.length);
    expect(stops.map((s) => s.cod)).toContain('1');
    expect(getAllStopsForLine('no-existe')).toEqual([]);
  });

  it('página con límites y recorte correctos', () => {
    const items = Array.from({ length: 39 }, (_, i) => i);
    const first = paginate(items, 0, 10);
    expect(first.items).toHaveLength(10);
    expect(first.totalPages).toBe(4);
    expect(first.hasNext).toBe(true);
    expect(first.hasPrev).toBe(false);

    const last = paginate(items, 99, 10);
    expect(last.page).toBe(3);
    expect(last.items).toHaveLength(9);
    expect(last.hasNext).toBe(false);

    expect(paginate(items, -3, 10).page).toBe(0);
    expect(paginate([], 0, 10).totalPages).toBe(1);
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

describe('paradas cercanas (índice local + catálogo)', () => {
  it('calcula distancias y cruza solo códigos presentes en el catálogo', () => {
    expect(haversineMeters(43.318, -1.985, 43.318, -1.985)).toBe(0);
    // ~111 m por milésima de grado de latitud.
    expect(haversineMeters(43.318, -1.985, 43.319, -1.985)).toBeGreaterThan(
      100
    );
    expect(haversineMeters(43.318, -1.985, 43.319, -1.985)).toBeLessThan(120);

    const ranked = rankNearbyStops(
      [
        { cod: '39', lat: 43.318, lon: -1.985 },
        { cod: '99999', lat: 43.318, lon: -1.985 },
        { cod: '39', lat: 43.32, lon: -1.985 },
      ],
      43.318,
      -1.985,
      (cod) => (cod === '39' ? { desc: 'Zubieta plaza' } : null)
    );
    expect(ranked).toHaveLength(1);
    expect(ranked[0].cod).toBe('39');
    expect(ranked[0].desc).toBe('Zubieta plaza');
    expect(ranked[0].distanceM).toBeLessThan(1);
  });
});

describe('logger con niveles', () => {
  it('filtra por LOG_LEVEL sin alterar el texto de los mensajes', () => {
    const rank = { debug: 10, info: 20, warn: 30, error: 40, silent: 100 };
    const seen: string[] = [];
    const originalLog = console.log;
    const originalError = console.error;
    console.log = (...args: unknown[]) => seen.push(args.join(' '));
    console.error = (...args: unknown[]) => seen.push(args.join(' '));
    try {
      logger.debug('msg-debug');
      logger.info('msg-info');
      logger.error('msg-error');
    } finally {
      console.log = originalLog;
      console.error = originalError;
    }
    expect(seen.includes('msg-debug')).toBe(rank.debug >= rank[logger.level]);
    expect(seen.includes('msg-info')).toBe(rank.info >= rank[logger.level]);
    expect(seen.includes('msg-error')).toBe(rank.error >= rank[logger.level]);
  });
});

describe('cobertura del índice regenerado', () => {
  it('registra el rango sondeado, sin techo hardcodeado', () => {
    const coverage = getCatalogCoverage();
    expect(coverage.lineIdsScanned).toBeGreaterThanOrEqual(120);
    expect(coverage.lastLineIdWithData).toBeGreaterThan(0);
    expect(coverage.emptyTail).toBeGreaterThan(0);
  });
});

describe('índice de descubrimiento sin service en vivo', () => {
  it('resuelve el id interno en el contexto de la parada, sin contexto gana la pública', () => {
    expect(toPublicLineId('32', '2')).toBe('28');
    expect(toPublicLineId('32')).toBe('32');
    // La parada 5 tiene las públicas 26 (id 28) y 28: el código público gana.
    expect(toPublicLineId('28', '5')).toBe('28');
    expect(toPublicLineId('26', '5')).toBe('26');
    expect(toPublicLineId('L028', '2')).toBe('28');
  });

  it('ofrece líneas reales del índice para la parada sin servicio', () => {
    const lines = getCatalogLinesForStop('340');
    expect(lines.length).toBeGreaterThan(0);
    expect(lines.map((line) => line.lineId)).toContain('5');
    expect(lines.every((line) => line.destination.length > 0)).toBe(true);
  });

  it('muestra el aviso real de Dbus cuando no hay servicio', () => {
    const msg = formatArrivalsList('Pío Baroja 10', [], 'SIN_SERVICIO', '340', [
      'sin servicio hasta las 14:00',
    ]);
    expect(msg).toContain('Sin servicio en este momento');
    expect(msg).toContain('sin servicio hasta las 14:00');
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

describe('escapeMd (convención de AGENTS.md)', () => {
  it('escapa todos los caracteres especiales del Markdown clásico', () => {
    expect(escapeMd('a_b *c* `d` [e](x) \\ ]')).toBe(
      'a\\_b \\*c\\* \\`d\\` \\[e\\](x) \\\\ \\]'
    );
  });
});

describe('identidad alfanumérica de línea (regla 5)', () => {
  it('acepta B4/B9 en normalizeLine y en matchesLine', () => {
    expect(normalizeLine(' b09 ')).toBe('B9');
    expect(normalizeLine('Lb4')).toBe('B4');
    const b9 = arrival({ lineId: 'B9', internalLineId: '77' });
    expect(matchesLine(b9, 'B9')).toBe(true);
    expect(matchesLine(b9, 'b09')).toBe(true);
    expect(matchesLine(b9, 'B4')).toBe(false);
  });
});

describe('configSchema (validación del entorno)', () => {
  const base = { botToken: 'test-token' };

  it('acepta la configuración mínima y aplica defaults', () => {
    const parsed = configSchema.parse(base);
    expect(parsed.language).toBe('es');
    expect(parsed.cacheTtlMs).toBe(30_000);
    expect(parsed.nearbyEnabled).toBe(true);
  });

  it('rechaza token vacío, URL inválida, idioma, TTL y radio fuera de rango', () => {
    expect(configSchema.safeParse({}).success).toBe(false);
    expect(configSchema.safeParse({ ...base, botToken: '' }).success).toBe(
      false
    );
    expect(
      configSchema.safeParse({ ...base, dbusBaseUrl: 'no-es-url' }).success
    ).toBe(false);
    expect(configSchema.safeParse({ ...base, language: 'de' }).success).toBe(
      false
    );
    expect(configSchema.safeParse({ ...base, cacheTtlMs: 0 }).success).toBe(
      false
    );
    expect(configSchema.safeParse({ ...base, nearbyRadiusM: 49 }).success).toBe(
      false
    );
    expect(
      configSchema.safeParse({ ...base, nearbyRadiusM: 2001 }).success
    ).toBe(false);
  });
});

describe('sin llegadas inventadas', () => {
  it('SIN_SERVICIO con aviso real no produce llegadas y muestra el aviso', async () => {
    const times = {
      estado: 'SIN_SERVICIO',
      parada: null,
      tiempos: [],
      creacion: null,
      avisos: ['sin servicio hasta las 14:00'],
    };
    const arrivals = await getNextArrivals('340', undefined, false, times);
    expect(arrivals).toEqual([]);

    const text = formatArrivalsList(
      'Pío Baroja 10',
      arrivals,
      times.estado,
      '340',
      times.avisos
    );
    expect(text).toContain('Sin servicio en este momento');
    expect(text).toContain('sin servicio hasta las 14:00');
    expect(text).not.toMatch(/\d+ min/);
  });
});

describe('carga del índice de catálogo', () => {
  const tmp = path.join(os.tmpdir(), `dbus-catalog-test-${process.pid}.json`);

  afterEach(() => {
    process.env.DBUS_CATALOG_PATH = '';
    fs.rmSync(tmp, { force: true });
    vi.resetModules();
  });

  it('falla con error claro si falta, está corrupto o no cumple el contrato', async () => {
    vi.resetModules();
    process.env.DBUS_CATALOG_PATH = `${tmp}.missing`;
    await expect(import('../src/catalog.js')).rejects.toThrow(/no encontrado/);

    vi.resetModules();
    fs.writeFileSync(tmp, '{esto no es json');
    process.env.DBUS_CATALOG_PATH = tmp;
    await expect(import('../src/catalog.js')).rejects.toThrow(/corrupto/);

    vi.resetModules();
    fs.writeFileSync(tmp, JSON.stringify({ generatedAt: '2026-11-22' }));
    await expect(import('../src/catalog.js')).rejects.toThrow(/inválido/);
  });
});
