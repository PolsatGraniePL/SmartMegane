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
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import pl.polsatgranie.smartmegane.data.serial.UsbConnectionState
import pl.polsatgranie.smartmegane.domain.vehicle.IndicatorSeverity
import pl.polsatgranie.smartmegane.domain.vehicle.GearGuidance
import pl.polsatgranie.smartmegane.domain.vehicle.ShiftDirection
import pl.polsatgranie.smartmegane.domain.vehicle.SweetSpotCalculator
import pl.polsatgranie.smartmegane.domain.vehicle.SweetSpotState
import pl.polsatgranie.smartmegane.domain.vehicle.VehicleIndicator
import pl.polsatgranie.smartmegane.domain.vehicle.VehicleState
import pl.polsatgranie.smartmegane.domain.vehicle.WiperMode
import pl.polsatgranie.smartmegane.domain.vehicle.hasCriticalWarning
import pl.polsatgranie.smartmegane.domain.vehicle.hasNonCriticalWarning
import pl.polsatgranie.smartmegane.domain.vehicle.isActive
import java.text.NumberFormat
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
private const val STEERING_DISPLAY_STEP_DEGREES = 2.5f
private const val MAX_STEERING_WHEEL_ANGLE_DEGREES = 576f
private const val MAX_ROAD_WHEEL_ANGLE_DEGREES = 32f

