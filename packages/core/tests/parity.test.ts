import { readdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';
import cases from '../../contracts/fixtures/domain-cases.json';
import {
  type Arrival,
  findMatchingArrival,
  mapTiemposParada,
  parseStopTimes,
} from '../src/index.js';

// Paridad con Android (ADR 009 §4, H6): los mismos casos los ejecuta
// `DomainParityTest` en apps/android. Las secciones que @dbus/core no implementa
// están declaradas en `runs`/`notApplicable` del fixture.
const fixturesDir = fileURLToPath(
  new URL('../../contracts/fixtures/', import.meta.url)
);

async function fixture(file: string): Promise<unknown> {
  return (await import(`../../contracts/fixtures/${file}`)).default;
}

describe('fixtures reales de tiemposParada (los 7)', () => {
  it('domain-cases.json cubre todos los fixtures de tiemposParada', () => {
    const files = readdirSync(fixturesDir)
      .filter((f) => f.startsWith('tiemposParada-'))
      .sort();
    expect(files).toHaveLength(7);
    expect(cases.tiemposParada.map((c) => c.file).sort()).toEqual(files);
  });

  for (const c of cases.tiemposParada) {
    it(`parsea ${c.file}`, async () => {
      const parsed = parseStopTimes(await fixture(c.file));
      expect(parsed?.estado ?? null).toBe(c.estado);
      const arrivals = mapTiemposParada(parsed, { stopCode: 'x', now: 0 });
      expect(
        arrivals.map(({ lineId, minutes, realtime, destination }) => ({
          lineId,
          minutes,
          realtime,
          destination,
        }))
      ).toEqual(c.arrivals);
    });
  }
});

describe('findMatchingArrival (casos compartidos)', () => {
  for (const c of cases.matching) {
    it(c.name, () => {
      const arrivals: Arrival[] = c.arrivals.map((a) => ({
        ...a,
        arrivalTime: 0,
        internalLineId: a.lineId,
        stopId: 'x',
        stopName: 'x',
        hora: '',
        itineraryId: '',
      }));
      const found = findMatchingArrival(arrivals, {
        lineId: c.lineId,
        destination: c.destination ?? undefined,
        minutesAhead: c.minutesAhead,
      });
      expect(found === null ? null : arrivals.indexOf(found)).toBe(
        c.expectedIndex
      );
    });
  }
});

describe('secciones que @dbus/core no implementa', () => {
  it('están declaradas como no aplicables, no fingidas', () => {
    for (const section of [
      'remainingMinutes',
      'freshness',
      'haversineMeters',
    ] as const) {
      expect(cases.runs[section]).not.toContain('@dbus/core');
      expect(cases.notApplicable[section]).toBeTruthy();
    }
  });
});
