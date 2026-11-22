// Tipos del dominio del bot. Los datos en tiempo real vienen de la API JSON
// de Dbus (ver dbus-client.ts), no de GTFS-RT.

export type Language = 'eu' | 'es' | 'en' | 'fr';

/** Parada con código público (el que aparece en la marquesina). */
export interface StopRef {
  cod: string;
  desc: string;
}

/** Llegada normalizada, lista para mostrar al usuario o disparar un aviso. */
export interface NextArrivalResult {
  /** epoch ms calculado a partir de los minutos que devuelve Dbus. */
  arrivalTime: number;
  /** minutos hasta la llegada (0 = llegando). */
  minutes: number;
  /** código público de línea, p. ej. "28". */
  lineId: string;
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

/** Aviso de usuario persistido en SQLite. */
export interface UserAlert {
  id: number;
  chatId: number;
  stopId: string;
  stopName: string;
  lineId: string;
  lineName: string;
  destination: string;
  minutesAhead: number;
  active: boolean;
  createdAt: number;
}
