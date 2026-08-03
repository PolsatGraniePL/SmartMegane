package pl.polsatgranie.smartmegane.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pl.polsatgranie.smartmegane.data.serial.UsbConnectionState
import pl.polsatgranie.smartmegane.domain.vehicle.GearEstimateStatus
import pl.polsatgranie.smartmegane.domain.vehicle.IndicatorSeverity
import pl.polsatgranie.smartmegane.domain.vehicle.GearGuidance
import pl.polsatgranie.smartmegane.domain.vehicle.RpmSuitabilityState
import pl.polsatgranie.smartmegane.domain.vehicle.RpmSuitabilityZone
import pl.polsatgranie.smartmegane.domain.vehicle.AntiStallGuidance
import pl.polsatgranie.smartmegane.domain.vehicle.AntiStallStatus
import pl.polsatgranie.smartmegane.domain.vehicle.AutoDisplayMode
import pl.polsatgranie.smartmegane.domain.vehicle.ShiftDirection
import pl.polsatgranie.smartmegane.domain.vehicle.VehicleIndicator
import pl.polsatgranie.smartmegane.domain.vehicle.VehiclePowerState
import pl.polsatgranie.smartmegane.domain.vehicle.VehicleState
import pl.polsatgranie.smartmegane.domain.vehicle.ParkingSlopeGuidance
import pl.polsatgranie.smartmegane.domain.vehicle.ParkingSlopeLevel
import pl.polsatgranie.smartmegane.domain.vehicle.WiperMode
import pl.polsatgranie.smartmegane.domain.trip.TripLiveStats
import pl.polsatgranie.smartmegane.domain.trip.TripSummary
import pl.polsatgranie.smartmegane.domain.signal.SignalDefinitions
import pl.polsatgranie.smartmegane.domain.signal.SignalState
import pl.polsatgranie.smartmegane.domain.signal.SignalValue
import pl.polsatgranie.smartmegane.domain.phone.PhoneOrientation
import pl.polsatgranie.smartmegane.domain.vehicle.hasCriticalWarning
import pl.polsatgranie.smartmegane.domain.vehicle.hasNonCriticalWarning
import pl.polsatgranie.smartmegane.domain.vehicle.isActive
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

private val CanvasBlack = Color(0xFF080B0D)
private val Ink = Color(0xFFE9EEF0)
private val Muted = Color(0xFF8E999D)
private val Green = Color(0xFF31C986)
private val Amber = Color(0xFFD99A38)
private val Red = Color(0xFFE64B58)
private val Blue = Color(0xFF4A91DC)
private const val STEERING_VISIBILITY_TIMEOUT_MS = 5_000L
private const val STEERING_DISPLAY_STEP_DEGREES = 0.5f
private const val MAX_STEERING_WHEEL_ANGLE_DEGREES = 576f
private const val MAX_ROAD_WHEEL_ANGLE_DEGREES = 32f
private const val TAB_TITLE_VISIBLE_MS = 1_800L
private const val STALK_MULTI_CLICK_SETTLE_MS = 420L
private const val FUEL_TANK_CAPACITY_LITERS = 60.0

private enum class DashboardTab(val title: String) {
    AUTOMATIC("Auto"),
    VEHICLE("Samochód"),
    SPEED("Prędkość"),
    FUEL("Paliwo"),
    TRIP("Podróż"),
    HISTORY("Historia"),
    DIAGNOSTICS("Diagnostyka"),
}

@Composable
fun CockpitScreen(
    vehicleState: VehicleState,
    gearGuidance: GearGuidance,
    rpmSuitability: RpmSuitabilityState,
    parkingSlopeGuidance: ParkingSlopeGuidance,
    signalState: SignalState,
    phoneOrientation: PhoneOrientation,
    currentTrip: TripLiveStats,
    tripHistory: List<TripSummary>,
    lastTripSummary: TripSummary?,
    autoDisplayMode: AutoDisplayMode,
    connectionState: UsbConnectionState,
    onReconnect: () -> Unit,
    onLaunchAssistant: () -> Unit,
    onLaunchMapsSplitScreen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by remember { mutableStateOf(DashboardTab.AUTOMATIC) }
    var diagnosticsUnlocked by remember { mutableStateOf(false) }
    var navigationDirection by remember { mutableStateOf(1) }
    var tabTitleVisible by remember { mutableStateOf(true) }
    val stalkScope = rememberCoroutineScope()
    var pendingStalkClick by remember { mutableStateOf<Job?>(null) }
    var stalkClickCount by remember { mutableStateOf(0) }
    var stalkWasPressed by remember { mutableStateOf(false) }
    val visibleTabs = DashboardTab.entries.filter {
        it != DashboardTab.DIAGNOSTICS || diagnosticsUnlocked || selectedTab == it
    }
    val changeTab: (Int) -> Unit = { delta ->
        val tabs = visibleTabs
        val currentIndex = tabs.indexOf(selectedTab).coerceAtLeast(0)
        val leavingDiagnostics = selectedTab == DashboardTab.DIAGNOSTICS
        navigationDirection = if (delta >= 0) 1 else -1
        selectedTab = tabs[(currentIndex + delta + tabs.size) % tabs.size]
        if (leavingDiagnostics) diagnosticsUnlocked = false
    }
    val stalkPressed =
        vehicleState.isTripComputerUpPressed ||
            vehicleState.isTripComputerDownPressed

    LaunchedEffect(selectedTab) {
        tabTitleVisible = true
        delay(TAB_TITLE_VISIBLE_MS)
        tabTitleVisible = false
    }
    LaunchedEffect(stalkPressed) {
        if (stalkPressed && !stalkWasPressed) {
            stalkClickCount += 1
            pendingStalkClick?.cancel()
            pendingStalkClick = stalkScope.launch {
                delay(STALK_MULTI_CLICK_SETTLE_MS)
                when (stalkClickCount) {
                    1 -> changeTab(1)
                    2 -> changeTab(-1)
                    3 -> onLaunchAssistant()
                    5 -> onLaunchMapsSplitScreen()
                    10 -> {
                        diagnosticsUnlocked = true
                        navigationDirection = 1
                        selectedTab = DashboardTab.DIAGNOSTICS
                    }
                }
                stalkClickCount = 0
                pendingStalkClick = null
            }
        }
        stalkWasPressed = stalkPressed
    }

    val leftTurnPulse by animateFloatAsState(
        targetValue = if (vehicleState.isLeftTurnSignalOn) 1f else 0f,
        animationSpec = tween(if (vehicleState.isLeftTurnSignalOn) 65 else 115),
        label = "leftTurnCanPhase",
    )
    val rightTurnPulse by animateFloatAsState(
        targetValue = if (vehicleState.isRightTurnSignalOn) 1f else 0f,
        animationSpec = tween(if (vehicleState.isRightTurnSignalOn) 65 else 115),
        label = "rightTurnCanPhase",
    )

    BoxWithConstraints(
        modifier = modifier.background(CanvasBlack),
    ) {
        val uiScale = min(maxWidth.value / 390f, maxHeight.value / 360f)
            .coerceIn(0.82f, 1.18f)
        DashboardBackdrop()
        EdgeGlow(
            state = vehicleState,
            leftTurnPulse = leftTurnPulse,
            rightTurnPulse = rightTurnPulse,
        )
        RpmSuitabilityRail(
            state = rpmSuitability,
            gearGuidance = gearGuidance,
            showRpmValue = selectedTab == DashboardTab.VEHICLE,
            uiScale = uiScale,
            modifier = Modifier
                .width((43f * uiScale).dp)
                .fillMaxHeight()
                .align(Alignment.CenterStart),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = (58f * uiScale).dp,
                    top = (10f * uiScale).dp,
                    end = (15f * uiScale).dp,
                    bottom = (9f * uiScale).dp,
                ),
        ) {
            TopSignalStrip(
                state = vehicleState,
                leftPulse = leftTurnPulse,
                rightPulse = rightTurnPulse,
                uiScale = uiScale,
            )
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    val direction = navigationDirection
                    (
                        fadeIn(tween(210)) +
                            slideInHorizontally(tween(260)) {
                                direction * it / 3
                            }
                        ).togetherWith(
                        fadeOut(tween(155)) +
                            slideOutHorizontally(tween(220)) {
                                -direction * it / 3
                            },
                    )
                },
                contentAlignment = Alignment.Center,
                label = "dashboardTabs",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(selectedTab) {
                        var horizontalDrag = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { horizontalDrag = 0f },
                            onHorizontalDrag = { _, dragAmount ->
                                horizontalDrag += dragAmount
                            },
                            onDragCancel = { horizontalDrag = 0f },
                            onDragEnd = {
                                val threshold = size.width * 0.14f
                                when {
                                    horizontalDrag <= -threshold -> changeTab(1)
                                    horizontalDrag >= threshold -> changeTab(-1)
                                }
                                horizontalDrag = 0f
                            },
                        )
                    },
            ) { tab ->
                DashboardTabContent(
                    tab = tab,
                    state = vehicleState,
                    currentTrip = currentTrip,
                    tripHistory = tripHistory,
                    lastTripSummary = lastTripSummary,
                    autoDisplayMode = autoDisplayMode,
                    parkingSlopeGuidance = parkingSlopeGuidance,
                    rpmSuitability = rpmSuitability,
                    gearGuidance = gearGuidance,
                    signalState = signalState,
                    phoneOrientation = phoneOrientation,
                    connectionState = connectionState,
                    uiScale = uiScale,
                )
            }
            DashboardTabTitle(
                selectedTab = selectedTab,
                tabs = visibleTabs,
                visible = tabTitleVisible,
                uiScale = uiScale,
            )
            BottomTelemetry(
                state = vehicleState,
                connectionState = connectionState,
                onReconnect = onReconnect,
                showPreciseValues = selectedTab == DashboardTab.VEHICLE,
                uiScale = uiScale,
            )
        }
        TurnSignalEdgeStroke(
            state = vehicleState,
            leftTurnPulse = leftTurnPulse,
            rightTurnPulse = rightTurnPulse,
        )
    }
}

@Composable
private fun DashboardBackdrop() {
    Canvas(Modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF0A0D10),
                    Color(0xFF141A1E),
                    Color(0xFF090C0F),
                ),
            ),
        )
        val glowCenter = Offset(size.width * 0.63f, size.height * 0.40f)
        val glowRadius = maxOf(size.width, size.height) * 0.72f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF71818A).copy(alpha = 0.18f),
                    Color(0xFF344149).copy(alpha = 0.10f),
                    Color.Transparent,
                ),
                center = glowCenter,
                radius = glowRadius,
            ),
            center = glowCenter,
            radius = glowRadius,
        )
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.Black.copy(alpha = 0.19f),
                    Color.Transparent,
                    Color.Black.copy(alpha = 0.28f),
                ),
            ),
        )
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color.Black.copy(alpha = 0.20f),
                    Color.Transparent,
                    Color.Black.copy(alpha = 0.15f),
                ),
            ),
        )
    }
}

@Composable
private fun TopSignalStrip(
    state: VehicleState,
    leftPulse: Float,
    rightPulse: Float,
    uiScale: Float,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height((36f * uiScale).dp),
        horizontalArrangement = Arrangement.spacedBy(
            (9f * uiScale).dp,
            Alignment.CenterHorizontally,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SignalIcon(
            indicator = VehicleIndicator.LEFT_TURN,
            active = state.isLeftTurnSignalOn,
            activeTint = if (state.areHazardLightsOn) Amber else Green,
            pulse = leftPulse,
            size = uiScale,
        )
        SignalIcon(
            VehicleIndicator.POSITION_LIGHTS,
            state.arePositionLightsOn,
            Green,
            1f,
            uiScale,
        )
        SignalIcon(
            VehicleIndicator.LOW_BEAM,
            state.areLowBeamLightsOn,
            Green,
            1f,
            uiScale,
        )
        SignalIcon(
            VehicleIndicator.HIGH_BEAM,
            state.areHighBeamLightsOn,
            Blue,
            1f,
            uiScale,
        )
        SignalIcon(
            VehicleIndicator.FRONT_FOG,
            state.areFrontFogLightsOn,
            Green,
            1f,
            uiScale,
        )
        SignalIcon(
            VehicleIndicator.REAR_FOG,
            state.areRearFogLightsOn,
            Amber,
            1f,
            uiScale,
        )
        SignalIcon(
            indicator = VehicleIndicator.RIGHT_TURN,
            active = state.isRightTurnSignalOn,
            activeTint = if (state.areHazardLightsOn) Amber else Green,
            pulse = rightPulse,
            size = uiScale,
        )
    }
}

@Composable
private fun SignalIcon(
    indicator: VehicleIndicator,
    active: Boolean,
    activeTint: Color,
    pulse: Float,
    size: Float,
) {
    val isTurn = indicator == VehicleIndicator.LEFT_TURN ||
        indicator == VehicleIndicator.RIGHT_TURN ||
        indicator == VehicleIndicator.HAZARD
    val strength = if (isTurn && active) pulse else if (active) 1f else 0f
    val tint = if (active) {
        activeTint.copy(alpha = 0.32f + strength * 0.68f)
    } else {
        Ink.copy(alpha = 0.12f)
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size((29f * size).dp)
            .graphicsLayer {
                val iconScale = if (isTurn && active) 0.94f + 0.06f * pulse else 1f
                scaleX = iconScale
                scaleY = iconScale
            }
            .drawBehind {
                if (active) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(
                                activeTint.copy(alpha = 0.20f * strength),
                                Color.Transparent,
                            ),
                        ),
                        radius = this.size.minDimension * 0.72f,
                    )
                }
            },
    ) {
        IndicatorSymbol(
            indicator = indicator,
            tint = tint,
            modifier = Modifier.size((22f * size).dp),
        )
    }
}

@Composable
private fun DashboardTabContent(
    tab: DashboardTab,
    state: VehicleState,
    currentTrip: TripLiveStats,
    tripHistory: List<TripSummary>,
    lastTripSummary: TripSummary?,
    autoDisplayMode: AutoDisplayMode,
    parkingSlopeGuidance: ParkingSlopeGuidance,
    rpmSuitability: RpmSuitabilityState,
    gearGuidance: GearGuidance,
    signalState: SignalState,
    phoneOrientation: PhoneOrientation,
    connectionState: UsbConnectionState,
    uiScale: Float,
) {
    Column(Modifier.fillMaxSize()) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            when (tab) {
                DashboardTab.AUTOMATIC ->
                    PrimaryReadout(
                        state = state,
                        lastTripSummary = lastTripSummary,
                        autoDisplayMode = autoDisplayMode,
                        parkingSlopeGuidance = parkingSlopeGuidance,
                        uiScale = uiScale,
                        modifier = Modifier.fillMaxSize(),
                    )

                DashboardTab.VEHICLE ->
                    VehicleDetailReadout(
                        state = state,
                        parkingSlopeGuidance = parkingSlopeGuidance,
                        uiScale = uiScale,
                        modifier = Modifier.fillMaxSize(),
                    )

                DashboardTab.SPEED ->
                    SpeedValueReadout(
                        speedKph = state.speedKph,
                        uiScale = uiScale,
                        modifier = Modifier.fillMaxSize(),
                    )

                DashboardTab.FUEL ->
                    FuelReadout(
                        state = state,
                        trip = currentTrip,
                        uiScale = uiScale,
                        modifier = Modifier.fillMaxSize(),
                    )

                DashboardTab.TRIP ->
                    TripReadout(
                        state = state,
                        trip = currentTrip,
                        uiScale = uiScale,
                        modifier = Modifier.fillMaxSize(),
                    )

                DashboardTab.HISTORY ->
                    TripHistoryReadout(
                        history = tripHistory,
                        uiScale = uiScale,
                        modifier = Modifier.fillMaxSize(),
                    )

                DashboardTab.DIAGNOSTICS ->
                    DiagnosticsReadout(
                        state = state,
                        gearGuidance = gearGuidance,
                        rpmSuitability = rpmSuitability,
                        parkingSlopeGuidance = parkingSlopeGuidance,
                        signalState = signalState,
                        phoneOrientation = phoneOrientation,
                        connectionState = connectionState,
                        trip = currentTrip,
                        uiScale = uiScale,
                        modifier = Modifier.fillMaxSize(),
                    )
            }
        }
        WarningTray(
            state = state,
            uiScale = uiScale,
        )
    }
}

