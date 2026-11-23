# Política Git — índice

Se lee **una vez por sesión, justo antes de la primera operación Git que
cambie algo** (preparar un commit, tocar ramas o worktrees, integrar,
publicar). Consultas (`status`, `diff`, `log`, `show`, `branch --list`) y
`git fetch` normal no la activan; `fetch --prune` y `restore` sí.

| Operación | Leer |
|---|---|
| `add`, `commit`, `revert`, `cherry-pick`, mensaje de squash | [`01-commits.md`](01-commits.md) + [`03-seguridad.md`](03-seguridad.md) |
| rama, worktree, `merge`, `rebase`, `push`, PR, CI | [`02-ramas-integracion.md`](02-ramas-integracion.md) + [`03-seguridad.md`](03-seguridad.md) |
| `restore`, `reset`, `clean`, `stash`, `fetch --prune`, borrar refs | [`03-seguridad.md`](03-seguridad.md) |

## Precedencia

1. Instrucción explícita del usuario en la sesión.
2. [`AGENTS.md`](../../AGENTS.md) (lanes, secretos, verificación, despliegue
   del bot).
3. Estos documentos.

Esta política fija formato y procedimiento; **no concede permisos**. Commit,
merge, push, borrado o despliegue siguen necesitando autorización.
