plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

/**
 * Catálogo offline (ADR 009): fusiona el índice del bot con las coordenadas OSM
 * en `assets/catalog.json` en cada build. El generado no se commitea ni se edita;
 * la fuente es `apps/bot/data/` (regenerar con `npm run build:catalog`).
 */
@CacheableTask
abstract class MergeCatalogTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val catalogFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val coordinatesFile: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun merge() {
        @Suppress("UNCHECKED_CAST")
        fun read(f: RegularFileProperty) =
            groovy.json.JsonSlurper().parse(f.get().asFile, "UTF-8") as Map<String, Any?>

        val catalog = read(catalogFile)
        val coords = read(coordinatesFile)
        @Suppress("UNCHECKED_CAST")
        val points = (coords["stops"] as List<Map<String, Any?>>).associateBy { it["cod"] as String }
        @Suppress("UNCHECKED_CAST")
        val stops = (catalog["stops"] as List<Map<String, Any?>>).map { stop ->
            // Coordenadas solo para paradas del catálogo; nunca se inventan paradas nuevas.
            val point = points[stop["cod"]] ?: return@map stop
            stop + mapOf("lat" to point["lat"], "lon" to point["lon"])
        }
        val merged = linkedMapOf(
            "schemaVersion" to 1,
            "generatedAt" to catalog["generatedAt"],
            "source" to catalog["source"],
            "attribution" to catalog["attribution"],
            "coordinates" to mapOf(
                "generatedAt" to coords["generatedAt"],
                "source" to coords["source"],
                "attribution" to coords["attribution"],
            ),
            "lines" to catalog["lines"],
            "itineraries" to catalog["itineraries"],
            "stops" to stops,
        )
        val out = outputDir.get().asFile.resolve("catalog.json")
        out.parentFile.mkdirs()
        out.writeText(groovy.json.JsonOutput.toJson(merged), Charsets.UTF_8)
    }
}

