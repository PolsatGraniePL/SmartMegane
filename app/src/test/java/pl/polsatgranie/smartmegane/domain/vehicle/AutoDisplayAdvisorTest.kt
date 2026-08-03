package pl.polsatgranie.smartmegane.domain.vehicle

import org.junit.Assert.assertEquals
import org.junit.Test

class AutoDisplayAdvisorTest {
    @Test
    fun brakingKeepsSpeedViewUntilZeroIsConfirmed() {
        val advisor = AutoDisplayAdvisor()
        assertEquals(AutoDisplayMode.SPEED, advisor.update(12.0, true, 0L))
        assertEquals(AutoDisplayMode.SPEED, advisor.update(4.0, true, 500L))
        assertEquals(AutoDisplayMode.SPEED, advisor.update(0.0, true, 600L))
        assertEquals(AutoDisplayMode.VEHICLE, advisor.update(0.0, true, 1_100L))
    }

    @Test
    fun quietCreepingShowsVehicleThenReturnsToSpeedAfterLongCreep() {
        val advisor = AutoDisplayAdvisor()
        advisor.update(0.0, true, 0L)
        advisor.update(0.0, true, 200L)
        advisor.update(2.0, true, 300L)

        assertEquals(AutoDisplayMode.VEHICLE, advisor.update(2.0, true, 900L))
        assertEquals(AutoDisplayMode.SPEED, advisor.update(2.0, true, 4_400L))
    }

    @Test
    fun unavailableSampleNeverForcesAViewChange() {
        val advisor = AutoDisplayAdvisor()
        advisor.update(20.0, true, 100L)

        assertEquals(AutoDisplayMode.SPEED, advisor.update(null, false, 5_000L))
    }
}
