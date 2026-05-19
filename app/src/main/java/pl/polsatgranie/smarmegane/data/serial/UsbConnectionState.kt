package pl.polsatgranie.smarmegane.data.serial

sealed class UsbConnectionState {
    data object Disconnected : UsbConnectionState()
    data object Searching : UsbConnectionState()
    data object NoDevice : UsbConnectionState()
    data class PermissionRequired(val deviceName: String) : UsbConnectionState()
    data class PermissionDenied(val deviceName: String) : UsbConnectionState()
    data class Connected(val deviceName: String) : UsbConnectionState()
    data class Error(val message: String) : UsbConnectionState()
}
