package eus.busdonostia.app.ui.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import eus.busdonostia.app.AppContainer
import eus.busdonostia.app.data.db.toDomain
import eus.busdonostia.app.data.db.toEntity
import eus.busdonostia.app.domain.AlertDraft
import eus.busdonostia.app.domain.AlertsConfig
import eus.busdonostia.app.domain.CatalogStop
import eus.busdonostia.app.domain.DraftError
import eus.busdonostia.app.domain.SavePlan
import eus.busdonostia.app.domain.lineDestinationsAtStop
import eus.busdonostia.app.domain.normalizeLine
import eus.busdonostia.app.domain.planSave
import eus.busdonostia.app.domain.searchStops
import eus.busdonostia.app.domain.stopByCode
import eus.busdonostia.app.domain.stopLineIds
import eus.busdonostia.app.domain.toDraft
import eus.busdonostia.app.domain.validateDraft
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class AlertEditUiState(
    /** Catálogo o aviso a editar aún cargando. */
    val loading: Boolean = true,
    val catalogError: Boolean = false,
    /** Se pidió editar un aviso que ya no existe. */
    val notFound: Boolean = false,
    val editing: Boolean = false,
    val draft: AlertDraft = AlertDraft(),
    /** Nombre real de la parada (catálogo o API); null si no hay parada elegida. */
    val stopName: String? = null,
    /** Líneas reales de la parada (catálogo + vistas en vivo), en código público. */
    val stopLines: List<String> = emptyList(),
    /** Destinos reales de la línea elegida en esa parada. */
    val destinations: List<String> = emptyList(),
    val lineColors: Map<String, Int> = emptyMap(),
    val errors: List<DraftError> = emptyList(),
    /** Los errores se enseñan tras intentar guardar, no mientras se rellena. */
    val showErrors: Boolean = false,
    /** Otro aviso con la misma (parada, línea): diálogo para actualizarlo. */
    val conflict: ConflictInfo? = null,
    val saving: Boolean = false,
    val saved: Boolean = false,
    /** Buscador de parada abierto («Cambiar» / «Elegir parada»). */
    val pickingStop: Boolean = false,
    val stopQuery: String = "",
    val stopResults: List<CatalogStop> = emptyList(),
    /** Rango de antelación permitido (`alerts.minMinutesAhead..maxMinutesAhead` de la config). */
    val minutesRange: IntRange = AlertsConfig().let { it.minMinutesAhead..it.maxMinutesAhead },
    /** Caducidad del aviso (`alerts.expirationDays`), para el texto de A06 (A5-10). */
    val expirationDays: Int = AlertsConfig().expirationDays,
)

data class ConflictInfo(val lineId: String, val stopName: String)

private data class FormState(
    val draft: AlertDraft?,
    val notFound: Boolean,
    val showErrors: Boolean,
    val conflict: ConflictInfo?,
    val saving: Boolean,
    val saved: Boolean,
    val pickingStop: Boolean,
    val stopQuery: String,
)

/**
 * A06 · Configurar aviso. Nuevo (`alert/new?stopId=&lineId=&destination=`) o edición
 * (`alert/{id}/edit`). Sin motor todavía (Sprint 4): guarda en Room con dedup `(stopId, lineId)`.
 */
