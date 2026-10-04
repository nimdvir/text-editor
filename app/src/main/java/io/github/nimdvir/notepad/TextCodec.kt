package io.github.nimdvir.notepad

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

enum class TextEncoding(val label: String) {
    UTF8("UTF-8"),
    UTF8_BOM("UTF-8 with BOM"),
    UTF16LE("UTF-16 LE"),
    UTF16BE("UTF-16 BE"),
    ANSI("ANSI"),
}

enum class LineEnding(val label: String, val chars: String) {
    CRLF("Windows (CRLF)", "\r\n"),
    LF("Unix (LF)", "\n"),
    CR("Macintosh (CR)", "\r"),
}

/** Text with line endings normalized to `\n`, plus what is needed to write it back unchanged. */
data class DecodedText(val text: String, val encoding: TextEncoding, val lineEnding: LineEnding)

/** Reads and writes text files the way Windows Notepad does: BOM sniffing, UTF-8 by default, ANSI fallback. */
object TextCodec {
    private val BOM_UTF8 = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
    private val BOM_UTF16LE = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
    private val BOM_UTF16BE = byteArrayOf(0xFE.toByte(), 0xFF.toByte())

    // Windows-1252 is what Notepad calls "ANSI" on Western systems.
    private val ANSI: Charset = Charset.forName("windows-1252")

    val DEFAULT_LINE_ENDING = LineEnding.LF

    fun decode(bytes: ByteArray): DecodedText {
        val (raw, encoding) = when {
            bytes.startsWith(BOM_UTF8) ->
                String(bytes, 3, bytes.size - 3, Charsets.UTF_8) to TextEncoding.UTF8_BOM
            bytes.startsWith(BOM_UTF16LE) ->
                String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE) to TextEncoding.UTF16LE
            bytes.startsWith(BOM_UTF16BE) ->
                String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE) to TextEncoding.UTF16BE
            else -> decodeUtf8Strict(bytes)?.let { it to TextEncoding.UTF8 }
                ?: (String(bytes, ANSI) to TextEncoding.ANSI)
        }
        return DecodedText(normalizeLineEndings(raw), encoding, detectLineEnding(raw))
    }

    fun encode(text: String, encoding: TextEncoding, lineEnding: LineEnding): ByteArray {
        val raw = if (lineEnding == LineEnding.LF) text else text.replace("\n", lineEnding.chars)
        return when (encoding) {
            TextEncoding.UTF8 -> raw.toByteArray(Charsets.UTF_8)
            TextEncoding.UTF8_BOM -> BOM_UTF8 + raw.toByteArray(Charsets.UTF_8)
            TextEncoding.UTF16LE -> BOM_UTF16LE + raw.toByteArray(Charsets.UTF_16LE)
            TextEncoding.UTF16BE -> BOM_UTF16BE + raw.toByteArray(Charsets.UTF_16BE)
            TextEncoding.ANSI -> raw.toByteArray(ANSI)
        }
    }

    /** Uses the first line break found; files without any get the default. */
    fun detectLineEnding(raw: String): LineEnding {
        val i = raw.indexOfFirst { it == '\r' || it == '\n' }
        return when {
            i < 0 -> DEFAULT_LINE_ENDING
            raw[i] == '\n' -> LineEnding.LF
            i + 1 < raw.length && raw[i + 1] == '\n' -> LineEnding.CRLF
            else -> LineEnding.CR
        }
    }

    fun normalizeLineEndings(raw: String): String =
        if (raw.indexOf('\r') < 0) raw else raw.replace("\r\n", "\n").replace('\r', '\n')

    private fun decodeUtf8Strict(bytes: ByteArray): String? = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (e: CharacterCodingException) {
        null
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }
}