@Composable
private fun DashboardTabTitle(
    selectedTab: DashboardTab,
    tabs: List<DashboardTab>,
    visible: Boolean,
    uiScale: Float,
) {
    val selectedIndex = tabs.indexOf(selectedTab).coerceAtLeast(0)
    val previous = tabs[(selectedIndex - 1 + tabs.size) % tabs.size]
    val next = tabs[(selectedIndex + 1) % tabs.size]
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height((19f * uiScale).dp),
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(420)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TabTitleText(
                    text = previous.title,
                    selected = false,
                    uiScale = uiScale,
                    modifier = Modifier.weight(1f),
                )
                TabTitleText(
                    text = selectedTab.title,
                    selected = true,
                    uiScale = uiScale,
                    modifier = Modifier.weight(1f),
                )
                TabTitleText(
                    text = next.title,
                    selected = false,
                    uiScale = uiScale,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun TabTitleText(
    text: String,
    selected: Boolean,
    uiScale: Float,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier,
    ) {
        Text(
            text = text.uppercase(Locale.getDefault()),
            color = Ink.copy(alpha = if (selected) 0.58f else 0.16f),
            fontSize = ((if (selected) 8.2f else 6.7f) * uiScale).sp,
            lineHeight = (9f * uiScale).sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            letterSpacing = ((if (selected) 1.15f else 0.65f) * uiScale).sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun VehicleDetailReadout(
    state: VehicleState,
    parkingSlopeGuidance: ParkingSlopeGuidance,
    uiScale: Float,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.padding(horizontal = (5f * uiScale).dp),
    ) {
        VehiclePowerHalo(
            powerState = state.powerState,
            modifier = Modifier
                .fillMaxHeight(0.92f)
                .aspectRatio(0.72f),
        )
        VehicleStatusSymbol(
            state = state,
            forceSteeringGuide = true,
            modifier = Modifier
                .fillMaxHeight(0.88f)
                .aspectRatio(0.55f),
        )
        ParkingSlopeOverlay(
            guidance = parkingSlopeGuidance,
            uiScale = uiScale,
            modifier = Modifier
                .fillMaxHeight(0.92f)
                .aspectRatio(0.72f),
        )
    }
}

@Composable
private fun VehiclePowerHalo(
    powerState: VehiclePowerState,
    modifier: Modifier = Modifier,
) {
    val tint = when (powerState) {
        VehiclePowerState.OFF -> Muted
        VehiclePowerState.CAN_AWAKE -> Blue
        VehiclePowerState.IGNITION_ON -> Amber
        VehiclePowerState.ENGINE_RUNNING -> Green
    }
    val targetAlpha = when (powerState) {
        VehiclePowerState.OFF -> 0.08f
        VehiclePowerState.CAN_AWAKE -> 0.14f
        VehiclePowerState.IGNITION_ON -> 0.18f
        VehiclePowerState.ENGINE_RUNNING -> 0.22f
    }
    val alpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(420),
        label = "vehiclePowerHalo",
    )
    Canvas(modifier) {
        drawOval(
            brush = Brush.radialGradient(
                listOf(tint.copy(alpha = alpha), Color.Transparent),
                center = center,
                radius = size.maxDimension * 0.58f,
            ),
        )
    }
}

@Composable
private fun ParkingSlopeOverlay(
    guidance: ParkingSlopeGuidance,
    uiScale: Float,
    modifier: Modifier = Modifier,
) {
    if (!guidance.isAvailable) return
    val lateralTint = parkingSlopeTint(guidance.lateralLevel)
    val longitudinalTint = parkingSlopeTint(guidance.longitudinalLevel)
    Box(modifier = modifier.semantics {
        contentDescription =
            "Nachylenie wzdłuż ${formatOneDecimal(guidance.vehiclePitchDegrees.toDouble())} stopnia, " +
                "wszerz ${formatOneDecimal(guidance.vehicleRollDegrees.toDouble())} stopnia"
    }) {
        Canvas(Modifier.fillMaxSize()) {
            val topY = (size.height * 0.07f).roundToInt().toFloat()
            val rightX = (size.width * 0.91f).roundToInt().toFloat()
            val horizontalStart = (size.width * 0.12f).roundToInt().toFloat()
            val horizontalEnd = (size.width * 0.88f).roundToInt().toFloat()
            val verticalStart = (size.height * 0.14f).roundToInt().toFloat()
            val verticalEnd = (size.height * 0.88f).roundToInt().toFloat()
            val shadowWidth = (3.4.dp.toPx() * uiScale)
                .roundToInt().coerceAtLeast(1).toFloat()
            val lineWidth = (1.35.dp.toPx() * uiScale)
                .roundToInt().coerceAtLeast(1).toFloat()
            drawRect(
                color = Color.Black.copy(alpha = 0.50f),
                topLeft = Offset(horizontalStart, topY - shadowWidth / 2f),
                size = Size(horizontalEnd - horizontalStart, shadowWidth),
            )
            drawRect(
                color = lateralTint,
                topLeft = Offset(horizontalStart, topY - lineWidth / 2f),
                size = Size(horizontalEnd - horizontalStart, lineWidth),
            )
            drawRect(
                color = Color.Black.copy(alpha = 0.50f),
                topLeft = Offset(rightX - shadowWidth / 2f, verticalStart),
                size = Size(shadowWidth, verticalEnd - verticalStart),
            )
            drawRect(
                color = longitudinalTint,
                topLeft = Offset(rightX - lineWidth / 2f, verticalStart),
                size = Size(lineWidth, verticalEnd - verticalStart),
            )
        }
        Text(
            text = formatSignedDegrees(guidance.vehicleRollDegrees),
            color = Ink.copy(alpha = 0.66f),
            fontSize = (7.5f * uiScale).sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .graphicsLayer {
                    translationY = -8.dp.toPx() * uiScale
                }
                .background(CanvasBlack.copy(alpha = 0.78f), RoundedCornerShape(4.dp))
                .padding(horizontal = (3f * uiScale).dp),
        )
        Text(
            text = formatSignedDegrees(guidance.vehiclePitchDegrees),
            color = Ink.copy(alpha = 0.66f),
            fontSize = (7.5f * uiScale).sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .graphicsLayer {
                    translationX = 8.dp.toPx() * uiScale
                }
                .background(CanvasBlack.copy(alpha = 0.78f), RoundedCornerShape(4.dp))
                .padding(horizontal = (3f * uiScale).dp),
        )
    }
}

private fun formatSignedDegrees(value: Float): String {
    val rounded = value.roundToInt()
    return when {
        rounded > 0 -> "+$rounded°"
        else -> "$rounded°"
    }
}

private fun parkingSlopeTint(level: ParkingSlopeLevel): Color = when (level) {
    ParkingSlopeLevel.FLAT -> Green
    ParkingSlopeLevel.GEAR_RECOMMENDED -> Amber
    ParkingSlopeLevel.STEEP -> Red
    ParkingSlopeLevel.UNAVAILABLE -> Muted
}

@Composable
private fun SpeedValueReadout(
    speedKph: Int,
    uiScale: Float,
    modifier: Modifier = Modifier,
) {
    val displayedSpeed by animateIntAsState(
        targetValue = speedKph,
        animationSpec = tween(60),
        label = "forcedSpeed",
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier,
    ) {
        Text(
            text = displayedSpeed.toString(),
            style = TextStyle(
                brush = Brush.verticalGradient(
                    listOf(
                        Color.White,
                        Color(0xFFB9C3C7),
                    ),
                ),
                fontFamily = FontFamily.SansSerif,
                fontSize = (112f * uiScale).sp,
                lineHeight = (112f * uiScale).sp,
                fontWeight = FontWeight.Thin,
                letterSpacing = (-5f * uiScale).sp,
                fontFeatureSettings = "tnum",
            ),
        )
        Text(
            text = "km/h",
            color = Muted.copy(alpha = 0.52f),
            fontSize = (10.5f * uiScale).sp,
            lineHeight = (11f * uiScale).sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = (1.1f * uiScale).sp,
            modifier = Modifier.graphicsLayer {
                translationY = -(5f * uiScale).dp.toPx()
            },
        )
    }
}

@Composable
private fun DrivetrainReadout(
    state: VehicleState,
    uiScale: Float,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.padding(horizontal = (9f * uiScale).dp),
    ) {
        Text(
            text = state.engineRpm.toString(),
            color = Ink,
            fontSize = (48f * uiScale).sp,
            lineHeight = (50f * uiScale).sp,
            fontWeight = FontWeight.Light,
            style = TextStyle(fontFeatureSettings = "tnum"),
        )
        Text(
            text = "rpm",
            color = Muted.copy(alpha = 0.52f),
            fontSize = (8f * uiScale).sp,
            letterSpacing = (1f * uiScale).sp,
        )
        Spacer(Modifier.height((12f * uiScale).dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            DetailMetric(
                label = "GAZ",
                value = state.acceleratorPedalPercent
                    ?.let { "${it.roundToInt()}%" }
                    ?: "—",
                tint = Green,
                uiScale = uiScale,
            )
            DetailMetric(
                label = "MOMENT",
                value = state.requestedEngineTorqueNm
                    ?.let { "$it Nm" }
                    ?: "—",
                tint = Amber,
                uiScale = uiScale,
            )
            DetailMetric(
                label = "PRĘDKOŚĆ",
                value = state.speedKphPrecise
                    ?.let { formatTwoDecimals(it) }
                    ?: state.speedKph.toString(),
                tint = Ink,
                uiScale = uiScale,
            )
        }
        Spacer(Modifier.height((13f * uiScale).dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            InlineState(
                label = "SILNIK",
                active = state.isEngineRunning,
                uiScale = uiScale,
            )
            InlineState(
                label = "SPRZĘGŁO",
                active = state.isClutchPedalPressed,
                uiScale = uiScale,
            )
            InlineState(
                label = "HAMULEC",
                active = state.isBrakePedalPressed,
                uiScale = uiScale,
            )
            InlineState(
                label = "WSTECZNY",
                active = state.isReverseGearEngaged,
                uiScale = uiScale,
            )
        }
    }
}

@Composable
private fun ChassisReadout(
    state: VehicleState,
    uiScale: Float,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.padding(horizontal = (8f * uiScale).dp),
    ) {
        Text(
            text = state.steeringWheelAngleDegrees
                ?.let { "${formatOneDecimal(it.toDouble())}°" }
                ?: "—",
            color = steeringTint(state.steeringWheelAngleDegrees),
            fontSize = (36f * uiScale).sp,
            lineHeight = (38f * uiScale).sp,
            fontWeight = FontWeight.Light,
            style = TextStyle(fontFeatureSettings = "tnum"),
        )
        Text(
            text = "KĄT KIEROWNICY",
            color = Muted.copy(alpha = 0.52f),
            fontSize = (7f * uiScale).sp,
            letterSpacing = (1f * uiScale).sp,
        )
        Spacer(Modifier.height((13f * uiScale).dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            DetailMetric(
                "A PRAWE",
                state.wheelPairARightSpeedKph?.let { "${formatOneDecimal(it)} km/h" } ?: "—",
                Ink,
                uiScale,
            )
            DetailMetric(
                "A LEWE",
                state.wheelPairALeftSpeedKph?.let { "${formatOneDecimal(it)} km/h" } ?: "—",
                Ink,
                uiScale,
            )
            DetailMetric(
                "B PRAWE",
                state.wheelPairBRightSpeedKph?.let { "${formatOneDecimal(it)} km/h" } ?: "—",
                Ink,
                uiScale,
            )
            DetailMetric(
                "B LEWE",
                state.wheelPairBLeftSpeedKph?.let { "${formatOneDecimal(it)} km/h" } ?: "—",
                Ink,
                uiScale,
            )
        }
        Spacer(Modifier.height((14f * uiScale).dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            DetailMetric("WZDŁUŻNE", raw(state.longitudinalAccelerationRaw), Blue, uiScale)
            DetailMetric("POPRZECZNE", raw(state.lateralAccelerationRaw), Blue, uiScale)
            DetailMetric("YAW", raw(state.yawRateRaw), Blue, uiScale)
            InlineState("ESP OFF", state.isEspAsrDisabled, uiScale)
        }
    }
}

@Composable
private fun BodyReadout(
    state: VehicleState,
    uiScale: Float,
    modifier: Modifier = Modifier,
) {
    val openDoorCount = listOf(
        state.isFrontLeftDoorOpen,
        state.isFrontRightDoorOpen,
        state.isRearLeftDoorOpen,
        state.isRearRightDoorOpen,
    ).count { it }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.padding(horizontal = (7f * uiScale).dp),
    ) {
        VehicleStatusSymbol(
            state = state,
            modifier = Modifier
                .fillMaxHeight(0.80f)
                .aspectRatio(0.55f),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy((15f * uiScale).dp),
            modifier = Modifier.align(Alignment.CenterStart),
        ) {
            DetailMetric(
                "DRZWI",
                if (openDoorCount == 0) "zamknięte" else "otwarte $openDoorCount",
                if (openDoorCount == 0) Green else Amber,
                uiScale,
            )
            DetailMetric(
                "ZAMEK DRZWI",
                if (!state.isDoorLockSignalAvailable) "—" else if (state.areDoorsLocked) "zaryglowane" else "otwarte",
                if (state.areDoorsLocked) Green else Amber,
                uiScale,
            )
            DetailMetric(
                "KLAPA",
                when {
                    state.isTrunkOpen -> "otwarta"
                    !state.isTrunkLockSignalAvailable -> "—"
                    state.isTrunkLocked -> "zaryglowana"
                    else -> "odryglowana"
                },
                if (state.isTrunkOpen ||
                    (state.isTrunkLockSignalAvailable && !state.isTrunkLocked)
                ) Amber else Green,
                uiScale,
            )
        }
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy((15f * uiScale).dp),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            DetailMetric(
                "ZAPŁON",
                if (state.isIgnitionOn) "RUN" else "OFF",
                if (state.isIgnitionOn) Green else Muted,
                uiScale,
                alignEnd = true,
            )
            DetailMetric(
                "TYLNA SZYBA",
                if (state.isRearDefrostCommandActive) "IMPULS" else "—",
                if (state.isRearDefrostCommandActive) Blue else Muted,
                uiScale,
                alignEnd = true,
            )
            DetailMetric(
                "PAS",
                if (state.isDriverSeatBeltWarningActive) "niezapięty" else "OK",
                if (state.isDriverSeatBeltWarningActive) Amber else Green,
                uiScale,
                alignEnd = true,
            )
        }
    }
}

@Composable
private fun TripReadout(
    state: VehicleState,
    trip: TripLiveStats,
    uiScale: Float,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.padding(horizontal = (9f * uiScale).dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = formatDuration(trip.durationMs),
                    color = Ink,
                    fontSize = (27f * uiScale).sp,
                    lineHeight = (29f * uiScale).sp,
                    fontWeight = FontWeight.Light,
                    style = TextStyle(fontFeatureSettings = "tnum"),
                )
                Text("CZAS", color = Muted.copy(alpha = 0.48f), fontSize = (6f * uiScale).sp)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = formatTwoDecimals(trip.distanceKm),
                    color = Green,
                    fontSize = (27f * uiScale).sp,
                    lineHeight = (29f * uiScale).sp,
                    fontWeight = FontWeight.Light,
                )
                Text("KILOMETRY", color = Muted.copy(alpha = 0.48f), fontSize = (6f * uiScale).sp)
            }
        }
        Spacer(Modifier.height((7f * uiScale).dp))
        TripTimeStrip(trip = trip, uiScale = uiScale)
        Spacer(Modifier.height((10f * uiScale).dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            DetailMetric(
                "SPALANIE",
                trip.averageConsumptionLitersPer100Km?.let { "${formatOneDecimal(it)} l/100" } ?: "—",
                Amber,
                uiScale,
            )
            DetailMetric(
                "PALIWO",
                "${formatTwoDecimals(trip.fuelUsedLiters)} L",
                Amber,
                uiScale,
            )
            DetailMetric(
                "ŚR. W RUCHU",
                "${formatOneDecimal(trip.averageMovingSpeedKph)} km/h",
                Ink,
                uiScale,
            )
        }
        Spacer(Modifier.height((9f * uiScale).dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            DetailMetric(
                "OBROTY",
                "${trip.averageRpm.roundToInt()} / ${trip.maxRpm}",
                Blue,
                uiScale,
            )
            DetailMetric(
                "MAKS.",
                "${formatOneDecimal(trip.maxSpeedKph)} km/h",
                Ink,
                uiScale,
            )
            DetailMetric(
                "POSTÓJ",
                formatDurationCompact(trip.idleDurationMs),
                Muted,
                uiScale,
            )
        }
    }
}

