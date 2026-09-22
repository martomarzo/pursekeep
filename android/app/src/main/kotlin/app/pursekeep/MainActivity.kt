package app.pursekeep

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pursekeep.ui.AppsScreen
import app.pursekeep.ui.HomeScreen
import app.pursekeep.ui.MainViewModel
import app.pursekeep.ui.PairingScreen
import app.pursekeep.ui.PurseKeepTheme
import app.pursekeep.ui.Screen
import androidx.compose.foundation.layout.Box
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    @Suppress("InvalidFragmentVersionForActivityResult")
    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) askNotifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        setContent {
            PurseKeepTheme {
                val state by vm.state.collectAsStateWithLifecycle()
                LifecycleResumeEffect(Unit) { vm.refreshSystemState(); onPauseOrDispose { } }
                BackHandler(enabled = state.screen != Screen.Home) { vm.go(Screen.Home) }
                Scaffold { padding ->
                    Box(Modifier.padding(padding)) {
                        when (state.screen) {
                            Screen.Home -> HomeScreen(state, vm)
                            Screen.Pairing -> PairingScreen(state, vm, onScan = scanAction())
                            Screen.Apps -> AppsScreen(state, vm)
                        }
                    }
                }
            }
        }
    }

    private fun scanAction(): (() -> Unit) = {
        val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
        GmsBarcodeScanning.getClient(this, options).startScan()
            .addOnSuccessListener { barcode -> barcode.rawValue?.let { vm.pairScanned(it) } }
            .addOnFailureListener { e ->
                if (e is MlKitException && e.errorCode == MlKitException.CODE_SCANNER_CANCELLED) {
                    // user backed out of the scanner; not an error
                } else {
                    vm.pairFailed(e.message ?: "Scanner unavailable — enter the token by hand")
                }
            }
    }
}
