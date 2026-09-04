package com.resqlink.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Message
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LocationOff
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MedicalInformation
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.OfflineBolt
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.resqlink.R
import com.resqlink.core.ui.GlassCard
import com.resqlink.core.ui.OrbitMark
import com.resqlink.core.ui.SectionLabel
import com.resqlink.core.ui.StatusPill
import com.resqlink.core.ui.theme.Emergency
import com.resqlink.core.ui.theme.Mint
import com.resqlink.domain.model.BatteryBand
import com.resqlink.domain.model.EmergencyEvent
import com.resqlink.domain.model.EmergencyState
import com.resqlink.domain.model.LocationStatus
import com.resqlink.domain.model.batteryBand
import java.text.DateFormat
import java.util.Calendar
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onContacts: () -> Unit,
    onProfile: () -> Unit,
    onHistory: () -> Unit,
    onRequestLocation: () -> Unit,
    onOpenAlert: (AlertDraft) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showPermissionInfo by remember { mutableStateOf(false) }

    LaunchedEffect(state.userMessage) {
        state.userMessage?.let {
            snackbar.showSnackbar(it)
            viewModel.messageShown()
        }
    }

    if (state.emergencyState is EmergencyState.Confirming) {
        SosConfirmationDialog(
            onDismiss = viewModel::dismissConfirmation,
            onConfirm = viewModel::activate,
        )
    }
    if (showPermissionInfo) {
        LocationPermissionDialog(
            onDismiss = { showPermissionInfo = false },
            onAllow = {
                showPermissionInfo = false
                onRequestLocation()
            },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val emergency = state.emergencyState as? EmergencyState.Active
        if (emergency != null) {
            EmergencyModeScreen(
                event = emergency.event,
                state = state,
                onOpenAlert = { state.alertDraft?.let(onOpenAlert) },
                onStop = viewModel::stopEmergency,
                modifier = Modifier.padding(padding),
            )
        } else {
            HomeContent(
                state = state,
                onSos = viewModel::requestSos,
                onContacts = onContacts,
                onProfile = onProfile,
                onHistory = onHistory,
                onEnableLocation = { showPermissionInfo = true },
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun HomeContent(
    state: HomeUiState,
    onSos: () -> Unit,
    onContacts: () -> Unit,
    onProfile: () -> Unit,
    onHistory: () -> Unit,
    onEnableLocation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isActivating = state.emergencyState is EmergencyState.Activating
    Column(
        modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OrbitMark(Modifier.size(44.dp))
            Column(Modifier.padding(start = 11.dp).weight(1f)) {
                Text("RESQLINK", fontWeight = FontWeight.Black, letterSpacing = 2.2.sp)
                Text(greeting(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            StatusPill(
                text = stringResource(if (state.systemStatus.networkAvailable) R.string.connected else R.string.offline),
                positive = state.systemStatus.networkAvailable,
            )
        }

        Spacer(Modifier.height(24.dp))
        GlassCard {
            Column {
                SectionLabel(stringResource(R.string.safety_overview))
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(if (state.contacts.isNotEmpty() && state.profile.isConfigured) R.string.ready_status else R.string.setup_needed),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.protected_offline),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MiniStatus(
                        icon = if (state.systemStatus.locationPermissionGranted) Icons.Rounded.LocationOn else Icons.Rounded.LocationOff,
                        label = stringResource(R.string.location),
                        value = stringResource(if (state.systemStatus.locationPermissionGranted) R.string.location_ready else R.string.location_needed),
                        modifier = Modifier.weight(1f),
                        onClick = if (state.systemStatus.locationPermissionGranted) null else onEnableLocation,
                    )
                    MiniStatus(
                        icon = Icons.Rounded.BatteryChargingFull,
                        label = stringResource(R.string.battery),
                        value = "${state.systemStatus.batteryLevel}%",
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        Spacer(Modifier.height(28.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .size(196.dp)
                    .shadow(28.dp, CircleShape, spotColor = Emergency.copy(alpha = 0.45f))
                    .background(
                        Brush.radialGradient(listOf(Color(0xFFFF7180), Emergency, Color(0xFFC21D40))),
                        CircleShape,
                    )
                    .border(7.dp, Emergency.copy(alpha = 0.16f), CircleShape)
                    .clickable(enabled = !isActivating, role = Role.Button, onClick = onSos)
                    .semantics {
                        contentDescription = "Activate SOS emergency mode"
                        role = Role.Button
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (isActivating) CircularProgressIndicator(color = Color.White)
                else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.Bolt, null, Modifier.size(42.dp), tint = Color.White)
                    Text(stringResource(R.string.sos), color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Black)
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(stringResource(R.string.tap_for_help), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(Modifier.height(30.dp))
        SectionLabel("Your setup")
        Spacer(Modifier.height(10.dp))
        GlassCard {
            Column {
                HomeLinkRow(
                    Icons.Rounded.Groups,
                    stringResource(R.string.trusted_contacts),
                    pluralStringResource(
                        R.plurals.contacts_count,
                        state.contacts.count { it.enabled },
                        state.contacts.count { it.enabled },
                    ),
                    onContacts,
                )
                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
                HomeLinkRow(
                    Icons.Rounded.MedicalInformation,
                    stringResource(R.string.emergency_profile),
                    stringResource(if (state.profile.isConfigured) R.string.configured else R.string.not_configured),
                    onProfile,
                )
            }
        }

        Spacer(Modifier.height(22.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SectionLabel(stringResource(R.string.recent_activity))
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onHistory) { Text(stringResource(R.string.view_all)) }
        }
        GlassCard(onClick = onHistory) {
            val event = state.recentEvent
            if (event == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.no_recent_emergency), Modifier.padding(start = 12.dp), fontWeight = FontWeight.SemiBold)
                }
            } else {
                Column {
                    Text(event.status.name.lowercase().replaceFirstChar { it.titlecase() }, fontWeight = FontWeight.Bold)
                    Text(
                        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(event.startedAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun MiniStatus(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
    ) {
        Column(Modifier.padding(13.dp)) {
            Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, maxLines = 2)
        }
    }
}

@Composable
private fun HomeLinkRow(icon: ImageVector, title: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)) {
            Icon(icon, null, Modifier.padding(10.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Column(Modifier.padding(horizontal = 13.dp).weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmergencyModeScreen(
    event: EmergencyEvent,
    state: HomeUiState,
    onOpenAlert: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var now by remember(event.id) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(event.id) {
        while (true) { delay(1_000); now = System.currentTimeMillis() }
    }
    val elapsed = ((now - event.startedAt).coerceAtLeast(0) / 1_000)
    val formatted = "%02d:%02d".format(elapsed / 60, elapsed % 60)

    Column(
        modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Emergency.copy(alpha = 0.19f), MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.background),
                ),
            )
            .windowInsetsPadding(WindowInsets.statusBars)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        OrbitMark(Modifier.size(82.dp), emergency = true)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.emergency_active).uppercase(), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
        Text(formatted, style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Light)
        Text(stringResource(R.string.emergency_supporting), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(Modifier.height(26.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                EmergencyStatusRow(
                    Icons.AutoMirrored.Rounded.Message,
                    stringResource(R.string.alert_prepared),
                    if (state.contacts.any { it.enabled }) stringResource(R.string.alert_prepared_detail) else stringResource(R.string.no_contacts_alert),
                    state.contacts.any { it.enabled },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
                EmergencyStatusRow(
                    if (event.locationStatus == LocationStatus.CAPTURED) Icons.Rounded.LocationOn else Icons.Rounded.LocationOff,
                    stringResource(R.string.location),
                    stringResource(if (event.locationStatus == LocationStatus.CAPTURED) R.string.location_available else R.string.location_unavailable),
                    event.locationStatus == LocationStatus.CAPTURED,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
                EmergencyStatusRow(
                    if (state.systemStatus.networkAvailable) Icons.Rounded.NetworkCheck else Icons.Rounded.OfflineBolt,
                    stringResource(R.string.network),
                    stringResource(if (state.systemStatus.networkAvailable) R.string.connected else R.string.offline),
                    state.systemStatus.networkAvailable,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
                EmergencyStatusRow(
                    Icons.Rounded.BatteryChargingFull,
                    stringResource(R.string.battery),
                    "${state.systemStatus.batteryLevel}% · ${batteryLabel(state.systemStatus.batteryLevel)}",
                    batteryBand(state.systemStatus.batteryLevel) != BatteryBand.CRITICAL,
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        if (state.alertDraft != null && state.alertDraft.recipients.isNotEmpty()) {
            Button(
                onClick = onOpenAlert,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(18.dp),
            ) {
                Icon(Icons.AutoMirrored.Rounded.Message, null)
                Text(stringResource(R.string.open_alert_message), Modifier.padding(start = 9.dp), fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
        }
        Button(
            onClick = onStop,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
        ) {
            Icon(Icons.Rounded.StopCircle, null)
            Text(stringResource(R.string.stop_emergency), Modifier.padding(start = 9.dp), fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun EmergencyStatusRow(icon: ImageVector, title: String, body: String, positive: Boolean) {
    Row(verticalAlignment = Alignment.Top) {
        Surface(
            shape = CircleShape,
            color = (if (positive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error).copy(alpha = 0.12f),
        ) {
            Icon(
                icon,
                null,
                Modifier.padding(10.dp),
                tint = if (positive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        }
        Column(Modifier.padding(start = 13.dp).weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SosConfirmationDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { OrbitMark(Modifier.size(56.dp), emergency = true) },
        title = { Text(stringResource(R.string.sos_confirmation_title), textAlign = TextAlign.Center) },
        text = {
            Column {
                Text(stringResource(R.string.sos_confirmation_body))
                Spacer(Modifier.height(12.dp))
                Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), shape = RoundedCornerShape(14.dp)) {
                    Text(stringResource(R.string.sos_confirmation_note), Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                Text(stringResource(R.string.activate_sos))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        shape = RoundedCornerShape(28.dp),
    )
}

@Composable
private fun LocationPermissionDialog(onDismiss: () -> Unit, onAllow: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.LocationOn, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(stringResource(R.string.permission_title)) },
        text = { Text(stringResource(R.string.permission_body)) },
        confirmButton = { Button(onClick = onAllow) { Text(stringResource(R.string.allow_location)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.not_now)) } },
        shape = RoundedCornerShape(28.dp),
    )
}

@Composable
private fun batteryLabel(level: Int) = stringResource(
    when (batteryBand(level)) {
        BatteryBand.NORMAL -> R.string.battery_normal
        BatteryBand.MODERATE -> R.string.battery_moderate
        BatteryBand.LOW -> R.string.battery_low
        BatteryBand.CRITICAL -> R.string.battery_critical
    },
)

@Composable
private fun greeting(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return stringResource(
        when (hour) {
            in 5..11 -> R.string.good_morning
            in 12..16 -> R.string.good_afternoon
            else -> R.string.good_evening
        },
    )
}
