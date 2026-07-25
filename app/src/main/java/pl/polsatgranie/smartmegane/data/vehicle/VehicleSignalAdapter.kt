package pl.polsatgranie.smartmegane.data.vehicle

import kotlin.math.abs
import kotlin.math.roundToInt
import pl.polsatgranie.smartmegane.domain.signal.SignalDefinitions
import pl.polsatgranie.smartmegane.domain.signal.SignalKey
import pl.polsatgranie.smartmegane.domain.signal.SignalState
import pl.polsatgranie.smartmegane.domain.signal.SignalValue
import pl.polsatgranie.smartmegane.domain.vehicle.VehicleState
import pl.polsatgranie.smartmegane.domain.vehicle.WiperMode

/**
 * Bridges decoded CAN signals into the typed, UI-facing vehicle API.
 *
 * Duplicate broadcasts stay separate in [SignalDefinitions]. That lets this
 * adapter prefer the fastest source, fall back to a second ECU and expose an
 * inconsistency instead of silently averaging two disagreeing values.
 */
class VehicleSignalAdapter {
    private companion object {
        const val FAST_SIGNAL_MAX_AGE_MS = 500L
        const val SPEED_MAX_AGE_MS = 750L
        const val BODY_SIGNAL_MAX_AGE_MS = 750L
        const val SLOW_SIGNAL_MAX_AGE_MS = 1_500L
        const val RPM_DISAGREEMENT_THRESHOLD = 50.0
        const val ACCELERATOR_RELEASED_RAW = 0x10
        const val ACCELERATOR_FULL_RAW = 0xE0
    }

