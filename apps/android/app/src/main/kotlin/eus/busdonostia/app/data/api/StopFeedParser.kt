package eus.busdonostia.app.data.api

import eus.busdonostia.app.domain.Arrival
import eus.busdonostia.app.domain.ArrivalSource
import eus.busdonostia.app.domain.StopFeed
import eus.busdonostia.app.domain.StopFeedStatus
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.roundToInt

/*
 * `tiemposParada` crudo → StopFeed. Tolerante a que Dbus devuelva `null` en
 * cualquier campo, como `parseStopTimes` de `@dbus/core`. Puro: sin red.
 */

private fun JsonElement?.obj(): JsonObject? = this as? JsonObject

private fun JsonElement?.str(): String =
    (this as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content?.trim().orEmpty()

private fun JsonElement?.double(): Double? =
    (this as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content?.toDoubleOrNull()?.takeIf { it.isFinite() }

private fun JsonElement?.array(): List<JsonElement> = (this as? JsonArray).orEmpty()

private val LINE_DIGITS = Regex("\\d+")

private fun parseArrival(raw: JsonObject, fetchedAtMs: Long): Arrival? {
    val itinerario = raw["itinerario"].obj() ?: return null
    val linea = itinerario["linea"].obj()
    val nombre = linea?.get("nombre").str()
    // Paridad con @dbus/core: si falta `cod`, los dígitos de `nombre`.
    val lineId = linea?.get("cod").str().ifEmpty { LINE_DIGITS.find(nombre)?.value ?: nombre }
    if (lineId.isEmpty()) return null
    val minutes = raw["minutos"].double() ?: return null
    return Arrival(
        lineId = lineId,
        internalLineId = linea?.get("id").str(),
        destination = itinerario["destino"].str().ifEmpty { itinerario["nombre"].str() },
        minutesAtFetch = maxOf(0, minutes.roundToInt()),
        clockTime = raw["hora"].str().ifEmpty { null },
        source = if (raw["tipo"].double() == 1.0) ArrivalSource.LIVE else ArrivalSource.SCHEDULED,
        fetchedAtMs = fetchedAtMs,
        colorArgb = linea?.get("color").double()?.toLong()?.toInt(),
    )
}

/** Estados observados en `estado`: `DATOS`, `SIN_SERVICIO`, `NO DESC`. Otro cualquiera → API_ERROR. */
fun parseStopFeed(body: String, fetchedAtMs: Long): StopFeed {
    val root = try {
        Json.parseToJsonElement(body).obj()
    } catch (_: SerializationException) {
        null
    } ?: return StopFeed(StopFeedStatus.API_ERROR, emptyList(), null, fetchedAtMs)

    val notice = root["avisos"].array().map { it.str() }.filter { it.isNotEmpty() }
        .joinToString("\n").ifEmpty { null }
    val stopName = root["parada"].obj()?.get("desc").str().ifEmpty { null }
    val arrivals = root["tiempos"].array()
        .mapNotNull { it.obj()?.let { raw -> parseArrival(raw, fetchedAtMs) } }
        .sortedBy { it.minutesAtFetch }
    val status = when (root["estado"].str()) {
        "DATOS" -> if (arrivals.isEmpty()) StopFeedStatus.EMPTY else StopFeedStatus.OK
        "SIN_SERVICIO" -> StopFeedStatus.NO_SERVICE
        else -> return StopFeed(StopFeedStatus.API_ERROR, emptyList(), notice, fetchedAtMs)
    }
    return StopFeed(status, if (status == StopFeedStatus.OK) arrivals else emptyList(), notice, fetchedAtMs, stopName)
}
