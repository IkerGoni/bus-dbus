package eus.busdonostia.app.domain

import java.time.LocalTime
import java.time.format.DateTimeParseException

/*
 * A06 · Configurar aviso: borrador, validación, presets y dedup (02-domain-contracts.md §1).
 * Puro: sin Android. La UI solo pinta y llama a estas funciones.
 */

/** Chips rápidos de antelación, sincronizados con el slider. */
val QUICK_MINUTES = listOf(2, 3, 5, 8, 10, 15)
val MINUTES_RANGE = 1..30

/** Franja máxima de un aviso recurrente. */
const val MAX_WINDOW_MINUTES = 180

/** Presets de días ISO (1=Lun .. 7=Dom): Laborables, Diario, Fines de semana. */
enum class DaysPreset(val days: Set<Int>) {
    WEEKDAYS(setOf(1, 2, 3, 4, 5)),
    DAILY((1..7).toSet()),
    WEEKEND(setOf(6, 7)),
}

/** Preset que coincide exactamente con [days], o null si es una selección propia. */
fun presetOf(days: Set<Int>): DaysPreset? = DaysPreset.entries.firstOrNull { it.days == days }

data class AlertDraft(
    val stopId: String? = null,
    val stopName: String? = null,
    val lineId: String? = null,
    /** null = cualquier destino. */
    val destination: String? = null,
    val minutesAhead: Int = 5,
    val frequency: AlertFrequency = AlertFrequency.ONCE,
    val daysOfWeek: Set<Int> = DaysPreset.WEEKDAYS.days,
    val windowFrom: String? = null,
    val windowTo: String? = null,
)

enum class DraftError { NO_STOP, NO_LINE, MINUTES_OUT_OF_RANGE, NO_DAYS, NO_WINDOW, EMPTY_WINDOW, WINDOW_TOO_LONG }

fun parseClock(value: String?): LocalTime? =
    try {
        value?.let(LocalTime::parse)
    } catch (_: DateTimeParseException) {
        null
    }

/** Duración de la franja en minutos; cruza medianoche si `to < from` (`23:30–00:30` = 60). */
fun windowMinutes(from: LocalTime, to: LocalTime): Int {
    val diff = (to.toSecondOfDay() - from.toSecondOfDay()) / 60
    return if (diff < 0) diff + 24 * 60 else diff
}

/** Errores que impiden guardar; vacío = se puede guardar. */
fun validateDraft(draft: AlertDraft): List<DraftError> = buildList {
    if (draft.stopId.isNullOrBlank()) add(DraftError.NO_STOP)
    if (draft.lineId.isNullOrBlank()) add(DraftError.NO_LINE)
    if (draft.minutesAhead !in MINUTES_RANGE) add(DraftError.MINUTES_OUT_OF_RANGE)
    if (draft.frequency == AlertFrequency.RECURRING) {
        if (draft.daysOfWeek.none { it in 1..7 }) add(DraftError.NO_DAYS)
        val from = parseClock(draft.windowFrom)
        val to = parseClock(draft.windowTo)
        when {
            from == null || to == null -> add(DraftError.NO_WINDOW)
            from == to -> add(DraftError.EMPTY_WINDOW)
            windowMinutes(from, to) > MAX_WINDOW_MINUTES -> add(DraftError.WINDOW_TOO_LONG)
        }
    }
}

/**
 * Regla a guardar. Guardar vuelve a armar el aviso: activo, vigila desde [nowMs]
 * (ONCE caduca a los 7 días desde aquí) y sin posponer ni disparo previo.
 * ONCE no guarda días ni franja.
 */
fun AlertDraft.toRule(id: String, nowMs: Long): AlertRule {
    val recurring = frequency == AlertFrequency.RECURRING
    return AlertRule(
        id = id,
        stopId = checkNotNull(stopId),
        stopName = stopName ?: stopId,
        lineId = normalizeLine(checkNotNull(lineId)),
        destination = destination,
        minutesAhead = minutesAhead.coerceIn(MINUTES_RANGE),
        frequency = frequency,
        daysOfWeek = if (recurring) daysOfWeek.filter { it in 1..7 }.toSet() else emptySet(),
        windowFrom = if (recurring) windowFrom else null,
        windowTo = if (recurring) windowTo else null,
        active = true,
        createdAtMs = nowMs,
    )
}

