package pl.polsatgranie.smartmegane.domain.signal

data class SignalState(
    val values: Map<String, SignalValue> = emptyMap(),
) {
    fun get(key: SignalKey): SignalValue? = values[key.id]

    fun withUpdates(readings: List<SignalReading>): SignalState {
        if (readings.isEmpty()) return this
        val updated = values.toMutableMap()
        readings.forEach { updated[it.key.id] = it.value }
        return copy(values = updated)
    }
}

data class SignalReading(
    val key: SignalKey,
    val value: SignalValue,
)
