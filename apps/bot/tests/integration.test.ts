import { describe, expect, it } from 'vitest';
import {
  formatArrival,
  formatArrivalsList,
  getNextArrival,
  getNextArrivals,
  getStopLines,
} from '../src/arrival.js';
import {
  DuplicateAlertError,
  addFavorite,
  addFavoriteLine,
  createAlert,
  deleteAlert,
  findActiveAlert,
  findFavorite,
  findFavoriteLine,
  getActiveAlerts,
  getAlertById,
  getAlertsByChat,
  getFavoriteLines,
  getFavorites,
  removeFavorite,
  removeFavoriteLine,
  updateAlert,
} from '../src/db.js';
import {
  fetchPopularStops,
  fetchStopTimes,
  getStopName,
} from '../src/dbus-client.js';

// Parada pública real con servicio: 2 = Urbieta 12, San Sebastián.
// (1 = Boulevard 9 está suprimida por Dbus desde 2026-11-22.)
const STOP = '2';

describe('Dbus live data', () => {
  it('returns real arrival data for a known stop', async () => {
    const times = await fetchStopTimes(STOP, true);
    expect(times).not.toBeNull();
    expect(times!.estado).toBeTruthy();

    if (times!.estado === 'DATOS') {
      expect(times!.parada?.cod).toBe(STOP);
      expect(times!.parada?.desc).toContain('Urbieta');
      expect(times!.tiempos.length).toBeGreaterThan(0);
      const first = times!.tiempos[0];
      expect(first.itinerario.linea.cod).toBeTruthy();
      expect(first.itinerario.destino).toBeTruthy();
      expect(first.minutos).toBeGreaterThanOrEqual(0);
      // tipo=1 GPS; cualquier otro valor es horario (Dbus da 2 y, desde el
      // 2026-11-22, 0). AGENTS.md: ningún test asume un conjunto cerrado.
      expect(Number.isInteger(first.tipo)).toBe(true);
    }
  });

  it('carga la semilla real de Dbus usada por /estado', async () => {
    const stops = await fetchPopularStops();
    expect(stops.length).toBeGreaterThan(5);
    expect(stops.every((s) => /^\d+$/.test(s.cod))).toBe(true);
    expect(stops.every((s) => s.desc.length > 0)).toBe(true);
    expect(getStopName(STOP)).toBeTruthy();
  });
});

/**
 * Dbus caído NO es un skip: `fetchStopTimes` devuelve null y el test falla.
 * `SIN_SERVICIO` con HTTP 200 es un estado operativo real: en ese caso se
 * exige CERO llegadas (nunca inventadas), no silencio.
 */
async function liveTimes() {
  const times = await fetchStopTimes(STOP, true);
  expect(
    times,
    'Dbus sin respuesta: no silenciar el fallo de red'
  ).not.toBeNull();
  return times!;
}

