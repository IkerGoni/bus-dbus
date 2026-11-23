import cron from 'node-cron';
import type { Context, Telegraf } from 'telegraf';
import {
  escapeMd,
  findMatchingArrival,
  formatArrival,
  getNextArrivals,
} from './arrival.js';
import { deactivateAlert, getActiveAlerts, updateAlert } from './db.js';
import { logger } from './logger.js';
import type { UserAlert } from './types.js';

const MAX_ALERT_AGE_MS = 7 * 24 * 60 * 60 * 1000;
/** Un chat que bloquea al bot deja de generar reintentos cada minuto. */
const MAX_SEND_FAILURES = 3;

let sentCount = 0;
let failedCount = 0;

/** Contadores de este proceso, para diagnóstico en /estado. */
export function getAlertStats(): { sent: number; failed: number } {
  return { sent: sentCount, failed: failedCount };
}
let scheduledTask: cron.ScheduledTask | null = null;
let botInstance: Telegraf<Context> | null = null;
let checking = false;

export function setBotInstance(bot: Telegraf<Context>): void {
  botInstance = bot;
}

export function startAlertScheduler(): void {
  if (scheduledTask) return;
  scheduledTask = cron.schedule('* * * * *', () => {
    // checkAlerts() tiene try/finally para el guard, pero no catch: un fallo
    // de SQLite al leer avisos debe loguearse, no convertirse en unhandled
    // rejection que mate el proceso.
    void checkAlerts().catch((error: unknown) => {
      logger.error(
        `[Alerts] Revisión fallida: ${error instanceof Error ? error.message : String(error)}`
      );
    });
  });
  logger.info('📅 Alert scheduler started (checking every minute)');
}

export function stopAlertScheduler(): void {
  if (scheduledTask) {
    scheduledTask.stop();
    scheduledTask = null;
    logger.info('📅 Alert scheduler stopped');
  }
}

async function checkAlerts(): Promise<void> {
  // node-cron puede disparar otra pasada antes de que termine la anterior
  // (muchas paradas × latencia de API). Sin este guard se enviaría dos veces.
  if (checking) return;
  checking = true;
  try {
    await checkAlertsUnsafe();
  } finally {
    checking = false;
  }
}

async function checkAlertsUnsafe(): Promise<void> {
  if (!botInstance) {
    logger.warn('Bot instance not set, skipping alert check');
    return;
  }

  const now = Date.now();
  const allAlerts = await getActiveAlerts();
  const alerts = allAlerts.filter((alert) => {
    if (now - alert.createdAt <= MAX_ALERT_AGE_MS) return true;
    void updateAlert(alert.id, { active: false });
    logger.info(`🧹 Alert ${alert.id} expired (older than 7 days)`);
    return false;
  });
  if (!alerts.length) return;

  // Agrupar por parada para minimizar peticiones a la API.
  const byStop = new Map<string, UserAlert[]>();
  for (const alert of alerts) {
    const group = byStop.get(alert.stopId);
    if (group) group.push(alert);
    else byStop.set(alert.stopId, [alert]);
  }

  for (const [stopId, stopAlerts] of byStop) {
    try {
      const arrivals = await getNextArrivals(stopId);
      for (const alert of stopAlerts) {
        const match = findMatchingArrival(arrivals, {
          lineId: alert.lineId,
          destination: alert.destination,
          minutesAhead: alert.minutesAhead,
        });
        if (!match) continue;

        const msg =
          `🔔 **¡Aviso! L${escapeMd(alert.lineId)} llega en ${match.minutes} min**\n` +
          `${formatArrival(match)}`;

        try {
          await botInstance.telegram.sendMessage(alert.chatId, msg, {
            parse_mode: 'Markdown',
          });
          await updateAlert(alert.id, { active: false, sendFailures: 0 });
          sentCount++;
          // Sin chat_id en claro (H19): el id del aviso basta para seguirlo.
          logger.info(`✅ Alert ${alert.id} triggered`);
        } catch (error) {
          // Un chat bloqueado no debe impedir avisar al resto ni repetir
          // el mismo fallo cada minuto hasta la caducidad del aviso.
          const failures = alert.sendFailures + 1;
          failedCount++;
          await updateAlert(alert.id, {
            sendFailures: failures,
            active: failures < MAX_SEND_FAILURES,
          });
          logger.error(
            `[Alerts] Send failed for alert ${alert.id} ` +
              `(${failures}/${MAX_SEND_FAILURES}): ` +
              `${error instanceof Error ? error.message : String(error)}`
          );
        }
      }
    } catch (error) {
      logger.error(
        `[Alerts] Error checking stop ${stopId}: ${error instanceof Error ? error.message : String(error)}`
      );
    }
  }
}

export async function triggerAlertCheck(): Promise<void> {
  await checkAlerts();
}
