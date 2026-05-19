package pl.polsatgranie.smartmegane.data.can

import android.os.SystemClock
import kotlin.math.min

class WaveshareFrameParser {
    private var buffer = ByteArray(0)

    fun append(bytes: ByteArray): List<CanFrame> {
        if (bytes.isEmpty()) return emptyList()
        val combined = ByteArray(buffer.size + bytes.size)
        System.arraycopy(buffer, 0, combined, 0, buffer.size)
        System.arraycopy(bytes, 0, combined, buffer.size, bytes.size)
        buffer = combined

        val frames = mutableListOf<CanFrame>()
        var index = 0
        while (buffer.size - index >= 20) {
            if (buffer[index] == 0xAA.toByte() && buffer[index + 1] == 0x55.toByte()) {
                val rawId = ((buffer[index + 3].toInt() and 0xFF) shl 24) or
                    ((buffer[index + 4].toInt() and 0xFF) shl 16) or
                    ((buffer[index + 5].toInt() and 0xFF) shl 8) or
                    (buffer[index + 6].toInt() and 0xFF)
                val canId = rawId and 0x7FF
                val dlc = min(buffer[index + 9].toInt() and 0xFF, 8)
                val payload = ByteArray(8)
                if (dlc > 0) {
                    System.arraycopy(buffer, index + 10, payload, 0, dlc)
                }
                frames.add(
                    CanFrame(
                        id = canId,
                        dlc = dlc,
                        data = payload,
                        timestampMs = SystemClock.elapsedRealtime(),
                    )
                )
                index += 20
            } else {
                index += 1
            }
        }
        buffer = buffer.copyOfRange(index, buffer.size)
        return frames
    }
}
