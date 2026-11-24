# Estado — bus-dbus

Actualizado: 2026-11-24 · Última sesión: hito **MVP Base (`v1.1.0`)** en GitHub Releases, canal de feedback, roadmap y README bilingüe orientado a perfil técnico

## Objetivo actual

Hito **MVP Base (`v1.1.0`, `versionCode 2`)** etiquetado y publicado en
`IkerGoni/bus-dbus` con el APK firmado de `Badator!` en GitHub Releases,
plantillas de GitHub Issues para recoger feedback real, roadmap `Soon` (sin
avisos recurrentes) y portada bilingüe (`README.md` + `README.es.md`) enfocada
a decisores técnicos y recruiters. GitHub Pages sigue activo con las 3 políticas
de privacidad en `HTTP 200` sin sesión, Secret Scanning + Push protection en `0`
alertas y el paquete de Google Play en `$PLAY_PACK_DIR` listo para los pasos
manuales en Play Console.

Detalle y trazabilidad completa de la publicación:
[`docs/auditoria/2026-11-estado-publicacion.md`](docs/auditoria/2026-11-estado-publicacion.md).

## Hecho en esta sesión

- **Hito MVP Base (`v1.1.0`) y recogida de feedback:**
  - Añadidas plantillas de GitHub Issues (`.github/ISSUE_TEMPLATE/bug_report.yml`,
    `feedback.yml` y `config.yml`) abiertas a reportes en ES / EU / EN.
  - Tag anotado `v1.1.0` y GitHub Release oficial (`Latest`) `v1.1.0 — MVP Base`
    con `badator-v1.1.0.apk` (copia exacta del binario firmado auditado
    `sha256:cc568f6c4fc5`, `classes.dex` `9648d959…`) y `SHA256SUMS.txt`.
  - Rediseñado `README.md` orientado a perfiles técnicos/hiring (resumen
    ejecutivo verificable, métricas de ingeniería, decisiones y trade-offs,
    descarga MVP Base, feedback y roadmap `Soon` sin avisos recurrentes) y
    creado espejo completo en castellano `README.es.md` con selector tipo tabs
    en cabecera; índice `docs/README.md` actualizado.
  - **Árbol Android intacto:** `HEAD:apps/android` = `e0db74e7268f6878f43e6a86718d83a7a0d630f7`.

## Hecho en la sesión previa (publicación Fases 1–4)

- **Fase 1 (paquete Play en `$PLAY_PACK_DIR`, Gate 1 OK):** `07-store-fields.csv`
  regenerado desde `07-store-fields.json`; `"status"` cambiado a
  `"PENDING_RELEASE_GATES"`; `Dev Igoni` y datos aprobados unificados en
  `00-START-HERE.md`, `04-console/CONSOLE-FIELDS.md` y
  `04-console/DEVELOPER-AND-SUPPORT.md`; corregidas §0, §0.2 y reforzada §3.7
  (`WorkManager`) en `GUIA-SUBIDA-PLAY-CONSOLE.md`. Binarios de `05-release/` intactos
  (`app-release-signed.aab` `8f339e261fa3`, `app-release-signed.apk` `cc568f6c4fc5`).
- **Fase 2 (purga de historial, Gates 2A y 2B OK):** respaldo `../bus-dbus-pre-purge-2026-11-24.bundle`
  verificado; purga con `git filter-repo` del correo `sha256:b0373516d594` (0 apariciones
  en `1.216` blobs); SHAs y docs actualizados (`README.md`, reauditoría, políticas ES/EN a
  `2026-11-24`, matiz de `data-safety-decision.md`); repo local limpio (`0` objetos
  inalcanzables, `check-identity.mjs` = `0`).
- **Fase 3 + Gate 3B (HTTP 422 verificado):** runs/artefactos/cachés pre-purga borrados;
  el repo anterior quedó renombrado como archivo privado (`IkerGoni/bus-dbus-archive-privado`,
  `private: true`, no hacer público) y el nuevo `IkerGoni/bus-dbus` recibió únicamente el
  historial purgado. Ambos SHAs pre-purga (`756eb9c` y `95e07a4`) devolvieron
  `HTTP/2.0 422 Unprocessable Entity` antes de abrir el repo.
- **Fase 4 (público + Pages + seguridad):**
  - `IkerGoni/bus-dbus` → `visibility: public`.
  - GitHub Pages activo (`main`, `/docs`, `https_enforced: true`, `status: built`).
  - Las 3 políticas (`privacy-policy-{es,eu,en}.html`) devuelven `HTTP/2 200`,
    `content-type: text/html; charset=utf-8` sin sesión ni redirección, con `Dev Igoni`,
    `dev.mail.igoni@gmail.com` y fecha `2026-11-24`.
  - **Secret scanning** y **Push protection** activos (`0` alertas).
  - **Árbol Android intacto:** `HEAD:apps/android` = `e0db74e7268f6878f43e6a86718d83a7a0d630f7`.

## Pendiente (pasos manuales de Iker en Google Play)

1. Abrir las 3 políticas de privacidad desde **Ajustes** en la app instalada (ES/EU/EN)
   para confirmar la navegación real en el dispositivo.
2. Revisión del euskera por hablante competente (`$PLAY_PACK_DIR/01-listing/REVISION-EUSKERA.md`).
3. Tras crear la app y subir `05-release/app-release-signed.aab`, cotejar la huella
   SHA-256 de la clave de subida (`46:E6:55:…:AA:9B`) en Play Console.
4. Confirmar si aplica el requisito de prueba cerrada (**12 testers / 14 días**) en la
   cuenta personal y completar la verificación de identidad/teléfono y el vídeo FGS
   (`04-console/fgs-demo.mp4` como oculto en YouTube).

## Decisiones tomadas

- El repositorio anterior se conservó renombrado como `IkerGoni/bus-dbus-archive-privado`
  en privado (sin borrarlo y sin hacerlo público nunca); el repositorio público
  `IkerGoni/bus-dbus` contiene exclusivamente el historial purgado.
- No se toca `apps/android/**` para preservar la correspondencia exacta con los binarios
  firmados del árbol `e0db74e7268f`.
- Integración con Dbus fuera del CI de cada push (no alcanzable desde los runners);
  Data Safety en «sin recogida» con el riesgo residual documentado.
