package eus.busdonostia.app.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import eus.busdonostia.app.domain.AlertFrequency
import eus.busdonostia.app.domain.AlertRule
import kotlinx.coroutines.flow.Flow

/** Aviso persistido. Índice único (stopId, lineId): un aviso por (parada, línea). */
@Entity(tableName = "alerts", indices = [Index(value = ["stopId", "lineId"], unique = true)])
data class AlertEntity(
    @PrimaryKey val id: String,
    val stopId: String,
    val stopName: String,
    val lineId: String,
    val destination: String?,
    val minutesAhead: Int,
    val frequency: String,
    /** Días ISO ordenados, p. ej. "1,3,5"; vacío si no hay. */
    val daysOfWeek: String,
    val windowFrom: String?,
    val windowTo: String?,
    val active: Boolean,
    val createdAtMs: Long,
    val snoozedUntilMs: Long?,
    val lastTriggeredAtMs: Long?,
)

fun AlertRule.toEntity() = AlertEntity(
    id = id,
    stopId = stopId,
    stopName = stopName,
    lineId = lineId,
    destination = destination,
    minutesAhead = minutesAhead,
    frequency = frequency.name,
    daysOfWeek = daysOfWeek.sorted().joinToString(","),
    windowFrom = windowFrom,
    windowTo = windowTo,
    active = active,
    createdAtMs = createdAtMs,
    snoozedUntilMs = snoozedUntilMs,
    lastTriggeredAtMs = lastTriggeredAtMs,
)

fun AlertEntity.toDomain() = AlertRule(
    id = id,
    stopId = stopId,
    stopName = stopName,
    lineId = lineId,
    destination = destination,
    minutesAhead = minutesAhead,
    frequency = AlertFrequency.valueOf(frequency),
    daysOfWeek = if (daysOfWeek.isEmpty()) emptySet() else daysOfWeek.split(",").map(String::toInt).toSet(),
    windowFrom = windowFrom,
    windowTo = windowTo,
    active = active,
    createdAtMs = createdAtMs,
    snoozedUntilMs = snoozedUntilMs,
    lastTriggeredAtMs = lastTriggeredAtMs,
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
