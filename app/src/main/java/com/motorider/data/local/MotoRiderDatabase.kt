package com.motorider.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.motorider.data.entity.RouteEntity

/**
 * Room database for MotoRider app.
 * Currently contains only the routes table.
 */
@Database(
    entities = [RouteEntity::class],
    version = 1,
    exportSchema = true
)
abstract class MotoRiderDatabase : RoomDatabase() {
    
    abstract fun routeDao(): RouteDao
    
    companion object {
        const val DATABASE_NAME = "motorider_database"
    }
}
