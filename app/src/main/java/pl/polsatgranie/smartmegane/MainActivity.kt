package pl.polsatgranie.smartmegane

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import pl.polsatgranie.smartmegane.data.serial.UsbConnectionState
import pl.polsatgranie.smartmegane.data.vehicle.PlaceholderVehicleTelemetry
import pl.polsatgranie.smartmegane.domain.vehicle.GearEstimate
import pl.polsatgranie.smartmegane.domain.vehicle.GearEstimateStatus
import pl.polsatgranie.smartmegane.domain.vehicle.GearGuidance
import pl.polsatgranie.smartmegane.ui.CockpitScreen
import pl.polsatgranie.smartmegane.ui.theme.SmartMeganeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContent {
            val viewModel: MainViewModel = viewModel()
            val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
            val vehicleState by viewModel.vehicleState.collectAsStateWithLifecycle()
            val gearGuidance by viewModel.gearGuidance.collectAsStateWithLifecycle()

            SmartMeganeTheme {
                CockpitScreen(
                    vehicleState = vehicleState,
                    gearGuidance = gearGuidance,
                    connectionState = connectionState,
                    onReconnect = viewModel::connectFirstAvailable,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Preview(
    showBackground = true,
    widthDp = 390,
    heightDp = 360,
)
@Composable
fun CockpitPreview() {
    SmartMeganeTheme {
        CockpitScreen(
            vehicleState = PlaceholderVehicleTelemetry.parkedPreviewState,
            gearGuidance = GearGuidance(
                estimate = GearEstimate(status = GearEstimateStatus.STATIONARY),
                preferredGear = 1,
                targetRpm = 1_480,
                targetRpmRange = 1_310..1_650,
                confidence = 0.96f,
            ),
            connectionState = UsbConnectionState.Disconnected,
            onReconnect = {},
        )
    }
}
