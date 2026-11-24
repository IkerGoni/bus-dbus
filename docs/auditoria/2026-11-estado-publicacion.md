# Estado de publicación y contexto de traspaso (2026-11-24)

Documento de contexto autocontenido de cierre de publicación de `bus-dbus`
(GitHub + Pages) y preparación de `Badator!` v1.1.0 (`versionCode 2`, Google Play).

> **Procedimiento maestro:** [`docs/07-android-mvp/prompts/EJECUCION-PUBLICACION.md`](../07-android-mvp/prompts/EJECUCION-PUBLICACION.md)  
> **Reauditoría de referencia:** [`docs/auditoria/2026-11-reauditoria-publicacion.md`](2026-11-reauditoria-publicacion.md)

---

## 1. Situación actual (foto exacta)

- **Repositorio público:** `IkerGoni/bus-dbus` (`id: 1469593675`, `visibility: public`) con el historial purgado y verificado.
- **Archivo privado (recuerdo):** `IkerGoni/bus-dbus-archive-privado` (`id: 1469447670`, `visibility: private`) — **no hacer público nunca**.
- **GitHub Pages:** activo desde `main` y `/docs` (`https_enforced: true`, `status: built`), con las 3 políticas de privacidad respondiendo `HTTP/2 200` sin sesión.
- **Seguridad GitHub:** `secret_scanning: enabled`, `secret_scanning_push_protection: enabled`, **`0` alertas**.
- **Paquete local de Google Play (`$PLAY_PACK_DIR`):** saneado y verificado (Gate 1 OK); listo para completar los pasos manuales en Play Console.

---

## 2. Estado por fases y gates

| Fase / Gate | Estado | Evidencia verificada |
|---|---|---|
| **Fase 1** · Saneamiento de `$PLAY_PACK_DIR` | **Hecha** | `07-store-fields.csv` regenerado, `07-store-fields.json` en `PENDING_RELEASE_GATES`, `Dev Igoni` unificado, placeholders sustituidos, §0 / §0.2 / §3.7 corregidos en `GUIA-SUBIDA-PLAY-CONSOLE.md`. |
| **Gate 1** · Cero fugas y binarios intactos | **OK** | 0 rutas locales, 0 correos no aprobados, 0 IDs GCP; binarios de `05-release/` sin cambios. |
| **Fase 2.1 + Gate 2A** · Respaldo bundle | **OK** | `../bus-dbus-pre-purge-2026-11-24.bundle` (fuera del repo) verificado con `git bundle verify`. |
| **Fase 2.2 + Gate 2B** · Purga en clon aislado | **OK** | `1.216` blobs escaneados: 0 apariciones de `sha256:b0373516d594` y 0 correos no aprobados en blobs ni commits; `HEAD^{tree}` y `apps/android` idénticos. |
| **Fase 2.3** · Actualización de SHAs y docs | **Hecha** | `README.md`, `2026-11-reauditoria-publicacion.md`, `privacy-policy-{es,en}.html` (`2026-11-24`), `data-safety-decision.md` actualizados en `508536b90929`. |
| **Fase 2.4** · Publicación purgada y limpieza local | **Hecha** | Repo local con `0` objetos inalcanzables (`git fsck --unreachable --no-reflogs`), `check-identity.mjs` = `0`. |
| **Fase 3 + Gate 3A** · Limpieza de Actions | **OK** | Borrados 5 runs, 4 artefactos y 10 cachés pre-purga (incluida `11003053669`). |
| **Gate 3B** · Commits colgantes en GitHub | **OK (HTTP 422)** | Tras archivar el repo previo como privado y crear `IkerGoni/bus-dbus` limpio: `756eb9cc201d1119521e6693a38fd19aa99b9157` → `HTTP/2.0 422` y `95e07a4e62ff3d76171bbc83149044a3e372182b` → `HTTP/2.0 422`. |
| **Fase 4** · Público + Pages + Secret Scanning | **Hecha** | Repo `public`, Pages `built` con HTTPS forzado, `HTTP/2 200` sin sesión en es/eu/en (`text/html`, `Dev Igoni`, `dev.mail.igoni@gmail.com`, `2026-11-24`), Secret Scanning + Push protection activos (`0` alertas). |

---

## 3. Invariantes verificados

1. **Árbol Android intacto:** `HEAD:apps/android` = `e0db74e7268f6878f43e6a86718d83a7a0d630f7`, correspondiente byte a byte con los binarios firmados de `$PLAY_PACK_DIR/05-release/`:
   - `app-release-signed.aab`: `sha256:8f339e261fa3c9c4bb991d0da140b503f8eb53a50236cfe8e45c72e2ea6acac3`
   - `app-release-signed.apk`: `sha256:cc568f6c4fc5cd4d6b1a5fcb46474f9c04a96161c6da1bdeb7614c8b209e68a5`
   - `classes.dex` determinista: `9648d959…`
2. **Políticas públicas verificadas sin sesión (`HTTP/2 200`):**
   - `https://ikergoni.github.io/bus-dbus/privacy/privacy-policy-es.html`
   - `https://ikergoni.github.io/bus-dbus/privacy/privacy-policy-eu.html`
   - `https://ikergoni.github.io/bus-dbus/privacy/privacy-policy-en.html`
3. **Mapa de SHAs clave (pre-purga → post-purga):**
   - Raíz: `2eabe0f` → `2eabe0f` (sin cambios)
   - Base auditada: `756eb9c` → `5ea5ef2`
   - `origin/main` pre-purga: `95e07a4` → `f36cf3e`
   - Actualización docs/SHAs: `508536b90929d50fac4f0504184a6c6141add4e7`

---

## 4. Pendientes manuales del propietario (Google Play)

1. **Probar las 3 políticas desde la app instalada:** abrir Ajustes → Política de privacidad en ES, EU y EN para confirmar la carga real en el móvil.
2. **Revisión del euskera** por hablante competente: `$PLAY_PACK_DIR/01-listing/REVISION-EUSKERA.md`.
3. **Cotejo de huella de la clave de subida** tras subir `05-release/app-release-signed.aab` en Play Console:
   `46:E6:55:AC:CE:C3:D5:34:BC:24:01:1F:60:09:6F:55:B6:60:EE:14:56:CA:9D:53:CC:B6:15:9D:5D:81:AA:9B`.
4. **Confirmación de prueba cerrada** (12 testers inscritos durante 14 días si aplica a la cuenta personal).
5. **Verificación de identidad/teléfono** en Play Console y subida de `$PLAY_PACK_DIR/04-console/fgs-demo.mp4` como vídeo oculto en YouTube para la declaración FGS `dataSync`.