class AlertEditViewModel(
    private val container: AppContainer,
    private val alertId: String?,
    stopId: String?,
    lineId: String?,
    destination: String?,
) : ViewModel() {
    private val form = MutableStateFlow(
        FormState(
            draft = if (alertId != null) {
                null
            } else {
                AlertDraft(
                    stopId = stopId?.takeIf { it.isNotBlank() },
                    lineId = lineId?.takeIf { it.isNotBlank() }?.let(::normalizeLine),
                    destination = destination?.takeIf { it.isNotBlank() },
                )
            },
            notFound = false,
            showErrors = false,
            conflict = null,
            saving = false,
            saved = false,
            pickingStop = alertId == null && stopId.isNullOrBlank(),
            stopQuery = "",
        ),
    )

    val state: StateFlow<AlertEditUiState> = combine(
        form,
        container.catalog,
        container.live.feeds,
        container.lineColors,
        container.configRepository.config,
    ) { f, catalogResult, feeds, colors, cfg ->
        val catalog = catalogResult?.getOrNull()
        val draft = f.draft
        if (catalogResult == null || (draft == null && !f.notFound)) {
            return@combine AlertEditUiState(loading = true, editing = alertId != null)
        }
        if (catalog == null || draft == null) {
            return@combine AlertEditUiState(loading = false, catalogError = catalog == null, notFound = f.notFound, editing = alertId != null)
        }
        val stop = draft.stopId
        val arrivals = stop?.let { feeds[it]?.lastValid?.arrivals }.orEmpty()
        val resolved = withStopName(draft)
        AlertEditUiState(
            loading = false,
            editing = alertId != null,
            draft = draft,
            stopName = resolved.stopName,
            stopLines = stop?.let { stopLineIds(catalog, it, arrivals) }.orEmpty(),
            destinations = if (stop != null && draft.lineId != null) {
                lineDestinationsAtStop(catalog, stop, checkNotNull(draft.lineId), arrivals)
            } else {
                emptyList()
            },
            lineColors = colors,
            errors = validateDraft(resolved, cfg.alerts),
            showErrors = f.showErrors,
            conflict = f.conflict,
            saving = f.saving,
            saved = f.saved,
            pickingStop = f.pickingStop,
            stopQuery = f.stopQuery,
            stopResults = searchStops(catalog, f.stopQuery, limit = cfg.uiLimits.searchStopsLimit),
            minutesRange = cfg.alerts.minMinutesAhead..cfg.alerts.maxMinutesAhead,
            expirationDays = cfg.alerts.expirationDays,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), AlertEditUiState(editing = alertId != null))

    init {
        if (alertId != null) {
            viewModelScope.launch {
                val rule = container.database.alerts().findById(alertId)?.toDomain()
                form.update { it.copy(draft = rule?.toDraft(), notFound = rule == null) }
                rule?.let { ensureLive(it.stopId) }
            }
        } else {
            stopId?.takeIf { it.isNotBlank() }?.let(::ensureLive)
        }
    }

    /** Nombre real de la parada: el del catálogo; si no está, el de la API; si no, el guardado. */
    private fun withStopName(draft: AlertDraft): AlertDraft {
        val stop = draft.stopId ?: return draft
        val name = container.catalog.value?.getOrNull()?.stopByCode(stop)?.desc
            ?: container.live.feeds.value[stop]?.lastValid?.stopName
            ?: draft.stopName
        return draft.copy(stopName = name)
    }

    /** Si la parada no se ha consultado en esta sesión, una consulta para conocer sus líneas y destinos en vivo. */
    private fun ensureLive(stopId: String) {
        if (container.live.feeds.value[stopId] != null) return
        viewModelScope.launch { container.live.fetchStop(stopId) }
    }

    private fun edit(change: (AlertDraft) -> AlertDraft) =
        form.update { f -> f.copy(draft = f.draft?.let(change)) }

    fun openStopPicker() = form.update { it.copy(pickingStop = true, stopQuery = "") }

    fun closeStopPicker() = form.update { it.copy(pickingStop = false) }

    fun onStopQuery(query: String) = form.update { it.copy(stopQuery = query) }

    fun selectStop(stop: CatalogStop) {
        val catalog = container.catalog.value?.getOrNull()
        edit { d ->
            val keepLine = d.lineId?.takeIf { line ->
                catalog != null && stopLineIds(catalog, stop.cod, emptyList()).any { normalizeLine(it) == line }
            }
            d.copy(stopId = stop.cod, stopName = stop.desc, lineId = keepLine, destination = null)
        }
        form.update { it.copy(pickingStop = false) }
        ensureLive(stop.cod)
    }

    fun selectLine(lineId: String) = edit { it.copy(lineId = normalizeLine(lineId), destination = null) }

    fun selectDestination(destination: String?) = edit { it.copy(destination = destination) }

    fun setMinutes(minutes: Int) {
        val alertsCfg = container.configRepository.alertsConfig.value
        edit { it.copy(minutesAhead = minutes.coerceIn(alertsCfg.minMinutesAhead..alertsCfg.maxMinutesAhead)) }
    }

    /** Marca los errores visibles y dice si se puede guardar (antes de pedir permisos). */
    fun validateForSave(): Boolean {
        form.update { it.copy(showErrors = true) }
        val draft = form.value.draft ?: return false
        return validateDraft(withStopName(draft), container.configRepository.alertsConfig.value).isEmpty()
    }

    fun dismissConflict() = form.update { it.copy(conflict = null) }

    /** Guarda con dedup `(stopId, lineId)`; con [confirmed] actualiza el aviso que ya existía. */
    fun save(confirmed: Boolean = false) {
        val draft = form.value.draft?.let(::withStopName) ?: return
        val alertsCfg = container.configRepository.alertsConfig.value
        if (validateDraft(draft, alertsCfg).isNotEmpty() || form.value.saving) return
        form.update { it.copy(saving = true, conflict = null) }
        viewModelScope.launch {
            try {
                val db = container.database
                val dao = db.alerts()
                val conflict = dao.findByStopAndLine(checkNotNull(draft.stopId), normalizeLine(checkNotNull(draft.lineId)))?.toDomain()
                when (val plan = planSave(draft, alertId, conflict, confirmed, UUID.randomUUID().toString(), System.currentTimeMillis())) {
                    is SavePlan.NeedsConfirmation ->
                        form.update { it.copy(conflict = ConflictInfo(plan.existing.lineId, plan.existing.stopName)) }
                    is SavePlan.Write -> {
                        db.withTransaction {
                            plan.deleteId?.let { dao.delete(it) }
                            if (plan.insert) dao.insert(plan.rule.toEntity()) else dao.update(plan.rule.toEntity())
                        }
                        form.update { it.copy(saved = true) }
                    }
                }
            } finally {
                form.update { it.copy(saving = false) }
            }
        }
    }
}
