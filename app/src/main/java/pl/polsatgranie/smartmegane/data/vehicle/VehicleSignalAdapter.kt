package pl.polsatgranie.smartmegane.data.vehicle

import pl.polsatgranie.smartmegane.domain.signal.SignalDefinitions
import pl.polsatgranie.smartmegane.domain.signal.SignalKey
import pl.polsatgranie.smartmegane.domain.signal.SignalState
import pl.polsatgranie.smartmegane.domain.signal.SignalValue
import pl.polsatgranie.smartmegane.domain.vehicle.VehicleState
import pl.polsatgranie.smartmegane.domain.vehicle.WiperMode

/**
 * Bridges already decoded CAN signals into the typed vehicle API.
 *
 * The steering signal is currently marked as "raw" in SignalDefinitions. Its scale,
 * offset and direction should be calibrated there; keeping that conversion at the
 * decoding boundary means the UI-facing API remains independent of CAN layout.
 */
class VehicleSignalAdapter {
    fun merge(
        placeholder: VehicleState,
        signals: SignalState,
    ): VehicleState {
        val wipers = (signals.get(SignalDefinitions.wipersMode) as? SignalValue.Enum)
            ?.code
            ?.toWiperModeOrNull()
        val steeringAngle =
            (signals.get(SignalDefinitions.steeringAngleRaw) as? SignalValue.Number)
                ?.value
                ?.toFloat()
        fun booleanValue(key: SignalKey) =
            (signals.get(key) as? SignalValue.Bool)?.value

        return placeholder.copy(
            wiperMode = wipers ?: placeholder.wiperMode,
            steeringWheelAngleDegrees =
                steeringAngle ?: placeholder.steeringWheelAngleDegrees,
            isBrakePedalPressed =
                booleanValue(SignalDefinitions.brakePressed)
                    ?: placeholder.isBrakePedalPressed,
            isClutchPedalPressed =
                booleanValue(SignalDefinitions.clutchPressed)
                    ?: placeholder.isClutchPedalPressed,
            isFrontLeftDoorOpen =
                booleanValue(SignalDefinitions.doorFrontLeft)
                    ?: placeholder.isFrontLeftDoorOpen,
            isFrontRightDoorOpen =
                booleanValue(SignalDefinitions.doorFrontRight)
                    ?: placeholder.isFrontRightDoorOpen,
            isRearLeftDoorOpen =
                booleanValue(SignalDefinitions.doorRearLeft)
                    ?: placeholder.isRearLeftDoorOpen,
            isRearRightDoorOpen =
                booleanValue(SignalDefinitions.doorRearRight)
                    ?: placeholder.isRearRightDoorOpen,
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
