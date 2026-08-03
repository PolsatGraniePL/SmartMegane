package pl.polsatgranie.smartmegane.domain.vehicle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.polsatgranie.smartmegane.domain.signal.SignalDefinitions
import pl.polsatgranie.smartmegane.domain.signal.SignalState
import pl.polsatgranie.smartmegane.domain.signal.SignalValue
import kotlin.math.roundToInt

class LiveVehicleStateStabilizerTest {
    @Test
    fun ignoresSingleZeroSpeedFrameWhileVehicleIsMoving() {
        val stabilizer = LiveVehicleStateStabilizer()
        val moving = stabilizer.stabilize(
            speedState(18.0),
            signal(SignalDefinitions.vehicleSpeedKph.id, SignalValue.Number(18.0), 100L),
            100L,
        )
        val oneZero = stabilizer.stabilize(
            speedState(0.0),
            signal(SignalDefinitions.vehicleSpeedKph.id, SignalValue.Number(0.0), 200L),
            200L,
        )

        assertEquals(18.0, moving.speedKphPrecise ?: 0.0, 0.0)
        assertEquals(18.0, oneZero.speedKphPrecise ?: 0.0, 0.0)
    }

    @Test
    fun acceptsConfirmedZeroAfterStopDwell() {
        val stabilizer = LiveVehicleStateStabilizer()
        stabilizer.stabilize(
            speedState(12.0),
            signal(SignalDefinitions.vehicleSpeedKph.id, SignalValue.Number(12.0), 100L),
            100L,
        )
        stabilizer.stabilize(
            speedState(0.0),
            signal(SignalDefinitions.vehicleSpeedKph.id, SignalValue.Number(0.0), 200L),
            200L,
        )
        val confirmed = stabilizer.stabilize(
            speedState(0.0),
            signal(SignalDefinitions.vehicleSpeedKph.id, SignalValue.Number(0.0), 600L),
            600L,
        )

        assertEquals(0.0, confirmed.speedKphPrecise ?: -1.0, 0.0)
    }

    @Test
    fun bodyChangesNeedTwoFramesBeforeEngineStarts() {
        val stabilizer = LiveVehicleStateStabilizer()
        val first = stabilizer.stabilize(
            VehicleState(isFrontLeftDoorOpen = true),
            signal(SignalDefinitions.doorFrontLeft.id, SignalValue.Bool(true), 100L),
            100L,
        )
        val second = stabilizer.stabilize(
            VehicleState(isFrontLeftDoorOpen = true),
            signal(SignalDefinitions.doorFrontLeft.id, SignalValue.Bool(true), 200L),
            200L,
        )
        val glitch = stabilizer.stabilize(
            VehicleState(isFrontLeftDoorOpen = false),
            signal(SignalDefinitions.doorFrontLeft.id, SignalValue.Bool(false), 300L),
            300L,
        )

        assertFalse(first.isFrontLeftDoorOpen)
        assertTrue(second.isFrontLeftDoorOpen)
        assertTrue(glitch.isFrontLeftDoorOpen)
    }

    @Test
    fun staleBodySampleNeverTurnsIntoSyntheticFalseWhileEngineRuns() {
        val stabilizer = LiveVehicleStateStabilizer()
        val timestamp = 100L
        val first = stabilizer.stabilize(
            VehicleState(
                engineRpm = 800,
                engineRpmPrecise = 800.0,
                isEngineRpmSignalAvailable = true,
                isFrontLeftDoorOpen = true,
            ),
            signal(SignalDefinitions.doorFrontLeft.id, SignalValue.Bool(true), timestamp),
            timestamp,
        )
        val repeatedTimestampWithAdapterDefault = stabilizer.stabilize(
            VehicleState(
                engineRpm = 800,
                engineRpmPrecise = 800.0,
                isEngineRpmSignalAvailable = true,
                isFrontLeftDoorOpen = false,
            ),
            signal(SignalDefinitions.doorFrontLeft.id, SignalValue.Bool(true), timestamp),
            3_000L,
        )

        assertTrue(first.isFrontLeftDoorOpen)
        assertTrue(repeatedTimestampWithAdapterDefault.isFrontLeftDoorOpen)
    }

    private fun speedState(speed: Double) = VehicleState(
        speedKphPrecise = speed,
        speedKph = speed.roundToInt(),
        isSpeedSignalAvailable = true,
    )

    private fun signal(
        id: String,
        value: SignalValue,
        timestamp: Long,
    ) = SignalState(
        values = mapOf(id to value),
        timestampsMs = mapOf(id to timestamp),
    )
}
