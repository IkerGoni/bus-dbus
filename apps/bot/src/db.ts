import postgres from 'postgres';
import { config } from './config.js';
import type { UserAlert } from './types.js';

// --- Tipos de dominio exportados (compatibilidad API pública) ---

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

export interface FavoriteLine {
  id: number;
  chatId: number;
  lineId: string;
  lineName: string;
  destination: string;
  createdAt: number;
}

/** Error por aviso duplicado (chat, parada, línea activa). */
export class DuplicateAlertError extends Error {
  constructor(readonly alert: UserAlert) {
    super('Ya existe un aviso activo para esta parada y línea');
    this.name = 'DuplicateAlertError';
  }
}

type AlertUpdate = Partial<Omit<UserAlert, 'id' | 'chatId' | 'createdAt'>>;

// --- Backend: Postgres (producción) o memoria (tests) ---
// DB_DRIVER=memory lo usan los tests: nunca abre conexión de red.

interface DbBackend {
  createAlert(
    alert: Omit<UserAlert, 'id' | 'createdAt' | 'sendFailures'>
  ): Promise<UserAlert>;
  getAlertsByChat(chatId: number): Promise<UserAlert[]>;
  findActiveAlert(
    chatId: number,
    stopId: string,
    lineId: string
  ): Promise<UserAlert | null>;
  getActiveAlerts(): Promise<UserAlert[]>;
  getAlertById(id: number): Promise<UserAlert | null>;
  updateAlert(id: number, updates: AlertUpdate): Promise<boolean>;
  deleteAlert(id: number): Promise<boolean>;
  deactivateAlert(id: number): Promise<boolean>;
  addFavorite(
    fav: Omit<UserFavorite, 'id' | 'createdAt'>
  ): Promise<UserFavorite>;
  getFavorites(chatId: number): Promise<UserFavorite[]>;
  findFavorite(
    chatId: number,
    stopId: string,
    lineId: string
  ): Promise<UserFavorite | null>;
  removeFavorite(id: number): Promise<boolean>;
  addFavoriteLine(
    fav: Omit<FavoriteLine, 'id' | 'createdAt'>
  ): Promise<FavoriteLine>;
  getFavoriteLines(chatId: number): Promise<FavoriteLine[]>;
  findFavoriteLine(
    chatId: number,
    lineId: string
  ): Promise<FavoriteLine | null>;
  removeFavoriteLine(id: number): Promise<boolean>;
  close(): Promise<void>;
}

