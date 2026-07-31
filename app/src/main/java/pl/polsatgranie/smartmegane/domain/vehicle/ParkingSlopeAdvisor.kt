package pl.polsatgranie.smartmegane.domain.vehicle

import kotlin.math.abs

enum class ParkingSlopeLevel { UNAVAILABLE, FLAT, GEAR_RECOMMENDED, STEEP }

enum class ParkingGearAdvice { NONE, FIRST, REVERSE }

data class ParkingSlopeGuidance(
    val level: ParkingSlopeLevel = ParkingSlopeLevel.UNAVAILABLE,
    val longitudinalLevel: ParkingSlopeLevel = ParkingSlopeLevel.UNAVAILABLE,
    val lateralLevel: ParkingSlopeLevel = ParkingSlopeLevel.UNAVAILABLE,
    /** Positive means the nose of the vehicle is higher than the rear. */
    val vehiclePitchDegrees: Float = 0f,
    /** Positive means the right side of the vehicle is higher. */
    val vehicleRollDegrees: Float = 0f,
    val recommendedGear: ParkingGearAdvice = ParkingGearAdvice.NONE,
    val isParkingBrakeApplied: Boolean = false,
) {
    val isAvailable: Boolean get() = level != ParkingSlopeLevel.UNAVAILABLE
}

/**
 * In the fixed landscape mount Android reports level ground as pitch=0° and
 * roll=-45°. The physical phone X axis therefore arrives as Android roll and
 * describes the vehicle's longitudinal incline; Android pitch describes the
 * lateral incline.
 */
object ParkingSlopeAdvisor {
    private const val FLAT_LONGITUDINAL_SENSOR_DEGREES = -45.0f
    private const val FLAT_LATERAL_SENSOR_DEGREES = 0.0f
    private const val GEAR_THRESHOLD_DEGREES = 5.0f
    private const val STEEP_THRESHOLD_DEGREES = 15.0f

    fun evaluate(
        phonePitchDegrees: Float?,
        phoneRollDegrees: Float? = null,
        parkingBrakeApplied: Boolean,
    ): ParkingSlopeGuidance {
        val vehiclePitch = vehiclePitchDegrees(phoneRollDegrees)
            ?: return ParkingSlopeGuidance()
        val vehicleRoll = vehicleRollDegrees(phonePitchDegrees) ?: 0f
        val longitudinalLevel = classify(vehiclePitch)
        val lateralLevel = classify(vehicleRoll)
        val level = listOf(longitudinalLevel, lateralLevel).maxBy { it.ordinal }
        val gear = when {
            level == ParkingSlopeLevel.FLAT -> ParkingGearAdvice.NONE
            vehiclePitch > 0f -> ParkingGearAdvice.FIRST
            else -> ParkingGearAdvice.REVERSE
        }
        return ParkingSlopeGuidance(
            level = level,
            longitudinalLevel = longitudinalLevel,
            lateralLevel = lateralLevel,
            vehiclePitchDegrees = vehiclePitch,
            vehicleRollDegrees = vehicleRoll,
            recommendedGear = gear,
            isParkingBrakeApplied = parkingBrakeApplied,
        )
    }

    /** Converts Android roll into the car's front/rear road incline. */
    fun vehiclePitchDegrees(phoneRollDegrees: Float?): Float? = phoneRollDegrees
        ?.takeIf { it.isFinite() }
        ?.let { it - FLAT_LONGITUDINAL_SENSOR_DEGREES }

    /** Converts Android pitch into the car's left/right road incline. */
    fun vehicleRollDegrees(phonePitchDegrees: Float?): Float? = phonePitchDegrees
        ?.takeIf { it.isFinite() }
        ?.let { it - FLAT_LATERAL_SENSOR_DEGREES }

    private fun classify(angleDegrees: Float): ParkingSlopeLevel = when {
        abs(angleDegrees) < GEAR_THRESHOLD_DEGREES -> ParkingSlopeLevel.FLAT
        abs(angleDegrees) < STEEP_THRESHOLD_DEGREES -> ParkingSlopeLevel.GEAR_RECOMMENDED
        else -> ParkingSlopeLevel.STEEP
    }
}
