package pl.polsatgranie.smarmegane.data.serial

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class UsbSerialDataSource(
    private val context: Context,
    private val baudRate: Int = 2_000_000,
) {
    private companion object {
        private const val TAG = "SmarMeganeUsb"
    }

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var readJob: Job? = null
    private var port: UsbSerialPort? = null
    private var connection: UsbDeviceConnection? = null
    private var connectedDeviceId: Int? = null
    private var deviceReceiver: BroadcastReceiver? = null

    private val _bytes = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
    val bytes: SharedFlow<ByteArray> = _bytes.asSharedFlow()

    private val _connectionState = MutableStateFlow<UsbConnectionState>(UsbConnectionState.Disconnected)
    val connectionState: StateFlow<UsbConnectionState> = _connectionState.asStateFlow()

    private val _devices = MutableStateFlow<List<UsbDeviceInfo>>(emptyList())
    val devices: StateFlow<List<UsbDeviceInfo>> = _devices.asStateFlow()

    fun startMonitoring() {
        if (deviceReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val action = intent.action ?: return
                val device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                refreshDevices()
                if (action == UsbManager.ACTION_USB_DEVICE_DETACHED && device?.deviceId == connectedDeviceId) {
                    Log.w(TAG, "Connected device detached; disconnecting")
                    disconnect()
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        deviceReceiver = receiver
    }

    fun stopMonitoring() {
        val receiver = deviceReceiver ?: return
        runCatching { context.unregisterReceiver(receiver) }
        deviceReceiver = null
    }

    fun refreshDevices() {
        val deviceList = usbManager.deviceList.values.toList()
        val infos = deviceList.map { device ->
            val driver = UsbSerialProber.getDefaultProber().probeDevice(device)
            UsbDeviceInfo(
                deviceId = device.deviceId,
                deviceName = device.deviceName,
                vendorId = device.vendorId,
                productId = device.productId,
                hasDriver = driver != null,
                portCount = driver?.ports?.size ?: 0,
            )
        }.sortedBy { it.deviceName }
        _devices.value = infos
    }

    suspend fun connectFirstAvailable() {
        disconnect()
        _connectionState.value = UsbConnectionState.Searching
        val drivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        if (drivers.isEmpty()) {
            _connectionState.value = UsbConnectionState.NoDevice
            return
        }
        connectDevice(drivers.first().device, 0)
    }

    suspend fun connect(deviceId: Int, portIndex: Int = 0) {
        disconnect()
        _connectionState.value = UsbConnectionState.Searching
        val device = usbManager.deviceList.values.firstOrNull { it.deviceId == deviceId }
        if (device == null) {
            _connectionState.value = UsbConnectionState.Error("Device not found")
            Log.w(TAG, "Connect failed: deviceId=$deviceId not found")
            return
        }
        connectDevice(device, portIndex)
    }

    fun disconnect() {
        cleanup()
        _connectionState.value = UsbConnectionState.Disconnected
    }

    fun close() {
        cleanup()
        stopMonitoring()
        ioScope.cancel()
    }

    private fun startReading(port: UsbSerialPort) {
        readJob?.cancel()
        readJob = ioScope.launch {
            val buffer = ByteArray(256)
            while (isActive) {
                val len = try {
                    port.read(buffer, 1000)
                } catch (e: Exception) {
                    _connectionState.value = UsbConnectionState.Error(e.message ?: "USB read failed")
                    Log.e(TAG, "USB read failed", e)
                    cleanup()
                    break
                }
                if (len > 0) {
                    _bytes.emit(buffer.copyOf(len))
                }
            }
        }
    }

    private fun cleanup() {
        readJob?.cancel()
        readJob = null
        runCatching { port?.close() }
        runCatching { connection?.close() }
        port = null
        connection = null
        connectedDeviceId = null
    }

    private suspend fun connectDevice(device: UsbDevice, portIndex: Int) {
        val driver = UsbSerialProber.getDefaultProber().probeDevice(device)
        if (driver == null) {
            _connectionState.value = UsbConnectionState.Error("No driver for device")
            Log.w(TAG, "No driver for device ${device.deviceName}")
            return
        }
        if (!usbManager.hasPermission(device)) {
            _connectionState.value = UsbConnectionState.PermissionRequired(device.deviceName)
            val granted = requestPermission(device)
            if (!granted || !usbManager.hasPermission(device)) {
                _connectionState.value = UsbConnectionState.PermissionDenied(device.deviceName)
                Log.w(TAG, "USB permission denied for ${device.deviceName}")
                return
            }
        }
        val connection = usbManager.openDevice(device)
        if (connection == null) {
            _connectionState.value = UsbConnectionState.Error("Unable to open USB device")
            Log.w(TAG, "Unable to open USB device ${device.deviceName}")
            return
        }
        val port = driver.ports.getOrNull(portIndex)
        if (port == null) {
            connection.close()
            _connectionState.value = UsbConnectionState.Error("No serial port available")
            Log.w(TAG, "No serial port available on ${device.deviceName}")
            return
        }
        port.open(connection)
        port.setParameters(baudRate, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
        this.connection = connection
        this.port = port
        this.connectedDeviceId = device.deviceId
        _connectionState.value = UsbConnectionState.Connected(device.deviceName)
        Log.i(TAG, "Connected to ${device.deviceName} baud=$baudRate portIndex=$portIndex")
        startReading(port)
    }

    private suspend fun requestPermission(device: UsbDevice): Boolean =
        suspendCancellableCoroutine { cont ->
            val action = "${context.packageName}.USB_PERMISSION"
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (intent.action != action) return
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    runCatching { context.unregisterReceiver(this) }
                    cont.resume(granted)
                }
            }
            val filter = IntentFilter(action)
            ContextCompat.registerReceiver(
                context,
                receiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                Intent(action).setPackage(context.packageName),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            usbManager.requestPermission(device, pendingIntent)
            cont.invokeOnCancellation { runCatching { context.unregisterReceiver(receiver) } }
        }
}
