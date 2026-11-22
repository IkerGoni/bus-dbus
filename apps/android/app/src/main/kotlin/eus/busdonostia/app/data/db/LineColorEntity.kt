package eus.busdonostia.app.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import eus.busdonostia.app.domain.StopFeed
import kotlinx.coroutines.flow.Flow

/** Último `linea.color` visto en vivo (el catálogo no trae colores). `lineId` = código público. */
@Entity(tableName = "line_colors")
data class LineColorEntity(
    @PrimaryKey val lineId: String,
    val colorArgb: Int,
    val seenAtMs: Long,
)

/** Colores reales de las líneas de una respuesta en vivo, uno por línea pública. */
fun StopFeed.lineColors(): List<LineColorEntity> =
    arrivals.mapNotNull { a -> a.colorArgb?.let { LineColorEntity(a.lineId, it, a.fetchedAtMs) } }.distinctBy { it.lineId }

@Dao
interface LineColorDao {
    @Upsert
    suspend fun upsertAll(list: List<LineColorEntity>)

    @Query("SELECT * FROM line_colors")
    fun observeAll(): Flow<List<LineColorEntity>>

    @Query("SELECT * FROM line_colors WHERE lineId = :lineId")
    suspend fun get(lineId: String): LineColorEntity?
}
