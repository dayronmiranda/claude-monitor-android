package com.claudemonitor.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [DriverEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun driverDao(): DriverDao
}
