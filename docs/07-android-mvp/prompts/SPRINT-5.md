# Prompt — Sprint 5 · Publicación (Android MVP v1)

Pégalo en una sesión nueva de Claude Code en la raíz de `bus-dbus`.

---

Implementa el **Sprint 5** del MVP Android de bus-dbus.

**Lee primero, en este orden:** `AGENTS.md`, `ESTADO.md`,
`docs/07-android-mvp/SPRINTS.md` (§ Sprint 5),
`docs/07-android-mvp/docs/00-agent-guardrails.md`,
`docs/07-android-mvp/docs/04-android-platform.md` §4 (publicación) y
`docs/03-architecture/adrs/009-android-nativo-mvp.md` (§ Configuración remota).

**Entorno (ya instalado, no reinstalar):**
```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
export PATH="$JAVA_HOME/bin:$PATH"
cd apps/android && ./gradlew check assembleDebug   # verde antes de empezar (106 tests por variante)
```
Emulador: AVD `dbus36`. Memoria (Iker): apaga el emulador (`adb emu kill`) y
`./gradlew --stop` al terminar.

**Rama:** `android/sprint-5` desde `main` (si `android/sprint-4` aún no está
integrada, pregúntale a Iker antes de ramificar). Alcance: `apps/android/**`
y docs.

**Decisiones de Iker que condicionan el sprint (2026-11-22, no reabrir):**
- **El repo sigue privado** hasta que Iker decida. No lo hagas público ni
  propongas hacerlo como paso del sprint.
- **Licencia pendiente.** No añadas `LICENSE` ni texto de licencia de la app en
  A07 hasta que Iker la elija.
- Por tanto, el `config.json` remoto **no** se publica desde este repo:
  `REMOTE_CONFIG_URL` sigue vacía (la app usa la copia empaquetada). Si Iker
  elige otro hosting estático en la sesión, se fija entonces.

**Ya existe (Sprints 1–4), no lo dupliques:** motor de avisos en
`engine/` (FGS `dataSync`, alarmas, receptores, canales) con la decisión pura
en `domain/AlertEngine.kt`; deep link `dbus://stop/{stopId}?highlightLine=`;
`ConfigRepository` con remota opcional; A07 con aviso de app no oficial.

**Entregables:**
1. **Auditoría de secretos del historial** (`git log -p --all`, `gitleaks` o
   `trufflehog` si están instalados; si no, `git grep` de patrones de token de
   Telegram `[0-9]{8,10}:[A-Za-z0-9_-]{35}` en todo el historial). Informe en
   `docs/07-android-mvp/auditoria-secretos.md`. Aunque el repo siga privado.
2. **Release firmado:** `signingConfigs.release` que lea keystore y claves solo
   de variables de entorno o `~/.gradle/gradle.properties` (nunca en el repo).
   **Crear el keystore es de Iker**: pídeselo y explica el comando `keytool`;
   no lo generes tú ni lo guardes en el repo.
3. **R8 en release:** `assembleRelease` con `isMinifyEnabled = true` sin romper
   kotlinx.serialization, Room ni OkHttp (reglas en `proguard-rules.pro`);
   prueba el APK release en el emulador: Cercanas, A05 en vivo, guardar ONCE →
   notificación real.
4. **Nombre e icono no oficiales** («Bus Donostia (no oficial)» o lo que Iker
   apruebe; `applicationId` `eus.busdonostia.app` pendiente de su visto bueno).
   Icono adaptativo propio que no se parezca al de `com.indra.dbus`.
5. **Ficha de Play (borrador en docs, no se sube):** textos es/eu,
   declaración de FGS `dataSync` (por qué, cuánto dura, que el usuario lo
   inicia), uso de ubicación (solo ordenar Cercanas, nunca se guarda),
   atribución OSM (ODbL), política de privacidad (sin datos personales ni
   servidor propio). En `docs/07-android-mvp/play/`.
6. Revisar en el emulador la notificación y «Vigilando …» en **euskera**
   (en el Sprint 4 solo se probó en castellano).

**Fuera de alcance sin orden explícita de Iker:** subir nada a Play Console
(cuenta, pago, pista interna), hacer público el repo, elegir licencia.

**DoD:** `./gradlew check assembleDebug assembleRelease` verde; APK release
instalado en el emulador con el flujo de avisos funcionando; si Iker conecta un
móvil real, instalarlo ahí (`adb install`). Cero literales y cero datos
inventados (`grep`). Actualiza `SPRINTS.md` y `ESTADO.md`. Delega lo mecánico
a subagentes baratos y verifica tú. Merge a `main`, push y borrado de rama
**solo con autorización de Iker en la sesión**; si no, repórtala con
`git log --oneline main..android/sprint-5`.
