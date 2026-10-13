package com.nexatrode.nexawal.logic

import java.io.InputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BoundedRateBodyTest {
    @Test fun limitsBothAdvertisedAndUnknownLengthBodies() {
        assertEquals("{}", BoundedRateBody.read("{}".byteInputStream()))
        assertEquals(16_384, BoundedRateBody.read(ByteArray(16_384) { 120 }.inputStream()).length)
        assertThrows(IllegalArgumentException::class.java) {
            BoundedRateBody.read("{}".byteInputStream(), 16_385)
        }
        var bytesRead = 0
        val endless = object : InputStream() {
            override fun read(): Int { bytesRead++; return 120 }
        }
        assertThrows(IllegalArgumentException::class.java) { BoundedRateBody.read(endless) }
        assertEquals(16_385, bytesRead)
    }
}
