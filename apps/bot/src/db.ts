import path from 'node:path';
import { fileURLToPath } from 'node:url';
import Database from 'better-sqlite3';
import { config } from './config.js';
import type { UserAlert } from './types.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// `src/` y `dist/` cuelgan de `apps/bot/`: un solo nivel arriba da el mismo
// fichero en desarrollo (tsx) y en producción (node dist/index.js).
// `DB_PATH=:memory:` lo usan los tests: nunca comparten BD con producción.
const dbPath =
  config.dbPath === ':memory:'
    ? ':memory:'
    : path.resolve(__dirname, '../', config.dbPath);
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

  CREATE TABLE IF NOT EXISTS user_favorite_lines (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    chat_id INTEGER NOT NULL,
    line_id TEXT NOT NULL,
    line_name TEXT NOT NULL,
    destination TEXT NOT NULL,
    created_at INTEGER NOT NULL
  );

  CREATE INDEX IF NOT EXISTS idx_fav_lines_chat ON user_favorite_lines(chat_id);
  CREATE INDEX IF NOT EXISTS idx_fav_lines_dedup
    ON user_favorite_lines(chat_id, line_id);

  CREATE TABLE IF NOT EXISTS schema_version (
    version INTEGER PRIMARY KEY
  );
`);

/**
 * Migraciones aditivas. `CREATE TABLE IF NOT EXISTS` no añade columnas a una
 * BD ya creada, así que cada versión se aplica una sola vez y se registra.
 */
function migrate(): void {
  const current = db
    .prepare('SELECT MAX(version) AS version FROM schema_version')
    .get() as { version: number | null };
  let version = current.version ?? 0;

  if (version < 2) {
    const columns = db.prepare('PRAGMA table_info(user_alerts)').all() as {
      name: string;
    }[];
    if (!columns.some((column) => column.name === 'send_failures')) {
      db.exec(
        'ALTER TABLE user_alerts ADD COLUMN send_failures INTEGER NOT NULL DEFAULT 0'
      );
    }
    db.prepare('INSERT INTO schema_version (version) VALUES (2)').run();
    version = 2;
  }

  if (version < 3) {
    // El dedup de favoritos era find + insert sin transacción: una BD ya
    // poblada puede tener duplicados. Se conserva el registro más antiguo y
    // se añaden índices UNIQUE para que la constraint cierre la carrera.
    db.exec(`
      DELETE FROM user_favorites WHERE id NOT IN (
        SELECT MIN(id) FROM user_favorites GROUP BY chat_id, stop_id, line_id
      );
      DELETE FROM user_favorite_lines WHERE id NOT IN (
        SELECT MIN(id) FROM user_favorite_lines GROUP BY chat_id, line_id
      );
      CREATE UNIQUE INDEX IF NOT EXISTS uq_favorites_dedup
        ON user_favorites(chat_id, stop_id, line_id);
      CREATE UNIQUE INDEX IF NOT EXISTS uq_fav_lines_dedup
        ON user_favorite_lines(chat_id, line_id);
    `);
    db.prepare('INSERT INTO schema_version (version) VALUES (3)').run();
    version = 3;
  }
}
migrate();

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
  send_failures: number;
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

/** Consulta de una sola fila con parámetros, compartida por los finders. */
function findRow<T>(sql: string, ...params: unknown[]): T | null {
  const row = db.prepare(sql).get(...params);
  return row ? (row as T) : null;
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
    sendFailures: row.send_failures ?? 0,
    createdAt: row.created_at,
  };
}

/** Lanzada cuando ya existe un aviso activo para la misma (chat, parada, línea). */
export class DuplicateAlertError extends Error {
  constructor(readonly alert: UserAlert) {
    super('Ya existe un aviso activo para esta parada y línea');
    this.name = 'DuplicateAlertError';
  }
}

const insertAlertStmt = db.prepare(`
  INSERT INTO user_alerts (chat_id, stop_id, stop_name, line_id, line_name, destination, minutes_ahead, active, send_failures, created_at, updated_at)
  VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
