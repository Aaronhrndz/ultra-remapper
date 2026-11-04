package com.aaronhrndz.ultraremapper.adb

import android.content.Context
import io.github.muntashirakon.adb.AdbConnection
import io.github.muntashirakon.adb.PairingConnectionCtx
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * ADB client implementation for local Wear OS (127.0.0.1) supporting both:
 * 1. SPAKE2 TLS 1.3 pairing via ephemeral pairing port & 6-digit code.
 * 2. Shell execution over AdbConnection (TLS or TCP).
 */
class LocalAdbClient(
    val host: String = "127.0.0.1",
    val port: Int = 5555
) : AutoCloseable {

    private var adbConnection: AdbConnection? = null

    var isConnected: Boolean = false
        private set

    /**
     * Performs SPAKE2 TLS 1.3 pairing using the 6-digit PIN on the pairing port.
     */
    fun pair(context: Context, pairingPort: Int, pin: String): Boolean {
        return try {
            val keyPair = AdbKeyManager.getOrCreateKeyPair(context)
            val cert = AdbKeyManager.getOrCreateCertificate(context)
            val pinBytes = pin.toByteArray(Charsets.UTF_8)

            val pairingCtx = PairingConnectionCtx(
                host,
                pairingPort,
                pinBytes,
                keyPair.private,
                cert,
                "UltraRemapper"
            )
            pairingCtx.use {
                it.start()
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Connects to the ADB daemon connection port.
     */
    fun connect(context: Context, timeoutMs: Long = 8000L): Boolean {
        return try {
            val keyPair = AdbKeyManager.getOrCreateKeyPair(context)
            val cert = AdbKeyManager.getOrCreateCertificate(context)

            val builder = AdbConnection.Builder(host, port)
                .setPrivateKey(keyPair.private)
                .setCertificate(cert)
                .setDeviceName("UltraRemapper")

            val conn = builder.build()
            conn.connect(timeoutMs, TimeUnit.MILLISECONDS, false)

            if (conn.isConnectionEstablished) {
                adbConnection = conn
                isConnected = true
                true
            } else {
                conn.close()
                false
            }
        } catch (_: Exception) {
            close()
            false
        }
    }

    /**
     * Executes a single shell command synchronously and returns the output string.
     */
    fun exec(command: String): String {
        val conn = adbConnection ?: throw IllegalStateException("Not connected to ADB")
        val stream = conn.open("shell:$command")
        val reader = BufferedReader(InputStreamReader(stream.openInputStream()))
        val output = StringBuilder()
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            output.append(line).append("\n")
        }
        stream.close()
        return output.toString().trim()
    }

    override fun close() {
        try {
            adbConnection?.close()
        } catch (_: Exception) {}
        adbConnection = null
        isConnected = false
    }
}
