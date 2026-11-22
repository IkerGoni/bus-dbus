# 04. Arquitectura Background, Batería y Avisos Recurrentes (Android 15+)

## 1. Diagnóstico Actual (`AlertMonitorService`)
- **Tipo de servicio:** `ForegroundService` con tipo `dataSync`.
- **Naturaleza actual (MVP v1):** Avisos *single-shot* (se disparan en ventana `minutosAhead + 30s`, notifican vía `notifyArrival()` y se borran de Room; caducidad automática a los 7 días).
- **Cuello de botella energético real:** Mantener el servicio vivo en RAM tiene coste despreciable; el consumo proviene de despertar el módem celular 4G/5G en cada llamada HTTP a `http://app.dbus.eus/SSIIMovilWSv2/ws/cons` más el **tail energy** (~10–15 s con la antena en estado de alta potencia tras recibir los bytes).
- **Límite duro de Android 15 (`targetSdk 36`):** Los servicios `dataSync` disponen de un máximo de **6 horas acumuladas en ventanas de 24 horas**. Al superarlo, el sistema invoca `onTimeout()` y mata el servicio.

---

## 2. Fase 1 (Sprint 5.0): Sondeo Adaptativo Ampliado
Con el tope anterior de 2 minutos (`4×`), un aviso creado con 1 hora de antelación realizaba ~30 requests/hora (~2–4 % batería/h en 4G).

Ampliamos `AlertEngine.pollDelayMs` gobernado desde `assets/config.json` (`polling.adaptiveTiers`):

| Gap (`minRestantes - antelación`) | Intervalo | Factor sobre base (30 s) | Peticiones / hora | Ahorro frente a tope 2 min |
| :--- | :--- | :--- | :--- | :--- |
| **`> 30 min`** | **10 min** (`600 s`) | `20×` | 6 req/h | **-80 %** radio móvil |
| **`15 – 30 min`** | **4 min** (`240 s`) | `8×` | 15 req/h | **-50 %** radio móvil |
| **`8 – 14 min`** | **1 min** (`60 s`) | `2×` | 60 req/h | Igual (alta precisión cercana) |
| **`≤ 7 min`** | **30 seg** (`30 s`) | `1×` | 120 req/h | Igual (precisión máxima GPS) |
| **Sin bus / error / sin servicio** | **1 min** (`60 s`) | `2×` | 60 req/h | Backoff controlado + `STOP_NO_SERVICE` |

> **Regla de consolidación por parada:** Si existen múltiples avisos sobre la misma parada, `AlertEngine.stopsToPoll()` agrupa las consultas en una sola llamada HTTP por parada (ya soportado), tomando el intervalo mínimo requerido entre los avisos de esa parada.

---

## 3. Fase 2 (Sprint 7 · v1.2): Avisos Recurrentes Diarios (Patrón Híbrido)
Ejemplo: *"Avísame 5 min antes del 28 en Boulevard, de Lunes a Viernes sobre las 08:00"*.

Mantener `AlertMonitorService` encendido toda la noche agotaría las 6 h de `dataSync` y drenaría la batería. La solución es un **ciclo en dos fases (Hibernación + Vigilia Corta)**:

```text
[08:05 Hoy] ──► Hibernación total (0% batería, sin ForegroundService vivo)
                     │
                     ▼  AlarmManager despierta (targetArrivalTime - 25 min)
[07:35 Mañana] ─► RecurringAlertReceiver crea instancia temporal en vigilancia
                     │  (con expiración automática a targetArrivalTime + 25 min = 08:25)
                     ▼  ensureMonitoring() arranca AlertMonitorService
[07:35–08:00] ──► Sondeo real a Dbus WS (GPS tipo=1.0) → notifyArrival() 5 min antes
                     │
                     ▼  Borra instancia temporal → STOP_IDLE
                        AlarmScheduler reprograma siguiente día activo de la máscara L–D
```

### Componentes a Implementar
1. **Nueva tabla Room (`ScheduledAlertEntity`, migración v2 → v3 NO destructiva):**
   - Mantener `alerts` intacta para avisos *single-shot* (y para las instancias temporales en vigilancia creadas al despertar la alarma, añadiendo opcionalmente un campo `expiresAtMs: Long?` y `sourceScheduleId: Long?` con valor `null` por defecto para conservar 100% compatibilidad).
   - Nueva tabla `scheduled_alerts`:
     - `id: Long` (PK autoincrement)
     - `stopId: String`
     - `lineId: String`
     - `destination: String?`
     - `minutesAhead: Int` (1..30)
     - `targetHour: Int` (0..23)
     - `targetMinute: Int` (0..59)
     - `daysOfWeekMask: Int` (máscara de 7 bits: L=1, M=2, X=4, J=8, V=16, S=32, D=64)
     - `enabled: Boolean`
     - Índice UNIQUE `(stopId, lineId, targetHour, targetMinute)` o validación en dominio para evitar duplicados solapados.

2. **Planificador (`AlarmScheduler` + cálculo puro en `domain/RecurringSchedule.kt`):**
   - Función pura `nextWakeupEpochMs(schedule, nowEpochMs, zoneOffsetMinutes, wakeupBeforeMin)`: calcula el siguiente instante exacto de despertar. Testada a fondo en JVM (cambios de semana, fin de mes, cambio de hora DST en España CET/CEST, hora ya pasada hoy).
   - **Permiso de alarmas exactas en Android 12–16:**
     - **Opción A (Recomendada UX sin fricción):** Usar `AlarmManager.setWindow(RTC_WAKEUP, wakeupMs, 5 * 60_000L, pendingIntent)`. Al despertar 25 minutos antes, una ventana de ±5 minutos no afecta a la precisión del aviso (que sigue siendo al segundo real gracias al sondeo GPS de Dbus), y **evita pedir el permiso especial `SCHEDULE_EXACT_ALARM`** que requiere aprobación en Google Play si la app no es un despertador/calendario.
     - **Opción B:** Si `canScheduleExactAlarms()` es `true`, usar `setExactAndAllowWhileIdle()`, haciendo fallback limpio a `setWindow()` si el usuario no lo concede. *(Combinar B con fallback a A es lo más robusto).*

3. **Receptores del Sistema (`BroadcastReceiver`):**
   - `RecurringAlertReceiver`: despierta, adquiere `goAsync()` para insertar la instancia temporal en Room con `expiresAtMs = targetMs + recurringExpiryAfterMin`, arranca `ensureMonitoring()` y reprograma la siguiente ocurrencia.
   - `BootCompletedReceiver`: escucha `ACTION_BOOT_COMPLETED`, `ACTION_MY_PACKAGE_REPLACED` y `ACTION_TIMEZONE_CHANGED` / `ACTION_TIME_CHANGED` para reprogramar todas las reglas `enabled = true` de `scheduled_alerts`.

4. **Impacto Energético y de Cuota Resultante:**
   - Tiempo activo de `AlertMonitorService`: **15–25 min/día** (~4–7 % de la cuota diaria de 6 h de Android 15).
   - Consumo estimado de batería: **< 0,5 % al día**.
