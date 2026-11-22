# Inventario de UI y pantallas — bus-dbus

Índice y alcance del inventario de superficies de usuario del proyecto. Documento
derivado del código y de los wireframes existentes; `AGENTS.md` sigue siendo la
fuente de verdad del proyecto.

Última revisión: 2026-11-22.

## 1. Alcance

El proyecto tiene **dos superficies de usuario** con naturezas distintas:

| Superficie | Paquete | Estado | Tipo de UI |
|---|---|---|---|
| Bot de Telegram | `@dbus/bot` (`apps/bot/`) | ✅ Activo, datos reales | Mensajes Markdown + inline keyboards |
| PWA / móvil | `@dbus/mobile` (`apps/mobile/`) | 🔧 Sprint 3 bloqueado (TS/lint) | React + wouter, Tailwind 4 |

Convención de este inventario: en el bot, una «pantalla» es un **estado de
conversación** (un mensaje enviado o editado junto a su teclado inline). En la
PWA, una pantalla es una **ruta** del router más sus estados visuales.

Fuentes primarias:

- `apps/bot/src/handlers.ts`, `apps/bot/src/alerts.ts`, `apps/bot/src/arrival.ts`
- `apps/mobile/src/App.tsx`, `apps/mobile/src/pages/`, `apps/mobile/src/components/`
- `apps/mobile/src/i18n/locales/es.json`, `eu.json`
- `docs/02-wireframes/` (9 wireframes de móvil, 2 flujos, 1 librería de componentes)

## 2. Documentos del inventario

| Doc | Contenido |
|---|---|
| [`02-bot-telegram.md`](02-bot-telegram.md) | Comandos, estados de conversación, textos exactos, botones, callbacks y avisos push del bot. |
| [`03-pwa.md`](03-pwa.md) | Shell, rutas, detalle por pantalla, pantallas solo diseñadas (onboarding, push, widget) e i18n. |
| [`04-brechas.md`](04-brechas.md) | Diferencias wireframe ↔ código, datos falsos y funcionalidad no implementada. |

## 3. Mapa de superficies

```text
bus-dbus
├── Bot de Telegram ................ apps/bot
│   ├── Comandos (/start, /parada, /aviso, /buscar, /linea, /lineas,
│   │   /lista, /borrar, /fav, /actualizar, /estado, /help)
│   ├── Menú inline principal + 7 entradas
│   ├── Flujos: llegadas · avisos · búsqueda · líneas · favoritos
│   └── Push: scheduler de avisos (1 min) → mensaje al chat
└── PWA / móvil .................... apps/mobile
    ├── Shell: Header · ToastContainer · main pb-20
    ├── Rutas: / · /stops · /stop/:stopId · /alert/new ·
    │   /alert/:alertId/edit · /alerts · /settings · /offline
    ├── Overlay global: sin conexión
    └── Diseño sin implementar: onboarding · push recibida · widget/lockscreen
```

## 4. Flujos principales

### 4.1 Consultar llegadas

- **Bot:** `/parada <codigo> [linea]` → «🔍 Consultando datos en tiempo real...» →
  lista de hasta 8 llegadas → botones *Refrescar / Crear aviso aquí / Guardar
  parada / Guardar línea / Menú*.
- **PWA:** `/stops` (cercanas, búsqueda o favoritas) → `/stop/:stopId` →
  «Próximas llegadas» (hasta 5, filtradas a ≤ 60 min).

### 4.2 Crear aviso

- **Bot:** menú `🔔 Crear aviso` o `/aviso <parada> <linea> <minutos>` →
  parada → línea → minutos (2/3/5/8/10/15) → confirmación con ID y ETA.
- **PWA:** `/alert/new` (o `/alert/:alertId/edit`) → línea, antelación 1–30 min,
  días, recurrencia, silencio nocturno y vista previa → `/alerts`.

### 4.3 Descubrir paradas y líneas

- **Bot:** `/buscar <texto>` → resultados parada/línea → destinos → paradas →
  llegadas. `/lineas` lista todas las líneas paginada (10/página);
  `/lineas <parada>` da las líneas de una parada.
- **PWA:** pestañas *Paradas cerca de ti · Buscar · Favoritas* en `/stops`.

### 4.4 Favoritos

- **Bot:** parada completa (`line_id=''`), parada+línea y línea favorita.
  Dedup por `(chat, parada, línea)`. Ver, borrar y navegar a llegadas desde
  el listado inline.
