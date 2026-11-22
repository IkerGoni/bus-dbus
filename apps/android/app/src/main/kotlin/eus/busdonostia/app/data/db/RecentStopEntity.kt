package eus.busdonostia.app.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

const val MAX_RECENTS = 10

/** Parada vista recientemente. */
@Entity(tableName = "recent_stops")
data class RecentStopEntity(
    @PrimaryKey val stopId: String,
    val viewedAtMs: Long,
)

@Dao
abstract class RecentStopDao {
    /** Registra la visita (upsert) y deja solo las [MAX_RECENTS] más recientes. */
    @Transaction
    open suspend fun record(stopId: String, nowMs: Long) {
        upsert(RecentStopEntity(stopId, nowMs))
        trim()
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun upsert(e: RecentStopEntity)

    @Query(
        "DELETE FROM recent_stops WHERE stopId NOT IN " +
            "(SELECT stopId FROM recent_stops ORDER BY viewedAtMs DESC LIMIT 10)",
    )
    protected abstract suspend fun trim()

    @Query("SELECT * FROM recent_stops ORDER BY viewedAtMs DESC")
    abstract fun observe(): Flow<List<RecentStopEntity>>
}
