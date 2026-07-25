package pl.polsatgranie.smartmegane.domain.signal

import pl.polsatgranie.smartmegane.data.can.CanFrame
import kotlin.math.pow

sealed interface SignalSpec {
    val key: SignalKey
    val canId: Int

    fun decode(frame: CanFrame): SignalValue?
}

data class BitSignalSpec(
    override val key: SignalKey,
    override val canId: Int,
    val byteIndex: Int,
    val mask: Int,
    val activeHigh: Boolean = true,
) : SignalSpec {
    override fun decode(frame: CanFrame): SignalValue? {
        if (byteIndex !in 0 until frame.dlc) return null
        val value = frame.data.getOrNull(byteIndex)?.toInt()?.and(0xFF) ?: return null
        val isSet = value and mask != 0
        val result = if (activeHigh) isSet else !isSet
        return SignalValue.Bool(result)
    }
}

data class EnumSignalSpec(
    override val key: SignalKey,
    override val canId: Int,
    val byteIndex: Int,
    val mask: Int,
    val shift: Int,
    val mapping: Map<Int, String>,
) : SignalSpec {
    override fun decode(frame: CanFrame): SignalValue? {
        if (byteIndex !in 0 until frame.dlc) return null
        val value = frame.data.getOrNull(byteIndex)?.toInt()?.and(0xFF) ?: return null
        val code = (value and mask) shr shift
        val label = mapping[code] ?: "Unknown($code)"
        return SignalValue.Enum(code, label)
    }
}

data class IntSignalSpec(
    override val key: SignalKey,
    override val canId: Int,
    val startByte: Int,
    val length: Int,
    val signed: Boolean = false,
    val littleEndian: Boolean = true,
    val scale: Double = 1.0,
    val offset: Double = 0.0,
    val unit: String? = null,
) : SignalSpec {
    override fun decode(frame: CanFrame): SignalValue? {
        if (length <= 0 || startByte < 0 || startByte + length > frame.dlc) return null
        val raw = readUnsigned(frame.data, startByte, length, littleEndian)
        val signedValue = if (signed) signExtend(raw, length * 8) else raw.toLong()
        val scaled = signedValue * scale + offset
        return SignalValue.Number(scaled, unit)
    }

    private fun signExtend(value: Long, bits: Int): Long {
        val signBit = 1L shl (bits - 1)
        return if (value and signBit != 0L) {
            value - 2.0.pow(bits).toLong()
        } else {
            value
        }
    }
}

/**
 * Reads a big-endian field that does not have to start on a byte boundary.
 *
 * [startBit] is counted from the most-significant bit of byte 0. For example,
 * startBit=0/length=20 reads the first five hexadecimal nibbles of a frame.
 */
data class BigEndianBitFieldSignalSpec(
    override val key: SignalKey,
    override val canId: Int,
    val startBit: Int,
    val length: Int,
    val scale: Double = 1.0,
    val offset: Double = 0.0,
    val unit: String? = null,
) : SignalSpec {
    override fun decode(frame: CanFrame): SignalValue? {
        if (startBit < 0 || length !in 1..63 || startBit + length > frame.dlc * 8) {
            return null
        }

        var raw = 0L
        for (bitOffset in 0 until length) {
            val absoluteBit = startBit + bitOffset
            val byteIndex = absoluteBit / 8
            val bitInByte = 7 - (absoluteBit % 8)
            val bit = (frame.data[byteIndex].toInt() ushr bitInByte) and 0x01
            raw = (raw shl 1) or bit.toLong()
        }
        return SignalValue.Number(raw * scale + offset, unit)
    }
}

fun bitSignal(
    key: SignalKey,
    canId: Int,
    byteIndex: Int,
    mask: Int,
    activeHigh: Boolean = true,
): SignalSpec = BitSignalSpec(key, canId, byteIndex, mask, activeHigh)

fun enumSignal(
    key: SignalKey,
    canId: Int,
    byteIndex: Int,
    mask: Int,
    shift: Int,
    mapping: Map<Int, String>,
): SignalSpec = EnumSignalSpec(key, canId, byteIndex, mask, shift, mapping)

fun intSignal(
    key: SignalKey,
    canId: Int,
    startByte: Int,
    length: Int,
    signed: Boolean = false,
    littleEndian: Boolean = true,
    scale: Double = 1.0,
    offset: Double = 0.0,
    unit: String? = null,
): SignalSpec = IntSignalSpec(
    key = key,
    canId = canId,
    startByte = startByte,
    length = length,
    signed = signed,
    littleEndian = littleEndian,
    scale = scale,
    offset = offset,
    unit = unit,
)

fun bigEndianBitFieldSignal(
    key: SignalKey,
    canId: Int,
    startBit: Int,
    length: Int,
    scale: Double = 1.0,
    offset: Double = 0.0,
    unit: String? = null,
): SignalSpec = BigEndianBitFieldSignalSpec(
    key = key,
    canId = canId,
    startBit = startBit,
    length = length,
    scale = scale,
    offset = offset,
    unit = unit,
)

private fun readUnsigned(
    data: ByteArray,
    startByte: Int,
    length: Int,
    littleEndian: Boolean,
): Long {
    var result = 0L
    val indices = if (littleEndian) {
        (startByte until startByte + length).reversed()
    } else {
        (startByte until startByte + length)
    }
    for (index in indices) {
        result = (result shl 8) or (data[index].toInt() and 0xFF).toLong()
    }
    return result
}