@Composable
fun CockpitScreen(
    vehicleState: VehicleState,
    gearGuidance: GearGuidance,
    connectionState: UsbConnectionState,
    onReconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "dashboardPulse")
    val turnPulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 660
                0f at 0
                1f at 85 using FastOutSlowInEasing
                1f at 360
                0f at 500 using FastOutSlowInEasing
                0f at 660
            },
        ),
        label = "turnSignal",
    )
    val sweetSpot = remember(
        vehicleState.speedKphPrecise,
        vehicleState.speedKph,
        vehicleState.engineRpmPrecise,
        vehicleState.engineRpm,
        vehicleState.isClutchPedalPressed,
        gearGuidance.revMatchGear,
        gearGuidance.revMatchConfidence,
    ) {
        SweetSpotCalculator.calculate(
            speedKph = vehicleState.speedKphPrecise ?: vehicleState.speedKph.toDouble(),
            engineRpm = vehicleState.engineRpmPrecise ?: vehicleState.engineRpm.toDouble(),
            isClutchPressed = vehicleState.isClutchPedalPressed,
            preferredGear = gearGuidance.revMatchGear,
            guidanceConfidence = gearGuidance.revMatchConfidence,
        )
    }

    BoxWithConstraints(
        modifier = modifier.background(CanvasBlack),
    ) {
        val uiScale = min(maxWidth.value / 390f, maxHeight.value / 360f)
            .coerceIn(0.82f, 1.18f)
        DashboardBackdrop()
        EdgeGlow(
            state = vehicleState,
            turnPulse = turnPulse,
        )
        SweetSpotRail(
            state = sweetSpot,
            gearGuidance = gearGuidance,
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
                pulse = turnPulse,
                uiScale = uiScale,
            )
            PrimaryReadout(
                state = vehicleState,
                uiScale = uiScale,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
            WarningTray(
                state = vehicleState,
                uiScale = uiScale,
            )
            BottomTelemetry(
                state = vehicleState,
                connectionState = connectionState,
                onReconnect = onReconnect,
                uiScale = uiScale,
            )
        }
        TurnSignalEdgeStroke(
            state = vehicleState,
            turnPulse = turnPulse,
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
    pulse: Float,
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
            activeTint = Green,
            pulse = pulse,
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
            activeTint = Green,
            pulse = pulse,
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
        indicator == VehicleIndicator.RIGHT_TURN
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
private fun PrimaryReadout(
    state: VehicleState,
    uiScale: Float,
    modifier: Modifier = Modifier,
) {
    var showVehicle by remember { mutableStateOf(state.speedKph == 0) }
    LaunchedEffect(state.speedKph) {
        if (state.speedKph == 0) {
            delay(420)
            if (state.speedKph == 0) showVehicle = true
        } else {
            showVehicle = false
        }
    }
    val displayedSpeed by animateIntAsState(
        targetValue = state.speedKph,
        animationSpec = tween(135),
        label = "speed",
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier,
    ) {
        AnimatedContent(
            targetState = showVehicle,
            transitionSpec = {
                (fadeIn(tween(210)) + scaleIn(tween(230), initialScale = 0.97f))
                    .togetherWith(
                        fadeOut(tween(160)) + scaleOut(tween(170), targetScale = 1.02f),
                    )
            },
            contentAlignment = Alignment.Center,
            label = "primaryReadout",
        ) { stationary ->
            if (stationary) {
                VehicleStatusSymbol(
                    state = state,
                    modifier = Modifier
                        .fillMaxHeight(0.91f)
                        .aspectRatio(0.55f),
                )
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
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
        }
    }
}

@Composable
private fun VehicleStatusSymbol(
    state: VehicleState,
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
    var showSteeringGuide by remember {
        mutableStateOf(steeringSample != null)
    }
    LaunchedEffect(steeringSample) {
        if (steeringSample == null) {
            showSteeringGuide = false
        } else {
            displayedSteeringAngle = steeringSample
            showSteeringGuide = true
            delay(STEERING_VISIBILITY_TIMEOUT_MS)
            showSteeringGuide = false
        }
    }
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
            stiffness = Spring.StiffnessMedium,
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
                it == VehicleIndicator.WIPERS
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
        indicator == VehicleIndicator.WIPERS -> Blue
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
    uiScale: Float,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height((48f * uiScale).dp),
    ) {
        Text(
            text = "${formatOdometer(state.odometerKm)} km",
            color = Ink.copy(alpha = 0.38f),
            fontSize = (9.5f * uiScale).sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = (0.35f * uiScale).sp,
            style = TextStyle(fontFeatureSettings = "tnum"),
        )
        Spacer(Modifier.height((6f * uiScale).dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ThinLevelBar(
                progress = state.fuelLevelPercent / 100f,
                tint = if (state.fuelLevelPercent <= 15) Red else Amber,
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
            ThinLevelBar(
                progress = ((state.coolantTemperatureCelsius - 40f) / 80f)
                    .coerceIn(0f, 1f),
                tint = if (state.coolantTemperatureCelsius >= 105) Red else Blue,
                modifier = Modifier.weight(1f),
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
    var lastPreferredGear by remember {
        mutableStateOf(guidance.preferredGear)
    }
    var lastShiftDirection by remember {
        mutableStateOf(guidance.shiftDirection)
    }
    LaunchedEffect(guidance.preferredGear) {
        guidance.preferredGear?.let { lastPreferredGear = it }
    }
    LaunchedEffect(guidance.shiftDirection) {
        if (guidance.shiftDirection != ShiftDirection.NONE) {
            lastShiftDirection = guidance.shiftDirection
        }
    }
    val displayedGear =
        guidance.preferredGear ?: lastPreferredGear ?: 1
    val displayedDirection =
        guidance.shiftDirection.takeUnless { it == ShiftDirection.NONE }
            ?: lastShiftDirection
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
        visible = guidance.preferredGear != null,
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
                        val increasing = targetState > initialState
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
                        text = gear.toString(),
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
private fun SweetSpotRail(
    state: SweetSpotState,
    gearGuidance: GearGuidance,
    uiScale: Float,
    modifier: Modifier = Modifier,
) {
    val animatedRpm by animateFloatAsState(
        targetValue = state.currentRpm.toFloat(),
        animationSpec = tween(110),
        label = "sweetSpotTape",
    )
    val animatedSafeFirst by animateIntAsState(
        targetValue = state.safeRange.first,
        animationSpec = tween(180),
        label = "sweetSpotSafeFirst",
    )
    val animatedSafeLast by animateIntAsState(
        targetValue = state.safeRange.last,
        animationSpec = tween(180),
        label = "sweetSpotSafeLast",
    )
    val animatedWarningFirst by animateIntAsState(
        targetValue = state.warningRange.first,
        animationSpec = tween(180),
        label = "sweetSpotWarningFirst",
    )
    val animatedWarningLast by animateIntAsState(
        targetValue = state.warningRange.last,
        animationSpec = tween(180),
        label = "sweetSpotWarningLast",
    )
    Box(
        modifier = modifier.semantics {
            val gearDescription = gearGuidance.preferredGear
                ?.let { ", preferowany bieg $it" }
                .orEmpty()
            val shiftDescription = when (gearGuidance.shiftDirection) {
                ShiftDirection.UP -> ", zmień bieg w górę"
                ShiftDirection.DOWN -> ", zmień bieg w dół"
                ShiftDirection.NONE -> ""
            }
            contentDescription =
                "Przesuwany wskaźnik sweet spot sprzęgła$gearDescription$shiftDescription"
        },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Color(0xFF0D1215))
            val trackLeft = 0f
            val trackRight = size.width - 5.dp.toPx()
            val trackTop = 0f
            val trackBottom = size.height
            val trackWidth = trackRight - trackLeft
            val trackHeight = trackBottom - trackTop
            val centerY = size.height / 2f
            val pixelsPerRpm = trackHeight / 2_550f
            fun yFor(rpm: Int) = centerY + (animatedRpm - rpm) * pixelsPerRpm
            fun drawBand(range: IntRange, color: Color) {
                val top = yFor(range.last).coerceAtLeast(trackTop)
                val bottom = yFor(range.first).coerceAtMost(trackBottom)
                if (bottom > top) {
                    drawRect(
                        brush = Brush.horizontalGradient(
                            listOf(
                                color.copy(alpha = 0.72f),
                                color,
                                color.copy(alpha = 0.70f),
                            ),
                            startX = trackLeft,
                            endX = trackRight,
                        ),
                        topLeft = Offset(trackLeft, top),
                        size = Size(trackWidth, bottom - top),
                    )
                }
            }

            clipRect(trackLeft, trackTop, trackRight, trackBottom) {
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(Color(0xFF8E303B), Color(0xFFA63C47), Color(0xFF812A35)),
                        startY = trackTop,
                        endY = trackBottom,
                    ),
                    topLeft = Offset(trackLeft, trackTop),
                    size = Size(trackWidth, trackHeight),
                )
                drawBand(
                    animatedWarningFirst..animatedWarningLast,
                    Color(0xFFB99B59),
                )
                drawBand(
                    animatedSafeFirst..animatedSafeLast,
                    Color(0xFF3F936D),
                )
                drawRect(
                    brush = Brush.horizontalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.32f),
                            Color.Transparent,
                            Color.White.copy(alpha = 0.07f),
                            Color.Black.copy(alpha = 0.22f),
                        ),
                        startX = trackLeft,
                        endX = trackRight,
                    ),
                    topLeft = Offset(trackLeft, trackTop),
                    size = Size(trackWidth, trackHeight),
                )

                val visibleMin = animatedRpm.toInt() - 1_450
                val visibleMax = animatedRpm.toInt() + 1_450
                val first = floor(visibleMin / 100f).toInt() * 100
                val last = ceil(visibleMax / 100f).toInt() * 100
                for (rpm in first..last step 100) {
                    val y = yFor(rpm)
                    if (y in trackTop..trackBottom) {
                        val major = rpm % 500 == 0
                        val tickInset = if (major) trackWidth * 0.18f else trackWidth * 0.31f
                        drawLine(
                            color = Color.Black.copy(alpha = if (major) 0.42f else 0.22f),
                            start = Offset(trackLeft + tickInset, y),
                            end = Offset(trackRight - tickInset, y),
                            strokeWidth = if (major) 1.5.dp.toPx() else 0.7.dp.toPx(),
                            cap = StrokeCap.Round,
                        )
                    }
                }
            }

            drawLine(
                color = Color.Black.copy(alpha = 0.65f),
                start = Offset(trackLeft - 2.dp.toPx(), centerY),
                end = Offset(trackRight + 2.dp.toPx(), centerY),
                strokeWidth = 8.dp.toPx(),
                cap = StrokeCap.Square,
            )
            drawLine(
                color = Color.White.copy(alpha = 0.94f),
                start = Offset(trackLeft - 1.dp.toPx(), centerY),
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
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(),
        )
    }
}

@Composable
private fun EdgeGlow(
    state: VehicleState,
    turnPulse: Float,
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
        if (state.isLeftTurnSignalOn) {
            drawRect(
                brush = Brush.horizontalGradient(
                    listOf(
                        Green.copy(alpha = 0.31f * turnPulse),
                        Green.copy(alpha = 0.10f * turnPulse),
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
                        Green.copy(alpha = 0.10f * turnPulse),
                        Green.copy(alpha = 0.31f * turnPulse),
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
    turnPulse: Float,
) {
    Canvas(Modifier.fillMaxSize()) {
        if (state.isLeftTurnSignalOn) {
            drawLine(
                color = Green.copy(alpha = 0.78f * turnPulse),
                start = Offset(1.dp.toPx(), 0f),
                end = Offset(1.dp.toPx(), size.height),
                strokeWidth = 2.dp.toPx(),
            )
        }
        if (state.isRightTurnSignalOn) {
            drawLine(
                color = Green.copy(alpha = 0.78f * turnPulse),
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

private fun formatOdometer(value: Long): String =
    NumberFormat.getIntegerInstance(Locale("pl", "PL")).format(value)
