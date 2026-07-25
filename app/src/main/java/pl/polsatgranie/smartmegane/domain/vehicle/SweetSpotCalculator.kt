package pl.polsatgranie.smartmegane.domain.vehicle

import kotlin.math.roundToInt

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
 * Initial diesel-oriented model. Its constants are intentionally isolated here so
 * they can later be calibrated from real starts and enriched with gear/clutch data.
 */
object SweetSpotCalculator {
    fun calculate(speedKph: Int, engineRpm: Int): SweetSpotState {
        val speed = speedKph.coerceIn(0, 30)
        val rollingAssistance = (speed / 30f) * 420f
        val safeCenter = (1_520f - rollingAssistance).roundToInt()
        val safeHalfWidth = 185 + (speed * 2)
        val safeRange = (safeCenter - safeHalfWidth)..(safeCenter + safeHalfWidth)
        val warningRange = (safeRange.first - 360)..(safeRange.last + 430)
        val zone = when (engineRpm) {
            in safeRange -> ClutchReleaseZone.SWEET_SPOT
            in warningRange -> ClutchReleaseZone.JERK
            else -> ClutchReleaseZone.STALL
        }
        val confidence = when {
            speedKph <= 15 -> 1f
            speedKph <= 30 -> 0.72f
            else -> 0.45f
        }
        return SweetSpotState(
            currentRpm = engineRpm.coerceAtLeast(0),
            safeRange = safeRange,
            warningRange = warningRange,
            zone = zone,
            confidence = confidence,
        )
    }
}
