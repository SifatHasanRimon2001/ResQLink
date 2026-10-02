package com.resqlink.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import com.resqlink.core.util.attemptOperation
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.first
import androidx.lifecycle.viewModelScope
import com.resqlink.domain.model.AppSettings
import com.resqlink.domain.model.EmergencyContact
import com.resqlink.domain.model.EmergencyEvent
import com.resqlink.domain.model.EmergencyFailure
import com.resqlink.domain.model.EmergencyProfile
import com.resqlink.domain.model.EmergencyState
import com.resqlink.domain.model.LocationStatus
import com.resqlink.domain.model.SystemStatus
import com.resqlink.domain.model.mostTrusted
import com.resqlink.domain.model.buildEmergencyMessage
import com.resqlink.domain.model.EmergencyMessageContent
import com.resqlink.domain.repository.ContactRepository
import com.resqlink.domain.repository.EmergencyRepository
import com.resqlink.domain.repository.LocationRepository
import com.resqlink.domain.repository.LocationResult
import com.resqlink.domain.repository.ProfileRepository
import com.resqlink.domain.repository.SettingsRepository
import com.resqlink.domain.repository.SystemStatusRepository
import com.resqlink.domain.usecase.ActivateEmergency
import com.resqlink.domain.usecase.CaptureEmergencyLocation
import com.resqlink.domain.usecase.CompleteEmergency
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AlertRecipient(
    val contactId: Long,
    val phoneNumber: String,
)

data class AlertDraft(
    val eventId: Long,
    val recipients: List<AlertRecipient>,
    /** Everything needed to compose the message; the body is built when the alert is actually sent. */
    val content: EmergencyMessageContent,
    val primaryPhoneNumber: String,
)

data class HomeUiState(
    val emergencyState: EmergencyState = EmergencyState.Idle,
    val contacts: List<EmergencyContact> = emptyList(),
    val profile: EmergencyProfile = EmergencyProfile(),
    val recentEvent: EmergencyEvent? = null,
    val systemStatus: SystemStatus = SystemStatus(),
    val settings: AppSettings = AppSettings(),
    val alertDraft: AlertDraft? = null,
    val userMessage: String? = null,
)

private data class PrimarySource(
    val active: EmergencyEvent?,
    val contacts: List<EmergencyContact>,
    val profile: EmergencyProfile,
)

