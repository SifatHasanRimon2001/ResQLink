package com.resqlink.feature.profile

import androidx.lifecycle.ViewModel
import com.resqlink.core.util.attemptOperation
import androidx.lifecycle.viewModelScope
import com.resqlink.domain.model.EmergencyMessageContent
import com.resqlink.domain.model.EmergencyProfile
import com.resqlink.domain.model.buildEmergencyMessage
import com.resqlink.domain.model.mostTrusted
import com.resqlink.domain.repository.ContactRepository
import com.resqlink.domain.repository.LocationRepository
import com.resqlink.domain.repository.LocationResult
import com.resqlink.domain.repository.ProfileRepository
import com.resqlink.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val repository: ProfileRepository,
    contactRepository: ContactRepository,
    settingsRepository: SettingsRepository,
    private val locationRepository: LocationRepository,
) : ViewModel() {
    val profile: StateFlow<EmergencyProfile> = repository.observeProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EmergencyProfile())

    /**
     * Everything the message needs apart from the profile, which the form edits locally.
     * Eagerly started because [messagePreview] reads `.value` imperatively from composition
     * rather than collecting, so `WhileSubscribed` would leave it permanently empty.
     */
    private val envelope = combine(
        settingsRepository.settings,
        contactRepository.observeContacts(),
    ) { settings, contacts -> settings.emergencyMessage to contacts.mostTrusted() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "" to null)

    private val message = MutableStateFlow<String?>(null)
    val userMessage = message.asStateFlow()
    private val isSaving = MutableStateFlow(false)
    val saving = isSaving.asStateFlow()

    /**
     * The cached fix used only to render a truthful preview link. It never waits for a
     * GPS acquisition, and the real SOS path resolves its own location independently.
     */
    private val previewLocation = flow { emit(locationRepository.lastKnownLocation()) }
        .map { (it as? LocationResult.Available)?.snapshot }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * The exact body recipients receive, rebuilt as the form is edited so the user
     * can see what will be sent.
     */
    fun messagePreview(draft: EmergencyProfile): String {
        val (configuredMessage, primaryContact) = envelope.value
        return buildEmergencyMessage(
            EmergencyMessageContent(
                configuredMessage = configuredMessage,
                profile = draft,
                primaryContact = primaryContact,
                location = previewLocation.value,
            ),
        )
    }

    fun save(profile: EmergencyProfile) {
        if (isSaving.value) return
        isSaving.value = true
        viewModelScope.launch {
            try {
                attemptOperation { repository.save(profile) }
                    .onSuccess { message.value = "Emergency profile saved." }
                    .onFailure { message.value = "Profile could not be saved. Please try again." }
            } finally {
                isSaving.value = false
            }
        }
    }
}