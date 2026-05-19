package pl.polsatgranie.smartmegane

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import pl.polsatgranie.smartmegane.data.serial.UsbConnectionState
import pl.polsatgranie.smartmegane.domain.signal.SignalDefinitions
import pl.polsatgranie.smartmegane.domain.signal.SignalState
import pl.polsatgranie.smartmegane.domain.signal.SignalValue
import pl.polsatgranie.smartmegane.ui.CockpitScreen
import pl.polsatgranie.smartmegane.ui.theme.smartmeganeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: MainViewModel = viewModel()
            val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
            val signalState by viewModel.signalState.collectAsStateWithLifecycle()

            smartmeganeTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    CockpitScreen(
                        signalState = signalState,
                        connectionState = connectionState,
                        groups = SignalDefinitions.groups,
                        onReconnect = viewModel::connectFirstAvailable,
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun CockpitPreview() {
    smartmeganeTheme {
        CockpitScreen(
            signalState = SignalState(
                values = mapOf(
                    "wipers.mode" to SignalValue.Enum(2, "Low"),
                    "pedal.brake" to SignalValue.Bool(true),
                    "pedal.clutch" to SignalValue.Bool(false),
                    "door.front_left" to SignalValue.Bool(false),
                    "door.front_right" to SignalValue.Bool(true),
                    "steering.raw" to SignalValue.Number(123.0, "raw"),
                ),
            ),
            connectionState = UsbConnectionState.Disconnected,
            groups = SignalDefinitions.groups,
            onReconnect = {},
        )
    }
}
