package com.numbered.app.data

import java.nio.ByteBuffer
import java.security.SecureRandom
import java.text.Normalizer
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** An encrypted payload and everything needed, besides the passphrase, to open it. */
internal class Sealed(val salt: ByteArray, val nonce: ByteArray, val iterations: Int, val ciphertext: ByteArray)

/**
 * Passphrase encryption for export files: PBKDF2-HMAC-SHA256 stretches the passphrase into an
 * AES-256-GCM key. The passphrase is NFC-normalized and encoded as UTF-8 explicitly, so the same
 * words typed on any keyboard or platform give the same key.
 */
internal object BackupCipher {
    const val KDF = "pbkdf2-hmac-sha256"
    const val CIPHER = "aes-256-gcm"

    /** OWASP's 2023 recommendation for PBKDF2-HMAC-SHA256. Files record their own count. */
    const val ITERATIONS = 600_000

    /** Refuses files that would make the phone hash for minutes. */
    const val MAX_ITERATIONS = 10_000_000

    private const val SALT_BYTES = 16
    private const val NONCE_BYTES = 12
    private const val KEY_BYTES = 32
    private const val TAG_BITS = 128

    /** Binds the ciphertext to this file format, so it cannot be passed off as something else. */
    private val associatedData = "numbered-backup".toByteArray(Charsets.UTF_8)
    private val random = SecureRandom()

    fun seal(plain: ByteArray, passphrase: String): Sealed {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val cipher = cipher(Cipher.ENCRYPT_MODE, passphrase, salt, nonce, ITERATIONS)
        return Sealed(salt, nonce, ITERATIONS, cipher.doFinal(plain))
    }

    /** The plain bytes, or null when the passphrase is wrong or the ciphertext was altered. */
    fun open(sealed: Sealed, passphrase: String): ByteArray? = try {
        cipher(Cipher.DECRYPT_MODE, passphrase, sealed.salt, sealed.nonce, sealed.iterations).doFinal(sealed.ciphertext)
    } catch (_: AEADBadTagException) {
        null
    }

    private fun cipher(mode: Int, passphrase: String, salt: ByteArray, nonce: ByteArray, iterations: Int): Cipher {
        val key = pbkdf2(passphraseBytes(passphrase), salt, iterations, KEY_BYTES)
        return Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
            updateAAD(associatedData)
        }.also { key.fill(0) }
    }

    fun passphraseBytes(passphrase: String): ByteArray =
        Normalizer.normalize(passphrase, Normalizer.Form.NFC).toByteArray(Charsets.UTF_8)

    /** PBKDF2 (RFC 8018) with HMAC-SHA256. */
    fun pbkdf2(password: ByteArray, salt: ByteArray, iterations: Int, length: Int): ByteArray {
        require(iterations > 0 && length > 0)
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(password, "HmacSHA256")) }
        val blockSize = mac.macLength
        val out = ByteArray(length)
        var block = 1
        var offset = 0
        while (offset < length) {
            mac.update(salt)
            var u = mac.doFinal(ByteBuffer.allocate(4).putInt(block).array())
            val t = u.copyOf()
            repeat(iterations - 1) {
                u = mac.doFinal(u)
                for (i in t.indices) t[i] = (t[i].toInt() xor u[i].toInt()).toByte()
            }
            t.copyInto(out, offset, 0, minOf(blockSize, length - offset))
            offset += blockSize
            block++
        }
        return out
    }
}
