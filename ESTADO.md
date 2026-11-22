# Estado — bus-dbus

Actualizado: 2026-11-22 13:50 · Última sesión: Android Sprint 1 (datos y dominio)

> **`AGENTS.md` es la fuente de verdad.** Este archivo es el estado de sesión;
> si algo aquí contradice a `AGENTS.md`, manda `AGENTS.md`.

## Objetivo actual

MVP Android nativo sin backend (ADR 009). Plan en
`docs/07-android-mvp/SPRINTS.md`. **Sprints 0 y 1 hechos; siguiente: Sprint 2
(consulta: Paradas, Detalle, Líneas).** El bot sigue en producción sin cambios
(PID `80402`).

## Hecho en la última sesión

- **Sprint 1** en `apps/android/` (sin UI nueva): tarea Gradle
  `merge<Variant>Catalog` → `assets/catalog.json` (548/514/39/127, no
  commiteado); `ApiConfig` empaquetada + carga remota opcional (12 h contando
  intentos, 5 s, validación, fallback a última válida; HTTP solo para
  `app.dbus.eus`; URL remota vacía); `DbusClient` → `StopFeed`; Room
  (favoritos, avisos con índice único `(stopId, lineId)`, recientes 10, colores
  de línea); dominio puro con tests de cada fila de la tabla §5 (DST marzo y
  octubre, medianoche, una vez por franja, ONCE a 7 días); `Ticker` de 1 s.
- `./gradlew check assembleDebug` verde: 66 tests JVM por variante, 0 errores de
  lint (24 warnings de versiones/atributos). `@dbus/core` sigue verde (14 tests).
- 5 fixtures reales nuevos en `packages/contracts/fixtures/` + `README.md`
  (DATOS GPS, DATOS con avisos, SIN_SERVICIO con y sin motivo, NO DESC).
- Dos ajustes deliberados al contrato (documentados en
  `02-domain-contracts.md`): `remainingMinutes` no sube si el reloj va por
  detrás del fetch; el matching compara solo código público (el alias interno
  se resuelve antes con `toPublicLineId`).

## Pendiente (por prioridad)

- [ ] **Sprint 2 · Consulta** (ver `SPRINTS.md`; prompt
      `docs/07-android-mvp/prompts/SPRINT-2.md`).
- [ ] Fixture real de `DATOS` con `tiempos` vacío: no observado aún (las
      paradas sin buses dan `SIN_SERVICIO`); capturarlo si aparece.
- [ ] Propuesta a Iker: guardar `linea.color` en `build:catalog` (lane bot);
      hoy la app usa el último color visto en vivo.
- [ ] Sprint 5: antes de hacer público el repo, auditar el historial en busca
      de secretos; publicar ahí `config.json`.
- [ ] Bot: validación conversacional con PID `80402`; rotar token de Telegram
      (R6, `.env` raíz, gitignored).
- [ ] Bot (opcional): residuales menores de la auditoría; 15 tests de
      integración dependen de `app.dbus.eus`.
- [ ] El `ci.yml` existente usa `npm ci` en un monorepo pnpm: revisar si pasa.
- [ ] Catálogo duplicado: `packages/contracts/data/` vs `apps/bot/data/`.

## Decisiones tomadas (no reabrir)

- 2026-11-22 · Android nativo (Kotlin + Compose), sin web app previa, sin
  backend ni FCM. Config remota = JSON estático opcional (ADR 009).
- 2026-11-22 · Android Sprint 1: Room 2.8.4, KSP 2.2.21-2.0.5, OkHttp 4.12,
  kotlinx.serialization 1.9, Robolectric 4.16.1 (SDK 35) para tests de Room.
  `domain/` sin imports de Android; config remota cuenta intentos fallidos en
  las 12 h.
- 2026-11-22 · AGP 8.13.2 / Gradle 8.14.3 / Kotlin 2.2.21 por estabilidad;
  AGP 9 es migración aparte. applicationId `eus.busdonostia.app`.
- 2026-11-22 · Avisos recurrentes con franja `windowFrom/windowTo`, una vez por
  franja; sin silencio nocturno. ONCE caduca a 7 días.
- 2026-11-22 · `SIN_SERVICIO` = HTTP 200 + `avisos`, estado operativo real,
  no outage. El motivo se muestra al usuario.
- 2026-11-22 · Reinicio de producción solo con autorización explícita;
  `git push` no despliega. Regla 9 ya ejecutada para la remediación.
- 2026-11-22 · Los callbacks de avisos son stateless: parada y línea viajan
  en `callback_data`; la sesión efímera es solo para texto libre.
- 2026-11-22 · Sin Overpass en caliente: `/ubicacion` usa el índice local
  validado con Zod; sin coordenadas inventadas; atribución ODbL visible.
- 2026-11-22 · No dividir módulos del bot: la arquitectura es sana; los
  problemas eran duplicación local, no límites mal trazados.
- Fuente del bot = API JSON de Dbus sobre HTTP; feeds `.pb` → 403. Sin mocks
  ni fallbacks en producción. Llegadas siempre en vivo.
- `codParada` = código visible en marquesina, no id interno.
- Producción con `npm run start`, nunca `tsx watch`; un solo proceso/polling.
- Línea = código público; id interno solo alias de entrada. En búsquedas gana
  el código público porque `32` también es línea pública.
- Catálogo JSON = índice de descubrimiento regenerable, nunca fuente de
  horarios. Sin "paradas populares": `/buscar` y `/linea` son la UX.
- Docs: `AGENTS.md` manda; `README.md` es uso; ADRs son decisiones históricas.

## Esperando a Iker

- Rotación del token de Telegram (R6).
- Validación conversacional del bot (PID `80402`).
- Visto bueno al nombre «Bus Donostia» y al applicationId antes de Play.

## Git

Rama `android/sprint-1` integrada en `main` y borrada al cerrar esta sesión.

## Siguiente sesión

- Prompt: `docs/07-android-mvp/prompts/SPRINT-2.md`.
