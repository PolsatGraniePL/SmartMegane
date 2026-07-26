package pl.polsatgranie.smartmegane.domain.vehicle

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

enum class ShiftDirection {
    NONE,
    UP,
    DOWN,
}

enum class GearEstimateStatus {
    COUPLED,
    CLUTCH_DISENGAGED,
    SHIFTING,
    STATIONARY,
    AMBIGUOUS,
    SIGNAL_UNAVAILABLE,
    REVERSE,
}

data class GearEstimate(
    val forwardGear: Int? = null,
    val status: GearEstimateStatus = GearEstimateStatus.SIGNAL_UNAVAILABLE,
    val confidence: Float = 0f,
    val lastStableGear: Int? = null,
    val observedKphPer1000Rpm: Double? = null,
    val relativeRatioError: Double? = null,
)

data class GearGuidance(
    val estimate: GearEstimate = GearEstimate(),
    val preferredGear: Int? = null,
    val shiftDirection: ShiftDirection = ShiftDirection.NONE,
    val targetRpm: Int? = null,
    val targetRpmRange: IntRange? = null,
    val confidence: Float = 0f,
    val revMatchGear: Int? = null,
    val revMatchConfidence: Float = 0f,
)

data class GearAdvisorInput(
    val speedKph: Double?,
    val engineRpm: Double?,
    val kinematicsTimestampMs: Long? = null,
    val clutchPressed: Boolean?,
    val brakePressed: Boolean?,
    val acceleratorPercent: Float?,
    val requestedTorqueNm: Int?,
    val reverseEngaged: Boolean?,
) {
    companion object {
        fun from(state: VehicleState): GearAdvisorInput = GearAdvisorInput(
            speedKph = if (state.isSpeedSignalAvailable) {
                state.speedKphPrecise ?: state.speedKph.toDouble()
            } else {
                null
            },
            engineRpm = if (state.isEngineRpmSignalAvailable) {
                state.engineRpmPrecise ?: state.engineRpm.toDouble()
            } else {
                null
            },
            kinematicsTimestampMs = state.kinematicsSampleTimestampMs,
            clutchPressed = state.isClutchPedalPressed
                .takeIf { state.isClutchPedalSignalAvailable },
            brakePressed = state.isBrakePedalPressed
                .takeIf { state.isBrakePedalSignalAvailable },
            acceleratorPercent = state.acceleratorPedalPercent,
            requestedTorqueNm = state.requestedEngineTorqueNm,
            reverseEngaged = state.isReverseGearEngaged
                .takeIf { state.isReverseGearSignalAvailable },
        )
    }
}

/**
 * Factory drivetrain data for the 78 kW K9K engine and TL4-001 six-speed
 * transmission used by the Mégane II 1.5 dCi 105/106.
 *
 * Values are vehicle speed in km/h at 1,000 engine rpm. Keeping this table in
 * the domain layer lets both gear inference and clutch rev matching use exactly
 * the same physical model.
 */
object MeganeIiK9kTl4001Profile {
    val kphPer1000Rpm: List<Double> = listOf(
        8.34,
        15.96,
        23.50,
        31.88,
        40.73,
        48.70,
    )

    const val MAX_ENGINE_TORQUE_NM = 240.0
    const val MAX_FORWARD_GEAR = 6

    fun predictedRpm(speedKph: Double, gear: Int): Double? =
        kphPer1000Rpm.getOrNull(gear - 1)
            ?.let { speedKph.coerceAtLeast(0.0) / it * 1_000.0 }
}

internal data class PreferredGearResult(
    val gear: Int,
    val targetRpm: Int,
    val targetRange: IntRange,
    val score: Double,
    val confidence: Float,
)

/**
 * Pure recommendation model. It favours economical cruising near the useful
 * diesel torque band, but progressively raises the target RPM as requested
 * engine load or acceleration increases.
 */
internal object GearRecommendationCalculator {
    private data class ScoredGear(
        val gear: Int,
        val predictedRpm: Double,
        val score: Double,
    )

