# Ejecución controlada de publicación (paquete Play + GitHub + Pages)

Procedimiento ejecutable derivado de la auditoría externa sobre `95e07a4` (plan de
publicación de `bus-dbus` y `Badator!` v1.1.0, `versionCode 2`).

Ejecuta las fases **en este orden exacto** respetando `AGENTS.md` y
`docs/contributing/` (`README.md`, `01-commits.md`, `02-ramas-integracion.md`,
`03-seguridad.md` — léelos antes de la primera operación Git).

## Reglas no negociables

1. **Autorización y gates:** el propietario autoriza la ejecución solo si se superan
   todos los gates intermedios. Si cualquier gate falla, **detente e informa sin avanzar**.
2. **Cero fugas:** nunca imprimas en stdout, logs, commits ni en el informe final ningún
   correo no aprobado, secreto ni ruta de usuario local. Cita siempre por tipo, fichero
   y huella (`sha256[:12]`).
3. **No reescribir prompts históricos** (`docs/07-android-mvp/prompts/*`).
4. **No tocar el bot de producción ni subir nada a Play Console** (la primera subida y
   las declaraciones en consola son manuales del propietario).
5. **No tocar `apps/android/**`:** el árbol `e0db74e7268f6878f43e6a86718d83a7a0d630f7`
   debe permanecer intacto para que los binarios firmados de `$PLAY_PACK_DIR/05-release/`
   sigan correspondiendo byte a byte (`classes.dex` `9648d959…`).

---

## Fase 1 · Saneamiento del paquete local de Google Play (primero, reversible y fuera del repo)

Hacer esta fase primero deja el paquete limpio y utilizable para crear borradores en Play
Console incluso si el Gate 3 de GitHub requiere esperar a GitHub Support.

Directorio del paquete: `$PLAY_PACK_DIR` (por defecto `~/Desktop/badator-google-play-pack`,
sin escribir rutas literales de usuario en los ficheros).

1. **`07-store-fields.csv`:** regenerarlo desde `07-store-fields.json` / `01-listing/*.md`
   (o retirarlo si ya no se usa) para eliminar los textos obsoletos («TODO LO QUE
   NECESITAS…», «Dev iGoni», mención solo a es/eu sin inglés y verbos antiguos de euskera).
2. **`07-store-fields.json`:** sustituir `"status": "VERIFIED_READY_FOR_PUBLICATION"` por
   `"status": "PENDING_RELEASE_GATES"` (fiel a `05-release-gates.md` hasta cerrar Pages y
   los 3 pendientes del propietario).
3. **`00-START-HERE.md`, `04-console/CONSOLE-FIELDS.md` y
   `04-console/DEVELOPER-AND-SUPPORT.md`:** unificar la marca a `Dev Igoni` (en lugar de
   `Dev iGoni`) y sustituir los placeholders obsoletos (`[EMAIL_SOPORTE_REAL]`,
   `[URL_PUBLICA_POLITICA_PRIVACIDAD]`, `[URL_PUBLICA_BADATOR]`) por los valores reales
   aprobados (`dev.mail.igoni@gmail.com`,
   `https://ikergoni.github.io/bus-dbus/privacy/privacy-policy-es.html` con nota de
   pendiente de Pages, y `https://github.com/IkerGoni/bus-dbus` cuando sea público), o
   marcar claramente arriba que la fuente vigente es `GUIA-SUBIDA-PLAY-CONSOLE.md`.
4. **`GUIA-SUBIDA-PLAY-CONSOLE.md`:**
   - **Corregir la contradicción en §0:** alinear la línea 111 («Play Console ya permite
     crear la app aunque la verificación siga en curso») con el paso 4 de la lista (l. 120,
     «Solo entonces aparece habilitado Crear aplicación»), dejando claro que **Crear
     aplicación** y los borradores (§1–§3) pueden hacerse mientras la verificación sigue en
     curso, pero la **publicación** queda bloqueada hasta completar identidad y teléfono.
   - **Corregir las rutas del AAB en §0.2:** apuntar a
     `$PLAY_PACK_DIR/05-release/app-release-signed.aab` en lugar de
     `apps/android/app/build/outputs/bundle/release/app-release.aab`.
   - **Reforzar §3.7 (justificación FGS `dataSync`):** añadir una frase breve explicando
     que `WorkManager` tiene un intervalo periódico mínimo de 15 minutos y no permite
     disparar avisos de autobús con antelación exacta de pocos minutos (sondeo cada 30 s
     en el último tramo).
5. **Gate 1:** comprobar que ningún fichero editado en `$PLAY_PACK_DIR` contiene rutas de
   usuario locales, correos fuera de la lista aprobada ni identificadores de GCP, y que no
   se han tocado los binarios de `05-release/`.

---

