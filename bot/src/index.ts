import { Telegraf, session } from 'telegraf';
import { stopAlertScheduler } from './alerts.js';
import { config } from './config.js';
import { getActiveAlerts } from './db.js';
import {
  fetchStopTimes,
  getCachedStopCodes,
  getDataSource,
} from './dbus-client.js';
import { setBotReference, setupBot } from './handlers.js';

const bot = new Telegraf(config.botToken);

// Middleware de sesión (los flujos usan además un mapa propio por chat).
bot.use(session());

setBotReference(bot);
setupBot(bot);

/** Refresca las paradas en caché y las de los avisos activos. */
function startLiveRefresh(intervalMs: number): () => void {
  const timer = setInterval(async () => {
    const codes = new Set<string>([
      ...getCachedStopCodes(),
      ...getActiveAlerts().map((alert) => alert.stopId),
    ]);
    for (const code of codes) {
      await fetchStopTimes(code, true);
    }
  }, intervalMs);
  timer.unref();
  return () => clearInterval(timer);
}

const stopRefresh = startLiveRefresh(config.pollIntervalMs);

function onLaunched(): void {
  // El callback se dispara tras getMe; launch().then() no llega hasta que
  // termina el long-polling, así que el log debe ir aquí.
  console.log('🤖 Dbus Telegram Bot started');
  console.log(`📡 Live data: ${getDataSource()}`);
  console.log(`🔄 Refreshing every ${config.pollIntervalMs / 1000}s`);
}

/**
 * Telegram solo permite un getUpdates long-polling a la vez. Al reiniciar,
 * la conexión anterior puede tardar unos segundos en liberarse (409).
 * Reintentamos con backoff en vez de morir.
 */
async function launchWithRetry(maxAttempts = 6): Promise<void> {
  for (let attempt = 1; attempt <= maxAttempts; attempt++) {
    try {
      await bot.launch({}, onLaunched);
      return;
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      const isConflict =
        message.includes('409') || message.includes('Conflict');
      if (!isConflict || attempt === maxAttempts) throw error;
      const delay = Math.min(2000 * 2 ** (attempt - 1), 15_000);
      console.warn(
        `[Telegram] Polling conflict (attempt ${attempt}/${maxAttempts}); retrying in ${delay / 1000}s`
      );
      await new Promise((resolve) => setTimeout(resolve, delay));
    }
  }
}

void launchWithRetry().catch((error: unknown) => {
  console.error(
    'Failed to start bot:',
    error instanceof Error ? error.message : String(error)
  );
  process.exit(1);
});

function shutdown(signal: string): void {
  console.log(`\n🛑 ${signal} — stopping bot`);
  stopRefresh();
  stopAlertScheduler();
  bot.stop(signal);
}

process.once('SIGINT', () => shutdown('SIGINT'));
process.once('SIGTERM', () => shutdown('SIGTERM'));

export { bot };
