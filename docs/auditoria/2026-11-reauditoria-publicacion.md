# Reauditoría de publicación (GitHub y Google Play) — 2026-11-24

Base auditada: `756eb9c`. Remediación en `fix/repo-reauditoria-publicacion`.
Valores sensibles redactados: los correos y rutas se citan por huella o por tipo,
nunca en claro.

## Método

- Git: identidades sin mailmap, `fsck --unreachable`, reflog, refs, stash y
  worktrees. Todos los blobs alcanzables con todas sus rutas históricas, más ZIP,
  gzip y metadatos PNG (tEXt/iTXt/zTXt/eXIf), más mensajes y trailers. Patrones:
  tokens de Telegram, AWS, GitHub, Google y OpenAI, claves privadas, credenciales en
  URL, correos fuera de la lista aprobada, rutas de usuario, hosts locales, DNI/NIE e
  IBAN con dígito de control y teléfonos.
- GitHub (`gh api`): visibilidad, refs, PR, releases, commits huérfanos, Pages, runs
  y logs descomprimidos.
- Verificación en un clon aislado: `pnpm verify`, `build:ci`,
  `./gradlew check assembleDebug`, checklist de Play y dos builds release sin firmar
  desde rutas distintas para comprobar que el build es determinista.

## Resultado en `756eb9c`

- 147 commits, todos con la identidad aprobada. Sin objetos inalcanzables, sin
  stash, un solo worktree y solo `main` en local y en remoto. 0 PR y 0 releases. Los
  SHA huérfanos `4d7573e02d` y `3130923474…` devuelven 422.
- 1.188 blobs (1.308 pares blob–ruta, más 72 partes extraídas): 0 secretos, 0 claves
  y 0 rutas prohibidas (`bot-code.zip`, `state/`, keystores, binarios). Falsos
  positivos documentados: chat ids de test y nonces de excalidraw (teléfonos),
  marcadores genéricos de ruta de usuario que dejó la purga anterior (rutas), `env.local` y `test.local` (hosts),
  `this@composed` de Kotlin (correo).
- Tests: 7 + 43 + 93 = 143 TS; Android 156 debug / 150 release, lint sin issues.

## Hallazgos y estado

| ID | Sev | Estado | Evidencia y cierre |
|---|---|---|---|
| ID-01 correo ajeno en lockfiles | P0 | **Parcial** | Venía del aviso `deprecated` de `glob@11` (PWA congelada → `vite-plugin-pwa` → `workbox-build`). Override a `glob@13` y guard `tools/repo/check-identity.mjs` en CI: 0 apariciones en `main` tras dos `install --frozen-lockfile`. **Quedan 4 blobs históricos**: la purga reescribe el historial y requiere confirmación. |
| CI-01 integración enmascarada | P1 | Cerrado | `-- --testTimeout` no llegaba a vitest (cortaba a 5 s). La sonda del run 45301360230 dio 3/3 timeouts de TCP desde el runner, mientras Dbus respondía en local en 0,17 s: los runners de GitHub no alcanzan Dbus. La integración pasa a `integration.yml` (manual, con sonda) y se verifica en local con `pnpm verify` (36/36 integración). |
| PLAY-01 texto FGS «Máx 3h» | P1 | Cerrado | El tope solo actúa si no hay bus de la línea; Data Safety y fichas lo describen ahora así. |
| PLAY-02 declaración «sin recogida» | P1 | Decidido, riesgo residual | [`data-safety-decision.md`](../play-console/data-safety-decision.md). |
| PLAY-03 binarios ≠ SHA | P1 | Cerrado | El DEX del paquete anterior (`9ddd6a26…`) no salía de `756eb9c` (`9648d959…`, determinista). AAB/APK regenerados y firmados con DEX `9648d959…`; los anteriores se archivan como «no subir». |
| PLAY-04 revisión del euskera | P1 | Abierto | Necesita un hablante competente. |
| PLAY-05 test cerrado 12/14 | P1 | No verificado | Depende de la cuenta de Play Console. |
| PLAY-06 clave de subida | P2 | No verificado | Cotejar en Play Console el certificado `46:E6:55:…:AA:9B`. |
| GH-01 políticas 404 | P1 | Abierto | Repo privado y Pages desactivado. |
| REPRO-01 checklist 29/29 | P2 | Cerrado | El checklist dice qué mide; en un clon limpio da 23/29. |
| REPRO-02 generador de políticas | P3 | Abierto | `01-gen-privacy.mjs` parte de plantillas del paquete, fuera del repo. |
| PACK-01 higiene del paquete | P2 | Cerrado | Sin rutas locales ni identificadores de GCP; estado y gates al día. |
| REPO-01 `*.apk` y `*.aab` | P2 | Cerrado | `.gitignore` en todo el repo. |
| DOC-01 índice con SHA inexistente | P2 | Cerrado | Índice sin SHA y con los docs nuevos. |
| DOC-02 cronología | P3 | Cerrado | Truncado al minuto; la ventana previa a Git deja de presentarse como verificable. |
| PRIV-01 política eu | P3 | Cerrado | Menciona los identificadores de parada. |
| TOK-01 literales `dp` | P3 | Deuda | Ratchet en 153; no afecta a la publicación. |

## Pasos que requieren confirmación explícita

1. Purga del historial (`git filter-repo --replace-text` sobre el correo del
   lockfile) y `push --force`, con `git bundle` de respaldo previo.
2. Borrar runs y artefactos de Actions anteriores a la purga.
3. Hacer público el repo, activar Pages desde `main` y `/docs` y comprobar HTTP 200
   sin sesión en las tres políticas.
