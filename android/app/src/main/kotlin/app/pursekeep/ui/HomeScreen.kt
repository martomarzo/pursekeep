package app.pursekeep.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.pursekeep.BuildConfig
import app.pursekeep.net.UpdateChecker
import java.text.DateFormat
import java.util.Date

@Composable
fun HomeScreen(state: UiState, vm: MainViewModel) {
    val ctx = LocalContext.current
    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("PurseKeep", style = MaterialTheme.typography.headlineMedium)
            Text("Companion v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall)
        }
        state.updateTag?.let { tag ->
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Update available: $tag", style = MaterialTheme.typography.titleSmall)
                        TextButton(onClick = { SystemActions.openUrl(ctx, UpdateChecker.RELEASES_PAGE) }) { Text("Download") }
                    }
                }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Pairing", style = MaterialTheme.typography.titleMedium)
                    when {
                        state.pairing != null -> {
                            Text("Paired with ${state.pairing.serverUrl.removePrefix("https://")}")
                            Text("Device: ${state.pairing.deviceName}", style = MaterialTheme.typography.bodySmall)
                            state.lastSentAt?.let { Text("Last sent: ${DateFormat.getDateTimeInstance().format(Date(it))}", style = MaterialTheme.typography.bodySmall) }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { vm.go(Screen.Web) }) { Text("Open PurseKeep") }
                                TextButton(onClick = { vm.unpair() }) { Text("Unpair") }
                            }
                            TextButton(onClick = { SystemActions.openUrl(ctx, state.pairing.serverUrl) }) { Text("Open in browser") }
                        }
                        state.pairingBroken -> {
                            Text("The server rejected this phone's token. Pair again from Settings › Devices.", color = MaterialTheme.colorScheme.error)
                            Button(onClick = { vm.go(Screen.Pairing) }) { Text("Pair again") }
                        }
                        else -> {
                            Text("Not paired. Create a device on the web app (Settings › Devices) and scan its QR code.")
                            Button(onClick = { vm.go(Screen.Pairing) }) { Text("Pair this phone") }
                        }
                    }
                }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Notification access", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "PurseKeep reads the payment notifications of the apps you enable (Google Wallet by default) " +
                            "and sends their text to your own server. Nothing else is read or stored.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (state.notificationAccess) Text("Granted ✓") else Button(onClick = { SystemActions.openNotificationAccess(ctx) }) { Text("Grant notification access") }
                    if (state.batteryExempt) Text("Battery optimisation: exempt ✓", style = MaterialTheme.typography.bodySmall)
                    else TextButton(onClick = { SystemActions.requestBatteryExemption(ctx) }) { Text("Exempt from battery optimisation") }
                    TextButton(onClick = { vm.go(Screen.Apps) }) { Text("Apps to listen to (${state.enabled.size})") }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.sendTest() }, enabled = state.pairing != null) { Text("Send test") }
                Spacer(Modifier.width(4.dp))
                Text("Pending: ${state.pending}", modifier = Modifier.padding(top = 12.dp))
            }
        }
        item { Text("Recent events", style = MaterialTheme.typography.titleMedium) }
        items(state.events, key = { it.id }) { e ->
            Column {
                Text("${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(e.at))} · ${e.kind}", style = MaterialTheme.typography.labelSmall)
                Text(e.message, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
