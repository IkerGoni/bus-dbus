/**
 * Logger mínimo con niveles. `LOG_LEVEL` (debug | info | warn | error | silent),
 * por defecto `info`. Los textos de los mensajes no cambian: los tests y el
 * log de producción siguen encontrando las mismas cadenas.
 *
 * No se imprime ningún secreto: el token y las coordenadas nunca llegan aquí.
 */

export type LogLevel = 'debug' | 'info' | 'warn' | 'error' | 'silent';

const LEVELS: Record<LogLevel, number> = {
  debug: 10,
  info: 20,
  warn: 30,
  error: 40,
  silent: 100,
};

function parseLevel(value: string | undefined): LogLevel {
  const candidate = (value ?? '').trim().toLowerCase();
  return candidate in LEVELS ? (candidate as LogLevel) : 'info';
}

export const logLevel: LogLevel = parseLevel(process.env.LOG_LEVEL);

function emit(level: LogLevel, args: unknown[]): void {
  if (LEVELS[level] < LEVELS[logLevel]) return;
  if (level === 'error') console.error(...args);
  else if (level === 'warn') console.warn(...args);
  else console.log(...args);
}

export const logger = {
  level: logLevel,
  debug: (...args: unknown[]) => emit('debug', args),
  info: (...args: unknown[]) => emit('info', args),
  warn: (...args: unknown[]) => emit('warn', args),
  error: (...args: unknown[]) => emit('error', args),
};
