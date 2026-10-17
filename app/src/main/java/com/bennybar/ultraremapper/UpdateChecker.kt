package com.bennybar.ultraremapper

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.wear.remote.interactions.RemoteActivityHelper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

data class ReleaseUpdate(
    val versionName: String,
    val htmlUrl: String,
    val apkDownloadUrl: String?,
    val releaseNotes: String
)

object UpdateChecker {
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private const val LATEST_RELEASE_API = "https://api.github.com/repos/Aaronhrndz/ultra-remapper/releases/latest"

    fun check(context: Context, onResult: (ReleaseUpdate?) -> Unit) {
        val currentVersion = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.0.0"
        } catch (e: Exception) {
            "0.0.0"
        }

        executor.execute {
            try {
                val conn = (URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "UltraRemapper/$currentVersion")
                    connectTimeout = 5000
                    readTimeout = 5000
                }
                if (conn.responseCode == 200) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(body)
                    val tag = json.optString("tag_name", "").removePrefix("v").trim()
                    val htmlUrl = json.optString("html_url", "https://github.com/Aaronhrndz/ultra-remapper/releases/latest")
                    val notes = json.optString("body", "").trim()
                    var apkUrl: String? = null
                    val assets = json.optJSONArray("assets")
                    if (assets != null) {
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            val name = asset.optString("name", "")
                            if (name.endsWith(".apk", ignoreCase = true)) {
                                apkUrl = asset.optString("browser_download_url", "").takeIf { it.isNotEmpty() }
                                break
                            }
                        }
                    }
                    if (tag.isNotEmpty() && isNewerVersion(tag, currentVersion.removePrefix("v"))) {
                        val update = ReleaseUpdate(tag, htmlUrl, apkUrl, notes)
                        mainHandler.post { onResult(update) }
                        return@execute
                    }
                }
            } catch (ignored: Exception) {
            }
            mainHandler.post { onResult(null) }
        }
    }

    private fun isNewerVersion(remote: String, local: String): Boolean {
        val rParts = remote.split(".").map { it.toIntOrNull() ?: 0 }
        val lParts = local.split(".").map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(rParts.size, lParts.size)
        for (i in 0 until maxLen) {
            val r = rParts.getOrElse(i) { 0 }
            val l = lParts.getOrElse(i) { 0 }
            if (r > l) return true
            if (r < l) return false
        }
        return false
    }

    fun openReleaseOnPhone(context: Context, update: ReleaseUpdate) {
        try {
            val remoteHelper = RemoteActivityHelper(context, executor)
            val intent = Intent(Intent.ACTION_VIEW)
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .setData(Uri.parse(update.htmlUrl))
            remoteHelper.startRemoteActivity(intent)
            Toast.makeText(context, "Opened on phone", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open on phone", Toast.LENGTH_SHORT).show()
        }
    }
}
