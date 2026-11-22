# Success Metrics & Measurement Plan — Dbus Mobile Web App

## Versión
v0.1 — Sprint 0 output | 2026-11-22

---

## KPIs Primarios (North Star + Guardrails)

| KPI | Definición | Umbral Go | Frecuencia | Herramienta |
|-----|------------|-----------|------------|-------------|
| **Push Delivery Rate** | % alertas entregadas al dispositivo / alertas programadas (permiso concedido) | >95% | Diaria (beta) / Semanal (prod) | Logs VAPID + client ack (custom event) |
| **Retención D7** | % usuarios beta que reciben ≥1 alerta día 7 | >40% | Semanal | Plausible event `alert_received` + cohort |
| **Lighthouse Performance** | Score performance en CI (mobile emulado) | >90 | Cada PR | GitHub Actions + Lighthouse CI |
| **Cold Start (LCP)** | Largest Contentful Paint p75 en móvil real | <2s | Semanal (beta) / Release | Web Vitals (web-vitals lib) → Plausible/Sentry |
| **Crash-Free Sessions** | % sesiones sin error no capturado | >99.5% | Diaria | Sentry |

---

## KPIs Secundarios (Diagnóstico)

| KPI | Definición | Target | Herramienta |
|-----|------------|--------|-------------|
| **Permiso Notificaciones Concedido** | % instalaciones que aceptan prompt | >60% | Custom event `perm_granted` / `perm_denied` |
| **Parse GTFS-RT Main Thread Block** | ms bloqueados por parse 3 feeds/30s (p95) | <100ms | Performance.mark en Web Worker → Sentry breadcrumb |
| **IndexedDB Cache Hit Rate** | % lecturas servidas desde cache vs red | >90% | Custom metric en SW fetch handler |
| **Bundle Size (gzipped)** | `dist/` total transfer size | <50MB | `vite build --mode production` + `gzip-size` |
| **Time to Interactive (TTI)** | Hasta que UI responde a tap | <3.5s | Lighthouse / Web Vitals |
| **Offline Launch Success** | % arranques sin red que muestran datos | >95% | SW `install` event + custom check |
| **Widget Impressions (iOS/Android)** | Veces widget visto en lock screen | Baseline β | Platform widget API (si disponible) / proxy via app opens |

---

## Plan de Medición

### Fase Beta (Sprint 6 — 20 testers, 2 semanas)

| Semana | Actividad | Responsable |
|--------|-----------|-------------|
| **Pre-beta** | Instrumentar custom events: `alert_scheduled`, `alert_delivered`, `alert_clicked`, `perm_granted`, `perm_denied`, `parse_duration_ms`, `cache_hit` | Developer |
| **Día 1** | Onboarding 20 testers (TestFlight no aplica → link PWA + instrucciones "Añadir a pantalla inicio") | QA/Orchestrator |
| **Día 1-14** | Dashboards diarios: delivery rate, retención, crashes, permisos | QA |
| **Día 7** | Checkpoint retención D7 → go/no-go extensión beta | Orchestrator |
| **Día 14** | Informe final beta + decisión Fase 2 | Orchestrator + Stakeholder |

### Fase Producción (Post-MVP Launch)

| Cadencia | Revisión | Métricas |
|----------|----------|----------|
| **Diaria** | Alertas automáticas | Delivery rate <90%, crash rate >1%, LCP p75 >3s |
| **Semanal** | Dashboard ejecutivo | Retención D7/D30, permisos, bundle size, cache hit |
| **Por Release** | Gate de deploy | Lighthouse >90, bundle <50MB, tests pass, 0 regressions Sentry |

---

## Definición de "Beta Exitoso" (Go para Fase 2)

**TODOS deben cumplirse:**

1. ✅ Push Delivery Rate >95% (mínimo 100 alertas programadas en beta)
2. ✅ Retención D7 >40% (mínimo 8/20 testers activos día 7)
3. ✅ 0 crash críticos no resueltos (Sentry severity=critical)
4. ✅ Lighthouse Performance >90 en build final
5. ✅ Bundle gzipped <50MB
6. ✅ Parse GTFS-RT main thread block p95 <100ms
7. ✅ Al menos 60% testers conceden permiso notificaciones
8. ✅ Feedback cualitativo: ≥4/5 en "utilidad alerta llegada" (encuesta final)

**Si ALGUNO falla** → Análisis causa raíz → Fix en Sprint 6 extendido / hotfix → Re-beta 1 semana.

---

## Instrumentación Técnica (para Developer)

```typescript
// Custom events a enviar a Plausible/Sentry
interface TelemetryEvents {
  'alert_scheduled': { stopId: string; lineId: string; minutesAhead: number; recurrence: string };
  'alert_delivered': { alertId: string; latencyMs: number; platform: 'ios' | 'android' | 'desktop' };
  'alert_clicked': { alertId: string; action: 'open' | 'dismiss' | 'snooze' };
  'perm_granted': { promptContext: 'onboarding' | 'settings' | 'alert_config' };
  'perm_denied': { promptContext: string; isIOS: boolean };
  'parse_duration_ms': { feedsParsed: number; durationMs: number; worker: boolean };
  'cache_hit': { resourceType: 'gtfs-static' | 'gtfs-rt' | 'shell'; hit: boolean };
  'offline_launch': { hadCachedData: boolean; showStaleIndicator: boolean };
}
```

---

## Dashboard Recomendado (Grafana/Plausible/Sentry)

- **Panel 1**: Push Delivery Rate (24h rolling) + alerta si <90%
- **Panel 2**: Retención D1/D7/D30 (cohort beta)
- **Panel 3**: LCP p75 + TTI (Web Vitals)
- **Panel 4**: Crash-free sessions + top errors
- **Panel 5**: Permiso grant/deny ratio por contexto
- **Panel 6**: Parse duration p50/p95 + cache hit rate
