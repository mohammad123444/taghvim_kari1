package ir.taghvimkari.app.data

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Sent files are encrypted (AES-256-GCM) so that anybody who opens one outside this app sees only
 * unreadable text. The key is built into the app, so every copy of the app can read the files of every
 * other copy without any setup. (It keeps the content from curious eyes; it is not protection against
 * someone who takes the app apart. Use a passphrase for that.)
 */
object Secure {
    private const val SECRET = "taghvim-kari/exchange/v1/9f3c1b7a"
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128

    private fun key(): SecretKeySpec =
        SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(SECRET.toByteArray(Charsets.UTF_8)), "AES")

    /** Returns the text as one Base64 string: the random IV followed by the encrypted bytes. */
    fun encrypt(plain: String): String {
        val iv = ByteArray(IV_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv))
        val body = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(iv + body)
    }

    /** Reverse of [encrypt]; throws if the text was changed or is not ours. */
    fun decrypt(text: String): String {
        val all = Base64.getDecoder().decode(text.trim())
        require(all.size > IV_BYTES) { "bad data" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, all.copyOfRange(0, IV_BYTES)))
        return String(cipher.doFinal(all, IV_BYTES, all.size - IV_BYTES), Charsets.UTF_8)
    }
}
