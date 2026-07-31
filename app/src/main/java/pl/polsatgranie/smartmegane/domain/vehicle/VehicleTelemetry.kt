package pl.polsatgranie.smartmegane.domain.vehicle

import kotlinx.coroutines.flow.StateFlow

/**
 * Typed, UI-facing API of the car.
 *
 * CAN IDs, bytes and bit masks deliberately do not leak into this layer. A future
 * CanVehicleTelemetry implementation can translate SignalState into this model
 * without changing a single composable.
 */
interface VehicleTelemetry {
    val state: StateFlow<VehicleState>
}

enum class WiperMode {
    OFF,
    INTERMITTENT,
    LOW,
    HIGH,
}

enum class VehiclePowerState {
    OFF,
    CAN_AWAKE,
    IGNITION_ON,
    ENGINE_RUNNING,
}

data class VehicleState(
    val powerState: VehiclePowerState = VehiclePowerState.OFF,
    val isCanBusActive: Boolean = false,
    val lastCanFrameTimestampMs: Long? = null,
    val speedKph: Int = 0,
    val speedKphPrecise: Double? = null,
    val isSpeedSignalAvailable: Boolean = false,
    val engineRpm: Int = 0,
    val engineRpmPrecise: Double? = null,
    val isEngineRpmSignalAvailable: Boolean = false,
    val kinematicsSampleTimestampMs: Long? = null,
    val fuelLevelPercent: Int = 0,
    val fuelLevelEstimatedPercent: Float? = null,
    val fuelLevelRaw: Int? = null,
    val isFuelLevelSignalAvailable: Boolean = false,
    val coolantTemperatureCelsius: Int = 0,
    val isCoolantTemperatureSignalAvailable: Boolean = false,
    val outsideTemperatureCelsius: Int? = null,
    val odometerKm: Long = 0,
    val isOdometerSignalAvailable: Boolean = false,
    val distanceSinceStartMeters: Double? = null,
    val fuelUsedSinceStartLiters: Double? = null,
    val fuelCounterSampleTimestampMs: Long? = null,
    val vehicleAgeMinutes: Long? = null,
    val acceleratorPedalPercent: Float? = null,
    val requestedEngineTorqueNm: Int? = null,
    val isEngineRunning: Boolean = false,
    val isEngineDataValid: Boolean = false,
    val isSteeringDataValid: Boolean = false,
    val isClusterNetworkActive: Boolean = false,
    val isRearDefrostCommandActive: Boolean = false,
    val isEspAsrDisabled: Boolean = false,
    val isAsrEspButtonPressed: Boolean = false,
    val wheelPairARightSpeedKph: Double? = null,
    val wheelPairALeftSpeedKph: Double? = null,
    val wheelPairBRightSpeedKph: Double? = null,
    val wheelPairBLeftSpeedKph: Double? = null,
    val longitudinalAccelerationRaw: Int? = null,
    val lateralAccelerationRaw: Int? = null,
    val yawRateRaw: Int? = null,
    val bodySensorRaw: Int? = null,
    val bodyStatusRaw: Int? = null,
    val serviceStatusRaw: Int? = null,
    val odometerStatusRaw: Int? = null,
    val isStopWarningActive: Boolean = false,
    val isOilPressureWarningActive: Boolean = false,
    val isCoolantOverheatWarningActive: Boolean = false,
    val isBrakeSystemWarningActive: Boolean = false,
    val isParkingBrakeActive: Boolean = false,
    val isChargingSystemWarningActive: Boolean = false,
    val isAirbagWarningActive: Boolean = false,
    val isServiceWarningActive: Boolean = false,
    val isEngineWarningActive: Boolean = false,
    val isElectronicFaultActive: Boolean = false,
    val isAbsWarningActive: Boolean = false,
    val isEspWarningActive: Boolean = false,
    val isLowFuelWarningActive: Boolean = false,
    val isGlowPlugActive: Boolean = false,
    val isDriverSeatBeltWarningActive: Boolean = false,
    val arePositionLightsOn: Boolean = false,
    val areLowBeamLightsOn: Boolean = false,
    val areHighBeamLightsOn: Boolean = false,
    val areFrontFogLightsOn: Boolean = false,
    val areRearFogLightsOn: Boolean = false,
    val isLeftTurnSignalOn: Boolean = false,
    val isRightTurnSignalOn: Boolean = false,
    val areHazardLightsOn: Boolean = false,
    val isFrontLeftDoorOpen: Boolean = false,
    val isFrontRightDoorOpen: Boolean = false,
    val isRearLeftDoorOpen: Boolean = false,
    val isRearRightDoorOpen: Boolean = false,
    val isTrunkOpen: Boolean = false,
    val isFrontLeftWindowOpen: Boolean = false,
    val isFrontRightWindowOpen: Boolean = false,
    val isRearLeftWindowOpen: Boolean = false,
    val isRearRightWindowOpen: Boolean = false,
    val isBrakePedalPressed: Boolean = false,
    val isBrakePedalSignalAvailable: Boolean = false,
    val isClutchPedalPressed: Boolean = false,
    val isClutchPedalSignalAvailable: Boolean = false,
    val isReverseGearEngaged: Boolean = false,
    val isReverseGearSignalAvailable: Boolean = false,
    val areDoorsLocked: Boolean = false,
    val isDoorLockSignalAvailable: Boolean = false,
    val isTrunkLocked: Boolean = false,
    val isTrunkLockSignalAvailable: Boolean = false,
    val isIgnitionOn: Boolean = false,
    val isAccessoryPowerOn: Boolean = false,
    val isPassengerAirbagDisabled: Boolean = false,
    val isTripComputerUpPressed: Boolean = false,
    val isTripComputerDownPressed: Boolean = false,
    val instrumentBacklightRaw: Int? = null,
    val steeringWheelAngleDegrees: Float? = null,
    val steeringWheelAngularVelocityRaw: Int? = null,
    val wiperMode: WiperMode = WiperMode.OFF,
)

