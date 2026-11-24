# Estado de publicación y contexto de traspaso (2026-11-24)

Documento de contexto autocontenido para retomar el cierre de publicación de
`bus-dbus` (GitHub + Pages) y `Badator!` v1.1.0 (`versionCode 2`, Google Play)
desde cualquier sesión o agente, sin depender del historial de chat previo.

> **Procedimiento maestro:** [`docs/07-android-mvp/prompts/EJECUCION-PUBLICACION.md`](../07-android-mvp/prompts/EJECUCION-PUBLICACION.md)  
> **Reauditoría de referencia:** [`docs/auditoria/2026-11-reauditoria-publicacion.md`](2026-11-reauditoria-publicacion.md)

---

## 1. Situación actual (foto exacta)

- **Repositorio GitHub:** `IkerGoni/bus-dbus` sigue **`private`** (`created_at: 2026-11-24T00:03:15Z`, 0 PRs, 0 issues, 0 forks, 0 releases). **GitHub Pages NO está activado todavía.**
- **Historial Git purgado y verificado:** el correo ajeno de lockfiles históricos (`ID-01`, huella `sha256:b0373516d594`) fue eliminado con `git filter-repo` en clon aislado y publicado en `origin/main` en `508536b90929d50fac4f0504184a6c6141add4e7` (antes de este commit documental).
- **CI en verde sobre el historial purgado (`508536b90929`):**
  - `CI` (run `45311305388`): `success`
  - `Android` (run `45311305365`): `success`
- **Paquete local de Google Play (`$PLAY_PACK_DIR`):** saneado y verificado (Gate 1 OK). Ya puede usarse para crear la app y preparar borradores en Play Console.
- **Punto de parada actual:** **Gate 3B (parada dura)**. GitHub aún responde `HTTP/2.0 200 OK` para los dos SHAs pre-purga.
- **Decisión del propietario:** esperar vía **Opción 1 (ticket a GitHub Support)** solicitando `git gc` en servidor y purga de vistas cacheadas. **No recrear el repositorio** salvo nueva instrucción explícita del propietario.

---

## 2. Estado por fases y gates

| Fase / Gate | Estado | Evidencia verificada |
|---|---|---|
| **Fase 1** · Saneamiento de `$PLAY_PACK_DIR` | **Hecha** | `07-store-fields.csv` regenerado, `07-store-fields.json` en `PENDING_RELEASE_GATES`, `Dev Igoni` unificado, placeholders sustituidos, §0 / §0.2 / §3.7 corregidos en `GUIA-SUBIDA-PLAY-CONSOLE.md`. |
| **Gate 1** · Cero fugas y binarios intactos | **OK** | 0 rutas locales, 0 correos no aprobados, 0 IDs GCP; binarios de `05-release/` sin cambios. |
| **Fase 2.1 + Gate 2A** · Respaldo bundle | **OK** | `../bus-dbus-pre-purge-2026-11-24.bundle` (fuera del repo) verificado con `git bundle verify`. |
| **Fase 2.2 + Gate 2B** · Purga en clon aislado | **OK** | `1.216` blobs escaneados: 0 apariciones de `sha256:b0373516d594` y 0 correos no aprobados en blobs ni commits; `HEAD^{tree}` y `apps/android` idénticos. |
| **Fase 2.3** · Actualización de SHAs y docs | **Hecha** | `README.md`, `2026-11-reauditoria-publicacion.md`, `privacy-policy-{es,en}.html` (`2026-11-24`), `data-safety-decision.md` actualizados y commiteados en `508536b90929`. |
| **Fase 2.4** · Force-push y limpieza local | **Hecha** | `push --force-with-lease` sobre `95e07a4e62ff` OK; repo local limpiado (`0` objetos inalcanzables en `git fsck --unreachable --no-reflogs`, `check-identity.mjs` = `0`). |
| **Fase 3 + Gate 3A** · Limpieza de Actions | **OK** | Borrados 5 runs pre-purga, 4 artefactos pre-purga y 10 cachés pre-purga (incluida `11003053669`). Quedan 0 runs/artefactos/cachés pre-purga. |
| **Gate 3B** · Commits colgantes en GitHub | **BLOQUEADO** | `756eb9cc201d1119521e6693a38fd19aa99b9157` y `95e07a4e62ff3d76171bbc83149044a3e372182b` siguen devolviendo `HTTP/2.0 200 OK`. |
| **Fase 4** · Público + Pages + Secret Scanning | **Pendiente** | Bloqueada por Gate 3B. No abrir el repo hasta que ambos SHAs respondan `HTTP 422`. |

---

## 3. Invariantes y reglas no negociables

1. **No tocar `apps/android/**`:** el árbol Git debe mantenerse exactamente en
   `e0db74e7268f6878f43e6a86718d83a7a0d630f7` para preservar la correspondencia
   byte a byte con los binarios firmados en `$PLAY_PACK_DIR/05-release/`:
   - `app-release-signed.aab`: `sha256:8f339e261fa3c9c4bb991d0da140b503f8eb53a50236cfe8e45c72e2ea6acac3`
   - `app-release-signed.apk`: `sha256:cc568f6c4fc5cd4d6b1a5fcb46474f9c04a96161c6da1bdeb7614c8b209e68a5`
   - `classes.dex` determinista: `9648d959…`
