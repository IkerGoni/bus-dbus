import { describe, expect, it } from 'vitest';
import {
  formatArrival,
  formatArrivalsList,
  getNextArrival,
  getNextArrivals,
  getStopLines,
} from '../src/arrival.js';
import {
  addFavorite,
  createAlert,
  deleteAlert,
  findActiveAlert,
  findFavorite,
  getActiveAlerts,
  getAlertsByChat,
  getFavorites,
  removeFavorite,
  updateAlert,
} from '../src/db.js';
import {
  fetchPopularStops,
  fetchStopTimes,
  getStopName,
} from '../src/dbus-client.js';

// Parada pública real: 1 = Boulevard 9, San Sebastián.
const STOP = '1';

describe('Dbus live data', () => {
  it('returns real arrival data for a known stop', async () => {
    const times = await fetchStopTimes(STOP, true);
    expect(times).not.toBeNull();
    expect(times!.estado).toBeTruthy();

    if (times!.estado === 'DATOS') {
      expect(times!.parada?.cod).toBe(STOP);
      expect(times!.parada?.desc).toContain('Boulevard');
      expect(times!.tiempos.length).toBeGreaterThan(0);
      const first = times!.tiempos[0];
      expect(first.itinerario.linea.cod).toBeTruthy();
      expect(first.itinerario.destino).toBeTruthy();
      expect(first.minutos).toBeGreaterThanOrEqual(0);
      expect([1, 2]).toContain(first.tipo);
    }
  });

  it('builds a real catalog of popular stops', async () => {
    const stops = await fetchPopularStops();
    expect(stops.length).toBeGreaterThan(5);
    expect(stops.every((s) => /^\d+$/.test(s.cod))).toBe(true);
    expect(stops.every((s) => s.desc.length > 0)).toBe(true);
    expect(getStopName(STOP)).toBeTruthy();
  });
});

describe('Arrival logic (live)', () => {
  it('returns normalized arrivals sorted by proximity', async () => {
    const arrivals = await getNextArrivals(STOP);
    if (!arrivals.length) return; // sin servicio en el momento del test
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
    const arrivals = await getNextArrivals(STOP);
    if (!arrivals.length) return;
    const lineId = arrivals[0].lineId;
    const filtered = await getNextArrivals(STOP, `L${lineId}`);
    expect(filtered.length).toBeGreaterThan(0);
    expect(filtered.every((a) => a.lineId === lineId)).toBe(true);
    expect((await getNextArrival(STOP, lineId))?.lineId).toBe(lineId);
  });

  it('lists real lines serving the stop', async () => {
    const lines = await getStopLines(STOP);
    if (!lines.length) return;
    expect(lines[0].lineId).toBeTruthy();
    expect(lines[0].destination).toBeTruthy();
  });

  it('formats arrivals for Telegram', async () => {
    const arrivals = await getNextArrivals(STOP);
    if (!arrivals.length) return;
    expect(formatArrival(arrivals[0])).toContain('L');
    expect(formatArrival(arrivals[0])).toMatch(/min|ahora/);
    expect(formatArrivalsList('Boulevard 9', arrivals)).toContain('Boulevard');
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
      stopName: 'Boulevard 9',
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
      stopName: 'Boulevard 9',
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
      stopName: 'Boulevard 9',
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
      stopName: 'Boulevard 9',
      lineId: '28',
      lineName: 'L28',
      destination: 'Ospitaleak',
    });
    expect(findFavorite(chatId, STOP, '28')?.id).toBe(fav.id);
    expect(findFavorite(chatId, STOP, '21')).toBeNull();
    removeFavorite(fav.id);
  });
});
