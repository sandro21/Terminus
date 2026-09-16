package com.terminus.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [StationEntity::class, DepartureEntity::class, BundleMetaEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class TerminusDatabase : RoomDatabase() {
    abstract fun transitDao(): TransitDao

    companion object {
        @Volatile private var instance: TerminusDatabase? = null

        fun get(context: Context): TerminusDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                TerminusDatabase::class.java,
                "terminus.db",
            ).build().also { instance = it }
        }
    }
}
