# Known issues — @dbus/mobile

> **PWA congelada por [ADR 009](../../docs/03-architecture/adrs/009-android-nativo-mvp.md).**
> Registro del build roto; no arreglar ni borrar sin acuerdo (`AGENTS.md`).

Estado del Sprint 3 tras el split en workspaces. Nada de esto es nuevo: son
los errores preexistentes del PWA, movidos a `apps/mobile/` sin tocar el
código. Se documentan aquí para que CI y las próximas sesiones sepan qué falta
y no se borre UI que funciona.

## Build bloqueado

`pnpm --filter @dbus/mobile build` falla. Orden real de fallo:

1. **CSS (Tailwind 4)** — `src/styles/tailwind.css` usa `@apply btn`, `@apply
   chip`, `@apply badge`. Tailwind 4 solo acepta utilidades en `@apply`, nunca
   clases propias. Hay que reescribirlas como `@utility` o CSS plano en
   `@layer components`.
2. **TypeScript** — `tsc -b` falla con los errores de abajo.

## Errores TS conocidos (`tsc -b`)

- `components/ui/Button.tsx`, `components/ui/Input.tsx`: `leftIcon` /
  `rightIcon` no existen en `JSX.IntrinsicElements` (tipado de `LucideIcon`
  vs componente).
- `components/ui/ToastContainer.tsx`: `useToast` declarado y exportado dos
  veces.
- `lib/db/index.ts`: el store `alerts` no encaja con el `DBSchema` de `idb`.
- `pages/AlertConfig.tsx`, `pages/AlertsList.tsx`, `pages/Home.tsx`,
  `pages/Settings.tsx`, `pages/StopDetail.tsx`: imports `../../store`,
  `../../types`, `../../lib/db` que quedaron un nivel de más (las páginas ya
  viven en `src/pages/`).
- `pages/AlertConfig.tsx`: `useParams()` sin genérico; `effect(fn, [])` — el
  array de deps lo ignora `@preact/signals-react`, se debe borrar.
- `pages/Settings.tsx`: `Check` usado sin importar.
- `pages/StopDetail.tsx`: `Users`, `CreditCard` importados dos veces.

## Datos falsos en UI (no borrar sin sustituir)

`Settings` muestra caché `2.3 MB`, `hace 15s` y `GTFS estático: v2026.11`;
`StopDetail` muestra saldo Mugi `12,50 €` y ocupación; `AlertConfig` usa
líneas hardcodeadas `['5','16','21','25','33']`. Se sustituyen por datos
reales en la fase M3; hasta entonces quedan marcados, no eliminados.

## Fuera de alcance hasta M5

- Push real: `vite-plugin-pwa` usa `GenerateSW` (no hay fichero SW propio) y
  `functions/api/push-send.js` usa `web-push` (necesita crypto de Node, no
  disponible en Workers). Requiere decisión M6.
- `strict: true` en `apps/mobile/tsconfig.json`: activar solo cuando el build
  esté verde.
