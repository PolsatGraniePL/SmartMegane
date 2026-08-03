package pl.polsatgranie.smartmegane.domain.vehicle

import pl.polsatgranie.smartmegane.domain.signal.SignalDefinitions
import pl.polsatgranie.smartmegane.domain.signal.SignalKey
import pl.polsatgranie.smartmegane.domain.signal.SignalState
import kotlin.math.roundToInt

/**
 * Rejects short transport gaps and single-frame startup glitches without
 * inventing alternate CAN sources. Every output still comes from its canonical
 * signal; only confirmation and last-sample retention are applied.
 */
class LiveVehicleStateStabilizer {
    private companion object {
        const val DYNAMIC_SIGNAL_HOLD_MS = 1_500L
        const val HIGH_SPEED_ZERO_CONFIRM_MS = 350L
        const val LOW_SPEED_ZERO_CONFIRM_MS = 120L
        const val BODY_CONFIRMING_SAMPLES = 2
        const val ENGINE_RUNNING_RPM = 450.0
    }

    private data class StableSlot(
        var output: Any? = null,
        var candidate: Any? = null,
        var candidateCount: Int = 0,
        var lastTimestampMs: Long? = null,
    )

    private val bodySlots = mutableMapOf<String, StableSlot>()
    private var displayedSpeedKph: Double? = null
    private var lastSpeedTimestampMs: Long? = null
    private var zeroCandidateSinceMs: Long? = null
    private var zeroCandidateSamples = 0
    private var retainedRpm: Double? = null
    private var retainedSteeringAngle: Float? = null

    fun reset() {
        bodySlots.clear()
        displayedSpeedKph = null
        lastSpeedTimestampMs = null
        zeroCandidateSinceMs = null
        zeroCandidateSamples = 0
        retainedRpm = null
        retainedSteeringAngle = null
    }

    fun stabilize(
        state: VehicleState,
        signals: SignalState,
        nowMs: Long,
    ): VehicleState {
        val speed = stabilizedSpeed(state, signals, nowMs)
        val rpm = retainedDynamicNumber(
            raw = state.engineRpmPrecise,
            available = state.isEngineRpmSignalAvailable,
            timestampMs = signals.timestampMs(SignalDefinitions.engineRpm),
            nowMs = nowMs,
            previous = retainedRpm,
        ).also { retainedRpm = it }
        val steering = retainedDynamicNumber(
            raw = state.steeringWheelAngleDegrees?.toDouble(),
            available = state.steeringWheelAngleDegrees != null,
            timestampMs = signals.timestampMs(SignalDefinitions.steeringAngleDegrees),
            nowMs = nowMs,
            previous = retainedSteeringAngle?.toDouble(),
        )?.toFloat().also { retainedSteeringAngle = it }
        val engineActuallyRunning = (rpm ?: 0.0) >= ENGINE_RUNNING_RPM

        fun stableBoolean(key: SignalKey, raw: Boolean): Boolean = stableBodyValue(
            key = key,
            raw = raw,
            timestampMs = signals.timestampMs(key),
            direct = engineActuallyRunning,
            default = false,
        )

        val doorsLocked = stableBoolean(SignalDefinitions.doorsLocked, state.areDoorsLocked)
        val trunkLocked = stableBoolean(SignalDefinitions.trunkLocked, state.isTrunkLocked)

        return state.copy(
            speedKph = speed?.roundToInt() ?: 0,
            speedKphPrecise = speed,
            isSpeedSignalAvailable = speed != null,
            engineRpm = rpm?.roundToInt() ?: 0,
            engineRpmPrecise = rpm,
            isEngineRpmSignalAvailable = rpm != null,
            steeringWheelAngleDegrees = steering,
            isFrontLeftDoorOpen = stableBoolean(
                SignalDefinitions.doorFrontLeft,
                state.isFrontLeftDoorOpen,
            ),
            isFrontRightDoorOpen = stableBoolean(
                SignalDefinitions.doorFrontRight,
                state.isFrontRightDoorOpen,
            ),
            isRearLeftDoorOpen = stableBoolean(
                SignalDefinitions.doorRearLeft,
                state.isRearLeftDoorOpen,
            ),
            isRearRightDoorOpen = stableBoolean(
                SignalDefinitions.doorRearRight,
                state.isRearRightDoorOpen,
            ),
            isTrunkOpen = stableBoolean(SignalDefinitions.trunkOpen, state.isTrunkOpen),
            isParkingBrakeActive = stableBoolean(
                SignalDefinitions.parkingBrake,
                state.isParkingBrakeActive,
            ),
            arePositionLightsOn = stableBoolean(
                SignalDefinitions.positionLights,
                state.arePositionLightsOn,
            ),
            areLowBeamLightsOn = stableBoolean(
                SignalDefinitions.lowBeamLights,
                state.areLowBeamLightsOn,
            ),
            areHighBeamLightsOn = stableBoolean(
                SignalDefinitions.highBeamLights,
                state.areHighBeamLightsOn,
            ),
            areFrontFogLightsOn = stableBoolean(
                SignalDefinitions.frontFogLights,
                state.areFrontFogLightsOn,
            ),
            areRearFogLightsOn = stableBoolean(
                SignalDefinitions.rearFogLights,
                state.areRearFogLightsOn,
            ),
            isLeftTurnSignalOn = stableBoolean(
                SignalDefinitions.leftTurnSignal,
                state.isLeftTurnSignalOn,
            ),
            isRightTurnSignalOn = stableBoolean(
                SignalDefinitions.rightTurnSignal,
                state.isRightTurnSignalOn,
            ),
            areHazardLightsOn = stableBoolean(
                SignalDefinitions.leftTurnSignal,
                state.isLeftTurnSignalOn,
            ) && stableBoolean(
                SignalDefinitions.rightTurnSignal,
                state.isRightTurnSignalOn,
            ),
            areDoorsLocked = doorsLocked,
            isDoorLockSignalAvailable = stableOutputAvailable(SignalDefinitions.doorsLocked),
            isTrunkLocked = trunkLocked,
            isTrunkLockSignalAvailable = stableOutputAvailable(SignalDefinitions.trunkLocked),
            isReverseGearEngaged = stableBoolean(
                SignalDefinitions.reverseGear,
                state.isReverseGearEngaged,
            ),
            isReverseGearSignalAvailable =
                stableOutputAvailable(SignalDefinitions.reverseGear),
            isDriverSeatBeltWarningActive = stableBoolean(
                SignalDefinitions.driverSeatBeltWarning,
                state.isDriverSeatBeltWarningActive,
            ),
            isElectronicFaultActive = stableBoolean(
                SignalDefinitions.electronicFault,
                state.isElectronicFaultActive,
            ),
            wiperMode = stableBodyValue(
                key = SignalDefinitions.wipersMode,
                raw = state.wiperMode,
                timestampMs = signals.timestampMs(SignalDefinitions.wipersMode),
                direct = engineActuallyRunning,
                default = WiperMode.OFF,
            ),
        )
    }

