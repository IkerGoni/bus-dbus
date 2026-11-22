import { describe, expect, it, vi } from 'vitest';
import fixture from '../../contracts/fixtures/tiemposParada-1.json';
import { createDbusClient } from '../src/index.js';

// H1 (auditoría 2026-11): el refresco del bot volvía a pedir cada 30 s toda
// parada consultada alguna vez. Con `maxIdleMs`, getCachedStopCodes() solo da
// las que alguien pidió hace poco; refreshStop() refresca sin contar como uso.
function client(maxIdleMs?: number) {
  let t = 1_700_000_000_000;
  const fetchImpl = vi.fn(async () => ({
    ok: true,
    json: async () => fixture,
  }));
  const c = createDbusClient({
    baseUrl: 'http://test.local/cons',
    fetch: fetchImpl as unknown as typeof fetch,
    now: () => t,
    maxIdleMs,
  });
  const advance = (ms: number) => {
    t += ms;
  };
  return { c, fetchImpl, advance };
}

describe('maxIdleMs', () => {
  it('solo da las paradas pedidas en los últimos maxIdleMs', async () => {
    const { c, advance } = client(120_000);
    await c.getStopTimes('1');
    await c.refreshStop('2'); // el refresco no cuenta como uso
    expect(c.getCachedStopCodes()).toEqual(['1']);
    advance(120_000);
    expect(c.getCachedStopCodes()).toEqual(['1']);
    await c.refreshStop('1');
    advance(1);
    expect(c.getCachedStopCodes()).toEqual([]); // nadie la mira: sale del refresco
    await c.getStopTimes('1');
    expect(c.getCachedStopCodes()).toEqual(['1']);
  });

  it('refreshStop siempre pide a Dbus (ignora la caché)', async () => {
    const { c, fetchImpl } = client(120_000);
    await c.getStopTimes('1');
    await c.refreshStop('1');
    expect(fetchImpl).toHaveBeenCalledTimes(2);
  });

  it('sin maxIdleMs se mantiene el comportamiento: todas las paradas en caché', async () => {
    const { c, advance } = client();
    await c.getStopTimes('1');
    await c.refreshStop('2');
    advance(24 * 3_600_000);
    expect(c.getCachedStopCodes()).toEqual(['1', '2']);
  });
});
