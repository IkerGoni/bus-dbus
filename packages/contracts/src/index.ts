/**
 * Contratos compartidos entre productos (bot + PWA).
 *
 * Solo dependen de `zod`. Nada de DOM, Node ni React: este paquete es la
 * especificación que sobrevive a una reescritura nativa.
 */
import { z } from 'zod';

export const StopSchema = z.object({
  id: z.string().min(1),
  code: z.string().min(1),
  name: z.string().min(1),
  lat: z.number().optional(),
  lon: z.number().optional(),
  /** Líneas que sirven la parada, según el catálogo de descubrimiento. */
  lines: z.array(z.string()).optional(),
  /** Destinos reales vistos en la parada, para búsqueda por texto. */
  destinations: z.array(z.string()).optional(),
});
export type Stop = z.infer<typeof StopSchema>;

export const LineSchema = z.object({
  id: z.string().min(1),
  code: z.string().min(1),
  name: z.string(),
  /** Color entero de Dbus (p. ej. -811520). `null` si no viene. */
  color: z.number().nullable(),
});
export type Line = z.infer<typeof LineSchema>;

export const ArrivalSchema = z.object({
  lineCode: z.string().min(1),
  destination: z.string().min(1),
  minutes: z.number().int().min(0),
  /** epoch ms calculado a partir de los minutos que devuelve Dbus. */
  expectedAt: z.number().int(),
  source: z.enum(['realtime', 'scheduled']),
});
export type ContractArrival = z.infer<typeof ArrivalSchema>;

export const AlertSpecSchema = z.object({
  id: z.string().min(1),
  stopId: z.string().min(1),
  stopName: z.string(),
  lineId: z.string(),
  lineCode: z.string().min(1),
  destination: z.string(),
  minutesAhead: z.number().int().min(1).max(60),
  active: z.boolean(),
  createdAt: z.number().int(),
});
export type AlertSpec = z.infer<typeof AlertSpecSchema>;

/** Entrada del catálogo de descubrimiento (índice, nunca horarios). */
export const CatalogStopSchema = z.object({
  cod: z.string().min(1),
  desc: z.string().min(1),
  lines: z.array(z.string()),
  destinations: z.array(z.string()),
});
export type CatalogStop = z.infer<typeof CatalogStopSchema>;

export const CatalogLineSchema = z.object({
  id: z.string().min(1),
  cod: z.string().min(1),
  destinations: z.array(z.string()),
  stops: z.array(z.string()),
});
export type CatalogLine = z.infer<typeof CatalogLineSchema>;

export const CatalogItinerarySchema = z.object({
  id: z.string().min(1),
  lineId: z.string().min(1),
  lineCod: z.string().min(1),
  destination: z.string(),
  stops: z.array(z.string()),
});
export type CatalogItinerary = z.infer<typeof CatalogItinerarySchema>;

export const CatalogSchema = z.object({
  generatedAt: z.string(),
  source: z.string(),
  attribution: z.string(),
  lines: z.array(CatalogLineSchema),
  itineraries: z.array(CatalogItinerarySchema),
  stops: z.array(CatalogStopSchema),
});
export type Catalog = z.infer<typeof CatalogSchema>;
