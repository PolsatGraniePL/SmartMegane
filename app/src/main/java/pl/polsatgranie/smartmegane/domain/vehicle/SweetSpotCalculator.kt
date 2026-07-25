package pl.polsatgranie.smartmegane.domain.vehicle

import kotlin.math.roundToInt
import kotlin.math.max

enum class ClutchReleaseZone {
    STALL,
    JERK,
    SWEET_SPOT,
}

data class SweetSpotState(
    val currentRpm: Int,
    val safeRange: IntRange,
    val warningRange: IntRange,
    val zone: ClutchReleaseZone,
    val confidence: Float,
)

/**
 * Diesel-oriented clutch-release estimate.
 *
 * At a standstill it targets the RPM needed to provide useful 1.5 dCi torque.
 * While rolling it blends that launch target with the approximate first-gear
 * synchronisation RPM. The first-gear coefficient is intentionally isolated: it
 * should later be learned from captures made with the clutch fully released.
 *
 * This remains guidance, not a guarantee: road slope, selected gear, vehicle load
 * and the analogue clutch position are not currently available on CAN.
 */
object SweetSpotCalculator {
    private const val STANDSTILL_TARGET_RPM = 1_480.0
    private const val MIN_ROLLING_TARGET_RPM = 1_030.0
    private const val FIRST_GEAR_RPM_PER_KPH = 145.0

    fun calculate(
        speedKph: Int,
        engineRpm: Int,
    ): SweetSpotState = calculate(
        speedKph = speedKph.toDouble(),
        engineRpm = engineRpm.toDouble(),
        isClutchPressed = null,
    )

    fun calculate(
        speedKph: Double,
        engineRpm: Double,
        isClutchPressed: Boolean? = null,
    ): SweetSpotState {
        val speed = speedKph.coerceAtLeast(0.0)
        val rpm = engineRpm.coerceAtLeast(0.0)
        val launchAssistTarget =
            STANDSTILL_TARGET_RPM -
                (speed.coerceIn(0.0, 5.0) / 5.0) *
                (STANDSTILL_TARGET_RPM - MIN_ROLLING_TARGET_RPM)
        val firstGearSynchronisationTarget =
            speed.coerceAtMost(15.0) * FIRST_GEAR_RPM_PER_KPH
        val safeCenter = max(
            launchAssistTarget,
            firstGearSynchronisationTarget,
        ).roundToInt()
        val safeHalfWidth = when {
            speed < 0.3 -> 170
            speed <= 5.0 -> 190
            else -> 220
        }
        val safeRange = (safeCenter - safeHalfWidth)..(safeCenter + safeHalfWidth)
        val warningRange = (safeRange.first - 360)..(safeRange.last + 430)
        val roundedRpm = rpm.roundToInt()
        val zone = when {
            roundedRpm < warningRange.first -> ClutchReleaseZone.STALL
            roundedRpm in safeRange -> ClutchReleaseZone.SWEET_SPOT
            else -> ClutchReleaseZone.JERK
        }
        val speedConfidence = when {
            speed <= 5.0 -> 0.95f
            speed <= 10.0 -> 0.82f
            speed <= 15.0 -> 0.55f
            else -> 0.30f
        }
        val confidence = when (isClutchPressed) {
            true -> (speedConfidence + 0.05f).coerceAtMost(1f)
            false -> speedConfidence * 0.72f
            null -> speedConfidence
        }
        return SweetSpotState(
            currentRpm = roundedRpm,
            safeRange = safeRange,
            warningRange = warningRange,
            zone = zone,
            confidence = confidence,
        )
    }
}
