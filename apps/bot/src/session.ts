/**
 * Estado conversacional efímero por chat.
 *
 * Los callbacks son stateless (el dato viaja en `data`); este mapa solo sostiene
 * los flujos multi-paso (elegir línea → minutos → crear aviso). Nada se
 * persiste en disco y las entradas caducan a los 10 min.
 */

export type Step =
  | 'idle'
  | 'searching'
  | 'waiting_stop_arrivals'
  | 'waiting_stop_alert'
  | 'select_line_alert'
  | 'select_minutes_alert';

export interface BotSession {
  step: Step;
  stopId: string;
  stopName: string;
  lineId: string;
  lastUsed: number;
}

const SESSION_TTL_MS = 10 * 60 * 1000;
const MAX_SESSIONS = 1000;
const userSessions = new Map<number, BotSession>();

function newSession(): BotSession {
  return {
    step: 'idle',
    stopId: '',
    stopName: '',
    lineId: '',
    lastUsed: Date.now(),
  };
}

export function getSession(chatId: number): BotSession {
  const existing = userSessions.get(chatId);
  if (existing && Date.now() - existing.lastUsed < SESSION_TTL_MS) {
    existing.lastUsed = Date.now();
    return existing;
  }
  const session = newSession();
  userSessions.set(chatId, session);
  // Los flujos se abandonan a menudo; evita que el mapa crezca sin límite.
  if (userSessions.size > MAX_SESSIONS) pruneSessions();
  return session;
}

export function clearSession(chatId: number): void {
  userSessions.delete(chatId);
}

/** Elimina sesiones caducadas; también usable en tests. */
export function pruneSessions(now = Date.now()): number {
  let removed = 0;
  for (const [chatId, session] of userSessions) {
    if (now - session.lastUsed >= SESSION_TTL_MS) {
      userSessions.delete(chatId);
      removed++;
    }
  }
  return removed;
}

/** Solo para tests: número de sesiones vivas. */
export function getSessionCount(): number {
  return userSessions.size;
}
