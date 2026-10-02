package com.resqlink.feature.settings

import androidx.lifecycle.ViewModel
import com.resqlink.core.util.attemptOperation
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.lifecycle.viewModelScope
import com.resqlink.domain.model.AppSettings
import com.resqlink.domain.model.EmergencyMessageContent
import com.resqlink.domain.model.EmergencyProfile
import com.resqlink.domain.model.SystemStatus
import com.resqlink.domain.model.ThemePreference
import com.resqlink.domain.model.buildEmergencyMessage
import com.resqlink.domain.model.mostTrusted
import com.resqlink.domain.repository.ContactRepository
import com.resqlink.domain.repository.DataControlRepository
import com.resqlink.domain.repository.EmergencyRepository
import com.resqlink.domain.repository.LocationRepository
import com.resqlink.domain.repository.LocationResult
import com.resqlink.domain.repository.ProfileRepository
import com.resqlink.domain.repository.SettingsRepository
import com.resqlink.domain.repository.SystemStatusRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val system: SystemStatus = SystemStatus(),
    val hasActiveEmergency: Boolean = false,
    val userMessage: String? = null,
    /** Builds the full SOS body for a candidate message, so the edit dialog can show the real result. */
    val messagePreview: (String) -> String = { buildEmergencyMessage(EmergencyMessageContent(configuredMessage = it)) },
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    statusRepository: SystemStatusRepository,
    emergencyRepository: EmergencyRepository,
    private val dataControlRepository: DataControlRepository,
    profileRepository: ProfileRepository,
    contactRepository: ContactRepository,
    locationRepository: LocationRepository,
) : ViewModel() {
    private val message = MutableStateFlow<String?>(null)

    /** Cached fix for a truthful preview link only; never blocks on a GPS acquisition. */
    private val previewLocation = flow { emit(locationRepository.lastKnownLocation()) }
        .map { (it as? LocationResult.Available)?.snapshot }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val envelope = combine(
        profileRepository.observeProfile(),
        contactRepository.observeContacts(),
        previewLocation,
    ) { profile, contacts, location ->
        Triple(profile, contacts.mostTrusted(), location)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Triple(EmergencyProfile(), null, null))

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.settings,
        statusRepository.status,
        emergencyRepository.observeActive(),
        envelope,
        message,
    ) { settings, system, active, (profile, primaryContact, location), userMessage ->
        SettingsUiState(
            settings = settings,
            system = system,
            hasActiveEmergency = active != null,
            userMessage = userMessage,
            messagePreview = { candidate ->
                buildEmergencyMessage(
                    EmergencyMessageContent(
                        configuredMessage = candidate,
                        profile = profile,
                        primaryContact = primaryContact,
                        location = location,
                    ),
                )
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    private fun update(action: suspend () -> Unit) = viewModelScope.launch {
        attemptOperation(action).onFailure { message.value = "Settings could not be saved. Please try again." }
    }

    fun setTheme(theme: ThemePreference) = update { settingsRepository.setTheme(theme) }
    fun setConfirmation(required: Boolean) = update { settingsRepository.setConfirmation(required) }
    fun setMessage(message: String) = update { settingsRepository.setEmergencyMessage(message) }
    fun clearAll() = viewModelScope.launch {
        attemptOperation { dataControlRepository.clearAll() }
            .onFailure { message.value = "Data could not be cleared. Stop any active emergency and try again." }
    }
}
