# Estado — bus-dbus

Actualizado: 2026-11-23 00:30 · Última sesión: auditoría y commit Sprint 5.0

> **`AGENTS.md` es la fuente de verdad.** Este archivo es el estado de sesión;
> si algo aquí contradice a `AGENTS.md`, manda `AGENTS.md`.

## Objetivo actual

MVP Android nativo sin backend (ADR 009), ahora `v1.0.0`. Plan vigente en
`bus-donostia-engineering-plan/` (Sprints 5.0–8) y anexo de reglas en
`AGENTS.md` / `apps/android/AGENTS.md`. Sprint 5.0 (config v2, rendimiento,
KMP) integrado en `main`; falta el cierre de release Play y el Sprint 5.1.

## Hecho en la última sesión

- Auditoría del Sprint 5.0 contra AGENTS.md, plan y plantilla v2: 6 desviaciones
  P1 corregidas (timeouts de la config ignorados, `System` en `domain/Ticker`,
  rango de antelación fijo `1..30`, `uiLimits` sin validar, StrictMode tarde,
  `key`/`contentType` en LazyColumn) y 2 P2 (KDoc borrados, baseline profile).
- Tests nuevos: escalones mal ordenados/vacíos/rápidos, límites de UI, config
  remota con rangos inválidos, `pollDelayMs` con `PollingConfig` propia.
- Verificación: `./gradlew check assembleDebug assembleRelease
  -Pdbus.release.unsigned=true` verde; 122 tests por variante, 0 fallos;
  lint 0 errores (29 avisos); `lintVitalRelease` OK; APK release 2,3 MB con
  `baseline.prof`. JDK: `JAVA_HOME=/opt/homebrew/opt/openjdk@17` (sin él,
  `gradlew` falla con «Unable to locate a Java Runtime»).
- Commits `036d500` (plan + anexo de reglas) y `3935d9e` (Sprint 5.0 + fixes).

## Pendiente (por prioridad)


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
- [ ] **Sprint 5.0 cierre de release** (fuera de esta auditoría): `REMOTE_CONFIG_URL` HTTPS, firma release, ficha Play (capturas ES/EU, privacidad) y prueba manual del APK release (R8) en móvil.
- [ ] Sprint 5.0 P2 de la auditoría: `config.json` aún plano (campos de API fuera de `api`) y con `pollIntervalSec` heredado; sobrecargas `baseMs` redundantes en `pollDelayMs`/`nextPollDelayMs`; baseline profile escrito a mano (no Macrobenchmark); `app.filesDir` y tema en SharedPreferences leídos en Main Thread; AGENTS.md dice «94 tests» (son 122 por variante).
- [ ] **Sprint 5.1** (`bus-donostia-engineering-plan/sprints/Sprint_5.1___…md`): quitar `StopCodePill` de AlertEdit, StopDetail, Lines y Stops.
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
- [x] Wireframes `.excalidraw` y `stitch-references/inicio/` al día con los
      avisos de un solo uso (2026-11-22, rama `docs/wireframes-android`).
      Comprobación: `python3 docs/07-android-mvp/wireframes/verificar.py`.

## Decisiones tomadas (no reabrir)

- 2026-11-23 · Sprint 5.0: sondeo del motor en 4 escalones desde `config.json` (≤ 7 min → 30 s, 8–14 → 1 min, 15–30 → 4 min, ≥ 31 → 10 min; sin datos → base × 2). Sustituye a los 3 escalones del 2026-11-22. Ticker de UI a 30 s (`uiTickerSec`).
- 2026-11-23 · Auditoría 5.0: se mantienen `freshness` 45/90 s y 4,8 km/h (comportamiento real) en vez de 30/300 y 5,0 de la plantilla v2.

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

- Rotación del token de Telegram (R6).
- Validación conversacional del bot.
- Visto bueno al nombre «Bus Donostia» y al applicationId antes de Play.
- Firma release y hosting HTTPS del `config.json` remoto (Sprint 5.0 §4).

## Git

`main` = ver `git log -1` (= `origin/main`). Ramas abiertas: ninguna de esta
sesión. Worktree ajeno: `~/.local/share/kilo/worktree-pool/bus-dbus-494227611d30/ahead-child`
(HEAD separado en `76aa4d5`, sin commits propios; no creado aquí).

## Siguiente sesión

- Leer primero: `AGENTS.md` (anexo), `apps/android/AGENTS.md`,
  `bus-donostia-engineering-plan/sprints/Sprint_5.1___Quick_Wins_UX__Limpieza_de_Códigos_y_Animaciones_Pre-Release.md`.
- Empezar por: Sprint 5.1 (retirar `StopCodePill`) o, si Iker lo prioriza, el
  cierre de release Play del 5.0.
- Reglas específicas: exportar `JAVA_HOME` y `ANDROID_HOME` como en
  `apps/android/README.md` antes de `gradlew`.