describe('Arrival logic (live)', () => {
  it('returns normalized arrivals sorted by proximity', async () => {
    const times = await liveTimes();
    const arrivals = await getNextArrivals(STOP, undefined, true, times);
    if (times.estado === 'DATOS') {
      expect(arrivals.length).toBeGreaterThan(0);
    } else {
      expect(arrivals).toEqual([]);
    }
    for (const arrival of arrivals) {
      expect(arrival.lineId).toBeTruthy();
      expect(arrival.minutes).toBeGreaterThanOrEqual(0);
      expect(arrival.arrivalTime).toBeGreaterThanOrEqual(Date.now() - 60_000);
      expect(typeof arrival.realtime).toBe('boolean');
      expect(arrival.stopId).toBe(STOP);
    }
    for (let i = 1; i < arrivals.length; i++) {
      expect(arrivals[i].arrivalTime).toBeGreaterThanOrEqual(
        arrivals[i - 1].arrivalTime
      );
    }
  });

  it('filters arrivals by line (accepts L prefix)', async () => {
    const times = await liveTimes();
    const arrivals = await getNextArrivals(STOP, undefined, true, times);
    if (times.estado !== 'DATOS') {
      expect(arrivals).toEqual([]);
      return;
    }
    const lineId = arrivals[0].lineId;
    const filtered = await getNextArrivals(STOP, `L${lineId}`, true, times);
    expect(filtered.length).toBeGreaterThan(0);
    expect(filtered.every((a) => a.lineId === lineId)).toBe(true);
    expect((await getNextArrival(STOP, lineId, times))?.lineId).toBe(lineId);
  });

  it('lists real lines serving the stop', async () => {
    const times = await liveTimes();
    const lines = await getStopLines(STOP, times);
    if (times.estado === 'DATOS') {
      expect(lines.length).toBeGreaterThan(0);
      expect(lines[0].lineId).toBeTruthy();
      expect(lines[0].destination).toBeTruthy();
    } else {
      expect(lines).toEqual([]);
    }
  });

  it('formats arrivals for Telegram', async () => {
    const times = await liveTimes();
    const arrivals = await getNextArrivals(STOP, undefined, true, times);
    const text = formatArrivalsList(
      'Urbieta 12',
      arrivals,
      times.estado,
      STOP,
      times.avisos
    );
    expect(text).toContain('Urbieta');
    if (arrivals.length) {
      expect(formatArrival(arrivals[0])).toContain('L');
      expect(formatArrival(arrivals[0])).toMatch(/min|ahora/);
    } else {
      expect(text).toMatch(/Sin servicio|No hay llegadas|No se pudo consultar/);
    }
  });
});

describe('Database - Alerts', () => {
  const testChatId = 999999999;

  it('creates, retrieves and deletes an alert', () => {
    const existing = getAlertsByChat(testChatId);
    for (const alert of existing) deleteAlert(alert.id);

    const alert = createAlert({
      chatId: testChatId,
      stopId: STOP,
      stopName: 'Urbieta 12',
      lineId: '28',
      lineName: 'L28',
      destination: 'Ospitaleak',
      minutesAhead: 5,
      active: true,
    });

    expect(alert.id).toBeDefined();
    expect(alert.chatId).toBe(testChatId);
    expect(alert.stopId).toBe(STOP);
    expect(alert.minutesAhead).toBe(5);
    expect(getAlertsByChat(testChatId).length).toBeGreaterThan(0);
    expect(getActiveAlerts().some((a) => a.id === alert.id)).toBe(true);
    expect(deleteAlert(alert.id)).toBe(true);
    expect(
      getAlertsByChat(testChatId).find((a) => a.id === alert.id)
    ).toBeUndefined();
  });

  it('finds an active alert for dedup and ignores inactive ones', () => {
    const chatId = 777777777;
    for (const alert of getAlertsByChat(chatId)) deleteAlert(alert.id);

    const alert = createAlert({
      chatId,
      stopId: STOP,
      stopName: 'Urbieta 12',
      lineId: '28',
      lineName: 'L28',
      destination: 'Ospitaleak',
      minutesAhead: 5,
      active: true,
    });
    expect(findActiveAlert(chatId, STOP, '28')?.id).toBe(alert.id);
    updateAlert(alert.id, { active: false });
    expect(findActiveAlert(chatId, STOP, '28')).toBeNull();
    deleteAlert(alert.id);
  });
});

