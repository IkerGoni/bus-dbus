# AGENTS.md — bus-dbus

Fuente de verdad del proyecto. Si este archivo contradice a un doc antiguo,
manda aquí. `README.md` = visión y uso; `ESTADO.md` = estado de sesión;
`docs/README.md` = índice de docs (qué mirar para cada cosa);
`docs/05-ui/02-bot-telegram.md` = UI del bot (el resto de `docs/05-ui/` es la
PWA, histórico); `docs/07-android-mvp/` = spec del MVP Android (ADR 009);
`docs/03-architecture/` = ADRs (001–008 históricos, 009 vigente);
`docs/04-planning/` y `docs/plan/` = planning histórico de la PWA y del split.

## Qué hay hoy

- **Bot de Telegram** (`apps/bot/`): activo, datos reales de la API JSON de
  Dbus. Paquete `@dbus/bot`. SQLite en `apps/bot/bot.db`.
- **App Android** (`apps/android/`): Sprints 0–4 hechos (último merge
  `b8e08f8`); Sprint 5 (publicación) pendiente. MVP v1 nativo, Kotlin +
  Compose, Gradle fuera de pnpm. Spec en `docs/07-android-mvp/`, plan en
  `docs/07-android-mvp/SPRINTS.md`, prompt pendiente en
  `docs/07-android-mvp/prompts/SPRINT-5.md`, decisión en ADR 009.
- **PWA** (`apps/mobile/`): **congelada** por ADR 009 (build roto, ver
  `KNOWN_ISSUES.md`). No arreglar ni borrar sin acuerdo. Paquete `@dbus/mobile`.
- **Shared** (`packages/core/`, `packages/contracts/`): dominio y contratos
  extraídos en el split. `@dbus/core`, `@dbus/contracts`.
- Monorepo pnpm (`pnpm-workspace.yaml`), Node >= 22.

**Convención de lanes:** `apps/bot/**` es propiedad del lane bot;
`apps/android/**` del lane android; `apps/mobile/**` del lane mobile (congelado); `packages/**` es compartido y solo se
modifica de forma aditiva. No tocar el lane ajeno salvo acuerdo explícito.

## Reglas del bot (no negociables)

1. **Datos reales únicamente.** Fuente:
   `http://app.dbus.eus/SSIIMovilWSv2/ws/cons` (JSON, HTTP, sin auth).
   Prohibido mock/fallback en producción. Si no hay datos, se dice. Los feeds
   `.pb` de `opendata.euskadi.eus` dan 403: no revivirlos para el bot.
2. **Llegadas siempre en vivo.** `apps/bot/data/dbus-catalog.json` es solo
   índice de descubrimiento (nombre/código/destino), nunca fuente de horarios.
   Regenerar con `cd apps/bot && npm run build:catalog`; no editar a mano;
   commitear el JSON resultante.
3. **Secretos.** `BOT_TOKEN` solo por entorno. Nunca escribirlo en
   `apps/bot/.env`, imprimirlo, loguearlo ni commitearlo. Si se compartió en
   texto plano, rotarlo. `.env.example` es plantilla, no credencial.
   Los tests inyectan `BOT_TOKEN=vitest_test_token` en el script `test`.
4. **Instancia única.** Lock por PID y retry de 409 de Telegram. Producción
   con `npm run start` desde `apps/bot` (nunca `tsx watch`). No llamar
   `getUpdates` a mano ni levantar un segundo proceso mientras hay polling.
5. **Identidad de línea.** Manda el código público (`28`); el id interno
   (`32`) es alias de entrada. `32` también es línea pública: en búsquedas
   gana la pública. Aceptar líneas alfanuméricas (`B4`, `B9`).
6. **Favoritos.** Parada completa (`line_id=''`), parada+línea y línea
   favorita (`user_favorite_lines`). Dedup por `(chat, parada, línea)`.
7. **Avisos.** Dedup por `(chat, parada, línea)`, caducidad a 7 días,
   preferencia GPS (`tipo=1`), filtro de destino, aislamiento de envíos y
   guard de reentrada del scheduler. No quitar estas garantías. GPS es solo
   `tipo=1`; cualquier otro valor (`2`, y `0` visto el 2026-11-22) es horario.
   Ningún test asume un conjunto cerrado de valores de `tipo`.
8. **UX de descubrimiento.** Sin "paradas populares": usar `/buscar`,
   `/linea`, `/lineas` (listado completo de líneas y de sus paradas) y
   `/ubicacion` (paradas cercanas con el índice local de OSM; la ubicación
   se usa una vez y nunca se guarda).
   Callbacks stateless: el dato va en `data` (`srch_s_1`, `ld_1281`,
   `ls_1_28`, `aline_<parada>_<línea>`, `minutes_<parada>_<línea>_<min>`),
   no en sesiones. La única sesión efímera permitida es el texto libre de los
   flujos multi-paso (`session.ts`); nunca se persiste en disco.
