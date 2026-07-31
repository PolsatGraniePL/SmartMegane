package pl.polsatgranie.smartmegane.domain.phone

data class PhoneOrientation(
    val isSensorAvailable: Boolean = false,
    val azimuthDegrees: Float = 0f,
    val pitchDegrees: Float = 0f,
    val rollDegrees: Float = 0f,
    val relativeYawDegrees: Float = 0f,
    val relativePitchDegrees: Float = 0f,
    val relativeRollDegrees: Float = 0f,
    val timestampNanos: Long = 0L,
)
