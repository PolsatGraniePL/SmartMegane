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

data class VehicleState(
    val speedKph: Int = 0,
    val speedKphPrecise: Double? = null,
    val isSpeedSignalAvailable: Boolean = false,
    val engineRpm: Int = 0,
    val engineRpmPrecise: Double? = null,
    val isEngineRpmSignalAvailable: Boolean = false,
    val kinematicsSampleTimestampMs: Long? = null,
    val areRpmSourcesConsistent: Boolean = true,
    val fuelLevelPercent: Int = 0,
    val coolantTemperatureCelsius: Int = 0,
    val outsideTemperatureCelsius: Int? = null,
    val odometerKm: Long = 0,
    val distanceSinceStartMeters: Double? = null,
    val fuelUsedSinceStartLiters: Double? = null,
    val vehicleAgeMinutes: Long? = null,
    val acceleratorPedalPercent: Float? = null,
    val requestedEngineTorqueNm: Int? = null,
    val isStopWarningActive: Boolean = false,
    val isOilPressureWarningActive: Boolean = false,
    val isCoolantOverheatWarningActive: Boolean = false,
    val isBrakeSystemWarningActive: Boolean = false,
    val isParkingBrakeActive: Boolean = false,
    val isChargingSystemWarningActive: Boolean = false,
    val isAirbagWarningActive: Boolean = false,
    val isServiceWarningActive: Boolean = false,
    val isEngineWarningActive: Boolean = false,
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
    val isTrunkLocked: Boolean = false,
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
        VehicleIndicator.ESP -> isEspWarningActive
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
    }

fun VehicleState.hasCriticalWarning(): Boolean =
    VehicleIndicator.entries.any {
        it.severity == IndicatorSeverity.CRITICAL && isActive(it)
    }

fun VehicleState.hasNonCriticalWarning(): Boolean =
    VehicleIndicator.entries.any {
        it.severity == IndicatorSeverity.WARNING && isActive(it)
    }
