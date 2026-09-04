package com.resqlink.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resqlink.domain.model.EmergencyEvent
import com.resqlink.domain.repository.EmergencyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: EmergencyRepository,
) : ViewModel() {
    val events: StateFlow<List<EmergencyEvent>> = repository.observeHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun delete(eventId: Long) = viewModelScope.launch { repository.delete(eventId) }
}
