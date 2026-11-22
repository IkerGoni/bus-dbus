package eus.busdonostia.app.data.live

import eus.busdonostia.app.domain.StopFeed
import eus.busdonostia.app.domain.StopFeedStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Último resultado de una parada y la última respuesta válida (no `API_ERROR`), como en A05. */
data class StopFeeds(val latest: StopFeed, val lastValid: StopFeed?)

/**
 * Respuestas en vivo de `tiemposParada` de esta sesión, por parada, en memoria.
 * No es una caché de horarios: quien las pinta pasa por `stopArrivalsView`, que
 * marca como `degraded` o `error` lo que ha envejecido. Se pierde al cerrar el proceso.
 */
class LiveFeeds {
    private val _feeds = MutableStateFlow<Map<String, StopFeeds>>(emptyMap())
    val feeds: StateFlow<Map<String, StopFeeds>> = _feeds.asStateFlow()

    fun record(stopId: String, feed: StopFeed) {
        _feeds.update { all ->
            val previousValid = all[stopId]?.lastValid
            val valid = if (feed.status == StopFeedStatus.API_ERROR) previousValid else feed
            all + (stopId to StopFeeds(feed, valid))
        }
    }
}
