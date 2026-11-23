import { describe, expect, it, vi } from 'vitest';
import fixture from '../../contracts/fixtures/tiemposParada-1.json';
import {
  createDbusClient,
  findMatchingArrival,
  linesFromStopTimes,
  mapTiemposParada,
  parseStopTimes,
} from '../src/index.js';

const NOW = 1_700_000_000_000;
const parsed = parseStopTimes(fixture);

function fakeResponse(body: unknown, ok = true): Response {
  return {
    ok,
    status: ok ? 200 : 500,
    json: async () => body,
  } as unknown as Response;
}

describe('parseStopTimes + mapTiemposParada (fixture real)', () => {
  it('normaliza la respuesta de Boulevard 9', () => {
    expect(parsed?.estado).toBe('DATOS');
    expect(parsed?.parada?.desc).toBe('Boulevard 9');
    expect(parsed?.tiempos).toHaveLength(2);
  });

  it('mapea llegadas ordenadas con línea pública y color real', () => {
    const arrivals = mapTiemposParada(parsed, { stopCode: '1', now: NOW });
    expect(arrivals.map((a) => a.lineId)).toEqual(['28', '21']);
    expect(arrivals[0]).toMatchObject({
      internalLineId: '32',
      destination: 'Ospitaleak',
      minutes: 36,
      realtime: false,
      hora: '07:30',
      stopName: 'Boulevard 9',
      arrivalTime: NOW + 36 * 60_000,
    });
  });

  it('filtra por línea y acepta el id interno', () => {
    const arrivals = mapTiemposParada(parsed, {
      stopCode: '1',
      lineId: '32',
      now: NOW,
    });
    expect(arrivals).toHaveLength(1);
    expect(arrivals[0].lineId).toBe('28');
  });

  it('conserva los avisos reales de Dbus (incluso en SIN_SERVICIO)', () => {
    const sinServicio = parseStopTimes({
      estado: 'SIN_SERVICIO',
      parada: null,
      tiempos: [],
      avisos: ['obras en la línea 28', '  ', null, 42],
    });
    expect(sinServicio?.avisos).toEqual(['obras en la línea 28', '42']);
    expect(parsed?.avisos).toEqual([]); // fixture real: avisos: null
  });

  it('devuelve lista vacía si la parada no tiene datos', () => {
    expect(mapTiemposParada(null, { stopCode: '1' })).toEqual([]);
    expect(
      mapTiemposParada(
        { estado: 'SIN DATOS', parada: null, tiempos: [], creacion: null },
        {
          stopCode: '1',
        }
      )
    ).toEqual([]);
  });
});

describe('linesFromStopTimes', () => {
  it('extrae las líneas reales con su color', () => {
    const lines = linesFromStopTimes(parsed);
    expect(lines.map((l) => l.code)).toEqual(['21', '28']);
    expect(lines.find((l) => l.code === '28')?.color).toBe(-811520);
    expect(lines.find((l) => l.code === '28')?.id).toBe('32');
  });
});

describe('findMatchingArrival', () => {
  const arrivals = mapTiemposParada(parsed, { stopCode: '1', now: NOW });

  it('elige la llegada de la línea dentro de la ventana', () => {
    const match = findMatchingArrival(arrivals, {
      lineId: '28',
      minutesAhead: 40,
    });
    expect(match?.lineId).toBe('28');
  });

  it('no avisa si la llegada queda fuera de la ventana', () => {
    expect(
      findMatchingArrival(arrivals, { lineId: '28', minutesAhead: 10 })
    ).toBeNull();
  });

  it('filtra por destino cuando se indicó uno real', () => {
    expect(
      findMatchingArrival(arrivals, {
        lineId: '28',
        destination: 'Amara-Mutualitateak',
        minutesAhead: 40,
      })
    ).toBeNull();
  });

  it('prefiere estimación GPS sobre horario programado', () => {
    const scheduled = arrivals[0];
    const realtime = { ...scheduled, minutes: 39, realtime: true };
    expect(
      findMatchingArrival([scheduled, realtime], {
        lineId: '28',
        minutesAhead: 60,
      })?.minutes
    ).toBe(39);
  });
});

