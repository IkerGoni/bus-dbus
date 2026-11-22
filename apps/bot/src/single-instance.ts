import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

const LOCK_FILE = path.join(os.tmpdir(), 'dbus-alarm-bot.lock');

/**
 * `kill(pid, 0)` lanza `EPERM` si el proceso existe pero es de otro usuario:
 * cuenta como vivo para no robarle el lock (H14). Solo `ESRCH` (u otro error)
 * es un proceso muerto.
 */
export function pidAlive(
  pid: number,
  kill: (pid: number, signal: number) => unknown = process.kill
): boolean {
  try {
    kill(pid, 0);
    return true;
  } catch (error) {
    return (error as NodeJS.ErrnoException).code === 'EPERM';
  }
}

function readLockPid(file: string): number | null {
  try {
    const parsed = Number.parseInt(fs.readFileSync(file, 'utf8').trim(), 10);
    return Number.isFinite(parsed) ? parsed : null;
  } catch {
    return null;
  }
}

/**
 * Crea el lock con `wx` y verifica que sigue siendo nuestro tras escribir.
 * La verificación evita que dos procesos que reclamaron el mismo lock caducado
 * se crean ambos dueños si el otro reescribió el fichero en la ventana.
 */
function claimLock(file: string, pid: number): boolean {
  try {
    fs.writeFileSync(file, String(pid), { flag: 'wx' });
  } catch {
    return false;
  }
  return readLockPid(file) === pid;
}

/**
 * Evita que dos procesos locales peleen por el mismo long-polling de
 * Telegram (el 409 que mataba al bot al reiniciar). Un lock con PID caducado
 * se considera libre, así que un crash no bloquea el siguiente arranque.
 *
 * La creación es atómica (`wx`): leer y después escribir permitía que dos
 * procesos vieran el lock libre y arrancaran los dos.
 */
export function acquireInstanceLock(
  file = LOCK_FILE,
  pid = process.pid
): boolean {
  const existing = readLockPid(file);
  if (existing !== null && existing !== pid && pidAlive(existing)) {
    return false;
  }

  if (claimLock(file, pid)) return true;

  // El fichero ya existía (o apareció entre lectura y escritura). Solo lo
  // reclamamos si su PID ya no vive; si otro lo reescribe, la verificación
  // de `claimLock` nos hace retirarnos.
  const current = readLockPid(file);
  if (current !== null && current !== pid && pidAlive(current)) return false;
  try {
    fs.unlinkSync(file);
  } catch {
    return false;
  }
  return claimLock(file, pid);
}

/** Libera el lock solo si pertenece a este proceso. */
export function releaseInstanceLock(file = LOCK_FILE, pid = process.pid): void {
  if (readLockPid(file) === pid) {
    try {
      fs.unlinkSync(file);
    } catch {
      // Nada que liberar.
    }
  }
}
