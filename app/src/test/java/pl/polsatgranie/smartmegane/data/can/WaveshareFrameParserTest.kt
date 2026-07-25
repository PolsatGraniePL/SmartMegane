package pl.polsatgranie.smartmegane.data.can

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WaveshareFrameParserTest {
    @Test
    fun parsesOfficialStandardDataFrameVector() {
        val parser = WaveshareFrameParser(clockMs = { 1234L })

        val frames = parser.append(OFFICIAL_STANDARD_FRAME)

        assertEquals(1, frames.size)
        with(frames.single()) {
            assertEquals(0x123, id)
            assertEquals("123", idHex)
            assertEquals(8, dlc)
            assertArrayEquals(
                byteArrayOf(
                    0x11,
                    0x22,
                    0x33,
                    0x44,
                    0x55,
                    0x66,
                    0x77,
                    0x88.toByte(),
                ),
                data,
            )
            assertEquals(1234L, timestampMs)
        }
    }

    @Test
    fun resynchronizesAfterNoiseAndParsesFragmentedFrame() {
        val parser = WaveshareFrameParser()
        val prefixAndFragment = byteArrayOf(0x13, 0x37, 0x00) +
            OFFICIAL_STANDARD_FRAME.copyOfRange(0, 9)

        assertTrue(parser.append(prefixAndFragment).isEmpty())

        val frames = parser.append(
            OFFICIAL_STANDARD_FRAME.copyOfRange(9, OFFICIAL_STANDARD_FRAME.size),
        )

        assertEquals(1, frames.size)
        assertEquals(0x123, frames.single().id)
    }

    @Test
    fun rejectsBadChecksumAndContinuesAtTheNextValidFrame() {
        val parser = WaveshareFrameParser()
        val damaged = OFFICIAL_STANDARD_FRAME.copyOf().also {
            it[it.lastIndex] = (it.last().toInt() xor 0x01).toByte()
        }

        val frames = parser.append(damaged + OFFICIAL_STANDARD_FRAME)

        assertEquals(1, frames.size)
        assertEquals(0x123, frames.single().id)
    }

    @Test
    fun resetDropsBufferedFragment() {
        val parser = WaveshareFrameParser()
        val splitAt = 11

        assertTrue(parser.append(OFFICIAL_STANDARD_FRAME.copyOfRange(0, splitAt)).isEmpty())
        parser.reset()
        assertTrue(
            parser.append(
                OFFICIAL_STANDARD_FRAME.copyOfRange(splitAt, OFFICIAL_STANDARD_FRAME.size),
            ).isEmpty(),
        )

        val frames = parser.append(OFFICIAL_STANDARD_FRAME)

        assertEquals(1, frames.size)
        assertEquals(0x123, frames.single().id)
    }

    @Test
    fun ignoresRemoteFramesBecauseTheyDoNotContainPayloadData() {
        val parser = WaveshareFrameParser()
        val remoteFrame = OFFICIAL_STANDARD_FRAME.copyOf().also {
            it[4] = 0x02
            it[19] = 0x94.toByte()
        }

        assertTrue(parser.append(remoteFrame).isEmpty())
    }

    private companion object {
        // AA 55 | packet | standard | data | ID 0x123 (little-endian) | DLC | data | reserved | sum.
        val OFFICIAL_STANDARD_FRAME = byteArrayOf(
            0xAA.toByte(),
            0x55,
            0x01,
            0x01,
            0x01,
            0x23,
            0x01,
            0x00,
            0x00,
            0x08,
            0x11,
            0x22,
            0x33,
            0x44,
            0x55,
            0x66,
            0x77,
            0x88.toByte(),
            0x00,
            0x93.toByte(),
        )
    }
}
