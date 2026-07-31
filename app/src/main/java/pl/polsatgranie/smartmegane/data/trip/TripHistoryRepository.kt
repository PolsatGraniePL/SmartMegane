package pl.polsatgranie.smartmegane.data.trip

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import pl.polsatgranie.smartmegane.domain.trip.TripSummary

class TripHistoryRepository(context: Context) {
    private companion object {
        const val PREFERENCES = "trip_history"
        const val KEY_TRIPS = "trips"
        const val MAX_TRIPS = 250
    }

    private val preferences =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun load(): List<TripSummary> {
        val raw = preferences.getString(KEY_TRIPS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    add(array.getJSONObject(index).toTripSummary())
                }
            }
        }.getOrDefault(emptyList())
            .sortedByDescending { it.startedAtEpochMs }
    }

    fun add(summary: TripSummary): List<TripSummary> {
        val updated = (listOf(summary) + load())
            .distinctBy { it.id }
            .sortedByDescending { it.startedAtEpochMs }
            .take(MAX_TRIPS)
        val array = JSONArray()
        updated.forEach { array.put(it.toJson()) }
        preferences.edit().putString(KEY_TRIPS, array.toString()).apply()
        return updated
    }

    private fun TripSummary.toJson() = JSONObject().apply {
        put("id", id)
        put("startedAt", startedAtEpochMs)
        put("endedAt", endedAtEpochMs)
        put("duration", durationMs)
        put("distance", distanceKm)
        put("fuel", fuelUsedLiters)
        put("consumption", averageConsumptionLitersPer100Km ?: JSONObject.NULL)
        put("averageSpeed", averageSpeedKph)
        put("averageMovingSpeed", averageMovingSpeedKph)
        put("maxSpeed", maxSpeedKph)
        put("movingDuration", movingDurationMs)
        put("idleDuration", idleDurationMs)
        put("averageRpm", averageRpm)
        put("maxRpm", maxRpm)
        putNullable("maxCoolant", maxCoolantTemperatureCelsius)
        putNullable("minOutside", minOutsideTemperatureCelsius)
        putNullable("maxOutside", maxOutsideTemperatureCelsius)
        putNullable("startFuelPercent", startFuelPercent)
        putNullable("endFuelPercent", endFuelPercent)
        putNullable("averageAccelerator", averageAcceleratorPercent)
        putNullable("maxAccelerator", maxAcceleratorPercent)
        put("hardAccelerationCount", hardAccelerationCount)
        put("hardBrakingCount", hardBrakingCount)
        putNullable("maxUphill", maxUphillDegrees)
        putNullable("maxDownhill", maxDownhillDegrees)
        put("startOdometer", startOdometerKm ?: JSONObject.NULL)
        put("endOdometer", endOdometerKm ?: JSONObject.NULL)
    }

    private fun JSONObject.putNullable(key: String, value: Number?) {
        put(key, value ?: JSONObject.NULL)
    }

    private fun JSONObject.toTripSummary() = TripSummary(
        id = getLong("id"),
        startedAtEpochMs = getLong("startedAt"),
        endedAtEpochMs = getLong("endedAt"),
        durationMs = getLong("duration"),
        distanceKm = getDouble("distance"),
        fuelUsedLiters = getDouble("fuel"),
        averageConsumptionLitersPer100Km =
            optDouble("consumption").takeUnless { it.isNaN() },
        averageSpeedKph = getDouble("averageSpeed"),
        averageMovingSpeedKph = optDouble("averageMovingSpeed", 0.0),
        maxSpeedKph = getDouble("maxSpeed"),
        movingDurationMs = optLong("movingDuration", 0L),
        idleDurationMs = optLong("idleDuration", 0L),
        averageRpm = optDouble("averageRpm", 0.0),
        maxRpm = optInt("maxRpm", 0),
        maxCoolantTemperatureCelsius = optNullableInt("maxCoolant"),
        minOutsideTemperatureCelsius = optNullableInt("minOutside"),
        maxOutsideTemperatureCelsius = optNullableInt("maxOutside"),
        startFuelPercent = optNullableDouble("startFuelPercent")?.toFloat(),
        endFuelPercent = optNullableDouble("endFuelPercent")?.toFloat(),
        averageAcceleratorPercent = optNullableDouble("averageAccelerator")?.toFloat(),
        maxAcceleratorPercent = optNullableDouble("maxAccelerator")?.toFloat(),
        hardAccelerationCount = optInt("hardAccelerationCount", 0),
        hardBrakingCount = optInt("hardBrakingCount", 0),
        maxUphillDegrees = optNullableDouble("maxUphill")?.toFloat(),
        maxDownhillDegrees = optNullableDouble("maxDownhill")?.toFloat(),
        startOdometerKm =
            optLong("startOdometer").takeIf { has("startOdometer") && !isNull("startOdometer") },
        endOdometerKm =
            optLong("endOdometer").takeIf { has("endOdometer") && !isNull("endOdometer") },
    )

    private fun JSONObject.optNullableInt(key: String): Int? =
        optInt(key).takeIf { has(key) && !isNull(key) }

    private fun JSONObject.optNullableDouble(key: String): Double? =
        optDouble(key).takeIf { has(key) && !isNull(key) && !it.isNaN() }
}
