package pl.polsatgranie.smartmegane.domain.trip

import pl.polsatgranie.smartmegane.domain.vehicle.VehiclePowerState
import pl.polsatgranie.smartmegane.domain.vehicle.VehicleState

data class TripTrackerResult(
    val live: TripLiveStats,
    val completed: TripSummary? = null,
)

class TripTracker {
    private companion object {
        const val FUEL_COUNTER_CYCLE_LITERS = 256.0 / 12_500.0
        const val MIN_STORED_DURATION_MS = 20_000L
        const val MIN_STORED_DISTANCE_KM = 0.03
        const val MAX_SAMPLE_GAP_MS = 2_000L
        const val TRIP_END_DWELL_MS = 2_200L
        const val MOVING_SPEED_KPH = 1.0
        const val HARD_EVENT_COOLDOWN_MS = 2_000L
    }

    private var startedAtEpochMs: Long? = null
    private var startedAtMonoMs: Long? = null
    private var lastMonoMs: Long? = null
    private var engineStoppedCandidateSinceMs: Long? = null
    private var lastFuelCounterLiters: Double? = null
    private var lastFuelSampleTimestampMs: Long? = null
    private var lastFuelIncrementTimestampMs: Long? = null
    private var startOdometerKm: Long? = null
    private var distanceKm = 0.0
    private var fuelUsedLiters = 0.0
    private var maxSpeedKph = 0.0
    private var movingDurationMs = 0L
    private var idleDurationMs = 0L
    private var rpmTimeIntegral = 0.0
    private var rpmSampleDurationMs = 0L
    private var maxRpm = 0
    private var maxCoolantTemperatureCelsius: Int? = null
    private var minOutsideTemperatureCelsius: Int? = null
    private var maxOutsideTemperatureCelsius: Int? = null
    private var startFuelPercent: Float? = null
    private var currentFuelPercent: Float? = null
    private var acceleratorTimeIntegral = 0.0
    private var acceleratorSampleDurationMs = 0L
    private var maxAcceleratorPercent: Float? = null
    private var maxUphillDegrees: Float? = null
    private var maxDownhillDegrees: Float? = null
    private var filteredFuelLitersPerHour: Double? = null
    private var lastSpeedKph: Double? = null
    private var lastHardAccelerationAtMs: Long? = null
    private var lastHardBrakingAtMs: Long? = null
    private var hardAccelerationCount = 0
    private var hardBrakingCount = 0

    fun reset(): TripLiveStats {
        clear()
        return TripLiveStats()
    }

    /**
     * Finalizes an active trip when the telemetry stream disappears. Without
     * this explicit path there are no later watchdog ticks to satisfy the
     * normal engine-off dwell after USB/CAN is disconnected.
     */
    fun finishNow(
        state: VehicleState,
        monotonicNowMs: Long,
        epochNowMs: Long,
    ): TripSummary? = finish(state, monotonicNowMs, epochNowMs)

    fun update(
        state: VehicleState,
        monotonicNowMs: Long,
        epochNowMs: Long,
        roadPitchDegrees: Float? = null,
    ): TripTrackerResult {
        if (startedAtMonoMs == null &&
            state.powerState == VehiclePowerState.ENGINE_RUNNING
        ) {
            start(state, monotonicNowMs, epochNowMs)
        }

        val startedMono = startedAtMonoMs ?: return TripTrackerResult(TripLiveStats())
        if (state.powerState != VehiclePowerState.ENGINE_RUNNING) {
            val stoppedSince = engineStoppedCandidateSinceMs ?: monotonicNowMs.also {
                engineStoppedCandidateSinceMs = it
            }
            if (monotonicNowMs - stoppedSince >= TRIP_END_DWELL_MS) {
                val summary = finish(state, monotonicNowMs, epochNowMs)
                return TripTrackerResult(TripLiveStats(), summary)
            }
        } else {
            engineStoppedCandidateSinceMs = null
        }

        val last = lastMonoMs ?: monotonicNowMs
        val deltaMs = (monotonicNowMs - last).coerceIn(0L, MAX_SAMPLE_GAP_MS)
        val speed = (state.speedKphPrecise ?: state.speedKph.toDouble()).coerceAtLeast(0.0)
        if (deltaMs > 0L && state.powerState == VehiclePowerState.ENGINE_RUNNING) {
            distanceKm += speed * deltaMs / 3_600_000.0
            if (speed >= MOVING_SPEED_KPH) movingDurationMs += deltaMs else idleDurationMs += deltaMs
            collectTimeWeightedStats(state, deltaMs)
            collectDynamics(speed, deltaMs, monotonicNowMs)
        }
        maxSpeedKph = maxOf(maxSpeedKph, speed)
        collectExtremes(state, roadPitchDegrees)
        collectFuel(state)
        lastMonoMs = monotonicNowMs

        return TripTrackerResult(
            live = createLive(monotonicNowMs - startedMono, speed),
        )
    }

