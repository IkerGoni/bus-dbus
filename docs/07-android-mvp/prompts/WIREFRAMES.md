# Prompt — Rehacer los wireframes Android (avisos de un solo uso)

Pégalo en una sesión nueva de Claude Code en la raíz de `bus-dbus`.

---

Pon al día los wireframes del MVP Android para que muestren la app tal como
está hoy en `main`. Solo diseño y docs: no se toca código.

**Lee primero, en este orden:** `AGENTS.md`, `ESTADO.md`, `docs/README.md`,
`docs/07-android-mvp/README.md` y la spec, que manda sobre cualquier dibujo:
`docs/07-android-mvp/docs/03-android-screens.md` (A01–A08),
`02-domain-contracts.md` §1 y §3 (aviso de un solo uso, 3 estados),
`04-android-platform.md` §1–§2 (motor y notificación) y
`design-system/DESIGN.md`. Textos exactos: `apps/android/app/src/main/res/values/strings*.xml`
(y `values-eu/`). Cómo es la app de verdad: `docs/07-android-mvp/capturas/sprint-4/`
y `capturas/sprint-3/` (ignora las capturas `*recurrente*` y `a06-selector-hora*`, son de
antes del cambio).

**Aislamiento:**
```bash
git worktree add ../bus-dbus-wireframes -b docs/wireframes-android main
```
Trabaja solo ahí.

**Alcance:** `docs/07-android-mvp/wireframes/**`,
`docs/07-android-mvp/stitch-references/inicio/code.html` y los `.md` que los
citan. **No toques:** código (`apps/**`, `packages/**`), `docs/05-ui/wireframes/`
(PWA, histórico), `docs/auditoria/` ni los prompts ejecutados `SPRINT-1` a
`SPRINT-4`.

**Qué ha cambiado y debe verse (2026-11-22, decisiones de Iker, no reabrir):**
- Avisos **de un solo uso**: vigilan desde que se guardan, avisan una vez y se
  borran. Caducan a los 7 días si no avisan («Caducada»).
- Fuera: recurrentes, días, franja, silencio nocturno, alarmas exactas,
  «Posponer 5'», «Descartar», estados `SNOOZED` (Pospuesta) y `SILENCED`
  (Silenciada). Inicio solo tiene «Pausar» (+ Snackbar «Deshacer»).
- 3 estados de aviso: `TRIGGERED` (⚡ Llegando), `ACTIVE` (● Activa),
  `INACTIVE` (Pausada o Caducada).
- A05: cada llegada tiene ★ (favorita parada+línea, `STOP_LINE`) y 🔔 (abre A06);
  la hora estimada va bajo el destino.
- Notificación del aviso **sin botones**; tap → `dbus://stop/{stopId}?highlightLine={lineId}`.
  Mientras vigila hay una notificación silenciosa «Vigilando L16 en Pio Baroja 15».
- Nombre visible «Bus Donostia», no «Dbus» (la app no es oficial).
- A07: sin caché ni licencia (está sin decidir). Muestra optimización de batería,
  datos reales del catálogo, versión y créditos (Dbus no oficial, OSM ODbL, fuentes OFL).

**Cambios por archivo** (inventario de la sesión de docs; compruébalo tú):

| Archivo | Qué corregir |
|---|---|
| `01-user-flows/01-onboarding-permissions` | Diálogo «¿Permitir que Dbus…?» → nombre de la app |
| `01-user-flows/02-core-loops` | Quitar «Recurrente», «L-V + Silencio 23-07», «Posponer 5'», «Descartar»; paradas con nombre real |
| `02-android-mvp/01-home` | Título «Bus Donostia»; solo «Pausar»; paradas reales |
| `02-android-mvp/02-stops-nearby` | Nombres de parada reales para cada código |
| `02-android-mvp/03-stop-detail` | ★ y 🔔 por fila, hora bajo el destino; quitar «aviso recurrente para mañana» |
| `02-android-mvp/04-alert-config` | Sin selector de frecuencia ni silencio nocturno: texto de un solo uso de la spec; vista previa sin botones |
| `02-android-mvp/05-alerts-list` | Grupos `ACTIVOS` y `PAUSADOS`; fuera Silenciada, `SNOOZED` y `SILENCED` |
| `02-android-mvp/06-lines-and-itinerary` | Nombres y destinos de línea del catálogo real |
| `02-android-mvp/07-settings` | Fuera «Caché local» y «MIT»; añadir batería; datos reales |
| `02-android-mvp/08-push-received` | Notificación sin botones + «Vigilando …» |
| `02-android-mvp/09-offline-empty` | Revisar contra los estados `degraded`/`error` de A05 |
| `components/component-library` | Estados de aviso (3), sin Frecuencia ni botones en el push |
| `stitch-references/inicio/code.html` | Título «Bus Donostia»; quitar el botón «Posponer 5'» |

**Datos de ejemplo:** nada inventado. Cada código de parada, nombre, línea y
destino sale de `apps/bot/data/dbus-catalog.json` (hoy los wireframes tienen
`Boulevard 15 (1)`, pero `1` es Boulevard 9; `214` es Errondo, `12` es Renfe Loiola
y `1281` es un itinerario, no una parada). Usa los de las capturas: 340 Pío
Baroja 10, 344 Pio Baroja 15 (L16 → Igeldo), 1 Boulevard 9 (L28).
Comprueba los ejemplos con un script contra el catálogo.

**Cómo editar los `.excalidraw`:** son JSON. Edita con un script (Python o
Node), no a mano. En cada texto cambia `text` y `originalText` y ajusta el
`width` si crece; respeta `containerId`/`boundElements`; ids nuevos únicos;
sube `version` y cambia `versionNonce` en lo que toques. Conserva el
layout: modifica marcos, no los rehagas desde cero salvo que la pantalla
cambie de estructura (A04, A06).

**Delega:** el inventario de textos por archivo y las ediciones mecánicas con
spec exacta, a subagentes baratos en paralelo. Tú decides el contenido y
verificas.

**Al terminar, en los `.md`:** quita de `docs/07-android-mvp/README.md` el
aviso de que los wireframes y `stitch-references/` están desfasados, y quita
también la nota equivalente de `docs/README.md` (índice, regla «Documentación»
de `AGENTS.md`). Marca como hecho el pendiente de wireframes en `ESTADO.md`.

**DoD:**
- `grep -rnE "Posponer|Descartar|[Rr]ecurrent|[Ss]ilenciad|Pospuesta|SNOOZ|SILENC|[Ff]ranja|L-V|L-D|23[:-]0|\bMIT\b|Caché local"`
  sobre `docs/07-android-mvp/wireframes/` y `stitch-references/` → 0 resultados.
- Los 12 `.excalidraw` cargan como JSON válido, con ids únicos y referencias
  `containerId`/`boundElements` resueltas (script).
- Script de ejemplos contra el catálogo: 0 discrepancias.
- Ningún enlace interno roto en los `.md` (script).
- Lista de qué cambió por archivo y 2–3 capturas PNG de los wireframes
  (exporta o renderiza si tienes herramienta; si no, dilo y pide a Iker que
  los abra en <https://excalidraw.com>).
- Merge a `main`, push y borrado de rama y worktree **solo con autorización
  de Iker en la sesión**; si no, repórtala con
  `git log --oneline main..docs/wireframes-android`.
