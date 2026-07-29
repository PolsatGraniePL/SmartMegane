package pl.polsatgranie.smartmegane.domain.signal

import kotlin.math.pow
import pl.polsatgranie.smartmegane.data.can.CanFrame

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
    val validWhen: (CanFrame) -> Boolean = { true },
) : SignalSpec {
    override fun decode(frame: CanFrame): SignalValue? {
        if (byteIndex !in 0 until frame.dlc || !validWhen(frame)) return null
        val value = frame.data[byteIndex].toInt() and 0xFF
        val isSet = value and mask != 0
        return SignalValue.Bool(if (activeHigh) isSet else !isSet)
    }
}

data class EnumSignalSpec(
    override val key: SignalKey,
    override val canId: Int,
    val byteIndex: Int,
    val mask: Int,
    val shift: Int,
    val mapping: Map<Int, String>,
    val validWhen: (CanFrame) -> Boolean = { true },
) : SignalSpec {
    override fun decode(frame: CanFrame): SignalValue? {
        if (byteIndex !in 0 until frame.dlc || !validWhen(frame)) return null
        val value = frame.data[byteIndex].toInt() and 0xFF
        val code = (value and mask) ushr shift
        return SignalValue.Enum(code, mapping[code] ?: "Unknown($code)")
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
    val validWhen: (CanFrame) -> Boolean = { true },
) : SignalSpec {
    override fun decode(frame: CanFrame): SignalValue? {
        if (length <= 0 || startByte < 0 || startByte + length > frame.dlc) return null
        if (!validWhen(frame)) return null
        val raw = readUnsigned(frame.data, startByte, length, littleEndian)
        val signedValue = if (signed) signExtend(raw, length * 8) else raw
        return SignalValue.Number(signedValue * scale + offset, unit)
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
    val validWhen: (CanFrame) -> Boolean = { true },
) : SignalSpec {
    override fun decode(frame: CanFrame): SignalValue? {
        if (startBit < 0 || length !in 1..63 || startBit + length > frame.dlc * 8) {
            return null
        }
        if (!validWhen(frame)) return null

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

data class MaskedIntSignalSpec(
    override val key: SignalKey,
    override val canId: Int,
    val byteIndex: Int,
    val mask: Int,
    val shift: Int = 0,
    val scale: Double = 1.0,
    val offset: Double = 0.0,
    val unit: String? = null,
    val validWhen: (CanFrame) -> Boolean = { true },
) : SignalSpec {
    override fun decode(frame: CanFrame): SignalValue? {
        if (byteIndex !in 0 until frame.dlc || !validWhen(frame)) return null
        val byte = frame.data[byteIndex].toInt() and 0xFF
        val raw = (byte and mask) ushr shift
        return SignalValue.Number(raw * scale + offset, unit)
    }
}

data class MaskedBoolSignalSpec(
    override val key: SignalKey,
    override val canId: Int,
    val byteIndex: Int,
    val mask: Int,
    val expectedValue: Int,
    val validWhen: (CanFrame) -> Boolean = { true },
) : SignalSpec {
    override fun decode(frame: CanFrame): SignalValue? {
        if (byteIndex !in 0 until frame.dlc || !validWhen(frame)) return null
        val byte = frame.data[byteIndex].toInt() and 0xFF
        return SignalValue.Bool(byte and mask == expectedValue)
    }
}

fun bitSignal(
    key: SignalKey,
    canId: Int,
    byteIndex: Int,
    mask: Int,
    activeHigh: Boolean = true,
    validWhen: (CanFrame) -> Boolean = { true },
): SignalSpec = BitSignalSpec(
    key = key,
    canId = canId,
    byteIndex = byteIndex,
    mask = mask,
    activeHigh = activeHigh,
    validWhen = validWhen,
)

fun enumSignal(
    key: SignalKey,
    canId: Int,
    byteIndex: Int,
    mask: Int,
    shift: Int,
    mapping: Map<Int, String>,
    validWhen: (CanFrame) -> Boolean = { true },
): SignalSpec = EnumSignalSpec(
    key = key,
    canId = canId,
    byteIndex = byteIndex,
    mask = mask,
    shift = shift,
    mapping = mapping,
    validWhen = validWhen,
)

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
    validWhen: (CanFrame) -> Boolean = { true },
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
    validWhen = validWhen,
)

fun bigEndianBitFieldSignal(
    key: SignalKey,
    canId: Int,
    startBit: Int,
    length: Int,
    scale: Double = 1.0,
    offset: Double = 0.0,
    unit: String? = null,
    validWhen: (CanFrame) -> Boolean = { true },
): SignalSpec = BigEndianBitFieldSignalSpec(
    key = key,
    canId = canId,
    startBit = startBit,
    length = length,
    scale = scale,
    offset = offset,
    unit = unit,
    validWhen = validWhen,
)

fun maskedIntSignal(
    key: SignalKey,
    canId: Int,
    byteIndex: Int,
    mask: Int,
    shift: Int = 0,
    scale: Double = 1.0,
    offset: Double = 0.0,
    unit: String? = null,
    validWhen: (CanFrame) -> Boolean = { true },
): SignalSpec = MaskedIntSignalSpec(
    key = key,
    canId = canId,
    byteIndex = byteIndex,
    mask = mask,
    shift = shift,
    scale = scale,
    offset = offset,
    unit = unit,
    validWhen = validWhen,
)

fun maskedBoolSignal(
    key: SignalKey,
    canId: Int,
    byteIndex: Int,
    mask: Int,
    expectedValue: Int,
    validWhen: (CanFrame) -> Boolean = { true },
): SignalSpec = MaskedBoolSignalSpec(
    key = key,
    canId = canId,
    byteIndex = byteIndex,
    mask = mask,
    expectedValue = expectedValue,
    validWhen = validWhen,
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
        startByte until startByte + length
    }
    for (index in indices) {
        result = (result shl 8) or (data[index].toInt() and 0xFF).toLong()
    }
    return result
}
