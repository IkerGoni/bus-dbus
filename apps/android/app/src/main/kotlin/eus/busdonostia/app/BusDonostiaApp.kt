package eus.busdonostia.app

import android.app.Application
import android.content.Context
import android.os.StrictMode
import androidx.appcompat.app.AppCompatDelegate
import eus.busdonostia.app.data.api.DbusClient
import eus.busdonostia.app.data.catalog.loadCatalog
import eus.busdonostia.app.data.config.ApiConfig
import eus.busdonostia.app.data.config.ConfigRepository
import eus.busdonostia.app.data.db.AppDatabase
import eus.busdonostia.app.data.db.toDomain
import eus.busdonostia.app.data.live.LiveRepository
import eus.busdonostia.app.data.location.LocationProvider
import eus.busdonostia.app.data.settings.SettingsStore
import eus.busdonostia.app.data.settings.currentAppLanguage
import eus.busdonostia.app.domain.AlertRule
import eus.busdonostia.app.domain.Catalog
import eus.busdonostia.app.domain.Favorite
import eus.busdonostia.app.domain.Ticker
import eus.busdonostia.app.engine.MonitoringStarter
import eus.busdonostia.app.engine.createNotificationChannels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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

    private val defaultConfig = ApiConfig.default()

    /** Dbus responde en < 0,3 s; timeouts configurados desde el esquema v2 (H7). */
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(defaultConfig.connectTimeoutSec.toLong(), TimeUnit.SECONDS)
        .callTimeout(defaultConfig.callTimeoutSec.toLong(), TimeUnit.SECONDS)
        .build()

    val database: AppDatabase = AppDatabase.build(app)

    val configRepository = ConfigRepository(
        bundled = defaultConfig,
        cacheFileProvider = { File(app.filesDir, "remote-config.json") },
        remoteUrl = ConfigRepository.remoteConfigUrl(BuildConfig.REMOTE_CONFIG_URL),
        http = http,
    )

    val dbusClient = DbusClient(http, config = { configRepository.config.value })

    /** Catálogo offline del APK, parseado fuera del hilo principal. null = cargando; fallo = bug de build. */
    val catalog: StateFlow<Result<Catalog>?> = flow { emit(runCatching { app.assets.loadCatalog() }) }
        .flowOn(Dispatchers.IO)
        .stateIn(appScope, SharingStarted.Eagerly, null)

    val location = LocationProvider(app) { configRepository.config.value.geo }

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

    val settings = SettingsStore({ app.getSharedPreferences("settings", Context.MODE_PRIVATE) }, appScope)

    /** Idioma de la app para `tiemposParada` (`es` | `eu`): el elegido en A07 o el del sistema. */
    private val resources = app.resources

    fun apiLanguage(): String = currentAppLanguage(resources.configuration.locales[0].language).tag

    /** Llegadas en vivo: única puerta a `tiemposParada` (UI y motor). */
    val live = LiveRepository(
        client = dbusClient,
        language = ::apiLanguage,
        pollIntervalMs = { configRepository.config.value.polling.foregroundVisibleSec * 1_000L },
        onLiveFetch = settings::recordLiveFetch,
        onLineColors = { database.lineColors().upsertAll(it) },
        scope = appScope,
        historySize = { configRepository.config.value.freshness.liveFeedHistorySize },
        pollingConfig = { configRepository.config.value.polling },
    )

    val ticker = Ticker(
        scope = appScope,
        nowMs = System::currentTimeMillis,
        periodMs = { configRepository.config.value.polling.uiTickerSec * 1_000L },
    )

    /** Arranca el motor de avisos con cada cambio de avisos y al abrir la app. */
    val monitoring = MonitoringStarter(
        context = app,
        alerts = alerts,
        scope = appScope,
        alertsConfig = { configRepository.config.value.alerts },
    )

    init {
        // Cero I/O en Main Thread: carga assets/config.json y remote-config.json en Dispatchers.IO.
        appScope.launch(Dispatchers.IO) {
            configRepository.initializeFromDisk { ConfigRepository.loadBundled(app.assets) }
            configRepository.refreshIfDue()
        }
    }
}

class BusDonostiaApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // Antes de crear el contenedor: así StrictMode audita también el arranque (config y catálogo fuera del Main Thread).
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .penaltyLog()
                    .build(),
            )
        }
        createNotificationChannels(this)
        container = AppContainer(this)
        // Apariencia elegida en A07 (AppCompat no la guarda): se aplica en cuanto SettingsStore
        // la lee de disco en IO (A5-13, cero I/O en Main). MainActivity espera a `settings.ready`.
        container.appScope.launch(Dispatchers.Main) {
            container.settings.theme.collect { AppCompatDelegate.setDefaultNightMode(it.nightMode) }
        }
    }
}
