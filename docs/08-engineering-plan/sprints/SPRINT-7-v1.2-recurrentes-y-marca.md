# Sprint 7 — Avisos Recurrentes Diarios y Delight de Marca (`v1.2.0`)

**Objetivo:** Lanzar los avisos recurrentes diarios ("Lunes a Viernes a las 08:00") con consumo < 0,5% de batería/día mediante el patrón híbrido `AlarmManager` + vigilia corta, y añadir personalidad visual (mascota Canvas, partículas *in-app* y mejoras táctiles en `AlertsScreen`/`AlertEditScreen`).

**Esfuerzo estimado:** ~25 horas · **Riesgo:** Medio · **Pre-requisito:** `v1.1.0` estable.

---

## Bloque A: Avisos Recurrentes Diarios (Prioridad Funcional)

### 1. Dominio Puro (`domain/RecurringSchedule.kt`)
- [ ] Modelar `ScheduledAlertRule` (`id`, `stopId`, `lineId`, `destination`, `minutesAhead`, `targetHour`, `targetMinute`, `daysOfWeekMask`, `enabled`).
- [ ] Implementar función pura `nextWakeupEpochMs(...)` con cobertura exhaustiva en tests unitarios JVM:
  - Mismo día antes de la hora vs después de la hora.
  - Salto de fin de semana (Viernes → Lunes).
  - Cambios de horario verano/invierno (CET ↔ CEST en Europa/Madrid).
  - Regla desactivada o máscara vacía.

### 2. Persistencia Room (Migración v2 → v3 NO Destructiva)
- [ ] Crear `ScheduledAlertEntity` y `ScheduledAlertDao` (tabla `scheduled_alerts`).
- [ ] Añadir a `AlertEntity` columnas opcionales `expiresAtMs: Long? = null` y `sourceScheduleId: Long? = null` para distinguir instancias temporales despertadas por una programación diaria.
- [ ] Escribir `MIGRATION_2_3` explícita, exportar esquema en `app/schemas/` y crear test de migración en `AppDatabaseTest` (conservar favoritos y avisos puntuales existentes sin pérdida de datos).

### 3. Planificador y Receptores (`engine/AlarmScheduler.kt`)
- [ ] Programar despertares a `targetArrivalTime - recurringWakeupBeforeMin` (25 min desde `config.json`) usando `setExactAndAllowWhileIdle()` si `canScheduleExactAlarms() == true`, o `setWindow(..., windowLength = 5 min)` como fallback sin requerir permisos especiales intrusivos.
- [ ] Implementar `RecurringAlertReceiver` (usando `goAsync()`): inserta instancia temporal en `alerts` con `expiresAtMs = target + recurringExpiryAfterMin` (25 min), llama a `ensureMonitoring()` y programa el siguiente día activo.
- [ ] Implementar `BootCompletedReceiver` (`BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `TIMEZONE_CHANGED`, `TIME_SET`) para restaurar todas las alarmas activas tras reinicios.
- [ ] Actualizar `AlertEngine.evaluateTick()` para expirar instancias temporales al superar `expiresAtMs` y transicionar limpiamente a `STOP_IDLE`.

### 4. UI de Avisos Recurrentes (`AlertsScreen` + `AlertEditScreen`)
- [ ] Añadir selector de modalidad en `AlertEditScreen`: *"Solo hoy (puntual)"* vs *"Repetir días"* (chips `L M X J V S D` + selector de hora habitual de llegada).
- [ ] Mostrar sección diferenciada o indicador claro de recurrencia en `AlertsScreen` con `Switch` de activación/pausa inmediata.

---

## Bloque B: Marca y Delight en Compose Nativo (0 KB de Lottie)

### 5. Mascota en Canvas (`ui/components/Mascot.kt`)
- [ ] Dibujar mascota geométrica nativa en Canvas con 5 estados (`Idle`, `Happy`, `Thinking`, `Celebrating`, `Sleeping`) y animaciones suaves condicionadas a `!reducedMotion && !LocalInspectionMode.current`.
- [ ] **Restricción UX obligatoria:** Integrar únicamente en **Onboarding** y **estados vacíos** (sin favoritos, sin avisos, `NO_SERVICE` nocturno en `StopDetailScreen`). Nunca en `HomeScreen` con contenido activo.

### 6. Microinteracciones Avanzadas en Avisos
- [ ] **Wizard direccional en `AlertEditScreen`:** Reemplazar transiciones estáticas entre los 4 pasos (`Parada → Línea → Destino → Antelación`) por `AnimatedContent` direccional (desliza a izquierda al avanzar, a derecha al retroceder) + barra de progreso superior con `animateFloatAsState` + háptica en el stepper (`1–30 min`).
- [ ] **Swipe-to-dismiss en `AlertCard` (`AlertsScreen`):** Permitir deslizar para borrar con fondo de confirmación, háptica `LongPress` y `Snackbar` inmediato con botón *"Deshacer"* (restaurando la regla y reprogramando `AlarmScheduler` si era recurrente).
- [ ] **Partículas Canvas (`800 ms`):** Disparar animación ligera de partículas (`CelebrationParticles.kt`, `0` allocaciones por frame) únicamente en eventos *in-app* de logro: **primera estrella favorita marcada** o **primer aviso creado** (persistir flag en `SettingsStore` para no resultar repetitivo).

---

## Criterios de Aceptación y Verificación
- `./gradlew check assembleDebug` en verde (incluyendo `StringsParityTest` con nuevas cadenas ES/EU y tests de `MIGRATION_2_3` + `RecurringScheduleTest`).
- Prueba en dispositivo físico con `adb shell dumpsys alarm` verificando que la alarma queda registrada tras reiniciar el dispositivo y que `AlertMonitorService` se apaga al expirar la ventana de salvaguarda.
- Verificación en batería real: servicio activo < 25 min por aviso diario y 0 procesos vivos fuera de ventana.
