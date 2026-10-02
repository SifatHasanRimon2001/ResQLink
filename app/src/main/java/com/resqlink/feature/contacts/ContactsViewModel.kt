package com.resqlink.feature.contacts

import androidx.lifecycle.ViewModel
import com.resqlink.core.util.attemptOperation
import androidx.lifecycle.viewModelScope
import com.resqlink.domain.model.EmergencyContact
import com.resqlink.domain.model.validateContact
import com.resqlink.domain.repository.ContactRepository
import com.resqlink.domain.repository.SaveContactResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ContactsUiState(
    val contacts: List<EmergencyContact> = emptyList(),
    val nameError: Boolean = false,
    val phoneError: Boolean = false,
    val duplicateError: Boolean = false,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val userMessage: String? = null,
)

private data class SaveState(
    val nameError: Boolean = false,
    val phoneError: Boolean = false,
    val duplicateError: Boolean = false,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val userMessage: String? = null,
)

@HiltViewModel
class ContactsViewModel @Inject constructor(
    private val repository: ContactRepository,
) : ViewModel() {
    private val saveState = MutableStateFlow(SaveState())
    val uiState: StateFlow<ContactsUiState> = combine(repository.observeContacts(), saveState) { contacts, save ->
        ContactsUiState(contacts, save.nameError, save.phoneError, save.duplicateError, save.saving, save.saved, save.userMessage)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ContactsUiState())

    fun save(id: Long, name: String, phone: String, email: String, enabled: Boolean) {
        if (saveState.value.saving) return
        val validation = validateContact(name, phone)
        if (!validation.valid) {
            saveState.value = SaveState(nameError = validation.nameError, phoneError = validation.phoneError)
            return
        }
        saveState.value = SaveState(saving = true)
        viewModelScope.launch {
            val contacts = uiState.value.contacts
            val priority = contacts.firstOrNull { it.id == id }?.priority
                ?: if (contacts.none { it.enabled && it.priority == 0 }) 0 else 1
            val result = attemptOperation { repository.save(
                EmergencyContact(
                    id = id,
                    name = name.trim(),
                    phoneNumber = validation.normalizedPhone,
                    email = email.trim(),
                    priority = priority,
                    enabled = enabled,
                ),
            ) }.getOrElse {
                saveState.value = SaveState(userMessage = "Contact could not be saved. Please try again.")
                return@launch
            }
            saveState.value = SaveState(
                userMessage = if (result == SaveContactResult.INVALID) "Contact details are invalid or the contact was deleted." else null,
                duplicateError = result == SaveContactResult.DUPLICATE,
                saved = result == SaveContactResult.SAVED,
            )
        }
    }

    fun setPrimary(contact: EmergencyContact) = viewModelScope.launch {
        if (contact.enabled) attemptOperation { repository.setPrimary(contact.id) }
            .onFailure { saveState.value = SaveState(userMessage = "Primary contact could not be changed.") }
    }

    fun delete(contact: EmergencyContact) = viewModelScope.launch {
        attemptOperation { repository.delete(contact) }
            .onFailure { saveState.value = SaveState(userMessage = "Contact could not be deleted.") }
    }
    fun consumeSaved() { saveState.value = SaveState() }
    fun clearErrors() { saveState.value = SaveState() }
}
