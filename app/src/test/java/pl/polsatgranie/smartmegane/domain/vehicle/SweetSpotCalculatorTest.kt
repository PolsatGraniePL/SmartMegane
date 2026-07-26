package pl.polsatgranie.smartmegane.domain.vehicle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SweetSpotCalculatorTest {
    @Test
    fun stationaryDieselAtIdleIsInStallZone() {
        val result = SweetSpotCalculator.calculate(speedKph = 0, engineRpm = 820)

        assertEquals(ClutchReleaseZone.STALL, result.zone)
        assertTrue(result.safeRange.first > result.currentRpm)
    }

    @Test
    fun stationaryDieselAtFifteenHundredRpmIsInSweetSpot() {
        val result = SweetSpotCalculator.calculate(speedKph = 0, engineRpm = 1_500)

        assertEquals(ClutchReleaseZone.SWEET_SPOT, result.zone)
    }

    @Test
    fun launchTargetDropsAtWalkingSpeedThenTracksFirstGearSynchronisation() {
        val stationary = SweetSpotCalculator.calculate(speedKph = 0, engineRpm = 1_300)
        val walking = SweetSpotCalculator.calculate(speedKph = 5, engineRpm = 1_300)
        val rolling = SweetSpotCalculator.calculate(speedKph = 10, engineRpm = 1_300)

        assertTrue(walking.safeRange.first < stationary.safeRange.first)
        assertTrue(rolling.safeRange.first > walking.safeRange.first)
    }

    @Test
    fun confidenceDropsOutsideClutchReleaseSpeeds() {
        val launch = SweetSpotCalculator.calculate(speedKph = 5, engineRpm = 1_400)
        val cruising = SweetSpotCalculator.calculate(speedKph = 70, engineRpm = 1_800)

        assertTrue(launch.confidence > cruising.confidence)
    }

    @Test
    fun highRpmIsJerkRiskRatherThanStallRisk() {
        val result = SweetSpotCalculator.calculate(speedKph = 0, engineRpm = 2_500)

        assertEquals(ClutchReleaseZone.JERK, result.zone)
    }

    @Test
    fun clutchPressedRaisesConfidenceWithoutChangingTheCalculatedRange() {
        val pressed = SweetSpotCalculator.calculate(
            speedKph = 3.2,
            engineRpm = 1_250.0,
            isClutchPressed = true,
        )
        val released = SweetSpotCalculator.calculate(
            speedKph = 3.2,
            engineRpm = 1_250.0,
            isClutchPressed = false,
        )

        assertEquals(pressed.safeRange, released.safeRange)
        assertTrue(pressed.confidence > released.confidence)
    }

    @Test
    fun firstGearSynchronisationUsesTl4001FactoryDevelopment() {
        val result = SweetSpotCalculator.calculate(
            speedKph = 10.0,
            engineRpm = 1_199.0,
            isClutchPressed = true,
        )

        assertEquals(SweetSpotMode.LAUNCH, result.mode)
        assertTrue(1_199 in result.safeRange)
        assertEquals(1_199, result.targetRpm)
    }

    @Test
    fun movingSweetSpotTargetsPreferredGearRevMatch() {
        val result = SweetSpotCalculator.calculate(
            speedKph = 48.70,
            engineRpm = 1_000.0,
            isClutchPressed = true,
            preferredGear = 6,
            guidanceConfidence = 0.9f,
        )

        assertEquals(SweetSpotMode.REV_MATCH, result.mode)
        assertEquals(1_000, result.targetRpm)
        assertEquals(ClutchReleaseZone.SWEET_SPOT, result.zone)
        assertTrue(result.confidence > 0.9f)
    }
}
