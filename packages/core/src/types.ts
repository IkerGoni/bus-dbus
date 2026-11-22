/**
 * Tipos de dominio de Dbus.
 *
 * Sin dependencias de Node, DOM ni React: este paquete se ejecuta igual en el
 * bot (Node) y en la PWA (navegador / WebView).
 */

export type Language = 'eu' | 'es' | 'en' | 'fr';

export interface DbusLinea {
  id: string;
  cod: string;
  nombre: string;
  empresa?: string;
  /** Color entero de Dbus (p. ej. -811520). */
  color?: number;
}

export interface DbusItinerario {
  id: string;
  cod: string;
  destino: string;
  nombre: string;
  linea: DbusLinea;
}

export interface DbusTiempo {
  itinerario: DbusItinerario;
  creacion: string;
  /** Minutos hasta la llegada (valor calculado por Dbus, ya incluye GPS si aplica). */
  minutos: number;
  /** true = cabecera de línea. */
  cabecera: boolean;
  /** 1 = estimación GPS en tiempo real, 2 = horario programado. */
  tipo: number;
  /** Hora absoluta prevista, "HH:MM". */
  hora: string;
}

export interface DbusParada {
  id: string;
  cod: string;
  desc: string;
}

export interface DbusStopTimes {
  estado: string;
  parada: DbusParada | null;
  tiempos: DbusTiempo[];
  /** Marca de tiempo del response ("DD/MM/YYYY HH:MM:SS"). */
  creacion: string | null;
  /** Avisos de servicio reales de Dbus (p. ej. motivo de SIN_SERVICIO). */
  avisos?: string[] | null;
}

export interface CacheEntry<T> {
  data: T;
  ts: number;
}

/** Llegada normalizada, lista para mostrar o disparar un aviso. */
export interface Arrival {
  /** epoch ms calculado a partir de los minutos que devuelve Dbus. */
  arrivalTime: number;
  /** minutos hasta la llegada (0 = llegando). */
  minutes: number;
  /** código público de línea, p. ej. "28". */
  lineId: string;
  /** id interno de Dbus, p. ej. "32" para la línea pública 28. */
  internalLineId: string;
  /** destino/headsign real devuelto por Dbus. */
  destination: string;
  stopId: string;
  stopName: string;
  /** true si la estimación viene del GPS (tipo=1), false si es horario (tipo=2). */
  realtime: boolean;
  /** hora absoluta prevista "HH:MM". */
  hora: string;
  itineraryId: string;
}

/**
 * Línea que pasa por una parada.
 *
 * `lineId`/`internalLineId` son la forma histórica del bot; `code`/`id` son los
 * nombres del contrato compartido. Ambos apuntan al mismo valor.
 */
export interface Line {
  /** Código público ("28"). */
  lineId: string;
  /** Id interno de Dbus ("32"). */
  internalLineId: string;
  /** Código público, alias de `lineId` (contrato `LineSchema`). */
  code: string;
  /** Id interno, alias de `internalLineId` (contrato `LineSchema`). */
  id: string;
  name: string;
  /** Color entero de la línea, `null` si la API no lo devuelve. */
  color: number | null;
  destination: string;
}

export interface AlertMatchOptions {
  /** Código público o id interno de Dbus. */
  lineId: string;
  /** Si se indicó al crear el aviso, evita avisar de la dirección contraria. */
  destination?: string;
  minutesAhead: number;
}

export interface Logger {
  error: (...args: unknown[]) => void;
  warn?: (...args: unknown[]) => void;
}