@Composable
private fun FuelReadout(
    state: VehicleState,
    trip: TripLiveStats,
    uiScale: Float,
    modifier: Modifier = Modifier,
) {
    val estimatedLiters =
        state.fuelLevelEstimatedPercent?.let { it / 100.0 * FUEL_TANK_CAPACITY_LITERS }
    val estimatedRangeKm = if (estimatedLiters != null &&
        trip.averageConsumptionLitersPer100Km != null &&
        trip.averageConsumptionLitersPer100Km > 0.1
    ) {
        estimatedLiters / trip.averageConsumptionLitersPer100Km * 100.0
    } else null
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.padding(horizontal = (9f * uiScale).dp),
    ) {
        Text(
            text = state.fuelLevelEstimatedPercent?.let { "${it.roundToInt()}%" } ?: "—",
            color = Amber,
            fontSize = (38f * uiScale).sp,
            lineHeight = (40f * uiScale).sp,
            fontWeight = FontWeight.Light,
            style = TextStyle(fontFeatureSettings = "tnum"),
        )
        Text(
            text = estimatedLiters?.let { "${formatOneDecimal(it)} Z 60,0 L" }
                ?: "POZIOM NIEDOSTĘPNY",
            color = Muted.copy(alpha = 0.58f),
            fontSize = (7f * uiScale).sp,
            letterSpacing = (1f * uiScale).sp,
        )
        Spacer(Modifier.height((8f * uiScale).dp))
        ThinLevelBar(
            progress = state.fuelLevelEstimatedPercent?.div(100f) ?: 0f,
            tint = if ((state.fuelLevelEstimatedPercent ?: 0f) <= 15f) Red else Amber,
            modifier = Modifier.fillMaxWidth(0.82f),
        )
        Spacer(Modifier.height((11f * uiScale).dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            DetailMetric(
                "CHWILOWE / H",
                trip.instantConsumptionLitersPer100Km
                    ?.let { "${formatOneDecimal(it)} l/100" }
                    ?: trip.instantFuelLitersPerHour
                        ?.let { "${formatOneDecimal(it)} l/h" }
                    ?: "—",
                Amber,
                uiScale,
            )
            DetailMetric(
                "ZASIĘG",
                estimatedRangeKm?.let { "~${it.roundToInt()} km" } ?: "—",
                Green,
                uiScale,
            )
            DetailMetric(
                "ŚREDNIE",
                trip.averageConsumptionLitersPer100Km
                    ?.let { "${formatOneDecimal(it)} l/100" }
                    ?: "—",
                Ink,
                uiScale,
            )
        }
        Spacer(Modifier.height((8f * uiScale).dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            DetailMetric("ZUŻYTE", "${formatThreeDecimals(trip.fuelUsedLiters)} L", Ink, uiScale)
            DetailMetric(
                "OD STARTU",
                if (trip.startFuelPercent != null && trip.currentFuelPercent != null) {
                    "-${formatOneDecimal((trip.startFuelPercent - trip.currentFuelPercent).coerceAtLeast(0f).toDouble())}%"
                } else "—",
                Muted,
                uiScale,
            )
            DetailMetric("RAW", state.fuelLevelRaw?.toString() ?: "—", Muted, uiScale)
        }
    }
}

@Composable
private fun TripTimeStrip(trip: TripLiveStats, uiScale: Float) {
    val total = (trip.movingDurationMs + trip.idleDurationMs).coerceAtLeast(1L)
    val moving = trip.movingDurationMs.toFloat() / total
    Canvas(Modifier.fillMaxWidth(0.82f).height((5f * uiScale).dp)) {
        drawRoundRect(Color.White.copy(alpha = 0.08f), cornerRadius = CornerRadius(size.height))
        drawRoundRect(
            brush = Brush.horizontalGradient(listOf(Green.copy(alpha = 0.55f), Green)),
            size = Size(size.width * moving, size.height),
            cornerRadius = CornerRadius(size.height),
        )
    }
}

@Composable
private fun TripSummaryReadout(
    summary: TripSummary,
    uiScale: Float,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.padding(horizontal = (9f * uiScale).dp),
    ) {
        Text(
            text = "PODSUMOWANIE",
            color = Green.copy(alpha = 0.84f),
            fontSize = (8f * uiScale).sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (1.3f * uiScale).sp,
        )
        Spacer(Modifier.height((9f * uiScale).dp))
        Text(
            text = "${formatTwoDecimals(summary.distanceKm)} km",
            color = Ink,
            fontSize = (34f * uiScale).sp,
            fontWeight = FontWeight.Light,
        )
        Spacer(Modifier.height((12f * uiScale).dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            DetailMetric("CZAS", formatDuration(summary.durationMs), Ink, uiScale)
            DetailMetric(
                "SPALANIE",
                summary.averageConsumptionLitersPer100Km
                    ?.let { "${formatOneDecimal(it)} l/100" }
                    ?: "—",
                Amber,
                uiScale,
            )
            DetailMetric(
                "ŚREDNIA",
                "${formatOneDecimal(summary.averageSpeedKph)} km/h",
                Ink,
                uiScale,
            )
        }
        Spacer(Modifier.height((7f * uiScale).dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            DetailMetric("PALIWO", "${formatThreeDecimals(summary.fuelUsedLiters)} L", Amber, uiScale)
            DetailMetric("MAKS.", "${formatOneDecimal(summary.maxSpeedKph)} km/h", Ink, uiScale)
            DetailMetric("POSTÓJ", formatDurationCompact(summary.idleDurationMs), Muted, uiScale)
        }
    }
}

@Composable
private fun DiagnosticsReadout(
    state: VehicleState,
    gearGuidance: GearGuidance,
    rpmSuitability: RpmSuitabilityState,
    parkingSlopeGuidance: ParkingSlopeGuidance,
    signalState: SignalState,
    phoneOrientation: PhoneOrientation,
    connectionState: UsbConnectionState,
    trip: TripLiveStats,
    uiScale: Float,
    modifier: Modifier = Modifier,
) {
    val apiRows = remember(state) { diagnosticFields(state) }
    val gearRows = remember(gearGuidance) { diagnosticFields(gearGuidance) }
    val rpmRows = remember(rpmSuitability) { diagnosticFields(rpmSuitability) }
    val slopeRows = remember(parkingSlopeGuidance) { diagnosticFields(parkingSlopeGuidance) }
    val phoneRows = remember(phoneOrientation) { diagnosticFields(phoneOrientation) }
    val tripRows = remember(trip) { diagnosticFields(trip) }
    val signalLabels = remember {
        SignalDefinitions.specs.associate { it.key.id to it.key.label }
    }
    val rawRows = remember(signalState) {
        signalState.values.entries.sortedBy { it.key }.map { (key, value) ->
            val timestamp = signalState.timestampsMs[key]
            "${signalLabels[key] ?: "Nieznany sygnał"} [$key]" to
                "${polishSignalValue(value)}${timestamp?.let { "  •  $it ms" } ?: ""}"
        }
    }
    Column(
        verticalArrangement = Arrangement.spacedBy((8f * uiScale).dp),
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = (7f * uiScale).dp, vertical = (4f * uiScale).dp),
    ) {
        DiagnosticSection(
            title = "POŁĄCZENIE",
            rows = listOf("Stan USB" to polishUsbConnectionState(connectionState)),
            uiScale = uiScale,
        )
        DiagnosticSection("API POJAZDU", apiRows, uiScale)
        DiagnosticSection("BIEG I ZAKRES RPM", gearRows + rpmRows, uiScale)
        DiagnosticSection("NACHYLENIE", slopeRows + phoneRows, uiScale)
        DiagnosticSection("PODRÓŻ", tripRows, uiScale)
        DiagnosticSection("WSZYSTKIE SYGNAŁY CAN", rawRows, uiScale)
    }
}

@Composable
private fun DiagnosticSection(
    title: String,
    rows: List<Pair<String, String>>,
    uiScale: Float,
) {
    Text(
        text = title,
        color = Blue.copy(alpha = 0.84f),
        fontSize = (7.2f * uiScale).sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (0.8f * uiScale).sp,
    )
    rows.forEach { (name, value) ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = name,
                color = Muted.copy(alpha = 0.68f),
                fontSize = (6.7f * uiScale).sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = value,
                color = Ink.copy(alpha = 0.86f),
                fontSize = (6.7f * uiScale).sp,
                fontWeight = FontWeight.Medium,
                style = TextStyle(fontFeatureSettings = "tnum"),
                modifier = Modifier.weight(1.15f),
            )
        }
    }
}

private fun diagnosticFields(
    value: Any,
    parentLabel: String? = null,
): List<Pair<String, String>> = value.javaClass.declaredFields
    .filterNot { java.lang.reflect.Modifier.isStatic(it.modifiers) }
    .flatMap { field ->
        runCatching {
            field.isAccessible = true
            val fieldValue = field.get(value)
            val label = listOfNotNull(parentLabel, polishDiagnosticLabel(field.name))
                .joinToString(" — ")
            if (fieldValue != null &&
                fieldValue !is Number &&
                fieldValue !is Boolean &&
                fieldValue !is CharSequence &&
                fieldValue !is Enum<*> &&
                fieldValue !is IntRange &&
                fieldValue.javaClass.name.startsWith("pl.polsatgranie.smartmegane")
            ) {
                diagnosticFields(fieldValue, label)
            } else {
                listOf(label to polishDiagnosticValue(fieldValue))
            }
        }.getOrDefault(emptyList())
    }
    .sortedBy { it.first }

private fun polishUsbConnectionState(state: UsbConnectionState): String = when (state) {
    UsbConnectionState.Disconnected -> "rozłączono"
    UsbConnectionState.Searching -> "wyszukiwanie urządzenia"
    UsbConnectionState.NoDevice -> "brak urządzenia"
    is UsbConnectionState.PermissionRequired -> "oczekiwanie na zgodę: ${state.deviceName}"
    is UsbConnectionState.PermissionDenied -> "brak zgody: ${state.deviceName}"
    is UsbConnectionState.Connected -> "połączono: ${state.deviceName}"
    is UsbConnectionState.Error -> "błąd: ${state.message}"
}

private fun polishSignalValue(value: SignalValue): String = when (value) {
    is SignalValue.Bool -> if (value.value) "aktywny" else "nieaktywny"
    is SignalValue.Enum -> polishEnumValue(value.label)
    is SignalValue.Number -> {
        val number = if (value.value % 1.0 == 0.0) {
            value.value.toLong().toString()
        } else {
            String.format(Locale("pl", "PL"), "%.2f", value.value)
        }
        if (value.unit.isNullOrBlank()) number else "$number ${value.unit}"
    }
}

private fun polishDiagnosticValue(value: Any?): String = when (value) {
    null -> "brak"
    is Boolean -> if (value) "tak" else "nie"
    is Enum<*> -> polishEnumValue(value.name)
    is IntRange -> "${value.first}–${value.last}"
    is Float -> String.format(Locale("pl", "PL"), "%.2f", value)
    is Double -> String.format(Locale("pl", "PL"), "%.2f", value)
    else -> value.toString()
}

private fun polishEnumValue(value: String): String = when (value.uppercase(Locale.ROOT)) {
    "OFF" -> "wyłączony"
    "INTERMITTENT" -> "przerywany"
    "LOW" -> "wolny"
    "HIGH" -> "szybki"
    "CAN_AWAKE" -> "magistrala CAN aktywna"
    "IGNITION_ON" -> "zapłon włączony"
    "ENGINE_RUNNING" -> "silnik pracuje"
    "NONE" -> "brak"
    "UP" -> "zmiana w górę"
    "DOWN" -> "redukcja"
    "COUPLED" -> "sprzęgło połączone"
    "CLUTCH_DISENGAGED" -> "sprzęgło rozłączone"
    "SHIFTING" -> "zmiana biegu"
    "STATIONARY" -> "postój"
    "AMBIGUOUS" -> "niejednoznaczny"
    "SIGNAL_UNAVAILABLE", "UNAVAILABLE" -> "sygnał niedostępny"
    "REVERSE" -> "wsteczny"
    "TOO_LOW" -> "za nisko"
    "LOW_MARGIN" -> "dolna granica"
    "OPTIMAL" -> "optymalnie"
    "HIGH_MARGIN" -> "górna granica"
    "TOO_HIGH" -> "za wysoko"
    "FLAT" -> "płasko"
    "GEAR_RECOMMENDED" -> "zalecany bieg"
    "STEEP" -> "duże nachylenie"
    "FIRST" -> "pierwszy bieg"
    else -> value
}

