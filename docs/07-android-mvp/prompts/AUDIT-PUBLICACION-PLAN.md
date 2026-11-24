# Auditoría externa del plan de publicación (GitHub y Google Play)

Actúa como auditor externo e independiente de seguridad, privacidad y release. No has
participado en el trabajo que vas a revisar: no des nada por bueno porque lo diga un
documento del repo. Tu misión es decidir si el plan para pasar de
`ESTADO: AVISO — remediación en main (2ffe968, CI verde); faltan 3 pasos irreversibles`
a la publicación es correcto, completo y seguro, **antes** de que se ejecute ninguno
de esos pasos. Entrega el informe en español.

## Alcance

- Repo local `bus-dbus` y su remoto `origin` en GitHub (`IkerGoni/bus-dbus`, privado).
- Paquete de Google Play en `~/Desktop/badator-google-play-pack`.
- Documentos de partida: `AGENTS.md`, `ESTADO.md`, `docs/README.md`,
  `docs/contributing/` (los tres), `docs/auditoria/2026-11-reauditoria-publicacion.md`,
  `docs/play-console/data-safety-decision.md`, el ADR 009 y, en el paquete,
  `GUIA-SUBIDA-PLAY-CONSOLE.md`, `05-release-gates.md` y `05-release/README.md`.

## Reglas

1. **Solo lectura.** No hagas commits, pushes, cambios de configuración de GitHub, ni
   subidas a Play Console. Todo lo que escriba (instalar, compilar, `filter-repo` de
   prueba) se hace en una copia aislada en `/tmp`, que borras al terminar.
2. **Nada irreversible**: ni `push --force`, ni borrar runs o artefactos, ni cambiar la
   visibilidad, ni publicar Pages, ni rotar credenciales. Tampoco uses la clave de
   firma: para cotejar binarios, compila sin firmar
   (`-Pdbus.release.storeFile= -Pdbus.release.unsigned=true`).
3. **Identidad pública aprobada:** Git `IkerGoni <8208093+IkerGoni@users.noreply.github.com>`;
   legal `Dev Igoni` / `Iker Goñi Cuesta (Pamplona, España)`; soporte
   `dev.mail.igoni@gmail.com`. Cualquier otro correo, ruta de usuario local, host local
   o secreto en un commit, trailer, blob, log o artefacto es P0. Cítalo por tipo,
   fichero y huella (`sha256[:12]`), **nunca en claro**.
4. Separa lo que has reproducido de lo que solo has leído. Usa `cerrado` solo para lo
   reproducido; si falta evidencia, `no verificado`.

## Parte A · Comprobar lo que se da por hecho

Reprodúcelo; no lo copies del informe.

1. **Lockfile e identidad.** En `main` no hay correos no aprobados (incluidos los
   lockfiles y metadatos de dependencias). Un `pnpm install --frozen-lockfile` en un
   clon limpio no los reintroduce. `node tools/repo/check-identity.mjs` falla con un
   caso positivo plantado y pasa sin él. Revisa si el override de `glob` en
   `pnpm-workspace.yaml` respeta el lane congelado de la PWA.
2. **Historial.** Cuenta los blobs históricos que aún contienen el correo del aviso
   `deprecated` de `glob` (el informe dice 4) y confirma que no hay otros hallazgos:
   recorre todos los blobs con todas sus rutas, comprimidos y metadatos PNG, mensajes y
   trailers, `fsck --unreachable`, reflog, stash y refs.
3. **CI.** `ci.yml` (lint, typecheck, identidad, unitarios, `build:ci`) y
   `integration.yml` (manual, con sonda). Valora la decisión de sacar la integración
   del CI de cada push porque Dbus no responde desde los runners (run `45301360230`).
   ¿Queda algún camino por el que una regresión de integración llegue a `main` sin
   verse? Ejecuta `pnpm verify` y `pnpm run build:ci`, y en `apps/android`
   `./gradlew check assembleDebug --no-daemon` (sin otros procesos pesados en
   paralelo: con 8 GB de RAM el daemon de Kotlin se queda sin memoria). Los recuentos
   documentados son 7 + 43 + 93 = 143 TS y 156 debug / 150 release.
