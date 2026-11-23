package eus.busdonostia.app.domain

/*
 * A06 · Configurar aviso: borrador, validación y dedup (02-domain-contracts.md §1).
 * Puro: sin Android ni java.*. La UI solo pinta y llama a estas funciones.
 */

/** Chips rápidos de antelación, sincronizados con el slider. */
val QUICK_MINUTES = listOf(2, 3, 5, 8, 10, 15)

data class AlertDraft(
    val stopId: String? = null,
    val stopName: String? = null,
    val lineId: String? = null,
    /** null = cualquier destino. */
    val destination: String? = null,
    val minutesAhead: Int = 5,
)

enum class DraftError { NO_STOP, NO_LINE, MINUTES_OUT_OF_RANGE }

/** Errores que impiden guardar; vacío = se puede guardar. */
fun validateDraft(
    draft: AlertDraft,
    config: AlertsConfig = AlertsConfig(),
): List<DraftError> = buildList {
    if (draft.stopId.isNullOrBlank()) add(DraftError.NO_STOP)
    if (draft.lineId.isNullOrBlank()) add(DraftError.NO_LINE)
    if (draft.minutesAhead !in config.minMinutesAhead..config.maxMinutesAhead) {
        add(DraftError.MINUTES_OUT_OF_RANGE)
    }
}

/**
 * Regla a guardar. Guardar vuelve a armar el aviso: activo, vigila desde [nowMs]
 * (caduca a los `expirationDays` desde aquí).
 */
fun AlertDraft.toRule(
    id: String,
    nowMs: Long,
    config: AlertsConfig = AlertsConfig(),
): AlertRule = AlertRule(
    id = id,
    stopId = checkNotNull(stopId),
    // Sin catálogo ni live, el nombre queda vacío: nunca guardar el código de poste como nombre.
    // La UI y las notificaciones muestran R.string.stop_unnamed cuando está vacío.
    stopName = stopName?.takeIf { it.isNotBlank() }.orEmpty(),
    lineId = normalizeLine(checkNotNull(lineId)),
    destination = destination,
    minutesAhead = minutesAhead.coerceIn(config.minMinutesAhead..config.maxMinutesAhead),
    active = true,
    createdAtMs = nowMs,
)

/** Borrador para editar una regla existente. */
fun AlertRule.toDraft(): AlertDraft = AlertDraft(
    stopId = stopId,
    stopName = stopName,
    lineId = lineId,
    destination = destination,
    minutesAhead = minutesAhead,
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
    config: AlertsConfig = AlertsConfig(),
): SavePlan {
    val other = conflict?.takeIf { it.id != editingId }
    return when {
        other == null && editingId == null -> SavePlan.Write(draft.toRule(newId, nowMs, config), insert = true)
        other == null -> SavePlan.Write(draft.toRule(checkNotNull(editingId), nowMs, config), insert = false)
        !confirmed -> SavePlan.NeedsConfirmation(other)
        else -> SavePlan.Write(draft.toRule(other.id, nowMs, config), insert = false, deleteId = editingId)
    }
}

/** Activar o pausar con el switch. Reactivar lo vuelve a armar desde [nowMs] (pausado o caducado). */
fun AlertRule.withActive(value: Boolean, nowMs: Long): AlertRule =
    if (value) copy(active = true, createdAtMs = nowMs) else copy(active = false)

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

/**
 * Chips de destino de A06: los de la línea en la parada más el elegido (p. ej. precargado
 * desde una llegada que ya no sale), sin repetir aunque cambien las mayúsculas.
 */
fun destinationChoices(destinations: List<String>, chosen: String?): List<String> =
    (destinations + listOfNotNull(chosen)).distinctBy { it.lowercase() }
