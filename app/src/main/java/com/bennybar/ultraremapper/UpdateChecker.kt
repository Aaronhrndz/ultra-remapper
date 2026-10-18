package com.bennybar.ultraremapper

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.wear.remote.interactions.RemoteActivityHelper
import org.json.JSONObject
import java.io.File
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
    private const val PREFS_NAME = "update_checker"
    private const val KEY_LAST_CHECK = "last_check_time"
    private const val KEY_NOTIFIED_VER = "last_notified_version"
    private const val CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L // 24 hours

    fun scheduleDailyCheck(context: Context) {
        try {
            val am = context.getSystemService(Context.ALARM_SERVICE) as? android.app.AlarmManager ?: return
            val intent = Intent(context, UpdateReceiver::class.java).apply {
                action = UpdateReceiver.ACTION_CHECK_UPDATE
            }
            val pi = android.app.PendingIntent.getBroadcast(
                context,
                901,
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            val triggerAt = System.currentTimeMillis() + CHECK_INTERVAL_MS
            // Inexact inexact repeating or setAndAllowWhileIdle for battery friendliness
            am.setInexactRepeating(
                android.app.AlarmManager.RTC,
                triggerAt,
                CHECK_INTERVAL_MS,
                pi
            )
        } catch (_: Exception) {}
    }

    /**
     * Called in background (e.g. from AlarmManager or button presses).
     * Only queries network if at least 24 hours have passed since last check.
     */
    fun checkOpportunistic(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastCheck = prefs.getLong(KEY_LAST_CHECK, 0L)
        val now = System.currentTimeMillis()
        if (now - lastCheck < CHECK_INTERVAL_MS) return

        prefs.edit().putLong(KEY_LAST_CHECK, now).apply()

        check(context) { update ->
            if (update != null) {
                val lastNotified = prefs.getString(KEY_NOTIFIED_VER, "")
                if (lastNotified != update.versionName) {
                    prefs.edit().putString(KEY_NOTIFIED_VER, update.versionName).apply()
                    UpdateReceiver.showUpdateNotification(context, update)
                }
            }
        }
    }

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
            Toast.makeText(context, context.getString(R.string.update_opened_phone), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open on phone", Toast.LENGTH_SHORT).show()
        }
    }

    fun downloadAndInstallOnWatch(
        context: Context,
        update: ReleaseUpdate,
        onProgress: (progressFraction: Float, statusText: String) -> Unit = { _, _ -> }
    ) {
        val appCtx = context.applicationContext
        val apkUrl = update.apkDownloadUrl
        if (apkUrl.isNullOrEmpty()) {
            openReleaseOnPhone(context, update)
            return
        }

        executor.execute {
            try {
                mainHandler.post { onProgress(0f, appCtx.getString(R.string.update_downloading)) }
                val url = URL(apkUrl)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = true
                    connectTimeout = 15000
                    readTimeout = 30000
                }
                if (conn.responseCode in 300..399) {
                    val redirectUrl = conn.getHeaderField("Location")
                    if (!redirectUrl.isNullOrEmpty()) {
                        val redirConn = URL(redirectUrl).openConnection() as HttpURLConnection
                        redirConn.connectTimeout = 15000
                        redirConn.readTimeout = 30000
                        installFromConnection(appCtx, redirConn, update.versionName, onProgress)
                        return@execute
                    }
                }
                installFromConnection(appCtx, conn, update.versionName, onProgress)
            } catch (e: Exception) {
                mainHandler.post {
                    Toast.makeText(appCtx, "Download error: ${e.message}", Toast.LENGTH_LONG).show()
                    onProgress(0f, "")
                }
            }
        }
    }

    private fun installFromConnection(
        context: Context,
        conn: HttpURLConnection,
        version: String,
        onProgress: (progressFraction: Float, statusText: String) -> Unit
    ) {
        val cacheDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val apkFile = File(cacheDir, "ultra-remapper-v$version.apk")
        if (apkFile.exists()) apkFile.delete()

        val totalLength = conn.contentLength.toLong()
        var downloaded = 0L

        conn.inputStream.use { input ->
            apkFile.outputStream().use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var lastProgressUpdate = 0L
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    downloaded += bytesRead
                    val now = System.currentTimeMillis()
                    if (now - lastProgressUpdate > 100L) {
                        lastProgressUpdate = now
                        val fraction = if (totalLength > 0) downloaded.toFloat() / totalLength.toFloat() else 0f
                        val percent = if (totalLength > 0) (fraction * 100).toInt() else 0
                        val status = if (totalLength > 0) "$percent%" else "${downloaded / 1024} KB"
                        mainHandler.post { onProgress(fraction, status) }
                    }
                }
            }
        }

        mainHandler.post {
            onProgress(1f, context.getString(R.string.update_launching))
            try {
                val apkUri: Uri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    apkFile
                )
                val installIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(apkUri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(installIntent)
            } catch (e: Exception) {
                Toast.makeText(context, "Install failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
            onProgress(0f, "")
        }
    }
}