    private fun start(state: VehicleState, monoMs: Long, epochMs: Long) {
        startedAtMonoMs = monoMs
        startedAtEpochMs = epochMs
        lastMonoMs = monoMs
        lastFuelCounterLiters = state.fuelUsedSinceStartLiters
        lastFuelSampleTimestampMs = state.fuelCounterSampleTimestampMs
        lastFuelIncrementTimestampMs = state.fuelCounterSampleTimestampMs
        startOdometerKm = state.odometerKm.takeIf { it > 0L }
        startFuelPercent = state.fuelLevelEstimatedPercent
        currentFuelPercent = state.fuelLevelEstimatedPercent
    }

    private fun collectFuel(state: VehicleState) {
        val current = state.fuelUsedSinceStartLiters ?: return
        val sampleTimestamp = state.fuelCounterSampleTimestampMs ?: return
        if (sampleTimestamp == lastFuelSampleTimestampMs) return
        val previous = lastFuelCounterLiters
        if (previous != null) {
            var delta = current - previous
            if (delta < 0.0) delta += FUEL_COUNTER_CYCLE_LITERS
            if (delta > 0.0 && delta <= FUEL_COUNTER_CYCLE_LITERS) {
                fuelUsedLiters += delta
                val previousIncrement = lastFuelIncrementTimestampMs
                val elapsedMs = previousIncrement?.let { sampleTimestamp - it }
                if (elapsedMs != null && elapsedMs in 1..MAX_SAMPLE_GAP_MS) {
                    val measuredPerHour = delta * 3_600_000.0 / elapsedMs
                    filteredFuelLitersPerHour = filteredFuelLitersPerHour?.let {
                        it * 0.78 + measuredPerHour * 0.22
                    } ?: measuredPerHour
                }
                lastFuelIncrementTimestampMs = sampleTimestamp
            }
        }
        lastFuelCounterLiters = current
        lastFuelSampleTimestampMs = sampleTimestamp
    }

    private fun collectTimeWeightedStats(state: VehicleState, deltaMs: Long) {
        if (state.isEngineRpmSignalAvailable) {
            val rpm = state.engineRpmPrecise ?: state.engineRpm.toDouble()
            rpmTimeIntegral += rpm * deltaMs
            rpmSampleDurationMs += deltaMs
            maxRpm = maxOf(maxRpm, rpm.toInt())
        }
        state.acceleratorPedalPercent?.let { accelerator ->
            acceleratorTimeIntegral += accelerator * deltaMs
            acceleratorSampleDurationMs += deltaMs
            maxAcceleratorPercent = maxOf(maxAcceleratorPercent ?: accelerator, accelerator)
        }
    }

    private fun collectDynamics(speed: Double, deltaMs: Long, nowMs: Long) {
        val previous = lastSpeedKph
        if (previous != null && deltaMs >= 80L) {
            val acceleration = ((speed - previous) / 3.6) / (deltaMs / 1_000.0)
            if (acceleration >= 2.2 &&
                lastHardAccelerationAtMs?.let { nowMs - it >= HARD_EVENT_COOLDOWN_MS } != false
            ) {
                hardAccelerationCount += 1
                lastHardAccelerationAtMs = nowMs
            }
            if (acceleration <= -3.0 &&
                lastHardBrakingAtMs?.let { nowMs - it >= HARD_EVENT_COOLDOWN_MS } != false
            ) {
                hardBrakingCount += 1
                lastHardBrakingAtMs = nowMs
            }
        }
        lastSpeedKph = speed
    }

    private fun collectExtremes(state: VehicleState, pitch: Float?) {
        if (state.isCoolantTemperatureSignalAvailable) {
            maxCoolantTemperatureCelsius = maxOf(
                maxCoolantTemperatureCelsius ?: state.coolantTemperatureCelsius,
                state.coolantTemperatureCelsius,
            )
        }
        state.outsideTemperatureCelsius?.let {
            minOutsideTemperatureCelsius = minOf(minOutsideTemperatureCelsius ?: it, it)
            maxOutsideTemperatureCelsius = maxOf(maxOutsideTemperatureCelsius ?: it, it)
        }
        state.fuelLevelEstimatedPercent?.let { currentFuelPercent = it }
        pitch?.takeIf { it.isFinite() }?.let {
            maxUphillDegrees = maxOf(maxUphillDegrees ?: it, it)
            maxDownhillDegrees = minOf(maxDownhillDegrees ?: it, it)
        }
    }

