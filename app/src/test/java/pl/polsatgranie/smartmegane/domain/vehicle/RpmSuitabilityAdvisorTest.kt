package pl.polsatgranie.smartmegane.domain.vehicle

import org.junit.Assert.assertEquals
import org.junit.Test

class RpmSuitabilityAdvisorTest {
    private val runningState = VehicleState(
        powerState = VehiclePowerState.ENGINE_RUNNING,
        engineRpm = 1_650,
        engineRpmPrecise = 1_650.0,
        isEngineRpmSignalAvailable = true,
        acceleratorPedalPercent = 18f,
    )

    @Test
    fun marksMatchedDieselOperatingBandAsOptimal() {
        val result = RpmSuitabilityAdvisor.calculate(
            runningState,
            guidance(currentGear = 3, preferredGear = 3),
        )
        assertEquals(RpmSuitabilityZone.OPTIMAL, result.zone)
        assertEquals(3, result.estimatedCurrentGear)
    }

    @Test
    fun distinguishesApproachingAndConfirmedUpshift() {
        val approaching = RpmSuitabilityAdvisor.calculate(
            runningState,
            guidance(currentGear = 2, preferredGear = 3),
        )
        assertEquals(RpmSuitabilityZone.HIGH_MARGIN, approaching.zone)

        val confirmed = RpmSuitabilityAdvisor.calculate(
            runningState,
            guidance(currentGear = 2, preferredGear = 3, shift = ShiftDirection.UP),
        )
        assertEquals(RpmSuitabilityZone.TOO_HIGH, confirmed.zone)
    }

    private fun guidance(
        currentGear: Int,
        preferredGear: Int,
        shift: ShiftDirection = ShiftDirection.NONE,
    ) = GearGuidance(
        estimate = GearEstimate(
            forwardGear = currentGear,
            lastStableGear = currentGear,
            status = GearEstimateStatus.COUPLED,
            confidence = 0.9f,
        ),
        preferredGear = preferredGear,
        shiftDirection = shift,
    )
}
