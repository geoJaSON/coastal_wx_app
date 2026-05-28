package com.example.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

// 1. Entities
@Entity(tableName = "saved_locations")
data class SavedLocation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val isGPS: Boolean = false
)

@Entity(tableName = "weather_cache")
data class WeatherCache(
    @PrimaryKey val locationId: Long,
    val currentTemp: Double,
    val feelsLike: Double,
    val humidity: Double,
    val windSpeed: Double,
    val windDirection: Double,
    val uvIndex: Double,
    val visibility: Double,
    val pressure: Double,
    val weatherCode: Int,
    val lastUpdatedMillis: Long,
    val hourlyJson: String,  // Serialization of compact lists of hourly items
    val dailyJson: String    // Serialization of compact lists of daily items
)

@Entity(tableName = "storm_cache")
data class StormCache(
    @PrimaryKey val id: String,
    val name: String,
    val basin: String,          // Atlantic, Eastern Pacific, Central Pacific
    val type: String,           // Tropical Depression, Tropical Storm, Hurricane
    val category: Int,          // Saffir-Simpson (1-5, or 0 if none)
    val latitude: Double,
    val longitude: Double,
    val windSpeedKt: Double,
    val pressureMb: Double,
    val direction: String,
    val speedMph: Double,
    val trackJson: String,      // JSON coordinates of past & future intervals
    val advisoryText: String,
    val discussionText: String,
    val detailUrl: String
)

@Entity(tableName = "active_alerts")
data class ActiveAlert(
    @PrimaryKey val id: String,
    val title: String,
    val severity: String,       // minor, moderate, severe, extreme
    val description: String,
    val dismissed: Boolean = false
)

// 2. DAOs
@Dao
interface SavedLocationDao {
    @Query("SELECT * FROM saved_locations ORDER BY isGPS DESC, id ASC")
    fun getAllLocationsFlow(): Flow<List<SavedLocation>>

    @Query("SELECT * FROM saved_locations WHERE id = :id LIMIT 1")
    suspend fun getLocationById(id: Long): SavedLocation?

    @Query("SELECT * FROM saved_locations WHERE isGPS = 1 LIMIT 1")
    suspend fun getGPSLocation(): SavedLocation?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocation(location: SavedLocation): Long

    @Delete
    suspend fun deleteLocation(location: SavedLocation)

    @Query("DELETE FROM saved_locations WHERE isGPS = 1")
    suspend fun deleteGPSLocation()
}

@Dao
interface WeatherCacheDao {
    @Query("SELECT * FROM weather_cache WHERE locationId = :locationId LIMIT 1")
    suspend fun getWeatherCacheByLocation(locationId: Long): WeatherCache?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeatherCache(cache: WeatherCache)

    @Query("DELETE FROM weather_cache WHERE locationId = :locationId")
    suspend fun deleteWeatherCache(locationId: Long)
}

@Dao
interface StormCacheDao {
    @Query("SELECT * FROM storm_cache")
    fun getAllStormsFlow(): Flow<List<StormCache>>

    @Query("SELECT * FROM storm_cache")
    suspend fun getAllStorms(): List<StormCache>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStorms(storms: List<StormCache>)

    @Query("DELETE FROM storm_cache")
    suspend fun clearAllStorms()
}

@Dao
interface ActiveAlertDao {
    @Query("SELECT * FROM active_alerts WHERE dismissed = 0")
    fun getActiveAlertsFlow(): Flow<List<ActiveAlert>>

    @Query("SELECT * FROM active_alerts")
    suspend fun getAllAlerts(): List<ActiveAlert>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlerts(alerts: List<ActiveAlert>)

    @Query("UPDATE active_alerts SET dismissed = 1 WHERE id = :id")
    suspend fun dismissAlert(id: String)

    @Query("DELETE FROM active_alerts WHERE id = :id")
    suspend fun deleteAlertById(id: String)

    @Query("DELETE FROM active_alerts WHERE id LIKE :prefix || '%'")
    suspend fun deleteAlertsByPrefix(prefix: String)

    @Query("DELETE FROM active_alerts")
    suspend fun clearAllAlerts()
}

// 3. Database
@Database(
    entities = [SavedLocation::class, WeatherCache::class, StormCache::class, ActiveAlert::class],
    version = 1,
    exportSchema = false
)
abstract class WeatherDatabase : RoomDatabase() {
    abstract val savedLocationDao: SavedLocationDao
    abstract val weatherCacheDao: WeatherCacheDao
    abstract val stormCacheDao: StormCacheDao
    abstract val activeAlertDao: ActiveAlertDao

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
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