enum class IndicatorSeverity {
    CRITICAL,
    WARNING,
    INFORMATION,
}

enum class VehicleIndicator(
    val shortLabel: String,
    val description: String,
    val severity: IndicatorSeverity,
) {
    STOP("STOP", "Poważna usterka", IndicatorSeverity.CRITICAL),
    OIL("OIL", "Ciśnienie oleju", IndicatorSeverity.CRITICAL),
    COOLANT("TEMP", "Temperatura płynu", IndicatorSeverity.CRITICAL),
    BRAKES("BRAKE", "Układ hamulcowy", IndicatorSeverity.CRITICAL),
    PARKING_BRAKE("P", "Hamulec ręczny", IndicatorSeverity.CRITICAL),
    CHARGING("±", "Układ ładowania", IndicatorSeverity.CRITICAL),
    AIRBAG("AIR", "Poduszka powietrzna", IndicatorSeverity.CRITICAL),
    SERVICE("SERVICE", "Przegląd / usterka", IndicatorSeverity.WARNING),
    ENGINE("CHECK", "Silnik / wtrysk", IndicatorSeverity.WARNING),
    ABS("ABS", "Układ ABS", IndicatorSeverity.WARNING),
    ESP("ESP", "Stabilizacja toru", IndicatorSeverity.WARNING),
    LOW_FUEL("FUEL", "Rezerwa paliwa", IndicatorSeverity.WARNING),
    GLOW_PLUG("COIL", "Świece żarowe", IndicatorSeverity.WARNING),
    SEAT_BELT("BELT", "Pas bezpieczeństwa", IndicatorSeverity.WARNING),
    WIPERS("WIPER", "Wycieraczki", IndicatorSeverity.INFORMATION),
    POSITION_LIGHTS("POS", "Światła pozycyjne", IndicatorSeverity.INFORMATION),
    LOW_BEAM("LOW", "Światła mijania", IndicatorSeverity.INFORMATION),
    HIGH_BEAM("HIGH", "Światła drogowe", IndicatorSeverity.INFORMATION),
    FRONT_FOG("F.FOG", "Przednie przeciwmgielne", IndicatorSeverity.INFORMATION),
    REAR_FOG("R.FOG", "Tylne przeciwmgielne", IndicatorSeverity.INFORMATION),
    LEFT_TURN("‹", "Lewy kierunkowskaz", IndicatorSeverity.INFORMATION),
    RIGHT_TURN("›", "Prawy kierunkowskaz", IndicatorSeverity.INFORMATION),
    HAZARD("HAZARD", "Światła awaryjne", IndicatorSeverity.INFORMATION),
    UNLOCKED("UNLOCK", "Samochód odryglowany", IndicatorSeverity.INFORMATION),
    ELECTRONIC_FAULT(
        "ELEC",
        "Usterka elektroniki",
        IndicatorSeverity.WARNING,
    ),
    PASSENGER_AIRBAG_OFF(
        "AIR OFF",
        "Poduszka pasażera wyłączona",
        IndicatorSeverity.WARNING,
    ),
    REAR_DEFROST(
        "DEFROST",
        "Polecenie ogrzewania tylnej szyby",
        IndicatorSeverity.INFORMATION,
    ),
}