    private fun finish(state: VehicleState, monoMs: Long, epochMs: Long): TripSummary? {
        val startedEpoch = startedAtEpochMs ?: return null
        val startedMono = startedAtMonoMs ?: return null
        val duration = (monoMs - startedMono).coerceAtLeast(0L)
        val live = createLive(duration, state.speedKphPrecise ?: state.speedKph.toDouble())
        val summary = TripSummary(
            id = startedEpoch,
            startedAtEpochMs = startedEpoch,
            endedAtEpochMs = epochMs,
            durationMs = duration,
            distanceKm = distanceKm,
            fuelUsedLiters = fuelUsedLiters,
            averageConsumptionLitersPer100Km = live.averageConsumptionLitersPer100Km,
            averageSpeedKph = live.averageSpeedKph,
            averageMovingSpeedKph = live.averageMovingSpeedKph,
            maxSpeedKph = maxSpeedKph,
            movingDurationMs = movingDurationMs,
            idleDurationMs = idleDurationMs,
            averageRpm = live.averageRpm,
            maxRpm = maxRpm,
            maxCoolantTemperatureCelsius = maxCoolantTemperatureCelsius,
            minOutsideTemperatureCelsius = minOutsideTemperatureCelsius,
            maxOutsideTemperatureCelsius = maxOutsideTemperatureCelsius,
            startFuelPercent = startFuelPercent,
            endFuelPercent = currentFuelPercent,
            averageAcceleratorPercent = live.averageAcceleratorPercent,
            maxAcceleratorPercent = maxAcceleratorPercent,
            hardAccelerationCount = hardAccelerationCount,
            hardBrakingCount = hardBrakingCount,
            maxUphillDegrees = maxUphillDegrees,
            maxDownhillDegrees = maxDownhillDegrees,
            startOdometerKm = startOdometerKm,
            endOdometerKm = state.odometerKm.takeIf { it > 0L },
        ).takeIf { duration >= MIN_STORED_DURATION_MS || distanceKm >= MIN_STORED_DISTANCE_KM }
        clear()
        return summary
    }

    private fun createLive(durationMs: Long, speedKph: Double): TripLiveStats {
        val fuelPerHour = filteredFuelLitersPerHour
        return TripLiveStats(
            isActive = true,
            startedAtEpochMs = startedAtEpochMs,
            durationMs = durationMs.coerceAtLeast(0L),
            distanceKm = distanceKm,
            fuelUsedLiters = fuelUsedLiters,
            averageConsumptionLitersPer100Km =
                fuelUsedLiters.takeIf { distanceKm >= 0.03 }?.let { it / distanceKm * 100.0 },
            instantFuelLitersPerHour = fuelPerHour,
            instantConsumptionLitersPer100Km =
                fuelPerHour?.takeIf { speedKph >= 5.0 }?.let { it / speedKph * 100.0 },
            averageSpeedKph = distanceKm / (durationMs.coerceAtLeast(1L) / 3_600_000.0),
            averageMovingSpeedKph = distanceKm / (movingDurationMs.coerceAtLeast(1L) / 3_600_000.0),
            maxSpeedKph = maxSpeedKph,
            movingDurationMs = movingDurationMs,
            idleDurationMs = idleDurationMs,
            averageRpm = rpmTimeIntegral / rpmSampleDurationMs.coerceAtLeast(1L),
            maxRpm = maxRpm,
            maxCoolantTemperatureCelsius = maxCoolantTemperatureCelsius,
            minOutsideTemperatureCelsius = minOutsideTemperatureCelsius,
            maxOutsideTemperatureCelsius = maxOutsideTemperatureCelsius,
            startFuelPercent = startFuelPercent,
            currentFuelPercent = currentFuelPercent,
            averageAcceleratorPercent =
                (acceleratorTimeIntegral / acceleratorSampleDurationMs.coerceAtLeast(1L)).toFloat(),
            maxAcceleratorPercent = maxAcceleratorPercent,
            hardAccelerationCount = hardAccelerationCount,
            hardBrakingCount = hardBrakingCount,
            maxUphillDegrees = maxUphillDegrees,
            maxDownhillDegrees = maxDownhillDegrees,
        )
    }

    private fun clear() {
        startedAtEpochMs = null; startedAtMonoMs = null; lastMonoMs = null
        engineStoppedCandidateSinceMs = null
        lastFuelCounterLiters = null; lastFuelSampleTimestampMs = null
        lastFuelIncrementTimestampMs = null; startOdometerKm = null
        distanceKm = 0.0; fuelUsedLiters = 0.0; maxSpeedKph = 0.0
        movingDurationMs = 0L; idleDurationMs = 0L
        rpmTimeIntegral = 0.0; rpmSampleDurationMs = 0L; maxRpm = 0
        maxCoolantTemperatureCelsius = null
        minOutsideTemperatureCelsius = null; maxOutsideTemperatureCelsius = null
        startFuelPercent = null; currentFuelPercent = null
        acceleratorTimeIntegral = 0.0; acceleratorSampleDurationMs = 0L
        maxAcceleratorPercent = null; maxUphillDegrees = null; maxDownhillDegrees = null
        filteredFuelLitersPerHour = null; lastSpeedKph = null
        lastHardAccelerationAtMs = null; lastHardBrakingAtMs = null
        hardAccelerationCount = 0; hardBrakingCount = 0
    }
}
