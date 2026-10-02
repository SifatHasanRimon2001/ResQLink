package com.resqlink.feature.onboarding

import com.resqlink.core.util.attemptOperation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resqlink.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val message = MutableStateFlow<String?>(null)
    val userMessage = message.asStateFlow()

    fun complete() {
        viewModelScope.launch {
            attemptOperation { settingsRepository.completeOnboarding() }
                .onSuccess { message.value = null }
                .onFailure { message.value = "Setup could not be saved. Check free storage and try again." }
        }
    }
}
