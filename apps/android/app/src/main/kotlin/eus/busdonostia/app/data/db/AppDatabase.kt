package eus.busdonostia.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/** Base de datos local. Sin migración destructiva: cada cambio de esquema exige migración. */
@Database(
    entities = [FavoriteEntity::class, AlertEntity::class, RecentStopEntity::class, LineColorEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun favorites(): FavoriteDao
    abstract fun alerts(): AlertDao
    abstract fun recentStops(): RecentStopDao
    abstract fun lineColors(): LineColorDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "busdonostia.db").build()
    }
}