@CacheableTask
abstract class GenerateDesignTokensTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val tokensFile: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        @Suppress("UNCHECKED_CAST")
        val root = groovy.json.JsonSlurper().parse(tokensFile.get().asFile, "UTF-8") as Map<String, Any?>

        @Suppress("UNCHECKED_CAST")
        fun section(parent: Map<String, Any?>, key: String): Map<String, Any?> =
            parent[key] as Map<String, Any?>

        fun intVal(map: Map<String, Any?>, key: String): Int =
            (map[key] as Number).toInt()

        fun floatLiteral(map: Map<String, Any?>, key: String): String =
            "${(map[key] as Number).toDouble()}f"

        val motion = section(root, "motion")
        val nav = section(motion, "navigation")
        val trans = section(motion, "transitions")
        val micro = section(motion, "microInteractions")
        val pulse = section(motion, "livePulse")
        val stagger = section(motion, "stagger")
        val celebration = section(motion, "celebration")

        val style = section(root, "style")
        val spacing = section(style, "spacingDp")
        val radius = section(style, "cornerRadiusDp")
        val comps = section(style, "componentsDp")

        // Validación defensiva: el build falla con el nombre exacto de la clave, no con un NPE opaco.
        fun fail(message: String): Nothing = throw GradleException("design-tokens.json: $message")

        fun requireKeys(section: Map<String, Any?>, path: String, vararg keys: String) {
            keys.forEach { key ->
                if (!section.containsKey(key)) fail("falta la clave '$path.$key'")
            }
        }

        requireKeys(motion, "motion", "maxBlockingDurationMs")
        requireKeys(
            nav, "motion.navigation",
            "bottomTabFadeInMs", "bottomTabFadeOutMs", "detailSlideInMs", "detailFadeInMs",
            "detailSlideOutMs", "detailFadeOutMs", "detailSlideFraction",
        )
        requireKeys(
            trans, "motion.transitions",
            "arrivalsStateFadeInMs", "arrivalsStateFadeOutMs", "arrivalsStateSlideFraction",
            "minutesSlideInMs", "minutesSlideOutMs", "searchClearButtonMs", "infoBannerMs",
            "lineDirectionCrossfadeMs", "wizardStepSlideMs",
        )
        requireKeys(
            micro, "motion.microInteractions",
            "pressScaleFactor", "favoriteStarMinScale", "favoriteStarPeakScale", "favoriteStarRotationDeg",
            "springNoBounceDamping", "springMediumBounceDamping", "springMediumStiffness",
            "searchClearScaleFactor", "chipIndicatorStartScale",
        )
        requireKeys(pulse, "motion.livePulse", "durationMs", "minAlpha", "maxAlpha")
        requireKeys(stagger, "motion.stagger", "itemDelayMs", "maxAnimatedIndex")
        requireKeys(celebration, "motion.celebration", "particlesDurationMs")
        requireKeys(spacing, "style.spacingDp", "xs", "sm", "md", "lg", "xl")
        requireKeys(radius, "style.cornerRadiusDp", "chip", "card", "pill")
        requireKeys(
            comps, "style.componentsDp",
            "timelineTerminalNode", "timelineIntermediateNode", "liveSignalDot", "lineBadgeMinWidth",
            "chipVisualHeight",
        )

        val maxBlockingMs = intVal(motion, "maxBlockingDurationMs")

        fun validate(prefix: String, value: Any?) {
            when (value) {
                is Map<*, *> -> value.forEach { (key, nested) -> validate("$prefix.$key", nested) }
                is Number -> {
                    val path = prefix
                    val number = value.toFloat()
                    // Duraciones bloqueantes: se excluyen pulso vivo y partículas (no bloquean).
                    if (path.endsWith("Ms") && path != "motion.maxBlockingDurationMs" &&
                        !path.startsWith("motion.livePulse.") && !path.startsWith("motion.celebration.")
                    ) {
                        if (value.toInt() > maxBlockingMs) {
                            fail("'$path' = ${value.toInt()} ms supera maxBlockingDurationMs ($maxBlockingMs)")
                        }
                    }
                    if (path.endsWith("Alpha") && (number < 0f || number > 1f)) {
                        fail("'$path' debe estar en [0,1]")
                    }
                    if ((path.endsWith("Scale") || path.endsWith("ScaleFactor")) && number <= 0f) {
                        fail("'$path' debe ser > 0")
                    }
                }
                else -> {}
            }
        }
        validate("motion", motion)
        validate("style", style)

        val maxAnimatedIndex = intVal(stagger, "maxAnimatedIndex")
        val itemDelayMs = intVal(stagger, "itemDelayMs")
        if (maxAnimatedIndex < 0) fail("'motion.stagger.maxAnimatedIndex' no puede ser negativo")
        if (itemDelayMs * maxAnimatedIndex > maxBlockingMs) {
            fail(
                "'motion.stagger.itemDelayMs * maxAnimatedIndex' = ${itemDelayMs * maxAnimatedIndex} ms " +
                    "supera maxBlockingDurationMs ($maxBlockingMs)",
            )
        }

        val pkgDir = outputDir.get().asFile.resolve("eus/busdonostia/app/ui/theme")
        pkgDir.mkdirs()

        val motionKt = """
            package eus.busdonostia.app.ui.theme

            object MotionTokens {
                const val MAX_BLOCKING_DURATION_MS = ${intVal(motion, "maxBlockingDurationMs")}

                object Navigation {
                    const val BOTTOM_TAB_FADE_IN_MS = ${intVal(nav, "bottomTabFadeInMs")}
                    const val BOTTOM_TAB_FADE_OUT_MS = ${intVal(nav, "bottomTabFadeOutMs")}
                    const val DETAIL_SLIDE_IN_MS = ${intVal(nav, "detailSlideInMs")}
                    const val DETAIL_FADE_IN_MS = ${intVal(nav, "detailFadeInMs")}
                    const val DETAIL_SLIDE_OUT_MS = ${intVal(nav, "detailSlideOutMs")}
                    const val DETAIL_FADE_OUT_MS = ${intVal(nav, "detailFadeOutMs")}
                    const val DETAIL_SLIDE_FRACTION = ${floatLiteral(nav, "detailSlideFraction")}
                }

                object Transitions {
                    const val ARRIVALS_STATE_FADE_IN_MS = ${intVal(trans, "arrivalsStateFadeInMs")}
                    const val ARRIVALS_STATE_FADE_OUT_MS = ${intVal(trans, "arrivalsStateFadeOutMs")}
                    const val ARRIVALS_STATE_SLIDE_FRACTION = ${floatLiteral(trans, "arrivalsStateSlideFraction")}
                    const val MINUTES_SLIDE_IN_MS = ${intVal(trans, "minutesSlideInMs")}
                    const val MINUTES_SLIDE_OUT_MS = ${intVal(trans, "minutesSlideOutMs")}
                    const val SEARCH_CLEAR_BUTTON_MS = ${intVal(trans, "searchClearButtonMs")}
                    const val INFO_BANNER_MS = ${intVal(trans, "infoBannerMs")}
                    const val LINE_DIRECTION_CROSSFADE_MS = ${intVal(trans, "lineDirectionCrossfadeMs")}
                    const val WIZARD_STEP_SLIDE_MS = ${intVal(trans, "wizardStepSlideMs")}
                }

                object MicroInteractions {
                    const val PRESS_SCALE_FACTOR = ${floatLiteral(micro, "pressScaleFactor")}
                    const val FAVORITE_STAR_MIN_SCALE = ${floatLiteral(micro, "favoriteStarMinScale")}
                    const val FAVORITE_STAR_PEAK_SCALE = ${floatLiteral(micro, "favoriteStarPeakScale")}
                    const val FAVORITE_STAR_ROTATION_DEG = ${floatLiteral(micro, "favoriteStarRotationDeg")}
                    const val SPRING_NO_BOUNCE_DAMPING = ${floatLiteral(micro, "springNoBounceDamping")}
                    const val SPRING_MEDIUM_BOUNCE_DAMPING = ${floatLiteral(micro, "springMediumBounceDamping")}
                    const val SPRING_MEDIUM_STIFFNESS = ${floatLiteral(micro, "springMediumStiffness")}
                    const val SEARCH_CLEAR_SCALE_FACTOR = ${floatLiteral(micro, "searchClearScaleFactor")}
                    const val CHIP_INDICATOR_START_SCALE = ${floatLiteral(micro, "chipIndicatorStartScale")}
                }

                object LivePulse {
                    const val DURATION_MS = ${intVal(pulse, "durationMs")}
                    const val MIN_ALPHA = ${floatLiteral(pulse, "minAlpha")}
                    const val MAX_ALPHA = ${floatLiteral(pulse, "maxAlpha")}
                }

                object Stagger {
                    const val ITEM_DELAY_MS = ${intVal(stagger, "itemDelayMs")}
                    const val MAX_ANIMATED_INDEX = ${intVal(stagger, "maxAnimatedIndex")}
                }

                object Celebration {
                    const val PARTICLES_DURATION_MS = ${intVal(celebration, "particlesDurationMs")}
                }

                data class ResolvedMotionTokens(
                    val bottomTabFadeInMs: Int,
                    val bottomTabFadeOutMs: Int,
                    val detailSlideInMs: Int,
                    val detailFadeInMs: Int,
                    val detailSlideOutMs: Int,
                    val detailFadeOutMs: Int,
                    val detailSlideFraction: Float,
                    val arrivalsStateFadeInMs: Int,
                    val arrivalsStateFadeOutMs: Int,
                    val arrivalsStateSlideFraction: Float,
                    val minutesSlideInMs: Int,
                    val minutesSlideOutMs: Int,
                    val searchClearButtonMs: Int,
                    val infoBannerMs: Int,
                    val lineDirectionCrossfadeMs: Int,
                    val wizardStepSlideMs: Int,
                    val pressScaleFactor: Float,
                    val favoriteStarMinScale: Float,
                    val favoriteStarPeakScale: Float,
                    val favoriteStarRotationDeg: Float,
                    val springNoBounceDamping: Float,
                    val springMediumBounceDamping: Float,
                    val springMediumStiffness: Float,
                    val searchClearScaleFactor: Float,
                    val chipIndicatorStartScale: Float,
                    val livePulseDurationMs: Int,
                    val livePulseMinAlpha: Float,
                    val livePulseMaxAlpha: Float,
                    val staggerItemDelayMs: Int,
                    val staggerMaxAnimatedIndex: Int,
                    val particlesDurationMs: Int,
                ) {
                    companion object {
                        val FULL = ResolvedMotionTokens(
                            bottomTabFadeInMs = Navigation.BOTTOM_TAB_FADE_IN_MS,
                            bottomTabFadeOutMs = Navigation.BOTTOM_TAB_FADE_OUT_MS,
                            detailSlideInMs = Navigation.DETAIL_SLIDE_IN_MS,
                            detailFadeInMs = Navigation.DETAIL_FADE_IN_MS,
                            detailSlideOutMs = Navigation.DETAIL_SLIDE_OUT_MS,
                            detailFadeOutMs = Navigation.DETAIL_FADE_OUT_MS,
                            detailSlideFraction = Navigation.DETAIL_SLIDE_FRACTION,
                            arrivalsStateFadeInMs = Transitions.ARRIVALS_STATE_FADE_IN_MS,
                            arrivalsStateFadeOutMs = Transitions.ARRIVALS_STATE_FADE_OUT_MS,
                            arrivalsStateSlideFraction = Transitions.ARRIVALS_STATE_SLIDE_FRACTION,
                            minutesSlideInMs = Transitions.MINUTES_SLIDE_IN_MS,
                            minutesSlideOutMs = Transitions.MINUTES_SLIDE_OUT_MS,
                            searchClearButtonMs = Transitions.SEARCH_CLEAR_BUTTON_MS,
                            infoBannerMs = Transitions.INFO_BANNER_MS,
                            lineDirectionCrossfadeMs = Transitions.LINE_DIRECTION_CROSSFADE_MS,
                            wizardStepSlideMs = Transitions.WIZARD_STEP_SLIDE_MS,
                            pressScaleFactor = MicroInteractions.PRESS_SCALE_FACTOR,
                            favoriteStarMinScale = MicroInteractions.FAVORITE_STAR_MIN_SCALE,
                            favoriteStarPeakScale = MicroInteractions.FAVORITE_STAR_PEAK_SCALE,
                            favoriteStarRotationDeg = MicroInteractions.FAVORITE_STAR_ROTATION_DEG,
                            springNoBounceDamping = MicroInteractions.SPRING_NO_BOUNCE_DAMPING,
                            springMediumBounceDamping = MicroInteractions.SPRING_MEDIUM_BOUNCE_DAMPING,
                            springMediumStiffness = MicroInteractions.SPRING_MEDIUM_STIFFNESS,
                            searchClearScaleFactor = MicroInteractions.SEARCH_CLEAR_SCALE_FACTOR,
                            chipIndicatorStartScale = MicroInteractions.CHIP_INDICATOR_START_SCALE,
                            livePulseDurationMs = LivePulse.DURATION_MS,
                            livePulseMinAlpha = LivePulse.MIN_ALPHA,
                            livePulseMaxAlpha = LivePulse.MAX_ALPHA,
                            staggerItemDelayMs = Stagger.ITEM_DELAY_MS,
                            staggerMaxAnimatedIndex = Stagger.MAX_ANIMATED_INDEX,
                            particlesDurationMs = Celebration.PARTICLES_DURATION_MS,
                        )

                        val ZERO = ResolvedMotionTokens(
                            bottomTabFadeInMs = 0,
                            bottomTabFadeOutMs = 0,
                            detailSlideInMs = 0,
                            detailFadeInMs = 0,
                            detailSlideOutMs = 0,
                            detailFadeOutMs = 0,
                            detailSlideFraction = 0f,
                            arrivalsStateFadeInMs = 0,
                            arrivalsStateFadeOutMs = 0,
                            arrivalsStateSlideFraction = 0f,
                            minutesSlideInMs = 0,
                            minutesSlideOutMs = 0,
                            searchClearButtonMs = 0,
                            infoBannerMs = 0,
                            lineDirectionCrossfadeMs = 0,
                            wizardStepSlideMs = 0,
                            pressScaleFactor = 1f,
                            favoriteStarMinScale = 1f,
                            favoriteStarPeakScale = 1f,
                            favoriteStarRotationDeg = 0f,
                            springNoBounceDamping = 1f,
                            springMediumBounceDamping = 1f,
                            springMediumStiffness = 10_000f, // = Spring.StiffnessHigh: colapso instantáneo
                            searchClearScaleFactor = 1f,
                            chipIndicatorStartScale = 1f,
                            livePulseDurationMs = 0,
                            livePulseMinAlpha = 1f,
                            livePulseMaxAlpha = 1f,
                            staggerItemDelayMs = 0,
                            staggerMaxAnimatedIndex = 0,
                            particlesDurationMs = 0,
                        )
                    }
                }

                fun resolve(reducedMotion: Boolean): ResolvedMotionTokens =
                    if (reducedMotion) ResolvedMotionTokens.ZERO else ResolvedMotionTokens.FULL
            }
        """.trimIndent() + "\n"

        val designKt = """
            package eus.busdonostia.app.ui.theme

            import androidx.compose.ui.unit.Dp
            import androidx.compose.ui.unit.dp

            object DesignTokens {
                object Spacing {
                    val XS = ${intVal(spacing, "xs")}.dp
                    val SM = ${intVal(spacing, "sm")}.dp
                    val MD = ${intVal(spacing, "md")}.dp
                    val LG = ${intVal(spacing, "lg")}.dp
                    val XL = ${intVal(spacing, "xl")}.dp
                }

                object CornerRadius {
                    val CHIP = ${intVal(radius, "chip")}.dp
                    val CARD = ${intVal(radius, "card")}.dp
                    val PILL = ${intVal(radius, "pill")}.dp
                }

                object Components {
                    val TIMELINE_TERMINAL_NODE = ${intVal(comps, "timelineTerminalNode")}.dp
                    val TIMELINE_INTERMEDIATE_NODE = ${intVal(comps, "timelineIntermediateNode")}.dp
                    val LIVE_SIGNAL_DOT_SIZE = ${intVal(comps, "liveSignalDot")}.dp
                    val LINE_BADGE_MIN_WIDTH = ${intVal(comps, "lineBadgeMinWidth")}.dp
                    val CHIP_VISUAL_HEIGHT = ${intVal(comps, "chipVisualHeight")}.dp
                }

                val spacingXs: Dp = Spacing.XS
                val spacingSm: Dp = Spacing.SM
                val spacingMd: Dp = Spacing.MD
                val spacingLg: Dp = Spacing.LG
                val spacingXl: Dp = Spacing.XL

                val cornerRadiusChip: Dp = CornerRadius.CHIP
                val cornerRadiusCard: Dp = CornerRadius.CARD
                val cornerRadiusPill: Dp = CornerRadius.PILL

                val timelineTerminalNode: Dp = Components.TIMELINE_TERMINAL_NODE
                val timelineIntermediateNode: Dp = Components.TIMELINE_INTERMEDIATE_NODE
                val liveSignalDotSize: Dp = Components.LIVE_SIGNAL_DOT_SIZE
                val lineBadgeMinWidth: Dp = Components.LINE_BADGE_MIN_WIDTH
                val chipVisualHeight: Dp = Components.CHIP_VISUAL_HEIGHT
            }
        """.trimIndent() + "\n"

        pkgDir.resolve("MotionTokens.kt").writeText(motionKt, Charsets.UTF_8)
        pkgDir.resolve("DesignTokens.kt").writeText(designKt, Charsets.UTF_8)
    }
}

