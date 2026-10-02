package com.resqlink.domain.usecase

import com.resqlink.domain.model.EmergencyContact
import com.resqlink.domain.model.EmergencyEvent
import com.resqlink.domain.model.EventStatus
import com.resqlink.domain.model.LocationSnapshot
import com.resqlink.domain.model.LocationStatus
import com.resqlink.domain.repository.ContactRepository
import com.resqlink.domain.repository.ProfileRepository
import com.resqlink.domain.model.validateContact
import kotlinx.coroutines.flow.first
import com.resqlink.domain.repository.EmergencyRepository
import com.resqlink.domain.repository.LocationRepository
import com.resqlink.domain.repository.LocationResult
import com.resqlink.notification.EmergencyNotifier
import javax.inject.Inject

data class ActivationResult(
    val eventId: Long,
    val recipients: List<EmergencyContact>,
)

class ActivateEmergency @Inject constructor(
    private val emergencyRepository: EmergencyRepository,
    private val contactRepository: ContactRepository,
    private val notifier: EmergencyNotifier,
    private val profileRepository: ProfileRepository,
) {
    suspend operator fun invoke(batteryLevel: Int): ActivationResult {
        // Recheck durable data after the potentially long Android permission flow.
        check(profileRepository.observeProfile().first().isConfigured) { "Emergency profile is incomplete." }
        val contacts = contactRepository.enabledContacts().filter { it.enabled }
        check(contacts.isNotEmpty() && contacts.all { validateContact(it.name, it.phoneNumber).valid }) {
            "At least one valid enabled contact is required."
        }
        val event = emergencyRepository.createOrGetActive(batteryLevel.coerceIn(0, 100))
        val prepared = emergencyRepository.prepareAlerts(event.id, contacts)
        if (prepared) notifier.show(event.id)
        // A recovered incident is displayed, but cannot create another dispatch draft.
        return ActivationResult(eventId = event.id, recipients = if (prepared) contacts else emptyList())
    }
}

/** Captures location independently so a slow GPS fix never delays SMS or calling. */
class CaptureEmergencyLocation @Inject constructor(
    private val emergencyRepository: EmergencyRepository,
    private val locationRepository: LocationRepository,
) {
    /** Returns the captured fix so a not-yet-sent draft can be upgraded with better coordinates. */
    suspend operator fun invoke(eventId: Long): LocationSnapshot? {
        val locationResult = locationRepository.currentLocation()
        val locationStatus = when (locationResult) {
            is LocationResult.Available -> {
                emergencyRepository.recordLocation(eventId, locationResult.snapshot)
                LocationStatus.CAPTURED
            }
            LocationResult.PermissionDenied -> LocationStatus.PERMISSION_DENIED
            LocationResult.Unavailable -> LocationStatus.UNAVAILABLE
        }
        emergencyRepository.updateLocationStatus(eventId, locationStatus)
        return (locationResult as? LocationResult.Available)?.snapshot
    }
}

class CompleteEmergency @Inject constructor(
    private val emergencyRepository: EmergencyRepository,
    private val notifier: EmergencyNotifier,
) {
    suspend operator fun invoke(event: EmergencyEvent) {
        emergencyRepository.complete(event.id, cancelled = true)
        notifier.cancel()
    }
}
