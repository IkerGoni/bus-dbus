# Estado — bus-dbus

Actualizado: 2026-11-22 13:15 · Última sesión: spec Android + Sprint 0

> **`AGENTS.md` es la fuente de verdad.** Este archivo es el estado de sesión;
> si algo aquí contradice a `AGENTS.md`, manda `AGENTS.md`.

## Objetivo actual

MVP Android nativo sin backend (ADR 009). Plan en
`docs/07-android-mvp/SPRINTS.md`. **Sprint 0 hecho; siguiente: Sprint 1
(datos y dominio).** El bot sigue en producción sin cambios (PID `80402`).

## Hecho en la última sesión

- Paquete externo `dbus-donostia-android-mvp/` importado en
  `docs/07-android-mvp/` y corregido contra la API real (tabla §5 de
  `docs/02-domain-contracts.md`): minutos enteros, 514/548 paradas con
  coordenadas, franja obligatoria en avisos recurrentes, sin FCM, colores de
  línea inventados eliminados (L27 real `#747719`).
- ADR 009: Kotlin + Compose, sin backend; única pieza remota opcional, un
  `config.json` estático con copia empaquetada. ADR 001/002/003/007 sustituidos
  para el cliente móvil. PWA congelada.
- Toolchain instalado con brew: `openjdk@17`, `android-commandlinetools`
  (platform 36, build-tools 36, emulador + imagen `android-36` arm64, AVD
  `dbus36`), `gradle`. Ver `apps/android/README.md`.
- **Sprint 0**: `apps/android/` con NavigationBar de 4 destinos, tema
  claro/oscuro, fuentes OFL, `es`/`eu`. `./gradlew check assembleDebug` verde
  (2 tests, 0 errores de lint, 13 warnings de versiones). Las barreras i18n
  (lint + test) se comprobaron fallando con una clave solo en `es`. Probado en
  emulador: navegación, CTA, oscuro y euskera; 0 crashes. Capturas en
  `docs/07-android-mvp/capturas/sprint-0/`. CI: `.github/workflows/android.yml`.

## Pendiente (por prioridad)

- [ ] **Sprint 1 · Datos y dominio** (ver `SPRINTS.md`).
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

Rama `android/sprint-0` integrada en `main` y borrada al cerrar esta sesión.

## Siguiente sesión

- Prompt: `docs/07-android-mvp/prompts/SPRINT-1.md`.
