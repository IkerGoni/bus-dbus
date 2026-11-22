import { Telegraf } from 'telegraf';
import { stopAlertScheduler } from './alerts.js';
import { config } from './config.js';
import { getActiveAlerts } from './db.js';
import {
  fetchStopTimes,
  getCachedStopCodes,
  getDataSource,
} from './dbus-client.js';
import { setupBot } from './handlers.js';
import { logger } from './logger.js';
import { acquireInstanceLock, releaseInstanceLock } from './single-instance.js';
import { launchWithRetry } from './telegram-launch.js';

if (!acquireInstanceLock()) {
  logger.error(
    '❌ Ya hay otra instancia del bot en ejecución. ' +
      'Detenla antes de arrancar otra (Telegram solo permite un getUpdates).'
  );
  process.exit(1);
}
process.once('exit', () => releaseInstanceLock());

const bot = new Telegraf(config.botToken);

// Los flujos usan su propio mapa por chat (session.ts); no hace falta el
// middleware de sesión de Telegraf, que nadie lee. setupBot ya fija la_ref.
setupBot(bot);

// Telegraf no convierte un throw en un error manejado: sin esto, un fallo de
// red o de parseo queda como unhandled rejection y puede tumbar el proceso.
bot.catch((error, ctx) => {
  const detail =
    error instanceof Error ? (error.stack ?? error.message) : String(error);
  logger.error(
    `❌ Unhandled handler error (update ${ctx.update.update_id}): ${detail}`
  );
  void ctx
    .reply(
      '⚠️ Ha ocurrido un error al procesar tu solicitud. Inténtalo de nuevo.'
    )
    .catch(() => {
      // El chat puede estar bloqueado: el log ya dejó constancia.
    });
});

const REFRESH_CONCURRENCY = 6;
const MAX_REFRESH_STOPS = 80;

/**
 * Refresca las paradas en caché y las de los avisos activos.
 *
 * Concurrencia acotada y guard anti-solapamiento: un ciclo secuencial de hasta
 * 200 paradas × 2 intentos × 10 s desborda los 30 s del intervalo y los
 * `setInterval` se pisaban. Las paradas con aviso van primero.
 */
function startLiveRefresh(intervalMs: number): () => void {
  let running = false;

  async function refreshOnce(): Promise<void> {
    if (running) return;
    running = true;
    try {
      const alertStops = getActiveAlerts().map((alert) => alert.stopId);
      const recentCached = getCachedStopCodes().reverse();
      const codes = [...new Set([...alertStops, ...recentCached])].slice(
        0,
        MAX_REFRESH_STOPS
      );
      let cursor = 0;
      const workers = Array.from(
        { length: Math.min(REFRESH_CONCURRENCY, codes.length) },
        async () => {
          while (cursor < codes.length) {
            const code = codes[cursor++];
            try {
              await fetchStopTimes(code, true);
            } catch (error) {
              logger.error(
                `[Refresh] ${code}: ${error instanceof Error ? error.message : String(error)}`
              );
            }
          }
        }
      );
      await Promise.all(workers);
    } finally {
      running = false;
    }
  }

  const timer = setInterval(() => {
    // getActiveAlerts()/getCachedStopCodes() pueden fallar (SQLite bloqueado):
    // una promise rechazada sin catch tumbaría el proceso en Node >=15.
    void refreshOnce().catch((error: unknown) => {
      logger.error(
        `[Refresh] Ciclo fallido: ${error instanceof Error ? error.message : String(error)}`
      );
    });
  }, intervalMs);
  timer.unref();
  return () => clearInterval(timer);
}

const stopRefresh = startLiveRefresh(config.pollIntervalMs);

function onLaunched(): void {
  // El callback se dispara tras getMe; launch().then() no llega hasta que
  // termina el long-polling, así que el log debe ir aquí.
  logger.info('🤖 Dbus Telegram Bot started');
  logger.info(`📡 Live data: ${getDataSource()}`);
  logger.info(`🔄 Refreshing every ${config.pollIntervalMs / 1000}s`);
}

// Reintenta solo conflictos 409: la lógica vive en telegram-launch.ts y es
// testeable sin importar este composition root.
void launchWithRetry(() => bot.launch({}, onLaunched)).catch(
  (error: unknown) => {
    logger.error(
      'Failed to start bot:',
      error instanceof Error ? error.message : String(error)
    );
    process.exit(1);
  }
);

function shutdown(signal: string): void {
  logger.info(`\n🛑 ${signal} — stopping bot`);
  stopRefresh();
  stopAlertScheduler();
  bot.stop(signal);
}

process.once('SIGINT', () => shutdown('SIGINT'));
process.once('SIGTERM', () => shutdown('SIGTERM'));

export { bot };
