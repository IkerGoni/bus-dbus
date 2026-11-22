import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import { Telegram, Telegraf } from 'telegraf';
import { stopAlertScheduler } from '../src/alerts.js';
import { deleteAlert, getAlertsByChat } from '../src/db.js';
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
    .filter((call) => call.method === 'sendMessage' || call.method === 'editMessageText')
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
    await bot.handleUpdate(messageUpdate('1', 3) as never);
    const output = texts();
    expect(output).toContain('Boulevard 9');
    expect(output).toMatch(/L\d+/);
  });

  it('/lineas lists real lines at the stop', async () => {
    await bot.handleUpdate(messageUpdate('/lineas 1', 4) as never);
    expect(texts()).toMatch(/Líneas en .*Boulevard/);
  });

  it('callback answers are sent', () => {
    expect(sent.some((call) => call.method === 'answerCallbackQuery')).toBe(true);
  });

  it('accepts the internal Dbus line id as an alias for the public code', async () => {
    const start = sent.length;
    await bot.handleUpdate(messageUpdate('/parada 1 32', 5) as never);
    const output = textsSince(start);
    expect(output).not.toContain('no pasa por la parada');
    expect(output).toContain('Boulevard 9');
  });

  it('creates an alert with the public line and offers a delete button', async () => {
    const target = { ...chat, id: 555555555 };
    for (const alert of getAlertsByChat(target.id)) deleteAlert(alert.id);
    try {
      let start = sent.length;
      await bot.handleUpdate(messageUpdate('/aviso 1 32 5', 6, target) as never);
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
});
