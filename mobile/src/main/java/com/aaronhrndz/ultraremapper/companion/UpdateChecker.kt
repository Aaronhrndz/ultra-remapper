package com.aaronhrndz.ultraremapper.companion

import android.content.Context
import android.content.Intent
import android.net.Uri
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
    val apkDownloadUrl: String?,
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

    fun check(context: Context, onResult: (ReleaseUpdate?) -> Unit) {
        val appContext = context.applicationContext
        val isBeta = isBetaChannel(appContext)
        val currentVersion = try {
            appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName ?: "1.0.0"
        } catch (_: Exception) {
            "1.0.0"
        }

        executor.execute {
            val update = try {
                val apiUrl = if (isBeta) ALL_RELEASES_API else LATEST_RELEASE_API
                val url = URL(apiUrl)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 8000
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "UltraRemapperCompanion")
                }
                if (conn.responseCode == 200) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    if (isBeta) {
                        val arr = JSONArray(body)
                        parseCompanionUpdate(arr.optJSONObject(0), currentVersion)
                    } else {
                        parseCompanionUpdate(JSONObject(body), currentVersion)
                    }
                } else null
            } catch (_: Exception) {
                null
            }

            mainHandler.post { onResult(update) }
        }
    }

    private fun parseCompanionUpdate(json: JSONObject?, currentVersion: String): ReleaseUpdate? {
        if (json == null) return null
        val tag = json.optString("tag_name", "").removePrefix("v").removePrefix("companion-v")
        val isPrerelease = json.optBoolean("prerelease", false)
        val htmlUrl = json.optString("html_url", "")
        val notes = json.optString("body", "")

        val assets = json.optJSONArray("assets")
        var apkUrl: String? = null
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val name = asset.optString("name", "")
                // Looks for mobile companion APK
                if (name.contains("companion", ignoreCase = true) && name.endsWith(".apk")) {
                    apkUrl = asset.optString("browser_download_url", null)
                    break
                }
            }
        }

        return if (apkUrl != null) {
            ReleaseUpdate(tag, htmlUrl, apkUrl, notes, isPrerelease)
        } else null
    }

    fun downloadAndInstall(
        context: Context,
        update: ReleaseUpdate,
        onProgress: (Float, String) -> Unit
    ) {
        val appCtx = context.applicationContext
        val apkUrl = update.apkDownloadUrl ?: return

        executor.execute {
            try {
                mainHandler.post { onProgress(0f, "Descargando...") }
                val url = URL(apkUrl)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = true
                    connectTimeout = 15000
                    readTimeout = 30000
                }
                val cacheDir = File(appCtx.cacheDir, "updates").apply { mkdirs() }
                val apkFile = File(cacheDir, "companion-v${update.versionName}.apk")

                val total = conn.contentLength.toLong()
                var downloaded = 0L
                conn.inputStream.use { input ->
                    apkFile.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            downloaded += read
                            val frac = if (total > 0) downloaded.toFloat() / total else 0f
                            mainHandler.post { onProgress(frac, "${(frac * 100).toInt()}%") }
                        }
                    }
                }

                mainHandler.post {
                    onProgress(1f, "Instalando...")
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
                    onProgress(0f, "")
                }
            } catch (e: Exception) {
                mainHandler.post {
                    Toast.makeText(appCtx, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    onProgress(0f, "")
                }
            }
        }
    }
}
