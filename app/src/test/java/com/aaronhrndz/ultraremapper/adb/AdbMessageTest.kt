package com.aaronhrndz.ultraremapper.adb

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class AdbMessageTest {

    @Test
    fun testAdbMessageSerialization() {
        val payload = "shell:getprop ro.build.version.release\u0000".toByteArray(Charsets.UTF_8)
        val msg = AdbMessage(
            command = AdbMessage.CMD_OPEN,
            arg0 = 1,
            arg1 = 0,
            data = payload
        )

        val out = ByteArrayOutputStream()
        msg.writeTo(out)

        val bytes = out.toByteArray()
        assertEquals(AdbMessage.HEADER_LENGTH + payload.size, bytes.size)

        val inStream = ByteArrayInputStream(bytes)
        val deserialized = AdbMessage.readFrom(inStream)

        assertEquals(AdbMessage.CMD_OPEN, deserialized.command)
        assertEquals(1, deserialized.arg0)
        assertEquals(0, deserialized.arg1)
        assertArrayEquals(payload, deserialized.data)
        assertEquals(msg.magic, deserialized.magic)
    }

    @Test
    fun testAdbChecksumValidation() {
        val payload = "hello world".toByteArray(Charsets.UTF_8)
        val msg = AdbMessage(AdbMessage.CMD_WRTE, 1, 2, payload)
        var sum = 0
        for (b in payload) {
            sum += b.toInt() and 0xFF
        }
        assertEquals(sum, msg.calculateChecksum())
    }
}
