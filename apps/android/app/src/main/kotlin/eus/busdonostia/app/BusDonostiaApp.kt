package eus.busdonostia.app

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import eus.busdonostia.app.data.api.DbusClient
import eus.busdonostia.app.data.catalog.loadCatalog
import eus.busdonostia.app.data.config.ConfigRepository
import eus.busdonostia.app.data.db.AppDatabase
import eus.busdonostia.app.data.db.lineColors
import eus.busdonostia.app.data.db.toDomain
import eus.busdonostia.app.data.live.LiveFeeds
import eus.busdonostia.app.data.location.LocationProvider
import eus.busdonostia.app.data.settings.SettingsStore
import eus.busdonostia.app.data.settings.currentAppLanguage
import eus.busdonostia.app.domain.AlertRule
import eus.busdonostia.app.domain.Catalog
import eus.busdonostia.app.domain.Favorite
import eus.busdonostia.app.domain.StopFeed
import eus.busdonostia.app.domain.StopFeedStatus
import eus.busdonostia.app.domain.Ticker
import eus.busdonostia.app.engine.createNotificationChannels
import eus.busdonostia.app.engine.ensureMonitoring
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/** Contenedor de dependencias sin framework de DI (ADR 009). Uno por proceso. */
class AppContainer(private val app: Application) {
    /** Vive lo que el proceso: carga del catálogo, config remota y escrituras que no deben cancelarse. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    val database: AppDatabase = AppDatabase.build(app)

    val configRepository = ConfigRepository(
        bundled = ConfigRepository.loadBundled(app.assets),
        cacheFile = File(app.filesDir, "remote-config.json"),
        remoteUrl = ConfigRepository.remoteConfigUrl(BuildConfig.REMOTE_CONFIG_URL),
        http = http,
    )

    val dbusClient = DbusClient(http, config = { configRepository.config.value })

    /** Catálogo offline del APK, parseado fuera del hilo principal. null = cargando; fallo = bug de build. */
    val catalog: StateFlow<Result<Catalog>?> = flow { emit(runCatching { app.assets.loadCatalog() }) }
        .flowOn(Dispatchers.IO)
        .stateIn(appScope, SharingStarted.Eagerly, null)

    val location = LocationProvider(app)

    /** Colores de línea vistos en vivo, por código público. */
    val lineColors: StateFlow<Map<String, Int>> = database.lineColors().observeAll()
        .map { list -> list.associate { it.lineId to it.colorArgb } }
        .stateIn(appScope, SharingStarted.Eagerly, emptyMap())

    /** Favoritos de los 3 tipos, compartidos por todas las pantallas. */
    val favorites: StateFlow<List<Favorite>> = database.favorites().observeAll()
        .map { list -> list.map { it.toDomain() } }
        .stateIn(appScope, SharingStarted.Eagerly, emptyList())

    /** Avisos guardados; null mientras Room no ha respondido (estado `loading`). */
    val alerts: StateFlow<List<AlertRule>?> = database.alerts().observeAll()
        .map { list -> list.map { it.toDomain() } }
        .stateIn(appScope, SharingStarted.Eagerly, null)

    val settings = SettingsStore(app.getSharedPreferences("settings", Context.MODE_PRIVATE))

    /** Respuestas en vivo de esta sesión por parada (A01, A04, A06 y badge). */
    val liveFeeds = LiveFeeds()

    /** Idioma de la app para `tiemposParada` (`es` | `eu`): el elegido en A07 o el del sistema. */
    private val resources = app.resources

    fun apiLanguage(): String = currentAppLanguage(resources.configuration.locales[0].language).tag

    /**
     * Única puerta a `tiemposParada` para la UI: guarda el resultado en [liveFeeds],
     * la hora de la última respuesta real (A07) y los colores de línea vistos en vivo.
     */
    suspend fun fetchStop(stopId: String): StopFeed {
        val feed = dbusClient.tiemposParada(stopId, apiLanguage())
        liveFeeds.record(stopId, feed)
        if (feed.status != StopFeedStatus.API_ERROR) settings.recordLiveFetch(feed.fetchedAtMs)
        if (feed.status == StopFeedStatus.OK) appScope.launch { database.lineColors().upsertAll(feed.lineColors()) }
        return feed
    }

    val ticker = Ticker(appScope)

    /** Arranca el motor de avisos si hace falta (al abrir la app; los cambios de avisos ya lo hacen solos). */
    fun ensureMonitoring() {
        alerts.value?.let { ensureMonitoring(app, it, System.currentTimeMillis()) }
    }

    init {
        // Sin bloquear el arranque: si no hay remota o falla, sigue la empaquetada.
        appScope.launch { configRepository.refreshIfDue() }
        // Cada cambio de avisos (guardar, switch, Posponer, Deshacer): si algo queda en vigilancia, arranca el servicio.
        appScope.launch {
            alerts.filterNotNull().collect { rules -> ensureMonitoring(app, rules, System.currentTimeMillis()) }
        }
    }
}

class BusDonostiaApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels(this)
        container = AppContainer(this)
        // Apariencia elegida en A07 (AppCompat no la guarda): antes de crear ninguna actividad.
        AppCompatDelegate.setDefaultNightMode(container.settings.theme.value.nightMode)
    }
}
