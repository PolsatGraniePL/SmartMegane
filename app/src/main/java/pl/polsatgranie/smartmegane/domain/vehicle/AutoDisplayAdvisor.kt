package pl.polsatgranie.smartmegane.domain.vehicle

enum class AutoDisplayMode { VEHICLE, SPEED }

/**
 * Hysteretic selector for the automatic dashboard tab. It distinguishes a
 * real stop after braking from low-speed manoeuvring and prevents one malformed
 * speed sample from repeatedly swapping the central view.
 */
class AutoDisplayAdvisor {
    private companion object {
        const val SPEED_VIEW_THRESHOLD_KPH = 5.0
        const val CREEP_MAX_KPH = 4.0
        const val STATIONARY_KPH = 0.15
        const val RECENT_FAST_SPEED_KPH = 8.0
        const val STRONG_DECELERATION_MS2 = -0.45
        const val MOMENTUM_MEMORY_MS = 2_500L
        const val FAST_STOP_CONFIRM_MS = 450L
        const val QUIET_STOP_CONFIRM_MS = 180L
        const val PARKING_CONFIRM_MS = 500L
        const val CREEP_VEHICLE_VIEW_MS = 4_000L
    }

    private var mode = AutoDisplayMode.VEHICLE
    private var lastSpeedKph: Double? = null
    private var lastSampleMs: Long? = null
    private var lastSignalTimestampMs: Long? = null
    private var momentumUntilMs = Long.MIN_VALUE
    private var stationarySinceMs: Long? = null
    private var creepingSinceMs: Long? = null

    fun reset(initialMode: AutoDisplayMode = AutoDisplayMode.VEHICLE) {
        mode = initialMode
        lastSpeedKph = null
        lastSampleMs = null
        lastSignalTimestampMs = null
        momentumUntilMs = Long.MIN_VALUE
        stationarySinceMs = null
        creepingSinceMs = null
    }

    fun update(
        speedKph: Double?,
        isSpeedAvailable: Boolean,
        nowMs: Long,
        sampleTimestampMs: Long? = null,
    ): AutoDisplayMode {
        if (!isSpeedAvailable || speedKph == null || !speedKph.isFinite()) return mode
        val speed = speedKph.coerceAtLeast(0.0)
        val isNewSignalSample = sampleTimestampMs == null ||
            sampleTimestampMs != lastSignalTimestampMs
        if (isNewSignalSample) {
            val sampleTime = sampleTimestampMs ?: nowMs
            val previousSpeed = lastSpeedKph
            val previousTime = lastSampleMs
            if (previousSpeed != null && previousTime != null) {
                val elapsedMs = sampleTime - previousTime
                if (elapsedMs in 20L..750L) {
                    val accelerationMs2 = ((speed - previousSpeed) / 3.6) /
                        (elapsedMs / 1_000.0)
                    if (accelerationMs2 <= STRONG_DECELERATION_MS2) {
                        momentumUntilMs = nowMs + MOMENTUM_MEMORY_MS
                    }
                }
            }
            lastSpeedKph = speed
            lastSampleMs = sampleTime
            lastSignalTimestampMs = sampleTimestampMs
        }
        if (speed >= RECENT_FAST_SPEED_KPH) {
            momentumUntilMs = nowMs + MOMENTUM_MEMORY_MS
        }
        val hasMomentum = nowMs <= momentumUntilMs
        when {
            speed >= SPEED_VIEW_THRESHOLD_KPH -> {
                mode = AutoDisplayMode.SPEED
                stationarySinceMs = null
                creepingSinceMs = null
            }

            speed <= STATIONARY_KPH -> {
                creepingSinceMs = null
                val since = stationarySinceMs ?: nowMs.also { stationarySinceMs = it }
                val dwell = if (hasMomentum) FAST_STOP_CONFIRM_MS else QUIET_STOP_CONFIRM_MS
                if (nowMs - since >= dwell) mode = AutoDisplayMode.VEHICLE
            }

            speed <= CREEP_MAX_KPH -> {
                stationarySinceMs = null
                if (hasMomentum) {
                    mode = AutoDisplayMode.SPEED
                    creepingSinceMs = null
                } else {
                    val since = creepingSinceMs ?: nowMs.also { creepingSinceMs = it }
                    val elapsed = nowMs - since
                    mode = when {
                        elapsed < PARKING_CONFIRM_MS -> mode
                        elapsed < CREEP_VEHICLE_VIEW_MS -> AutoDisplayMode.VEHICLE
                        else -> AutoDisplayMode.SPEED
                    }
                }
            }

            else -> {
                stationarySinceMs = null
                creepingSinceMs = null
            }
        }
        return mode
    }
}
