package eus.busdonostia.app.ui.components

/**
 * Claves de shared element: nombre canónico (`stop-title-…`, `line-…`) + el **origen** de la
 * tarjeta pulsada. Sin el origen, varias tarjetas visibles a la vez (aviso + favoritos,
 * favorita de parada + favorita de parada+línea, paradas repetidas en líneas circulares)
 * compartirían clave y la transición sería indeterminada.
 *
 * El origen es un identificador técnico: vive solo en la ruta de navegación y en la clave,
 * nunca se muestra en la UI.
 */
object SharedKeys {
    fun stopTitle(stopId: String, origin: String): String = "stop-title-$stopId@$origin"

    fun line(stopId: String, lineId: String, origin: String): String = "line-$stopId-$lineId@$origin"
}

/** Orígenes de tarjeta: únicos por pantalla. */
object SharedOrigins {
    const val NEXT_ALERT = "next"

    fun favorite(stopId: String, lineId: String?): String = "fav-$stopId-${lineId.orEmpty()}"

    fun search(stopId: String): String = "search-$stopId"

    fun stopRow(tab: String, stopId: String, lineId: String?): String = "stops-$tab-$stopId-${lineId.orEmpty()}"

    fun timeline(direction: Int, index: Int): String = "tl-$direction-$index"
}