function createMemoryBackend(): DbBackend {
  const alerts = new Map<number, UserAlert>();
  const favorites = new Map<number, UserFavorite>();
  const favoriteLines = new Map<number, FavoriteLine>();
  let alertId = 1;
  let favoriteId = 1;
  let favoriteLineId = 1;

  return {
    async createAlert(alert) {
      for (const existing of alerts.values()) {
        if (
          existing.chatId === alert.chatId &&
          existing.stopId === alert.stopId &&
          existing.lineId === alert.lineId &&
          existing.active
        ) {
          throw new DuplicateAlertError(existing);
        }
      }
      const now = Date.now();
      const created: UserAlert = {
        ...alert,
        id: alertId++,
        createdAt: now,
        sendFailures: 0,
      };
      alerts.set(created.id, created);
      return created;
    },
    async getAlertsByChat(chatId) {
      return Array.from(alerts.values())
        .filter((a) => a.chatId === chatId)
        .sort((a, b) => b.createdAt - a.createdAt);
    },
    async findActiveAlert(chatId, stopId, lineId) {
      for (const alert of alerts.values()) {
        if (
          alert.chatId === chatId &&
          alert.stopId === stopId &&
          alert.lineId === lineId &&
          alert.active
        ) {
          return alert;
        }
      }
      return null;
    },
    async getActiveAlerts() {
      return Array.from(alerts.values()).filter((a) => a.active);
    },
    async getAlertById(id) {
      return alerts.get(id) ?? null;
    },
    async updateAlert(id, updates) {
      const alert = alerts.get(id);
      if (!alert) return false;
      // Whitelist: ignorar columnas no permitidas (igual que la versión SQL).
      const allowed = (Object.keys(updates) as (keyof AlertUpdate)[]).filter(
        (key) => key in ALERT_COLUMNS
      );
      if (!allowed.length) return false;
      const patch: Partial<UserAlert> = {};
      for (const key of allowed) {
        patch[key] = updates[key] as never;
      }
      alerts.set(id, { ...alert, ...patch });
      return true;
    },
    async deleteAlert(id) {
      return alerts.delete(id);
    },
    async deactivateAlert(id) {
      const alert = alerts.get(id);
      if (!alert) return false;
      alerts.set(id, { ...alert, active: false });
      return true;
    },
    async addFavorite(fav) {
      for (const existing of favorites.values()) {
        if (
          existing.chatId === fav.chatId &&
          existing.stopId === fav.stopId &&
          existing.lineId === fav.lineId
        ) {
          return existing;
        }
      }
      const created: UserFavorite = {
        ...fav,
        id: favoriteId++,
        createdAt: Date.now(),
      };
      favorites.set(created.id, created);
      return created;
    },
    async getFavorites(chatId) {
      return Array.from(favorites.values())
        .filter((f) => f.chatId === chatId)
        .sort((a, b) => b.createdAt - a.createdAt);
    },
    async findFavorite(chatId, stopId, lineId) {
      for (const fav of favorites.values()) {
        if (
          fav.chatId === chatId &&
          fav.stopId === stopId &&
          fav.lineId === lineId
        ) {
          return fav;
        }
      }
      return null;
    },
    async removeFavorite(id) {
      return favorites.delete(id);
    },
    async addFavoriteLine(fav) {
      for (const existing of favoriteLines.values()) {
        if (existing.chatId === fav.chatId && existing.lineId === fav.lineId) {
          return existing;
        }
      }
      const created: FavoriteLine = {
        ...fav,
        id: favoriteLineId++,
        createdAt: Date.now(),
      };
      favoriteLines.set(created.id, created);
      return created;
    },
    async getFavoriteLines(chatId) {
      return Array.from(favoriteLines.values())
        .filter((f) => f.chatId === chatId)
        .sort((a, b) => b.createdAt - a.createdAt);
    },
    async findFavoriteLine(chatId, lineId) {
      for (const fav of favoriteLines.values()) {
        if (fav.chatId === chatId && fav.lineId === lineId) {
          return fav;
        }
      }
      return null;
    },
    async removeFavoriteLine(id) {
      return favoriteLines.delete(id);
    },
    async close() {},
  };
}

// --- Filas de Postgres (snake_case) ---

