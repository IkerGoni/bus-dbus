# Estado — bus-dbus

Actualizado: 2026-11-22 15:00 · Última sesión: Android Sprint 2 (consulta)

> **`AGENTS.md` es la fuente de verdad.** Este archivo es el estado de sesión;
> si algo aquí contradice a `AGENTS.md`, manda `AGENTS.md`.

## Objetivo actual

MVP Android nativo sin backend (ADR 009). Plan en
`docs/07-android-mvp/SPRINTS.md`. **Sprints 0, 1 y 2 hechos; siguiente: Sprint 3 (Inicio, Avisos, Ajustes,
Onboarding).** El bot sigue en producción sin cambios
(PID `80402`).

## Hecho en la última sesión

- **Sprint 2** en `apps/android/`: `AppContainer` en `BusDonostiaApp` (sin DI;
  catálogo cargado en IO, `refreshIfDue` sin bloquear). A02 Paradas (buscador
  offline con paridad `searchStops`, Cercanas top 20 Haversine con «X m · Y min»
  a 80 m/min y nota de estimación, Favoritas STOP + STOP_LINE, Recientes,
  permiso de ubicación no bloqueante, atribución ODbL). A05 Detalle (sondeo
  `pollIntervalSec` con `repeatOnLifecycle(STARTED)`, 5 vistas vía
  `stopArrivalsView`, `notice` también con datos, chips con color visto en
  vivo, ★ STOP y STOP_LINE, recientes, `geo:` solo con coordenadas y app que
  lo abra). A03 Líneas (búsqueda `32` → gana la pública, filtros Todas /
  Favoritas / Nocturnas, ★ LINE) y A03b Itinerario (timeline, tap → A05 con la línea).
- `./gradlew check assembleDebug` verde: 82 tests JVM por variante (164), 0
  errores de lint. Probado en el AVD `dbus36` contra la API real: 340 en vivo,
  parada 1 `SIN_SERVICIO` («Parada suprimida - traslado: L21, L28 Okendo 11»),
  `STALE` y error en modo avión. 19 capturas en
  `docs/07-android-mvp/capturas/sprint-2/`. `grep` de literales limpio.
- Memoria: `gradle.properties` cierra los daemons a los 10 min y limita el de
  Kotlin a 1 GB; el emulador se apaga al terminar las capturas.
- Prompt del Sprint 3 en `docs/07-android-mvp/prompts/SPRINT-3.md`.

## Pendiente (por prioridad)

- [ ] **Sprint 3** (prompt `docs/07-android-mvp/prompts/SPRINT-3.md`).
- [ ] Revisar en móvil real Cercanas con ubicación aproximada (en Android 12+
      se difumina ~2 km) y el tiempo hasta la primera posición.
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

- 2026-11-22 · Android Sprint 2: se piden COARSE + FINE juntos (el usuario
  elige aproximada o precisa en el diálogo; con solo aproximada Cercanas sería
  poco útil). Ubicación sin Play Services (`LocationManagerCompat`), atajo con
  la última posición del sistema de < 2 min, nunca persistida.
- 2026-11-22 · A05: si falla un refresco, los datos válidos anteriores siguen
  `live` mientras sean FRESH, pasan a `degraded` (banner con la hora real del
  dato) si STALE y a `error` si EXPIRED. ★ STOP_LINE = botón bajo los chips
  cuando hay una línea seleccionada; desde A03b o una favorita parada+línea se
  entra con ese chip ya seleccionado.

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

Rama `android/sprint-2` integrada en `main` y borrada (autorizado por Iker).

## Siguiente sesión

- Prompt: `docs/07-android-mvp/prompts/SPRINT-3.md`.
