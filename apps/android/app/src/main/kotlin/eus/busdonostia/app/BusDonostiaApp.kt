package eus.busdonostia.app

import android.app.Application
import eus.busdonostia.app.data.api.DbusClient
import eus.busdonostia.app.data.catalog.loadCatalog
import eus.busdonostia.app.data.config.ConfigRepository
import eus.busdonostia.app.data.db.AppDatabase
import eus.busdonostia.app.data.db.toDomain
import eus.busdonostia.app.data.location.LocationProvider
import eus.busdonostia.app.domain.Catalog
import eus.busdonostia.app.domain.Favorite
import eus.busdonostia.app.domain.Ticker
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
class AppContainer(app: Application) {
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

    /** Idioma de la app para `tiemposParada` (`es` | `eu`). */
    private val resources = app.resources

    fun apiLanguage(): String = if (resources.configuration.locales[0].language == "eu") "eu" else "es"

    val ticker = Ticker(appScope)

    init {
        // Sin bloquear el arranque: si no hay remota o falla, sigue la empaquetada.
        appScope.launch { configRepository.refreshIfDue() }
    }
}

class BusDonostiaApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
