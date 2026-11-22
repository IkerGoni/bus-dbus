import { Telegraf, Telegram } from 'telegraf';
import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import { stopAlertScheduler } from '../src/alerts.js';
import {
  deleteAlert,
  getAlertsByChat,
  getFavoriteLines,
  getFavorites,
  removeFavorite,
  removeFavoriteLine,
} from '../src/db.js';
import { setupBot } from '../src/handlers.js';

interface SentCall {
  method: string;
  payload: Record<string, unknown>;
}

const sent: SentCall[] = [];
// handleUpdate() crea un Telegram nuevo por update, así que hay que stubear
// el prototipo, no la instancia del bot.
const realCallApi = Telegram.prototype.callApi;
Telegram.prototype.callApi = (async (
  method: string,
  payload: Record<string, unknown>
) => {
  sent.push({ method, payload });
  return payload ?? true;
}) as never;

const bot = new Telegraf('vitest:fake_token');
(bot as unknown as { botInfo: unknown }).botInfo = {
  id: 9133861608,
  is_bot: true,
  first_name: 'DbusAlarm',
  username: 'dbus_alarm_bot',
  can_join_groups: true,
  can_read_all_group_messages: false,
  supports_inline_queries: false,
  can_connect_to_business: false,
  has_main_web_app: false,
};

const chat = { id: 123456789, type: 'private' as const, first_name: 'Tester' };
const from = { id: 123456789, is_bot: false, first_name: 'Tester' };

function messageUpdate(
  text: string,
  updateId: number,
  targetChat = chat
): Record<string, unknown> {
  const isCommand = text.startsWith('/');
  return {
    update_id: updateId,
    message: {
      message_id: updateId,
      date: Math.floor(Date.now() / 1000),
      chat: targetChat,
      from: { ...from, id: targetChat.id },
      text,
      ...(isCommand
        ? {
            entities: [
              {
                type: 'bot_command',
                offset: 0,
                length: text.split(/\s+/)[0].length,
              },
            ],
          }
        : {}),
    },
  };
}

function callbackUpdate(
  data: string,
  updateId: number,
  targetChat = chat
): Record<string, unknown> {
  return {
    update_id: updateId,
    callback_query: {
      id: `cb${updateId}`,
      from: { ...from, id: targetChat.id },
      chat_instance: 'test',
      data,
      message: {
        message_id: updateId,
        date: Math.floor(Date.now() / 1000),
        chat: targetChat,
      },
    },
  };
}

function textsSince(start = 0): string {
  return sent
    .slice(start)
    .filter(
      (call) =>
        call.method === 'sendMessage' || call.method === 'editMessageText'
    )
    .map((call) => JSON.stringify(call.payload))
    .join('\n');
}

function texts(): string {
  return textsSince(0);
}

beforeAll(() => {
  setupBot(bot);
});

afterAll(() => {
  stopAlertScheduler();
  Telegram.prototype.callApi = realCallApi;
});

