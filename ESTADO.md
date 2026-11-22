# Estado — bus-dbus

Actualizado: 2026-11-22 18:50 · Última sesión: puesta al día de la documentación

> **`AGENTS.md` es la fuente de verdad.** Este archivo es el estado de sesión;
> si algo aquí contradice a `AGENTS.md`, manda `AGENTS.md`.

## Objetivo actual

MVP Android nativo sin backend (ADR 009). Plan en
`docs/07-android-mvp/SPRINTS.md`. **Sprints 0–4 hechos e integrados en `main`
(último merge `b8e08f8`, `android/favoritas-sondeo`); siguiente: Sprint 5
(publicación).** El bot sigue en producción (PID `4713`, ver Pendiente).

## Hecho en la última sesión

- **Docs al día con `main` `b8e08f8`** (rama `docs/puesta-al-dia`): 44 `.md`
  revisados contra el código, 37 editados y 1 nuevo; `README.md` reescrito (bot + Android);
  `docs/README.md` nuevo como índice; 18 docs marcados «Histórico: ver …»;
  specs Android con avisos de un solo uso, ★ en A05 y sondeo adaptativo;
  `/ubicacion` y callbacks del bot en `docs/05-ui/02-bot-telegram.md`;
  B1–B16 marcadas por sprint. 98 enlaces internos, 0 rotos.
- Verificación: bot 85/86 (falla el test de integración: la API devolvió
  `tipo=0`, que el test no acepta); `pnpm verify` igual (core 14 y contracts 7
  verdes). Android: 94 tests JVM por variante y 0 errores de lint en el build
  de `main` `b8e08f8` (18:28, worktree principal); el `gradlew` de esta sesión
  se canceló tras 20 min en `compileKotlin`. La rama solo toca `.md`.

### Sesiones anteriores (Android, 2026-11-22)

- **Sprint 4:** motor de avisos con FGS `dataSync` (`engine/AlertMonitorService`),
  decisión pura en `domain/AlertEngine.kt`, canales `dbus_monitoring` y
  `dbus_arrivals_high`, deep link `dbus://stop/{stopId}?highlightLine={lineId}`.
  Avisos de un solo uso: Room v2 con `MIGRATION_1_2` (borra recurrentes y ya
  disparados). 11 capturas en `docs/07-android-mvp/capturas/sprint-4/`.
- **Tras el Sprint 4** (`android/favoritas-sondeo`, integrada): ★ por llegada
  en A05 y sondeo adaptativo del motor (30 s si faltan ≤ 7 min para avisar,
  1 min hasta 14, 2 min más allá) con aviso 30 s antes (`NOTIFY_EARLY_MS`).

## Pendiente (por prioridad)

- [ ] Merge de `docs/puesta-al-dia` a `main` + push + borrar rama y worktree
      `../bus-dbus-docs` (falta la orden de Iker).
- [ ] Test `apps/bot/tests/integration.test.ts:51` falla: Dbus devuelve
      `tipo=0` (parada 2, L36 y L46, 18:40). Arreglo ya hecho en la rama
      `audit/arquitectura` (`014471b`), sin integrar.
- [ ] Bot: `ESTADO` decía PID `80402`, que ya no existe; corre el PID `4713`
      (`node dist/index.js` desde `apps/bot`, arrancado hoy a las 13:35).
      Confirmar qué `dist` ejecuta y si hubo reinicio del Mac.
- [ ] Iker: probar en su móvil el APK debug (`~/Downloads/bus-donostia-debug.apk`).
- [ ] Idea de Iker (después del Sprint 5): opción «Sonido de alarma» para los
      avisos (canal aparte con audio de alarma, vibración fuerte y repetición
      hasta que se toque).
- [ ] **Sprint 5** (prompt `docs/07-android-mvp/prompts/SPRINT-5.md`).
- [ ] Licencia y repo público: los decide Iker más adelante (repo privado hasta entonces).
- [ ] Probar en móvil real el motor con Doze y fabricantes agresivos con la batería.
- [ ] Revisar en móvil real Cercanas con ubicación aproximada (en Android 12+
      se difumina ~2 km) y el tiempo hasta la primera posición.
- [ ] Fixture real de `DATOS` con `tiempos` vacío: no observado aún.
- [ ] Propuesta a Iker: guardar `linea.color` en `build:catalog` (lane bot);
      hoy la app usa el último color visto en vivo.
- [ ] Sprint 5: auditar el historial en busca de secretos (aunque siga privado);
      `config.json` remoto sin hosting mientras el repo sea privado.
- [ ] Bot: validación conversacional; rotar token de Telegram (R6, `.env` raíz,
      gitignored).
- [ ] El `ci.yml` usa `npm ci` en un monorepo pnpm: revisar si pasa.
- [ ] Catálogo duplicado: `packages/contracts/data/` vs `apps/bot/data/`.
- [ ] Wireframes `.excalidraw` y `stitch-references/` de `docs/07-android-mvp/`
      aún muestran recurrentes y «Posponer»: rehacerlos o dejarlos como
      histórico (hoy hay aviso en el README del paquete).

## Decisiones tomadas (no reabrir)

- 2026-11-22 · Iker: **licencia se decide más tarde; repo privado hasta que él
  decida.** Sin `LICENSE` ni `config.json` remoto publicado mientras tanto.
- 2026-11-22 · Iker: **avisos de un solo uso**: saltan una vez a los X min y
  desaparecen. Sin recurrentes, sin Posponer/Descartar en la notificación y
  sin Posponer en Inicio (solo Pausar).
- 2026-11-22 · Iker: sondeo adaptativo solo en el motor en segundo plano con
  mucho margen (≤ 7 min para avisar → 30 s, 8–14 → 1 min, ≥ 15 → 2 min, sin
  datos → 1 min) y avisar 30 s antes: mejor pronto que tarde. La pantalla
  visible sigue a 30 s (guardrails §3). ★ por llegada en A05 = `STOP_LINE`.
- 2026-11-22 · Android Sprint 4: el motor arranca desde un observador de
  `alerts` en `AppContainer` (guardar, switch, Deshacer) y
  al abrir la app; tras reiniciar el móvil, un aviso activo se retoma al abrir
  la app (Android 15 no deja arrancar `dataSync` desde `BOOT_COMPLETED`). El
  servicio no repite una consulta que la UI hizo hace < 25 s.

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
- 2026-11-22 · ~~Avisos recurrentes con franja~~: sustituida el mismo día por
  los avisos de un solo uso (arriba). Queda: un aviso caduca a 7 días.
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
- Docs: `AGENTS.md` manda; `README.md` es uso; `docs/README.md` es el índice;
  los docs históricos llevan arriba «Histórico: ver …» y no se reescriben.

## Esperando a Iker

- Orden de merge de `docs/puesta-al-dia`.
- Revisar las reglas de `AGENTS.md` propuestas en el informe de la sesión de
  docs (no se cambiaron).
- Rotación del token de Telegram (R6).
- Validación conversacional del bot.
- Visto bueno al nombre «Bus Donostia» y al applicationId antes de Play.

## Git

`main` = `b8e08f8` (Sprints 0–4 integrados). Ramas abiertas:
`docs/puesta-al-dia` (worktree `../bus-dbus-docs`, esta sesión) y
`audit/arquitectura` (worktree `../bus-dbus-audit`, sesión paralela).

## Siguiente sesión

- Prompt: `docs/07-android-mvp/prompts/SPRINT-5.md`.
