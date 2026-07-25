package pl.polsatgranie.smartmegane

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
import pl.polsatgranie.smartmegane.domain.vehicle.VehicleState

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private companion object {
        private const val TARGET_VENDOR_ID = 0x1A86
        private const val TARGET_PRODUCT_ID = 0x7523
    }

    private val usbDataSource = UsbSerialDataSource(application)
    private val vehicleTelemetry = PlaceholderVehicleTelemetry()
    private val vehicleSignalAdapter = VehicleSignalAdapter()
    private val parser = WaveshareFrameParser()
    private val signalMapper = SignalMapper(SignalDefinitions.specs)
    private var lastDeviceIds: Set<Int> = emptySet()
    private var lastAutoConnectDeviceId: Int? = null
    private var autoConnectSuppressed = false

    private val _signalState = MutableStateFlow(SignalState())
    val signalState: StateFlow<SignalState> = _signalState.asStateFlow()

    val devices: StateFlow<List<UsbDeviceInfo>> = usbDataSource.devices
    val connectionState: StateFlow<UsbConnectionState> = usbDataSource.connectionState
    private val _vehicleState = MutableStateFlow(vehicleTelemetry.state.value)
    val vehicleState: StateFlow<VehicleState> = _vehicleState.asStateFlow()

    init {
        usbDataSource.startMonitoring()
        viewModelScope.launch {
            usbDataSource.bytes.collect { bytes ->
                val frames = parser.append(bytes)
                for (frame in frames) {
                    val updatedSignals = signalMapper.applyFrame(_signalState.value, frame)
                    _signalState.value = updatedSignals
                    _vehicleState.value = vehicleSignalAdapter.merge(
                        placeholder = vehicleTelemetry.state.value,
                        signals = updatedSignals,
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
            connectionState.collect { state ->
                if (state is UsbConnectionState.Disconnected ||
                    state is UsbConnectionState.NoDevice ||
                    state is UsbConnectionState.Error
                ) {
                    _signalState.value = SignalState()
                    _vehicleState.value = vehicleTelemetry.state.value
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

    override fun onCleared() {
        usbDataSource.close()
    }
}
