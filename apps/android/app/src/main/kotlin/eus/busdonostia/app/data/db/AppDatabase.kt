package eus.busdonostia.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Base de datos local. Sin migración destructiva: cada cambio de esquema exige migración. */
@Database(
    entities = [FavoriteEntity::class, AlertEntity::class, RecentStopEntity::class, LineColorEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun favorites(): FavoriteDao
    abstract fun alerts(): AlertDao
    abstract fun recentStops(): RecentStopDao
    abstract fun lineColors(): LineColorDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "busdonostia.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}

/**
 * v2: los avisos pasan a ser de un solo uso. Se borran los recurrentes y los que ya
 * avisaron (`lastTriggeredAtMs`), y desaparecen las columnas de frecuencia, días, franja y posponer.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DELETE FROM `alerts` WHERE `frequency` <> 'ONCE' OR `lastTriggeredAtMs` IS NOT NULL")
        db.execSQL(
            "CREATE TABLE `alerts_v2` (`id` TEXT NOT NULL, `stopId` TEXT NOT NULL, `stopName` TEXT NOT NULL, " +
                "`lineId` TEXT NOT NULL, `destination` TEXT, `minutesAhead` INTEGER NOT NULL, `active` INTEGER NOT NULL, " +
                "`createdAtMs` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "INSERT INTO `alerts_v2` SELECT `id`, `stopId`, `stopName`, `lineId`, `destination`, `minutesAhead`, " +
                "`active`, `createdAtMs` FROM `alerts`",
        )
        db.execSQL("DROP TABLE `alerts`")
        db.execSQL("ALTER TABLE `alerts_v2` RENAME TO `alerts`")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_alerts_stopId_lineId` ON `alerts` (`stopId`, `lineId`)")
    }
}
