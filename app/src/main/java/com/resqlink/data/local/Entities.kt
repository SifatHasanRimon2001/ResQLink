package com.resqlink.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "emergency_contacts", indices = [Index(value = ["phoneNumber"], unique = true)])
data class EmergencyContactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phoneNumber: String,
    val email: String,
    val priority: Int,
    val enabled: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "emergency_profile")
data class EmergencyProfileEntity(
    @PrimaryKey val id: Int = 1,
    val name: String,
    val notes: String,
    val homeAddress: String,
    val importantInformation: String,
    val preferredLanguage: String,
    val updatedAt: Long,
)

@Entity(tableName = "emergency_events", indices = [Index("status")])
data class EmergencyEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val endedAt: Long? = null,
    val status: String,
    val locationStatus: String,
    val batteryLevel: Int,
)

@Entity(
    tableName = "alert_attempts",
    foreignKeys = [ForeignKey(
        entity = EmergencyEventEntity::class,
        parentColumns = ["id"],
        childColumns = ["emergencyEventId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("emergencyEventId")],
)
data class AlertAttemptEntity(
    @PrimaryKey val id: String,
    val emergencyEventId: Long,
    val recipientId: Long,
    val channel: String,
    val createdAt: Long,
    val attemptCount: Int,
    val status: String,
    val lastAttemptAt: Long?,
    val errorCode: String?,
)

@Entity(
    tableName = "location_points",
    foreignKeys = [ForeignKey(
        entity = EmergencyEventEntity::class,
        parentColumns = ["id"],
        childColumns = ["emergencyEventId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("emergencyEventId")],
)
data class LocationPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val emergencyEventId: Long,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val timestamp: Long,
    val provider: String,
)
