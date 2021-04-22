package com.reach_android.model.remotesupport

import java.io.InputStream
import java.nio.ByteBuffer

/**
 * [InputStream] backed by a [ByteBuffer]
 */
class ByteBufferInputStream(
    private val buffer: ByteBuffer
) : InputStream() {

    override fun read(): Int {
        return if (!buffer.hasRemaining()) {
            -1
        } else buffer.get().toInt() and 0xFF
    }

    override fun read(bytes: ByteArray, off: Int, length: Int): Int {
        var len = length
        if (!buffer.hasRemaining()) {
            return -1
        }
        len = len.coerceAtMost(buffer.remaining())
        buffer.get(bytes, off, len)
        return len
    }
}