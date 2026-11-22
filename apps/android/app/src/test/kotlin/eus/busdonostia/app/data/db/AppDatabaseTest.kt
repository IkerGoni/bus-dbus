package eus.busdonostia.app.data.db

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import eus.busdonostia.app.domain.AlertRule
import eus.busdonostia.app.domain.Favorite
import eus.busdonostia.app.domain.FavoriteType
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppDatabaseTest {
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    // --- favoritos ---

    @Test
    fun `favoritos - los 3 tipos hacen ida y vuelta`() = runTest {
        val favs = listOf(
            Favorite(FavoriteType.STOP, "340", null, 1),
            Favorite(FavoriteType.STOP_LINE, "340", "28", 2),
            Favorite(FavoriteType.LINE, null, "B4", 3),
        )
        favs.forEach { db.favorites().insert(it.toEntity()) }
        val back = db.favorites().observeAll().first().map { it.toDomain() }
        assertEquals(favs.reversed(), back)
    }

    @Test
    fun `favoritos - insertar dos veces deja una fila con el createdAtMs original`() = runTest {
        db.favorites().insert(Favorite(FavoriteType.STOP, "340", null, 100).toEntity())
        db.favorites().insert(Favorite(FavoriteType.STOP, "340", null, 999).toEntity())
        val all = db.favorites().observeAll().first()
        assertEquals(1, all.size)
        assertEquals(100L, all[0].createdAtMs)
    }

    @Test
    fun `favoritos - parada y parada+linea coexisten, exists y delete`() = runTest {
        db.favorites().insert(Favorite(FavoriteType.STOP, "340", null, 1).toEntity())
        db.favorites().insert(Favorite(FavoriteType.STOP_LINE, "340", "28", 2).toEntity())
        assertEquals(2, db.favorites().observeAll().first().size)
        assertTrue(db.favorites().exists("STOP:340:"))
        db.favorites().delete("STOP:340:")
        assertFalse(db.favorites().exists("STOP:340:"))
        assertTrue(db.favorites().exists("STOP_LINE:340:28"))
    }

    @Test
    fun `favoritos - formato exacto del id`() {
        assertEquals("STOP:340:", Favorite(FavoriteType.STOP, "340", null, 0).toEntity().id)
        assertEquals("STOP_LINE:340:28", Favorite(FavoriteType.STOP_LINE, "340", "28", 0).toEntity().id)
        assertEquals("LINE::B4", Favorite(FavoriteType.LINE, null, "B4", 0).toEntity().id)
    }

    // --- avisos ---

    private fun rule(id: String, stop: String = "340", line: String = "28") = AlertRule(
        id = id,
        stopId = stop,
        stopName = "Easo",
        lineId = line,
        minutesAhead = 5,
        createdAtMs = 10,
    )

    @Test
    fun `avisos - regla hace ida y vuelta`() = runTest {
        val r = rule("a1").copy(destination = "Intxaurrondo", snoozedUntilMs = 123)
        db.alerts().insert(r.toEntity())
        assertEquals(r, db.alerts().findById("a1")!!.toDomain())
    }

    @Test
    fun `avisos - campos nulos hacen ida y vuelta`() = runTest {
        val r = rule("a1")
        db.alerts().insert(r.toEntity())
        val e = db.alerts().findById("a1")!!
        assertEquals(r, e.toDomain())
        assertNull(e.destination)
    }

    @Test
    fun `migracion 1 a 2 - borra recurrentes y disparados y conserva el resto`() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "migration-test.db"
        context.deleteDatabase(name)
        // Esquema v1 exacto, del JSON que Room exportó (los tests corren con cwd = módulo `app`).
        val schema = Json.parseToJsonElement(File("schemas/eus.busdonostia.app.data.db.AppDatabase/1.json").readText())
        val entities = schema.jsonObject["database"]!!.jsonObject["entities"]!!.jsonArray.map { it.jsonObject }
        val v1 = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(
                object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        entities.forEach { entity ->
                            val table = entity["tableName"]!!.jsonPrimitive.content
                            val sqls = listOf(entity["createSql"]!!) + entity["indices"]?.jsonArray.orEmpty().map { it.jsonObject["createSql"]!! }
                            sqls.forEach { db.execSQL(it.jsonPrimitive.content.replace("\${TABLE_NAME}", table)) }
                        }
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                },
            ).build(),
        )
        v1.writableDatabase.apply {
            execSQL("INSERT INTO alerts VALUES ('once', '340', 'Easo', '28', 'Amara', 5, 'ONCE', '', NULL, NULL, 1, 10, 20, NULL)")
            execSQL("INSERT INTO alerts VALUES ('paused', '1', 'Boulevard', '5', NULL, 3, 'ONCE', '', NULL, NULL, 0, 10, NULL, NULL)")
            execSQL("INSERT INTO alerts VALUES ('fired', '2', 'Urbieta', '21', NULL, 5, 'ONCE', '', NULL, NULL, 0, 10, NULL, 99)")
            execSQL("INSERT INTO alerts VALUES ('rec', '3', 'Urbieta', '35', NULL, 5, 'RECURRING', '1,2', '07:45', '08:30', 1, 10, NULL, NULL)")
        }
        v1.close()
        // Room aplica la migración y valida el esquema v2 al abrir.
        val migrated = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()
        val rules = migrated.alerts().getAll().map { it.toDomain() }
        assertEquals(listOf("once", "paused"), rules.map { it.id }.sorted())
        assertEquals(
            AlertRule("once", "340", "Easo", "28", "Amara", 5, active = true, createdAtMs = 10, snoozedUntilMs = 20),
            rules.first { it.id == "once" },
        )
        migrated.close()
        context.deleteDatabase(name)
    }

    @Test
    fun `avisos - segundo aviso para la misma parada y linea lanza`() = runTest {
        db.alerts().insert(rule("a1").toEntity())
        var thrown: Throwable? = null
        try {
            db.alerts().insert(rule("a2").toEntity())
        } catch (e: SQLiteConstraintException) {
            thrown = e
        }
        assertNotNull(thrown)
    }

    @Test
    fun `avisos - misma parada con otra linea es valido`() = runTest {
        db.alerts().insert(rule("a1").toEntity())
        db.alerts().insert(rule("a2", line = "B4").toEntity())
        assertEquals(2, db.alerts().observeAll().first().size)
    }

    @Test
    fun `avisos - findByStopAndLine y update (ofrecer actualizar)`() = runTest {
        db.alerts().insert(rule("a1").toEntity())
        val existing = db.alerts().findByStopAndLine("340", "28")
        assertNotNull(existing)
        db.alerts().update(existing!!.copy(minutesAhead = 12, active = false))
        val after = db.alerts().findById("a1")!!
        assertEquals(12, after.minutesAhead)
        assertFalse(after.active)
        assertNull(db.alerts().findByStopAndLine("340", "99"))
        db.alerts().delete("a1")
        assertNull(db.alerts().findById("a1"))
    }

    // --- recientes ---

    @Test
    fun `recientes - 12 paradas dejan las 10 mas recientes`() = runTest {
        (1..12).forEach { db.recentStops().record("s$it", it.toLong()) }
        val ids = db.recentStops().observe().first().map { it.stopId }
        assertEquals(MAX_RECENTS, ids.size)
        assertEquals((12 downTo 3).map { "s$it" }, ids)
    }

    @Test
    fun `recientes - repetir una parada la sube sin duplicar`() = runTest {
        (1..3).forEach { db.recentStops().record("s$it", it.toLong()) }
        db.recentStops().record("s1", 10)
        val ids = db.recentStops().observe().first().map { it.stopId }
        assertEquals(listOf("s1", "s3", "s2"), ids)
    }

    // --- colores de línea ---

    @Test
    fun `colores - upsert sobrescribe el color de la misma linea`() = runTest {
        db.lineColors().upsertAll(listOf(LineColorEntity("28", -811520, 1), LineColorEntity("B4", 5, 1)))
        db.lineColors().upsertAll(listOf(LineColorEntity("28", -16711936, 2)))
        val c = db.lineColors().get("28")!!
        assertEquals(-16711936, c.colorArgb)
        assertEquals(2L, c.seenAtMs)
        assertEquals(2, db.lineColors().observeAll().first().size)
        assertNull(db.lineColors().get("zz"))
    }
}
