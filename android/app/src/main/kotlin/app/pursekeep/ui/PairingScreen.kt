package app.pursekeep.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun PairingScreen(state: UiState, vm: MainViewModel, onScan: (() -> Unit)?) {
    var url by rememberSaveable { mutableStateOf(state.serverUrl ?: "https://") }
    var token by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Pair this phone", style = MaterialTheme.typography.headlineSmall)
        Text("On the web app open Settings › Devices, create a device and scan the QR code it shows.")
        if (onScan != null) Button(onClick = onScan, modifier = Modifier.fillMaxWidth()) { Text("Scan QR code") }
        Text("Or enter the details by hand:", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("Server URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text("Device token") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        state.pairingError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = { vm.pairManual(url, token) }, modifier = Modifier.fillMaxWidth()) { Text("Pair") }
        TextButton(onClick = { vm.go(Screen.Home) }) { Text("Cancel") }
    }
}
