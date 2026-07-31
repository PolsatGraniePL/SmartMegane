package pl.polsatgranie.smartmegane.domain.vehicle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParkingSlopeAdvisorTest {
    @Test
    fun convertsFixedPhoneMountPoseToLevelVehicle() {
        assertEquals(0f, ParkingSlopeAdvisor.vehiclePitchDegrees(-45f) ?: Float.NaN, 0.001f)
        assertEquals(0f, ParkingSlopeAdvisor.vehicleRollDegrees(0f) ?: Float.NaN, 0.001f)
        assertEquals(5f, ParkingSlopeAdvisor.vehiclePitchDegrees(-40f) ?: Float.NaN, 0.001f)
    }

    @Test
    fun mapsFlatMediumAndSteepSlopeToSafeGuidance() {
        assertEquals(
            ParkingSlopeLevel.FLAT,
            ParkingSlopeAdvisor.evaluate(
                phonePitchDegrees = 0f,
                phoneRollDegrees = -45f,
                parkingBrakeApplied = true,
            ).level,
        )
        val uphill = ParkingSlopeAdvisor.evaluate(
            phonePitchDegrees = 0f,
            phoneRollDegrees = -39f,
            parkingBrakeApplied = true,
        )
        assertEquals(ParkingSlopeLevel.GEAR_RECOMMENDED, uphill.level)
        assertEquals(ParkingGearAdvice.FIRST, uphill.recommendedGear)

        val downhill = ParkingSlopeAdvisor.evaluate(
            phonePitchDegrees = 0f,
            phoneRollDegrees = -61f,
            parkingBrakeApplied = false,
        )
        assertEquals(ParkingSlopeLevel.STEEP, downhill.level)
        assertEquals(ParkingGearAdvice.REVERSE, downhill.recommendedGear)
        assertTrue(!downhill.isParkingBrakeApplied)

        val sideways = ParkingSlopeAdvisor.evaluate(
            phonePitchDegrees = 6f,
            phoneRollDegrees = -45f,
            parkingBrakeApplied = true,
        )
        assertEquals(ParkingSlopeLevel.FLAT, sideways.longitudinalLevel)
        assertEquals(ParkingSlopeLevel.GEAR_RECOMMENDED, sideways.lateralLevel)
    }
}
