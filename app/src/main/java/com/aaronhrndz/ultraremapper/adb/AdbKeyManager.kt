package com.aaronhrndz.ultraremapper.adb

import android.content.Context
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.interfaces.RSAPrivateCrtKey
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.Base64
import javax.crypto.Cipher

/**
 * Handles RSA-2048 keypair generation, storage and ADB authentication token signing.
 */
object AdbKeyManager {
    private const val PREF_NAME = "ultra_adb_keys"
    private const val KEY_PRIVATE = "rsa_private"
    private const val KEY_PUBLIC = "rsa_public"

    @Volatile
    private var cachedKeyPair: KeyPair? = null

    fun getOrCreateKeyPair(context: Context): KeyPair {
        cachedKeyPair?.let { return it }

        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val privStr = prefs.getString(KEY_PRIVATE, null)
        val pubStr = prefs.getString(KEY_PUBLIC, null)

        if (privStr != null && pubStr != null) {
            try {
                val kf = java.security.KeyFactory.getInstance("RSA")
                val privBytes = Base64.getDecoder().decode(privStr)
                val pubBytes = Base64.getDecoder().decode(pubStr)

                val privKey = kf.generatePrivate(java.security.spec.PKCS8EncodedKeySpec(privBytes))
                val pubKey = kf.generatePublic(java.security.spec.X509EncodedKeySpec(pubBytes))

                val pair = KeyPair(pubKey, privKey)
                cachedKeyPair = pair
                return pair
            } catch (_: Exception) {}
        }

        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048, SecureRandom())
        val newPair = kpg.generateKeyPair()

        val privBase64 = Base64.getEncoder().encodeToString(newPair.private.encoded)
        val pubBase64 = Base64.getEncoder().encodeToString(newPair.public.encoded)

        prefs.edit()
            .putString(KEY_PRIVATE, privBase64)
            .putString(KEY_PUBLIC, pubBase64)
            .apply()

        cachedKeyPair = newPair
        return newPair
    }

    /** Signs token from ADB daemon using raw RSA PKCS#1 v1.5 padding */
    fun signToken(token: ByteArray, privateKey: RSAPrivateKey): ByteArray {
        val cipher = Cipher.getInstance("RSA/ECB/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, privateKey)

        // Format PKCS#1 v1.5 padding block for 2048-bit (256-byte) RSA
        val modulusSize = 256
        val padded = ByteArray(modulusSize)
        padded[0] = 0x00
        padded[1] = 0x01
        for (i in 2 until modulusSize - token.size - 1) {
            padded[i] = 0xFF.toByte()
        }
        padded[modulusSize - token.size - 1] = 0x00
        System.arraycopy(token, 0, padded, modulusSize - token.size, token.size)

        return cipher.doFinal(padded)
    }

    /** Formats ADB public key string format: "<base64_android_pubkey> UltraRemapper\u0000" */
    fun getAdbPublicKeyString(publicKey: RSAPublicKey): String {
        val base64 = Base64.getEncoder().encodeToString(publicKey.encoded)
        return "$base64 UltraRemapper@localhost\u0000"
    }
}
