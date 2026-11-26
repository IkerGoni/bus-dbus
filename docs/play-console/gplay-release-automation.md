# Publicación automatizada en Google Play (`gplay`)

Guía operativa para consultar el estado de **Badator! Bus Donostia** (`eus.busdonostia.app`) y automatizar la compilación, validación y subida de nuevas versiones a Google Play Console mediante la CLI `gplay` y [`tools/play-store/10-release-gplay.mjs`](../../tools/play-store/10-release-gplay.mjs).

## 1. Requisitos locales (fuera del repositorio)

- **CLI `gplay` instalada:** disponible en el `PATH` (`gplay --version`).
- **Clave JSON de la Service Account fuera del repo:** guardada en `~/.config/gplay/devplayconsole.json` con permisos `600` (y directorio `~/.config/gplay` con `700`).
  - **Prohibido** copiar la clave dentro del repositorio Git, leer su contenido en pantalla/logs o incluirla en commits.
  - `.gitignore` bloquea `devplayconsole*.json`, `gplay-service-account*.json`, `play-service-account.json`, `.gplay/`, `*.jks`, `*.keystore`, `*.aab` y `*.apk`.
- **Perfil `default` configurado en `~/.gplay/config.json`:**

```bash
mkdir -p -m 700 ~/.config/gplay
chmod 600 ~/.config/gplay/devplayconsole.json
gplay auth login --service-account ~/.config/gplay/devplayconsole.json --profile default --set-default
gplay auth status
```

- **Clave de subida Android (`bundleRelease`):** configurada fuera del repo en `~/.gradle/gradle.properties` (`dbus.release.*`) o mediante variables de entorno `DBUS_RELEASE_*` (ver [`apps/android/README.md`](../../apps/android/README.md)).

## 2. Permisos y regla de autorización para agentes

La Service Account autorizada tiene acceso exclusivo sobre el paquete `eus.busdonostia.app` (lectura de app y calidad, lanzamiento en canales de pruebas y producción, y gestión de la ficha de Play Store; sin acceso a finanzas, pedidos, reseñas ni gestión de testers).

- **Operaciones de solo lectura (permitidas sin pedir permiso):**
  - `gplay auth status`
  - `gplay apps list`
  - `gplay status --package eus.busdonostia.app --pretty`
  - `gplay preflight --file apps/android/app/build/outputs/bundle/release/app-release.aab`
- **Operaciones de escritura (requieren orden explícita del propietario para esa versión concreta):**
  - Subir AABs o crear releases (`gplay release`, `tools/play-store/10-release-gplay.mjs` sin `--dry-run`).
  - Promover o modificar tracks (`internal`, `alpha`, `beta`, `production`).
  - Editar ficha de Play Store, capturas o declaraciones.

## 3. Flujo paso a paso para publicar una nueva versión

1. **Consultar el estado actual y el último `versionCode` en Play:**

```bash
gplay status --package eus.busdonostia.app --pretty
```

2. **Subir `versionCode` (+1 respecto al máximo publicado en Play) y `versionName`** en `apps/android/app/build.gradle.kts`.

3. **Preparar las notas de versión** en `build/outputs/play-store/release-notes-<versionName>.json` (directorio `build/` ignorado por Git), con códigos de idioma `es-ES`, `eu-ES` y `en-US` (≤ 500 caracteres por idioma):

```json
[
  { "language": "es-ES", "text": "Resumen en español..." },
  { "language": "eu-ES", "text": "Laburpena euskaraz..." },
  { "language": "en-US", "text": "Summary in English..." }
]
```

4. **Compilar, verificar y previsualizar en seco (`--dry-run`):**

```bash
node tools/play-store/10-release-gplay.mjs --track alpha --status completed --dry-run
```

Este comando ejecuta en orden:
- Comprobación de perfil activo en `gplay auth status`.
- `./gradlew check bundleRelease` en `apps/android/` (lint, tests JVM Debug/Release y AAB firmado).
- `gplay preflight` sobre `app-release.aab` (debe cerrar con `0` errores).
- `gplay --dry-run release ...` sin enviar cambios reales a Google Play.

5. **Publicar tras aprobación explícita** reutilizando el AAB ya compilado (`--skip-build`):

```bash
node tools/play-store/10-release-gplay.mjs --track alpha --status completed --skip-build
```

Opciones útiles del script:
- `--track <internal|alpha|beta|production>` (por defecto `internal`; la prueba cerrada activa usa `alpha`).
- `--status <completed|draft|inProgress|halted>` (por defecto `completed`).
- `--notes <ruta>` para indicar un fichero JSON concreto (por defecto usa `build/outputs/play-store/release-notes-<versionName>.json` si existe).
- `--version-name <nombre>` para fijar un nombre de release distinto del `versionName` del AAB.
- `--wait` para esperar a que Play termine de procesar el artefacto.

6. **Verificar la publicación en Play:**

```bash
gplay status --package eus.busdonostia.app --pretty
```

## 4. Notas operativas

- `gplay preflight` puede informar `[WARNING] misplaced_files: BUNDLE-METADATA/com.android.tools.build.gradle/app-metadata.properties`; es metadato estándar de AGP y no bloquea la subida (`0` errores).
- Si Google Play devuelve `403` / `permission denied` justo después de otorgar permisos en Play Console, suele deberse a la ventana de propagación (de minutos hasta 24 h): no regenerar ni alterar credenciales de inmediato; reintentar más tarde con `gplay apps list`.
