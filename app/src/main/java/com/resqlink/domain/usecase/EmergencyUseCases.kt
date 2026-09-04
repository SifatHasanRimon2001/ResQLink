package com.resqlink.domain.usecase

import com.resqlink.domain.model.EmergencyContact
import com.resqlink.domain.model.EmergencyEvent
import com.resqlink.domain.model.EventStatus
import com.resqlink.domain.model.LocationStatus
import com.resqlink.domain.repository.ContactRepository
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
) {
    suspend operator fun invoke(batteryLevel: Int): ActivationResult {
        val event = emergencyRepository.createOrGetActive(batteryLevel)
        val contacts = contactRepository.enabledContacts()
        emergencyRepository.prepareAlerts(event.id, contacts)
        if (event.status == EventStatus.ACTIVATING) {
            emergencyRepository.markActive(event.id, LocationStatus.NOT_REQUESTED)
            notifier.show(event.id)
        }
        return ActivationResult(eventId = event.id, recipients = contacts)
    }
}

/** Captures location independently so a slow GPS fix never delays SMS or calling. */
class CaptureEmergencyLocation @Inject constructor(
    private val emergencyRepository: EmergencyRepository,
    private val locationRepository: LocationRepository,
) {
    suspend operator fun invoke(eventId: Long) {
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
