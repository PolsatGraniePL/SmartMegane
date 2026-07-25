package pl.polsatgranie.smartmegane.data.vehicle

import org.junit.Assert.assertEquals
import org.junit.Test
import pl.polsatgranie.smartmegane.domain.signal.SignalDefinitions
import pl.polsatgranie.smartmegane.domain.signal.SignalState
import pl.polsatgranie.smartmegane.domain.signal.SignalValue
import pl.polsatgranie.smartmegane.domain.vehicle.VehicleState
import pl.polsatgranie.smartmegane.domain.vehicle.WiperMode

class VehicleSignalAdapterTest {
    private val adapter = VehicleSignalAdapter()

    @Test
    fun missingSignalsLeaveTheWholePlaceholderUntouched() {
        val base = VehicleState(
            speedKph = 72,
            isFrontLeftDoorOpen = true,
            wiperMode = WiperMode.LOW,
            steeringWheelAngleDegrees = -42f,
        )

        assertEquals(base, adapter.merge(base, SignalState()))
    }

    @Test
    fun knownWiperCodesMapToTypedModes() {
        val expected = mapOf(
            0 to WiperMode.OFF,
            1 to WiperMode.INTERMITTENT,
            2 to WiperMode.LOW,
            3 to WiperMode.HIGH,
        )

        expected.forEach { (code, mode) ->
            val signals = SignalState(
                mapOf(
                    SignalDefinitions.wipersMode.id to
                        SignalValue.Enum(code, "ignored label"),
                ),
            )

            assertEquals(mode, adapter.merge(VehicleState(), signals).wiperMode)
        }
    }

    @Test
    fun unknownWiperCodePreservesPlaceholderMode() {
        val base = VehicleState(wiperMode = WiperMode.INTERMITTENT)
        val signals = SignalState(
            mapOf(
                SignalDefinitions.wipersMode.id to SignalValue.Enum(7, "unknown"),
            ),
        )

        assertEquals(WiperMode.INTERMITTENT, adapter.merge(base, signals).wiperMode)
    }

    @Test
    fun steeringValueMapsWithoutChangingUnrelatedVehicleData() {
        val base = VehicleState(
            speedKph = 31,
            isRearRightDoorOpen = true,
            isOilPressureWarningActive = true,
        )
        val signals = SignalState(
            mapOf(
                SignalDefinitions.steeringAngleRaw.id to
                    SignalValue.Number(-127.5, "deg"),
            ),
        )

        val result = adapter.merge(base, signals)

        assertEquals(-127.5f, result.steeringWheelAngleDegrees ?: 0f, 0.001f)
        assertEquals(31, result.speedKph)
        assertEquals(true, result.isRearRightDoorOpen)
        assertEquals(true, result.isOilPressureWarningActive)
    }

    @Test
    fun alreadyDecodedDoorAndPedalSignalsReachTheTypedApi() {
        val signals = SignalState(
            mapOf(
                SignalDefinitions.doorFrontLeft.id to SignalValue.Bool(true),
                SignalDefinitions.doorFrontRight.id to SignalValue.Bool(false),
                SignalDefinitions.doorRearLeft.id to SignalValue.Bool(true),
                SignalDefinitions.doorRearRight.id to SignalValue.Bool(false),
                SignalDefinitions.brakePressed.id to SignalValue.Bool(true),
                SignalDefinitions.clutchPressed.id to SignalValue.Bool(false),
            ),
        )

        val result = adapter.merge(VehicleState(), signals)

        assertEquals(true, result.isFrontLeftDoorOpen)
        assertEquals(false, result.isFrontRightDoorOpen)
        assertEquals(true, result.isRearLeftDoorOpen)
        assertEquals(false, result.isRearRightDoorOpen)
        assertEquals(true, result.isBrakePedalPressed)
        assertEquals(false, result.isClutchPedalPressed)
    }
}
