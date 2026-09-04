package com.resqlink.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        EmergencyContactEntity::class,
        EmergencyProfileEntity::class,
        EmergencyEventEntity::class,
        AlertAttemptEntity::class,
        LocationPointEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class ResQLinkDatabase : RoomDatabase() {
    abstract fun contactDao(): ContactDao
    abstract fun profileDao(): ProfileDao
    abstract fun emergencyDao(): EmergencyDao
}
