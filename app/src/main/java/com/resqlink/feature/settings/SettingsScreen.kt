package com.resqlink.feature.settings

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Message
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.MedicalInformation
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.resqlink.BuildConfig
import com.resqlink.R
import com.resqlink.core.ui.EmergencyMessagePreview
import com.resqlink.core.ui.GlassCard
import com.resqlink.core.ui.MenuRow
import com.resqlink.core.ui.PageHeading
import com.resqlink.core.ui.SectionLabel
import com.resqlink.core.ui.StatusPill
import com.resqlink.domain.model.ThemePreference

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onProfile: () -> Unit,
    onContacts: () -> Unit,
    onDiagnostics: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showMessage by remember { mutableStateOf(false) }
    var showClear by remember { mutableStateOf(false) }

    if (showMessage) MessageDialog(
        current = state.settings.emergencyMessage,
        preview = state.messagePreview,
        onDismiss = { showMessage = false },
        onSave = { viewModel.setMessage(it); showMessage = false },
    )
    if (showClear) {
        AlertDialog(
            onDismissRequest = { showClear = false },
            icon = { Icon(Icons.Rounded.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.clear_data_title)) },
            text = { Text(if (state.hasActiveEmergency) "Stop the active emergency before clearing your data." else stringResource(R.string.clear_data_body)) },
            confirmButton = {
                Button(onClick = { showClear = false; viewModel.clearAll() }, enabled = !state.hasActiveEmergency) { Text(stringResource(R.string.clear_everything)) }
            },
            dismissButton = { TextButton(onClick = { showClear = false }) { Text(stringResource(R.string.cancel)) } },
            shape = RoundedCornerShape(28.dp),
        )
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .windowInsetsPadding(WindowInsets.statusBars)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(16.dp))
            PageHeading(stringResource(R.string.settings_title), stringResource(R.string.settings_subtitle))
            state.userMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp)) }
            Spacer(Modifier.height(24.dp))

            SectionLabel(stringResource(R.string.appearance))
            Spacer(Modifier.height(10.dp))
            GlassCard {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Palette, null, tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.theme), Modifier.padding(start = 10.dp), fontWeight = FontWeight.Bold)
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(top = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ThemeChip(
                            stringResource(R.string.system_default),
                            Icons.Rounded.PhoneAndroid,
                            state.settings.theme == ThemePreference.SYSTEM,
                            { viewModel.setTheme(ThemePreference.SYSTEM) },
                            Modifier.weight(1f),
                        )
                        ThemeChip(
                            stringResource(R.string.light),
                            Icons.Rounded.LightMode,
                            state.settings.theme == ThemePreference.LIGHT,
                            { viewModel.setTheme(ThemePreference.LIGHT) },
                            Modifier.weight(1f),
                        )
                        ThemeChip(
                            stringResource(R.string.dark),
                            Icons.Rounded.DarkMode,
                            state.settings.theme == ThemePreference.DARK,
                            { viewModel.setTheme(ThemePreference.DARK) },
                            Modifier.weight(1f),
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            SectionLabel(stringResource(R.string.emergency))
            Spacer(Modifier.height(10.dp))
            GlassCard {
                Column {
                    MenuRow(
                        Icons.Rounded.HealthAndSafety,
                        stringResource(R.string.sos_confirmation),
                        stringResource(R.string.sos_confirmation_description),
                        onClick = { viewModel.setConfirmation(!state.settings.requireSosConfirmation) },
                        trailing = { Switch(state.settings.requireSosConfirmation, viewModel::setConfirmation) },
                    )
                    HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
                    MenuRow(Icons.AutoMirrored.Rounded.Message, stringResource(R.string.default_message), state.settings.emergencyMessage, { showMessage = true })
                    HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
                    MenuRow(Icons.Rounded.Group, stringResource(R.string.trusted_contacts), stringResource(R.string.contacts_subtitle), onContacts)
                    HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
                    MenuRow(Icons.Rounded.MedicalInformation, stringResource(R.string.emergency_profile), stringResource(R.string.profile_subtitle), onProfile)
                }
            }

            Spacer(Modifier.height(24.dp))
            SectionLabel(stringResource(R.string.privacy_data))
            Spacer(Modifier.height(10.dp))
            GlassCard {
                Column {
                    MenuRow(Icons.Rounded.BugReport, stringResource(R.string.diagnostics), stringResource(R.string.diagnostics_description), onDiagnostics)
                    HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
                    MenuRow(
                        Icons.Rounded.DeleteForever,
                        stringResource(R.string.clear_local_data),
                        stringResource(R.string.clear_data_description),
                        { showClear = true },
                        trailing = { Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.error) },
                    )
                }
            }

            Spacer(Modifier.height(22.dp))
            Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f), shape = RoundedCornerShape(20.dp)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Rounded.Info, null, tint = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.about_disclaimer), Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
private fun ThemeChip(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 1) },
        leadingIcon = { Icon(icon, null) },
        modifier = modifier,
    )
}

@Composable
private fun MessageDialog(current: String, preview: (String) -> String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by remember(current) { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.default_message)) },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it.take(500) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    supportingText = { Text("${value.length}/500") },
                    shape = RoundedCornerShape(16.dp),
                )
                Spacer(Modifier.height(16.dp))
                EmergencyMessagePreview(preview(value))
            }
        },
        confirmButton = { Button(onClick = { onSave(value) }, enabled = value.isNotBlank()) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        shape = RoundedCornerShape(28.dp),
    )
}

@Composable
fun DiagnosticsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .windowInsetsPadding(WindowInsets.statusBars)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
            Spacer(Modifier.height(8.dp))
            PageHeading(stringResource(R.string.diagnostics), stringResource(R.string.diagnostics_description))
            Spacer(Modifier.height(22.dp))
            GlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    DiagnosticRow(Icons.Rounded.Storage, stringResource(R.string.database), stringResource(R.string.healthy), true)
                    DiagnosticRow(Icons.Rounded.Wifi, stringResource(R.string.network), stringResource(if (state.system.networkAvailable) R.string.connected else R.string.offline), state.system.networkAvailable)
                    DiagnosticRow(Icons.Rounded.PrivacyTip, stringResource(R.string.location), stringResource(if (state.system.locationPermissionGranted) R.string.location_ready else R.string.location_needed), state.system.locationPermissionGranted)
                    DiagnosticRow(Icons.Rounded.Notifications, stringResource(R.string.notification_permission), stringResource(if (state.system.notificationPermissionGranted) R.string.available else R.string.unavailable), state.system.notificationPermissionGranted)
                    DiagnosticRow(Icons.Rounded.PhoneAndroid, stringResource(R.string.bluetooth), stringResource(if (state.system.bluetoothAvailable) R.string.available else R.string.unavailable), state.system.bluetoothAvailable)
                    DiagnosticRow(Icons.Rounded.HealthAndSafety, stringResource(R.string.active_emergency), stringResource(if (state.hasActiveEmergency) R.string.event_active else R.string.none), !state.hasActiveEmergency)
                    DiagnosticRow(Icons.Rounded.Info, stringResource(R.string.app_version), BuildConfig.VERSION_NAME, true)
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.about_disclaimer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DiagnosticRow(icon: ImageVector, label: String, value: String, positive: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Text(label, Modifier.weight(1f).padding(horizontal = 12.dp), fontWeight = FontWeight.SemiBold)
        StatusPill(value, positive)
    }
}
