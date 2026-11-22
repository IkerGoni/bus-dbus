package eus.busdonostia.app

import eus.busdonostia.app.data.catalog.parseCatalog
import eus.busdonostia.app.domain.Catalog
import java.io.File

/**
 * Datos reales para tests JVM. Rutas inyectadas por Gradle (`app/build.gradle.kts`):
 * `fixtures.dir` = `packages/contracts/fixtures/` (compartidos con `@dbus/core`) y
 * `catalog.dir` = salida de `mergeDebugCatalog`.
 */
object TestData {
    private fun dir(property: String) =
        File(checkNotNull(System.getProperty(property)) { "Falta -D$property (ejecutar vía Gradle)" })

    fun fixture(name: String): String = File(dir("fixtures.dir"), name).readText()

    val catalogJson: String by lazy { File(dir("catalog.dir"), "catalog.json").readText() }

    val catalog: Catalog by lazy { parseCatalog(catalogJson) }

    /** `assets/config.json` empaquetada (los tests corren con cwd = módulo `app`). */
    val bundledConfigJson: String get() = File("src/main/assets/config.json").readText()
}
