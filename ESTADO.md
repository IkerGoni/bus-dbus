# Estado — bus-dbus

Actualizado: 2026-11-24 · Última sesión: ejecución controlada de publicación (Fases 1–3 hechas; parado en Gate 3B)

## Objetivo actual

Publicar el repositorio `IkerGoni/bus-dbus` + GitHub Pages y subir `Badator!` v1.1.0
(`versionCode 2`) a Play Console. Veredicto: 🟡 EN ESPERA de **Gate 3B** (GitHub Support
para purgar commits huérfanos pre-purga antes de abrir el repo).

Contexto completo para retomar desde cualquier sesión o agente:
[`docs/auditoria/2026-11-estado-publicacion.md`](docs/auditoria/2026-11-estado-publicacion.md).

## Hecho en esta sesión

- **Fase 1 (paquete Play en `$PLAY_PACK_DIR`, Gate 1 OK):** `07-store-fields.csv`
  regenerado desde `07-store-fields.json`; `"status"` cambiado a
  `"PENDING_RELEASE_GATES"`; `Dev Igoni` y datos reales unificados en `00-START-HERE.md`,
  `04-console/CONSOLE-FIELDS.md` y `04-console/DEVELOPER-AND-SUPPORT.md`; corregidas la
  contradicción de §0, las rutas del AAB en §0.2 y añadida la justificación de
  `WorkManager` en §3.7 de `GUIA-SUBIDA-PLAY-CONSOLE.md`. Binarios de `05-release/`
  intactos (`app-release-signed.aab` `8f339e261fa3`, `app-release-signed.apk` `cc568f6c4fc5`).
- **Fase 2 (purga de historial, Gates 2A y 2B OK):** respaldo completo fuera del repo
  (`../bus-dbus-pre-purge-2026-11-24.bundle` verificado), purga con `git filter-repo` en
  clon aislado del correo histórico `sha256:b0373516d594` (0 apariciones en `1.216` blobs),
  SHAs y docs actualizados (`README.md`, `docs/auditoria/2026-11-reauditoria-publicacion.md`,
  `docs/privacy/privacy-policy-{es,en}.html` a `2026-11-24`,
  `docs/play-console/data-safety-decision.md`), `push --force-with-lease` sobre `main`
  (`508536b90929`) y limpieza local (`0` objetos inalcanzables, `check-identity.mjs` = `0`).
- **Árbol Android intacto:** `HEAD:apps/android` = `e0db74e7268f6878f43e6a86718d83a7a0d630f7`.
- **Fase 3 (Actions, Gate 3A OK):** borrados 5 runs, 4 artefactos y 10 cachés pre-purga
  (incluida `11003053669`). Los nuevos runs sobre `508536b90929` terminaron en **verde**:
  `CI` (`45311305388`) y `Android` (`45311305365`).
- **Gate 3B (parada dura):** los SHAs pre-purga `756eb9cc201d1119521e6693a38fd19aa99b9157`
  y `95e07a4e62ff3d76171bbc83149044a3e372182b` siguen respondiendo `HTTP/2.0 200 OK` en
  GitHub. El repo se mantiene `private` y Pages desactivado.

## Pendiente

1. **Desbloquear Gate 3B (Opción 1 elegida por Iker):** enviar ticket a GitHub Support
   pidiendo `git gc` en servidor y purga de vistas cacheadas de `IkerGoni/bus-dbus` hasta
   que ambos SHAs pre-purga devuelvan `HTTP 422`.
2. **Fase 4 (solo con Gate 3B = HTTP 422):** hacer público `IkerGoni/bus-dbus`, activar
   GitHub Pages (`main`, `/docs`, HTTPS forzado), activar Secret Scanning + Push
   protection y verificar `HTTP 200` sin sesión en las 3 políticas de privacidad.
3. **Pasos manuales de Iker en Google Play:** borradores en consola (ya desbloqueados con
   `$PLAY_PACK_DIR`), revisión del euskera (`$PLAY_PACK_DIR/01-listing/REVISION-EUSKERA.md`),
   cotejo de la huella `46:E6:55:…:AA:9B`, confirmación de prueba cerrada (12 testers /
   14 días) y prueba de los 3 enlaces de privacidad desde Ajustes de la app.

## Decisiones tomadas

- Se evita recrear el repositorio (Opción 2) y se espera a **GitHub Support (Opción 1)**
  salvo nueva confirmación explícita de Iker.
- No se toca `apps/android/**` para mantener la equivalencia exacta de los binarios
  firmados con el árbol `e0db74e7268f`.
- Integración con Dbus fuera del CI de cada push (no alcanzable desde los runners);
  Data Safety en «sin recogida» con el riesgo residual matizado en
  `docs/play-console/data-safety-decision.md`.
