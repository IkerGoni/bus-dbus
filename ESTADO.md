# Estado — bus-dbus

Actualizado: 2026-11-24 · Última sesión: reauditoría de publicación (GitHub y Google Play) y remediación

## Objetivo actual

Publicar el repositorio y subir `Badator!` v1.1.0 (versionCode 2) a Play Console con
evidencia por SHA y por binario. Veredicto: 🔴 NO-GO hasta cerrar los pendientes de
abajo. Informe completo en `docs/auditoria/2026-11-reauditoria-publicacion.md`.

## Hecho en esta sesión

- **Correo ajeno en el lockfile (ID-01):** venía del aviso `deprecated` de `glob@11`
  (PWA congelada → `workbox-build`). Override a `glob@13` en `pnpm-workspace.yaml`;
  0 apariciones en `main`. Guard `tools/repo/check-identity.mjs` en CI (correos fuera
  de la lista aprobada, rutas locales e identidad y mensaje de HEAD).
- **CI (CI-01):** el timeout de integración no llegaba a vitest. Desde los runners de
  GitHub Dbus no es alcanzable (timeout de TCP); la integración pasa a
  `integration.yml` (manual, con sonda) y se verifica en local con `pnpm verify`.
- **Play:** texto FGS corregido en Data Safety y fichas (el tope de 3 h solo actúa si
  no hay bus de la línea); decisión de Data Safety en
  `docs/play-console/data-safety-decision.md`; política eu con identificadores de
  parada; AAB/APK regenerados y firmados con DEX `9648d959…` (determinista, árbol
  `apps/android` `e0db74e7`), los anteriores archivados como «no subir».
- **Docs y paquete:** cronología del README truncada al minuto, índice sin SHA
  inexistente, `*.apk`/`*.aab` ignorados, checklist que ya no se anuncia «listo para
  publicar», guía del paquete sin rutas locales ni identificadores de GCP.
- Verificación: 7 + 43 + 93 = 143 TS, `build:ci`, Android 156 debug / 150 release,
  lint sin issues.

## Pendiente

1. **Purga del historial** de los 4 blobs con el correo del lockfile
   (`filter-repo --replace-text` + `push --force`, con `git bundle` previo). Requiere
   confirmación explícita de Iker. Borrar los runs antiguos es opcional: sus logs no
   contienen el correo (0 en los 4 runs).
   Antes, auditoría externa del plan: `docs/07-android-mvp/prompts/AUDIT-PUBLICACION-PLAN.md`.
2. **Hacer público el repo y activar Pages** (`main`, `/docs`); comprobar HTTP 200 sin
   sesión en las tres políticas y desde Ajustes. Requiere confirmación.
3. Iker: revisión del euskera (`~/Desktop/badator-google-play-pack/01-listing/REVISION-EUSKERA.md`), huella de la clave de subida
   en Play Console (`46:E6:55:…:AA:9B`) y requisito de test cerrado (12/14).

## Decisiones tomadas

- Purga del historial al final: reescribe todos los SHA y no cambia el árbol de
  `apps/android`, así que los binarios regenerados siguen valiendo.
- Integración con Dbus fuera del CI de cada push (no alcanzable desde los runners).
- Data Safety se mantiene en «sin recogida», con el riesgo residual documentado.
- Override de `glob` en la raíz en vez de tocar `apps/mobile` (congelada).