    fun calculate(
        speedKph: Double,
        load: Double,
    ): PreferredGearResult {
        val boundedLoad = load.coerceIn(0.0, 1.0)
        if (speedKph < 2.0) {
            return PreferredGearResult(
                gear = 1,
                targetRpm = 1_480,
                targetRange = 1_310..1_650,
                score = 0.0,
                confidence = 0.96f,
            )
        }

        val secondGearRpm =
            MeganeIiK9kTl4001Profile.predictedRpm(speedKph, 2) ?: 0.0
        val firstCandidateGear =
            if (secondGearRpm >= minimumSecondGearRpm(boundedLoad)) 2 else 1
        val candidates = (
            firstCandidateGear..
                MeganeIiK9kTl4001Profile.MAX_FORWARD_GEAR
            )
            .mapNotNull { gear -> scoreGear(speedKph, load, gear) }

        val selected = candidates.minByOrNull { it.score }
            ?: ScoredGear(
                gear = 1,
                predictedRpm =
                    (
                        MeganeIiK9kTl4001Profile.predictedRpm(speedKph, 1)
                            ?: 1_480.0
                        ).coerceAtLeast(850.0),
                score = 2.0,
            )
        val selectedRpm = selected.predictedRpm.roundToInt()
        val rangeHalfWidth = (140.0 + 150.0 * boundedLoad).roundToInt()
        return PreferredGearResult(
            gear = selected.gear,
            targetRpm = selectedRpm,
            targetRange = (selectedRpm - rangeHalfWidth)..(selectedRpm + rangeHalfWidth),
            score = selected.score,
            confidence = (1.0 - selected.score / 1.45).coerceIn(0.35, 0.98).toFloat(),
        )
    }

    fun score(
        speedKph: Double,
        load: Double,
        gear: Int,
    ): Double? = scoreGear(speedKph, load, gear)?.score

    fun evaluate(
        speedKph: Double,
        load: Double,
        gear: Int,
    ): PreferredGearResult? {
        val scored = scoreGear(speedKph, load, gear) ?: return null
        val boundedLoad = load.coerceIn(0.0, 1.0)
        val rpm = scored.predictedRpm.roundToInt()
        val rangeHalfWidth = (140.0 + 150.0 * boundedLoad).roundToInt()
        return PreferredGearResult(
            gear = gear,
            targetRpm = rpm,
            targetRange = (rpm - rangeHalfWidth)..(rpm + rangeHalfWidth),
            score = scored.score,
            confidence =
                (1.0 - scored.score / 1.45).coerceIn(0.35, 0.98).toFloat(),
        )
    }

    fun shouldUseSecondWhileRolling(
        speedKph: Double,
        load: Double,
    ): Boolean {
        val secondGearRpm =
            MeganeIiK9kTl4001Profile.predictedRpm(speedKph, 2) ?: return false
        return load < ROLLING_SECOND_MAX_LOAD &&
            secondGearRpm >= ROLLING_SECOND_MIN_RPM
    }

    private fun minimumSecondGearRpm(load: Double): Double =
        MIN_SECOND_GEAR_RPM + SECOND_GEAR_LOAD_RPM_SPAN * load

    private fun scoreGear(
        speedKph: Double,
        load: Double,
        gear: Int,
    ): ScoredGear? {
        val predictedRpm =
            MeganeIiK9kTl4001Profile.predictedRpm(speedKph, gear)
                ?: return null
        if (predictedRpm !in 800.0..4_300.0) return null

        val boundedLoad = load.coerceIn(0.0, 1.0)
        val minimumRpm = 1_250.0 + 500.0 * boundedLoad
        val targetRpm = 1_450.0 + 900.0 * boundedLoad
        val maximumRpm = 2_200.0 + 1_600.0 * boundedLoad
        val lowRpmPenalty =
            3.0 * (max(0.0, minimumRpm - predictedRpm) / minimumRpm).pow(2)
        val highRpmPenalty =
            1.5 * (max(0.0, predictedRpm - maximumRpm) / maximumRpm).pow(2)
        val economyBias =
            0.045 * (MeganeIiK9kTl4001Profile.MAX_FORWARD_GEAR - gear) *
                (1.0 - boundedLoad).pow(2)
        val score =
            abs(ln(predictedRpm / targetRpm)) +
                lowRpmPenalty +
                highRpmPenalty +
                economyBias
        return ScoredGear(
            gear = gear,
            predictedRpm = predictedRpm,
            score = score,
        )
    }