9. **Reinicio obligatorio en cada versión nueva del bot.** Todo cambio en
   `apps/bot/**` (o en `packages/**` que el bot consuma) solo existe en
   producción tras reiniciar: commit + push → SIGTERM al PID anterior →
   recompilar → arrancar con `BOT_TOKEN` por entorno y `npm run start` →
   comprobar log y lock con el PID nuevo. `git push` **no** despliega: el
   proceso vivo sigue ejecutando el `dist` anterior. No dar una versión por
   terminada sin este paso; pedir autorización si el reinicio requiere
   intervención en producción.

## Reglas de la app Android

Las reglas 1 (datos reales), 2 (llegadas en vivo), 5 (identidad de línea),
6 (favoritos) y 7 (dedup, caducidad 7 días, preferencia GPS, filtro de
destino) del bot aplican igual. Además:

- Sin SDK de mapas, sin Firebase/FCM y sin servidor propio. Único elemento
  remoto nuestro permitido: un `config.json` estático opcional con copia
  empaquetada (ADR 009); la app nunca depende de él para arrancar.
- Cleartext HTTP solo para `app.dbus.eus` (network security config).
- Nunca mostrar segundos: la API da `minutos` enteros.
- El catálogo empaquetado se genera desde `apps/bot/data/*.json`; no se edita.
- Textos en `res/values*/strings*.xml`: `es` (`values/`) y `eu`
  (`values-eu/`) con las mismas claves (`StringsParityTest`).

## Verificación

```bash
pnpm --filter @dbus/bot verify     # tsc -b + biome + 86 tests (BOT_TOKEN + DB_PATH=:memory:)
pnpm --filter @dbus/bot build      # tsc -b -> apps/bot/dist
```

Android (toolchain en `apps/android/README.md`):

```bash
cd apps/android && ./gradlew check assembleDebug   # lint + 94 tests JVM por variante + APK debug
```

Root: `pnpm verify` (lint + typecheck + tests de `apps/bot` y `packages/**`).
No cubre Android (se verifica con `gradlew`) ni la PWA congelada (su `test`
pasa con 0 tests). Los
tests E2E/integración usan la API real: si Dbus está caído, decirlo, no
silenciarlo. No dar nada por bueno sin ejecutar la verificación.

## Despliegue (instrucción permanente del usuario)

Aplica a **toda versión nueva del bot**, sin excepción (regla 9): commit +
push, parar el proceso anterior con SIGTERM, recompilar, arrancar desde
`apps/bot` con `BOT_TOKEN` por entorno y `npm run start`, comprobar log, lock y
PID nuevo. Un cambio solo de docs no requiere reiniciar el bot.

## Convenciones

- Mensajes al usuario en español; `escapeMd` antes de interpolar texto externo
  en Markdown de Telegram.
- Preferir funciones puras testeables antes que lógica dentro de handlers.
- No persistir ubicación del usuario: efímera en sesión.
- Tests nuevos: bot en `apps/bot/tests/`, Android en
  `apps/android/app/src/test/`; dominio compartido en `packages/core/`.

## Documentación

- `docs/README.md` es el índice: actualizarlo al añadir, retirar o archivar
  un doc.
- Un doc histórico no se reescribe: lleva arriba «Histórico: ver …» con el doc
  vigente. Los prompts ya ejecutados (`docs/07-android-mvp/prompts/SPRINT-N.md`)
  se conservan tal cual.


ANEXO HASTA FIN DE SPRINT 8:


## 1. Política de Configuración y Tokens (Modelo en 3 Niveles)

### Nivel 1 · Parámetros Operativos (`assets/config.json` — Runtime + Remoto)
Queda **prohibido** escribir inline en código Kotlin valores operativos de red, tiempos o negocio:
- Intervalos de sondeo (`pollDelayMs`, escalones adaptativos), periodo de `Ticker`, polling visible.
- Umbrales de frescura (`FRESH`, `STALE`, `EXPIRED`) y tamaño de historial de `LiveFeeds`.
- Reglas de avisos (`triggerBufferSec`, `expirationDays`, rangos de `minutesAhead`, ventanas de avisos recurrentes, tope de badge `9+`).
- Timeouts HTTP, caché remota, velocidad de caminata (`walkingSpeedKmh`) y límites de ítems en `HomeScreen`.

**Regla de inyección:** `domain/` nunca accede a `assets/` ni `Context`. Las funciones puras reciben sub-objetos tipados (`PollingConfig`, `FreshnessConfig`, `AlertsConfig`) con validación defensiva en `ConfigRepository`.

