package eus.busdonostia.app.engine

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import eus.busdonostia.app.AppContainer
import eus.busdonostia.app.BusDonostiaApp
import eus.busdonostia.app.R
import eus.busdonostia.app.data.db.toDomain
import eus.busdonostia.app.data.live.feedView
import eus.busdonostia.app.domain.AlertRule
import eus.busdonostia.app.domain.EngineAction
import eus.busdonostia.app.domain.evaluateTick
import eus.busdonostia.app.domain.failedTick
import eus.busdonostia.app.domain.firingsToDelete
import eus.busdonostia.app.domain.isFetchDue
import eus.busdonostia.app.domain.isWatching
import eus.busdonostia.app.domain.nextPollDelayMs
import eus.busdonostia.app.domain.pollDelayMs
import eus.busdonostia.app.domain.resumeAfterStop
import eus.busdonostia.app.domain.stopsToPoll
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
 * mientras haya avisos en vigilancia, consulta sus paradas (según [nextPollDelayMs] y
 * `PollingConfig` de `config.json`) y dispara lo que esté `TRIGGERED`: notifica y, si
 * el usuario puede verlo, borra el aviso (un solo uso).
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
        val cfg = container.configRepository.config.value
        val watching = container.alerts.value.orEmpty().filter { isWatching(it, now, cfg.alerts) }
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

    /** Android 15+: `dataSync` tiene 6 h cada 24 h. Con el tope de `maxWatchHours` (3 h por defecto) no debería pasar; si pasa, se avisa. */
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

    private fun pollIntervalSec(): Int = container.configRepository.config.value.polling.baseBackgroundSec

    private suspend fun runUntilStopped() {
        val ruleChanges = scope.launch { container.alerts.filterNotNull().drop(1).collect { wake.trySend(Unit) } }
        try {
            while (true) {
                val action = watchUntilDone()
                if (action == EngineAction.STOP_NO_SERVICE) {
                    val stopMin = container.configRepository.config.value.polling.noServiceStopMin
                    notifyStopped(this, R.string.notif_stopped_no_service, stopMin)
                }
                running = false
                val alertsCfg = container.configRepository.config.value.alerts
                // Un aviso guardado antes de soltar `running` no arrancó el servicio (lo veía vivo): se relee la BD.
                if (resumeAfterStop(action, savedRules(), System.currentTimeMillis(), canNotifyArrivals(this), alertsCfg)) {
                    running = true
                    continue
                }
                // Uno guardado después sí pidió arranque: entonces stopSelfResult falla y se sigue vigilando.
                if (stopSelfResult(lastStartId)) return
                running = true
            }
        } finally {
            ruleChanges.cancel()
        }
    }

    private suspend fun savedRules(): List<AlertRule> = try {
        container.database.alerts().getAll().map { it.toDomain() }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "no se pudieron releer los avisos al parar", e)
        emptyList()
    }

    /** Vueltas cada `baseBackgroundSec` (o el escalón adaptativo) hasta que el motor decide parar. */
    private suspend fun watchUntilDone(): EngineAction {
        var troubleSinceMs: Long? = null
        while (true) {
            val pollingCfg = container.configRepository.config.value.polling
            val baseMs = pollingCfg.baseBackgroundSec * 1_000L
            var delayMs = baseMs
            val action = try {
                val rules = container.database.alerts().getAll().map { it.toDomain() }
                val result = tick(rules, baseMs, troubleSinceMs)
                troubleSinceMs = result.troubleSinceMs
                delayMs = result.delayMs
                result.action
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Un fallo inesperado (BD, bug) no tumba la vigilancia, pero cuenta como problema:
                // si sigue noServiceStopMin, el motor se para y lo notifica.
                Log.w(TAG, "vuelta del motor fallida", e)
                val failed = failedTick(troubleSinceMs, System.currentTimeMillis(), pollingCfg)
                troubleSinceMs = failed.troubleSinceMs
                delayMs = pollDelayMs(null, baseMs, pollingCfg)
                failed.action
            }
            if (action != EngineAction.CONTINUE) return action
            withTimeoutOrNull(delayMs) { wake.receive() }
        }
    }

    private class TickResult(val troubleSinceMs: Long?, val action: EngineAction, val delayMs: Long)

    private suspend fun tick(rules: List<AlertRule>, baseMs: Long, troubleSinceMs: Long?): TickResult {
        val cfg = container.configRepository.config.value
        val alertsCfg = cfg.alerts
        val pollingCfg = cfg.polling
        val freshnessCfg = cfg.freshness
        val startMs = System.currentTimeMillis()
        val known = container.live.feeds.value
        container.live.fetchStops(
            stopsToPoll(rules, startMs, alertsCfg).filterTo(sortedSetOf()) {
                isFetchDue(known[it]?.latest?.fetchedAtMs, startMs, baseMs, pollingCfg)
            },
        )
        val now = System.currentTimeMillis()
        val feeds = container.live.feeds.value
        val views = stopsToPoll(rules, now, alertsCfg).associateWith { feedView(feeds, it, now, freshnessCfg) }
        val tick = evaluateTick(rules, views, now, troubleSinceMs, alertsCfg, pollingCfg)
        // Primero se notifica; solo se borra (un solo uso) si el usuario puede ver la notificación.
        tick.firings.forEach { notifyArrival(this, it, now, alertsCfg) }
        firingsToDelete(tick.firings, canNotifyArrivals(this)).forEach { container.database.alerts().delete(it.rule.id) }
        // Tope de vigilancia (A5-09): N horas sin bus que coincida → se notifica y se elimina igual que un disparo.
        tick.watchExpired.forEach { notifyWatchExpired(this, it, alertsCfg.maxWatchHours) }
        if (canNotifyArrivals(this)) tick.watchExpired.forEach { container.database.alerts().delete(it.id) }
        // Sondeo adaptativo: más espaciado cuanto más lejos está el bus de la antelación.
        val delayMs = nextPollDelayMs(tick.watching, views, now, baseMs, pollingCfg)
        if (tick.action == EngineAction.CONTINUE) updateMonitoringNotification(this, tick.watching, (delayMs / 1_000).toInt())
        return TickResult(tick.troubleSinceMs, tick.action, delayMs)
    }

    companion object {
        private const val TAG = "AlertMonitorService"

        /** El servicio está vivo (o arrancando): no hace falta volver a arrancarlo. */
        @Volatile
        var running = false
            private set

        /** Arranca la vigilancia. En Android 12+ solo con la app visible; devuelve false si el sistema lo impide. */
        fun start(context: Context): Boolean = try {
            ContextCompat.startForegroundService(context, Intent(context, AlertMonitorService::class.java))
            true
        } catch (_: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException (12+) o arranque en segundo plano (8+).
            false
        }
    }
}
