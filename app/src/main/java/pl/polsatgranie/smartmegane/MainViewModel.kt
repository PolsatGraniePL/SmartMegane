package pl.polsatgranie.smartmegane

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import pl.polsatgranie.smartmegane.data.phone.PhoneOrientationDataSource
import pl.polsatgranie.smartmegane.data.can.WaveshareFrameParser
import pl.polsatgranie.smartmegane.data.serial.UsbConnectionState
import pl.polsatgranie.smartmegane.data.serial.UsbDeviceInfo
import pl.polsatgranie.smartmegane.data.serial.UsbSerialDataSource
import pl.polsatgranie.smartmegane.data.vehicle.PlaceholderVehicleTelemetry
import pl.polsatgranie.smartmegane.data.vehicle.VehicleSignalAdapter
import pl.polsatgranie.smartmegane.data.trip.TripHistoryRepository
import pl.polsatgranie.smartmegane.domain.phone.PhoneOrientation
import pl.polsatgranie.smartmegane.domain.signal.SignalDefinitions
import pl.polsatgranie.smartmegane.domain.signal.SignalMapper
import pl.polsatgranie.smartmegane.domain.signal.SignalState
import pl.polsatgranie.smartmegane.domain.vehicle.GearAdvisor
import pl.polsatgranie.smartmegane.domain.vehicle.GearAdvisorInput
import pl.polsatgranie.smartmegane.domain.vehicle.GearGuidance
import pl.polsatgranie.smartmegane.domain.vehicle.AntiStallAdvisor
import pl.polsatgranie.smartmegane.domain.vehicle.AntiStallGuidance
import pl.polsatgranie.smartmegane.domain.vehicle.VehicleState
import pl.polsatgranie.smartmegane.domain.vehicle.VehicleStateDeriver
import pl.polsatgranie.smartmegane.domain.vehicle.ParkingSlopeAdvisor
import pl.polsatgranie.smartmegane.domain.vehicle.ParkingSlopeGuidance
import pl.polsatgranie.smartmegane.domain.vehicle.RpmSuitabilityAdvisor
import pl.polsatgranie.smartmegane.domain.vehicle.RpmSuitabilityState
import pl.polsatgranie.smartmegane.domain.trip.TripLiveStats
import pl.polsatgranie.smartmegane.domain.trip.TripSummary
import pl.polsatgranie.smartmegane.domain.trip.TripTracker

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private companion object {
        private const val TARGET_VENDOR_ID = 0x1A86
        private const val TARGET_PRODUCT_ID = 0x7523
        private const val SIGNAL_WATCHDOG_INTERVAL_MS = 100L
        private const val NANOS_PER_MILLISECOND = 1_000_000L
    }

    private val usbDataSource = UsbSerialDataSource(application)
    private val vehicleTelemetry = PlaceholderVehicleTelemetry()
    private val vehicleSignalAdapter = VehicleSignalAdapter()
    private val vehicleStateDeriver = VehicleStateDeriver()
    private val parser = WaveshareFrameParser()
    private val parserLock = Any()
    private val signalMapper = SignalMapper(SignalDefinitions.specs)
    private val gearAdvisor = GearAdvisor()
    private val antiStallAdvisor = AntiStallAdvisor()
    private val phoneOrientationDataSource = PhoneOrientationDataSource(application)
    private val tripTracker = TripTracker()
    private val tripHistoryRepository = TripHistoryRepository(application)
    @Volatile
    private var lastCanFrameTimestampMs: Long? = null
    private var lastDeviceIds: Set<Int> = emptySet()
    private var lastAutoConnectDeviceId: Int? = null
    private var autoConnectSuppressed = false

    private val _signalState = MutableStateFlow(SignalState())
    val signalState: StateFlow<SignalState> = _signalState.asStateFlow()

    val devices: StateFlow<List<UsbDeviceInfo>> = usbDataSource.devices
    val connectionState: StateFlow<UsbConnectionState> = usbDataSource.connectionState
    val phoneOrientation: StateFlow<PhoneOrientation> =
        phoneOrientationDataSource.orientation
    private val _vehicleState = MutableStateFlow(vehicleTelemetry.state.value)
    val vehicleState: StateFlow<VehicleState> = _vehicleState.asStateFlow()
    private val _gearGuidance = MutableStateFlow(
        gearAdvisor.update(
            input = GearAdvisorInput.from(vehicleTelemetry.state.value),
            nowMs = monotonicNowMs(),
        ),
    )
    val gearGuidance: StateFlow<GearGuidance> = _gearGuidance.asStateFlow()
    private val _rpmSuitability = MutableStateFlow(RpmSuitabilityState())
    val rpmSuitability: StateFlow<RpmSuitabilityState> = _rpmSuitability.asStateFlow()
    private val _antiStallGuidance = MutableStateFlow(AntiStallGuidance())
    val antiStallGuidance: StateFlow<AntiStallGuidance> =
        _antiStallGuidance.asStateFlow()
    private val _parkingSlopeGuidance = MutableStateFlow(ParkingSlopeGuidance())
    val parkingSlopeGuidance: StateFlow<ParkingSlopeGuidance> =
        _parkingSlopeGuidance.asStateFlow()
    private val _currentTrip = MutableStateFlow(TripLiveStats())
    val currentTrip: StateFlow<TripLiveStats> = _currentTrip.asStateFlow()
    private val _tripHistory = MutableStateFlow(tripHistoryRepository.load())
    val tripHistory: StateFlow<List<TripSummary>> = _tripHistory.asStateFlow()
    private val _lastTripSummary = MutableStateFlow<TripSummary?>(null)
    val lastTripSummary: StateFlow<TripSummary?> = _lastTripSummary.asStateFlow()

    init {
        usbDataSource.startMonitoring()
        phoneOrientationDataSource.start()
        viewModelScope.launch(Dispatchers.Default) {
            usbDataSource.bytes.collect { bytes ->
                val frames = synchronized(parserLock) { parser.append(bytes) }
                if (frames.isEmpty()) return@collect
                var updatedSignals = _signalState.value
                for (frame in frames) {
                    updatedSignals = signalMapper.applyFrame(updatedSignals, frame)
                }
                if (connectionState.value !is UsbConnectionState.Connected) return@collect
                val newestTimestampMs = frames.last().timestampMs
                _signalState.value = updatedSignals
                lastCanFrameTimestampMs = newestTimestampMs
                // One coherent publication per USB read batch avoids hundreds
                // of redundant Compose updates while preserving every frame in
                // SignalState. Speed changes from 0x354 are still published in
                // the same read cycle in which they arrive.
                publishVehicleState(
                    state = vehicleSignalAdapter.merge(
                        signals = updatedSignals,
                        nowMs = newestTimestampMs,
                    ),
                    nowMs = newestTimestampMs,
                )
            }
        }
        viewModelScope.launch {
            usbDataSource.refreshDevices()
        }
        viewModelScope.launch {
            phoneOrientation.collectLatest { orientation ->
                _parkingSlopeGuidance.value = ParkingSlopeAdvisor.evaluate(
                    phonePitchDegrees = orientation.pitchDegrees
                        .takeIf { orientation.isSensorAvailable },
                    phoneRollDegrees = orientation.rollDegrees
                        .takeIf { orientation.isSensorAvailable },
                    parkingBrakeApplied = _vehicleState.value.isParkingBrakeActive,
                )
            }
        }
        viewModelScope.launch {
            devices.collect { list ->
                val currentIds = list.map { it.deviceId }.toSet()
                if (currentIds != lastDeviceIds) {
                    lastDeviceIds = currentIds
                    autoConnectSuppressed = false
                    if (lastAutoConnectDeviceId != null && lastAutoConnectDeviceId !in currentIds) {
                        lastAutoConnectDeviceId = null
                    }
                }
                val state = connectionState.value
                if (state is UsbConnectionState.Connected ||
                    state is UsbConnectionState.Searching ||
                    state is UsbConnectionState.PermissionRequired
                ) {
                    return@collect
                }
                val target = list.firstOrNull {
                    it.vendorId == TARGET_VENDOR_ID &&
                        it.productId == TARGET_PRODUCT_ID &&
                        it.hasDriver
                }
                if (target != null && !autoConnectSuppressed && lastAutoConnectDeviceId != target.deviceId) {
                    lastAutoConnectDeviceId = target.deviceId
                    usbDataSource.connect(target.deviceId)
                }
            }
        }
        viewModelScope.launch {
            while (isActive) {
                delay(SIGNAL_WATCHDOG_INTERVAL_MS)
                if (connectionState.value is UsbConnectionState.Connected) {
                    val nowMs = monotonicNowMs()
                    publishVehicleState(
                        state = vehicleSignalAdapter.merge(
                            signals = _signalState.value,
                            nowMs = nowMs,
                        ),
                        nowMs = nowMs,
                    )
                }
            }
        }
        viewModelScope.launch {
            connectionState.collect { state ->
                if (state is UsbConnectionState.Connected) {
                    resetLiveState(VehicleState())
                }
                if (state is UsbConnectionState.Disconnected ||
                    state is UsbConnectionState.NoDevice ||
                    state is UsbConnectionState.Error
                ) {
                    finishTripAfterTelemetryLoss()
                    synchronized(parserLock) { parser.reset() }
                    _signalState.value = SignalState()
                    resetLiveState(vehicleTelemetry.state.value)
                }
                if (state is UsbConnectionState.PermissionDenied && lastAutoConnectDeviceId != null) {
                    autoConnectSuppressed = true
                }
            }
        }
    }

    fun refreshDevices() {
        usbDataSource.refreshDevices()
    }

    fun connectFirstAvailable() {
        viewModelScope.launch {
            usbDataSource.connectFirstAvailable()
        }
    }

    fun connectToDevice(deviceId: Int) {
        viewModelScope.launch {
            usbDataSource.connect(deviceId)
        }
    }

    fun disconnect() {
        usbDataSource.disconnect()
    }

    fun recalibratePhoneOrientation() {
        phoneOrientationDataSource.recalibrate()
    }

    @Synchronized
    private fun publishVehicleState(
        state: VehicleState,
        nowMs: Long,
    ) {
        val derivedState = vehicleStateDeriver.derive(
            state = state,
            nowMs = nowMs,
            lastCanFrameTimestampMs = lastCanFrameTimestampMs,
        )
        _vehicleState.value = derivedState
        _gearGuidance.value = gearAdvisor.update(
            input = GearAdvisorInput.from(derivedState),
            nowMs = nowMs,
        )
        _rpmSuitability.value = RpmSuitabilityAdvisor.calculate(
            state = derivedState,
            guidance = _gearGuidance.value,
        )
        _antiStallGuidance.value = antiStallAdvisor.update(
            state = derivedState,
            roadPitchDegrees = ParkingSlopeAdvisor.vehiclePitchDegrees(
                phoneOrientation.value.rollDegrees
                    .takeIf { phoneOrientation.value.isSensorAvailable },
            ),
            nowMs = nowMs,
        )
        _parkingSlopeGuidance.value = ParkingSlopeAdvisor.evaluate(
            phonePitchDegrees = phoneOrientation.value.pitchDegrees
                .takeIf { phoneOrientation.value.isSensorAvailable },
            phoneRollDegrees = phoneOrientation.value.rollDegrees
                .takeIf { phoneOrientation.value.isSensorAvailable },
            parkingBrakeApplied = derivedState.isParkingBrakeActive,
        )
        val tripResult = tripTracker.update(
            state = derivedState,
            monotonicNowMs = nowMs,
            epochNowMs = System.currentTimeMillis(),
            roadPitchDegrees = ParkingSlopeAdvisor.vehiclePitchDegrees(
                phoneOrientation.value.rollDegrees
                    .takeIf { phoneOrientation.value.isSensorAvailable },
            ),
        )
        _currentTrip.value = tripResult.live
        tripResult.completed?.let(::recordCompletedTrip)
    }

    @Synchronized
    private fun finishTripAfterTelemetryLoss() {
        val nowMs = monotonicNowMs()
        tripTracker.finishNow(
            state = _vehicleState.value,
            monotonicNowMs = nowMs,
            epochNowMs = System.currentTimeMillis(),
        )?.let(::recordCompletedTrip)
    }

    private fun recordCompletedTrip(completed: TripSummary) {
        _lastTripSummary.value = completed
        viewModelScope.launch(Dispatchers.IO) {
            _tripHistory.value = tripHistoryRepository.add(completed)
        }
    }

    private fun resetLiveState(state: VehicleState) {
        gearAdvisor.reset()
        antiStallAdvisor.reset()
        vehicleStateDeriver.reset()
        lastCanFrameTimestampMs = null
        publishVehicleState(
            state = state,
            nowMs = monotonicNowMs(),
        )
    }

    private fun monotonicNowMs(): Long =
        System.nanoTime() / NANOS_PER_MILLISECOND

    override fun onCleared() {
        phoneOrientationDataSource.stop()
        usbDataSource.close()
    }
}
