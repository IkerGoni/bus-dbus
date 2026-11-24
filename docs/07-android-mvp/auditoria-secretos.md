# Auditoría de secretos — historial git (CORREGIDA)

**Fecha original:** 2026-11-23 · **Corregida:** 2026-11-23 tras la auditoría
adversarial ([`A5-01`](../auditoria/2026-11-sprint5-adversarial.md)).

> **La versión original de este informe era falsa.** Concluyó «0 coincidencias,
> no hay que rotar nada» porque `git grep` solo mira texto: no inspecciona el
> interior de blobs binarios o comprimidos. El token real del bot de Telegram
> estaba dentro de `bot-code.zip`, en el historial y en `origin/main`. La línea
> «Mensajes de commit: 0» también era falsa: el grep citado devuelve 35.

## Hallazgo (A5-01)

- Blob `da8522ef1648…` = `bot-code.zip`, añadido en `bef1873` (2026-11-22
  05:04) y borrado en `482b53f` («refactor: monorepo pnpm…»). Borrado del
  árbol, **no del historial**: `git branch -r --contains bef1873` → `origin/main`.
- Dentro del zip: `bot/src/config.ts:12` con un token real de Telegram
  (patrón `[0-9]{8,10}:[A-Za-z0-9_-]{35}`). Era el **mismo** token que tenía
  `.env` raíz. Censurado aquí; nunca se imprime ni se persiste (regla 3 de
  `AGENTS.md`).
- El repo es privado, pero el token estaba en GitHub y en cada clon.

## Método (corregido)

Original (insuficiente): patrón de token sobre `git grep` por commit y árbol de
trabajo; mensajes de commit; `.env` trackeados. **No abría blobs binarios.**

Añadido tras A5-01:

- Inventario de objetos: `git rev-list --all --objects` → localizar blobs
  binarios/comprimidos (`.zip`, `.tar`, `.gz`…) aunque estén borrados del árbol.
- Extracción en memoria del zip (`git cat-file -p <blob>` + `zipfile`) y escaneo
  de su contenido con el mismo patrón, sin escribir el secreto a disco.
- Pasada amplia sobre `git log --all --reflog -p --text` (125 commits con
  reflog; 124 sin él) con 10 patrones: `AIza…`, `-----BEGIN … PRIVATE KEY-----`,
  `AKIA…`, `ghp_…`, `xox…`, `sk-…`, `api.telegram.org/bot<n>`, `BOT_TOKEN=`,
  token de Telegram y claves privadas.
- Recomendado para próximas auditorías: `gitleaks` o `trufflehog` (no instalados
  en esta máquina; el método manual anterior los sustituye solo parcialmente).

## Resultados (corregidos)

| Comprobación | Resultado |
|---|---|
| Árbol de trabajo en `HEAD` | 0 coincidencias |
| Historial en texto (124 commits + 1 solo en reflog) | 0 coincidencias fuera del zip |
| **Blob binario `bot-code.zip`** | **1 token real de Telegram** (`bot/src/config.ts:12`) |
| Mensajes de commit (`token\|bot\|secret\|password\|api.?key`) | **35** coincidencias de palabras; ninguna es un secreto (el «0» original era falso) |
| `.env` trackeados | Ninguno (`.env` y `apps/bot/.env` en `.gitignore`); las 15 coincidencias de `BOT_TOKEN=` son `vitest_test_token` y plantillas |
| `.jks` / `.keystore` / `.p12` / `.pem` trackeados | Ninguno |

## Acciones

1. **Rotación del token: HECHA** (Iker, 2026-11-23, BotFather + entorno local).
   Verificado sin imprimir nada: el token vigente ya **no** coincide con el del
   antiguo `bot-code.zip`. El token histórico quedó revocado e invalidado.
2. **Purga del historial: HECHA** (2026-11-23/24 con `git filter-repo`).
   Eliminados de todos los commits y árboles del repositorio `bot-code.zip`,
   `state/**`, artefactos de `build/outputs/play-store/**`, correos no
   destinados a soporte público y rutas locales `/Users/...`. Verificado con
   escaneo completo de `git rev-list --all --objects` (0 ficheros prohibidos,
   0 secretos, 100 % de commits con identidad pública unificada).
3. **Publicación en contenedor remoto limpio:** para evitar que GitHub siga
   sirviendo objetos huérfanos cacheados por runs antiguos de GitHub Actions,
   el historial saneado se publica en un repositorio remoto limpio sin objetos
   ni runs previos.

**Estado:** token rotado y revocado · historial Git purgado y verificado (0 secretos en blobs) · informe original corregido.
