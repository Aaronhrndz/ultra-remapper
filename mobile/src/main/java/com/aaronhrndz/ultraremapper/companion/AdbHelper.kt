package com.aaronhrndz.ultraremapper.companion

import android.content.Context
import android.util.Base64
import android.util.Log
import io.github.muntashirakon.adb.AdbConnection
import io.github.muntashirakon.adb.PairingConnectionCtx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * ADB Manager for Mobile Companion using the battle-tested Wear OS implementation:
 * - SPAKE2 TLS 1.3 pairing via PairingConnectionCtx
 * - Persistent RSA-2048 & X.509 certificate
 * - Reused AdbConnection with non-blocking stream reads
 * - Direct APK installation onto the watch via pm install-create / write / commit
 */
class AdbHelper private constructor(private val context: Context) {

    private val TAG = "UltraAdbHelper"
    private var mPrivateKey: PrivateKey? = null
    private var mCertificate: X509Certificate? = null

    private var activeConnection: AdbConnection? = null
    private var connectedHost: String? = null
    private var connectedPort: Int? = null

    init {
        initOrLoadKeys()
    }

    private fun initOrLoadKeys() {
        val prefs = context.getSharedPreferences(PREFS_KEYS, Context.MODE_PRIVATE)
        val privB64 = prefs.getString(KEY_PRIV, null)
        val certB64 = prefs.getString(KEY_CERT, null)

        if (privB64 != null && certB64 != null) {
            try {
                val privBytes = Base64.decode(privB64, Base64.NO_WRAP)
                val keyFactory = KeyFactory.getInstance("RSA")
                mPrivateKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(privBytes))

                val certBytes = Base64.decode(certB64, Base64.NO_WRAP)
                val cf = CertificateFactory.getInstance("X.509")
                mCertificate = cf.generateCertificate(ByteArrayInputStream(certBytes)) as X509Certificate
                return
            } catch (e: Exception) {
                Log.e(TAG, "Failed loading saved keys: ${e.message}")
            }
        }

