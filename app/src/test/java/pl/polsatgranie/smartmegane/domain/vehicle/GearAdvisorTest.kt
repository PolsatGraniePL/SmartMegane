package pl.polsatgranie.smartmegane.domain.vehicle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GearAdvisorTest {
    @Test
    fun reverseDisplaysRWithoutForwardRecommendationOrArrow() {
        val result = GearAdvisor().update(
            input(
                speedKph = 0.0,
                engineRpm = 800.0,
                reverseEngaged = true,
            ),
            nowMs = 0L,
        )

        assertEquals(GearEstimateStatus.REVERSE, result.estimate.status)
        assertEquals("R", result.displayGear)
        assertNull(result.preferredGear)
        assertEquals(ShiftDirection.NONE, result.shiftDirection)
    }

    @Test
    fun infersSecondGearFromFactoryRatioOnlyAfterDwell() {
        val advisor = GearAdvisor()
        val input = input(speedKph = 15.96, engineRpm = 1_000.0)

        val first = advisor.update(input, nowMs = 0L)
        advisor.update(input, nowMs = 200L)
        val stable = advisor.update(input, nowMs = 350L)

        assertEquals(GearEstimateStatus.SHIFTING, first.estimate.status)
        assertNull(first.estimate.forwardGear)
        assertEquals(GearEstimateStatus.COUPLED, stable.estimate.status)
        assertEquals(2, stable.estimate.forwardGear)
        assertTrue(stable.estimate.confidence > 0.95f)
    }

    @Test
    fun recognisesSixthGearAtFactoryDevelopment() {
        val advisor = GearAdvisor()
        val input = input(speedKph = 48.70, engineRpm = 1_000.0)

        advisor.update(input, nowMs = 0L)
        advisor.update(input, nowMs = 200L)
        val result = advisor.update(input, nowMs = 350L)

        assertEquals(6, result.estimate.forwardGear)
    }

    @Test
    fun clutchDisengagementNeverPretendsThatLastGearIsStillEngaged() {
        val advisor = GearAdvisor()
        val coupled = input(speedKph = 31.92, engineRpm = 2_000.0)
        advisor.update(coupled, nowMs = 0L)
        advisor.update(coupled, nowMs = 200L)
        advisor.update(coupled, nowMs = 350L)

        val result = advisor.update(
            coupled.copy(clutchPressed = true),
            nowMs = 500L,
        )

        assertEquals(GearEstimateStatus.CLUTCH_DISENGAGED, result.estimate.status)
        assertNull(result.estimate.forwardGear)
        assertEquals(ShiftDirection.NONE, result.shiftDirection)
    }

    @Test
    fun implausibleNeutralCoastRatioIsAmbiguous() {
        val advisor = GearAdvisor()
        val result = advisor.update(
            input(speedKph = 50.0, engineRpm = 800.0),
            nowMs = 0L,
        )

        assertEquals(GearEstimateStatus.AMBIGUOUS, result.estimate.status)
        assertNull(result.estimate.forwardGear)
    }

    @Test
    fun oneSampleCannotSwitchAnAlreadyStableGear() {
        val advisor = GearAdvisor()
        val second = input(speedKph = 31.92, engineRpm = 2_000.0)
        advisor.update(second, nowMs = 0L)
        advisor.update(second, nowMs = 200L)
        advisor.update(second, nowMs = 350L)

        val third = input(speedKph = 47.0, engineRpm = 2_000.0)
        val transient = advisor.update(third, nowMs = 450L)

        assertEquals(GearEstimateStatus.SHIFTING, transient.estimate.status)
        assertNull(transient.estimate.forwardGear)
        assertEquals(2, transient.estimate.lastStableGear)
    }

    @Test
    fun ratioMustRemainCloseToFactoryValueForTheWholeDwell() {
        val advisor = GearAdvisor()
        val nominalSecondRpm = 1_000.0
        val slippingSpeed = 15.96 * 1.06
        val slipping = input(
            speedKph = slippingSpeed,
            engineRpm = nominalSecondRpm,
        )

        advisor.update(slipping, nowMs = 0L)
        advisor.update(slipping, nowMs = 200L)
        advisor.update(slipping, nowMs = 350L)
        val oneGoodSample = advisor.update(
            input(speedKph = 15.96, engineRpm = nominalSecondRpm),
            nowMs = 400L,
        )

        assertNull(oneGoodSample.estimate.forwardGear)
        assertEquals(GearEstimateStatus.SHIFTING, oneGoodSample.estimate.status)
    }

    @Test
    fun repeatedForeignFramesCannotTurnOneKinematicSampleIntoStableGear() {
        val advisor = GearAdvisor()
        val staleSample = input(
            speedKph = 15.96,
            engineRpm = 1_000.0,
        ).copy(kinematicsTimestampMs = 100L)

        advisor.update(staleSample, nowMs = 0L)
        advisor.update(staleSample, nowMs = 200L)
        val repeated = advisor.update(staleSample, nowMs = 600L)

        assertNull(repeated.estimate.forwardGear)

        advisor.update(
            staleSample.copy(kinematicsTimestampMs = 200L),
            nowMs = 700L,
        )
        val freshAgain = advisor.update(
            staleSample.copy(kinematicsTimestampMs = 300L),
            nowMs = 800L,
        )

        assertEquals(2, freshAgain.estimate.forwardGear)
    }

    @Test
    fun economicalProfileProgressesAcrossAllSixGears() {
        val lowLoad = 0.12

        assertEquals(1, GearRecommendationCalculator.calculate(0.0, lowLoad).gear)
        assertEquals(2, GearRecommendationCalculator.calculate(20.0, lowLoad).gear)
        assertEquals(3, GearRecommendationCalculator.calculate(30.0, lowLoad).gear)
        assertEquals(4, GearRecommendationCalculator.calculate(45.0, lowLoad).gear)
        assertEquals(5, GearRecommendationCalculator.calculate(58.0, lowLoad).gear)
        assertEquals(6, GearRecommendationCalculator.calculate(70.0, lowLoad).gear)
    }

    @Test
    fun firstGearIsReservedForLaunchUntilSecondCanRunAboveIdle() {
        val lowLoad = 0.12

        assertEquals(1, GearRecommendationCalculator.calculate(0.0, lowLoad).gear)
        assertEquals(1, GearRecommendationCalculator.calculate(10.0, lowLoad).gear)
        assertEquals(1, GearRecommendationCalculator.calculate(12.0, lowLoad).gear)
        assertEquals(1, GearRecommendationCalculator.calculate(17.0, lowLoad).gear)
        assertEquals(2, GearRecommendationCalculator.calculate(18.0, lowLoad).gear)
    }

    @Test
    fun firstToSecondThresholdRisesWhenDriverRequestsMoreTorque() {
        val highLoad = 1.0

        assertEquals(1, GearRecommendationCalculator.calculate(23.0, highLoad).gear)
        assertEquals(2, GearRecommendationCalculator.calculate(25.0, highLoad).gear)
    }

    @Test
    fun firstGearExitCueDoesNotArriveTooLateForTheShortLaunchGear() {
        val advisor = GearAdvisor()
        advisor.update(
            input(
                speedKph = 0.0,
                engineRpm = 800.0,
                acceleratorPercent = 100f,
                requestedTorqueNm = 240,
            ),
            nowMs = 0L,
        )
        val firstBelowThreshold = input(
            speedKph = 23.0,
            engineRpm = 23.0 / 8.34 * 1_000.0,
            acceleratorPercent = 100f,
            requestedTorqueNm = 240,
        )
        advisor.update(firstBelowThreshold, nowMs = 1_500L)
        advisor.update(firstBelowThreshold, nowMs = 1_700L)
        val coupledFirst = advisor.update(firstBelowThreshold, nowMs = 1_850L)

        val firstAboveThreshold = firstBelowThreshold.copy(
            speedKph = 28.0,
            engineRpm = 28.0 / 8.34 * 1_000.0,
        )
        val thresholdCrossed = advisor.update(firstAboveThreshold, nowMs = 1_950L)
        val stillDwelling = advisor.update(firstAboveThreshold, nowMs = 2_150L)
        val preferredSecond = advisor.update(firstAboveThreshold, nowMs = 2_250L)
        val arrowReady = advisor.update(firstAboveThreshold, nowMs = 2_450L)

        assertEquals(1, coupledFirst.estimate.forwardGear)
        assertEquals(1, thresholdCrossed.preferredGear)
        assertEquals(ShiftDirection.NONE, stillDwelling.shiftDirection)
        assertEquals(2, preferredSecond.preferredGear)
        assertEquals(ShiftDirection.NONE, preferredSecond.shiftDirection)
        assertEquals(ShiftDirection.UP, arrowReady.shiftDirection)
    }

    @Test
    fun calmlyRollingInSecondDoesNotRequestFirstGear() {
        val advisor = GearAdvisor()
        val speed = 16.0
        val second = input(
            speedKph = speed,
            engineRpm = speed / 15.96 * 1_000.0,
            acceleratorPercent = 10f,
        )

        advisor.update(second, nowMs = 0L)
        advisor.update(second, nowMs = 200L)
        val coupled = advisor.update(second, nowMs = 350L)
        val settled = advisor.update(second, nowMs = 1_000L)

        assertEquals(2, coupled.estimate.forwardGear)
        assertEquals(2, coupled.preferredGear)
        assertEquals(2, settled.preferredGear)
        assertEquals(ShiftDirection.NONE, settled.shiftDirection)
    }

    @Test
    fun calmlyRollingInThirdRequestsSecondRatherThanFirst() {
        val advisor = GearAdvisor()
        val speed = 17.0
        val third = input(
            speedKph = speed,
            engineRpm = speed / 23.50 * 1_000.0,
            acceleratorPercent = 10f,
        )

        advisor.update(third, nowMs = 0L)
        advisor.update(third, nowMs = 200L)
        val coupled = advisor.update(third, nowMs = 350L)
        val actionable = advisor.update(third, nowMs = 900L)

        assertEquals(3, coupled.estimate.forwardGear)
        assertEquals(2, coupled.preferredGear)
        assertEquals(2, actionable.preferredGear)
        assertEquals(ShiftDirection.DOWN, actionable.shiftDirection)
    }

    @Test
    fun heavilyLoadedSecondCanRequestFirstBelowItsUsefulRange() {
        val advisor = GearAdvisor()
        val speed = 16.0
        val second = input(
            speedKph = speed,
            engineRpm = speed / 15.96 * 1_000.0,
            acceleratorPercent = 100f,
            requestedTorqueNm = 240,
        )

        advisor.update(second, nowMs = 0L)
        advisor.update(second, nowMs = 200L)
        advisor.update(second, nowMs = 350L)
        val result = advisor.update(second, nowMs = 700L)

        assertEquals(2, result.estimate.forwardGear)
        assertEquals(1, result.preferredGear)
        assertEquals(ShiftDirection.DOWN, result.shiftDirection)
    }

    @Test
    fun highLoadHoldsLowerGearThanEcoCruise() {
        val eco = GearRecommendationCalculator.calculate(70.0, load = 0.12)
        val accelerating = GearRecommendationCalculator.calculate(70.0, load = 0.90)

        assertTrue(accelerating.gear < eco.gear)
        assertTrue(accelerating.targetRpm > eco.targetRpm)
    }

    @Test
    fun preferredGearAdvancesAfterHysteresisDuringSpeedIncrease() {
        val advisor = GearAdvisor()
        val secondAtTwenty = input(
            speedKph = 20.0,
            engineRpm = 20.0 / 15.96 * 1_000.0,
            acceleratorPercent = 10f,
        )
        advisor.update(secondAtTwenty, nowMs = 0L)
        advisor.update(secondAtTwenty, nowMs = 200L)
        val initial = advisor.update(secondAtTwenty, nowMs = 350L)
        assertEquals(2, initial.preferredGear)

        val thirdAtThirty = input(
            speedKph = 30.0,
            engineRpm = 30.0 / 23.50 * 1_000.0,
            acceleratorPercent = 10f,
        )
        var shiftedPreference = advisor.update(thirdAtThirty, nowMs = 750L)
        repeat(30) { sample ->
            shiftedPreference = advisor.update(
                thirdAtThirty,
                nowMs = 850L + sample * 100L,
            )
        }

        assertEquals(3, shiftedPreference.preferredGear)
    }

    @Test
    fun showsUpshiftOnlyAfterCurrentGearIsConfidentlyCoupled() {
        val advisor = GearAdvisor()
        val speed = 45.0
        val secondGearRpm = speed / 15.96 * 1_000.0
        val input = input(
            speedKph = speed,
            engineRpm = secondGearRpm,
            acceleratorPercent = 10f,
        )

        val first = advisor.update(input, nowMs = 0L)
        advisor.update(input, nowMs = 200L)
        val coupledWithoutArrow = advisor.update(input, nowMs = 350L)
        val stillWaiting = advisor.update(input, nowMs = 800L)
        val actionable = advisor.update(input, nowMs = 1_150L)

        assertEquals(ShiftDirection.NONE, first.shiftDirection)
        assertEquals(2, coupledWithoutArrow.estimate.forwardGear)
        assertEquals(ShiftDirection.NONE, coupledWithoutArrow.shiftDirection)
        assertEquals(ShiftDirection.NONE, stillWaiting.shiftDirection)
        assertEquals(ShiftDirection.UP, actionable.shiftDirection)
        assertTrue(actionable.preferredGear!! > actionable.estimate.forwardGear!!)
    }

    @Test
    fun highLoadInSixthRequestsDownshift() {
        val advisor = GearAdvisor()
        val speed = 50.0
        val sixthGearRpm = speed / 48.70 * 1_000.0
        val input = input(
            speedKph = speed,
            engineRpm = sixthGearRpm,
            acceleratorPercent = 100f,
            requestedTorqueNm = 240,
        )

        advisor.update(input, nowMs = 0L)
        advisor.update(input, nowMs = 200L)
        val coupled = advisor.update(input, nowMs = 350L)
        val result = advisor.update(input, nowMs = 700L)

        assertEquals(ShiftDirection.NONE, coupled.shiftDirection)
        assertEquals(6, result.estimate.forwardGear)
        assertEquals(ShiftDirection.DOWN, result.shiftDirection)
        assertTrue(result.preferredGear!! < 6)
    }

    @Test
    fun brakingSuppressesShiftArrow() {
        val advisor = GearAdvisor()
        val speed = 45.0
        val input = input(
            speedKph = speed,
            engineRpm = speed / 15.96 * 1_000.0,
            brakePressed = true,
        )

        advisor.update(input, nowMs = 0L)
        advisor.update(input, nowMs = 200L)
        val result = advisor.update(input, nowMs = 350L)

        assertEquals(2, result.estimate.forwardGear)
        assertEquals(ShiftDirection.NONE, result.shiftDirection)
    }

    @Test
    fun shiftArrowDwellStartsOnlyAfterBrakeIsReleased() {
        val advisor = GearAdvisor()
        val speed = 45.0
        val braking = input(
            speedKph = speed,
            engineRpm = speed / 15.96 * 1_000.0,
            brakePressed = true,
        )
        advisor.update(braking, nowMs = 0L)
        advisor.update(braking, nowMs = 200L)
        advisor.update(braking, nowMs = 350L)

        val released = braking.copy(brakePressed = false)
        val justReleased = advisor.update(released, nowMs = 1_000L)
        var tooSoon = justReleased
        for (nowMs in 1_100L..1_700L step 100L) {
            tooSoon = advisor.update(released, nowMs = nowMs)
        }
        val ready = advisor.update(released, nowMs = 1_800L)

        assertEquals(ShiftDirection.NONE, justReleased.shiftDirection)
        assertEquals(ShiftDirection.NONE, tooSoon.shiftDirection)
        assertEquals(ShiftDirection.UP, ready.shiftDirection)
    }

    @Test
    fun reverseHasNoForwardGearRecommendation() {
        val advisor = GearAdvisor()

        val result = advisor.update(
            input(
                speedKph = 4.0,
                engineRpm = 1_100.0,
                reverseEngaged = true,
            ),
            nowMs = 0L,
        )

        assertEquals(GearEstimateStatus.REVERSE, result.estimate.status)
        assertNull(result.preferredGear)
        assertEquals(ShiftDirection.NONE, result.shiftDirection)
    }

    @Test
    fun revMatchIsAvailableAfterClutchingOutOfKnownGear() {
        val advisor = GearAdvisor()
        val coupled = input(speedKph = 31.92, engineRpm = 2_000.0)
        advisor.update(coupled, nowMs = 0L)
        advisor.update(coupled, nowMs = 200L)
        advisor.update(coupled, nowMs = 350L)

        val result = advisor.update(
            coupled.copy(clutchPressed = true),
            nowMs = 500L,
        )

        assertTrue(result.revMatchGear != null)
        assertTrue(result.revMatchConfidence >= 0.65f)
    }

    @Test
    fun staleGearCannotEnableRevMatchAfterLongNeutralCoast() {
        val advisor = GearAdvisor()
        val fourth = input(
            speedKph = 63.76,
            engineRpm = 2_000.0,
        )
        advisor.update(fourth, nowMs = 0L)
        advisor.update(fourth, nowMs = 200L)
        advisor.update(fourth, nowMs = 350L)

        val neutralCoast = input(
            speedKph = 50.0,
            engineRpm = 800.0,
        )
        advisor.update(neutralCoast, nowMs = 3_350L)
        val result = advisor.update(
            neutralCoast.copy(clutchPressed = true),
            nowMs = 3_450L,
        )

        assertNull(result.estimate.lastStableGear)
        assertNull(result.revMatchGear)
        assertEquals(0f, result.revMatchConfidence)
    }

    @Test
    fun preferredShiftTargetIsLatchedWhileClutchIsPressed() {
        val advisor = GearAdvisor()
        val speed = 45.0
        val secondGear = input(
            speedKph = speed,
            engineRpm = speed / 15.96 * 1_000.0,
            acceleratorPercent = 5f,
        )
        advisor.update(secondGear, nowMs = 0L)
        advisor.update(secondGear, nowMs = 200L)
        val beforeShift = advisor.update(secondGear, nowMs = 350L)
        val targetBeforeClutch = beforeShift.preferredGear

        val highLoadDuringShift = secondGear.copy(
            clutchPressed = true,
            acceleratorPercent = 100f,
            requestedTorqueNm = 240,
        )
        advisor.update(highLoadDuringShift, nowMs = 450L)
        val held = advisor.update(highLoadDuringShift, nowMs = 2_200L)

        assertEquals(targetBeforeClutch, held.preferredGear)
        assertEquals(targetBeforeClutch, held.revMatchGear)
    }

    @Test
    fun unknownDriveRatioCannotClaimConfidentRevMatch() {
        val advisor = GearAdvisor()

        val result = advisor.update(
            input(
                speedKph = 50.0,
                engineRpm = 800.0,
                clutchPressed = true,
            ),
            nowMs = 0L,
        )

        assertNull(result.revMatchGear)
        assertEquals(0f, result.revMatchConfidence)
    }

    @Test
    fun typedInputDoesNotTreatPlaceholderZerosAsLiveCanSignals() {
        val input = GearAdvisorInput.from(
            VehicleState(
                speedKph = 50,
                engineRpm = 2_000,
                isClutchPedalPressed = false,
            ),
        )

        assertNull(input.speedKph)
        assertNull(input.engineRpm)
        assertNull(input.clutchPressed)
        assertNull(input.brakePressed)
        assertNull(input.reverseEngaged)
    }

    private fun input(
        speedKph: Double,
        engineRpm: Double,
        clutchPressed: Boolean = false,
        brakePressed: Boolean = false,
        acceleratorPercent: Float? = null,
        requestedTorqueNm: Int? = null,
        reverseEngaged: Boolean = false,
    ) = GearAdvisorInput(
        speedKph = speedKph,
        engineRpm = engineRpm,
        clutchPressed = clutchPressed,
        brakePressed = brakePressed,
        acceleratorPercent = acceleratorPercent,
        requestedTorqueNm = requestedTorqueNm,
        reverseEngaged = reverseEngaged,
    )
}
