package pl.polsatgranie.smarmegane.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pl.polsatgranie.smarmegane.data.serial.UsbConnectionState
import pl.polsatgranie.smarmegane.domain.signal.SignalGroup
import pl.polsatgranie.smarmegane.domain.signal.SignalState
import pl.polsatgranie.smarmegane.domain.signal.displayText

@Composable
fun CockpitScreen(
    signalState: SignalState,
    connectionState: UsbConnectionState,
    groups: List<SignalGroup>,
    onReconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isConnected = connectionState is UsbConnectionState.Connected
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(text = "SmarMegane", style = MaterialTheme.typography.headlineSmall)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "USB: ${connectionStateLabel(connectionState)}",
                    style = MaterialTheme.typography.titleMedium,
                )
                if (!isConnected) {
                    Button(onClick = onReconnect) {
                        Text(text = "Reconnect")
                    }
                }
            }
        }

        items(groups) { group ->
            SignalGroupCard(group = group, state = signalState)
        }
    }
}

@Composable
private fun SignalGroupCard(
    group: SignalGroup,
    state: SignalState,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = group.title, style = MaterialTheme.typography.titleMedium)
            group.keys.forEachIndexed { index, key ->
                SignalRow(
                    label = key.label,
                    value = state.get(key)?.displayText() ?: "--",
                )
                if (index != group.keys.lastIndex) {
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun SignalRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label)
        Text(text = value, fontWeight = FontWeight.SemiBold)
    }
}

private fun connectionStateLabel(state: UsbConnectionState): String =
    when (state) {
        UsbConnectionState.Disconnected -> "Disconnected"
        UsbConnectionState.Searching -> "Searching"
        UsbConnectionState.NoDevice -> "No device"
        is UsbConnectionState.PermissionRequired -> "Permission required"
        is UsbConnectionState.PermissionDenied -> "Permission denied"
        is UsbConnectionState.Connected -> "Connected"
        is UsbConnectionState.Error -> "Error: ${state.message}"
    }