    private const val MIN_SECOND_GEAR_RPM = 1_050.0
    private const val SECOND_GEAR_LOAD_RPM_SPAN = 500.0
    private const val ROLLING_SECOND_MIN_RPM = 900.0
    private const val ROLLING_SECOND_MAX_LOAD = 0.40
}

/**
 * Stateful gear observer and shift advisor.
 *
 * A single speed/RPM sample never changes the inferred gear. Ratio matching,
 * dwell times, post-clutch cooldown and preferred-gear hysteresis suppress the
 * flicker that would otherwise be produced by CAN quantisation and clutch slip.
 */
class GearAdvisor {
    private var stableGear: Int? = null
    private var ratioCandidateGear: Int? = null
    private var ratioCandidateSinceMs: Long = 0L
    private var ratioCandidateSampleCount = 0
    private var lastRatioSampleTimestampMs: Long? = null
    private var lastCoupledAtMs = 0L
    private var preferredGear: Int? = null
    private var preferredCandidateGear: Int? = null
    private var preferredCandidateSinceMs: Long = 0L
    private var preferredScore = Double.POSITIVE_INFINITY
    private var lastCoupledConfidence = 0f
    private var latchedShiftGear: Int? = null
    private var shiftLatchUntilMs = 0L
    private var shiftLatchWasCoupled = false
    private var arrowCandidateDirection = ShiftDirection.NONE
    private var arrowCandidateSinceMs = 0L
    private var wasClutchPressed = false
    private var postClutchCooldownUntilMs = 0L
    private var lastKinematicTimestampMs: Long? = null
    private var lastSpeedKph: Double? = null
    private var filteredAccelerationMps2 = 0.0
    private var filteredLoad: Double? = null
    private var lastUpdateTimestampMs: Long? = null

    fun reset() {
        stableGear = null
        ratioCandidateGear = null
        ratioCandidateSinceMs = 0L
        ratioCandidateSampleCount = 0
        lastRatioSampleTimestampMs = null
        lastCoupledAtMs = 0L
        preferredGear = null
        preferredCandidateGear = null
        preferredCandidateSinceMs = 0L
        preferredScore = Double.POSITIVE_INFINITY
        lastCoupledConfidence = 0f
        latchedShiftGear = null
        shiftLatchUntilMs = 0L
        shiftLatchWasCoupled = false
        arrowCandidateDirection = ShiftDirection.NONE
        arrowCandidateSinceMs = 0L
        wasClutchPressed = false
        postClutchCooldownUntilMs = 0L
        lastKinematicTimestampMs = null
        lastSpeedKph = null
        filteredAccelerationMps2 = 0.0
        filteredLoad = null
        lastUpdateTimestampMs = null
    }

