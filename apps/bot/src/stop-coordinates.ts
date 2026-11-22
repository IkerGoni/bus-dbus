/**
 * Índice local de coordenadas de paradas Dbus, generado una vez por
 * `apps/bot/scripts/` (sesión A). Se lee de disco: sin red en caliente, sin
 * relleno y sin persistir la ubicación del usuario.
 *
 * Ruta por defecto: apps/bot/data/dbus-stop-coordinates.json
 * Configurable con DBUS_STOP_COORDINATES_PATH (útil en tests).
 *
 * Un archivo ausente, corrupto o que no cumple el contrato es un error
 * explícito: nunca se devuelven coordenadas inventadas.
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { z } from 'zod';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

export const DEFAULT_COORDINATES_PATH = path.resolve(
  __dirname,
  '../data/dbus-stop-coordinates.json'
);

/** Contrato del JSON producido por la sesión A. */
export const stopCoordinatesSchema = z.object({
  generatedAt: z.string().min(1),
  source: z.string().min(1),
  attribution: z.string().min(1),
  coverage: z.object({
    catalogStops: z.number().int().nonnegative(),
    withCoordinates: z.number().int().nonnegative(),
    withoutCoordinates: z.number().int().nonnegative(),
  }),
  stops: z.array(
    z.object({
      cod: z.string().min(1),
      lat: z.number().finite().min(-90).max(90),
      lon: z.number().finite().min(-180).max(180),
    })
  ),
});

export type StopCoordinates = z.infer<typeof stopCoordinatesSchema>;
export type StopCoordinate = StopCoordinates['stops'][number];

/** Error de carga legible para el usuario; nunca contiene coordenadas del usuario. */
export class StopCoordinatesError extends Error {
  constructor(message: string, options?: { cause?: unknown }) {
    super(message, options);
    this.name = 'StopCoordinatesError';
  }
}

export function coordinatesPath(): string {
  return process.env.DBUS_STOP_COORDINATES_PATH || DEFAULT_COORDINATES_PATH;
}

let cached: { path: string; value: StopCoordinates } | null = null;

/** Solo para tests: fuerza una nueva lectura del archivo configurado. */
export function resetStopCoordinatesCache(): void {
  cached = null;
}

/** Lee y valida el índice. Carga única por ruta; falla alto si está mal. */
export function loadStopCoordinates(
  filePath: string = coordinatesPath()
): StopCoordinates {
  if (cached?.path === filePath) return cached.value;

  let raw: string;
  try {
    raw = fs.readFileSync(filePath, 'utf8');
  } catch (error) {
    const code = (error as NodeJS.ErrnoException).code;
    if (code === 'ENOENT') {
      throw new StopCoordinatesError(
        `Índice de coordenadas no encontrado (${filePath}).`,
        { cause: error }
      );
    }
    throw new StopCoordinatesError(
      `No se pudo leer el índice de coordenadas (${filePath}).`,
      { cause: error }
    );
  }

  let parsed: unknown;
  try {
    parsed = JSON.parse(raw);
  } catch (error) {
    throw new StopCoordinatesError(
      `Índice de coordenadas corrupto: JSON inválido (${filePath}).`,
      { cause: error }
    );
  }

  const result = stopCoordinatesSchema.safeParse(parsed);
  if (!result.success) {
    const detail = result.error.issues
      .slice(0, 3)
      .map((issue) => issue.path.join('.') || 'raíz')
      .join(', ');
    throw new StopCoordinatesError(
      `Índice de coordenadas con formato inválido (${detail}).`
    );
  }

  cached = { path: filePath, value: result.data };
  return result.data;
}
