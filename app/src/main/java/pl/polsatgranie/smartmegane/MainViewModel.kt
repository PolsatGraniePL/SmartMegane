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
import pl.polsatgranie.smartmegane.data.vehicle.LastVehicleSnapshotRepository
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
import pl.polsatgranie.smartmegane.domain.vehicle.LiveVehicleStateStabilizer
import pl.polsatgranie.smartmegane.domain.vehicle.AutoDisplayAdvisor
import pl.polsatgranie.smartmegane.domain.vehicle.AutoDisplayMode
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
        private const val VEHICLE_PUBLISH_INTERVAL_MS = 25L
        private const val DIAGNOSTICS_PUBLISH_INTERVAL_MS = 100L
        private const val TRIP_UI_PUBLISH_INTERVAL_MS = 200L
        private const val SNAPSHOT_SAVE_INTERVAL_MS = 2_000L
        private const val NANOS_PER_MILLISECOND = 1_000_000L
    }

    private val usbDataSource = UsbSerialDataSource(application)
    private val vehicleTelemetry = PlaceholderVehicleTelemetry()
    private val vehicleSignalAdapter = VehicleSignalAdapter()
    private val liveVehicleStateStabilizer = LiveVehicleStateStabilizer()
    private val vehicleStateDeriver = VehicleStateDeriver()
    private val autoDisplayAdvisor = AutoDisplayAdvisor()
    private val parser = WaveshareFrameParser()
    private val parserLock = Any()
    private val signalMapper = SignalMapper(SignalDefinitions.specs)
    private val gearAdvisor = GearAdvisor()
    private val antiStallAdvisor = AntiStallAdvisor()
    private val phoneOrientationDataSource = PhoneOrientationDataSource(application)
    private val tripTracker = TripTracker()
    private val tripHistoryRepository = TripHistoryRepository(application)
    private val lastVehicleSnapshotRepository = LastVehicleSnapshotRepository(application)
    private val initialVehicleState =
        lastVehicleSnapshotRepository.load() ?: vehicleTelemetry.state.value
    @Volatile
    private var lastCanFrameTimestampMs: Long? = null
    @Volatile
    private var latestSignalState = SignalState()
    private var lastDiagnosticsPublishAtMs = 0L
    private var lastTripUiPublishAtMs = 0L
    private var lastSnapshotSaveAtMs = 0L
    private var lastDeviceIds: Set<Int> = emptySet()
    private var lastAutoConnectDeviceId: Int? = null
    private var autoConnectSuppressed = false

    private val _signalState = MutableStateFlow(SignalState())
    val signalState: StateFlow<SignalState> = _signalState.asStateFlow()

    val devices: StateFlow<List<UsbDeviceInfo>> = usbDataSource.devices
    val connectionState: StateFlow<UsbConnectionState> = usbDataSource.connectionState
    val phoneOrientation: StateFlow<PhoneOrientation> =
        phoneOrientationDataSource.orientation
    private val _vehicleState = MutableStateFlow(initialVehicleState)
    val vehicleState: StateFlow<VehicleState> = _vehicleState.asStateFlow()
    private val _gearGuidance = MutableStateFlow(
        gearAdvisor.update(
            input = GearAdvisorInput.from(initialVehicleState),
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
    private val _autoDisplayMode = MutableStateFlow(AutoDisplayMode.VEHICLE)
    val autoDisplayMode: StateFlow<AutoDisplayMode> = _autoDisplayMode.asStateFlow()

    init {
        usbDataSource.startMonitoring()
        phoneOrientationDataSource.start()
        viewModelScope.launch(Dispatchers.Default) {
            usbDataSource.bytes.collect { bytes ->
                synchronized(parserLock) {
                    val frames = parser.append(bytes)
                    if (frames.isNotEmpty() &&
                        connectionState.value is UsbConnectionState.Connected
                    ) {
                        latestSignalState = signalMapper.applyFrames(
                            latestSignalState,
                            frames,
                        )
                        lastCanFrameTimestampMs = frames.last().timestampMs
                    }
                }
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
        viewModelScope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(VEHICLE_PUBLISH_INTERVAL_MS)
                if (connectionState.value is UsbConnectionState.Connected) {
                    val nowMs = monotonicNowMs()
                    val signals = latestSignalState
                    if (nowMs - lastDiagnosticsPublishAtMs >=
                        DIAGNOSTICS_PUBLISH_INTERVAL_MS
                    ) {
                        lastDiagnosticsPublishAtMs = nowMs
                        _signalState.value = signals
                    }
                    val rawState = vehicleSignalAdapter.merge(
                        signals = signals,
                        nowMs = nowMs,
                    )
                    publishVehicleState(
                        state = liveVehicleStateStabilizer.stabilize(
                            state = rawState,
                            signals = signals,
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
                    synchronized(parserLock) {
                        parser.reset()
                        latestSignalState = SignalState()
                    }
                    _signalState.value = SignalState()
                    resetLiveState(VehicleState())
                }
                if (state is UsbConnectionState.Disconnected ||
                    state is UsbConnectionState.NoDevice ||
                    state is UsbConnectionState.Error
                ) {
                    finishTripAfterTelemetryLoss()
                    lastVehicleSnapshotRepository.save(_vehicleState.value)
                    synchronized(parserLock) {
                        parser.reset()
                        latestSignalState = SignalState()
                    }
                    _signalState.value = SignalState()
                    resetLiveState(
                        lastVehicleSnapshotRepository.load() ?: vehicleTelemetry.state.value,
                    )
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
        _autoDisplayMode.value = if (derivedState.isCanBusActive) {
            autoDisplayAdvisor.update(
                speedKph = derivedState.speedKphPrecise,
                isSpeedAvailable = derivedState.isSpeedSignalAvailable,
                nowMs = nowMs,
                sampleTimestampMs = latestSignalState.timestampMs(
                    SignalDefinitions.vehicleSpeedKph,
                ),
            )
        } else {
            autoDisplayAdvisor.reset()
            AutoDisplayMode.VEHICLE
        }
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
        if (tripResult.completed != null ||
            nowMs - lastTripUiPublishAtMs >= TRIP_UI_PUBLISH_INTERVAL_MS
        ) {
            lastTripUiPublishAtMs = nowMs
            _currentTrip.value = tripResult.live
        }
        tripResult.completed?.let(::recordCompletedTrip)
        if (derivedState.isCanBusActive &&
            nowMs - lastSnapshotSaveAtMs >= SNAPSHOT_SAVE_INTERVAL_MS
        ) {
            lastSnapshotSaveAtMs = nowMs
            viewModelScope.launch(Dispatchers.IO) {
                lastVehicleSnapshotRepository.save(derivedState)
            }
        }
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
        liveVehicleStateStabilizer.reset()
        vehicleStateDeriver.reset()
        autoDisplayAdvisor.reset()
        _autoDisplayMode.value = AutoDisplayMode.VEHICLE
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
