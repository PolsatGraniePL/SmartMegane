package pl.polsatgranie.smartmegane.data.can

class WaveshareFrameParser(
    private val clockMs: () -> Long = { System.nanoTime() / NANOS_PER_MILLISECOND },
) {
    private var buffer = ByteArray(0)

    fun append(bytes: ByteArray): List<CanFrame> {
        if (bytes.isEmpty()) return emptyList()

        val combined = ByteArray(buffer.size + bytes.size)
        System.arraycopy(buffer, 0, combined, 0, buffer.size)
        System.arraycopy(bytes, 0, combined, buffer.size, bytes.size)
        buffer = combined

        val frames = mutableListOf<CanFrame>()
        var index = 0
        while (buffer.size - index >= FRAME_SIZE) {
            if (!hasHeaderAt(index) || !isValidFrameAt(index)) {
                index += 1
                continue
            }

            // A valid RTR envelope must be consumed as one complete record, but it
            // deliberately produces no CanFrame because it has no payload.
            if (unsignedByte(index + FRAME_FORMAT_OFFSET) == REMOTE_FRAME) {
                index += FRAME_SIZE
                continue
            }

            val frameType = unsignedByte(index + FRAME_TYPE_OFFSET)
            val rawId = unsignedByte(index + ID_OFFSET).toLong() or
                (unsignedByte(index + ID_OFFSET + 1).toLong() shl 8) or
                (unsignedByte(index + ID_OFFSET + 2).toLong() shl 16) or
                (unsignedByte(index + ID_OFFSET + 3).toLong() shl 24)
            val canId = (rawId and if (frameType == STANDARD_FRAME) {
                STANDARD_ID_MASK
            } else {
                EXTENDED_ID_MASK
            }).toInt()
            val dlc = unsignedByte(index + DLC_OFFSET)
            val payload = ByteArray(MAX_DLC)
            if (dlc > 0) {
                System.arraycopy(buffer, index + DATA_OFFSET, payload, 0, dlc)
            }

            frames += CanFrame(
                id = canId,
                dlc = dlc,
                data = payload,
                timestampMs = clockMs(),
            )
            index += FRAME_SIZE
        }

        buffer = buffer.copyOfRange(index, buffer.size)
        return frames
    }

    fun reset() {
        buffer = ByteArray(0)
    }

    private fun hasHeaderAt(index: Int): Boolean =
        buffer[index] == HEADER_FIRST &&
            buffer[index + 1] == HEADER_SECOND

    private fun isValidFrameAt(index: Int): Boolean {
        if (unsignedByte(index + PACKET_TYPE_OFFSET) != DATA_PACKET) return false

        val frameType = unsignedByte(index + FRAME_TYPE_OFFSET)
        if (frameType != STANDARD_FRAME && frameType != EXTENDED_FRAME) return false

        val frameFormat = unsignedByte(index + FRAME_FORMAT_OFFSET)
        if (frameFormat != DATA_FRAME && frameFormat != REMOTE_FRAME) return false

        if (unsignedByte(index + DLC_OFFSET) > MAX_DLC) return false

        var checksum = 0
        for (offset in PACKET_TYPE_OFFSET..RESERVED_OFFSET) {
            checksum = (checksum + unsignedByte(index + offset)) and 0xFF
        }
        return checksum == unsignedByte(index + CHECKSUM_OFFSET)
    }

    private fun unsignedByte(index: Int): Int = buffer[index].toInt() and 0xFF

    private companion object {
        const val FRAME_SIZE = 20
        const val MAX_DLC = 8

        val HEADER_FIRST = 0xAA.toByte()
        val HEADER_SECOND = 0x55.toByte()

        const val PACKET_TYPE_OFFSET = 2
        const val FRAME_TYPE_OFFSET = 3
        const val FRAME_FORMAT_OFFSET = 4
        const val ID_OFFSET = 5
        const val DLC_OFFSET = 9
        const val DATA_OFFSET = 10
        const val RESERVED_OFFSET = 18
        const val CHECKSUM_OFFSET = 19

        const val DATA_PACKET = 0x01
        const val STANDARD_FRAME = 0x01
        const val EXTENDED_FRAME = 0x02
        const val DATA_FRAME = 0x01
        const val REMOTE_FRAME = 0x02

        const val STANDARD_ID_MASK = 0x7FFL
        const val EXTENDED_ID_MASK = 0x1FFFFFFFL
        const val NANOS_PER_MILLISECOND = 1_000_000L
    }
}
