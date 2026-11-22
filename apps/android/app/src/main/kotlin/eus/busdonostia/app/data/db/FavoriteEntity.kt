package eus.busdonostia.app.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import eus.busdonostia.app.domain.Favorite
import eus.busdonostia.app.domain.FavoriteType
import kotlinx.coroutines.flow.Flow

/** Favorito persistido. `id` = `favoriteId(type, stopId, lineId)` (dedup determinista). */
@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val id: String,
    val type: String,
    val stopId: String?,
    val lineId: String?,
    val createdAtMs: Long,
)

fun Favorite.toEntity() = FavoriteEntity(id, type.name, stopId, lineId, createdAtMs)

fun FavoriteEntity.toDomain() = Favorite(FavoriteType.valueOf(type), stopId, lineId, createdAtMs)

@Dao
interface FavoriteDao {
    /** Re-añadir el mismo favorito no hace nada y conserva el `createdAtMs` original. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(e: FavoriteEntity): Long

    @Query("DELETE FROM favorites WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM favorites ORDER BY createdAtMs DESC")
    fun observeAll(): Flow<List<FavoriteEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE id = :id)")
    suspend fun exists(id: String): Boolean
}
