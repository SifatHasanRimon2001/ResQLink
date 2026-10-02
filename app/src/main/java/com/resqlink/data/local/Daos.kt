package com.resqlink.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {
    @Query("SELECT * FROM emergency_contacts ORDER BY priority ASC, name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<EmergencyContactEntity>>

    @Query("SELECT * FROM emergency_contacts WHERE enabled = 1 ORDER BY priority ASC, id ASC")
    suspend fun enabledContacts(): List<EmergencyContactEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM emergency_contacts WHERE phoneNumber = :phone AND id != :excludeId)")
    suspend fun phoneExists(phone: String, excludeId: Long): Boolean

    @Query("SELECT * FROM emergency_contacts WHERE id = :contactId")
    suspend fun find(contactId: Long): EmergencyContactEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(contact: EmergencyContactEntity): Long

    @Update suspend fun update(contact: EmergencyContactEntity)
    @Delete suspend fun delete(contact: EmergencyContactEntity)

    @Query("UPDATE emergency_contacts SET priority = 1")
    suspend fun demoteAll()

    @Query("UPDATE emergency_contacts SET priority = 0 WHERE id = :contactId AND enabled = 1")
    suspend fun makePrimary(contactId: Long)

    @Query("SELECT COUNT(*) FROM emergency_contacts WHERE enabled = 1 AND priority = 0")
    suspend fun enabledPrimaryCount(): Int

    @Query("UPDATE emergency_contacts SET priority = 0 WHERE id = (SELECT id FROM emergency_contacts WHERE enabled = 1 ORDER BY priority ASC, id ASC LIMIT 1)")
    suspend fun promoteFirstEnabled()

    @Query("DELETE FROM emergency_contacts") suspend fun clear()
}

@Dao
interface ProfileDao {
    @Query("SELECT * FROM emergency_profile WHERE id = 1")
    fun observe(): Flow<EmergencyProfileEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(profile: EmergencyProfileEntity)

    @Query("DELETE FROM emergency_profile") suspend fun clear()
}

@Dao
interface EmergencyDao {
    @Query("SELECT * FROM emergency_events ORDER BY startedAt DESC")
    fun observeHistory(): Flow<List<EmergencyEventEntity>>

    @Query("SELECT * FROM emergency_events WHERE status IN ('ACTIVATING', 'ACTIVE') ORDER BY startedAt DESC LIMIT 1")
    fun observeActive(): Flow<EmergencyEventEntity?>

    @Query("SELECT * FROM emergency_events WHERE status IN ('ACTIVATING', 'ACTIVE') ORDER BY startedAt DESC LIMIT 1")
    suspend fun active(): EmergencyEventEntity?

    @Insert suspend fun insertEvent(event: EmergencyEventEntity): Long
    @Update suspend fun updateEvent(event: EmergencyEventEntity)

    /** Serializes the active-event check and insert so simultaneous triggers share one incident. */
    @Transaction
    suspend fun insertOrGetActive(event: EmergencyEventEntity): EmergencyEventEntity {
        active()?.let { return it }
        return event.copy(id = insertEvent(event))
    }

    @Query("UPDATE emergency_events SET status = :status, locationStatus = :locationStatus WHERE id = :eventId AND status = 'ACTIVATING'")
    suspend fun activate(eventId: Long, status: String, locationStatus: String)

    @Query("UPDATE emergency_events SET locationStatus = :locationStatus WHERE id = :eventId AND status IN ('ACTIVATING', 'ACTIVE')")
    suspend fun updateLocationStatus(eventId: Long, locationStatus: String)

    @Query("UPDATE emergency_events SET status = :status, endedAt = :endedAt WHERE id = :eventId AND status IN ('ACTIVATING', 'ACTIVE')")
    suspend fun finishEvent(eventId: Long, status: String, endedAt: Long)

    @Query("UPDATE alert_attempts SET status = 'CANCELLED' WHERE emergencyEventId = :eventId AND status = 'PREPARED'")
    suspend fun cancelPreparedAlerts(eventId: Long)

    @Transaction
    suspend fun complete(eventId: Long, status: String, endedAt: Long) {
        finishEvent(eventId, status, endedAt)
        cancelPreparedAlerts(eventId)
    }

    @Query("SELECT COUNT(*) FROM alert_attempts WHERE emergencyEventId = :eventId")
    suspend fun alertCount(eventId: Long): Int

    @Query("SELECT * FROM alert_attempts WHERE emergencyEventId = :eventId ORDER BY createdAt ASC")
    suspend fun alertsForEvent(eventId: Long): List<AlertAttemptEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAlerts(attempts: List<AlertAttemptEntity>)

    /** Freezes the first recipient snapshot for an incident and ignores repeated activation calls. */
    @Transaction
    suspend fun insertAlertsIfAbsent(eventId: Long, attempts: List<AlertAttemptEntity>): Boolean {
        if (attempts.isEmpty() || active()?.let { it.id == eventId && it.status == "ACTIVATING" } != true || alertCount(eventId) != 0) return false
        insertAlerts(attempts)
        activate(eventId, "ACTIVE", "NOT_REQUESTED")
        return true
    }

    // Persist before external actions. Unknown outcomes after process death must not be retried.
    @Query("UPDATE alert_attempts SET status = 'UNKNOWN', attemptCount = 1, lastAttemptAt = :attemptedAt WHERE emergencyEventId = :eventId AND status = 'PREPARED' AND EXISTS(SELECT 1 FROM emergency_events WHERE id = :eventId AND status = 'ACTIVE')")
    suspend fun claimDispatch(eventId: Long, attemptedAt: Long): Int

    @Query("UPDATE alert_attempts SET status = :status, lastAttemptAt = :attemptedAt, errorCode = :errorCode WHERE emergencyEventId = :eventId AND recipientId IN (:recipientIds) AND status = 'UNKNOWN'")
    suspend fun updateAlertOutcomes(
        eventId: Long,
        recipientIds: List<Long>,
        status: String,
        attemptedAt: Long,
        errorCode: String?,
    )

    @Insert suspend fun insertLocation(point: LocationPointEntity)

    @Transaction
    suspend fun insertLocationIfActive(point: LocationPointEntity) {
        if (active()?.id == point.emergencyEventId) insertLocation(point)
    }

    @Query("SELECT COUNT(*) FROM location_points WHERE emergencyEventId = :eventId")
    suspend fun locationCount(eventId: Long): Int

    @Query("DELETE FROM emergency_events WHERE id = :eventId AND status NOT IN ('ACTIVATING', 'ACTIVE')")
    suspend fun deleteEvent(eventId: Long)

    @Query("DELETE FROM emergency_events") suspend fun clear()
}
