# apps/android — Bus Donostia (MVP v1)

App Android nativa (Kotlin + Compose). Spec: `docs/07-android-mvp/`,
plan: `docs/07-android-mvp/SPRINTS.md`, decisión: ADR 009.

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

## Versiones

AGP 8.13.2 · Gradle 8.14.3 · Kotlin 2.2.21 · Compose BOM 2025.12.01 ·
minSdk 26 · targetSdk/compileSdk 36. Se eligió la línea AGP 8 por estabilidad;
migrar a AGP 9 (Kotlin integrado) es tarea aparte.

Fuentes Plus Jakarta Sans y JetBrains Mono: OFL, ver `licenses/`.

## Datos (Sprint 1)

- `assets/catalog.json` **no se commitea**: lo genera `merge<Variant>Catalog` en
  cada build fusionando `apps/bot/data/dbus-catalog.json` +
  `dbus-stop-coordinates.json` (548 paradas, 514 con coordenadas, 39 líneas,
  127 itinerarios). Para actualizarlo, regenerar el catálogo del bot.
- `assets/config.json`: config de la API por defecto (ADR 009). La URL de la
  config remota va en `BuildConfig.REMOTE_CONFIG_URL` (vacía hasta el Sprint 5;
  solo HTTPS).
- Paquetes: `domain/` (Kotlin puro, sin imports de Android), `data/api`
  (`DbusClient` → `StopFeed`), `data/config`, `data/catalog`, `data/db` (Room;
  esquema exportado en `app/schemas/`, commitearlo en cada cambio de versión).
- Los tests JVM leen los fixtures reales de `packages/contracts/fixtures/` y el
  catálogo generado (rutas inyectadas por Gradle). La capa Room se prueba con
  Robolectric (SDK 35).
