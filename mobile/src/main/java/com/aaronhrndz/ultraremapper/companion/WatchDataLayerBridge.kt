package com.aaronhrndz.ultraremapper.companion

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import org.json.JSONObject

data class WatchAppEntry(
    val pkg: String,
    val label: String
)

data class WatchSyncedState(
    val versionName: String,
    val masterEnabled: Boolean,
    val hapticEnabled: Boolean,
    val mappings: Map<String, String>,
    val permissions: Map<String, Boolean>,
    val installedApps: List<WatchAppEntry>
)

/**
 * Manages Bluetooth / Wearable DataLayer synchronization between Phone Companion and Watch.
 * Works continuously over regular Bluetooth without needing Wi-Fi or ADB active.
 */
class WatchDataLayerBridge(
    private val context: Context,
    private val onStateReceived: (WatchSyncedState, String?) -> Unit
) : DataClient.OnDataChangedListener {

    private val dataClient = Wearable.getDataClient(context)
    private val messageClient = Wearable.getMessageClient(context)
    private val nodeClient = Wearable.getNodeClient(context)

    fun start() {
        dataClient.addListener(this)
        fetchCurrentDataItem()
        requestSyncFromWatch()
    }

    fun stop() {
        try {
            dataClient.removeListener(this)
        } catch (_: Exception) {}
    }

    fun requestSyncFromWatch() {
        nodeClient.connectedNodes.addOnSuccessListener { nodes ->
            val watchNode = nodes.firstOrNull()
            if (watchNode != null) {
                messageClient.sendMessage(watchNode.id, PATH_REQUEST_SYNC, ByteArray(0))
            }
        }
    }

    fun sendCommandToWatch(json: JSONObject, onSent: ((Boolean) -> Unit)? = null) {
        val bytes = json.toString().toByteArray(Charsets.UTF_8)
        nodeClient.connectedNodes.addOnSuccessListener { nodes ->
            val watchNode = nodes.firstOrNull()
            if (watchNode != null) {
                messageClient.sendMessage(watchNode.id, PATH_CMD, bytes)
                    .addOnSuccessListener { onSent?.invoke(true) }
                    .addOnFailureListener { onSent?.invoke(false) }
            } else {
                onSent?.invoke(false)
            }
        }.addOnFailureListener {
            onSent?.invoke(false)
        }
    }

    private fun fetchCurrentDataItem() {
        nodeClient.connectedNodes.addOnSuccessListener { nodes ->
            val nodeName = nodes.firstOrNull()?.displayName
            val uri = Uri.Builder().scheme("wear").path(PATH_STATE).build()
            dataClient.getDataItems(uri).addOnSuccessListener { buffer ->
                try {
                    for (item in buffer) {
                        if (item.uri.path == PATH_STATE) {
                            val dataMap = DataMapItem.fromDataItem(item).dataMap
                            val jsonStr = dataMap.getString("state_json")
                            if (!jsonStr.isNullOrBlank()) {
                                parseStateJson(jsonStr)?.let { parsed ->
                                    onStateReceived(parsed, nodeName)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("WatchDataLayerBridge", "Error reading DataItems: ${e.message}")
                } finally {
                    buffer.release()
                }
            }
        }
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        try {
            for (event in dataEvents) {
                if (event.dataItem.uri.path == PATH_STATE) {
                    val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                    val jsonStr = dataMap.getString("state_json")
                    if (!jsonStr.isNullOrBlank()) {
                        parseStateJson(jsonStr)?.let { parsed ->
                            nodeClient.connectedNodes.addOnSuccessListener { nodes ->
                                onStateReceived(parsed, nodes.firstOrNull()?.displayName)
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}
    }

    companion object {
        const val PATH_STATE = "/ultra_remapper/state"
        const val PATH_REQUEST_SYNC = "/ultra_remapper/request_sync"
        const val PATH_CMD = "/ultra_remapper/cmd"

        fun parseStateJson(jsonStr: String): WatchSyncedState? {
            return try {
                val root = JSONObject(jsonStr)
                val versionName = root.optString("versionName", "")
                val masterEnabled = root.optBoolean("masterEnabled", true)
                val hapticEnabled = root.optBoolean("hapticEnabled", true)

                val mappings = mutableMapOf<String, String>()
                root.optJSONObject("mappings")?.let { mObj ->
                    val keys = mObj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        mappings[k] = mObj.optString(k, "none")
                    }
                }

                val perms = mutableMapOf<String, Boolean>()
                root.optJSONObject("permissions")?.let { pObj ->
                    val keys = pObj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        perms[k] = pObj.optBoolean(k, false)
                    }
                }

                val apps = mutableListOf<WatchAppEntry>()
                root.optJSONArray("installedApps")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val item = arr.optJSONObject(i) ?: continue
                        val pkg = item.optString("pkg")
                        val label = item.optString("label", pkg)
                        if (pkg.isNotBlank()) {
                            apps.add(WatchAppEntry(pkg, label))
                        }
                    }
                }

                WatchSyncedState(
                    versionName = versionName,
                    masterEnabled = masterEnabled,
                    hapticEnabled = hapticEnabled,
                    mappings = mappings,
                    permissions = perms,
                    installedApps = apps
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}
