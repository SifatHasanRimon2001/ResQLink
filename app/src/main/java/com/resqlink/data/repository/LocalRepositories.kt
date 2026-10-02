package com.resqlink.data.repository

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import android.os.BatteryManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.room.withTransaction
import com.resqlink.data.security.StorageHealth
import com.resqlink.data.local.ResQLinkDatabase
import com.resqlink.domain.model.validateContact
import com.resqlink.notification.EmergencyNotifier
import com.resqlink.data.local.AlertAttemptEntity
import com.resqlink.data.local.ContactDao
import com.resqlink.data.local.EmergencyDao
import com.resqlink.data.local.EmergencyEventEntity
import com.resqlink.data.local.LocationPointEntity
import com.resqlink.data.local.ProfileDao
import com.resqlink.data.mapper.toDomain
import com.resqlink.data.mapper.toEntity
import com.resqlink.domain.model.AlertStatus
import com.resqlink.domain.model.EmergencyContact
import com.resqlink.domain.model.EmergencyEvent
import com.resqlink.domain.model.EmergencyProfile
import com.resqlink.domain.model.EventStatus
import com.resqlink.domain.model.LocationSnapshot
import com.resqlink.domain.model.LocationStatus
import com.resqlink.domain.model.SystemStatus
import com.resqlink.domain.repository.ContactRepository
import com.resqlink.domain.repository.DataControlRepository
import com.resqlink.domain.repository.EmergencyRepository
import com.resqlink.domain.repository.ProfileRepository
import com.resqlink.domain.repository.SaveContactResult
import com.resqlink.domain.repository.SettingsRepository
import com.resqlink.domain.repository.SystemStatusRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Singleton
class ContactRepositoryImpl @Inject constructor(
    private val database: ResQLinkDatabase,
    private val storageHealth: StorageHealth = StorageHealth(),
) : ContactRepository {
    private val dao get() = database.contactDao()
    override fun observeContacts() = storageHealth.observe("contacts", dao.observeAll().map { rows -> rows.map { it.toDomain() } })

    override suspend fun enabledContacts() = dao.enabledContacts().map { it.toDomain() }

    override suspend fun save(contact: EmergencyContact): SaveContactResult = database.withTransaction {
        val validation = validateContact(contact.name, contact.phoneNumber)
        if (!validation.valid || contact.id < 0 || contact.email.length > 254) return@withTransaction SaveContactResult.INVALID
        if (dao.phoneExists(validation.normalizedPhone, contact.id)) return@withTransaction SaveContactResult.DUPLICATE
        val existing = if (contact.id == 0L) null else dao.find(contact.id)
            ?: return@withTransaction SaveContactResult.INVALID
        val now = System.currentTimeMillis()
        val stored = contact.copy(priority = 1, phoneNumber = validation.normalizedPhone)
        val savedId = if (contact.id == 0L) {
            dao.insert(stored.toEntity(now))
        } else {
            dao.update(stored.toEntity(now, createdAt = requireNotNull(existing).createdAt))
            contact.id
        }
        if (contact.enabled && contact.priority == 0) {
            dao.demoteAll()
            dao.makePrimary(savedId)
        } else {
            ensurePrimary()
        }
        SaveContactResult.SAVED
    }

    override suspend fun setPrimary(contactId: Long) = database.withTransaction {
        if (dao.find(contactId)?.enabled != true) return@withTransaction
        dao.demoteAll()
        dao.makePrimary(contactId)
    }

    override suspend fun delete(contact: EmergencyContact) = database.withTransaction {
        dao.delete(contact.toEntity(System.currentTimeMillis()))
        ensurePrimary()
    }

    private suspend fun ensurePrimary() {
        if (dao.enabledPrimaryCount() != 1) {
            dao.demoteAll()
            dao.promoteFirstEnabled()
        }
    }
}

@Singleton
class ProfileRepositoryImpl @Inject constructor(
    private val dao: ProfileDao,
    private val storageHealth: StorageHealth = StorageHealth(),
) : ProfileRepository {
    override fun observeProfile() = storageHealth.observe("profile", dao.observe().map { it?.toDomain() ?: EmergencyProfile() })
    override suspend fun save(profile: EmergencyProfile) = dao.save(profile.toEntity(System.currentTimeMillis()))
}

