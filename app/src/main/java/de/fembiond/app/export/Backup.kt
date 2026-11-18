package de.fembiond.app.export

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Passwortgeschützte Sicherung: PBKDF2-HMAC-SHA256 + AES-256-GCM. */
object Backup {
    private val MAGIC = "FEMBIOND1".toByteArray()
    private const val ITERATIONS = 210_000

    private fun key(password: CharArray, salt: ByteArray): SecretKeySpec {
        val f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val bytes = f.generateSecret(PBEKeySpec(password, salt, ITERATIONS, 256)).encoded
        return SecretKeySpec(bytes, "AES")
    }

    fun encrypt(plain: String, password: CharArray): ByteArray {
        val rnd = SecureRandom()
        val salt = ByteArray(16).also(rnd::nextBytes)
        val iv = ByteArray(12).also(rnd::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
        return MAGIC + salt + iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
    }

    fun decrypt(data: ByteArray, password: CharArray): String {
        require(data.size > MAGIC.size + 28 && data.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) { "Keine Fembiond-Sicherung" }
        var o = MAGIC.size
        val salt = data.copyOfRange(o, o + 16); o += 16
        val iv = data.copyOfRange(o, o + 12); o += 12
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(data.copyOfRange(o, data.size)), Charsets.UTF_8)
    }
}
