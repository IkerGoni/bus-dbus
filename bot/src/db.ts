import path from 'node:path';
import { fileURLToPath } from 'node:url';
import Database from 'better-sqlite3';
import { config } from './config.js';
import type { UserAlert } from './types.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const dbPath = path.resolve(__dirname, '../../', config.dbPath);
const db = new Database(dbPath);

// Inicializar esquema
db.exec(`
  CREATE TABLE IF NOT EXISTS user_alerts (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    chat_id INTEGER NOT NULL,
    stop_id TEXT NOT NULL,
    stop_name TEXT NOT NULL,
    line_id TEXT NOT NULL,
    line_name TEXT NOT NULL,
    destination TEXT NOT NULL,
    minutes_ahead INTEGER NOT NULL,
    active INTEGER NOT NULL DEFAULT 1,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
  );
  
  CREATE INDEX IF NOT EXISTS idx_alerts_chat ON user_alerts(chat_id);
  CREATE INDEX IF NOT EXISTS idx_alerts_active ON user_alerts(active);
  CREATE INDEX IF NOT EXISTS idx_alerts_dedup
    ON user_alerts(chat_id, stop_id, line_id, active);

  CREATE TABLE IF NOT EXISTS user_favorites (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    chat_id INTEGER NOT NULL,
    stop_id TEXT NOT NULL,
    stop_name TEXT NOT NULL,
    line_id TEXT NOT NULL,
    line_name TEXT NOT NULL,
    destination TEXT NOT NULL,
    created_at INTEGER NOT NULL
  );
  
  CREATE INDEX IF NOT EXISTS idx_favorites_chat ON user_favorites(chat_id);
  CREATE INDEX IF NOT EXISTS idx_favorites_dedup
    ON user_favorites(chat_id, stop_id, line_id);
`);

// Tipos de filas de SQLite (snake_case -> tipos estrictos, sin `any`)
interface AlertRow {
  id: number;
  chat_id: number;
  stop_id: string;
  stop_name: string;
  line_id: string;
  line_name: string;
  destination: string;
  minutes_ahead: number;
  active: number;
  created_at: number;
  updated_at: number;
}

interface FavoriteRow {
  id: number;
  chat_id: number;
  stop_id: string;
  stop_name: string;
  line_id: string;
  line_name: string;
  destination: string;
  created_at: number;
}

function rowToAlert(row: AlertRow): UserAlert {
  return {
    id: row.id,
    chatId: row.chat_id,
    stopId: row.stop_id,
    stopName: row.stop_name,
    lineId: row.line_id,
    lineName: row.line_name,
    destination: row.destination,
    minutesAhead: row.minutes_ahead,
    active: Boolean(row.active),
    createdAt: row.created_at,
  };
}

export function createAlert(
  alert: Omit<UserAlert, 'id' | 'createdAt'>
): UserAlert {
  const now = Date.now();
  const stmt = db.prepare(`
    INSERT INTO user_alerts (chat_id, stop_id, stop_name, line_id, line_name, destination, minutes_ahead, active, created_at, updated_at)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
  `);
  const result = stmt.run(
    alert.chatId,
    alert.stopId,
    alert.stopName,
    alert.lineId,
    alert.lineName,
    alert.destination,
    alert.minutesAhead,
    alert.active ? 1 : 0,
    now,
    now
  );
  return { ...alert, id: result.lastInsertRowid as number, createdAt: now };
}

export function getAlertsByChat(chatId: number): UserAlert[] {
  const stmt = db.prepare(
    'SELECT * FROM user_alerts WHERE chat_id = ? ORDER BY created_at DESC'
  );
  return stmt.all(chatId).map((row): UserAlert => rowToAlert(row as AlertRow));
}

/** Evita avisos duplicados para la misma parada y línea. */
export function findActiveAlert(
  chatId: number,
  stopId: string,
  lineId: string
): UserAlert | null {
  const stmt = db.prepare(
    'SELECT * FROM user_alerts WHERE chat_id = ? AND stop_id = ? AND line_id = ? AND active = 1 LIMIT 1'
  );
  const row = stmt.get(chatId, stopId, lineId);
  return row ? rowToAlert(row as AlertRow) : null;
}