        try {
            val keyGen = KeyPairGenerator.getInstance("RSA")
            keyGen.initialize(2048, SecureRandom())
            val keyPair = keyGen.generateKeyPair()
            mPrivateKey = keyPair.private

            val cert = generateCertificate(keyPair)
            mCertificate = cert

            prefs.edit()
                .putString(KEY_PRIV, Base64.encodeToString(keyPair.private.encoded, Base64.NO_WRAP))
                .putString(KEY_CERT, Base64.encodeToString(cert.encoded, Base64.NO_WRAP))
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error generating persistent keys: ${e.message}", e)
        }
    }

    private fun generateCertificate(keyPair: KeyPair): X509Certificate {
        val now = System.currentTimeMillis()
        val notBefore = Date(now - 1000L * 60 * 60 * 24)
        val notAfter = Date(now + 1000L * 60 * 60 * 24 * 365 * 10)
        val dnName = X500Name("CN=UltraRemapperCompanion")
        val serial = BigInteger.valueOf(now)

        val builder = JcaX509v3CertificateBuilder(
            dnName,
            serial,
            notBefore,
            notAfter,
            dnName,
            keyPair.public
        )

        val signer = JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private)
        val holder = builder.build(signer)
        return JcaX509CertificateConverter().getCertificate(holder)
    }

    fun isPaired(): Boolean {
        val prefs = context.getSharedPreferences(PREFS_STATE, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_IS_PAIRED, false)
    }

    fun setPaired(paired: Boolean, host: String? = null, port: Int? = null) {
        val prefs = context.getSharedPreferences(PREFS_STATE, Context.MODE_PRIVATE)
        val editor = prefs.edit().putBoolean(KEY_IS_PAIRED, paired)
        if (!host.isNullOrBlank()) editor.putString(KEY_LAST_HOST, host)
        if (port != null && port > 0) editor.putInt(KEY_LAST_PORT, port)
        editor.apply()
    }

    fun getLastHost(): String {
        val prefs = context.getSharedPreferences(PREFS_STATE, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LAST_HOST, "") ?: ""
    }

    fun getLastPort(): Int? {
        val prefs = context.getSharedPreferences(PREFS_STATE, Context.MODE_PRIVATE)
        val p = prefs.getInt(KEY_LAST_PORT, -1)
        return if (p > 0) p else null
    }

    fun unpair() {
        closeConnection()
        context.getSharedPreferences(PREFS_STATE, Context.MODE_PRIVATE).edit().clear().apply()
        context.getSharedPreferences(PREFS_KEYS, Context.MODE_PRIVATE).edit().clear().apply()
        mPrivateKey = null
        mCertificate = null
        initOrLoadKeys()
    }

    fun closeConnection() {
        try {
            activeConnection?.close()
        } catch (_: Exception) {}
        activeConnection = null
        connectedHost = null
        connectedPort = null
    }

    val isConnected: Boolean
        get() = activeConnection?.isConnectionEstablished == true

    suspend fun pairDevice(host: String, pairingPort: Int, pairingCode: String): Pair<Boolean, String> =
        withContext(Dispatchers.IO) {
            val result = withTimeoutOrNull(12000L) {
                try {
                    if (mPrivateKey == null || mCertificate == null) initOrLoadKeys()
                    val pinBytes = pairingCode.trim().toByteArray(Charsets.UTF_8)
                    val pairingCtx = PairingConnectionCtx(
                        host.trim(),
                        pairingPort,
                        pinBytes,
                        mPrivateKey!!,
                        mCertificate!!,
                        "UltraRemapperCompanion"
                    )
                    pairingCtx.use {
                        it.start()
                    }
                    setPaired(true, host.trim(), null)
                    Pair(true, "¡Reloj vinculado con éxito!")
                } catch (e: Exception) {
                    Log.e(TAG, "Pairing failed: ${e.message}", e)
                    Pair(false, "Fallo al vincular. Código incorrecto, puerto erróneo o caducado.")
                }
            }
            result ?: Pair(false, "Tiempo agotado (12s). Verifica que el reloj esté en la pantalla de vinculación y en la misma red Wi-Fi.")
        }

    suspend fun ensureConnected(host: String, connectPort: Int): Boolean = withContext(Dispatchers.IO) {
        val cleanHost = host.trim()
        val current = activeConnection
        if (current != null && current.isConnectionEstablished && connectedHost == cleanHost && connectedPort == connectPort) {
            return@withContext true
        }
        closeConnection()
        return@withContext try {
            if (mPrivateKey == null || mCertificate == null) initOrLoadKeys()
            val conn = AdbConnection.Builder(cleanHost, connectPort)
                .setPrivateKey(mPrivateKey!!)
                .setCertificate(mCertificate!!)
                .setDeviceName("UltraRemapperCompanion")
                .build()

            conn.connect(8000L, TimeUnit.MILLISECONDS, false)
            if (conn.isConnectionEstablished) {
                activeConnection = conn
                connectedHost = cleanHost
                connectedPort = connectPort
                setPaired(true, cleanHost, connectPort)
                true
            } else {
                conn.close()
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Connect failed to $cleanHost:$connectPort -> ${e.message}")
            closeConnection()
            false
        }
    }

    /**
     * Executes a shell command using the non-blocking stream read loop proven on Wear OS.
     */
    suspend fun executeCommand(host: String, port: Int, command: String, timeoutMs: Long = 5000L): String =
        withContext(Dispatchers.IO) {
            if (!ensureConnected(host, port)) {
                return@withContext "ERROR: No se pudo conectar a $host:$port"
            }
            val conn = activeConnection ?: return@withContext "ERROR: Sin conexión activa"
            return@withContext try {
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
                            Thread.sleep(40)
                        }
                    }
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

                val res = output.toString().trim()
                Log.d(TAG, "exec('$command') -> '$res'")
                res
            } catch (e: Exception) {
                Log.e(TAG, "Command failed ($command): ${e.message}", e)
                closeConnection()
                "ERROR: ${e.message}"
            }
        }

    /**
     * Streams a local APK file from the phone directly onto the watch over ADB
     * using `pm install-create`, `pm install-write`, and `pm install-commit`.
     */
    suspend fun installApkOnWatch(
        host: String,
        port: Int,
        apkFile: File,
        onProgress: (Float, String) -> Unit
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        if (!apkFile.exists() || apkFile.length() <= 0L) {
            return@withContext Pair(false, "Archivo APK no encontrado o vacío.")
        }
        if (!ensureConnected(host, port)) {
            return@withContext Pair(false, "No se pudo conectar al reloj en $host:$port.")
        }
        val conn = activeConnection ?: return@withContext Pair(false, "Sin sesión ADB activa.")

        try {
            val totalBytes = apkFile.length()
            onProgress(0.05f, "Creando sesión de instalación en el reloj…")

            // 1. Create install session
            val createOut = executeCommand(host, port, "pm install-create -r -S $totalBytes", 8000L)
            val sessionMatch = Regex("\\[(\\d+)]").find(createOut)
            val sessionId = sessionMatch?.groupValues?.getOrNull(1)
                ?: return@withContext Pair(false, "Error al crear sesión pm: $createOut")

            // 2. Stream APK bytes into pm install-write
            val writeCmd = "shell:pm install-write -S $totalBytes $sessionId base.apk -"
            val stream = conn.open(writeCmd)
            val outStream = stream.openOutputStream()
            val inStream = stream.openInputStream()

            try {
                FileInputStream(apkFile).use { fis ->
                    val buf = ByteArray(16 * 1024)
                    var sent = 0L
                    var read: Int
                    var lastReport = 0L
                    while (fis.read(buf).also { read = it } != -1) {
                        outStream.write(buf, 0, read)
                        sent += read
                        val now = System.currentTimeMillis()
                        if (now - lastReport > 120L) {
                            lastReport = now
                            val frac = 0.05f + 0.80f * (sent.toFloat() / totalBytes.toFloat())
                            val pct = ((sent * 100L) / totalBytes).toInt()
                            onProgress(frac, "Enviando APK al reloj ($pct%)…")
                        }
                    }
                    outStream.flush()
                }
            } finally {
                try { outStream.close() } catch (_: Exception) {}
            }

            // Wait briefly for pm install-write response
            val writeResp = StringBuilder()
            val startWait = System.currentTimeMillis()
            val rBuf = ByteArray(512)
            while (!stream.isClosed && System.currentTimeMillis() - startWait < 10000L) {
                val avail = inStream.available()
                if (avail > 0) {
                    val r = inStream.read(rBuf, 0, minOf(avail, rBuf.size))
                    if (r > 0) writeResp.append(String(rBuf, 0, r, Charsets.UTF_8))
                    if (writeResp.contains("Success")) break
                } else {
                    Thread.sleep(50)
                }
            }
            try { stream.close() } catch (_: Exception) {}

            // 3. Commit install session
            onProgress(0.90f, "Instalando paquete en el reloj…")
            val commitOut = executeCommand(host, port, "pm install-commit $sessionId", 25000L)
            return@withContext if (commitOut.contains("Success", ignoreCase = true)) {
                onProgress(1.0f, "¡Instalación completada!")
                Pair(true, "Ultra Remapper instalado/actualizado correctamente en el reloj.")
            } else {
                Pair(false, "Fallo en commit de instalación: $commitOut")
            }
        } catch (e: Exception) {
            Log.e(TAG, "installApkOnWatch failed: ${e.message}", e)
            return@withContext Pair(false, "Error enviando APK al reloj: ${e.message}")
        }
    }

    companion object {
        private const val PREFS_KEYS = "ultra_adb_keys"
        private const val KEY_PRIV = "priv_key"
        private const val KEY_CERT = "cert"

        private const val PREFS_STATE = "ultra_companion_adb_state"
        private const val KEY_IS_PAIRED = "is_adb_paired"
        private const val KEY_LAST_HOST = "last_watch_host"
        private const val KEY_LAST_PORT = "last_watch_port"

        @Volatile
        private var instance: AdbHelper? = null

        fun getInstance(context: Context): AdbHelper {
            return instance ?: synchronized(this) {
                instance ?: AdbHelper(context.applicationContext).also { instance = it }
            }
        }
    }
}