/**
 * Firma de release (H8). Con Play App Signing esta es la clave de subida: la crea y la
 * custodia Iker, nunca va al repo (`*.jks` y `*.keystore` están ignorados). Se lee de
 * `~/.gradle/gradle.properties` (`dbus.release.storeFile`, `…storePassword`,
 * `…keyAlias`, `…keyPassword`) o de variables de entorno (`DBUS_RELEASE_STORE_FILE`,
 * `…_STORE_PASSWORD`, `…_KEY_ALIAS`, `…_KEY_PASSWORD`). Si falta alguna, empaquetar
 * release falla con un mensaje claro; `-Pdbus.release.unsigned=true` genera un APK sin
 * firmar (solo para medir tamaño). Debug, `check` y los tests no la necesitan.
 */
val releaseSigning: Map<String, String?> = listOf("storeFile", "storePassword", "keyAlias", "keyPassword").associateWith { key ->
    val env = "DBUS_RELEASE_" + key.replace(Regex("([A-Z])"), "_$1").uppercase()
    providers.gradleProperty("dbus.release.$key").orElse(providers.environmentVariable(env)).orNull?.takeIf { it.isNotBlank() }
}
val missingReleaseSigning = releaseSigning.filterValues { it == null }.keys
val unsignedRelease = providers.gradleProperty("dbus.release.unsigned").orNull == "true"

