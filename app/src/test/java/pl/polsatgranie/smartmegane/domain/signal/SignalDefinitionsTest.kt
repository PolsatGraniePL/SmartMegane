package pl.polsatgranie.smartmegane.domain.signal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.polsatgranie.smartmegane.data.can.CanFrame

class SignalDefinitionsTest {
    private val mapper = SignalMapper(SignalDefinitions.specs)

    @Test
    fun decodesCanonicalRpmPedalsSpeedAndSteering() {
        var state = apply(
            frame(
                0x181,
                0x19, 0x00, 0x10, 0x22, 0x33, 0x09, 0x44, 0x02,
            ),
        )
        state = apply(
            frame(
                0x354,
                0x04, 0xD2, 0x00, 0x7B, 0x40, 0x00, 0x00, 0x00,
            ),
            state,
        )
        state = apply(
            frame(
                0x0C2,
                0x80, 0x9C, 0x80, 0x0A, 0x10, 0x00,
            ),
            state,
        )

        assertEquals(800.0, state.number(SignalDefinitions.engineRpm), 0.0)
        assertEquals(16.0, state.number(SignalDefinitions.acceleratorPedalRaw), 0.0)
        assertTrue(state.boolean(SignalDefinitions.clutchPressed))
        assertTrue(state.boolean(SignalDefinitions.brakePressed))
        assertEquals(12.34, state.number(SignalDefinitions.vehicleSpeedKph), 0.0001)
        assertEquals(12.3, state.number(SignalDefinitions.distanceSinceStart), 0.0001)
        assertTrue(state.boolean(SignalDefinitions.asrEspDisabled))
        assertEquals(15.6, state.number(SignalDefinitions.steeringAngleDegrees), 0.0001)
        assertEquals(
            10.0,
            state.number(SignalDefinitions.steeringAngularVelocityRaw),
            0.0,
        )
        assertTrue(state.boolean(SignalDefinitions.steeringDataValid))
    }

    @Test
    fun usesOnlyOneCanSourceForEveryDuplicatedVehicleValue() {
        val expectedSources = mapOf(
            SignalDefinitions.engineRpm to 0x181,
            SignalDefinitions.vehicleSpeedKph to 0x354,
            SignalDefinitions.coolantTemperature to 0x551,
            SignalDefinitions.odometer to 0x5C5,
            SignalDefinitions.brakePressed to 0x181,
            SignalDefinitions.clutchPressed to 0x181,
            SignalDefinitions.reverseGear to 0x60D,
            SignalDefinitions.wipersMode to 0x35D,
        )

        expectedSources.forEach { (key, canId) ->
            val matching = SignalDefinitions.specs.filter { it.key == key }
            assertEquals(key.id, 1, matching.size)
            assertEquals(key.id, canId, matching.single().canId)
        }
    }

    @Test
    fun decodesCanonicalOdometerAndCorrectParkingBrakeBitFrom5C5() {
        val state = apply(
            frame(
                0x5C5,
                0x04, 0x03, 0xF0, 0x0A, 0x00, 0x00, 0x00, 0x1D,
            ),
        )

        assertEquals(258_058.0, state.number(SignalDefinitions.odometer), 0.0)
        assertTrue(state.boolean(SignalDefinitions.parkingBrake))
        assertEquals(0x1D.toDouble(), state.number(SignalDefinitions.serviceStatusRaw), 0.0)
    }

    @Test
    fun decodesThermalAndFuelFieldsFrom551() {
        val state = apply(
            frame(
                0x551,
                0x62, 0x26, 0x6A, 0x80, 0xFF, 0x72, 0x00, 0x26,
            ),
        )

        assertEquals(58.0, state.number(SignalDefinitions.coolantTemperature), 0.0)
        assertEquals(
            38.0 / 12_500.0,
            state.number(SignalDefinitions.fuelUsedSinceStart),
            0.000_000_1,
        )
        assertEquals(0x72.toDouble(), state.number(SignalDefinitions.fuelLevelRaw), 0.0)
    }

