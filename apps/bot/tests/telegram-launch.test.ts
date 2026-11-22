import { describe, expect, it, vi } from 'vitest';
import { isTelegramConflict, launchWithRetry } from '../src/telegram-launch.js';

const noSleep = vi.fn(async () => {});

describe('launchWithRetry (regla 4: retry de 409)', () => {
  it('reintenta los conflictos 409 y vuelve cuando Telegram libera el polling', async () => {
    const launch = vi
      .fn<() => Promise<void>>()
      .mockRejectedValueOnce(
        new Error('409: Conflict: terminated by another getUpdates')
      )
      .mockRejectedValueOnce(new Error('409: Conflict'))
      .mockResolvedValueOnce(undefined);
    const sleep = vi.fn(async () => {});

    await launchWithRetry(launch, 5, sleep);

    expect(launch).toHaveBeenCalledTimes(3);
    expect(sleep).toHaveBeenCalledTimes(2);
    expect(sleep.mock.calls[0][0]).toBe(2000);
    expect(sleep.mock.calls[1][0]).toBe(4000);
  });

  it('no reintent errores que no son 409', async () => {
    const launch = vi
      .fn<() => Promise<void>>()
      .mockRejectedValue(new Error('network down'));
    await expect(launchWithRetry(launch, 5, noSleep)).rejects.toThrow(
      'network down'
    );
    expect(launch).toHaveBeenCalledTimes(1);
  });

  it('agota los intentos y propaga el último conflicto', async () => {
    const launch = vi
      .fn<() => Promise<void>>()
      .mockRejectedValue(new Error('409: Conflict'));
    await expect(launchWithRetry(launch, 3, noSleep)).rejects.toThrow('409');
    expect(launch).toHaveBeenCalledTimes(3);
  });

  it('reconoce el conflict tanto por código como por texto', () => {
    expect(isTelegramConflict(new Error('409: Conflict'))).toBe(true);
    expect(isTelegramConflict(new Error('Conflict: terminated'))).toBe(true);
    expect(isTelegramConflict(new Error('ETIMEDOUT'))).toBe(false);
    expect(isTelegramConflict('409')).toBe(true);
  });
});
