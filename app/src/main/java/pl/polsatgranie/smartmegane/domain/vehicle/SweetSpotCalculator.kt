package pl.polsatgranie.smartmegane.domain.vehicle

import kotlin.math.roundToInt
import kotlin.math.max

enum class ClutchReleaseZone {
    STALL,
    JERK,
    SWEET_SPOT,
}

enum class SweetSpotMode {
    LAUNCH,
    REV_MATCH,
}

data class SweetSpotState(
    val currentRpm: Int,
    val targetRpm: Int,
    val safeRange: IntRange,
    val warningRange: IntRange,
    val zone: ClutchReleaseZone,
    val confidence: Float,
    val mode: SweetSpotMode,
)

/**
 * Diesel-oriented clutch-release estimate.
 *
 * At a standstill it targets the RPM needed to provide useful 1.5 dCi torque.
 * While rolling it blends that launch target with the approximate first-gear
 * synchronisation RPM. While moving, a preferred gear switches the calculation
 * to rev matching based on the factory TL4-001 transmission development.
 *
 * This remains guidance, not a guarantee: road slope, selected gear, vehicle load
 * and the analogue clutch position are not currently available on CAN.
 */
object SweetSpotCalculator {
    private const val STANDSTILL_TARGET_RPM = 1_480.0
    private const val MIN_ROLLING_TARGET_RPM = 1_030.0
    private const val FIRST_GEAR_RPM_PER_KPH = 1_000.0 / 8.34

    fun calculate(
        speedKph: Int,
        engineRpm: Int,
    ): SweetSpotState = calculate(
        speedKph = speedKph.toDouble(),
        engineRpm = engineRpm.toDouble(),
        isClutchPressed = null,
        preferredGear = null,
        guidanceConfidence = null,
    )

    fun calculate(
        speedKph: Double,
        engineRpm: Double,
        isClutchPressed: Boolean? = null,
        preferredGear: Int? = null,
        guidanceConfidence: Float? = null,
    ): SweetSpotState {
        val speed = speedKph.coerceAtLeast(0.0)
        val rpm = engineRpm.coerceAtLeast(0.0)
        val launchAssistTarget =
            STANDSTILL_TARGET_RPM -
                (speed.coerceIn(0.0, 5.0) / 5.0) *
                (STANDSTILL_TARGET_RPM - MIN_ROLLING_TARGET_RPM)
        val firstGearSynchronisationTarget =
            speed.coerceAtMost(15.0) * FIRST_GEAR_RPM_PER_KPH
        val revMatchTarget = preferredGear
            ?.takeIf { speed > 5.0 }
            ?.let { MeganeIiK9kTl4001Profile.predictedRpm(speed, it) }
            ?.coerceIn(850.0, 4_200.0)
        val mode = if (revMatchTarget == null) {
            SweetSpotMode.LAUNCH
        } else {
            SweetSpotMode.REV_MATCH
        }
        val safeCenter = (
            revMatchTarget ?: max(
                launchAssistTarget,
                firstGearSynchronisationTarget,
            )
            ).roundToInt()
        val safeHalfWidth = when (mode) {
            SweetSpotMode.REV_MATCH ->
                max(140.0, safeCenter * 0.08).roundToInt()

            SweetSpotMode.LAUNCH -> when {
                speed < 0.3 -> 170
                speed <= 5.0 -> 190
                else -> 220
            }
        }
        val safeRange = (safeCenter - safeHalfWidth)..(safeCenter + safeHalfWidth)
        val warningRange = when (mode) {
            SweetSpotMode.LAUNCH ->
                (safeRange.first - 360)..(safeRange.last + 430)

            SweetSpotMode.REV_MATCH -> {
                val margin = max(320.0, safeCenter * 0.17).roundToInt()
                (safeRange.first - margin)..(safeRange.last + margin)
            }
        }
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
        val baseConfidence = when (mode) {
            SweetSpotMode.LAUNCH -> speedConfidence
            SweetSpotMode.REV_MATCH -> guidanceConfidence ?: 0.74f
        }
        val confidence = when (isClutchPressed) {
            true -> (baseConfidence + 0.05f).coerceAtMost(1f)
            false -> baseConfidence * 0.72f
            null -> baseConfidence
        }
        return SweetSpotState(
            currentRpm = roundedRpm,
            targetRpm = safeCenter,
            safeRange = safeRange,
            warningRange = warningRange,
            zone = zone,
            confidence = confidence,
            mode = mode,
        )
    }
}