export function getActiveAlerts(): UserAlert[] {
  const stmt = db.prepare('SELECT * FROM user_alerts WHERE active = 1');
  return stmt.all().map((row): UserAlert => rowToAlert(row as AlertRow));
}

export function getAlertById(id: number): UserAlert | null {
  const stmt = db.prepare('SELECT * FROM user_alerts WHERE id = ?');
  const row = stmt.get(id);
  return row ? rowToAlert(row as AlertRow) : null;
}

export function updateAlert(
  id: number,
  updates: Partial<Omit<UserAlert, 'id' | 'chatId' | 'createdAt'>>
): boolean {
  const fields = Object.keys(updates).filter(
    (k) => k !== 'id' && k !== 'chatId' && k !== 'createdAt'
  );
  if (!fields.length) return false;

  const setClause = fields.map((f) => `${f} = ?`).join(', ');
  // better-sqlite3 no acepta booleanos: SQLite los guarda como 0/1.
  const values = fields.map((f) => {
    const value = updates[f as keyof typeof updates];
    return typeof value === 'boolean' ? (value ? 1 : 0) : value;
  });
  values.push(Date.now(), id);

  const stmt = db.prepare(
    `UPDATE user_alerts SET ${setClause}, updated_at = ? WHERE id = ?`
  );
  const result = stmt.run(...values);
  return result.changes > 0;
}

export function deleteAlert(id: number): boolean {
  const stmt = db.prepare('DELETE FROM user_alerts WHERE id = ?');
  const result = stmt.run(id);
  return result.changes > 0;
}

export function deactivateAlert(id: number): boolean {
  return updateAlert(id, { active: false });
}

// Favorites
export interface UserFavorite {
  id: number;
  chatId: number;
  stopId: string;
  stopName: string;
  lineId: string;
  lineName: string;
  destination: string;
  createdAt: number;
}

function rowToFavorite(row: FavoriteRow): UserFavorite {
  return {
    id: row.id,
    chatId: row.chat_id,
    stopId: row.stop_id,
    stopName: row.stop_name,
    lineId: row.line_id,
    lineName: row.line_name,
    destination: row.destination,
    createdAt: row.created_at,
  };
}

export function addFavorite(
  fav: Omit<UserFavorite, 'id' | 'createdAt'>
): UserFavorite {
  const now = Date.now();
  const stmt = db.prepare(`
    INSERT INTO user_favorites (chat_id, stop_id, stop_name, line_id, line_name, destination, created_at)
    VALUES (?, ?, ?, ?, ?, ?, ?)
  `);
  const result = stmt.run(
    fav.chatId,
    fav.stopId,
    fav.stopName,
    fav.lineId,
    fav.lineName,
    fav.destination,
    now
  );
  return { ...fav, id: result.lastInsertRowid as number, createdAt: now };
}

export function getFavorites(chatId: number): UserFavorite[] {
  const stmt = db.prepare(
    'SELECT * FROM user_favorites WHERE chat_id = ? ORDER BY created_at DESC'
  );
  return stmt
    .all(chatId)
    .map((row): UserFavorite => rowToFavorite(row as FavoriteRow));
}

export function findFavorite(
  chatId: number,
  stopId: string,
  lineId: string
): UserFavorite | null {
  const stmt = db.prepare(
    'SELECT * FROM user_favorites WHERE chat_id = ? AND stop_id = ? AND line_id = ? LIMIT 1'
  );
  const row = stmt.get(chatId, stopId, lineId);
  return row ? rowToFavorite(row as FavoriteRow) : null;
}

export function removeFavorite(id: number): boolean {
  const stmt = db.prepare('DELETE FROM user_favorites WHERE id = ?');
  const result = stmt.run(id);
  return result.changes > 0;
}
