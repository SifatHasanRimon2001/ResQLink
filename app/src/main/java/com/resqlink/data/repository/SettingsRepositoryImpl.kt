package com.resqlink.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.resqlink.data.security.SecureStorage
import com.resqlink.data.security.StorageHealth
import com.resqlink.domain.model.AppSettings
import com.resqlink.domain.model.ThemePreference
import com.resqlink.domain.model.normalizeEmergencyMessage
import com.resqlink.domain.repository.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val secureStorage: SecureStorage,
    private val storageHealth: StorageHealth,
) : SettingsRepository {
    private object Keys {
        val onboarding = booleanPreferencesKey("onboarding_complete")
        val theme = stringPreferencesKey("theme")
        val confirmation = booleanPreferencesKey("sos_confirmation")
        val legacyMessage = stringPreferencesKey("emergency_message")
        val message = stringPreferencesKey("encrypted_emergency_message")
    }

    override val settings: Flow<AppSettings> = storageHealth.observe("settings", dataStore.data
        .onStart {
            dataStore.edit { preferences ->
                preferences[Keys.legacyMessage]?.let { legacy ->
                    val encrypted = preferences[Keys.message]
                    if (encrypted == null) {
                        preferences[Keys.message] = secureStorage.encryptMessage(normalizeEmergencyMessage(legacy))
                    } else {
                        secureStorage.decryptMessage(encrypted)
                    }
                    preferences.remove(Keys.legacyMessage)
                }
            }
        }
        .map { preferences ->
            AppSettings(
                onboardingComplete = preferences[Keys.onboarding] ?: false,
                theme = preferences[Keys.theme]?.let { runCatching { ThemePreference.valueOf(it) }.getOrNull() }
                    ?: ThemePreference.SYSTEM,
                requireSosConfirmation = preferences[Keys.confirmation] ?: true,
                emergencyMessage = normalizeEmergencyMessage(preferences[Keys.message]?.let(secureStorage::decryptMessage).orEmpty()),
            )
        }.flowOn(Dispatchers.IO))

    override suspend fun completeOnboarding() {
        dataStore.edit { it[Keys.onboarding] = true }
    }

    override suspend fun setTheme(theme: ThemePreference) {
        dataStore.edit { it[Keys.theme] = theme.name }
    }

    override suspend fun setConfirmation(required: Boolean) {
        dataStore.edit { it[Keys.confirmation] = required }
    }

    override suspend fun setEmergencyMessage(message: String) = withContext(Dispatchers.IO) {
        dataStore.edit {
            // Do not replace unreadable saved ciphertext after key loss.
            it[Keys.message]?.let(secureStorage::decryptMessage)
            it[Keys.message] = secureStorage.encryptMessage(normalizeEmergencyMessage(message))
            it.remove(Keys.legacyMessage)
        }
        Unit
    }

    override suspend fun clearPreferences() {
        dataStore.edit { it.clear() }
    }
}