4. **Binarios.** El AAB y el APK de `05-release/` están firmados con el mismo
   certificado y su `classes.dex` debe ser igual al de un build sin firmar del árbol
   `apps/android` de `main` (el informe da `9648d959…`). Compila dos veces desde rutas
   distintas para confirmar que el build es determinista. Comprueba versionCode,
   versionName, permisos y ausencia de SDKs de seguimiento.
5. **Privacidad y Data Safety.** Contrasta `data-safety-responses.json`,
   `data-safety-decision.md`, las tres políticas de `docs/privacy/` y el código
   (`AlertEngine.kt`, `AlertMonitorService.kt`, `LocationProvider.kt`, intent `geo:`,
   manifest y reglas de backup) con la guía vigente de Google Play. Di si «sin recogida
   ni compartición» es defendible y si el texto del servicio en primer plano describe
   el motor tal como funciona.

## Parte B · Revisar los tres pasos irreversibles

Para cada paso: ¿es necesario?, ¿el orden es correcto?, ¿falta algo?, ¿qué puede salir
mal y cómo se recupera?

1. **Purga del historial y force-push.** Plan previsto: `git bundle --all` de respaldo
   fuera del repo → clon nuevo → `git filter-repo --replace-text` solo con el correo →
   verificar 0 apariciones y que el árbol de HEAD no cambia → actualizar los SHA
   citados en la documentación (cronología del README, informes) → `push
   --force-with-lease` → comprobar refs remotas.
   Preguntas concretas:
   - ¿Sigue GitHub sirviendo por SHA los commits antiguos (vistas cacheadas, commits
     colgantes) después del force-push, y en particular cuando el repo pase a público?
     ¿Hace falta pedir a GitHub Support que haga GC antes de abrir el repo?
   - ¿Hay forks, refs de PR, tags, releases o caches de Actions que conserven los
     objetos antiguos?
   - ¿El plan rompe algo más: enlaces a commits en docs, badges, runs?
2. **Borrar runs y artefactos de Actions anteriores a la purga.** Los logs de los 4 runs
   existentes no contienen el correo, y los artefactos `dist` son builds del bot.
   ¿Hace falta borrarlos, o basta con dejarlos caducar?
3. **Hacer público el repo y activar Pages desde `main` y `/docs`.** Comprueba que el
   árbol y todo el historial que quedaría público cumplen la regla 3, que
   `docs/.nojekyll` existe y que las tres URLs
   `https://ikergoni.github.io/bus-dbus/privacy/privacy-policy-{es,eu,en}.html`
   responderían con el contenido de `docs/privacy/`. Propón las comprobaciones
   posteriores: HTTP 200 sin sesión, apertura desde Ajustes de la app y alertas de
   secret scanning.

## Parte C · Google Play

Revisa `GUIA-SUBIDA-PLAY-CONSOLE.md` contra la consola actual: formulario «Crear
aplicación» (idioma predeterminado, nombre, nombre de paquete, firma de apps de Play,
leyes de exportación), ficha, configuración de la tienda, contenido de la app,
Data Safety, declaración FGS, pistas de prueba (incluido el requisito de prueba
cerrada de 12 testers durante 14 días para cuentas personales) y subida del AAB. Señala
cualquier valor que no coincida con el binario o con el código.

Quedan fuera de tu alcance y se marcan como pendientes del propietario: la revisión del
euskera (`01-listing/REVISION-EUSKERA.md`), el cotejo de la huella de la clave de
subida en Play Console y el tipo de cuenta.

## Entrega

1. Tabla `ID | Severidad (P0–P3) | Estado | Evidencia | Impacto | Criterio de cierre`.
   Estados: `cerrado` (reproducido), `abierto`, `no verificado`.
2. Para cada uno de los tres pasos irreversibles: **aprobar**, **aprobar con cambios**
   (di cuáles) o **rechazar**, con el procedimiento corregido si hace falta.
3. Veredicto por separado para GitHub y para Google Play, y uno global:
   `🔴 NO-GO`, `🟡 GO CON CONDICIONES` o `🟢 GO`.
4. Comandos ejecutados con su código de salida y los recuentos reales. Sin secretos,
   correos no aprobados ni rutas locales en el informe.