    private fun stabilizedSpeed(
        state: VehicleState,
        signals: SignalState,
        nowMs: Long,
    ): Double? {
        val timestamp = signals.timestampMs(SignalDefinitions.vehicleSpeedKph)
        val raw = state.speedKphPrecise
        if (state.isSpeedSignalAvailable && raw != null && timestamp != null) {
            if (timestamp != lastSpeedTimestampMs) {
                lastSpeedTimestampMs = timestamp
                if (raw <= 0.05 && (displayedSpeedKph ?: 0.0) > 1.0) {
                    if (zeroCandidateSinceMs == null) {
                        zeroCandidateSinceMs = nowMs
                        zeroCandidateSamples = 1
                    } else {
                        zeroCandidateSamples += 1
                    }
                } else {
                    displayedSpeedKph = raw.coerceAtLeast(0.0)
                    zeroCandidateSinceMs = null
                    zeroCandidateSamples = 0
                }
            }
            val zeroSince = zeroCandidateSinceMs
            if (zeroSince != null && zeroCandidateSamples >= 2) {
                val confirmMs = if ((displayedSpeedKph ?: 0.0) > 4.0) {
                    HIGH_SPEED_ZERO_CONFIRM_MS
                } else {
                    LOW_SPEED_ZERO_CONFIRM_MS
                }
                if (nowMs - zeroSince >= confirmMs) {
                    displayedSpeedKph = 0.0
                    zeroCandidateSinceMs = null
                    zeroCandidateSamples = 0
                }
            }
        } else if (timestamp == null || nowMs - timestamp > DYNAMIC_SIGNAL_HOLD_MS) {
            displayedSpeedKph = null
            zeroCandidateSinceMs = null
            zeroCandidateSamples = 0
        }
        return displayedSpeedKph
    }

    private fun retainedDynamicNumber(
        raw: Double?,
        available: Boolean,
        timestampMs: Long?,
        nowMs: Long,
        previous: Double?,
    ): Double? = when {
        available && raw != null -> raw
        timestampMs != null && nowMs - timestampMs <= DYNAMIC_SIGNAL_HOLD_MS -> previous
        else -> null
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> stableBodyValue(
        key: SignalKey,
        raw: T,
        timestampMs: Long?,
        direct: Boolean,
        default: T,
    ): T {
        val slot = bodySlots.getOrPut(key.id) { StableSlot() }
        if (timestampMs == null) return (slot.output as? T) ?: default
        // Re-publication of the same CAN sample must never reinterpret an
        // adapter default (false/OFF) as a new body state after freshness expiry.
        if (slot.lastTimestampMs == timestampMs && slot.output != null) {
            return slot.output as T
        }
        if (direct) {
            slot.output = raw
            slot.candidate = raw
            slot.candidateCount = BODY_CONFIRMING_SAMPLES
            slot.lastTimestampMs = timestampMs
            return raw
        }
        if (slot.lastTimestampMs != timestampMs) {
            slot.lastTimestampMs = timestampMs
            if (slot.candidate == raw) {
                slot.candidateCount += 1
            } else {
                slot.candidate = raw
                slot.candidateCount = 1
            }
            if (slot.candidateCount >= BODY_CONFIRMING_SAMPLES) slot.output = raw
        }
        return (slot.output as? T) ?: default
    }

    private fun stableOutputAvailable(key: SignalKey): Boolean =
        bodySlots[key.id]?.output != null
}
