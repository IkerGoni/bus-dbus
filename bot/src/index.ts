import { Telegraf, session } from 'telegraf';
import { config } from './config.js';
import { startRTPolling } from './gtfs-client.js';
import { setBotReference, setupBot } from './handlers.js';

const bot = new Telegraf(config.botToken);

// Session middleware para flujo interactivo
bot.use(session());

// Setup handlers
setBotReference(bot);
setupBot(bot);

// Iniciar bot
bot
  .launch()
  .then(() => {
    console.log('🤖 Dbus Telegram Bot started');
    console.log('   Token:', `${config.botToken.slice(0, 10)}...`);
    console.log('   Polling GTFS-RT every', config.pollIntervalMs / 1000, 's');
  })
  .catch((err) => {
    console.error('Failed to start bot:', err);
    process.exit(1);
  });

// Graceful shutdown
process.once('SIGINT', () => bot.stop('SIGINT'));
process.once('SIGTERM', () => bot.stop('SIGTERM'));

export { bot };