## Fase 2 · Respaldo, purga del historial, actualización de SHAs/docs y `push --force-with-lease`

### 2.1 Pre-flight y respaldo
1. Comprobar que el repo local está en `main`, limpio (`git status -sb`), sincronizado
   con `origin/main`, sin tags, sin stashes y con un único worktree.
   *(Si el único cambio sin commitear en el workspace es este prompt y su entrada en
   `docs/README.md`, commitearlo antes en `main` con formato Conventional Commits en
   español y hacer `push` normal, o incluirlo antes del respaldo.)*
2. Guardar las referencias previas:
   - `OLD_REMOTE_SHA=$(git ls-remote origin refs/heads/main | awk '{print $1}')`
   - `ORIG_HEAD_TREE=$(git rev-parse HEAD^{tree})`
   - `ORIG_ANDROID_TREE=$(git rev-parse HEAD:apps/android)` (debe ser `e0db74e7268f6878f43e6a86718d83a7a0d630f7`)
   - `ORIGIN_URL=$(git remote get-url origin)`
3. Crear respaldo completo **fuera del repositorio** y verificarlo:
   - `git bundle create <directorio-respaldo-fuera-del-repo>/bus-dbus-pre-purge-2026-11-24.bundle --all`
   - `git bundle verify <directorio-respaldo-fuera-del-repo>/bus-dbus-pre-purge-2026-11-24.bundle`
   - **Gate 2A:** si `bundle verify` falla, abortar.

### 2.2 Purga en clon aislado
1. Clonar en `/tmp`: `git clone --no-hardlinks . /tmp/bus-dbus-purge`.
2. Extraer programáticamente del blob `a56189b87e5a` el único correo fuera de la lista
   aprobada (`8208093+ikergoni@users.noreply.github.com`, `dev.mail.igoni@gmail.com`),
   confirmar que su `sha256[:12]` es exactamente `b0373516d594` (sin imprimir el correo)
   y escribir `<correo>==>[redacted-email]` en `/tmp/replace-email.txt`.
3. Ejecutar en `/tmp/bus-dbus-purge`:
   - `git filter-repo --replace-text /tmp/replace-email.txt --force`
   - Borrar inmediatamente `/tmp/replace-email.txt` (`rm -f /tmp/replace-email.txt`).
4. **Gate 2B (antes de commitear ni tocar `origin`):**
   - `git rev-parse HEAD^{tree}` == `$ORIG_HEAD_TREE`.
   - `git rev-parse HEAD:apps/android` == `$ORIG_ANDROID_TREE`.
   - Recorrer todos los blobs alcanzables (`git rev-list --objects --all`) y confirmar
     **0 apariciones** de `sha256:b0373516d594` y **0 correos no aprobados** en todo el
     historial.
   - Si falla cualquier comprobación, borrar `/tmp/bus-dbus-purge` y detenerse.

### 2.3 Actualización de SHAs y docs antes del force-push
En `/tmp/bus-dbus-purge`, usando `.git/filter-repo/commit-map`:
1. **`README.md`:** actualizar los SHAs reescritos de la tabla cronológica y del diagrama
   Mermaid (conservar `2eabe0f` si no cambió) y corregir la línea 43 para no anunciar
   «Play Store checklist at 29/29» mientras existan gates abiertos.
2. **`docs/auditoria/2026-11-reauditoria-publicacion.md`:** añadir nota breve con el nuevo
   SHA equivalente a `756eb9c` tras la purga y precisar «0 errores de lint» en lugar de
   «lint sin issues».
3. **`docs/privacy/privacy-policy-es.html` y `docs/privacy/privacy-policy-en.html`:**
   actualizar la fecha a `2026-11-24` (paridad con `privacy-policy-eu.html` y
   `03-privacy/*-FINAL.md`).
4. **`docs/play-console/data-safety-decision.md`:** matizar en la sección «Riesgo
   residual» (l. 45) que, si Google exigiera declarar consultas a Dbus como «App activity»,
   al ser un servidor tercero sin contrato de encargado podría requerir marcar también
   compartición y activar «datos no cifrados en tránsito» (HTTP).
5. Ejecutar `node tools/repo/check-identity.mjs` (debe salir `0`) y commitear siguiendo
   `docs/contributing/01-commits.md` (`docs: actualiza SHAs y documentación tras saneamiento del historial`).
6. Confirmar que `git rev-parse HEAD:apps/android` sigue siendo `$ORIG_ANDROID_TREE`.

### 2.4 Publicación del historial purgado y limpieza local
1. Reañadir el remoto (que `git filter-repo` elimina por defecto) y subir con guarda
   explícita de lease:
   - `git remote add origin "$ORIGIN_URL"`
   - `git push --force-with-lease=refs/heads/main:$OLD_REMOTE_SHA origin main`
