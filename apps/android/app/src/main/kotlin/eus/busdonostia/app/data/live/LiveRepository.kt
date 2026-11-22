package eus.busdonostia.app.data.live

import eus.busdonostia.app.data.api.DbusClient
import eus.busdonostia.app.data.db.LineColorEntity
import eus.busdonostia.app.data.db.lineColors
import eus.busdonostia.app.domain.FreshnessConfig
import eus.busdonostia.app.domain.StopFeed
import eus.busdonostia.app.domain.StopFeedStatus
import eus.busdonostia.app.domain.reusableFeed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Única puerta a `tiemposParada` de la app, la pida la UI o el motor (H9). Guarda cada
 * resultado en [feeds], avisa de la hora de la última respuesta real (A07) y de los
 * colores de línea vistos en vivo. Una sola petición por parada a la vez (H16).
 */
class LiveRepository(
    private val client: DbusClient,
    /** `es` | `eu`: el idioma de la app. */
    private val language: () -> String,
    /** `foregroundVisibleSec` de la config, en ms. */
    private val pollIntervalMs: () -> Long,
    private val onLiveFetch: (fetchedAtMs: Long) -> Unit,
    private val onLineColors: suspend (List<LineColorEntity>) -> Unit,
    private val scope: CoroutineScope,
    private val nowMs: () -> Long = System::currentTimeMillis,
    historySize: () -> Int = { FreshnessConfig().liveFeedHistorySize },
) {
    private val live = LiveFeeds(historySize)
    private val inFlight = InFlight<String, StopFeed>(scope)

    /** Respuestas de esta sesión por parada (A01, A04, A06, badge y motor). */
    val feeds: StateFlow<Map<String, StopFeeds>> = live.feeds

    suspend fun fetchStop(stopId: String): StopFeed = inFlight.run(stopId) {
        val feed = client.tiemposParada(stopId, language())
        live.record(stopId, feed)
        if (feed.status != StopFeedStatus.API_ERROR) onLiveFetch(feed.fetchedAtMs)
        if (feed.status == StopFeedStatus.OK) scope.launch { onLineColors(feed.lineColors()) }
        feed
    }

    /**
     * Sondeo periódico de la UI: si alguien (el motor) consultó la parada hace menos de
     * un intervalo (− 5 s de margen), reutiliza esa respuesta en vez de pedir otra (H16).
     */
    suspend fun pollStop(stopId: String): StopFeed =
        reusableFeed(feeds.value[stopId]?.latest, nowMs(), pollIntervalMs()) ?: fetchStop(stopId)

    /** Consulta todas las [stops] en paralelo (una petición por parada, como el bot). */
    suspend fun fetchStops(stops: Set<String>) = coroutineScope {
        stops.map { async { fetchStop(it) } }.awaitAll()
    }
}
