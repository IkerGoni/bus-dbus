# AGENTS.md — bus-dbus

Fuente de verdad del proyecto. Si este archivo contradice a un doc antiguo,
manda aquí. `README.md` = visión y uso; `ESTADO.md` = estado de sesión;
`03-architecture/adrs/` = decisiones históricas.

## Qué hay hoy

- **Bot de Telegram** (`bot/`): activo, funciona con datos reales de la API
  JSON de Dbus. Es el único componente operativo y desplegado.
- **PWA** (`src/`): Sprint 3 bloqueado por errores preexistentes de TS/lint.
  Fuera del alcance del bot salvo que se pida explícitamente.

## Reglas del bot (no negociables)

1. **Datos reales únicamente.** La fuente es
   `http://app.dbus.eus/SSIIMovilWSv2/ws/cons` (JSON, HTTP, sin auth).
   Prohibido mock/fallback en producción. Si no hay datos, se dice; no se
   inventan paradas ni llegadas. Los feeds `.pb` de `opendata.euskadi.eus`
   dan 403: no revivirlos para el bot.
2. **Llegadas siempre en vivo.** `bot/data/dbus-catalog.json` es solo un
   índice de descubrimiento (búsqueda por nombre/código/destino), nunca
   fuente de horarios. Regenerar con `cd bot && npm run build:catalog`;
   no editar a mano; commitear el JSON resultante.
3. **Secretos.** `BOT_TOKEN` se pasa solo por variable de entorno. Nunca
   escribirlo en `bot/.env`, imprimirlo, loguearlo ni commitearlo. Si se
   comparte en texto plano, rotarlo. `bot/.env.example` es plantilla, no
   credencial.
4. **Instancia única.** Existe lock por PID y retry de 409 de Telegram.
   Producción se arranca con `npm run start` (no `tsx watch`). Nunca llamar
   `getUpdates` a mano mientras el bot hace polling, ni levantar un segundo
   proceso: el lock y el long-polling se pelean.
5. **Identidad de línea.** Manda el código público (`28`). El id interno
   (`32`) se acepta como alias de entrada. Ojo: `32` también es una línea
   pública, así que en búsquedas gana la pública. Aceptar líneas
   alfanuméricas (`B4`, `B9`) en comandos y callbacks.
6. **Favoritos.** Tres tipos: parada completa (`line_id=''`), parada+línea,
   y línea favorita (`user_favorite_lines`). Dedup por
   `(chat, parada, línea)`. No eliminar estas semánticas.
7. **Avisos.** Dedup por `(chat, parada, línea)`, caducidad a 7 días,
   preferencia por GPS (`tipo=1`), filtro de destino y aislamiento de fallos
   de envío por chat. El scheduler ya tiene guard de reentrada: no quitarlo.
8. **UX de descubrimiento.** No hay lista de "paradas populares": se
   eliminó por arbitraria e incompleta. Usar `/buscar` y `/linea`.

## Verificación antes de dar algo por hecho

```bash
cd bot && npx tsc --noEmit
cd bot && npx biome check src/ tests/
cd bot && npm run build
npm run test        # desde la raíz: 40 tests
```

No reportar éxito sin haber ejecutado esto. Los tests de integración y E2E
usan la API real: pueden fallar si Dbus está caído; decirlo, no silenciarlo.

## Despliegue (instrucción permanente del usuario)

Tras cualquier cambio de código: commit + push, parar el proceso anterior con
SIGTERM, recompilar, arrancar con `BOT_TOKEN` por entorno y `npm run start`,
comprobar log y lock. Un cambio solo de docs no requiere reiniciar el bot.

## Convenciones

- Comandos y mensajes al usuario en español; `escapeMd` antes de interpolar
  texto externo en Markdown de Telegram.
- Callbacks de teclado stateless: codificar el dato en el `data`
  (`srch_s_1`, `ld_1281`, `ls_1_28`), no depender de sesiones para datos
  que se pueden llevar en el propio callback.
- No persistir coordenadas de usuario ni ubicación: efímeras en sesión.
- Los tests nuevos van en `bot/tests/`; preferir funciones puras testeables
  en `arrival.ts` / `catalog.ts` antes que lógica dentro de handlers.
