package eus.busdonostia.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import eus.busdonostia.app.domain.AlertMode
import eus.busdonostia.app.domain.AlertRule
import kotlinx.coroutines.flow.Flow

/** Aviso persistido (un solo uso: se borra al avisar). Índice único (stopId, lineId): un aviso por (parada, línea). */
@Entity(tableName = "alerts", indices = [Index(value = ["stopId", "lineId"], unique = true)])
data class AlertEntity(
    @PrimaryKey val id: String,
    val stopId: String,
    val stopName: String,
    val lineId: String,
    val destination: String?,
    val minutesAhead: Int,
    val active: Boolean,
    val createdAtMs: Long,
    @ColumnInfo(defaultValue = "NOTIFICATION")
    val mode: String = "NOTIFICATION",
)

fun AlertRule.toEntity() = AlertEntity(
    id = id,
    stopId = stopId,
    stopName = stopName,
    lineId = lineId,
    destination = destination,
    minutesAhead = minutesAhead,
    active = active,
    createdAtMs = createdAtMs,
    mode = mode.name,
)

fun AlertEntity.toDomain() = AlertRule(
    id = id,
    stopId = stopId,
    stopName = stopName,
    lineId = lineId,
    destination = destination,
    minutesAhead = minutesAhead,
    active = active,
    createdAtMs = createdAtMs,
    mode = when (mode) {
        AlertMode.ALARM.name -> AlertMode.ALARM
        else -> AlertMode.NOTIFICATION
    },
)

@Dao
interface AlertDao {
    /** Un segundo aviso para la misma (parada, línea) lanza `SQLiteConstraintException`. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(e: AlertEntity)

    @Update
    suspend fun update(e: AlertEntity)

    @Query("SELECT * FROM alerts WHERE stopId = :stopId AND lineId = :lineId")
    suspend fun findByStopAndLine(stopId: String, lineId: String): AlertEntity?

    @Query("SELECT * FROM alerts WHERE id = :id")
    suspend fun findById(id: String): AlertEntity?

    @Query("DELETE FROM alerts WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM alerts ORDER BY createdAtMs")
    suspend fun getAll(): List<AlertEntity>

    @Query("SELECT * FROM alerts ORDER BY createdAtMs")
    fun observeAll(): Flow<List<AlertEntity>>
}