    @Test
    fun decodesConfirmedBodyMapFrom60D() {
        val state = apply(
            frame(
                0x60D,
                0xFE, 0x6F, 0x34, 0x00, 0x3C, 0x62, 0x11, 0x03,
            ),
        )

        listOf(
            SignalDefinitions.trunkOpen,
            SignalDefinitions.doorFrontLeft,
            SignalDefinitions.doorFrontRight,
            SignalDefinitions.doorRearLeft,
            SignalDefinitions.doorRearRight,
            SignalDefinitions.positionLights,
            SignalDefinitions.lowBeamLights,
            SignalDefinitions.highBeamLights,
            SignalDefinitions.frontFogLights,
            SignalDefinitions.rearFogLights,
            SignalDefinitions.leftTurnSignal,
            SignalDefinitions.rightTurnSignal,
            SignalDefinitions.ignitionOn,
            SignalDefinitions.accessoryPowerOn,
            SignalDefinitions.doorsLocked,
            SignalDefinitions.trunkLocked,
            SignalDefinitions.reverseGear,
            SignalDefinitions.tripComputerUp,
            SignalDefinitions.tripComputerDown,
        ).forEach { key -> assertTrue(key.id, state.boolean(key)) }
        assertEquals(20.0, state.number(SignalDefinitions.outsideTemperature), 0.0)
    }

    @Test
    fun decodesCapturedThreeBitWiperCodesAndRearDefrost() {
        val expected = mapOf(
            0x00 to 0,
            0x20 to 1,
            0xC0 to 6,
            0xE0 to 7,
        )

        expected.forEach { (encoded, code) ->
            val state = apply(
                frame(
                    0x35D,
                    0x06, 0x00, encoded, 0x00, 0x00, 0x01, 0x00, 0x00,
                ),
            )
            val mode = state.get(SignalDefinitions.wipersMode) as SignalValue.Enum
            assertEquals(code, mode.code)
            assertTrue(state.boolean(SignalDefinitions.rearDefrostOn))
            assertTrue(state.boolean(SignalDefinitions.engineRunning))
        }
    }

    @Test
    fun rejectsInvalidRpmSpeedSteeringAndReverseFrames() {
        var state = apply(frame(0x181, 0x19, 0x00, 0, 0, 0, 0, 0, 0))
        state = apply(frame(0x354, 0xFF, 0xFF, 0, 0, 0, 0, 0x10, 0), state)
        state = apply(frame(0x0C2, 0x80, 0x9C, 0, 0, 0, 0), state)
        state = apply(frame(0x60D, 0, 0, 0, 0, 0, 0, 0x2F, 0), state)

        assertNull(state.get(SignalDefinitions.engineRpm))
        assertNull(state.get(SignalDefinitions.vehicleSpeedKph))
        assertNull(state.get(SignalDefinitions.steeringAngleDegrees))
        assertNull(state.get(SignalDefinitions.reverseGear))
    }

    @Test
    fun bodyBitsRemainFalseWhenTheFrameDoesNotSetThem() {
        val state = apply(frame(0x60D, 0, 0, 0, 0, 40, 40, 0x01, 0))

        assertFalse(state.boolean(SignalDefinitions.doorFrontLeft))
        assertFalse(state.boolean(SignalDefinitions.leftTurnSignal))
    }

    private fun apply(
        frame: CanFrame,
        state: SignalState = SignalState(),
    ): SignalState = mapper.applyFrame(state, frame)

    private fun frame(
        id: Int,
        vararg data: Int,
    ): CanFrame = CanFrame(
        id = id,
        dlc = data.size,
        data = ByteArray(8) { index -> data.getOrElse(index) { 0 }.toByte() },
        timestampMs = 1_000,
    )

    private fun SignalState.number(key: SignalKey): Double =
        (get(key) as SignalValue.Number).value

    private fun SignalState.boolean(key: SignalKey): Boolean =
        (get(key) as SignalValue.Bool).value
}