private fun polishDiagnosticLabel(name: String): String {
    val exact = diagnosticLabelOverrides[name]
    if (exact != null) return exact
    val words = name
        .replace(Regex("([a-z0-9])([A-Z])"), "$1 $2")
        .split(' ')
        .filterNot { it.lowercase(Locale.ROOT) in setOf("is", "are", "has") }
        .map { diagnosticWordTranslations[it.lowercase(Locale.ROOT)] ?: it.uppercase(Locale.ROOT) }
    return words.joinToString(" ").replaceFirstChar { it.uppercase(Locale("pl", "PL")) }
}

private val diagnosticLabelOverrides = mapOf(
    "powerState" to "Stan samochodu",
    "lastCanFrameTimestampMs" to "Czas ostatniej ramki CAN",
    "speedKph" to "Prędkość",
    "speedKphPrecise" to "Prędkość dokładna",
    "engineRpm" to "Obroty silnika",
    "engineRpmPrecise" to "Obroty silnika dokładne",
    "fuelLevelPercent" to "Poziom paliwa",
    "fuelLevelEstimatedPercent" to "Szacowany poziom paliwa",
    "coolantTemperatureCelsius" to "Temperatura płynu chłodzącego",
    "outsideTemperatureCelsius" to "Temperatura zewnętrzna",
    "odometerKm" to "Przebieg",
    "wiperMode" to "Tryb wycieraczek",
    "vehiclePitchDegrees" to "Nachylenie przód–tył",
    "vehicleRollDegrees" to "Nachylenie lewo–prawo",
    "pitchDegrees" to "Surowy kąt X telefonu",
    "rollDegrees" to "Surowy kąt Y telefonu",
    "azimuthDegrees" to "Azymut telefonu",
    "preferredGear" to "Zalecany bieg",
    "shiftDirection" to "Zalecenie zmiany biegu",
    "forwardGear" to "Oszacowany aktualny bieg",
    "status" to "Stan oszacowania",
    "confidence" to "Pewność",
    "currentRpm" to "Aktualne obroty",
    "zone" to "Strefa obrotów",
)

private val diagnosticWordTranslations = mapOf(
    "active" to "aktywny", "available" to "dostępny", "average" to "średnia",
    "acceleration" to "przyspieszenie", "accelerator" to "gazu", "accessory" to "akcesoriów",
    "airbag" to "poduszki",
    "age" to "wiek", "angle" to "kąt", "angular" to "kątowa", "applied" to "zaciągnięty",
    "asr" to "ASR", "abs" to "ABS", "backlight" to "podświetlenie",
    "beam" to "światła", "belt" to "pasa", "body" to "nadwozia", "brake" to "hamulca",
    "braking" to "hamowań", "bus" to "magistrala", "can" to "CAN",
    "charging" to "ładowania", "command" to "polecenie", "computer" to "komputera",
    "clutch" to "sprzęgła", "cluster" to "zegarów", "coolant" to "płynu",
    "consumption" to "spalanie", "count" to "liczba", "counter" to "licznik",
    "current" to "aktualny", "data" to "dane", "defrost" to "ogrzewania szyby",
    "degrees" to "stopnie", "disabled" to "wyłączony", "distance" to "dystans", "driver" to "kierowcy",
    "door" to "drzwi", "doors" to "drzwi", "downhill" to "zjazdu",
    "duration" to "czas", "electronic" to "elektroniki", "engine" to "silnika",
    "end" to "końcowy", "ended" to "zakończenie", "engaged" to "włączony",
    "epoch" to "systemowy", "error" to "błąd", "estimate" to "oszacowanie",
    "estimated" to "szacowany", "esp" to "ESP", "fault" to "usterka",
    "fog" to "przeciwmgielne", "frame" to "ramki", "front" to "przednie", "fuel" to "paliwa",
    "gear" to "bieg", "glow" to "żarowe", "hard" to "gwałtownych",
    "hazard" to "awaryjne", "high" to "wysoki", "hour" to "godzinę",
    "idle" to "postoju",
    "ignition" to "zapłonu", "instrument" to "zegarów", "kinematics" to "kinematyki",
    "kph" to "km/h", "lateral" to "boczne",
    "left" to "lewe", "level" to "poziom", "lights" to "światła",
    "liters" to "litry", "lock" to "rygla", "locked" to "zaryglowany", "match" to "międzygaz",
    "longitudinal" to "wzdłużne", "low" to "niski", "max" to "maksymalny",
    "meters" to "metry", "min" to "minimalny", "minutes" to "minuty", "ms" to "ms",
    "moving" to "jazdy", "network" to "sieci", "nm" to "Nm",
    "odometer" to "przebiegu", "oil" to "oleju", "open" to "otwarte",
    "observed" to "zaobserwowany", "optimal" to "optymalny", "outside" to "zewnętrzna",
    "overheat" to "przegrzania", "pair" to "para", "parking" to "postojowego",
    "passenger" to "pasażera", "pedal" to "pedał", "percent" to "procent",
    "per" to "na", "plug" to "świece", "position" to "pozycyjne",
    "precise" to "dokładny", "pressure" to "ciśnienia", "pressed" to "wciśnięty",
    "range" to "zakres", "rate" to "tempo", "ratio" to "przełożenia", "raw" to "surowe",
    "rear" to "tylne", "relative" to "względny", "requested" to "żądany",
    "recommended" to "zalecany", "reverse" to "wsteczny", "rev" to "obrotów",
    "right" to "prawe", "rpm" to "RPM",
    "running" to "pracuje", "sample" to "próbki", "seat" to "fotela",
    "sensor" to "czujnika", "service" to "serwisowe", "signal" to "sygnał",
    "since" to "od", "speed" to "prędkość", "start" to "startu",
    "stable" to "stabilny", "started" to "rozpoczęcie", "state" to "stan", "status" to "status",
    "steering" to "kierownicy", "stop" to "STOP", "temperature" to "temperatura",
    "system" to "układu", "target" to "docelowy", "timestamp" to "czas",
    "torque" to "moment", "trip" to "podróży",
    "trunk" to "klapa", "turn" to "kierunkowskaz", "used" to "zużyte",
    "up" to "w górę", "uphill" to "podjazdu", "valid" to "ważne",
    "vehicle" to "pojazdu", "velocity" to "prędkość",
    "warning" to "ostrzeżenie", "wheel" to "koła", "window" to "szyba",
    "wiper" to "wycieraczki", "yaw" to "odchylenie", "zone" to "strefa",
)

@Composable
private fun TripHistoryReadout(
    history: List<TripSummary>,
    uiScale: Float,
    modifier: Modifier = Modifier,
) {
    var selectedTripId by remember { mutableStateOf<Long?>(null) }
    val selectedTrip = history.firstOrNull { it.id == selectedTripId }
    if (selectedTrip != null) {
        Box(modifier = modifier) {
            TripHistoryDetailReadout(
                summary = selectedTrip,
                uiScale = uiScale,
                modifier = Modifier.fillMaxSize(),
            )
            Text(
                text = "‹ HISTORIA",
                color = Blue.copy(alpha = 0.82f),
                fontSize = (7f * uiScale).sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .clickable { selectedTripId = null }
                    .padding((8f * uiScale).dp),
            )
        }
        return
    }
    if (history.isEmpty()) {
        Box(contentAlignment = Alignment.Center, modifier = modifier) {
            Text(
                text = "BRAK ZAPISANYCH PODRÓŻY",
                color = Muted.copy(alpha = 0.55f),
                fontSize = (8f * uiScale).sp,
                letterSpacing = (1f * uiScale).sp,
            )
        }
        return
    }
    Column(
        verticalArrangement = Arrangement.spacedBy((5f * uiScale).dp),
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(
                horizontal = (8f * uiScale).dp,
                vertical = (4f * uiScale).dp,
            ),
    ) {
        history.forEach { summary ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Color.White.copy(alpha = 0.035f),
                        RoundedCornerShape((10f * uiScale).dp),
                    )
                    .clickable { selectedTripId = summary.id }
                    .padding(
                        horizontal = (9f * uiScale).dp,
                        vertical = (6f * uiScale).dp,
                    ),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = formatTripDate(summary.startedAtEpochMs),
                        color = Ink.copy(alpha = 0.82f),
                        fontSize = (8f * uiScale).sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = formatDuration(summary.durationMs),
                        color = Muted.copy(alpha = 0.55f),
                        fontSize = (6.5f * uiScale).sp,
                    )
                }
                Text(
                    text = "${formatTwoDecimals(summary.distanceKm)} km",
                    color = Ink.copy(alpha = 0.78f),
                    fontSize = (10f * uiScale).sp,
                    style = TextStyle(fontFeatureSettings = "tnum"),
                )
                Spacer(Modifier.width((14f * uiScale).dp))
                Text(
                    text = summary.averageConsumptionLitersPer100Km
                        ?.let { "${formatOneDecimal(it)} l/100" }
                        ?: "—",
                    color = Amber.copy(alpha = 0.82f),
                    fontSize = (8f * uiScale).sp,
                )
            }
        }
    }
}

@Composable
private fun TripHistoryDetailReadout(
    summary: TripSummary,
    uiScale: Float,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy((9f * uiScale).dp),
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = (10f * uiScale).dp, vertical = (22f * uiScale).dp),
    ) {
        Text(
            text = formatTripDate(summary.startedAtEpochMs),
            color = Ink,
            fontSize = (13f * uiScale).sp,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = "KONIEC ${formatTripTime(summary.endedAtEpochMs)}  •  ${formatDuration(summary.durationMs)}",
            color = Muted.copy(alpha = 0.66f),
            fontSize = (7f * uiScale).sp,
            letterSpacing = (0.5f * uiScale).sp,
        )
        HistoryMetricRow(
            listOf(
                "DYSTANS" to "${formatTwoDecimals(summary.distanceKm)} km",
                "SPALANIE" to (summary.averageConsumptionLitersPer100Km?.let { "${formatOneDecimal(it)} l/100" } ?: "—"),
                "PALIWO" to "${formatThreeDecimals(summary.fuelUsedLiters)} L",
            ), uiScale,
        )
        HistoryMetricRow(
            listOf(
                "ŚREDNIA" to "${formatOneDecimal(summary.averageSpeedKph)} km/h",
                "W RUCHU" to "${formatOneDecimal(summary.averageMovingSpeedKph)} km/h",
                "MAKS." to "${formatOneDecimal(summary.maxSpeedKph)} km/h",
            ), uiScale,
        )
        HistoryMetricRow(
            listOf(
                "JAZDA" to formatDurationCompact(summary.movingDurationMs),
                "POSTÓJ" to formatDurationCompact(summary.idleDurationMs),
                "RPM ŚR./MAX" to "${summary.averageRpm.roundToInt()} / ${summary.maxRpm}",
            ), uiScale,
        )
        HistoryMetricRow(
            listOf(
                "PALIWO START" to (summary.startFuelPercent?.let { "${it.roundToInt()}%" } ?: "—"),
                "PALIWO KONIEC" to (summary.endFuelPercent?.let { "${it.roundToInt()}%" } ?: "—"),
                "GAZ ŚR./MAX" to if (summary.averageAcceleratorPercent != null && summary.maxAcceleratorPercent != null) {
                    "${summary.averageAcceleratorPercent.roundToInt()} / ${summary.maxAcceleratorPercent.roundToInt()}%"
                } else "—",
            ), uiScale,
        )
        HistoryMetricRow(
            listOf(
                "PŁYN MAX" to (summary.maxCoolantTemperatureCelsius?.let { "$it°C" } ?: "—"),
                "ZEWN. MIN/MAX" to if (summary.minOutsideTemperatureCelsius != null && summary.maxOutsideTemperatureCelsius != null) {
                    "${summary.minOutsideTemperatureCelsius} / ${summary.maxOutsideTemperatureCelsius}°C"
                } else "—",
                "MOCNE A/H" to "${summary.hardAccelerationCount} / ${summary.hardBrakingCount}",
            ), uiScale,
        )
        HistoryMetricRow(
            listOf(
                "PODJAZD" to (summary.maxUphillDegrees?.let { "${formatOneDecimal(it.toDouble())}°" } ?: "—"),
                "ZJAZD" to (summary.maxDownhillDegrees?.let { "${formatOneDecimal(abs(it).toDouble())}°" } ?: "—"),
                "PRZEBIEG" to if (summary.startOdometerKm != null && summary.endOdometerKm != null) {
                    "${formatOdometer(summary.startOdometerKm)} → ${formatOdometer(summary.endOdometerKm)}"
                } else "—",
            ), uiScale,
        )
    }
}

@Composable
private fun HistoryMetricRow(values: List<Pair<String, String>>, uiScale: Float) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        values.forEach { (label, value) ->
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                DetailMetric(label, value, if (label.contains("SPAL") || label == "PALIWO") Amber else Ink, uiScale)
            }
        }
    }
}

@Composable
private fun DetailMetric(
    label: String,
    value: String,
    tint: Color,
    uiScale: Float,
    alignEnd: Boolean = false,
) {
    Column(
        horizontalAlignment =
            if (alignEnd) Alignment.End else Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            color = Muted.copy(alpha = 0.46f),
            fontSize = (6.3f * uiScale).sp,
            lineHeight = (7f * uiScale).sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (0.65f * uiScale).sp,
            maxLines = 1,
        )
        Text(
            text = value,
            color = tint.copy(alpha = 0.88f),
            fontSize = (10.5f * uiScale).sp,
            lineHeight = (12f * uiScale).sp,
            fontWeight = FontWeight.Medium,
            style = TextStyle(fontFeatureSettings = "tnum"),
            maxLines = 1,
        )
    }
}