describe('Database - Favorites', () => {
  const testChatId = 888888888;

  it('creates, retrieves and removes a favorite', () => {
    const existing = getFavorites(testChatId);
    for (const fav of existing) removeFavorite(fav.id);

    const fav = addFavorite({
      chatId: testChatId,
      stopId: STOP,
      stopName: 'Urbieta 12',
      lineId: '28',
      lineName: 'L28',
      destination: 'Ospitaleak',
    });

    expect(fav.id).toBeDefined();
    expect(fav.chatId).toBe(testChatId);
    expect(getFavorites(testChatId).length).toBeGreaterThan(0);
    expect(removeFavorite(fav.id)).toBe(true);
    expect(
      getFavorites(testChatId).find((f) => f.id === fav.id)
    ).toBeUndefined();
  });

  it('finds a favorite for dedup', () => {
    const chatId = 666666666;
    for (const fav of getFavorites(chatId)) removeFavorite(fav.id);

    const fav = addFavorite({
      chatId,
      stopId: STOP,
      stopName: 'Urbieta 12',
      lineId: '28',
      lineName: 'L28',
      destination: 'Ospitaleak',
    });
    expect(findFavorite(chatId, STOP, '28')?.id).toBe(fav.id);
    expect(findFavorite(chatId, STOP, '21')).toBeNull();
    removeFavorite(fav.id);
  });

  it('guarda una parada completa (sin línea) como favorito', () => {
    const chatId = 444444444;
    for (const fav of getFavorites(chatId)) removeFavorite(fav.id);

    const fav = addFavorite({
      chatId,
      stopId: STOP,
      stopName: 'Urbieta 12',
      lineId: '',
      lineName: 'Todas',
      destination: 'Todas las líneas',
    });
    expect(fav.lineId).toBe('');
    expect(findFavorite(chatId, STOP, '')?.id).toBe(fav.id);
    expect(findFavorite(chatId, STOP, '28')).toBeNull();
    removeFavorite(fav.id);
  });
});

describe('Database - resistencia de avisos', () => {
  const chatId = 222222222;

  function newAlert() {
    return createAlert({
      chatId,
      stopId: STOP,
      stopName: 'Urbieta 12',
      lineId: '28',
      lineName: 'L28',
      destination: 'Ospitaleak',
      minutesAhead: 5,
      active: true,
    });
  }

  it('persiste los fallos de envío y permite desactivar el aviso', () => {
    for (const alert of getAlertsByChat(chatId)) deleteAlert(alert.id);
    const alert = newAlert();
    try {
      expect(alert.sendFailures).toBe(0);
      expect(updateAlert(alert.id, { sendFailures: 2 })).toBe(true);
      expect(getAlertById(alert.id)?.sendFailures).toBe(2);
      expect(updateAlert(alert.id, { active: false, sendFailures: 3 })).toBe(
        true
      );
      expect(getAlertById(alert.id)?.active).toBe(false);
    } finally {
      deleteAlert(alert.id);
    }
  });

  it('el guard de duplicados es atómico', () => {
    for (const alert of getAlertsByChat(chatId)) deleteAlert(alert.id);
    const alert = newAlert();
    try {
      expect(() => newAlert()).toThrow(DuplicateAlertError);
      expect(getAlertsByChat(chatId)).toHaveLength(1);

      deleteAlert(alert.id);
      expect(() => newAlert()).not.toThrow();
    } finally {
      for (const current of getAlertsByChat(chatId)) deleteAlert(current.id);
    }
  });

  it('updateAlert ignora columnas no permitidas', () => {
    const alert = newAlert();
    try {
      expect(updateAlert(alert.id, { bogus: 1 } as never)).toBe(false);
      expect(getAlertById(alert.id)?.destination).toBe('Ospitaleak');
    } finally {
      deleteAlert(alert.id);
    }
  });
});

describe('Database - Favorite lines', () => {
  const testChatId = 333333333;

  it('creates, finds and removes a favorite line', () => {
    for (const line of getFavoriteLines(testChatId))
      removeFavoriteLine(line.id);

    const line = addFavoriteLine({
      chatId: testChatId,
      lineId: '28',
      lineName: 'L28',
      destination: 'Amara - Ospitaleak - Miramon',
    });
    expect(line.id).toBeDefined();
    expect(findFavoriteLine(testChatId, '28')?.id).toBe(line.id);
    expect(getFavoriteLines(testChatId)).toHaveLength(1);
    expect(removeFavoriteLine(line.id)).toBe(true);
    expect(getFavoriteLines(testChatId)).toHaveLength(0);
  });
});
