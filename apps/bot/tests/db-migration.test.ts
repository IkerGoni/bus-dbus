import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import Database from 'better-sqlite3';
import { afterEach, describe, expect, it, vi } from 'vitest';

describe('migraciones aditivas de SQLite', () => {
  const file = path.join(os.tmpdir(), `dbus-db-migration-${process.pid}.db`);

  function removeDb() {
    for (const suffix of ['', '-wal', '-shm']) {
      fs.rmSync(`${file}${suffix}`, { force: true });
    }
  }

  afterEach(() => {
    process.env.DB_PATH = '';
    removeDb();
    vi.resetModules();
  });

  it('v1 -> v3: añade send_failures, deduplica y crea índices UNIQUE', async () => {
    removeDb();
    const old = new Database(file);
    old.exec(`
      CREATE TABLE user_alerts (
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
      CREATE TABLE user_favorites (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        chat_id INTEGER NOT NULL,
        stop_id TEXT NOT NULL,
        stop_name TEXT NOT NULL,
        line_id TEXT NOT NULL,
        line_name TEXT NOT NULL,
        destination TEXT NOT NULL,
        created_at INTEGER NOT NULL
      );
      CREATE TABLE user_favorite_lines (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        chat_id INTEGER NOT NULL,
        line_id TEXT NOT NULL,
        line_name TEXT NOT NULL,
        destination TEXT NOT NULL,
        created_at INTEGER NOT NULL
      );
      CREATE TABLE schema_version (version INTEGER PRIMARY KEY);
      INSERT INTO schema_version (version) VALUES (1);
      INSERT INTO user_favorites (chat_id, stop_id, stop_name, line_id, line_name, destination, created_at)
        VALUES (1,'2','Urbieta','28','L28','D',1),(1,'2','Urbieta','28','L28','D',2);
      INSERT INTO user_favorite_lines (chat_id, line_id, line_name, destination, created_at)
        VALUES (1,'28','L28','D',1),(1,'28','L28','D',2);
    `);
    old.close();

    vi.resetModules();
    process.env.DB_PATH = file;
    const mod = await import('../src/db.js');

    const check = new Database(file, { readonly: true });
    const columns = check.prepare('PRAGMA table_info(user_alerts)').all() as {
      name: string;
    }[];
    expect(columns.some((column) => column.name === 'send_failures')).toBe(
      true
    );

    const indexes = check
      .prepare('PRAGMA index_list(user_favorites)')
      .all() as { name: string }[];
    expect(indexes.some((index) => index.name === 'uq_favorites_dedup')).toBe(
      true
    );

    // Duplicados preexistentes: se conserva uno y las inserciones son idempotentes.
    expect(mod.getFavorites(1)).toHaveLength(1);
    expect(mod.getFavoriteLines(1)).toHaveLength(1);
    const fav = mod.addFavorite({
      chatId: 1,
      stopId: '2',
      stopName: 'Urbieta',
      lineId: '28',
      lineName: 'L28',
      destination: 'D',
    });
    expect(fav.id).toBe(mod.getFavorites(1)[0]?.id);
    expect(mod.getFavorites(1)).toHaveLength(1);
    check.close();
  });
});
