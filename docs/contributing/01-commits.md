# Commits

Aplica a commits nuevos y a mensajes de squash. El historial existente no se
reescribe para cumplirla.

## Formato

```text
<tipo>[(scope)][!]: <resumen>

[cuerpo: por qué, no qué]

[BREAKING CHANGE: ...]
[Refs: ...]
[Co-authored-by: Nombre <correo>]
```

Base: [Conventional Commits 1.0.0](https://www.conventionalcommits.org/es/v1.0.0/).

- **Tipos** (minúscula): `feat`, `fix`, `perf`, `refactor`, `test`, `docs`,
  `build`, `ci`, `chore`, `revert`.
- **Scopes** orientativos, no lista cerrada: `android`, `bot`, `core`,
  `contracts`, `mobile`, `repo`. En `docs` se usa el área afectada
  (`docs(estado)`, `docs(auditoria)`) o ninguno; nunca `docs(docs)`. Sin
  scope si el cambio cruza varias áreas. Nunca nombres de clase, ticket,
  agente o sprint como scope.
- **Idioma:** español, sin mezclar idiomas en una serie.
- **Resumen:** imperativo o descriptivo, ≤ 72 caracteres preferente, sin
  punto final. **Sin sprint ni versión delante** (`Sprint 6 v1.1.0 — …`):
  esos datos van al cuerpo o a un tag decidido explícitamente.
- `!` o `BREAKING CHANGE:` solo si rompe un contrato real (API, esquema
  Room, formato de callback, `packages/**`).

Ejemplos válidos de este repo:

```text
fix(bot): EPERM cuenta como proceso vivo en el lock (H14)
test(contracts): paridad TS/Kotlin con casos de dominio compartidos (H6)
docs: pone al día la documentación con el código de main
```

## Contenido

- Un commit = un cambio coherente que compila y pasa la verificación de su
  área. Separar refactor, comportamiento y docs cuando sean independientes.
- Stage explícito por ruta (`git add <rutas>`); revisar `git diff --cached`.
  No usar `git add .` / `-A` con cambios ajenos en el árbol.
- El cuerpo no afirma tests, CI o despliegues que no se hayan ejecutado.

## Merges, reverts y fixups

- Merge real (dos o más padres) hecho en local:
  `merge: <descripción breve> (<rama>)`. Se aceptan también los mensajes que
  generan Git (`Merge branch …`) o GitHub (`Merge pull request #…`).
- `merge:` nunca se usa en un commit de un solo padre.
- El commit resultante de un squash sigue este formato; revisar el mensaje
  que propone GitHub antes de confirmar.
- `revert: …`, `Revert "…"`, `fixup!` y `squash!` se toleran; los dos
  últimos no llegan a `main`.

## Coautoría con IA

- `Co-authored-by` es **opcional** y debe ser **veraz**: solo para una
  contribución sustancial y cuando se conozcan con certeza la identidad y el
  correo (por ejemplo, el modelo que realmente respondió).
- Usar IA no obliga a añadir el trailer. No inventar modelos ni correos.
- Las atribuciones ya existentes se conservan; no se limpia el historial.