    fun update(
        input: GearAdvisorInput,
        nowMs: Long,
    ): GearGuidance {
        if (lastUpdateTimestampMs?.let { nowMs < it } == true) reset()
        lastUpdateTimestampMs = nowMs

        val speed = input.speedKph?.takeIf { it.isFinite() && it >= 0.0 }
        val rpm = input.engineRpm?.takeIf { it.isFinite() && it >= 0.0 }
        updateAcceleration(speed, nowMs)
        val load = updateLoad(input, speed)

        if (input.clutchPressed == true) {
            if (!wasClutchPressed) {
                shiftLatchWasCoupled = isCouplingFresh(nowMs)
                if (!shiftLatchWasCoupled) expireStaleCoupling()
                latchedShiftGear = preferredGear
                shiftLatchUntilMs = nowMs + SHIFT_TARGET_LATCH_MS
            }
            wasClutchPressed = true
        } else if (input.clutchPressed == false && wasClutchPressed) {
            wasClutchPressed = false
            postClutchCooldownUntilMs = nowMs + POST_CLUTCH_COOLDOWN_MS
            latchedShiftGear = null
            shiftLatchUntilMs = 0L
            shiftLatchWasCoupled = false
            ratioCandidateGear = null
            ratioCandidateSampleCount = 0
        } else if (input.clutchPressed == false && !isCouplingFresh(nowMs)) {
            expireStaleCoupling()
        }

        val estimate = estimateGear(
            speedKph = speed,
            engineRpm = rpm,
            clutchPressed = input.clutchPressed == true,
            reverseEngaged = input.reverseEngaged == true,
            kinematicsTimestampMs = input.kinematicsTimestampMs,
            nowMs = nowMs,
        )

        if (input.reverseEngaged == true) {
            latchedShiftGear = null
            shiftLatchUntilMs = 0L
            shiftLatchWasCoupled = false
            return GearGuidance(estimate = estimate)
        }

        if (speed == null) {
            return GearGuidance(estimate = estimate)
        }

        val rawPreferred = GearRecommendationCalculator.calculate(speed, load)
        val preferSecondWhileRolling =
            rawPreferred.gear == 1 &&
                estimate.forwardGear?.let { it >= 2 } == true &&
                GearRecommendationCalculator.shouldUseSecondWhileRolling(
                    speedKph = speed,
                    load = load,
                )
        val contextualPreferred =
            if (preferSecondWhileRolling) {
                GearRecommendationCalculator.evaluate(
                    speedKph = speed,
                    load = load,
                    gear = 2,
                ) ?: rawPreferred
            } else {
                rawPreferred
            }
        val latchedPreference = latchedShiftGear.takeIf {
            input.clutchPressed == true && nowMs <= shiftLatchUntilMs
        }
        val acceptedPreferred = latchedPreference ?: if (preferSecondWhileRolling) {
            preferredGear = 2
            preferredScore = contextualPreferred.score
            preferredCandidateGear = null
            2
        } else {
            updatePreferredGear(
                raw = contextualPreferred,
                estimatedGear = estimate.forwardGear,
                speedKph = speed,
                load = load,
                nowMs = nowMs,
            )
        }
        val acceptedModel = if (acceptedPreferred == contextualPreferred.gear) {
            contextualPreferred
        } else {
            GearRecommendationCalculator.evaluate(
                speedKph = speed,
                load = load,
                gear = acceptedPreferred ?: contextualPreferred.gear,
            ) ?: contextualPreferred
        }

        val currentGear = estimate.forwardGear
        val requestedDirection = if (currentGear == null) {
            ShiftDirection.NONE
        } else {
            when {
                acceptedModel.gear > currentGear -> ShiftDirection.UP
                acceptedModel.gear < currentGear -> ShiftDirection.DOWN
                else -> ShiftDirection.NONE
            }
        }
        val directionDwell = when (requestedDirection) {
            ShiftDirection.UP ->
                if (currentGear == 1) {
                    FIRST_GEAR_EXIT_ARROW_DWELL_MS
                } else {
                    UPSHIFT_DWELL_MS
                }

            ShiftDirection.DOWN ->
                if (load >= 0.68) {
                    HIGH_LOAD_DOWNSHIFT_DWELL_MS
                } else {
                    DOWNSHIFT_DWELL_MS
                }

            ShiftDirection.NONE -> 0L
        }
        val arrowConditionsMet =
            input.clutchPressed == false &&
                input.brakePressed == false &&
                input.reverseEngaged == false &&
                nowMs >= postClutchCooldownUntilMs &&
                speed >= MIN_ARROW_SPEED_KPH &&
                estimate.status == GearEstimateStatus.COUPLED &&
                estimate.confidence >= MIN_ARROW_CONFIDENCE &&
                requestedDirection != ShiftDirection.NONE
        val direction = when {
            !arrowConditionsMet -> {
                arrowCandidateDirection = ShiftDirection.NONE
                ShiftDirection.NONE
            }

            arrowCandidateDirection != requestedDirection -> {
                arrowCandidateDirection = requestedDirection
                arrowCandidateSinceMs = nowMs
                ShiftDirection.NONE
            }

            nowMs - arrowCandidateSinceMs >= directionDwell ->
                requestedDirection

            else -> ShiftDirection.NONE
        }

        val guidanceConfidence = if (estimate.forwardGear == null || speed < MIN_ARROW_SPEED_KPH) {
            acceptedModel.confidence
        } else {
            minOf(acceptedModel.confidence, estimate.confidence)
        }
        val revMatchAvailable =
            speed >= MIN_ARROW_SPEED_KPH &&
                input.brakePressed == false &&
                input.reverseEngaged == false &&
                (
                    (
                        estimate.status == GearEstimateStatus.COUPLED &&
                            input.clutchPressed == false
                        ) ||
                        (
                            input.clutchPressed == true &&
                                shiftLatchWasCoupled &&
                                nowMs <= shiftLatchUntilMs
                            )
                    )
        val revMatchConfidence = if (revMatchAvailable) {
            minOf(
                acceptedModel.confidence,
                if (estimate.status == GearEstimateStatus.COUPLED) {
                    estimate.confidence
                } else {
                    lastCoupledConfidence
                },
            )
        } else {
            0f
        }
        return GearGuidance(
            estimate = estimate,
            preferredGear = acceptedModel.gear,
            shiftDirection = direction,
            targetRpm = acceptedModel.targetRpm,
            targetRpmRange = acceptedModel.targetRange,
            confidence = guidanceConfidence,
            revMatchGear = acceptedModel.gear.takeIf {
                revMatchAvailable && revMatchConfidence >= MIN_REV_MATCH_CONFIDENCE
            },
            revMatchConfidence = revMatchConfidence,
        )
    }

