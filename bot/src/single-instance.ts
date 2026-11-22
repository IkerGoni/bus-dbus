import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

const LOCK_FILE = path.join(os.tmpdir(), 'dbus-alarm-bot.lock');

function pidAlive(pid: number): boolean {
  try {
    process.kill(pid, 0);
    return true;
  } catch {
    return false;
  }
}

/**
 * Evita que dos procesos locales peleen por el mismo long-polling de
 * Telegram (el 409 que mataba al bot al reiniciar). Un lock con PID caducado
 * se considera libre, así que un crash no bloquea el siguiente arranque.
 */
export function acquireInstanceLock(
  file = LOCK_FILE,
  pid = process.pid
): boolean {
  try {
    const existing = Number.parseInt(fs.readFileSync(file, 'utf8').trim(), 10);
    if (Number.isFinite(existing) && existing !== pid && pidAlive(existing)) {
      return false;
    }
  } catch {
    // No existe o no se puede leer: intentamos crearlo.
  }
  try {
    fs.writeFileSync(file, String(pid));
    return true;
  } catch {
    return false;
  }
}

/** Libera el lock solo si pertenece a este proceso. */
export function releaseInstanceLock(file = LOCK_FILE, pid = process.pid): void {
  try {
    const existing = Number.parseInt(fs.readFileSync(file, 'utf8').trim(), 10);
    if (existing === pid) fs.unlinkSync(file);
  } catch {
    // Nada que liberar.
  }
}
