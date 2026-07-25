package pl.polsatgranie.smartmegane.domain.signal

import org.junit.Assert.assertEquals
import org.junit.Test
import pl.polsatgranie.smartmegane.data.can.CanFrame

class IntSignalSpecTest {
    private val key = SignalKey("test", "Test")

    @Test
    fun decodesLittleEndianPositiveValue() {
        val value = decode(
            bytes = byteArrayOf(0x01, 0x00, 0x00, 0x00),
            littleEndian = true,
            signed = true,
        )

        assertEquals(1.0, value, 0.0)
    }

    @Test
    fun decodesLittleEndianNegativeValue() {
        val value = decode(
            bytes = byteArrayOf(
                0xFE.toByte(),
                0xFF.toByte(),
                0xFF.toByte(),
                0xFF.toByte(),
            ),
            littleEndian = true,
            signed = true,
        )

        assertEquals(-2.0, value, 0.0)
    }

    @Test
    fun decodesBigEndianPositiveValue() {
        val value = decode(
            bytes = byteArrayOf(0x00, 0x00, 0x00, 0x01),
            littleEndian = false,
            signed = true,
        )

        assertEquals(1.0, value, 0.0)
    }

    @Test
    fun wiperDefinitionDecodesAllFourModes() {
        val spec = SignalDefinitions.specs.single {
            it.key == SignalDefinitions.wipersMode
        }
        listOf(0x00, 0x40, 0x80, 0xC0).forEachIndexed { expected, byte ->
            val frame = CanFrame(
                id = spec.canId,
                dlc = 8,
                data = byteArrayOf(0, 0, byte.toByte(), 0, 0, 0, 0, 0),
                timestampMs = 0,
            )

            val result = spec.decode(frame) as SignalValue.Enum

            assertEquals(expected, result.code)
        }
    }

    private fun decode(
        bytes: ByteArray,
        littleEndian: Boolean,
        signed: Boolean,
    ): Double {
        val spec = IntSignalSpec(
            key = key,
            canId = 0x123,
            startByte = 0,
            length = bytes.size,
            littleEndian = littleEndian,
            signed = signed,
        )
        val frame = CanFrame(
            id = 0x123,
            dlc = bytes.size,
            data = bytes,
            timestampMs = 0,
        )
        return (spec.decode(frame) as SignalValue.Number).value
    }
}