2. Verificar que `git ls-remote origin refs/heads/main` coincide con el nuevo `HEAD` y
   borrar `/tmp/bus-dbus-purge`.
3. En la copia local principal del workspace:
   - `git fetch origin && git reset --hard origin/main`
   - `git reflog expire --expire=now --all && git gc --prune=now`
   - Confirmar `git fsck --unreachable --no-reflogs` sin objetos inalcanzables y
     `node tools/repo/check-identity.mjs` con salida `0`.

---

## Fase 3 · Borrado obligatorio de runs, artefactos y cachés de Actions pre-purga

1. Listar todos los runs (`gh api repos/IkerGoni/bus-dbus/actions/runs`) y borrar **todos**
   los que tengan `head_sha` anterior al nuevo `HEAD`
   (`gh api -X DELETE repos/IkerGoni/bus-dbus/actions/runs/{id}`).
2. Comprobar `gh api repos/IkerGoni/bus-dbus/actions/artifacts` y borrar cualquier artefacto
   asociado a commits pre-purga.
3. Listar `gh api repos/IkerGoni/bus-dbus/actions/caches` y borrar las cachés pre-purga
   (en especial `11003053669`, caché `pnpm` anterior al override de `glob@13`, o todas
   las anteriores al nuevo `HEAD`) con
   `gh api -X DELETE repos/IkerGoni/bus-dbus/actions/caches/{id}`.
4. **Gate 3A:** confirmar 0 runs/artefactos apuntando a SHAs pre-purga y 0 cachés `pnpm`
   antiguas.

---

## Gate 3B · Comprobación bloqueante de commits colgantes en GitHub (ANTES de abrir)

Consultar por API si GitHub sigue sirviendo los commits pre-purga:
- `gh api repos/IkerGoni/bus-dbus/commits/756eb9cc201d1119521e6693a38fd19aa99b9157`
- `gh api repos/IkerGoni/bus-dbus/commits/$OLD_REMOTE_SHA`

**REGLA DE PARADA DURA:**
- Si **cualquiera** de los dos devuelve HTTP `200` (en lugar de `422`), **NO ejecutes la
  Fase 4** ni cambies la visibilidad del repo. Detente y ofrece al propietario las dos
  salidas seguras:
  1. Abrir ticket a **GitHub Support** solicitando `git gc` en servidor y purga de vistas
     cacheadas para `IkerGoni/bus-dbus`, y retomar la Fase 4 solo cuando ambos devuelvan
     HTTP `422`.
  2. Recrear el repositorio privado limpio (`IkerGoni/bus-dbus`, actualmente con 0 PRs,
     0 issues, 0 forks y 0 releases) y subir únicamente el historial ya purgado,
     verificando HTTP `422` antes de abrirlo.

---

## Fase 4 · Hacer público el repo, activar GitHub Pages y verificar (solo si Gate 3B = HTTP 422)

1. Cambiar la visibilidad de `IkerGoni/bus-dbus` a `public`.
2. Activar GitHub Pages desde la rama `main` y carpeta `/docs` con HTTPS forzado
   (`docs/.nojekyll` ya existe en `main`).
3. Habilitar **Secret scanning** y **Push protection** (vía API si está disponible; si
   requiere acción en la web de Settings, indicarlo explícitamente).
4. Esperar al despliegue de Pages y comprobar **sin sesión ni token**:
   - `curl -sSI` sobre las 3 URLs
     `https://ikergoni.github.io/bus-dbus/privacy/privacy-policy-{es,eu,en}.html`:
     confirmar HTTP `200`, `Content-Type: text/html` y sin redirección a login.
   - Confirmar en el HTML descargado «Dev Igoni» y `dev.mail.igoni@gmail.com`.
   - Confirmar nuevo run de `CI` en verde sobre el nuevo `HEAD` y **0 alertas** de
     Secret Scanning (`gh api repos/IkerGoni/bus-dbus/secret-scanning/alerts`).
5. Actualizar `ESTADO.md` si procede (o informar del estado final para cerrarlo en un
   commit limpio) y entregar un informe breve en español con:
   - Resumen de cambios aplicados en `$PLAY_PACK_DIR` (Fase 1).
   - Nuevo SHA de `main` y confirmación de `apps/android` = `e0db74e7268f`.
   - Evidencia de HTTP `422` en los SHA pre-purga.
   - Evidencia de HTTP `200` en las 3 políticas de privacidad.
   - Estado de `CI` y Secret Scanning.
   - Recordatorio de los pasos manuales restantes de Google Play (abrir las 3 políticas
     desde Ajustes de la app instalada, revisión del euskera en
     `01-listing/REVISION-EUSKERA.md`, cotejo de la huella `46:E6:55:…:AA:9B` en Play
     Console y confirmación del requisito 12 testers / 14 días).
