package pl.polsatgranie.smartmegane.domain.vehicle

import kotlin.math.max
import kotlin.math.roundToInt

enum class RpmSuitabilityZone {
    UNAVAILABLE,
    TOO_LOW,
    LOW_MARGIN,
    OPTIMAL,
    HIGH_MARGIN,
    TOO_HIGH,
}

data class RpmSuitabilityState(
    val currentRpm: Int = 0,
    val estimatedCurrentGear: Int? = null,
    val optimalRange: IntRange = 1_350..2_150,
    val warningRange: IntRange = 1_100..2_650,
    val zone: RpmSuitabilityZone = RpmSuitabilityZone.UNAVAILABLE,
)

/** Operating-band indicator for the K9K 77 kW and six-speed TL4 drivetrain. */
object RpmSuitabilityAdvisor {
    fun calculate(state: VehicleState, guidance: GearGuidance): RpmSuitabilityState {
        val rpm = (state.engineRpmPrecise ?: state.engineRpm.toDouble())
            .coerceAtLeast(0.0)
            .roundToInt()
        val currentGear = guidance.estimate.forwardGear ?: guidance.estimate.lastStableGear
        if (!state.isEngineRpmSignalAvailable || state.powerState != VehiclePowerState.ENGINE_RUNNING) {
            return RpmSuitabilityState(currentRpm = rpm, estimatedCurrentGear = currentGear)
        }

        val acceleratorLoad = (state.acceleratorPedalPercent ?: 0f) / 100f
        val torqueLoad = (state.requestedEngineTorqueNm ?: 0)
            .coerceAtLeast(0) / MeganeIiK9kTl4001Profile.MAX_ENGINE_TORQUE_NM
        val load = max(acceleratorLoad.toDouble(), torqueLoad).coerceIn(0.0, 1.0)
        val optimalFirst = (1_300.0 + 420.0 * load).roundToInt()
        val optimalLast = (2_100.0 + 900.0 * load).roundToInt()
        val optimal = optimalFirst..optimalLast
        val warning = (optimalFirst - 260)..(optimalLast + 480)

        val preferred = guidance.preferredGear
        val requestedDirection = when {
            currentGear == null || preferred == null -> ShiftDirection.NONE
            preferred > currentGear -> ShiftDirection.UP
            preferred < currentGear -> ShiftDirection.DOWN
            else -> ShiftDirection.NONE
        }
        val zone = when {
            guidance.shiftDirection == ShiftDirection.DOWN -> RpmSuitabilityZone.TOO_LOW
            guidance.shiftDirection == ShiftDirection.UP -> RpmSuitabilityZone.TOO_HIGH
            requestedDirection == ShiftDirection.DOWN -> RpmSuitabilityZone.LOW_MARGIN
            requestedDirection == ShiftDirection.UP -> RpmSuitabilityZone.HIGH_MARGIN
            rpm < warning.first -> RpmSuitabilityZone.TOO_LOW
            rpm < optimal.first -> RpmSuitabilityZone.LOW_MARGIN
            rpm <= optimal.last -> RpmSuitabilityZone.OPTIMAL
            rpm <= warning.last -> RpmSuitabilityZone.HIGH_MARGIN
            else -> RpmSuitabilityZone.TOO_HIGH
        }
        val visualRanges = when (zone) {
            RpmSuitabilityZone.LOW_MARGIN ->
                ((rpm + 120)..(rpm + 650)) to ((rpm - 300)..(rpm + 900))
            RpmSuitabilityZone.HIGH_MARGIN ->
                ((rpm - 650)..(rpm - 120)) to ((rpm - 900)..(rpm + 300))
            RpmSuitabilityZone.TOO_LOW ->
                ((rpm + 400)..(rpm + 900)) to ((rpm + 80)..(rpm + 1_200))
            RpmSuitabilityZone.TOO_HIGH ->
                ((rpm - 900)..(rpm - 400)) to ((rpm - 1_200)..(rpm - 80))
            RpmSuitabilityZone.OPTIMAL,
            RpmSuitabilityZone.UNAVAILABLE,
            -> optimal to warning
        }
        return RpmSuitabilityState(
            currentRpm = rpm,
            estimatedCurrentGear = currentGear,
            optimalRange = visualRanges.first,
            warningRange = visualRanges.second,
            zone = zone,
        )
    }
}
