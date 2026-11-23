# Ramas, worktrees e integración

## Ramas

- Nombre `<tipo>/<area>-<descripcion>` en minúsculas ASCII con guiones:
  `fix/bot-lock-eperm`, `android/sprint-7`, `docs/politica-git`.
- Sin IDs de sesión ni nombres de agente en ramas que se integren en
  `main`. Los worktrees internos de las herramientas (pools de agentes)
  quedan fuera.
- No renombrar ramas activas ni ajenas.

## Trabajo con varios agentes

1. Una rama (o worktree) por tarea.
2. Respetar los lanes de `AGENTS.md`; `packages/**` solo de forma aditiva y
   coordinada.
3. Nunca dos agentes editando el mismo fichero a la vez.
4. Antes de mutar: comprobar directorio, rama, base y `git status`.
5. Nunca `stash`, `reset` o `restore` para quitar de en medio trabajo ajeno.

Worktrees: `git worktree add ../bus-dbus-<tarea> -b <rama> main`. Tras
integrar, `git worktree remove <ruta>` solo si está limpio; `--force` nunca
sin confirmación.

## Integración en `main`

Se integra en local y se publica con `push` (no hay PRs ni protección de
ramas: el repo es privado en plan gratuito). El método se elige por cambio:

| Situación | Método |
|---|---|
| Rama con varios commits con sentido propio (sprint, auditoría) | `git merge --no-ff` con mensaje `merge: <desc> (<rama>)` |
| Rama pequeña o con commits WIP | squash; el commit resultante sigue `01-commits.md` |
| Cambio aislado de uno o dos commits | commit directo en `main` |

- Sin `rebase` sobre ramas compartidas ni ya publicadas.
- Antes de integrar: diff completo contra la base, sin secretos, datos
  personales ni binarios no previstos, y la verificación de `AGENTS.md` del
  área en verde.
- Si algún día se usan PRs: título con formato de `01-commits.md`; cuerpo
  con Objetivo, Cambios, Validación (comandos ejecutados), Riesgos y método
  de integración.
- **`push` no despliega el bot**: sigue la regla 9 de `AGENTS.md`.

## CI y automatización

- `ci.yml`: `pnpm install --frozen-lockfile` + `pnpm verify` (lint,
  typecheck y tests de `apps/bot` y `packages/**`). Los tests de integración
  usan la API real de Dbus; si cae, el fallo se reporta, no se silencia.
- `android.yml`: `./gradlew check assembleDebug` con filtro de rutas.
- La PWA congelada (ADR 009) no se compila ni se despliega desde CI.
- Sin commitlint ni hooks de mensaje por ahora: la revisión es manual. Si
  aparecen errores recurrentes, evaluar un chequeo opcional y probado; no
  instalar hooks ni cambiar `core.hooksPath` sin acuerdo.
