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
    fun safeRangeMovesDownAsVehicleStartsRolling() {
        val stationary = SweetSpotCalculator.calculate(speedKph = 0, engineRpm = 1_300)
        val rolling = SweetSpotCalculator.calculate(speedKph = 15, engineRpm = 1_300)

        assertTrue(rolling.safeRange.first < stationary.safeRange.first)
        assertTrue(rolling.safeRange.last < stationary.safeRange.last)
    }

    @Test
    fun confidenceDropsOutsideClutchReleaseSpeeds() {
        val launch = SweetSpotCalculator.calculate(speedKph = 5, engineRpm = 1_400)
        val cruising = SweetSpotCalculator.calculate(speedKph = 70, engineRpm = 1_800)

        assertTrue(launch.confidence > cruising.confidence)
    }
}