@Singleton
class EmergencyRepositoryImpl @Inject constructor(
    private val dao: EmergencyDao,
    private val storageHealth: StorageHealth = StorageHealth(),
) : EmergencyRepository {
    override fun observeHistory(): Flow<List<EmergencyEvent>> = storageHealth.observe("history", dao.observeHistory().map { rows ->
        rows.map { it.toDomain(dao.alertCount(it.id)) }
    })

    override fun observeActive(): Flow<EmergencyEvent?> = storageHealth.observe("active-event", dao.observeActive().map { row ->
        row?.toDomain(dao.alertCount(row.id))
    })

    override suspend fun createOrGetActive(batteryLevel: Int): EmergencyEvent {
        val now = System.currentTimeMillis()
        val entity = EmergencyEventEntity(
            startedAt = now,
            status = EventStatus.ACTIVATING.name,
            locationStatus = LocationStatus.NOT_REQUESTED.name,
            batteryLevel = batteryLevel,
        )
        val persisted = dao.insertOrGetActive(entity)
        return persisted.toDomain(dao.alertCount(persisted.id))
    }

    override suspend fun markActive(eventId: Long, locationStatus: LocationStatus) {
        dao.activate(eventId, EventStatus.ACTIVE.name, locationStatus.name)
    }

    override suspend fun updateLocationStatus(eventId: Long, locationStatus: LocationStatus) {
        dao.updateLocationStatus(eventId, locationStatus.name)
    }

    override suspend fun recordLocation(eventId: Long, location: LocationSnapshot) {
        dao.insertLocationIfActive(
            LocationPointEntity(
                emergencyEventId = eventId,
                latitude = location.latitude,
                longitude = location.longitude,
                accuracyMeters = location.accuracyMeters,
                timestamp = location.capturedAt,
                provider = location.provider,
            ),
        )
    }

    override suspend fun prepareAlerts(eventId: Long, recipients: List<EmergencyContact>): Boolean {
        val now = System.currentTimeMillis()
        return dao.insertAlertsIfAbsent(eventId, recipients.distinctBy { it.id }.map { contact ->
            AlertAttemptEntity(
                id = UUID.randomUUID().toString(),
                emergencyEventId = eventId,
                recipientId = contact.id,
                channel = "SMS",
                createdAt = now,
                attemptCount = 0,
                status = AlertStatus.PREPARED.name,
                lastAttemptAt = null,
                errorCode = null,
            )
        })
    }

    override suspend fun claimDispatch(eventId: Long): Boolean =
        dao.claimDispatch(eventId, System.currentTimeMillis()) > 0

    override suspend fun recordAlertOutcomes(
        eventId: Long,
        dispatchedRecipientIds: List<Long>,
        failedRecipientIds: List<Long>,
    ) {
        val now = System.currentTimeMillis()
        if (dispatchedRecipientIds.isNotEmpty()) {
            dao.updateAlertOutcomes(eventId, dispatchedRecipientIds, AlertStatus.DISPATCHED.name, now, null)
        }
        if (failedRecipientIds.isNotEmpty()) {
            dao.updateAlertOutcomes(eventId, failedRecipientIds, AlertStatus.FAILED.name, now, "DISPATCH_FAILED")
        }
    }

    override suspend fun complete(eventId: Long, cancelled: Boolean) {
        val status = if (cancelled) EventStatus.CANCELLED else EventStatus.COMPLETED
        dao.complete(eventId, status.name, System.currentTimeMillis())
    }

    override suspend fun delete(eventId: Long) = dao.deleteEvent(eventId)
}

@Singleton
class SystemStatusRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : SystemStatusRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val network = MutableStateFlow(isNetworkAvailable())
    private val battery = MutableStateFlow(readBattery())
    private val permissionTick = MutableStateFlow(0)

    override val status: Flow<SystemStatus> = combine(network, battery, permissionTick) { connected, level, _ ->
        SystemStatus(
            networkAvailable = connected,
            batteryLevel = level,
            locationPermissionGranted = hasLocationPermission(),
            notificationPermissionGranted = Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
            bluetoothAvailable = runCatching {
                context.getSystemService(BluetoothManager::class.java)?.adapter != null
            }.getOrDefault(false),
        )
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(networkObject: Network) { network.value = true }
        override fun onLost(networkObject: Network) { network.value = isNetworkAvailable() }
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(receiverContext: Context?, intent: Intent?) {
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            if (level >= 0) battery.value = (level * 100 / scale.coerceAtLeast(1)).coerceIn(0, 100)
        }
    }

    init {
        runCatching {
            val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                connectivityManager.registerDefaultNetworkCallback(networkCallback)
            } else {
                connectivityManager.registerNetworkCallback(NetworkRequest.Builder().build(), networkCallback)
            }
        }
        ContextCompat.registerReceiver(
            context,
            batteryReceiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        scope.launch { permissionTick.emit(1) }
    }

    override fun refreshPermissions() { permissionTick.value += 1 }

    private fun hasLocationPermission() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @Suppress("DEPRECATION")
    private fun isNetworkAvailable(): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        return manager.activeNetwork != null
    }

    private fun readBattery(): Int {
        val manager = context.getSystemService(BatteryManager::class.java)
        return manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).takeIf { it in 0..100 } ?: 100
    }
}

@Singleton
class DataControlRepositoryImpl @Inject constructor(
    private val database: ResQLinkDatabase,
    private val settingsRepository: SettingsRepository,
    private val notifier: EmergencyNotifier,
) : DataControlRepository {
    override suspend fun clearAll() {
        database.withTransaction {
            check(database.emergencyDao().active() == null) { "Stop the active emergency before clearing data." }
            database.emergencyDao().clear()
            database.contactDao().clear()
            database.profileDao().clear()
        }
        settingsRepository.clearPreferences()
        notifier.cancel()
    }
}
