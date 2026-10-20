package com.aaronhrndz.ultraremapper.companion

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey

/**
 * Native ADB implementation supporting CNXN, RSA AUTH (pairing prompt on device), and shell streams.
 */
class AdbClient(private val context: Context) {

    private val TAG = "UltraAdb"

    data class DiscoveredWatch(
        val name: String,
        val host: String,
        val port: Int
    )

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    fun startDiscovery(onFound: (DiscoveredWatch) -> Unit) {
        stopDiscovery()
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {}

            override fun onServiceFound(service: NsdServiceInfo) {
                val type = service.serviceType
                if (type.contains("_adb-tls-connect") || type.contains("_adb")) {
                    nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}

                        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                            val host = serviceInfo.host?.hostAddress ?: return
                            val port = serviceInfo.port
                            val name = serviceInfo.serviceName ?: "Galaxy Watch"
                            mainHandler.post {
                                onFound(DiscoveredWatch(name, host, port))
                            }
                        }
                    })
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }
        discoveryListener = listener
        try {
            nsdManager.discoverServices("_adb-tls-connect._tcp", NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (_: Exception) {}
    }

    fun stopDiscovery() {
        discoveryListener?.let {
            try { nsdManager.stopServiceDiscovery(it) } catch (_: Exception) {}
        }
        discoveryListener = null
    }

    suspend fun runShell(host: String, port: Int, command: String): String = withContext(Dispatchers.IO) {
        val socket = Socket()
        try {
            Log.d(TAG, "Connecting to $host:$port for command: $command")
            socket.connect(InetSocketAddress(host, port), 4000)
            socket.soTimeout = 6000
            val input = socket.getInputStream()
            val output = socket.getOutputStream()

            // 1. Send CNXN
            val banner = "host::Companion\u0000"
            sendPacket(output, A_CNXN, A_VERSION, 1024 * 1024, banner.toByteArray())

            var resp = readPacket(input)
            Log.d(TAG, "Received packet after CNXN: cmd=0x${Integer.toHexString(resp.command)}")

            if (resp.command == A_AUTH) {
                Log.d(TAG, "Auth requested by device (arg0=${resp.arg0})")
                // Android ADB Auth: arg0 == 1 -> TOKEN
                // Send RSA public key to trigger authorization dialog on watch
                val prefs = context.getSharedPreferences("adb_keys", Context.MODE_PRIVATE)
                var pubKeyB64 = prefs.getString("pub_b64", null)
                if (pubKeyB64 == null) {
                    val kpg = KeyPairGenerator.getInstance("RSA")
                    kpg.initialize(2048)
                    val kp = kpg.generateKeyPair()
                    val pub = kp.public as RSAPublicKey
                    val encoded = Base64.encodeToString(pub.encoded, Base64.NO_WRAP)
                    prefs.edit().putString("pub_b64", encoded).apply()
                    pubKeyB64 = encoded
                }

                // Send RSAPUBLICKEY packet (type 3)
                val pubPayload = "$pubKeyB64 companion@phone\u0000".toByteArray()
                sendPacket(output, A_AUTH, 3, 0, pubPayload)
                Log.d(TAG, "Sent RSA public key to watch")

                resp = readPacket(input)
                Log.d(TAG, "Packet after RSA auth: cmd=0x${Integer.toHexString(resp.command)}")
                if (resp.command != A_CNXN) {
                    return@withContext "AUTH_REQUIRED: Acepta la solicitud en la pantalla del reloj"
                }
            }

            if (resp.command != A_CNXN) {
                return@withContext "ERROR: Conexión rechazada (cmd 0x${Integer.toHexString(resp.command)})"
            }

            // Open stream: shell:<command>
            val streamId = 1
            val destination = "shell:$command\u0000"
            sendPacket(output, A_OPEN, streamId, 0, destination.toByteArray())

            val openResp = readPacket(input)
            if (openResp.command != A_OKAY) {
                Log.e(TAG, "Open failed: 0x${Integer.toHexString(openResp.command)}")
                return@withContext "FAILED_OPEN_STREAM"
            }

            val resultBuilder = StringBuilder()
            while (true) {
                val dataPacket = readPacket(input)
                if (dataPacket.command == A_WRTE) {
                    resultBuilder.append(String(dataPacket.data))
                    sendPacket(output, A_OKAY, streamId, dataPacket.arg0, ByteArray(0))
                } else if (dataPacket.command == A_CLSE) {
                    sendPacket(output, A_CLSE, streamId, dataPacket.arg0, ByteArray(0))
                    break
                }
            }
            val res = resultBuilder.toString().trim()
            Log.d(TAG, "Command result: $res")
            res
        } catch (e: Exception) {
            Log.e(TAG, "Socket exception: ${e.message}", e)
            "ERROR: ${e.message}"
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    companion object {
        const val A_SYNC = 0x434e5953
        const val A_CNXN = 0x4e584e43
        const val A_OPEN = 0x4e45504f
        const val A_OKAY = 0x59414b4f
        const val A_CLSE = 0x45534c43
        const val A_WRTE = 0x45545257
        const val A_AUTH = 0x48545541
        const val A_VERSION = 0x01000000

        data class AdbPacket(
            val command: Int,
            val arg0: Int,
            val arg1: Int,
            val dataLength: Int,
            val dataChecksum: Int,
            val magic: Int,
            val data: ByteArray
        )

        fun sendPacket(out: OutputStream, command: Int, arg0: Int, arg1: Int, data: ByteArray) {
            val buf = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
            buf.putInt(command)
            buf.putInt(arg0)
            buf.putInt(arg1)
            buf.putInt(data.size)
            buf.putInt(getChecksum(data))
            buf.putInt(command xor -0x1)
            out.write(buf.array())
            if (data.isNotEmpty()) {
                out.write(data)
            }
            out.flush()
        }

        fun readPacket(inp: InputStream): AdbPacket {
            val header = ByteArray(24)
            var read = 0
            while (read < 24) {
                val r = inp.read(header, read, 24 - read)
                if (r < 0) throw RuntimeException("Stream closed unexpectedly")
                read += r
            }
            val buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
            val cmd = buf.getInt()
            val arg0 = buf.getInt()
            val arg1 = buf.getInt()
            val len = buf.getInt()
            val checksum = buf.getInt()
            val magic = buf.getInt()

            val data = ByteArray(len)
            var dataRead = 0
            while (dataRead < len) {
                val r = inp.read(data, dataRead, len - dataRead)
                if (r < 0) throw RuntimeException("Premature EOF reading packet data")
                dataRead += r
            }
            return AdbPacket(cmd, arg0, arg1, len, checksum, magic, data)
        }

        private fun getChecksum(data: ByteArray): Int {
            var sum = 0
            for (b in data) {
                sum += b.toInt() and 0xFF
            }
            return sum
        }
    }
}
