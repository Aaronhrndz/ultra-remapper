package com.aaronhrndz.ultraremapper.companion

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.net.Inet4Address

/**
 * Discovers Wireless Debugging ADB Connect (_adb-tls-connect._tcp) and
 * Pairing (_adb-tls-pairing._tcp) services on the local Wi-Fi network via mDNS.
 */
class AdbClient(private val context: Context) {

    private val TAG = "UltraAdbMdns"

    data class DiscoveredEndpoint(
        val name: String,
        val host: String,
        val port: Int,
        val isPairing: Boolean
    )

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var connectListener: NsdManager.DiscoveryListener? = null
    private var pairingListener: NsdManager.DiscoveryListener? = null

    fun startDiscovery(onFound: (DiscoveredEndpoint) -> Unit) {
        stopDiscovery()
        val manager = nsdManager ?: return

        connectListener = createListener(isPairing = false, onFound = onFound)
        try {
            manager.discoverServices("_adb-tls-connect._tcp", NsdManager.PROTOCOL_DNS_SD, connectListener)
        } catch (e: Exception) {
            Log.e(TAG, "Failed starting connect discovery: ${e.message}")
        }

        pairingListener = createListener(isPairing = true, onFound = onFound)
        try {
            manager.discoverServices("_adb-tls-pairing._tcp", NsdManager.PROTOCOL_DNS_SD, pairingListener)
        } catch (e: Exception) {
            Log.e(TAG, "Failed starting pairing discovery: ${e.message}")
        }
    }

    private fun createListener(
        isPairing: Boolean,
        onFound: (DiscoveredEndpoint) -> Unit
    ): NsdManager.DiscoveryListener {
        return object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {}

            override fun onServiceFound(service: NsdServiceInfo) {
                try {
                    nsdManager?.resolveService(service, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}

                        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                            val inetAddr = serviceInfo.host ?: return
                            if (inetAddr.isLoopbackAddress) return
                            val host = inetAddr.hostAddress ?: return
                            // Ignore link-local IPv6 addresses if IPv4 is preferred
                            if (inetAddr !is Inet4Address && host.contains(":")) return

                            val port = serviceInfo.port
                            val name = serviceInfo.serviceName ?: "Galaxy Watch"
                            mainHandler.post {
                                onFound(DiscoveredEndpoint(name, host, port, isPairing))
                            }
                        }
                    })
                } catch (_: Exception) {}
            }
        }
    }

    fun stopDiscovery() {
        val manager = nsdManager ?: return
        connectListener?.let {
            try { manager.stopServiceDiscovery(it) } catch (_: Exception) {}
        }
        pairingListener?.let {
            try { manager.stopServiceDiscovery(it) } catch (_: Exception) {}
        }
        connectListener = null
        pairingListener = null
    }
}
