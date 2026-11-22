# Milestones & Timeline Overview — Dbus Mobile Web App

## Versión
v0.1 — Sprint 0 output | 2026-11-22

---

## Milestones (M1-M6)

| Milestone | Sprint | Objetivo | Criterio de Entrada | Criterio de Salida (Go) | Buffer |
|-----------|--------|----------|---------------------|-------------------------|--------|
| **🎯 M1: Planning Complete** | 0-2 | Scope, stack, wireframes, ADRs firmados | Investigación completada | 3 planning docs + 12 wireframes + 8 ADRs en repo | 0 días |
| **🏗️ M2: PWA Shell Ready** | 3 | Vite+React+TS+Tailwind, SW, routing, i18n, CI | M1 done | `npm run build` pasa, Lighthouse >90, preview deploy funciona | 2 días |
| **⚡ M3: Real-time Engine** | 4 | GTFS-RT parser (3 feeds), IndexedDB cache, Web Worker | M2 done | Parse 3 feeds/30s <100ms main thread, cache hit >90%, fallback scheduled funciona | 3 días |
| **🔔 M4: Push Notifications** | 5 | Web Push VAPID, config granular, iOS fallback | M3 done | Delivery rate >95% (permiso concedido), silencioso nocturno, recurrencia, preview push | 3 días |
| **🧪 M5: Beta Launch** | 6 | 20 testers, 2 semanas, métricas Go/No-Go | M4 done | Retención D7 >40%, 0 crash críticos, Lighthouse >90, bundle <50MB | 5 días |
| **🚀 M6: Production Release** | 6+ | Deploy prod, docs handoff, monitoring activo | M5 Go | Uptime >99.5%, alertas configuradas, RUNBOOK.md, CONTRIBUTING.md | 2 días |

---

## Dependencias Críticas
- **M2 → M3**: PWA shell debe estar estable antes de añadir worker + parsing
- **M3 → M4**: RT data + cache son prerequisito para scheduler de alertas
- **M4 → M5**: Push debe funcionar end-to-end antes de beta
- **M5 → M6**: Solo si TODOS los KPIs Go se cumplen

---

## Estimación de Esfuerzo (días laborables)

| Sprint | Días núcleo | Buffer | Total | Notas |
|--------|-------------|--------|-------|-------|
| Sprint 0 (Planning) | 3 | 0 | 3 | ✅ DONE |
| Sprint 1 (Wireframes) | 4 | 1 | 5 | ✅ DONE (paralelo con Sprint 2) |
| Sprint 2 (ADRs) | 4 | 1 | 5 | ✅ DONE (paralelo con Sprint 1) |
| Sprint 3 (PWA Shell) | 6 | 2 | 8 | Setup Vite, Tailwind, SW, routing, i18n, CI, lint, test |
| Sprint 4 (RT Engine) | 7 | 3 | 10 | Worker, protobufjs, zod, IndexedDB, merge VP+TU, fallback |
| Sprint 5 (Push) | 6 | 3 | 9 | VAPID, subscription, scheduler, granular config, iOS fallback |
| Sprint 6 (Beta/Launch) | 8 | 5 | 13 | Onboarding testers, 2 sem beta, fixes, docs, deploy |

**Total estimado: ~53 días laborables (~11 semanas desde inicio Sprint 3)**

---

## Owner por Sprint
| Sprint | Owner | Perfil |
|--------|-------|--------|
| Sprint 0 | Iker | Planner/Stakeholder |
| Sprint 1 | Designer | Excalidraw wireframes |
| Sprint 2 | Architect | ADRs técnicos |
| Sprint 3 | Developer | PWA shell, CI/CD, components |
| Sprint 4 | Developer | RT engine, worker, parsing |
| Sprint 5 | Developer | Push, scheduler, functions |
| Sprint 6 | QA + Orchestrator | Beta, polish, deploy, docs |

---

## Riesgos de Timeline (mitigados en ADR 008)
- **R2 (parse perf)** → buffer +3 días en Sprint 4
- **R3 (SW iOS)** → buffer +3 días en Sprint 5
- **R1 (iOS push)** → buffer +5 días en Sprint 6
- **Scope creep (R8)** → parking lot v2 estricto, solo MVP en sprints 3-5

---

## Criterios de Transición Sprint N → N+1
1. **Code complete**: Features del sprint implementadas + tests pasan
2. **CI green**: lint, typecheck, unit, e2e en GitHub Actions
3. **Preview deploy**: Funciona en Cloudflare Pages preview
4. **Documentación**: ADR actualizado si hay cambios, README actualizado
5. **Revisión rápida**: 30 min sync con stakeholder (tú)

---

## Timeline Visual (Semanas desde hoy)

```
Semana 1-2:  ████ Sprint 0,1,2 (DONE)
Semana 3-4:  ████████ Sprint 3 (PWA Shell)
Semana 5-6:  ████████████ Sprint 4 (RT Engine)
Semana 7-8:  ██████████ Sprint 5 (Push)
Semana 9-10: ████████████████████ Sprint 6 (Beta 2 sem)
Semana 11:   ████ Sprint 6 (Polish + Deploy)
```

---

## Fechas Clave Relativas (desde hoy 2026-11-22)
- **M1 (Planning Done)**: ✅ HOY
- **M2 (PWA Shell)**: ~2026-12-06 (+2 semanas)
- **M3 (RT Engine)**: ~2026-12-20 (+4 semanas)
- **M4 (Push Ready)**: ~2026-01-03 (+6 semanas)
- **M5 (Beta Go/No-Go)**: ~2026-01-17 (+8 semanas)
- **M6 (Prod Release)**: ~2026-01-24 (+9 semanas)

*Buffers incluidos. Fines de semana no laborables.*

---

## Notas
- Fechas son estimaciones con buffers; revisar en cada revisión de sprint
- Si Sprint 3-5 van más rápido, beta puede empezar antes
- M5 es gate duro: NO pasar a M6 sin TODOS los KPIs Go
- Documentación (RUNBOOK, CONTRIBUTING) se escribe en paralelo durante Sprints 3-5
