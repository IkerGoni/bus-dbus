import { describe, expect, it } from 'vitest';
import catalog from '../data/catalog.json';
import stops from '../data/stops.json';
import {
  AlertSpecSchema,
  ArrivalSchema,
  CatalogLineSchema,
  CatalogStopSchema,
  LineSchema,
  StopSchema,
} from '../src/index.js';

describe('contratos de parada', () => {
  it('valida el catálogo real completo', () => {
    const parsed = stops.map((stop) => StopSchema.parse(stop));
    expect(parsed).toHaveLength(548);
    expect(parsed[0]).toMatchObject({
      id: '1',
      code: '1',
      name: 'Boulevard 9',
    });
  });

  it('rechaza una parada sin nombre', () => {
    expect(() => StopSchema.parse({ id: '1', code: '1', name: '' })).toThrow();
  });

  it('valida las paradas del catálogo de descubrimiento', () => {
    const first = CatalogStopSchema.parse(catalog.stops[0]);
    expect(first.cod).toBe('1');
    expect(first.lines).toContain('28');
  });
});

describe('contratos de línea', () => {
  it('acepta color entero y color nulo', () => {
    expect(
      LineSchema.parse({ id: '32', code: '28', name: '28', color: -811520 })
        .color
    ).toBe(-811520);
    expect(
      LineSchema.parse({ id: '32', code: '28', name: '28', color: null }).color
    ).toBeNull();
  });

  it('valida una línea de catálogo', () => {
    expect(() =>
      CatalogLineSchema.parse({
        id: '1',
        cod: '5',
        destinations: ['Benta Berri'],
        stops: ['34'],
      })
    ).not.toThrow();
  });
});

describe('contratos de llegada y aviso', () => {
  it('valida una llegada en tiempo real', () => {
    expect(
      ArrivalSchema.parse({
        lineCode: '28',
        destination: 'Ospitaleak',
        minutes: 3,
        expectedAt: 1_700_000_000_000,
        source: 'realtime',
      }).source
    ).toBe('realtime');
  });

  it('acota minutesAhead entre 1 y 60', () => {
    const base = {
      id: 'a1',
      stopId: '1',
      stopName: 'Boulevard 9',
      lineId: '28',
      lineCode: '28',
      destination: 'Ospitaleak',
      minutesAhead: 5,
      active: true,
      createdAt: 1_700_000_000_000,
    };
    expect(AlertSpecSchema.parse(base).minutesAhead).toBe(5);
    expect(() => AlertSpecSchema.parse({ ...base, minutesAhead: 0 })).toThrow();
    expect(() =>
      AlertSpecSchema.parse({ ...base, minutesAhead: 61 })
    ).toThrow();
  });
});
