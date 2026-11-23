# Estado — bus-dbus

Actualizado: 2026-11-23 01:52 · Última sesión: Sprint 5.1 verificado y consolidado

> **`AGENTS.md` es la fuente de verdad.** Este archivo es el estado de sesión;
> si algo aquí contradice a `AGENTS.md`, manda `AGENTS.md`.

## Objetivo actual

MVP Android nativo sin backend (ADR 009), `v1.0.0` con Sprint 5.1 integrado.
Plan vigente en `bus-donostia-engineering-plan/` (Sprints 5.0–8) y anexo de
reglas en `AGENTS.md` / `apps/android/AGENTS.md`. Sigue: Sprint 6 (v1.1.0,
tokens generados en build-time) y el cierre de release Play del 5.0.

## Hecho en la última sesión

- Sprint 5.1 implementado y auditado contra el anexo (tokens en 3 niveles,
  cero códigos opacos, rendimiento/KMP): MotionTokens/DesignTokens en
  `ui/theme/` con `ResolvedMotionTokens` FULL/ZERO y paridad 1:1 con
  `design-tokens.v1.template.json` (+`searchClearScaleFactor` 0.8,
  +`arrivalsStateSlideFraction` 0.0625).
- Auditoría de conformidad 5.1: los 11 hallazgos (2×P1, 5×P2, 4×P3) corregidos
  — `LocalReducedMotion` proveído en `BusDonostiaTheme`; timeline y fallbacks
  de cabecera/a11y usan `stop_unnamed`; háptica de `FavoriteStar` solo en
  interacción real (LongPress); `pressScale` con `springNoBounceDamping`;
  `MinutesLabel` con `LiveSignalDot` (pulso 8 dp); `key`+`contentType` en todos
  los `item` de `ui/` (los de `AnimatedContent` con key derivada de la vista
  para no colisionar); placeholder de paradas sin nº de poste; preview de A06
  replica la notificación real (`notif_body`); `stop_title_fallback` y
  `notif_body_stop` eliminados (es/eu); `MotionTokensTest` nuevo.
- Verificación: `./gradlew check assembleDebug assembleRelease
  -Pdbus.release.unsigned=true` verde (2 m 53 s, daemon + configuration cache);
  **127 tests por variante, 0 fallos**; lint 0 errores; APKs reconstruidos:
  `app-debug.apk` 20,3 MB, `app-release-unsigned.apk` 2,2 MB (R8 full mode).
- Re-greps de certificación a 0: `StopCodePill`/`stop_with_code`, inline
  `0.8f`/`it / 16` en `ui/`, `item {` sin key, imports `android.*`/`java.*` en
  `domain/`, strings muertos.
- Residual anotado (no corregido, menor): el label del intent `geo:` en
  `StopDetailScreen` aún cae a `stopId` crudo sin nombre (pin de app externa
  de mapas, no UI interna ni TalkBack). Candidato para Sprint 6.
- Commit `afcbd1b` (Sprint 5.1 completo).

## Pendiente (por prioridad)

- [ ] **Sprint 6 (v1.1.0)**: generar MotionTokens/DesignTokens en build-time
      desde `packages/contracts/design-tokens.json` (el template v1 ya incluye
      los dos tokens nuevos); resolver el residual del label `geo:`.
- [ ] **Sprint 5.0 cierre de release Play**: `REMOTE_CONFIG_URL` HTTPS, firma
      release, ficha Play (capturas ES/EU, privacidad) y prueba manual del APK
      release (R8) en móvil.
- [ ] Test `apps/bot/tests/integration.test.ts:51` falla: Dbus devuelve
      `tipo=0`. Arreglo en commit `014471b` (rama `audit/arquitectura`), aún
      **no integrado en `main`**.
- [ ] Bot: confirmar el PID vivo (`ESTADO` anterior decía 80402, muerto; luego
      4713). Verificar qué `dist` ejecuta y si hubo reinicio del Mac.
- [ ] Sprint 5.0 P2 de la auditoría: `config.json` aún plano (campos de API
      fuera de `api`) y con `pollIntervalSec` heredado; sobrecargas `baseMs`
      redundantes en `pollDelayMs`/`nextPollDelayMs`; baseline profile escrito
      a mano (no Macrobenchmark); `app.filesDir` y tema en SharedPreferences
      leídos en Main Thread; AGENTS.md dice «94 tests» (son 127 por variante).
- [ ] Iker: probar en su móvil el APK debug (`~/Downloads/bus-donostia-debug.apk`).
- [ ] Idea de Iker (después del Sprint 5): opción «Sonido de alarma» para los
      avisos (canal aparte con audio de alarma, vibración fuerte y repetición
      hasta que se toque).
- [ ] Licencia y repo público: los decide Iker más adelante (repo privado hasta entonces).
- [ ] Probar en móvil real el motor con Doze y fabricantes agresivos con la batería.
- [ ] Revisar en móvil real Cercanas con ubicación aproximada (Android 12+
      difumina ~2 km) y el tiempo hasta la primera posición.
- [ ] Fixture real de `DATOS` con `tiempos` vacío: no observado aún.
- [ ] Propuesta a Iker: guardar `linea.color` en `build:catalog` (lane bot);
      hoy la app usa el último color visto en vivo.
- [ ] Sprint 5: auditar el historial en busca de secretos (aunque siga privado);
      `config.json` remoto sin hosting mientras el repo sea privado.
- [ ] Bot: validación conversacional; rotar token de Telegram (R6, `.env` raíz,
      gitignored).
