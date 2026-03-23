package com.example.weatherapp

import androidx.room.*

@Entity(tableName = "weather_cache")
data class WeatherCacheEntity(
    @PrimaryKey val id: Int = 1,
    val weatherJson: String,
    val forecastJson: String,
    val lastUpdated: Long
)

@Dao
interface WeatherDao {
    @Query("SELECT * FROM weather_cache WHERE id = 1")
    suspend fun getCache(): WeatherCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCache(cache: WeatherCacheEntity)
}

@Database(entities = [WeatherCacheEntity::class], version = 1)
abstract class WeatherDatabase : RoomDatabase() {
    abstract fun weatherDao(): WeatherDao
}
