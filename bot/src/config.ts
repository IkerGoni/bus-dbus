import { z } from 'zod';

export const configSchema = z.object({
  botToken: z.string().min(1),
  fetcherUrl: z.string().url().default('http://localhost:3002'),
  pollIntervalMs: z.number().int().positive().default(30000),
  defaultAlertMinutes: z.number().int().positive().default(5),
  dbPath: z.string().default('./bot.db'),
});

export const config = configSchema.parse({
  botToken: process.env.BOT_TOKEN,
  fetcherUrl: process.env.FETCHER_URL,
  pollIntervalMs: process.env.POLL_INTERVAL_MS
    ? Number.parseInt(process.env.POLL_INTERVAL_MS, 10)
    : 30000,
  defaultAlertMinutes: process.env.DEFAULT_ALERT_MINUTES
    ? Number.parseInt(process.env.DEFAULT_ALERT_MINUTES, 10)
    : 5,
  dbPath: process.env.DB_PATH || './bot.db',
});

export type Config = z.infer<typeof configSchema>;
