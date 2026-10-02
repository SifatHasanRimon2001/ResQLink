package com.resqlink.feature.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resqlink.data.security.StorageHealth
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import com.resqlink.domain.model.AppSettings
import com.resqlink.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class AppUiState(
    val loading: Boolean = true,
    val storageUnavailable: Boolean = false,
    val settings: AppSettings = AppSettings(),
)

@HiltViewModel
class AppViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    private val storageHealth: StorageHealth,
) : ViewModel() {
    val uiState: StateFlow<AppUiState> = combine(
        settingsRepository.settings.map { AppUiState(loading = false, settings = it) }.onStart { emit(AppUiState()) },
        storageHealth.failedSources,
    ) { state, failures -> state.copy(storageUnavailable = failures.isNotEmpty()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppUiState())

    fun retryStorage() = storageHealth.retry()
}
