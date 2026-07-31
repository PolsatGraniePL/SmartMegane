package pl.polsatgranie.smartmegane.domain.vehicle

import org.junit.Assert.assertEquals
import org.junit.Test

class VehicleStateDeriverTest {
    private val deriver = VehicleStateDeriver()

    @Test
    fun distinguishesAllFourPowerStates() {
        assertEquals(
            VehiclePowerState.OFF,
            deriver.derive(VehicleState(), 2_000L, null).powerState,
        )
        assertEquals(
            VehiclePowerState.CAN_AWAKE,
            deriver.derive(VehicleState(), 2_000L, 1_900L).powerState,
        )
        assertEquals(
            VehiclePowerState.IGNITION_ON,
            deriver.derive(
                VehicleState(isIgnitionOn = true),
                2_000L,
                1_900L,
            ).powerState,
        )
        assertEquals(
            VehiclePowerState.ENGINE_RUNNING,
            deriver.derive(
                VehicleState(
                    isIgnitionOn = true,
                    engineRpm = 800,
                    isEngineRpmSignalAvailable = true,
                ),
                2_000L,
                1_900L,
            ).powerState,
        )
    }

}
