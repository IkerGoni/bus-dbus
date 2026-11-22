import { describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({ getStopLinesWithSource: vi.fn() }));

vi.mock('../src/arrival.js', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../src/arrival.js')>();
  return { ...actual, getStopLinesWithSource: mocks.getStopLinesWithSource };
});

import { validateLineAtStop } from '../src/render.js';

const LIVE_LINES = [
  { lineId: '28', internalLineId: '32', destination: 'Ospitaleak' },
];

describe('validateLineAtStop (datos reales, sin líneas inventadas)', () => {
  it('rechaza una línea que no pasa por la parada', async () => {
    mocks.getStopLinesWithSource.mockResolvedValue({
      lines: LIVE_LINES,
      source: 'live',
    });
    const error = await validateLineAtStop('2', '21');
    expect(error).toContain('no pasa por la parada');
    expect(error).toContain('L28');
  });

  it('acepta el código público y el id interno', async () => {
    mocks.getStopLinesWithSource.mockResolvedValue({
      lines: LIVE_LINES,
      source: 'live',
    });
    expect(await validateLineAtStop('2', '28')).toBeNull();
    expect(await validateLineAtStop('2', '32')).toBeNull();
  });

  it('no juzga si no hay datos (SIN_SERVICIO)', async () => {
    mocks.getStopLinesWithSource.mockResolvedValue({
      lines: [],
      source: 'catalog',
    });
    expect(await validateLineAtStop('2', '21')).toBeNull();
  });
});
