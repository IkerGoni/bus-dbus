import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import { Telegram, Telegraf } from 'telegraf';
import { stopAlertScheduler } from '../src/alerts.js';
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

function messageUpdate(text: string, updateId: number): Record<string, unknown> {
  const isCommand = text.startsWith('/');
  return {
    update_id: updateId,
    message: {
      message_id: updateId,
      date: Math.floor(Date.now() / 1000),
      chat,
      from,
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

function callbackUpdate(data: string, updateId: number): Record<string, unknown> {
  return {
    update_id: updateId,
    callback_query: {
      id: `cb${updateId}`,
      from,
      chat_instance: 'test',
      data,
      message: {
        message_id: updateId,
        date: Math.floor(Date.now() / 1000),
        chat,
      },
    },
  };
}

function texts(): string {
  return sent
    .filter((call) => call.method === 'sendMessage' || call.method === 'editMessageText')
    .map((call) => JSON.stringify(call.payload))
    .join('\n');
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
});
