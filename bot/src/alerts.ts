import cron from 'node-cron';
import type { Context, Telegraf } from 'telegraf';
import { formatArrival, getCachedArrivals } from './arrival.js';
import { getActiveAlerts, updateAlert } from './db.js';
import type { NextArrivalResult, UserAlert } from './types.js';

let scheduledTask: cron.ScheduledTask | null = null;
let botInstance: Telegraf<Context> | null = null;

export function setBotInstance(bot: Telegraf<Context>) {
  botInstance = bot;
}

export function startAlertScheduler() {
  if (scheduledTask) return;

  // Cada minuto comprobar alertas
  scheduledTask = cron.schedule('* * * * *', async () => {
    await checkAlerts();
  });

  console.log('📅 Alert scheduler started (checking every minute)');
}

export function stopAlertScheduler() {
  if (scheduledTask) {
    scheduledTask.stop();
    scheduledTask = null;
    console.log('📅 Alert scheduler stopped');
  }
}

async function checkAlerts() {
  if (!botInstance) {
    console.warn('Bot instance not set, skipping alert check');
    return;
  }

  const alerts = getActiveAlerts();
  if (!alerts.length) return;

  for (const alert of alerts) {
    try {
      const arrivalResult = getCachedArrivals(alert.stopId, alert.lineId);
      if (!arrivalResult) continue;

      // getCachedArrivals con lineId devuelve NextArrivalResult | null
      const arrival = arrivalResult as NextArrivalResult;

      const minsUntilArrival = Math.round(
        (arrival.arrivalTime - Date.now()) / 1000 / 60
      );

      // Avisar si llega en <= minutesAhead minutos
      if (minsUntilArrival <= alert.minutesAhead && minsUntilArrival >= 0) {
        const msg = `🔔 **Aviso: L${alert.lineId} llega en ${minsUntilArrival} min**\n${formatArrival(arrival)}`;

        await botInstance.telegram.sendMessage(alert.chatId, msg, {
          parse_mode: 'Markdown',
        });

        // Desactivar alerta tras avisar (one-shot)
        updateAlert(alert.id, { active: false });
        console.log(`✅ Alert ${alert.id} triggered for chat ${alert.chatId}`);
      }
    } catch (err) {
      console.error(`Error checking alert ${alert.id}:`, err);
    }
  }
}

// Para testing manual
export async function triggerAlertCheck() {
  await checkAlerts();
}
