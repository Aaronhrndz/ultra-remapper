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

    companion object {
        private const val PREFS_NAME = "ultra_adb_state"
        private const val KEY_IS_PAIRED = "is_adb_paired"
        private const val KEY_LAST_PORT = "last_connect_port"

        @Volatile
        var activeInstance: LocalAdbClient? = null
            private set

        fun isPaired(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_IS_PAIRED, false)
        }

        fun setPaired(context: Context, paired: Boolean, port: Int? = null) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val editor = prefs.edit().putBoolean(KEY_IS_PAIRED, paired)
            if (port != null && port > 0) {
                editor.putInt(KEY_LAST_PORT, port)
            }
            editor.apply()
        }

        fun getLastPort(context: Context): Int {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getInt(KEY_LAST_PORT, 5555)
        }

        fun setLastPort(context: Context, port: Int) {
            if (port > 0) {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit().putInt(KEY_LAST_PORT, port).apply()
            }
        }
    }

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
            setPaired(context, true, port)
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
                activeInstance = this
                setPaired(context, true, port)
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
    fun exec(command: String, timeoutMs: Long = 5000L): String {
        val conn = adbConnection ?: activeInstance?.adbConnection
            ?: throw IllegalStateException("Not connected to ADB")
        val stream = conn.open("shell:$command")
        val output = StringBuilder()
        val inStream = stream.openInputStream()
        val buffer = ByteArray(1024)
        val startTime = System.currentTimeMillis()

        try {
            while (!stream.isClosed) {
                val available = inStream.available()
                if (available > 0) {
                    val readBytes = inStream.read(buffer, 0, minOf(available, buffer.size))
                    if (readBytes > 0) {
                        output.append(String(buffer, 0, readBytes, Charsets.UTF_8))
                    }
                } else {
                    if (System.currentTimeMillis() - startTime > timeoutMs) {
                        break
                    }
                    Thread.sleep(50)
                }
            }
            // Read any final remaining bytes after close
            while (inStream.available() > 0) {
                val readBytes = inStream.read(buffer, 0, minOf(inStream.available(), buffer.size))
                if (readBytes > 0) {
                    output.append(String(buffer, 0, readBytes, Charsets.UTF_8))
                }
            }
        } catch (_: Exception) {
        } finally {
            try {
                stream.close()
            } catch (_: Exception) {}
        }

        val result = output.toString().trim()
        android.util.Log.d("LocalAdbClient", "exec('$command') -> '$result'")
        return result
    }

    override fun close() {
        try {
            adbConnection?.close()
        } catch (_: Exception) {}
        adbConnection = null
        isConnected = false
        if (activeInstance == this) {
            activeInstance = null
        }
    }
}
