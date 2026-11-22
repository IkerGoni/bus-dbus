import cron from 'node-cron';
import type { Context, Telegraf } from 'telegraf';
import { escapeMd, formatArrival, getNextArrivals } from './arrival.js';
import { getActiveAlerts, updateAlert } from './db.js';
import type { UserAlert } from './types.js';

let scheduledTask: cron.ScheduledTask | null = null;
let botInstance: Telegraf<Context> | null = null;

export function setBotInstance(bot: Telegraf<Context>): void {
  botInstance = bot;
}

export function startAlertScheduler(): void {
  if (scheduledTask) return;
  scheduledTask = cron.schedule('* * * * *', () => {
    void checkAlerts();
  });
  console.log('📅 Alert scheduler started (checking every minute)');
}

export function stopAlertScheduler(): void {
  if (scheduledTask) {
    scheduledTask.stop();
    scheduledTask = null;
    console.log('📅 Alert scheduler stopped');
  }
}

async function checkAlerts(): Promise<void> {
  if (!botInstance) {
    console.warn('Bot instance not set, skipping alert check');
    return;
  }

  const alerts = getActiveAlerts();
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
        const match = arrivals.find(
          (arrival) =>
            arrival.lineId === alert.lineId &&
            arrival.minutes >= 0 &&
            arrival.minutes <= alert.minutesAhead
        );
        if (!match) continue;

        const msg =
          `🔔 **¡Aviso! L${escapeMd(alert.lineId)} llega en ${match.minutes} min**\n` +
          `${formatArrival(match)}`;

        await botInstance.telegram.sendMessage(alert.chatId, msg, {
          parse_mode: 'Markdown',
        });
        updateAlert(alert.id, { active: false });
        console.log(`✅ Alert ${alert.id} triggered for chat ${alert.chatId}`);
      }
    } catch (error) {
      console.error(
        `[Alerts] Error checking stop ${stopId}: ${error instanceof Error ? error.message : String(error)}`
      );
    }
  }
}

export async function triggerAlertCheck(): Promise<void> {
  await checkAlerts();
}