    private fun isCouplingFresh(nowMs: Long): Boolean =
        stableGear != null &&
            nowMs >= lastCoupledAtMs &&
            nowMs - lastCoupledAtMs <= COUPLING_FRESHNESS_MS

    private fun expireStaleCoupling() {
        stableGear = null
        lastCoupledAtMs = 0L
        lastCoupledConfidence = 0f
    }

    private fun estimateGear(
        speedKph: Double?,
        engineRpm: Double?,
        clutchPressed: Boolean,
        reverseEngaged: Boolean,
        kinematicsTimestampMs: Long?,
        nowMs: Long,
    ): GearEstimate {
        if (reverseEngaged) {
            ratioCandidateGear = null
            stableGear = null
            return GearEstimate(
                status = GearEstimateStatus.REVERSE,
                lastStableGear = null,
            )
        }
        if (speedKph == null || engineRpm == null) {
            ratioCandidateGear = null
            return GearEstimate(
                status = GearEstimateStatus.SIGNAL_UNAVAILABLE,
                lastStableGear = stableGear,
            )
        }
        if (speedKph < STATIONARY_SPEED_KPH) {
            ratioCandidateGear = null
            stableGear = null
            return GearEstimate(status = GearEstimateStatus.STATIONARY)
        }
        if (clutchPressed) {
            ratioCandidateGear = null
            return GearEstimate(
                status = GearEstimateStatus.CLUTCH_DISENGAGED,
                lastStableGear = stableGear,
            )
        }
        if (engineRpm < MIN_RATIO_RPM) {
            ratioCandidateGear = null
            return GearEstimate(
                status = GearEstimateStatus.AMBIGUOUS,
                lastStableGear = stableGear,
            )
        }

        val observedRatio = speedKph * 1_000.0 / engineRpm
        val nearest = MeganeIiK9kTl4001Profile.kphPer1000Rpm
            .withIndex()
            .minByOrNull { abs(observedRatio - it.value) }
            ?: return GearEstimate(
                status = GearEstimateStatus.SIGNAL_UNAVAILABLE,
                lastStableGear = stableGear,
            )
        val gear = nearest.index + 1
        val error = abs(observedRatio - nearest.value) / nearest.value
        val sampleTimestampMs = kinematicsTimestampMs ?: nowMs
        val isNewSample = sampleTimestampMs != lastRatioSampleTimestampMs
        if (isNewSample) lastRatioSampleTimestampMs = sampleTimestampMs
        if (error > MAX_RELATIVE_RATIO_ERROR) {
            ratioCandidateGear = null
            ratioCandidateSampleCount = 0
            return GearEstimate(
                status = GearEstimateStatus.AMBIGUOUS,
                lastStableGear = stableGear,
                observedKphPer1000Rpm = observedRatio,
                relativeRatioError = error,
            )
        }
        if (error > MAX_COUPLED_RATIO_ERROR) {
            ratioCandidateGear = null
            ratioCandidateSampleCount = 0
            return GearEstimate(
                status = GearEstimateStatus.SHIFTING,
                lastStableGear = stableGear,
                observedKphPer1000Rpm = observedRatio,
                relativeRatioError = error,
            )
        }

        if (stableGear == gear && nowMs - lastCoupledAtMs <= COUPLED_REACQUIRE_HOLD_MS) {
            ratioCandidateGear = gear
            ratioCandidateSinceMs = nowMs
            ratioCandidateSampleCount = 0
        } else {
            if (stableGear == gear) stableGear = null
        }
        if (stableGear != gear && ratioCandidateGear != gear) {
            ratioCandidateGear = gear
            ratioCandidateSinceMs = nowMs
            ratioCandidateSampleCount = if (isNewSample) 1 else 0
        } else if (stableGear != gear && isNewSample) {
            ratioCandidateSampleCount += 1
            val dwell = if (speedKph < 8.0) {
                LOW_SPEED_GEAR_DWELL_MS
            } else {
                GEAR_DWELL_MS
            }
            if (nowMs - ratioCandidateSinceMs >= dwell &&
                ratioCandidateSampleCount >= MIN_GEAR_SAMPLES
            ) {
                stableGear = gear
            }
        }

        val coupled = stableGear == gear
        val confidence =
            (1.0 - error / MAX_COUPLED_RATIO_ERROR * 0.25)
                .coerceIn(0.75, 1.0)
                .toFloat()
        if (coupled) {
            lastCoupledConfidence = confidence
            lastCoupledAtMs = nowMs
        }
        return GearEstimate(
            forwardGear = gear.takeIf { coupled },
            status = if (coupled) {
                GearEstimateStatus.COUPLED
            } else {
                GearEstimateStatus.SHIFTING
            },
            confidence = confidence.takeIf { coupled } ?: 0f,
            lastStableGear = stableGear,
            observedKphPer1000Rpm = observedRatio,
            relativeRatioError = error,
        )
    }

