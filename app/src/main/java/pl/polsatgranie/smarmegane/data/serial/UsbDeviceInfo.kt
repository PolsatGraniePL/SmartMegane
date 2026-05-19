package pl.polsatgranie.smarmegane.data.serial

data class UsbDeviceInfo(
    val deviceId: Int,
    val deviceName: String,
    val vendorId: Int,
    val productId: Int,
    val hasDriver: Boolean,
    val portCount: Int,
) {
    val vendorIdHex: String
        get() = vendorId.toString(16).uppercase().padStart(4, '0')

    val productIdHex: String
        get() = productId.toString(16).uppercase().padStart(4, '0')
}
