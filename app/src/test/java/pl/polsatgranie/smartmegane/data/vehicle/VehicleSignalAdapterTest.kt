package pl.polsatgranie.smartmegane.data.vehicle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.polsatgranie.smartmegane.domain.signal.SignalDefinitions
import pl.polsatgranie.smartmegane.domain.signal.SignalKey
import pl.polsatgranie.smartmegane.domain.signal.SignalReading
import pl.polsatgranie.smartmegane.domain.signal.SignalState
import pl.polsatgranie.smartmegane.domain.signal.SignalValue
import pl.polsatgranie.smartmegane.domain.vehicle.VehicleState
import pl.polsatgranie.smartmegane.domain.vehicle.WiperMode

class VehicleSignalAdapterTest {
    private val adapter = VehicleSignalAdapter()

    @Test
    fun missingSignalsProduceNeutralLiveState() {
        assertEquals(VehicleState(), adapter.merge(SignalState()))
    }

    @Test
    fun mapsCanonicalNumericAndControlSignals() {
        val signals = stateOf(
            SignalDefinitions.engineRpm to SignalValue.Number(799.75, "rpm"),
            SignalDefinitions.vehicleSpeedKph to SignalValue.Number(12.34, "km/h"),
            SignalDefinitions.coolantTemperature to SignalValue.Number(58.0, "degC"),
            SignalDefinitions.odometer to SignalValue.Number(258_058.0, "km"),
            SignalDefinitions.steeringAngleDegrees to SignalValue.Number(15.6, "deg"),
            SignalDefinitions.steeringAngularVelocityRaw to SignalValue.Number(10.0, "raw"),
            SignalDefinitions.clutchPressed to SignalValue.Bool(true),
            SignalDefinitions.brakePressed to SignalValue.Bool(true),
            SignalDefinitions.parkingBrake to SignalValue.Bool(true),
            SignalDefinitions.acceleratorPedalRaw to SignalValue.Number(224.0, "raw"),
            SignalDefinitions.fuelLevelRaw to SignalValue.Number(114.0, "raw"),
            SignalDefinitions.engineRunning to SignalValue.Bool(true),
            SignalDefinitions.engineDataValid to SignalValue.Bool(true),
            SignalDefinitions.steeringDataValid to SignalValue.Bool(true),
            SignalDefinitions.rearDefrostCommandActive to SignalValue.Bool(true),
            SignalDefinitions.asrEspDisabled to SignalValue.Bool(true),
            SignalDefinitions.electronicFault to SignalValue.Bool(true),
            SignalDefinitions.wheelPairARightSpeedKph to SignalValue.Number(12.3, "km/h"),
            SignalDefinitions.wheelPairALeftSpeedKph to SignalValue.Number(12.1, "km/h"),
            SignalDefinitions.wheelPairBRightSpeedKph to SignalValue.Number(12.4, "km/h"),
            SignalDefinitions.wheelPairBLeftSpeedKph to SignalValue.Number(12.2, "km/h"),
            SignalDefinitions.longitudinalAccelerationRaw to SignalValue.Number(-3.0, "raw"),
            SignalDefinitions.lateralAccelerationRaw to SignalValue.Number(24.0, "raw"),
            SignalDefinitions.yawRateRaw to SignalValue.Number(-12.0, "raw"),
        )

        val result = adapter.merge(signals, nowMs = 1_000)

        assertEquals(800, result.engineRpm)
        assertEquals(799.75, result.engineRpmPrecise ?: 0.0, 0.0)
        assertTrue(result.isEngineRpmSignalAvailable)
        assertEquals(12, result.speedKph)
        assertEquals(12.34, result.speedKphPrecise ?: 0.0, 0.0)
        assertTrue(result.isSpeedSignalAvailable)
        assertEquals(58, result.coolantTemperatureCelsius)
        assertEquals(258_058L, result.odometerKm)
        assertEquals(114, result.fuelLevelRaw)
        assertEquals(45, result.fuelLevelPercent)
        assertEquals(44.7059f, result.fuelLevelEstimatedPercent ?: 0f, 0.001f)
        assertEquals(15.6f, result.steeringWheelAngleDegrees ?: 0f, 0.001f)
        assertEquals(10, result.steeringWheelAngularVelocityRaw)
        assertEquals(100f, result.acceleratorPedalPercent ?: 0f, 0.001f)
        assertTrue(result.isClutchPedalPressed)
        assertTrue(result.isClutchPedalSignalAvailable)
        assertTrue(result.isBrakePedalPressed)
        assertTrue(result.isBrakePedalSignalAvailable)
        assertTrue(result.isParkingBrakeActive)
        assertTrue(result.isEngineRunning)
        assertTrue(result.isEngineDataValid)
        assertTrue(result.isSteeringDataValid)
        assertTrue(result.isRearDefrostCommandActive)
        assertTrue(result.isEspAsrDisabled)
        assertTrue(result.isElectronicFaultActive)
        assertEquals(12.3, result.wheelPairARightSpeedKph ?: 0.0, 0.0)
        assertEquals(12.1, result.wheelPairALeftSpeedKph ?: 0.0, 0.0)
        assertEquals(12.4, result.wheelPairBRightSpeedKph ?: 0.0, 0.0)
        assertEquals(12.2, result.wheelPairBLeftSpeedKph ?: 0.0, 0.0)
        assertEquals(-3, result.longitudinalAccelerationRaw)
        assertEquals(24, result.lateralAccelerationRaw)
        assertEquals(-12, result.yawRateRaw)
    }