    fun merge(
        placeholder: VehicleState,
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

        fun combinedBoolean(
            keys: List<SignalKey>,
            maxAgeMs: Long,
            fallback: Boolean,
        ): Boolean {
            val candidates = keys.mapNotNull { boolean(it, maxAgeMs) }
            return if (candidates.isEmpty()) fallback else candidates.any { it }
        }

        val rpmPrimary = number(
            SignalDefinitions.engineRpmPrimary,
            FAST_SIGNAL_MAX_AGE_MS,
        )
        val rpmSecondary = number(
            SignalDefinitions.engineRpmSecondary,
            FAST_SIGNAL_MAX_AGE_MS,
        )
        val rpm = rpmPrimary ?: rpmSecondary
        val rpmSourcesConsistent = when {
            rpmPrimary != null && rpmSecondary != null ->
                abs(rpmPrimary - rpmSecondary) <= RPM_DISAGREEMENT_THRESHOLD

            rpm != null -> true
            else -> placeholder.areRpmSourcesConsistent
        }

        val speed = number(
            SignalDefinitions.vehicleSpeedKph,
            SPEED_MAX_AGE_MS,
        ) ?: number(
            SignalDefinitions.speedSecondary645,
            SLOW_SIGNAL_MAX_AGE_MS,
        )
        val coolantTemperature = number(
            SignalDefinitions.coolantTemperature60D,
            BODY_SIGNAL_MAX_AGE_MS,
        ) ?: number(
            SignalDefinitions.coolantTemperature551,
            SLOW_SIGNAL_MAX_AGE_MS,
        )
        val odometer = number(SignalDefinitions.odometer5FD)
            ?: number(SignalDefinitions.odometer5C5)
            ?: number(SignalDefinitions.odometer715)
        val wipers = (signalValue(
            SignalDefinitions.wipersMode,
            BODY_SIGNAL_MAX_AGE_MS,
        ) as? SignalValue.Enum)?.code?.toWiperModeOrNull()
        val acceleratorRaw = number(
            SignalDefinitions.acceleratorPedalRaw,
            FAST_SIGNAL_MAX_AGE_MS,
        )
        val acceleratorPercent = acceleratorRaw?.let {
            (((it - ACCELERATOR_RELEASED_RAW) /
                (ACCELERATOR_FULL_RAW - ACCELERATOR_RELEASED_RAW)) * 100.0)
                .coerceIn(0.0, 100.0)
                .toFloat()
        }

        return placeholder.copy(
            speedKph = speed?.roundToInt() ?: placeholder.speedKph,
            speedKphPrecise = speed ?: placeholder.speedKphPrecise,
            engineRpm = rpm?.roundToInt() ?: placeholder.engineRpm,
            engineRpmPrecise = rpm ?: placeholder.engineRpmPrecise,
            areRpmSourcesConsistent = rpmSourcesConsistent,
            coolantTemperatureCelsius =
                coolantTemperature?.roundToInt()
                    ?: placeholder.coolantTemperatureCelsius,
            outsideTemperatureCelsius =
                number(
                    SignalDefinitions.outsideTemperature,
                    BODY_SIGNAL_MAX_AGE_MS,
                )?.roundToInt() ?: placeholder.outsideTemperatureCelsius,
            odometerKm = odometer?.toLong() ?: placeholder.odometerKm,
            distanceSinceStartMeters =
                number(
                    SignalDefinitions.distanceSinceStart,
                    SPEED_MAX_AGE_MS,
                ) ?: placeholder.distanceSinceStartMeters,
            fuelUsedSinceStartLiters =
                number(
                    SignalDefinitions.fuelUsedSinceStart,
                    SLOW_SIGNAL_MAX_AGE_MS,
                ) ?: placeholder.fuelUsedSinceStartLiters,
            vehicleAgeMinutes =
                number(SignalDefinitions.vehicleAgeMinutes)?.toLong()
                    ?: placeholder.vehicleAgeMinutes,
            acceleratorPedalPercent =
                acceleratorPercent ?: placeholder.acceleratorPedalPercent,
            requestedEngineTorqueNm =
                number(
                    SignalDefinitions.driverRequestedTorque,
                    FAST_SIGNAL_MAX_AGE_MS,
                )?.roundToInt() ?: placeholder.requestedEngineTorqueNm,
            isParkingBrakeActive =
                boolean(
                    SignalDefinitions.parkingBrake,
                    SLOW_SIGNAL_MAX_AGE_MS,
                ) ?: placeholder.isParkingBrakeActive,
            isDriverSeatBeltWarningActive =
                boolean(
                    SignalDefinitions.driverSeatBeltWarning,
                    SLOW_SIGNAL_MAX_AGE_MS,
                ) ?: placeholder.isDriverSeatBeltWarningActive,
            arePositionLightsOn =
                boolean(
                    SignalDefinitions.positionLights,
                    BODY_SIGNAL_MAX_AGE_MS,
                ) ?: placeholder.arePositionLightsOn,
            areLowBeamLightsOn =
                boolean(
                    SignalDefinitions.lowBeamLights,
                    BODY_SIGNAL_MAX_AGE_MS,
                ) ?: placeholder.areLowBeamLightsOn,
            areHighBeamLightsOn =
                boolean(
                    SignalDefinitions.highBeamLights,
                    BODY_SIGNAL_MAX_AGE_MS,
                ) ?: placeholder.areHighBeamLightsOn,
            areFrontFogLightsOn =
                boolean(
                    SignalDefinitions.frontFogLights,
                    BODY_SIGNAL_MAX_AGE_MS,
                ) ?: placeholder.areFrontFogLightsOn,
            areRearFogLightsOn =
                boolean(
                    SignalDefinitions.rearFogLights,
                    BODY_SIGNAL_MAX_AGE_MS,
                ) ?: placeholder.areRearFogLightsOn,
            isLeftTurnSignalOn =
                boolean(
                    SignalDefinitions.leftTurnSignal,
                    BODY_SIGNAL_MAX_AGE_MS,
                ) ?: placeholder.isLeftTurnSignalOn,
            isRightTurnSignalOn =
                boolean(
                    SignalDefinitions.rightTurnSignal,
                    BODY_SIGNAL_MAX_AGE_MS,
                ) ?: placeholder.isRightTurnSignalOn,
            isFrontLeftDoorOpen =
                boolean(
                    SignalDefinitions.doorFrontLeft,
                    BODY_SIGNAL_MAX_AGE_MS,
                ) ?: placeholder.isFrontLeftDoorOpen,
            isFrontRightDoorOpen =
                boolean(
                    SignalDefinitions.doorFrontRight,
                    BODY_SIGNAL_MAX_AGE_MS,
                ) ?: placeholder.isFrontRightDoorOpen,
            isRearLeftDoorOpen =
                boolean(
                    SignalDefinitions.doorRearLeft,
                    BODY_SIGNAL_MAX_AGE_MS,
                ) ?: placeholder.isRearLeftDoorOpen,
            isRearRightDoorOpen =
                boolean(
                    SignalDefinitions.doorRearRight,
                    BODY_SIGNAL_MAX_AGE_MS,
                ) ?: placeholder.isRearRightDoorOpen,
            isTrunkOpen =
                boolean(
                    SignalDefinitions.trunkOpen,
                    BODY_SIGNAL_MAX_AGE_MS,
                ) ?: placeholder.isTrunkOpen,
            isBrakePedalPressed = combinedBoolean(
                keys = listOf(
                    SignalDefinitions.brakePressed181,
                    SignalDefinitions.brakePressed354,
                ),
                maxAgeMs = SPEED_MAX_AGE_MS,
                fallback = placeholder.isBrakePedalPressed,
            ),
            isClutchPedalPressed =
                boolean(
                    SignalDefinitions.clutchPressed181,
                    FAST_SIGNAL_MAX_AGE_MS,
                ) ?: placeholder.isClutchPedalPressed,
            isReverseGearEngaged = combinedBoolean(
                keys = listOf(
                    SignalDefinitions.reverseGear60D,
                    SignalDefinitions.reverseGear215,
                ),
                maxAgeMs = BODY_SIGNAL_MAX_AGE_MS,
                fallback = placeholder.isReverseGearEngaged,
            ),
            areDoorsLocked =
                boolean(
                    SignalDefinitions.doorsLocked,
                    BODY_SIGNAL_MAX_AGE_MS,
                ) ?: placeholder.areDoorsLocked,
            isTrunkLocked =
                boolean(
                    SignalDefinitions.trunkLocked,
                    BODY_SIGNAL_MAX_AGE_MS,
                ) ?: placeholder.isTrunkLocked,
            isIgnitionOn =
                boolean(
                    SignalDefinitions.ignitionOn,
                    BODY_SIGNAL_MAX_AGE_MS,
                ) ?: placeholder.isIgnitionOn,
            isAccessoryPowerOn =
                boolean(
                    SignalDefinitions.accessoryPowerOn,
                    BODY_SIGNAL_MAX_AGE_MS,
                ) ?: placeholder.isAccessoryPowerOn,
            isPassengerAirbagDisabled =
                boolean(
                    SignalDefinitions.passengerAirbagDisabled,
                    SLOW_SIGNAL_MAX_AGE_MS,
                ) ?: placeholder.isPassengerAirbagDisabled,
            isTripComputerUpPressed =
                boolean(
                    SignalDefinitions.tripComputerUp,
                    BODY_SIGNAL_MAX_AGE_MS,
                ) ?: placeholder.isTripComputerUpPressed,
            isTripComputerDownPressed =
                boolean(
                    SignalDefinitions.tripComputerDown,
                    BODY_SIGNAL_MAX_AGE_MS,
                ) ?: placeholder.isTripComputerDownPressed,
            instrumentBacklightRaw =
                number(
                    SignalDefinitions.instrumentBacklightRaw,
                    SLOW_SIGNAL_MAX_AGE_MS,
                )?.roundToInt() ?: placeholder.instrumentBacklightRaw,
            steeringWheelAngleDegrees =
                number(
                    SignalDefinitions.steeringAngleDegrees,
                    FAST_SIGNAL_MAX_AGE_MS,
                )?.toFloat() ?: placeholder.steeringWheelAngleDegrees,
            steeringWheelAngularVelocityRaw =
                number(
                    SignalDefinitions.steeringAngularVelocityRaw,
                    FAST_SIGNAL_MAX_AGE_MS,
                )?.roundToInt() ?: placeholder.steeringWheelAngularVelocityRaw,
            wiperMode = wipers ?: placeholder.wiperMode,
        )
    }
}

private fun Int.toWiperModeOrNull(): WiperMode? =
    when (this) {
        0 -> WiperMode.OFF
        1 -> WiperMode.INTERMITTENT
        2 -> WiperMode.LOW
        3 -> WiperMode.HIGH
        else -> null
    }
