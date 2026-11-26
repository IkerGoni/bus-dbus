# Estado — bus-dbus

Actualizado: 2026-11-27 · Última sesión: verificación e integración del modo **Alarma (`v1.2.0`, `versionCode 3`)**, automatización CLI con `gplay` y subida a prueba cerrada (`alpha`) en Google Play

## Objetivo actual

Versión **`1.2.0` (`versionCode 3`)** de `Badator!` verificada, firmada y
publicada en la pista **`alpha` (prueba cerrada)** de Google Play mediante la
CLI `gplay` y [`tools/play-store/10-release-gplay.mjs`](tools/play-store/10-release-gplay.mjs),
con el modo de aviso **Alarma** integrado de extremo a extremo. El hito público
en GitHub Releases sigue en `v1.1.0` hasta decidir nueva publicación en GitHub,
GitHub Pages mantiene las 3 políticas de privacidad en `HTTP 200` y el guard de
identidad pública (`tools/repo/check-identity.mjs`) está en `0` hallazgos.

Detalle de automatización CLI con Google Play:
[`docs/play-console/gplay-release-automation.md`](docs/play-console/gplay-release-automation.md).
Trazabilidad de la publicación inicial (`v1.1.0`):
[`docs/auditoria/2026-11-estado-publicacion.md`](docs/auditoria/2026-11-estado-publicacion.md).

## Hecho en esta sesión

- **Modo alarma en avisos y endurecimiento (`v1.2.0`, `versionCode 3`):**
  - Auditada de extremo a extremo la integración de `AlertMode.ALARM` (Room v3 con `MIGRATION_2_3`, canal `dbus_arrivals_alarm` con `USAGE_ALARM`, `FLAG_INSISTENT` y `alarmTimeoutSec`, prevención de bucles en `AlertMonitorService` y `triggerBufferSec = 0`).
  - Extraída la resolución pura de canales bloqueados (`BlockedChannelNotice`) en `SystemStatus.kt`, añadido aviso con acceso directo en `AlertEditScreen.kt` (A06) cuando el canal del modo seleccionado está bloqueado y eliminado el truncado en la nota de vista previa de alarma.
  - Ampliada la cobertura con tests de `SystemStatusTest`, `AlertNotificationsTest` (`AudioAttributes.USAGE_ALARM`, `triggerBufferSec`, ausencia de `stopId`) y `AlertModeUiTest` (Compose/Robolectric).
  - Verificación completa en verde: `./gradlew check assembleDebug assembleRelease bundleRelease` (**187** tests debug / **179** tests release, `0` errores de lint) + `pnpm verify` (**143** tests TS/Node) + `check-identity.mjs` (`0` hallazgos).
- **Acceso CLI a Google Play (`gplay`) y subida a prueba cerrada (`alpha`):**
  - Movida la credencial de Service Account fuera del repo a `~/.config/gplay/devplayconsole.json` (`chmod 600`), añadido `devplayconsole*.json` a `.gitignore` y configurado el perfil `default` de `gplay`.
  - Verificado el acceso real sobre `eus.busdonostia.app` con `gplay apps list` y `gplay status`.
  - Creado [`tools/play-store/10-release-gplay.mjs`](tools/play-store/10-release-gplay.mjs) y la guía [`docs/play-console/gplay-release-automation.md`](docs/play-console/gplay-release-automation.md) (también documentado en `AGENTS.md`, `apps/android/README.md` y `docs/README.md`).
  - Compilado y validado con `gplay preflight` (`0` errores) el AAB firmado `1.2.0` (`versionCode 3`, SHA-256 `884b63574002`) y subido con éxito a la pista `alpha` (`editId 08952291310920922115`, `status: completed`).

## Hecho en sesiones previas (MVP Base `v1.1.0` y publicación Fases 1–4)

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

1. Revisión del euskera por hablante competente (`$PLAY_PACK_DIR/01-listing/REVISION-EUSKERA.md`).
2. Completar la ventana de prueba cerrada (**12 testers / 14 días**) en la pista `alpha` y verificar en dispositivo real las 3 políticas de privacidad desde **Ajustes** y los avisos en modo alarma.
3. Si se desea publicar `v1.2.0` también en GitHub Releases, crear el tag `v1.2.0` y adjuntar el APK firmado (`dc09add0d234`).

## Decisiones tomadas

- El repositorio anterior se conservó renombrado como `IkerGoni/bus-dbus-archive-privado`
  en privado (sin borrarlo y sin hacerlo público nunca); el repositorio público
  `IkerGoni/bus-dbus` contiene exclusivamente el historial purgado.
- Las futuras subidas a Google Play se automatizan mediante `tools/play-store/10-release-gplay.mjs` y `gplay`, manteniendo la credencial fuera del repositorio (`~/.config/gplay/devplayconsole.json`, `600`) y exigiendo autorización explícita por versión antes de publicar.
- Integración con Dbus fuera del CI de cada push (no alcanzable desde los runners);
  Data Safety en «sin recogida» con el riesgo residual documentado.
