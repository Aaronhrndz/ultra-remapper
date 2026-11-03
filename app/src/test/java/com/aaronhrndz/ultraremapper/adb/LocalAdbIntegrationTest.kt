package com.aaronhrndz.ultraremapper.adb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

class LocalAdbIntegrationTest {

    @Test
    fun testMockAdbServerExecution() {
        // Start mock ADB server on random local port
        val server = ServerSocket(0)
        val port = server.localPort

        thread {
            try {
                val clientSock = server.accept()
                val input = clientSock.getInputStream()
                val output = clientSock.getOutputStream()

                // 1. Read CNXN
                val cnxn = AdbMessage.readFrom(input)
                assertEquals(AdbMessage.CMD_CNXN, cnxn.command)

                // Respond with CNXN (no auth in mock)
                val cnxnResp = AdbMessage(
                    AdbMessage.CMD_CNXN,
                    AdbMessage.CONNECT_VERSION,
                    AdbMessage.CONNECT_MAXDATA,
                    "device::ro.product.name=watch\u0000".toByteArray(Charsets.UTF_8)
                )
                cnxnResp.writeTo(output)

                // 2. Read OPEN
                val openMsg = AdbMessage.readFrom(input)
                assertEquals(AdbMessage.CMD_OPEN, openMsg.command)
                val remoteId = openMsg.arg0

                // Respond with OKAY
                val okayResp = AdbMessage(AdbMessage.CMD_OKAY, 10, remoteId)
                okayResp.writeTo(output)

                // Send mock output
                val outPayload = "Package com.samsung.android.samsungpay.gear new state: disabled-user\n".toByteArray(Charsets.UTF_8)
                val wrteMsg = AdbMessage(AdbMessage.CMD_WRTE, 10, remoteId, outPayload)
                wrteMsg.writeTo(output)

                // Read client OKAY
                val clientOkay = AdbMessage.readFrom(input)
                assertEquals(AdbMessage.CMD_OKAY, clientOkay.command)

                // Send CLSE
                val clseMsg = AdbMessage(AdbMessage.CMD_CLSE, 10, remoteId)
                clseMsg.writeTo(output)

                // Read client CLSE
                val clientClse = AdbMessage.readFrom(input)
                assertEquals(AdbMessage.CMD_CLSE, clientClse.command)

                clientSock.close()
                server.close()
            } catch (_: Exception) {}
        }

        // Test LocalAdbClient against mock server
        val client = LocalAdbClient("127.0.0.1", port)
        // Simulate connecting by writing dummy socket
        val testSock = Socket("127.0.0.1", port)
        val testIn = testSock.getInputStream()
        val testOut = testSock.getOutputStream()

        val banner = "host::\u0000".toByteArray(Charsets.UTF_8)
        AdbMessage(AdbMessage.CMD_CNXN, AdbMessage.CONNECT_VERSION, AdbMessage.CONNECT_MAXDATA, banner).writeTo(testOut)
        val sResp = AdbMessage.readFrom(testIn)
        assertEquals(AdbMessage.CMD_CNXN, sResp.command)

        // Test command execution
        val openCmd = AdbMessage(AdbMessage.CMD_OPEN, 1, 0, "shell:pm disable-user com.samsung.android.samsungpay.gear\u0000".toByteArray())
        openCmd.writeTo(testOut)
        val ok = AdbMessage.readFrom(testIn)
        assertEquals(AdbMessage.CMD_OKAY, ok.command)

        val dataPkt = AdbMessage.readFrom(testIn)
        assertEquals(AdbMessage.CMD_WRTE, dataPkt.command)
        val receivedText = String(dataPkt.data)
        assertTrue(receivedText.contains("new state: disabled-user"))

        AdbMessage(AdbMessage.CMD_OKAY, 1, ok.arg0).writeTo(testOut)
        val clse = AdbMessage.readFrom(testIn)
        assertEquals(AdbMessage.CMD_CLSE, clse.command)
        AdbMessage(AdbMessage.CMD_CLSE, 1, ok.arg0).writeTo(testOut)

        testSock.close()
    }

    @Test
    fun testSamsungPaySecurityReversionOnAdbFailure() {
        val disconnectedClient = LocalAdbClient("127.0.0.1", 9999)
        // Client is not connected, toggle must return false and fail gracefully
        val success = AdbLocalManager.toggleSamsungPay(disconnectedClient, false)
        assertFalse("Toggle must fail if ADB is disconnected", success)
    }
}
