package pl.polsatgranie.smartmegane.domain.signal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.polsatgranie.smartmegane.data.can.CanFrame

class SignalDefinitionsTest {
    private val mapper = SignalMapper(SignalDefinitions.specs)

    @Test
    fun decodesConfirmedRpmPedalsSpeedAndSteering() {
        var state = apply(
            frame(
                0x181,
                0x19, 0x00, 0x00, 0x10, 0x00, 0x09, 0x00, 0x00,
            ),
        )
        state = apply(
            frame(
                0x1F9,
                0x00, 0x00, 0x19, 0x00, 0x00, 0x00, 0x00, 0x00,
            ),
            state,
        )
        state = apply(
            frame(
                0x354,
                0x04, 0xD2, 0x00, 0x7B, 0x10, 0x00, 0x00, 0x00,
            ),
            state,
        )
        state = apply(
            frame(
                0x0C2,
                0x80, 0x9C, 0x80, 0x0A, 0x00, 0x00, 0x00, 0x00,
            ),
            state,
        )

        assertEquals(800.0, state.number(SignalDefinitions.engineRpmPrimary), 0.0)
        assertEquals(800.0, state.number(SignalDefinitions.engineRpmSecondary), 0.0)
        assertEquals(16.0, state.number(SignalDefinitions.acceleratorPedalRaw), 0.0)
        assertTrue(state.boolean(SignalDefinitions.clutchPressed181))
        assertTrue(state.boolean(SignalDefinitions.brakePressed181))
        assertEquals(12.34, state.number(SignalDefinitions.vehicleSpeedKph), 0.0001)
        assertEquals(12.3, state.number(SignalDefinitions.distanceSinceStart), 0.0001)
        assertTrue(state.boolean(SignalDefinitions.brakePressed354))
        assertEquals(15.6, state.number(SignalDefinitions.steeringAngleDegrees), 0.0001)
        assertEquals(
            10.0,
            state.number(SignalDefinitions.steeringAngularVelocityRaw),
            0.0,
        )
    }

    @Test
    fun decodesAllThreeOdometerPackingsToTheSameValue() {
        var state = apply(
            frame(
                0x5FD,
                0x3F, 0x00, 0xA0, 0x00, 0x00, 0x00, 0x00, 0x00,
            ),
        )
        state = apply(
            frame(
                0x5C5,
                0x08, 0x03, 0xF0, 0x0A, 0x00, 0x00, 0x00, 0x00,
            ),
            state,
        )
        state = apply(
            frame(
                0x715,
                0x03, 0xF0, 0x0A, 0x00, 0x00, 0x00, 0x00, 0x00,
            ),
            state,
        )

        assertEquals(258_058.0, state.number(SignalDefinitions.odometer5FD), 0.0)
        assertEquals(258_058.0, state.number(SignalDefinitions.odometer5C5), 0.0)
        assertEquals(258_058.0, state.number(SignalDefinitions.odometer715), 0.0)
        assertTrue(state.boolean(SignalDefinitions.parkingBrake))
    }

    @Test
    fun decodesCoolantAndSingleByteFuelCounterFrom551() {
        val state = apply(
            frame(
                0x551,
                0x62, 0x26, 0x6A, 0x00, 0xFF, 0x72, 0x00,
            ),
        )

        assertEquals(58.0, state.number(SignalDefinitions.coolantTemperature551), 0.0)
        assertEquals(
            38.0 / 12_500.0,
            state.number(SignalDefinitions.fuelUsedSinceStart),
            0.000_000_1,
        )
    }

    @Test
    fun decodesCandidateBodyMapFrom60D() {
        val state = apply(
            frame(
                0x60D,
                0xFE, 0x6F, 0x34, 0x00, 0x3C, 0x62, 0x10, 0x03,
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
            SignalDefinitions.reverseGear60D,
            SignalDefinitions.tripComputerUp,
            SignalDefinitions.tripComputerDown,
        ).forEach { key -> assertTrue(key.id, state.boolean(key)) }
        assertEquals(20.0, state.number(SignalDefinitions.outsideTemperature), 0.0)
        assertEquals(58.0, state.number(SignalDefinitions.coolantTemperature60D), 0.0)
    }

    @Test
    fun migratedWiperFrameUsesCorrected35DIdAndKeepsFourModes() {
        val spec = SignalDefinitions.specs.single {
            it.key == SignalDefinitions.wipersMode
        }

        assertEquals(0x35D, spec.canId)
        listOf(0x00, 0x40, 0x80, 0xC0).forEachIndexed { expected, encoded ->
            val state = apply(
                frame(
                    0x35D,
                    0x00, 0x00, encoded, 0x00, 0x00, 0x00, 0x00, 0x00,
                ),
            )
            val mode = state.get(SignalDefinitions.wipersMode) as SignalValue.Enum
            assertEquals(expected, mode.code)
        }
    }

    @Test
    fun bodyBitsRemainFalseWhenTheFrameDoesNotSetThem() {
        val state = apply(frame(0x60D, 0, 0, 0, 0, 40, 40, 0, 0))

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
