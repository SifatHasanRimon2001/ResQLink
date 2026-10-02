package com.resqlink.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.resqlink.data.security.EncryptedDatabaseFactory
import com.resqlink.data.local.ContactDao
import com.resqlink.data.local.EmergencyDao
import com.resqlink.data.local.ProfileDao
import com.resqlink.data.local.ResQLinkDatabase
import com.resqlink.data.repository.ContactRepositoryImpl
import com.resqlink.data.repository.DataControlRepositoryImpl
import com.resqlink.data.repository.EmergencyRepositoryImpl
import com.resqlink.data.repository.ProfileRepositoryImpl
import com.resqlink.data.repository.SettingsRepositoryImpl
import com.resqlink.data.repository.SystemStatusRepositoryImpl
import com.resqlink.domain.repository.ContactRepository
import com.resqlink.domain.repository.DataControlRepository
import com.resqlink.domain.repository.EmergencyRepository
import com.resqlink.domain.repository.LocationRepository
import com.resqlink.domain.repository.ProfileRepository
import com.resqlink.domain.repository.SettingsRepository
import com.resqlink.domain.repository.SystemStatusRepository
import com.resqlink.location.AndroidLocationRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "resqlink_settings")

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(factory: EncryptedDatabaseFactory): ResQLinkDatabase = factory.build()

    @Provides
    @Singleton
    fun preferences(@ApplicationContext context: Context): DataStore<Preferences> = context.settingsDataStore

    @Provides fun contacts(database: ResQLinkDatabase): ContactDao = database.contactDao()
    @Provides fun profile(database: ResQLinkDatabase): ProfileDao = database.profileDao()
    @Provides fun emergencies(database: ResQLinkDatabase): EmergencyDao = database.emergencyDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun contacts(implementation: ContactRepositoryImpl): ContactRepository
    @Binds abstract fun profile(implementation: ProfileRepositoryImpl): ProfileRepository
    @Binds abstract fun emergencies(implementation: EmergencyRepositoryImpl): EmergencyRepository
    @Binds abstract fun settings(implementation: SettingsRepositoryImpl): SettingsRepository
    @Binds abstract fun location(implementation: AndroidLocationRepository): LocationRepository
    @Binds abstract fun status(implementation: SystemStatusRepositoryImpl): SystemStatusRepository
    @Binds abstract fun dataControl(implementation: DataControlRepositoryImpl): DataControlRepository
}
