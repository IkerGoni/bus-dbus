# Paquete de UI y Arquitectura — Dbus Donostia (Android MVP v1)

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
   toque desde cualquier llegada (`🔔`) en Detalle de parada (`A05`) y configurable
   en 2 modos (`Solo esta vez` o `Recurrente` con silencio nocturno) en `A06`, con
   notificaciones Android nativas (`Posponer 5'` y `Descartar`).
4. **Cero datos falsos:** eliminados saldo Mugi (`12,50 €`), ocupación inventada,
   caché fija (`2.3 MB`), `GTFS v2026.11`, telemetría inventada y catálogo reducido
   de 5 líneas.
5. **Sistema de diseño unificado:** reconciliada la paleta (`#F8FAFC` / `#005BB5`
   / `#059669`), añadido modo oscuro completo, bilingüismo ES/EU, contraste WCAG AA
   y targets táctiles de `48×48dp`.

## Estructura del paquete

```text
dbus_donostia_android_mvp_ui_package/
├── README.md
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
│   ├── 04-android-platform.md              # Push (FCM / Foreground Service), permisos, roadmap
│   └── 05-migration-from-brechas.md        # Checklist trazable de cierre B1–B16
├── wireframes/
│   ├── 01-user-flows/                      # 2 flujos actualizados al MVP Android
│   ├── 02-android-mvp/                     # 9 wireframes Excalidraw válidos y corregidos
│   └── components/component-library.excalidraw
└── prompts/
    ├── AGENT-ARCHITECTURE-MIGRATION-PROMPT.md  # Prompt maestro para tus agentes de código
    └── STITCH-REMAINING-SCREENS-PROMPTS.md     # Prompts para generar el resto en Stitch AI
```

## Orden recomendado de uso para agentes
1. Leer `prompts/AGENT-ARCHITECTURE-MIGRATION-PROMPT.md` y `docs/00-agent-guardrails.md`.
2. Cargar `design-system/design-tokens.json` y `docs/02-domain-contracts.md`.
3. Implementar las pantallas `A01–A08` siguiendo `docs/03-android-screens.md`,
   tomando `stitch-references/*/code.html` como referencia visual exacta.
4. Conectar notificaciones y permisos según `docs/04-android-platform.md`.
5. Verificar cierre completo en `docs/05-migration-from-brechas.md`.
