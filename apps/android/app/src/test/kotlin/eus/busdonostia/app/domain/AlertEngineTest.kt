package eus.busdonostia.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Motor de avisos de un solo uso: disparar / seguir / parar y sondeo (04-android-platform.md §1). */
class AlertEngineTest {
    private fun madrid(iso: String): Long = ZonedDateTime.of(LocalDateTime.parse(iso), ZoneId.of("Europe/Madrid")).toInstant().toEpochMilli()

    private val now = madrid("2026-11-23T08:00") // lunes

    private fun arrival(line: String, minutes: Int, destination: String = "Amara", source: ArrivalSource = ArrivalSource.LIVE) =
        Arrival(line, line, destination, minutes, null, source, now, null)

    private fun live(vararg arrivals: Arrival) = ArrivalsView.Live(arrivals.toList(), null, now)

    private val noService = ArrivalsView.Degraded(DegradedReason.NO_SERVICE, emptyList(), null, now)

    private fun once(id: String, line: String = "5", stop: String = "340", minutesAhead: Int = 5) = AlertRule(
        id = id, stopId = stop, stopName = "Pío Baroja 10", lineId = line, minutesAhead = minutesAhead,
        createdAtMs = now - 1_000,
    )

    @Test
    fun disparaUnaVezYSePara() {
        val tick = evaluateTick(listOf(once("a")), mapOf("340" to live(arrival("5", 4))), now, null)
        val firing = tick.firings.single()
        assertEquals("a", firing.rule.id) // el servicio lo notifica y lo borra
        assertEquals(4, firing.arrival.minutesAtFetch)
        assertEquals(EngineAction.STOP_IDLE, tick.action)
        assertTrue(tick.watching.isEmpty())
    }

    @Test
    fun losQueNoDisparanSiguenVigilados() {
        val tick = evaluateTick(listOf(once("a"), once("b", line = "28")), mapOf("340" to live(arrival("5", 4), arrival("28", 15))), now, null)
        assertEquals(listOf("a"), tick.firings.map { it.rule.id })
        assertEquals(listOf("b"), tick.watching.map { it.id })
        assertEquals(EngineAction.CONTINUE, tick.action)
    }

    @Test
    fun sigueMientrasNoLlegaDentroDeLaAntelacion() {
        val tick = evaluateTick(listOf(once("a")), mapOf("340" to live(arrival("5", 12), arrival("28", 2))), now, null)
        assertTrue(tick.firings.isEmpty())
        assertEquals(EngineAction.CONTINUE, tick.action)
        assertEquals(listOf("a"), tick.watching.map { it.id })
    }

    @Test
    fun soloVigilaLoQueTocaYAgrupaPorParada() {
        val rules = listOf(
            once("a", stop = "340"),
            once("b", line = "28", stop = "340"),
            once("c", stop = "1").copy(active = false),
        )
        assertEquals(setOf("340"), stopsToPoll(rules, now))
        assertEquals(listOf("a", "b"), rules.filter { isWatching(it, now) }.map { it.id })
        assertFalse(isWatching(once("x").copy(createdAtMs = now - ONCE_TTL_MS - 1), now)) // caducado
    }

    @Test
    fun seParaTras60MinutosSinServicio() {
        val rules = listOf(once("a"), once("b", stop = "1"))
        val start = evaluateTick(rules, mapOf("340" to noService, "1" to ArrivalsView.Error), now, null)
        assertEquals(now, start.troubleSinceMs)
        assertEquals(EngineAction.CONTINUE, start.action)
        val later = now + NO_SERVICE_STOP_MS - 1
        assertEquals(EngineAction.CONTINUE, evaluateTick(rules, mapOf("340" to noService, "1" to ArrivalsView.Error), later, now).action)
        val end = evaluateTick(rules, mapOf("340" to noService, "1" to ArrivalsView.Error), now + NO_SERVICE_STOP_MS, now)
        assertEquals(EngineAction.STOP_NO_SERVICE, end.action)
        // Basta una parada que responda para reiniciar la cuenta.
        val ok = evaluateTick(rules, mapOf("340" to noService, "1" to live(arrival("13", 20))), now + NO_SERVICE_STOP_MS, now)
        assertNull(ok.troubleSinceMs)
        assertEquals(EngineAction.CONTINUE, ok.action)
    }