- [ ] El `ci.yml` usa `npm ci` en un monorepo pnpm: revisar si pasa.
- [ ] Catálogo duplicado: `packages/contracts/data/` vs `apps/bot/data/`.

## Decisiones tomadas (no reabrir)

- 2026-11-23 · Preview de A06 replica la notificación real (`notif_body` +
  `source_live` de muestra, sin código); `stop_title_fallback` y
  `notif_body_stop` eliminados. Un solo string de cuerpo para ambas vías.
- 2026-11-23 · Los `item` dentro de `AnimatedContent` (notice, estado vacío)
  llevan key derivada de la vista (`$tag`): durante una transición se componen
  a la vez saliente y entrante y una key estática colisionaría en la lista.
- 2026-11-23 · Los códigos públicos de línea sí se permiten en placeholders de
  búsqueda (`lines_search_hint` «ej. 28»); los códigos de poste, nunca.
- 2026-11-23 · Sprint 5.0: sondeo del motor en 4 escalones desde `config.json`
  (≤ 7 min → 30 s, 8–14 → 1 min, 15–30 → 4 min, ≥ 31 → 10 min; sin datos →
  base × 2). Sustituye a los 3 escalones del 2026-11-22. Ticker de UI a 30 s.
- 2026-11-23 · Auditoría 5.0: se mantienen `freshness` 45/90 s y 4,8 km/h
  (comportamiento real) en vez de 30/300 y 5,0 de la plantilla v2.
- 2026-11-22 · Iker: **licencia se decide más tarde; repo privado hasta que él
  decida.** Sin `LICENSE` ni `config.json` remoto publicado mientras tanto.
- 2026-11-22 · Iker: **avisos de un solo uso**: saltan una vez a los X min y
  desaparecen. Sin recurrentes, sin Posponer/Descartar en la notificación y
  sin Posponer en Inicio (solo Pausar).
- 2026-11-22 · Iker: sondeo adaptativo solo en el motor en segundo plano con
  mucho margen y avisar 30 s antes: mejor pronto que tarde. La pantalla
  visible sigue a 30 s (guardrails §3). ★ por llegada en A05 = `STOP_LINE`.
- 2026-11-22 · Android Sprint 4: el motor arranca desde un observador de
  `alerts` en `AppContainer` y al abrir la app; tras reiniciar el móvil, un
  aviso activo se retoma al abrir la app (Android 15 no deja arrancar
  `dataSync` desde `BOOT_COMPLETED`). El servicio no repite una consulta que
  la UI hizo hace < 25 s.
- 2026-11-22 · Android Sprint 3: `MainActivity` pasa a `AppCompatActivity`
  (appcompat 1.7.1) para idioma por app y tema también en Android < 13; tema en
  `SharedPreferences`, idioma lo guarda AppCompat. Guardar o reactivar un ONCE
  lo vuelve a armar (`createdAtMs = now`). Dedup al editar: queda un único
  aviso. ONCE activo de más de 7 días se muestra «Caducada», no «Pausada». La
  cabecera de Inicio usa «Bus Donostia» (no «Dbus») por el aviso de no oficialidad.
- 2026-11-22 · Android Sprint 2: se piden COARSE + FINE juntos. Ubicación sin
  Play Services (`LocationManagerCompat`), atajo con la última posición del
  sistema de < 2 min, nunca persistida.
- 2026-11-22 · A05: si falla un refresco, los datos válidos anteriores siguen
  `live` mientras sean FRESH, `degraded` si STALE y `error` si EXPIRED.
  ★ STOP_LINE = botón bajo los chips cuando hay línea seleccionada.
- 2026-11-22 · Android nativo (Kotlin + Compose), sin web app previa, sin
  backend ni FCM. Config remota = JSON estático opcional (ADR 009).
- 2026-11-22 · Android Sprint 1: Room 2.8.4, KSP 2.2.21-2.0.5, OkHttp 4.12,
  kotlinx.serialization 1.9, Robolectric 4.16.1 (SDK 35). `domain/` sin imports
  de Android; config remota cuenta intentos fallidos en las 12 h.
- 2026-11-22 · AGP 8.13.2 / Gradle 8.14.3 / Kotlin 2.2.21 por estabilidad;
  AGP 9 es migración aparte. applicationId `eus.busdonostia.app`.
- 2026-11-22 · ~~Avisos recurrentes con franja~~: sustituida por los avisos de
  un solo uso. Un aviso caduca a 7 días.
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
- Qué prioriza para la siguiente: Sprint 6 (v1.1.0) o cierre de release Play.

## Git

`main` `afcbd1b` = `origin/main` · ramas abiertas: ninguna. Worktrees: solo el
principal (el de Kilo `~/.local/share/kilo/worktree-pool/…/ahead-child` se
eliminó en este cierre: estaba limpio y en un commit ya integrado). Stash vacío.

## Siguiente sesión

- Leer primero: `AGENTS.md` (anexo), `apps/android/AGENTS.md`,
  `bus-donostia-engineering-plan/contracts-templates/design-tokens.v1.template.json`
  y el doc del Sprint 6 en `bus-donostia-engineering-plan/sprints/`.
- Empezar por: Sprint 6 (v1.1.0, tokens build-time) o, si Iker prioriza, el
  cierre de release Play del 5.0.
- Reglas específicas: `JAVA_HOME=/opt/homebrew/opt/openjdk@17/…` antes de
  `gradlew`; PROHIBIDO `--no-daemon` (daemon + configuration cache); Gradle
  una sola vez al final; checklist del anexo antes de entregar PR/commit.
