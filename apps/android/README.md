# apps/android — Bus Donostia (MVP v1)

App Android nativa (Kotlin + Compose). Spec: `docs/07-android-mvp/`,
plan: `docs/07-android-mvp/SPRINTS.md`, decisión: ADR 009. Sprints 0–4 hechos;
Sprint 5 (publicación) pendiente.

## Toolchain (macOS, instalado con Homebrew)

```bash
brew install openjdk@17
brew install --cask android-commandlinetools
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
export PATH="$JAVA_HOME/bin:$PATH"
sdkmanager "platforms;android-36" "build-tools;36.0.0" "platform-tools"
echo "sdk.dir=$ANDROID_HOME" > local.properties   # no se commitea
```

Emulador: `sdkmanager "emulator" "system-images;android-36;google_apis;arm64-v8a"`
y `avdmanager create avd -n dbus36 -k "system-images;android-36;google_apis;arm64-v8a" -d pixel_7`.

## Verificación

```bash
./gradlew check assembleDebug   # lint (MissingTranslation/HardcodedText = error) + tests JVM + APK debug
```

Resultado esperado: **152 tests JVM (debug) / 138 tests JVM (release)**, 0 fallos, 0 errores de lint. CI: `.github/workflows/android.yml`.

## Release firmado (H8)

La clave de subida la crea y custodia Iker (Play App Signing); nunca va al repo.
Gradle la lee de `~/.gradle/gradle.properties`:

```properties
dbus.release.storeFile=/ruta/fuera/del/repo/upload.jks
dbus.release.storePassword=…
dbus.release.keyAlias=…
dbus.release.keyPassword=…
```

o de `DBUS_RELEASE_STORE_FILE`, `DBUS_RELEASE_STORE_PASSWORD`,
`DBUS_RELEASE_KEY_ALIAS` y `DBUS_RELEASE_KEY_PASSWORD`. Sin ellas,
`assembleRelease`/`bundleRelease` fallan con un mensaje que dice qué falta.
Para medir tamaño sin firma: `./gradlew assembleRelease -Pdbus.release.unsigned=true`.
Ojo: con la caché de configuración activa, Gradle guarda esos valores en
`apps/android/.gradle/` (ignorado por git); bórrala si el equipo se comparte.

## Versiones

AGP 8.13.2 · Gradle 8.14.3 · Kotlin 2.2.21 · Compose BOM 2025.12.01 ·
minSdk 26 · targetSdk/compileSdk 36. Se eligió la línea AGP 8 por estabilidad;
migrar a AGP 9 (Kotlin integrado) es tarea aparte.

Fuentes Plus Jakarta Sans y JetBrains Mono: OFL, ver `licenses/`.

## Datos (Sprint 1)

- El asset `catalog.json` **no se commitea**: lo genera `merge<Variant>Catalog` (en `app/build/`) en
  cada build fusionando `apps/bot/data/dbus-catalog.json` +
  `dbus-stop-coordinates.json` (548 paradas, 514 con coordenadas, 39 líneas,
  127 itinerarios). Para actualizarlo, regenerar el catálogo del bot.
- `app/src/main/assets/config.json`: config de la API por defecto (ADR 009). La
  URL de la config remota va en `BuildConfig.REMOTE_CONFIG_URL` (vacía mientras
  el repo sea privado y no haya hosting; solo HTTPS).
- Paquetes: `domain/` (Kotlin puro, sin imports de Android), `data/api`
  (`DbusClient` → `StopFeed`), `data/config`, `data/catalog`, `data/db` (Room;
  esquema exportado en `app/schemas/`, commitearlo en cada cambio de versión).
- Los tests JVM leen los fixtures reales de `packages/contracts/fixtures/` y el
  catálogo generado (rutas inyectadas por Gradle). La capa Room se prueba con
  Robolectric (SDK 35).
