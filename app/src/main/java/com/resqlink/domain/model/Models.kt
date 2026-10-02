package com.resqlink.domain.model

data class EmergencyContact(
    val id: Long = 0,
    val name: String,
    val phoneNumber: String,
    val email: String = "",
    val priority: Int = 1,
    val enabled: Boolean = true,
)

data class EmergencyProfile(
    val name: String = "",
    val notes: String = "",
    val homeAddress: String = "",
    val importantInformation: String = "",
    val preferredLanguage: String = "English",
) {
    val isConfigured: Boolean get() = name.isNotBlank() && notes.isNotBlank()
}

enum class EventStatus { ACTIVATING, ACTIVE, COMPLETED, CANCELLED }
enum class LocationStatus { NOT_REQUESTED, CAPTURED, PERMISSION_DENIED, UNAVAILABLE }
enum class AlertStatus { PREPARED, DISPATCHED, ACTION_REQUIRED, FAILED, CANCELLED, UNKNOWN }

data class EmergencyEvent(
    val id: Long,
    val startedAt: Long,
    val endedAt: Long?,
    val status: EventStatus,
    val locationStatus: LocationStatus,
    val batteryLevel: Int,
    val alertAttempts: Int,
)

data class LocationSnapshot(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val capturedAt: Long,
    val provider: String,
)

enum class ThemePreference { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val onboardingComplete: Boolean = false,
    val theme: ThemePreference = ThemePreference.SYSTEM,
    val requireSosConfirmation: Boolean = true,
    val emergencyMessage: String = DEFAULT_EMERGENCY_MESSAGE,
) {
    companion object {
        const val DEFAULT_EMERGENCY_MESSAGE =
            "I may need assistance. ResQLink SOS has been activated. Please contact me as soon as possible."
    }
}

sealed interface EmergencyState {
    data object Idle : EmergencyState
    data object Confirming : EmergencyState
    data object Activating : EmergencyState
    data class Active(val event: EmergencyEvent) : EmergencyState
    data class Failed(val reason: EmergencyFailure) : EmergencyState
}

enum class EmergencyFailure {
    DATABASE_ERROR,
    ACTIVATION_FAILED,
}

data class SystemStatus(
    val networkAvailable: Boolean = false,
    val batteryLevel: Int = 100,
    val locationPermissionGranted: Boolean = false,
    val notificationPermissionGranted: Boolean = false,
    val bluetoothAvailable: Boolean = false,
)

enum class BatteryBand { NORMAL, MODERATE, LOW, CRITICAL }

fun batteryBand(level: Int): BatteryBand = when {
    level < 10 -> BatteryBand.CRITICAL
    level < 20 -> BatteryBand.LOW
    level <= 50 -> BatteryBand.MODERATE
    else -> BatteryBand.NORMAL
}

fun List<EmergencyContact>.mostTrusted(): EmergencyContact? =
    asSequence()
        .filter(EmergencyContact::enabled)
        .minWithOrNull(compareBy<EmergencyContact>({ it.priority }, { it.id }, { it.name.lowercase() }))

data class ContactValidation(
    val normalizedPhone: String,
    val nameError: Boolean,
    val phoneError: Boolean,
) {
    val valid: Boolean get() = !nameError && !phoneError
}

fun validateContact(name: String, phone: String): ContactValidation {
    // Ignore presentation separators only, never dial codes, extensions or URI payloads.
    val trimmed = phone.trim()
    val normalized = trimmed.filterNot { it == ' ' || it == '-' || it == '(' || it == ')' || it == '.' }
    val validPhone = trimmed.length <= 64 && normalized.matches(Regex("\\+?[0-9]{7,15}"))
    return ContactValidation(
        normalizedPhone = normalized,
        nameError = name.trim().isEmpty() || name.length > 200 || name.any(Char::isISOControl),
        phoneError = !validPhone,
    )
}

fun normalizeEmergencyMessage(message: String): String =
    message.trim().take(500).ifBlank { AppSettings.DEFAULT_EMERGENCY_MESSAGE }
