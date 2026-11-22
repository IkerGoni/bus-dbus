# Índice de documentación — bus-dbus

Qué doc mirar para cada cosa. Si un doc contradice a
[`AGENTS.md`](../AGENTS.md), manda `AGENTS.md`. Revisado el 2026-11-22 contra
`main` (`b8e08f8`).

## Por pregunta

| Necesito… | Mira |
|---|---|
| Reglas del repo, verificación y despliegue | [`AGENTS.md`](../AGENTS.md) |
| Qué se hizo, qué falta y decisiones tomadas | [`ESTADO.md`](../ESTADO.md) |
| Visión, comandos y configuración del bot | [`README.md`](../README.md) |
| Pantallas, textos y callbacks del bot | [`05-ui/02-bot-telegram.md`](05-ui/02-bot-telegram.md) |
| Por qué Android nativo y sin backend | [ADR 009](03-architecture/adrs/009-android-nativo-mvp.md) |
| Plan y resultado de los sprints Android | [`07-android-mvp/SPRINTS.md`](07-android-mvp/SPRINTS.md) |
| Prompt del próximo sprint (5, publicación) | [`07-android-mvp/prompts/SPRINT-5.md`](07-android-mvp/prompts/SPRINT-5.md) |
| Prompt para rehacer los wireframes Android | [`07-android-mvp/prompts/WIREFRAMES.md`](07-android-mvp/prompts/WIREFRAMES.md) |
| Reglas duras para implementar Android | [`07-android-mvp/docs/00-agent-guardrails.md`](07-android-mvp/docs/00-agent-guardrails.md) |
| Modelos, estados de aviso y matching | [`07-android-mvp/docs/02-domain-contracts.md`](07-android-mvp/docs/02-domain-contracts.md) |
| Spec de pantallas A01–A08 | [`07-android-mvp/docs/03-android-screens.md`](07-android-mvp/docs/03-android-screens.md) |
| Motor de avisos, permisos y red | [`07-android-mvp/docs/04-android-platform.md`](07-android-mvp/docs/04-android-platform.md) |
| Colores, tipografía y componentes | [`07-android-mvp/design-system/DESIGN.md`](07-android-mvp/design-system/DESIGN.md) |
| Toolchain, versiones y datos de la app | [`apps/android/README.md`](../apps/android/README.md) |
| Endpoints de Dbus y riesgo de corte | [`06-api-externa/01-endpoints-y-riesgo.md`](06-api-externa/01-endpoints-y-riesgo.md) |
| Fixtures reales de la API | [`packages/contracts/fixtures/README.md`](../packages/contracts/fixtures/README.md) |
| Por qué la PWA está congelada | [`apps/mobile/KNOWN_ISSUES.md`](../apps/mobile/KNOWN_ISSUES.md) y ADR 009 |

## Vigentes

| Doc | Qué cuenta |
|---|---|
| [`05-ui/README.md`](05-ui/README.md) | Índice de la UI del bot y de la PWA (histórica) |
| [`05-ui/01-inventario-pantallas.md`](05-ui/01-inventario-pantallas.md) | Inventario: vigente para el bot; la parte PWA es histórica |
| [`05-ui/02-bot-telegram.md`](05-ui/02-bot-telegram.md) | UI del bot |
| [`06-api-externa/`](06-api-externa/README.md) | API de Dbus |
| [`07-android-mvp/README.md`](07-android-mvp/README.md) | Paquete de spec Android y orden de lectura |
| [`07-android-mvp/docs/01-inventario-pantallas-mvp.md`](07-android-mvp/docs/01-inventario-pantallas-mvp.md) | Mapa de pantallas y flujos Android |
| [`07-android-mvp/docs/05-migration-from-brechas.md`](07-android-mvp/docs/05-migration-from-brechas.md) | Brechas B1–B16 y en qué sprint se cerraron |
| ADR 009 y los docs de la tabla anterior | — |

## Históricos (no se actualizan)

Llevan arriba una línea «Histórico: ver …» con el doc vigente, salvo los
prompts ejecutados, que se conservan tal cual.

| Doc | Sustituido por |
|---|---|
| ADR [001](03-architecture/adrs/001-frontend-stack.md)–[008](03-architecture/adrs/008-risk-mitigations.md) | ADR 009 y `06-api-externa/` |
| [`04-planning/`](04-planning/milestones.md) (hitos, alcance, métricas, stack de la PWA) | `07-android-mvp/SPRINTS.md` y ADR 009 |
| [`plan/repo-split.md`](plan/repo-split.md), [`plan/ui-audit.md`](plan/ui-audit.md) | `AGENTS.md`, `07-android-mvp/` |
| [`05-ui/03-pwa.md`](05-ui/03-pwa.md), [`05-ui/04-brechas.md`](05-ui/04-brechas.md), `05-ui/wireframes/` | `07-android-mvp/docs/` |
| `07-android-mvp/prompts/SPRINT-1.md` … `SPRINT-4.md` | Prompts ejecutados; resultado en `SPRINTS.md` |
| [`07-android-mvp/prompts/PROMPT-IMPLEMENTACION.md`](07-android-mvp/prompts/PROMPT-IMPLEMENTACION.md), [`STITCH-REMAINING-SCREENS-PROMPTS.md`](07-android-mvp/prompts/STITCH-REMAINING-SCREENS-PROMPTS.md) | `SPRINTS.md`, `03-android-screens.md` |

Los wireframes `.excalidraw` y `stitch-references/` de `07-android-mvp/` son
anteriores a los avisos de un solo uso (aún muestran recurrentes y
«Posponer»/«Descartar»): manda `03-android-screens.md`. Para corregirlos:
[`prompts/WIREFRAMES.md`](07-android-mvp/prompts/WIREFRAMES.md).

`docs/auditoria/` es de la sesión de auditoría de arquitectura.
