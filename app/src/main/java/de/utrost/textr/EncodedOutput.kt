package de.utrost.textr

import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/** Strict streaming encoding: no substitutions, newline normalization, or unbounded byte buffer. */
object EncodedOutput {
    fun write(text: String, encoding: TextEncoding, bom: Boolean, output: OutputStream, check: () -> Unit = { RenderPolicy.checkCancellation() }): Int {
        require(text.length <= MAX_FILE_BYTES)
        val marker = if (!bom) byteArrayOf() else when (encoding) {
            TextEncoding.UTF8 -> byteArrayOf(0xef.toByte(), 0xbb.toByte(), 0xbf.toByte())
            TextEncoding.UTF16LE -> byteArrayOf(0xff.toByte(), 0xfe.toByte())
            TextEncoding.UTF16BE -> byteArrayOf(0xfe.toByte(), 0xff.toByte())
            else -> error("BOM unsupported for this encoding")
        }
        val encoder = Charset.forName(encoding.charsetName).newEncoder()
            .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        val input = CharBuffer.wrap(text)
        val bytes = ByteBuffer.allocate(8192)
        var written = 0
        fun emit(value: ByteArray, count: Int) {
            check()
            require(written + count <= MAX_FILE_BYTES)
            output.write(value, 0, count)
            written += count
        }
        emit(marker, marker.size)
        while (true) {
            check()
            val result = encoder.encode(input, bytes, true)
            if (result.isError) result.throwException()
            emit(bytes.array(), bytes.position()); bytes.clear()
            if (result.isUnderflow) break
        }
        while (true) {
            check()
            val result = encoder.flush(bytes)
            if (result.isError) result.throwException()
            emit(bytes.array(), bytes.position()); bytes.clear()
            if (result.isUnderflow) break
        }
        check()
        return written
    }
}
