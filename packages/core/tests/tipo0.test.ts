import { describe, expect, it } from 'vitest';
import fixture from '../../contracts/fixtures/tiemposParada-2-datos-tipo0.json';
import { mapTiemposParada, parseStopTimes } from '../src/index.js';

// Respuesta real de Urbieta 12 (parada 2) capturada el 2026-11-22 18:37:
// Dbus devuelve `tipo=0` (sin GPS) donde antes daba `tipo=2`. Solo `tipo=1`
// es tiempo real; cualquier otro valor se trata como horario.
describe('tipo=0 de Dbus (fixture real)', () => {
  const arrivals = mapTiemposParada(parseStopTimes(fixture), {
    stopCode: '2',
    now: 1_700_000_000_000,
  });

  it('marca tipo=0 como horario y tipo=1 como tiempo real', () => {
    expect(arrivals.map((a) => [a.lineId, a.minutes, a.realtime])).toEqual([
      ['36', 5, false],
      ['28', 8, true],
      ['46', 10, false],
      ['28', 16, true],
      ['21', 17, true],
    ]);
  });
});
