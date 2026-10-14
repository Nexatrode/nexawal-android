package com.nexatrode.nexawal.logic

import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Rate JSON is tiny; reject untrusted advertised or streamed bodies before growing memory. */
object BoundedRateBody {
    const val LIMIT = 16_384

    fun read(input: InputStream, contentLength: Long = -1, limit: Int = LIMIT): String {
        require(limit in 1 until Int.MAX_VALUE && contentLength <= limit) { "response too large" }
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (true) {
            val count = input.read(buffer, 0, minOf(buffer.size, limit + 1 - output.size()))
            if (count < 0) break
            require(output.size() + count <= limit) { "response too large" }
            output.write(buffer, 0, count)
        }
        return output.toString(Charsets.UTF_8.name())
    }
}
