package pl.polsatgranie.smartmegane.domain.trip

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.polsatgranie.smartmegane.domain.vehicle.VehiclePowerState
import pl.polsatgranie.smartmegane.domain.vehicle.VehicleState

class TripTrackerTest {
    @Test
    fun recordsAndFinishesTripWhenEngineStops() {
        val tracker = TripTracker()
        tracker.update(driving(speed = 60.0, fuelCounter = 0.001, sampleMs = 0L), 0L, 10_000L)
        val live = tracker.update(
            driving(speed = 60.0, fuelCounter = 0.003, sampleMs = 2_000L),
            2_000L,
            12_000L,
        ).live
        assertTrue(live.distanceKm > 0.0)
        assertTrue(live.fuelUsedLiters > 0.0)

        tracker.update(
            VehicleState(powerState = VehiclePowerState.IGNITION_ON),
            3_000L,
            13_000L,
        )
        val completed = tracker.update(
            VehicleState(powerState = VehiclePowerState.IGNITION_ON),
            5_300L,
            15_300L,
        ).completed
        assertNotNull(completed)
    }

    @Test
    fun countsFuelCounterWrapOnceAndIgnoresRepeatedUiPublication() {
        val tracker = TripTracker()
        tracker.update(driving(120.0, 0.02040, 1_000L), 1_000L, 10_000L)
        tracker.update(driving(120.0, 0.02040, 1_000L), 1_100L, 10_100L)
        val live = tracker.update(
            driving(120.0, 0.00008, 2_000L),
            2_000L,
            11_000L,
        ).live

        assertEquals(0.00016, live.fuelUsedLiters, 0.000001)
        assertTrue((live.averageConsumptionLitersPer100Km ?: 0.0) > 0.0)
    }

    @Test
    fun finishesActiveTripImmediatelyWhenTelemetryIsLost() {
        val tracker = TripTracker()
        tracker.update(driving(30.0, 0.001, 0L), 0L, 10_000L)
        tracker.update(driving(30.0, 0.003, 21_000L), 21_000L, 31_000L)

        val completed = tracker.finishNow(
            state = driving(0.0, 0.003, 21_000L),
            monotonicNowMs = 21_100L,
            epochNowMs = 31_100L,
        )

        assertNotNull(completed)
        assertEquals(21_100L, completed?.durationMs)
    }

    private fun driving(speed: Double, fuelCounter: Double, sampleMs: Long) = VehicleState(
        powerState = VehiclePowerState.ENGINE_RUNNING,
        speedKphPrecise = speed,
        isSpeedSignalAvailable = true,
        fuelUsedSinceStartLiters = fuelCounter,
        fuelCounterSampleTimestampMs = sampleMs,
        odometerKm = 258_000,
    )
}
