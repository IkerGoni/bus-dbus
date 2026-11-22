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

Aún sin capturar: `DATOS` con `tiempos` vacío (en el barrido del 2026-11-22 las
paradas sin buses devolvían `SIN_SERVICIO`).
