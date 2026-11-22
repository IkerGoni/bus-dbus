import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import type { Context } from 'telegraf';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { handleLocation } from '../src/location.js';
import { logger } from '../src/logger.js';
import {
  NEARBY_UNAVAILABLE_TEXT,
  NearbyUnavailableError,
  getNearbyStops,
} from '../src/nearby.js';
import {
  StopCoordinatesError,
  loadStopCoordinates,
  resetStopCoordinatesCache,
} from '../src/stop-coordinates.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const FIXTURE = path.join(__dirname, 'fixtures', 'dbus-stop-coordinates.json');
const QUERY = { lat: 43.32, lon: -1.982 };
const TMP_DIR = os.tmpdir();
const tempFile = (name: string) =>
  path.join(TMP_DIR, `dbus-coords-${name}-${process.pid}.json`);

interface Reply {
  text: string;
  extra?: unknown;
}

function fakeLocationContext(latitude: number, longitude: number) {
  const replies: Reply[] = [];
  const ctx = {
    message: { location: { latitude, longitude } },
    reply: async (text: string, extra?: unknown) => {
      replies.push({ text, extra });
    },
  } as unknown as Context;
  return { ctx, replies };
}

beforeEach(() => {
  resetStopCoordinatesCache();
  process.env.DBUS_STOP_COORDINATES_PATH = FIXTURE;
  vi.spyOn(logger, 'error').mockImplementation(() => {});
  // Ningún test de /ubicacion debe hacer red: fallar si algo intenta fetch.
  vi.stubGlobal('fetch', () => {
    throw new Error('fetch no permitido en tests de /ubicacion');
  });
});

afterEach(() => {
  process.env.DBUS_STOP_COORDINATES_PATH = '';
  resetStopCoordinatesCache();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('loader del índice local de coordenadas', () => {
  it('carga y valida el contrato del fixture', () => {
    const index = loadStopCoordinates();
    expect(index.stops).toHaveLength(5);
    expect(index.attribution).toContain('ODbL');
    expect(index.coverage.withCoordinates).toBe(4);
  });

  it('falla con error claro si el archivo no existe', () => {
    const missing = tempFile('missing');
    fs.rmSync(missing, { force: true });
    expect(() => loadStopCoordinates(missing)).toThrow(StopCoordinatesError);
    expect(() => loadStopCoordinates(missing)).toThrow(/no encontrado/);
  });

  it('falla con error claro si el JSON está corrupto', () => {
    const corrupt = tempFile('corrupt');
    fs.writeFileSync(corrupt, '{esto no es json');
    try {
      expect(() => loadStopCoordinates(corrupt)).toThrow(/corrupto/);
    } finally {
      fs.rmSync(corrupt, { force: true });
    }
  });

  it('rechaza un JSON válido que no cumple el contrato', () => {
    const invalid = tempFile('invalid');
    fs.writeFileSync(invalid, JSON.stringify({ generatedAt: '2026-11-22' }));
    try {
      expect(() => loadStopCoordinates(invalid)).toThrow(/inválido/);
    } finally {
      fs.rmSync(invalid, { force: true });
    }
  });
});

describe('/ubicacion sobre el índice local', () => {
  it('devuelve paradas reales del catálogo ordenadas por distancia', async () => {
    const stops = await getNearbyStops(QUERY.lat, QUERY.lon);
    expect(stops.map((stop) => stop.cod)).toEqual(['1', '2', '39']);
    expect(stops[0].desc).toBe('Boulevard 9');
    expect(stops[0].distanceM).toBeLessThan(stops[1].distanceM);
    expect(stops.every((stop) => stop.distanceM <= 500)).toBe(true);
    // El código del fixture fuera del catálogo nunca se inventa como parada.
    expect(stops.some((stop) => stop.cod === '99999')).toBe(false);

    const { ctx, replies } = fakeLocationContext(QUERY.lat, QUERY.lon);
    await handleLocation(ctx);
    expect(replies).toHaveLength(1);
    const buttons = JSON.stringify(replies[0].extra);
    expect(replies[0].text).toContain('Paradas cercanas');
    expect(buttons).toContain('srch_s_1');
    expect(buttons).toContain('Boulevard 9');
    expect(buttons).toMatch(/\d+ m/);
    expect(replies[0].text).toContain('ODbL');
    expect(replies[0].text + buttons).not.toContain('Overpass');
  });

  it('índice ausente: mensaje graceful sin paradas inventadas', async () => {
    const missing = tempFile('absent');
    fs.rmSync(missing, { force: true });
    process.env.DBUS_STOP_COORDINATES_PATH = missing;
    resetStopCoordinatesCache();

    await expect(getNearbyStops(QUERY.lat, QUERY.lon)).rejects.toBeInstanceOf(
      NearbyUnavailableError
    );
    const { ctx, replies } = fakeLocationContext(QUERY.lat, QUERY.lon);
    await handleLocation(ctx);
    expect(replies).toHaveLength(1);
    expect(replies[0].text).toBe(NEARBY_UNAVAILABLE_TEXT);
    expect(replies[0].text).toContain('/buscar');
    expect(replies[0].text).not.toContain('Overpass');
    expect(replies[0].text).not.toContain('srch_s_');
  });

  it('índice corrupto: mismo mensaje graceful', async () => {
    const corrupt = tempFile('broken');
    fs.writeFileSync(corrupt, 'no-json');
    try {
      process.env.DBUS_STOP_COORDINATES_PATH = corrupt;
      resetStopCoordinatesCache();
      const { ctx, replies } = fakeLocationContext(QUERY.lat, QUERY.lon);
      await handleLocation(ctx);
      expect(replies).toHaveLength(1);
      expect(replies[0].text).toBe(NEARBY_UNAVAILABLE_TEXT);
      expect(replies[0].text).not.toContain('srch_s_');
    } finally {
      fs.rmSync(corrupt, { force: true });
    }
  });

  it('sin paradas en radio: mensaje existente con /buscar', async () => {
    expect(await getNearbyStops(0, 0)).toEqual([]);
    const { ctx, replies } = fakeLocationContext(0, 0);
    await handleLocation(ctx);
    expect(replies).toHaveLength(1);
    expect(replies[0].text).toContain('No encontré paradas');
    expect(replies[0].text).toContain('/buscar');
  });
});
