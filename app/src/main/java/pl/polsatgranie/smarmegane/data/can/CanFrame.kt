package pl.polsatgranie.smarmegane.data.can

data class CanFrame(
    val id: Int,
    val dlc: Int,
    val data: ByteArray,
    val timestampMs: Long,
) {
    val idHex: String
        get() = id.toString(16).uppercase().padStart(3, '0')

    fun dataUnsigned(): List<Int> = data.map { it.toInt() and 0xFF }
}
