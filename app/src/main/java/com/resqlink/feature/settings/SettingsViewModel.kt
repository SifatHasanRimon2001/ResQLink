package com.resqlink.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resqlink.domain.model.AppSettings
import com.resqlink.domain.model.SystemStatus
import com.resqlink.domain.model.ThemePreference
import com.resqlink.domain.repository.DataControlRepository
import com.resqlink.domain.repository.EmergencyRepository
import com.resqlink.domain.repository.SettingsRepository
import com.resqlink.domain.repository.SystemStatusRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val system: SystemStatus = SystemStatus(),
    val hasActiveEmergency: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    statusRepository: SystemStatusRepository,
    emergencyRepository: EmergencyRepository,
    private val dataControlRepository: DataControlRepository,
) : ViewModel() {
    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.settings,
        statusRepository.status,
        emergencyRepository.observeActive(),
    ) { settings, system, active -> SettingsUiState(settings, system, active != null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setTheme(theme: ThemePreference) = viewModelScope.launch { settingsRepository.setTheme(theme) }
    fun setConfirmation(required: Boolean) = viewModelScope.launch { settingsRepository.setConfirmation(required) }
    fun setMessage(message: String) = viewModelScope.launch { settingsRepository.setEmergencyMessage(message) }
    fun clearAll() = viewModelScope.launch { dataControlRepository.clearAll() }
}
