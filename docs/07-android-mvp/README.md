# Paquete de UI y Arquitectura — Dbus Donostia (Android MVP v1)

> **Origen y estado en el repo (2026-11-22).** Paquete generado por un agente
> externo, importado en `f6aeefb` y corregido después contra la API real y el
> código (ver `git log -- docs/07-android-mvp` y la tabla §5 de
> `docs/02-domain-contracts.md`). Stack: ADR 009 (Kotlin + Compose, motor de
> avisos local, sin FCM). Sprints 0–4 hechos; plan y estado en
> [`SPRINTS.md`](SPRINTS.md); prompt pendiente:
> [`prompts/SPRINT-5.md`](prompts/SPRINT-5.md).
> Si algo aquí contradice a `AGENTS.md`, manda `AGENTS.md`.
>
> **Avisos de un solo uso (2026-11-22):** los wireframes `.excalidraw`, las
> `stitch-references/` y `prompts/STITCH-REMAINING-SCREENS-PROMPTS.md` aún
> muestran avisos recurrentes y los botones «Posponer 5'» / «Descartar». Son
> diseño anterior: manda [`docs/03-android-screens.md`](docs/03-android-screens.md).

Paquete definitivo y unificado para que los agentes de desarrollo implementen la
aplicación Android **Dbus Donostia (MVP v1)**, cerrando todas las brechas
detectadas (B1–B16) y resolviendo las inconsistencias de las exportaciones
originales de Stitch y los wireframes antiguos de la PWA.

## Qué cambia en este paquete frente a la PWA anterior
1. **Sin mapa embebido en v1:** `Mapa` pasa a ser **`Paradas`** (cercanas por GPS
   calculadas offline con Haversine + buscador universal + favoritas + recientes).
   En Detalle de parada (`A05`) se ofrece `Cómo llegar` vía intent nativo `geo:`.
2. **Navegación inferior fija de 4 destinos:** `Inicio · Paradas · Líneas · Avisos`
   (adiós menú hamburguesa y adiós `pb-20` huérfano).
3. **Feature principal en primer plano («Avísame X min antes»):** accesible en 1
   toque desde cualquier llegada (`🔔`) en Detalle de parada (`A05`), que también
   tiene ★ por llegada (favorita parada+línea). Los avisos son de **un solo uso**:
   `A06` fija la antelación, el aviso salta una vez con una notificación nativa
   sin botones y se borra.
4. **Cero datos falsos:** eliminados saldo Mugi (`12,50 €`), ocupación inventada,
   caché fija (`2.3 MB`), `GTFS v2026.11`, telemetría inventada y catálogo reducido
   de 5 líneas.
5. **Sistema de diseño unificado:** reconciliada la paleta (`#F8FAFC` / `#005BB5`
   / `#059669`), añadido modo oscuro completo, bilingüismo ES/EU, contraste WCAG AA
   y targets táctiles de `48×48dp`.

## Estructura del paquete

```text
docs/07-android-mvp/
├── README.md
├── SPRINTS.md                              # Plan y resultado de cada sprint
├── capturas/sprint-{0,2,3,4}/              # Capturas del emulador por sprint
├── design-system/
│   ├── DESIGN.md                           # Sistema de diseño unificado (Claro + Oscuro)
│   └── design-tokens.json                  # Tokens consumibles por código/scripts
├── stitch-references/
│   ├── inicio/
│   │   ├── code.html                       # HTML corregido (nowrap, 48dp, nav unificada)
│   │   └── screen-stitch-original.png      # Captura original exportada de Stitch
│   └── paradas/
│       ├── code.html                       # HTML corregido (sin serif, sin opacity:0, 48dp)
│       └── screen-stitch-original.png      # Captura original exportada de Stitch
├── docs/
│   ├── 00-agent-guardrails.md              # Reglas duras (cero mocks, 5 estados, i18n, DoD)
│   ├── 01-inventario-pantallas-mvp.md      # Inventario actualizado (Bot Telegram + Android MVP)
│   ├── 02-domain-contracts.md              # Modelos, computeAlertStatus() y computeFreshness()
│   ├── 03-android-screens.md               # Spec ejecutable A01–A08
│   ├── 04-android-platform.md              # Motor local (Foreground Service), permisos, roadmap
│   └── 05-migration-from-brechas.md        # Checklist trazable de cierre B1–B16
├── wireframes/
│   ├── 01-user-flows/                      # 2 flujos actualizados al MVP Android
│   ├── 02-android-mvp/                     # 9 wireframes Excalidraw válidos y corregidos
│   └── components/component-library.excalidraw
└── prompts/
    ├── PROMPT-IMPLEMENTACION.md                # Histórico: prompt general previo a los sprints
    ├── SPRINT-1.md … SPRINT-4.md               # Histórico: prompts ejecutados
    ├── SPRINT-5.md                             # Pendiente: publicación
    └── STITCH-REMAINING-SCREENS-PROMPTS.md     # Histórico: prompts de diseño para Stitch AI
```

## Orden recomendado de uso para agentes
1. Leer `SPRINTS.md`, el prompt del sprint en curso y `docs/00-agent-guardrails.md`.
2. Cargar `design-system/design-tokens.json` y `docs/02-domain-contracts.md`.
3. Implementar las pantallas `A01–A08` siguiendo `docs/03-android-screens.md`,
   tomando `stitch-references/*/code.html` como referencia visual exacta.
4. Conectar notificaciones y permisos según `docs/04-android-platform.md`.
5. Verificar cierre completo en `docs/05-migration-from-brechas.md`.
