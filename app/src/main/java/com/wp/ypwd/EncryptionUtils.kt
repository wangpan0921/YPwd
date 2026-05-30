package com.wp.ypwd

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object EncryptionUtils {
    private const val ALGORITHM = "AES/CBC/PKCS7Padding"
    private const val SECRET_KEY_FACTORY_ALGORITHM = "PBKDF2WithHmacSHA1"
    private const val ITERATION_COUNT = 1000
    private const val KEY_SIZE = 256
    private const val SALT = "password_manager_salt"
    private const val IV = "passwordmanageiv"

    // 加密
    fun encrypt(data: String, key: String): String {
        try {
            val secretKey = generateSecretKey(key.toCharArray())
            val cipher = Cipher.getInstance(ALGORITHM)
            val ivSpec = IvParameterSpec(IV.toByteArray(Charsets.UTF_8))
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec)
            val encryptedData = cipher.doFinal(data.toByteArray(Charsets.UTF_8))
            return Base64.encodeToString(encryptedData, Base64.DEFAULT)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return ""
    }

    // 解密
    fun decrypt(encryptedData: String, key: String): String {
        try {
            val secretKey = generateSecretKey(key.toCharArray())
            val cipher = Cipher.getInstance(ALGORITHM)
            val ivSpec = IvParameterSpec(IV.toByteArray(Charsets.UTF_8))
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)
            val decodedData = Base64.decode(encryptedData, Base64.DEFAULT)
            val decryptedData = cipher.doFinal(decodedData)
            return String(decryptedData, Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return ""
    }

    // 生成密钥
    private fun generateSecretKey(password: CharArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance(SECRET_KEY_FACTORY_ALGORITHM)
        val spec = PBEKeySpec(password, SALT.toByteArray(), ITERATION_COUNT, KEY_SIZE)
        val tmp = factory.generateSecret(spec)
        return SecretKeySpec(tmp.encoded, "AES")
    }
}
    