private data class SecondarySource(
    val recent: EmergencyEvent?,
    val system: SystemStatus,
    val settings: AppSettings,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    contactRepository: ContactRepository,
    profileRepository: ProfileRepository,
    private val emergencyRepository: EmergencyRepository,
    private val settingsRepository: SettingsRepository,
    private val systemStatusRepository: SystemStatusRepository,
    private val activateEmergency: ActivateEmergency,
    private val captureEmergencyLocation: CaptureEmergencyLocation,
    private val completeEmergency: CompleteEmergency,
    private val locationRepository: LocationRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val phase = MutableStateFlow<EmergencyState>(EmergencyState.Idle)
    private val draft = MutableStateFlow<AlertDraft?>(null)
    private val message = MutableStateFlow<String?>(null)
    private val activationChannel = Channel<Unit>(Channel.CONFLATED)
    val activationRequests = activationChannel.receiveAsFlow()
    private var activationJob: Job? = null
    private var locationJob: Job? = null
    private var dispatchingEventId: Long? = null
    private var stoppedEventId: Long? = null
    private var permissionRequestPending: Boolean
        get() = savedStateHandle["permissionRequestPending"] ?: false
        set(value) { savedStateHandle["permissionRequestPending"] = value }

    private val primary = combine(
        emergencyRepository.observeActive(),
        contactRepository.observeContacts(),
        profileRepository.observeProfile(),
    ) { active, contacts, profile -> PrimarySource(active, contacts, profile) }

    private val secondary = combine(
        emergencyRepository.observeHistory(),
        systemStatusRepository.status,
        settingsRepository.settings,
    ) { history, system, settings -> SecondarySource(history.firstOrNull(), system, settings) }

    val uiState: StateFlow<HomeUiState> = combine(primary, secondary, phase, draft, message) {
            first, second, transientPhase, alertDraft, userMessage ->
        HomeUiState(
            emergencyState = first.active?.let { EmergencyState.Active(it) } ?: transientPhase,
            contacts = first.contacts.filter(EmergencyContact::enabled),
            profile = first.profile,
            recentEvent = second.recent,
            systemStatus = second.system,
            settings = second.settings,
            alertDraft = alertDraft,
            userMessage = userMessage,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun requestSos() {
        val state = uiState.value
        if (state.emergencyState is EmergencyState.Active || permissionRequestPending || activationJob?.isActive == true) return
        val contactsReady = state.contacts.any { it.enabled }
        val profileReady = state.profile.isConfigured
        if (!contactsReady || !profileReady) {
            message.value = when {
                !contactsReady && !profileReady -> "Add an enabled trusted contact and complete your emergency profile before using SOS."
                !contactsReady -> "Add at least one enabled trusted contact before using SOS."
                else -> "Complete your full name and emergency notes before using SOS."
            }
            return
        }
        if (state.settings.requireSosConfirmation) phase.value = EmergencyState.Confirming else activate()
    }

    fun dismissConfirmation() { phase.value = EmergencyState.Idle }

    /** Requests the Activity-owned dangerous-permission flow after an explicit SOS action. */
    fun activate() {
        if (uiState.value.emergencyState is EmergencyState.Active || permissionRequestPending || activationJob?.isActive == true) return
        permissionRequestPending = true
        phase.value = EmergencyState.Activating
        activationChannel.trySend(Unit)
    }

    /** Continues after Android has returned the contextual communication and location permissions. */
    fun continueActivation() {
        if (!permissionRequestPending || activationJob?.isActive == true) return
        permissionRequestPending = false
        phase.value = EmergencyState.Activating
        activationJob = viewModelScope.launch {
            attemptOperation {
                val settings = settingsRepository.settings.first()
                val result = activateEmergency(uiState.value.systemStatus.batteryLevel)
                val trusted = result.recipients.mostTrusted()
                if (trusted != null) {
                    // A cached fix keeps coordinates in the first message; the accurate fix
                    // arrives later and upgrades the location recorded against the incident.
                    val cached = attemptOperation { locationRepository.lastKnownLocation() }.getOrNull()
                    draft.value = AlertDraft(
                        eventId = result.eventId,
                        recipients = result.recipients.map { AlertRecipient(it.id, it.phoneNumber) },
                        content = EmergencyMessageContent(
                            configuredMessage = settings.emergencyMessage,
                            profile = uiState.value.profile,
                            primaryContact = trusted,
                            location = (cached as? LocationResult.Available)?.snapshot,
                        ),
                        primaryPhoneNumber = trusted.phoneNumber,
                    )
                    locationJob = viewModelScope.launch {
                        attemptOperation { captureEmergencyLocation(result.eventId) }
                            .onSuccess { snapshot ->
                                // Upgrade the message if it has not been claimed yet.
                                if (snapshot != null) {
                                    draft.update { pending ->
                                        pending?.takeIf { it.eventId == result.eventId }
                                            ?.copy(content = pending.content.copy(location = snapshot))
                                    }
                                }
                            }
                            .onFailure {
                                attemptOperation {
                                    emergencyRepository.updateLocationStatus(result.eventId, LocationStatus.UNAVAILABLE)
                                }
                            }
                    }
                }
                phase.value = EmergencyState.Idle
            }.onFailure {
                phase.value = EmergencyState.Failed(EmergencyFailure.ACTIVATION_FAILED)
                message.value = "Emergency activation could not be completed. Check your saved profile and contacts, then try again."
            }
        }
    }

    /** Take the draft in memory, then durably claim it before performing external actions. */
    suspend fun claimDraft(eventId: Long): AlertDraft? {
        val pending = draft.value?.takeIf { it.eventId == eventId } ?: return null
        draft.value = null
        val claimed = attemptOperation { emergencyRepository.claimDispatch(eventId) }
            .getOrElse {
                message.value = "Emergency communication could not be prepared. Stop SOS and try again."
                false
            }
        if (!claimed || stoppedEventId == eventId) return null
        dispatchingEventId = eventId
        return pending
    }

    fun communicationFinished(
        eventId: Long,
        dispatchedRecipientIds: List<Long>,
        failedRecipientIds: List<Long>,
        callStarted: Boolean,
    ) {
        if (dispatchingEventId != eventId) return
        dispatchingEventId = null
        viewModelScope.launch {
            val recorded = attemptOperation {
                emergencyRepository.recordAlertOutcomes(eventId, dispatchedRecipientIds, failedRecipientIds)
            }.isSuccess
            val smsSummary = if (failedRecipientIds.isEmpty()) {
                "SMS submitted for ${dispatchedRecipientIds.size} trusted contact${if (dispatchedRecipientIds.size == 1) "" else "s"}."
            } else {
                "SMS submitted for ${dispatchedRecipientIds.size}; ${failedRecipientIds.size} could not be submitted."
            }
            val callSummary = if (callStarted) " Primary-contact call started." else " Primary-contact call could not start."
            message.value = smsSummary + callSummary +
                if (recorded) "" else " The result could not be saved; delivery remains unknown."
        }
    }

    fun stopEmergency() {
        val event = (uiState.value.emergencyState as? EmergencyState.Active)?.event ?: return
        draft.value = null
        stoppedEventId = event.id
        locationJob?.cancel()
        viewModelScope.launch {
            attemptOperation { completeEmergency(event) }
                .onSuccess {
                    phase.value = EmergencyState.Idle
                    draft.value = null
                    message.value = "Emergency mode stopped and saved to history."
                }
                .onFailure { message.value = "Emergency mode could not be stopped. Please try again." }
        }
    }

    fun refreshPermissions() = systemStatusRepository.refreshPermissions()
    fun showLocationDenied() { message.value = "Location permission wasn’t granted. SOS communication can still continue when SMS and call permissions are available." }
    fun composerUnavailable() { message.value = "Automatic emergency communication is unavailable on this device." }
    fun messageShown() { message.value = null }
}
