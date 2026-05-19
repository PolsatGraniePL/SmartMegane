package pl.polsatgranie.smartmegane.domain.signal

import pl.polsatgranie.smartmegane.data.can.CanFrame

class SignalMapper(specs: List<SignalSpec>) {
    private val specsById = specs.groupBy { it.canId }

    fun applyFrame(state: SignalState, frame: CanFrame): SignalState {
        val specs = specsById[frame.id] ?: return state
        val readings = specs.mapNotNull { spec ->
            spec.decode(frame)?.let { value -> SignalReading(spec.key, value) }
        }
        return state.withUpdates(readings)
    }
}
