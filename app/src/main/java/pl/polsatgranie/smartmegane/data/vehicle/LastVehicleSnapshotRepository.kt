package pl.polsatgranie.smartmegane.data.vehicle

import android.content.Context
import pl.polsatgranie.smartmegane.domain.vehicle.VehicleState

/** Stores slow-changing telemetry so it remains visible without the CAN adapter. */
class LastVehicleSnapshotRepository(context: Context) {
    private companion object {
        const val PREFERENCES = "last_vehicle_snapshot"
        const val SAVED_AT = "saved_at"
        const val FUEL_RAW = "fuel_raw"
        const val FUEL_PERCENT = "fuel_percent"
        const val COOLANT = "coolant"
        const val OUTSIDE = "outside"
        const val ODOMETER = "odometer"
        const val VEHICLE_AGE = "vehicle_age"
    }

    private val preferences =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun save(state: VehicleState) {
        if (!state.isFuelLevelSignalAvailable &&
            !state.isCoolantTemperatureSignalAvailable &&
            state.outsideTemperatureCelsius == null &&
            !state.isOdometerSignalAvailable
        ) return

        preferences.edit().apply {
            putLong(SAVED_AT, System.currentTimeMillis())
            if (state.isFuelLevelSignalAvailable) {
                state.fuelLevelRaw?.let { putInt(FUEL_RAW, it) }
                state.fuelLevelEstimatedPercent?.let { putFloat(FUEL_PERCENT, it) }
            }
            if (state.isCoolantTemperatureSignalAvailable) {
                putInt(COOLANT, state.coolantTemperatureCelsius)
            }
            state.outsideTemperatureCelsius?.let { putInt(OUTSIDE, it) }
            if (state.isOdometerSignalAvailable) putLong(ODOMETER, state.odometerKm)
            state.vehicleAgeMinutes?.let { putLong(VEHICLE_AGE, it) }
        }.apply()
    }

    fun load(): VehicleState? {
        if (!preferences.contains(SAVED_AT)) return null
        val hasFuel = preferences.contains(FUEL_RAW) || preferences.contains(FUEL_PERCENT)
        val hasCoolant = preferences.contains(COOLANT)
        val hasOdometer = preferences.contains(ODOMETER)
        val fuelPercent = preferences.getFloat(FUEL_PERCENT, 0f)
        return VehicleState(
            fuelLevelPercent = fuelPercent.toInt(),
            fuelLevelEstimatedPercent = fuelPercent.takeIf { hasFuel },
            fuelLevelRaw = preferences.getInt(FUEL_RAW, 0).takeIf { hasFuel },
            isFuelLevelSignalAvailable = hasFuel,
            coolantTemperatureCelsius = preferences.getInt(COOLANT, 0),
            isCoolantTemperatureSignalAvailable = hasCoolant,
            outsideTemperatureCelsius = preferences.getInt(OUTSIDE, 0)
                .takeIf { preferences.contains(OUTSIDE) },
            odometerKm = preferences.getLong(ODOMETER, 0L),
            isOdometerSignalAvailable = hasOdometer,
            vehicleAgeMinutes = preferences.getLong(VEHICLE_AGE, 0L)
                .takeIf { preferences.contains(VEHICLE_AGE) },
        )
    }
}
