package io.github.nimdvir.notepad

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class TextCodecTest {

    @Test
    fun plainUtf8WithLf() {
        val d = TextCodec.decode("héllo\nworld".toByteArray(Charsets.UTF_8))
        assertEquals("héllo\nworld", d.text)
        assertEquals(TextEncoding.UTF8, d.encoding)
        assertEquals(LineEnding.LF, d.lineEnding)
    }

    @Test
    fun crlfIsNormalizedAndRestored() {
        val bytes = "a\r\nb\r\n".toByteArray(Charsets.UTF_8)
        val d = TextCodec.decode(bytes)
        assertEquals("a\nb\n", d.text)
        assertEquals(LineEnding.CRLF, d.lineEnding)
        assertArrayEquals(bytes, TextCodec.encode(d.text, d.encoding, d.lineEnding))
    }

    @Test
    fun classicMacCr() {
        val d = TextCodec.decode("a\rb".toByteArray())
        assertEquals("a\nb", d.text)
        assertEquals(LineEnding.CR, d.lineEnding)
    }

    @Test
    fun noLineBreaksUsesDefault() {
        assertEquals(TextCodec.DEFAULT_LINE_ENDING, TextCodec.decode("one line".toByteArray()).lineEnding)
    }

    @Test
    fun utf8BomRoundTrip() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "שלום".toByteArray(Charsets.UTF_8)
        val d = TextCodec.decode(bytes)
        assertEquals("שלום", d.text)
        assertEquals(TextEncoding.UTF8_BOM, d.encoding)
        assertArrayEquals(bytes, TextCodec.encode(d.text, d.encoding, d.lineEnding))
    }

    @Test
    fun utf16LeAndBeRoundTrip() {
        for ((enc, bom, cs) in listOf(
            Triple(TextEncoding.UTF16LE, byteArrayOf(0xFF.toByte(), 0xFE.toByte()), Charsets.UTF_16LE),
            Triple(TextEncoding.UTF16BE, byteArrayOf(0xFE.toByte(), 0xFF.toByte()), Charsets.UTF_16BE),
        )) {
            val bytes = bom + "x\r\ny".toByteArray(cs)
            val d = TextCodec.decode(bytes)
            assertEquals("x\ny", d.text)
            assertEquals(enc, d.encoding)
            assertEquals(LineEnding.CRLF, d.lineEnding)
            assertArrayEquals(bytes, TextCodec.encode(d.text, d.encoding, d.lineEnding))
        }
    }

    @Test
    fun invalidUtf8FallsBackToAnsi() {
        val bytes = byteArrayOf('c'.code.toByte(), 0xE9.toByte(), 0x80.toByte()) // "cé€" in windows-1252
        val d = TextCodec.decode(bytes)
        assertEquals(TextEncoding.ANSI, d.encoding)
        assertEquals("cé€", d.text)
        assertArrayEquals(bytes, TextCodec.encode(d.text, d.encoding, d.lineEnding))
    }

    @Test
    fun emptyFile() {
        val d = TextCodec.decode(ByteArray(0))
        assertEquals("", d.text)
        assertEquals(TextEncoding.UTF8, d.encoding)
    }
}