@Composable
private fun InlineState(
    label: String,
    active: Boolean,
    uiScale: Float,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy((3.5f * uiScale).dp),
    ) {
        Canvas(Modifier.size((4.5f * uiScale).dp)) {
            drawCircle(
                color = if (active) Green else Muted.copy(alpha = 0.24f),
            )
        }
        Text(
            text = label,
            color = Ink.copy(alpha = if (active) 0.76f else 0.30f),
            fontSize = (6.5f * uiScale).sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (0.45f * uiScale).sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun PrimaryReadout(
    state: VehicleState,
    lastTripSummary: TripSummary?,
    autoDisplayMode: AutoDisplayMode,
    parkingSlopeGuidance: ParkingSlopeGuidance,
    uiScale: Float,
    modifier: Modifier = Modifier,
) {
    val steeringSample = state.steeringWheelAngleDegrees?.let {
        (it / STEERING_DISPLAY_STEP_DEGREES).roundToInt() * STEERING_DISPLAY_STEP_DEGREES
    }
    val showParkingAxes = rememberSteeringActivity(steeringSample, forceVisible = false)
    var recentSummary by remember(lastTripSummary?.id) { mutableStateOf(false) }
    LaunchedEffect(lastTripSummary?.id) {
        val remainingMs = lastTripSummary?.let {
            15_000L - (System.currentTimeMillis() - it.endedAtEpochMs)
        } ?: 0L
        recentSummary = remainingMs > 0L
        if (remainingMs > 0L) {
            delay(remainingMs)
            recentSummary = false
        }
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier,
    ) {
        AnimatedContent(
            targetState = when {
                recentSummary && lastTripSummary != null -> 2
                autoDisplayMode == AutoDisplayMode.VEHICLE -> 1
                else -> 0
            },
            transitionSpec = {
                (fadeIn(tween(210)) + scaleIn(tween(230), initialScale = 0.97f))
                    .togetherWith(
                        fadeOut(tween(160)) + scaleOut(tween(170), targetScale = 1.02f),
                    )
            },
            contentAlignment = Alignment.Center,
            label = "primaryReadout",
        ) { mode ->
            when (mode) {
                2 -> TripSummaryReadout(
                    summary = lastTripSummary!!,
                    uiScale = uiScale,
                    modifier = Modifier.fillMaxSize(),
                )
                1 -> Box(contentAlignment = Alignment.Center) {
                    VehiclePowerHalo(
                        powerState = state.powerState,
                        modifier = Modifier
                            .fillMaxHeight(0.95f)
                            .aspectRatio(0.72f),
                    )
                    VehicleStatusSymbol(
                        state = state,
                        modifier = Modifier
                            .fillMaxHeight(0.91f)
                            .aspectRatio(0.55f),
                    )
                    if (showParkingAxes) {
                        ParkingSlopeOverlay(
                            guidance = parkingSlopeGuidance,
                            uiScale = uiScale,
                            modifier = Modifier
                                .fillMaxHeight(0.92f)
                                .aspectRatio(0.72f),
                        )
                    }
                }
                else -> SpeedValueReadout(
                    speedKph = state.speedKph,
                    uiScale = uiScale,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun rememberSteeringActivity(
    steeringSample: Float?,
    forceVisible: Boolean,
): Boolean {
    var previousSample by remember { mutableStateOf<Float?>(null) }
    var visible by remember { mutableStateOf(forceVisible && steeringSample != null) }
    LaunchedEffect(steeringSample, forceVisible) {
        if (steeringSample == null) {
            visible = false
            previousSample = null
            return@LaunchedEffect
        }
        val moved = previousSample?.let {
            abs(steeringSample - it) >= STEERING_DISPLAY_STEP_DEGREES
        } == true
        previousSample = steeringSample
        if (forceVisible) {
            visible = true
        } else if (moved) {
            visible = true
            delay(STEERING_VISIBILITY_TIMEOUT_MS)
            visible = false
        }
    }
    return visible
}

@Composable
private fun VehicleStatusSymbol(
    state: VehicleState,
    forceSteeringGuide: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val doorAnimation = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )
    val frontLeft by animateFloatAsState(
        if (state.isFrontLeftDoorOpen) 1f else 0f,
        doorAnimation,
        label = "frontLeftDoor",
    )
    val frontRight by animateFloatAsState(
        if (state.isFrontRightDoorOpen) 1f else 0f,
        doorAnimation,
        label = "frontRightDoor",
    )
    val rearLeft by animateFloatAsState(
        if (state.isRearLeftDoorOpen) 1f else 0f,
        doorAnimation,
        label = "rearLeftDoor",
    )
    val rearRight by animateFloatAsState(
        if (state.isRearRightDoorOpen) 1f else 0f,
        doorAnimation,
        label = "rearRightDoor",
    )
    val trunk by animateFloatAsState(
        if (state.isTrunkOpen) 1f else 0f,
        doorAnimation,
        label = "trunk",
    )
    val windowAnimation = tween<Float>(
        durationMillis = 360,
        easing = FastOutSlowInEasing,
    )
    val frontLeftWindow by animateFloatAsState(
        if (state.isFrontLeftWindowOpen) 1f else 0f,
        windowAnimation,
        label = "frontLeftWindow",
    )
    val frontRightWindow by animateFloatAsState(
        if (state.isFrontRightWindowOpen) 1f else 0f,
        windowAnimation,
        label = "frontRightWindow",
    )
    val rearLeftWindow by animateFloatAsState(
        if (state.isRearLeftWindowOpen) 1f else 0f,
        windowAnimation,
        label = "rearLeftWindow",
    )
    val rearRightWindow by animateFloatAsState(
        if (state.isRearRightWindowOpen) 1f else 0f,
        windowAnimation,
        label = "rearRightWindow",
    )

    val steeringSample = state.steeringWheelAngleDegrees
        ?.let {
            (it / STEERING_DISPLAY_STEP_DEGREES).roundToInt() *
                STEERING_DISPLAY_STEP_DEGREES
        }
    var displayedSteeringAngle by remember {
        mutableStateOf(steeringSample ?: 0f)
    }
    LaunchedEffect(steeringSample) {
        if (steeringSample != null) displayedSteeringAngle = steeringSample
    }
    val showSteeringGuide = rememberSteeringActivity(
        steeringSample = steeringSample,
        forceVisible = forceSteeringGuide,
    )
    val steeringGuideAlpha by animateFloatAsState(
        targetValue = if (showSteeringGuide) 1f else 0f,
        animationSpec = tween(300),
        label = "steeringGuideAlpha",
    )
    val roadWheelAngle by animateFloatAsState(
        targetValue = (
            displayedSteeringAngle /
                MAX_STEERING_WHEEL_ANGLE_DEGREES *
                MAX_ROAD_WHEEL_ANGLE_DEGREES
            ).coerceIn(
            -MAX_ROAD_WHEEL_ANGLE_DEGREES,
            MAX_ROAD_WHEEL_ANGLE_DEGREES,
        ),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = 700f,
        ),
        label = "roadWheelAngle",
    )

    Canvas(
        modifier = modifier.semantics {
            contentDescription = "Samochód oraz stan drzwi, szyb i bagażnika"
        },
    ) {
        val sx = size.width / 100f
        val sy = size.height / 184f
        val line = min(sx, sy)
        fun p(x: Float, y: Float) = Offset(x * sx, (y + 9f) * sy)

        drawOval(
            brush = Brush.radialGradient(
                listOf(Color.Black.copy(alpha = 0.44f), Color.Transparent),
            ),
            topLeft = p(8f, 149f),
            size = Size(84f * sx, 17f * sy),
        )

        if (steeringGuideAlpha > 0.001f) {
            val steeringStrength =
                (abs(roadWheelAngle) / MAX_ROAD_WHEEL_ANGLE_DEGREES)
                    .coerceIn(0f, 1f)
            val guideTint = steeringGuideTint(steeringStrength)
            val bend =
                roadWheelAngle / MAX_ROAD_WHEEL_ANGLE_DEGREES * 21f
            val guideEffect = PathEffect.dashPathEffect(
                floatArrayOf(4.8f * line, 3.2f * line),
            )
            listOf(15f, 85f).forEach { x ->
                val frontTrack = Path().apply {
                    moveTo(p(x, 34f).x, p(x, 34f).y)
                    cubicTo(
                        p(x + bend * 0.16f, 22f).x,
                        p(x + bend * 0.16f, 22f).y,
                        p(x + bend * 0.54f, 8f).x,
                        p(x + bend * 0.54f, 8f).y,
                        p(x + bend, -9f).x,
                        p(x + bend, -9f).y,
                    )
                }
                val rearTrack = Path().apply {
                    moveTo(p(x, 130f).x, p(x, 130f).y)
                    cubicTo(
                        p(x - bend * 0.14f, 142f).x,
                        p(x - bend * 0.14f, 142f).y,
                        p(x - bend * 0.50f, 157f).x,
                        p(x - bend * 0.50f, 157f).y,
                        p(x - bend * 0.88f, 174f).x,
                        p(x - bend * 0.88f, 174f).y,
                    )
                }
                drawPath(
                    frontTrack,
                    guideTint.copy(alpha = 0.24f * steeringGuideAlpha),
                    style = Stroke(
                        width = 6.2f * line,
                        cap = StrokeCap.Round,
                    ),
                )
                drawPath(
                    rearTrack,
                    guideTint.copy(alpha = 0.18f * steeringGuideAlpha),
                    style = Stroke(
                        width = 5.2f * line,
                        cap = StrokeCap.Round,
                    ),
                )
                drawPath(
                    frontTrack,
                    guideTint.copy(alpha = 0.92f * steeringGuideAlpha),
                    style = Stroke(
                        width = 2.15f * line,
                        cap = StrokeCap.Round,
                        pathEffect = guideEffect,
                    ),
                )
                drawPath(
                    rearTrack,
                    guideTint.copy(alpha = 0.72f * steeringGuideAlpha),
                    style = Stroke(
                        width = 1.8f * line,
                        cap = StrokeCap.Round,
                        pathEffect = guideEffect,
                    ),
                )
            }
        }

        val wheelStrength =
            (abs(roadWheelAngle) / MAX_ROAD_WHEEL_ANGLE_DEGREES)
                .coerceIn(0f, 1f)
        val wheelGuideTint = steeringGuideTint(wheelStrength)
        drawRoundRect(
            color = Color.Black.copy(alpha = 0.88f * (1f - steeringGuideAlpha)),
            topLeft = p(8f, 20f),
            size = Size(14f * sx, 28f * sy),
            cornerRadius = CornerRadius(5f * line),
        )
        if (steeringGuideAlpha > 0.001f) {
            rotate(roadWheelAngle, p(15f, 34f)) {
                drawRoundRect(
                    color = Color.Black.copy(alpha = 0.88f * steeringGuideAlpha),
                    topLeft = p(8f, 20f),
                    size = Size(14f * sx, 28f * sy),
                    cornerRadius = CornerRadius(5f * line),
                )
                drawRoundRect(
                    color = wheelGuideTint.copy(alpha = 0.26f * steeringGuideAlpha),
                    topLeft = p(7f, 19f),
                    size = Size(16f * sx, 30f * sy),
                    cornerRadius = CornerRadius(6f * line),
                    style = Stroke(4.6f * line),
                )
                drawRoundRect(
                    color = wheelGuideTint.copy(alpha = 0.96f * steeringGuideAlpha),
                    topLeft = p(8f, 20f),
                    size = Size(14f * sx, 28f * sy),
                    cornerRadius = CornerRadius(5f * line),
                    style = Stroke(2.1f * line),
                )
            }
        }
        drawRoundRect(
            color = Color.Black.copy(alpha = 0.88f * (1f - steeringGuideAlpha)),
            topLeft = p(78f, 20f),
            size = Size(14f * sx, 28f * sy),
            cornerRadius = CornerRadius(5f * line),
        )
        if (steeringGuideAlpha > 0.001f) {
            rotate(roadWheelAngle, p(85f, 34f)) {
                drawRoundRect(
                    color = Color.Black.copy(alpha = 0.88f * steeringGuideAlpha),
                    topLeft = p(78f, 20f),
                    size = Size(14f * sx, 28f * sy),
                    cornerRadius = CornerRadius(5f * line),
                )
                drawRoundRect(
                    color = wheelGuideTint.copy(alpha = 0.26f * steeringGuideAlpha),
                    topLeft = p(77f, 19f),
                    size = Size(16f * sx, 30f * sy),
                    cornerRadius = CornerRadius(6f * line),
                    style = Stroke(4.6f * line),
                )
                drawRoundRect(
                    color = wheelGuideTint.copy(alpha = 0.96f * steeringGuideAlpha),
                    topLeft = p(78f, 20f),
                    size = Size(14f * sx, 28f * sy),
                    cornerRadius = CornerRadius(5f * line),
                    style = Stroke(2.1f * line),
                )
            }
        }
        drawRoundRect(
            color = Color.Black.copy(alpha = 0.82f),
            topLeft = p(8f, 116f),
            size = Size(14f * sx, 27f * sy),
            cornerRadius = CornerRadius(5f * line),
        )
        drawRoundRect(
            color = Color.Black.copy(alpha = 0.82f),
            topLeft = p(78f, 116f),
            size = Size(14f * sx, 27f * sy),
            cornerRadius = CornerRadius(5f * line),
        )

        // Wide mirrors, long roof and the rear axle pushed towards the tail are
        // the strongest top-view cues of the Mégane II Grandtour.
        drawRoundRect(
            brush = Brush.horizontalGradient(
                listOf(Color(0xFF182127), Color(0xFF5C6870)),
            ),
            topLeft = p(5f, 48f),
            size = Size(17f * sx, 10f * sy),
            cornerRadius = CornerRadius(4f * line),
        )
        drawRoundRect(
            brush = Brush.horizontalGradient(
                listOf(Color(0xFF5C6870), Color(0xFF182127)),
            ),
            topLeft = p(78f, 48f),
            size = Size(17f * sx, 10f * sy),
            cornerRadius = CornerRadius(4f * line),
        )

        val body = Path().apply {
            moveTo(p(38f, 2f).x, p(38f, 2f).y)
            cubicTo(
                p(44f, 0f).x,
                p(44f, 0f).y,
                p(56f, 0f).x,
                p(56f, 0f).y,
                p(62f, 2f).x,
                p(62f, 2f).y,
            )
            lineTo(p(69f, 5f).x, p(69f, 5f).y)
            quadraticTo(
                p(76f, 9f).x,
                p(76f, 9f).y,
                p(78f, 18f).x,
                p(78f, 18f).y,
            )
            lineTo(p(82f, 29f).x, p(82f, 29f).y)
            quadraticTo(
                p(84f, 34f).x,
                p(84f, 34f).y,
                p(84f, 43f).x,
                p(84f, 43f).y,
            )
            lineTo(p(84f, 139f).x, p(84f, 139f).y)
            quadraticTo(
                p(84f, 151f).x,
                p(84f, 151f).y,
                p(77f, 158f).x,
                p(77f, 158f).y,
            )
            quadraticTo(
                p(70f, 166f).x,
                p(70f, 166f).y,
                p(61f, 167f).x,
                p(61f, 167f).y,
            )
            lineTo(p(39f, 167f).x, p(39f, 167f).y)
            quadraticTo(
                p(30f, 166f).x,
                p(30f, 166f).y,
                p(23f, 158f).x,
                p(23f, 158f).y,
            )
            quadraticTo(
                p(16f, 151f).x,
                p(16f, 151f).y,
                p(16f, 139f).x,
                p(16f, 139f).y,
            )
            lineTo(p(16f, 43f).x, p(16f, 43f).y)
            quadraticTo(
                p(16f, 34f).x,
                p(16f, 34f).y,
                p(18f, 29f).x,
                p(18f, 29f).y,
            )
            lineTo(p(22f, 18f).x, p(22f, 18f).y)
            quadraticTo(
                p(24f, 9f).x,
                p(24f, 9f).y,
                p(31f, 5f).x,
                p(31f, 5f).y,
            )
            close()
        }
        drawPath(
            path = body,
            brush = Brush.verticalGradient(
                listOf(
                    Color(0xFF68757E),
                    Color(0xFF3D4A52),
                    Color(0xFF263139),
                    Color(0xFF182127),
                ),
            ),
        )
        drawPath(
            body,
            Color(0xFFD9E3E7).copy(alpha = 0.44f),
            style = Stroke(1.45f * line, join = StrokeJoin.Round),
        )
        drawPath(
            body,
            Color(0xFF10171B).copy(alpha = 0.72f),
            style = Stroke(3.8f * line, join = StrokeJoin.Round),
        )
        drawPath(
            body,
            Color.White.copy(alpha = 0.36f),
            style = Stroke(1.05f * line, join = StrokeJoin.Round),
        )

        val hood = Path().apply {
            moveTo(p(39f, 4.5f).x, p(39f, 4.5f).y)
            quadraticTo(
                p(50f, 2.3f).x,
                p(50f, 2.3f).y,
                p(61f, 4.5f).x,
                p(61f, 4.5f).y,
            )
            cubicTo(
                p(66f, 11f).x,
                p(66f, 11f).y,
                p(69f, 21f).x,
                p(69f, 21f).y,
                p(70f, 28.5f).x,
                p(70f, 28.5f).y,
            )
            quadraticTo(
                p(50f, 31f).x,
                p(50f, 31f).y,
                p(30f, 28.5f).x,
                p(30f, 28.5f).y,
            )
            cubicTo(
                p(31f, 21f).x,
                p(31f, 21f).y,
                p(34f, 11f).x,
                p(34f, 11f).y,
                p(39f, 4.5f).x,
                p(39f, 4.5f).y,
            )
            close()
        }
        drawPath(
            hood,
            Brush.verticalGradient(
                listOf(
                    Color(0xFF59656C).copy(alpha = 0.38f),
                    Color(0xFF303C43).copy(alpha = 0.22f),
                ),
            ),
        )
        drawPath(
            hood,
            Color.White.copy(alpha = 0.10f),
            style = Stroke(0.8f * line, join = StrokeJoin.Round),
        )

        val windshield = Path().apply {
            moveTo(p(29f, 33f).x, p(29f, 33f).y)
            quadraticTo(
                p(31f, 29f).x,
                p(31f, 29f).y,
                p(36f, 28f).x,
                p(36f, 28f).y,
            )
            quadraticTo(
                p(50f, 25.5f).x,
                p(50f, 25.5f).y,
                p(64f, 28f).x,
                p(64f, 28f).y,
            )
            quadraticTo(
                p(69f, 29f).x,
                p(69f, 29f).y,
                p(71f, 33f).x,
                p(71f, 33f).y,
            )
            lineTo(p(66f, 58f).x, p(66f, 58f).y)
            quadraticTo(
                p(65f, 61f).x,
                p(65f, 61f).y,
                p(61f, 62f).x,
                p(61f, 62f).y,
            )
            lineTo(p(39f, 62f).x, p(39f, 62f).y)
            quadraticTo(
                p(35f, 61f).x,
                p(35f, 61f).y,
                p(34f, 58f).x,
                p(34f, 58f).y,
            )
            close()
        }
        drawPath(
            windshield,
            Brush.verticalGradient(
                listOf(
                    Color(0xFF38505A).copy(alpha = 0.88f),
                    Color(0xFF17252C).copy(alpha = 0.98f),
                ),
            ),
        )
        drawPath(
            windshield,
            Color.White.copy(alpha = 0.15f),
            style = Stroke(0.9f * line, join = StrokeJoin.Round),
        )
        drawLine(
            color = Color(0xFF080D10).copy(alpha = 0.30f),
            start = p(34f, 32.5f),
            end = p(66f, 32.5f),
            strokeWidth = 0.75f * line,
            cap = StrokeCap.Round,
        )

        drawRoundRect(
            brush = Brush.verticalGradient(
                listOf(
                    Color(0xFF18242A),
                    Color(0xFF2E414A),
                    Color(0xFF1A252B),
                ),
            ),
            topLeft = p(27f, 64f),
            size = Size(46f * sx, 74f * sy),
            cornerRadius = CornerRadius(4.5f * line),
        )
        drawRoundRect(
            color = Color.White.copy(alpha = 0.10f),
            topLeft = p(27f, 64f),
            size = Size(46f * sx, 74f * sy),
            cornerRadius = CornerRadius(4.5f * line),
            style = Stroke(1f * line),
        )

        // Raised roof rails continue almost to the square tailgate.
        listOf(24.5f, 75.5f).forEach { railX ->
            drawLine(
                color = Color.Black.copy(alpha = 0.72f),
                start = p(railX, 57f),
                end = p(railX, 146f),
                strokeWidth = 4.7f * line,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = Color(0xFFB8C2C7).copy(alpha = 0.52f),
                start = p(railX - 0.45f, 58f),
                end = p(railX - 0.45f, 145f),
                strokeWidth = 1.15f * line,
                cap = StrokeCap.Round,
            )
        }

        val rearGlass = Path().apply {
            moveTo(p(27f, 140f).x, p(27f, 140f).y)
            lineTo(p(73f, 140f).x, p(73f, 140f).y)
            lineTo(p(78f, 156f).x, p(78f, 156f).y)
            quadraticTo(
                p(50f, 161f).x,
                p(50f, 161f).y,
                p(22f, 156f).x,
                p(22f, 156f).y,
            )
            close()
        }
        drawPath(
            rearGlass,
            Brush.verticalGradient(
                listOf(
                    Color(0xFF23343C).copy(alpha = 0.96f * (1f - trunk)),
                    Color(0xFF71909D).copy(alpha = 0.68f * (1f - trunk)),
                ),
            ),
        )
        drawPath(
            rearGlass,
            Color.White.copy(alpha = 0.18f * (1f - trunk)),
            style = Stroke(1f * line, join = StrokeJoin.Round),
        )

        // Closed door seams keep the vehicle readable even with every opening shut.
        listOf(64f, 101f, 141f).forEach { y ->
            drawLine(
                color = Color.Black.copy(alpha = 0.34f),
                start = p(17f, y),
                end = p(28f, y + if (y == 64f) 2f else 0f),
                strokeWidth = 1.2f * line,
            )
            drawLine(
                color = Color.Black.copy(alpha = 0.34f),
                start = p(83f, y),
                end = p(72f, y + if (y == 64f) 2f else 0f),
                strokeWidth = 1.2f * line,
            )
        }
        // In top view the headlamps remain slim, subdued edge details.
        val leftHeadlamp = Path().apply {
            moveTo(p(24.5f, 15.5f).x, p(24.5f, 15.5f).y)
            cubicTo(
                p(28f, 11.5f).x,
                p(28f, 11.5f).y,
                p(34f, 8.5f).x,
                p(34f, 8.5f).y,
                p(39f, 8f).x,
                p(39f, 8f).y,
            )
            quadraticTo(
                p(38.5f, 11f).x,
                p(38.5f, 11f).y,
                p(36.5f, 13f).x,
                p(36.5f, 13f).y,
            )
            quadraticTo(
                p(31f, 14f).x,
                p(31f, 14f).y,
                p(26f, 18.5f).x,
                p(26f, 18.5f).y,
            )
            close()
        }
        val rightHeadlamp = Path().apply {
            moveTo(p(75.5f, 15.5f).x, p(75.5f, 15.5f).y)
            cubicTo(
                p(72f, 11.5f).x,
                p(72f, 11.5f).y,
                p(66f, 8.5f).x,
                p(66f, 8.5f).y,
                p(61f, 8f).x,
                p(61f, 8f).y,
            )
            quadraticTo(
                p(61.5f, 11f).x,
                p(61.5f, 11f).y,
                p(63.5f, 13f).x,
                p(63.5f, 13f).y,
            )
            quadraticTo(
                p(69f, 14f).x,
                p(69f, 14f).y,
                p(74f, 18.5f).x,
                p(74f, 18.5f).y,
            )
            close()
        }
        listOf(leftHeadlamp, rightHeadlamp).forEach { lamp ->
            drawPath(
                path = lamp,
                color = Color(0xFFAAB8BD).copy(alpha = 0.46f),
            )
            drawPath(
                lamp,
                Color.White.copy(alpha = 0.24f),
                style = Stroke(0.65f * line, join = StrokeJoin.Round),
            )
        }
        drawLine(
            color = Color.White.copy(alpha = 0.14f),
            start = p(38f, 4f),
            end = p(62f, 4f),
            strokeWidth = 0.85f * line,
            cap = StrokeCap.Round,
        )

        drawLine(
            color = Color(0xFF0A0D0F).copy(alpha = 0.88f),
            start = p(28f, 163f),
            end = p(72f, 163f),
            strokeWidth = 3.1f * line,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = Red.copy(alpha = 0.82f),
            start = p(22f, 151f),
            end = p(28f, 162f),
            strokeWidth = 3.4f * line,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = Red.copy(alpha = 0.82f),
            start = p(78f, 151f),
            end = p(72f, 162f),
            strokeWidth = 3.4f * line,
            cap = StrokeCap.Round,
        )

        fun drawDoor(
            left: Boolean,
            front: Boolean,
            openness: Float,
            windowOpenness: Float,
        ) {
            val xOuter = if (left) 17f else 83f
            val xInner = if (left) 29f else 71f
            val top = if (front) 62f else 101f
            val bottom = if (front) 99f else 142f
            val pivot = p(xOuter, top)
            val degrees = (if (left) 31f else -31f) * openness
            if (openness > 0.01f) {
                val cavityX = if (left) 15f else 77f
                drawRoundRect(
                    Color.Black.copy(alpha = 0.74f * openness),
                    topLeft = p(cavityX, top + 2f),
                    size = Size(8f * sx, (bottom - top - 3f) * sy),
                    cornerRadius = CornerRadius(2f * line),
                )
            }
            rotate(degrees, pivot) {
                val panel = Path().apply {
                    moveTo(p(xOuter, top).x, p(xOuter, top).y)
                    lineTo(p(xInner, top + 2f).x, p(xInner, top + 2f).y)
                    lineTo(p(xInner, bottom - 2f).x, p(xInner, bottom - 2f).y)
                    lineTo(p(xOuter, bottom).x, p(xOuter, bottom).y)
                    close()
                }
                if (openness > 0.01f) {
                    drawPath(
                        panel,
                        Brush.horizontalGradient(
                            listOf(Color(0xFF273035), Color(0xFF69747A)),
                        ),
                    )
                    drawPath(
                        panel,
                        Amber.copy(alpha = 0.42f + openness * 0.36f),
                        style = Stroke(1.2f * line),
                    )
                }
                val glassTop =
                    top + 4f + (bottom - top - 8f) * 0.90f * windowOpenness
                if (glassTop < bottom - 4f) {
                    drawLine(
                        Color(0xFF72BBDD).copy(
                            alpha = 0.56f * (1f - windowOpenness * 0.72f),
                        ),
                        p(xInner, glassTop),
                        p(xInner, bottom - 5f),
                        1.55f * line,
                        StrokeCap.Round,
                    )
                }
            }
        }

        drawDoor(true, true, frontLeft, frontLeftWindow)
        drawDoor(false, true, frontRight, frontRightWindow)
        drawDoor(true, false, rearLeft, rearLeftWindow)
        drawDoor(false, false, rearRight, rearRightWindow)

        if (trunk > 0.01f) {
            drawRoundRect(
                color = Color.Black.copy(alpha = 0.72f * trunk),
                topLeft = p(22f, 148f),
                size = Size(56f * sx, 15f * sy),
                cornerRadius = CornerRadius(3f * line),
            )
            translate(0f, 12f * sy * trunk) {
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        listOf(
                            Color(0xFF64727A).copy(alpha = trunk),
                            Color(0xFF202A30).copy(alpha = trunk),
                        ),
                    ),
                    topLeft = p(22f, 146f),
                    size = Size(56f * sx, 17f * sy),
                    cornerRadius = CornerRadius(3f * line),
                )
                drawRoundRect(
                    color = Amber.copy(alpha = 0.72f * trunk),
                    topLeft = p(22f, 146f),
                    size = Size(56f * sx, 17f * sy),
                    cornerRadius = CornerRadius(3f * line),
                    style = Stroke(1.35f * line),
                )
                drawRoundRect(
                    color = Color(0xFF7393A0).copy(alpha = 0.72f * trunk),
                    topLeft = p(27f, 149f),
                    size = Size(46f * sx, 8f * sy),
                    cornerRadius = CornerRadius(2f * line),
                )
            }
        }
    }
}

@Composable
private fun WarningTray(
    state: VehicleState,
    uiScale: Float,
) {
    val activeIndicators = VehicleIndicator.entries
        .filter {
            it.severity != IndicatorSeverity.INFORMATION ||
                it == VehicleIndicator.WIPERS ||
                it == VehicleIndicator.UNLOCKED ||
                it == VehicleIndicator.HAZARD ||
                it == VehicleIndicator.REAR_DEFROST
        }
        .sortedBy { it.severity.ordinal }
        .filter(state::isActive)
    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = Modifier
            .fillMaxWidth()
            .height((74f * uiScale).dp),
    ) {
        AnimatedVisibility(
            visible = activeIndicators.isNotEmpty(),
            enter = fadeIn(tween(180)) + scaleIn(tween(200), initialScale = 0.96f),
            exit = fadeOut(tween(140)) + scaleOut(tween(150), targetScale = 0.97f),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy((3f * uiScale).dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Color.White.copy(alpha = 0.035f),
                        RoundedCornerShape((16f * uiScale).dp),
                    )
                    .padding(
                        horizontal = (9f * uiScale).dp,
                        vertical = (5f * uiScale).dp,
                    ),
            ) {
                activeIndicators.chunked(8).forEach { rowIndicators ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(
                            (7f * uiScale).dp,
                            Alignment.CenterHorizontally,
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        rowIndicators.forEach { indicator ->
                            WarningIcon(
                                indicator = indicator,
                                wiperMode = state.wiperMode,
                                uiScale = uiScale,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WarningIcon(
    indicator: VehicleIndicator,
    wiperMode: WiperMode,
    uiScale: Float,
) {
    val tint = when {
        indicator == VehicleIndicator.WIPERS ||
            indicator == VehicleIndicator.UNLOCKED ||
            indicator == VehicleIndicator.REAR_DEFROST -> Blue
        indicator.severity == IndicatorSeverity.CRITICAL -> Red
        else -> Amber
    }
    val cellWidth = if (indicator == VehicleIndicator.WIPERS) 34f else 25f
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .width((cellWidth * uiScale).dp)
            .height((25f * uiScale).dp)
            .drawBehind {
                drawCircle(
                    Brush.radialGradient(
                        listOf(
                            tint.copy(
                                alpha = if (indicator == VehicleIndicator.WIPERS) {
                                    0.27f
                                } else {
                                    0.18f
                                },
                            ),
                            Color.Transparent,
                        ),
                    ),
                    radius = if (indicator == VehicleIndicator.WIPERS) {
                        size.minDimension * 0.90f
                    } else {
                        size.minDimension * 0.75f
                    },
                )
            },
    ) {
        if (indicator == VehicleIndicator.WIPERS) {
            WiperStatusSymbol(
                mode = wiperMode,
                tint = tint,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            IndicatorSymbol(
                indicator = indicator,
                tint = tint,
                modifier = Modifier.size((21f * uiScale).dp),
            )
        }
    }
}

@Composable
private fun WiperStatusSymbol(
    mode: WiperMode,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "wiperSweep")
    val sweep by transition.animateFloat(
        initialValue = -5.5f,
        targetValue = 5.5f,
        animationSpec = when (mode) {
            WiperMode.INTERMITTENT -> infiniteRepeatable(
                animation = keyframes {
                    durationMillis = 2_400
                    -5.5f at 0
                    6.5f at 190 using FastOutSlowInEasing
                    -5.5f at 420 using FastOutSlowInEasing
                    -5.5f at 2_400
                },
            )

            WiperMode.LOW -> infiniteRepeatable(
                animation = tween(550, easing = FastOutSlowInEasing),
                repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
            )

            WiperMode.HIGH -> infiniteRepeatable(
                animation = tween(275, easing = FastOutSlowInEasing),
                repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
            )

            WiperMode.OFF -> infiniteRepeatable(animation = tween(1_000))
        },
        label = "wiperModeSweep",
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .background(
                color = tint.copy(alpha = 0.075f),
                shape = RoundedCornerShape(percent = 50),
            )
            .semantics {
                contentDescription = when (mode) {
                    WiperMode.INTERMITTENT -> "Wycieraczki: tryb przerywany"
                    WiperMode.LOW -> "Wycieraczki: bieg wolny"
                    WiperMode.HIGH -> "Wycieraczki: bieg szybki"
                    WiperMode.OFF -> "Wycieraczki wyłączone"
                }
            },
    ) {
        IndicatorSymbol(
            indicator = VehicleIndicator.WIPERS,
            tint = tint,
            modifier = Modifier
                .size(21.dp)
                .align(Alignment.CenterStart)
                .padding(start = 1.dp)
                .graphicsLayer {
                    rotationZ = if (mode == WiperMode.OFF) 0f else sweep
                },
        )
        Canvas(
            Modifier
                .width(10.dp)
                .height(19.dp)
                .align(Alignment.CenterEnd)
                .padding(end = 1.dp),
        ) {
            val centerX = size.width / 2f
            val centerY = size.height / 2f
            val stroke = size.width * 0.20f
            when (mode) {
                WiperMode.INTERMITTENT -> {
                    // A small clock makes intermittent operation readable even
                    // when the one-shot sweep happens between two glances.
                    drawCircle(
                        color = tint,
                        radius = size.width * 0.31f,
                        center = Offset(centerX, centerY),
                        style = Stroke(width = stroke),
                    )
                    drawLine(
                        color = tint,
                        start = Offset(centerX, centerY),
                        end = Offset(centerX, centerY - size.height * 0.16f),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        color = tint,
                        start = Offset(centerX, centerY),
                        end = Offset(centerX + size.width * 0.18f, centerY),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round,
                    )
                }

                WiperMode.LOW -> {
                    drawRoundRect(
                        color = tint,
                        topLeft = Offset(centerX - stroke / 2f, size.height * 0.21f),
                        size = Size(stroke, size.height * 0.58f),
                        cornerRadius = CornerRadius(stroke, stroke),
                    )
                }

                WiperMode.HIGH -> {
                    repeat(2) { index ->
                        val x = size.width * (0.31f + index * 0.38f)
                        drawRoundRect(
                            color = tint,
                            topLeft = Offset(x - stroke / 2f, size.height * 0.21f),
                            size = Size(stroke, size.height * 0.58f),
                            cornerRadius = CornerRadius(stroke, stroke),
                        )
                    }
                }

                WiperMode.OFF -> Unit
            }
        }
    }
}

@Composable
private fun BottomTelemetry(
    state: VehicleState,
    connectionState: UsbConnectionState,
    onReconnect: () -> Unit,
    showPreciseValues: Boolean,
    uiScale: Float,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height((48f * uiScale).dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = state.outsideTemperatureCelsius
                    ?.let { "$it°C" }
                    ?: "—",
                color = Ink.copy(alpha = 0.38f),
                fontSize = (9.5f * uiScale).sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = (0.35f * uiScale).sp,
                style = TextStyle(fontFeatureSettings = "tnum"),
            )
            Text(
                text = state.odometerKm
                    .takeIf { state.isOdometerSignalAvailable }
                    ?.let { "${formatOdometer(it)} km" }
                    ?: "—",
                color = Ink.copy(alpha = 0.38f),
                fontSize = (9.5f * uiScale).sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = (0.35f * uiScale).sp,
                style = TextStyle(fontFeatureSettings = "tnum"),
            )
        }
        Spacer(Modifier.height((6f * uiScale).dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TelemetryLevelBar(
                progress = state.fuelLevelPercent / 100f,
                tint = if (state.fuelLevelPercent <= 15) Red else Amber,
                label = if (showPreciseValues) {
                    state.fuelLevelEstimatedPercent?.let {
                        "${(it / 100f * FUEL_TANK_CAPACITY_LITERS).roundToInt()} / 60 L"
                    }
                } else null,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width((7f * uiScale).dp))
            IndicatorSymbol(
                VehicleIndicator.LOW_FUEL,
                Amber.copy(alpha = 0.90f),
                Modifier.size((17f * uiScale).dp),
            )
            Spacer(Modifier.width((20f * uiScale).dp))
            UsbStatusDot(
                state = connectionState,
                onReconnect = onReconnect,
                uiScale = uiScale,
            )
            Spacer(Modifier.width((20f * uiScale).dp))
            IndicatorSymbol(
                VehicleIndicator.COOLANT,
                Blue.copy(alpha = 0.92f),
                Modifier.size((17f * uiScale).dp),
            )
            Spacer(Modifier.width((7f * uiScale).dp))
            TelemetryLevelBar(
                progress = ((state.coolantTemperatureCelsius - 40f) / 80f)
                    .coerceIn(0f, 1f),
                tint = if (state.coolantTemperatureCelsius >= 105) Red else Blue,
                label = if (showPreciseValues && state.isCoolantTemperatureSignalAvailable) {
                    "${state.coolantTemperatureCelsius}°C"
                } else null,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun TelemetryLevelBar(
    progress: Float,
    tint: Color,
    label: String?,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.height(15.dp),
    ) {
        ThinLevelBar(
            progress = progress,
            tint = tint,
            modifier = Modifier.fillMaxWidth(),
        )
        if (label != null) {
            Text(
                text = label,
                color = Ink.copy(alpha = 0.72f),
                fontSize = 7.sp,
                lineHeight = 8.sp,
                fontWeight = FontWeight.Medium,
                style = TextStyle(fontFeatureSettings = "tnum"),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .background(CanvasBlack.copy(alpha = 0.72f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 3.dp),
            )
        }
    }
}

@Composable
private fun ThinLevelBar(
    progress: Float,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(520),
        label = "level",
    )
    Canvas(
        modifier = modifier
            .height(7.dp)
            .semantics {
                contentDescription = "Poziom ${(animatedProgress * 100).toInt()} procent"
            },
    ) {
        val trackHeight = 4.dp.toPx()
        val y = (size.height - trackHeight) / 2f
        drawRoundRect(
            color = Color.White.copy(alpha = 0.10f),
            topLeft = Offset(0f, y),
            size = Size(size.width, trackHeight),
            cornerRadius = CornerRadius(trackHeight / 2f),
        )
        val fillWidth = size.width * animatedProgress
        if (fillWidth > 0f) {
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    listOf(tint.copy(alpha = 0.60f), tint),
                ),
                topLeft = Offset(0f, y),
                size = Size(fillWidth, trackHeight),
                cornerRadius = CornerRadius(trackHeight / 2f),
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.35f),
                radius = 1.4.dp.toPx(),
                center = Offset(fillWidth.coerceAtLeast(1.4.dp.toPx()), size.height / 2f),
            )
        }
    }
}

@Composable
private fun UsbStatusDot(
    state: UsbConnectionState,
    onReconnect: () -> Unit,
    uiScale: Float,
) {
    val connected = state is UsbConnectionState.Connected
    val searching = state is UsbConnectionState.Searching
    val transition = rememberInfiniteTransition(label = "usbPulse")
    val pulse by transition.animateFloat(
        initialValue = 0.30f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(760),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "usbSearching",
    )
    val tint = when {
        connected -> Green
        searching -> Amber.copy(alpha = pulse)
        else -> Muted.copy(alpha = 0.42f)
    }
    Box(
        modifier = Modifier
            .size((11f * uiScale).dp)
            .drawBehind {
                if (connected || searching) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(tint.copy(alpha = 0.32f), Color.Transparent),
                        ),
                        radius = size.minDimension * 1.45f,
                    )
                }
            }
            .background(tint, CircleShape)
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = if (connected) 0.28f else 0.10f),
                shape = CircleShape,
            )
            .clickable(enabled = !connected, onClick = onReconnect)
            .semantics {
                contentDescription = if (connected) {
                    "USB połączone"
                } else {
                    "USB rozłączone, dotknij aby połączyć"
                }
            },
    )
}

@Composable
private fun GearRecommendationReadout(
    guidance: GearGuidance,
    uiScale: Float,
    modifier: Modifier = Modifier,
) {
    var lastConfirmedGear by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(guidance.displayGear) {
        guidance.displayGear?.let { lastConfirmedGear = it }
    }
    val displayedGear = guidance.displayGear ?: lastConfirmedGear ?: "—"
    val displayedDirection = guidance.shiftDirection
    val arrowPresence by animateFloatAsState(
        targetValue =
            if (guidance.shiftDirection == ShiftDirection.NONE) 0f else 1f,
        animationSpec = if (guidance.shiftDirection == ShiftDirection.NONE) {
            tween(100)
        } else {
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium,
            )
        },
        label = "gearArrowPresence",
    )
    AnimatedVisibility(
        visible = displayedGear != "—",
        enter = fadeIn(tween(150)) + scaleIn(tween(170), initialScale = 0.92f),
        exit = fadeOut(tween(120)) + scaleOut(tween(120), targetScale = 0.96f),
        modifier = modifier,
    ) {
        Box(
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier
                .fillMaxWidth()
                .height((58f * uiScale).dp)
                .drawBehind {
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                CanvasBlack.copy(alpha = 0.86f),
                                CanvasBlack.copy(alpha = 0.52f),
                                Color.Transparent,
                            ),
                            endY = size.height,
                        ),
                    )
                },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(top = (7f * uiScale).dp),
            ) {
                AnimatedContent(
                    targetState = displayedGear,
                    contentAlignment = Alignment.Center,
                    transitionSpec = {
                        val increasing =
                            (targetState.toIntOrNull() ?: 0) >
                                (initialState.toIntOrNull() ?: 0)
                        (
                            fadeIn(tween(140)) +
                                slideInVertically(tween(160)) {
                                    if (increasing) it / 4 else -it / 4
                                }
                            ).togetherWith(
                            fadeOut(tween(110)) +
                                slideOutVertically(tween(140)) {
                                    if (increasing) -it / 4 else it / 4
                                },
                        )
                    },
                    label = "preferredGear",
                    modifier = Modifier.width((18f * uiScale).dp),
                ) { gear ->
                    Text(
                        text = gear,
                        color = Ink,
                        fontSize = (24f * uiScale).sp,
                        lineHeight = (26f * uiScale).sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.SansSerif,
                        style = TextStyle(fontFeatureSettings = "tnum"),
                    )
                }
                Spacer(Modifier.width((1.5f * uiScale).dp))
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .width((9f * uiScale).dp)
                        .height((18f * uiScale).dp),
                ) {
                    val directionTint = when (displayedDirection) {
                        ShiftDirection.UP -> Color(0xFF55D6A3)
                        ShiftDirection.DOWN -> Color(0xFFFFB85A)
                        ShiftDirection.NONE -> Color.Transparent
                    }
                    Canvas(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                alpha = arrowPresence
                                scaleX = 0.72f + arrowPresence * 0.28f
                                scaleY = 0.72f + arrowPresence * 0.28f
                            },
                    ) {
                        val pointsUp =
                            displayedDirection == ShiftDirection.UP
                        val shaftStartY =
                            if (pointsUp) size.height * 0.84f else size.height * 0.16f
                        val shaftEndY =
                            if (pointsUp) size.height * 0.34f else size.height * 0.66f
                        drawLine(
                            color = directionTint,
                            start = Offset(size.width / 2f, shaftStartY),
                            end = Offset(size.width / 2f, shaftEndY),
                            strokeWidth = 1.55.dp.toPx() * uiScale,
                            cap = StrokeCap.Round,
                        )
                        val arrowHead = Path().apply {
                            if (pointsUp) {
                                moveTo(size.width / 2f, size.height * 0.10f)
                                lineTo(size.width * 0.10f, size.height * 0.42f)
                                lineTo(size.width * 0.90f, size.height * 0.42f)
                            } else {
                                moveTo(size.width / 2f, size.height * 0.90f)
                                lineTo(size.width * 0.10f, size.height * 0.58f)
                                lineTo(size.width * 0.90f, size.height * 0.58f)
                            }
                            close()
                        }
                        drawPath(arrowHead, directionTint)
                    }
                }
            }
        }
    }
}

