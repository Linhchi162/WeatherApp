package com.example.weatherapp

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration // Thêm import này
import androidx.sqlite.db.SupportSQLiteDatabase // Thêm import này

@Database(entities = [WeatherData::class, WeatherDetail::class, WeatherDailyDetail::class], version = 2, exportSchema = false)
abstract class WeatherDatabase : RoomDatabase() {
    abstract fun weatherDao(): WeatherDao

    companion object {
        @Volatile
        private var INSTANCE: WeatherDatabase? = null

        fun getDatabase(context: Context): WeatherDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    WeatherDatabase::class.java,
                    "weather_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `weather_daily_detail` (
                        `detailId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `weatherDataId` INTEGER NOT NULL,
                        `time` TEXT NOT NULL,
                        `temperature_2m_max` REAL NOT NULL,
                        `temperature_2m_min` REAL NOT NULL,
                        `precipitation_probability_max` REAL NOT NULL,
                        `weather_code` INTEGER NOT NULL,
                        FOREIGN KEY(`weatherDataId`) REFERENCES `weather_data`(`id`) ON DELETE CASCADE
                    )
                """)
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_weather_daily_detail_weatherDataId` ON `weather_daily_detail` (`weatherDataId`)")
            }
        }
    }
}
