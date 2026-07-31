package pl.polsatgranie.smartmegane.data.phone

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import pl.polsatgranie.smartmegane.domain.phone.PhoneOrientation
import kotlin.math.PI

class PhoneOrientationDataSource(context: Context) : SensorEventListener {
    private companion object {
        const val FILTER_ALPHA = 0.16f
    }

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val orientationSensor =
        sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val rotationMatrix = FloatArray(9)
    private val angles = FloatArray(3)

    private var baseline: Triple<Float, Float, Float>? = null
    private var filtered: Triple<Float, Float, Float>? = null

    private val _orientation = MutableStateFlow(
        PhoneOrientation(isSensorAvailable = orientationSensor != null),
    )
    val orientation: StateFlow<PhoneOrientation> = _orientation.asStateFlow()

    fun start() {
        orientationSensor?.let {
            sensorManager.registerListener(
                this,
                it,
                SensorManager.SENSOR_DELAY_GAME,
            )
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    fun recalibrate() {
        baseline = filtered
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_GAME_ROTATION_VECTOR &&
            event.sensor.type != Sensor.TYPE_ROTATION_VECTOR
        ) {
            return
        }
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        SensorManager.getOrientation(rotationMatrix, angles)
        val raw = Triple(
            radiansToDegrees(angles[0]),
            radiansToDegrees(angles[1]),
            radiansToDegrees(angles[2]),
        )
        val smoothed = filtered?.let { previous ->
            Triple(
                smoothAngle(previous.first, raw.first),
                smoothAngle(previous.second, raw.second),
                smoothAngle(previous.third, raw.third),
            )
        } ?: raw
        filtered = smoothed
        val reference = baseline ?: smoothed.also { baseline = it }
        _orientation.value = PhoneOrientation(
            isSensorAvailable = true,
            azimuthDegrees = smoothed.first,
            pitchDegrees = smoothed.second,
            rollDegrees = smoothed.third,
            relativeYawDegrees = angleDifference(smoothed.first, reference.first),
            relativePitchDegrees = angleDifference(smoothed.second, reference.second),
            relativeRollDegrees = angleDifference(smoothed.third, reference.third),
            timestampNanos = event.timestamp,
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun smoothAngle(previous: Float, current: Float): Float =
        normalizeAngle(previous + angleDifference(current, previous) * FILTER_ALPHA)

    private fun angleDifference(value: Float, reference: Float): Float =
        normalizeAngle(value - reference)

    private fun normalizeAngle(value: Float): Float {
        var result = value
        while (result > 180f) result -= 360f
        while (result <= -180f) result += 360f
        return result
    }

    private fun radiansToDegrees(value: Float): Float =
        (value * 180f / PI.toFloat())
}