@Composable
private fun RpmSuitabilityRail(
    state: RpmSuitabilityState,
    gearGuidance: GearGuidance,
    showRpmValue: Boolean,
    uiScale: Float,
    modifier: Modifier = Modifier,
) {
    val animatedRpm by animateFloatAsState(
        targetValue = state.currentRpm.toFloat(),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = 700f,
        ),
        label = "rpmSuitabilityTape",
    )
    val safeFirst by animateIntAsState(state.optimalRange.first, tween(180), label = "rpmSafeFirst")
    val safeLast by animateIntAsState(state.optimalRange.last, tween(180), label = "rpmSafeLast")
    val warningFirst by animateIntAsState(state.warningRange.first, tween(180), label = "rpmWarnFirst")
    val warningLast by animateIntAsState(state.warningRange.last, tween(180), label = "rpmWarnLast")
    Box(
        modifier = modifier.semantics {
            contentDescription =
                "Zakres obrotów dla biegu ${state.estimatedCurrentGear ?: 0}: ${state.zone}"
        },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val trackRight = size.width - 5.dp.toPx()
            val centerY = size.height / 2f
            val pixelsPerRpm = size.height / 2_550f
            fun yFor(rpm: Int) = centerY + (animatedRpm - rpm) * pixelsPerRpm
            fun drawBand(range: IntRange, color: Color) {
                val top = yFor(range.last).coerceAtLeast(0f)
                val bottom = yFor(range.first).coerceAtMost(size.height)
                if (bottom > top) {
                    drawRect(
                        brush = Brush.horizontalGradient(
                            listOf(color.copy(alpha = 0.72f), color, color.copy(alpha = 0.70f)),
                            endX = trackRight,
                        ),
                        topLeft = Offset(0f, top),
                        size = Size(trackRight, bottom - top),
                    )
                }
            }
            clipRect(0f, 0f, trackRight, size.height) {
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(Color(0xFF8E303B), Color(0xFFA63C47), Color(0xFF812A35)),
                    ),
                    size = Size(trackRight, size.height),
                )
                drawBand(warningFirst..warningLast, Color(0xFFB99B59))
                drawBand(safeFirst..safeLast, Color(0xFF3F936D))
                drawRect(
                    brush = Brush.horizontalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.32f),
                            Color.Transparent,
                            Color.White.copy(alpha = 0.07f),
                            Color.Black.copy(alpha = 0.22f),
                        ),
                    ),
                    size = Size(trackRight, size.height),
                )
                val first = floor((animatedRpm - 1_450f) / 100f).toInt() * 100
                val last = ceil((animatedRpm + 1_450f) / 100f).toInt() * 100
                for (rpm in first..last step 100) {
                    val y = yFor(rpm)
                    if (y in 0f..size.height) {
                        val major = rpm % 500 == 0
                        val inset = if (major) trackRight * 0.18f else trackRight * 0.31f
                        drawLine(
                            color = Color.Black.copy(alpha = if (major) 0.42f else 0.22f),
                            start = Offset(inset, y),
                            end = Offset(trackRight - inset, y),
                            strokeWidth = if (major) 1.5.dp.toPx() else 0.7.dp.toPx(),
                            cap = StrokeCap.Round,
                        )
                    }
                }
            }
            drawLine(
                color = Color.Black.copy(alpha = 0.65f),
                start = Offset(-2.dp.toPx(), centerY),
                end = Offset(trackRight + 2.dp.toPx(), centerY),
                strokeWidth = 8.dp.toPx(),
                cap = StrokeCap.Square,
            )
            drawLine(
                color = Color.White.copy(alpha = 0.94f),
                start = Offset(-1.dp.toPx(), centerY),
                end = Offset(trackRight + 1.dp.toPx(), centerY),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Square,
            )
            drawLine(
                color = Color.White.copy(alpha = 0.10f),
                start = Offset(size.width - 1.dp.toPx(), 0f),
                end = Offset(size.width - 1.dp.toPx(), size.height),
                strokeWidth = 1.dp.toPx(),
            )
        }
        GearRecommendationReadout(
            guidance = gearGuidance,
            uiScale = uiScale,
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
        )
        if (showRpmValue) {
            Text(
                text = state.currentRpm.toString(),
                color = Ink.copy(alpha = 0.86f),
                fontSize = (7.5f * uiScale).sp,
                fontWeight = FontWeight.Medium,
                style = TextStyle(fontFeatureSettings = "tnum"),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = (4f * uiScale).dp)
                    .background(CanvasBlack.copy(alpha = 0.70f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 3.dp),
            )
        }
    }
}