interface AlertRow {
  id: number;
  chat_id: number;
  stop_id: string;
  stop_name: string;
  line_id: string;
  line_name: string;
  destination: string;
  minutes_ahead: number;
  active: boolean;
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

interface FavoriteLineRow {
  id: number;
  chat_id: number;
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
    active: row.active,
    sendFailures: row.send_failures,
    createdAt: row.created_at,
  };
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

const SCHEMA = `
CREATE TABLE IF NOT EXISTS user_alerts (
  id BIGSERIAL PRIMARY KEY,
  chat_id BIGINT NOT NULL,
  stop_id TEXT NOT NULL,
  stop_name TEXT NOT NULL,
  line_id TEXT NOT NULL,
  line_name TEXT NOT NULL,
  destination TEXT NOT NULL,
  minutes_ahead INTEGER NOT NULL,
  active BOOLEAN NOT NULL DEFAULT true,
  send_failures INTEGER NOT NULL DEFAULT 0,
  created_at BIGINT NOT NULL,
  updated_at BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_alerts_chat ON user_alerts(chat_id);
CREATE INDEX IF NOT EXISTS idx_alerts_active ON user_alerts(active);
CREATE UNIQUE INDEX IF NOT EXISTS uq_alerts_dedup
  ON user_alerts(chat_id, stop_id, line_id) WHERE active;

CREATE TABLE IF NOT EXISTS user_favorites (
  id BIGSERIAL PRIMARY KEY,
  chat_id BIGINT NOT NULL,
  stop_id TEXT NOT NULL,
  stop_name TEXT NOT NULL,
  line_id TEXT NOT NULL,
  line_name TEXT NOT NULL,
  destination TEXT NOT NULL,
  created_at BIGINT NOT NULL,
  UNIQUE (chat_id, stop_id, line_id)
);

CREATE INDEX IF NOT EXISTS idx_favorites_chat ON user_favorites(chat_id);

CREATE TABLE IF NOT EXISTS user_favorite_lines (
  id BIGSERIAL PRIMARY KEY,
  chat_id BIGINT NOT NULL,
  line_id TEXT NOT NULL,
  line_name TEXT NOT NULL,
  destination TEXT NOT NULL,
  created_at BIGINT NOT NULL,
  UNIQUE (chat_id, line_id)
);

CREATE INDEX IF NOT EXISTS idx_fav_lines_chat ON user_favorite_lines(chat_id);

CREATE TABLE IF NOT EXISTS schema_version (
  version INTEGER PRIMARY KEY
);
`;

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

async function createPostgresBackend(): Promise<DbBackend> {
  const databaseUrl = config.databaseUrl ?? 'postgresql://localhost:5432/dbus';
  const sql = postgres(databaseUrl, {
    max: 5,
    idle_timeout: 30,
    connect_timeout: 10,
    ssl: databaseUrl.includes('sslmode=require') ? 'require' : 'prefer',
    onnotice: () => {},
    debug: false,
  });

  // Esquema + migraciones aditivas
  await sql.unsafe(SCHEMA);
  const hasSendFailures = await sql`
    SELECT column_name FROM information_schema.columns
    WHERE table_name = 'user_alerts' AND column_name = 'send_failures'
  `;
  if (hasSendFailures.length === 0) {
    await sql`ALTER TABLE user_alerts ADD COLUMN send_failures INTEGER NOT NULL DEFAULT 0`;
  }
  await sql`
    INSERT INTO schema_version (version) VALUES (3)
    ON CONFLICT (version) DO NOTHING
  `;

  // Helpers standalone (evitan usar `this` dentro del objeto backend)
  const findFavoriteRow = async (
    chatId: number,
    stopId: string,
    lineId: string
  ) => {
    const rows = await sql<FavoriteRow[]>`
      SELECT * FROM user_favorites
      WHERE chat_id = ${chatId} AND stop_id = ${stopId} AND line_id = ${lineId}
      LIMIT 1
    `;
    return rows.length > 0 ? rowToFavorite(rows[0]) : null;
  };
  const findFavoriteLineRow = async (chatId: number, lineId: string) => {
    const rows = await sql<FavoriteLineRow[]>`
      SELECT * FROM user_favorite_lines
      WHERE chat_id = ${chatId} AND line_id = ${lineId}
      LIMIT 1
    `;
    return rows.length > 0 ? rowToFavoriteLine(rows[0]) : null;
  };

  return {
    async createAlert(alert) {
      return sql.begin(async (txSql) => {
        const existing = await txSql<AlertRow[]>`
          SELECT * FROM user_alerts
          WHERE chat_id = ${alert.chatId} AND stop_id = ${alert.stopId}
            AND line_id = ${alert.lineId} AND active = true
          LIMIT 1
        `;
        if (existing.length > 0)
          throw new DuplicateAlertError(rowToAlert(existing[0]));

        const now = Date.now();
        const [inserted] = await txSql<AlertRow[]>`
          INSERT INTO user_alerts (
            chat_id, stop_id, stop_name, line_id, line_name,
            destination, minutes_ahead, active, send_failures,
            created_at, updated_at
          ) VALUES (
            ${alert.chatId}, ${alert.stopId}, ${alert.stopName}, ${alert.lineId},
            ${alert.lineName}, ${alert.destination}, ${alert.minutesAhead},
            ${alert.active}, 0, ${now}, ${now}
          )
          RETURNING *
        `;
        return rowToAlert(inserted);
      });
    },
    async getAlertsByChat(chatId) {
      const rows = await sql<AlertRow[]>`
        SELECT * FROM user_alerts WHERE chat_id = ${chatId} ORDER BY created_at DESC
      `;
      return rows.map(rowToAlert);
    },
    async findActiveAlert(chatId, stopId, lineId) {
      const rows = await sql<AlertRow[]>`
        SELECT * FROM user_alerts
        WHERE chat_id = ${chatId} AND stop_id = ${stopId}
          AND line_id = ${lineId} AND active = true
        LIMIT 1
      `;
      return rows.length > 0 ? rowToAlert(rows[0]) : null;
    },
    async getActiveAlerts() {
      const rows = await sql<AlertRow[]>`
        SELECT * FROM user_alerts WHERE active = true
      `;
      return rows.map(rowToAlert);
    },
    async getAlertById(id) {
      const rows = await sql<AlertRow[]>`
        SELECT * FROM user_alerts WHERE id = ${id}
      `;
      return rows.length > 0 ? rowToAlert(rows[0]) : null;
    },
    async updateAlert(id, updates) {
      const fields = (Object.keys(updates) as (keyof AlertUpdate)[]).filter(
        (key) => key in ALERT_COLUMNS
      );
      if (!fields.length) return false;

      const setClause = fields
        .map((key) => `${ALERT_COLUMNS[key]} = $u_${key}`)
        .join(', ');
      const values: Record<string, unknown> = {};
      for (const key of fields) {
        const value = updates[key];
        values[`u_${key}`] =
          typeof value === 'boolean' ? (value ? 1 : 0) : value;
      }
      values.u_id = id;
      values.u_updated = Date.now();

      const result = await sql.unsafe(
        `UPDATE user_alerts SET ${setClause}, updated_at = $u_updated WHERE id = $u_id`,
        values
      );
      return result.count > 0;
    },
    async deleteAlert(id) {
      const result = await sql`DELETE FROM user_alerts WHERE id = ${id}`;
      return result.count > 0;
    },
    async deactivateAlert(id) {
      const result = await sql`
        UPDATE user_alerts SET active = false, updated_at = ${Date.now()} WHERE id = ${id}
      `;
      return result.count > 0;
    },
    async addFavorite(fav) {
      const now = Date.now();
      const rows = await sql<FavoriteRow[]>`
        INSERT INTO user_favorites (chat_id, stop_id, stop_name, line_id, line_name, destination, created_at)
        VALUES (${fav.chatId}, ${fav.stopId}, ${fav.stopName}, ${fav.lineId}, ${fav.lineName}, ${fav.destination}, ${now})
        ON CONFLICT (chat_id, stop_id, line_id) DO NOTHING
        RETURNING *
      `;
      if (rows.length === 0) {
        const existing = await findFavoriteRow(
          fav.chatId,
          fav.stopId,
          fav.lineId
        );
        if (existing) return existing;
        throw new Error('No se pudo guardar el favorito');
      }
      return rowToFavorite(rows[0]);
    },
    async getFavorites(chatId) {
      const rows = await sql<FavoriteRow[]>`
        SELECT * FROM user_favorites WHERE chat_id = ${chatId} ORDER BY created_at DESC
      `;
      return rows.map(rowToFavorite);
    },
    async findFavorite(chatId, stopId, lineId) {
      const rows = await sql<FavoriteRow[]>`
        SELECT * FROM user_favorites
        WHERE chat_id = ${chatId} AND stop_id = ${stopId} AND line_id = ${lineId}
        LIMIT 1
      `;
      return rows.length > 0 ? rowToFavorite(rows[0]) : null;
    },
    async removeFavorite(id) {
      const result = await sql`DELETE FROM user_favorites WHERE id = ${id}`;
      return result.count > 0;
    },
    async addFavoriteLine(fav) {
      const now = Date.now();
      const rows = await sql<FavoriteLineRow[]>`
        INSERT INTO user_favorite_lines (chat_id, line_id, line_name, destination, created_at)
        VALUES (${fav.chatId}, ${fav.lineId}, ${fav.lineName}, ${fav.destination}, ${now})
        ON CONFLICT (chat_id, line_id) DO NOTHING
        RETURNING *
      `;
      if (rows.length === 0) {
        const existing = await findFavoriteLineRow(fav.chatId, fav.lineId);
        if (existing) return existing;
        throw new Error('No se pudo guardar la línea favorita');
      }
      return rowToFavoriteLine(rows[0]);
    },
    async getFavoriteLines(chatId) {
      const rows = await sql<FavoriteLineRow[]>`
        SELECT * FROM user_favorite_lines WHERE chat_id = ${chatId} ORDER BY created_at DESC
      `;
      return rows.map(rowToFavoriteLine);
    },
    async findFavoriteLine(chatId, lineId) {
      const rows = await sql<FavoriteLineRow[]>`
        SELECT * FROM user_favorite_lines
        WHERE chat_id = ${chatId} AND line_id = ${lineId}
        LIMIT 1
      `;
      return rows.length > 0 ? rowToFavoriteLine(rows[0]) : null;
    },
    async removeFavoriteLine(id) {
      const result =
        await sql`DELETE FROM user_favorite_lines WHERE id = ${id}`;
      return result.count > 0;
    },
    async close() {
      await sql.end({ timeout: 5 });
    },
  };
}

// Selección de backend al importar
const useMemory =
  process.env.DB_DRIVER === 'memory' || config.dbPath === ':memory:';
const backend: DbBackend = useMemory
  ? createMemoryBackend()
  : await createPostgresBackend();

// --- API pública (idéntica a la versión SQLite) ---

export const createAlert = (a: Parameters<DbBackend['createAlert']>[0]) =>
  backend.createAlert(a);
export const getAlertsByChat = (chatId: number) =>
  backend.getAlertsByChat(chatId);
export const findActiveAlert = (c: number, s: string, l: string) =>
  backend.findActiveAlert(c, s, l);
export const getActiveAlerts = () => backend.getActiveAlerts();
export const getAlertById = (id: number) => backend.getAlertById(id);
export const updateAlert = (id: number, u: AlertUpdate) =>
  backend.updateAlert(id, u);
export const deleteAlert = (id: number) => backend.deleteAlert(id);
export const deactivateAlert = (id: number) => backend.deactivateAlert(id);
export const addFavorite = (f: Parameters<DbBackend['addFavorite']>[0]) =>
  backend.addFavorite(f);
export const getFavorites = (chatId: number) => backend.getFavorites(chatId);
export const findFavorite = (c: number, s: string, l: string) =>
  backend.findFavorite(c, s, l);
export const removeFavorite = (id: number) => backend.removeFavorite(id);
export const addFavoriteLine = (
  f: Parameters<DbBackend['addFavoriteLine']>[0]
) => backend.addFavoriteLine(f);
export const getFavoriteLines = (chatId: number) =>
  backend.getFavoriteLines(chatId);
export const findFavoriteLine = (c: number, l: string) =>
  backend.findFavoriteLine(c, l);
export const removeFavoriteLine = (id: number) =>
  backend.removeFavoriteLine(id);
export const closePool = () => backend.close();
