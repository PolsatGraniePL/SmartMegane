package pl.polsatgranie.smartmegane.domain.vehicle

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleStateTest {
    @Test
    fun criticalIndicatorActivatesCriticalAmbientAlert() {
        val state = VehicleState(isOilPressureWarningActive = true)

        assertTrue(state.isActive(VehicleIndicator.OIL))
        assertTrue(state.hasCriticalWarning())
        assertFalse(state.hasNonCriticalWarning())
    }

    @Test
    fun serviceIndicatorActivatesWarningAmbientAlert() {
        val state = VehicleState(isServiceWarningActive = true)

        assertTrue(state.isActive(VehicleIndicator.SERVICE))
        assertFalse(state.hasCriticalWarning())
        assertTrue(state.hasNonCriticalWarning())
    }
}