@Composable
private fun AntiStallRail(
    guidance: AntiStallGuidance,
    gearGuidance: GearGuidance,
    engineRpm: Int,
    uiScale: Float,
    modifier: Modifier = Modifier,
) {
    val position by animateFloatAsState(
        targetValue = ((guidance.normalizedReserve + 1f) / 2f).coerceIn(0f, 1f),
        animationSpec = tween(260, easing = FastOutSlowInEasing),
        label = "antiStallReserve",
    )
    val statusTint = when (guidance.status) {
        AntiStallStatus.STALL_RISK -> Red
        AntiStallStatus.ADD_THROTTLE -> Amber
        AntiStallStatus.RELEASE_SMOOTHLY -> Green
        AntiStallStatus.ENGINE_RESERVE -> when {
            guidance.normalizedReserve < -0.28f -> Red
            guidance.normalizedReserve < 0f -> Amber
            else -> Green
        }
        else -> Muted
    }
    val isAssistActive = guidance.status != AntiStallStatus.ENGINE_OFF &&
        guidance.status != AntiStallStatus.INACTIVE
    val compactPrompt = when (guidance.status) {
        AntiStallStatus.STALL_RISK -> "RYZYKO ZGAŚNIĘCIA"
        AntiStallStatus.ADD_THROTTLE -> "DODAJ GAZU"
        AntiStallStatus.RELEASE_SMOOTHLY -> "PUŚĆ PŁYNNIE"
        AntiStallStatus.ENGINE_RESERVE -> "ANTI-STALL"
        AntiStallStatus.ENGINE_OFF -> ""
        AntiStallStatus.CLUTCH_RELEASED -> "SPRZĘGŁO PUSZCZONE"
        AntiStallStatus.INACTIVE -> ""
    }
    Box(
        modifier = modifier
            .background(Color(0xFF0D1215))
            .semantics {
                contentDescription =
                    "Asystent anti-stall: ${guidance.prompt}, zapas ${guidance.rpmReserve} obrotów"
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val right = size.width - 4.dp.toPx()
            val top = 62.dp.toPx() * uiScale
            val bottom = size.height
            val height = (bottom - top).coerceAtLeast(1f)
            if (isAssistActive) {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Green.copy(alpha = 0.68f),
                            Green.copy(alpha = 0.48f),
                            Amber.copy(alpha = 0.50f),
                            Red.copy(alpha = 0.66f),
                        ),
                        startY = top,
                        endY = bottom,
                    ),
                    topLeft = Offset(0f, top),
                    size = Size(right, height),
                )
            } else {
                drawRect(
                    color = Muted.copy(alpha = 0.08f),
                    topLeft = Offset(0f, top),
                    size = Size(right, height),
                )
            }
            if (isAssistActive) {
                var tickY = top + 9.dp.toPx()
                while (tickY < bottom) {
                    drawLine(
                        color = Color.Black.copy(alpha = 0.30f),
                        start = Offset(right * 0.28f, tickY),
                        end = Offset(right * 0.74f, tickY),
                        strokeWidth = 0.8.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                    tickY += 10.dp.toPx()
                }
            }
            drawRect(
                brush = Brush.horizontalGradient(
                    listOf(
                        Color.Black.copy(alpha = 0.34f),
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.28f),
                    ),
                ),
                topLeft = Offset(0f, top),
                size = Size(right, height),
            )
            if (isAssistActive) {
                val y = bottom - position * height
                drawLine(
                    color = Color.Black.copy(alpha = 0.72f),
                    start = Offset(0f, y),
                    end = Offset(right, y),
                    strokeWidth = 8.dp.toPx(),
                )
                drawLine(
                    color = statusTint.copy(alpha = 0.98f),
                    start = Offset(0f, y),
                    end = Offset(right, y),
                    strokeWidth = 2.3.dp.toPx(),
                )
            }
            drawLine(
                color = Color.White.copy(alpha = 0.10f),
                start = Offset(right, 0f),
                end = Offset(right, size.height),
                strokeWidth = 1.dp.toPx(),
            )
        }
        GearRecommendationReadout(
            guidance = gearGuidance,
            uiScale = uiScale,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(),
        )
        if (compactPrompt.isNotBlank()) {
            Text(
                text = compactPrompt,
                color = statusTint.copy(alpha = 0.88f),
                fontSize = (6.2f * uiScale).sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (0.7f * uiScale).sp,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.Center)
                    .graphicsLayer { rotationZ = -90f },
            )
        }
        Text(
            text = engineRpm.toString(),
            color = Ink.copy(alpha = 0.72f),
            fontSize = (7.5f * uiScale).sp,
            fontWeight = FontWeight.Medium,
            style = TextStyle(fontFeatureSettings = "tnum"),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = (4f * uiScale).dp)
                .background(CanvasBlack.copy(alpha = 0.62f), RoundedCornerShape(4.dp))
                .padding(horizontal = 3.dp),
        )
    }
}