val botData = layout.projectDirectory.dir("../../bot/data")
val contractFixtures = layout.projectDirectory.dir("../../../packages/contracts/fixtures")
val designTokensJson = layout.projectDirectory.file("../../../packages/contracts/design-tokens.json")

android {
    namespace = "eus.busdonostia.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "eus.busdonostia.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "1.1.0"
        // URL HTTPS del config.json remoto (ADR 009). Vacía hasta configurar el hosting estático: solo la empaquetada.
        buildConfigField("String", "REMOTE_CONFIG_URL", "\"\"")
    }

    signingConfigs {
        if (missingReleaseSigning.isEmpty()) {
            create("release") {
                storeFile = file(releaseSigning.getValue("storeFile")!!)
                storePassword = releaseSigning.getValue("storePassword")
                keyAlias = releaseSigning.getValue("keyAlias")
                keyPassword = releaseSigning.getValue("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        // es (por defecto) y eu; nada más (ADR 009).
        localeFilters += listOf("es", "eu")
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    lint {
        warningsAsErrors = false
        abortOnError = true
        error += listOf("MissingTranslation", "ExtraTranslation", "HardcodedText")
        checkDependencies = false
    }
}

kotlin {
    jvmToolchain(17)
    
    // Permitir APIs experimentales de animación de Compose
    compilerOptions {
        freeCompilerArgs.add("-opt-in=androidx.compose.animation.ExperimentalAnimationApi")
        freeCompilerArgs.add("-opt-in=androidx.compose.animation.ExperimentalSharedTransitionApi")
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

androidComponents {
    onVariants { variant ->
        val capName = variant.name.replaceFirstChar(Char::uppercase)
        val task = tasks.register<MergeCatalogTask>("merge${capName}Catalog") {
            catalogFile.set(botData.file("dbus-catalog.json"))
            coordinatesFile.set(botData.file("dbus-stop-coordinates.json"))
        }
        variant.sources.assets?.addGeneratedSourceDirectory(task, MergeCatalogTask::outputDir)

        val tokensTask = tasks.register<GenerateDesignTokensTask>("generate${capName}DesignTokens") {
            tokensFile.set(designTokensJson)
        }
        variant.sources.java?.addGeneratedSourceDirectory(tokensTask, GenerateDesignTokensTask::outputDir)
    }
}

// Sin firma, empaquetar release (APK o AAB) falla aquí y no al subirlo a Play.
if (missingReleaseSigning.isNotEmpty() && !unsignedRelease) {
    val message = "Falta la firma de release: ${missingReleaseSigning.joinToString { "dbus.release.$it" }}. " +
        "Ponlas en ~/.gradle/gradle.properties o en variables DBUS_RELEASE_* (ver apps/android/README.md), " +
        "o usa -Pdbus.release.unsigned=true para un APK sin firmar."
    tasks.matching { it.name == "packageRelease" || it.name == "packageReleaseBundle" }.configureEach {
        doFirst { throw GradleException(message) }
    }
}

// Los tests JVM leen el catálogo generado y los fixtures reales compartidos con @dbus/core.
tasks.withType<Test>().configureEach {
    val merge = tasks.named<MergeCatalogTask>("mergeDebugCatalog")
    dependsOn(merge)
    val catalogDir = merge.flatMap { it.outputDir }
    val fixturesDir = contractFixtures.asFile
    val tokensPath = designTokensJson.asFile.absolutePath
    inputs.dir(fixturesDir).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(designTokensJson).withPathSensitivity(PathSensitivity.NONE)
    jvmArgumentProviders.add(
        CommandLineArgumentProvider {
            listOf(
                "-Dcatalog.dir=${catalogDir.get().asFile.absolutePath}",
                "-Dfixtures.dir=${fixturesDir.absolutePath}",
                "-Ddesign.tokens=$tokensPath",
            )
        },
    )
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
}
