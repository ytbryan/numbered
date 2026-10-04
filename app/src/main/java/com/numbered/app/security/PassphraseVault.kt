package com.numbered.app.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Keeps a passphrase on this phone so work in the background can use it without asking. */
interface PassphraseVault {
    fun seal(passphrase: String): String

    /** The passphrase, or null when it can no longer be opened, such as after its key was removed. */
    fun open(sealed: String): String?
}

/**
 * Seals with an AES-GCM key held by Android Keystore, which never leaves the secure hardware where
 * the phone has it. The sealed text is useless on any other phone or after the app is reinstalled.
 */
class KeystoreVault : PassphraseVault {
    override fun seal(passphrase: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val sealed = cipher.iv + cipher.doFinal(passphrase.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(sealed)
    }

    override fun open(sealed: String): String? = try {
        val bytes = Base64.getDecoder().decode(sealed)
        val key = existingKey() ?: return null
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES))
        }
        cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES).toString(Charsets.UTF_8)
    } catch (_: GeneralSecurityException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun existingKey(): SecretKey? =
        (KeyStore.getInstance(KEYSTORE).apply { load(null) }.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey

    private fun key(): SecretKey = existingKey() ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
        init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "auto-backup-passphrase"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