describe('destinationsMatch vía findMatchingArrival (A5-02)', () => {
  const base = mapTiemposParada(parsed, { stopCode: '1', now: NOW })[0];
  const withDestination = (destination: string, minutes = 2) => ({
    ...base,
    lineId: '13',
    internalLineId: '13',
    destination,
    minutes,
  });

  it('igualdad tras normalizar mayúsculas y espacios alrededor de - y /', () => {
    expect(
      findMatchingArrival([withDestination('Buenavista - Altza')], {
        lineId: '13',
        destination: '  BUENAVISTA-ALTZA ',
        minutesAhead: 10,
      })?.destination
    ).toBe('Buenavista - Altza');
    expect(
      findMatchingArrival([withDestination('Amara / Mutualitateak')], {
        lineId: '13',
        destination: 'amara/mutualitateak',
        minutesAhead: 10,
      })?.destination
    ).toBe('Amara / Mutualitateak');
  });

  it('un destino contenido en otro NO casa: L13 real de la parada 103', () => {
    const destinos = [
      'Altza',
      'Buenavista - Altza',
      'Bidebieta - Altza',
      'Bidebieta - Buenavista - Altza',
    ];
    for (const expected of destinos) {
      for (const [i, actual] of destinos.entries()) {
        const found = findMatchingArrival([withDestination(actual, i + 1)], {
          lineId: '13',
          destination: expected,
          minutesAhead: 30,
        });
        if (actual === expected) {
          expect(
            found?.destination,
            `${expected} debe casar consigo mismo`
          ).toBe(actual);
        } else {
          expect(
            found,
            `«${actual}» no debe casar con «${expected}»`
          ).toBeNull();
        }
      }
    }
  });

  it('un destino esperado vacío no casa con nada', () => {
    expect(
      findMatchingArrival([withDestination('Altza')], {
        lineId: '13',
        destination: '   ',
        minutesAhead: 10,
      })
    ).toBeNull();
  });
});

describe('createDbusClient', () => {
  it('cachea por TTL y deduplica peticiones en vuelo', async () => {
    let calls = 0;
    const fetchImpl = vi.fn(async () => {
      calls++;
      return fakeResponse(fixture);
    });

    const client = createDbusClient({
      baseUrl: 'http://test.local/cons/',
      fetch: fetchImpl as unknown as typeof fetch,
      now: () => NOW,
      ttlMs: 15_000,
    });

    const [a, b] = await Promise.all([
      client.getArrivals('1'),
      client.getArrivals('1'),
    ]);
    expect(a).toHaveLength(2);
    expect(b).toHaveLength(2);
    expect(calls).toBe(1);

    await client.getArrivals('1');
    expect(calls).toBe(1);

    await client.getArrivals('1', { forceRefresh: true });
    expect(calls).toBe(2);
    expect(client.getLastSync()).toBe(NOW);
  });

  it('devuelve vacío y no lanza si la API falla', async () => {
    const errors: unknown[] = [];
    const client = createDbusClient({
      baseUrl: 'http://test.local/cons',
      fetch: (async () => fakeResponse({}, false)) as unknown as typeof fetch,
      retryDelayMs: 0,
      logger: { error: (...args) => errors.push(args) },
    });
    await expect(client.getArrivals('1')).resolves.toEqual([]);
    expect(errors).toHaveLength(1);
  });

  it('reintenta un HTTP 500 y devuelve datos válidos en el segundo intento', async () => {
    const fetchImpl = vi
      .fn()
      .mockResolvedValueOnce(fakeResponse({}, false))
      .mockResolvedValueOnce(fakeResponse(fixture));
    const client = createDbusClient({
      baseUrl: 'http://test.local/cons',
      fetch: fetchImpl as unknown as typeof fetch,
      retryDelayMs: 0,
    });

    const times = await client.getStopTimes('1');
    expect(times?.estado).toBe('DATOS');
    expect(fetchImpl).toHaveBeenCalledTimes(2);
  });

  it('rechaza códigos de parada no numéricos sin hacer red', async () => {
    const fetchImpl = vi.fn();
    const client = createDbusClient({
      baseUrl: 'http://test.local/cons',
      fetch: fetchImpl as unknown as typeof fetch,
    });
    await expect(client.getStopTimes('abc')).resolves.toBeNull();
    expect(fetchImpl).not.toHaveBeenCalled();
  });
});
