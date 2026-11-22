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

val botData = layout.projectDirectory.dir("../../bot/data")
val contractFixtures = layout.projectDirectory.dir("../../../packages/contracts/fixtures")

android {
    namespace = "eus.busdonostia.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "eus.busdonostia.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        // URL HTTPS del config.json remoto (ADR 009). Vacía hasta el Sprint 5: solo la empaquetada.
        buildConfigField("String", "REMOTE_CONFIG_URL", "\"\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
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
}

room {
    schemaDirectory("$projectDir/schemas")
}

androidComponents {
    onVariants { variant ->
        val task = tasks.register<MergeCatalogTask>("merge${variant.name.replaceFirstChar(Char::uppercase)}Catalog") {
            catalogFile.set(botData.file("dbus-catalog.json"))
            coordinatesFile.set(botData.file("dbus-stop-coordinates.json"))
        }
        variant.sources.assets?.addGeneratedSourceDirectory(task, MergeCatalogTask::outputDir)
    }
}

// Los tests JVM leen el catálogo generado y los fixtures reales compartidos con @dbus/core.
tasks.withType<Test>().configureEach {
    val merge = tasks.named<MergeCatalogTask>("mergeDebugCatalog")
    dependsOn(merge)
    val catalogDir = merge.flatMap { it.outputDir }
    val fixturesDir = contractFixtures.asFile
    inputs.dir(fixturesDir).withPathSensitivity(PathSensitivity.RELATIVE)
    jvmArgumentProviders.add(
        CommandLineArgumentProvider {
            listOf(
                "-Dcatalog.dir=${catalogDir.get().asFile.absolutePath}",
                "-Dfixtures.dir=${fixturesDir.absolutePath}",
            )
        },
    )
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

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
}
