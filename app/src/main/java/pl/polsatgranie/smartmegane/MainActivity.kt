package pl.polsatgranie.smartmegane

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.WindowManager
import android.widget.Toast
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
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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
            val rpmSuitability by viewModel.rpmSuitability.collectAsStateWithLifecycle()
            val parkingSlopeGuidance by viewModel.parkingSlopeGuidance.collectAsStateWithLifecycle()
            val signalState by viewModel.signalState.collectAsStateWithLifecycle()
            val phoneOrientation by viewModel.phoneOrientation.collectAsStateWithLifecycle()
            val currentTrip by viewModel.currentTrip.collectAsStateWithLifecycle()
            val tripHistory by viewModel.tripHistory.collectAsStateWithLifecycle()
            val lastTripSummary by viewModel.lastTripSummary.collectAsStateWithLifecycle()

            SmartMeganeTheme {
                CockpitScreen(
                    vehicleState = vehicleState,
                    gearGuidance = gearGuidance,
                    rpmSuitability = rpmSuitability,
                    parkingSlopeGuidance = parkingSlopeGuidance,
                    signalState = signalState,
                    phoneOrientation = phoneOrientation,
                    currentTrip = currentTrip,
                    tripHistory = tripHistory,
                    lastTripSummary = lastTripSummary,
                    connectionState = connectionState,
                    onReconnect = viewModel::connectFirstAvailable,
                    onLaunchAssistant = ::launchDefaultAssistant,
                    onLaunchMapsSplitScreen = ::launchMapsAdjacent,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    private fun launchDefaultAssistant() {
        val voiceIntent = Intent(RecognizerIntent.ACTION_VOICE_SEARCH_HANDS_FREE).apply {
            putExtra(RecognizerIntent.EXTRA_SECURE, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val launched = runCatching { startActivity(voiceIntent) }.isSuccess
        if (!launched) {
            Toast.makeText(
                this,
                "Brak aplikacji obsługującej komendy głosowe",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    private fun launchMapsAdjacent() {
        val mapsIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("geo:0,0"),
        ).apply {
            setPackage("com.google.android.apps.maps")
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or
                    Intent.FLAG_ACTIVITY_MULTIPLE_TASK,
            )
        }
        val launched = runCatching { startActivity(mapsIntent) }.isSuccess
        if (!launched) {
            Toast.makeText(
                this,
                "Mapy Google nie są dostępne",
                Toast.LENGTH_SHORT,
            ).show()
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
            rpmSuitability =
                pl.polsatgranie.smartmegane.domain.vehicle.RpmSuitabilityState(),
            parkingSlopeGuidance =
                pl.polsatgranie.smartmegane.domain.vehicle.ParkingSlopeGuidance(),
            signalState = pl.polsatgranie.smartmegane.domain.signal.SignalState(),
            phoneOrientation = pl.polsatgranie.smartmegane.domain.phone.PhoneOrientation(),
            currentTrip =
                pl.polsatgranie.smartmegane.domain.trip.TripLiveStats(),
            tripHistory = emptyList(),
            lastTripSummary = null,
            connectionState = UsbConnectionState.Disconnected,
            onReconnect = {},
            onLaunchAssistant = {},
            onLaunchMapsSplitScreen = {},
        )
    }
}
