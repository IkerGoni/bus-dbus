package eus.busdonostia.app.engine

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.room.withTransaction
import eus.busdonostia.app.AppContainer
import eus.busdonostia.app.BusDonostiaApp
import eus.busdonostia.app.R
import eus.busdonostia.app.data.db.toDomain
import eus.busdonostia.app.data.db.toEntity
import eus.busdonostia.app.domain.AlertRule
import eus.busdonostia.app.domain.EngineAction
import eus.busdonostia.app.domain.evaluateTick
import eus.busdonostia.app.domain.fired
import eus.busdonostia.app.domain.isFetchDue
import eus.busdonostia.app.domain.isWatching
import eus.busdonostia.app.domain.stopsToPoll
import eus.busdonostia.app.ui.alerts.feedView
import eus.busdonostia.app.ui.alerts.fetchStops
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Motor de avisos (04-android-platform.md §1): Foreground Service `dataSync` que,
 * mientras haya avisos en vigilancia, consulta sus paradas cada `pollIntervalSec`
 * (una petición por parada, por `AppContainer.fetchStop`) y dispara lo que esté
 * `TRIGGERED`. La decisión es [evaluateTick]; aquí solo se consulta, escribe y notifica.
 */
class AlertMonitorService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loop: Job? = null

    /** Despierta la vuelta antes de tiempo (nuevo arranque o cambio en los avisos). */
    private val wake = Channel<Unit>(Channel.CONFLATED)

    @Volatile
    private var lastStartId = 0

    private val container: AppContainer get() = (application as BusDonostiaApp).container

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val now = System.currentTimeMillis()
        val watching = container.alerts.value.orEmpty().filter { isWatching(it, now) }
        ServiceCompat.startForeground(
            this,
            MONITORING_NOTIFICATION_ID,
            monitoringNotification(this, watching, pollIntervalSec()),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
        cancelStopped(this)
        lastStartId = startId
        running = true
        if (loop?.isActive == true) {
            wake.trySend(Unit)
        } else {
            loop = scope.launch { runUntilStopped() }
        }
        return START_NOT_STICKY
    }

    /** Android 15+: `dataSync` tiene 6 h cada 24 h. Con franjas ≤ 3 h no debería pasar; si pasa, se avisa. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        notifyStopped(this, R.string.notif_stopped_time_limit)
        loop?.cancel()
        running = false
        stopSelf()
    }

    override fun onDestroy() {
        running = false
        scope.cancel()
        super.onDestroy()
    }

    private fun pollIntervalSec(): Int = container.configRepository.config.value.pollIntervalSec

    private suspend fun runUntilStopped() {
        val ruleChanges = scope.launch { container.alerts.filterNotNull().drop(1).collect { wake.trySend(Unit) } }
        try {
            while (true) {
                val action = watchUntilDone()
                if (action == EngineAction.STOP_NO_SERVICE) notifyStopped(this, R.string.notif_stopped_no_service)
                // Si llegó otro arranque mientras se decidía parar, se sigue vigilando.
                running = false
                if (stopSelfResult(lastStartId)) return
                running = true
            }
        } finally {
            ruleChanges.cancel()
        }
    }

    /** Vueltas cada `pollIntervalSec` hasta que el motor decide parar; devuelve el motivo. */
    private suspend fun watchUntilDone(): EngineAction {
        var troubleSinceMs: Long? = null
        while (true) {
            val intervalMs = pollIntervalSec() * 1_000L
            val action = try {
                val rules = container.database.alerts().getAll().map { it.toDomain() }
                val tick = tick(rules, intervalMs, troubleSinceMs)
                troubleSinceMs = tick.first
                tick.second
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Un fallo inesperado (BD, red) no debe tumbar la vigilancia: se reintenta en la siguiente vuelta.
                EngineAction.CONTINUE
            }
            if (action != EngineAction.CONTINUE) return action
            withTimeoutOrNull(intervalMs) { wake.receive() }
        }
    }

    private suspend fun tick(rules: List<AlertRule>, intervalMs: Long, troubleSinceMs: Long?): Pair<Long?, EngineAction> {
        val startMs = System.currentTimeMillis()
        val known = container.liveFeeds.feeds.value
        container.fetchStops(
            stopsToPoll(rules, startMs).filterTo(sortedSetOf()) { isFetchDue(known[it]?.latest?.fetchedAtMs, startMs, intervalMs) },
        )
        val now = System.currentTimeMillis()
        val feeds = container.liveFeeds.feeds.value
        val views = stopsToPoll(rules, now).associateWith { feedView(feeds, it, now) }
        val tick = evaluateTick(rules, views, now, troubleSinceMs)
        tick.firings.forEach { firing ->
            markFired(firing.rule.id, now)
            notifyArrival(this, firing, now)
        }
        if (tick.action == EngineAction.CONTINUE) updateMonitoringNotification(this, tick.watching, pollIntervalSec())
        return tick.troubleSinceMs to tick.action
    }

    /** Sobre la versión guardada: el usuario puede haber editado el aviso durante la consulta. */
    private suspend fun markFired(id: String, nowMs: Long) {
        val db = container.database
        db.withTransaction {
            val rule = db.alerts().findById(id)?.toDomain() ?: return@withTransaction
            db.alerts().update(rule.fired(nowMs).toEntity())
        }
    }

    companion object {
        /** El servicio está vivo (o arrancando): no hace falta volver a arrancarlo. */
        @Volatile
        var running = false
            private set

        /**
         * Arranca la vigilancia. Solo funciona con la app visible, desde una acción de
         * notificación o una alarma exacta (Android 12+); devuelve false si el sistema lo impide.
         */
        fun start(context: Context): Boolean = try {
            ContextCompat.startForegroundService(context, Intent(context, AlertMonitorService::class.java))
            true
        } catch (_: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException (12+) o arranque en segundo plano (8+).
            false
        }
    }
}