    private fun updatePreferredGear(
        raw: PreferredGearResult,
        estimatedGear: Int?,
        speedKph: Double,
        load: Double,
        nowMs: Long,
    ): Int? {
        if (speedKph < STATIONARY_SPEED_KPH || preferredGear == null) {
            preferredGear = raw.gear
            preferredScore = raw.score
            preferredCandidateGear = null
            return preferredGear
        }
        if (raw.gear == preferredGear) {
            preferredCandidateGear = null
            preferredScore = raw.score
            return preferredGear
        }

        if (preferredCandidateGear != raw.gear) {
            preferredCandidateGear = raw.gear
            preferredCandidateSinceMs = nowMs
            return preferredGear
        }

        val isDownshift =
            estimatedGear?.let { raw.gear < it } == true ||
                raw.gear < (preferredGear ?: raw.gear)
        val dwell = when {
            estimatedGear == 1 && raw.gear > 1 ->
                FIRST_GEAR_EXIT_PREFERENCE_DWELL_MS

            isDownshift && load >= 0.68 -> HIGH_LOAD_DOWNSHIFT_DWELL_MS
            isDownshift -> DOWNSHIFT_DWELL_MS
            else -> UPSHIFT_DWELL_MS
        }
        val currentPreferredRpm = preferredGear
            ?.let { MeganeIiK9kTl4001Profile.predictedRpm(speedKph, it) }
        val currentScore = preferredGear
            ?.let { GearRecommendationCalculator.score(speedKph, load, it) }
            ?: preferredScore
        val hasUsefulAdvantage =
            raw.score + MIN_SCORE_ADVANTAGE <= currentScore ||
                currentPreferredRpm == null ||
                currentPreferredRpm !in 800.0..4_300.0
        if (nowMs - preferredCandidateSinceMs >= dwell && hasUsefulAdvantage) {
            preferredGear = raw.gear
            preferredScore = raw.score
            preferredCandidateGear = null
        }
        return preferredGear
    }

