package eus.busdonostia.app.data

import android.util.Log

/** Aviso a logcat de un fallo que se recupera solo. Se inyecta para poder probarlo en JVM, donde no hay `Log`. */
typealias Warn = (message: String, error: Throwable) -> Unit

fun logcatWarn(tag: String): Warn = { message, error -> Log.w(tag, message, error) }
