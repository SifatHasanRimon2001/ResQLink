package com.resqlink.feature.history

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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.resqlink.core.ui.StatusPill
import com.resqlink.domain.model.EmergencyEvent
import com.resqlink.domain.model.EventStatus
import com.resqlink.domain.model.LocationStatus
import java.text.DateFormat

@Composable
fun HistoryScreen(viewModel: HistoryViewModel) {
    val events by viewModel.events.collectAsStateWithLifecycle()
    val userMessage by viewModel.userMessage.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<EmergencyEvent?>(null) }
    var deleting by remember { mutableStateOf<EmergencyEvent?>(null) }

    selected?.let { event -> EventDetailsDialog(event, onDismiss = { selected = null }, onDelete = { selected = null; deleting = event }) }
    deleting?.let { event ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.delete_event)) },
            text = { Text(stringResource(R.string.delete_event_body)) },
            confirmButton = { Button(onClick = { viewModel.delete(event.id); deleting = null }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } },
            shape = RoundedCornerShape(28.dp),
        )
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Spacer(Modifier.height(16.dp))
                PageHeading(stringResource(R.string.history_title), stringResource(R.string.history_subtitle))
                Spacer(Modifier.height(12.dp))
            }
            userMessage?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
            if (events.isEmpty()) item { EmptyHistory() }
            else items(events, key = { it.id }) { event -> EventCard(event, onClick = { selected = event }) }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun EventCard(event: EmergencyEvent, onClick: () -> Unit) {
    GlassCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                Modifier.size(50.dp),
                shape = CircleShape,
                color = eventColor(event.status).copy(alpha = 0.12f),
            ) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.History, null, tint = eventColor(event.status)) } }
            Column(Modifier.weight(1f).padding(horizontal = 13.dp)) {
                Text(eventLabel(event.status), fontWeight = FontWeight.Bold)
                Text(
                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(event.startedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(Modifier.padding(top = 5.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(durationLabel(event), style = MaterialTheme.typography.labelMedium)
                    Text("${event.alertAttempts} alert${if (event.alertAttempts == 1) "" else "s"}", style = MaterialTheme.typography.labelMedium)
                }
            }
            StatusPill(
                text = if (event.locationStatus == LocationStatus.CAPTURED) stringResource(R.string.available) else stringResource(R.string.unavailable),
                positive = event.locationStatus == LocationStatus.CAPTURED,
            )
        }
    }
}

@Composable
private fun EmptyHistory() {
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(Modifier.size(76.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.CheckCircle, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary) }
            }
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.empty_history_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.empty_history_body), Modifier.padding(top = 7.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EventDetailsDialog(event: EmergencyEvent, onDismiss: () -> Unit, onDelete: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.event_details)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(15.dp)) {
                DetailRow(stringResource(R.string.status), eventLabel(event.status))
                DetailRow(stringResource(R.string.started), DateFormat.getDateTimeInstance().format(event.startedAt))
                DetailRow(stringResource(R.string.ended), event.endedAt?.let { DateFormat.getDateTimeInstance().format(it) } ?: "—")
                DetailRow(stringResource(R.string.duration), durationLabel(event))
                DetailRow(stringResource(R.string.alert_attempts), event.alertAttempts.toString())
                DetailRow(
                    stringResource(R.string.location_status),
                    if (event.locationStatus == LocationStatus.CAPTURED) stringResource(R.string.location_available) else stringResource(R.string.location_unavailable),
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.back)) } },
        dismissButton = {
            if (event.status != EventStatus.ACTIVE && event.status != EventStatus.ACTIVATING) {
                TextButton(onClick = onDelete) {
                    Icon(Icons.Rounded.DeleteOutline, null, tint = MaterialTheme.colorScheme.error)
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        shape = RoundedCornerShape(28.dp),
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(1.2f), fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun eventColor(status: EventStatus) = when (status) {
    EventStatus.ACTIVATING, EventStatus.ACTIVE -> MaterialTheme.colorScheme.error
    EventStatus.COMPLETED -> MaterialTheme.colorScheme.primary
    EventStatus.CANCELLED -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun eventLabel(status: EventStatus) = stringResource(
    when (status) {
        EventStatus.ACTIVATING, EventStatus.ACTIVE -> R.string.event_active
        EventStatus.COMPLETED -> R.string.event_completed
        EventStatus.CANCELLED -> R.string.event_cancelled
    },
)

private fun durationLabel(event: EmergencyEvent): String {
    val seconds = ((event.endedAt ?: System.currentTimeMillis()) - event.startedAt).coerceAtLeast(0) / 1_000
    return if (seconds < 60) "${seconds}s" else "${seconds / 60}m ${seconds % 60}s"
}
