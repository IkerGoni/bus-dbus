/**
 * Normalización de identificadores de línea.
 *
 * Dbus publica la línea 28 con id interno 32. El bot acepta ambos como alias,
 * pero siempre guarda y muestra el código público.
 */

/**
 * Normaliza un identificador de línea introducido por el usuario.
 * Acepta `28`, `L28` y `028`.
 */
export function normalizeLine(lineId: string): string {
  return lineId
    .trim()
    .toUpperCase()
    .replace(/^L/, '')
    .replace(/^0+(?=\d)/, '');
}

/** Comprueba si una llegada pertenece a la línea indicada (pública o interna). */
export function matchesLine(
  arrival: { lineId: string; internalLineId: string },
  lineId: string
): boolean {
  const wanted = normalizeLine(lineId);
  return Boolean(
    wanted &&
      (normalizeLine(arrival.lineId) === wanted ||
        normalizeLine(arrival.internalLineId) === wanted)
  );
}
