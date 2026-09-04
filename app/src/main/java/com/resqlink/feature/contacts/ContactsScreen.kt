package com.resqlink.feature.contacts

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.resqlink.R
import com.resqlink.core.ui.GlassCard
import com.resqlink.core.ui.PageHeading
import com.resqlink.domain.model.EmergencyContact

@Composable
fun ContactsScreen(viewModel: ContactsViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<EmergencyContact?>(null) }
    var formVisible by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<EmergencyContact?>(null) }

    LaunchedEffect(state.saved) {
        if (state.saved) {
            formVisible = false
            editing = null
            viewModel.consumeSaved()
        }
    }
    if (formVisible) ContactFormDialog(
        contact = editing,
        state = state,
        onDismiss = { formVisible = false; editing = null; viewModel.clearErrors() },
        onSave = viewModel::save,
    )
    deleting?.let { contact ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.delete_contact)) },
            text = { Text(stringResource(R.string.delete_contact_body)) },
            confirmButton = { Button(onClick = { viewModel.delete(contact); deleting = null }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } },
            shape = RoundedCornerShape(28.dp),
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = null; formVisible = true }, containerColor = MaterialTheme.colorScheme.primary) {
                Icon(Icons.Rounded.Add, stringResource(R.string.add_contact))
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Spacer(Modifier.height(16.dp))
                PageHeading(stringResource(R.string.contacts_title), stringResource(R.string.contacts_subtitle))
                Spacer(Modifier.height(12.dp))
            }
            if (state.contacts.isEmpty()) item { EmptyContacts { formVisible = true } }
            else items(state.contacts, key = { it.id }) { contact ->
                ContactCard(
                    contact = contact,
                    onPrimary = { viewModel.setPrimary(contact) },
                    onEdit = { editing = contact; formVisible = true },
                    onDelete = { deleting = contact },
                )
            }
            item { Spacer(Modifier.height(92.dp)) }
        }
    }
}

@Composable
private fun ContactCard(
    contact: EmergencyContact,
    onPrimary: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val primary = contact.enabled && contact.priority == 0
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(54.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = if (contact.enabled) 0.14f else 0.06f), shape = CircleShape) {
                Box(contentAlignment = Alignment.Center) { Text(contact.name.take(1).uppercase(), fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary) }
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(contact.name, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Phone, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(contact.phoneNumber, Modifier.padding(start = 5.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (primary) {
                    Text(
                        stringResource(R.string.primary_call_contact),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            IconButton(onClick = onPrimary, enabled = contact.enabled) {
                Icon(
                    if (primary) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    stringResource(if (primary) R.string.primary_call_contact else R.string.set_primary_contact),
                    tint = if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalIconButton(onClick = onEdit) { Icon(Icons.Rounded.Edit, stringResource(R.string.edit_contact)) }
            IconButton(onClick = onDelete) { Icon(Icons.Rounded.DeleteOutline, stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun EmptyContacts(onAdd: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(Modifier.size(74.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Groups, null, Modifier.size(34.dp), tint = MaterialTheme.colorScheme.primary) }
            }
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.empty_contacts_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.empty_contacts_body), Modifier.padding(top = 7.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onAdd, modifier = Modifier.padding(top = 18.dp)) {
                Icon(Icons.Rounded.Add, null)
                Text(stringResource(R.string.add_contact), Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun ContactFormDialog(
    contact: EmergencyContact?,
    state: ContactsUiState,
    onDismiss: () -> Unit,
    onSave: (Long, String, String, String, Boolean) -> Unit,
) {
    var name by remember(contact?.id) { mutableStateOf(contact?.name.orEmpty()) }
    var phone by remember(contact?.id) { mutableStateOf(contact?.phoneNumber.orEmpty()) }
    var email by remember(contact?.id) { mutableStateOf(contact?.email.orEmpty()) }
    var enabled by remember(contact?.id) { mutableStateOf(contact?.enabled ?: true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Person, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(stringResource(if (contact == null) R.string.add_contact else R.string.edit_contact)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.name)) },
                    singleLine = true,
                    isError = state.nameError,
                    supportingText = if (state.nameError) ({ Text(stringResource(R.string.name_required)) }) else null,
                    shape = RoundedCornerShape(16.dp),
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text(stringResource(R.string.phone_number)) },
                    singleLine = true,
                    isError = state.phoneError || state.duplicateError,
                    supportingText = when {
                        state.duplicateError -> ({ Text(stringResource(R.string.duplicate_contact)) })
                        state.phoneError -> ({ Text(stringResource(R.string.phone_invalid)) })
                        else -> null
                    },
                    shape = RoundedCornerShape(16.dp),
                )
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text(stringResource(R.string.email_optional)) }, singleLine = true, shape = RoundedCornerShape(16.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.enabled_for_sos), Modifier.weight(1f))
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(contact?.id ?: 0, name, phone, email, enabled) }, enabled = !state.saving) {
                if (state.saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        shape = RoundedCornerShape(28.dp),
    )
}
