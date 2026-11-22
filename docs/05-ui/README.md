# Paquete de UI — bus-dbus

Documentación de las dos superficies de usuario y sus wireframes. Este
directorio es la unidad de exportación para diseñar la interfaz por encargo:
todo lo necesario está aquí, sin depender de `docs/02-wireframes/` (ruta
retirada). Copiar este directorio completo.

| Documento | Contenido |
|---|---|
| [`01-inventario-pantallas.md`](01-inventario-pantallas.md) | Índice de superficies, flujos, catálogo de componentes, tokens y convenciones. |
| [`02-bot-telegram.md`](02-bot-telegram.md) | Bot: comandos, estados de conversación, textos, botones y callbacks. |
| [`03-pwa.md`](03-pwa.md) | PWA: shell, rutas, detalle por pantalla, pantallas solo diseñadas e i18n. |
| [`04-brechas.md`](04-brechas.md) | Diferencias wireframe ↔ código, datos falsos y funcionalidad no implementada. **Leer antes de diseñar.** |

## Wireframes (`wireframes/`, 12 archivos Excalidraw)

| Grupo | Archivos |
|---|---|
| `01-user-flows/` | `01-onboarding-permissions`, `02-core-loops` |
| `02-mobile-web/` | `01-home`, `02-stop-selector`, `03-stop-detail`, `04-alert-config`, `05-alerts-list`, `06-settings`, `07-push-received`, `08-widget-lockscreen`, `09-offline-empty` |
| `components/` | `component-library` (tokens, botones, chips, badges, cards, feedback) |

Los `.excalidraw` son JSON válido: se abren en <https://excalidraw.com> o con
`File → Open` en Excalidraw.

## Notas para el diseño

- Los valores de ejemplo de los wireframes (L5 → Amara, Loiola, `12,50 €`,
  `2.3 MB`, `GTFS estático v2026.11`) son **ilustrativos**, no datos reales.
  No trasladarlos al producto: ver [`04-brechas.md`](04-brechas.md).
- Onboarding, push recibida y widget/lockscreen existen **solo como
  wireframe** (sin ruta ni componente en `apps/mobile/`).
- El bot usa datos reales en vivo; la PWA tiene el build bloqueado por errores
  preexistentes. El estado vigente manda en [`../../AGENTS.md`](../../AGENTS.md).
