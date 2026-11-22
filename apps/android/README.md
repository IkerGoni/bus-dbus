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
