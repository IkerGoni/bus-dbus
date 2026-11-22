# Prompt de implementación — Android MVP v1 (bus-dbus)

> **Histórico:** ver [`SPRINTS.md`](../SPRINTS.md) y [`SPRINT-5.md`](SPRINT-5.md). Prompt previo a los sprints: sus avisos recurrentes, alarmas exactas y «Posponer»/«Descartar» ya no existen (avisos de un solo uso, [`02-domain-contracts.md`](../docs/02-domain-contracts.md)).

Sustituye al `AGENT-ARCHITECTURE-MIGRATION-PROMPT.md` del paquete externo
(2026-11-22). Pégalo a la sesión del agente. Una fase por sesión.

---

Eres el ingeniero principal del **MVP Android v1** de bus-dbus. Trabajas en el
repo `bus-dbus` y solo en el lane `android` (`apps/android/**`).

## Lee primero (en este orden)
1. `AGENTS.md` (manda sobre todo lo demás) y `ESTADO.md`.
2. `docs/03-architecture/adrs/009-android-nativo-mvp.md` (stack y por qué).
3. `docs/07-android-mvp/docs/00-agent-guardrails.md`.
4. `docs/07-android-mvp/docs/02-domain-contracts.md` (incluye la tabla §5 de
   correcciones al paquete original: aplica la versión corregida).
5. `docs/07-android-mvp/docs/03-android-screens.md` y `04-android-platform.md`.
6. `docs/07-android-mvp/design-system/` y `stitch-references/*/code.html`
   (referencia visual, no código a portar).
7. `docs/06-api-externa/01-endpoints-y-riesgo.md` (respuesta real de la API).

## Arquitectura (cerrada, no reabrir)
- Kotlin + Jetpack Compose + Material 3, Gradle (Kotlin DSL) en
  `apps/android/`, fuera del workspace pnpm. minSdk 26, targetSdk 36.
  Room, OkHttp + kotlinx.serialization, Navigation Compose. Sin DI framework,
  sin SDK de mapas, sin Firebase.
- **Datos:** catálogo offline empaquetado en `assets/` por una tarea Gradle que
  lee `apps/bot/data/dbus-catalog.json` + `apps/bot/data/dbus-stop-coordinates.json`
  (548 paradas, 514 con coordenadas, 39 líneas, 127 itinerarios). Tiempo real
  solo de `http://app.dbus.eus/SSIIMovilWSv2/ws/cons/tiemposParada`, con
  cleartext permitido solo para ese dominio. Prohibido `.pb`, mocks y fallbacks.
- **Sin backend:** nada de servidores propios. Única excepción: un
  `config.json` estático opcional (ADR 009 § Configuración remota) con copia
  empaquetada; si no se descarga, todo funciona igual.
- **Avisos:** motor 100 % local (Foreground Service `dataSync` + alarmas
  exactas para recurrentes). Sin FCM.
- **Dominio:** `remainingMinutes`, `computeAlertStatus`, `computeFreshness`,
  `haversineMeters`, matching (prefiere GPS) e identidad de línea (`32`→`28`)
  en Kotlin puro, sin dependencias de Android, con tests JUnit.

## Reglas duras
- Nunca mostrar segundos ni datos que la API no da (saldo, ocupación, caché,
  versiones, «hace X» inventados). Si falta un campo, se oculta el bloque.
- Las 5 vistas de estado (`loading`, `empty`, `live`, `degraded`, `error`) en
  cada pantalla con datos.
- Todo texto en `strings.xml` `es` + `eu`; lint `MissingTranslation` y
  `HardcodedText` como error.
- Targets ≥ 48 dp; `●` verde en `LIVE`, reloj gris en `SCHEDULED`.
- No tocar `apps/bot/**` ni `apps/mobile/**`. `packages/**` solo aditivo.
- Rama propia por fase; merge a `main` y push solo con orden de Iker.

## Fases (una por sesión; no empezar la siguiente sin la anterior verificada)

**Fase 0 · Esqueleto verde**
- Comprobar toolchain (`java -version` 17+, `$ANDROID_HOME`). Si falta, parar
  y pedírselo a Iker; no instalar nada sin permiso.
- Proyecto Compose vacío con la `NavigationBar` de 4 destinos (pantallas
  vacías), tema claro/oscuro desde `design-tokens.json`, fuentes empaquetadas
  (Plus Jakarta Sans, JetBrains Mono; OFL), `strings.xml` es/eu.
- `./gradlew check` en verde + workflow de CI. Documentar el comando en
  `AGENTS.md` § Verificación.

**Fase 1 · Datos y dominio**
- Tarea Gradle de catálogo + parser + test de conteos (548/514/39/127).
- `ApiConfig`: `assets/config.json` + descarga remota opcional (máx. cada 12 h,
  timeout 5 s, validación de esquema, fallback a última válida/empaquetada).
  Tests: config rota, timeout, `apiBaseUrl` HTTP rechazado. La URL remota la
  da Iker; hasta entonces, solo la empaquetada.
- Cliente `tiemposParada` (lee endpoints de `ApiConfig`) → `StopFeed` (`OK`, `NO_SERVICE` con `avisos`,
  `EMPTY`, `API_ERROR`). Tests contra los fixtures de
  `packages/contracts/fixtures/` (añadir los que falten capturando respuestas
  reales: `DATOS`, `SIN_SERVICIO`, `NO DESC`; no inventarlos).
- Room: favoritos (3 tipos, dedup por id determinista), avisos (dedup
  `(stopId, lineId)`), recientes (10).
- Dominio puro + tests de la tabla §5 de `02-domain-contracts.md`
  (DST, cruce de medianoche, una vez por franja, ONCE a 7 días).
- Reloj reactivo de 1 s (`StateFlow<Instant>`).
- Color de línea: el catálogo no lo trae. Guardar en Room el último
  `linea.color` visto en vivo; si nunca se ha visto, color neutro. Añadirlo a
  `build:catalog` toca el lane bot: proponerlo a Iker, no hacerlo.

**Fase 2 · Pantallas A01–A08** según `03-android-screens.md`.

**Fase 3 · Motor de avisos** según `04-android-platform.md`: servicio,
alarmas, canal `dbus_arrivals_high`, acciones `Posponer 5'` / `Descartar`
por `BroadcastReceiver`, `POST_NOTIFICATIONS` con fallback a ajustes, deep
link `dbus://stop/{stopId}`.

## Definition of Done (de todo el MVP)
- `./gradlew check` verde y tests de dominio pasando.
- La app hace el flujo completo en modo avión salvo las llegadas en vivo, y
  sin acceso a la config remota.
- Cero datos inventados visibles (revisión con `grep` de literales).
- Flujo probado en emulador o dispositivo real, con capturas: parada cercana
  → A05 → 🔔 → guardar aviso ONCE → notificación real → `Posponer 5'` →
  segunda notificación.
- `ESTADO.md` actualizado; rama integrada o reportada como pendiente con
  `git log --oneline main..<rama>`.
