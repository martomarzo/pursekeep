package app.pursekeep.ui

import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.pursekeep.capture.KnownApps

@Composable
fun AppsScreen(state: UiState, vm: MainViewModel) {
    val pm = LocalContext.current.packageManager
    fun installed(pkg: String) = try { pm.getPackageInfo(pkg, 0); true } catch (_: PackageManager.NameNotFoundException) { false }
    var custom by rememberSaveable { mutableStateOf("") }

    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Apps to listen to", style = MaterialTheme.typography.headlineSmall)
        KnownApps.all.forEach { app ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(app.label)
                    Text(if (installed(app.packageName)) "installed" else "not installed", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = app.packageName in state.enabled, onCheckedChange = { vm.setPackageEnabled(app.packageName, it) })
            }
        }
        state.custom.forEach { pkg ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column { Text(pkg); TextButton(onClick = { vm.removeCustomPackage(pkg) }) { Text("Remove") } }
                Switch(checked = pkg in state.enabled, onCheckedChange = { vm.setPackageEnabled(pkg, it) })
            }
        }
        OutlinedTextField(value = custom, onValueChange = { custom = it }, label = { Text("Add package name (e.g. com.bank.app)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Button(onClick = { vm.addCustomPackage(custom); custom = "" }, enabled = custom.contains('.')) { Text("Add") }
        TextButton(onClick = { vm.go(Screen.Home) }) { Text("Back") }
    }
}