    @Test
    fun datosViejosSiguenSirviendoYPrefiereEnVivo() {
        val stale = ArrivalsView.Degraded(DegradedReason.STALE, listOf(arrival("5", 6).copy(fetchedAtMs = now - 60_000)), null, now - 60_000)
        val tick = evaluateTick(listOf(once("a")), mapOf("340" to stale), now, null)
        assertEquals(1, tick.firings.size) // 6 min hace 1 min → 5 min
        assertNull(tick.troubleSinceMs)
        val both = live(arrival("5", 3, source = ArrivalSource.SCHEDULED), arrival("5", 4))
        assertEquals(ArrivalSource.LIVE, evaluateTick(listOf(once("a")), mapOf("340" to both), now, null).firings.single().arrival.source)
    }

    @Test
    fun consultaSoloSiTocaPorIntervalo() {
        assertTrue(isFetchDue(null, now, 30_000))
        assertFalse(isFetchDue(now - 10_000, now, 30_000)) // la UI acaba de consultar
        assertTrue(isFetchDue(now - 25_000, now, 30_000))
    }

    @Test
    fun sondeoAdaptativoSegunLoQueFaltaParaAvisar() {
        val base = 30_000L
        assertEquals(base, pollDelayMs(0, base))
        assertEquals(base, pollDelayMs(7, base)) // menos de 7 min para avisar: cada 30 s
        assertEquals(60_000L, pollDelayMs(8, base))
        assertEquals(60_000L, pollDelayMs(14, base))
        assertEquals(120_000L, pollDelayMs(15, base))
        assertEquals(60_000L, pollDelayMs(null, base)) // sin bus de la línea, sin servicio o error
        // Aviso 5' con el bus a 25 min (falta 20) → 2 min; a 15 (falta 10) → 1 min; a 12 (falta 7) → 30 s.
        val rule = once("a")
        assertEquals(120_000L, nextPollDelayMs(listOf(rule), mapOf("340" to live(arrival("5", 25))), now, base))
        assertEquals(60_000L, nextPollDelayMs(listOf(rule), mapOf("340" to live(arrival("5", 15))), now, base))
        assertEquals(base, nextPollDelayMs(listOf(rule), mapOf("340" to live(arrival("5", 12))), now, base))
        // Con varios avisos manda el más urgente.
        val urgent = once("b", line = "28")
        val views = mapOf("340" to live(arrival("5", 25), arrival("28", 7)))
        assertEquals(base, nextPollDelayMs(listOf(rule, urgent), views, now, base))
        // Sin avisos en vigilancia, la base.
        assertEquals(base, nextPollDelayMs(emptyList(), emptyMap(), now, base))
    }

    @Test
    fun unFalloInesperadoCuentaComoProblemaYAcabaParando() {
        // Primera vuelta fallida: empieza la cuenta y se sigue.
        val first = failedTick(null, now)
        assertEquals(now, first.troubleSinceMs)
        assertEquals(EngineAction.CONTINUE, first.action)
        // Si ya había problemas, la cuenta no se reinicia.
        assertEquals(now - 1_000, failedTick(now - 1_000, now).troubleSinceMs)
        // Fallando seguido (Room roto) llega a NO_SERVICE_STOP_MS y se para: no sondea para siempre.
        assertEquals(EngineAction.CONTINUE, failedTick(now, now + NO_SERVICE_STOP_MS - 1).action)
        assertEquals(EngineAction.STOP_NO_SERVICE, failedTick(now, now + NO_SERVICE_STOP_MS).action)
    }

    @Test
    fun sinPermisoDeNotificacionesNoSeBorraElAviso() {
        val firings = evaluateTick(listOf(once("a")), mapOf("340" to live(arrival("5", 4))), now, null).firings
        assertEquals(listOf("a"), firingsToDelete(firings, canNotify = true).map { it.rule.id })
        // El usuario no lo vería: se queda en Mis avisos (A04 ya avisa de que faltan permisos).
        assertTrue(firingsToDelete(firings, canNotify = false).isEmpty())
    }

    @Test
    fun destinoDelTitulo() {
        assertEquals("Amara", firingDestination(once("a"), arrival("5", 3)))
        assertEquals("Ospitaleak", firingDestination(once("a").copy(destination = "Ospitaleak"), arrival("5", 3, destination = " ")))
        assertNull(firingDestination(once("a").copy(destination = UNKNOWN_DESTINATION), arrival("5", 3, destination = "")))
        assertNull(firingDestination(once("a"), arrival("5", 3, destination = "")))
    }
}
