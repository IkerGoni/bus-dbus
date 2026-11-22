package eus.busdonostia.app.domain

import eus.busdonostia.app.TestData
import eus.busdonostia.app.data.live.LiveFeeds
import eus.busdonostia.app.ui.alerts.alertItems
import eus.busdonostia.app.ui.alerts.stopsToWatch
import eus.busdonostia.app.ui.home.favoriteCards
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** A01, A04 y badge: todos pasan por `computeAlertStatus` (guardrails §3) y el orden de §3. */
class AlertListTest {
    private fun madrid(iso: String): Long = ZonedDateTime.of(LocalDateTime.parse(iso), ZoneId.of("Europe/Madrid")).toInstant().toEpochMilli()

    private val now = madrid("2026-11-23T08:00") // lunes

    private fun arrival(line: String, minutes: Int, source: ArrivalSource = ArrivalSource.LIVE, destination: String = "Amara") =
        Arrival(line, line, destination, minutes, null, source, now, null)

    private fun rule(id: String, line: String, minutesAhead: Int = 5, stop: String = "340") = AlertRule(
        id = id, stopId = stop, stopName = "Pío Baroja 10", lineId = line, minutesAhead = minutesAhead,
        createdAtMs = now - 1_000,
    )

    @Test
    fun estadosYDetalle() {
        val arrivals = listOf(arrival("5", 3), arrival("28", 12))
        assertEquals(ComputedAlertStatus.TRIGGERED, alertItem(rule("t", "5"), arrivals, now).status)
        val active = alertItem(rule("a", "28"), arrivals, now)
        assertEquals(ComputedAlertStatus.ACTIVE, active.status)
        assertEquals(12, active.remainingMin) // minutos actuales aunque no esté dentro de la antelación
        assertEquals(AlertDetail.PAUSED, alertItem(rule("p", "5").copy(active = false), arrivals, now).detail)
        assertEquals(AlertDetail.EXPIRED, alertItem(rule("e", "5").copy(createdAtMs = now - ONCE_TTL_MS - 1), arrivals, now).detail)
        assertNull(alertItem(rule("x", "13"), arrivals, now).nextArrival)
    }

    @Test
    fun ordenGruposYBadge() {
        val arrivals = listOf(arrival("5", 3), arrival("28", 12), arrival("27", 7))
        val items = listOf(
            alertItem(rule("inactive", "5").copy(active = false), arrivals, now),
            alertItem(rule("expired", "5").copy(createdAtMs = now - ONCE_TTL_MS - 1), arrivals, now),
            alertItem(rule("snoozed", "5").copy(snoozedUntilMs = now + SNOOZE_MS), arrivals, now),
            alertItem(rule("noArrival", "13"), arrivals, now),
            alertItem(rule("active12", "28"), arrivals, now),
            alertItem(rule("active7", "27"), arrivals, now),
            alertItem(rule("triggered", "5", stop = "1"), arrivals, now),
        )
        assertEquals(
            listOf("triggered", "active7", "active12", "noArrival", "snoozed", "expired", "inactive"),
            sortAlerts(items).map { it.rule.id },
        )
        val (activeGroup, pausedGroup) = groupAlerts(items)
        assertEquals(listOf("triggered", "active7", "active12", "noArrival", "snoozed"), activeGroup.map { it.rule.id })
        assertEquals(listOf("expired", "inactive"), pausedGroup.map { it.rule.id })
        assertEquals("triggered", nextAlert(items)?.rule?.id)
        assertEquals(4, alertBadgeCount(items)) // ACTIVE | TRIGGERED
        assertEquals(listOf(null, "1", "9", "9+"), listOf(0, 1, 9, 10).map(::badgeText))
    }

    @Test
    fun proximaLlegadaParaPintar() {
        val scheduled2 = arrival("5", 2, ArrivalSource.SCHEDULED)
        val live2 = arrival("5", 2)
        val live9 = arrival("5", 9)
        assertEquals(scheduled2, nextArrivalFor(listOf(live9, scheduled2), "5", null, now)) // manda el más cercano
        assertEquals(live2, nextArrivalFor(listOf(scheduled2, live2), "5", null, now)) // a igualdad, en vivo
        assertNull(nextArrivalFor(listOf(live2), "5", "Ospitaleak", now)) // filtro de destino
    }

    @Test
    fun soloSeVigilanParadasDeAvisosActivos() {
        assertEquals(setOf("1", "340"), stopsToWatch(listOf(rule("a", "5", stop = "340"), rule("b", "28", stop = "1"), rule("c", "5", stop = "9").copy(active = false))))
    }

    @Test
    fun errorDeRedSinDatosPreviosNoInventaLlegadas() {
        val feeds = LiveFeeds()
        feeds.record("340", StopFeed(StopFeedStatus.API_ERROR, emptyList(), null, now))
        val item = alertItems(listOf(rule("a", "5")), feeds.feeds.value, now).single()
        assertEquals(ComputedAlertStatus.ACTIVE, item.status)
        assertNull(item.nextArrival)
        // Con datos válidos previos y un fallo después, siguen valiendo mientras no caduquen.
        feeds.record("340", StopFeed(StopFeedStatus.OK, listOf(arrival("5", 3)), null, now))
        feeds.record("340", StopFeed(StopFeedStatus.API_ERROR, emptyList(), null, now + 1_000))
        assertEquals(ComputedAlertStatus.TRIGGERED, alertItems(listOf(rule("a", "5")), feeds.feeds.value, now + 1_000).single().status)
        assertEquals(ComputedAlertStatus.ACTIVE, alertItems(listOf(rule("a", "5")), feeds.feeds.value, now + 120_000).single().status)
    }

    @Test
    fun tarjetasDeFavoritasConSusLlegadas() {
        val catalog = TestData.catalog
        val feeds = LiveFeeds()
        val arrivals = listOf(arrival("5", 9), arrival("28", 2), arrival("5", 4), arrival("5", 14), arrival("5", 20))
        feeds.record("340", StopFeed(StopFeedStatus.OK, arrivals, null, now))
        val cards = favoriteCards(
            catalog,
            listOf(Favorite(FavoriteType.STOP, "340", null, 1), Favorite(FavoriteType.STOP_LINE, "340", "5", 2), Favorite(FavoriteType.STOP, "1", null, 0)),
            feeds.feeds.value,
            now,
        )
        assertEquals(listOf("340", "340", "1"), cards.map { it.row.stopId })
        assertEquals(listOf(4, 9, 14), cards[0].arrivals.map { it.minutesAtFetch }) // solo la línea 5, máx. 3
        assertEquals(listOf(2, 4, 9), cards[1].arrivals.map { it.minutesAtFetch }) // toda la parada, máx. 3
        assertTrue(cards[2].view == ArrivalsView.Loading && cards[2].arrivals.isEmpty()) // aún sin consultar
    }
}