2. **Cero fugas:** nunca imprimir en stdout, logs, commits ni informes correos fuera
   de la lista aprobada (`8208093+ikergoni@users.noreply.github.com`,
   `dev.mail.igoni@gmail.com`), secretos ni rutas de usuario locales. Ejecutar siempre
   `node tools/repo/check-identity.mjs` antes de commitear.
3. **No tocar el bot de producción** ni subir nada a Play Console (los pasos en consola
   son manuales del propietario).
4. **No reescribir prompts históricos** (`docs/07-android-mvp/prompts/*`).
5. **Parada dura de Gate 3B:** prohibido cambiar la visibilidad de `IkerGoni/bus-dbus`
   a `public` mientras alguno de los dos SHAs pre-purga devuelva `HTTP 200`.

### Mapa de SHAs clave (pre-purga → post-purga)

| Referencia | Pre-purga | Post-purga |
|---|---|---|
| Commit raíz (`README.md`) | `2eabe0f` | `2eabe0f` (sin cambios) |
| Base auditada (`reauditoria`) | `756eb9c` | `5ea5ef2` |
| `origin/main` previo a purga | `95e07a4` | `f36cf3e` |
| Commit tras actualización de docs/SHAs | — | `508536b90929d50fac4f0504184a6c6141add4e7` |

---

## 4. Cómo retomar en la siguiente sesión

### Paso A · Recomprobar Gate 3B

```bash
for sha in \
  756eb9cc201d1119521e6693a38fd19aa99b9157 \
  95e07a4e62ff3d76171bbc83149044a3e372182b; do
  line=$(gh api -i "repos/IkerGoni/bus-dbus/commits/$sha" 2>&1 | head -n 1 || true)
  echo "$sha -> $line"
done
```

- Si **cualquiera** devuelve `HTTP/2.0 200 OK`: **detenerse** e informar (seguir esperando
  a GitHub Support o pedir confirmación explícita si el propietario decide cambiar a la
  Opción 2 de recrear el repo privado limpio).
- Si **ambos** devuelven **`HTTP/2.0 422`** (Unprocessable Entity): Gate 3B superado →
  continuar con el Paso B (Fase 4).

### Paso B · Ejecutar Fase 4 (solo con Gate 3B = HTTP 422)

Seguir exactamente la sección «Fase 4» de
`docs/07-android-mvp/prompts/EJECUCION-PUBLICACION.md`:

1. Verificar que `git rev-parse HEAD:apps/android` sigue siendo
   `e0db74e7268f6878f43e6a86718d83a7a0d630f7` y que no han aparecido runs/cachés
   pre-purga.
2. Cambiar la visibilidad de `IkerGoni/bus-dbus` a `public`.
3. Activar GitHub Pages en rama `main`, carpeta `/docs`, con HTTPS forzado
   (`docs/.nojekyll` ya existe).
4. Habilitar **Secret scanning** y **Push protection** (por API si está disponible; si
   requiere acción manual en Settings, indicarlo al propietario).
5. Esperar el despliegue de Pages y verificar **sin sesión ni token** (`curl -sSI` +
   comprobación de contenido HTML):
   - `https://ikergoni.github.io/bus-dbus/privacy/privacy-policy-es.html` → HTTP `200`, `text/html`, contiene `Dev Igoni` y `dev.mail.igoni@gmail.com`.
   - `https://ikergoni.github.io/bus-dbus/privacy/privacy-policy-eu.html` → HTTP `200`, `text/html`, contiene `Dev Igoni` y `dev.mail.igoni@gmail.com`.
   - `https://ikergoni.github.io/bus-dbus/privacy/privacy-policy-en.html` → HTTP `200`, `text/html`, contiene `Dev Igoni` y `dev.mail.igoni@gmail.com`.
6. Confirmar CI en verde sobre `HEAD` y `0` alertas en
   `gh api repos/IkerGoni/bus-dbus/secret-scanning/alerts`.
7. Actualizar `ESTADO.md` y este documento (y, si el propietario lo pide, actualizar
   `05-release-gates.md` en `$PLAY_PACK_DIR` marcando Pages como resuelto).

---

## 5. Pendientes manuales del propietario (Google Play)

Mientras se resuelve Gate 3B, el propietario ya puede preparar borradores en Play
Console usando `$PLAY_PACK_DIR/GUIA-SUBIDA-PLAY-CONSOLE.md`. Antes de pulsar **Enviar
para revisión** quedan estos pasos manuales:

1. **GitHub Pages en HTTP 200** y prueba real abriendo las 3 políticas desde Ajustes
   de la app instalada (ES/EU/EN).
2. **Revisión del euskera** por hablante competente:
   `$PLAY_PACK_DIR/01-listing/REVISION-EUSKERA.md`.
3. **Cotejo de huella de la clave de subida** tras la primera subida en Play Console:
   `46:E6:55:AC:CE:C3:D5:34:BC:24:01:1F:60:09:6F:55:B6:60:EE:14:56:CA:9D:53:CC:B6:15:9D:5D:81:AA:9B`.
4. **Confirmación de pista cerrada** (12 testers inscritos durante 14 días si aplica a
   la cuenta personal).
5. **Verificación de identidad y teléfono** en Play Console y subida del vídeo
   `$PLAY_PACK_DIR/04-console/fgs-demo.mp4` como oculto en YouTube para la declaración
   FGS `dataSync`.
