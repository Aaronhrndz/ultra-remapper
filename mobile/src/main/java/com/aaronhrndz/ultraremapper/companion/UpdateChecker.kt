package com.aaronhrndz.ultraremapper.companion

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

data class ReleaseUpdate(
    val versionName: String,
    val htmlUrl: String,
    val companionApkUrl: String?,
    val watchApkUrl: String?,
    val releaseNotes: String,
    val isPrerelease: Boolean = false
)

object UpdateChecker {
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private const val LATEST_RELEASE_API = "https://api.github.com/repos/Aaronhrndz/ultra-remapper/releases/latest"
    private const val ALL_RELEASES_API = "https://api.github.com/repos/Aaronhrndz/ultra-remapper/releases?per_page=5"
    private const val PREFS_NAME = "companion_update_checker"
    private const val KEY_BETA_CHANNEL = "beta_channel_enabled"

    fun isBetaChannel(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_BETA_CHANNEL, false)
    }

    fun setBetaChannel(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_BETA_CHANNEL, enabled)
            .apply()
    }

    /**
     * Checks GitHub Releases and returns both:
     * - companionUpdate (if a newer mobile companion APK is available)
     * - latestRelease (always contains the latest Watch APK URL so the phone can install/update the watch)
     */
    fun checkAll(
        context: Context,
        onResult: (companionUpdate: ReleaseUpdate?, latestRelease: ReleaseUpdate?) -> Unit
    ) {
        val appContext = context.applicationContext
        val isBeta = isBetaChannel(appContext)
        val currentVersion = try {
            appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName ?: "1.0.0"
        } catch (_: Exception) {
            "1.0.0"
        }

        executor.execute {
            var companionUpd: ReleaseUpdate? = null
            var latestRel: ReleaseUpdate? = null
            try {
                val apiUrl = if (isBeta) ALL_RELEASES_API else LATEST_RELEASE_API
                val url = URL(apiUrl)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 8000
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "UltraRemapperCompanion/$currentVersion")
                }
                if (conn.responseCode == 200) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    if (isBeta) {
                        val arr = JSONArray(body)
                        var foundCompanionRel: ReleaseUpdate? = null
                        var foundWatchRel: ReleaseUpdate? = null
                        for (i in 0 until arr.length()) {
                            val parsed = parseRelease(arr.optJSONObject(i)) ?: continue
                            if (foundCompanionRel == null && parsed.companionApkUrl != null) {
                                foundCompanionRel = parsed
                            }
                            if (foundWatchRel == null && parsed.watchApkUrl != null) {
                                foundWatchRel = parsed
                            }
                            if (foundCompanionRel != null && foundWatchRel != null) break
                        }
                        latestRel = foundWatchRel ?: foundCompanionRel
                        if (foundCompanionRel != null &&
                            isNewerVersion(foundCompanionRel.versionName, currentVersion.removePrefix("v"))
                        ) {
                            companionUpd = foundCompanionRel
                        }
                    } else {
                        latestRel = parseRelease(JSONObject(body))
                        if (latestRel != null &&
                            latestRel.companionApkUrl != null &&
                            isNewerVersion(latestRel.versionName, currentVersion.removePrefix("v"))
                        ) {
                            companionUpd = latestRel
                        }
                    }
                }
            } catch (_: Exception) {
            }

            mainHandler.post { onResult(companionUpd, latestRel) }
        }
    }

    private fun parseRelease(json: JSONObject?): ReleaseUpdate? {
        if (json == null) return null
        val tag = json.optString("tag_name", "").removePrefix("v").removePrefix("companion-v").trim()
        if (tag.isEmpty()) return null
        val isPrerelease = json.optBoolean("prerelease", false)
        val htmlUrl = json.optString("html_url", "https://github.com/Aaronhrndz/ultra-remapper/releases/latest")
        val notes = json.optString("body", "").trim()

        val assets = json.optJSONArray("assets")
        var companionUrl: String? = null
        var watchUrl: String? = null
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val name = asset.optString("name", "")
                if (!name.endsWith(".apk", ignoreCase = true)) continue
                val dlUrl = asset.optString("browser_download_url", "").takeIf { it.isNotEmpty() } ?: continue
                if (name.contains("companion", ignoreCase = true) || name.contains("mobile", ignoreCase = true)) {
                    companionUrl = dlUrl
                } else {
                    watchUrl = dlUrl
                }
            }
        }

        return ReleaseUpdate(
            versionName = tag,
            htmlUrl = htmlUrl,
            companionApkUrl = companionUrl,
            watchApkUrl = watchUrl,
            releaseNotes = notes,
            isPrerelease = isPrerelease
        )
    }

    fun isNewerVersion(remote: String, local: String): Boolean {
        if (remote == local) return false
        fun parseAll(v: String): List<Int> {
            return Regex("\\d+").findAll(v).map { it.value.toIntOrNull() ?: 0 }.toList()
        }
        val rParts = parseAll(remote)
        val lParts = parseAll(local)
        val maxLen = maxOf(rParts.size, lParts.size)
        for (i in 0 until maxLen) {
            val r = rParts.getOrElse(i) { 0 }
            val l = lParts.getOrElse(i) { 0 }
            if (r > l) return true
            if (r < l) return false
        }
        return false
    }

    /**
     * Downloads an APK from a URL into the app cache directory with live progress callbacks.
     */
    fun downloadApkToFile(
        context: Context,
        apkUrl: String,
        targetFileName: String,
        onProgress: (Float, String) -> Unit,
        onComplete: (File?) -> Unit
    ) {
        val appCtx = context.applicationContext
        executor.execute {
            try {
                mainHandler.post { onProgress(0f, "Descargando APK…") }
                var conn = (URL(apkUrl).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = true
                    connectTimeout = 15000
                    readTimeout = 30000
                }
                if (conn.responseCode in 300..399) {
                    val redir = conn.getHeaderField("Location")
                    if (!redir.isNullOrEmpty()) {
                        conn = (URL(redir).openConnection() as HttpURLConnection).apply {
                            connectTimeout = 15000
                            readTimeout = 30000
                        }
                    }
                }

                val cacheDir = File(appCtx.cacheDir, "updates").apply { mkdirs() }
                val apkFile = File(cacheDir, targetFileName)
                if (apkFile.exists()) apkFile.delete()

                val total = conn.contentLength.toLong()
                var downloaded = 0L
                var lastReport = 0L
                conn.inputStream.use { input ->
                    apkFile.outputStream().use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            downloaded += read
                            val now = System.currentTimeMillis()
                            if (now - lastReport > 100L) {
                                lastReport = now
                                val frac = if (total > 0) downloaded.toFloat() / total else 0f
                                val pct = (frac * 100).toInt()
                                mainHandler.post { onProgress(frac, "Descargando ($pct%)…") }
                            }
                        }
                    }
                }
                mainHandler.post { onComplete(apkFile) }
            } catch (e: Exception) {
                mainHandler.post {
                    Toast.makeText(appCtx, "Error descarga: ${e.message}", Toast.LENGTH_LONG).show()
                    onProgress(0f, "")
                    onComplete(null)
                }
            }
        }
    }

    /**
     * Downloads and installs the mobile Companion APK on the phone itself.
     */
    fun downloadAndInstallCompanion(
        context: Context,
        update: ReleaseUpdate,
        onProgress: (Float, String) -> Unit
    ) {
        val appCtx = context.applicationContext
        val apkUrl = update.companionApkUrl ?: return
        downloadApkToFile(
            context = appCtx,
            apkUrl = apkUrl,
            targetFileName = "ultra-remapper-companion-v${update.versionName}.apk",
            onProgress = onProgress
        ) { apkFile ->
            if (apkFile == null) return@downloadApkToFile
            onProgress(1f, "Abriendo instalador…")
            try {
                val apkUri = FileProvider.getUriForFile(
                    appCtx,
                    "${appCtx.packageName}.fileprovider",
                    apkFile
                )
                val installIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(apkUri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                appCtx.startActivity(installIntent)
            } catch (e: Exception) {
                Toast.makeText(appCtx, "Error al abrir instalador: ${e.message}", Toast.LENGTH_LONG).show()
            }
            onProgress(0f, "")
        }
    }
}
