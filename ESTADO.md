# Estado — bus-dbus

Actualizado: 2026-11-22 16:40 · Última sesión: Android Sprint 4 (motor de avisos)

> **`AGENTS.md` es la fuente de verdad.** Este archivo es el estado de sesión;
> si algo aquí contradice a `AGENTS.md`, manda `AGENTS.md`.

## Objetivo actual

MVP Android nativo sin backend (ADR 009). Plan en
`docs/07-android-mvp/SPRINTS.md`. **Sprints 0–3 en `main`; Sprint 4 (motor de
avisos) hecho en la rama `android/sprint-4`, pendiente de merge; siguiente: Sprint 5
(publicación).**
El bot sigue en producción sin cambios (PID `80402`).

## Hecho en la última sesión

- **Sprint 4** en `apps/android/` (rama `android/sprint-4`): decisión pura en
  `domain/AlertEngine.kt` (`evaluateTick`, `stopsToPoll`, `isFetchDue`,
  `nextAlarmAtMs`, `firingDestination`); `engine/AlertMonitorService` (FGS
  `dataSync`, sondeo cada `pollIntervalSec` agrupado por parada, se para sin
  avisos en vigilancia o tras 60 min sin servicio y lo notifica, `onTimeout` de
  Android 15); canales `dbus_monitoring` (LOW) y `dbus_arrivals_high` (HIGH);
  `AlertActionReceiver` (Posponer 5' / Descartar sin abrir la app); una alarma
  exacta en el próximo `windowFrom` (inexacta sin permiso) reprogramada en boot,
  hora, TZ, actualización y cambio de permiso; deep link
  `dbus://stop/{stopId}?highlightLine={lineId}`; A04 avisa si faltan
  notificaciones o alarmas exactas.
- `./gradlew check assembleDebug` verde: 106 tests JVM por variante (212), 0
  errores de lint. Probado en el AVD `dbus36` contra la API real con la app en
  segundo plano: ONCE → «Vigilando L35» → notificación → Posponer → segunda
  notificación → tap abre A05 con la línea; RECURRING 16:10–16:40 con el proceso
  matado → alarma exacta → notificación; reinicio → alarma reprogramada.
  15 capturas en `docs/07-android-mvp/capturas/sprint-4/`.
- Prompt del Sprint 5 en `docs/07-android-mvp/prompts/SPRINT-5.md` (con repo
  privado y licencia pendiente).

## Pendiente (por prioridad)

- [ ] Merge de `android/sprint-4` a `main` + push + borrar rama (falta la orden de Iker).
- [ ] **Sprint 5** (prompt `docs/07-android-mvp/prompts/SPRINT-5.md`).
- [ ] Licencia y repo público: los decide Iker más adelante (repo privado hasta entonces).
- [ ] Probar en móvil real el motor con Doze y fabricantes agresivos con la batería;
      Android 14+ niega `SCHEDULE_EXACT_ALARM` por defecto: sin él, el RECURRING
      no puede arrancar el servicio en segundo plano y avisa para abrir la app.
- [ ] Revisar en móvil real Cercanas con ubicación aproximada (en Android 12+
      se difumina ~2 km) y el tiempo hasta la primera posición.
- [ ] Fixture real de `DATOS` con `tiempos` vacío: no observado aún (las
      paradas sin buses dan `SIN_SERVICIO`); capturarlo si aparece.
- [ ] Propuesta a Iker: guardar `linea.color` en `build:catalog` (lane bot);
      hoy la app usa el último color visto en vivo.
- [ ] Sprint 5: auditar el historial en busca de secretos (aunque siga privado);
      `config.json` remoto sin hosting mientras el repo sea privado.
- [ ] Bot: validación conversacional con PID `80402`; rotar token de Telegram
      (R6, `.env` raíz, gitignored).
- [ ] Bot (opcional): residuales menores de la auditoría; 15 tests de
      integración dependen de `app.dbus.eus`.
- [ ] El `ci.yml` existente usa `npm ci` en un monorepo pnpm: revisar si pasa.
- [ ] Catálogo duplicado: `packages/contracts/data/` vs `apps/bot/data/`.

## Decisiones tomadas (no reabrir)

- 2026-11-22 · Iker: **licencia se decide más tarde; repo privado hasta que él
  decida.** Sin `LICENSE` ni `config.json` remoto publicado mientras tanto.
- 2026-11-22 · Android Sprint 4: una sola alarma (la del próximo inicio de franja
  de todos los RECURRING), no una por aviso. `Posponer 5'` en RECURRING borra
  `lastTriggeredAtMs` para volver a avisar en la misma franja. El motor arranca
  desde un observador de `alerts` en `AppContainer` (cubre guardar, switch,
  Posponer y Deshacer) y al abrir la app; tras un reinicio solo se reprograma la
  alarma (Android 15 no deja arrancar `dataSync` desde `BOOT_COMPLETED`): un ONCE
  activo se retoma al abrir la app. El servicio no repite una consulta que la UI
  hizo hace < 25 s.

- 2026-11-22 · Android Sprint 3: `MainActivity` pasa a `AppCompatActivity`
  (appcompat 1.7.1) para idioma por app y tema también en Android < 13; tema en
  `SharedPreferences`, idioma lo guarda AppCompat. Guardar o reactivar un ONCE
  lo vuelve a armar (`createdAtMs = now`). Dedup al editar: queda un único
  aviso (se escribe sobre el existente y se borra el editado). ONCE activo de
  más de 7 días se muestra «Caducada», no «Pausada». La cabecera de Inicio usa
  «Bus Donostia» (no «Dbus») por el aviso de no oficialidad.

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

Rama `android/sprint-4` con el Sprint 4, **sin integrar** en `main` (falta la
orden de Iker). Ver `git log --oneline main..android/sprint-4`.

## Siguiente sesión

- Integrar `android/sprint-4` si Iker lo autoriza; luego prompt `docs/07-android-mvp/prompts/SPRINT-5.md`.
