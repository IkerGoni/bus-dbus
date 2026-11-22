package eus.busdonostia.app.data.catalog

import android.content.res.AssetManager
import eus.busdonostia.app.domain.Catalog
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

/** Parsea `catalog.json` (generado por `merge<Variant>Catalog`). Falla si no es válido: viene en el APK. */
fun parseCatalog(text: String): Catalog = json.decodeFromString(Catalog.serializer(), text)

fun AssetManager.loadCatalog(): Catalog = open("catalog.json").bufferedReader().use { parseCatalog(it.readText()) }
