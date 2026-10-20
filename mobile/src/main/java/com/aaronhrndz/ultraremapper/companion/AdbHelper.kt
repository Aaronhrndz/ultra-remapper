package com.aaronhrndz.ultraremapper.companion

import android.content.Context
import android.os.Build
import android.util.Base64
import android.util.Log
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date

class AdbHelper private constructor(private val context: Context) : AbsAdbConnectionManager() {

    private val TAG = "UltraAdbHelper"
    private var mPrivateKey: PrivateKey? = null
    private var mCertificate: Certificate? = null

    init {
        setApi(Build.VERSION.SDK_INT)
        initOrLoadKeys()
    }

    private fun initOrLoadKeys() {
        val prefs = context.getSharedPreferences("ultra_adb_keys", Context.MODE_PRIVATE)
        val privB64 = prefs.getString("priv_key", null)
        val certB64 = prefs.getString("cert", null)

        if (privB64 != null && certB64 != null) {
            try {
                val privBytes = Base64.decode(privB64, Base64.NO_WRAP)
                val keyFactory = KeyFactory.getInstance("RSA")
                mPrivateKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(privBytes))

                val certBytes = Base64.decode(certB64, Base64.NO_WRAP)
                val cf = CertificateFactory.getInstance("X.509")
                mCertificate = cf.generateCertificate(ByteArrayInputStream(certBytes))
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
                .putString("priv_key", Base64.encodeToString(keyPair.private.encoded, Base64.NO_WRAP))
                .putString("cert", Base64.encodeToString(cert.encoded, Base64.NO_WRAP))
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

    override fun getPrivateKey(): PrivateKey = mPrivateKey!!

    override fun getCertificate(): Certificate = mCertificate!!

    override fun getDeviceName(): String = "UltraRemapperCompanion"

    suspend fun pairDevice(host: String, port: Int, pairingCode: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val result = withTimeoutOrNull(12000L) {
            try {
                Log.d(TAG, "Pairing with $host:$port code: $pairingCode")
                val success = pair(host, port, pairingCode)
                if (success) {
                    Pair(true, "¡Reloj emparejado con éxito!")
                } else {
                    Pair(false, "El reloj rechazó la vinculación. Comprueba que el código de 6 dígitos no haya caducado.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Pairing exception: ${e.message}", e)
                val msg = e.message ?: e.javaClass.simpleName
                Pair(false, "Error al vincular: $msg")
            }
        }
        result ?: Pair(false, "Tiempo de espera agotado (12s). Asegúrate de que la pantalla de vincular del reloj sigue abierta.")
    }

    suspend fun executeCommand(host: String, port: Int, command: String): String = withContext(Dispatchers.IO) {
        val res = withTimeoutOrNull(10000L) {
            try {
                Log.d(TAG, "Connecting to $host:$port to run: $command")
                connect(host, port)
                val stream = openStream("shell:$command")
                val reader = BufferedReader(InputStreamReader(stream.openInputStream()))
                val output = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    output.append(line).append("\n")
                }
                stream.close()
                output.toString().trim()
            } catch (e: Exception) {
                Log.e(TAG, "Command execution failed: ${e.message}", e)
                "ERROR: ${e.message}"
            }
        }
        res ?: "ERROR: Tiempo de espera agotado (10s)"
    }

    companion object {
        @Volatile
        private var instance: AdbHelper? = null

        fun getInstance(context: Context): AdbHelper {
            return instance ?: synchronized(this) {
                instance ?: AdbHelper(context.applicationContext).also { instance = it }
            }
        }
    }
}
