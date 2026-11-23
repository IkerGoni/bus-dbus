import type { Context, Telegraf } from 'telegraf';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({ getNextArrivals: vi.fn() }));

vi.mock('../src/arrival.js', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../src/arrival.js')>();
  return { ...actual, getNextArrivals: mocks.getNextArrivals };
});

import { setBotInstance, triggerAlertCheck } from '../src/alerts.js';
import {
  createAlert,
  deleteAlert,
  getAlertById,
  getAlertsByChat,
} from '../src/db.js';
import { logger } from '../src/logger.js';
import type { NextArrivalResult } from '../src/types.js';

const CHAT = 111111111;

const ARRIVAL: NextArrivalResult = {
  arrivalTime: Date.now() + 2 * 60_000,
  minutes: 2,
  lineId: '28',
  internalLineId: '32',
  destination: 'Ospitaleak',
  stopId: '2',
  stopName: 'Urbieta 12',
  realtime: true,
  hora: '08:15',
  itineraryId: '1280',
};

function fakeBot(sendMessage: (...args: unknown[]) => Promise<unknown>) {
  return { telegram: { sendMessage } } as unknown as Telegraf<Context>;
}

async function newAlert() {
  return createAlert({
    chatId: CHAT,
    stopId: '2',
    stopName: 'Urbieta 12',
    lineId: '28',
    lineName: 'L28',
    destination: 'Ospitaleak',
    minutesAhead: 5,
    active: true,
  });
}

beforeEach(async () => {
  for (const alert of await getAlertsByChat(CHAT)) await deleteAlert(alert.id);
  mocks.getNextArrivals.mockReset();
});

afterEach(async () => {
  for (const alert of await getAlertsByChat(CHAT)) await deleteAlert(alert.id);
  vi.useRealTimers();
  vi.restoreAllMocks();
});

describe('scheduler de avisos (garantías de AGENTS.md:45-47)', () => {
  it('desactiva avisos de más de 7 días sin enviar ni consultar la API', async () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-11-01T00:00:00Z'));
    const alert = await newAlert();
    const send = vi.fn().mockResolvedValue({});
    setBotInstance(fakeBot(send));

    vi.setSystemTime(new Date('2026-11-09T00:00:00Z')); // 8 días después
    await triggerAlertCheck();

    expect((await getAlertById(alert.id))?.active).toBe(false);
    expect(send).not.toHaveBeenCalled();
    expect(mocks.getNextArrivals).not.toHaveBeenCalled();
  });

  it('un doble disparo concurrente consulta la API una sola vez', async () => {
    const alert = await newAlert();
    let release!: () => void;
    const gate = new Promise<void>((resolve) => {
      release = resolve;
    });
    mocks.getNextArrivals.mockImplementation(() => gate.then(() => [ARRIVAL]));
    const send = vi.fn().mockResolvedValue({});
    setBotInstance(fakeBot(send));

    const first = triggerAlertCheck();
    const second = triggerAlertCheck();
    await Promise.resolve(); // el primer ciclo entra en checkAlertsUnsafe
    expect(mocks.getNextArrivals).toHaveBeenCalledTimes(1);
    release();
    await Promise.all([first, second]);

    expect(send).toHaveBeenCalledTimes(1);
    expect((await getAlertById(alert.id))?.active).toBe(false);
  });

  it('desactiva tras 3 fallos de envío y no vuelve a intentarlo', async () => {
    const alert = await newAlert();
    mocks.getNextArrivals.mockResolvedValue([ARRIVAL]);
    const send = vi.fn().mockRejectedValue(new Error('chat bloqueado'));
    setBotInstance(fakeBot(send));

    await triggerAlertCheck();
    await triggerAlertCheck();
    await triggerAlertCheck();

    expect(await getAlertById(alert.id)).toMatchObject({
      active: false,
      sendFailures: 3,
    });
    const attempts = send.mock.calls.length;
    expect(attempts).toBe(3);

    await triggerAlertCheck();
    expect(send).toHaveBeenCalledTimes(attempts);
  });

  it('prefiere la estimación GPS y filtra por destino', async () => {
    const alert = await newAlert();
    mocks.getNextArrivals.mockResolvedValue([
      { ...ARRIVAL, lineId: '21', minutes: 1 },
      ARRIVAL,
      { ...ARRIVAL, minutes: 1, realtime: false },
    ]);
    const send = vi.fn().mockResolvedValue({});
    setBotInstance(fakeBot(send));

    await triggerAlertCheck();

    expect(send).toHaveBeenCalledTimes(1);
    const message = String(send.mock.calls[0][1]);
    expect(message).toContain('L28');
    expect(message).toContain('tiempo real');
    expect((await getAlertById(alert.id))?.active).toBe(false);
  });

  it('no escribe el chat_id en claro en los logs (H19)', async () => {
    await newAlert();
    mocks.getNextArrivals.mockResolvedValue([ARRIVAL]);
    const lines: string[] = [];
    vi.spyOn(logger, 'info').mockImplementation((...args) => {
      lines.push(args.join(' '));
    });
    vi.spyOn(logger, 'error').mockImplementation((...args) => {
      lines.push(args.join(' '));
    });

    setBotInstance(fakeBot(vi.fn().mockResolvedValue({})));
    await triggerAlertCheck(); // envío correcto: el aviso queda inactivo
    await newAlert();
    setBotInstance(fakeBot(vi.fn().mockRejectedValue(new Error('x'))));
    await triggerAlertCheck(); // envío fallido

    expect(lines.length).toBeGreaterThanOrEqual(2);
    for (const line of lines) expect(line).not.toContain(String(CHAT));
  });
});
