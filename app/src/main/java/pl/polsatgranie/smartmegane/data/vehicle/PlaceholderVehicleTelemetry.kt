package pl.polsatgranie.smartmegane.data.vehicle

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import pl.polsatgranie.smartmegane.domain.vehicle.VehicleState
import pl.polsatgranie.smartmegane.domain.vehicle.VehicleTelemetry
import pl.polsatgranie.smartmegane.domain.vehicle.WiperMode

/**
 * Temporary deterministic source used until the remaining Megane CAN frames are mapped.
 * Change [previewState] to exercise every dashboard state without a connected car.
 */
class PlaceholderVehicleTelemetry(
    previewState: VehicleState = parkedPreviewState,
) : VehicleTelemetry {
    private val mutableState = MutableStateFlow(previewState)
    override val state: StateFlow<VehicleState> = mutableState.asStateFlow()

    fun setPreviewState(state: VehicleState) {
        mutableState.value = state
    }

    companion object {
        val parkedPreviewState = VehicleState(
            speedKph = 0,
            engineRpm = 0,
            fuelLevelPercent = 0,
            coolantTemperatureCelsius = 0,
            odometerKm = 0,
            isParkingBrakeActive = false,
            isDriverSeatBeltWarningActive = false,
            arePositionLightsOn = false,
            isFrontLeftDoorOpen = false,
            isFrontLeftWindowOpen = false,
            isClutchPedalPressed = false,
            isTrunkOpen = false,
            isGlowPlugActive = false,
            areLowBeamLightsOn = false,
            areRearFogLightsOn = false,
            isAbsWarningActive = false,
            isEspWarningActive = false,
            isLeftTurnSignalOn = false,
            isRearLeftDoorOpen = false,
            areFrontFogLightsOn = false,
            areHighBeamLightsOn = false,
            isBrakePedalPressed = false,
            isRearRightDoorOpen = false,
            isRightTurnSignalOn = false,
            isStopWarningActive = false,
            isAirbagWarningActive = false,
            isEngineWarningActive = false,
            isRearRightWindowOpen = false,
            isFrontRightDoorOpen = false,
            isRearLeftWindowOpen = false,
            isFrontRightWindowOpen = false,
            isLowFuelWarningActive = false,
            isServiceWarningActive = false,
            isBrakeSystemWarningActive = false,
            isOilPressureWarningActive = false,
            isChargingSystemWarningActive = false,
            isCoolantOverheatWarningActive = false,
            steeringWheelAngleDegrees = null,
            wiperMode = WiperMode.OFF,
        )
    }
}