describe('Telegram bot end-to-end (real Dbus data)', () => {
  it('/start shows the main menu', async () => {
    await bot.handleUpdate(messageUpdate('/start', 1) as never);
    expect(texts()).toContain('Dbus Donostia');
    expect(texts()).toContain('Ver llegadas');
  });

  it('menu_arrivals asks for a stop code', async () => {
    await bot.handleUpdate(callbackUpdate('menu_arrivals', 2) as never);
    expect(texts()).toContain('Escribe el código de la parada');
  });

  it('a stop code returns live arrivals', async () => {
    await bot.handleUpdate(messageUpdate('2', 3) as never);
    const output = texts();
    expect(output).toContain('Urbieta 12');
    expect(output).toMatch(/L\d+/);
  });

  it('/lineas lists real lines at the stop', async () => {
    await bot.handleUpdate(messageUpdate('/lineas 2', 4) as never);
    expect(texts()).toMatch(/Líneas en .*Urbieta/);
  });

  it('callback answers are sent', () => {
    expect(sent.some((call) => call.method === 'answerCallbackQuery')).toBe(
      true
    );
  });

  it('accepts the internal Dbus line id as an alias for the public code', async () => {
    const start = sent.length;
    await bot.handleUpdate(messageUpdate('/parada 2 32', 5) as never);
    const output = textsSince(start);
    expect(output).not.toContain('no pasa por la parada');
    expect(output).toContain('Urbieta 12');
  });

  it('creates an alert with the public line and offers a delete button', async () => {
    const target = { ...chat, id: 555555555 };
    for (const alert of getAlertsByChat(target.id)) deleteAlert(alert.id);
    try {
      let start = sent.length;
      await bot.handleUpdate(
        messageUpdate('/aviso 2 32 5', 6, target) as never
      );
      const created = textsSince(start);
      expect(created).toContain('Aviso creado');
      expect(created).toContain('L28');

      start = sent.length;
      await bot.handleUpdate(messageUpdate('/lista', 7, target) as never);
      const listed = textsSince(start);
      expect(listed).toContain('adel_');
      expect(listed).toContain('L28');

      const alert = getAlertsByChat(target.id)[0];
      expect(alert?.lineId).toBe('28');

      start = sent.length;
      await bot.handleUpdate(
        callbackUpdate(`adel_${alert.id}`, 8, target) as never
      );
      expect(
        sent
          .slice(start)
          .some(
            (call) =>
              call.method === 'answerCallbackQuery' &&
              String(call.payload.text ?? '').includes('Aviso eliminado')
          )
      ).toBe(true);
      expect(textsSince(start)).not.toContain('adel_');
      expect(getAlertsByChat(target.id)).toHaveLength(0);
    } finally {
      for (const alert of getAlertsByChat(target.id)) deleteAlert(alert.id);
    }
  });

  it('rechaza un aviso duplicado por comando', async () => {
    const target = { ...chat, id: 555555556 };
    for (const alert of getAlertsByChat(target.id)) deleteAlert(alert.id);
    try {
      await bot.handleUpdate(
        messageUpdate('/aviso 2 32 5', 20, target) as never
      );
      const start = sent.length;
      await bot.handleUpdate(
        messageUpdate('/aviso 2 32 5', 21, target) as never
      );
      expect(textsSince(start)).toContain('Ya tienes un aviso activo');
    } finally {
      for (const alert of getAlertsByChat(target.id)) deleteAlert(alert.id);
    }
  });

  it('el flujo interactivo informa del duplicado y muestra la ETA', async () => {
    const target = { ...chat, id: 555555557 };
    for (const alert of getAlertsByChat(target.id)) deleteAlert(alert.id);
    try {
      let start = sent.length;
      await bot.handleUpdate(messageUpdate('/start', 22, target) as never);
      await bot.handleUpdate(callbackUpdate('menu_alert', 23, target) as never);
      await bot.handleUpdate(messageUpdate('2', 24, target) as never);
      await bot.handleUpdate(callbackUpdate('aline_28', 25, target) as never);
      await bot.handleUpdate(callbackUpdate('minutes_5', 26, target) as never);
      const first = textsSince(start);
      expect(first).toContain('Aviso creado');
      expect(first).toContain('Próxima llegada');

      start = sent.length;
      await bot.handleUpdate(callbackUpdate('menu_alert', 27, target) as never);
      await bot.handleUpdate(messageUpdate('2', 28, target) as never);
      await bot.handleUpdate(callbackUpdate('aline_28', 29, target) as never);
      await bot.handleUpdate(callbackUpdate('minutes_5', 30, target) as never);
      expect(textsSince(start)).toContain('Ya tienes un aviso activo');
      const answers = sent
        .slice(start)
        .filter((call) => call.method === 'answerCallbackQuery')
        .map((call) => String(call.payload.text ?? ''));
      expect(answers.some((text) => text.includes('Ya tenías un aviso'))).toBe(
        true
      );
    } finally {
      for (const alert of getAlertsByChat(target.id)) deleteAlert(alert.id);
    }
  });

  it('/buscar devuelve paradas y líneas del catálogo real', async () => {
    let start = sent.length;
    await bot.handleUpdate(messageUpdate('/buscar Boulevard', 40) as never);
    expect(textsSince(start)).toContain('srch_s_1');

    start = sent.length;
    await bot.handleUpdate(messageUpdate('/buscar 28', 41) as never);
    expect(textsSince(start)).toContain('srch_l_28');
  });

  it('la búsqueda por texto responde aunque Telegram rechace editar el mensaje del usuario', async () => {
    // Error real visto en producción: el prompt del botón es nuestro, pero el
    // texto que escribe el usuario no se puede editar (400 can't be edited).
    const target = { ...chat, id: 555555570 };
    const previous = Telegram.prototype.callApi;
    Telegram.prototype.callApi = (async (
      method: string,
      payload: Record<string, unknown>
    ) => {
      if (method === 'editMessageText') {
        throw new Error("400: Bad Request: message can't be edited");
      }
      return previous(method, payload);
    }) as never;
    const start = sent.length;
    try {
      await bot.handleUpdate(
        callbackUpdate('menu_search', 90, target) as never
      );
      await bot.handleUpdate(messageUpdate('Boulevard', 91, target) as never);
    } finally {
      Telegram.prototype.callApi = previous;
    }
    const output = textsSince(start);
    expect(output).toContain('srch_s_1');
    expect(output).not.toContain('Ha ocurrido un error');
  });

  it('el botón de búsqueda explica qué escribir', async () => {
    const target = { ...chat, id: 555555571 };
    const start = sent.length;
    await bot.handleUpdate(callbackUpdate('menu_search', 92, target) as never);
    const output = textsSince(start);
    expect(output).toContain('Buscar parada o línea');
    expect(output).toContain('Escribe el nombre de la parada');
    expect(output).not.toContain(
      'Escribe un código de parada, nombre de calle'
    );
  });

  it('/linea muestra destinos reales de la línea', async () => {
    const start = sent.length;
    await bot.handleUpdate(messageUpdate('/linea 28', 42) as never);
    const output = textsSince(start);
    expect(output).toContain('ld_');
    expect(output).toContain('L28');
  });

  it('/fav add sin línea guarda la parada completa', async () => {
    const target = { ...chat, id: 555555558 };
    for (const fav of getFavorites(target.id)) removeFavorite(fav.id);
    try {
      const start = sent.length;
      await bot.handleUpdate(messageUpdate('/fav add 2', 43, target) as never);
      expect(textsSince(start)).toContain('Favorito guardado');
      expect(getFavorites(target.id)[0]?.lineId).toBe('');
    } finally {
      for (const fav of getFavorites(target.id)) removeFavorite(fav.id);
    }
  });

  it('/fav linea guarda una línea favorita', async () => {
    const target = { ...chat, id: 555555559 };
    for (const line of getFavoriteLines(target.id)) removeFavoriteLine(line.id);
    try {
      const start = sent.length;
      await bot.handleUpdate(
        messageUpdate('/fav linea 28', 44, target) as never
      );
      expect(textsSince(start)).toContain('Línea guardada');
      expect(getFavoriteLines(target.id)[0]?.lineId).toBe('28');
    } finally {
      for (const line of getFavoriteLines(target.id))
        removeFavoriteLine(line.id);
    }
  });

  it('/lineas sin argumentos abre el listado paginado de líneas', async () => {
    const target = { ...chat, id: 555555560 };
    let start = sent.length;
    await bot.handleUpdate(messageUpdate('/lineas', 60, target) as never);
    let output = textsSince(start);
    expect(output).toContain('Todas las líneas');
    expect(output).toContain('srch_l_21');
    expect(output).toContain('lpages_1');

    start = sent.length;
    await bot.handleUpdate(callbackUpdate('lpages_1', 61, target) as never);
    output = textsSince(start);
    expect(output).toContain('srch_l_28');
    expect(output).toContain('lpages_0');
  });

  it('una línea muestra todas sus paradas paginadas y permite guardarlas', async () => {
    const target = { ...chat, id: 555555561 };
    let start = sent.length;
    await bot.handleUpdate(messageUpdate('/linea 28', 62, target) as never);
    expect(textsSince(start)).toContain('la_28');

    start = sent.length;
    await bot.handleUpdate(callbackUpdate('la_28', 63, target) as never);
    const output = textsSince(start);
    expect(output).toContain('todas las paradas');
    expect(output).toContain('ls_2_28');
    expect(output).toContain('lapages_28_1');

    start = sent.length;
    await bot.handleUpdate(callbackUpdate('lapages_28_1', 64, target) as never);
    expect(textsSince(start)).toContain('Página 2/3');

    start = sent.length;
    await bot.handleUpdate(callbackUpdate('ls_2_28', 65, target) as never);
    expect(textsSince(start)).toContain('favstop_2');
  });

  it('/parada ofrece las mismas acciones que la vista por callback', async () => {
    const target = { ...chat, id: 555555562 };
    const start = sent.length;
    await bot.handleUpdate(messageUpdate('/parada 2', 66, target) as never);
    const output = textsSince(start);
    expect(output).toContain('stop_refresh_2');
    expect(output).toContain('stop_alert_2');
    expect(output).toContain('favstop_2');
  });

  it('/actualizar tiene cooldown por chat', async () => {
    const target = { ...chat, id: 555555563 };
    await bot.handleUpdate(messageUpdate('/actualizar', 67, target) as never);
    const start = sent.length;
    await bot.handleUpdate(messageUpdate('/actualizar', 68, target) as never);
    expect(textsSince(start)).toContain('Espera');
  });

  it('/estado muestra diagnóstico sin secretos', async () => {
    const start = sent.length;
    await bot.handleUpdate(messageUpdate('/estado', 31) as never);
    const output = textsSince(start);
    expect(output).toContain('Estado del bot');
    expect(output).toContain('Paradas en caché');
    expect(output).not.toContain('BOT_TOKEN');
  });
});
