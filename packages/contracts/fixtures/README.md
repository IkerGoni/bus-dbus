# Fixtures de contrato

Respuestas **reales** de la API de Dbus, capturadas con `curl` y sin editar.
Las leen los tests de `@dbus/core` (vitest) y de `apps/android` (JUnit) para
garantizar la paridad del dominio (ADR 009). No inventar ni retocar fixtures:
si falta un caso, se captura.

```bash
curl -sS 'http://app.dbus.eus/SSIIMovilWSv2/ws/cons/tiemposParada?codParada=<parada>&idioma=es'
```

| Archivo | Parada | `estado` | Qué cubre | Capturado |
|---|---|---|---|---|
| `tiemposParada-1.json` | 1 Boulevard 9 | `DATOS` | 2 tiempos horario (`tipo=2`), línea 28 con id interno 32, `avisos: null` | 2026-11-22 06:53 |
| `tiemposParada-50-datos.json` | 50 Tolosa 138 | `DATOS` | 3 tiempos GPS (`tipo=1`) | 2026-11-22 13:32 |
| `tiemposParada-340-datos-avisos.json` | 340 Pío Baroja 10 | `DATOS` | tiempos **y** `avisos` a la vez | 2026-11-22 13:32 |
| `tiemposParada-1-sin-servicio.json` | 1 | `SIN_SERVICIO` | `tiempos: null` + motivo en `avisos` | 2026-11-22 13:32 |
| `tiemposParada-566-sin-servicio.json` | 566 | `SIN_SERVICIO` | sin `avisos` | 2026-11-22 13:33 |
| `tiemposParada-99999-no-desc.json` | 99999 (no existe) | `NO DESC` | parada desconocida | 2026-11-22 13:33 |
| `tiemposParada-2-datos-tipo0.json` | 2 Urbieta 12 | `DATOS` | 5 tiempos mezclando `tipo=0` (horario) y `tipo=1` (GPS) | 2026-11-22 18:37 |

Aún sin capturar: `DATOS` con `tiempos` vacío (en el barrido del 2026-11-22 las
paradas sin buses devolvían `SIN_SERVICIO`).

## Casos de dominio (`domain-cases.json`)

No es una respuesta de Dbus: son los casos de paridad TS ↔ Kotlin (ADR 009 §4,
H6 de la auditoría 2026-11). `runs` dice qué lados ejecutan cada sección y
`notApplicable` por qué falta un lado.

| Sección | TS | Android |
|---|---|---|
| `tiemposParada` (los 7 fixtures: `estado` y llegadas) | `packages/core/tests/parity.test.ts` | `DomainParityTest` |
| `matching` (destino, GPS preferido, antelación) | `packages/core/tests/parity.test.ts` | `DomainParityTest` |
| `haversineMeters` | `apps/bot/tests/haversine-parity.test.ts` (no está en `@dbus/core`) | `DomainParityTest` |
| `remainingMinutes`, `freshness` | no aplica: el bot no recalcula contra un reloj | `DomainParityTest` |

Las expectativas de `tiemposParada` salieron de `@dbus/core` y Android las
cumple sin cambios. Si se captura un fixture nuevo, añadir su caso aquí.
