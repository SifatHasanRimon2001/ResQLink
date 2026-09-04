package com.resqlink.domain.repository

import com.resqlink.domain.model.AppSettings
import com.resqlink.domain.model.EmergencyContact
import com.resqlink.domain.model.EmergencyEvent
import com.resqlink.domain.model.EmergencyProfile
import com.resqlink.domain.model.LocationSnapshot
import com.resqlink.domain.model.LocationStatus
import com.resqlink.domain.model.SystemStatus
import com.resqlink.domain.model.ThemePreference
import kotlinx.coroutines.flow.Flow

interface ContactRepository {
    fun observeContacts(): Flow<List<EmergencyContact>>
    suspend fun enabledContacts(): List<EmergencyContact>
    suspend fun save(contact: EmergencyContact): SaveContactResult
    suspend fun setPrimary(contactId: Long)
    suspend fun delete(contact: EmergencyContact)
}

enum class SaveContactResult { SAVED, DUPLICATE }

interface ProfileRepository {
    fun observeProfile(): Flow<EmergencyProfile>
    suspend fun save(profile: EmergencyProfile)
}

interface EmergencyRepository {
    fun observeHistory(): Flow<List<EmergencyEvent>>
    fun observeActive(): Flow<EmergencyEvent?>
    suspend fun createOrGetActive(batteryLevel: Int): EmergencyEvent
    suspend fun markActive(eventId: Long, locationStatus: LocationStatus)
    suspend fun updateLocationStatus(eventId: Long, locationStatus: LocationStatus)
    suspend fun recordLocation(eventId: Long, location: LocationSnapshot)
    suspend fun prepareAlerts(eventId: Long, recipients: List<EmergencyContact>)
    suspend fun recordAlertOutcomes(eventId: Long, dispatchedRecipientIds: List<Long>, failedRecipientIds: List<Long>)
    suspend fun complete(eventId: Long, cancelled: Boolean = false)
    suspend fun delete(eventId: Long)
}

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun completeOnboarding()
    suspend fun setTheme(theme: ThemePreference)
    suspend fun setConfirmation(required: Boolean)
    suspend fun setEmergencyMessage(message: String)
    suspend fun clearPreferences()
}

sealed interface LocationResult {
    data class Available(val snapshot: LocationSnapshot) : LocationResult
    data object PermissionDenied : LocationResult
    data object Unavailable : LocationResult
}

interface LocationRepository {
    suspend fun currentLocation(): LocationResult
}

interface SystemStatusRepository {
    val status: Flow<SystemStatus>
    fun refreshPermissions()
}

interface DataControlRepository {
    suspend fun clearAll()
}
