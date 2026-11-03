package com.aaronhrndz.ultraremapper.adb

import android.content.Context
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey

/**
 * Lightweight local ADB client communicating with adbd on localhost (127.0.0.1).
 */
class LocalAdbClient(
    private val host: String = "127.0.0.1",
    private val port: Int = 5555
) : AutoCloseable {

    private var socket: Socket? = null
    private var inStream: InputStream? = null
    private var outStream: OutputStream? = null
    private var localIdCounter = 1

    var isConnected: Boolean = false
        private set

    fun connect(context: Context, timeoutMs: Int = 5000): Boolean {
        try {
            val sock = Socket()
            sock.tcpNoDelay = true
            sock.connect(InetSocketAddress(host, port), timeoutMs)
            sock.soTimeout = timeoutMs

            val input = sock.getInputStream()
            val output = sock.getOutputStream()

            // 1. Send CNXN
            val banner = "host::features=shell_v2,cmd\u0000".toByteArray(Charsets.UTF_8)
            val cnxn = AdbMessage(
                command = AdbMessage.CMD_CNXN,
                arg0 = AdbMessage.CONNECT_VERSION,
                arg1 = AdbMessage.CONNECT_MAXDATA,
                data = banner
            )
            cnxn.writeTo(output)

            // 2. Read Response
            var resp = AdbMessage.readFrom(input)

            // Handle AUTH challenge if required
            if (resp.command == AdbMessage.CMD_AUTH) {
                val keyPair = AdbKeyManager.getOrCreateKeyPair(context)
                val token = resp.data

                // Attempt signature authentication first
                val signature = AdbKeyManager.signToken(token, keyPair.private as RSAPrivateKey)
                val authSig = AdbMessage(
                    command = AdbMessage.CMD_AUTH,
                    arg0 = AdbMessage.AUTH_TYPE_SIGNATURE,
                    arg1 = 0,
                    data = signature
                )
                authSig.writeTo(output)

                resp = AdbMessage.readFrom(input)

                // If signature declined, send Public Key
                if (resp.command == AdbMessage.CMD_AUTH) {
                    val pubKeyStr = AdbKeyManager.getAdbPublicKeyString(keyPair.public as RSAPublicKey)
                    val authPub = AdbMessage(
                        command = AdbMessage.CMD_AUTH,
                        arg0 = AdbMessage.AUTH_TYPE_RSAPUBLICKEY,
                        arg1 = 0,
                        data = pubKeyStr.toByteArray(Charsets.UTF_8)
                    )
                    authPub.writeTo(output)
                    resp = AdbMessage.readFrom(input)
                }
            }

            if (resp.command == AdbMessage.CMD_CNXN) {
                socket = sock
                inStream = input
                outStream = output
                isConnected = true
                return true
            } else {
                sock.close()
                return false
            }
        } catch (_: Exception) {
            close()
            return false
        }
    }

    /**
     * Executes a single shell command synchronously and returns the output string.
     */
    fun exec(command: String, timeoutMs: Int = 10000): String {
        val out = outStream ?: throw IllegalStateException("Not connected to ADB")
        val input = inStream ?: throw IllegalStateException("Not connected to ADB")
        val sock = socket ?: throw IllegalStateException("Not connected to ADB")
        sock.soTimeout = timeoutMs

        val localId = localIdCounter++
        val destination = "shell:$command\u0000".toByteArray(Charsets.UTF_8)

        // 1. OPEN channel
        val openMsg = AdbMessage(AdbMessage.CMD_OPEN, localId, 0, destination)
        openMsg.writeTo(out)

        val resp = AdbMessage.readFrom(input)
        if (resp.command != AdbMessage.CMD_OKAY) {
            throw IllegalStateException("Failed to open ADB stream: received cmd ${resp.command}")
        }
        val remoteId = resp.arg0

        val resultBuilder = StringBuilder()

        // 2. Read WRTE packets until CLSE
        while (true) {
            val packet = AdbMessage.readFrom(input)
            when (packet.command) {
                AdbMessage.CMD_WRTE -> {
                    resultBuilder.append(String(packet.data, Charsets.UTF_8))
                    // Send OKAY acknowledge
                    val okayMsg = AdbMessage(AdbMessage.CMD_OKAY, localId, remoteId)
                    okayMsg.writeTo(out)
                }
                AdbMessage.CMD_CLSE -> {
                    // Send CLSE acknowledge
                    val closeMsg = AdbMessage(AdbMessage.CMD_CLSE, localId, remoteId)
                    closeMsg.writeTo(out)
                    break
                }
                AdbMessage.CMD_OKAY -> {
                    // Ignored / stream sync
                }
                else -> {
                    break
                }
            }
        }

        return resultBuilder.toString().trim()
    }

    override fun close() {
        try { inStream?.close() } catch (_: Exception) {}
        try { outStream?.close() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        inStream = null
        outStream = null
        socket = null
        isConnected = false
    }
}