    @Test
    fun staleCanonicalSignalsAreUnavailableAndNeverReplaced() {
        val stale = SignalState(
            values = mapOf(
                SignalDefinitions.engineRpm.id to SignalValue.Number(2_000.0, "rpm"),
                SignalDefinitions.vehicleSpeedKph.id to SignalValue.Number(50.0, "km/h"),
                SignalDefinitions.clutchPressed.id to SignalValue.Bool(false),
                SignalDefinitions.brakePressed.id to SignalValue.Bool(false),
            ),
            timestampsMs = mapOf(
                SignalDefinitions.engineRpm.id to 0L,
                SignalDefinitions.vehicleSpeedKph.id to 0L,
                SignalDefinitions.clutchPressed.id to 0L,
                SignalDefinitions.brakePressed.id to 0L,
            ),
        )

        val result = adapter.merge(stale, nowMs = 2_000L)

        assertFalse(result.isEngineRpmSignalAvailable)
        assertFalse(result.isSpeedSignalAvailable)
        assertFalse(result.isClutchPedalSignalAvailable)
        assertFalse(result.isBrakePedalSignalAvailable)
        assertNull(result.engineRpmPrecise)
        assertNull(result.speedKphPrecise)
    }

    @Test
    fun bodySignalsReachTheTypedApi() {
        val signals = stateOf(
            SignalDefinitions.doorFrontLeft to SignalValue.Bool(true),
            SignalDefinitions.doorFrontRight to SignalValue.Bool(false),
            SignalDefinitions.doorRearLeft to SignalValue.Bool(true),
            SignalDefinitions.doorRearRight to SignalValue.Bool(false),
            SignalDefinitions.trunkOpen to SignalValue.Bool(true),
            SignalDefinitions.positionLights to SignalValue.Bool(true),
            SignalDefinitions.lowBeamLights to SignalValue.Bool(true),
            SignalDefinitions.highBeamLights to SignalValue.Bool(false),
            SignalDefinitions.frontFogLights to SignalValue.Bool(true),
            SignalDefinitions.rearFogLights to SignalValue.Bool(false),
            SignalDefinitions.leftTurnSignal to SignalValue.Bool(true),
            SignalDefinitions.rightTurnSignal to SignalValue.Bool(false),
            SignalDefinitions.reverseGear to SignalValue.Bool(true),
            SignalDefinitions.doorsLocked to SignalValue.Bool(true),
            SignalDefinitions.trunkLocked to SignalValue.Bool(true),
            SignalDefinitions.ignitionOn to SignalValue.Bool(true),
            SignalDefinitions.accessoryPowerOn to SignalValue.Bool(false),
            SignalDefinitions.outsideTemperature to SignalValue.Number(20.0, "degC"),
        )

        val result = adapter.merge(signals, nowMs = 1_000)

        assertTrue(result.isFrontLeftDoorOpen)
        assertFalse(result.isFrontRightDoorOpen)
        assertTrue(result.isRearLeftDoorOpen)
        assertFalse(result.isRearRightDoorOpen)
        assertTrue(result.isTrunkOpen)
        assertTrue(result.arePositionLightsOn)
        assertTrue(result.areLowBeamLightsOn)
        assertFalse(result.areHighBeamLightsOn)
        assertTrue(result.areFrontFogLightsOn)
        assertFalse(result.areRearFogLightsOn)
        assertTrue(result.isLeftTurnSignalOn)
        assertFalse(result.isRightTurnSignalOn)
        assertTrue(result.isReverseGearEngaged)
        assertTrue(result.isReverseGearSignalAvailable)
        assertTrue(result.areDoorsLocked)
        assertTrue(result.isDoorLockSignalAvailable)
        assertTrue(result.isTrunkLocked)
        assertTrue(result.isTrunkLockSignalAvailable)
        assertTrue(result.isIgnitionOn)
        assertFalse(result.isAccessoryPowerOn)
        assertEquals(20, result.outsideTemperatureCelsius)
    }

    @Test
    fun capturedWiperCodesMapToDistinctTypedModes() {
        val expected = mapOf(
            0 to WiperMode.OFF,
            1 to WiperMode.INTERMITTENT,
            6 to WiperMode.LOW,
            7 to WiperMode.HIGH,
        )

        expected.forEach { (code, mode) ->
            val signals = SignalState(
                mapOf(
                    SignalDefinitions.wipersMode.id to
                        SignalValue.Enum(code, "ignored label"),
                ),
            )

            assertEquals(mode, adapter.merge(signals).wiperMode)
        }
    }

    @Test
    fun stableTenHertzBodyFramesNeverPulseToFalseBetweenUpdates() {
        var signals = SignalState()
        repeat(150) { index ->
            val timestamp = 1_000L + index * 100L
            signals = signals.withUpdates(
                listOf(
                    SignalReading(SignalDefinitions.doorFrontLeft, SignalValue.Bool(true)),
                    SignalReading(SignalDefinitions.parkingBrake, SignalValue.Bool(true)),
                    SignalReading(SignalDefinitions.doorsLocked, SignalValue.Bool(false)),
                ),
                timestampMs = timestamp,
            )
            val betweenFrames = adapter.merge(signals, nowMs = timestamp + 99L)
            assertTrue(betweenFrames.isFrontLeftDoorOpen)
            assertTrue(betweenFrames.isParkingBrakeActive)
            assertTrue(betweenFrames.isDoorLockSignalAvailable)
            assertFalse(betweenFrames.areDoorsLocked)
        }
    }

    private fun stateOf(
        vararg values: Pair<SignalKey, SignalValue>,
    ): SignalState = SignalState(
        values = values.associate { (key, value) -> key.id to value },
        timestampsMs = values.associate { (key, _) -> key.id to 1_000L },
    )
}
