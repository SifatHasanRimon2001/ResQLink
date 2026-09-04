package com.resqlink

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telephony.SmsManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.resqlink.feature.app.AppRoot
import com.resqlink.feature.app.AppViewModel
import com.resqlink.feature.contacts.ContactsViewModel
import com.resqlink.feature.history.HistoryViewModel
import com.resqlink.feature.home.AlertDraft
import com.resqlink.feature.home.HomeViewModel
import com.resqlink.feature.onboarding.OnboardingViewModel
import com.resqlink.feature.profile.ProfileViewModel
import com.resqlink.feature.settings.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val appViewModel by viewModels<AppViewModel>()
    private val onboardingViewModel by viewModels<OnboardingViewModel>()
    private val homeViewModel by viewModels<HomeViewModel>()
    private val contactsViewModel by viewModels<ContactsViewModel>()
    private val profileViewModel by viewModels<ProfileViewModel>()
    private val historyViewModel by viewModels<HistoryViewModel>()
    private val settingsViewModel by viewModels<SettingsViewModel>()
    private val dispatchedEventIds = mutableSetOf<Long>()
    private var emergencyActivationPending = false

    private val locationPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        homeViewModel.refreshPermissions()
        if (result.values.none { it }) homeViewModel.showLocationDenied()
    }

    private val emergencyPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        if (!emergencyActivationPending) return@registerForActivityResult
        emergencyActivationPending = false
        homeViewModel.refreshPermissions()
        homeViewModel.continueActivation()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppRoot(
                appViewModel = appViewModel,
                onboardingViewModel = onboardingViewModel,
                homeViewModel = homeViewModel,
                contactsViewModel = contactsViewModel,
                profileViewModel = profileViewModel,
                historyViewModel = historyViewModel,
                settingsViewModel = settingsViewModel,
                onRequestLocation = {
                    locationPermission.launch(arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    ))
                },
                onOpenAlert = ::dispatchEmergencyActions,
            )
        }
        observeEmergencyActions()
    }

    override fun onResume() {
        super.onResume()
        homeViewModel.refreshPermissions()
    }

    private fun observeEmergencyActions() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    homeViewModel.activationRequests.collect { requestEmergencyPermissions() }
                }
                launch {
                    homeViewModel.uiState
                        .map { it.alertDraft }
                        .filterNotNull()
                        .distinctUntilChangedBy { it.eventId }
                        .collect(::dispatchEmergencyActions)
                }
            }
        }
    }

    private fun requestEmergencyPermissions() {
        val permissions = buildList {
            add(Manifest.permission.SEND_SMS)
            add(Manifest.permission.CALL_PHONE)
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.filter { permission ->
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        }

        if (permissions.isEmpty()) {
            homeViewModel.continueActivation()
        } else {
            emergencyActivationPending = true
            emergencyPermissions.launch(permissions.toTypedArray())
        }
    }

    @SuppressLint("MissingPermission")
    private fun dispatchEmergencyActions(draft: AlertDraft) {
        if (!dispatchedEventIds.add(draft.eventId)) return
        val dispatched = mutableListOf<Long>()
        val failed = mutableListOf<Long>()
        val hasTelephony = packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
        val canSendSms = hasTelephony &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

        if (canSendSms) {
            val smsManager = getSystemService(SmsManager::class.java)
            draft.recipients.forEach { recipient ->
                runCatching {
                    val parts = smsManager.divideMessage(draft.body)
                    if (parts.size == 1) {
                        smsManager.sendTextMessage(recipient.phoneNumber, null, draft.body, null, null)
                    } else {
                        smsManager.sendMultipartTextMessage(recipient.phoneNumber, null, parts, null, null)
                    }
                }.onSuccess {
                    dispatched += recipient.contactId
                }.onFailure {
                    failed += recipient.contactId
                }
            }
        } else {
            failed += draft.recipients.map { it.contactId }
        }

        val canCall = hasTelephony &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        val callStarted = canCall && runCatching {
            startActivity(Intent(Intent.ACTION_CALL, Uri.fromParts("tel", draft.primaryPhoneNumber, null)))
        }.isSuccess

        homeViewModel.communicationFinished(
            eventId = draft.eventId,
            dispatchedRecipientIds = dispatched,
            failedRecipientIds = failed,
            callStarted = callStarted,
        )
    }

    companion object {
        const val EXTRA_OPEN_EVENT_ID = "open_event_id"
    }
}
