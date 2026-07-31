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
        // A missing publication is not 0 km/h. Retain the last confirmed 0x354
        // sample until the whole CAN bus itself is considered silent.
        const val SPEED_MAX_AGE_MS = 1_600L
        // Body frames arrive at ~10 Hz. Keep their last confirmed value beyond
        // the 1.5 s whole-bus silence timeout so a scheduling pause can never
        // turn a door, lock or parking-brake bit into a synthetic false pulse.
        const val BODY_SIGNAL_MAX_AGE_MS = 2_000L
        const val SLOW_SIGNAL_MAX_AGE_MS = 3_000L
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
        val fuelLevelRaw =
            rounded(SignalDefinitions.fuelLevelRaw, SLOW_SIGNAL_MAX_AGE_MS)
        val fuelLevelEstimatedPercent =
            fuelLevelRaw?.let { (it / 255f * 100f).coerceIn(0f, 100f) }
        val coolantTemperature =
            rounded(
                SignalDefinitions.coolantTemperature,
                SLOW_SIGNAL_MAX_AGE_MS,
            )
        val odometer = number(SignalDefinitions.odometer)
        val leftTurnSignal =
            boolean(SignalDefinitions.leftTurnSignal, BODY_SIGNAL_MAX_AGE_MS)
        val rightTurnSignal =
            boolean(SignalDefinitions.rightTurnSignal, BODY_SIGNAL_MAX_AGE_MS)

        return VehicleState(
            speedKph = speed?.roundToInt() ?: 0,
            speedKphPrecise = speed,
            isSpeedSignalAvailable = speed != null,
            engineRpm = rpm?.roundToInt() ?: 0,
            engineRpmPrecise = rpm,
            isEngineRpmSignalAvailable = rpm != null,
            kinematicsSampleTimestampMs = kinematicsTimestamp,
            fuelLevelPercent = fuelLevelEstimatedPercent?.roundToInt() ?: 0,
            fuelLevelEstimatedPercent = fuelLevelEstimatedPercent,
            fuelLevelRaw = fuelLevelRaw,
            isFuelLevelSignalAvailable = fuelLevelRaw != null,
            coolantTemperatureCelsius = coolantTemperature ?: 0,
            isCoolantTemperatureSignalAvailable = coolantTemperature != null,
            outsideTemperatureCelsius =
                rounded(
                    SignalDefinitions.outsideTemperature,
                    BODY_SIGNAL_MAX_AGE_MS,
                ),
            odometerKm = odometer?.toLong() ?: 0L,
            isOdometerSignalAvailable = odometer != null,
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
            fuelCounterSampleTimestampMs =
                signals.timestampMs(SignalDefinitions.fuelUsedSinceStart),
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
            isRearDefrostCommandActive =
                boolean(
                    SignalDefinitions.rearDefrostCommandActive,
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
            wheelPairARightSpeedKph =
                number(
                    SignalDefinitions.wheelPairARightSpeedKph,
                    FAST_SIGNAL_MAX_AGE_MS,
                ),
            wheelPairALeftSpeedKph =
                number(
                    SignalDefinitions.wheelPairALeftSpeedKph,
                    FAST_SIGNAL_MAX_AGE_MS,
                ),
            wheelPairBRightSpeedKph =
                number(
                    SignalDefinitions.wheelPairBRightSpeedKph,
                    FAST_SIGNAL_MAX_AGE_MS,
                ),
            wheelPairBLeftSpeedKph =
                number(
                    SignalDefinitions.wheelPairBLeftSpeedKph,
                    FAST_SIGNAL_MAX_AGE_MS,
                ),
            longitudinalAccelerationRaw =
                rounded(
                    SignalDefinitions.longitudinalAccelerationRaw,
                    FAST_SIGNAL_MAX_AGE_MS,
                ),
            lateralAccelerationRaw =
                rounded(
                    SignalDefinitions.lateralAccelerationRaw,
                    FAST_SIGNAL_MAX_AGE_MS,
                ),
            yawRateRaw =
                rounded(
                    SignalDefinitions.yawRateRaw,
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
            isElectronicFaultActive =
                boolean(
                    SignalDefinitions.electronicFault,
                    BODY_SIGNAL_MAX_AGE_MS,
                ) ?: false,
            isCoolantOverheatWarningActive =
                coolantTemperature?.let { it >= 110 } ?: false,
            isLowFuelWarningActive =
                fuelLevelEstimatedPercent?.let { it <= 15f } ?: false,
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
            isLeftTurnSignalOn = leftTurnSignal ?: false,
            isRightTurnSignalOn = rightTurnSignal ?: false,
            areHazardLightsOn =
                leftTurnSignal == true && rightTurnSignal == true,
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
            isDoorLockSignalAvailable =
                boolean(
                    SignalDefinitions.doorsLocked,
                    BODY_SIGNAL_MAX_AGE_MS,
                ) != null,
            isTrunkLocked =
                boolean(
                    SignalDefinitions.trunkLocked,
                    BODY_SIGNAL_MAX_AGE_MS,
                ) ?: false,
            isTrunkLockSignalAvailable =
                boolean(
                    SignalDefinitions.trunkLocked,
                    BODY_SIGNAL_MAX_AGE_MS,
                ) != null,
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
