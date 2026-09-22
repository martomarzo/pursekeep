package app.pursekeep

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import app.pursekeep.ui.PurseKeepTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PurseKeepTheme {
                Scaffold { padding ->
                    Text("PurseKeep ${BuildConfig.VERSION_NAME}", modifier = Modifier.padding(padding))
                }
            }
        }
    }
}
