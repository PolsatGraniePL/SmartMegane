package pl.polsatgranie.smartmegane.domain.vehicle

/**
 * Adds session-level state that cannot be decoded from one CAN frame.
 */
class VehicleStateDeriver {
    private companion object {
        const val CAN_SILENCE_TIMEOUT_MS = 1_500L
        const val ENGINE_RUNNING_RPM = 450
    }

    fun reset() = Unit

    fun derive(
        state: VehicleState,
        nowMs: Long,
        lastCanFrameTimestampMs: Long?,
    ): VehicleState {
        val canActive = lastCanFrameTimestampMs?.let {
            nowMs >= it && nowMs - it <= CAN_SILENCE_TIMEOUT_MS
        } == true
        val engineRunning =
            canActive &&
                (
                    state.isEngineRunning ||
                        (
                            state.isEngineRpmSignalAvailable &&
                                state.engineRpm >= ENGINE_RUNNING_RPM
                            )
                    )
        val powerState = when {
            !canActive -> VehiclePowerState.OFF
            engineRunning -> VehiclePowerState.ENGINE_RUNNING
            state.isIgnitionOn -> VehiclePowerState.IGNITION_ON
            else -> VehiclePowerState.CAN_AWAKE
        }

        return state.copy(
            powerState = powerState,
            isCanBusActive = canActive,
            lastCanFrameTimestampMs = lastCanFrameTimestampMs,
            isEngineRunning = engineRunning,
        )
    }
}
