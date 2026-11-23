# Seguridad Git

## Antes de cualquier mutación

1. **Qué cambia:** índice, árbol de trabajo, rama, historial, refs,
   worktree o remoto.
2. **Dónde estoy:** directorio, rama, base y cambios pendientes, comprobado
   con comandos de lectura.
3. **Trabajo ajeno:** cambios sin commitear, lanes y ficheros compartidos.
   Hacer stage solo de lo propio.
4. **Autorización:** la política no la concede; publicar, integrar en
   `main`, borrar o desplegar la necesita.
5. **Evidencia:** qué verificación real respalda el cambio.

## Prohibido sin confirmación específica

```text
git push --force / --force-with-lease
git reset --hard, git clean -fdx, git restore sobre cambios no propios
git rebase -i / filter-repo sobre historial publicado
git worktree remove --force, borrar ramas o tags ajenos
cambiar visibilidad del repo o configuración de GitHub
```

La lista no es exhaustiva: cualquier comando o API con efecto equivalente
cuenta igual.

## Secretos y datos

- Nunca en commits: tokens, `.env`, keystores, `*.db` con datos reales,
  logs con `chat_id`, rutas privadas identificables. Reglas de `AGENTS.md`.
- Borrar un secreto en un commit nuevo **no lo quita del historial**:
  parar la publicación, rotar la credencial y pedir decisión sobre el
  saneamiento del historial.

## Identidad y resultados

- No alterar autor, correo ni fechas para aparentar otra autoría.
- No reportar tests, CI, push o despliegue como hechos sin evidencia
  observable.
- Antes de hacer público el repo: auditar todas las ramas y tags (secretos,
  datos personales, licencias), no solo `HEAD`.
