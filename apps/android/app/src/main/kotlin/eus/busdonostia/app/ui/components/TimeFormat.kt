package eus.busdonostia.app.ui.components

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val HH_MM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** Formatea un epoch en milisegundos como `HH:mm` en la zona horaria del dispositivo. */
fun clockOf(ms: Long): String = HH_MM.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))
