package pl.polsatgranie.smartmegane.data.vehicle

import kotlin.math.roundToInt
import pl.polsatgranie.smartmegane.domain.signal.SignalDefinitions
import pl.polsatgranie.smartmegane.domain.signal.SignalKey
import pl.polsatgranie.smartmegane.domain.signal.SignalState
import pl.polsatgranie.smartmegane.domain.signal.SignalValue
import pl.polsatgranie.smartmegane.domain.vehicle.VehicleState
import pl.polsatgranie.smartmegane.domain.vehicle.WiperMode

/**
 * Bridges the canonical CAN map into the typed, UI-facing vehicle API.
 *
 * There is no alternate-source selection here. Each property reads one
 * [SignalDefinitions] key and becomes unavailable/default when that source is
 * absent or stale.
 */
class VehicleSignalAdapter {
    private companion object {
        const val FAST_SIGNAL_MAX_AGE_MS = 500L
        const val SPEED_MAX_AGE_MS = 750L
        const val BODY_SIGNAL_MAX_AGE_MS = 750L
        const val SLOW_SIGNAL_MAX_AGE_MS = 1_500L
        const val ACCELERATOR_RELEASED_RAW = 0x10
        const val ACCELERATOR_FULL_RAW = 0xE0
    }

    fun merge(
        signals: SignalState,
        nowMs: Long? = null,
    ): VehicleState {
        fun signalValue(
            key: SignalKey,
            maxAgeMs: Long? = null,
        ): SignalValue? =
            if (nowMs != null && maxAgeMs != null) {
                signals.getFresh(key, nowMs, maxAgeMs)
            } else {
                signals.get(key)
            }

        fun number(
            key: SignalKey,
            maxAgeMs: Long? = null,
        ): Double? =
            (signalValue(key, maxAgeMs) as? SignalValue.Number)?.value

        fun boolean(
            key: SignalKey,
            maxAgeMs: Long? = null,
        ): Boolean? =
            (signalValue(key, maxAgeMs) as? SignalValue.Bool)?.value

        fun rounded(
            key: SignalKey,
            maxAgeMs: Long? = null,
        ): Int? = number(key, maxAgeMs)?.roundToInt()

        val rpm = number(SignalDefinitions.engineRpm, FAST_SIGNAL_MAX_AGE_MS)
        val speed = number(SignalDefinitions.vehicleSpeedKph, SPEED_MAX_AGE_MS)
        val rpmTimestampMs =
            rpm?.let { signals.timestampMs(SignalDefinitions.engineRpm) }
        val speedTimestampMs =
            speed?.let { signals.timestampMs(SignalDefinitions.vehicleSpeedKph) }
        val kinematicsTimestamp =
            if (rpmTimestampMs != null && speedTimestampMs != null) {
                minOf(rpmTimestampMs, speedTimestampMs)
            } else {
                null
            }
        val acceleratorRaw =
            number(SignalDefinitions.acceleratorPedalRaw, FAST_SIGNAL_MAX_AGE_MS)
        val acceleratorPercent = acceleratorRaw?.let {
            (((it - ACCELERATOR_RELEASED_RAW) /
                (ACCELERATOR_FULL_RAW - ACCELERATOR_RELEASED_RAW)) * 100.0)
                .coerceIn(0.0, 100.0)
                .toFloat()
        }
        val brakePressed =
            boolean(SignalDefinitions.brakePressed, FAST_SIGNAL_MAX_AGE_MS)
        val clutchPressed =
            boolean(SignalDefinitions.clutchPressed, FAST_SIGNAL_MAX_AGE_MS)
        val reverseEngaged =
            boolean(SignalDefinitions.reverseGear, BODY_SIGNAL_MAX_AGE_MS)
        val wipers = (signalValue(
            SignalDefinitions.wipersMode,
            BODY_SIGNAL_MAX_AGE_MS,
        ) as? SignalValue.Enum)?.code?.toWiperModeOrNull()

        return VehicleState(
            speedKph = speed?.roundToInt() ?: 0,
            speedKphPrecise = speed,
            isSpeedSignalAvailable = speed != null,
            engineRpm = rpm?.roundToInt() ?: 0,
            engineRpmPrecise = rpm,
            isEngineRpmSignalAvailable = rpm != null,
            kinematicsSampleTimestampMs = kinematicsTimestamp,
            fuelLevelRaw =
                rounded(SignalDefinitions.fuelLevelRaw, SLOW_SIGNAL_MAX_AGE_MS),
            coolantTemperatureCelsius =
                rounded(
                    SignalDefinitions.coolantTemperature,
                    SLOW_SIGNAL_MAX_AGE_MS,
                ) ?: 0,
            outsideTemperatureCelsius =
                rounded(
                    SignalDefinitions.outsideTemperature,
                    BODY_SIGNAL_MAX_AGE_MS,
                ),
            odometerKm =
                number(SignalDefinitions.odometer)?.toLong() ?: 0L,
            distanceSinceStartMeters =
                number(
                    SignalDefinitions.distanceSinceStart,
                    SPEED_MAX_AGE_MS,
                ),
            fuelUsedSinceStartLiters =
                number(
                    SignalDefinitions.fuelUsedSinceStart,
                    SLOW_SIGNAL_MAX_AGE_MS,
                ),
            vehicleAgeMinutes =
                number(SignalDefinitions.vehicleAgeMinutes)?.toLong(),
            acceleratorPedalPercent = acceleratorPercent,
            requestedEngineTorqueNm =
                rounded(
                    SignalDefinitions.driverRequestedTorque,
                    FAST_SIGNAL_MAX_AGE_MS,
                ),
            isEngineRunning =
                boolean(
                    SignalDefinitions.engineRunning,
                    BODY_SIGNAL_MAX_AGE_MS,
                ) ?: false,
            isEngineDataValid =
                boolean(
                    SignalDefinitions.engineDataValid,
                    FAST_SIGNAL_MAX_AGE_MS,
                ) ?: false,
            isSteeringDataValid =
                boolean(
                    SignalDefinitions.steeringDataValid,
                    FAST_SIGNAL_MAX_AGE_MS,
                ) ?: false,
            isClusterNetworkActive =
                boolean(
                    SignalDefinitions.clusterNetworkActive,
                    BODY_SIGNAL_MAX_AGE_MS,
                ) ?: false,
            isRearDefrostOn =
                boolean(
                    SignalDefinitions.rearDefrostOn,
                    BODY_SIGNAL_MAX_AGE_MS,
                ) ?: false,
            isEspAsrDisabled =
                boolean(
                    SignalDefinitions.asrEspDisabled,
                    SPEED_MAX_AGE_MS,
                ) ?: false,
            isAsrEspButtonPressed =
                boolean(
                    SignalDefinitions.asrEspButtonPressed,
                    BODY_SIGNAL_MAX_AGE_MS,
                ) ?: false,
            wheelPairAFirstRaw =
                rounded(
                    SignalDefinitions.wheelPairAFirstRaw,
                    FAST_SIGNAL_MAX_AGE_MS,
                ),
            wheelPairASecondRaw =
                rounded(
                    SignalDefinitions.wheelPairASecondRaw,
                    FAST_SIGNAL_MAX_AGE_MS,
                ),
            wheelPairBFirstRaw =
                rounded(
                    SignalDefinitions.wheelPairBFirstRaw,
                    FAST_SIGNAL_MAX_AGE_MS,
                ),
            wheelPairBSecondRaw =
                rounded(
                    SignalDefinitions.wheelPairBSecondRaw,
                    FAST_SIGNAL_MAX_AGE_MS,
                ),
            yawSensorRaw =
                rounded(SignalDefinitions.yawRaw, FAST_SIGNAL_MAX_AGE_MS),
            inertialAxisARaw =
                rounded(
                    SignalDefinitions.inertialAxisARaw,
                    FAST_SIGNAL_MAX_AGE_MS,
                ),
            inertialAxisBRaw =
                rounded(
                    SignalDefinitions.inertialAxisBRaw,
                    FAST_SIGNAL_MAX_AGE_MS,
                ),
            bodySensorRaw =
                rounded(SignalDefinitions.bodySensorRaw, SLOW_SIGNAL_MAX_AGE_MS),
            bodyStatusRaw =
                rounded(SignalDefinitions.bodyStatusRaw, SLOW_SIGNAL_MAX_AGE_MS),
            serviceStatusRaw =
                rounded(SignalDefinitions.serviceStatusRaw, SLOW_SIGNAL_MAX_AGE_MS),
            odometerStatusRaw =
                rounded(SignalDefinitions.odometerStatusRaw, SLOW_SIGNAL_MAX_AGE_MS),
            isParkingBrakeActive =
                boolean(
                    SignalDefinitions.parkingBrake,
                    SLOW_SIGNAL_MAX_AGE_MS,
                ) ?: false,
            isDriverSeatBeltWarningActive =
                boolean(
                    SignalDefinitions.driverSeatBeltWarning,
                    SLOW_SIGNAL_MAX_AGE_MS,
                ) ?: false,
            arePositionLightsOn =
                boolean(
                    SignalDefinitions.positionLights,
                    BODY_SIGNAL_MAX_AGE_MS,
                ) ?: false,
            areLowBeamLightsOn =
                boolean(
                    SignalDefinitions.lowBeamLights,
                    BODY_SIGNAL_MAX_AGE_MS,
                ) ?: false,
            areHighBeamLightsOn =
                boolean(
                    SignalDefinitions.highBeamLights,
                    BODY_SIGNAL_MAX_AGE_MS,
                ) ?: false,
            areFrontFogLightsOn =
                boolean(
                    SignalDefinitions.frontFogLights,
                    BODY_SIGNAL_MAX_AGE_MS,
                ) ?: false,
            areRearFogLightsOn =
                boolean(
                    SignalDefinitions.rearFogLights,
                    BODY_SIGNAL_MAX_AGE_MS,
                ) ?: false,
            isLeftTurnSignalOn =
                boolean(
                    SignalDefinitions.leftTurnSignal,
                    BODY_SIGNAL_MAX_AGE_MS,
                ) ?: false,
            isRightTurnSignalOn =
                boolean(
                    SignalDefinitions.rightTurnSignal,
                    BODY_SIGNAL_MAX_AGE_MS,
                ) ?: false,
            isFrontLeftDoorOpen =
                boolean(
                    SignalDefinitions.doorFrontLeft,
                    BODY_SIGNAL_MAX_AGE_MS,
                ) ?: false,
            isFrontRightDoorOpen =
                boolean(
                    SignalDefinitions.doorFrontRight,
                    BODY_SIGNAL_MAX_AGE_MS,
                ) ?: false,
            isRearLeftDoorOpen =
                boolean(
                    SignalDefinitions.doorRearLeft,
                    BODY_SIGNAL_MAX_AGE_MS,
                ) ?: false,
            isRearRightDoorOpen =
                boolean(
                    SignalDefinitions.doorRearRight,
                    BODY_SIGNAL_MAX_AGE_MS,
                ) ?: false,
            isTrunkOpen =
                boolean(
                    SignalDefinitions.trunkOpen,
                    BODY_SIGNAL_MAX_AGE_MS,
                ) ?: false,
            isBrakePedalPressed = brakePressed ?: false,
            isBrakePedalSignalAvailable = brakePressed != null,
            isClutchPedalPressed = clutchPressed ?: false,
            isClutchPedalSignalAvailable = clutchPressed != null,
            isReverseGearEngaged = reverseEngaged ?: false,
            isReverseGearSignalAvailable = reverseEngaged != null,
            areDoorsLocked =
                boolean(
                    SignalDefinitions.doorsLocked,
                    BODY_SIGNAL_MAX_AGE_MS,
                ) ?: false,
            isTrunkLocked =
                boolean(
                    SignalDefinitions.trunkLocked,
                    BODY_SIGNAL_MAX_AGE_MS,
                ) ?: false,
            isIgnitionOn =
                boolean(
                    SignalDefinitions.ignitionOn,
                    BODY_SIGNAL_MAX_AGE_MS,
                ) ?: false,
            isAccessoryPowerOn =
                boolean(
                    SignalDefinitions.accessoryPowerOn,
                    BODY_SIGNAL_MAX_AGE_MS,
                ) ?: false,
            isPassengerAirbagDisabled =
                boolean(
                    SignalDefinitions.passengerAirbagDisabled,
                    SLOW_SIGNAL_MAX_AGE_MS,
                ) ?: false,
            isTripComputerUpPressed =
                boolean(
                    SignalDefinitions.tripComputerUp,
                    BODY_SIGNAL_MAX_AGE_MS,
                ) ?: false,
            isTripComputerDownPressed =
                boolean(
                    SignalDefinitions.tripComputerDown,
                    BODY_SIGNAL_MAX_AGE_MS,
                ) ?: false,
            instrumentBacklightRaw =
                rounded(
                    SignalDefinitions.instrumentBacklightRaw,
                    SLOW_SIGNAL_MAX_AGE_MS,
                ),
            steeringWheelAngleDegrees =
                number(
                    SignalDefinitions.steeringAngleDegrees,
                    FAST_SIGNAL_MAX_AGE_MS,
                )?.toFloat(),
            steeringWheelAngularVelocityRaw =
                rounded(
                    SignalDefinitions.steeringAngularVelocityRaw,
                    FAST_SIGNAL_MAX_AGE_MS,
                ),
            wiperMode = wipers ?: WiperMode.OFF,
        )
    }
}

private fun Int.toWiperModeOrNull(): WiperMode? =
    when (this) {
        0 -> WiperMode.OFF
        1 -> WiperMode.INTERMITTENT
        6 -> WiperMode.LOW
        7 -> WiperMode.HIGH
        else -> null
    }
