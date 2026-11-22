/**
 * Normalizadores tolerantes a que la API de Dbus devuelva `null` en cualquier
 * campo. Puro: no hace red, no lee configuración.
 */
import type {
  DbusItinerario,
  DbusLinea,
  DbusParada,
  DbusStopTimes,
  DbusTiempo,
} from './types.js';

function obj(value: unknown): Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : {};
}

function str(value: unknown): string {
  if (typeof value === 'string') return value;
  if (typeof value === 'number' || typeof value === 'bigint')
    return String(value);
  return '';
}

function num(value: unknown): number {
  const parsed = typeof value === 'number' ? value : Number(value);
  return Number.isFinite(parsed) ? parsed : 0;
}

function arr(value: unknown): unknown[] {
  return Array.isArray(value) ? value : [];
}

export function normalizeLinea(value: unknown): DbusLinea {
  const raw = obj(value);
  return {
    id: str(raw.id),
    cod: str(raw.cod),
    nombre: str(raw.nombre),
    empresa: raw.empresa == null ? undefined : str(raw.empresa),
    color: raw.color == null ? undefined : num(raw.color),
  };
}

export function normalizeItinerario(value: unknown): DbusItinerario {
  const raw = obj(value);
  return {
    id: str(raw.id),
    cod: str(raw.cod),
    destino: str(raw.destino),
    nombre: str(raw.nombre),
    linea: normalizeLinea(raw.linea),
  };
}

export function normalizeParada(value: unknown): DbusParada | null {
  if (value == null) return null;
  const raw = obj(value);
  const cod = str(raw.cod);
  if (!cod) return null;
  return { id: str(raw.id), cod, desc: str(raw.desc).trim() };
}

export function normalizeTiempo(value: unknown): DbusTiempo | null {
  if (value == null) return null;
  const raw = obj(value);
  const itinerario = normalizeItinerario(raw.itinerario);
  if (!itinerario.id && !itinerario.linea.cod) return null;
  return {
    itinerario,
    creacion: str(raw.creacion),
    minutos: num(raw.minutos),
    cabecera: Boolean(raw.cabecera),
    tipo: num(raw.tipo),
    hora: str(raw.hora),
  };
}

/** Convierte la respuesta cruda de `tiemposParada` en datos de dominio. */
export function parseStopTimes(value: unknown): DbusStopTimes | null {
  if (value == null || typeof value !== 'object') return null;
  const raw = obj(value);
  const estado = str(raw.estado);
  if (!estado) return null;

  const tiempos = arr(raw.tiempos)
    .map(normalizeTiempo)
    .filter((t): t is DbusTiempo => t !== null);

  const avisos = arr(raw.avisos)
    .map((aviso) => str(aviso).trim())
    .filter(Boolean);

  return {
    estado,
    parada: normalizeParada(raw.parada),
    tiempos,
    creacion: raw.creacion == null ? null : str(raw.creacion),
    avisos,
  };
}
