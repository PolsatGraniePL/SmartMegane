package pl.polsatgranie.smartmegane.domain.signal

data class SignalState(
    val values: Map<String, SignalValue> = emptyMap(),
    val timestampsMs: Map<String, Long> = emptyMap(),
) {
    fun get(key: SignalKey): SignalValue? = values[key.id]

    fun timestampMs(key: SignalKey): Long? = timestampsMs[key.id]

    /**
     * Values constructed without timestamps (for previews/tests) remain readable.
     * Runtime readings always carry the monotonic timestamp of their CAN frame.
     */
    fun getFresh(
        key: SignalKey,
        nowMs: Long,
        maxAgeMs: Long,
    ): SignalValue? {
        val value = get(key) ?: return null
        val timestamp = timestampMs(key) ?: return value
        val age = nowMs - timestamp
        return value.takeIf { age in 0..maxAgeMs }
    }

    fun withUpdates(
        readings: List<SignalReading>,
        timestampMs: Long,
    ): SignalState {
        if (readings.isEmpty()) return this
        val updated = values.toMutableMap()
        val updatedTimestamps = timestampsMs.toMutableMap()
        readings.forEach {
            updated[it.key.id] = it.value
            updatedTimestamps[it.key.id] = timestampMs
        }
        return copy(
            values = updated,
            timestampsMs = updatedTimestamps,
        )
    }
}

data class SignalReading(
    val key: SignalKey,
    val value: SignalValue,
)
