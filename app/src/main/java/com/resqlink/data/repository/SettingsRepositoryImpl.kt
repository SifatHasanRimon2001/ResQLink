package com.resqlink.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.resqlink.domain.model.AppSettings
import com.resqlink.domain.model.ThemePreference
import com.resqlink.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "resqlink_settings")

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : SettingsRepository {
    private object Keys {
        val onboarding = booleanPreferencesKey("onboarding_complete")
        val theme = stringPreferencesKey("theme")
        val confirmation = booleanPreferencesKey("sos_confirmation")
        val message = stringPreferencesKey("emergency_message")
    }

    override val settings: Flow<AppSettings> = context.settingsDataStore.data.map { preferences ->
        AppSettings(
            onboardingComplete = preferences[Keys.onboarding] ?: false,
            theme = preferences[Keys.theme]?.let { runCatching { ThemePreference.valueOf(it) }.getOrNull() }
                ?: ThemePreference.SYSTEM,
            requireSosConfirmation = preferences[Keys.confirmation] ?: true,
            emergencyMessage = preferences[Keys.message] ?: AppSettings.DEFAULT_EMERGENCY_MESSAGE,
        )
    }

    override suspend fun completeOnboarding() {
        context.settingsDataStore.edit { it[Keys.onboarding] = true }
    }

    override suspend fun setTheme(theme: ThemePreference) {
        context.settingsDataStore.edit { it[Keys.theme] = theme.name }
    }

    override suspend fun setConfirmation(required: Boolean) {
        context.settingsDataStore.edit { it[Keys.confirmation] = required }
    }

    override suspend fun setEmergencyMessage(message: String) {
        context.settingsDataStore.edit { it[Keys.message] = message.trim().take(500) }
    }

    override suspend fun clearPreferences() {
        context.settingsDataStore.edit { it.clear() }
    }
}
