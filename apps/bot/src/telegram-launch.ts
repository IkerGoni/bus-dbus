/**
 * Arranque del long-polling con reintento de conflictos 409 (regla 4).
 *
 * Extraído de `index.ts` para poder probarlo sin importar el composition root
 * (que abre el lock, arranca timers y llama a Telegram al importarse).
 */
import { logger } from './logger.js';

export function isTelegramConflict(error: unknown): boolean {
  const message = error instanceof Error ? error.message : String(error);
  return message.includes('409') || message.includes('Conflict');
}

export async function launchWithRetry(
  launch: () => Promise<void>,
  maxAttempts = 12,
  sleep: (ms: number) => Promise<void> = (ms) =>
    new Promise((resolve) => setTimeout(resolve, ms))
): Promise<void> {
  for (let attempt = 1; attempt <= maxAttempts; attempt++) {
    try {
      await launch();
      return;
    } catch (error) {
      if (!isTelegramConflict(error) || attempt === maxAttempts) throw error;
      const delay = Math.min(2000 * 2 ** (attempt - 1), 30_000);
      logger.warn(
        `[Telegram] Polling conflict (attempt ${attempt}/${maxAttempts}); retrying in ${delay / 1000}s`
      );
      await sleep(delay);
    }
  }
}