`);

// findActiveAlert() + INSERT en dos statements dejaba una ventana de carrera:
// dos comandos rápidos podían crear el mismo aviso dos veces. La transacción
// serializa comprobación e inserción en la misma conexión SQLite.
const createAlertTx = db.transaction(
  (alert: Omit<UserAlert, 'id' | 'createdAt' | 'sendFailures'>): UserAlert => {
    const existing = findActiveAlert(alert.chatId, alert.stopId, alert.lineId);
    if (existing) throw new DuplicateAlertError(existing);

    const now = Date.now();
    const result = insertAlertStmt.run(
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
    return {
      ...alert,
      id: result.lastInsertRowid as number,
      createdAt: now,
      sendFailures: 0,
    };
  }
);

export function createAlert(
  alert: Omit<UserAlert, 'id' | 'createdAt' | 'sendFailures'>
): UserAlert {
  return createAlertTx(alert);
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
  const row = findRow<AlertRow>(
    'SELECT * FROM user_alerts WHERE chat_id = ? AND stop_id = ? AND line_id = ? AND active = 1 LIMIT 1',
    chatId,
    stopId,
    lineId
  );
  return row ? rowToAlert(row) : null;
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

type AlertUpdate = Partial<Omit<UserAlert, 'id' | 'chatId' | 'createdAt'>>;

/** Whitelist: nunca se interpola una clave arbitraria en SQL. */
const ALERT_COLUMNS: Record<keyof AlertUpdate, string> = {
  stopId: 'stop_id',
  stopName: 'stop_name',
  lineId: 'line_id',
  lineName: 'line_name',
  destination: 'destination',
  minutesAhead: 'minutes_ahead',
  active: 'active',
  sendFailures: 'send_failures',
};

export function updateAlert(id: number, updates: AlertUpdate): boolean {
  const fields = (Object.keys(updates) as (keyof AlertUpdate)[]).filter(
    (key) => key in ALERT_COLUMNS
  );
  if (!fields.length) return false;

  const setClause = fields.map((key) => `${ALERT_COLUMNS[key]} = ?`).join(', ');
  // better-sqlite3 no acepta booleanos: SQLite los guarda como 0/1.
  const values = fields.map((key) => {
    const value = updates[key];
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

const insertFavoriteStmt = db.prepare(`
  INSERT INTO user_favorites (chat_id, stop_id, stop_name, line_id, line_name, destination, created_at)
  VALUES (?, ?, ?, ?, ?, ?, ?)
  ON CONFLICT(chat_id, stop_id, line_id) DO NOTHING
`);

export function addFavorite(
  fav: Omit<UserFavorite, 'id' | 'createdAt'>
): UserFavorite {
  const now = Date.now();
  const result = insertFavoriteStmt.run(
    fav.chatId,
    fav.stopId,
    fav.stopName,
    fav.lineId,
    fav.lineName,
    fav.destination,
    now
  );
  if (result.changes === 0) {
    // Otro callback ganó la carrera: devolver el registro ya guardado.
    const existing = findFavorite(fav.chatId, fav.stopId, fav.lineId);
    if (existing) return existing;
    throw new Error('No se pudo guardar el favorito');
  }
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
  const row = findRow<FavoriteRow>(
    'SELECT * FROM user_favorites WHERE chat_id = ? AND stop_id = ? AND line_id = ? LIMIT 1',
    chatId,
    stopId,
    lineId
  );
  return row ? rowToFavorite(row) : null;
}

export function removeFavorite(id: number): boolean {
  const stmt = db.prepare('DELETE FROM user_favorites WHERE id = ?');
  const result = stmt.run(id);
  return result.changes > 0;
}

// Líneas favoritas (acceso directo sin elegir parada)

export interface FavoriteLine {
  id: number;
  chatId: number;
  lineId: string;
  lineName: string;
  destination: string;
  createdAt: number;
}

interface FavoriteLineRow {
  id: number;
  chat_id: number;
  line_id: string;
  line_name: string;
  destination: string;
  created_at: number;
}

function rowToFavoriteLine(row: FavoriteLineRow): FavoriteLine {
  return {
    id: row.id,
    chatId: row.chat_id,
    lineId: row.line_id,
    lineName: row.line_name,
    destination: row.destination,
    createdAt: row.created_at,
  };
}

const insertFavoriteLineStmt = db.prepare(`
  INSERT INTO user_favorite_lines (chat_id, line_id, line_name, destination, created_at)
  VALUES (?, ?, ?, ?, ?)
  ON CONFLICT(chat_id, line_id) DO NOTHING
`);

export function addFavoriteLine(
  fav: Omit<FavoriteLine, 'id' | 'createdAt'>
): FavoriteLine {
  const now = Date.now();
  const result = insertFavoriteLineStmt.run(
    fav.chatId,
    fav.lineId,
    fav.lineName,
    fav.destination,
    now
  );
  if (result.changes === 0) {
    const existing = findFavoriteLine(fav.chatId, fav.lineId);
    if (existing) return existing;
    throw new Error('No se pudo guardar la línea favorita');
  }
  return { ...fav, id: result.lastInsertRowid as number, createdAt: now };
}

export function getFavoriteLines(chatId: number): FavoriteLine[] {
  const stmt = db.prepare(
    'SELECT * FROM user_favorite_lines WHERE chat_id = ? ORDER BY created_at DESC'
  );
  return stmt
    .all(chatId)
    .map((row): FavoriteLine => rowToFavoriteLine(row as FavoriteLineRow));
}

export function findFavoriteLine(
  chatId: number,
  lineId: string
): FavoriteLine | null {
  const row = findRow<FavoriteLineRow>(
    'SELECT * FROM user_favorite_lines WHERE chat_id = ? AND line_id = ? LIMIT 1',
    chatId,
    lineId
  );
  return row ? rowToFavoriteLine(row) : null;
}

export function removeFavoriteLine(id: number): boolean {
  const stmt = db.prepare('DELETE FROM user_favorite_lines WHERE id = ?');
  const result = stmt.run(id);
  return result.changes > 0;
}