### Nivel 2 · Animaciones y Tokens Visuales (`MotionTokens` / `DesignTokens` — Compile-Time)
Queda **prohibido** declarar inline duraciones (`tween(200)`), fracciones de desplazamiento, escalas (`0.97f`), ángulos (`72f`), constantes de *spring*, parámetros de *stagger*, opacidades de pulso o espaciados/radios estándar.
- Todos estos valores se consumen desde `MotionTokens` y `DesignTokens` (inicialmente en `ui/theme/` y a partir de v1.1 generados en *build-time* desde `packages/contracts/design-tokens.json`).
- **No crear parsers JSON en runtime para estilos o animaciones** (protege tiempo de arranque, R8 inlining y `@Preview`).

### Nivel 3 · Excepciones Permitidas Inline
- Identidades matemáticas (`0`, `1`, `0f`, `1f`), índices de listas.
- Micro-ajustes ópticos locales irrepetibles (`1.dp` hairline, `2.dp` ajuste óptico de fuente).
- Constantes de protocolo inmutables de la API Dbus (`tipo == 1.0`, `"DATOS"`, `"SIN_SERVICIO"`).

### Regla "Boy Scout" Acotada (Anti-Explosión de Diffs)
- Si encuentras valores inline de Nivel 1 o Nivel 2 **dentro de los archivos que estás modificando para tu tarea actual**, extráelos a `config.json` o `MotionTokens`/`DesignTokens` y añade/actualiza el test correspondiente.
- **No hagas barridos globales** en archivos no relacionados con tu tarea actual salvo que la tarea sea explícitamente un ticket de auditoría/refactorización.

---

## 2. Regla UX Obligatoria: Cero Códigos Internos o Numéricos Opacos en UI

La interfaz está diseñada para personas en la calle, nunca para exponer claves de base de datos ni de API:
1. **Nunca mostrar códigos de parada (`stopId`) en UI:** No renderizar `stopId`, `StopCodePill` ni números de poste en tarjetas, filas de lista, timelines, cabeceras, banners, notificaciones o placeholders de búsqueda. Identificar siempre las paradas por su **nombre legible**, sus **líneas**, su **distancia a pie** o su **sentido/destino** (en caso de nombres homónimos).
   - *Uso interno intacto:* `stopId` permanece únicamente en capa interna (`Room`, `DbusClient`, `LazyColumn` `key`, `sharedElement` `key`, rutas y deep links `dbus://stop/{stopId}`). En búsqueda puede aceptarse coincidencia silenciosa en segundo plano sin mostrar el número al usuario.
2. **Nunca mostrar IDs internos de línea ni de itinerario:** Prohibido renderizar el ID interno de Dbus (ej. `32` para la línea pública `28`), IDs técnicos de sentido/itinerario o identificadores numéricos no reconocibles en la calle. Mostrar **exclusivamente el código público oficial** (`28`, `5`, `B4`, `B9`) en `LineBadge` y el **destino legible**.

---

## 3. Reglas de Rendimiento y Preparación KMP (iOS)

1. **Cero I/O en Main Thread:** Nunca leer `assets/`, `filesDir` ni Room en el hilo principal.
2. **Aislar el `Ticker` de 30 s:** No pasar `nowMs` a pantallas completas; usar `distinctUntilChanged()` en ViewModel o lambdas hacia composables hoja.
3. **Animaciones en fase de dibujo:** Leer estados animados dentro de `Modifier.graphicsLayer { ... }` o `Modifier.offset { ... }`, nunca disparando recomposición de filas de `LazyColumn`.
4. **Estabilidad de listas:** Todo `items(...)` en `LazyColumn` debe declarar `key` estable y `contentType`.
5. **Accesibilidad y Robolectric:** Toda animación respeta `LocalReducedMotion` (`Settings.Global.ANIMATOR_DURATION_SCALE == 0f`). Cualquier `rememberInfiniteTransition` debe condicionarse a `!reducedMotion && !LocalInspectionMode.current` para no bloquear `waitForIdle()` en tests.
6. **Pureza KMP en `domain/`:** Prohibido añadir nuevos imports `java.time.*`, `java.util.Locale`, `java.text.*` o `String.format()` dentro de `domain/`. Usar `Long` (epoch ms UTC) o `kotlinx.datetime` y formatear cadenas únicamente en `ui/`.

---

## 4. Checklist Obligatorio Antes de Entregar PR / Commit
- [ ] Ningún `tween(número)`, `delay(número)` ni constante operativa inline introducida en el diff.
- [ ] Ningún `stopId`, `StopCodePill`, ID interno de línea ni código técnico visible en la UI o notificaciones.
- [ ] Ningún texto o `contentDescription` hardcodeado (`HardcodedText = ERROR` + paridad `values/` y `values-eu/`).
- [ ] Ningún import `java.time.*` o Android en `domain/`.
- [ ] `./gradlew check assembleDebug` ejecutado y en verde (incluyendo lint y todos los tests JVM/Robolectric).