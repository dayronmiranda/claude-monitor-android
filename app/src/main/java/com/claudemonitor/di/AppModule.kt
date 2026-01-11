package com.claudemonitor.di

import android.content.Context
import androidx.room.Room
import com.claudemonitor.data.local.AppDatabase
import com.claudemonitor.data.local.DriverDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "claude_monitor_db"
        ).build()
    }

    @Provides
    @Singleton
    fun provideDriverDao(database: AppDatabase): DriverDao {
        return database.driverDao()
    }
}