    private fun updateAcceleration(
        speedKph: Double?,
        nowMs: Long,
    ) {
        if (speedKph == null) return
        val previousTime = lastKinematicTimestampMs
        val previousSpeed = lastSpeedKph
        if (previousTime == null || previousSpeed == null) {
            lastKinematicTimestampMs = nowMs
            lastSpeedKph = speedKph
            return
        }
        val elapsedMs = nowMs - previousTime
        if (elapsedMs < MIN_ACCELERATION_SAMPLE_MS) return
        if (elapsedMs > MAX_ACCELERATION_SAMPLE_MS) {
            lastKinematicTimestampMs = nowMs
            lastSpeedKph = speedKph
            filteredAccelerationMps2 = 0.0
            return
        }

        val acceleration =
            ((speedKph - previousSpeed) / 3.6) / (elapsedMs / 1_000.0)
        filteredAccelerationMps2 =
            filteredAccelerationMps2 * 0.72 + acceleration.coerceIn(-5.0, 5.0) * 0.28
        lastKinematicTimestampMs = nowMs
        lastSpeedKph = speedKph
    }

    private fun updateLoad(
        input: GearAdvisorInput,
        speedKph: Double?,
    ): Double {
        val throttleLoad = input.acceleratorPercent
            ?.toDouble()
            ?.let { ((it - 5.0) / 75.0).coerceIn(0.0, 1.0) }
        val torqueLoad = input.requestedTorqueNm
            ?.toDouble()
            ?.let {
                (max(0.0, it) / MeganeIiK9kTl4001Profile.MAX_ENGINE_TORQUE_NM)
                    .coerceIn(0.0, 1.0)
            }
        val accelerationLoad =
            ((filteredAccelerationMps2 - 0.05) / 1.20).coerceIn(0.0, 1.0)
        val measured = listOfNotNull(throttleLoad, torqueLoad)
            .maxOrNull()
        val rawLoad = max(
            measured ?: if ((speedKph ?: 0.0) < 5.0) 0.22 else 0.12,
            accelerationLoad,
        )
        val smoothed = filteredLoad?.let { it * 0.74 + rawLoad * 0.26 } ?: rawLoad
        filteredLoad = smoothed
        return smoothed
    }

    private companion object {
        const val STATIONARY_SPEED_KPH = 2.0
        const val MIN_RATIO_RPM = 700.0
        const val MAX_RELATIVE_RATIO_ERROR = 0.08
        const val MAX_COUPLED_RATIO_ERROR = 0.05
        const val MIN_ARROW_SPEED_KPH = 5.0
        const val MIN_ARROW_CONFIDENCE = 0.72f
        const val MIN_REV_MATCH_CONFIDENCE = 0.65f
        const val GEAR_DWELL_MS = 320L
        const val LOW_SPEED_GEAR_DWELL_MS = 500L
        const val COUPLED_REACQUIRE_HOLD_MS = 650L
        const val COUPLING_FRESHNESS_MS = 1_200L
        const val MIN_GEAR_SAMPLES = 3
        const val POST_CLUTCH_COOLDOWN_MS = 420L
        const val SHIFT_TARGET_LATCH_MS = 2_500L
        const val FIRST_GEAR_EXIT_PREFERENCE_DWELL_MS = 250L
        const val FIRST_GEAR_EXIT_ARROW_DWELL_MS = 150L
        const val UPSHIFT_DWELL_MS = 760L
        const val DOWNSHIFT_DWELL_MS = 520L
        const val HIGH_LOAD_DOWNSHIFT_DWELL_MS = 300L
        const val MIN_SCORE_ADVANTAGE = 0.015
        const val MIN_ACCELERATION_SAMPLE_MS = 80L
        const val MAX_ACCELERATION_SAMPLE_MS = 1_200L
    }
}
