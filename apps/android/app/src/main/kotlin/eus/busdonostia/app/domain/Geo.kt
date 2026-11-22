package eus.busdonostia.app.domain

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

private const val EARTH_RADIUS_M = 6_371_000.0

/** Distancia en línea recta, en metros redondeados (misma fórmula que `apps/bot/src/nearby.ts`). */
fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Long {
    val toRad = PI / 180.0
    val dLat = (lat2 - lat1) * toRad
    val dLon = (lon2 - lon1) * toRad
    val a = sin(dLat / 2).let { it * it } +
        cos(lat1 * toRad) * cos(lat2 * toRad) * sin(dLon / 2).let { it * it }
    return (2 * EARTH_RADIUS_M * atan2(sqrt(a), sqrt(1 - a))).roundToLong()
}
