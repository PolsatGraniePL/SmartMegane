package pl.polsatgranie.smartmegane.domain.signal

import pl.polsatgranie.smartmegane.data.can.CanFrame

class SignalMapper(specs: List<SignalSpec>) {
    private val specsById = specs.groupBy { it.canId }

    fun applyFrame(state: SignalState, frame: CanFrame): SignalState =
        applyFrames(state, listOf(frame))

    /**
     * Applies a complete USB read batch with one map copy. The previous code
     * copied both SignalState maps for every CAN frame, which could make the
     * serial reader fall behind on a busy 500 kbit/s bus.
     */
    fun applyFrames(state: SignalState, frames: List<CanFrame>): SignalState {
        if (frames.isEmpty()) return state
        val values = state.values.toMutableMap()
        val timestamps = state.timestampsMs.toMutableMap()
        var changed = false
        for (frame in frames) {
            val specs = specsById[frame.id] ?: continue
            for (spec in specs) {
                val value = spec.decode(frame) ?: continue
                values[spec.key.id] = value
                timestamps[spec.key.id] = frame.timestampMs
                changed = true
            }
        }
        return if (changed) SignalState(values, timestamps) else state
    }
}
