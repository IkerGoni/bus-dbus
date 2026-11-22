import { z } from 'zod';

/**
 * Configuración del bot.
 *
 * La fuente de datos es la API JSON pública que usa la app oficial de Dbus:
 *   http://app.dbus.eus/SSIIMovilWSv2/ws/cons
 * (HTTP, sin autenticación. HTTPS no responde en este despliegue.)
 */
export const configSchema = z.object({
  botToken: z.string().min(1),
  dbusBaseUrl: z
    .string()
    .url()
    .default('http://app.dbus.eus/SSIIMovilWSv2/ws/cons'),
  language: z.enum(['eu', 'es', 'en', 'fr']).default('es'),
  cacheTtlMs: z.number().int().positive().default(30_000),
  pollIntervalMs: z.number().int().positive().default(30_000),
  defaultAlertMinutes: z.number().int().positive().default(5),
  dbPath: z.string().default('./bot.db'),
});

export const config = configSchema.parse({
  botToken: process.env.BOT_TOKEN,
  dbusBaseUrl: process.env.DBUS_BASE_URL || undefined,
  language: process.env.BOT_LANGUAGE || 'es',
  cacheTtlMs: process.env.CACHE_TTL_MS
    ? Number.parseInt(process.env.CACHE_TTL_MS, 10)
    : 30_000,
  pollIntervalMs: process.env.POLL_INTERVAL_MS
    ? Number.parseInt(process.env.POLL_INTERVAL_MS, 10)
    : 30_000,
  defaultAlertMinutes: process.env.DEFAULT_ALERT_MINUTES
    ? Number.parseInt(process.env.DEFAULT_ALERT_MINUTES, 10)
    : 5,
  dbPath: process.env.DB_PATH || './bot.db',
});

export type Config = z.infer<typeof configSchema>;
