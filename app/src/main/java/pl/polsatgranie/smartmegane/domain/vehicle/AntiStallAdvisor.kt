package pl.polsatgranie.smartmegane.domain.vehicle

import kotlin.math.abs
import kotlin.math.roundToInt

enum class AntiStallStatus {
    INACTIVE,
    ENGINE_OFF,
    ENGINE_RESERVE,
    CLUTCH_RELEASED,
    STALL_RISK,
    ADD_THROTTLE,
    RELEASE_SMOOTHLY,
}

data class AntiStallGuidance(
    val status: AntiStallStatus = AntiStallStatus.INACTIVE,
    val currentRpm: Int = 0,
    val targetRpm: Int = 0,
    val stallFloorRpm: Int = 0,
    val rpmReserve: Int = 0,
    val normalizedReserve: Float = 0f,
    val confidence: Float = 0f,
) {
    val prompt: String
        get() = when (status) {
            AntiStallStatus.STALL_RISK -> "RYZYKO ZGAŚNIĘCIA"
            AntiStallStatus.ADD_THROTTLE -> "DODAJ LEKKO GAZU"
            AntiStallStatus.RELEASE_SMOOTHLY -> "PUŚĆ PŁYNNIE"
            AntiStallStatus.CLUTCH_RELEASED -> "SPRZĘGŁO PUSZCZONE"
            AntiStallStatus.ENGINE_RESERVE -> "ZAPAS OBROTÓW"
            AntiStallStatus.ENGINE_OFF -> "SILNIK WYŁĄCZONY"
            AntiStallStatus.INACTIVE -> ""
        }
}

/**
 * Launch-only assistant. Without an analogue clutch-position sensor the app
 * cannot know the bite point, so it deliberately answers the useful question:
 * whether the engine currently has enough RPM reserve for a smooth release.
 */
class AntiStallAdvisor {
    private companion object {
        const val MAX_ASSIST_SPEED_KPH = 9.0
        const val STATUS_DWELL_MS = 260L
        const val RPM_FILTER_ALPHA = 0.24
    }

    private var filteredRpm: Double? = null
    private var acceptedStatus = AntiStallStatus.INACTIVE
    private var candidateStatus = AntiStallStatus.INACTIVE
    private var candidateSinceMs = 0L

    fun reset() {
        filteredRpm = null
        acceptedStatus = AntiStallStatus.INACTIVE
        candidateStatus = AntiStallStatus.INACTIVE
        candidateSinceMs = 0L
    }

    fun update(
        state: VehicleState,
        roadPitchDegrees: Float?,
        nowMs: Long,
    ): AntiStallGuidance {
        val liveRpm = state.engineRpmPrecise ?: state.engineRpm.toDouble()
        filteredRpm = filteredRpm?.let {
            it + (liveRpm - it) * RPM_FILTER_ALPHA
        } ?: liveRpm
        val rpm = filteredRpm?.roundToInt() ?: 0
        val speed = state.speedKphPrecise ?: state.speedKph.toDouble()

        if (state.powerState != VehiclePowerState.ENGINE_RUNNING) {
            acceptImmediately(AntiStallStatus.ENGINE_OFF, nowMs)
            return AntiStallGuidance(
                status = AntiStallStatus.ENGINE_OFF,
                currentRpm = rpm,
            )
        }
        if (!state.isEngineRpmSignalAvailable) {
            acceptImmediately(AntiStallStatus.INACTIVE, nowMs)
            return AntiStallGuidance(
                status = AntiStallStatus.INACTIVE,
                currentRpm = rpm,
            )
        }

        val slopeReserve = abs(roadPitchDegrees ?: 0f).coerceAtMost(12f) * 22f
        val accelerator = (state.acceleratorPedalPercent ?: 0f) / 100f
        val torqueLoad = (
            (state.requestedEngineTorqueNm ?: 0)
                .coerceAtLeast(0) / MeganeIiK9kTl4001Profile.MAX_ENGINE_TORQUE_NM
            ).toFloat()
        val load = maxOf(accelerator, torqueLoad).coerceIn(0f, 1f)
        val target = (1_050f + slopeReserve + load * 230f)
            .coerceIn(1_020f, 1_550f)
            .roundToInt()
        val stallFloor = (780f + slopeReserve * 0.55f + load * 90f)
            .coerceIn(760f, 1_080f)
            .roundToInt()
        val launchAssistActive = state.isClutchPedalSignalAvailable &&
            state.isClutchPedalPressed &&
            speed <= MAX_ASSIST_SPEED_KPH
        val rawStatus = when {
            !launchAssistActive -> AntiStallStatus.ENGINE_RESERVE
            rpm < stallFloor -> AntiStallStatus.STALL_RISK
            rpm < target - 80 -> AntiStallStatus.ADD_THROTTLE
            else -> AntiStallStatus.RELEASE_SMOOTHLY
        }
        val status = acceptWithDwell(rawStatus, nowMs)
        val reserve = rpm - target
        val confidence = (
            0.72f +
                (if (state.acceleratorPedalPercent != null) 0.08f else 0f) +
                (if (state.requestedEngineTorqueNm != null) 0.08f else 0f) +
                (if (roadPitchDegrees != null) 0.07f else 0f)
            ).coerceAtMost(0.95f)

        return AntiStallGuidance(
            status = status,
            currentRpm = rpm,
            targetRpm = target,
            stallFloorRpm = stallFloor,
            rpmReserve = reserve,
            normalizedReserve = (reserve / 450f).coerceIn(-1f, 1f),
            confidence = confidence,
        )
    }

    private fun acceptImmediately(status: AntiStallStatus, nowMs: Long) {
        acceptedStatus = status
        candidateStatus = status
        candidateSinceMs = nowMs
    }

    private fun acceptWithDwell(
        status: AntiStallStatus,
        nowMs: Long,
    ): AntiStallStatus {
        if (status == acceptedStatus) {
            candidateStatus = status
            candidateSinceMs = nowMs
            return acceptedStatus
        }
        if (status != candidateStatus) {
            candidateStatus = status
            candidateSinceMs = nowMs
        } else if (nowMs - candidateSinceMs >= STATUS_DWELL_MS) {
            acceptedStatus = candidateStatus
        }
        return acceptedStatus
    }
}
