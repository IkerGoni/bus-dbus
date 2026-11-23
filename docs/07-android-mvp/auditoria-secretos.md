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

1. **Rotación del token: HECHA** (Iker, 2026-11-23, BotFather + `.env` raíz).
   Verificado sin imprimir nada: el token actual de `.env` ya **no** coincide
   con el del zip. El token filtrado queda invalidado aunque el blob permanezca.
2. **Purga del historial: pendiente de decisión de Iker.** `git filter-repo`
   reescribe historia y exige force-push (irreversible; todos los clones deben
   re-clonarse). Procedimiento en
   [`remediacion`](../auditoria/2026-11-sprint5-remediacion.md). Mientras no se
   purgue, **el repo no puede hacerse público**.
3. Reinicio del bot en producción con el token nuevo (regla 9): pendiente de
   autorización; el bot está parado.

**Estado:** token rotado y verificado · historial pendiente de purga ·
informe original corregido (no volver a citar «0 coincidencias, no hay que rotar»).
