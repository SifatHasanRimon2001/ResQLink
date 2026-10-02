package com.resqlink.feature.profile

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
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.resqlink.core.ui.EmergencyMessagePreview
import com.resqlink.core.ui.PageHeading
import com.resqlink.core.ui.SectionLabel
import com.resqlink.domain.model.EmergencyProfile

@Composable
fun ProfileScreen(viewModel: ProfileViewModel, onBack: () -> Unit) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val userMessage by viewModel.userMessage.collectAsStateWithLifecycle()
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var information by remember { mutableStateOf("") }
    var language by remember { mutableStateOf("English") }

    LaunchedEffect(profile) {
        name = profile.name
        notes = profile.notes
        address = profile.homeAddress
        information = profile.importantInformation
        language = profile.preferredLanguage
    }

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
            PageHeading(stringResource(R.string.profile_title), stringResource(R.string.profile_subtitle))
            Spacer(Modifier.height(22.dp))
            Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
                ProfileField(name, { name = it }, stringResource(R.string.full_name), singleLine = true)
                ProfileField(notes, { notes = it }, stringResource(R.string.emergency_notes), minLines = 3)
                ProfileField(address, { address = it }, stringResource(R.string.home_address), minLines = 2)
                ProfileField(information, { information = it }, stringResource(R.string.important_information), minLines = 3)
                ProfileField(language, { language = it }, stringResource(R.string.preferred_language), singleLine = true)
            }
            Spacer(Modifier.height(18.dp))
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                shape = RoundedCornerShape(18.dp),
            ) {
                Row(Modifier.padding(15.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        stringResource(R.string.profile_privacy_note),
                        Modifier.padding(start = 11.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Spacer(Modifier.height(22.dp))
            Button(
                onClick = {
                    viewModel.save(EmergencyProfile(name, notes, address, information, language))
                },
                enabled = name.isNotBlank() && notes.isNotBlank() && !saving,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(18.dp),
            ) { Text(stringResource(R.string.save), fontWeight = FontWeight.Bold) }
            userMessage?.let { Text(it, modifier = Modifier.padding(top = 10.dp)) }
            Spacer(Modifier.height(22.dp))
            SectionLabel(stringResource(R.string.message_preview_title))
            Spacer(Modifier.height(10.dp))
            EmergencyMessagePreview(
                viewModel.messagePreview(EmergencyProfile(name, notes, address, information, language)),
            )
            if (name.isBlank() || notes.isBlank()) {
                Text(
                    stringResource(R.string.profile_required_hint),
                    Modifier.padding(top = 10.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun ProfileField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    singleLine: Boolean = false,
    minLines: Int = 1,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = singleLine,
        minLines = minLines,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(17.dp),
    )
}
