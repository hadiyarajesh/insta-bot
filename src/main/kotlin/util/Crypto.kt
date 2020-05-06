package util

import java.math.BigInteger
import java.util.*
import kotlin.math.roundToInt

object Crypto {

    // Signature class
    data class Signature(val signed: String, val appVersion: String, val sigKeyVersion: String, val payload: String)

    // Signature function
    fun signData(payload: String): Signature {
        val signed = generateHMAC(KEY.SIG_KEY, payload)
        return Signature(
            signed,
            KEY.APP_VERSION,
            KEY.SIG_KEY_VERSION,
            payload
        )
    }

    // Generate MD5 Hash of given string
    private fun generateMD5(s: String): String {
        return try {
            val messageDigest = java.security.MessageDigest.getInstance("MD5")
            messageDigest.update(s.toByteArray(), 0, s.length)
            BigInteger(1, messageDigest.digest()).toString(16)
        } catch (e: Exception) {
            System.err.println("Error occurred while generating MD5 $e")
            ""
        }
    }

    // Generate hash-based message authentication code of given data
    fun generateHMAC(key: String, data: String): String {
        return try {
            val sha256HMAC = javax.crypto.Mac.getInstance("HmacSHA256")
            val secretKey = javax.crypto.spec.SecretKeySpec(key.toByteArray(charset("UTF-8")), "HmacSHA256")
            sha256HMAC.init(secretKey)
            val bytes = sha256HMAC.doFinal(data.toByteArray(charset("UTF-8")))
            java.lang.String.format("%040x", BigInteger(1, bytes))
        } catch (e: Exception) {
            System.err.println("Error occurred while generating HMAC $e")
            ""
        }
    }

    // Random UUID Generator function
    fun generateUUID(type: Boolean): String {
        var uuid = UUID.randomUUID().toString()
        if (!type) {
            uuid = uuid.replace("-", "")
        }
        return uuid
    }

    // Generate temporary GUID
    fun generateTemporaryGUID(name: String, uuid: String, duration: Float): String {
        return UUID.nameUUIDFromBytes("$name$uuid${(System.currentTimeMillis() / duration).roundToInt()}".toByteArray())
            .toString()
    }

    // Generate Device ID
    fun generateDeviceId(username: String): String {
        val seed = 11111 + (Math.random() * ((99999 - 11111) + 1))
        val hash = generateMD5("$username$seed")
        return "android-${hash.substring(0, 16)}"
    }
}
