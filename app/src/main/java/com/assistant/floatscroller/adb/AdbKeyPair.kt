package com.assistant.floatscroller.adb

import android.content.Context
import android.util.Base64
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher

class AdbKeyPair(
    val privateKey: PrivateKey,
    val publicKey: PublicKey,
    val publicKeyBytes: ByteArray
) {
    companion object {
        private const val TAG = "AdbKeyPair"
        private const val KEY_SIZE = 2048

        fun getOrGenerate(context: Context): AdbKeyPair {
            val privFile = File(context.filesDir, "local_adb_key")
            val pubFile = File(context.filesDir, "local_adb_key.pub")

            if (privFile.exists() && pubFile.exists()) {
                try {
                    val privBytes = FileInputStream(privFile).use { it.readBytes() }
                    val pubBytes = FileInputStream(pubFile).use { it.readBytes() }

                    val kf = KeyFactory.getInstance("RSA")
                    val privKey = kf.generatePrivate(PKCS8EncodedKeySpec(privBytes))
                    val pubKey = kf.generatePublic(X509EncodedKeySpec(pubBytes))

                    val adbPubString = "${Base64.encodeToString(pubKey.encoded, Base64.NO_WRAP)} local@floatscroller\u0000"
                    return AdbKeyPair(privKey, pubKey, adbPubString.toByteArray(Charsets.UTF_8))
                } catch (e: Exception) {
                    Log.w(TAG, "加载已有 ADB 密钥失败，重新生成: ${e.message}")
                }
            }

            // 生成新的 RSA 密钥对
            val kpg = KeyPairGenerator.getInstance("RSA")
            kpg.initialize(KEY_SIZE)
            val pair = kpg.generateKeyPair()

            FileOutputStream(privFile).use { it.write(pair.private.encoded) }
            FileOutputStream(pubFile).use { it.write(pair.public.encoded) }

            val adbPubString = "${Base64.encodeToString(pair.public.encoded, Base64.NO_WRAP)} local@floatscroller\u0000"
            return AdbKeyPair(pair.private, pair.public, adbPubString.toByteArray(Charsets.UTF_8))
        }
    }

    /**
     * 使用私钥对 adbd 挑战的 token 进行签名
     */
    fun sign(token: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, privateKey)
        return cipher.doFinal(token)
    }
}
