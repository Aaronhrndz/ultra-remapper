package com.aaronhrndz.ultraremapper.adb

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo

/**
 * Discovers local Wireless Debugging ADB and Pairing ports announced via mDNS on localhost.
 */
class AdbMdnsDiscovery(context: Context) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager

    @Volatile
    var discoveredConnectPort: Int? = null
        private set

    @Volatile
    var discoveredPairingPort: Int? = null
        private set

    private var connectListener: NsdManager.DiscoveryListener? = null
    private var pairingListener: NsdManager.DiscoveryListener? = null

    fun startDiscovery(onPortFound: (isPairing: Boolean, port: Int) -> Unit) {
        val manager = nsdManager ?: return

        // 1. Discover ADB Connect service: _adb-tls-connect._tcp
        connectListener = createListener { info ->
            discoveredConnectPort = info.port
            onPortFound(false, info.port)
        }
        try {
            manager.discoverServices("_adb-tls-connect._tcp", NsdManager.PROTOCOL_DNS_SD, connectListener)
        } catch (_: Exception) {}

        // 2. Discover ADB Pairing service: _adb-tls-pairing._tcp
        pairingListener = createListener { info ->
            discoveredPairingPort = info.port
            onPortFound(true, info.port)
        }
        try {
            manager.discoverServices("_adb-tls-pairing._tcp", NsdManager.PROTOCOL_DNS_SD, pairingListener)
        } catch (_: Exception) {}
    }

    private fun createListener(onResolved: (NsdServiceInfo) -> Unit): NsdManager.DiscoveryListener {
        return object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                try {
                    nsdManager?.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {}
                        override fun onServiceResolved(resolvedInfo: NsdServiceInfo) {
                            onResolved(resolvedInfo)
                        }
                    })
                } catch (_: Exception) {}
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
        }
    }

    fun stopDiscovery() {
        val manager = nsdManager ?: return
        try { connectListener?.let { manager.stopServiceDiscovery(it) } } catch (_: Exception) {}
        try { pairingListener?.let { manager.stopServiceDiscovery(it) } } catch (_: Exception) {}
        connectListener = null
        pairingListener = null
    }
}
