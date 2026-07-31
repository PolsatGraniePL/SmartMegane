package pl.polsatgranie.smartmegane.domain.vehicle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AntiStallAdvisorTest {
    @Test
    fun requestsThrottleThenAllowsSmoothReleaseWithDwell() {
        val advisor = AntiStallAdvisor()
        val lowRpm = vehicle(rpm = 900)
        advisor.update(lowRpm, roadPitchDegrees = 0f, nowMs = 0L)
        val lowAccepted = advisor.update(lowRpm, 0f, 300L)
        assertTrue(
            lowAccepted.status == AntiStallStatus.STALL_RISK ||
                lowAccepted.status == AntiStallStatus.ADD_THROTTLE,
        )

        val ready = vehicle(rpm = 1_300)
        var accepted = advisor.update(ready, 0f, 400L)
        for (time in 500L..1_600L step 100) {
            accepted = advisor.update(ready, 0f, time)
        }
        assertEquals(AntiStallStatus.RELEASE_SMOOTHLY, accepted.status)
        assertTrue(accepted.rpmReserve > 0)
    }

    private fun vehicle(rpm: Int) = VehicleState(
        powerState = VehiclePowerState.ENGINE_RUNNING,
        engineRpm = rpm,
        engineRpmPrecise = rpm.toDouble(),
        isEngineRpmSignalAvailable = true,
        isClutchPedalPressed = true,
        isClutchPedalSignalAvailable = true,
        isSpeedSignalAvailable = true,
        speedKphPrecise = 0.0,
        acceleratorPedalPercent = 10f,
        requestedEngineTorqueNm = 20,
    )
}