- **PWA:** botón ★ en `/stops` y `/stop/:stopId` (actualmente solo feedback
  visual: ver [`04-brechas.md`](04-brechas.md#b12)).

### 4.5 Aviso disparado (push)

```text
Scheduler (cada minuto)
  → getActiveAlerts() (máx. 7 días de antigüedad)
  → agrupar por parada → getNextArrivals(stopId)
  → findMatchingArrival({ lineId, destination, minutesAhead })
  → sendMessage(chatId, "🔔 ¡Aviso! L5 llega en 3 min")
  → marcar aviso inactivo
```

En la PWA el equivalente (notificación nativa, banner in-app, actions y widget)
existe solo en wireframes; ver [`03-pwa.md`](03-pwa.md#5-pantallas-solo-diseñadas).

### 4.6 Sin conexión

- **Bot:** no aplica: la API se consulta en vivo y el mensaje lo dice
  (`⚠️ No se pudo consultar la API de Dbus`).
- **PWA:** `Offline.tsx` se monta como overlay global cuando
  `!navigator.onLine && !rtData`, ocultando `Header`.

## 5. Catálogo de componentes (PWA)

| Componente | Archivo | Variantes / props | Uso principal |
|---|---|---|---|
| `Button` | `components/ui/Button.tsx` | `primary · secondary · ghost · danger`; `sm · md · lg`; `leftIcon`, `rightIcon`, `loading`, `fullWidth` | Acciones de todas las pantallas; alto mínimo 48 px |
| `Card` | `components/ui/Card.tsx` | `padding: none · sm · md · lg`; `hoverable` | Contenedores de llegadas, alertas, ajustes y estados vacíos |
| `Chip` | `components/ui/Chip.tsx` | `day · line · recurrence`; `selected` | Días, líneas, recurrencia, tema e idioma |
| `Badge` | `components/ui/Badge.tsx` | `rt · scheduled · occupancy-low/medium/high · silenced · default` | Estado en vivo/horario, ocupación y silencio |
| `Input` | `components/ui/Input.tsx` | `label`, `error`, `helperText`, iconos, `fullWidth` | Búsqueda de paradas y campos de hora |
| `Modal` | `components/ui/Modal.tsx` | `sm · md · lg`, portal, `Escape`, bloqueo de scroll | Diálogos (genérico) |
| `BottomSheet` | `components/ui/BottomSheet.tsx` | portal, `Escape`, `max-h-[85vh]` | Hojas inferiores (genérico) |
| `Skeleton` | `components/ui/Skeleton.tsx` | `circular · text` | Carga en `/stops` y `/stop/:stopId` |
| `Spinner` | `components/ui/Spinner.tsx` | — | Indicador de carga |
| `ToastContainer` | `components/ui/ToastContainer.tsx` | `success · error · info · warning`; 4 posiciones; 5 s | Feedback de acciones (guardar, borrar, posponer) |
| `Header` | `components/layout/Header.tsx` | logo, campana con contador, ajustes, menú móvil | Shell persistente |

No existe `BottomNav`, aunque `App.tsx` reserva `pb-20` para una barra inferior.

## 6. Design tokens y accesibilidad

Tokens declarados en el wireframe
[`components/component-library.excalidraw`](../02-wireframes/components/component-library.excalidraw):

| Grupo | Tokens |
|---|---|
| Color | `--color-primary #0066CC`, `--primary-hover #0052A3`, `--success #16A34A`, `--warning #F59E0B`, `--error #DC2626` |
| Color (modo claro/oscuro) | `--bg #FFFFFF / #0F172A`, `--surface #FFFFFF / #1E293B`, `--border #E2E8F0 / #334155`, `--text #0F172A / #F1F5F9`, `--text-muted #64748B / #94A3B8` |
| Radios | `sm 6px`, `md 10px`, `lg 16px` |
| Sombras | `sm 0 1px 2px`, `md 0 4px 6px`, `lg 0 10px 15px` |
| Espaciado | `1:4`, `2:8`, `3:12`, `4:16`, `5:24`, `6:32` |
| Tipografía | `--font-sans system-ui`, `--font-mono ui-monospace` |

Reglas de accesibilidad presentes en el código: objetivos táctiles mínimos de
48×48 px (`Button`, `Chip`, `Input`, iconos del `Header`), `aria-label` en
controles iconográficos, `aria-pressed` en chips y toggles, `role="list"` /
`listitem` en listados, `role="status"` en skeletons, `role="dialog"` /
`aria-modal` en `Modal`, `BottomSheet`, `Offline` y el confirm de caché, y
clases `safe-top` / `safe-bottom` para áreas seguras.

## 7. Convenciones de contenido

- Idiomas: español (`es`, por defecto) y euskera (`eu`), con claves en
  `apps/mobile/src/i18n/locales/`.
- Texto externo interpolado en Markdown de Telegram: siempre `escapeMd()`.
- Los datos de llegadas son reales (API JSON de Dbus). Los valores de ejemplo
  de los wireframes (L5 → Amara, Loiola, 12,50 €) son ilustrativos.
- El bot no persiste ubicación del usuario; la PWA usa geolocalización efímera
  en sesión.