@Composable
private fun RpmRail(
    state: VehicleState,
    gearGuidance: GearGuidance,
    uiScale: Float,
    modifier: Modifier = Modifier,
) {
    val progress by animateFloatAsState(
        targetValue = (state.engineRpm / 4_500f).coerceIn(0f, 1f),
        animationSpec = tween(150),
        label = "rpmRail",
    )
    val tint = when {
        state.engineRpm >= 4_000 -> Red
        state.engineRpm >= 3_200 -> Amber
        state.engineRpm >= 1_200 -> Green
        else -> Blue
    }
    Box(
        modifier = modifier
            .background(Color(0xFF0D1215))
            .semantics { contentDescription = "Obroty silnika ${state.engineRpm}" },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val right = size.width - 4.dp.toPx()
            val top = 62.dp.toPx() * uiScale
            val available = (size.height - top).coerceAtLeast(1f)
            val fillTop = size.height - available * progress
            drawRect(
                color = Color.White.copy(alpha = 0.035f),
                topLeft = Offset(0f, top),
                size = Size(right, available),
            )
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(tint.copy(alpha = 0.92f), tint.copy(alpha = 0.34f)),
                    startY = fillTop,
                    endY = size.height,
                ),
                topLeft = Offset(0f, fillTop),
                size = Size(right, size.height - fillTop),
            )
            for (rpm in 0..4_500 step 500) {
                val y = size.height - available * (rpm / 4_500f)
                drawLine(
                    color = Ink.copy(alpha = if (rpm % 1_000 == 0) 0.28f else 0.13f),
                    start = Offset(right * 0.32f, y),
                    end = Offset(right * 0.72f, y),
                    strokeWidth = if (rpm % 1_000 == 0) 1.3.dp.toPx() else 0.7.dp.toPx(),
                )
            }
        }
        GearRecommendationReadout(
            guidance = gearGuidance,
            uiScale = uiScale,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(),
        )
        Text(
            text = state.engineRpm.toString(),
            color = Ink.copy(alpha = 0.84f),
            fontSize = (8f * uiScale).sp,
            fontWeight = FontWeight.Medium,
            style = TextStyle(fontFeatureSettings = "tnum"),
            modifier = Modifier
                .align(Alignment.Center)
                .graphicsLayer { rotationZ = -90f },
        )
    }
}

@Composable
private fun EdgeGlow(
    state: VehicleState,
    leftTurnPulse: Float,
    rightTurnPulse: Float,
) {
    Canvas(Modifier.fillMaxSize()) {
        val alertTint = when {
            state.hasCriticalWarning() -> Red
            state.hasNonCriticalWarning() -> Amber
            else -> Color.Transparent
        }
        val edgeDepth = size.width * 0.17f
        val verticalDepth = size.height * 0.16f
        if (alertTint != Color.Transparent) {
            val alpha = if (state.hasCriticalWarning()) 0.17f else 0.12f
            drawRect(
                Brush.horizontalGradient(
                    listOf(alertTint.copy(alpha = alpha), Color.Transparent),
                    endX = edgeDepth,
                ),
                size = Size(edgeDepth, size.height),
            )
            drawRect(
                Brush.horizontalGradient(
                    listOf(Color.Transparent, alertTint.copy(alpha = alpha)),
                    startX = size.width - edgeDepth,
                    endX = size.width,
                ),
                topLeft = Offset(size.width - edgeDepth, 0f),
                size = Size(edgeDepth, size.height),
            )
            drawRect(
                Brush.verticalGradient(
                    listOf(alertTint.copy(alpha = alpha), Color.Transparent),
                    endY = verticalDepth,
                ),
                size = Size(size.width, verticalDepth),
            )
            drawRect(
                Brush.verticalGradient(
                    listOf(Color.Transparent, alertTint.copy(alpha = alpha)),
                    startY = size.height - verticalDepth,
                    endY = size.height,
                ),
                topLeft = Offset(0f, size.height - verticalDepth),
                size = Size(size.width, verticalDepth),
            )
        }
        val turnDepth = size.width * 0.36f
        val turnTint = if (state.areHazardLightsOn) Amber else Green
        if (state.isLeftTurnSignalOn) {
            drawRect(
                brush = Brush.horizontalGradient(
                    listOf(
                        turnTint.copy(alpha = 0.31f * leftTurnPulse),
                        turnTint.copy(alpha = 0.10f * leftTurnPulse),
                        Color.Transparent,
                    ),
                    endX = turnDepth,
                ),
                size = Size(turnDepth, size.height),
            )
        }
        if (state.isRightTurnSignalOn) {
            drawRect(
                brush = Brush.horizontalGradient(
                    listOf(
                        Color.Transparent,
                        turnTint.copy(alpha = 0.10f * rightTurnPulse),
                        turnTint.copy(alpha = 0.31f * rightTurnPulse),
                    ),
                    startX = size.width - turnDepth,
                    endX = size.width,
                ),
                topLeft = Offset(size.width - turnDepth, 0f),
                size = Size(turnDepth, size.height),
            )
        }
    }
}

@Composable
private fun TurnSignalEdgeStroke(
    state: VehicleState,
    leftTurnPulse: Float,
    rightTurnPulse: Float,
) {
    Canvas(Modifier.fillMaxSize()) {
        val turnTint = if (state.areHazardLightsOn) Amber else Green
        if (state.isLeftTurnSignalOn) {
            drawLine(
                color = turnTint.copy(alpha = 0.78f * leftTurnPulse),
                start = Offset(1.dp.toPx(), 0f),
                end = Offset(1.dp.toPx(), size.height),
                strokeWidth = 2.dp.toPx(),
            )
        }
        if (state.isRightTurnSignalOn) {
            drawLine(
                color = turnTint.copy(alpha = 0.78f * rightTurnPulse),
                start = Offset(size.width - 1.dp.toPx(), 0f),
                end = Offset(size.width - 1.dp.toPx(), size.height),
                strokeWidth = 2.dp.toPx(),
            )
        }
    }
}

private fun steeringGuideTint(strength: Float): Color {
    val bounded = strength.coerceIn(0f, 1f)
    return when {
        bounded <= 0.12f -> Color(0xFF45D8A0)
        bounded <= 0.70f -> lerp(
            Color(0xFF45D8A0),
            Color(0xFFF5C357),
            (bounded - 0.12f) / 0.58f,
        )

        else -> lerp(
            Color(0xFFF5C357),
            Color(0xFFFF5E6E),
            (bounded - 0.70f) / 0.30f,
        )
    }
}

private fun steeringTint(angleDegrees: Float?): Color =
    angleDegrees
        ?.let {
            steeringGuideTint(
                abs(it) / MAX_STEERING_WHEEL_ANGLE_DEGREES,
            )
        }
        ?: Muted

private fun raw(value: Int?): String = value?.toString() ?: "—"

private fun formatOneDecimal(value: Double): String =
    String.format(Locale("pl", "PL"), "%.1f", value)

private fun formatTwoDecimals(value: Double): String =
    String.format(Locale("pl", "PL"), "%.2f", value)

private fun formatThreeDecimals(value: Double): String =
    String.format(Locale("pl", "PL"), "%.3f", value)

private fun formatFourDecimals(value: Double): String =
    String.format(Locale("pl", "PL"), "%.4f", value)

private fun formatDuration(durationMs: Long): String {
    val totalMinutes = durationMs.coerceAtLeast(0L) / 60_000L
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return if (hours > 0L) {
        String.format(Locale("pl", "PL"), "%d:%02d h", hours, minutes)
    } else {
        "$minutes min"
    }
}

private fun formatDurationCompact(durationMs: Long): String {
    val totalSeconds = durationMs.coerceAtLeast(0L) / 1_000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return String.format(Locale("pl", "PL"), "%d:%02d", minutes, seconds)
}

private fun formatTripDate(epochMs: Long): String =
    SimpleDateFormat("dd.MM.yyyy  HH:mm", Locale("pl", "PL"))
        .format(Date(epochMs))

private fun formatTripTime(epochMs: Long): String =
    SimpleDateFormat("HH:mm", Locale("pl", "PL")).format(Date(epochMs))

private fun formatOdometer(value: Long): String =
    NumberFormat.getIntegerInstance(Locale("pl", "PL")).format(value)
