package pl.polsatgranie.smartmegane.data.vehicle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.polsatgranie.smartmegane.domain.signal.SignalDefinitions
import pl.polsatgranie.smartmegane.domain.signal.SignalKey
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
    fun mapsConfirmedNumericSignalsAndDuplicatePedalInputs() {
        val signals = stateOf(
            SignalDefinitions.engineRpmPrimary to SignalValue.Number(799.75, "rpm"),
            SignalDefinitions.vehicleSpeedKph to SignalValue.Number(12.34, "km/h"),
            SignalDefinitions.coolantTemperature60D to SignalValue.Number(58.0, "degC"),
            SignalDefinitions.odometer5FD to SignalValue.Number(258_058.0, "km"),
            SignalDefinitions.steeringAngleDegrees to SignalValue.Number(15.6, "deg"),
            SignalDefinitions.steeringAngularVelocityRaw to SignalValue.Number(10.0, "raw"),
            SignalDefinitions.clutchPressed181 to SignalValue.Bool(true),
            SignalDefinitions.brakePressed181 to SignalValue.Bool(false),
            SignalDefinitions.brakePressed354 to SignalValue.Bool(true),
            SignalDefinitions.parkingBrake to SignalValue.Bool(true),
            SignalDefinitions.acceleratorPedalRaw to SignalValue.Number(224.0, "raw"),
        )

        val result = adapter.merge(VehicleState(), signals, nowMs = 1_000)

        assertEquals(800, result.engineRpm)
        assertEquals(799.75, result.engineRpmPrecise ?: 0.0, 0.0)
        assertEquals(12, result.speedKph)
        assertEquals(12.34, result.speedKphPrecise ?: 0.0, 0.0)
        assertEquals(58, result.coolantTemperatureCelsius)
        assertEquals(258_058L, result.odometerKm)
        assertEquals(15.6f, result.steeringWheelAngleDegrees ?: 0f, 0.001f)
        assertEquals(10, result.steeringWheelAngularVelocityRaw)
        assertEquals(100f, result.acceleratorPedalPercent ?: 0f, 0.001f)
        assertTrue(result.isClutchPedalPressed)
        assertTrue(result.isBrakePedalPressed)
        assertTrue(result.isParkingBrakeActive)
    }

    @Test
    fun freshPrimaryRpmWinsAndStalePrimaryFallsBackToSecondary() {
        val values = mapOf(
            SignalDefinitions.engineRpmPrimary.id to SignalValue.Number(900.0, "rpm"),
            SignalDefinitions.engineRpmSecondary.id to SignalValue.Number(800.0, "rpm"),
        )
        val stalePrimary = SignalState(
            values = values,
            timestampsMs = mapOf(
                SignalDefinitions.engineRpmPrimary.id to 0L,
                SignalDefinitions.engineRpmSecondary.id to 900L,
            ),
        )

        val fallbackResult = adapter.merge(
            VehicleState(),
            stalePrimary,
            nowMs = 1_000,
        )

        assertEquals(800, fallbackResult.engineRpm)
        assertTrue(fallbackResult.areRpmSourcesConsistent)

        val bothFresh = stalePrimary.copy(
            timestampsMs = stalePrimary.timestampsMs.mapValues { 1_000L },
        )
        val primaryResult = adapter.merge(
            VehicleState(),
            bothFresh,
            nowMs = 1_000,
        )

        assertEquals(900, primaryResult.engineRpm)
        assertFalse(primaryResult.areRpmSourcesConsistent)
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
            SignalDefinitions.reverseGear60D to SignalValue.Bool(true),
            SignalDefinitions.doorsLocked to SignalValue.Bool(true),
            SignalDefinitions.trunkLocked to SignalValue.Bool(true),
            SignalDefinitions.ignitionOn to SignalValue.Bool(true),
            SignalDefinitions.accessoryPowerOn to SignalValue.Bool(false),
            SignalDefinitions.outsideTemperature to SignalValue.Number(20.0, "degC"),
        )

        val result = adapter.merge(VehicleState(), signals, nowMs = 1_000)

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
        assertTrue(result.areDoorsLocked)
        assertTrue(result.isTrunkLocked)
        assertTrue(result.isIgnitionOn)
        assertFalse(result.isAccessoryPowerOn)
        assertEquals(20, result.outsideTemperatureCelsius)
    }

    @Test
    fun knownWiperCodesMapToDistinctTypedModes() {
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

    private fun stateOf(
        vararg values: Pair<SignalKey, SignalValue>,
    ): SignalState = SignalState(
        values = values.associate { (key, value) -> key.id to value },
        timestampsMs = values.associate { (key, _) -> key.id to 1_000L },
    )
}
