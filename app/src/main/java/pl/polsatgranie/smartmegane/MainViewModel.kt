package pl.polsatgranie.smartmegane

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import pl.polsatgranie.smartmegane.data.can.WaveshareFrameParser
import pl.polsatgranie.smartmegane.data.serial.UsbConnectionState
import pl.polsatgranie.smartmegane.data.serial.UsbDeviceInfo
import pl.polsatgranie.smartmegane.data.serial.UsbSerialDataSource
import pl.polsatgranie.smartmegane.data.vehicle.PlaceholderVehicleTelemetry
import pl.polsatgranie.smartmegane.data.vehicle.VehicleSignalAdapter
import pl.polsatgranie.smartmegane.domain.signal.SignalDefinitions
import pl.polsatgranie.smartmegane.domain.signal.SignalMapper
import pl.polsatgranie.smartmegane.domain.signal.SignalState
import pl.polsatgranie.smartmegane.domain.vehicle.GearAdvisor
import pl.polsatgranie.smartmegane.domain.vehicle.GearAdvisorInput
import pl.polsatgranie.smartmegane.domain.vehicle.GearGuidance
import pl.polsatgranie.smartmegane.domain.vehicle.VehicleState

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private companion object {
        private const val TARGET_VENDOR_ID = 0x1A86
        private const val TARGET_PRODUCT_ID = 0x7523
        private const val SIGNAL_WATCHDOG_INTERVAL_MS = 100L
        private const val NANOS_PER_MILLISECOND = 1_000_000L
    }

    private val usbDataSource = UsbSerialDataSource(application)
    private val vehicleTelemetry = PlaceholderVehicleTelemetry()
    private val liveSignalFallbackState = vehicleTelemetry.state.value.copy(
        engineRpm = 0,
        engineRpmPrecise = null,
        coolantTemperatureCelsius = 0,
        odometerKm = 0,
    )
    private val vehicleSignalAdapter = VehicleSignalAdapter()
    private val parser = WaveshareFrameParser()
    private val signalMapper = SignalMapper(SignalDefinitions.specs)
    private val gearAdvisor = GearAdvisor()
    private var lastDeviceIds: Set<Int> = emptySet()
    private var lastAutoConnectDeviceId: Int? = null
    private var autoConnectSuppressed = false

    private val _signalState = MutableStateFlow(SignalState())
    val signalState: StateFlow<SignalState> = _signalState.asStateFlow()

    val devices: StateFlow<List<UsbDeviceInfo>> = usbDataSource.devices
    val connectionState: StateFlow<UsbConnectionState> = usbDataSource.connectionState
    private val _vehicleState = MutableStateFlow(vehicleTelemetry.state.value)
    val vehicleState: StateFlow<VehicleState> = _vehicleState.asStateFlow()
    private val _gearGuidance = MutableStateFlow(
        gearAdvisor.update(
            input = GearAdvisorInput.from(vehicleTelemetry.state.value),
            nowMs = monotonicNowMs(),
        ),
    )
    val gearGuidance: StateFlow<GearGuidance> = _gearGuidance.asStateFlow()

    init {
        usbDataSource.startMonitoring()
        viewModelScope.launch {
            usbDataSource.bytes.collect { bytes ->
                val frames = parser.append(bytes)
                for (frame in frames) {
                    val updatedSignals = signalMapper.applyFrame(_signalState.value, frame)
                    _signalState.value = updatedSignals
                    _vehicleState.value = vehicleSignalAdapter.merge(
                        placeholder = liveSignalFallbackState,
                        signals = updatedSignals,
                        nowMs = frame.timestampMs,
                    )
                }
            }
        }
        viewModelScope.launch {
            usbDataSource.refreshDevices()
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
                            placeholder = liveSignalFallbackState,
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
                    resetDerivedState(liveSignalFallbackState)
                }
                if (state is UsbConnectionState.Disconnected ||
                    state is UsbConnectionState.NoDevice ||
                    state is UsbConnectionState.Error
                ) {
                    parser.reset()
                    _signalState.value = SignalState()
                    resetDerivedState(vehicleTelemetry.state.value)
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

    private fun publishVehicleState(
        state: VehicleState,
        nowMs: Long,
    ) {
        _vehicleState.value = state
        _gearGuidance.value = gearAdvisor.update(
            input = GearAdvisorInput.from(state),
            nowMs = nowMs,
        )
    }

    private fun resetDerivedState(state: VehicleState) {
        gearAdvisor.reset()
        publishVehicleState(
            state = state,
            nowMs = monotonicNowMs(),
        )
    }

    private fun monotonicNowMs(): Long =
        System.nanoTime() / NANOS_PER_MILLISECOND

    override fun onCleared() {
        usbDataSource.close()
    }
}
