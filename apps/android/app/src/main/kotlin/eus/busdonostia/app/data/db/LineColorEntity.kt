package eus.busdonostia.app.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Último `linea.color` visto en vivo (el catálogo no trae colores). `lineId` = código público. */
@Entity(tableName = "line_colors")
data class LineColorEntity(
    @PrimaryKey val lineId: String,
    val colorArgb: Int,
    val seenAtMs: Long,
)

@Dao
interface LineColorDao {
    @Upsert
    suspend fun upsertAll(list: List<LineColorEntity>)

    @Query("SELECT * FROM line_colors")
    fun observeAll(): Flow<List<LineColorEntity>>

    @Query("SELECT * FROM line_colors WHERE lineId = :lineId")
    suspend fun get(lineId: String): LineColorEntity?
}