/** Borrador para editar una regla existente (si era ONCE, días por defecto L–V por si cambia a RECURRING). */
fun AlertRule.toDraft(): AlertDraft = AlertDraft(
    stopId = stopId,
    stopName = stopName,
    lineId = lineId,
    destination = destination,
    minutesAhead = minutesAhead,
    frequency = frequency,
    daysOfWeek = daysOfWeek.ifEmpty { DaysPreset.WEEKDAYS.days },
    windowFrom = windowFrom,
    windowTo = windowTo,
)

/** Qué hacer al pulsar Guardar. */
sealed interface SavePlan {
    /** Ya hay otro aviso para (parada, línea): A06 pregunta si actualizarlo. */
    data class NeedsConfirmation(val existing: AlertRule) : SavePlan

    /** Escribir [rule] (insertar si [insert], si no actualizar) y borrar [deleteId] si no es null, en una transacción. */
    data class Write(val rule: AlertRule, val insert: Boolean, val deleteId: String? = null) : SavePlan
}

/**
 * Dedup `(stopId, lineId)`. [editingId] = aviso que se edita (null si es nuevo);
 * [conflict] = otro aviso guardado con la misma (parada, línea), si lo hay.
 * Con [confirmed], el borrador se escribe sobre el aviso existente (conserva su id)
 * y, si se estaba editando otro, ese se borra: queda un único aviso con estos valores.
 */
fun planSave(
    draft: AlertDraft,
    editingId: String?,
    conflict: AlertRule?,
    confirmed: Boolean,
    newId: String,
    nowMs: Long,
): SavePlan {
    val other = conflict?.takeIf { it.id != editingId }
    return when {
        other == null && editingId == null -> SavePlan.Write(draft.toRule(newId, nowMs), insert = true)
        other == null -> SavePlan.Write(draft.toRule(checkNotNull(editingId), nowMs), insert = false)
        !confirmed -> SavePlan.NeedsConfirmation(other)
        else -> SavePlan.Write(draft.toRule(other.id, nowMs), insert = false, deleteId = editingId)
    }
}

/**
 * Activar o pausar con el switch. Reactivar un ONCE (pausado, disparado o caducado)
 * lo vuelve a armar desde [nowMs]; reactivar un RECURRING conserva su historial.
 */
fun AlertRule.withActive(value: Boolean, nowMs: Long): AlertRule = when {
    !value -> copy(active = false)
    frequency == AlertFrequency.ONCE -> copy(active = true, createdAtMs = nowMs, snoozedUntilMs = null, lastTriggeredAtMs = null)
    else -> copy(active = true)
}

/**
 * Posponer 5': `snoozedUntilMs = now + 300_000` y sigue activo: vuelve a avisar pasado
 * ese tiempo. En RECURRING se olvida el disparo de esta franja; si no, quedaría `SILENCED`
 * y no volvería a avisar hasta la franja siguiente.
 */
fun AlertRule.snoozed(nowMs: Long): AlertRule = copy(
    active = true,
    snoozedUntilMs = nowMs + SNOOZE_MS,
    lastTriggeredAtMs = if (frequency == AlertFrequency.RECURRING) null else lastTriggeredAtMs,
)

/**
 * Destinos reales de [lineId] en [stopId]: los de los itinerarios del catálogo que pasan
 * por la parada más los vistos en vivo para esa línea; sin duplicados.
 */
fun lineDestinationsAtStop(catalog: Catalog, stopId: String, lineId: String, arrivals: List<Arrival>): List<String> {
    val line = normalizeLine(catalog.toPublicLineId(lineId, stopId))
    val fromCatalog = catalog.itineraries
        .filter { normalizeLine(it.lineCod) == line && stopId in it.stops }
        .map { it.destination }
    val fromLive = arrivals
        .filter { normalizeLine(catalog.toPublicLineId(it.lineId, stopId)) == line && it.destination.isNotBlank() }
        .map { it.destination.trim() }
    return (fromCatalog + fromLive).distinctBy { it.lowercase() }
}