fun VehicleState.isActive(indicator: VehicleIndicator): Boolean =
    when (indicator) {
        VehicleIndicator.STOP -> isStopWarningActive
        VehicleIndicator.OIL -> isOilPressureWarningActive
        VehicleIndicator.COOLANT -> isCoolantOverheatWarningActive
        VehicleIndicator.BRAKES -> isBrakeSystemWarningActive
        VehicleIndicator.PARKING_BRAKE -> isParkingBrakeActive
        VehicleIndicator.CHARGING -> isChargingSystemWarningActive
        VehicleIndicator.AIRBAG -> isAirbagWarningActive
        VehicleIndicator.SERVICE -> isServiceWarningActive
        VehicleIndicator.ENGINE -> isEngineWarningActive
        VehicleIndicator.ABS -> isAbsWarningActive
        VehicleIndicator.ESP -> isEspWarningActive || isEspAsrDisabled
        VehicleIndicator.LOW_FUEL -> isLowFuelWarningActive
        VehicleIndicator.GLOW_PLUG -> isGlowPlugActive
        VehicleIndicator.SEAT_BELT -> isDriverSeatBeltWarningActive
        VehicleIndicator.WIPERS -> wiperMode != WiperMode.OFF
        VehicleIndicator.POSITION_LIGHTS -> arePositionLightsOn
        VehicleIndicator.LOW_BEAM -> areLowBeamLightsOn
        VehicleIndicator.HIGH_BEAM -> areHighBeamLightsOn
        VehicleIndicator.FRONT_FOG -> areFrontFogLightsOn
        VehicleIndicator.REAR_FOG -> areRearFogLightsOn
        VehicleIndicator.LEFT_TURN -> isLeftTurnSignalOn
        VehicleIndicator.RIGHT_TURN -> isRightTurnSignalOn
        VehicleIndicator.HAZARD -> areHazardLightsOn
        VehicleIndicator.UNLOCKED ->
            powerState != VehiclePowerState.OFF &&
                (
                    (isDoorLockSignalAvailable && !areDoorsLocked) ||
                        (isTrunkLockSignalAvailable && !isTrunkLocked)
                    )
        VehicleIndicator.ELECTRONIC_FAULT -> isElectronicFaultActive
        VehicleIndicator.PASSENGER_AIRBAG_OFF -> isPassengerAirbagDisabled
        VehicleIndicator.REAR_DEFROST -> isRearDefrostCommandActive
    }

fun VehicleState.hasCriticalWarning(): Boolean =
    VehicleIndicator.entries.any {
        it.severity == IndicatorSeverity.CRITICAL && isActive(it)
    }

fun VehicleState.hasNonCriticalWarning(): Boolean =
    VehicleIndicator.entries.any {
        it.severity == IndicatorSeverity.WARNING && isActive(it)
    }
